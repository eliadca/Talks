package com.eliadca.talks.core.track

import com.eliadca.talks.core.text.Boundary
import com.eliadca.talks.core.text.ScriptToken
import com.eliadca.talks.core.text.SpanishText
import com.eliadca.talks.core.text.Tokenizer
import kotlin.math.abs

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

    /** Vocabulary id of each distinct word. */
    private val vocabIds: Map<String, Int>

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
        vocabIds = ids
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
     * True after token t when something that is not said lies before token t+1: a note, a heading
     * the speaker skips, or a line break. Marking never runs across such a gap.
     */
    private val breakAfter = BooleanArray(size).also { b ->
        for (t in 0 until size - 1) {
            var k = tokens[t].end
            val to = tokens[t + 1].start
            while (k < to) {
                val c = text[k]
                if (c == '\n' || c.isLetterOrDigit()) {
                    b[t] = true
                    break
                }
                k++
            }
        }
    }

    private fun endsSentence(t: Int) = tokens[t].boundary >= Boundary.SENTENCE

    /** True after token t when a line break lies before token t+1. */
    private val lineAfter = BooleanArray(size).also { b ->
        for (t in 0 until size - 1) {
            for (k in tokens[t].end until tokens[t + 1].start) if (text[k] == '\n') { b[t] = true; break }
        }
    }

    /** Where phrases begin: they end at commas, full stops and notes; tiny ones join a neighbour. */
    private val phraseStarts: IntArray = blocks(
        endsAfter = { t -> tokens[t].boundary >= Boundary.CLAUSE || breakAfter[t] },
        minWords = MIN_PHRASE,
        maxWords = MAX_PHRASE,
    )

    /** Where sentences begin; very long ones are split at the clause nearest their middle. */
    private val sentenceStarts: IntArray = blocks(
        endsAfter = { t -> endsSentence(t) },
        minWords = 1,
        maxWords = MAX_SENTENCE,
    )

    /**
     * Cuts the script into blocks that end after the tokens [endsAfter] accepts. A block shorter than
     * [minWords] joins the next block of the same sentence, or else the previous one, as long as no
     * note or line break lies in between; a block longer than [maxWords] is split at the clause
     * boundary nearest its middle, or into equal parts.
     */
    private fun blocks(endsAfter: (Int) -> Boolean, minWords: Int, maxWords: Int): IntArray {
        if (size == 0) return IntArray(0)
        val raw = ArrayList<IntArray>()
        var s = 0
        for (t in 0 until size) {
            if (t == size - 1 || endsAfter(t)) {
                raw += intArrayOf(s, t + 1)
                s = t + 1
            }
        }
        // Tiny blocks join a neighbour within the sentence.
        val joined = ArrayList<IntArray>()
        var i = 0
        while (i < raw.size) {
            val cur = raw[i].copyOf()
            while (cur[1] - cur[0] < minWords && i + 1 < raw.size && !endsSentence(cur[1] - 1) &&
                !breakAfter[cur[1] - 1] && raw[i + 1][1] - cur[0] <= maxWords
            ) {
                i++
                cur[1] = raw[i][1]
            }
            val prev = joined.lastOrNull()
            if (cur[1] - cur[0] < minWords && prev != null && !endsSentence(prev[1] - 1) &&
                !breakAfter[prev[1] - 1] && cur[1] - prev[0] <= maxWords
            ) {
                prev[1] = cur[1]
            } else {
                joined += cur
            }
            i++
        }
        val starts = ArrayList<Int>()
        for (b in joined) split(b[0], b[1], maxWords, starts)
        return starts.toIntArray()
    }

    /** Adds the starts of [from, until) split into pieces of at most [maxWords]. */
    private fun split(from: Int, until: Int, maxWords: Int, out: MutableList<Int>) {
        val len = until - from
        if (len <= maxWords) {
            out += from
            return
        }
        // Prefer a clause boundary near the middle; otherwise cut evenly.
        val mid = from + len / 2
        var best = -1
        for (t in from until until - 1) {
            if (tokens[t].boundary >= Boundary.CLAUSE || breakAfter[t]) {
                if (best < 0 || abs(t + 1 - mid) < abs(best - mid)) best = t + 1
            }
        }
        if (best <= from || best >= until || minOf(best - from, until - best) < len / 4) {
            val parts = (len + maxWords - 1) / maxWords
            for (k in 0 until parts) out += from + k * len / parts
            return
        }
        split(from, best, maxWords, out)
        split(best, until, maxWords, out)
    }

    /** Where paragraphs begin; very long ones are split near their middle. */
    private val paragraphStarts: IntArray = blocks(
        endsAfter = { t -> tokens[t].boundary >= Boundary.PARAGRAPH || lineAfter[t] },
        minWords = 1,
        maxWords = MAX_PARAGRAPH,
    )

    private fun startsFor(unit: MarkUnit): IntArray = when (unit) {
        MarkUnit.SENTENCE -> sentenceStarts
        MarkUnit.PARAGRAPH -> paragraphStarts
        else -> phraseStarts
    }

    /** Index in [starts] of the block that contains token [pos] (0 <= pos < size). */
    private fun blockIndex(starts: IntArray, pos: Int): Int {
        var lo = 0
        var hi = starts.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (starts[mid] <= pos) lo = mid + 1 else hi = mid
        }
        return (lo - 1).coerceAtLeast(0)
    }

    private fun blockEnd(starts: IntArray, i: Int): Int = if (i + 1 < starts.size) starts[i + 1] else size

    /** Start of the block of [unit] (phrases unless sentences are asked for) that contains [pos]. */
    fun blockStart(unit: MarkUnit, pos: Int): Int {
        if (pos >= size) return size
        val starts = startsFor(unit)
        return starts[blockIndex(starts, pos.coerceAtLeast(0))]
    }

    /** Start of the first block that begins after [pos]; [size] when there is none. */
    fun nextBlockStart(unit: MarkUnit, pos: Int): Int {
        if (pos >= size) return size
        val starts = startsFor(unit)
        return blockEnd(starts, blockIndex(starts, pos.coerceAtLeast(0)))
    }

    /** Start of the last block that begins before [pos]; 0 when there is none. */
    fun previousBlockStart(unit: MarkUnit, pos: Int): Int {
        if (size == 0 || pos <= 0) return 0
        val starts = startsFor(unit)
        val i = blockIndex(starts, (pos - 1).coerceAtMost(size - 1))
        return starts[i]
    }

    /** Start of the first phrase that begins after [pos]; [size] when there is none. */
    fun nextPhraseStart(pos: Int): Int = nextBlockStart(MarkUnit.PHRASE, pos)

    /** Start of the last phrase that begins before [pos]; 0 when there is none. */
    fun previousPhraseStart(pos: Int): Int = previousBlockStart(MarkUnit.PHRASE, pos)

    /**
     * End of the word-by-word window that starts at [pos]: at least a few words, up to a clause
     * boundary, never past the end of a sentence, a note or a line, and at most [maxWords] long.
     */
    fun wordWindowEnd(pos: Int, maxWords: Int): Int {
        if (pos >= size) return size
        val limit = minOf(size, pos + maxWords.coerceAtLeast(1))
        var k = pos
        while (k < limit) {
            if (endsSentence(k) || breakAfter[k]) return k + 1
            if (tokens[k].boundary != Boundary.NONE && k - pos + 1 >= MIN_WINDOW) return k + 1
            k++
        }
        return limit
    }

    /**
     * What to mark for a speaker whose next word is [pos]. The marked block is the one that holds
     * the word [Marking.lead] words away from the voice, but never more than one block ahead of or
     * behind the voice; only what the voice has really passed is dimmed.
     */
    fun mark(pos: Int, marking: Marking): Mark {
        val p = pos.coerceIn(0, size)
        if (p >= size) return Mark(size, size, size, size)
        val lead = marking.lead.coerceIn(Marking.MIN_LEAD, Marking.MAX_LEAD)
        val d = (p + lead).coerceIn(0, size - 1)
        return when (marking.unit) {
            MarkUnit.WORD -> Mark(d, wordWindowEnd(d, marking.maxWords), minOf(p, d), d)
            MarkUnit.NONE -> Mark(d, d, blockStart(MarkUnit.PHRASE, minOf(p, d)), d)
            MarkUnit.PHRASE, MarkUnit.SENTENCE, MarkUnit.PARAGRAPH -> {
                val starts = startsFor(marking.unit)
                val voice = blockIndex(starts, p)
                val shown = blockIndex(starts, d).coerceIn(voice - 1, voice + 1).coerceIn(0, starts.size - 1)
                val from = starts[shown]
                Mark(from, blockEnd(starts, shown), minOf(starts[voice], from), d)
            }
        }
    }

    /**
     * The text of tokens [from, until) as stretches that leave out notes, skipped headings and line
     * breaks, so that only words to be said are ever marked.
     */
    fun segments(from: Int, until: Int): List<CharSpan> {
        val a = from.coerceIn(0, size)
        val b = until.coerceIn(a, size)
        if (a >= b) return emptyList()
        val out = ArrayList<CharSpan>(2)
        var segStart = tokens[a].start
        for (t in a until b - 1) {
            if (breakAfter[t]) {
                out += CharSpan(segStart, tokens[t].end)
                segStart = tokens[t + 1].start
            }
        }
        out += CharSpan(segStart, tokens[b - 1].end)
        return out
    }

    /** Whether the script contains the (canonical) word [norm]. */
    fun hasWord(norm: String): Boolean = norm in vocabIds

    fun progress(pos: Int): Float = if (size == 0) 1f else (pos.coerceIn(0, size).toFloat() / size)

    companion object {
        /** Phrases shorter than this join a neighbour; longer than [MAX_PHRASE] they are split. */
        private const val MIN_PHRASE = 3
        private const val MAX_PHRASE = 16
        private const val MAX_SENTENCE = 30
        private const val MAX_PARAGRAPH = 90

        /** The word-by-word window covers at least this many words before stopping at a comma. */
        private const val MIN_WINDOW = 3

        fun build(text: String, silent: List<IntRange> = emptyList()): ScriptIndex {
            val all = Tokenizer.bracketRanges(text) + silent
            return ScriptIndex(text, Tokenizer.script(text, all))
        }
    }
}
