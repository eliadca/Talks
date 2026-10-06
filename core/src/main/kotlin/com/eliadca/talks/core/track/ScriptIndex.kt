package com.eliadca.talks.core.track

import com.eliadca.talks.core.text.Boundary
import com.eliadca.talks.core.text.ScriptToken
import com.eliadca.talks.core.text.SpanishText
import com.eliadca.talks.core.text.Tokenizer

/**
 * A speech prepared for tracking: its spoken tokens, a vocabulary of the distinct words, and the
 * positions at which each word occurs. Built once per speech when Talks mode starts.
 */
class ScriptIndex(val text: String, val tokens: List<ScriptToken>) {

    val size: Int = tokens.size

    /** Distinct canonical words of the script. */
    val vocab: Array<String>
    val vocabPhon: Array<String>

    /** For each token, the id of its word in [vocab]. */
    val tokenVocab: IntArray

    /** For each vocabulary id, the token positions where it occurs. */
    val occurrences: Array<IntArray>

    init {
        val ids = HashMap<String, Int>()
        val words = ArrayList<String>()
        val phons = ArrayList<String>()
        tokenVocab = IntArray(size)
        for ((i, t) in tokens.withIndex()) {
            val id = ids.getOrPut(t.norm) {
                words += t.norm
                phons += t.phon
                words.size - 1
            }
            tokenVocab[i] = id
        }
        vocab = words.toTypedArray()
        vocabPhon = phons.toTypedArray()
        val counts = IntArray(vocab.size)
        for (id in tokenVocab) counts[id]++
        val occ = Array(vocab.size) { IntArray(counts[it]) }
        val fill = IntArray(vocab.size)
        for ((i, id) in tokenVocab.withIndex()) occ[id][fill[id]++] = i
        occurrences = occ
    }

    /** Text offset where token [pos] starts; the end of the text when the speech is finished. */
    fun startChar(pos: Int): Int = if (pos >= size) text.length else tokens[pos].start

    /** Text offset where token [pos] ends. */
    fun endChar(pos: Int): Int = if (pos >= size) text.length else tokens[pos].end

    /**
     * Index just past the "chunk" that starts at [pos]: the words the speaker should say next,
     * ending at the first clause or sentence boundary once at least [minWords] are included, and
     * never longer than [maxWords].
     */
    fun chunkEnd(pos: Int, minWords: Int, maxWords: Int): Int {
        if (pos >= size) return size
        val limit = minOf(size, pos + maxWords)
        var k = pos
        while (k < limit) {
            if (tokens[k].boundary != Boundary.NONE && (k - pos + 1) >= minWords) return k + 1
            k++
        }
        return limit
    }

    /** First token whose end lies after [offset]; [size] when [offset] is past the last token. */
    fun tokenAtChar(offset: Int): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (tokens[mid].end > offset) hi = mid else lo = mid + 1
        }
        return lo
    }

    /**
     * Token indices where a phrase begins: the first token, each token after a clause/sentence
     * boundary, and every [PHRASE_LIMIT] words inside a very long unpunctuated run.
     */
    private val phraseStarts: IntArray = run {
        val starts = ArrayList<Int>()
        if (size > 0) starts += 0
        var run = 0
        for (i in 0 until size) {
            run++
            if (i + 1 < size && (tokens[i].boundary != Boundary.NONE || run >= PHRASE_LIMIT)) {
                starts += i + 1
                run = 0
            }
        }
        starts.toIntArray()
    }

    /** Start of the first phrase that begins after [pos]; [size] when there is none. */
    fun nextPhraseStart(pos: Int): Int {
        var lo = 0
        var hi = phraseStarts.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (phraseStarts[mid] > pos) hi = mid else lo = mid + 1
        }
        return if (lo < phraseStarts.size) phraseStarts[lo] else size
    }

    /** Start of the last phrase that begins before [pos]; 0 when there is none. */
    fun previousPhraseStart(pos: Int): Int {
        var lo = 0
        var hi = phraseStarts.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (phraseStarts[mid] >= pos) hi = mid else lo = mid + 1
        }
        return if (lo > 0) phraseStarts[lo - 1] else 0
    }

    fun progress(pos: Int): Float = if (size == 0) 1f else (pos.coerceIn(0, size).toFloat() / size)

    companion object {
        private const val PHRASE_LIMIT = 14

        fun build(text: String, silent: List<IntRange> = emptyList()): ScriptIndex {
            val all = Tokenizer.bracketRanges(text) + silent
            return ScriptIndex(text, Tokenizer.script(text, all))
        }
    }
}
