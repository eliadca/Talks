package com.eliadca.talks.notes

import android.app.Application
import android.graphics.*
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.eliadca.talks.core.ink.*
import com.eliadca.talks.ui.notes.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteArtworkTest {
    private fun rectangle(id:Long,l:Float,t:Float,r:Float,b:Float)=InkStroke(id,PenKind.BALLPOINT,Color.RED,4f,shapePoints(NoteShape.RECTANGLE,InkPoint(l,t),InkPoint(r,b)),geometric=true,fillColor=Color.RED)
    @Test fun booleanDifferenceKeepsTheHoleAsAContourAndUndoCanRestoreTheSources() {
        val original=InkDocument(strokes=listOf(rectangle(1,40f,40f,400f,400f),rectangle(2,120f,120f,300f,300f)),nextId=3)
        val history=InkHistory(original);val cut=combineShapes(original,setOf(1L,2L),Path.Op.DIFFERENCE)
        history.record(cut);assertEquals(1,cut.strokes.size);assertTrue(cut.strokes.single().contours.isNotEmpty())
        val view=InkPageView(ApplicationProvider.getApplicationContext()).apply {document=cut;tool=InkTool.MOVE;layout(0,0,1000,1500)}
        val bitmap=Bitmap.createBitmap(1000,1500,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE);view.draw(canvas)
        assertEquals(Color.RED,bitmap.getPixel(80,80));assertEquals(Color.WHITE,bitmap.getPixel(200,200));bitmap.recycle()
        assertEquals(original,history.undo())
    }
    @Test fun unionAndIntersectionYieldRealEditableGeometry() {
        val doc=InkDocument(strokes=listOf(rectangle(1,40f,40f,240f,240f),rectangle(2,140f,140f,340f,340f)),nextId=3)
        val union=combineShapes(doc,setOf(1L,2L),Path.Op.UNION)
        assertTrue(union.strokes.single().containsPoint(InkPoint(60f,60f)))
        assertTrue(union.strokes.single().containsPoint(InkPoint(320f,320f)))
        val intersection=combineShapes(doc,setOf(1L,2L),Path.Op.INTERSECT)
        assertFalse(intersection.strokes.single().containsPoint(InkPoint(60f,60f)))
        assertTrue(intersection.strokes.single().containsPoint(InkPoint(180f,180f)))
    }
    @Test fun imageCropIsRenderedAndImagesAreEmbeddedInPngAndSvg() {
        val source=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888);val c=Canvas(source);c.drawColor(Color.BLUE);val p=Paint().apply {color=Color.GREEN};c.drawRect(40f,0f,80f,40f,p)
        val bytes=ByteArrayOutputStream().apply {source.compress(Bitmap.CompressFormat.PNG,100,this)}.toByteArray();source.recycle()
        val image=NoteImage(1,Base64.encodeToString(bytes,Base64.NO_WRAP),x=50f,y=50f,width=300f,height=150f,cropLeft=.5f)
        val doc=InkDocument(images=listOf(image),nextId=2)
        val context=ApplicationProvider.getApplicationContext<Application>()
        val png=ByteArrayOutputStream();NoteArtworkExport.write(context,doc,"", "png",png)
        val output=BitmapFactory.decodeByteArray(png.toByteArray(),0,png.size());assertEquals(Color.GREEN,output.getPixel(100,100));output.recycle()
        val svg=NoteSvgExport.write(doc,"A < B & C");assertTrue(svg.contains("data:image/png;base64,"));assertTrue(svg.contains("&lt;"));assertTrue(svg.contains("&amp;"))
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg.byteInputStream())
    }
    @Test fun cubicBezierHasRealControlPointsAndSvgPreservesTheCurve() {
        val points=shapePoints(NoteShape.BEZIER,InkPoint(40f,40f),InkPoint(400f,400f))
        val curve=InkStroke(1,PenKind.BALLPOINT,Color.BLACK,4f,points,geometric=true,bezier=true)
        assertEquals(4,curve.points.size);assertTrue(curve.resolvedPaths().single().size>4)
        val svg=NoteSvgExport.write(InkDocument(strokes=listOf(curve)),"");assertTrue(svg.contains("C"));assertTrue(svg.contains("stroke="))
    }
}
