package com.eliadca.talks.core.track

import com.eliadca.talks.core.text.Boundary
import com.eliadca.talks.core.text.SpanishText
import kotlin.math.min
import kotlin.random.Random

/** Knobs of the simulated speaker + recogniser. Probabilities are per word or per sentence. */
class SimConfig(
    val substitution: Double = 0.06,
    val drop: Double = 0.03,
    val filler: Double = 0.02,
    val digressionPerSentence: Double = 0.0,
    val paraphrasePerSentence: Double = 0.0,
    val skipPerSentence: Double = 0.0,
    val backPerSentence: Double = 0.0,
    val sessionMin: Int = 6,
    val sessionMax: Int = 20,
    val gapMax: Int = 2,
    val truncatePartial: Double = 0.3,
    /** Share of improvised words taken from the script's own vocabulary (the speaker elaborates on the topic). */
    val scriptVocabularyInImprov: Double = 0.0,
    /** False simulates a recogniser that only delivers final results. */
    val emitPartials: Boolean = true,
    /** Chance per sentence of a long stretch of speech about something else entirely. */
    val offTopicPerSentence: Double = 0.0,
    val offTopicMin: Int = 40,
    val offTopicMax: Int = 160,
    /** Natural running text the off-topic speech is taken from (consecutive words). */
    val offTopicCorpus: List<String> = emptyList(),
    /** Chance that, after going off topic, the speaker comes back at the next sentence instead of where they left. */
    val offTopicResumeNext: Double = 0.3,
    /** Chance of a stray word (a filler, breath read as "y"...) between two sentences, before the next one starts. */
    val sentenceEndFiller: Double = 0.0,
    val sentenceEndFillers: List<String> = listOf("eh", "este", "pues", "y", "a", "bueno", "entonces"),
    /** Chance that the recogniser ends its session at the end of a sentence (the speaker pauses there). */
    val breakAtSentenceEnd: Double = 0.0,
)

class SimEvent(
    val text: String,
    val isFinal: Boolean,
    /** Where the tracker should say the speaker is: the next script word to be read. */
    val truth: Int,
    val graded: Boolean,
    /** Number of words spoken so far in the whole run (including words the recogniser lost). */
    val wordsSoFar: Int,
    /** True while the speaker is digressing: the tracker should keep the position where they left the script. */
    val digressing: Boolean = false,
    /** Which off-topic stretch this event belongs to (-1: none). */
    val offTopicId: Int = -1,
    /** Index of the off-topic stretch that ended most recently before this event (-1: none yet). */
    val afterOffTopic: Int = -1,
)

class SimRun(val events: List<SimEvent>, val jumps: List<Int>, val offTopicCount: Int = 0)

/** Words used for improvisation, fillers and substitutions: deliberately unrelated to the scripts. */
private val POOL = (
    "pues bueno entonces realmente verdad claro mira fijense imaginen recuerdo semana ejemplo trabajo " +
        "empresa tecnologia proyecto equipo mercado cliente resultado proceso calidad servicio cambio " +
        "problema solucion importante necesario diferente algunos siempre nunca tambien ademas todavia " +
        "cuando mientras aunque porque como donde quien cuales cuanto nuestro vuestro sencillo dificil " +
        "grande pequeno primero segundo final inicio momento historia persona ciudad pais mundo camino " +
        "puerta ventana mesa libro musica pelicula deporte comida viaje playa montana rio mar cielo " +
        "ayer hoy manana semana mes ano hora minuto dinero precio costo ganancia perdida riesgo " +
        "oportunidad esfuerzo disciplina energia tiempo espacio idea pregunta respuesta duda certeza"
    ).split(" ")

private val FILLERS = listOf("eh", "este", "pues", "mm", "bueno")

object Simulator {

