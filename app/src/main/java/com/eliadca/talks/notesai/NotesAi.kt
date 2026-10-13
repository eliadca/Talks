package com.eliadca.talks.notesai

import android.content.Context
import android.os.StatFs
import com.eliadca.talks.core.notes.CorrectionReview
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface AiDownload {
    data class Progress(val fraction: Float) : AiDownload
    data object Verifying : AiDownload
    data class Failed(val message: String) : AiDownload
}

/** Optional, verified local model. Text and audio never leave the tablet through this service. */
class NotesAi(private val context: Context, private val scope: CoroutineScope) {
    private val root get() = File(context.filesDir, "notes-ai").apply { mkdirs() }
    private val model get() = File(root, "qwen-1.5b-q4.gguf")
    private val ready = MutableStateFlow(model.length() == MODEL_BYTES && File(root,"verified").readTextOrEmpty() == MODEL_SHA)
    val installed = ready.asStateFlow()
    private val downloadState = MutableStateFlow<AiDownload?>(null)
    val download = downloadState.asStateFlow()
    private var downloadJob: Job? = null
    private val inference = Mutex()

    fun install() {
        if (downloadJob?.isCompleted == false || ready.value) return
        downloadJob = scope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val part = File(root, "model.gguf.part")
                if (part.length() > MODEL_BYTES) part.delete()
                val offset = part.length()
                check(StatFs(root.path).availableBytes > MODEL_BYTES - offset + 32L*1024*1024) { "Necesitas unos 1,2 GB libres para instalar la IA." }
                if (offset < MODEL_BYTES) {
                    connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20_000; readTimeout = 45_000
                        if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
                    }
                    val code = connection.responseCode
                    check(code == 200 || code == 206) { "No se pudo descargar la IA (HTTP $code)." }
                    val resume = code == 206 && offset > 0
                    if (resume) check(connection.getHeaderField("Content-Range")?.startsWith("bytes $offset-") == true) { "El servidor no pudo reanudar la descarga." }
                    var done = if (resume) offset else 0L
                    downloadState.value = AiDownload.Progress(done.toFloat()/MODEL_BYTES)
                    connection.inputStream.use { input -> java.io.FileOutputStream(part,resume).use { out ->
                        val bytes=ByteArray(128*1024); var last=done
                        while (true) {
                            currentCoroutineContext().ensureActive(); val n=input.read(bytes); if(n<0) break
                            done+=n; check(done<=MODEL_BYTES) { "El archivo recibido no coincide con el modelo." }; out.write(bytes,0,n)
                            if(done-last>512*1024) { last=done; downloadState.value=AiDownload.Progress(done.toFloat()/MODEL_BYTES) }
                        }
                    } }
                }
                downloadState.value = AiDownload.Verifying
                check(part.length()==MODEL_BYTES) { "La descarga está incompleta. Toca Reintentar para continuar." }
                val digest=MessageDigest.getInstance("SHA-256")
                part.inputStream().use { input -> val bytes=ByteArray(128*1024); while(true) { currentCoroutineContext().ensureActive(); val n=input.read(bytes); if(n<0)break; digest.update(bytes,0,n) } }
                val hash=digest.digest().joinToString("") { "%02x".format(it) }
                if(hash!=MODEL_SHA) { part.delete(); error("La verificación del modelo falló. Vuelve a descargarlo.") }
                check(part.renameTo(model)) { "No se pudo guardar el modelo de IA." }
                File(root,"verified").writeText(MODEL_SHA)
                ready.value=true; downloadState.value=null
            } catch (e: CancellationException) { downloadState.value=null; throw e }
            catch (e: Exception) { downloadState.value=AiDownload.Failed(e.message ?: "No se pudo instalar la IA.") }
            finally { connection?.disconnect() }
        }
    }
    fun cancelDownload() { downloadJob?.cancel() }

    @OptIn(InternalCoroutinesApi::class)
    suspend fun correct(text: String, onProgress: (Int,Int)->Unit = {_,_->}): String = inference.withLock {
        check(ready.value) { "Primero instala la IA local." }
        withContext(Dispatchers.Default) {
            // Short, bounded prompts also keep a very small model focused on nearby mistakes.
            val chunks=split(text,850)
            chunks.mapIndexed { i, chunk ->
                ensureActive(); onProgress(i+1,chunks.size)
                val request=NativeNotesAi.newRequest()
                val lifecycle=Any(); var released=false
                val cancellation=currentCoroutineContext().job.invokeOnCompletion(onCancelling=true, invokeImmediately=true) { if(it!=null) synchronized(lifecycle) { if(!released) NativeNotesAi.cancel(request) } }
                try {
                    val result=NativeNotesAi.generate(model.path,prompt(chunk).toByteArray(Charsets.UTF_8),request)
                    ensureActive(); check(result!=null) { "Corrección cancelada." }
                    CorrectionReview.clean(result.toString(Charsets.UTF_8))
                } finally { cancellation.dispose(); synchronized(lifecycle) { released=true; NativeNotesAi.freeRequest(request) } }
            }.joinToString(" ")
        }
    }
    private fun split(text: String, limit: Int): List<String> {
        val parts=mutableListOf<String>(); var rest=text.trim()
        while(rest.length>limit) { val boundary=rest.lastIndexOf(' ',limit).takeIf { it>limit/2 } ?: limit
            parts+=rest.take(boundary); rest=rest.drop(boundary).trimStart() }
        if(rest.isNotBlank())parts+=rest; return parts
    }
    private fun prompt(text: String) = "<|im_start|>system\nCorrige las faltas de ortografía y añade puntuación al texto en español. No elimines palabras ni añadas ideas. Conserva los nombres y todos los números. Devuelve solamente el texto corregido.<|im_end|>\n<|im_start|>user\noy la esperansa nos alluda cuando tenemos problemas<|im_end|>\n<|im_start|>assistant\nHoy la esperanza nos ayuda cuando tenemos problemas.<|im_end|>\n<|im_start|>user\n$text<|im_end|>\n<|im_start|>assistant\n"
    private fun File.readTextOrEmpty() = runCatching { readText() }.getOrDefault("")
    companion object {
        const val MODEL_BYTES=1117320736L
        const val MODEL_SHA="6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
        const val MODEL_URL="https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true"
    }
}
