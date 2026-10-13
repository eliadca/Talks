package com.eliadca.talks.core.ink

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class InkDocumentTest {
    @Test fun serializationRetainsPressureColorsAndPartialPageLines() {
        val page = InkDocument().addStroke(PenKind.FOUNTAIN, 0xff123456.toInt(), 8f,
            listOf(InkPoint(23f, 56f, .15f), InkPoint(90f, 1800f, .9f)))
            .addArea(InkPoint(700f, 600f), InkPoint(80f, 200f))
        assertEquals(page, Json.decodeFromString<InkDocument>(Json.encodeToString(page)))
        assertTrue(page.height >= 1900f)
        assertEquals(80f, page.areas.single().left)
        assertEquals(InkDocument(), Json.decodeFromString<InkDocument>("{}"))
    }

    @Test fun eraserCrossesLongSegmentsEvenIfNoSavedPointIsNearTheEraser() {
        val page = InkDocument().addStroke(PenKind.BALLPOINT, 0, 3f,
            listOf(InkPoint(100f, 300f), InkPoint(800f, 300f)))
            .addStroke(PenKind.PENCIL, 0, 3f, listOf(InkPoint(20f, 900f)))
        assertEquals(setOf(1L), page.strokesHit(InkPoint(400f, 200f), InkPoint(400f, 400f), 10f))
        assertTrue(page.strokesHit(InkPoint(900f, 400f), InkPoint(950f, 500f), 10f).isEmpty())
        assertEquals(setOf(2L), page.strokesHit(InkPoint(20f, 910f), InkPoint(20f, 910f), 10f))
    }

    @Test fun undoRedoGroupsGesturesAndANewEditReplacesTheRedoBranch() {
        val history = InkHistory()
        val first = InkDocument().addStroke(PenKind.PENCIL, 0, 5f, listOf(InkPoint(10f, 20f)))
        history.record(first)
        history.record(first.copy(wholePageRuled = true))
        assertEquals(first, history.undo()); assertTrue(history.canRedo)
        assertTrue(history.redo().wholePageRuled)
        history.undo(); history.record(first.copy(spacing = 80f))
        assertFalse(history.canRedo)
        assertEquals(first, history.undo()); assertEquals(InkDocument(), history.undo())
        assertFalse(history.canUndo)
    }

    @Test fun malformedInputAndAccidentalTinyRuleSelectionsDoNotAddObjects() {
        assertEquals(InkDocument(), InkDocument().addStroke(PenKind.PENCIL, 0, 4f, listOf(InkPoint(Float.NaN, 0f))))
        assertEquals(InkDocument(), InkDocument().addArea(InkPoint(0f, 0f), InkPoint(10f, 10f)))
        val dot = InkDocument().addStroke(PenKind.BALLPOINT, 0, -2f, listOf(InkPoint(-10f, -5f, 4f))).strokes.single()
        assertEquals(1f, dot.width); assertEquals(InkPoint(0f, 0f, 1f), dot.points.single())
    }
}
