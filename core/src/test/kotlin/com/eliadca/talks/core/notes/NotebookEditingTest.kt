package com.eliadca.talks.core.notes

import com.eliadca.talks.core.ink.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class NotebookEditingTest {
    @Test fun formattingSurvivesTypingReplacingAndRemovingASelection() {
        val bold = NoteFormatting().apply(0, 5) { it.copy(bold = true, color = 0xff1565c0.toInt()) }
        val typed = bold.replace("Hello world", "Hello! world")
        assertTrue(typed.styleAt(5).bold)
        assertFalse(typed.styleAt(7).bold)
        val replaced = typed.replace("Hello! world", "Hi world")
        assertTrue(replaced.styleAt(1).bold)
        assertFalse(replaced.styleAt(3).bold)
        val removed = replaced.replace("Hi world", "world")
        assertTrue(removed.spans.isEmpty())
    }
    @Test fun mixedStylesAndParagraphAlignmentKeepTheirRangesAcrossAnInsertion() {
        val original = "Primera\nSegunda"
        val format = NoteFormatting().apply(0, 7) { it.copy(bold = true) }.apply(8, 15) { it.copy(italic = true) }
            .align(original, 8, 15, 2)
        val updated = format.replace(original, "Título\n" + original, NoteTextStyle())
        assertFalse(updated.styleAt(0).bold)
        assertTrue(updated.styleAt(7).bold)
        assertTrue(updated.styleAt(15).italic)
        assertEquals(NoteParagraph(15, 22, 2), updated.paragraphs.single())
    }
    @Test fun crossingASelectionFindsAFastStrokeEvenWhenItsVerticesAreOutside() {
        val doc = InkDocument().addStroke(PenKind.PENCIL, 1, 5f, listOf(InkPoint(0f, 100f), InkPoint(900f, 100f)))
        assertEquals(setOf(1L), doc.selectRectangle(InkBounds(300f, 80f, 500f, 120f)))
        val moved = doc.transformSelection(setOf(1), 50f, 200f)
        assertEquals(InkPoint(50f, 300f), moved.strokes.single().points.first())
    }
    @Test fun groupMovementAndResizingRetainPressureAndLeaveOtherStrokesUntouched() {
        val original = InkDocument().addStroke(PenKind.FOUNTAIN, 12, 5f, listOf(InkPoint(100f, 100f, .2f), InkPoint(300f, 200f, .8f)))
            .addStroke(PenKind.HIGHLIGHTER, 13, 10f, listOf(InkPoint(400f, 400f)))
        val moved = original.transformSelection(setOf(1L), 150f, 1600f, 2f)
        assertEquals(InkPoint(250f, 1700f, .2f), moved.strokes.first().points.first())
        assertEquals(InkPoint(650f, 1900f, .8f), moved.strokes.first().points.last())
        assertEquals(original.strokes.last(), moved.strokes.last())
        assertEquals(2000f, moved.height)
        val copy = moved.duplicateSelection(setOf(1L))
        assertEquals(listOf(1L, 2L, 3L), copy.strokes.map { it.id })
        assertEquals(moved.strokes.first(), copy.strokes.first())
    }
    @Test fun movingAndResizingATextBoxSurvivesSerializationAndPageResizingCannotCropIt() {
        val doc = InkDocument().addTextBox("Una idea que puedo reubicar")
        val moved = doc.transformSelection(setOf(1L), 200f, 400f, 1.4f)
        val read = Json.decodeFromString<InkDocument>(Json.encodeToString(moved))
        assertEquals(moved, read)
        assertEquals(64.4f, read.textBoxes.single().size, .01f)
        val smaller = read.resizePage(600f, 1000f)
        assertTrue(smaller.pageWidth >= read.textBoxes.single().x + read.textBoxes.single().width)
        assertEquals(1000f, Json.decodeFromString<InkDocument>("{}").pageWidth)
    }
}
