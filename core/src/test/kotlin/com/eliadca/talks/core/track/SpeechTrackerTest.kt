package com.eliadca.talks.core.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTrackerTest {

    private val text = "Buenos días a todos. Gracias por estar aquí, y gracias por regalarme lo más valioso que tienen: " +
        "su tiempo. Quiero comenzar con una pregunta sencilla. ¿Cuántas decisiones creen que han tomado desde " +
        "que se levantaron esta mañana? Piénsenlo un momento."
    private val index = ScriptIndex.build(text)

    private fun indexOf(word: String, nth: Int = 0): Int {
        var seen = 0
        for ((i, t) in index.tokens.withIndex()) if (t.norm == word) { if (seen == nth) return i; seen++ }
        error("no $word")
    }

    @Test fun followsASentenceWordByWord() {
        val t = SpeechTracker(index)
        val words = "buenos dias a todos gracias por estar aqui".split(" ")
        for (k in 1..words.size) {
            val st = t.onHypothesis(words.take(k).joinToString(" "), isFinal = false)
            assertTrue("after $k words position ${st.position}", st.position in k - 1..k + 1)
        }
        assertEquals(words.size, t.state.position)
    }

    @Test fun toleratesWrongAndMissingAndExtraWords() {
        val t = SpeechTracker(index)
        // "gracias por estar" misheard, an extra "eh", and "regalarme" dropped.
        t.onHypothesis("buenos dias a todos gracias por estas aqui eh y gracias por lo mas valioso", true)
        val expected = indexOf("valioso") + 1
        assertTrue("pos=${t.state.position} expected≈$expected", kotlin.math.abs(t.state.position - expected) <= 2)
    }

    @Test fun unknownImprovisationDoesNotMoveThePosition() {
        val t = SpeechTracker(index)
        t.onHypothesis("buenos dias a todos", true)
        val before = t.state.position
        t.onHypothesis("como les decia ayer en la reunion con el equipo de ventas y los clientes del norte", true)
        assertTrue("moved from $before to ${t.state.position}", t.state.position - before <= 4)
        // Back on script.
        t.onHypothesis("gracias por estar aqui", true)
        assertTrue(kotlin.math.abs(t.state.position - (indexOf("aqui") + 1)) <= 2)
    }

    @Test fun aLongTalkAboutSomethingElseLeavesTheMarkerWhereTheSpeakerLeftTheText() {
        val practice = SampleScripts.practice
        val t = SpeechTracker(practice)
        val words = practice.tokens.map { it.norm }
        // Read the opening two paragraphs as growing partial results, a session per sentence or so.
        val read = 40
        var k = 0
        while (k < read) {
            val end = minOf(read, k + 10)
            for (m in k + 1..end) t.onHypothesis(words.subList(k, m).joinToString(" "), isFinal = m == end)
            k = end
        }
        val left = t.state.position
        assertTrue("reading: pos=$left", left in read - 2..read)
        // Two minutes about the airport, the football match, the traffic, the weather...
        val chatter = OffTopicSimulationTest.OFF_TOPIC
        var i = 0
        var statusDuring = TrackStatus.FOLLOWING
        while (i < chatter.size) {
            val end = minOf(chatter.size, i + 12)
            for (m in i + 1..end) {
                val st = t.onHypothesis(chatter.subList(i, m).joinToString(" "), isFinal = m == end)
                assertTrue("the marker moved during the chatter: $left -> ${st.position} after '${chatter.subList(i, m).joinToString(" ")}'", kotlin.math.abs(st.position - left) <= 3)
                statusDuring = st.status
            }
            i = end
        }
        assertEquals(TrackStatus.OFF_SCRIPT, statusDuring)
        // Back to the speech, right where it was left.
        val resume = words.subList(t.state.position, t.state.position + 8)
        for (m in 1..resume.size) t.onHypothesis(resume.take(m).joinToString(" "), isFinal = m == resume.size)
        assertTrue("found again: pos=${t.state.position} expected≈${left + 8}", kotlin.math.abs(t.state.position - (left + 8)) <= 2)
        assertEquals(TrackStatus.FOLLOWING, t.state.status)
    }

    @Test fun jumpsAheadWhenTheSpeakerSkips() {
        val t = SpeechTracker(index)
        t.onHypothesis("buenos dias a todos", true)
        val skipped = "quiero comenzar con una pregunta sencilla cuantas decisiones".split(" ")
        for (k in 1..skipped.size) t.onHypothesis(skipped.take(k).joinToString(" "), isFinal = k == skipped.size)
        val expected = indexOf("decisiones") + 1
        assertTrue("pos=${t.state.position} expected≈$expected", kotlin.math.abs(t.state.position - expected) <= 2)
    }

    /** The speaker reads [words] from..until as a recogniser hears them: growing partials, a final every few words. */
    private fun read(t: SpeechTracker, words: List<String>, from: Int, until: Int, check: (Int) -> Unit = {}) {
        var k = from
        while (k < until) {
            val end = minOf(until, k + 8)
            for (m in k + 1..end) check(t.onHypothesis(words.subList(k, m).joinToString(" "), isFinal = m == end).position)
            k = end
        }
    }

    private fun say(t: SpeechTracker, phrase: String, check: (Int) -> Unit) {
        val heard = phrase.split(" ")
        for (k in 1..heard.size) check(t.onHypothesis(heard.take(k).joinToString(" "), isFinal = k == heard.size).position)
    }

    private val withReference by lazy {
        ScriptIndex.build(
            "Leamos Isaías 42:9, que dice: las cosas primeras ya han venido, y yo anuncio cosas nuevas. " +
                SampleScripts.practice.tokens.take(160).joinToString(" ") { it.norm } +
                ". Por eso, como dice Isaías 42:9, las cosas primeras ya han venido. " +
                SampleScripts.practice.tokens.drop(160).take(80).joinToString(" ") { it.norm },
        )
    }

    @Test fun aPhraseSaidAgainNeverPullsTheMarkerBack() {
        val ix = withReference
        val words = ix.tokens.map { it.norm }
        for (phrase in listOf("Isaías 42:9", "como dice Isaías 42:9", "las cosas primeras ya han venido", "y yo anuncio cosas nuevas")) {
            val t = SpeechTracker(ix)
            val here = 110
            read(t, words, 0, here)
            assertTrue("read up to $here: ${t.state.position}", kotlin.math.abs(t.state.position - here) <= 2)
            say(t, phrase) { assertTrue("«$phrase» pulled the marker back to $it", it >= here - 2) }
            // ...and the speaker goes on where they were.
            read(t, words, here, here + 12) { assertTrue("«$phrase», then reading: back to $it", it >= here - 2) }
            assertTrue("«$phrase»: lost at ${t.state.position}", kotlin.math.abs(t.state.position - (here + 12)) <= 3)
        }
    }

    @Test fun aPhraseSaidBeforeItsTimeDoesNotPushTheMarkerAhead() {
        val ix = withReference
        val words = ix.tokens.map { it.norm }
        val later = words.withIndex().filter { it.value == "isaias" }[1].index
        assertTrue("second reference at $later", later in 150..200)
        val t = SpeechTracker(ix)
        val here = later - 30
        read(t, words, 0, here)
        // The quote may show where it is written ahead while it is being said...
        val seen = ArrayList<Int>()
        say(t, "como dice Isaías 42:9 las cosas primeras ya han venido") { seen += it }
        // ...but as soon as the speaker goes on where they were, the marker is back there.
        var k = 0
        read(t, words, here, here + 12) {
            k++
            seen += it
            if (k >= 4) assertTrue("still ahead at $it after $k words (here=$here, later=$later): $seen", it <= here + k + 2)
        }
        assertTrue("lost at ${t.state.position}", kotlin.math.abs(t.state.position - (here + 12)) <= 3)
    }

    @Test fun reallyGoingBackToReadAPassageAgainIsFollowed() {
        val ix = withReference
        val words = ix.tokens.map { it.norm }
        val t = SpeechTracker(ix)
        read(t, words, 0, 120)
        // The speaker lost the thread and reads two sentences again from word 40.
        read(t, words, 40, 70)
        assertTrue("did not follow back: ${t.state.position}", kotlin.math.abs(t.state.position - 70) <= 3)
    }

    @Test fun manualPositionIsHonouredAndOldSessionWordsAreIgnored() {
        val t = SpeechTracker(index)
        t.onHypothesis("buenos dias a todos gracias", false)
        val target = indexOf("pregunta")
        t.setPosition(target)
        assertEquals(target, t.state.position)
        // Same recogniser session keeps growing; its earlier words must not be replayed at the new place.
        t.onHypothesis("buenos dias a todos gracias por", false)
        assertTrue("pos=${t.state.position}", t.state.position in target..target + 2)
        t.onHypothesis("buenos dias a todos gracias por sencilla", false)
        assertTrue("pos=${t.state.position}", kotlin.math.abs(t.state.position - (indexOf("sencilla") + 1)) <= 3)
    }

    @Test fun reportsFinishedAtTheEnd() {
        val t = SpeechTracker(index)
        t.setPosition(index.size - 3)
        t.onHypothesis("un momento", true)
        t.onHypothesis("piensenlo un momento", true)
        assertTrue(t.state.finished)
    }

    @Test fun emptyScriptIsHarmless() {
        val t = SpeechTracker(ScriptIndex.build("[solo una nota]"))
        assertTrue(t.state.finished)
        t.onHypothesis("hola", true)
        assertEquals(0, t.state.position)
    }

    @Test fun resetStartsOver() {
        val t = SpeechTracker(index)
        t.onHypothesis("buenos dias a todos gracias por estar aqui", true)
        t.reset(0)
        assertEquals(0, t.state.position)
    }

    // --- line starts --------------------------------------------------------------------------

    private enum class Where { SAME_PARTIAL, SAME_FINAL, NEW_SESSION }

    /** Reads the sentence [from, until) of [ix] as growing partials, then a stray word, as [where] says. */
    private fun readThenStray(ix: ScriptIndex, from: Int, until: Int, stray: String, where: Where): SpeechTracker {
        val t = SpeechTracker(ix)
        t.reset(from)
        val words = ix.tokens.subList(from, until).map { it.norm }
        for (k in 1..words.size) t.onHypothesis(words.take(k).joinToString(" "), isFinal = false)
        val said = words.joinToString(" ")
        when (where) {
            Where.SAME_PARTIAL -> t.onHypothesis("$said $stray", isFinal = false)
            Where.SAME_FINAL -> t.onHypothesis("$said $stray", isFinal = true)
            Where.NEW_SESSION -> {
                t.onHypothesis(said, isFinal = true)
                t.onHypothesis(stray, isFinal = false)
            }
        }
        return t
    }

    @Test fun aStrayWordAtTheEndOfASentenceNeverSkipsTheFirstWordOfTheNext() {
        val ix = SampleScripts.practice
        val starts = (1 until ix.size).filter { ix.tokens[it - 1].boundary >= com.eliadca.talks.core.text.Boundary.SENTENCE }
        val strays = listOf("y", "a", "eh", "este", "pues", "bueno", "entonces")
        val failures = ArrayList<String>()
        var cases = 0
        var prev = 0
        for (s in starts) {
            for (stray in strays) {
                if (com.eliadca.talks.core.text.SpanishText.canonical(stray) == ix.tokens[s].norm) continue // that is just reading
                for (where in Where.entries) {
                    cases++
                    val t = readThenStray(ix, prev, s, stray, where)
                    if (t.state.position > s) failures += "'$stray' ($where) before «${ix.tokens[s].norm}» at $s -> ${t.state.position}"
                }
            }
            prev = s
        }
        assertTrue("${failures.size}/$cases skipped:\n" + failures.take(15).joinToString("\n"), failures.isEmpty())
    }

    @Test fun theFirstWordOfTheNextSentenceStillMovesTheMarkerAfterAStrayWord() {
        val ix = SampleScripts.practice
        val s = (1 until ix.size).first { it > 20 && ix.tokens[it - 1].boundary >= com.eliadca.talks.core.text.Boundary.SENTENCE }
        val prev = (1 until s).last { ix.tokens[it - 1].boundary >= com.eliadca.talks.core.text.Boundary.SENTENCE }
        val t = readThenStray(ix, prev, s, "y", Where.SAME_PARTIAL)
        assertEquals(s, t.state.position)
        val said = ix.tokens.subList(prev, s).joinToString(" ") { it.norm }
        t.onHypothesis("$said y ${ix.tokens[s].norm} ${ix.tokens[s + 1].norm}", isFinal = false)
        assertEquals(s + 2, t.state.position)
    }

    @Test fun twoStrayWordsAndARevisedPartialStayPut() {
        val t = SpeechTracker(index)
        t.onHypothesis("buenos dias a todos eh y", isFinal = false)
        assertEquals(4, t.state.position)
        val u = SpeechTracker(index)
        u.onHypothesis("buenos dias a todos y", isFinal = false)
        u.onHypothesis("buenos dias a todos", isFinal = true)
        assertEquals(4, u.state.position)
        u.onHypothesis("gracias por estar", isFinal = true)
        assertEquals(7, u.state.position)
    }

    @Test fun aHeadingReadAloudDoesNotSkipTheFirstWordAfterIt() {
        val text = "Gracias por venir esta noche.\nPrimera idea: el valor del primer paso\nHace algunos años conocí a Marta."
        val heading = text.indexOf("Primera") until text.indexOf("\nHace")
        val ix = ScriptIndex.build(text, listOf(heading))
        val hace = ix.tokens.indexOfFirst { it.norm == "hace" }
        val t = SpeechTracker(ix)
        t.onHypothesis("gracias por venir esta noche", isFinal = true)
        assertEquals(hace, t.state.position)
        t.onHypothesis("primera idea el valor del primer paso", isFinal = false)
        assertTrue("moved to ${t.state.position}", t.state.position <= hace)
        // Coming back to the text after words that are not in it takes a real phrase of it.
        t.onHypothesis("primera idea el valor del primer paso hace algunos años", isFinal = false)
        assertEquals(hace + 3, t.state.position)
    }
}
