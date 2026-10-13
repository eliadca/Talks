package com.eliadca.talks.ui.notes

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.eliadca.talks.core.notes.NoteFormatting
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.eliadca.talks.core.bible.BibleReference
import com.eliadca.talks.core.bible.BibleReferenceMatch
import com.eliadca.talks.core.bible.BibleReferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class NoteLinks(val source: String, val matches: List<BibleReferenceMatch>)

/** Saved Bible passages have a reference heading and numbered verse lines; the entire quote can reopen it. */
private fun passageEnd(text: String, match: BibleReferenceMatch): Int {
    val lineStart = text.lastIndexOf('\n', match.start - 1) + 1
    if (text.substring(lineStart, match.start).isNotBlank()) return match.end
    val lineEnd = text.indexOf('\n', match.end)
    if (lineEnd < 0 || text.substring(match.end, lineEnd).isNotBlank()) return match.end
    var rowStart = lineEnd + 1
    var end = match.end
    for (verse in match.reference.firstVerse..match.reference.lastVerse) {
        val rowEnd = text.indexOf('\n', rowStart).takeIf { it >= 0 } ?: text.length
        val number = Regex("^(\\d{1,3})\\s+").find(text.substring(rowStart, rowEnd)) ?: break
        if (number.groupValues[1].toIntOrNull() != verse) break
        end = rowEnd
        if (rowEnd == text.length) break
        rowStart = rowEnd + 1
    }
    return end
}

/** A tap opens a verse; long press still selects editable text, and ink tools never trigger links. */
@Composable
fun BibleLinkedTextField(
    fieldValue: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, readOnly: Boolean, linksEnabled: Boolean,
    onReference: (BibleReference) -> Unit, textStyle: TextStyle, onTextLayout: (TextLayoutResult) -> Unit,
    modifier: Modifier = Modifier,
    formatting: NoteFormatting = NoteFormatting(), textScale: Float = 1f,
    onReferenceAt: ((BibleReference, Rect) -> Unit)? = null, onAnchorChanged: (Rect) -> Unit = {},
    decorationBox: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    val links by produceState(NoteLinks("", emptyList()), fieldValue.text) {
        delay(100)
        val matches = withContext(Dispatchers.Default) { BibleReferences.scan(fieldValue.text).matches }
        value = NoteLinks(fieldValue.text, matches)
    }
    val matches = if (links.source == fieldValue.text) links.matches else emptyList()
    val quoteEnds = remember(matches, fieldValue.text) { matches.associateWith { passageEnd(fieldValue.text, it) } }
    val open by rememberUpdatedState(onReference)
    val openAt by rememberUpdatedState(onReferenceAt)
    val moveAnchor by rememberUpdatedState(onAnchorChanged)
    var anchorOffset by remember { mutableStateOf<Int?>(null) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val color = MaterialTheme.colorScheme.primary
    val transformation = remember(matches, quoteEnds, color, formatting, textScale) {
        object : VisualTransformation {
            override fun filter(text: AnnotatedString): TransformedText {
                val builder = AnnotatedString.Builder(text)
                formatting.spans.forEach { span ->
                    val a = span.start.coerceIn(0, text.length); val b = span.end.coerceIn(a, text.length)
                    if (b > a) with(span.style) {
                        val decorations = buildList { if (underline) add(TextDecoration.Underline); if (strike) add(TextDecoration.LineThrough) }
                        builder.addStyle(SpanStyle(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = NoteFonts.family(font), letterSpacing = (tracking * textScale).sp,
                            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = TextDecoration.combine(decorations), fontSize = (size * textScale).sp,
                            color = span.style.color?.let { Color(it) } ?: Color.Unspecified,
                            background = span.style.highlight?.let { Color(it) } ?: Color.Unspecified), a, b)
                    }
                }
                // A large font must also enlarge its line box. Fixed leading caused overlapping
                // lines in free text boxes; compute leading per paragraph, including mixed sizes.
                var start = 0
                while (start < text.length) {
                    val lineEnd = text.text.indexOf('\n', start).takeIf { it >= 0 } ?: text.length
                    val end = if (lineEnd < text.length) lineEnd + 1 else lineEnd
                    val boundaries = (listOf(start, lineEnd) + formatting.spans.flatMap { listOf(it.start, it.end) }
                        .filter { it > start && it < lineEnd }).distinct().sorted()
                    val size = boundaries.zipWithNext().maxOfOrNull { (a, _) -> formatting.styleAt(a).size } ?: 46f
                    val alignment = formatting.paragraphs.lastOrNull { start >= it.start && start < it.end }?.alignment ?: 0
                    builder.addStyle(ParagraphStyle(lineHeight = (size * textScale * 1.39f).sp, textAlign = when (alignment) {
                        1 -> TextAlign.Center; 2 -> TextAlign.End; 3 -> TextAlign.Justify; else -> TextAlign.Start
                    }), start, end)
                    start = end
                }
                matches.filter { it.end <= text.length }.forEach {
                    val quoteEnd = quoteEnds[it] ?: it.end
                    if (quoteEnd > it.end && quoteEnd <= text.length) builder.addStyle(SpanStyle(background = color.copy(alpha = .04f)), it.start, quoteEnd)
                    builder.addStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline,
                        background = color.copy(alpha = .08f)), it.start, it.end)
                }
                return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
            }
        }
    }
    BasicTextField(fieldValue, onValueChange, readOnly = readOnly, textStyle = textStyle,
        visualTransformation = transformation,
        onTextLayout = { layout = it; onTextLayout(it) }, decorationBox = decorationBox,
        modifier = modifier.onGloballyPositioned { coord ->
            coordinates = coord
            anchorOffset?.takeIf { it in fieldValue.text.indices }?.let { offset ->
                layout?.getBoundingBox(offset)?.let { bounds -> moveAnchor(bounds.translate(coord.positionInWindow())) }
            }
        }
            .semantics {
                customActions = if (!linksEnabled) emptyList() else matches.distinctBy { it.reference }.map { match ->
                    CustomAccessibilityAction("Ver ${match.reference.label}") { open(match.reference); true }
                }
            }
            .pointerInput(fieldValue.text, matches, quoteEnds, linksEnabled) {
                if (!linksEnabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val offset = layout?.getOffsetForPosition(down.position)
                    val match = matches.firstOrNull { offset != null && offset >= it.start && offset < it.end }
                        ?: matches.firstOrNull { offset != null && offset >= it.start && offset < (quoteEnds[it] ?: it.end) }
                    if (match != null) {
                        // TextField consumes its press for focus. Observe the physical gesture at Initial
                        // without treating that focus consumption as a cancelled tap.
                        var dragged = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (event.changes.size > 1 || (change.position - down.position).getDistance() >= viewConfiguration.touchSlop) dragged = true
                            if (!change.pressed) {
                                if (!dragged && event.type == PointerEventType.Release &&
                                    change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                                    change.consume(); keyboard?.hide()
                                    val origin = coordinates?.takeIf { it.isAttached }?.positionInWindow()
                                    val box = offset?.coerceIn(0, (fieldValue.text.length - 1).coerceAtLeast(0))?.let { layout?.getBoundingBox(it) }
                                    if (origin != null && box != null && openAt != null) { anchorOffset = offset; openAt?.invoke(match.reference, box.translate(origin)) }
                                    else open(match.reference)
                                }
                                break
                            }
                        }
                    }
                }
            })
}
