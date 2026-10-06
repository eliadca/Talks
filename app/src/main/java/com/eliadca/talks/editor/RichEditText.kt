package com.eliadca.talks.editor

import android.content.Context
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import com.eliadca.talks.core.doc.Align
import com.eliadca.talks.core.doc.BlockType
import com.eliadca.talks.core.doc.RichDoc
import com.eliadca.talks.core.text.SpanishText
import com.eliadca.talks.editor.SpannableCodec.BLOCK_FLAGS
import com.eliadca.talks.editor.SpannableCodec.END_MARKER
import com.eliadca.talks.editor.SpannableCodec.INLINE_FLAGS
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A word-processor style text editor: bold/italic/underline/strike, colours, highlight, sizes,
 * headings, bullet/numbered/check lists, quotes, alignment, indentation, undo/redo and find/replace,
 * on top of Android's own text engine (so IME, spell-check, hardware keyboards and S Pen
 * handwriting all behave natively).
 *
 * Offsets used by every public method are offsets into the document text; the invisible end marker
 * (see [SpannableCodec]) is an internal detail.
 */
class RichEditText(context: Context, val style: EditorStyle) : EditText(context) {

    var listener: EditorListener? = null

    private var internalDepth = 0
    private var quiet = false
    private var ready = false

    // --- undo / redo -------------------------------------------------------------------------

    private class Snapshot(val doc: RichDoc, val selStart: Int, val selEnd: Int)

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var lastEditTime = 0L
    private var forceNewGroup = true

    // --- state carried from the TextWatcher callbacks to afterTextChanged -------------------

    private var changeStart = 0
    private var changeEnd = 0
    private var newlineAt = -1

    // --- find --------------------------------------------------------------------------------

    private var findQuery = ""
    private var findMatches: List<IntRange> = emptyList()
    private var findIndex = -1

    /** Empty runs planted at the cursor to style what is typed next; dropped once the cursor leaves. */
    private val pendingSpans = ArrayList<Any>()

    // --- checkbox taps -----------------------------------------------------------------------

