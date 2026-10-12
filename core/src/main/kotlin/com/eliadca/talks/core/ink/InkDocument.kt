package com.eliadca.talks.core.ink

import kotlinx.serialization.Serializable
import com.eliadca.talks.core.notes.NoteFormatting
import kotlin.math.max

/** Document coordinates keep content in place independently of the viewport and zoom. */
@Serializable
data class InkPoint(val x: Float, val y: Float, val pressure: Float = .5f)

@Serializable
enum class PenKind { BALLPOINT, PENCIL, FOUNTAIN, HIGHLIGHTER }

@Serializable
data class InkStroke(val id: Long, val kind: PenKind, val color: Int, val width: Float, val points: List<InkPoint>)

@Serializable
data class NoteTextBox(val id: Long, val text: String, val x: Float, val y: Float,
    val width: Float = 500f, val height: Float = 220f, val size: Float = 46f, val color: Int = 0xff232b3c.toInt(),
    val formatting: NoteFormatting = NoteFormatting())

@Serializable
data class NoteTextFrame(val x: Float = 40f, val y: Float = 40f, val width: Float = 920f,
    val height: Float = 100f, val scale: Float = 1f)

@Serializable
data class RuledArea(val id: Long, val left: Float, val top: Float, val right: Float, val bottom: Float)

@Serializable
data class InkDocument(
    val strokes: List<InkStroke> = emptyList(),
    val areas: List<RuledArea> = emptyList(),
    val wholePageRuled: Boolean = false,
    val spacing: Float = 64f,
    val height: Float = 1500f,
    val nextId: Long = 1,
    val pageWidth: Float = PAGE_WIDTH,
    val formatting: NoteFormatting = NoteFormatting(),
    val textBoxes: List<NoteTextBox> = emptyList(),
    val bodyFrame: NoteTextFrame? = null,
) {
    fun addStroke(kind: PenKind, color: Int, width: Float, points: List<InkPoint>): InkDocument {
        val valid = points.filter { it.x.isFinite() && it.y.isFinite() && it.pressure.isFinite() }
            .map { it.copy(x = it.x.coerceIn(0f, pageWidth), y = max(0f, it.y), pressure = it.pressure.coerceIn(0f, 1f)) }
        if (valid.isEmpty()) return this
        return copy(strokes = strokes + InkStroke(nextId, kind, color, width.coerceIn(1f, 32f), valid),
            height = max(height, valid.maxOf { it.y } + 100), nextId = nextId + 1)
    }

    fun addArea(start: InkPoint, end: InkPoint): InkDocument {
        val left = minOf(start.x, end.x).coerceIn(0f, pageWidth)
        val right = maxOf(start.x, end.x).coerceIn(0f, pageWidth)
        val top = max(0f, minOf(start.y, end.y))
        val bottom = max(0f, maxOf(start.y, end.y))
        if (right - left < 80 || bottom - top < 64) return this
        return copy(areas = areas + RuledArea(nextId, left, top, right, bottom),
            height = max(height, bottom + 100), nextId = nextId + 1)
    }

    /** Erases complete strokes crossed by the eraser, including fast movement between samples. */
    fun strokesHit(start: InkPoint, end: InkPoint, radius: Float): Set<Long> = strokes.filter { stroke ->
        val tolerance = radius + stroke.width * (if (stroke.kind == PenKind.HIGHLIGHTER) 1.5f else .5f)
        val path = stroke.points
        path.any { distanceToSegment(it, start, end) <= tolerance * tolerance } ||
            path.zipWithNext().any { (a, b) ->
                segmentsCross(a, b, start, end) || distanceToSegment(start, a, b) <= tolerance * tolerance ||
                    distanceToSegment(end, a, b) <= tolerance * tolerance
            }
    }.map { it.id }.toSet()

    fun selectionBounds(ids: Set<Long>): InkBounds? {
        val chosen = strokes.filter { it.id in ids }.flatMap { it.points }
        val boxes = textBoxes.filter { it.id in ids }
        val body = bodyFrame?.takeIf { MAIN_TEXT_ID in ids }?.let { listOf(InkPoint(it.x,it.y), InkPoint(it.x+it.width,it.y+it.height)) }.orEmpty()
        val all = chosen + body + boxes.flatMap { listOf(InkPoint(it.x, it.y), InkPoint(it.x + it.width, it.y + it.height)) }
        if (all.isEmpty()) return null
        return InkBounds(all.minOf { it.x }, all.minOf { it.y }, all.maxOf { it.x }, all.maxOf { it.y })
    }
    fun selectRectangle(bounds: InkBounds): Set<Long> = strokes.filter { stroke ->
        stroke.points.any { bounds.contains(it.x, it.y) } || stroke.points.zipWithNext().any { (a, b) ->
            val corners = listOf(InkPoint(bounds.left, bounds.top), InkPoint(bounds.right, bounds.top),
                InkPoint(bounds.right, bounds.bottom), InkPoint(bounds.left, bounds.bottom))
            corners.indices.any { i -> segmentsCross(a, b, corners[i], corners[(i + 1) % 4]) }
        }
    }.map { it.id }.toSet() + textBoxes.filter { box ->
        box.x <= bounds.right && box.x + box.width >= bounds.left && box.y <= bounds.bottom && box.y + box.height >= bounds.top
    }.map { it.id } + if (bodyFrame?.let { it.x <= bounds.right && it.x+it.width >= bounds.left && it.y<=bounds.bottom && it.y+it.height>=bounds.top } == true) setOf(MAIN_TEXT_ID) else emptySet()

    fun addTextBox(text: String, y: Float = 160f): InkDocument {
        if (text.isBlank()) return this
        val width = minOf(500f, pageWidth - 120f)
        val height = maxOf(140f, text.lines().sumOf { maxOf(1, (it.length * 27 / width).toInt() + 1) } * 64f)
        return copy(textBoxes = textBoxes + NoteTextBox(nextId, text, 60f, y, width, height), nextId = nextId + 1,
            height = maxOf(this.height, y + height + 100))
    }

    /** Group transforms keep pressure, ordering and colours, clamp to the paper, and grow its height. */
    fun transformSelection(ids: Set<Long>, dx: Float = 0f, dy: Float = 0f, factor: Float = 1f): InkDocument {
        if (!dx.isFinite() || !dy.isFinite() || !factor.isFinite()) return this
        val bounds = selectionBounds(ids) ?: return this
        val scale = factor.coerceIn(.1f, minOf(10f, pageWidth / maxOf(1f, bounds.right - bounds.left)))
        val x = (bounds.left + dx).coerceIn(0f, maxOf(0f, pageWidth - (bounds.right - bounds.left) * scale))
        val y = maxOf(0f, bounds.top + dy)
        val result = strokes.map { stroke -> if (stroke.id !in ids) stroke else stroke.copy(
            width = (stroke.width * scale).coerceIn(1f, 32f), points = stroke.points.map {
                it.copy(x = x + (it.x - bounds.left) * scale, y = y + (it.y - bounds.top) * scale)
            }) }
        val boxes = textBoxes.map { box -> if (box.id !in ids) box else box.copy(
            x = x + (box.x - bounds.left) * scale, y = y + (box.y - bounds.top) * scale,
            width = box.width * scale, height = box.height * scale, size = box.size * scale) }
        val frame = bodyFrame?.let { f -> if (MAIN_TEXT_ID !in ids) f else f.copy(
            x=x+(f.x-bounds.left)*scale, y=y+(f.y-bounds.top)*scale, width=f.width*scale, height=f.height*scale, scale=f.scale*scale) }
        return copy(strokes = result, textBoxes = boxes, bodyFrame = frame, height = max(height, y + (bounds.bottom - bounds.top) * scale + 100))
    }
    fun duplicateSelection(ids: Set<Long>): InkDocument {
        val chosen = strokes.filter { it.id in ids }
        val copies = chosen.mapIndexed { index, stroke -> stroke.copy(id = nextId + index) }
        val copyIds = copies.map { it.id }.toSet()
        val boxes = textBoxes.filter { it.id in ids }.mapIndexed { i, box -> box.copy(id = nextId + copies.size + i) }
        return copy(strokes = strokes + copies, textBoxes = textBoxes + boxes, nextId = nextId + copies.size + boxes.size)
            .transformSelection(copyIds + boxes.map { it.id }, 40f, 60f)
    }
    fun resizePage(width: Float, newHeight: Float): InkDocument {
        val contentRight = maxOf(strokes.flatMap { it.points }.maxOfOrNull { it.x } ?: 0f, areas.maxOfOrNull { it.right } ?: 0f, textBoxes.maxOfOrNull { it.x + it.width } ?: 0f, bodyFrame?.let { it.x+it.width } ?: 0f)
        val contentBottom = maxOf(strokes.flatMap { it.points }.maxOfOrNull { it.y } ?: 0f, areas.maxOfOrNull { it.bottom } ?: 0f, textBoxes.maxOfOrNull { it.y + it.height } ?: 0f, bodyFrame?.let { it.y+it.height } ?: 0f)
        return copy(pageWidth = maxOf(width.coerceIn(600f, 2400f), contentRight + 32), height = maxOf(newHeight.coerceIn(1000f, 20000f), contentBottom + 100))
    }

    companion object { const val PAGE_WIDTH = 1000f; const val MAIN_TEXT_ID = 0L }
}

