package com.eliadca.talks.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.Spanned

/**
 * Draws a soft rounded background behind notes for the speaker ([NoteSpan]), so they read as
 * asides and not as part of the speech. Call it before the text is drawn, with the canvas at the
 * origin of [layout].
 */
object NoteDecor {
    private const val ALPHA = 0x2E
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun draw(canvas: Canvas, layout: Layout, text: CharSequence, style: EditorStyle) {
        val sp = text as? Spanned ?: return
        val spans = sp.getSpans(0, sp.length, NoteSpan::class.java)
        if (spans.isEmpty()) return
        val ranges = spans
            .map { sp.getSpanStart(it) to sp.getSpanEnd(it) }
            .filter { it.second > it.first }
            .sortedBy { it.first }
        paint.style = Paint.Style.FILL
        paint.color = if (style.noteColor != 0) style.noteColor else (style.muted and 0x00FFFFFF) or (ALPHA shl 24)
        val radius = style.dp(7f)
        val padX = style.dp(4f)
        val len = layout.text.length
        // Overlapping notes (a marked note around a bracketed one) share one background.
        var from = -1
        var to = -1
        for ((s, e) in ranges) {
            if (s <= to) {
                to = maxOf(to, e)
            } else {
                if (to > from) drawRange(canvas, layout, from.coerceIn(0, len), to.coerceIn(0, len), padX, radius)
                from = s
                to = e
            }
        }
        if (to > from) drawRange(canvas, layout, from.coerceIn(0, len), to.coerceIn(0, len), padX, radius)
    }

    private fun drawRange(canvas: Canvas, l: Layout, s: Int, e: Int, padX: Float, radius: Float) {
        if (e <= s) return
        val first = l.getLineForOffset(s)
        val last = l.getLineForOffset(e - 1)
        for (line in first..last) {
            val left = if (line == first) l.getPrimaryHorizontal(s) else l.getLineLeft(line)
            var right = if (line == last) l.getPrimaryHorizontal(e) else l.getLineRight(line)
            // At the end of a wrapped line the offset reports the start of the next line.
            if (right <= left) right = l.getLineRight(line)
            if (right <= left) continue
            val baseline = l.getLineBaseline(line).toFloat()
            rect.set(left - padX, baseline + l.getLineAscent(line) * 0.95f, right + padX, baseline + l.getLineDescent(line))
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }
}
