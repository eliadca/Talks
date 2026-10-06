package com.eliadca.talks.speech

import android.os.SystemClock
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
    private val highlightWords: Int,
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
        /** Text offsets for the reader: everything before [spokenEnd] is done, [nextStart, nextEnd) is what to say next. */
        val spokenEnd: Int = 0,
        val nextStart: Int = 0,
        val nextEnd: Int = 0,
        val progress: Float = 0f,
        /** Counts every manual move, so the screen can tell that the position jumped on purpose. */
        val manualMoves: Int = 0,
    )

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    /** The tracker is not thread-safe, so every use of it happens on this one-at-a-time dispatcher. */
    private val trackerDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val tracker = SpeechTracker(index)

    private var collectJob: Job? = null
    private var tickerJob: Job? = null

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
        activeSince = SystemClock.elapsedRealtime()

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
        mutableState.update { it.copy(listening = true, error = null) }
        engine.start()
    }

    /** Stops listening without ending the run (the clock stops too). */
    fun pause() {
        if (!mutableState.value.listening) return
        activeMs = currentActiveMs()
        activeSince = 0
        engine.stop()
        mutableState.update { it.copy(listening = false, level = 0f) }
    }

    fun resume() {
        if (mutableState.value.listening) return
        activeSince = SystemClock.elapsedRealtime()
        mutableState.update { it.copy(listening = true, error = null) }
        engine.start()
    }

    /** Retries after a fatal recogniser error. */
    fun retry() {
        engine.stop()
        activeSince = if (activeSince == 0L) SystemClock.elapsedRealtime() else activeSince
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

    /** Moves by [phrases] phrases (negative goes back). */
    fun nudge(phrases: Int) {
        scope.launch(trackerDispatcher) {
            var p = tracker.state.position
            repeat(kotlin.math.abs(phrases)) {
                p = if (phrases > 0) index.nextPhraseStart(p) else index.previousPhraseStart(p)
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
        engine.stop()
        mutableState.update { it.copy(listening = false, level = 0f) }
        return SessionSummary(startedAtWall, total, startPosition, furthest, index.size, engine.name)
    }

    /** Frees the recogniser. Call on the main thread, after [stop]. */
    fun release() {
        collectJob?.cancel()
        tickerJob?.cancel()
        engine.release()
    }

    // --- internals --------------------------------------------------------------------------------

    private fun currentActiveMs(): Long =
        if (activeSince == 0L) activeMs else activeMs + (SystemClock.elapsedRealtime() - activeSince)

    private fun handle(event: SpeechEvent) {
        when (event) {
            is SpeechEvent.Partial -> {
                tracker.onHypothesis(event.text, isFinal = false)
                publish(manual = false, heard = event.text)
            }
            is SpeechEvent.Final -> {
                tracker.onHypothesis(event.text, isFinal = true)
                publish(manual = false, heard = event.text)
            }
            is SpeechEvent.Level -> mutableState.update { it.copy(level = event.value) }
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
        val nextStart = index.startChar(pos)
        val chunkEnd = index.chunkEnd(pos, MIN_HIGHLIGHT_WORDS, highlightWords)
        val nextEnd = if (pos >= index.size) index.text.length else index.endChar(chunkEnd - 1)
        mutableState.update {
            it.copy(
                position = pos,
                status = t.status,
                confidence = t.confidence,
                finished = t.finished,
                heard = heard ?: it.heard,
                spokenEnd = nextStart,
                nextStart = nextStart,
                nextEnd = nextEnd,
                progress = index.progress(pos),
                manualMoves = if (manual) it.manualMoves + 1 else it.manualMoves,
            )
        }
    }

    private companion object {
        const val MIN_HIGHLIGHT_WORDS = 3
    }
}
