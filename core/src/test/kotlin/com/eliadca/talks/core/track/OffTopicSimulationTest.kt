package com.eliadca.talks.core.track

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * The speaker talks for a long while about something else entirely (an anecdote, thanking the
 * organisers, a joke, the traffic...) and then comes back to the script. While they are off topic
 * the marker must stay where they left the script; when they come back it must find them quickly.
 */
class OffTopicSimulationTest {

    private class OffTopicScore(
        val stretches: Int,
        val calm: Int,
        val events: Int,
        val anchored: Int,
        val worstForward: Int,
        val worstBack: Int,
        val recoveries: List<Int>,
        val unrecovered: Int,
        val readingAccuracy: Double,
    )

    private fun scoreOffTopic(index: ScriptIndex, run: SimRun, config: TrackerConfig = TrackerConfig()): OffTopicScore {
        val tracker = SpeechTracker(index, config)
        val maxForward = HashMap<Int, Int>()
        val maxBack = HashMap<Int, Int>()
        var events = 0
        var anchored = 0
        val returnedAt = HashMap<Int, Int>()
        val recovered = HashMap<Int, Int>()
        var graded = 0
        var within = 0
        for (e in run.events) {
            val st = tracker.onHypothesis(e.text, e.isFinal)
            val d = st.position - e.truth
            if (e.offTopicId >= 0) {
                events++
                if (d in -3..6) anchored++
                maxForward[e.offTopicId] = maxOf(maxForward[e.offTopicId] ?: 0, d)
                maxBack[e.offTopicId] = maxOf(maxBack[e.offTopicId] ?: 0, -d)
            } else if (e.afterOffTopic >= 0) {
                val id = e.afterOffTopic
                if (id !in returnedAt) returnedAt[id] = e.wordsSoFar
                if (id !in recovered && abs(d) <= 5) recovered[id] = e.wordsSoFar - returnedAt.getValue(id)
            }
            if (e.graded) {
                graded++
                if (abs(d) <= 5) within++
            }
        }
        val ids = maxForward.keys
        val calm = ids.count { (maxForward[it] ?: 0) <= 6 && (maxBack[it] ?: 0) <= 3 }
        return OffTopicScore(
            stretches = ids.size,
            calm = calm,
            events = events,
            anchored = anchored,
            worstForward = maxForward.values.maxOrNull() ?: 0,
            worstBack = maxBack.values.maxOrNull() ?: 0,
            recoveries = recovered.values.toList(),
            unrecovered = returnedAt.keys.count { it !in recovered },
            readingAccuracy = if (graded == 0) 1.0 else within.toDouble() / graded,
        )
    }

    private class Totals(val scores: List<OffTopicScore>) {
        val stretches = scores.sumOf { it.stretches }
        val calmShare = scores.sumOf { it.calm }.toDouble() / stretches
        val anchoredShare = scores.sumOf { it.anchored }.toDouble() / scores.sumOf { it.events }
        val rec = scores.flatMap { it.recoveries }.sorted()
        val unrecovered = scores.sumOf { it.unrecovered }
        val median = rec.getOrElse(rec.size / 2) { 0 }
        val p90 = rec.getOrElse(rec.size * 9 / 10) { 0 }
        val reading = scores.map { it.readingAccuracy }.average()
        val worstForward = scores.maxOf { it.worstForward }
        override fun toString() =
            "stretches=%d calm=%.3f anchored=%.3f worstFwd=%d worstBack=%d recovery median=%d p90=%d unrecovered=%d reading=%.4f".format(
                stretches, calmShare, anchoredShare, scores.maxOf { it.worstForward }, scores.maxOf { it.worstBack },
                median, p90, unrecovered, reading,
            )
    }

    private fun runMany(index: ScriptIndex, cfg: SimConfig, seeds: Int = 20, tracker: TrackerConfig = TrackerConfig()): Totals =
        Totals((1..seeds).map { seed -> scoreOffTopic(index, Simulator.generate(index, cfg, Random(seed * 104729L)), tracker) })

    private val offTopic = SimConfig(offTopicPerSentence = 0.12, offTopicCorpus = OFF_TOPIC)

    @Test fun longTalkAboutSomethingElseDoesNotMoveTheMarker() {
        val t = runMany(SampleScripts.practice, offTopic)
        println("off topic (practice)           $t")
        assertTrue("too few stretches: ${t.stretches}", t.stretches > 40)
        assertTrue("marker moved during off-topic talk: $t", t.calmShare >= 0.95)
        assertTrue("marker not anchored: $t", t.anchoredShare >= 0.97)
        assertTrue("marker wandered off: $t", t.worstForward <= 12)
        assertTrue("slow to find the speaker again: $t", t.median <= 8 && t.p90 <= 20)
        assertTrue("lost after coming back: $t", t.unrecovered * 20 <= t.stretches)
        assertTrue("reading got worse: $t", t.reading >= 0.99)
    }

