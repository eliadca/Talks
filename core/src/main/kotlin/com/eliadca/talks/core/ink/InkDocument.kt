package com.eliadca.talks.core.ink

import kotlinx.serialization.Serializable
import kotlin.math.max

/** Coordinates belong to a fixed-width page, so ink keeps its position when the screen changes. */
@Serializable
data class InkPoint(val x: Float, val y: Float, val pressure: Float = .5f)

@Serializable
enum class PenKind { BALLPOINT, PENCIL, FOUNTAIN, HIGHLIGHTER }

@Serializable
data class InkStroke(val id: Long, val kind: PenKind, val color: Int, val width: Float, val points: List<InkPoint>)

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
) {
    fun addStroke(kind: PenKind, color: Int, width: Float, points: List<InkPoint>): InkDocument {
        val valid = points.filter { it.x.isFinite() && it.y.isFinite() && it.pressure.isFinite() }
            .map { it.copy(x = it.x.coerceIn(0f, PAGE_WIDTH), y = max(0f, it.y), pressure = it.pressure.coerceIn(0f, 1f)) }
        if (valid.isEmpty()) return this
        return copy(strokes = strokes + InkStroke(nextId, kind, color, width.coerceIn(1f, 32f), valid),
            height = max(height, valid.maxOf { it.y } + 100), nextId = nextId + 1)
    }

    fun addArea(start: InkPoint, end: InkPoint): InkDocument {
        val left = minOf(start.x, end.x).coerceIn(0f, PAGE_WIDTH)
        val right = maxOf(start.x, end.x).coerceIn(0f, PAGE_WIDTH)
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

    companion object { const val PAGE_WIDTH = 1000f }
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
