package com.eliadca.talks.core.bible

import com.eliadca.talks.core.text.SpanishNumbers
import com.eliadca.talks.core.text.SpanishText

data class BibleReference(val book: Int, val chapter: Int, val firstVerse: Int, val lastVerse: Int = firstVerse) {
    val label: String get() = "${BibleReferences.books[book - 1]} $chapter:$firstVerse" +
        if (lastVerse != firstVerse) "–$lastVerse" else ""
}

/** Conservative Spanish citation recognition; the imported Bible validates actual verse numbers. */
object BibleReferences {
    val books = listOf(
        "Génesis", "Éxodo", "Levítico", "Números", "Deuteronomio", "Josué", "Jueces", "Rut",
        "1 Samuel", "2 Samuel", "1 Reyes", "2 Reyes", "1 Crónicas", "2 Crónicas", "Esdras", "Nehemías",
        "Ester", "Job", "Salmos", "Proverbios", "Eclesiastés", "Cantar de los Cantares", "Isaías",
        "Jeremías", "Lamentaciones", "Ezequiel", "Daniel", "Oseas", "Joel", "Amós", "Abdías", "Jonás",
        "Miqueas", "Nahúm", "Habacuc", "Sofonías", "Ageo", "Zacarías", "Malaquías", "Mateo", "Marcos",
        "Lucas", "Juan", "Hechos", "Romanos", "1 Corintios", "2 Corintios", "Gálatas", "Efesios",
        "Filipenses", "Colosenses", "1 Tesalonicenses", "2 Tesalonicenses", "1 Timoteo", "2 Timoteo",
        "Tito", "Filemón", "Hebreos", "Santiago", "1 Pedro", "2 Pedro", "1 Juan", "2 Juan", "3 Juan",
        "Judas", "Apocalipsis",
    )
    private data class Alias(val book: Int, val words: List<String>)
    private val aliases: List<Alias> = buildList {
        books.forEachIndexed { index, name ->
            val words = SpanishText.foldForSearch(name).split(' ')
            add(Alias(index + 1, words))
            if (words.first() in listOf("1", "2", "3")) {
                val prefixes = when (words.first()) {
                    "1" -> listOf("primera", "primer", "primero", "uno", "i")
                    "2" -> listOf("segunda", "segundo", "dos", "ii")
                    else -> listOf("tercera", "tercer", "tercero", "tres", "iii")
                }
                for (p in prefixes) {
                    add(Alias(index + 1, listOf(p) + words.drop(1)))
                    add(Alias(index + 1, listOf(p, "de") + words.drop(1)))
                    add(Alias(index + 1, listOf(p, "carta", "a", "los") + words.drop(1)))
                    add(Alias(index + 1, listOf(p, "carta", "de") + words.drop(1)))
                }
            }
        }
        add(Alias(19, listOf("salmo")))
        add(Alias(22, listOf("cantares")))
        add(Alias(44, listOf("hechos", "de", "los", "apostoles")))
        add(Alias(66, listOf("revelacion")))
    }.sortedByDescending { it.words.size }
    private val numbers = (1..176).associateBy { SpanishNumbers.toWords(it.toLong()).joinToString(" ") } +
        mapOf("un" to 1, "una" to 1, "veintiun" to 21, "veintiuna" to 21)
    private data class NumberRead(val value: Int, val next: Int)
    private fun number(tokens: List<String>, from: Int): NumberRead? {
        tokens.getOrNull(from)?.toIntOrNull()?.let { return NumberRead(it, from + 1) }
        for (length in 4 downTo 1) {
            if (from + length > tokens.size) continue
            val n = numbers[tokens.subList(from, from + length).joinToString(" ")]
            if (n != null) return NumberRead(n, from + length)
        }
        return null
    }

    fun find(text: String): List<BibleReference> {
        // Preserve punctuation that distinguishes a verse range from ordinary speech.
        val tokens = Regex("[\\p{L}\\p{N}]+|[:;,\\-–]").findAll(text)
            .map { if (it.value.first().isLetterOrDigit()) SpanishText.fold(it.value) else it.value }.toList()
        val out = linkedSetOf<BibleReference>()
        var i = 0
        while (i < tokens.size) {
            val alias = aliases.firstOrNull { a ->
                i + a.words.size <= tokens.size && tokens.subList(i, i + a.words.size) == a.words
            }
            if (alias == null) { i++; continue }
            var pos = i + alias.words.size
            if (tokens.getOrNull(pos) in listOf("capitulo", "cap", "numero")) pos++
            val chapter = number(tokens, pos)
            if (chapter == null) { i += alias.words.size; continue }
            pos = chapter.next
            // A bare book and chapter is deliberately not a verse citation.
            if (tokens.getOrNull(pos) in listOf(":", ",", "versiculo", "versiculos", "verso", "versos")) pos++
            val verse = number(tokens, pos)
            if (verse == null) { i = pos.coerceAtLeast(i + 1); continue }
            pos = verse.next
            var last = verse.value
            if (tokens.getOrNull(pos) in listOf("-", "–", "al", "a", "hasta")) {
                var rangeStart = pos + 1
                if (tokens.getOrNull(rangeStart) in listOf("el", "versiculo")) rangeStart++
                number(tokens, rangeStart)?.let { last = it.value; pos = it.next }
            }
            if (chapter.value in 1..150 && verse.value in 1..176 && last in verse.value..176 && last - verse.value < 30) {
                out += BibleReference(alias.book, chapter.value, verse.value, last)
            }
            i = pos.coerceAtLeast(i + 1)
        }
        return out.toList()
    }
}
