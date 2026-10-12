package com.eliadca.talks.ui.notes

import com.eliadca.talks.core.ink.selectLasso
import com.eliadca.talks.core.ink.allPoints
import android.graphics.LinearGradient
import android.graphics.Shader
import com.eliadca.talks.core.ink.NoteShape
import com.eliadca.talks.core.ink.shapePoints
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.graphics.Rect
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.DashPathEffect
import com.eliadca.talks.core.ink.InkBounds
import com.eliadca.talks.core.ink.erasePart
import android.view.MotionEvent
import android.view.View
import android.text.StaticLayout
import android.text.TextPaint
import android.text.Layout
import com.eliadca.talks.core.ink.InkDocument
import com.eliadca.talks.core.ink.InkPoint
import com.eliadca.talks.core.ink.InkStroke
import com.eliadca.talks.core.ink.PenKind

enum class EraserMode { PARTIAL, STROKE }

enum class InkTool { TEXT, PEN, ERASER, MOVE, SELECT, RULE_AREA, SHAPE, NODES, LASSO }

/** Native input retains stylus pressure and coalesced samples, and rejects palms during a stylus stroke. */
class InkPageView(context: Context) : View(context) {
    var document = InkDocument()
        set(value) { if (field != value) { field = value; invalidate() } }
    var tool = InkTool.PEN
    var shape = NoteShape.LINE
    var pen = PenKind.BALLPOINT
    var penColor = Color.rgb(35, 43, 60)
    var penWidth = 5f
    var eraserMode = EraserMode.PARTIAL
    var eraserRadius = 20f
    var drawTextBoxes = true
    var drawContent = true
    var onPreview: (InkDocument?) -> Unit = {}
    private var erasedPreview: InkDocument? = null
    var usePressure = true
    var stylusOnly = false
    var ruleColor = Color.rgb(165, 181, 201)
    var onChange: (InkDocument) -> Unit = {}
    var onAreaFinished: () -> Unit = {}
    var selection: Set<Long> = emptySet()
        set(value) { if (field != value) { field = value; invalidate() } }
    var onSelectionChange: (Set<Long>) -> Unit = {}
    private var selectionDrag = false
    private var selectionResize = false
    private var nodeIndex = -1
    private var nodeStroke: Long? = null
    private var selectionPreview: InkDocument? = null
    private var selectionSource: InkDocument? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private data class RenderedStroke(val stroke: InkStroke, val bounds: RectF, val paths: Map<Int, Path>)
    private val rendered = mutableMapOf<Long, RenderedStroke>()
    private val images=mutableMapOf<Long,Pair<String,Bitmap>>()
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val textLayouts = mutableMapOf<Long, Pair<com.eliadca.talks.core.ink.NoteTextBox, StaticLayout>>()
    private var activePointer = -1
    private var activeStylus = false
    private var erasing = false
    private var points = mutableListOf<InkPoint>()
    private var erased = emptySet<Long>()
    private var gesturePen = pen
    private var gestureColor = penColor
    private var gestureWidth = penWidth
    private var gestureTool = tool
    private val touchDensity get() = resources.displayMetrics.density
    private val scale get() = width / document.pageWidth

