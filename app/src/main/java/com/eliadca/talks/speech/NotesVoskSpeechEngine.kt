package com.eliadca.talks.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.*

/** Dedicated Notes capture: microphone meter, word confidence and alternatives, at Vosk's 16 kHz. */
class NotesVoskSpeechEngine(@Suppress("unused") context: Context, private val directory: File) : SpeechEngine {
    override val name = "Vosk · Notas"
    private val stream=MutableSharedFlow<SpeechEvent>(replay=16,extraBufferCapacity=256,onBufferOverflow=BufferOverflow.DROP_OLDEST)
    override val events=stream.asSharedFlow()
    @Volatile private var running=false
    @Volatile private var recorder: AudioRecord?=null
    private var worker: Thread?=null
    override fun start() {
        if(worker?.isAlive==true)return
        running=true; emit(SpeechEvent.State(EngineState.STARTING))
        worker=thread(name="notes-vosk",isDaemon=true) {
            var model: Model?=null; var recognizer: Recognizer?=null; var audio: AudioRecord?=null
            try {
                check(File(directory,"am/final.mdl").exists()) { "Instala el modelo de Vosk en Ajustes de voz de Notas." }
                model=Model(directory.path)
                if(!running)return@thread
                recognizer=Recognizer(model,16000f).apply { setWords(true); setMaxAlternatives(3) }
                val minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
                check(minimum>0) { "El micrófono no admite el formato de reconocimiento." }
                audio=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(minimum*2,8192))
                check(audio.state==AudioRecord.STATE_INITIALIZED) { "No se pudo abrir el micrófono." }
                recorder=audio
                if(!running)return@thread
                audio.startRecording(); emit(SpeechEvent.State(EngineState.LISTENING))
                val buffer=ShortArray(1600); var previousPartial=""
                while(running) {
                    val n=audio.read(buffer,0,buffer.size)
                    if(!running)break
                    check(n>0) { "No se pudo leer el micrófono ($n)." }
                    var energy=0.0; for(i in 0 until n)energy+=buffer[i].toDouble()*buffer[i]
                    val rms=sqrt(energy/n)/32768.0
                    val db=20*log10(max(rms,1e-7)); emit(SpeechEvent.Level(((db+60)/50).toFloat().coerceIn(0f,1f)))
                    if(recognizer.acceptWaveForm(buffer,n)) { emitFinal(recognizer.result); previousPartial="" }
                    else { val partial=JSONObject(recognizer.partialResult).optString("partial")
                        if(partial!=previousPartial) { previousPartial=partial; emit(SpeechEvent.Partial(partial)) } }
                }
            } catch(e: Exception) {
                if(running)emit(SpeechEvent.Failure(EngineError(ErrorKind.AUDIO,e.message ?: "No se pudo iniciar Vosk.",true)))
            } finally {
                running=false; recorder=null
                try { audio?.stop() } catch(_:Exception){}; audio?.release()
                // Native resources are closed by their own worker, never while recognition is in flight.
                recognizer?.close(); model?.close(); emit(SpeechEvent.State(EngineState.STOPPED))
            }
        }
    }
    private fun emitFinal(raw: String) {
        val json=JSONObject(raw); val candidates=json.optJSONArray("alternatives")
        val best=candidates?.optJSONObject(0) ?: json
        val text=best.optString("text").trim(); if(text.isBlank())return
        val words=best.optJSONArray("result")
        val confidences=if(words!=null)(0 until words.length()).mapNotNull { i -> words.optJSONObject(i)?.optDouble("conf",Double.NaN)?.takeIf { it.isFinite() } } else emptyList()
        val confidence=confidences.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val alternatives=if(candidates!=null)(1 until candidates.length()).map { candidates.getJSONObject(it).optString("text") }.filter { it.isNotBlank() } else emptyList()
        emit(SpeechEvent.Final(text,confidence,alternatives))
    }
    private fun emit(event: SpeechEvent) { stream.tryEmit(event) }
    override fun stop() { running=false; try { recorder?.stop() } catch(_:Exception){} }
    override fun release() = stop()
}
