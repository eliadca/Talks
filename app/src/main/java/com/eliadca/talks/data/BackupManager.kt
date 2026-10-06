package com.eliadca.talks.data

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.eliadca.talks.data.db.FolderEntity
import com.eliadca.talks.data.db.SpeechEntity
import com.eliadca.talks.data.db.TalkSessionEntity
import com.eliadca.talks.data.db.TalksDatabase
import com.eliadca.talks.data.db.VersionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class BackupFolder(val id: Long, val name: String, val color: Int, val createdAt: Long)

@Serializable
private data class BackupSpeech(
    val id: Long,
    val title: String,
    val body: String,
    val searchText: String,
    val preview: String,
    val wordCount: Int,
    val folderId: Long? = null,
    val pinned: Boolean = false,
    val color: Int = 0,
    val targetMinutes: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val trashedAt: Long? = null,
    val lastTalkAt: Long? = null,
)

@Serializable
private data class BackupVersion(
    val speechId: Long,
    val createdAt: Long,
    val title: String,
    val body: String,
    val wordCount: Int,
    val label: String? = null,
)

@Serializable
private data class BackupSession(
    val speechId: Long,
    val startedAt: Long,
    val durationMs: Long,
    val wordsSpoken: Int,
    val wpm: Int,
    val completion: Float,
    val engine: String,
)

@Serializable
private data class BackupFile(
    val format: Int = 1,
    val app: String = "Talks",
    val exportedAt: Long,
    val folders: List<BackupFolder>,
    val speeches: List<BackupSpeech>,
    val versions: List<BackupVersion> = emptyList(),
    val sessions: List<BackupSession> = emptyList(),
)

class BackupSummary(val speeches: Int, val folders: Int, val skipped: Int = 0)

/** Writes every speech, folder, version and session to one JSON file, and reads it back. */
class BackupManager(
    private val db: TalksDatabase,
    private val resolver: ContentResolver,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(target: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val folders = db.folders().all()
        val speeches = db.speeches().all()
        val file = BackupFile(
            exportedAt = System.currentTimeMillis(),
            folders = folders.map { BackupFolder(it.id, it.name, it.color, it.createdAt) },
            speeches = speeches.map {
                BackupSpeech(
                    it.id, it.title, it.body, it.searchText, it.preview, it.wordCount, it.folderId, it.pinned, it.color,
                    it.targetMinutes, it.createdAt, it.updatedAt, it.trashedAt, it.lastTalkAt,
                )
            },
            versions = db.versions().all().map { BackupVersion(it.speechId, it.createdAt, it.title, it.body, it.wordCount, it.label) },
            sessions = db.sessions().all().map { BackupSession(it.speechId, it.startedAt, it.durationMs, it.wordsSpoken, it.wpm, it.completion, it.engine) },
        )
        val text = json.encodeToString(BackupFile.serializer(), file)
        resolver.openOutputStream(target, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("No se pudo escribir el archivo.")
        BackupSummary(speeches.size, folders.size)
    }

    /**
     * Adds the contents of a backup to the library. Speeches that are already present (same title
     * and creation time) are skipped, so restoring twice does not duplicate anything.
     */
    suspend fun import(source: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(source)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("No se pudo leer el archivo.")
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw IllegalStateException("El archivo no es una copia de seguridad de Talks.")
        }
        if (file.app != "Talks") throw IllegalStateException("El archivo no es una copia de seguridad de Talks.")

        db.withTransaction {
            val existingFolders = db.folders().all()
            val folderMap = HashMap<Long, Long>()
            var newFolders = 0
            for (f in file.folders) {
                val same = existingFolders.firstOrNull { it.name == f.name && it.color == f.color }
                if (same != null) {
                    folderMap[f.id] = same.id
                } else {
                    folderMap[f.id] = db.folders().insert(FolderEntity(name = f.name, color = f.color, createdAt = f.createdAt))
                    newFolders++
                }
            }

            val existing = db.speeches().all().map { it.title to it.createdAt }.toHashSet()
            val speechMap = HashMap<Long, Long>()
            var added = 0
            var skipped = 0
            for (s in file.speeches) {
                if ((s.title to s.createdAt) in existing) {
                    skipped++
                    continue
                }
                speechMap[s.id] = db.speeches().insert(
                    SpeechEntity(
                        title = s.title, body = s.body, searchText = s.searchText, preview = s.preview,
                        wordCount = s.wordCount, folderId = s.folderId?.let { folderMap[it] }, pinned = s.pinned,
                        color = s.color, targetMinutes = s.targetMinutes, createdAt = s.createdAt, updatedAt = s.updatedAt,
                        trashedAt = s.trashedAt, lastTalkAt = s.lastTalkAt,
                    ),
                )
                added++
            }
            for (v in file.versions) {
                val id = speechMap[v.speechId] ?: continue
                db.versions().insert(VersionEntity(speechId = id, createdAt = v.createdAt, title = v.title, body = v.body, wordCount = v.wordCount, label = v.label))
            }
            for (t in file.sessions) {
                val id = speechMap[t.speechId] ?: continue
                db.sessions().insert(
                    TalkSessionEntity(
                        speechId = id, startedAt = t.startedAt, durationMs = t.durationMs, wordsSpoken = t.wordsSpoken,
                        wpm = t.wpm, completion = t.completion, engine = t.engine,
                    ),
                )
            }
            BackupSummary(added, newFolders, skipped)
        }
    }
}
