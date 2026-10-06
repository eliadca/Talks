package com.eliadca.talks.core.track

import com.eliadca.talks.core.text.HeardWord
import com.eliadca.talks.core.text.SpanishFrequency
import com.eliadca.talks.core.text.SpanishText
import com.eliadca.talks.core.text.Tokenizer
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.min

/** Tunable probabilities of the tracking model. The defaults come from simulation (see tests). */
data class TrackerConfig(
    /** Chance that the recogniser mangles a word that was really read from the script. */
    val recognitionError: Double = 0.12,
    /** While reading: chance that the next word is the very next script word. */
    val readAdvance: Double = 0.83,
    /** While reading: total chance that the speaker skipped some script words before this one. */
    val readSkip: Double = 0.06,
    /** How quickly longer skips become less likely. */
    val skipDecay: Double = 0.65,
    val maxSkip: Int = 14,
    /** While reading: chance that a single extra word (filler, aside) is inserted. */
    val readInsert: Double = 0.04,
    /** While reading: chance that the speaker starts improvising, leaving the script. */
    val startFree: Double = 0.05,
    /** While improvising: chance of staying off-script for another word. */
    val stayFree: Double = 0.88,
    /** While improvising: chance of going back to reading the script on this word. */
    val resumeReading: Double = 0.10,
    /** When resuming, how many script words may have been replaced by the improvisation. */
    val maxResumeSkip: Int = 40,
    val resumeSkipDecay: Double = 0.93,
    /** Chance that the speaker jumped anywhere else in the script (spread uniformly). */
    val jump: Double = 0.005,
    /** Forward moves up to this many words are shown at once; bigger moves must be confirmed. */
    val freeForward: Int = 8,
    /** Belief needed before a big move (a jump forwards or back) is even considered. */
    val jumpConfidence: Float = 0.55f,
    /** How many consecutive recogniser updates must agree on a big move before it is shown. */
    val jumpConfirmations: Int = 2,
)

enum class TrackStatus {
    /** Nothing heard yet. */
    WAITING,

    /** The position is well established. */
    FOLLOWING,

    /** The speaker is off-script (improvising); the position stays where they left the script. */
    OFF_SCRIPT,

    /** Recent speech does not match the script clearly and the position is uncertain. */
    SEARCHING,
}

/**
 * What the UI needs to know after each recogniser update.
 *
 * [position] is the index of the next word the speaker should say; everything before it counts as
 * already spoken.
 */
data class TrackerState(
    val position: Int,
    val confidence: Float,
    val status: TrackStatus,
    val finished: Boolean,
)

/**
 * Follows a speaker through a script from a stream of speech-recognition results.
 *
 * Model: a hidden Markov model whose state is a pair (mode, position). In READING mode every heard
 * word is expected to be the next script word, or a few words later if the speaker skipped some, or
 * occasionally a stray extra word. In IMPROVISING mode the speaker is off-script: the position stays
 * where they left the script until they start reading again, possibly further on. At any moment the
 * speaker may also jump to any other place. Each heard word is scored against the script word it
 * would align with, and the evidence is weighed by how common the word is, so matching "esperanza"
 * counts far more than matching "de". The belief over states is updated word by word; the answer is
 * where most of the belief sits.
 *
 * Recognisers send growing, sometimes revised, partial hypotheses followed by a final one. Each
 * hypothesis is therefore re-evaluated from the belief at the start of its session, which makes
 * revisions harmless; only a final hypothesis commits.
 *
 * Not thread-safe: call from a single thread.
 */
