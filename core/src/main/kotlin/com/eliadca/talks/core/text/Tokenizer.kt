package com.eliadca.talks.core.text

/** What follows a word in the script; used to decide where the next "chunk" to read ends. */
object Boundary {
    const val NONE = 0
    const val CLAUSE = 1     // , ; : — or similar
    const val SENTENCE = 2   // . ! ? …
    const val PARAGRAPH = 3  // newline
}

/**
 * One spoken word of the script.
 *
 * [start]/[end] are offsets into the original document text, so the position found by the tracker
 * maps straight back onto what the user sees. A numeral such as "2024" expands into several tokens
 * (dos, mil, veinticuatro) that all share the numeral's range.
 */
class ScriptToken(
    val norm: String,
    val phon: String,
    val start: Int,
    val end: Int,
    val boundary: Int,
    val paragraph: Int,
)

/** A word as heard by the recogniser, already normalised. */
class HeardWord(val norm: String, val phon: String)

object Tokenizer {

    private val connectors = setOf('.', ',')

    /** Ranges of `[...]` stage directions in [text]; they are shown but never expected to be spoken. */
    fun bracketRanges(text: String): List<IntRange> {
        val out = ArrayList<IntRange>()
        var i = 0
        while (i < text.length) {
            if (text[i] == '[') {
                var j = i + 1
                while (j < text.length && text[j] != ']' && text[j] != '\n') j++
                val endExclusive = if (j < text.length && text[j] == ']') j + 1 else j
                out += i until endExclusive
                i = endExclusive
            } else i++
        }
        return out
    }

