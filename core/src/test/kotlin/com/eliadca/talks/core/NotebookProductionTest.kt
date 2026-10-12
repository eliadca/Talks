package com.eliadca.talks.core

import com.eliadca.talks.core.ink.*
import com.eliadca.talks.core.notes.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NotebookProductionTest {
    private val original=InkDocument().addStroke(PenKind.FOUNTAIN,123,5f,listOf(InkPoint(100f,200f,.2f),InkPoint(900f,200f,.8f)))
    @Test fun eraserCutsTheMiddleOfASparseStrokeAndPreservesBothEndsAndPressure() {
        val erased=original.erasePart(InkPoint(500f,100f),InkPoint(500f,300f),20f)
        assertEquals(2,erased.strokes.size)
        assertEquals(original.strokes.single().points.first(),erased.strokes.first().points.first())
        assertEquals(original.strokes.single().points.last(),erased.strokes.last().points.last())
        assertTrue(erased.strokes.first().points.last().x<480)
        assertTrue(erased.strokes.last().points.first().x>520)
        assertTrue(erased.strokes.first().points.last().pressure in .45f.. .55f)
        assertEquals(2,erased.strokes.map {it.id}.distinct().size)
        assertTrue(erased.strokes.all {it.kind==PenKind.FOUNTAIN && it.color==123 && it.width==5f})
        assertEquals(1,original.strokes.size)
    }
    @Test fun repeatedErasureDoesNotRejoinTheGapAndUnrelatedDrawingIsUnchanged() {
        val elsewhere=original.addStroke(PenKind.PENCIL,10,4f,listOf(InkPoint(40f,40f)))
        val erased=elsewhere.erasePart(InkPoint(500f,200f),InkPoint(500f,200f),20f)
            .erasePart(InkPoint(700f,150f),InkPoint(700f,250f),20f)
        assertEquals(4,erased.strokes.size)
        assertEquals(elsewhere.strokes.last(),erased.strokes.last())
        assertSame(elsewhere,elsewhere.erasePart(InkPoint(200f,700f),InkPoint(300f,700f),10f))
    }
    @Test fun aTextFrameMovesAndResizesWithInkAndSurvivesSerialization() {
        val doc=original.copy(bodyFrame=NoteTextFrame(100f,100f,400f,200f))
        val ids=doc.selectRectangle(InkBounds(90f,90f,920f,320f))
        assertTrue(InkDocument.MAIN_TEXT_ID in ids)
        val moved=doc.transformSelection(ids,20f,200f,.8f)
        assertEquals(120f,moved.bodyFrame!!.x);assertEquals(300f,moved.bodyFrame!!.y)
        assertEquals(320f,moved.bodyFrame!!.width);assertEquals(.8f,moved.bodyFrame!!.scale)
        assertEquals(moved,Json.decodeFromString<InkDocument>(Json.encodeToString(moved)))
    }
    @Test fun aReviewedPrefixAcceptsNewSpeechWhileKeepingOriginalChunks() {
        val original=listOf(1L to "oy ablo",2L to "de la fe")
        val revision=TranscriptRevision("Hoy hablo de la fe.",2,true)
        assertEquals("Hoy hablo de la fe. Y seguimos.",revision.render(original+(3L to "Y seguimos.")))
        assertEquals("oy ablo de la fe",TranscriptRevision().render(original))
        assertEquals(revision,Json.decodeFromString<TranscriptRevision>(Json.encodeToString(revision)))
    }
    @Test fun aiReviewRejectsChangedNumbersUnrelatedRewritesAndEmptyResults() {
        assertNotNull(CorrectionReview.concern("Juan 3 16", "Juan 3 17"))
        assertNotNull(CorrectionReview.concern("Hoy estudiamos juntos la Biblia y seguimos el ejemplo de Jesús", "La tecnología ha avanzado rápidamente en la industria moderna y los mercados"))
        assertNotNull(CorrectionReview.concern("Hola", ""))
        assertNull(CorrectionReview.concern("oy vamos a ablar de la fe", "Hoy vamos a hablar de la fe."))
    }
}