class SpeechTracker(
    private val index: ScriptIndex,
    private val config: TrackerConfig = TrackerConfig(),
) {
    private val n = index.size

    /** Probability mass over positions 0..n for each mode. Entries outside [lo, hi] are zero. */
    private class Belief(n: Int) {
        val read = DoubleArray(n + 2)
        val free = DoubleArray(n + 2)
        var lo = 0
        var hi = -1

        fun clear() {
            if (hi >= lo) {
                Arrays.fill(read, lo, hi + 1, 0.0)
                Arrays.fill(free, lo, hi + 1, 0.0)
            }
            lo = 0; hi = -1
        }

        fun copyFrom(o: Belief) {
            clear()
            if (o.hi >= o.lo) {
                System.arraycopy(o.read, o.lo, read, o.lo, o.hi - o.lo + 1)
                System.arraycopy(o.free, o.lo, free, o.lo, o.hi - o.lo + 1)
            }
            lo = o.lo; hi = o.hi
        }

        fun setPoint(pos: Int) {
            clear()
            read[pos] = 1.0
            lo = pos; hi = pos
        }

        fun total(p: Int) = read[p] + free[p]
    }

    private val committed = Belief(n)
    private val base = Belief(n)
    private val bufA = Belief(n)
    private val bufB = Belief(n)
    private var result = bufA

    /** Probability of reading script word p+s next, given that the previous word was read. */
    private val readSkipWeights = DoubleArray(config.maxSkip).also { w ->
        w[0] = config.readAdvance
        if (config.maxSkip > 1) {
            val d = config.skipDecay
            val norm = (1 - d) / (1 - Math.pow(d, (config.maxSkip - 1).toDouble()))
            for (s in 1 until config.maxSkip) w[s] = config.readSkip * norm * Math.pow(d, (s - 1).toDouble())
        }
    }

    /** Where reading resumes after an improvisation: usually right where it stopped, sometimes later. */
    private val resumeWeights = DoubleArray(config.maxResumeSkip).also { w ->
        val d = config.resumeSkipDecay
        var sum = 0.0
        for (s in w.indices) { w[s] = Math.pow(d, s.toDouble()); sum += w[s] }
        // Half of the weight sits on "exactly where I left off"; the rest decays with distance.
        val rest = sum - w[0]
        for (s in w.indices) w[s] = if (s == 0) 0.5 else 0.5 * w[s] / rest
        for (s in w.indices) w[s] *= config.resumeReading
    }

    private val similarityCache = object : LinkedHashMap<String, FloatArray>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean = size > 256
    }
    private val similarityNonZero = HashMap<String, IntArray>()

    private var segmentOpen = false
    private var hasSpoken = false
    private var lastWords: List<HeardWord> = emptyList()

    /** Words of the in-flight recogniser session that were heard before a manual reposition. */
    private var stale: List<HeardWord> = emptyList()

    private var displayPos = 0
    private var confidence = 0f
    private var status = TrackStatus.WAITING
    private var pendingPos = -1
    private var pendingCount = 0
    private var backCount = 0

    init {
        reset(0)
    }

    val state: TrackerState
        get() = TrackerState(
            position = displayPos,
            confidence = confidence,
            status = status,
            finished = n == 0 || displayPos >= n,
        )

    /** Starts over with the speaker at script word [start]. */
    fun reset(start: Int = 0) {
        val s = start.coerceIn(0, n)
        committed.setPoint(s)
        segmentOpen = false
        hasSpoken = false
        lastWords = emptyList()
        stale = emptyList()
        displayPos = s
        confidence = 1f
        status = TrackStatus.WAITING
        pendingPos = -1; pendingCount = 0; backCount = 0
    }

    /**
     * Moves the position by hand (the speaker tapped the text, or used a remote). Words already in
     * the recogniser's current session are ignored so they are not counted again at the new place.
     */
    fun setPosition(position: Int) {
        val s = position.coerceIn(0, n)
        stale = if (segmentOpen) lastWords else emptyList()
        committed.setPoint(s)
        segmentOpen = false
        lastWords = emptyList()
        displayPos = s
        confidence = 1f
        status = if (hasSpoken) TrackStatus.FOLLOWING else TrackStatus.WAITING
        pendingPos = -1; pendingCount = 0; backCount = 0
    }

    /**
     * Feeds one recogniser result. [text] is the whole hypothesis for the current session so far
     * (partials grow and may be revised); [isFinal] marks the session's last result.
     */
    fun onHypothesis(text: String, isFinal: Boolean): TrackerState {
        var words = Tokenizer.heard(text)

        if (stale.isNotEmpty()) {
            if (startsWithMostly(words, stale)) {
                words = words.subList(stale.size, words.size)
            } else {
                stale = emptyList()
            }
        }
        if (words.isEmpty()) {
            if (isFinal) {
                closeSegment()
                stale = emptyList()
            }
            return state
        }

        // How many words this update adds; a recogniser that sends no partials delivers many at once.
        var newWords = words.size
        if (segmentOpen) {
            if (looksLikeNewSession(lastWords, words)) closeSegment()
            else newWords = maxOf(1, words.size - lastWords.size)
        }
        if (!segmentOpen) openSegment()

        runForward(words, prefixLast = !isFinal)
        lastWords = words
        hasSpoken = true
        updateDisplay(result, newWords)
        if (isFinal) {
            closeSegment()
            stale = emptyList()
        }
        return state
    }

    // --- segments ---------------------------------------------------------------------------

    private fun openSegment() {
        base.copyFrom(committed)
        if (hasSpoken) blurForGap(base)
        segmentOpen = true
    }

    private fun closeSegment() {
        if (segmentOpen) {
            committed.copyFrom(result)
            segmentOpen = false
        }
        lastWords = emptyList()
    }

    /**
     * Between two recogniser sessions a word or two of speech is usually lost, so before a new
     * session the belief is smeared a little forward.
     */
    private fun blurForGap(b: Belief) {
        if (b.hi < b.lo) return
        val spread = GAP_SPREAD
        val tmp = bufB
        tmp.clear()
        val newHi = min(n, b.hi + spread.size - 1)
        for (p in b.lo..b.hi) {
            val br = b.read[p]
            val bf = b.free[p]
            if (br <= 0.0 && bf <= 0.0) continue
            for (s in spread.indices) {
                val q = p + s
                if (q > n) break
                tmp.read[q] += br * spread[s]
                tmp.free[q] += bf * spread[s]
            }
        }
        tmp.lo = b.lo; tmp.hi = newHi
        normalize(tmp)
        b.copyFrom(tmp)
        tmp.clear()
    }

    private fun looksLikeNewSession(prev: List<HeardWord>, cur: List<HeardWord>): Boolean {
        if (prev.size < 4) return false
        if (cur.size + 2 >= prev.size) return false
        var common = 0
        val m = min(prev.size, cur.size)
        while (common < m && prev[common].norm == cur[common].norm) common++
        return common * 2 < prev.size
    }

    private fun startsWithMostly(words: List<HeardWord>, prefix: List<HeardWord>): Boolean {
        if (words.size < prefix.size) return false
        var same = 0
        for (i in prefix.indices) if (words[i].norm == prefix[i].norm) same++
        return same * 10 >= prefix.size * 6
    }

    // --- the model --------------------------------------------------------------------------

    private fun runForward(words: List<HeardWord>, prefixLast: Boolean) {
        var cur = base
        var out = bufA
        for ((i, w) in words.withIndex()) {
            val prefix = prefixLast && i == words.lastIndex
            step(cur, out, w, prefix)
            cur = out
            out = if (cur === bufA) bufB else bufA
        }
        result = cur
    }

    private fun step(src: Belief, dst: Belief, word: HeardWord, prefix: Boolean) {
        dst.clear()
        if (src.hi < src.lo) return
        val sim = similarity(word, prefix)
        val prw = SpanishFrequency.probability(word.norm)
        val err = config.recognitionError
        val tokenVocab = index.tokenVocab
        val maxReach = maxOf(config.maxSkip, config.maxResumeSkip)

        val readInsert = config.readInsert * prw
        val toFree = config.startFree * prw
        val stayFree = config.stayFree * prw

        var lo = src.lo
        var hi = min(n, src.hi + maxReach)
        var total = 0.0
        for (p in src.lo..src.hi) {
            val br = src.read[p]
            val bf = src.free[p]
            if (br <= 0.0 && bf <= 0.0) continue
            total += br + bf

            if (br > 0.0) {
                dst.read[p] += br * readInsert
                dst.free[p] += br * toFree
                var s = 0
                val reach = if (prefix) min(readSkipWeights.size, PREFIX_REACH) else readSkipWeights.size
                while (s < reach) {
                    val q = p + s
                    if (q >= n) break
                    val align = (1 - err) * sim[tokenVocab[q]] + err * prw
                    dst.read[q + 1] += br * readSkipWeights[s] * align
                    s++
                }
            }
            if (bf > 0.0) {
                dst.free[p] += bf * stayFree
                var s = 0
                val reach = if (prefix) min(resumeWeights.size, PREFIX_REACH) else resumeWeights.size
                while (s < reach) {
                    val q = p + s
                    if (q >= n) break
                    val align = (1 - err) * sim[tokenVocab[q]] + err * prw
                    dst.read[q + 1] += bf * resumeWeights[s] * align
                    s++
                }
            }
        }

        // Jumps: the speaker went somewhere else and this word is the first read there. A word that
        // is still being recognised (a prefix) is never enough to justify one.
        if (n > 0 && total > 0.0 && !prefix) {
            val seed = total * config.jump / n
            val ids = similarityNonZero[cacheKey(word, prefix)]
            if (ids != null) {
                for (v in ids) {
                    val add = seed * ((1 - err) * sim[v] + err * prw)
                    for (q in index.occurrences[v]) {
                        dst.read[q + 1] += add
                        if (q + 1 > hi) hi = q + 1
                        if (q + 1 < lo) lo = q + 1
                    }
                }
            }
        }
        dst.lo = lo
        dst.hi = min(n, hi)
        normalize(dst)
    }

    /** Scales [b] to sum to one and drops negligible entries, tightening its bounds. */
    private fun normalize(b: Belief) {
        var sum = 0.0
        for (p in b.lo..b.hi) sum += b.read[p] + b.free[p]
        if (sum <= 0.0 || sum.isNaN()) {
            // Cannot happen with a sane model, but never leave the tracker without a belief.
            b.setPoint(displayPos.coerceIn(0, n))
            return
        }
        val inv = 1.0 / sum
        var newLo = Int.MAX_VALUE
        var newHi = -1
        for (p in b.lo..b.hi) {
            val r = b.read[p] * inv
            val f = b.free[p] * inv
            if (r + f < PRUNE) {
                b.read[p] = 0.0
                b.free[p] = 0.0
            } else {
                b.read[p] = r
                b.free[p] = f
                if (p < newLo) newLo = p
                newHi = p
            }
        }
        if (newHi < 0) {
            b.setPoint(displayPos.coerceIn(0, n))
            return
        }
        b.lo = newLo
        b.hi = newHi
    }

    private fun cacheKey(w: HeardWord, prefix: Boolean) = if (prefix) w.norm + "*" else w.norm

    /** For every distinct script word, the probability that [word] is a rendition of it. */
    private fun similarity(word: HeardWord, prefix: Boolean): FloatArray {
        val key = cacheKey(word, prefix)
        similarityCache[key]?.let { return it }
        val vocab = index.vocab
        val phon = index.vocabPhon
        val out = FloatArray(vocab.size)
        val nonZero = ArrayList<Int>()
        for (v in vocab.indices) {
            var m = SpanishText.matchProbability(word.norm, word.phon, vocab[v], phon[v])
            if (prefix && word.norm.length >= 3 && vocab[v].length > word.norm.length &&
                (vocab[v].startsWith(word.norm) || phon[v].startsWith(word.phon))
            ) {
                m = maxOf(m, if (word.norm.length >= 4) 0.5 else 0.3)
            }
            if (m > 0.0) {
                out[v] = m.toFloat()
                nonZero += v
            }
        }
        similarityCache[key] = out
        similarityNonZero[key] = nonZero.toIntArray()
        if (similarityNonZero.size > 512) {
            // Keep the side table in step with the LRU cache.
            similarityNonZero.keys.retainAll(similarityCache.keys.toSet())
        }
        return out
    }

    // --- output filter ----------------------------------------------------------------------

    private fun updateDisplay(b: Belief, newWords: Int) {
        if (b.hi < b.lo) return
        var best = b.lo
        var bestV = -1.0
        var freeMass = 0.0
        for (p in b.lo..b.hi) {
            val v = b.total(p)
            freeMass += b.free[p]
            if (v >= bestV * 0.999) { // ties go to the later position
                if (v >= bestV) bestV = v
                best = p
            }
        }
        var mass = 0.0
        for (p in maxOf(b.lo, best - 3)..min(b.hi, best + 3)) mass += b.total(p)
        confidence = mass.toFloat()
        status = when {
            freeMass >= 0.6 -> TrackStatus.OFF_SCRIPT
            confidence >= 0.4f -> TrackStatus.FOLLOWING
            else -> TrackStatus.SEARCHING
        }

        val delta = best - displayPos
        // Moving forward by about as many words as were just heard is normal reading.
        val allowance = config.freeForward + newWords * 2
        // Several words that all agree on a far-away place are strong evidence on their own.
        val strong = newWords >= 4 && confidence >= 0.8f
        when {
            delta == 0 -> { pendingCount = 0; backCount = 0 }
            delta in 1..allowance -> accept(best)
            delta in -3..-1 -> {
                // A small step back is usually a revised partial hypothesis; wait for it to persist.
                backCount++
                if (backCount >= 3) accept(best)
            }
            else -> {
                // A big move (a skip or a jump back) must be confident and repeated.
                if (strong) {
                    accept(best)
                } else if (confidence >= config.jumpConfidence) {
                    if (pendingPos >= 0 && abs(pendingPos - best) <= 6) pendingCount++ else {
                        pendingPos = best; pendingCount = 1
                    }
                    if (pendingCount >= config.jumpConfirmations) accept(best)
                } else {
                    pendingPos = -1; pendingCount = 0
                }
            }
        }
    }

    private fun accept(pos: Int) {
        displayPos = pos
        pendingPos = -1; pendingCount = 0; backCount = 0
    }

    private companion object {
        const val PRUNE = 1e-9

        /** Farthest ahead a half-heard word (the unfinished last word of a partial result) may match. */
        const val PREFIX_REACH = 4

        /** Probability that 0,1,2... words were lost in the gap between two recogniser sessions. */
        val GAP_SPREAD = doubleArrayOf(0.55, 0.20, 0.11, 0.07, 0.04, 0.02, 0.01)
    }
}
