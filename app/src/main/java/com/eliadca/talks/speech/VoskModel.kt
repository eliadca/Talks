package com.eliadca.talks.speech

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

sealed interface ModelDownload {
    /** [fraction] is 0..1, or negative when the size is unknown. */
    data class Progress(val fraction: Float) : ModelDownload
    data object Installing : ModelDownload
    data object Done : ModelDownload
    data class Failed(val message: String) : ModelDownload
}

/** Keeps the offline Spanish model for the Vosk engine: download, import from a file, remove. */
class VoskModelManager(private val context: Context) {

    val modelDir: File get() = File(context.filesDir, "vosk/model-es")

    fun isInstalled(): Boolean = File(modelDir, "am/final.mdl").exists()

    fun sizeOnDiskMb(): Long = if (isInstalled()) modelDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / (1024 * 1024) else 0

    fun remove() {
        modelDir.deleteRecursively()
    }

    /** Downloads the small Spanish model (about 40 MB) and installs it. */
    fun download(): Flow<ModelDownload> = flow {
        val root = File(context.filesDir, "vosk").apply { mkdirs() }
        val part = File(root, "model.zip.part")
        try {
            emit(ModelDownload.Progress(0f))
            val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) {
                emit(ModelDownload.Failed("El servidor respondió ${conn.responseCode}."))
                return@flow
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(part).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastEmit = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (done - lastEmit > 256 * 1024) {
                            lastEmit = done
                            emit(ModelDownload.Progress(if (total > 0) done.toFloat() / total else -1f))
                        }
                    }
                }
            }
            emit(ModelDownload.Installing)
            part.inputStream().use { install(it) }
            emit(ModelDownload.Done)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(ModelDownload.Failed(e.message ?: e.javaClass.simpleName))
        } finally {
            part.delete()
        }
    }.flowOn(Dispatchers.IO)

    /** Installs a model from a .zip the user picked (for use without internet access). */
    fun importZip(uri: Uri): Flow<ModelDownload> = flow {
        try {
            emit(ModelDownload.Installing)
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                emit(ModelDownload.Failed("No se pudo abrir el archivo."))
                return@flow
            }
            input.use { install(it) }
            emit(ModelDownload.Done)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(ModelDownload.Failed(e.message ?: e.javaClass.simpleName))
        }
    }.flowOn(Dispatchers.IO)

    /** Unzips a model archive, dropping the single top-level folder most archives have. */
    private fun install(zip: InputStream) {
        val root = File(context.filesDir, "vosk").apply { mkdirs() }
        val staging = File(root, "staging")
        staging.deleteRecursively()
        staging.mkdirs()
        val canonicalStaging = staging.canonicalPath
        ZipInputStream(zip.buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val target = File(staging, entry.name)
                // Refuse entries that would escape the staging folder.
                if (!target.canonicalPath.startsWith(canonicalStaging + File.separator) && target.canonicalPath != canonicalStaging) {
                    throw SecurityException("Archivo no válido")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { zis.copyTo(it) }
                }
            }
        }
        // Find the folder that holds "am/final.mdl".
        val modelRoot = staging.walkTopDown().firstOrNull { File(it, "am/final.mdl").exists() }
            ?: run {
                staging.deleteRecursively()
                throw IllegalStateException("El archivo no contiene un modelo Vosk válido.")
            }
        modelDir.deleteRecursively()
        modelDir.parentFile?.mkdirs()
        if (!modelRoot.renameTo(modelDir)) {
            modelRoot.copyRecursively(modelDir, overwrite = true)
        }
        staging.deleteRecursively()
    }

    companion object {
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
    }
}
