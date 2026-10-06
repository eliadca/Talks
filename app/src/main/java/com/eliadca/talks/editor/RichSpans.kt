package com.eliadca.talks.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan
import com.eliadca.talks.core.doc.Align
import com.eliadca.talks.core.doc.BlockType

/**
 * Colours and sizes shared by every span of an editor or reader. Spans keep a reference to the
 * same instance, so changing the theme only needs a redraw.
 */
class EditorStyle(
    var density: Float,
    var accent: Int,
    var muted: Int,
    var findColor: Int = 0x66FFC107,
    var findCurrentColor: Int = 0xCCFF9800.toInt(),
    /** Background behind notes for the speaker; 0 means a faint tint of [muted]. */
    var noteColor: Int = 0,
) {
    fun dp(v: Float) = v * density
}

/**
 * Paragraph-level formatting: heading size, list marker, quote bar, indentation and alignment.
 * There is at most one per paragraph and it covers the paragraph including its newline.
 */
class BlockSpan(
    var block: BlockType,
    var align: Align,
    var indent: Int,
    var checked: Boolean,
    private val style: EditorStyle,
) : MetricAffectingSpan(), LeadingMarginSpan, AlignmentSpan {

    /** Position in a numbered list, kept up to date by the editor. */
    var number: Int = 0

    val isDefault: Boolean
        get() = block == BlockType.NORMAL && align == Align.START && indent == 0

    fun copyAttributes(): BlockSpan = BlockSpan(block, align, indent, checked, style).also { it.number = number }

    // --- character metrics (headings are bigger and bold) -----------------------------------

    private fun sizeFactor(): Float = when (block) {
        BlockType.H1 -> 1.7f
        BlockType.H2 -> 1.4f
        BlockType.H3 -> 1.18f
        else -> 1f
    }

    private fun applyMetrics(tp: TextPaint) {
        val f = sizeFactor()
        if (f != 1f) {
            tp.textSize = tp.textSize * f
            val base = tp.typeface ?: Typeface.DEFAULT
            tp.typeface = Typeface.create(base, base.style or Typeface.BOLD)
        }
    }

    override fun updateMeasureState(tp: TextPaint) = applyMetrics(tp)

    override fun updateDrawState(tp: TextPaint) {
        applyMetrics(tp)
        if (block == BlockType.CHECK && checked) {
            tp.isStrikeThruText = true
            tp.color = (tp.color and 0x00FFFFFF) or (0x99 shl 24)
        }
        if (block == BlockType.QUOTE) {
            val base = tp.typeface ?: Typeface.DEFAULT
            tp.typeface = Typeface.create(base, base.style or Typeface.ITALIC)
        }
    }

    // --- margin: indentation + marker ---------------------------------------------------------

    private fun indentPx() = style.dp(INDENT_DP) * indent

    private fun markerPx(): Float = when (block) {
        BlockType.BULLET, BlockType.CHECK -> style.dp(28f)
        BlockType.NUMBER -> style.dp(if (number >= 100) 44f else if (number >= 10) 36f else 30f)
        BlockType.QUOTE -> style.dp(18f)
        else -> 0f
    }

    override fun getLeadingMargin(first: Boolean): Int = (indentPx() + markerPx()).toInt()

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout,
    ) {
        val oldColor = p.color
        val oldStyle = p.style
        val oldStroke = p.strokeWidth
        val oldAlign = p.textAlign
        val ind = indentPx()
        val marker = markerPx()
        try {
            when (block) {
                BlockType.BULLET -> if (first) {
                    p.style = Paint.Style.FILL
                    val r = p.textSize * 0.13f
                    val cx = x + dir * (ind + marker * 0.4f)
                    c.drawCircle(cx, baseline - p.textSize * 0.32f, r, p)
                }
                BlockType.NUMBER -> if (first) {
                    p.style = Paint.Style.FILL
                    p.textAlign = if (dir > 0) Paint.Align.RIGHT else Paint.Align.LEFT
                    c.drawText("$number.", x + dir * (ind + marker - style.dp(6f)), baseline.toFloat(), p)
                }
                BlockType.CHECK -> if (first) drawCheckbox(c, p, x + dir * (ind + style.dp(3f)), baseline)
                BlockType.QUOTE -> {
                    p.style = Paint.Style.FILL
                    p.color = style.accent
                    val left = x + dir * (ind + style.dp(2f))
                    c.drawRect(left, top.toFloat(), left + dir * style.dp(3f), bottom.toFloat(), p)
                }
                else -> {}
            }
        } finally {
            p.color = oldColor
            p.style = oldStyle
            p.strokeWidth = oldStroke
            p.textAlign = oldAlign
        }
    }

    private fun drawCheckbox(c: Canvas, p: Paint, left: Float, baseline: Int) {
        val size = p.textSize * 0.78f
        val top = baseline - size * 1.02f
        val oldAa = p.isAntiAlias
        p.isAntiAlias = true
        p.strokeWidth = style.dp(1.8f)
        if (checked) {
            p.style = Paint.Style.FILL
            p.color = style.accent
            c.drawRoundRect(left, top, left + size, top + size, size * 0.2f, size * 0.2f, p)
            p.style = Paint.Style.STROKE
            // White on a deep accent, near-black on the light accent of the dark theme.
            p.color = if (androidx.core.graphics.ColorUtils.calculateLuminance(style.accent) > 0.5) 0xFF1A1B22.toInt() else 0xFFFFFFFF.toInt()
            p.strokeCap = Paint.Cap.ROUND
            val path = android.graphics.Path().apply {
                moveTo(left + size * 0.22f, top + size * 0.54f)
                lineTo(left + size * 0.43f, top + size * 0.74f)
                lineTo(left + size * 0.79f, top + size * 0.28f)
            }
            c.drawPath(path, p)
        } else {
            p.style = Paint.Style.STROKE
            p.color = style.accent
            c.drawRoundRect(left, top, left + size, top + size, size * 0.2f, size * 0.2f, p)
        }
        p.isAntiAlias = oldAa
    }

    /** The horizontal extent (relative to the left text edge) of the checkbox, for tap detection. */
    fun checkboxBounds(): ClosedFloatingPointRange<Float> = indentPx()..(indentPx() + markerPx())

    override fun getAlignment(): Layout.Alignment = when (align) {
        Align.CENTER -> Layout.Alignment.ALIGN_CENTER
        Align.END -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
    }

    companion object {
        const val INDENT_DP = 26f
    }
}

/** Text the speaker is not meant to read aloud: shown muted and in italics. */
open class NoteSpan(private val style: EditorStyle) : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint) {
        tp.color = style.muted
        val base = tp.typeface ?: Typeface.DEFAULT
        tp.typeface = Typeface.create(base, base.style or Typeface.ITALIC)
    }
}

/** A note the user marked explicitly; stored in the document. */
class StageSpan(style: EditorStyle) : NoteSpan(style)

/** `[bracketed]` text detected automatically; not stored, because the brackets already say it. */
class AutoNoteSpan(style: EditorStyle) : NoteSpan(style)

/** Highlight for every match of the find bar. Never stored. */
class FindSpan(color: Int) : BackgroundColorSpan(color)

/** Highlight for the match the find bar is currently on. Never stored. */
class FindCurrentSpan(color: Int) : BackgroundColorSpan(color)
