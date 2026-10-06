package com.eliadca.talks.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import kotlin.concurrent.thread

/**
 * Fully offline, continuous recognition with Vosk. It never stops between phrases and plays no
 * sounds, which makes it the most dependable choice on a stage without a good connection.
 */
class VoskSpeechEngine(
    @Suppress("unused") private val context: Context,
    private val modelDir: File,
) : SpeechEngine {

    override val name: String = "Vosk"

    private val main = Handler(Looper.getMainLooper())
    private val mutableEvents = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<SpeechEvent> = mutableEvents.asSharedFlow()

    private var model: Model? = null
    private var service: SpeechService? = null

    @Volatile private var running = false
    @Volatile private var generation = 0

    override fun start() {
        if (running) return
        running = true
        val gen = ++generation
        emit(SpeechEvent.State(EngineState.STARTING))
        thread(name = "vosk-start", isDaemon = true) {
            try {
                if (!File(modelDir, "am/final.mdl").exists()) {
                    fail("Falta el modelo sin conexión. Descárgalo en Ajustes > Reconocimiento de voz.", ErrorKind.MODEL)
                    return@thread
                }
                val m = model ?: Model(modelDir.absolutePath).also { model = it }
                val recognizer = Recognizer(m, SAMPLE_RATE)
                val svc = SpeechService(recognizer, SAMPLE_RATE)
                main.post {
                    if (!running || gen != generation) {
                        svc.shutdown()
                        return@post
                    }
                    service = svc
                    if (svc.startListening(listener)) {
                        emit(SpeechEvent.State(EngineState.LISTENING))
                    } else {
                        fail("No se pudo abrir el micrófono.", ErrorKind.AUDIO)
                    }
                }
            } catch (e: Throwable) {
                fail("No se pudo iniciar el reconocimiento sin conexión: ${e.message ?: e.javaClass.simpleName}", ErrorKind.MODEL)
            }
        }
    }

    override fun stop() {
        running = false
        generation++
        service?.let {
            try {
                it.stop()
                it.shutdown()
            } catch (_: Exception) {
            }
        }
        service = null
        emit(SpeechEvent.State(EngineState.STOPPED))
    }

    override fun release() {
        stop()
        try {
            model?.close()
        } catch (_: Exception) {
        }
        model = null
    }

    private fun emit(e: SpeechEvent) {
        mutableEvents.tryEmit(e)
    }

    private fun fail(message: String, kind: ErrorKind) {
        running = false
        emit(SpeechEvent.Failure(EngineError(kind, message, fatal = true)))
        emit(SpeechEvent.State(EngineState.STOPPED))
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            val text = parse(hypothesis, "partial") ?: return
            emit(SpeechEvent.Partial(text))
        }

        override fun onResult(hypothesis: String?) {
            val text = parse(hypothesis, "text") ?: return
            emit(SpeechEvent.Final(text))
        }

        override fun onFinalResult(hypothesis: String?) {
            val text = parse(hypothesis, "text") ?: return
            emit(SpeechEvent.Final(text))
        }

        override fun onError(exception: Exception?) {
            emit(SpeechEvent.Failure(EngineError(ErrorKind.AUDIO, "Error del micrófono: ${exception?.message ?: "desconocido"}", fatal = false)))
        }

        override fun onTimeout() {}
    }

    private fun parse(json: String?, key: String): String? {
        if (json.isNullOrBlank()) return null
        return try {
            JSONObject(json).optString(key, "").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16000.0f
    }
}
