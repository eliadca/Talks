package com.eliadca.talks.core.bible

import com.eliadca.talks.core.text.SpanishNumbers
import com.eliadca.talks.core.text.SpanishText

data class BibleReference(val book: Int, val chapter: Int, val firstVerse: Int, val lastVerse: Int = firstVerse) {
    val label: String get() = "${BibleReferences.books[book - 1]} $chapter:$firstVerse" +
        if (lastVerse != firstVerse) "–$lastVerse" else ""
}

data class BibleReferenceMatch(
    val reference: BibleReference, val start: Int, val end: Int, val bookEnd: Int,
    val corrected: Boolean = false, val confidence: Float = 1f,
)
data class BibleReferenceSuggestion(val reference: BibleReference, val heard: String, val confidence: Float)
data class BibleReferenceScan(val matches: List<BibleReferenceMatch>, val suggestions: List<BibleReferenceSuggestion>) {
    fun correctBookNames(text: String): String {
        var result = text
        for (match in matches.filter { it.corrected }.sortedByDescending { it.start }) {
            result = result.replaceRange(match.start, match.bookEnd, BibleReferences.books[match.reference.book - 1])
        }
        return result
    }
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
    private val compactNumbers = numbers.mapKeys { it.key.replace(" ", "") } +
        mapOf("dieziseis" to 16, "diezisiete" to 17, "dieziocho" to 18, "diezinueve" to 19)
    private val chapterCounts = listOf(50,40,27,36,34,24,21,4,31,24,22,25,29,36,10,13,10,42,150,31,12,8,
        66,52,5,48,12,14,3,9,1,4,7,3,3,3,2,14,4,28,16,24,21,28,16,16,13,6,6,4,4,5,3,6,4,3,1,13,5,5,3,5,1,1,1,22)
    private val ordinal = mapOf("1" to 1, "primera" to 1, "primer" to 1, "primero" to 1, "uno" to 1, "i" to 1,
        "2" to 2, "segunda" to 2, "segundo" to 2, "dos" to 2, "ii" to 2,
        "3" to 3, "tercera" to 3, "tercer" to 3, "tercero" to 3, "tres" to 3, "iii" to 3)
    private val familyIds = books.map { title -> books.indexOfFirst {
        it.replace(Regex("^[123] "), "") == title.replace(Regex("^[123] "), "")
    } + 1 }
    private data class Token(val value: String, val start: Int, val end: Int)
    private data class NumberRead(val value: Int, val next: Int)
    private fun number(tokens: List<String>, from: Int): NumberRead? {
        tokens.getOrNull(from)?.toIntOrNull()?.let { return NumberRead(it, from + 1) }
        for (length in 4 downTo 1) {
            if (from + length > tokens.size) continue
            val words = tokens.subList(from, from + length)
            if (words.any { !it.first().isLetter() }) continue
            val n = numbers[words.joinToString(" ")] ?: compactNumbers[words.joinToString("")]
            if (n != null) return NumberRead(n, from + length)
        }
        return null
    }

    fun find(text: String): List<BibleReference> = findMatches(text).map { it.reference }.distinct()

    fun findMatches(text: String): List<BibleReferenceMatch> = scan(text, tolerateErrors = false).matches

