package com.eliadca.talks.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.min

/**
 * Continuous recognition on top of Android's [SpeechRecognizer].
 *
 * The platform recogniser stops after a pause or a few seconds of speech, so this engine restarts it
 * the moment it finishes, recreates it when it misbehaves, backs off after repeated failures and
 * watches for a recogniser that has gone silent without reporting anything.
 */
class AndroidSpeechEngine(
    private val context: Context,
    private val config: Config,
) : SpeechEngine {

    data class Config(
        val language: String,
        val preferOffline: Boolean,
        val forceGoogleService: Boolean,
        val muteSounds: Boolean,
    )

    override val name: String = "Android"

    private val main = Handler(Looper.getMainLooper())
    // A little replay so that events emitted while starting (such as an immediate failure) reach a
    // collector that subscribes a moment later.
    private val mutableEvents = MutableSharedFlow<SpeechEvent>(replay = 16, extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<SpeechEvent> = mutableEvents.asSharedFlow()

    private val muter = SoundMuter(context)
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var recreateNext = false
    private var failures = 0
    private var lastActivity = 0L
    private var lastLevelAt = 0L

    private val restartRunnable = Runnable { listenAgain() }
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            if (SystemClock.elapsedRealtime() - lastActivity > WATCHDOG_MS) {
                // The recogniser has not said anything for far too long: assume it is stuck.
                recreateNext = true
                scheduleRestart(0)
            }
            main.postDelayed(this, WATCHDOG_MS / 4)
        }
    }

    override fun start() {
        if (running) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            emit(
                SpeechEvent.Failure(
                    EngineError(
                        ErrorKind.NO_SERVICE,
                        "Este dispositivo no tiene un servicio de reconocimiento de voz. Instala o activa «Servicios de voz de Google» o usa el modo sin conexión (Vosk).",
                        fatal = true,
                    ),
                ),
            )
            return
        }
        running = true
        failures = 0
        recreateNext = true
        lastActivity = SystemClock.elapsedRealtime()
        if (config.muteSounds) muter.mute()
        emit(SpeechEvent.State(EngineState.STARTING))
        main.removeCallbacks(watchdogRunnable)
        main.postDelayed(watchdogRunnable, WATCHDOG_MS / 4)
        listenAgain()
    }

    override fun stop() {
        running = false
        main.removeCallbacks(restartRunnable)
        main.removeCallbacks(watchdogRunnable)
        destroyRecognizer()
        muter.unmute()
        emit(SpeechEvent.State(EngineState.STOPPED))
    }

    override fun release() {
        stop()
    }

    // --- internals --------------------------------------------------------------------------------

    private fun emit(e: SpeechEvent) {
        mutableEvents.tryEmit(e)
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!running) return
        main.removeCallbacks(restartRunnable)
        main.postDelayed(restartRunnable, delayMs)
    }

    private fun listenAgain() {
        if (!running) return
        try {
            if (recognizer == null || recreateNext) {
                destroyRecognizer()
                recognizer = createRecognizer().also { it.setRecognitionListener(listener) }
                recreateNext = false
            } else {
                recognizer?.cancel()
            }
            lastActivity = SystemClock.elapsedRealtime()
            recognizer?.startListening(buildIntent())
        } catch (e: Exception) {
            failures++
            recreateNext = true
            if (failures > MAX_FAILURES) {
                fatal(ErrorKind.NO_SERVICE, "No se pudo iniciar el reconocimiento de voz (${e.javaClass.simpleName}).")
            } else {
                scheduleRestart(backoff())
            }
        }
    }

    private fun createRecognizer(): SpeechRecognizer {
        if (Build.VERSION.SDK_INT >= 33 && config.preferOffline && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }
        if (config.forceGoogleService) {
            val google = ComponentName(GOOGLE_PACKAGE, GOOGLE_SERVICE)
            val installed = try {
                context.packageManager.getServiceInfo(google, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
            if (installed) return SpeechRecognizer.createSpeechRecognizer(context, google)
        }
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.language)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, config.language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (config.preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        // Ask the service to wait longer through pauses; services that ignore this just end earlier.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
    }

    private fun destroyRecognizer() {
        recognizer?.let {
            try {
                it.setRecognitionListener(null)
                it.cancel()
                it.destroy()
            } catch (_: Exception) {
            }
        }
        recognizer = null
    }

    private fun backoff(): Long = min(2500L, 250L * failures.coerceAtLeast(1))

    private fun fatal(kind: ErrorKind, message: String) {
        running = false
        main.removeCallbacks(restartRunnable)
        main.removeCallbacks(watchdogRunnable)
        destroyRecognizer()
        muter.unmute()
        emit(SpeechEvent.Failure(EngineError(kind, message, fatal = true)))
        emit(SpeechEvent.State(EngineState.STOPPED))
    }

    private fun bestResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            lastActivity = SystemClock.elapsedRealtime()
            emit(SpeechEvent.State(EngineState.LISTENING))
        }

        override fun onBeginningOfSpeech() {
            lastActivity = SystemClock.elapsedRealtime()
        }

        override fun onRmsChanged(rmsdB: Float) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastLevelAt < 80) return
            lastLevelAt = now
            emit(SpeechEvent.Level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)))
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            lastActivity = SystemClock.elapsedRealtime()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = bestResult(partialResults) ?: return
            lastActivity = SystemClock.elapsedRealtime()
            failures = 0
            emit(SpeechEvent.Partial(text))
        }

        override fun onResults(results: Bundle?) {
            lastActivity = SystemClock.elapsedRealtime()
            failures = 0
            bestResult(results)?.let { emit(SpeechEvent.Final(it)) }
            emit(SpeechEvent.State(EngineState.RESTARTING))
            scheduleRestart(RESTART_DELAY_MS)
        }

        override fun onError(error: Int) {
            lastActivity = SystemClock.elapsedRealtime()
            when (error) {
                // Silence or unintelligible speech: perfectly normal, just listen again.
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    emit(SpeechEvent.State(EngineState.RESTARTING))
                    scheduleRestart(RESTART_DELAY_MS)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    fatal(ErrorKind.PERMISSION, "Falta el permiso del micrófono. Concédelo en los ajustes de la app.")
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    fatal(
                        ErrorKind.LANGUAGE,
                        if (config.preferOffline) {
                            "El idioma ${config.language} no está disponible sin conexión. Descárgalo en Ajustes del sistema > Idioma > Voz, o desactiva «Preferir reconocimiento sin conexión»."
                        } else {
                            "El servicio de voz no admite el idioma ${config.language}."
                        },
                    )
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                    recoverable(ErrorKind.OTHER, "El reconocedor está ocupado; reintentando…", recreate = true)
                }
                SpeechRecognizer.ERROR_AUDIO ->
                    recoverable(ErrorKind.AUDIO, "No se puede acceder al micrófono; reintentando…", recreate = true)
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                    recoverable(ErrorKind.NETWORK, "Sin conexión con el servicio de voz; reintentando…", recreate = false)
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> {
                    failures += 2
                    recoverable(ErrorKind.OTHER, "Demasiadas peticiones al servicio de voz; esperando…", recreate = true)
                }
                else -> recoverable(ErrorKind.OTHER, "Error del reconocedor ($error); reintentando…", recreate = true)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun recoverable(kind: ErrorKind, message: String, recreate: Boolean) {
        failures++
        if (failures > MAX_FAILURES) {
            fatal(kind, "$message Se agotaron los reintentos.")
            return
        }
        emit(SpeechEvent.Failure(EngineError(kind, message, fatal = false)))
        emit(SpeechEvent.State(EngineState.RESTARTING))
        if (recreate) recreateNext = true
        scheduleRestart(backoff())
    }

    private companion object {
        const val RESTART_DELAY_MS = 40L
        const val MAX_FAILURES = 12
        const val WATCHDOG_MS = 28_000L
        const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
        const val GOOGLE_SERVICE = "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
    }
}
