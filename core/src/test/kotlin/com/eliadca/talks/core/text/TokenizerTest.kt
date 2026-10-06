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

    private fun boundaries(text: String, silent: List<IntRange> = emptyList()) =
        Tokenizer.script(text, Tokenizer.bracketRanges(text) + silent).associate { it.norm to it.boundary }

    @Test fun aNoteIsAPause() {
        // A note before the full stop no longer hides it.
        assertEquals(Boundary.SENTENCE, boundaries("Una palabra [pausa]. Siguiente")["palabra"])
        // A note at the end of a line ends the paragraph.
        assertEquals(Boundary.PARAGRAPH, boundaries("Un momento. [Esperar.]\nLos")["momento"])
        // A note in the middle of a sentence is a pause like a comma.
        val mid = Tokenizer.script("Es pequeño [sonreír] es enorme", Tokenizer.bracketRanges("Es pequeño [sonreír] es enorme"))
        assertEquals(Boundary.CLAUSE, mid[1].boundary)
        // A silent stretch that spans a line break ends the paragraph too.
        val text = "Uno dos tres cuatro"
        assertEquals(Boundary.PARAGRAPH, Tokenizer.script("Uno dos\ntres cuatro", listOf(3 until 10)).first().boundary)
        assertEquals(Boundary.NONE, Tokenizer.script(text).first().boundary)
        // The words of the note are still left out.
        assertEquals(4, mid.size)
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

    @Test fun wordsThatRecognisersSplitOrJoinAreFoldedTogether() {
        // Script says "porque", the recogniser heard "por que" (and the reverse).
        assertEquals(listOf("porque", "te", "quiero", "tambien"), norms("Porque te quiero, también"))
        assertEquals(listOf("porque", "te", "quiero", "tambien"), norms("Por qué te quiero, tan bien"))
        assertEquals(listOf("porque", "te", "quiero", "tambien"), Tokenizer.heard("por que te quiero tan bien").map { it.norm })
        assertEquals(listOf("porque", "te", "quiero", "tambien"), Tokenizer.heard("porque te quiero también").map { it.norm })
        // A pair separated by punctuation is not one word.
        assertEquals(listOf("por", "que"), norms("por. que"))
    }

    @Test fun joinedPairKeepsTheRangeOfBothWords() {
        val text = "Dime por que vienes"
        val t = Tokenizer.script(text)
        assertEquals(listOf("dime", "porque", "vienes"), t.map { it.norm })
        assertEquals("por que", text.substring(t[1].start, t[1].end))
    }

    @Test fun heardTextIsNormalisedLikeTheScript() {
        val h = Tokenizer.heard("Hola a todos, en 2024 vamos")
        assertEquals(listOf("hola", "a", "todos", "en", "dos", "mil", "veinticuatro", "vamos"), h.map { it.norm })
        assertTrue(h.all { it.phon.isNotEmpty() })
    }
}
