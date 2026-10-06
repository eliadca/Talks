package com.eliadca.talks.speech

import android.os.SystemClock
import com.eliadca.talks.core.track.CharSpan
import com.eliadca.talks.core.track.MarkUnit
import com.eliadca.talks.core.track.Marking
import com.eliadca.talks.core.track.ScriptIndex
import com.eliadca.talks.core.track.SpeechTracker
import com.eliadca.talks.core.track.TrackStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What a Talks run covered; used to learn the speaker's pace. */
class SessionSummary(
    val startedAtMs: Long,
    val activeMs: Long,
    val startPosition: Int,
    val furthestPosition: Int,
    val totalWords: Int,
    val engineName: String,
) {
    val wordsCovered: Int get() = (furthestPosition - startPosition).coerceAtLeast(0)
    val completion: Float get() = if (totalWords == 0) 0f else wordsCovered.toFloat() / totalWords
    val wpm: Int get() = if (activeMs < 15_000 || wordsCovered < 20) 0 else (wordsCovered * 60_000L / activeMs).toInt()
}

/**
 * One run of Talks mode: feeds what the recogniser hears to the tracker and publishes where the
 * speaker is, plus the clock, the listening state and any problem, for the screen to show.
 *
 * Create it, call [start] (on the main thread) and collect [state]. Moves made by hand with
 * [setPosition]/[nudge] keep working even when the recogniser has failed.
 */