    /** Exact grammar plus a tiny classifier and phonetic spelling distance; uncertainty stays a suggestion. */
    fun scan(text: String, tolerateErrors: Boolean = true): BibleReferenceScan {
        // Preserve punctuation that distinguishes a verse range from ordinary speech.
        val positions = Regex("[\\p{L}\\p{N}]+|[:;,\\-–]").findAll(text).map {
            Token(if (it.value.first().isLetterOrDigit()) SpanishText.fold(it.value) else it.value, it.range.first, it.range.last + 1)
        }.toList()
        val tokens = positions.map { it.value }
        val out = mutableListOf<BibleReferenceMatch>()
        val suggestions = mutableListOf<BibleReferenceSuggestion>()
        val modelScores = mutableMapOf<String, Map<Int, Float>>()
        var i = 0
        var lastCitationEnd = 0
        while (i < tokens.size) {
            var before = i - 1
            while (before >= lastCitationEnd && tokens[before] in listOf("de", "del", "carta", "a", "los", "al")) before--
            if (before >= lastCitationEnd && ordinal[tokens[before]] != null) { i++; continue }
            val exact = aliases.firstNotNullOfOrNull { a ->
                if (i + a.words.size > tokens.size || tokens.subList(i, i + a.words.size) != a.words) null
                else readCitation(tokens, i + a.words.size)?.takeIf { valid(a.book, it) }?.let { a to it }
            }
            if (exact != null) {
                val (alias, citation) = exact
                out += BibleReferenceMatch(citation.reference(alias.book), positions[i].start,
                    positions[citation.next - 1].end, positions[i + alias.words.size - 1].end)
                i = citation.next; lastCitationEnd = i; continue
            }
            if (!tolerateErrors) { i++; continue }
            if (tokens[i] in listOf("de", "del", "carta", "a", "los", "al")) { i++; continue }
            data class Candidate(val book: Int, val length: Int, val score: Float, val citation: Citation)
            val candidates = mutableListOf<Candidate>()
            for (length in 1..minOf(8, tokens.size - i)) {
                val words = tokens.subList(i, i + length)
                if (words.any { it in listOf(":", ",", ";", "-", "–", "capitulo", "versiculo", "versiculos") }) break
                if (words.drop(1).any { it.toIntOrNull() != null }) break
                val citation = readCitation(tokens, i + length) ?: continue
                val prefix = ordinal[words.first()]
                val stemWords = if (prefix == null) words else words.drop(1).dropWhile { it in listOf("de", "del", "carta", "a", "los", "al") }
                val heard = stemWords.joinToString("")
                if (heard.length < 3 || stemWords.isEmpty()) continue
                val sound = TinyBookModel.phonetic(heard)
                val scores by lazy { modelScores.getOrPut(heard) { TinyBookModel.bundled?.predict(heard).orEmpty() } }
                for (alias in aliases) {
                    val expectedPrefix = alias.words.first().toIntOrNull() ?: ordinal[alias.words.first()]
                    if (prefix != expectedPrefix) continue
                    val stem = if (expectedPrefix == null) alias.words else alias.words.drop(1).dropWhile { it in listOf("de", "del", "carta", "a", "los", "al") }
                    val target = stem.joinToString("")
                    if (target.isEmpty() || !valid(alias.book, citation)) continue
                    val targetSound = TinyBookModel.phonetic(target)
                    val distance = minOf(editDistance(heard, target), editDistance(sound, targetSound))
                    val maxLength = maxOf(heard.length, target.length)
                    if (distance > maxOf(1, maxLength / 4) || (maxLength < 5 && !citation.explicit)) continue
                    val lexical = 1f - distance.toFloat() / (maxLength + 2)
                    if (lexical < .74f) continue
                    val family = familyIds[alias.book - 1]
                    val bestModel = scores.maxByOrNull { it.value }
                    val support = if (bestModel?.key == family) .055f else if ((bestModel?.value ?: 0f) > .65f) -.06f else 0f
                    val context = if (citation.explicit) .035f else .015f
                    candidates += Candidate(alias.book, length, (lexical + support + context).coerceAtMost(.99f), citation)
                }
            }
            val ranked = candidates.groupBy { it.book }.map { (_, hits) -> hits.maxBy { it.score } }.sortedByDescending { it.score }
            val best = ranked.firstOrNull()
            if (best == null) { i++; continue }
            val margin = best.score - (ranked.getOrNull(1)?.score ?: 0f)
            if (best.score >= .89f && margin >= .065f) {
                out += BibleReferenceMatch(best.citation.reference(best.book), positions[i].start,
                    positions[best.citation.next - 1].end, positions[i + best.length - 1].end, true, best.score)
                i = best.citation.next
                lastCitationEnd = i
            } else {
                ranked.filter { it.score >= .77f && best.score - it.score <= .15f }.take(3).forEach {
                    suggestions += BibleReferenceSuggestion(it.citation.reference(it.book),
                        text.substring(positions[i].start, positions[i + it.length - 1].end), it.score)
                }
                i++
            }
        }
        return BibleReferenceScan(out, suggestions.distinctBy { it.reference })
    }

    private data class Citation(val chapter: Int, val first: Int, val last: Int, val next: Int, val explicit: Boolean) {
        fun reference(book: Int) = BibleReference(book, chapter, first, last)
    }
    private fun readCitation(tokens: List<String>, from: Int): Citation? {
        var pos = from
        var explicit = tokens.getOrNull(pos) in listOf("capitulo", "cap", "numero")
        if (explicit) pos++
        val chapter = number(tokens, pos) ?: return null
        pos = chapter.next
        if (tokens.getOrNull(pos) in listOf(":", ",", "versiculo", "versiculos", "verso", "versos")) { pos++; explicit = true }
        val verse = number(tokens, pos) ?: return null
        pos = verse.next
        var last = verse.value
        if (tokens.getOrNull(pos) in listOf("-", "–", "al", "a", "hasta")) {
            var rangeStart = pos + 1
            if (tokens.getOrNull(rangeStart) in listOf("el", "versiculo")) rangeStart++
            number(tokens, rangeStart)?.let { last = it.value; pos = it.next }
        }
        return Citation(chapter.value, verse.value, last, pos, explicit)
    }
    private fun valid(book: Int, citation: Citation): Boolean = citation.chapter in 1..chapterCounts[book - 1] &&
        citation.first in 1..176 && citation.last in citation.first..176 && citation.last - citation.first < 30

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val row = IntArray(b.length + 1); row[0] = i + 1
            for (j in b.indices) row[j + 1] = minOf(row[j] + 1, previous[j + 1] + 1, previous[j] + if (a[i] == b[j]) 0 else 1)
            previous = row
        }
        return previous.last()
    }
}
