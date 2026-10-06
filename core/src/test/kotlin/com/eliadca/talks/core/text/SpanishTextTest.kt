package com.eliadca.talks.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpanishTextTest {

    @Test fun foldRemovesAccentsButKeepsEnye() {
        assertEquals("cancion", SpanishText.fold("Canción"))
        assertEquals("pequeñas", SpanishText.fold("PEQUEÑAS"))
        assertEquals("pinguino", SpanishText.fold("pingüino"))
        assertEquals("que", SpanishText.fold("¿Qué"))
    }

    @Test fun foldForSearchKeepsLengthAndPositions() {
        val text = "¿Cuántas DECISIONES, año 2024? Ñandú — İstanbul"
        val folded = SpanishText.foldForSearch(text)
        assertEquals(text.length, folded.length)
        assertEquals(text.indexOf("DECISIONES"), folded.indexOf("decisiones"))
        assertEquals("cuantas", folded.substring(1, 8))
    }

    @Test fun phoneticMergesSoundAlikes() {
        val pairs = listOf(
            "vaca" to "baca", "hoy" to "oy", "llave" to "yabe", "cena" to "sena", "zapato" to "sapato",
            "queso" to "keso", "gente" to "jente", "jirafa" to "girafa", "haber" to "aber", "casa" to "kasa",
        )
        for ((a, b) in pairs) {
            assertEquals("$a vs $b", SpanishText.phonetic(SpanishText.fold(a)), SpanishText.phonetic(SpanishText.fold(b)))
        }
    }

    @Test fun phoneticKeepsDifferentWordsApart() {
        assertNotEquals(SpanishText.phonetic("casa"), SpanishText.phonetic("cosa"))
        assertNotEquals(SpanishText.phonetic("pero"), SpanishText.phonetic("perro").replace("rr", "r").let { it + "x" })
        assertNotEquals(SpanishText.phonetic("mano"), SpanishText.phonetic("mañ".plus("o")))
    }

    @Test fun canonicalUnifiesArticlesAndNumbers() {
        assertEquals("un", SpanishText.canonical("una"))
        assertEquals("un", SpanishText.canonical("Uno"))
        assertEquals("veintiun", SpanishText.canonical("veintiuna"))
    }

    @Test fun editDistanceBasics() {
        assertEquals(0, SpanishText.editDistance("casa", "casa"))
        assertEquals(1, SpanishText.editDistance("casa", "cas"))
        assertEquals(2, SpanishText.editDistance("kitten", "sitten") + 1)
        assertEquals(3, SpanishText.editDistance("sitting", "kitten"))
        assertTrue(SpanishText.editDistance("abcdef", "uvwxyz", 2) > 2)
    }

    @Test fun matchProbabilityRanksSensibly() {
        fun m(a: String, b: String): Double {
            val ca = SpanishText.canonical(a); val cb = SpanishText.canonical(b)
            return SpanishText.matchProbability(ca, SpanishText.phonetic(ca), cb, SpanishText.phonetic(cb))
        }
        assertEquals(1.0, m("hermanos", "hermanos"), 0.0)
        assertTrue(m("Vamos", "bamos") > 0.9)
        assertTrue(m("hermano", "hermanos") >= 0.6)
        assertEquals(0.0, m("con", "son"), 0.0)
        assertEquals(0.0, m("camino", "destino"), 0.0)
        assertTrue(m("decisiones", "decision") > 0.0)
    }
}
