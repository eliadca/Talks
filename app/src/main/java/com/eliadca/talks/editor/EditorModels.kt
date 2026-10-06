package com.eliadca.talks.editor

import com.eliadca.talks.core.doc.Align
import com.eliadca.talks.core.doc.BlockType

/** Inline formatting the toolbar can switch on and off. */
enum class InlineKind { BOLD, ITALIC, UNDERLINE, STRIKE, COLOR, HIGHLIGHT, SIZE, STAGE }

/** What the toolbar shows for the current selection or cursor. */
data class FormatState(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val stage: Boolean = false,
    /** Text colour (ARGB), 0 when the default colour is used. */
    val color: Int = 0,
    /** Highlight colour (ARGB), 0 for none. */
    val highlight: Int = 0,
    /** Size relative to the normal size, in percent. */
    val sizePercent: Int = 100,
    val block: BlockType = BlockType.NORMAL,
    val align: Align = Align.START,
    val indent: Int = 0,
)

/** Events from the editor view to whoever hosts it. */
interface EditorListener {
    fun onContentChanged()
    fun onFormatChanged(state: FormatState)
    fun onHistoryChanged(canUndo: Boolean, canRedo: Boolean)
    /** [total] matches of the find bar and the 1-based number of the [current] one (0 when none). */
    fun onFindChanged(total: Int, current: Int)
    /** The user pressed the find shortcut on a hardware keyboard. */
    fun onFindShortcut()
}