    init { contentDescription = "Página para escribir a mano"; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (scale <= 0f) return
        canvas.save(); canvas.scale(scale, scale)
        if(drawContent) {
        images.keys.filter {id->document.images.none {it.id==id}}.forEach {id->images.remove(id)?.second?.recycle()}
        fun pictures(front:Boolean) {
            (selectionPreview ?: document).images.filter {it.foreground==front && it.id !in document.hiddenIds}.forEach {image->
                val cached=images[image.id]
                val bitmap=if(cached?.first==image.data)cached.second else run {
                    cached?.second?.recycle()
                    val decoded=runCatching {val bytes=Base64.decode(image.data,Base64.DEFAULT);BitmapFactory.decodeByteArray(bytes,0,bytes.size)}.getOrNull()
                    if(decoded!=null)images[image.id]=image.data to decoded
                    decoded
                }
                if(bitmap!=null) {
                    val src=Rect((image.cropLeft.coerceIn(0f,.99f)*bitmap.width).toInt(),(image.cropTop.coerceIn(0f,.99f)*bitmap.height).toInt(),(image.cropRight.coerceIn(.01f,1f)*bitmap.width).toInt(),(image.cropBottom.coerceIn(.01f,1f)*bitmap.height).toInt())
                    paint.alpha=(image.opacity.coerceIn(0f,1f)*255).toInt();paint.isFilterBitmap=true
                    canvas.save();canvas.rotate(image.rotation,image.x+image.width/2,image.y+image.height/2)
                    canvas.drawBitmap(bitmap,src,RectF(image.x,image.y,image.x+image.width,image.y+image.height),paint);canvas.restore()
                }
            }
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f; paint.color = ruleColor
        paint.alpha = 100; paint.strokeCap = Paint.Cap.BUTT
        val spacing = document.spacing.coerceIn(40f, 100f)
        fun lines(left: Float, top: Float, right: Float, bottom: Float) {
            var y = top + spacing
            while (y <= bottom) { canvas.drawLine(left, y, right, y, paint); y += spacing }
        }
        if(document.dotted) {
            paint.style=Paint.Style.FILL
            val step=document.gridSpacing.coerceIn(16f,160f);var x=step
            while(x<document.pageWidth) {var y=step;while(y<height/scale) {canvas.drawCircle(x,y,1.6f,paint);y+=step};x+=step}
            paint.style=Paint.Style.STROKE
        }
        if(document.gridVisible) {
            val step=document.gridSpacing.coerceIn(16f,160f)
            var x=step;while(x<document.pageWidth) {canvas.drawLine(x,0f,x,height/scale,paint);x+=step}
            var y=step;while(y<height/scale) {canvas.drawLine(0f,y,document.pageWidth,y,paint);y+=step}
        }
        if (document.wholePageRuled) lines(32f, 32f, document.pageWidth - 32, height / scale)
        else document.areas.forEach { lines(it.left, it.top, it.right, it.bottom) }
        pictures(false)
        val existing = (erasedPreview ?: selectionPreview ?: document).strokes.map { it.id }.toSet()
        rendered.keys.retainAll(existing)
        (erasedPreview ?: selectionPreview ?: document).strokes.filterNot { it.id in erased || it.id in document.hiddenIds }.forEach { drawStroke(canvas, it) }
        pictures(true)
        val boxes = (selectionPreview ?: document).textBoxes.filterNot {it.id in document.hiddenIds}
        textLayouts.keys.retainAll(boxes.map { it.id }.toSet())
        if (drawTextBoxes) boxes.forEach { box ->
            val layout = textLayouts[box.id]?.takeIf { it.first == box }?.second ?: run {
                val p = TextPaint(textPaint).apply { textSize = box.size; color = box.color }
                StaticLayout.Builder.obtain(box.text, 0, box.text.length, p, box.width.toInt().coerceAtLeast(1))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build().also { textLayouts[box.id] = box to it }
            }
            canvas.save(); canvas.translate(box.x, box.y); layout.draw(canvas); canvas.restore()
        }
        }
        if (points.isNotEmpty() && !erasing && gestureTool in listOf(InkTool.PEN,InkTool.SHAPE)) {
            drawStroke(canvas, InkStroke(0, gesturePen, gestureColor, gestureWidth, if(gestureTool==InkTool.SHAPE)shapePoints(shape,points.first(),points.last()) else points, geometric=gestureTool==InkTool.SHAPE,bezier=gestureTool==InkTool.SHAPE && shape==NoteShape.BEZIER))
        }
        if (points.isNotEmpty() && (gestureTool == InkTool.RULE_AREA || (gestureTool in listOf(InkTool.SELECT,InkTool.LASSO) && !selectionDrag))) {
            val a = points.first(); val b = points.last()
            paint.color = ruleColor; paint.alpha = 220; paint.strokeWidth = 3f; paint.style = Paint.Style.STROKE
            if(gestureTool==InkTool.LASSO) {val path=Path().apply {moveTo(a.x,a.y);points.drop(1).forEach {lineTo(it.x,it.y)};close()};canvas.drawPath(path,paint)} else canvas.drawRect(RectF(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)), paint)
        }
        if(tool==InkTool.NODES) {
            (selectionPreview ?: document).strokes.filter {it.id in selection && it.id !in document.hiddenIds}.forEach {stroke->
                paint.color=ruleColor;paint.alpha=255;paint.style=Paint.Style.FILL
                stroke.points.forEachIndexed {i,p->if(i%maxOf(1,stroke.points.size/120)==0 || i==nodeIndex)canvas.drawCircle(p.x,p.y,if(i==nodeIndex)7f*touchDensity/scale else 3f*touchDensity/scale,paint)}
            }
        }
        if (tool in listOf(InkTool.SELECT,InkTool.LASSO)) {
            (selectionPreview ?: document).selectionBounds(selection)?.let { box ->
                paint.color = ruleColor; paint.alpha = 255; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f / scale
                paint.pathEffect = DashPathEffect(floatArrayOf(8f * touchDensity / scale, 5f / scale), 0f)
                val margin = 18f * touchDensity / scale
                canvas.drawRect(RectF(box.left - margin, box.top - margin, box.right + margin, box.bottom + margin), paint)
                paint.pathEffect = null; paint.style = Paint.Style.FILL
                canvas.drawCircle(box.right + margin, box.bottom + margin, 8f * touchDensity / scale, paint)
            }
        }
        if (erasing && points.isNotEmpty()) {
            val p = points.last(); paint.style = Paint.Style.STROKE; paint.color = ruleColor; paint.alpha = 200; paint.strokeWidth = 2f / scale
            canvas.drawCircle(p.x, p.y, eraserRadius, paint)
        }
        canvas.restore()
    }

