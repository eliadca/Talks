package com.eliadca.talks.editor

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.eliadca.talks.core.doc.Align
import com.eliadca.talks.core.doc.BlockType
import com.eliadca.talks.core.doc.Markup
import com.eliadca.talks.core.doc.RichDoc
import com.eliadca.talks.core.doc.RichSpan
import com.eliadca.talks.core.doc.SpanType
import com.eliadca.talks.core.doc.normalized
import com.eliadca.talks.core.doc.paragraphs
import com.eliadca.talks.core.sample.SampleContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RichEditTextTest {

    private lateinit var edit: RichEditText
    private val style = EditorStyle(1f, 0xFF3F4FD8.toInt(), 0xFF888888.toInt())

    /** Remembers what the editor last told its host. */
    private class Recorder : EditorListener {
        var format = FormatState()
        var findTotal = -1
        override fun onContentChanged() {}
        override fun onFormatChanged(state: FormatState) { format = state }
        override fun onHistoryChanged(canUndo: Boolean, canRedo: Boolean) {}
        override fun onFindChanged(total: Int, current: Int) { findTotal = total }
        override fun onFindShortcut() {}
    }

    private val recorder = Recorder()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        edit = RichEditText(context, style)
        edit.listener = recorder
    }

    /** Types like a keyboard: inserts at the cursor, one chunk at a time. */
    private fun type(s: String) {
        for (c in s) {
            val at = edit.selectionStart.coerceAtLeast(0)
            edit.text.insert(at, c.toString())
        }
    }

    private fun doc() = edit.toDocument()

    private fun blocks() = doc().paragraphs().map { it.block }

    // --- basics ------------------------------------------------------------------------------

    @Test fun typedTextBecomesTheDocumentWithoutTheEndMarker() {
        type("Hola mundo")
        assertEquals("Hola mundo", doc().text)
        assertEquals(10, edit.contentLength)
    }

    @Test fun cursorCannotPassTheEndMarker() {
        type("abc")
        edit.setSelection(edit.text.length)
        assertTrue(edit.selectionEnd <= edit.contentLength)
    }

    @Test fun loadingADocumentRoundTripsIncludingAllFormatting() {
        for (source in listOf(SampleContent.practice, SampleContent.welcome)) {
            edit.loadDocument(source)
            assertEquals(source.text, doc().text)
            assertEquals(source.spans.sortedBy { it.start * 100000L + it.end }, doc().spans.sortedBy { it.start * 100000L + it.end })
        }
    }

    @Test fun everySpanKindSurvivesARoundTrip() {
        val original = RichDoc(
            "uno dos tres\ncuatro\ncinco\n\nseis",
            listOf(
                RichSpan(SpanType.BOLD, 0, 3), RichSpan(SpanType.ITALIC, 4, 7), RichSpan(SpanType.UNDERLINE, 8, 12),
                RichSpan(SpanType.STRIKE, 13, 15), RichSpan(SpanType.COLOR, 0, 12, 0xFFE53935.toInt()),
                RichSpan(SpanType.HIGHLIGHT, 4, 12, 0x80FFEB3B.toInt()), RichSpan(SpanType.SIZE, 8, 12, 150),
                RichSpan(SpanType.STAGE, 13, 17),
                RichSpan(SpanType.H2, 0, 12), RichSpan(SpanType.BULLET, 13, 19), RichSpan(SpanType.CHECK, 20, 25, 1),
                RichSpan(SpanType.ALIGN_CENTER, 13, 19), RichSpan(SpanType.INDENT, 13, 19, 2),
                RichSpan(SpanType.QUOTE, 27, 31),
            ),
        ).normalized()
        edit.loadDocument(original)
        assertEquals(original, doc())
    }

    // --- inline formatting ---------------------------------------------------------------------

    @Test fun boldOnASelectionAndBackOff() {
        type("Hola mundo")
        edit.setSelection(0, 4)
        edit.toggleInline(InlineKind.BOLD)
        assertEquals(listOf(RichSpan(SpanType.BOLD, 0, 4)), doc().spans)
        edit.toggleInline(InlineKind.BOLD)
        assertTrue(doc().spans.isEmpty())
    }

    @Test fun boldAppliedToPartOfABoldRunSplitsIt() {
        type("abcdef")
        edit.setSelection(0, 6)
        edit.toggleInline(InlineKind.BOLD)
        edit.setSelection(2, 4)
        edit.toggleInline(InlineKind.BOLD)
        assertEquals(listOf(RichSpan(SpanType.BOLD, 0, 2), RichSpan(SpanType.BOLD, 4, 6)), doc().spans.sortedBy { it.start })
    }

    @Test fun turningBoldOnAtTheCursorStylesWhatIsTypedNext() {
        type("ab")
        edit.toggleInline(InlineKind.BOLD)
        type("cd")
        assertEquals(listOf(RichSpan(SpanType.BOLD, 2, 4)), doc().spans)
        assertEquals("abcd", doc().text)
    }

    @Test fun continuingBoldAtTheEndOfABoldWordAndTurningItOff() {
        type("ab")
        edit.setSelection(0, 2)
        edit.toggleInline(InlineKind.BOLD)
        edit.setSelection(2)
        type("c") // typed at the end of the bold run: stays bold
        assertEquals(listOf(RichSpan(SpanType.BOLD, 0, 3)), doc().spans)
        edit.toggleInline(InlineKind.BOLD) // switch off at the cursor
        type("d")
        assertEquals(listOf(RichSpan(SpanType.BOLD, 0, 3)), doc().spans)
        assertEquals("abcd", doc().text)
    }

    @Test fun differentColourReplacesTheOldOne() {
        type("palabra")
        edit.setSelection(0, 7)
        edit.setInlineValue(InlineKind.COLOR, 0xFFE53935.toInt())
        edit.setSelection(2, 4)
        edit.setInlineValue(InlineKind.COLOR, 0xFF1E88E5.toInt())
        val colours = doc().spans.filter { it.type == SpanType.COLOR }.sortedBy { it.start }
        assertEquals(listOf(0 to 2, 2 to 4, 4 to 7), colours.map { it.start to it.end })
        assertEquals(listOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFFE53935.toInt()), colours.map { it.arg })
        edit.setSelection(0, 7)
        edit.setInlineValue(InlineKind.COLOR, null)
        assertTrue(doc().spans.none { it.type == SpanType.COLOR })
    }

    @Test fun formatStateFollowsTheSelection() {
        type("ab cd")
        edit.setSelection(0, 2)
        edit.toggleInline(InlineKind.ITALIC)
        edit.setSelection(0, 2)
        assertTrue(recorder.format.italic)
        edit.setSelection(3, 5)
        assertFalse(recorder.format.italic)
    }

    // --- paragraphs ----------------------------------------------------------------------------

    @Test fun enterInABulletListContinuesIt() {
        type("uno")
        edit.toggleBlock(BlockType.BULLET)
        type("\n")
        type("dos")
        assertEquals("uno\ndos", doc().text)
        assertEquals(listOf(BlockType.BULLET, BlockType.BULLET), blocks())
    }

    @Test fun enterOnAnEmptyBulletLeavesTheList() {
        type("uno")
        edit.toggleBlock(BlockType.BULLET)
        type("\n")
        assertEquals(listOf(BlockType.BULLET, BlockType.BULLET), blocks())
        type("\n")
        assertEquals("uno\n", doc().text)
        assertEquals(listOf(BlockType.BULLET, BlockType.NORMAL), blocks())
        type("texto")
        assertEquals("uno\ntexto", doc().text)
    }

    @Test fun enterInTheMiddleOfAListItemSplitsIt() {
        type("unodos")
        edit.toggleBlock(BlockType.BULLET)
        edit.setSelection(3)
        type("\n")
        assertEquals("uno\ndos", doc().text)
        assertEquals(listOf(BlockType.BULLET, BlockType.BULLET), blocks())
    }

    @Test fun numberedItemsAreCountedAndRestartAfterAGap() {
        type("a")
        edit.toggleBlock(BlockType.NUMBER)
        type("\nb\nc")
        assertEquals(listOf(BlockType.NUMBER, BlockType.NUMBER, BlockType.NUMBER), blocks())
        assertEquals(listOf(1, 2, 3), edit.numbersForTest())
        // Take the middle item out of the list: numbering restarts below it.
        edit.setSelection(2)
        edit.toggleBlock(BlockType.NUMBER)
        assertEquals(listOf(BlockType.NUMBER, BlockType.NORMAL, BlockType.NUMBER), blocks())
        assertEquals(listOf(1, 1), edit.numbersForTest())
    }

    @Test fun enterAtTheEndOfAHeadingStartsNormalText() {
        type("Título")
        edit.toggleBlock(BlockType.H1)
        type("\n")
        type("cuerpo")
        assertEquals(listOf(BlockType.H1, BlockType.NORMAL), blocks())
    }

    @Test fun alignmentAndIndentApplyToTheParagraph() {
        type("uno\ndos")
        edit.setSelection(5)
        edit.setAlign(Align.CENTER)
        edit.changeIndent(1)
        val p = doc().paragraphs()
        assertEquals(Align.START, p[0].align)
        assertEquals(Align.CENTER, p[1].align)
        assertEquals(1, p[1].indent)
        edit.setAlign(Align.CENTER) // toggles off
        assertEquals(Align.START, doc().paragraphs()[1].align)
    }

    @Test fun backspaceAtTheStartOfABulletRemovesTheBulletInsteadOfJoiningLines() {
        type("uno")
        edit.toggleBlock(BlockType.BULLET)
        type("\ndos")
        edit.setSelection(4) // start of "dos"
        edit.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        assertEquals("uno\ndos", doc().text)
        assertEquals(listOf(BlockType.BULLET, BlockType.NORMAL), blocks())
        // A second backspace now joins the lines as usual.
        edit.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        assertEquals("unodos", doc().text)
    }

    @Test fun mergingParagraphsKeepsTheUpperParagraphFormatting() {
        edit.loadDocument(Markup.parse("# Titulo\ncuerpo"))
        edit.text.delete(6, 7) // the newline after "Titulo"
        assertEquals("Titulocuerpo", doc().text)
        assertEquals(listOf(BlockType.H1), blocks())
    }

    @Test fun checkListItemsKeepTheirTickedState() {
        edit.loadDocument(Markup.parse("- [x] hecho\n- [ ] pendiente"))
        val p = doc().paragraphs()
        assertTrue(p[0].checked)
        assertFalse(p[1].checked)
        edit.setSelection(edit.contentLength)
        type("\nnuevo")
        val after = doc().paragraphs()
        assertEquals(BlockType.CHECK, after[2].block)
        assertFalse(after[2].checked)
    }

    @Test fun bracketedNotesAreMarkedButNotStored() {
        type("Digo esto [pausa] y sigo")
        assertTrue(doc().spans.isEmpty())
        val marked = edit.text.getSpans(0, edit.text.length, AutoNoteSpan::class.java)
        assertEquals(1, marked.size)
        assertEquals("[pausa]", edit.text.substring(edit.text.getSpanStart(marked[0]), edit.text.getSpanEnd(marked[0])))
    }

    // --- undo / redo ---------------------------------------------------------------------------

    @Test fun undoAndRedoRestoreTextAndFormatting() {
        type("uno")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3))
        type(" dos")
        assertEquals("uno dos", doc().text)
        edit.undo()
        assertEquals("uno", doc().text)
        edit.redo()
        assertEquals("uno dos", doc().text)

        edit.setSelection(0, 3)
        edit.toggleInline(InlineKind.BOLD)
        assertEquals(1, doc().spans.size)
        edit.undo()
        assertTrue(doc().spans.isEmpty())
        assertEquals("uno dos", doc().text)
        edit.redo()
        assertEquals(1, doc().spans.size)
    }

    @Test fun fastTypingIsOneUndoStep() {
        type("hola")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3))
        type(" a todos")
        edit.undo()
        assertEquals("hola", doc().text)
        edit.undo()
        assertEquals("", doc().text)
        assertFalse(edit.canUndo)
    }

    // --- find / replace ------------------------------------------------------------------------

    @Test fun findIgnoresCaseAndAccents() {
        type("Canción y cancion y CANCIÓN")
        edit.find("cancion")
        assertEquals(3, recorder.findTotal)
    }

    @Test fun replaceAllChangesEveryMatchAndKeepsFormatting() {
        edit.loadDocument(Markup.parse("**Hola** mundo, hola Mundo"))
        edit.find("hola")
        assertEquals(2, edit.replaceAll("adiós"))
        assertEquals("adiós mundo, adiós Mundo", doc().text)
        assertTrue(doc().spans.any { it.type == SpanType.BOLD && it.start == 0 })
    }

    @Test fun replaceCurrentMovesOn() {
        type("a b a b a")
        edit.find("a")
        assertEquals(3, recorder.findTotal)
        edit.replaceCurrent("X")
        assertEquals("X b a b a", doc().text)
        assertEquals(2, recorder.findTotal)
    }

    @Test fun findMatchesAreNotPartOfTheStoredDocument() {
        type("hola hola")
        edit.find("hola")
        assertTrue(doc().spans.isEmpty())
        edit.clearFind()
        assertEquals(0, edit.text.getSpans(0, edit.text.length, FindSpan::class.java).size)
    }

    // --- misc ----------------------------------------------------------------------------------

    @Test fun clearFormattingRemovesInlineAndBlockStyles() {
        edit.loadDocument(Markup.parse("# **Hola** *mundo*"))
        edit.setSelection(0, edit.contentLength)
        edit.clearFormatting()
        assertTrue(doc().spans.isEmpty())
    }

    @Test fun emptyDocumentHasOneEmptyParagraph() {
        assertEquals("", doc().text)
        assertEquals(1, doc().paragraphs().size)
    }

    @Test fun selectingAllAndDeletingKeepsTheEditorUsable() {
        type("abc\ndef")
        edit.selectAllContent()
        edit.text.delete(0, edit.text.length)
        assertEquals("", doc().text)
        type("x")
        assertEquals("x", doc().text)
    }
}

private fun RichEditText.numbersForTest(): List<Int> {
    val spans = text.getSpans(0, text.length, BlockSpan::class.java)
    return spans.sortedBy { text.getSpanStart(it) }.filter { it.block == BlockType.NUMBER }.map { it.number }
}
