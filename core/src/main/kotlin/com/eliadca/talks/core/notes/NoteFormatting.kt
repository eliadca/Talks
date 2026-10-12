package com.eliadca.talks.core.notes

import kotlinx.serialization.Serializable

@Serializable
data class NoteTextStyle(
    val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false,
    val strike: Boolean = false, val size: Float = 46f, val color: Int? = null, val highlight: Int? = null,
)
@Serializable
data class NoteTextSpan(val start: Int, val end: Int, val style: NoteTextStyle)
@Serializable
data class NoteParagraph(val start: Int, val end: Int, val alignment: Int = 0)
@Serializable
data class NoteFormatting(val spans: List<NoteTextSpan> = emptyList(), val paragraphs: List<NoteParagraph> = emptyList()) {
    fun styleAt(offset: Int): NoteTextStyle = spans.lastOrNull { offset >= it.start && offset < it.end }?.style ?: NoteTextStyle()

    fun apply(start: Int, end: Int, transform: (NoteTextStyle) -> NoteTextStyle): NoteFormatting {
        if (end <= start) return this
        val boundaries = (listOf(start, end) + spans.flatMap { listOf(it.start, it.end) }).distinct().sorted()
        val result = boundaries.zipWithNext().mapNotNull { (a, b) ->
            val before = styleAt(a)
            val style = if (a >= start && b <= end) transform(before) else before
            if (a == b || style == NoteTextStyle()) null else NoteTextSpan(a, b, style)
        }
        return copy(spans = merge(result))
    }

    /** A single contiguous replacement preserves formatting on both sides and styles new typing. */
    fun replace(old: String, new: String, typing: NoteTextStyle? = null): NoteFormatting {
        if (old == new) return this
        var start = 0
        while (start < minOf(old.length, new.length) && old[start] == new[start]) start++
        var suffix = 0
        while (suffix < minOf(old.length, new.length) - start && old[old.lastIndex - suffix] == new[new.lastIndex - suffix]) suffix++
        val end = old.length - suffix
        val inserted = new.length - suffix - start
        val delta = inserted - (end - start)
        val style = typing ?: styleAt(if (start > 0) start - 1 else start)
        val rebased = spans.flatMap { span ->
            buildList {
                if (span.start < start) add(span.copy(end = minOf(span.end, start)))
                if (span.end > end) add(span.copy(start = maxOf(span.start, end) + delta, end = span.end + delta))
            }.filter { it.end > it.start }
        }.toMutableList()
        if (inserted > 0 && style != NoteTextStyle()) rebased.add(NoteTextSpan(start, start + inserted, style))
        fun offset(pos: Int) = when { pos <= start -> pos; pos >= end -> pos + delta; else -> start + inserted }
        return copy(spans = merge(rebased.sortedBy { it.start }), paragraphs = paragraphs.map {
            it.copy(start = offset(it.start), end = offset(it.end))
        }.filter { it.end > it.start })
    }

    fun slice(start: Int, end: Int): NoteFormatting = NoteFormatting(
        spans.mapNotNull { span -> val a=maxOf(span.start,start); val b=minOf(span.end,end)
            if (a<b) span.copy(start=a-start,end=b-start) else null },
        paragraphs.mapNotNull { paragraph -> val a=maxOf(paragraph.start,start); val b=minOf(paragraph.end,end)
            if (a<b) paragraph.copy(start=a-start,end=b-start) else null })

    fun align(text: String, start: Int, end: Int, alignment: Int): NoteFormatting {
        val a = text.lastIndexOf('\n', (start - 1).coerceAtLeast(-1)) + 1
        val b = text.indexOf('\n', end.coerceAtMost(text.length)).takeIf { it >= 0 } ?: text.length
        return copy(paragraphs = paragraphs.filterNot { it.start < b && it.end > a } + NoteParagraph(a, b, alignment))
    }
    private fun merge(items: List<NoteTextSpan>): List<NoteTextSpan> = buildList {
        for (item in items) {
            val last = lastOrNull()
            if (last != null && last.end == item.start && last.style == item.style) { removeAt(lastIndex); add(last.copy(end = item.end)) }
            else add(item)
        }
    }
}
