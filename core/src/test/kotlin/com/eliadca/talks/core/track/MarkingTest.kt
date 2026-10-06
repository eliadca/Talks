package com.eliadca.talks.core.track

import com.eliadca.talks.core.doc.Markup
import com.eliadca.talks.core.doc.silentRanges
import com.eliadca.talks.core.text.Boundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkingTest {

    private val ix = SampleScripts.practice

    private fun words(from: Int, until: Int) = ix.tokens.subList(from, until).joinToString(" ") { it.norm }

    private fun blocks(unit: MarkUnit): List<IntRange> {
        val out = ArrayList<IntRange>()
        var p = 0
        while (p < ix.size) {
            val e = ix.nextBlockStart(unit, p)
            out += p until e
            p = e
        }
        return out
    }

    @Test fun phrasesAreWholeAndTheirSizeIsComfortable() {
        val phrases = blocks(MarkUnit.PHRASE)
        val first = phrases.take(4).map { words(it.first, it.last + 1) }
        assertEquals("buenos dias a todos", first[0])
        assertEquals("gracias por estar aqui", first[1])
        // "su tiempo." is too short alone and joins the phrase before it in the same sentence.
        assertEquals("y gracias por regalarme lo mas valioso que tienen su tiempo", first[2])
        for (r in phrases) {
            val len = r.last - r.first + 1
            assertTrue("phrase of $len words: ${words(r.first, r.last + 1)}", len <= 16)
            // A phrase never runs on past the end of a sentence.
            for (t in r.first until r.last) assertTrue(words(r.first, r.last + 1), ix.tokens[t].boundary < Boundary.SENTENCE)
        }
        val short = phrases.count { it.last - it.first + 1 < 3 }
        assertTrue("too many tiny phrases: $short of ${phrases.size}", short * 10 <= phrases.size)
    }

    @Test fun sentencesAreLongerBlocks() {
        val sentences = blocks(MarkUnit.SENTENCE)
        assertTrue(sentences.size < blocks(MarkUnit.PHRASE).size)
        for (r in sentences) assertTrue(r.last - r.first + 1 <= 30)
        assertEquals("buenos dias a todos", words(sentences[0].first, sentences[0].last + 1))
    }

    @Test fun thePhraseStaysStillWhileItIsBeingSaid() {
        for (r in blocks(MarkUnit.PHRASE)) {
            for (p in r) {
                val m = ix.mark(p, Marking())
                assertEquals(r.first, m.from)
                assertEquals(r.last + 1, m.until)
                assertEquals(r.first, m.dimUntil)
                assertEquals(p, m.focus)
            }
        }
    }

    @Test fun nothingUnsaidIsEverDimmedAndTheMarkStaysNearTheVoice() {
        for (unit in MarkUnit.entries) {
            for (lead in Marking.MIN_LEAD..Marking.MAX_LEAD) {
                val marking = Marking(unit, lead)
                for (p in 0..ix.size) {
                    val m = ix.mark(p, marking)
                    assertTrue("$unit lead=$lead pos=$p dim=${m.dimUntil}", m.dimUntil <= p)
                    if (p >= ix.size) {
                        assertEquals(m.from, m.until)
                        continue
                    }
                    if (unit == MarkUnit.PHRASE || unit == MarkUnit.SENTENCE) {
                        val voice = ix.blockStart(unit, p)
                        val shown = m.from
                        val before = ix.previousBlockStart(unit, voice)
                        val after = ix.nextBlockStart(unit, p)
                        assertTrue("$unit lead=$lead pos=$p shows $shown", shown == voice || shown == before || shown == after)
                    }
                }
            }
        }
    }

    @Test fun movingAheadShowsTheNextPhraseBeforeTheLastWordIsSaid() {
        val second = ix.nextBlockStart(MarkUnit.PHRASE, 0) // "gracias…"
        val m = ix.mark(second - 1, Marking(lead = 1)) // the speaker is about to say "todos"
        assertEquals(second, m.from)
        assertEquals(0, m.dimUntil) // "todos" is not dimmed: it has not been said
        val behind = ix.mark(second, Marking(lead = -1))
        assertEquals(0, behind.from)
    }

    @Test fun wordWindowsAndNoMarking() {
        for (p in 0 until ix.size) {
            val m = ix.mark(p, Marking(MarkUnit.WORD))
            assertEquals(p, m.from)
            assertTrue(m.until > p)
            for (t in p until m.until - 1) assertTrue(ix.tokens[t].boundary < Boundary.SENTENCE)
            val none = ix.mark(p, Marking(MarkUnit.NONE))
            assertEquals(none.from, none.until)
            assertEquals(ix.blockStart(MarkUnit.PHRASE, p), none.dimUntil)
        }
    }

    @Test fun notesHeadingsAndLineBreaksAreNeverMarked() {
        for (unit in listOf(MarkUnit.PHRASE, MarkUnit.SENTENCE, MarkUnit.WORD)) {
            for (p in 0 until ix.size) {
                val m = ix.mark(p, Marking(unit))
                for (seg in ix.segments(m.from, m.until)) {
                    val shown = ix.text.substring(seg.start, seg.end)
                    assertFalse("$unit at $p marks «$shown»", shown.contains('[') || shown.contains(']') || shown.contains('\n'))
                }
            }
        }
        // A note in the middle of a phrase splits the marking around it.
        val doc = Markup.parse("# Título\nHoy quiero [mirar al público] hablarles de algo.")
        val small = ScriptIndex.build(doc.text, doc.silentRanges())
        val m = small.mark(0, Marking(MarkUnit.SENTENCE))
        val segs = small.segments(m.from, m.until).map { small.text.substring(it.start, it.end) }
        assertEquals(listOf("Hoy quiero", "hablarles de algo"), segs)
        // In phrase mode the note ends the phrase.
        assertEquals("Hoy quiero", small.segments(small.mark(0, Marking()).from, small.mark(0, Marking()).until).single().let { small.text.substring(it.start, it.end) })
    }

    @Test fun theEndOfTheSpeech() {
        val m = ix.mark(ix.size, Marking())
        assertEquals(Mark(ix.size, ix.size, ix.size, ix.size), m)
        assertTrue(ix.segments(m.from, m.until).isEmpty())
    }
}
