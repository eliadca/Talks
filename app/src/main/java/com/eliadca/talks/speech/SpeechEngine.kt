package com.eliadca.talks.speech

import kotlinx.coroutines.flow.SharedFlow

enum class EngineState { IDLE, STARTING, LISTENING, RESTARTING, STOPPED }

enum class ErrorKind { PERMISSION, NO_SERVICE, LANGUAGE, NETWORK, AUDIO, MODEL, OTHER }

/** A problem with speech recognition. A [fatal] one stops listening; the others are retried. */
class EngineError(val kind: ErrorKind, val message: String, val fatal: Boolean)

sealed interface SpeechEvent {
    /** The recogniser's best guess so far for what is being said; it may still change. */
    data class Partial(val text: String) : SpeechEvent

    /** The recogniser's final answer for a stretch of speech. */
    data class Final(val text: String) : SpeechEvent

    /** Microphone loudness from 0 to 1, when the engine can tell. */
    data class Level(val value: Float) : SpeechEvent

    data class State(val state: EngineState) : SpeechEvent

    data class Failure(val error: EngineError) : SpeechEvent
}

/**
 * A speech recogniser that listens continuously and reports what it hears. Implementations must be
 * started, stopped and released from the main thread.
 */
interface SpeechEngine {
    val events: SharedFlow<SpeechEvent>

    /** A short name for messages and statistics. */
    val name: String

    fun start()

    /** Stops listening but keeps resources so that [start] is quick. */
    fun stop()

    fun release()
}