    fun generate(index: ScriptIndex, cfg: SimConfig, rng: Random): SimRun {
        data class Spoken(
            val word: String,
            val truthAfter: Int,
            val graded: Boolean,
            val digressing: Boolean = false,
            val offTopicId: Int = -1,
            val afterOffTopic: Int = -1,
        )

        val n = index.size
        val tokens = index.tokens
        val spoken = ArrayList<Spoken>()
        val jumpWordIdx = ArrayList<Int>()
        var pos = 0
        var grace = 0
        var offTopicCount = 0

        fun sentenceStart(p: Int) = p == 0 || tokens[p - 1].boundary >= Boundary.SENTENCE
        fun nextSentenceStart(p: Int): Int {
            var k = p + 1
            while (k < n && !sentenceStart(k)) k++
            return min(k, n)
        }
        fun prevSentenceStart(p: Int): Int {
            var k = p - 1
            while (k > 0 && !sentenceStart(k)) k--
            return maxOf(0, k)
        }

        fun improvWord(): String =
            if (rng.nextDouble() < cfg.scriptVocabularyInImprov) tokens[rng.nextInt(n)].norm else POOL.random(rng)

        fun mutate(w: String): String {
            if (w.length < 3) return POOL.random(rng)
            return when (rng.nextInt(3)) {
                0 -> { val i = rng.nextInt(w.length); w.substring(0, i) + ('a' + rng.nextInt(26)) + w.substring(i + 1) }
                1 -> { val i = rng.nextInt(w.length); w.removeRange(i, i + 1) }
                else -> POOL.random(rng)
            }
        }

        while (pos < n) {
            // Random draws only for knobs that are on, so existing seeded runs stay the same.
            if (sentenceStart(pos) && pos > 0 && cfg.sentenceEndFiller > 0 && rng.nextDouble() < cfg.sentenceEndFiller) {
                // A filler that is the sentence's own first word would just be reading it.
                val choices = cfg.sentenceEndFillers.filter { SpanishText.canonical(it) != tokens[pos].norm }
                if (choices.isNotEmpty()) spoken += Spoken(choices.random(rng), pos, false)
            }
            if (sentenceStart(pos) && pos > 0 && cfg.offTopicCorpus.isNotEmpty() && rng.nextDouble() < cfg.offTopicPerSentence) {
                // A long stretch about something else entirely, taken as running text from the corpus.
                val k = rng.nextInt(cfg.offTopicMin, cfg.offTopicMax + 1)
                val start = rng.nextInt(cfg.offTopicCorpus.size)
                val id = offTopicCount++
                repeat(k) { i -> spoken += Spoken(cfg.offTopicCorpus[(start + i) % cfg.offTopicCorpus.size], pos, false, true, id) }
                if (rng.nextDouble() < cfg.offTopicResumeNext) {
                    val next = nextSentenceStart(pos)
                    if (next < n) pos = next
                }
                grace = 8
            }
            if (sentenceStart(pos)) {
                val r = rng.nextDouble()
                var edge = cfg.digressionPerSentence
                if (r < edge) {
                    val k = rng.nextInt(6, 26)
                    // Only digressions that start from a clean reading are used to measure drift.
                    val clean = grace == 0
                    repeat(k) { spoken += Spoken(improvWord(), pos, false, digressing = clean) }
                    grace = 6
                } else {
                    edge += cfg.paraphrasePerSentence
                    if (r < edge) {
                        val e = nextSentenceStart(pos)
                        val k = maxOf(3, (e - pos) + rng.nextInt(-2, 4))
                        repeat(k) { spoken += Spoken(improvWord(), e, false) }
                        pos = e
                        grace = 6
                        continue
                    }
                    edge += cfg.skipPerSentence
                    if (r < edge) {
                        var target = pos
                        repeat(rng.nextInt(1, 4)) { target = nextSentenceStart(target) }
                        if (target < n) {
                            pos = target
                            jumpWordIdx += spoken.size
                            grace = 12
                        }
                    } else {
                        edge += cfg.backPerSentence
                        if (r < edge && pos > 0) {
                            var target = pos
                            repeat(rng.nextInt(1, 4)) { target = prevSentenceStart(target) }
                            pos = target
                            jumpWordIdx += spoken.size
                            grace = 12
                        }
                    }
                }
            }
            if (pos >= n) break

            if (rng.nextDouble() < cfg.filler) {
                spoken += Spoken(FILLERS.random(rng), pos, false)
            }
            val tok = tokens[pos]
            val r = rng.nextDouble()
            val graded = grace == 0
            when {
                r < cfg.drop -> {}
                r < cfg.drop + cfg.substitution -> spoken += Spoken(mutate(tok.norm), pos + 1, graded)
                else -> spoken += Spoken(tok.norm, pos + 1, graded)
            }
            // A dropped word still moves the speaker on; make the truth of the previous event match.
            if (r < cfg.drop && spoken.isNotEmpty()) {
                val last = spoken.removeAt(spoken.lastIndex)
                spoken += Spoken(last.word, pos + 1, last.graded && graded)
            }
            pos++
            if (grace > 0) grace--
        }

        // Remember, for every word, which off-topic stretch ended last before it.
        run {
            var last = -1
            for (k in spoken.indices) {
                val w = spoken[k]
                if (w.offTopicId >= 0) last = w.offTopicId
                else if (last >= 0) spoken[k] = w.copy(afterOffTopic = last)
            }
        }

        // Cut the stream of words into recogniser sessions with growing, sometimes truncated, partials.
        val events = ArrayList<SimEvent>()
        var i = 0
        while (i < spoken.size) {
            val len = rng.nextInt(cfg.sessionMin, cfg.sessionMax + 1)
            var end = min(spoken.size, i + len)
            if (cfg.breakAtSentenceEnd > 0) {
                // The speaker pauses at the end of a sentence and the recogniser starts a new session.
                for (k in i until end - 1) {
                    val t = spoken[k].truthAfter
                    if (t < n && t > 0 && sentenceStart(t) && spoken[k + 1].truthAfter >= t && rng.nextDouble() < cfg.breakAtSentenceEnd) {
                        end = k + 1
                        break
                    }
                }
            }
            for (k in (if (cfg.emitPartials) i until end else IntRange.EMPTY)) {
                val words = ArrayList<String>()
                for (m in i..k) words += spoken[m].word
                if (rng.nextDouble() < cfg.truncatePartial && words.last().length > 3) {
                    val w = words.last()
                    words[words.lastIndex] = w.substring(0, maxOf(2, (w.length * 0.6).toInt()))
                }
                val sk = spoken[k]
                events += SimEvent(words.joinToString(" "), false, sk.truthAfter, sk.graded, k + 1, sk.digressing, sk.offTopicId, sk.afterOffTopic)
            }
            val finalWords = (i until end).joinToString(" ") { spoken[it].word }
            val se = spoken[end - 1]
            events += SimEvent(finalWords, true, se.truthAfter, se.graded, end, se.digressing, se.offTopicId, se.afterOffTopic)
            i = end + if (cfg.gapMax > 0) rng.nextInt(0, cfg.gapMax + 1) else 0
        }
        return SimRun(events, jumpWordIdx, offTopicCount)
    }
}

