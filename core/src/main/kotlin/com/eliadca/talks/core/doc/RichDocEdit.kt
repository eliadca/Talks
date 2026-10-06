package com.eliadca.talks.core.doc

/** The paragraph-level formatting of one paragraph. */
private data class ParaFormat(val block: BlockType, val align: Align, val indent: Int, val checked: Boolean) {
    val isDefault get() = block == BlockType.NORMAL && align == Align.START && indent == 0

    companion object {
        val DEFAULT = ParaFormat(BlockType.NORMAL, Align.START, 0, false)
    }
}

private val Paragraph.format get() = ParaFormat(block, align, indent, checked)

/** The runs that give a paragraph over [start, end) the formatting [f]. */
private fun spansFor(f: ParaFormat, start: Int, end: Int): List<RichSpan> {
    val r = ArrayList<RichSpan>(3)
    when (f.block) {
        BlockType.H1 -> r += RichSpan(SpanType.H1, start, end)
        BlockType.H2 -> r += RichSpan(SpanType.H2, start, end)
        BlockType.H3 -> r += RichSpan(SpanType.H3, start, end)
        BlockType.BULLET -> r += RichSpan(SpanType.BULLET, start, end)
        BlockType.NUMBER -> r += RichSpan(SpanType.NUMBER, start, end)
        BlockType.CHECK -> r += RichSpan(SpanType.CHECK, start, end, if (f.checked) 1 else 0)
        BlockType.QUOTE -> r += RichSpan(SpanType.QUOTE, start, end)
        BlockType.NORMAL -> {}
    }
    when (f.align) {
        Align.CENTER -> r += RichSpan(SpanType.ALIGN_CENTER, start, end)
        Align.END -> r += RichSpan(SpanType.ALIGN_END, start, end)
        Align.JUSTIFY -> r += RichSpan(SpanType.ALIGN_JUSTIFY, start, end)
        Align.START -> {}
    }
    if (f.indent > 0) r += RichSpan(SpanType.INDENT, start, end, f.indent)
    return r
}

/** Builds the paragraph runs of [text], one format per paragraph. */
private fun paragraphSpans(text: String, formats: List<ParaFormat>): List<RichSpan> {
    val out = ArrayList<RichSpan>()
    var start = 0
    var i = 0
    while (true) {
        val nl = text.indexOf('\n', start)
        val end = if (nl < 0) text.length else nl
        formats.getOrNull(i)?.let { out += spansFor(it, start, end) }
        if (nl < 0) break
        start = nl + 1
        i++
    }
    return out
}

/**
 * The part [start, end) of the document as a document of its own, with its formatting (what the
 * editor copies). A paragraph cut at the very edge of the range keeps no paragraph formatting.
 */
fun RichDoc.slice(start: Int, end: Int): RichDoc {
    val s = start.coerceIn(0, text.length)
    val e = end.coerceIn(s, text.length)
    val part = text.substring(s, e)
    val inline = spans.filter { !SpanType.isParagraph(it.type) && it.end > s && it.start < e }
        .map { it.copy(start = maxOf(it.start, s) - s, end = minOf(it.end, e) - s) }
    val formats = paragraphs()
        .filter { it.end >= s && it.start <= e }
        .map { p ->
            val touched = minOf(p.end, e) > maxOf(p.start, s) || (p.isEmpty && p.start > s && p.start < e)
            if (touched) p.format else ParaFormat.DEFAULT
        }
    return RichDoc(part, inline + paragraphSpans(part, formats), version).normalized()
}

/**
 * Replaces [start, end) with [fragment], keeping the fragment's own formatting (what the editor
 * does when it pastes formatted text).
 *
 * Text around the range keeps its formatting; inline runs that cover the range are cut around the
 * fragment. The first pasted line joins the paragraph where the range starts and the last one the
 * paragraph where it ends, as in any word processor: a pasted heading or list item keeps its
 * formatting where it starts a line (always on an empty line), and otherwise takes that paragraph's.
 */
fun RichDoc.replaceRange(start: Int, end: Int, fragment: RichDoc): RichDoc {
    val s = start.coerceIn(0, text.length)
    val e = end.coerceIn(s, text.length)
    val frag = fragment.text
    val newText = text.substring(0, s) + frag + text.substring(e)
    val delta = frag.length - (e - s)

    // Inline runs: the document's own, cut around the fragment, and the fragment's, moved into place.
    val inline = ArrayList<RichSpan>()
    for (sp in spans) {
        if (SpanType.isParagraph(sp.type)) continue
        when {
            sp.end <= s -> inline += sp
            sp.start >= e -> inline += sp.copy(start = sp.start + delta, end = sp.end + delta)
            else -> {
                if (sp.start < s) inline += sp.copy(end = s)
                if (sp.end > e) inline += sp.copy(start = s + frag.length, end = sp.end + delta)
            }
        }
    }
    for (sp in fragment.spans) {
        if (!SpanType.isParagraph(sp.type)) inline += sp.copy(start = sp.start + s, end = sp.end + s)
    }

    // Paragraph formatting, paragraph by paragraph.
    val paras = paragraphs()
    val pa = paras.indexOfFirst { s >= it.start && s <= it.end }.coerceAtLeast(0)
    val pb = paras.indexOfFirst { e >= it.start && e <= it.end }.coerceAtLeast(pa)
    val first = paras[pa]
    val last = paras[pb]
    val prefixEmpty = s == first.start
    val suffixEmpty = e == last.end
    val fp = fragment.paragraphs()
    val formats = ArrayList<ParaFormat>(paras.size + fp.size)
    for (k in 0 until pa) formats += paras[k].format
    if (fp.size == 1) {
        val f = fp[0].format
        formats += if (prefixEmpty && suffixEmpty && !f.isDefault) f else first.format
    } else {
        val f0 = fp.first().format
        formats += if (prefixEmpty && !f0.isDefault) f0 else first.format
        for (k in 1 until fp.size - 1) formats += fp[k].format
        val fl = fp.last()
        formats += when {
            !fl.isEmpty && !fl.format.isDefault -> fl.format
            suffixEmpty -> fl.format
            else -> last.format
        }
    }
    for (k in pb + 1 until paras.size) formats += paras[k].format

    return RichDoc(newText, inline + paragraphSpans(newText, formats), version).normalized()
}

/** The text of the first paragraph when it is a `# ` title, or null. */
fun RichDoc.leadingTitle(): String? {
    val p = paragraphs().firstOrNull { text.substring(it.start, it.end).isNotBlank() } ?: return null
    if (p.block != BlockType.H1) return null
    return text.substring(p.start, p.end).trim().takeIf { it.isNotEmpty() }
}