    private fun drawStroke(canvas: Canvas, stroke: InkStroke) {
        val ps = stroke.points
        if (ps.isEmpty()) return
        if(stroke.geometric || stroke.bezier || stroke.contours.isNotEmpty()) {
            val path=vectorPath(stroke)
            paint.pathEffect=null;paint.shader=null;paint.alpha=(stroke.opacity.coerceIn(0f,1f)*255).toInt()
            stroke.fillColor?.let {color->
                paint.style=Paint.Style.FILL;paint.color=color;paint.alpha=(stroke.opacity.coerceIn(0f,1f)*255).toInt()
                stroke.gradientColor?.let {end->val box=RectF();path.computeBounds(box,true);paint.shader=LinearGradient(box.left,box.top,maxOf(box.left+1,box.right),maxOf(box.top+1,box.bottom),color,end,Shader.TileMode.CLAMP)}
                canvas.drawPath(path,paint);paint.shader=null
            }
            paint.style=Paint.Style.STROKE;paint.color=stroke.color;paint.alpha=(stroke.opacity.coerceIn(0f,1f)*255).toInt();paint.strokeWidth=stroke.width;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
            if(stroke.dashed)paint.pathEffect=DashPathEffect(floatArrayOf(stroke.width*4,stroke.width*3),0f)
            canvas.drawPath(path,paint);paint.pathEffect=null;return
        }
        val cached = rendered[stroke.id]?.takeIf { it.stroke == stroke } ?: run {
            val bounds = RectF(ps.minOf { it.x }, ps.minOf { it.y }, ps.maxOf { it.x }, ps.maxOf { it.y })
            bounds.inset(-stroke.width * 2, -stroke.width * 2)
            val paths = mutableMapOf<Int, Path>()
            if (stroke.kind == PenKind.HIGHLIGHTER || stroke.geometric) {
                paths[4] = Path().apply {
                    moveTo(ps[0].x, ps[0].y)
                    ps.drop(1).forEach { lineTo(it.x, it.y) }
                }
            } else if (ps.size > 1) {
                var lastX = ps[0].x; var lastY = ps[0].y
                for (i in 1 until ps.size) {
                    val a = ps[i - 1]; val b = ps[i]
                    val x = if (i == ps.lastIndex) b.x else (a.x + b.x) / 2
                    val y = if (i == ps.lastIndex) b.y else (a.y + b.y) / 2
                    val bucket = (((a.pressure + b.pressure) / 2) * 8).toInt().coerceIn(0, 8)
                    paths.getOrPut(bucket) { Path() }.apply { moveTo(lastX, lastY); quadTo(a.x, a.y, x, y) }
                    lastX = x; lastY = y
                }
            }
            RenderedStroke(stroke, bounds, paths).also { if (stroke.id != 0L) rendered[stroke.id] = it }
        }
        val clip = canvas.clipBounds
        if (!RectF.intersects(cached.bounds, RectF(clip))) return
        paint.style = Paint.Style.STROKE; paint.color = stroke.color
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = if (stroke.kind == PenKind.HIGHLIGHTER) Paint.Cap.SQUARE else Paint.Cap.ROUND
        if (stroke.kind == PenKind.HIGHLIGHTER) {
            // Draw one path, so overlapping samples within one marker stroke don't darken it.
            paint.alpha = (65*stroke.opacity.coerceIn(0f,1f)).toInt(); paint.strokeWidth = stroke.width * 3
            if (ps.size == 1) canvas.drawPoint(ps[0].x, ps[0].y, paint) else canvas.drawPath(cached.paths.getValue(4), paint)
            return
        }
        fun setup(pressure: Float) {
            paint.strokeWidth = stroke.width * when (stroke.kind) {
                PenKind.FOUNTAIN -> .3f + pressure * 1.4f
                PenKind.PENCIL -> .55f + pressure * .7f
                else -> .65f + pressure * .7f
            }
            paint.alpha = ((if (stroke.kind == PenKind.PENCIL) (90 + pressure * 135).toInt() else 255)*stroke.opacity.coerceIn(0f,1f)).toInt()
        }
        if (ps.size == 1) { setup(ps[0].pressure); canvas.drawPoint(ps[0].x, ps[0].y, paint); return }
        cached.paths.forEach { (pressure, path) -> setup(pressure / 8f); canvas.drawPath(path, paint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (tool == InkTool.TEXT || tool == InkTool.MOVE || scale <= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = event.actionIndex
                val kind = event.getToolType(index)
                activeStylus = kind == MotionEvent.TOOL_TYPE_STYLUS || kind == MotionEvent.TOOL_TYPE_ERASER
                if (stylusOnly && !activeStylus && tool == InkTool.PEN) return false
                activePointer = event.getPointerId(index)
                selectionSource=document
                gestureTool = tool; gesturePen = pen; gestureWidth = penWidth; gestureColor = penColor
                erasing = tool == InkTool.ERASER || kind == MotionEvent.TOOL_TYPE_ERASER ||
                    (activeStylus && event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0)
                points.clear(); erased = emptySet(); erasedPreview = null
                parent?.requestDisallowInterceptTouchEvent(true)
                nodeIndex=-1;nodeStroke=null
                if(tool==InkTool.NODES) {
                    val px=event.getX(index)/scale;val py=event.getY(index)/scale
                    val stroke=document.strokes.singleOrNull {it.id in selection && it.id !in document.lockedIds && it.id !in document.hiddenIds}
                    val i=stroke?.points?.indices?.minByOrNull {k->kotlin.math.hypot(stroke.points[k].x-px,stroke.points[k].y-py)}
                    if(stroke!=null && i!=null && kotlin.math.hypot(stroke.points[i].x-px,stroke.points[i].y-py)<=24f*touchDensity/scale) {nodeIndex=i;nodeStroke=stroke.id}
                }
                sample(event, index)
                if (tool in listOf(InkTool.SELECT,InkTool.LASSO)) {
                    val p = points.first()
                    val bounds = document.selectionBounds(selection)
                    val tolerance = 24f * touchDensity / scale
                    selectionResize = bounds != null && kotlin.math.hypot(p.x - bounds.right - 18f * touchDensity / scale, p.y - bounds.bottom - 18f * touchDensity / scale) <= 14f * touchDensity / scale
                    selectionDrag = selectionResize || (bounds != null && p.x in (bounds.left - tolerance)..(bounds.right + tolerance) && p.y in (bounds.top - tolerance)..(bounds.bottom + tolerance))
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!activeStylus) { cancelGesture(); parent?.requestDisallowInterceptTouchEvent(false) }
                // A finger arriving while the pen is down is a palm, not another pen sample.
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointer < 0) return false
                val index = event.findPointerIndex(activePointer)
                if (index < 0) { cancelGesture(); return false }
                for (h in 0 until event.historySize) sample(event, index, h)
                sample(event, index)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) != activePointer) return activePointer >= 0
                sample(event, event.actionIndex)
                finishGesture(); performClick()
            }
            MotionEvent.ACTION_CANCEL -> { if (activeStylus) finishGesture() else cancelGesture() }
        }
        invalidate()
        return true
    }

    private fun sample(event: MotionEvent, index: Int, history: Int = -1) {
        val x = if (history < 0) event.getX(index) else event.getHistoricalX(index, history)
        val y = if (history < 0) event.getY(index) else event.getHistoricalY(index, history)
        val pressure = if (activeStylus && usePressure) {
            if (history < 0) event.getPressure(index) else event.getHistoricalPressure(index, history)
        } else .5f
        var point = InkPoint((x / scale).coerceIn(0f, document.pageWidth), (y / scale).coerceAtLeast(0f), pressure.coerceIn(0f, 1f))
        if(document.snapToGrid && gestureTool in listOf(InkTool.SHAPE,InkTool.NODES)) {
            val step=document.gridSpacing.coerceIn(16f,160f)
            point=point.copy(x=(kotlin.math.round(point.x/step)*step).coerceIn(0f,document.pageWidth),y=(kotlin.math.round(point.y/step)*step).coerceAtLeast(0f))
        }
        if(gestureTool==InkTool.NODES && nodeIndex>=0) {
            val source=selectionSource ?: document
            selectionPreview=mergeSelection(source.copy(strokes=source.strokes.map {stroke->if(stroke.id!=nodeStroke)stroke else stroke.copy(points=stroke.points.mapIndexed {i,p->if(i==nodeIndex)point.copy(pressure=p.pressure) else p})}))
            onPreview(selectionPreview)
        }
        if (erasing) {
            val previous = points.lastOrNull() ?: point
            if (eraserMode == EraserMode.PARTIAL) {erasedPreview = (erasedPreview ?: document).erasePart(previous, point, eraserRadius);onPreview(erasedPreview)}
            else erased = erased + document.strokesHit(previous, point, eraserRadius)
        }
        points.add(point)
        if (gestureTool in listOf(InkTool.SELECT,InkTool.LASSO) && selectionDrag && points.size > 1) {
            val origin = points.first()
            val source=selectionSource ?: document
            val bounds = source.selectionBounds(selection)
            selectionPreview = mergeSelection( if (selectionResize && bounds != null) {
                val initial = kotlin.math.hypot(origin.x - bounds.left, origin.y - bounds.top).coerceAtLeast(1f)
                source.transformSelection(selection, factor = kotlin.math.hypot(point.x - bounds.left, point.y - bounds.top) / initial)
            } else source.transformSelection(selection, point.x - origin.x, point.y - origin.y))
            onPreview(selectionPreview)
        }
    }

    private fun mergeSelection(preview:InkDocument):InkDocument = document.copy(
        strokes=document.strokes.map {s->if(s.id in selection)preview.strokes.firstOrNull {it.id==s.id} ?: s else s},
        textBoxes=document.textBoxes.map {b->if(b.id in selection)preview.textBoxes.firstOrNull {it.id==b.id} ?: b else b},
        images=document.images.map {i->if(i.id in selection)preview.images.firstOrNull {it.id==i.id} ?: i else i},
        bodyFrame=if(InkDocument.MAIN_TEXT_ID in selection)preview.bodyFrame else document.bodyFrame,
        height=maxOf(document.height,preview.height),pageWidth=maxOf(document.pageWidth,preview.pageWidth))

    private fun finishGesture() {
        if (activePointer < 0) return
        if (gestureTool in listOf(InkTool.SELECT,InkTool.LASSO) && !selectionDrag && points.isNotEmpty()) {
            val a = points.first(); val b = points.last()
            val chosen = if(gestureTool==InkTool.LASSO && points.size>=3 && points.any {kotlin.math.hypot(a.x-it.x,a.y-it.y)>10f*touchDensity/scale})document.selectLasso(points) else if (kotlin.math.hypot(a.x - b.x, a.y - b.y) < 10f * touchDensity / scale) document.images.lastOrNull {it.id !in document.lockedIds && it.id !in document.hiddenIds && a.x in it.x..(it.x+it.width) && a.y in it.y..(it.y+it.height)}?.let {setOf(it.id)}
                ?: document.textBoxes.lastOrNull { a.x in it.x..(it.x + it.width) && a.y in it.y..(it.y + it.height) }?.let { setOf(it.id) }
                ?: document.bodyFrame?.takeIf { a.x in it.x..(it.x+it.width) && a.y in it.y..(it.y+it.height) }?.let { setOf(InkDocument.MAIN_TEXT_ID) }
                ?: document.strokesHit(a, b, 14f * touchDensity / scale).takeLastStroke()
                else document.selectRectangle(InkBounds(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)))
            selection = document.expandSelection(chosen); onSelectionChange(selection)
        }
        val updated = when {
            gestureTool in listOf(InkTool.SELECT,InkTool.NODES,InkTool.LASSO) -> selectionPreview ?: document
            erasing -> erasedPreview ?: document.copy(strokes = document.strokes.filterNot { it.id in erased || it.id in document.hiddenIds })
            gestureTool == InkTool.RULE_AREA && points.isNotEmpty() -> document.addArea(points.first(), points.last())
            gestureTool == InkTool.SHAPE && points.isNotEmpty() -> document.addStroke(gesturePen,gestureColor,gestureWidth,shapePoints(shape,points.first(),points.last()),geometric=true).let {doc->doc.copy(strokes=doc.strokes.map {if(it.id==document.nextId)it.copy(bezier=shape==NoteShape.BEZIER) else it})}
            gestureTool == InkTool.PEN -> document.addStroke(gesturePen, gestureColor, gestureWidth, points.toList())
            else -> document
        }
        val area = gestureTool == InkTool.RULE_AREA
        cancelGesture()
        if (updated != document) onChange(updated)
        if (area) onAreaFinished()
    }

    private fun cancelGesture() {
        activePointer = -1; points.clear(); erased = emptySet(); erasedPreview = null; selectionPreview = null; selectionSource=null; onPreview(null); selectionDrag = false; selectionResize = false; nodeIndex=-1;nodeStroke=null
        parent?.requestDisallowInterceptTouchEvent(false); invalidate()
    }

    private fun Set<Long>.takeLastStroke() = document.strokes.lastOrNull { it.id in this }?.let { setOf(it.id) } ?: emptySet()

    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDetachedFromWindow() { finishGesture(); super.onDetachedFromWindow() }
}
