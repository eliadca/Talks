package com.eliadca.talks.ui.notes

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

/** A tap opens a verse; long press still selects editable text, and ink tools never trigger links. */
@Composable
fun BibleLinkedTextField(
    fieldValue: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, readOnly: Boolean, linksEnabled: Boolean,
    onReference: (BibleReference) -> Unit, textStyle: TextStyle, onTextLayout: (TextLayoutResult) -> Unit,
    modifier: Modifier = Modifier, decorationBox: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    val links by produceState(NoteLinks("", emptyList()), fieldValue.text) {
        delay(100)
        val matches = withContext(Dispatchers.Default) { BibleReferences.scan(fieldValue.text).matches }
        value = NoteLinks(fieldValue.text, matches)
    }
    val matches = if (links.source == fieldValue.text) links.matches else emptyList()
    val open by rememberUpdatedState(onReference)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val color = MaterialTheme.colorScheme.primary
    val transformation = remember(matches, color) {
        object : VisualTransformation {
            override fun filter(text: AnnotatedString): TransformedText {
                val builder = AnnotatedString.Builder(text)
                matches.filter { it.end <= text.length }.forEach {
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
        modifier = modifier
            .semantics {
                customActions = if (!linksEnabled) emptyList() else matches.distinctBy { it.reference }.map { match ->
                    CustomAccessibilityAction("Ver ${match.reference.label}") { open(match.reference); true }
                }
            }
            .pointerInput(fieldValue.text, matches, linksEnabled) {
                if (!linksEnabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val offset = layout?.getOffsetForPosition(down.position)
                    val match = matches.firstOrNull { offset != null && offset >= it.start && offset < it.end }
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
                                    change.consume(); keyboard?.hide(); open(match.reference)
                                }
                                break
                            }
                        }
                    }
                }
            })
}
