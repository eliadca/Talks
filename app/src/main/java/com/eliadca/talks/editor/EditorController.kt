package com.eliadca.talks.editor

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eliadca.talks.core.doc.Align
import com.eliadca.talks.core.doc.BlockType
import com.eliadca.talks.core.doc.Markup
import com.eliadca.talks.core.doc.toMarkdown
import com.eliadca.talks.core.doc.RichDoc

/**
 * Connects the Android [RichEditText] to Compose: exposes what the toolbar needs as observable
 * state and forwards its commands to the view.
 */
@Stable
class EditorController : EditorListener {

    private var view: RichEditText? = null

    var format by mutableStateOf(FormatState())
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var findTotal by mutableIntStateOf(0)
        private set
    var findCurrent by mutableIntStateOf(0)
        private set
    var findOpen by mutableStateOf(false)

    /** Called whenever the text or its formatting changes. */
    var onChanged: (() -> Unit)? = null

    fun attach(v: RichEditText) {
        view = v
        v.listener = this
    }

    fun detach(v: RichEditText) {
        if (view === v) {
            v.listener = null
            view = null
        }
    }

    val isAttached: Boolean get() = view != null

    /** The Markdown source shown instead of the formatted page; null while the page is formatted. */
    var markdown by mutableStateOf<String?>(null)
        private set

    /** The document the formatted page shows when it comes back from Markdown. */
    private var pendingDoc: RichDoc? = null

    /** Shows the speech as Markdown, to read or edit it as plain text. */
    fun showMarkdown() {
        val doc = snapshot() ?: return
        markdown = doc.toMarkdown()
    }

    fun editMarkdown(text: String) {
        if (text == markdown) return
        markdown = text
        onChanged?.invoke()
    }

    /** Back to the formatted page, with what was written in Markdown. */
    fun showFormatted() {
        val md = markdown ?: return
        pendingDoc = Markup.parse(md)
        markdown = null
    }

    /** The document a new page should load instead of the stored one, if any (taken once). */
    fun takePendingDoc(): RichDoc? = pendingDoc.also { pendingDoc = null }

    /** Forgets any Markdown view (another speech is opened). */
    fun reset() {
        markdown = null
        pendingDoc = null
    }

    /** The current document, or null when no editor is attached. */
    fun snapshot(): RichDoc? = markdown?.let { Markup.parse(it) } ?: pendingDoc ?: view?.toDocument()

    // --- listener -----------------------------------------------------------------------------

    override fun onContentChanged() { onChanged?.invoke() }
    override fun onFormatChanged(state: FormatState) { format = state }
    override fun onHistoryChanged(canUndo: Boolean, canRedo: Boolean) {
        this.canUndo = canUndo
        this.canRedo = canRedo
    }
    override fun onFindChanged(total: Int, current: Int) {
        findTotal = total
        findCurrent = current
    }
    override fun onFindShortcut() { findOpen = true }

    // --- commands -----------------------------------------------------------------------------

    fun toggle(kind: InlineKind) { view?.toggleInline(kind) }
    fun setColor(argb: Int?) { view?.setInlineValue(InlineKind.COLOR, argb) }
    fun setHighlight(argb: Int?) { view?.setInlineValue(InlineKind.HIGHLIGHT, argb) }
    fun setSizePercent(percent: Int?) { view?.setInlineValue(InlineKind.SIZE, percent?.takeIf { it != 100 }) }
    fun toggleBlock(block: BlockType) { view?.toggleBlock(block) }
    fun setAlign(align: Align) { view?.setAlign(align) }
    fun indent(delta: Int) { view?.changeIndent(delta) }
    fun clearFormatting() { view?.clearFormatting() }
    fun undo() { view?.undo() }
    fun redo() { view?.redo() }
    fun insertText(text: String) { view?.insertAtCursor(text) }
    fun insertNote() { view?.insertNote() }
    fun goTo(offset: Int) { view?.goTo(offset) }

    fun find(query: String) { view?.find(query) }
    fun findNext() { view?.findNext() }
    fun findPrevious() { view?.findPrevious() }
    fun replaceCurrent(with: String) { view?.replaceCurrent(with) }
    fun replaceAll(with: String): Int = view?.replaceAll(with) ?: 0
    fun closeFind() {
        findOpen = false
        view?.clearFind()
    }

    fun focus(showKeyboard: Boolean = false) {
        val v = view ?: return
        v.requestFocus()
        if (showKeyboard) {
            val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun hideKeyboard() {
        val v = view ?: return
        val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(v.windowToken, 0)
    }
}
