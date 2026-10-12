package com.eliadca.talks.ui.notes

import android.content.Context
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

enum class InkTool { TEXT, PEN, ERASER, MOVE, SELECT, RULE_AREA }

/** Native input retains stylus pressure and coalesced samples, and rejects palms during a stylus stroke. */
class InkPageView(context: Context) : View(context) {
    var document = InkDocument()
        set(value) { if (field != value) { field = value; invalidate() } }
    var tool = InkTool.PEN
    var pen = PenKind.BALLPOINT
    var penColor = Color.rgb(35, 43, 60)
    var penWidth = 5f
    var eraserMode = EraserMode.PARTIAL
    var eraserRadius = 20f
    var drawTextBoxes = true
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
    private var selectionPreview: InkDocument? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private data class RenderedStroke(val stroke: InkStroke, val bounds: RectF, val paths: Map<Int, Path>)
    private val rendered = mutableMapOf<Long, RenderedStroke>()
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
    private val scale get() = width / document.pageWidth

    init { contentDescription = "Página para escribir a mano"; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (scale <= 0f) return
        canvas.save(); canvas.scale(scale, scale)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f; paint.color = ruleColor
        paint.alpha = 100; paint.strokeCap = Paint.Cap.BUTT
        val spacing = document.spacing.coerceIn(40f, 100f)
        fun lines(left: Float, top: Float, right: Float, bottom: Float) {
            var y = top + spacing
            while (y <= bottom) { canvas.drawLine(left, y, right, y, paint); y += spacing }
        }
        if (document.wholePageRuled) lines(32f, 32f, document.pageWidth - 32, height / scale)
        else document.areas.forEach { lines(it.left, it.top, it.right, it.bottom) }
        val existing = (erasedPreview ?: selectionPreview ?: document).strokes.map { it.id }.toSet()
        rendered.keys.retainAll(existing)
        (erasedPreview ?: selectionPreview ?: document).strokes.filterNot { it.id in erased }.forEach { drawStroke(canvas, it) }
        val boxes = (selectionPreview ?: document).textBoxes
        textLayouts.keys.retainAll(boxes.map { it.id }.toSet())
        if (drawTextBoxes) boxes.forEach { box ->
            val layout = textLayouts[box.id]?.takeIf { it.first == box }?.second ?: run {
                val p = TextPaint(textPaint).apply { textSize = box.size; color = box.color }
                StaticLayout.Builder.obtain(box.text, 0, box.text.length, p, box.width.toInt().coerceAtLeast(1))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build().also { textLayouts[box.id] = box to it }
            }
            canvas.save(); canvas.translate(box.x, box.y); layout.draw(canvas); canvas.restore()
        }
        if (points.isNotEmpty() && !erasing && gestureTool == InkTool.PEN) {
            drawStroke(canvas, InkStroke(0, gesturePen, gestureColor, gestureWidth, points))
        }
        if (points.isNotEmpty() && (gestureTool == InkTool.RULE_AREA || (gestureTool == InkTool.SELECT && !selectionDrag))) {
            val a = points.first(); val b = points.last()
            paint.color = ruleColor; paint.alpha = 220; paint.strokeWidth = 3f; paint.style = Paint.Style.STROKE
            canvas.drawRect(RectF(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)), paint)
        }
        if (tool == InkTool.SELECT) {
            (selectionPreview ?: document).selectionBounds(selection)?.let { box ->
                paint.color = ruleColor; paint.alpha = 255; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f / scale
                paint.pathEffect = DashPathEffect(floatArrayOf(8f / scale, 5f / scale), 0f)
                val margin = 18f / scale
                canvas.drawRect(RectF(box.left - margin, box.top - margin, box.right + margin, box.bottom + margin), paint)
                paint.pathEffect = null; paint.style = Paint.Style.FILL
                canvas.drawCircle(box.right + margin, box.bottom + margin, 8f / scale, paint)
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
        val cached = rendered[stroke.id]?.takeIf { it.stroke == stroke } ?: run {
            val bounds = RectF(ps.minOf { it.x }, ps.minOf { it.y }, ps.maxOf { it.x }, ps.maxOf { it.y })
            bounds.inset(-stroke.width * 2, -stroke.width * 2)
            val paths = mutableMapOf<Int, Path>()
            if (stroke.kind == PenKind.HIGHLIGHTER) {
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
            paint.alpha = 65; paint.strokeWidth = stroke.width * 3
            if (ps.size == 1) canvas.drawPoint(ps[0].x, ps[0].y, paint) else canvas.drawPath(cached.paths.getValue(4), paint)
            return
        }
        fun setup(pressure: Float) {
            paint.strokeWidth = stroke.width * when (stroke.kind) {
                PenKind.FOUNTAIN -> .3f + pressure * 1.4f
                PenKind.PENCIL -> .55f + pressure * .7f
                else -> .65f + pressure * .7f
            }
            paint.alpha = if (stroke.kind == PenKind.PENCIL) (90 + pressure * 135).toInt() else 255
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
                gestureTool = tool; gesturePen = pen; gestureWidth = penWidth; gestureColor = penColor
                erasing = tool == InkTool.ERASER || kind == MotionEvent.TOOL_TYPE_ERASER ||
                    (activeStylus && event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0)
                points.clear(); erased = emptySet(); erasedPreview = null
                parent?.requestDisallowInterceptTouchEvent(true)
                sample(event, index)
                if (tool == InkTool.SELECT) {
                    val p = points.first()
                    val bounds = document.selectionBounds(selection)
                    val tolerance = 24f / scale
                    selectionResize = bounds != null && kotlin.math.hypot(p.x - bounds.right - 18f / scale, p.y - bounds.bottom - 18f / scale) <= 14f / scale
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
        val point = InkPoint((x / scale).coerceIn(0f, document.pageWidth), (y / scale).coerceAtLeast(0f), pressure.coerceIn(0f, 1f))
        if (erasing) {
            val previous = points.lastOrNull() ?: point
            if (eraserMode == EraserMode.PARTIAL) erasedPreview = (erasedPreview ?: document).erasePart(previous, point, eraserRadius)
            else erased = erased + document.strokesHit(previous, point, eraserRadius)
        }
        points.add(point)
        if (gestureTool == InkTool.SELECT && selectionDrag && points.size > 1) {
            val origin = points.first()
            val bounds = document.selectionBounds(selection)
            selectionPreview = if (selectionResize && bounds != null) {
                val initial = kotlin.math.hypot(origin.x - bounds.left, origin.y - bounds.top).coerceAtLeast(1f)
                document.transformSelection(selection, factor = kotlin.math.hypot(point.x - bounds.left, point.y - bounds.top) / initial)
            } else document.transformSelection(selection, point.x - origin.x, point.y - origin.y)
            onPreview(selectionPreview)
        }
    }

    private fun finishGesture() {
        if (activePointer < 0) return
        if (gestureTool == InkTool.SELECT && !selectionDrag && points.isNotEmpty()) {
            val a = points.first(); val b = points.last()
            val chosen = if (kotlin.math.hypot(a.x - b.x, a.y - b.y) < 10f / scale) document.textBoxes.lastOrNull { a.x in it.x..(it.x + it.width) && a.y in it.y..(it.y + it.height) }?.let { setOf(it.id) }
                ?: document.bodyFrame?.takeIf { a.x in it.x..(it.x+it.width) && a.y in it.y..(it.y+it.height) }?.let { setOf(InkDocument.MAIN_TEXT_ID) }
                ?: document.strokesHit(a, b, 14f / scale).takeLastStroke()
                else document.selectRectangle(InkBounds(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)))
            selection = chosen; onSelectionChange(chosen)
        }
        val updated = when {
            gestureTool == InkTool.SELECT -> selectionPreview ?: document
            erasing -> erasedPreview ?: document.copy(strokes = document.strokes.filterNot { it.id in erased })
            gestureTool == InkTool.RULE_AREA && points.isNotEmpty() -> document.addArea(points.first(), points.last())
            gestureTool == InkTool.PEN -> document.addStroke(gesturePen, gestureColor, gestureWidth, points.toList())
            else -> document
        }
        val area = gestureTool == InkTool.RULE_AREA
        cancelGesture()
        if (updated != document) onChange(updated)
        if (area) onAreaFinished()
    }

    private fun cancelGesture() {
        activePointer = -1; points.clear(); erased = emptySet(); erasedPreview = null; selectionPreview = null; onPreview(null); selectionDrag = false; selectionResize = false
        parent?.requestDisallowInterceptTouchEvent(false); invalidate()
    }

    private fun Set<Long>.takeLastStroke() = document.strokes.lastOrNull { it.id in this }?.let { setOf(it.id) } ?: emptySet()

    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDetachedFromWindow() { finishGesture(); super.onDetachedFromWindow() }
}
