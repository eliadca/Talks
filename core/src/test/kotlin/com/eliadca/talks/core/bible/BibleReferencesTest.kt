package com.eliadca.talks.core.bible

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class BibleReferencesTest {
    @Test fun writtenAndSpokenReferencesMatchTheSameVerses() {
        for (text in listOf("Leamos Juan 3:16", "Juan capítulo tres versículo dieciséis", "juan tres dieciseis", "JUAN 3, 16")) {
            assertEquals(text, listOf(BibleReference(43, 3, 16)), BibleReferences.find(text))
        }
    }
    @Test fun numberedBooksAreNotMistakenForTheUnnumberedBook() {
        for (text in listOf("1 Juan 4:8", "primera de Juan cuatro ocho", "primera carta de Juan capítulo cuatro versículo ocho")) {
            assertEquals(text, listOf(BibleReference(62, 4, 8)), BibleReferences.find(text))
        }
        assertEquals(listOf(BibleReference(46, 13, 4, 7)), BibleReferences.find("primera carta a los Corintios trece cuatro al siete"))
    }
    @Test fun longSpanishNumbersAndRangesWorkWithoutAccents() {
        assertEquals(listOf(BibleReference(19, 119, 105)), BibleReferences.find("Salmo ciento diecinueve versículo ciento cinco"))
        assertEquals(listOf(BibleReference(43, 3, 16, 18)), BibleReferences.find("Juan 3:16-18"))
        assertEquals(listOf(BibleReference(45, 8, 38, 39)), BibleReferences.find("Romanos ocho versículos treinta y ocho hasta el treinta y nueve"))
    }
    @Test fun multipleReferencesKeepTheirOrderAndIgnoreOrdinarySpeech() {
        assertEquals(listOf(BibleReference(1, 1, 1), BibleReference(43, 3, 16)), BibleReferences.find("Génesis 1:1 y luego Juan 3:16"))
        for (text in listOf("Juan habló con tres personas", "Juan capítulo tres", "Salmo 0:1", "Juan 3:16-15", "Juan 3:1-176")) {
            assertTrue(text, BibleReferences.find(text).isEmpty())
        }
    }
    @Test fun independentEncryptedFixtureUsesThePublicationIdentity() {
        val blob = Base64.getDecoder().decode("qIVD5kxWICNjpJTA/1JOdN2TtTF3dN4+MliShiM5sPQiRVTQBveDt+NkurtdTlLC/sklHwNpDB7D6AKjlRuaWXjS5T77obo8cMx4qjgP26g=")
        assertEquals("<span id=\"v43-3-16\" class=\"v\">Texto de prueba con amor.</span>", JwpubCrypto(1, "nwtsty", 2026).decrypt(blob))
        assertTrue(runCatching { JwpubCrypto(1, "nwtsty", 2025).decrypt(blob) }.isFailure)
    }
}