class SimScore(
    val graded: Int,
    val within: Int,
    val recoveries: List<Int>,
    val unrecovered: Int,
    val finalError: Int,
    val digressionEvents: Int = 0,
    val digressionAnchored: Int = 0,
) {
    val accuracy get() = if (graded == 0) 1.0 else within.toDouble() / graded
    override fun toString() =
        "accuracy=%.4f (%d/%d) jumps=%d unrecovered=%d medianRecovery=%s finalError=%d".format(
            accuracy, within, graded, recoveries.size + unrecovered, unrecovered,
            recoveries.sorted().let { if (it.isEmpty()) "-" else it[it.size / 2].toString() }, finalError,
        )
}

fun score(index: ScriptIndex, run: SimRun, config: TrackerConfig = TrackerConfig(), tolerance: Int = 5): SimScore {
    val tracker = SpeechTracker(index, config)
    var graded = 0
    var within = 0
    val recovered = HashMap<Int, Int>()
    var lastPos = 0
    var lastTruth = 0
    var digressionEvents = 0
    var digressionAnchored = 0
    for (e in run.events) {
        val st = tracker.onHypothesis(e.text, e.isFinal)
        lastPos = st.position
        lastTruth = e.truth
        val err = kotlin.math.abs(st.position - e.truth)
        if (e.graded) {
            graded++
            if (err <= tolerance) within++
        }
        if (e.digressing) {
            digressionEvents++
            // Staying put (or drifting a few words ahead early on) is right; wandering off is not.
            if (st.position - e.truth in -3..6) digressionAnchored++
        }
        if (err <= tolerance) {
            for ((j, w) in run.jumps.withIndex()) {
                if (j !in recovered && e.wordsSoFar > w) recovered[j] = e.wordsSoFar - w
            }
        }
    }
    val recoveries = recovered.values.toList()
    return SimScore(graded, within, recoveries, run.jumps.size - recoveries.size, kotlin.math.abs(lastPos - lastTruth), digressionEvents, digressionAnchored)
}

/** How often the marker went past the first word of a sentence before the speaker said it. */
class StartScore(val starts: Int, val skipped: Int, val aheadEvents: Int, val events: Int) {
    val skippedShare get() = if (starts == 0) 0.0 else skipped.toDouble() / starts
    override fun toString() = "sentence starts=%d skipped=%d (%.4f) ahead events=%d/%d".format(starts, skipped, skippedShare, aheadEvents, events)
}

/**
 * Scores the moments when the speaker has finished a sentence (and maybe said a stray word) but not
 * yet the first word of the next one: the marker must not already be past that word.
 */
fun scoreSentenceStarts(index: ScriptIndex, run: SimRun, config: TrackerConfig = TrackerConfig()): StartScore {
    val tracker = SpeechTracker(index, config)
    val tokens = index.tokens
    fun isStart(p: Int) = p in 1 until index.size && tokens[p - 1].boundary >= com.eliadca.talks.core.text.Boundary.SENTENCE
    var starts = 0
    var skipped = 0
    var ahead = 0
    var events = 0
    var current = -1
    var currentSkipped = false
    for (e in run.events) {
        val st = tracker.onHypothesis(e.text, e.isFinal)
        if (isStart(e.truth)) {
            if (e.truth != current) {
                if (currentSkipped) skipped++
                current = e.truth
                currentSkipped = false
                starts++
            }
            events++
            if (st.position > e.truth) {
                ahead++
                currentSkipped = true
            }
        }
    }
    if (currentSkipped) skipped++
    return StartScore(starts, skipped, ahead, events)
}