data class InkBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

private fun distanceToSegment(p: InkPoint, a: InkPoint, b: InkPoint): Float {
    val dx = b.x - a.x; val dy = b.y - a.y
    val length = dx * dx + dy * dy
    val t = if (length == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / length).coerceIn(0f, 1f)
    val x = p.x - (a.x + t * dx); val y = p.y - (a.y + t * dy)
    return x * x + y * y
}

private fun segmentsCross(a: InkPoint, b: InkPoint, c: InkPoint, d: InkPoint): Boolean {
    fun cross(p: InkPoint, q: InkPoint, r: InkPoint) = (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
    val abC = cross(a, b, c); val abD = cross(a, b, d)
    val cdA = cross(c, d, a); val cdB = cross(c, d, b)
    return ((abC > 0 && abD < 0) || (abC < 0 && abD > 0)) &&
        ((cdA > 0 && cdB < 0) || (cdA < 0 && cdB > 0))
}

/** One gesture is one undo step; immutable strokes are shared across history snapshots. */
class InkHistory(initial: InkDocument = InkDocument(), private val limit: Int = 50) {
    var document: InkDocument = initial; private set
    private val past = ArrayDeque<InkDocument>()
    private val future = ArrayDeque<InkDocument>()
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()
    fun reset(value: InkDocument) { document = value; past.clear(); future.clear() }
    fun record(value: InkDocument) {
        if (value == document) return
        past.addLast(document)
        if (past.size > limit) past.removeFirst()
        document = value; future.clear()
    }
    fun undo(): InkDocument {
        if (canUndo) { future.addLast(document); document = past.removeLast() }
        return document
    }
    fun redo(): InkDocument {
        if (canRedo) { past.addLast(document); document = future.removeLast() }
        return document
    }
}