class TalksSession(
    private val scope: CoroutineScope,
    private val index: ScriptIndex,
    private val engine: SpeechEngine,
    /** How what comes next is marked; can be changed during the run with [setMarking]. */
    marking: Marking = Marking(),
    /** Monotonic milliseconds; replaceable so that tests do not depend on the platform clock. */
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
) {
    data class State(
        /** Index of the next word the speaker should say. */
        val position: Int = 0,
        val status: TrackStatus = TrackStatus.WAITING,
        val confidence: Float = 1f,
        val finished: Boolean = false,
        val engineState: EngineState = EngineState.IDLE,
        /** The latest problem reported by the recogniser, if it still applies. */
        val error: EngineError? = null,
        /** Microphone loudness 0..1. */
        val level: Float = 0f,
        /** The last words the recogniser heard. */
        val heard: String = "",
        /** Whether the app is listening (false while paused or after a fatal error). */
        val listening: Boolean = false,
        val elapsedMs: Long = 0,
        /** Text offset before which everything has been said (the reader dims it). */
        val spokenEnd: Int = 0,
        /** Text offset whose line the reader keeps at the reading line: where the voice is. */
        val focus: Int = 0,
        /** What to say next, as stretches of text that leave out notes, skipped headings and line breaks. */
        val marks: List<CharSpan> = emptyList(),
        val progress: Float = 0f,
        /** Counts every manual move, so the screen can tell that the position jumped on purpose. */
        val manualMoves: Int = 0,
        /** True while the text advances by itself at [autoWpm] instead of following the voice. */
        val auto: Boolean = false,
        val autoWpm: Int = 130,
        /**
         * True while everything automatic is off: nothing listens or moves the text, and the
         * speaker scrolls by hand. The clock keeps running.
         */
        val manual: Boolean = false,
    ) {
        /** Where the marked text starts (the voice's place when nothing is marked). */
        val nextStart: Int get() = marks.firstOrNull()?.start ?: focus

        /** Where the marked text ends. */
        val nextEnd: Int get() = marks.lastOrNull()?.end ?: focus
    }

    /** Read on the tracker's dispatcher, written from the screen. */
    @Volatile
    private var marking: Marking = marking

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    /** The tracker is not thread-safe, so every use of it happens on this one-at-a-time dispatcher. */
    private val trackerDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val tracker = SpeechTracker(index)

    private var collectJob: Job? = null
    private var tickerJob: Job? = null
    private var autoJob: Job? = null

    private var startedAtWall = 0L
    private var startPosition = 0
    private var furthest = 0
    private var activeMs = 0L
    private var activeSince = 0L

    /** Starts listening from script word [startPosition]. Call on the main thread. */
    fun start(startPosition: Int = 0) {
        this.startPosition = startPosition.coerceIn(0, index.size)
        furthest = this.startPosition
        startedAtWall = System.currentTimeMillis()
        activeMs = 0
        activeSince = now()

        // Set the state before anything can report into it, so an early failure is not wiped out.
        mutableState.update { it.copy(listening = true, error = null) }
        collectJob = scope.launch(trackerDispatcher) {
            tracker.reset(this@TalksSession.startPosition)
            publish(manual = false)
            engine.events.collect { handle(it) }
        }
        tickerJob = scope.launch {
            while (isActive) {
                mutableState.update { it.copy(elapsedMs = currentActiveMs()) }
                delay(250)
            }
        }
        engine.start()
    }

    /**
     * Teleprompter fallback: the text advances on its own at [wpm] words per minute and the
     * recogniser is switched off. Handy when the microphone cannot be relied on.
     */
    fun startAuto(wpm: Int) {
        val rate = wpm.coerceIn(40, 300)
        autoJob?.cancel()
        if (mutableState.value.listening) engine.stop()
        if (activeSince == 0L) activeSince = now()
        mutableState.update { it.copy(auto = true, autoWpm = rate, listening = false, level = 0f, error = null) }
        autoJob = scope.launch(trackerDispatcher) {
            var carry = 0.0
            var last = now()
            while (isActive) {
                delay(100)
                val tick = now()
                carry += (tick - last) * mutableState.value.autoWpm / 60_000.0
                last = tick
                val whole = carry.toInt()
                if (whole > 0) {
                    carry -= whole
                    val pos = tracker.state.position
                    if (pos < index.size) {
                        tracker.setPosition((pos + whole).coerceAtMost(index.size))
                        publish(manual = false)
                    }
                }
            }
        }
    }

    fun setAutoSpeed(wpm: Int) {
        mutableState.update { it.copy(autoWpm = wpm.coerceIn(40, 300)) }
    }

    /** Leaves automatic advance and goes back to listening. */
    fun stopAuto() {
        autoJob?.cancel()
        autoJob = null
        mutableState.update { it.copy(auto = false, listening = true, error = null) }
        engine.start()
    }

    /**
     * Turns everything automatic off, for when something goes badly wrong on stage: the recogniser
     * and automatic advance stop and the speaker moves the text by hand. The clock keeps running,
     * since the speaker is still talking.
     */
    fun enterManual() {
        if (mutableState.value.manual) return
        autoJob?.cancel()
        autoJob = null
        if (mutableState.value.listening) engine.stop()
        if (activeSince == 0L) activeSince = now()
        mutableState.update { it.copy(manual = true, auto = false, listening = false, level = 0f, error = null) }
    }

    /** Back to following the voice, from script word [token]: where the speaker scrolled to. */
    fun leaveManual(token: Int) {
        if (!mutableState.value.manual) return
        mutableState.update { it.copy(manual = false, listening = true, error = null) }
        scope.launch(trackerDispatcher) {
            tracker.setPosition(token)
            publish(manual = true)
        }
        engine.start()
    }

    /** Stops listening without ending the run (the clock stops too). */
    fun pause() {
        if (mutableState.value.manual) {
            activeMs = currentActiveMs()
            activeSince = 0
            return
        }
        val wasAuto = mutableState.value.auto
        if (wasAuto) {
            autoJob?.cancel()
            autoJob = null
        }
        if (!wasAuto && !mutableState.value.listening) return
        activeMs = currentActiveMs()
        activeSince = 0
        if (!wasAuto) engine.stop() // in automatic mode the recogniser is already off
        mutableState.update { it.copy(auto = false, listening = false, level = 0f) }
    }

    fun resume() {
        if (mutableState.value.manual) {
            if (activeSince == 0L) activeSince = now()
            return
        }
        if (mutableState.value.listening) return
        activeSince = now()
        mutableState.update { it.copy(listening = true, error = null) }
        engine.start()
    }

    /** Retries after a fatal recogniser error. */
    fun retry() {
        engine.stop()
        activeSince = if (activeSince == 0L) now() else activeSince
        mutableState.update { it.copy(listening = true, error = null) }
        engine.start()
    }

    /** Puts the speaker at script word [token] (for instance where they long-pressed the text). */
    fun setPosition(token: Int) {
        scope.launch(trackerDispatcher) {
            tracker.setPosition(token)
            publish(manual = true)
        }
    }

    /** Changes how what comes next is marked; the reader updates at once. */
    fun setMarking(marking: Marking) {
        if (marking == this.marking) return
        this.marking = marking
        scope.launch(trackerDispatcher) { publish(manual = false) }
    }

    /** Moves by [phrases] marked blocks (sentences when marking sentences, phrases otherwise; negative goes back). */
    fun nudge(phrases: Int) {
        scope.launch(trackerDispatcher) {
            val unit = if (marking.unit == MarkUnit.SENTENCE) MarkUnit.SENTENCE else MarkUnit.PHRASE
            var p = tracker.state.position
            repeat(kotlin.math.abs(phrases)) {
                p = if (phrases > 0) index.nextBlockStart(unit, p) else index.previousBlockStart(unit, p)
            }
            tracker.setPosition(p)
            publish(manual = true)
        }
    }

    /** Ends the run and returns what it covered. Call on the main thread. */
    fun stop(): SessionSummary {
        val total = currentActiveMs()
        collectJob?.cancel()
        tickerJob?.cancel()
        autoJob?.cancel()
        engine.stop()
        mutableState.update { it.copy(listening = false, level = 0f) }
        return SessionSummary(startedAtWall, total, startPosition, furthest, index.size, engine.name)
    }

    /** Frees the recogniser. Call on the main thread, after [stop]. */
    fun release() {
        collectJob?.cancel()
        tickerJob?.cancel()
        autoJob?.cancel()
        engine.release()
    }

    // --- internals --------------------------------------------------------------------------------

    private fun currentActiveMs(): Long =
        if (activeSince == 0L) activeMs else activeMs + (now() - activeSince)

    private fun handle(event: SpeechEvent) {
        when (event) {
            is SpeechEvent.Partial -> {
                if (mutableState.value.manual) return
                tracker.onHypothesis(event.text, isFinal = false)
                publish(manual = false, heard = event.text)
            }
            is SpeechEvent.Final -> {
                if (mutableState.value.manual) return
                tracker.onHypothesis(event.text, isFinal = true)
                publish(manual = false, heard = event.text)
            }
            is SpeechEvent.Level -> if (!mutableState.value.manual) mutableState.update { it.copy(level = event.value) }
            is SpeechEvent.State -> mutableState.update {
                // A recovered engine clears a transient error.
                val clear = event.state == EngineState.LISTENING && it.error?.fatal == false
                it.copy(engineState = event.state, error = if (clear) null else it.error)
            }
            is SpeechEvent.Failure -> mutableState.update {
                it.copy(error = event.error, listening = if (event.error.fatal) false else it.listening)
            }
        }
    }

    private fun publish(manual: Boolean, heard: String? = null) {
        val t = tracker.state
        val pos = t.position
        if (pos > furthest) furthest = pos
        val m = index.mark(pos, marking)
        val marks = index.segments(m.from, m.until)
        mutableState.update {
            it.copy(
                position = pos,
                status = t.status,
                confidence = t.confidence,
                finished = t.finished,
                heard = heard ?: it.heard,
                spokenEnd = index.startChar(m.dimUntil),
                focus = index.startChar(m.focus),
                marks = marks,
                progress = index.progress(pos),
                manualMoves = if (manual) it.manualMoves + 1 else it.manualMoves,
            )
        }
    }
}
