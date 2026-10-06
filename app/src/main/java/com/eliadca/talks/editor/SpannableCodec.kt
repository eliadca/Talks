package com.eliadca.talks.editor

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.eliadca.talks.core.doc.Align
import com.eliadca.talks.core.doc.BlockType
import com.eliadca.talks.core.doc.RichDoc
import com.eliadca.talks.core.doc.RichSpan
import com.eliadca.talks.core.doc.SpanType
import com.eliadca.talks.core.doc.normalized
import com.eliadca.talks.core.doc.paragraphs
import com.eliadca.talks.core.text.Tokenizer

/**
 * Converts between the stored [RichDoc] and Android's styled text.
 *
 * The editor's text always ends with an invisible [END_MARKER]. Android ignores paragraph styles on
 * an empty last line, which would hide the bullet of a new list item or the centring of an empty
 * line; the marker keeps that line from ever being empty.
 */
object SpannableCodec {

    const val END_MARKER = '​'

    /** Inline runs grow when text is typed at their end, as in any word processor. */
    const val INLINE_FLAGS = Spannable.SPAN_EXCLUSIVE_INCLUSIVE

    /**
     * Paragraph spans are re-fitted to their paragraph after every edit. Their start is inclusive so
     * that a newline typed at the very start of a list item grows the span (and is then split
     * between the two paragraphs) instead of landing outside it.
     */
    const val BLOCK_FLAGS = Spannable.SPAN_INCLUSIVE_EXCLUSIVE

    /** Builds styled text for [doc], including the end marker. */
    fun toSpannable(doc: RichDoc, style: EditorStyle, withMarker: Boolean = true): SpannableStringBuilder {
        val len = doc.text.length
        val ssb = SpannableStringBuilder(if (withMarker) doc.text + END_MARKER else doc.text)
        val limit = ssb.length

        for (s in doc.spans) {
            if (SpanType.isParagraph(s.type)) continue
            val start = s.start.coerceIn(0, len)
            val end = s.end.coerceIn(0, len)
            if (end <= start) continue
            createInline(s, style)?.let { ssb.setSpan(it, start, end, INLINE_FLAGS) }
        }

        for (p in doc.paragraphs()) {
            if (p.block == BlockType.NORMAL && p.align == Align.START && p.indent == 0) continue
            val end = minOf(limit, p.end + 1)
            ssb.setSpan(BlockSpan(p.block, p.align, p.indent, p.checked, style), p.start, end, BLOCK_FLAGS)
        }

        markBrackets(ssb, style)
        return ssb
    }

    /** Marks `[bracketed]` text as a note. */
    fun markBrackets(text: Spannable, style: EditorStyle, from: Int = 0, to: Int = text.length) {
        for (old in text.getSpans(from, to, AutoNoteSpan::class.java)) text.removeSpan(old)
        val slice = text.subSequence(from, to).toString()
        for (r in Tokenizer.bracketRanges(slice)) {
            text.setSpan(AutoNoteSpan(style), from + r.first, from + r.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun createInline(s: RichSpan, style: EditorStyle): Any? = when (s.type) {
        SpanType.BOLD -> StyleSpan(Typeface.BOLD)
        SpanType.ITALIC -> StyleSpan(Typeface.ITALIC)
        SpanType.UNDERLINE -> UnderlineSpan()
        SpanType.STRIKE -> StrikethroughSpan()
        SpanType.COLOR -> ForegroundColorSpan(s.arg)
        SpanType.HIGHLIGHT -> BackgroundColorSpan(s.arg)
        SpanType.SIZE -> RelativeSizeSpan(s.arg / 100f)
        SpanType.STAGE -> StageSpan(style)
        else -> null
    }

    /** Reads the document back out of [text], dropping the end marker and every span that is not stored. */
    fun fromSpannable(text: Spanned): RichDoc {
        val len = if (text.isNotEmpty() && text[text.length - 1] == END_MARKER) text.length - 1 else text.length
        val plain = text.subSequence(0, len).toString()
        val out = ArrayList<RichSpan>()

        for (span in text.getSpans(0, text.length, Any::class.java)) {
            val st = text.getSpanStart(span).coerceIn(0, len)
            val en = text.getSpanEnd(span).coerceIn(0, len)
            when (span) {
                is BlockSpan -> {
                    // Paragraph runs exclude the newline.
                    val paraStart = st
                    val nl = plain.indexOf('\n', paraStart)
                    val paraEnd = if (nl < 0) len else nl
                    out += blockSpans(span, paraStart, paraEnd)
                }
                is FindSpan, is FindCurrentSpan, is AutoNoteSpan -> {}
                is StageSpan -> if (en > st) out += RichSpan(SpanType.STAGE, st, en)
                is StyleSpan -> if (en > st) {
                    if (span.style and Typeface.BOLD != 0) out += RichSpan(SpanType.BOLD, st, en)
                    if (span.style and Typeface.ITALIC != 0) out += RichSpan(SpanType.ITALIC, st, en)
                }
                is UnderlineSpan -> if (en > st) out += RichSpan(SpanType.UNDERLINE, st, en)
                is StrikethroughSpan -> if (en > st) out += RichSpan(SpanType.STRIKE, st, en)
                is ForegroundColorSpan -> if (en > st) out += RichSpan(SpanType.COLOR, st, en, span.foregroundColor)
                is BackgroundColorSpan -> if (en > st) out += RichSpan(SpanType.HIGHLIGHT, st, en, span.backgroundColor)
                is RelativeSizeSpan -> if (en > st) out += RichSpan(SpanType.SIZE, st, en, Math.round(span.sizeChange * 100f))
            }
        }
        return RichDoc(plain, out).normalized()
    }

    private fun blockSpans(b: BlockSpan, start: Int, end: Int): List<RichSpan> {
        val r = ArrayList<RichSpan>(3)
        when (b.block) {
            BlockType.H1 -> r += RichSpan(SpanType.H1, start, end)
            BlockType.H2 -> r += RichSpan(SpanType.H2, start, end)
            BlockType.H3 -> r += RichSpan(SpanType.H3, start, end)
            BlockType.BULLET -> r += RichSpan(SpanType.BULLET, start, end)
            BlockType.NUMBER -> r += RichSpan(SpanType.NUMBER, start, end)
            BlockType.CHECK -> r += RichSpan(SpanType.CHECK, start, end, if (b.checked) 1 else 0)
            BlockType.QUOTE -> r += RichSpan(SpanType.QUOTE, start, end)
            BlockType.NORMAL -> {}
        }
        when (b.align) {
            Align.CENTER -> r += RichSpan(SpanType.ALIGN_CENTER, start, end)
            Align.END -> r += RichSpan(SpanType.ALIGN_END, start, end)
            Align.JUSTIFY -> r += RichSpan(SpanType.ALIGN_JUSTIFY, start, end)
            Align.START -> {}
        }
        if (b.indent > 0) r += RichSpan(SpanType.INDENT, start, end, b.indent)
        return r
    }
}
