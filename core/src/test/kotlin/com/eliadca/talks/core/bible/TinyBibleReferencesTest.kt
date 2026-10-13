package com.eliadca.talks.core.bible

import org.junit.Assert.*
import org.junit.Test

class TinyBibleReferencesTest {
    @Test fun bundledClassifierRecognizesSpellingAndSoundVariants() {
        val model = requireNotNull(TinyBookModel.bundled)
        for ((heard, book) in listOf("esequiel" to 26, "efecios" to 49, "tesaloni senses" to 52, "deutronomio" to 5)) {
            assertEquals(heard, book, model.predict(heard).maxBy { it.value }.key)
        }
    }
    @Test fun phoneticBookErrorsOpenTheCorrectReferenceAndKeepTheOriginalOffsets() {
        for ((heard, expected) in listOf("Esequiel 3:16" to BibleReference(26, 3, 16),
            "Efecios capítulo dos versículo ocho" to BibleReference(49, 2, 8),
            "Deutero nomio seis cuatro" to BibleReference(5, 6, 4),
            "Genesys 1:1" to BibleReference(1, 1, 1),
            "Ebreos 4:12" to BibleReference(58, 4, 12), "Jhan 3:16" to BibleReference(43, 3, 16))) {
            val text = "Escuchemos $heard ahora."
            val scan = BibleReferences.scan(text)
            assertEquals("$heard: $scan", listOf(expected), scan.matches.map { it.reference })
            assertTrue(scan.matches.single().corrected)
            assertEquals(heard, text.substring(scan.matches.single().start, scan.matches.single().end))
            assertTrue(scan.correctBookNames(text).startsWith("Escuchemos ${BibleReferences.books[expected.book - 1]}"))
        }
    }
    @Test fun aMisspelledNumberedBookDoesNotFallBackToAnUnnumberedBook() {
        assertEquals(listOf(BibleReference(53, 3, 5)), BibleReferences.scan("segunda de tesaloni senses 3:5").matches.map { it.reference })
        assertEquals(listOf(BibleReference(47, 5, 17)), BibleReferences.scan("2 Corintio 5:17").matches.map { it.reference })
    }
    @Test fun unsureWordsAndOrdinarySpeechNeverAutomaticallyOpenAnInventedCitation() {
        for (text in listOf("Tenemos ropa 3:16", "Juan habló con tres personas", "Esequiel capítulo tres",
            "Efecios 40:8", "Primera de Marcos 3:16", "Me gustó el texto de ayer", "compramos dos libros por veinte")) {
            assertTrue("$text: ${BibleReferences.scan(text)}", BibleReferences.scan(text).matches.isEmpty())
        }
    }
    @Test fun misspelledShortNamesThatCannotBeResolvedHaveSuggestionsInstead() {
        val scan = BibleReferences.scan("jon capítulo uno versículo uno")
        assertTrue(scan.matches.isEmpty())
        assertTrue(scan.suggestions.isNotEmpty())
    }
    @Test fun fragmentedNumberWordsAndVerseRangesAreUnderstoodWithoutChangingNumbers() {
        assertEquals(listOf(BibleReference(43, 3, 16, 18)), BibleReferences.find("Juan tres dieci seis hasta dieci ocho"))
        assertEquals(listOf(BibleReference(19, 119, 105)), BibleReferences.find("Salmos ciento dieci nueve ciento cinco"))
    }
    @Test fun repeatedWrittenReferencesEachKeepTheirOwnClickableRange() {
        val text = "📖 Juan 3:16; y otra vez Juan 3:16. Génesis 1:1"
        val matches = BibleReferences.findMatches(text)
        assertEquals(3, matches.size)
        assertEquals(listOf("Juan 3:16", "Juan 3:16", "Génesis 1:1"), matches.map { text.substring(it.start, it.end) })
        assertEquals(listOf(BibleReference(43, 3, 16), BibleReference(1, 1, 1)), BibleReferences.find(text))
    }
}
