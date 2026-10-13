package com.eliadca.talks.core.ink

import org.junit.Assert.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class VectorNoteToolsTest {
    private fun document()=InkDocument().addStroke(PenKind.PENCIL,7,4f,listOf(InkPoint(100f,100f,.2f),InkPoint(200f,180f,.8f),InkPoint(300f,100f,.5f)))
        .addStroke(PenKind.BALLPOINT,9,6f,listOf(InkPoint(500f,100f),InkPoint(800f,100f)))
    @Test fun closedShapesAndArrowHaveCorrectBoundsAndFinitePoints() {
        for(shape in NoteShape.entries) {
            val ps=shapePoints(shape,InkPoint(80f,100f),InkPoint(300f,400f))
            assertTrue(ps.all {it.x.isFinite() && it.y.isFinite()})
            if(shape in listOf(NoteShape.RECTANGLE,NoteShape.TRIANGLE,NoteShape.STAR))assertEquals(ps.first(),ps.last())
        }
        assertTrue(shapePoints(NoteShape.LINE,InkPoint(Float.NaN,0f),InkPoint(1f,1f)).isEmpty())
    }
    @Test fun lockedOrHiddenObjectsCannotBeSelectedTransformedStyledOrErased() {
        val doc=document().copy(lockedIds=setOf(1L),hiddenIds=setOf(2L))
        assertTrue(doc.selectRectangle(InkBounds(0f,0f,1000f,1000f)).isEmpty())
        assertTrue(doc.strokesHit(InkPoint(0f,100f),InkPoint(1000f,100f),100f).isEmpty())
        assertEquals(doc,doc.erasePart(InkPoint(0f,100f),InkPoint(1000f,100f),100f))
        assertEquals(doc,doc.styleSelection(setOf(1L,2L),22,8f))
        assertEquals(doc,doc.transformSelection(setOf(1L,2L),30f,40f))
        assertEquals(doc,doc.rotateInk(setOf(1L,2L),90f))
        assertEquals(doc,doc.removeSelection(setOf(1L)))
    }
    @Test fun groupingExpandsSelectionAndDuplicateKeepsAnIndependentGroup() {
        val doc=document().groupSelection(setOf(1L,2L))
        assertEquals(setOf(1L,2L),doc.expandSelection(setOf(1L)))
        val copies=doc.duplicateSelection(setOf(1L,2L))
        assertEquals(setOf(3L,4L),copies.expandSelection(setOf(3L)))
        assertEquals(2,copies.groups.size)
        assertEquals(1,copies.ungroupSelection(setOf(3L)).groups.size)
        assertTrue(doc.removeSelection(setOf(1L)).groups.isEmpty())
    }
    @Test fun partialEraserAddsNewFragmentsToTheExistingGroup() {
        val doc=document().groupSelection(setOf(1L,2L))
        val erased=doc.erasePart(InkPoint(650f,0f),InkPoint(650f,200f),12f)
        assertEquals(3,erased.strokes.size)
        assertEquals(erased.strokes.map {it.id}.toSet(),erased.expandSelection(setOf(1L)))
    }
    @Test fun rotationFlipAndSmoothingPreservePressureAndOtherObjects() {
        val doc=document()
        val rotated=doc.rotateInk(setOf(1L),90f)
        assertEquals(doc.strokes[1],rotated.strokes[1])
        assertEquals(doc.strokes[0].points.map {it.pressure},rotated.strokes[0].points.map {it.pressure})
        assertEquals(doc,doc.flipInk(setOf(1L),true).flipInk(setOf(1L),true))
        val smoothed=doc.smoothInk(setOf(1L))
        assertEquals(doc.strokes[0].points.first(),smoothed.strokes[0].points.first())
        assertTrue(smoothed.strokes[0].points[1].y<doc.strokes[0].points[1].y)
    }
    @Test fun imageOrderingChangesItsActualLayerAndKeepsOtherObjects() {
        val doc=document().copy(images=listOf(NoteImage(3,"a"),NoteImage(4,"b",foreground=true)))
        val front=doc.reorderInk(setOf(3L),true)
        assertEquals(listOf(4L,3L),front.images.map {it.id});assertTrue(front.images.last().foreground)
        val back=front.reorderInk(setOf(3L),false)
        assertEquals(listOf(3L,4L),back.images.map {it.id});assertFalse(back.images.first().foreground)
        assertEquals(doc.strokes,back.strokes)
    }
    @Test fun metadataRoundTripsAndOldDocumentsOpenWithDefaults() {
        val doc=document().groupSelection(setOf(1L,2L)).copy(lockedIds=setOf(1L),hiddenIds=setOf(2L),gridVisible=true,snapToGrid=true)
        assertEquals(doc,Json.decodeFromString<InkDocument>(Json.encodeToString(doc)))
        val old=Json.decodeFromString<InkDocument>("{}")
        assertTrue(old.lockedIds.isEmpty());assertFalse(old.snapToGrid)
    }
}
