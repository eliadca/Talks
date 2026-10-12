package com.eliadca.talks.core.ink

import kotlin.math.*

/** Removes only the ink inside the swept disk, even between sparse/fast pointer samples. */
fun InkDocument.erasePart(start: InkPoint, end: InkPoint, radius: Float): InkDocument {
    if (!radius.isFinite() || radius <= 0 || !start.x.isFinite() || !start.y.isFinite() || !end.x.isFinite() || !end.y.isFinite()) return this
    var id = nextId
    var changed = false
    val result = buildList {
        for (stroke in strokes) {
            val r = radius + stroke.width * if (stroke.kind == PenKind.HIGHLIGHTER) 1.5f else .85f
            val path = stroke.points
            val left = min(start.x, end.x) - r; val right = max(start.x, end.x) + r
            val top = min(start.y, end.y) - r; val bottom = max(start.y, end.y) + r
            if (path.isEmpty() || path.maxOf { it.x } < left || path.minOf { it.x } > right || path.maxOf { it.y } < top || path.minOf { it.y } > bottom) {
                add(stroke); continue
            }
            if (path.size == 1) {
                if (pointDistance(path[0], start, end) > r * r) add(stroke) else changed = true
                continue
            }
            val pieces = mutableListOf<List<InkPoint>>()
            var current = mutableListOf<InkPoint>()
            fun finish() { if (current.isNotEmpty()) pieces.add(current.toList()); current = mutableListOf() }
            var hit = false
            for ((a, b) in path.zipWithNext()) {
                val erased = capsuleIntervals(a, b, start, end, r)
                if (erased.isEmpty()) {
                    if (current.isEmpty()) current.add(a)
                    if (current.lastOrNull() != b) current.add(b)
                    continue
                }
                hit = true
                var t = 0f
                for ((lo, hi) in erased) {
                    if (lo > t + 1e-5f) {
                        val from = mix(a, b, t); val to = mix(a, b, lo)
                        if (current.lastOrNull() != from) current.add(from)
                        current.add(to)
                    }
                    finish(); t = max(t, hi)
                }
                if (t < 1f - 1e-5f) { current.add(mix(a, b, t)); current.add(b) }
            }
            finish()
            if (!hit) { add(stroke); continue }
            changed = true
            pieces.filter { piece -> piece.size > 1 && piece.zipWithNext().any { (a,b) -> abs(a.x-b.x)+abs(a.y-b.y) > .001f } }
                .forEachIndexed { index, piece -> add(stroke.copy(id = if (index == 0) stroke.id else id++, points = piece)) }
        }
    }
    return if (changed) copy(strokes = result, nextId = id) else this
}

private fun mix(a: InkPoint, b: InkPoint, t: Float) = InkPoint(a.x + (b.x-a.x)*t, a.y + (b.y-a.y)*t, a.pressure + (b.pressure-a.pressure)*t)
private fun pointDistance(p: InkPoint, a: InkPoint, b: InkPoint): Float {
    val dx=b.x-a.x; val dy=b.y-a.y; val length=dx*dx+dy*dy
    val t = if (length < 1e-10f) 0f else (((p.x-a.x)*dx+(p.y-a.y)*dy)/length).coerceIn(0f,1f)
    val x=p.x-a.x-t*dx; val y=p.y-a.y-t*dy
    return x*x+y*y
}
private fun capsuleIntervals(a: InkPoint, b: InkPoint, c: InkPoint, d: InkPoint, r: Float): List<Pair<Float,Float>> {
    val intervals = mutableListOf<Pair<Float,Float>>()
    val dx=b.x-a.x; val dy=b.y-a.y; val aa=dx*dx+dy*dy
    fun circle(p: InkPoint) {
        val x=a.x-p.x; val y=a.y-p.y
        if (aa < 1e-10f) { if (x*x+y*y<=r*r) intervals.add(0f to 1f); return }
        val bb=2f*(x*dx+y*dy); val cc=x*x+y*y-r*r
        val disc=bb*bb-4f*aa*cc
        if (disc < 0) return
        val root=sqrt(max(0f,disc)); val lo=max(0f,(-bb-root)/(2f*aa)); val hi=min(1f,(-bb+root)/(2f*aa))
        if (hi>=lo) intervals.add(lo to hi)
    }
    circle(c); circle(d)
    val ex=d.x-c.x; val ey=d.y-c.y; val length=hypot(ex,ey)
    if (length > 1e-5f) {
        val ux=ex/length; val uy=ey/length
        val x=(a.x-c.x)*ux+(a.y-c.y)*uy; val y=-(a.x-c.x)*uy+(a.y-c.y)*ux
        val vx=dx*ux+dy*uy; val vy=-dx*uy+dy*ux
        var lo=0f; var hi=1f
        fun clip(value: Float, velocity: Float, min: Float, max: Float) {
            if (abs(velocity) < 1e-8f) { if (value < min || value > max) { lo=1f;hi=0f } }
            else { val t1=(min-value)/velocity; val t2=(max-value)/velocity; lo=maxOf(lo,minOf(t1,t2)); hi=minOf(hi,maxOf(t1,t2)) }
        }
        clip(x,vx,0f,length); clip(y,vy,-r,r)
        if (hi>=lo) intervals.add(lo to hi)
    }
    val merged=mutableListOf<Pair<Float,Float>>()
    for (range in intervals.sortedBy { it.first }) {
        val last=merged.lastOrNull()
        if (last!=null && range.first<=last.second+1e-5f) merged[merged.lastIndex]=last.first to max(last.second,range.second)
        else merged.add(range)
    }
    return merged
}
