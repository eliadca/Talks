package com.eliadca.talks.core.text

import java.text.Normalizer
import java.util.Locale

/**
 * Text normalisation helpers for Spanish.
 *
 * Everything the speech tracker compares goes through [fold] (case/accent-insensitive form) and
 * [phonetic] (a coarse pronunciation key). Speech recognisers routinely confuse letters that sound
 * the same (b/v, c/s/z, ll/y, silent h, g/j) and drop or add accents, so matching on spelling alone
 * would lose the thread of a speech constantly.
 */
object SpanishText {

    private val combiningMarks = Regex("\\p{Mn}+")

    /** Lower-cases and removes accents. `ñ` is kept because it is a different letter. */
    fun fold(word: String): String {
        val lower = word.lowercase(Locale.ROOT)
        val sb = StringBuilder(lower.length)
        for (c in lower) {
            when (c) {
                'ñ' -> sb.append('ñ')
                in 'a'..'z', in '0'..'9' -> sb.append(c)
                'á', 'à', 'â', 'ä', 'ã' -> sb.append('a')
                'é', 'è', 'ê', 'ë' -> sb.append('e')
                'í', 'ì', 'î', 'ï' -> sb.append('i')
                'ó', 'ò', 'ô', 'ö', 'õ' -> sb.append('o')
                'ú', 'ù', 'û', 'ü' -> sb.append('u')
                'ç' -> sb.append('c')
                else -> {
                    if (c.isLetterOrDigit()) {
                        val decomposed = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
                        sb.append(combiningMarks.replace(decomposed, ""))
                    }
                }
            }
        }
        return sb.toString()
    }

    /** Folds a whole string for substring search (keeps spaces, drops punctuation). */
    fun foldForSearch(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        val sb = StringBuilder(lower.length)
        for (c in lower) {
            when {
                c == 'ñ' -> sb.append('ñ')
                c in 'a'..'z' || c in '0'..'9' -> sb.append(c)
                c == 'á' || c == 'à' || c == 'â' || c == 'ä' -> sb.append('a')
                c == 'é' || c == 'è' || c == 'ê' || c == 'ë' -> sb.append('e')
                c == 'í' || c == 'ì' || c == 'î' || c == 'ï' -> sb.append('i')
                c == 'ó' || c == 'ò' || c == 'ô' || c == 'ö' -> sb.append('o')
                c == 'ú' || c == 'ù' || c == 'û' || c == 'ü' -> sb.append('u')
                c.isLetterOrDigit() -> {
                    val d = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
                    sb.append(combiningMarks.replace(d, ""))
                }
                else -> sb.append(' ')
            }
        }
        return sb.toString()
    }

    /**
     * Folds [word] and maps the spellings that recognisers produce interchangeably onto one form:
     * `un/uno/una`, `veintiun/veintiuno/veintiuna`, and the apocopated `primer/tercer/buen/gran/...`.
     */
    fun canonical(word: String): String {
        val f = fold(word)
        return when (f) {
            "uno", "una" -> "un"
            "veintiuno", "veintiuna" -> "veintiun"
            "primero", "primera" -> "primer"
            "tercero", "tercera" -> "tercer"
            "bueno", "buena" -> "buen"
            "grande" -> "gran"
            else -> f
        }
    }

    /**
     * Coarse Spanish pronunciation key. Letters that sound alike share a symbol:
     * v→b, z/ce/ci→s, c/qu/k→k, ll→y, h→(silent), ch→ç, ge/gi→j, gue/gui→g, x→ks, w→u, doubles collapsed.
     * Input must already be folded.
     */
    fun phonetic(folded: String): String {
        val s = folded
        val n = s.length
        val sb = StringBuilder(n)
        var i = 0
        fun push(ch: Char) {
            if (sb.isEmpty() || sb[sb.length - 1] != ch) sb.append(ch)
        }
        while (i < n) {
            val c = s[i]
            val next = if (i + 1 < n) s[i + 1] else '\u0000'
            val next2 = if (i + 2 < n) s[i + 2] else '\u0000'
            when (c) {
                'h' -> {} // silent
                'c' -> when {
                    next == 'h' -> { push('ç'); i++ }
                    next == 'e' || next == 'i' -> push('s')
                    else -> push('k')
                }
                'q' -> { push('k'); if (next == 'u') i++ }
                'k' -> push('k')
                'z', 's' -> push('s')
                'v' -> push('b')
                'w' -> push('u')
                'x' -> { push('k'); push('s') }
                'l' -> if (next == 'l') { push('y'); i++ } else push('l')
                'g' -> when {
                    next == 'e' || next == 'i' -> push('j')
                    next == 'u' && (next2 == 'e' || next2 == 'i') -> { push('g'); i++ }
                    else -> push('g')
                }
                else -> push(c)
            }
            i++
        }
        return sb.toString()
    }

    /** Levenshtein distance with an early exit once it is certain to exceed [limit]. */
    fun editDistance(a: String, b: String, limit: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        val la = a.length
        val lb = b.length
        if (la == 0) return lb
        if (lb == 0) return la
        if (kotlin.math.abs(la - lb) > limit) return limit + 1
        var prev = IntArray(lb + 1) { it }
        var cur = IntArray(lb + 1)
        for (i in 1..la) {
            cur[0] = i
            var rowMin = cur[0]
            val ca = a[i - 1]
            for (j in 1..lb) {
                val cost = if (ca == b[j - 1]) 0 else 1
                var v = prev[j - 1] + cost
                val del = prev[j] + 1
                if (del < v) v = del
                val ins = cur[j - 1] + 1
                if (ins < v) v = ins
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1
            val t = prev; prev = cur; cur = t
        }
        return prev[lb]
    }

    /**
     * Probability in [0,1] that the recognised word [heard] is a rendition of the script word
     * [expected]. Both must be [canonical] and have their [phonetic] keys supplied.
     */
    fun matchProbability(
        heard: String,
        heardPhon: String,
        expected: String,
        expectedPhon: String,
    ): Double {
        if (heard == expected) return 1.0
        if (heardPhon == expectedPhon) return 0.92
        val len = maxOf(heardPhon.length, expectedPhon.length)
        if (len < 4) return 0.0
        if (kotlin.math.abs(heard.length - expected.length) > 2) return 0.0
        // Plurals and gender: "hermano" vs "hermanos", "ciudad" vs "ciudades".
        if (stripPlural(heard) == stripPlural(expected) && heard.length >= 4) return 0.7
        val limit = when {
            len >= 8 -> 2
            len >= 5 -> 1
            else -> 0
        }
        if (limit == 0) return 0.0
        val d = editDistance(heardPhon, expectedPhon, limit)
        return when {
            d > limit -> 0.0
            d == 1 -> 0.62
            else -> 0.45
        }
    }

    private fun stripPlural(w: String): String = when {
        w.length > 4 && w.endsWith("es") -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") -> w.dropLast(1)
        else -> w
    }
}
