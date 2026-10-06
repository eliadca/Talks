package com.eliadca.talks.core.doc

import com.eliadca.talks.core.text.Tokenizer

/** Splits the document into paragraphs and resolves their formatting. */
fun RichDoc.paragraphs(): List<Paragraph> {
    val out = ArrayList<Paragraph>()
    val paraSpans = spans.filter { SpanType.isParagraph(it.type) }
    var start = 0
    while (true) {
        val nl = text.indexOf('\n', start)
        val end = if (nl < 0) text.length else nl
        out += resolveParagraph(start, end, paraSpans)
        if (nl < 0) break
        start = nl + 1
    }
    return out
}

private fun covers(s: RichSpan, pStart: Int, pEnd: Int): Boolean =
    if (pStart == pEnd) {
        (s.start == s.end && s.start == pStart) || (s.start <= pStart && s.end > pStart)
    } else {
        s.start < pEnd && s.end > pStart
    }

private fun resolveParagraph(start: Int, end: Int, paraSpans: List<RichSpan>): Paragraph {
    var block = BlockType.NORMAL
    var align = Align.START
    var indent = 0
    var checked = false
    for (s in paraSpans) {
        if (!covers(s, start, end)) continue
        when (s.type) {
            SpanType.H1 -> block = BlockType.H1
            SpanType.H2 -> block = BlockType.H2
            SpanType.H3 -> block = BlockType.H3
            SpanType.BULLET -> block = BlockType.BULLET
            SpanType.NUMBER -> block = BlockType.NUMBER
            SpanType.CHECK -> { block = BlockType.CHECK; checked = s.arg != 0 }
            SpanType.QUOTE -> block = BlockType.QUOTE
            SpanType.ALIGN_CENTER -> align = Align.CENTER
            SpanType.ALIGN_END -> align = Align.END
            SpanType.ALIGN_JUSTIFY -> align = Align.JUSTIFY
            SpanType.INDENT -> indent = s.arg.coerceIn(0, 6)
        }
    }
    return Paragraph(start, end, block, align, indent, checked)
}

/**
 * Ranges the speaker is not expected to say aloud: `[bracketed]` notes, runs styled as stage
 * directions, and (unless [readHeadings]) heading paragraphs.
 */
fun RichDoc.silentRanges(readHeadings: Boolean = false): List<IntRange> {
    val out = ArrayList<IntRange>()
    for (s in spans) {
        if (s.type == SpanType.STAGE && s.end > s.start) out += s.start until s.end
    }
    if (!readHeadings) {
        for (p in paragraphs()) if (p.block.isHeading && !p.isEmpty) out += p.start until p.end
    }
    return out
}

/** Number of words the speaker is expected to say. */
fun RichDoc.spokenWordCount(readHeadings: Boolean = false): Int {
    val all = Tokenizer.bracketRanges(text) + silentRanges(readHeadings)
    return Tokenizer.script(text, all).size
}

/** Estimated speaking time in seconds at [wordsPerMinute]. */
fun RichDoc.estimatedSeconds(wordsPerMinute: Int, readHeadings: Boolean = false): Int {
    val wpm = wordsPerMinute.coerceAtLeast(40)
    return (spokenWordCount(readHeadings) * 60.0 / wpm).toInt()
}

fun RichDoc.outline(): List<OutlineEntry> =
    paragraphs().filter { it.block.isHeading && !it.isEmpty }.map {
        val level = when (it.block) { BlockType.H1 -> 1; BlockType.H2 -> 2; else -> 3 }
        OutlineEntry(level, text.substring(it.start, it.end).trim(), it.start)
    }

/** The first non-empty line, for use as a title when the user has not given one. */
fun RichDoc.firstLine(maxLength: Int = 60): String {
    val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
    return if (line.length <= maxLength) line else line.take(maxLength - 1).trimEnd() + "…"
}

/**
 * Cleans the formatting runs: clamps them to the text, drops empty inline runs, merges overlapping
 * or touching runs of the same kind, and sorts them. Run before storing so equal documents compare
 * equal.
 */
fun RichDoc.normalized(): RichDoc {
    val len = text.length
    val inline = HashMap<Pair<String, Int>, MutableList<IntArray>>()
    val paragraph = ArrayList<RichSpan>()
    for (s in spans) {
        val st = s.start.coerceIn(0, len)
        val en = s.end.coerceIn(0, len)
        if (SpanType.isParagraph(s.type)) {
            if (en >= st) paragraph += s.copy(start = st, end = en)
        } else if (s.type in SpanType.inline && en > st) {
            val key = s.type to (if (s.type == SpanType.COLOR || s.type == SpanType.HIGHLIGHT || s.type == SpanType.SIZE) s.arg else 0)
            inline.getOrPut(key) { ArrayList() } += intArrayOf(st, en)
        }
    }
    val merged = ArrayList<RichSpan>()
    for ((key, runs) in inline) {
        runs.sortBy { it[0] }
        var cs = runs[0][0]
        var ce = runs[0][1]
        for (k in 1 until runs.size) {
            val r = runs[k]
            if (r[0] <= ce) {
                if (r[1] > ce) ce = r[1]
            } else {
                merged += RichSpan(key.first, cs, ce, key.second)
                cs = r[0]; ce = r[1]
            }
        }
        merged += RichSpan(key.first, cs, ce, key.second)
    }
    // One paragraph attribute of each kind per paragraph; the last one wins.
    val dedup = LinkedHashMap<Pair<String, Int>, RichSpan>()
    for (p in paragraph) {
        val kind = when (p.type) {
            SpanType.H1, SpanType.H2, SpanType.H3, SpanType.BULLET, SpanType.NUMBER, SpanType.CHECK, SpanType.QUOTE -> "block"
            SpanType.ALIGN_CENTER, SpanType.ALIGN_END, SpanType.ALIGN_JUSTIFY -> "align"
            else -> p.type
        }
        dedup[kind to p.start] = p
    }
    val all = (merged + dedup.values).sortedWith(compareBy({ it.start }, { it.type }, { it.end }))
    return RichDoc(text, all, version)
}

/** Plain text with list markers, suitable for sharing as a .txt file. */
fun RichDoc.toPlainText(): String {
    val sb = StringBuilder()
    var number = 0
    for ((i, p) in paragraphs().withIndex()) {
        if (i > 0) sb.append('\n')
        if (p.block == BlockType.NUMBER) number++ else number = 0
        repeat(p.indent) { sb.append("    ") }
        when (p.block) {
            BlockType.BULLET -> sb.append("• ")
            BlockType.NUMBER -> sb.append(number).append(". ")
            BlockType.CHECK -> sb.append(if (p.checked) "[x] " else "[ ] ")
            BlockType.QUOTE -> sb.append("> ")
            else -> {}
        }
        sb.append(text, p.start, p.end)
    }
    return sb.toString()
}
