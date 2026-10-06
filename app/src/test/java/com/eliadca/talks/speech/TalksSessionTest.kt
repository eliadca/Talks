package com.eliadca.talks.speech

import com.eliadca.talks.core.sample.SampleContent
import com.eliadca.talks.core.track.ScriptIndex
import com.eliadca.talks.core.track.TrackStatus
import com.eliadca.talks.core.doc.silentRanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TalksSessionTest {

    private class FakeEngine : SpeechEngine {
        val flow = MutableSharedFlow<SpeechEvent>(replay = 16, extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        override val events: SharedFlow<SpeechEvent> = flow
        override val name = "Fake"
        var started = 0
        var stopped = 0
        override fun start() { started++ }
        override fun stop() { stopped++ }
        override fun release() {}
        fun hear(text: String, final: Boolean = false) {
            flow.tryEmit(if (final) SpeechEvent.Final(text) else SpeechEvent.Partial(text))
        }
    }

    private lateinit var scope: CoroutineScope
    private lateinit var engine: FakeEngine
    private lateinit var index: ScriptIndex
    private lateinit var session: TalksSession

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        engine = FakeEngine()
        val doc = SampleContent.practice
        index = ScriptIndex.build(doc.text, doc.silentRanges(readHeadings = false))
        session = TalksSession(scope, index, engine, highlightWords = 9)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun waitFor(what: String, timeoutMs: Long = 5000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) fail("Timed out waiting for $what; state=${session.state.value}")
            Thread.sleep(10)
        }
    }

    @Test fun followsSpeechAndPublishesTextRanges() {
        session.start(0)
        assertEquals(1, engine.started)
        engine.hear("buenos dias a todos")
        engine.hear("buenos dias a todos gracias por estar aqui")
        waitFor("position to reach the 8th word") { session.state.value.position >= 8 }

        val st = session.state.value
        assertEquals(TrackStatus.FOLLOWING, st.status)
        assertEquals(index.startChar(st.position), st.nextStart)
        assertEquals(st.nextStart, st.spokenEnd)
        assertTrue(st.nextEnd > st.nextStart)
        assertTrue("next chunk is a phrase, not the rest of the speech", st.nextEnd - st.nextStart < 200)
        assertTrue(st.progress > 0f)
        assertTrue(st.listening)
    }

    @Test fun startingInTheMiddleBeginsThere() {
        val start = index.size / 2
        session.start(start)
        waitFor("start position published") { session.state.value.position == start }
        assertEquals(index.startChar(start), session.state.value.nextStart)
    }

    @Test fun manualPositionAndPhraseNudges() {
        session.start(0)
        session.setPosition(60)
        waitFor("manual move") { session.state.value.position == 60 }
        assertEquals(1, session.state.value.manualMoves)

        session.nudge(1)
        waitFor("next phrase") { session.state.value.position > 60 }
        val forward = session.state.value.position
        assertEquals(index.nextPhraseStart(60), forward)

        session.nudge(-1)
        waitFor("previous phrase") { session.state.value.position < forward }
        assertEquals(index.previousPhraseStart(forward), session.state.value.position)
    }

    @Test fun wordsHeardBeforeAManualMoveAreNotReplayedAtTheNewPlace() {
        session.start(0)
        engine.hear("buenos dias a todos gracias")
        waitFor("some progress") { session.state.value.position >= 4 }
        session.setPosition(100)
        waitFor("manual move") { session.state.value.position == 100 }
        // The recogniser keeps growing the same utterance.
        engine.hear("buenos dias a todos gracias por")
        Thread.sleep(200)
        val p = session.state.value.position
        assertTrue("position $p should stay near 100", p in 100..104)
    }

    @Test fun pauseAndResumeControlTheEngineAndTheClock() {
        session.start(0)
        waitFor("clock running") { session.state.value.elapsedMs > 0 }
        session.pause()
        assertEquals(1, engine.stopped)
        assertFalse(session.state.value.listening)
        Thread.sleep(300)
        val frozen = session.state.value.elapsedMs
        Thread.sleep(600)
        assertEquals("the clock must not run while paused", frozen, session.state.value.elapsedMs)

        session.resume()
        assertEquals(2, engine.started)
        assertTrue(session.state.value.listening)
        waitFor("clock running again") { session.state.value.elapsedMs > frozen }
    }

    @Test fun aFatalErrorStopsListeningAndRetryRestartsIt() {
        session.start(0)
        engine.flow.tryEmit(SpeechEvent.Failure(EngineError(ErrorKind.NO_SERVICE, "sin servicio", fatal = true)))
        waitFor("error shown") { session.state.value.error != null }
        assertFalse(session.state.value.listening)
        assertTrue(session.state.value.error!!.fatal)

        session.retry()
        assertTrue(session.state.value.listening)
        assertEquals(null, session.state.value.error)
        assertEquals(2, engine.started)
    }

    @Test fun anErrorEmittedWhileStartingIsNotLost() {
        // The engine fails inside start(), before the session has subscribed.
        engine.flow.tryEmit(SpeechEvent.Failure(EngineError(ErrorKind.NO_SERVICE, "no hay servicio", fatal = true)))
        session.start(0)
        waitFor("early error delivered") { session.state.value.error != null }
        assertEquals("no hay servicio", session.state.value.error?.message)
    }

    @Test fun aTransientErrorClearsWhenListeningResumes() {
        session.start(0)
        engine.flow.tryEmit(SpeechEvent.Failure(EngineError(ErrorKind.NETWORK, "red", fatal = false)))
        waitFor("transient error") { session.state.value.error != null }
        assertTrue("still listening after a transient error", session.state.value.listening)
        engine.flow.tryEmit(SpeechEvent.State(EngineState.LISTENING))
        waitFor("error cleared") { session.state.value.error == null }
    }

    @Test fun reachingTheEndFinishesTheSpeech() {
        session.start(index.size - 6)
        waitFor("start") { session.state.value.position == index.size - 6 }
        val lastWords = index.tokens.takeLast(6).joinToString(" ") { it.norm }
        engine.hear(lastWords, final = true)
        waitFor("finished") { session.state.value.finished }
        assertEquals(1f, session.state.value.progress, 0.001f)
    }

    @Test fun stopReturnsASummaryOfWhatWasCovered() {
        session.start(0)
        engine.hear("buenos dias a todos gracias por estar aqui y gracias por regalarme lo mas valioso que tienen su tiempo")
        waitFor("progress") { session.state.value.position >= 15 }
        val summary = session.stop()
        assertEquals(0, summary.startPosition)
        assertTrue(summary.furthestPosition >= 15)
        assertEquals(index.size, summary.totalWords)
        assertTrue(summary.completion > 0f)
        assertNotNull(summary.engineName)
        assertEquals(1, engine.stopped)
        assertFalse(session.state.value.listening)
    }
}