    private var checkboxTarget = -1
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        isVerticalScrollBarEnabled = true
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        setHorizontallyScrolling(false)
        setLineSpacing(0f, 1.3f)
        isFocusableInTouchMode = true
        isSaveEnabled = false
        addTextChangedListener(watcher())
        internal { setText(SpannableCodec.toSpannable(RichDoc.EMPTY, style)) }
        ready = true
    }

    // ======================================================================================
    // Public API
    // ======================================================================================

    /** Replaces the content with [doc] and clears the undo history. */
    fun loadDocument(doc: RichDoc) {
        quiet = true
        internal {
            setText(SpannableCodec.toSpannable(doc, style))
            renumber()
        }
        undoStack.clear()
        redoStack.clear()
        forceNewGroup = true
        findMatches = emptyList(); findIndex = -1
        setSelection(0)
        scrollTo(0, 0)
        quiet = false
        notifyHistory()
        notifyFormat()
        if (findQuery.isNotEmpty()) recomputeFind(keepIndex = false)
    }

    /** The current content as a storable document. */
    fun toDocument(): RichDoc = SpannableCodec.fromSpannable(text)

    val contentLength: Int get() = max(0, text.length - 1)

    private var appliedSizeSp = -1f
    private var appliedSerif: Boolean? = null
    private var appliedColors = 0L

    /** Applies the page appearance; cheap to call repeatedly, it only touches what changed. */
    fun applyAppearance(textColor: Int, hintColor: Int, selectionColor: Int, sizeSp: Float, serif: Boolean) {
        val colorsKey = textColor.toLong() * 31 + hintColor.toLong() * 17 + selectionColor.toLong()
        if (colorsKey != appliedColors) {
            appliedColors = colorsKey
            setTextColor(textColor)
            setHintTextColor(hintColor)
            highlightColor = selectionColor
        }
        if (sizeSp != appliedSizeSp) {
            appliedSizeSp = sizeSp
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        }
        if (serif != appliedSerif) {
            appliedSerif = serif
            typeface = if (serif) Typeface.SERIF else Typeface.SANS_SERIF
        }
        invalidate()
    }

    /** Moves the cursor to [offset] (a document offset) and scrolls it into view. */
    fun goTo(offset: Int) {
        setSelection(offset.coerceIn(0, contentLength))
        scrollToOffset(offset)
    }

    // --- inline formatting --------------------------------------------------------------------

    fun toggleInline(kind: InlineKind) {
        val (s, t) = selection()
        applyInline(kind, !inlineActive(kind, s, t), 0)
    }

    /** Sets (or, with [value] null, removes) a colour, highlight or relative size. */
    fun setInlineValue(kind: InlineKind, value: Int?) {
        if (value == null) applyInline(kind, false, 0) else applyInline(kind, true, value)
    }

    private fun applyInline(kind: InlineKind, on: Boolean, arg: Int) {
        beginFormatGroup()
        val (s, t) = selection()
        internal {
            if (s == t) applyCollapsed(kind, on, arg) else applyRange(kind, on, arg, s, t)
        }
        afterFormatChange()
    }

    private fun applyRange(kind: InlineKind, on: Boolean, arg: Int, s: Int, t: Int) {
        clearInline(kind, s, t)
        if (on) text.setSpan(createSpan(kind, arg), s, t, INLINE_FLAGS)
    }

    /** Removes [kind] from [s, t), keeping the parts of runs that stick out on either side. */
    private fun clearInline(kind: InlineKind, s: Int, t: Int) {
        val e = text
        for (span in spansOf(kind, s, t)) {
            val ss = e.getSpanStart(span)
            val se = e.getSpanEnd(span)
            if (se <= s || ss >= t) continue
            e.removeSpan(span)
            if (ss < s) e.setSpan(cloneSpan(kind, span), ss, s, INLINE_FLAGS)
            if (se > t) e.setSpan(cloneSpan(kind, span), t, se, INLINE_FLAGS)
        }
    }

    /**
     * With no selection the choice applies to what is typed next: runs that would grow at the cursor
     * are cut there, and an empty run that does grow is planted.
     */
    private fun applyCollapsed(kind: InlineKind, on: Boolean, arg: Int) {
        val e = text
        val c = selectionStart.coerceIn(0, contentLength)
        for (span in spansOf(kind, c, c)) {
            val ss = e.getSpanStart(span)
            val se = e.getSpanEnd(span)
            if (ss == se) {
                if (ss == c) e.removeSpan(span)
                continue
            }
            val growsHere = (ss < c && c < se) || (se == c && endInclusive(span))
            if (!growsHere) continue
            e.removeSpan(span)
            if (ss < c) e.setSpan(cloneSpan(kind, span), ss, c, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (se > c) e.setSpan(cloneSpan(kind, span), c, se, INLINE_FLAGS)
        }
        if (on) {
            val planted = createSpan(kind, arg)
            e.setSpan(planted, c, c, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            pendingSpans += planted
        }
    }

    // --- paragraph formatting -----------------------------------------------------------------

    /** Switches the selected paragraphs to [block]; choosing the current block again turns it off. */
    fun toggleBlock(block: BlockType) {
        val current = computeFormat().block
        val target = if (current == block) BlockType.NORMAL else block
        editBlocks { it.block = target; if (target != BlockType.CHECK) it.checked = false }
    }

    fun setAlign(align: Align) {
        val current = computeFormat().align
        val target = if (current == align) Align.START else align
        editBlocks { it.align = target }
    }

    fun changeIndent(delta: Int) = editBlocks { it.indent = (it.indent + delta).coerceIn(0, MAX_INDENT) }

    private fun editBlocks(change: (BlockSpan) -> Unit) {
        beginFormatGroup()
        internal {
            val e = text
            for ((ps, pe) in selectedParagraphs()) {
                var b = blockAt(ps, pe)
                if (b == null) {
                    b = BlockSpan(BlockType.NORMAL, Align.START, 0, false, style)
                    e.setSpan(b, ps, pe, BLOCK_FLAGS)
                }
                change(b)
                if (b.isDefault) e.removeSpan(b) else e.setSpan(b, ps, pe, BLOCK_FLAGS)
            }
            renumber()
        }
        invalidate()
        afterFormatChange()
    }

    /** Removes bold/italic/colours/etc. and block formatting from the selection. */
    fun clearFormatting() {
        beginFormatGroup()
        val (s, t) = selection()
        internal {
            if (s != t) for (kind in InlineKind.entries) clearInline(kind, s, t)
            val e = text
            for ((ps, pe) in selectedParagraphs()) blockAt(ps, pe)?.let { e.removeSpan(it) }
            renumber()
        }
        afterFormatChange()
    }

    // --- editing helpers ----------------------------------------------------------------------

    /** Inserts [s] at the cursor, replacing the selection (used by dictation). */
    fun insertAtCursor(s: String) {
        val (a, b) = selection()
        text.replace(a, b, s)
    }

    fun selectAllContent() = setSelection(0, contentLength)

    // --- undo / redo --------------------------------------------------------------------------

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(capture())
        restore(undoStack.removeLast())
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(capture())
        restore(redoStack.removeLast())
    }

    private fun capture() = Snapshot(toDocument(), selectionStart, selectionEnd)

    private fun restore(s: Snapshot) {
        quiet = true
        internal {
            setText(SpannableCodec.toSpannable(s.doc, style))
            renumber()
        }
        val len = contentLength
        setSelection(min(max(0, s.selStart), len), min(max(0, s.selEnd), len))
        quiet = false
        forceNewGroup = true
        notifyHistory()
        notifyChanged()
        notifyFormat()
        if (findQuery.isNotEmpty()) recomputeFind(keepIndex = false)
        scrollToOffset(selectionEnd)
    }

    private fun pushUndo() {
        undoStack.addLast(capture())
        redoStack.clear()
        val limit = if (contentLength > 300_000) 20 else 100
        while (undoStack.size > limit) undoStack.removeFirst()
        notifyHistory()
    }

    /** Called before a formatting change: it becomes its own undo step. */
    private fun beginFormatGroup() {
        pushUndo()
        lastEditTime = 0L
        forceNewGroup = true
    }

    // --- find / replace -----------------------------------------------------------------------

    fun find(query: String) {
        findQuery = query
        recomputeFind(keepIndex = false)
        if (findMatches.isNotEmpty()) {
            val from = selectionStart
            val first = findMatches.indexOfFirst { it.first >= from }.let { if (it < 0) 0 else it }
            goToMatch(first)
        }
    }

    fun findNext() {
        if (findMatches.isEmpty()) return
        goToMatch((findIndex + 1) % findMatches.size)
    }

    fun findPrevious() {
        if (findMatches.isEmpty()) return
        goToMatch((findIndex - 1 + findMatches.size) % findMatches.size)
    }

    fun replaceCurrent(replacement: String) {
        if (findIndex !in findMatches.indices) return
        val m = findMatches[findIndex]
        val keep = findIndex
        text.replace(m.first, m.last + 1, replacement)
        // afterTextChanged recomputed the matches; stay at the same ordinal, which is now the next one.
        if (findMatches.isNotEmpty()) goToMatch(min(keep, findMatches.size - 1))
    }

    fun replaceAll(replacement: String): Int {
        if (findMatches.isEmpty()) return 0
        beginFormatGroup()
        val matches = findMatches
        internal {
            for (m in matches.asReversed()) text.replace(m.first, m.last + 1, replacement)
            normalizeParagraphs(text, 0, text.length)
            renumber()
        }
        recomputeFind(keepIndex = false)
        notifyChanged()
        notifyFormat()
        return matches.size
    }

    fun clearFind() {
        findQuery = ""
        recomputeFind(keepIndex = false)
    }

    private fun recomputeFind(keepIndex: Boolean) {
        val e = text
        internal {
            for (s in e.getSpans(0, e.length, FindSpan::class.java)) e.removeSpan(s)
            for (s in e.getSpans(0, e.length, FindCurrentSpan::class.java)) e.removeSpan(s)
        }
        val matches = ArrayList<IntRange>()
        if (findQuery.isNotBlank()) {
            val hay = SpanishText.foldForSearch(e.subSequence(0, contentLength).toString())
            val needle = SpanishText.foldForSearch(findQuery)
            var from = 0
            while (matches.size < MAX_FIND_MATCHES) {
                val i = hay.indexOf(needle, from)
                if (i < 0) break
                matches += i until i + needle.length
                from = i + max(1, needle.length)
            }
        }
        findMatches = matches
        findIndex = if (matches.isEmpty()) -1 else if (keepIndex) findIndex.coerceIn(0, matches.size - 1) else 0
        internal {
            for (m in matches) e.setSpan(FindSpan(style.findColor), m.first, m.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            markCurrentMatch()
        }
        listener?.onFindChanged(matches.size, if (findIndex >= 0) findIndex + 1 else 0)
    }

    private fun goToMatch(i: Int) {
        findIndex = i
        internal { markCurrentMatch() }
        val m = findMatches[i]
        scrollToOffset(m.first)
        listener?.onFindChanged(findMatches.size, i + 1)
    }

    private fun markCurrentMatch() {
        val e = text
        for (s in e.getSpans(0, e.length, FindCurrentSpan::class.java)) e.removeSpan(s)
        if (findIndex in findMatches.indices) {
            val m = findMatches[findIndex]
            e.setSpan(FindCurrentSpan(style.findCurrentColor), m.first, m.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun scrollToOffset(offset: Int) {
        val l = layout ?: return
        val line = l.getLineForOffset(offset.coerceIn(0, text.length))
        val maxScroll = max(0, l.height + totalPaddingTop + totalPaddingBottom - height)
        scrollTo(0, (l.getLineTop(line) - height / 3).coerceIn(0, maxScroll))
    }

    // ======================================================================================
    // Text change handling
    // ======================================================================================

    private inline fun internal(block: () -> Unit) {
        internalDepth++
        try {
            block()
        } finally {
            internalDepth--
        }
    }

    private val inInternal get() = internalDepth > 0

    private fun watcher() = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            if (!ready || inInternal || quiet) return
            val now = SystemClock.uptimeMillis()
            if (forceNewGroup || now - lastEditTime > TYPING_GROUP_MS) {
                pushUndo()
                forceNewGroup = false
            }
            lastEditTime = now
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (!ready || inInternal || quiet) return
            changeStart = start
            changeEnd = start + count
            newlineAt = if (before == 0 && count == 1 && start < s.length && s[start] == '\n') start else -1
        }

        override fun afterTextChanged(e: Editable) {
            if (!ready || inInternal || quiet) return
            internal {
                if (e.isEmpty() || e[e.length - 1] != END_MARKER) e.append(END_MARKER)
                normalizeParagraphs(e, changeStart, changeEnd)
                if (newlineAt >= 0) handleEnter(e, newlineAt)
                newlineAt = -1
                renumber()
                val ps = paraStart(e, min(changeStart, e.length))
                val pe = paraEndIncl(e, min(changeEnd, e.length))
                SpannableCodec.markBrackets(e, style, ps, pe)
                if (findQuery.isNotEmpty()) recomputeFind(keepIndex = true)
            }
            notifyChanged()
            notifyFormat()
        }
    }

    /** Makes each paragraph in the touched range carry at most one [BlockSpan] that fits it exactly. */
    private fun normalizeParagraphs(e: Editable, from: Int, to: Int) {
        val total = e.length
        var lo = max(0, from - 1)
        var hi = min(total, to + 1)
        for (s in e.getSpans(lo, hi, BlockSpan::class.java)) {
            lo = min(lo, e.getSpanStart(s))
            hi = max(hi, e.getSpanEnd(s))
        }
        var pos = paraStart(e, lo)
        val stop = paraEndIncl(e, min(hi, total))
        var guard = 0
        while (pos < total && guard++ < MAX_PARAGRAPH_SCAN) {
            val end = paraEndIncl(e, pos)
            fitParagraph(e, pos, end)
            if (end >= stop || end >= total) break
            pos = end
        }
    }

    private fun fitParagraph(e: Editable, ps: Int, pe: Int) {
        val spans = e.getSpans(ps, pe, BlockSpan::class.java)
        if (spans.isEmpty()) return
        var keep = spans[0]
        for (s in spans) if (e.getSpanStart(s) < e.getSpanStart(keep)) keep = s
        for (s in spans) if (s !== keep) e.removeSpan(s)
        val ks = e.getSpanStart(keep)
        val ke = e.getSpanEnd(keep)
        // A run that stretches over several paragraphs (an inserted newline) passes its formatting on.
        if (ke > pe) e.setSpan(keep.copyAttributes(), pe, ke, BLOCK_FLAGS)
        if (keep.isDefault) {
            e.removeSpan(keep)
        } else if (ks != ps || ke != pe) {
            e.setSpan(keep, ps, pe, BLOCK_FLAGS)
        }
    }

    /** After Enter: continue lists and quotes, leave them on an empty item, and reset headings. */
    private fun handleEnter(e: Editable, nl: Int) {
        if (nl < 0 || nl >= e.length || e[nl] != '\n') return
        val leftStart = paraStart(e, nl)
        val rightStart = nl + 1
        val left = blockAt(leftStart, nl + 1)
        val right = blockAt(rightStart, paraEndIncl(e, rightStart))
        val leftBlock = left?.block ?: BlockType.NORMAL
        val continues = leftBlock == BlockType.BULLET || leftBlock == BlockType.NUMBER ||
            leftBlock == BlockType.CHECK || leftBlock == BlockType.QUOTE
        when {
            continues && leftStart == nl -> {
                // Enter on an empty item ends the list; the line it was on becomes a normal paragraph.
                e.delete(nl, nl + 1)
                val ps = leftStart
                fitParagraph(e, ps, paraEndIncl(e, ps))
                blockAt(ps, paraEndIncl(e, ps))?.let {
                    it.block = BlockType.NORMAL
                    it.checked = false
                    if (it.isDefault) e.removeSpan(it) else e.setSpan(it, ps, paraEndIncl(e, ps), BLOCK_FLAGS)
                }
                setSelection(min(ps, contentLength))
            }
            continues -> right?.checked = false
            leftBlock.isHeading && contentLength(e, rightStart, paraEndIncl(e, rightStart)) == 0 && right != null -> {
                right.block = BlockType.NORMAL
                val re = paraEndIncl(e, rightStart)
                if (right.isDefault) e.removeSpan(right) else e.setSpan(right, rightStart, re, BLOCK_FLAGS)
            }
        }
    }

    /** Numbers consecutive numbered paragraphs (restarting after any other paragraph). */
    private fun renumber() {
        val e = text
        val spans = e.getSpans(0, e.length, BlockSpan::class.java)
        if (spans.none { it.block == BlockType.NUMBER }) return
        val sorted = spans.sortedBy { e.getSpanStart(it) }
        val counters = IntArray(MAX_INDENT + 1)
        var prevEnd = -1
        for (b in sorted) {
            val ps = e.getSpanStart(b)
            if (prevEnd >= 0 && ps > prevEnd) counters.fill(0)
            prevEnd = e.getSpanEnd(b)
            val level = b.indent.coerceIn(0, MAX_INDENT)
            if (b.block == BlockType.NUMBER) {
                counters[level]++
                for (j in level + 1..MAX_INDENT) counters[j] = 0
                if (b.number != counters[level]) {
                    b.number = counters[level]
                    e.setSpan(b, ps, prevEnd, BLOCK_FLAGS)
                }
            } else {
                for (j in level..MAX_INDENT) counters[j] = 0
            }
        }
    }

    // ======================================================================================
    // Paragraph helpers (offsets include the end marker)
    // ======================================================================================

    private fun paraStart(e: CharSequence, pos: Int): Int {
        var i = pos.coerceIn(0, e.length)
        while (i > 0 && e[i - 1] != '\n') i--
        return i
    }

    /** Offset just past the newline of the paragraph containing [pos] (or the end of the text). */
    private fun paraEndIncl(e: CharSequence, pos: Int): Int {
        var i = pos.coerceIn(0, e.length)
        while (i < e.length && e[i] != '\n') i++
        return if (i < e.length) i + 1 else e.length
    }

    /** Characters of the paragraph that the user can see (no newline, no end marker). */
    private fun contentLength(e: CharSequence, ps: Int, pe: Int): Int {
        var end = pe
        if (end > ps && e[end - 1] == '\n') end--
        if (end > ps && e[end - 1] == END_MARKER) end--
        return end - ps
    }

    private fun blockAt(ps: Int, pe: Int): BlockSpan? {
        val spans = text.getSpans(ps, pe, BlockSpan::class.java)
        if (spans.isEmpty()) return null
        return spans.firstOrNull { text.getSpanStart(it) == ps } ?: spans[0]
    }

    /** [start, endInclusive) pairs of the paragraphs touched by the selection. */
    private fun selectedParagraphs(): List<IntArray> {
        val e = text
        val (s, t) = selection()
        val result = ArrayList<IntArray>()
        var pos = paraStart(e, s)
        val limit = if (t > s && t > 0 && e[t - 1] == '\n') t - 1 else t
        var guard = 0
        while (guard++ < MAX_PARAGRAPH_SCAN) {
            val end = paraEndIncl(e, pos)
            result += intArrayOf(pos, end)
            if (end > limit || end >= e.length) break
            pos = end
        }
        return result
    }

    private fun selection(): Pair<Int, Int> {
        val len = contentLength
        val a = selectionStart.coerceIn(0, len)
        val b = selectionEnd.coerceIn(0, len)
        return min(a, b) to max(a, b)
    }

    // ======================================================================================
    // Inline span helpers
    // ======================================================================================

    private fun spansOf(kind: InlineKind, s: Int, t: Int): List<Any> {
        val e = text
        return when (kind) {
            InlineKind.BOLD -> e.getSpans(s, t, StyleSpan::class.java).filter { it.style == Typeface.BOLD }
            InlineKind.ITALIC -> e.getSpans(s, t, StyleSpan::class.java).filter { it.style == Typeface.ITALIC }
            InlineKind.UNDERLINE -> e.getSpans(s, t, UnderlineSpan::class.java).toList()
            InlineKind.STRIKE -> e.getSpans(s, t, StrikethroughSpan::class.java).toList()
            InlineKind.COLOR -> e.getSpans(s, t, ForegroundColorSpan::class.java).toList()
            InlineKind.HIGHLIGHT -> e.getSpans(s, t, BackgroundColorSpan::class.java)
                .filter { it !is FindSpan && it !is FindCurrentSpan }
            InlineKind.SIZE -> e.getSpans(s, t, RelativeSizeSpan::class.java).toList()
            InlineKind.STAGE -> e.getSpans(s, t, StageSpan::class.java).toList()
        }
    }

    private fun argOf(kind: InlineKind, span: Any): Int = when (kind) {
        InlineKind.COLOR -> (span as ForegroundColorSpan).foregroundColor
        InlineKind.HIGHLIGHT -> (span as BackgroundColorSpan).backgroundColor
        InlineKind.SIZE -> (span as RelativeSizeSpan).sizeChange.times(100f).roundToInt()
        else -> 0
    }

    private fun createSpan(kind: InlineKind, arg: Int): Any = when (kind) {
        InlineKind.BOLD -> StyleSpan(Typeface.BOLD)
        InlineKind.ITALIC -> StyleSpan(Typeface.ITALIC)
        InlineKind.UNDERLINE -> UnderlineSpan()
        InlineKind.STRIKE -> StrikethroughSpan()
        InlineKind.COLOR -> ForegroundColorSpan(arg)
        InlineKind.HIGHLIGHT -> BackgroundColorSpan(arg)
        InlineKind.SIZE -> RelativeSizeSpan(arg / 100f)
        InlineKind.STAGE -> StageSpan(style)
    }

    private fun cloneSpan(kind: InlineKind, span: Any): Any = createSpan(kind, argOf(kind, span))

    private fun endInclusive(span: Any): Boolean = (text.getSpanFlags(span) and 0x0F) == 0x02

    /** Whether [kind] applies to all of [s, t), or, for a cursor, to the text typed next. */
    private fun inlineActive(kind: InlineKind, s: Int, t: Int): Boolean = coveringArg(kind, s, t) != null

    /** The argument of the run that covers all of [s, t) (0 for kinds without one), or null. */
    private fun coveringArg(kind: InlineKind, s: Int, t: Int): Int? {
        val e = text
        if (s == t) {
            for (span in spansOf(kind, s, t)) {
                val ss = e.getSpanStart(span)
                val se = e.getSpanEnd(span)
                val active = (ss == se && ss == s) || (ss < s && s < se) || (ss < s && se == s && endInclusive(span))
                if (active) return argOf(kind, span)
            }
            return null
        }
        var covered = s
        var arg: Int? = null
        val sorted = spansOf(kind, s, t).sortedBy { e.getSpanStart(it) }
        for (span in sorted) {
            val ss = e.getSpanStart(span)
            val se = e.getSpanEnd(span)
            if (se <= covered) continue
            if (ss > covered) return null
            val a = argOf(kind, span)
            if (arg != null && arg != a) return null
            arg = a
            covered = se
            if (covered >= t) return arg
        }
        return null
    }

    private fun computeFormat(): FormatState {
        val e = text
        val (s, t) = selection()
        val ps = paraStart(e, s)
        val b = blockAt(ps, paraEndIncl(e, ps))
        return FormatState(
            bold = inlineActive(InlineKind.BOLD, s, t),
            italic = inlineActive(InlineKind.ITALIC, s, t),
            underline = inlineActive(InlineKind.UNDERLINE, s, t),
            strike = inlineActive(InlineKind.STRIKE, s, t),
            stage = inlineActive(InlineKind.STAGE, s, t),
            color = coveringArg(InlineKind.COLOR, s, t) ?: 0,
            highlight = coveringArg(InlineKind.HIGHLIGHT, s, t) ?: 0,
            sizePercent = coveringArg(InlineKind.SIZE, s, t) ?: 100,
            block = b?.block ?: BlockType.NORMAL,
            align = b?.align ?: Align.START,
            indent = b?.indent ?: 0,
        )
    }

    // ======================================================================================
    // Notifications
    // ======================================================================================

    private fun notifyChanged() {
        if (!quiet) listener?.onContentChanged()
    }

    private fun notifyFormat() {
        if (!quiet) listener?.onFormatChanged(computeFormat())
    }

    private fun notifyHistory() {
        listener?.onHistoryChanged(canUndo, canRedo)
    }

    private fun afterFormatChange() {
        notifyHistory()
        notifyFormat()
        notifyChanged()
    }

    // ======================================================================================
    // View overrides
    // ======================================================================================

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!ready || inInternal || quiet) return
        val e = text ?: return
        val limit = e.length - 1
        if (limit >= 0 && (selStart > limit || selEnd > limit)) {
            // The cursor may not pass the end marker.
            setSelection(min(selStart, limit).coerceAtLeast(0), min(selEnd, limit).coerceAtLeast(0))
            return
        }
        // Empty "typing style" runs left behind elsewhere would capture text typed there later.
        if (pendingSpans.isNotEmpty()) {
            internal {
                val it = pendingSpans.iterator()
                while (it.hasNext()) {
                    val span = it.next()
                    val ss = e.getSpanStart(span)
                    when {
                        ss < 0 -> it.remove()
                        ss != e.getSpanEnd(span) -> it.remove() // it grew: text was typed with that style
                        ss != selStart -> { e.removeSpan(span); it.remove() }
                    }
                }
            }
        }
        notifyFormat()
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        // Pasted text brings foreign styling that would not survive saving; always paste plain text.
        if (id == android.R.id.paste) return super.onTextContextMenuItem(android.R.id.pasteAsPlainText)
        return super.onTextContextMenuItem(id)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleKey(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (handleKey(keyCode, event)) return true
        return super.onKeyShortcut(keyCode, event)
    }

    private fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed || event.isMetaPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_B -> { toggleInline(InlineKind.BOLD); return true }
                KeyEvent.KEYCODE_I -> { toggleInline(InlineKind.ITALIC); return true }
                KeyEvent.KEYCODE_U -> { toggleInline(InlineKind.UNDERLINE); return true }
                KeyEvent.KEYCODE_Z -> { if (event.isShiftPressed) redo() else undo(); return true }
                KeyEvent.KEYCODE_Y -> { redo(); return true }
                KeyEvent.KEYCODE_F -> { listener?.onFindShortcut(); return true }
                KeyEvent.KEYCODE_A -> { selectAllContent(); return true }
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_TAB -> {
                val (s, _) = selection()
                val ps = paraStart(text, s)
                val b = blockAt(ps, paraEndIncl(text, ps))
                if (b != null && (b.block.isList)) {
                    changeIndent(if (event.isShiftPressed) -1 else 1)
                } else {
                    insertAtCursor("    ")
                }
                return true
            }
            KeyEvent.KEYCODE_DEL -> if (event.action == KeyEvent.ACTION_DOWN && backspaceAtBlockStart()) return true
        }
        return false
    }

    /**
     * Backspace at the very start of a list item, quote, heading or indented paragraph removes that
     * formatting instead of joining the line to the previous one.
     */
    private fun backspaceAtBlockStart(): Boolean {
        if (selectionStart != selectionEnd) return false
        val e = text
        val ps = paraStart(e, selectionStart)
        if (selectionStart != ps) return false
        val b = blockAt(ps, paraEndIncl(e, ps)) ?: return false
        if (b.block == BlockType.NORMAL && b.indent == 0) return false
        beginFormatGroup()
        internal {
            if (b.block != BlockType.NORMAL) {
                b.block = BlockType.NORMAL
                b.checked = false
            } else {
                b.indent = max(0, b.indent - 1)
            }
            val pe = paraEndIncl(e, ps)
            if (b.isDefault) e.removeSpan(b) else e.setSpan(b, ps, pe, BLOCK_FLAGS)
            renumber()
        }
        invalidate()
        afterFormatChange()
        return true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, true) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength == 1 && afterLength == 0 && backspaceAtBlockStart()) return true
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN && backspaceAtBlockStart()) {
                    return true
                }
                return super.sendKeyEvent(event)
            }
        }
    }

    // --- check boxes ----------------------------------------------------------------------------

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                checkboxTarget = checkboxAt(ev.x, ev.y)
                if (checkboxTarget >= 0) return true
            }
            MotionEvent.ACTION_MOVE -> if (checkboxTarget >= 0) {
                if (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop) checkboxTarget = -1
                return true
            }
            MotionEvent.ACTION_UP -> if (checkboxTarget >= 0) {
                val ps = checkboxTarget
                checkboxTarget = -1
                toggleCheck(ps)
                return true
            }
            MotionEvent.ACTION_CANCEL -> checkboxTarget = -1
        }
        return super.onTouchEvent(ev)
    }

    /** Paragraph start of the check box under ([x], [y]), or -1. */
    private fun checkboxAt(x: Float, y: Float): Int {
        val l = layout ?: return -1
        val line = l.getLineForVertical((y - totalPaddingTop + scrollY).toInt())
        val offset = l.getLineStart(line)
        val e = text
        if (offset != paraStart(e, offset)) return -1
        val b = blockAt(offset, paraEndIncl(e, offset)) ?: return -1
        if (b.block != BlockType.CHECK || b.align != Align.START) return -1
        val rel = x - compoundPaddingLeft + scrollX
        return if (rel in b.checkboxBounds()) offset else -1
    }

    private fun toggleCheck(ps: Int) {
        val pe = paraEndIncl(text, ps)
        val b = blockAt(ps, pe) ?: return
        beginFormatGroup()
        internal {
            b.checked = !b.checked
            text.setSpan(b, ps, pe, BLOCK_FLAGS)
        }
        invalidate()
        afterFormatChange()
    }

    private companion object {
        const val TYPING_GROUP_MS = 900L
        const val MAX_INDENT = 6
        const val MAX_FIND_MATCHES = 5000
        const val MAX_PARAGRAPH_SCAN = 200_000
    }
}
