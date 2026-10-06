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

    @Test fun jumpsAheadWhenTheSpeakerSkips() {
        val t = SpeechTracker(index)
        t.onHypothesis("buenos dias a todos", true)
        t.onHypothesis("quiero comenzar con una pregunta sencilla", true)
        val expected = indexOf("sencilla") + 1
        assertTrue("pos=${t.state.position} expected≈$expected", kotlin.math.abs(t.state.position - expected) <= 2)
    }

    @Test fun jumpsBackWhenTheSpeakerRepeats() {
        val t = SpeechTracker(index)
        t.onHypothesis("buenos dias a todos gracias por estar aqui y gracias por regalarme lo mas valioso que tienen su tiempo", true)
        t.onHypothesis("gracias por estar aqui y gracias por regalarme", false)
        t.onHypothesis("gracias por estar aqui y gracias por regalarme lo mas valioso", true)
        val expected = indexOf("valioso") + 1
        assertTrue("pos=${t.state.position} expected≈$expected", kotlin.math.abs(t.state.position - expected) <= 3)
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
}