    @Test fun offTopicTalkWithANoisyRecogniser() {
        val t = runMany(SampleScripts.practice, SimConfig(substitution = 0.12, drop = 0.06, offTopicPerSentence = 0.12, offTopicCorpus = OFF_TOPIC))
        println("off topic, noisy (practice)    $t")
        assertTrue("marker moved during off-topic talk: $t", t.calmShare >= 0.95)
        assertTrue("marker wandered off: $t", t.worstForward <= 12)
        assertTrue("slow to find the speaker again: $t", t.median <= 10 && t.p90 <= 24)
    }

    @Test fun offTopicTalkInAScriptFullOfRepeatedPhrases() {
        val t = runMany(SampleScripts.refrains, offTopic)
        println("off topic (refrains)           $t")
        assertTrue("marker moved during off-topic talk: $t", t.calmShare >= 0.95)
        assertTrue("marker wandered off: $t", t.worstForward <= 12)
        assertTrue("slow to find the speaker again: $t", t.median <= 10 && t.p90 <= 24)
    }

    @Test fun veryLongOffTopicStretches() {
        val t = runMany(SampleScripts.practice, SimConfig(offTopicPerSentence = 0.06, offTopicMin = 150, offTopicMax = 400, offTopicCorpus = OFF_TOPIC))
        println("very long off topic (practice) $t")
        assertTrue("marker moved during off-topic talk: $t", t.calmShare >= 0.97)
        assertTrue("marker wandered off: $t", t.worstForward <= 12)
        assertTrue("slow to find the speaker again: $t", t.median <= 10 && t.p90 <= 24)
    }

    companion object {
        /** Natural Spanish about other things, sharing many everyday words with the scripts. */
        val OFF_TOPIC: List<String> = """
            Antes de seguir quiero contarles algo que me pasó esta mañana en el aeropuerto. El vuelo salió con dos horas
            de retraso porque había niebla y nadie sabía nada. Al lado mío iba un señor con un perro pequeño que no dejaba
            de ladrar, y la azafata le pidió que lo calmara. El señor le contestó que el perro solo se calmaba con música
            clásica, y terminamos todos escuchando a Mozart desde su teléfono. Les juro que es verdad. Por cierto, ¿se
            escucha bien al fondo? Levanten la mano si no me escuchan. Ah, perfecto, gracias. Quiero agradecer también a
            los organizadores, a Carlos, a Lucía y a todo el equipo técnico, que llevan desde las seis de la mañana
            preparando este salón. Hace un rato estaba hablando con algunos de ustedes en el café y me preguntaban por el
            partido de anoche. Yo no soy muy futbolero, pero mi hijo sí, y me tuvo despierto hasta la una gritando cada
            gol. Ganamos tres a uno, así que hoy en mi casa todos están felices. También me comentaban que el tráfico para
            llegar aquí estaba imposible, con obras en la avenida principal y un semáforo roto en la esquina del mercado.
            Yo vine caminando desde el hotel y aproveché para comprar un pan dulce que estaba buenísimo. Si tienen hambre
            después, la panadería está a dos calles, al lado de la farmacia. Ahora que lo pienso, la última vez que estuve
            en esta ciudad llovió toda la semana y no pude ver nada. Hoy el clima está precioso, así que espero que al
            salir puedan dar un paseo por el parque. Una vez me perdí en este mismo barrio buscando una librería antigua y
            terminé en una boda de desconocidos que me invitaron a bailar. Bailé fatal, pero me regalaron un trozo de
            pastel. Bueno, mi esposa dice que siempre me pierdo en las historias, y tiene razón. Una cosa más: después de
            la charla habrá preguntas, así que vayan pensando qué quieren preguntar, aunque les advierto que de física
            cuántica no sé absolutamente nada. Y si alguien tiene el teléfono encendido, no pasa nada, el mío también
            sonó la semana pasada en plena conferencia y era mi madre preguntando si había comido. Las madres son así,
            da igual la edad que tengas. Bueno, ¿dónde estaba? Ah, sí, el micrófono hace un ruido raro cuando me muevo,
            así que voy a quedarme quieto aquí, aunque me cuesta porque soy de los que caminan mucho cuando hablan.
        """.trimIndent()
            .split(Regex("\\s+"))
            .map { it.trim { c -> !c.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
    }
}
