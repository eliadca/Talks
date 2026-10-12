package com.eliadca.talks.ui.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.eliadca.talks.bible.BiblePassage
import kotlin.math.roundToInt

/** Window coordinates come from the tapped glyph, so the bubble follows the actual citation. */
class CitationPosition(private val citation: Rect?, private val gap: Int = 18) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val ref = citation ?: Rect(anchorBounds.left.toFloat() + 16, anchorBounds.bottom.toFloat() - 90, anchorBounds.left.toFloat() + 20, anchorBounds.bottom.toFloat() - 70)
        val right = ref.right.roundToInt() + gap
        val x = when {
            right + popupContentSize.width + gap <= windowSize.width -> right
            ref.left - popupContentSize.width - gap >= gap -> ref.left.roundToInt() - popupContentSize.width - gap
            else -> ref.left.roundToInt()
        }.coerceIn(gap, maxOf(gap, windowSize.width - popupContentSize.width - gap))
        val y = (ref.bottom.roundToInt() + gap).let {
            if (it + popupContentSize.height + gap <= windowSize.height) it else ref.top.roundToInt() - popupContentSize.height - gap
        }.coerceIn(gap, maxOf(gap, windowSize.height - popupContentSize.height - gap))
        return IntOffset(x, y)
    }
}

@Composable
fun CitationThought(passage: BiblePassage, anchor: Rect?, expanded: Boolean, onExpand: (Boolean) -> Unit,
    onDismiss: () -> Unit, onAdd: () -> Unit) {
    val position = remember(anchor) { CitationPosition(anchor) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val color = MaterialTheme.colorScheme.secondaryContainer
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true)) {
        AnimatedVisibility(visible, enter = fadeIn(tween(160)) + scaleIn(initialScale = .94f, animationSpec = tween(160))) {
        Column(Modifier.widthIn(max = 400.dp)) {
            Canvas(Modifier.width(48.dp).height(20.dp).padding(start = 10.dp)) {
                drawCircle(color, 3.dp.toPx(), Offset(3.dp.toPx(), 3.dp.toPx()))
                drawCircle(color, 5.dp.toPx(), Offset(12.dp.toPx(), 10.dp.toPx()))
                drawCircle(color, 7.dp.toPx(), Offset(24.dp.toPx(), 18.dp.toPx()))
            }
            FloatingPassage(passage, expanded, onExpand, onDismiss, onAdd, Modifier.widthIn(max = 400.dp))
        }
        }
    }
}
