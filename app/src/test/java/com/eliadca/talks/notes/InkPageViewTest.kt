package com.eliadca.talks.notes

import android.app.Application
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.eliadca.talks.core.ink.InkDocument
import com.eliadca.talks.core.ink.InkPoint
import com.eliadca.talks.core.ink.PenKind
import com.eliadca.talks.ui.notes.InkPageView
import com.eliadca.talks.ui.notes.InkTool
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InkPageViewTest {
    private fun page(width: Int = 500) = InkPageView(ApplicationProvider.getApplicationContext<Application>()).also {
        it.layout(0, 0, width, 1000)
        it.onChange = { value -> it.document = value }
    }
    private fun event(action: Int, x: Float, y: Float, pressure: Float = .5f, stylus: Boolean = true): MotionEvent {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = if (stylus) MotionEvent.TOOL_TYPE_STYLUS else MotionEvent.TOOL_TYPE_FINGER }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; this.pressure = pressure }
        return MotionEvent.obtain(0, 20, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, 0, 0)
    }
    private fun send(view: InkPageView, action: Int, x: Float, y: Float, pressure: Float = .5f, stylus: Boolean = true): Boolean {
        return event(action, x, y, pressure, stylus).let { try { view.onTouchEvent(it) } finally { it.recycle() } }
    }

    @Test fun shapesAndTheirBezierControlPointsCanBeEditedWithThePen() {
        val page=page();page.tool=InkTool.SHAPE;page.shape=com.eliadca.talks.core.ink.NoteShape.BEZIER
        send(page,MotionEvent.ACTION_DOWN,40f,50f);send(page,MotionEvent.ACTION_UP,180f,220f)
        val stroke=page.document.strokes.single();assertTrue(stroke.bezier);assertEquals(4,stroke.points.size)
        page.tool=InkTool.NODES;page.selection=setOf(stroke.id)
        val control=stroke.points[1]
        send(page,MotionEvent.ACTION_DOWN,control.x/2,control.y/2)
        send(page,MotionEvent.ACTION_UP,control.x/2+30f,control.y/2+40f)
        assertEquals(control.x+60f,page.document.strokes.single().points[1].x,0f)
        assertEquals(control.y+80f,page.document.strokes.single().points[1].y,0f)
        assertEquals(1,page.document.strokes.size)
    }
    @Test fun movingASelectionKeepsOtherObjectsAndFormattingAddedDuringTheGesture() {
        val page=page();page.document=InkDocument().addStroke(PenKind.BALLPOINT,8,4f,listOf(InkPoint(100f,100f),InkPoint(300f,180f)));page.tool=InkTool.SELECT;page.selection=setOf(1L)
        send(page,MotionEvent.ACTION_DOWN,80f,70f);send(page,MotionEvent.ACTION_MOVE,100f,90f)
        val formatting=com.eliadca.talks.core.notes.NoteFormatting().apply(0,4) {it.copy(bold=true)}
        page.document=page.document.copy(images=listOf(com.eliadca.talks.core.ink.NoteImage(2,"new image")),nextId=3,formatting=formatting)
        send(page,MotionEvent.ACTION_UP,120f,110f)
        assertEquals(180f,page.document.strokes.single().points.first().x,0f)
        assertEquals(180f,page.document.strokes.single().points.first().y,0f)
        assertEquals("new image",page.document.images.single().data);assertEquals(formatting,page.document.formatting)
    }

    @Test fun imagesUseTheSameSelectionMoveAndLockMechanismAsTextAndInk() {
        val page=page();page.document=InkDocument(images=listOf(com.eliadca.talks.core.ink.NoteImage(1,"",x=100f,y=100f,width=400f,height=200f)),nextId=2);page.tool=InkTool.SELECT
        send(page,MotionEvent.ACTION_DOWN,100f,100f);send(page,MotionEvent.ACTION_UP,100f,100f);assertEquals(setOf(1L),page.selection)
        send(page,MotionEvent.ACTION_DOWN,100f,100f);send(page,MotionEvent.ACTION_UP,160f,140f)
        assertEquals(220f,page.document.images.single().x,0f);assertEquals(180f,page.document.images.single().y,0f)
        page.document=page.document.copy(lockedIds=setOf(1L));page.selection=emptySet()
        send(page,MotionEvent.ACTION_DOWN,140f,110f);send(page,MotionEvent.ACTION_UP,140f,110f);assertTrue(page.selection.isEmpty())
    }

    @Test fun stylusPressureIsSavedInPageCoordinatesAndFingerInputCanBeDisabled() {
        val page = page()
        page.stylusOnly = true
        assertFalse(send(page, MotionEvent.ACTION_DOWN, 50f, 70f, stylus = false))
        send(page, MotionEvent.ACTION_DOWN, 50f, 70f, .1f)
        send(page, MotionEvent.ACTION_MOVE, 100f, 150f, .8f)
        send(page, MotionEvent.ACTION_UP, 110f, 155f, .9f)
        val stroke = page.document.strokes.single()
        assertEquals(InkPoint(100f, 140f, .1f), stroke.points.first())
        assertEquals(.8f, stroke.points[1].pressure)
        page.layout(0, 0, 1000, 2000)
        assertEquals(stroke, page.document.strokes.single())
        page.tool = InkTool.ERASER; page.eraserMode = com.eliadca.talks.ui.notes.EraserMode.STROKE
        send(page, MotionEvent.ACTION_DOWN, 200f, 300f)
        send(page, MotionEvent.ACTION_UP, 200f, 300f)
        assertTrue(page.document.strokes.isEmpty())
    }

    @Test fun partialEraserCutsOnlyTheMiddleAndCommitsOneGesture() {
        val page=page();var commits=0
        page.document=InkDocument().addStroke(PenKind.BALLPOINT,7,4f,listOf(InkPoint(100f,200f),InkPoint(900f,200f)))
        page.onChange={commits++;page.document=it};page.tool=InkTool.ERASER
        send(page,MotionEvent.ACTION_DOWN,250f,50f);send(page,MotionEvent.ACTION_MOVE,250f,150f)
        assertEquals(1,page.document.strokes.size)
        send(page,MotionEvent.ACTION_UP,250f,150f)
        assertEquals(1,commits);assertEquals(2,page.document.strokes.size)
        assertEquals(100f,page.document.strokes.first().points.first().x)
        assertEquals(900f,page.document.strokes.last().points.last().x)
    }
    @Test fun theExistingTextFrameCanBeSelectedAndMovedWithThePen() {
        val page=page();page.document=InkDocument(bodyFrame=com.eliadca.talks.core.ink.NoteTextFrame(100f,100f,400f,200f));page.tool=InkTool.SELECT
        send(page,MotionEvent.ACTION_DOWN,75f,75f);send(page,MotionEvent.ACTION_UP,75f,75f)
        assertEquals(setOf(InkDocument.MAIN_TEXT_ID),page.selection)
        send(page,MotionEvent.ACTION_DOWN,100f,100f);send(page,MotionEvent.ACTION_MOVE,180f,220f);send(page,MotionEvent.ACTION_UP,180f,220f)
        assertEquals(260f,page.document.bodyFrame!!.x);assertEquals(340f,page.document.bodyFrame!!.y)
    }
    @Test fun partialRulingIsARegionAndTextOrMoveModeDoesNotConsumeDrawingInput() {
        val page = page()
        page.tool = InkTool.RULE_AREA
        send(page, MotionEvent.ACTION_DOWN, 20f, 30f)
        send(page, MotionEvent.ACTION_UP, 200f, 250f)
        val area = page.document.areas.single()
        assertEquals(40f, area.left); assertEquals(500f, area.bottom)
        page.tool = InkTool.TEXT
        assertFalse(send(page, MotionEvent.ACTION_DOWN, 30f, 30f))
        page.tool = InkTool.MOVE
        assertFalse(send(page, MotionEvent.ACTION_DOWN, 30f, 30f))
    }

    @Test fun aPalmArrivingDuringAStylusStrokeDoesNotContributePoints() {
        val page = page()
        send(page, MotionEvent.ACTION_DOWN, 50f, 60f)
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = 60f; y = 65f; pressure = .7f },
            MotionEvent.PointerCoords().apply { x = 450f; y = 400f; pressure = 1f })
        val palm = MotionEvent.obtain(0, 30, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        page.onTouchEvent(palm); palm.recycle()
        val move = MotionEvent.obtain(0, 40, MotionEvent.ACTION_MOVE, 2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        page.onTouchEvent(move); move.recycle()
        send(page, MotionEvent.ACTION_UP, 75f, 80f)
        assertTrue(page.document.strokes.single().points.all { it.x < 200f })
        assertEquals(PenKind.BALLPOINT, page.document.strokes.single().kind)
    }
    @Test fun aFingerCanSelectMoveAndResizePressureStrokesWithoutDrawingExtraInk() {
        val page = page(); page.stylusOnly = true
        page.document = InkDocument().addStroke(PenKind.PENCIL, 12, 5f,
            listOf(InkPoint(100f, 100f, .2f), InkPoint(300f, 200f, .8f)))
        page.tool = InkTool.SELECT
        send(page, MotionEvent.ACTION_DOWN, 20f, 20f, stylus = false)
        send(page, MotionEvent.ACTION_UP, 170f, 120f, stylus = false)
        assertEquals(setOf(1L), page.selection)
        send(page, MotionEvent.ACTION_DOWN, 100f, 75f, stylus = false)
        send(page, MotionEvent.ACTION_MOVE, 150f, 175f, stylus = false)
        send(page, MotionEvent.ACTION_UP, 150f, 175f, stylus = false)
        assertEquals(InkPoint(200f, 300f, .2f), page.document.strokes.single().points.first())
        val old = page.document
        send(page, MotionEvent.ACTION_DOWN, 218f, 218f, stylus = false)
        send(page, MotionEvent.ACTION_MOVE, 260f, 260f, stylus = false)
        send(page, MotionEvent.ACTION_UP, 260f, 260f, stylus = false)
        assertTrue(page.document.strokes.single().width > old.strokes.single().width)
        assertEquals(.8f, page.document.strokes.single().points.last().pressure)
        page.document = page.document.resizePage(2000f, 1500f)
        page.tool = InkTool.PEN
        send(page, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(page, MotionEvent.ACTION_UP, 120f, 120f)
        assertEquals(400f, page.document.strokes.last().points.first().x)
    }

    @Test fun aSingleDotMovesInsteadOfAccidentallyGrabbingItsResizeHandle() {
        val page = page()
        page.document = InkDocument().addStroke(PenKind.PENCIL, 1, 5f, listOf(InkPoint(200f, 200f, .5f)))
        page.tool = InkTool.SELECT; page.selection = setOf(1)
        send(page, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(page, MotionEvent.ACTION_MOVE, 150f, 180f)
        send(page, MotionEvent.ACTION_UP, 150f, 180f)
        assertEquals(InkPoint(300f, 360f, .5f), page.document.strokes.single().points.single())
        assertEquals(5f, page.document.strokes.single().width)
    }

}
