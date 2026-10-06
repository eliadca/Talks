package com.eliadca.talks.speech

import com.eliadca.talks.core.sample.SampleContent
import com.eliadca.talks.core.track.MarkUnit
import com.eliadca.talks.core.track.Marking
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
        // Robolectric's platform clock does not advance by itself, so the session gets a real one.
        session = TalksSession(scope, index, engine, now = { System.nanoTime() / 1_000_000 })
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
        // The reading line follows the voice; the whole phrase being said is marked.
        assertEquals(index.startChar(st.position), st.focus)
        val phrase = index.blockStart(MarkUnit.PHRASE, st.position)
        assertEquals(index.startChar(phrase), st.nextStart)
        assertEquals(st.nextStart, st.spokenEnd)
        assertTrue(st.spokenEnd <= index.startChar(st.position))
        assertTrue(st.nextEnd > st.nextStart)
        assertTrue("next chunk is a phrase, not the rest of the speech", st.nextEnd - st.nextStart < 200)
        assertTrue(st.progress > 0f)
        assertTrue(st.listening)
    }

    @Test fun startingInTheMiddleBeginsThere() {
        val start = index.size / 2
        session.start(start)
        waitFor("start position published") { session.state.value.position == start }
        assertEquals(index.startChar(start), session.state.value.focus)
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

    @Test fun manualModeStopsEverythingAutomaticAndResumesFromWhereTheSpeakerScrolled() {
        session.start(0)
        engine.hear("buenos dias a todos gracias por estar aqui")
        waitFor("following") { session.state.value.position >= 8 }

        session.enterManual()
        val st = session.state.value
        assertTrue(st.manual)
        assertFalse("the recogniser is off", st.listening)
        assertEquals(1, engine.stopped)
        val before = st.position
        // Late results from the recogniser must not move anything.
        engine.hear("buenos dias a todos gracias por estar aqui y gracias por regalarme lo mas valioso", final = true)
        Thread.sleep(200)
        assertEquals(before, session.state.value.position)
        // The clock keeps running: the speaker is still talking.
        val t0 = session.state.value.elapsedMs
        waitFor("clock running in manual mode") { session.state.value.elapsedMs > t0 }

        session.leaveManual(120)
        waitFor("resynchronised where the speaker scrolled") { session.state.value.position == 120 }
        assertFalse(session.state.value.manual)
        assertTrue(session.state.value.listening)
        assertEquals(2, engine.started)
    }

    @Test fun reachingTheEndFinishesTheSpeech() {
        session.start(index.size - 6)
        waitFor("start") { session.state.value.position == index.size - 6 }
        val lastWords = index.tokens.takeLast(6).joinToString(" ") { it.norm }
        engine.hear(lastWords, final = true)
        waitFor("finished") { session.state.value.finished }
        assertEquals(1f, session.state.value.progress, 0.001f)
    }

    @Test fun automaticAdvanceMovesTheTextOnItsOwnAndStopsListening() {
        session.start(0)
        session.startAuto(300) // five words a second
        assertEquals("the recogniser is switched off", 1, engine.stopped)
        waitFor("automatic advance") { session.state.value.position >= 5 }
        val st = session.state.value
        assertTrue(st.auto)
        assertFalse(st.listening)
        assertEquals(300, st.autoWpm)
        assertEquals(index.startChar(st.position), st.focus)

        session.setAutoSpeed(1000)
        assertEquals("speed is capped", 300, session.state.value.autoWpm)

        session.stopAuto()
        assertFalse(session.state.value.auto)
        assertTrue(session.state.value.listening)
        assertEquals(2, engine.started)
        Thread.sleep(200) // let an in-flight tick settle
        val p = session.state.value.position
        Thread.sleep(500)
        assertEquals("no longer advancing by itself", p, session.state.value.position)
    }

    @Test fun pausingDuringAutomaticAdvanceStopsEverything() {
        session.start(0)
        session.startAuto(300)
        waitFor("some advance") { session.state.value.position >= 2 }
        session.pause()
        assertFalse(session.state.value.auto)
        assertFalse(session.state.value.listening)
        Thread.sleep(400) // let an in-flight tick and the clock settle
        val p = session.state.value.position
        val t = session.state.value.elapsedMs
        Thread.sleep(600)
        assertEquals(p, session.state.value.position)
        assertEquals("clock stopped", t, session.state.value.elapsedMs)
        session.resume()
        assertTrue(session.state.value.listening)
    }

    @Test fun automaticAdvanceStopsAtTheEnd() {
        session.start(index.size - 3)
        session.startAuto(300)
        waitFor("finished") { session.state.value.finished }
        assertEquals(index.size, session.state.value.position)
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

    @Test fun thePhraseStaysMarkedWhileItIsBeingSaid() {
        session.start(0)
        val words = index.tokens.take(3).map { it.norm }
        engine.hear(words.take(2).joinToString(" "))
        waitFor("second word") { session.state.value.position >= 2 }
        val first = session.state.value
        engine.hear(words.joinToString(" "))
        waitFor("third word") { session.state.value.position >= 3 }
        val third = session.state.value
        // Same phrase ("Buenos días a todos."): the marks and the dimming do not move, only the focus.
        assertEquals(first.marks, third.marks)
        assertEquals(first.spokenEnd, third.spokenEnd)
        assertTrue(third.focus > first.focus)
    }

    @Test fun marksNeverCoverNotesHeadingsOrLineBreaks() {
        session.start(0)
        for (unit in MarkUnit.entries) {
            session.setMarking(Marking(unit))
            for (p in listOf(0, 30, 37, 38, 100, 105, 107, 200, 300, index.size - 3)) {
                session.setPosition(p)
                waitFor("position $p with $unit") { session.state.value.position == p }
                Thread.sleep(20)
                for (m in session.state.value.marks) {
                    val shown = index.text.substring(m.start, m.end)
                    assertFalse("$unit at $p marks «$shown»", shown.contains('[') || shown.contains(']') || shown.contains('\n'))
                }
            }
        }
    }

    @Test fun changingTheMarkingRepublishesWithoutAManualMove() {
        session.start(0)
        // Inside "Gracias por estar aquí," of a longer sentence, so phrase and sentence differ.
        session.setPosition(5)
        waitFor("manual move") { session.state.value.position == 5 }
        val moves = session.state.value.manualMoves
        val phraseMarks = session.state.value.marks
        session.setMarking(Marking(MarkUnit.SENTENCE))
        waitFor("sentence marking") { session.state.value.marks != phraseMarks }
        assertEquals(moves, session.state.value.manualMoves)
        session.setMarking(Marking(MarkUnit.NONE))
        waitFor("no marking") { session.state.value.marks.isEmpty() }
        assertEquals(index.startChar(5), session.state.value.focus)
    }

    @Test fun markingAheadShowsTheNextPhraseWithoutDimmingWhatIsNotSaid() {
        session.start(0)
        val second = index.nextBlockStart(MarkUnit.PHRASE, 0)
        session.setPosition(second - 1) // about to say the last word of the first phrase
        waitFor("position") { session.state.value.position == second - 1 }
        session.setMarking(Marking(lead = 2))
        waitFor("lead applied") { session.state.value.nextStart == index.startChar(second) }
        val st = session.state.value
        assertEquals("the first phrase is not dimmed yet", index.startChar(0), st.spokenEnd)
    }
}
