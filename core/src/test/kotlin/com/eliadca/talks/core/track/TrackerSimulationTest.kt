package com.eliadca.talks.core.track

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Statistical tests: a simulated speaker reads (and sometimes improvises, skips, repeats) while a
 * simulated recogniser makes mistakes. The tracker must stay on the right word.
 */
class TrackerSimulationTest {

    private fun runMany(index: ScriptIndex, cfg: SimConfig, seeds: Int = 15, tracker: TrackerConfig = TrackerConfig()): List<SimScore> =
        (1..seeds).map { seed -> score(index, Simulator.generate(index, cfg, Random(seed * 7919L)), tracker) }

    private fun report(name: String, s: List<SimScore>) {
        val graded = s.sumOf { it.graded }
        val within = s.sumOf { it.within }
        val rec = s.flatMap { it.recoveries }.sorted()
        val unrec = s.sumOf { it.unrecovered }
        println(
            "%-34s accuracy=%.4f jumps=%d unrecovered=%d recovery median=%s p90=%s maxFinalErr=%d".format(
                name, within.toDouble() / graded, rec.size + unrec, unrec,
                rec.getOrNull(rec.size / 2) ?: "-", rec.getOrNull((rec.size * 9) / 10) ?: "-", s.maxOf { it.finalError },
            ),
        )
    }

    private fun accuracy(s: List<SimScore>) = s.sumOf { it.within }.toDouble() / s.sumOf { it.graded }

    @Test fun perfectRecognitionFollowsExactly() {
        val s = runMany(SampleScripts.practice, SimConfig(substitution = 0.0, drop = 0.0, filler = 0.0, gapMax = 0, truncatePartial = 0.0))
        report("perfect", s)
        assertTrue(accuracy(s) > 0.995)
        assertTrue(s.all { it.finalError <= 2 })
    }

    @Test fun noisyRecognitionStillFollows() {
        val s = runMany(SampleScripts.practice, SimConfig())
        report("noisy (6% wrong, 3% dropped)", s)
        assertTrue("accuracy ${accuracy(s)}", accuracy(s) > 0.97)
    }

    @Test fun veryNoisyRecognitionStillFollows() {
        val s = runMany(SampleScripts.practice, SimConfig(substitution = 0.15, drop = 0.08, filler = 0.05, gapMax = 3))
        report("very noisy (15% wrong, 8% dropped)", s)
        assertTrue("accuracy ${accuracy(s)}", accuracy(s) > 0.90)
    }

    @Test fun improvisationDoesNotLoseThePlace() {
        val s = runMany(SampleScripts.practice, SimConfig(digressionPerSentence = 0.10, paraphrasePerSentence = 0.06))
        report("improvising", s)
        assertTrue("accuracy ${accuracy(s)}", accuracy(s) > 0.92)
        val events = s.sumOf { it.digressionEvents }
        val anchored = s.sumOf { it.digressionAnchored }
        println("digression events=%d anchored=%.3f".format(events, anchored.toDouble() / events))
        assertTrue("pointer wandered during improvisation: ${anchored.toDouble() / events}", anchored.toDouble() / events > 0.85)
    }

    @Test fun improvisationReusingScriptVocabulary() {
        val s = runMany(
            SampleScripts.practice,
            SimConfig(digressionPerSentence = 0.10, paraphrasePerSentence = 0.06, scriptVocabularyInImprov = 0.2),
        )
        report("improvising with script words", s)
        assertTrue("accuracy ${accuracy(s)}", accuracy(s) > 0.90)
        val events = s.sumOf { it.digressionEvents }
        val anchored = s.sumOf { it.digressionAnchored }
        println("digression (script words) events=%d anchored=%.3f".format(events, anchored.toDouble() / events))
        assertTrue("pointer wandered during improvisation: ${anchored.toDouble() / events}", anchored.toDouble() / events > 0.75)
    }

    @Test fun recoversAfterSkipsAndRepeats() {
        val s = runMany(SampleScripts.practice, SimConfig(skipPerSentence = 0.08, backPerSentence = 0.06), seeds = 25)
        report("skips and repeats", s)
        val rec = s.flatMap { it.recoveries }.sorted()
        val unrec = s.sumOf { it.unrecovered }
        val total = rec.size + unrec
        assertTrue("jumps=$total", total > 20)
        assertTrue("unrecovered $unrec of $total", unrec * 20 <= total)
        assertTrue("median recovery ${rec[rec.size / 2]}", rec[rec.size / 2] <= 8)
        assertTrue("p90 recovery ${rec[rec.size * 9 / 10]}", rec[rec.size * 9 / 10] <= 20)
    }

    @Test fun repeatedPhrasesDoNotConfuseTheTracker() {
        val s = runMany(SampleScripts.refrains, SimConfig(), seeds = 30)
        report("refrains (repeated phrases)", s)
        assertTrue("accuracy ${accuracy(s)}", accuracy(s) > 0.93)
    }

    @Test fun repeatedPhrasesWithImprovisationAndJumps() {
        val s = runMany(
            SampleScripts.refrains,
            SimConfig(digressionPerSentence = 0.06, skipPerSentence = 0.04, backPerSentence = 0.04),
            seeds = 30,
        )
        report("refrains + improvisation/jumps", s)
        assertTrue("accuracy ${accuracy(s)}", accuracy(s) > 0.80)
    }

    @Test fun recogniserWithoutPartialResultsStillFollows() {
        val s = runMany(SampleScripts.practice, SimConfig(emitPartials = false, skipPerSentence = 0.03))
        report("finals only", s)
        assertTrue("accuracy ${accuracy(s)}", accuracy(s) > 0.95)
    }

    @Test fun largeScriptIsFastAndAccurate() {
        // ~12 000 words of Zipf-distributed pseudo-text.
        val rng = Random(42)
        val vocab = (0 until 3000).map { i ->
            val len = 3 + (i % 9)
            buildString { repeat(len) { append("abcdefghilmnoprstuv"[rng.nextInt(19)]) } }
        }
        fun zipf(): String = vocab[(Math.pow(rng.nextDouble(), 3.0) * vocab.size).toInt().coerceAtMost(vocab.size - 1)]
        val text = buildString {
            repeat(1000) {
                repeat(rng.nextInt(7, 15)) { append(zipf()).append(' ') }
                setLength(length - 1); append(". ")
            }
        }
        val index = ScriptIndex.build(text)
        assertTrue("size ${index.size}", index.size > 9000)
        val run = Simulator.generate(index, SimConfig(skipPerSentence = 0.01), Random(3))
        val t0 = System.nanoTime()
        val sc = score(index, run)
        val ms = (System.nanoTime() - t0) / 1e6
        println("large script: %d tokens, %d hypotheses, %.1f ms total, %.2f ms/hypothesis, %s".format(index.size, run.events.size, ms, ms / run.events.size, sc))
        assertTrue("accuracy ${sc.accuracy}", sc.accuracy > 0.93)
        assertTrue("slow: ${ms / run.events.size} ms per hypothesis", ms / run.events.size < 25.0)
    }
}
