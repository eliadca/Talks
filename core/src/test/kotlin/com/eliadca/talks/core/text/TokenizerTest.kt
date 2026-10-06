package com.eliadca.talks.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenizerTest {

    private fun norms(text: String, silent: List<IntRange> = emptyList()) =
        Tokenizer.script(text, Tokenizer.bracketRanges(text) + silent).map { it.norm }

    @Test fun keepsRangesIntoOriginalText() {
        val text = "Hola, mundo. ¿Qué tal?"
        val t = Tokenizer.script(text)
        assertEquals(listOf("hola", "mundo", "que", "tal"), t.map { it.norm })
        assertEquals("Hola", text.substring(t[0].start, t[0].end))
        assertEquals("mundo", text.substring(t[1].start, t[1].end))
        assertEquals("Qué", text.substring(t[2].start, t[2].end))
    }

    @Test fun boundariesFollowPunctuation() {
        val t = Tokenizer.script("Hola, amigos. Vamos \"ya\"\nSí")
        assertEquals(Boundary.CLAUSE, t[0].boundary)
        assertEquals(Boundary.SENTENCE, t[1].boundary)
        assertEquals(Boundary.NONE, t[2].boundary)
        assertEquals(Boundary.PARAGRAPH, t[3].boundary)
        assertEquals(0, t[0].paragraph)
        assertEquals(1, t[4].paragraph)
    }

    @Test fun bracketedNotesAreNotSpoken() {
        assertEquals(listOf("buenos", "dias", "gracias"), norms("Buenos días [pausa larga] gracias"))
        // Unclosed bracket silences to the end of the line only.
        assertEquals(listOf("un", "tres"), norms("uno [dos\ntres"))
    }

    @Test fun silentRangesAreSkipped() {
        val text = "Título\nPrimera frase"
        assertEquals(listOf("primer", "frase"), norms(text, listOf(0 until 6)))
    }

    @Test fun numbersBecomeWordsSharingTheNumeralRange() {
        val text = "En 2024 fuimos 1.500 personas."
        val t = Tokenizer.script(text)
        assertEquals(
            listOf("en", "dos", "mil", "veinticuatro", "fuimos", "mil", "quinientos", "personas"),
            t.map { it.norm },
        )
        assertEquals("2024", text.substring(t[1].start, t[1].end))
        assertEquals(t[1].start, t[3].start)
        assertEquals("1.500", text.substring(t[5].start, t[5].end))
    }

    @Test fun decimalsPercentAndOrdinals() {
        assertEquals(listOf("tres", "coma", "cinco"), norms("3,5"))
        assertEquals(listOf("cincuenta", "por", "ciento"), norms("50%"))
        assertEquals(listOf("cincuenta", "por", "ciento"), norms("50 %"))
        assertEquals(listOf("primer"), norms("1.º"))
    }

    @Test fun sentenceFinalNumberIsNotAThousandsGroup() {
        assertEquals(listOf("nacio", "en", "mil", "novecientos", "ochenta", "y", "cinco", "es", "cierto"),
            norms("Nació en 1985. Es cierto"))
    }

    @Test fun heardTextIsNormalisedLikeTheScript() {
        val h = Tokenizer.heard("Hola a todos, en 2024 vamos")
        assertEquals(listOf("hola", "a", "todos", "en", "dos", "mil", "veinticuatro", "vamos"), h.map { it.norm })
        assertTrue(h.all { it.phon.isNotEmpty() })
    }
}