    /**
     * Splits [text] into spoken tokens. Words whose start offset falls inside any of [silent] are
     * skipped (stage directions, headings the speaker does not read aloud).
     */
    fun script(text: String, silent: List<IntRange> = emptyList()): List<ScriptToken> {
        val quiet = SilentRanges(silent)
        fun isSilent(pos: Int): Boolean = quiet.endIfInside(pos) > pos

        val tokens = ArrayList<ScriptToken>(text.length / 5 + 4)
        var paragraph = 0
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '\n' -> { paragraph++; i++ }
                c.isDigit() -> {
                    val parsed = parseNumber(text, i)
                    if (!isSilent(i)) {
                        val b = boundaryAfter(text, parsed.end, quiet)
                        emitWords(tokens, parsed.words, i, parsed.end, b, paragraph)
                    }
                    i = parsed.end
                }
                c.isLetter() -> {
                    var j = i + 1
                    while (j < n && (text[j].isLetter() || (text[j] == '\'' && j + 1 < n && text[j + 1].isLetter()))) j++
                    if (!isSilent(i)) {
                        val word = text.substring(i, j).replace("'", "")
                        val b = boundaryAfter(text, j, quiet)
                        emitWords(tokens, listOf(SpanishText.canonical(word)), i, j, b, paragraph)
                    }
                    i = j
                }
                else -> i++
            }
        }
        return mergeScriptPairs(tokens)
    }

    /**
     * Word pairs that recognisers write either apart or together ("por que" / "porque"). Both the
     * script and what is heard are folded to the joined form, so they align whichever is used.
     */
    private val joinedPairs: Map<Pair<String, String>, String> = mapOf(
        ("por" to "que") to "porque",
        ("tan" to "bien") to "tambien",
        ("tan" to "poco") to "tampoco",
        ("si" to "no") to "sino",
        ("asi" to "mismo") to "asimismo",
        ("con" to "migo") to "conmigo",
        ("con" to "tigo") to "contigo",
        ("en" to "seguida") to "enseguida",
        ("a" to "penas") to "apenas",
        ("a" to "veces") to "aveces",
    )

    private fun mergeScriptPairs(tokens: List<ScriptToken>): List<ScriptToken> {
        if (tokens.size < 2) return tokens
        val out = ArrayList<ScriptToken>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val a = tokens[i]
            val b = tokens.getOrNull(i + 1)
            val joined = if (b != null && a.boundary == Boundary.NONE && a.paragraph == b.paragraph) joinedPairs[a.norm to b.norm] else null
            if (joined != null && b != null) {
                out += ScriptToken(joined, SpanishText.phonetic(joined), a.start, b.end, b.boundary, a.paragraph)
                i += 2
            } else {
                out += a
                i++
            }
        }
        return out
    }

    private fun mergeHeardPairs(words: List<HeardWord>): List<HeardWord> {
        if (words.size < 2) return words
        val out = ArrayList<HeardWord>(words.size)
        var i = 0
        while (i < words.size) {
            val joined = words.getOrNull(i + 1)?.let { joinedPairs[words[i].norm to it.norm] }
            if (joined != null) {
                out += HeardWord(joined, SpanishText.phonetic(joined))
                i += 2
            } else {
                out += words[i]
                i++
            }
        }
        return out
    }

    /** Normalises recogniser output ("hola a todos 2024") into comparable words. */
    fun heard(text: String): List<HeardWord> {
        val out = ArrayList<HeardWord>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c.isDigit() -> {
                    val parsed = parseNumber(text, i)
                    for (w in parsed.words) addHeard(out, w)
                    i = parsed.end
                }
                c.isLetter() -> {
                    var j = i + 1
                    while (j < n && (text[j].isLetter() || (text[j] == '\'' && j + 1 < n && text[j + 1].isLetter()))) j++
                    addHeard(out, SpanishText.canonical(text.substring(i, j).replace("'", "")))
                    i = j
                }
                else -> i++
            }
        }
        return mergeHeardPairs(out)
    }

    private fun addHeard(out: MutableList<HeardWord>, canonical: String) {
        if (canonical.isEmpty()) return
        out += HeardWord(canonical, SpanishText.phonetic(canonical))
    }

    private fun emitWords(
        out: MutableList<ScriptToken>,
        words: List<String>,
        start: Int,
        end: Int,
        boundary: Int,
        paragraph: Int,
    ) {
        for ((k, w) in words.withIndex()) {
            if (w.isEmpty()) continue
            val last = k == words.lastIndex
            out += ScriptToken(w, SpanishText.phonetic(w), start, end, if (last) boundary else Boundary.NONE, paragraph)
        }
    }

    private class ParsedNumber(val words: List<String>, val end: Int)

    /**
     * Parses a numeral starting at [from] (which must be a digit). Handles Spanish thousands
     * separators ("1.500"), decimals ("3,5"), percentages, ordinal marks ("1.º") and plain integers.
     */
    private fun parseNumber(text: String, from: Int): ParsedNumber {
        val n = text.length
        var i = from
        while (i < n && text[i].isDigit()) i++
        var intEnd = i
        val intPart = StringBuilder(text.substring(from, i))
        var fraction: String? = null

        // Thousands groups such as 1.500.000 (or 1,500 as written in Mexico/US): exactly three digits
        // after the separator and no further digit.
        while (i < n && text[i] in connectors && hasDigits(text, i + 1, 3) &&
            (i + 4 >= n || !text[i + 4].isDigit())
        ) {
            intPart.append(text, i + 1, i + 4)
            i += 4
            intEnd = i
        }
        // Decimal part: "3,5" or "3.5"
        if (i < n - 1 && text[i] in connectors && text[i + 1].isDigit()) {
            var j = i + 1
            while (j < n && text[j].isDigit()) j++
            fraction = text.substring(i + 1, j)
            i = j
            intEnd = i
        }

        val words = ArrayList<String>()
        val digits = intPart.toString().trimStart('0').ifEmpty { "0" }
        val value = digits.toLongOrNull()
        var end = intEnd

        // Ordinal marks: 1º 1.º 2ª 3ro 4to ... only for small whole numbers.
        var ordinalWord: String? = null
        if (fraction == null && value != null && value in 1..10) {
            var k = intEnd
            if (k < n && text[k] == '.') k++
            if (k < n && (text[k] == 'º' || text[k] == 'ª' || text[k] == '°')) {
                ordinalWord = SpanishNumbers.ordinal(value.toInt())
                end = k + 1
            }
        }

        if (ordinalWord != null) {
            words += SpanishText.canonical(ordinalWord)
        } else if (value != null) {
            words += SpanishNumbers.toWords(value)
            if (fraction != null) {
                words += "coma"
                for (d in fraction) words += SpanishNumbers.toWords((d - '0').toLong())
            }
        } else {
            for (d in digits) words += SpanishNumbers.toWords((d - '0').toLong())
        }

        // "50%" is read "cincuenta por ciento".
        var k = end
        while (k < n && text[k] == ' ') k++
        if (k < n && text[k] == '%') {
            words += "por"; words += "ciento"
            end = k + 1
        }
        return ParsedNumber(words.map { SpanishText.canonical(it) }, end)
    }

    private fun hasDigits(text: String, from: Int, count: Int): Boolean {
        if (from + count > text.length) return false
        for (k in from until from + count) if (!text[k].isDigit()) return false
        return true
    }

    /**
     * Sorted, merged ranges of text that is not spoken, looked up without state (so looking ahead
     * from one word never disturbs the lookup for the next one).
     */
    private class SilentRanges(ranges: List<IntRange>) {
        private val starts: IntArray
        private val ends: IntArray // exclusive

        init {
            val sorted = ranges.filter { !it.isEmpty() }.sortedBy { it.first }
            val s = ArrayList<Int>()
            val e = ArrayList<Int>()
            for (r in sorted) {
                if (e.isNotEmpty() && r.first <= e.last()) {
                    if (r.last + 1 > e.last()) e[e.lastIndex] = r.last + 1
                } else {
                    s += r.first
                    e += r.last + 1
                }
            }
            starts = s.toIntArray()
            ends = e.toIntArray()
        }

        /** The (exclusive) end of the silent range that contains [pos], or -1. */
        fun endIfInside(pos: Int): Int {
            var lo = 0
            var hi = starts.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (starts[mid] <= pos) lo = mid + 1 else hi = mid
            }
            val k = lo - 1
            return if (k >= 0 && pos < ends[k]) ends[k] else -1
        }
    }

    /**
     * What follows the word that ends at [from]. A note for the speaker (any [silent] text) is a
     * pause, at least a clause boundary; one that spans a line break ends the paragraph.
     */
    private fun boundaryAfter(text: String, from: Int, silent: SilentRanges): Int {
        var i = from
        val n = text.length
        var result = Boundary.NONE
        while (i < n) {
            val skipTo = silent.endIfInside(i)
            if (skipTo > i) {
                val nl = text.indexOf('\n', i)
                if (nl in i until skipTo) return Boundary.PARAGRAPH
                result = maxOf(result, Boundary.CLAUSE)
                i = skipTo
                continue
            }
            val c = text[i]
            when {
                c == '\n' -> return Boundary.PARAGRAPH
                c == '.' || c == '!' || c == '?' || c == '…' -> result = maxOf(result, Boundary.SENTENCE)
                c == ',' || c == ';' || c == ':' || c == '—' || c == '–' -> result = maxOf(result, Boundary.CLAUSE)
                c == ' ' || c == '\t' || c == '"' || c == '”' || c == '“' || c == '»' || c == '«' ||
                    c == ')' || c == ']' || c == '¡' || c == '¿' || c == '\'' || c == '’' -> {}
                else -> return result
            }
            i++
        }
        // End of the text counts as the end of a paragraph.
        return if (result == Boundary.NONE) Boundary.PARAGRAPH else result
    }
}
