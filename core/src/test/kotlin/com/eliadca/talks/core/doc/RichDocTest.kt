package com.eliadca.talks.core.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichDocTest {

    @Test fun jsonRoundTrips() {
        val doc = Markup.parse("# Título\nHola **mundo** y *todos*\n- uno\n- dos")
        val back = RichDoc.fromJson(doc.toJson())
        assertEquals(doc, back)
    }

    @Test fun damagedJsonGivesEmptyDocument() {
        assertEquals(RichDoc.EMPTY, RichDoc.fromJson("{not json"))
    }

    @Test fun markupBlocksAndInline() {
        val doc = Markup.parse("# Uno\n## Dos\ntexto **negrita** normal\n1. a\n2. b\n- [x] hecho\n> cita")
        val p = doc.paragraphs()
        assertEquals(listOf(BlockType.H1, BlockType.H2, BlockType.NORMAL, BlockType.NUMBER, BlockType.NUMBER, BlockType.CHECK, BlockType.QUOTE), p.map { it.block })
        assertTrue(p[5].checked)
        val bold = doc.spans.single { it.type == SpanType.BOLD }
        assertEquals("negrita", doc.text.substring(bold.start, bold.end))
    }

    @Test fun unmatchedMarkersStayLiteral() {
        val doc = Markup.parse("2 * 3 = 6 y snake_case")
        assertEquals("2 * 3 = 6 y snake_case", doc.text)
        assertTrue(doc.spans.isEmpty())
    }

    @Test fun silentRangesCoverHeadingsAndStage() {
        val doc = Markup.parse("# Título\nDigo esto").let {
            it.copy(spans = it.spans + RichSpan(SpanType.STAGE, 9, 13))
        }
        val silent = doc.silentRanges(readHeadings = false)
        assertTrue(silent.any { 0 in it })
        assertTrue(silent.any { 10 in it })
        assertTrue(doc.silentRanges(readHeadings = true).none { 0 in it })
    }

    @Test fun wordCountIgnoresNotesAndHeadings() {
        val doc = Markup.parse("# Título largo\nUno dos tres [pausa] cuatro")
        assertEquals(4, doc.spokenWordCount())
        assertEquals(6, doc.spokenWordCount(readHeadings = true))
        assertEquals(120, doc.copy(text = "palabra ".repeat(260), spans = emptyList()).estimatedSeconds(130))
    }

    @Test fun outlineListsHeadings() {
        val doc = Markup.parse("# A\ntexto\n## B\n### C")
        assertEquals(listOf(1 to "A", 2 to "B", 3 to "C"), doc.outline().map { it.level to it.title })
    }

    @Test fun normalizedMergesTouchingRunsAndDropsEmpty() {
        val doc = RichDoc(
            "abcdefghij",
            listOf(
                RichSpan(SpanType.BOLD, 0, 3), RichSpan(SpanType.BOLD, 3, 6), RichSpan(SpanType.BOLD, 8, 8),
                RichSpan(SpanType.ITALIC, 2, 99), RichSpan(SpanType.COLOR, 0, 2, 5), RichSpan(SpanType.COLOR, 2, 4, 6),
            ),
        ).normalized()
        assertEquals(RichSpan(SpanType.BOLD, 0, 6), doc.spans.single { it.type == SpanType.BOLD })
        assertEquals(RichSpan(SpanType.ITALIC, 2, 10), doc.spans.single { it.type == SpanType.ITALIC })
        assertEquals(2, doc.spans.count { it.type == SpanType.COLOR })
    }

    @Test fun emptyListParagraphIsKept() {
        val doc = RichDoc("uno\n\ndos", listOf(RichSpan(SpanType.BULLET, 4, 4))).normalized()
        val p = doc.paragraphs()
        assertEquals(3, p.size)
        assertEquals(BlockType.NORMAL, p[0].block)
        assertEquals(BlockType.BULLET, p[1].block)
        assertEquals(BlockType.NORMAL, p[2].block)
    }

    @Test fun plainTextNumbersListsAndMarkdown() {
        val doc = Markup.parse("1. a\n2. b\ntexto\n1. c\n- x")
        assertEquals("1. a\n2. b\ntexto\n1. c\n• x", doc.toPlainText())
        val md = Markup.parse("# T\nhola **fuerte** y *suave*").toMarkdown()
        assertEquals("# T\nhola **fuerte** y *suave*", md)
    }

    @Test fun sampleContentParses() {
        val s = com.eliadca.talks.core.sample.SampleContent.practice
        assertTrue(s.spokenWordCount() > 450)
        assertTrue(s.outline().size >= 5)
    }
}
