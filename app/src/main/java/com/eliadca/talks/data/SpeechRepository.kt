package com.eliadca.talks.data

import androidx.room.withTransaction
import com.eliadca.talks.core.doc.RichDoc
import com.eliadca.talks.core.doc.normalized
import com.eliadca.talks.core.doc.spokenWordCount
import com.eliadca.talks.core.text.SpanishText
import com.eliadca.talks.data.db.FolderEntity
import com.eliadca.talks.data.db.SpeechEntity
import com.eliadca.talks.data.db.SpeechListItem
import com.eliadca.talks.data.db.TalkSessionEntity
import com.eliadca.talks.data.db.TalksDatabase
import com.eliadca.talks.data.db.VersionEntity
import com.eliadca.talks.data.db.VersionItem
import kotlinx.coroutines.flow.Flow

/** A speech loaded for editing or for Talks mode. */
class LoadedSpeech(
    val id: Long,
    val title: String,
    val doc: RichDoc,
    val folderId: Long?,
    val targetMinutes: Int,
    val updatedAt: Long,
)

/** All reads and writes of speeches, folders, versions and Talks sessions. */
class SpeechRepository(
    private val db: TalksDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val speechDao = db.speeches()
    private val folderDao = db.folders()
    private val versionDao = db.versions()
    private val sessionDao = db.sessions()

    // --- observing --------------------------------------------------------------------------

    fun observeActive(): Flow<List<SpeechListItem>> = speechDao.observeActive()
    fun observeTrashed(): Flow<List<SpeechListItem>> = speechDao.observeTrashed()
    fun observeItem(id: Long): Flow<SpeechListItem?> = speechDao.observeItem(id)
    fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeAll()
    fun observeVersions(speechId: Long): Flow<List<VersionItem>> = versionDao.observe(speechId)
    fun observeSessions(speechId: Long, limit: Int = 10): Flow<List<TalkSessionEntity>> =
        sessionDao.observeForSpeech(speechId, limit)

    /** Speeches whose title or text contains every word of [query], ignoring case and accents. */
    fun search(query: String): Flow<List<SpeechListItem>> {
        val folded = SpanishText.foldForSearch(query).trim().replace(Regex("\\s+"), " ")
        val pattern = "%" + folded.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace(" ", "%") + "%"
        return speechDao.search(pattern)
    }

    // --- speeches ---------------------------------------------------------------------------

    suspend fun load(id: Long): LoadedSpeech? {
        val e = speechDao.get(id) ?: return null
        return LoadedSpeech(e.id, e.title, RichDoc.fromJson(e.body), e.folderId, e.targetMinutes, e.updatedAt)
    }

    suspend fun create(title: String = "", doc: RichDoc = RichDoc.EMPTY, folderId: Long? = null): Long {
        val t = now()
        val d = doc.normalized()
        val derived = derive(title, d)
        return speechDao.insert(
            SpeechEntity(
                title = title,
                body = d.toJson(),
                searchText = derived.search,
                preview = derived.preview,
                wordCount = derived.words,
                folderId = folderId,
                pinned = false,
                color = 0,
                targetMinutes = 0,
                createdAt = t,
                updatedAt = t,
                trashedAt = null,
                lastTalkAt = null,
            ),
        )
    }

    /** Stores new content for a speech and, if enough time has passed, keeps a snapshot of it. */
    suspend fun saveContent(id: Long, title: String, doc: RichDoc) {
        val d = doc.normalized()
        val body = d.toJson()
        val derived = derive(title, d)
        val t = now()
        db.withTransaction {
            speechDao.updateContent(id, title, body, derived.search, derived.preview, derived.words, t)
            snapshotIfDue(id, title, body, derived.words, t)
        }
    }

    private suspend fun snapshotIfDue(id: Long, title: String, body: String, words: Int, t: Long) {
        val latest = versionDao.latestTime(id)
        val due = latest == null || t - latest >= SNAPSHOT_INTERVAL_MS
        if (!due) return
        if (versionDao.latestBody(id) == body) return
        versionDao.insert(VersionEntity(speechId = id, createdAt = t, title = title, body = body, wordCount = words, label = null))
        versionDao.prune(id, MAX_AUTOMATIC_VERSIONS)
    }

    /** Keeps the current content as a named version that is never pruned. */
    suspend fun saveVersion(id: Long, label: String) {
        val e = speechDao.get(id) ?: return
        versionDao.insert(
            VersionEntity(
                speechId = id, createdAt = now(), title = e.title, body = e.body, wordCount = e.wordCount,
                label = label.ifBlank { "Versión guardada" },
            ),
        )
    }

    suspend fun loadVersion(versionId: Long): LoadedSpeech? {
        val v = versionDao.get(versionId) ?: return null
        val e = speechDao.get(v.speechId)
        return LoadedSpeech(v.speechId, v.title, RichDoc.fromJson(v.body), e?.folderId, e?.targetMinutes ?: 0, v.createdAt)
    }

    /** Replaces the speech with an older version, first keeping the current text so nothing is lost. */
    suspend fun restoreVersion(speechId: Long, versionId: Long) {
        val v = versionDao.get(versionId) ?: return
        val current = speechDao.get(speechId) ?: return
        db.withTransaction {
            versionDao.insert(
                VersionEntity(
                    speechId = speechId, createdAt = now(), title = current.title, body = current.body,
                    wordCount = current.wordCount, label = "Antes de restaurar",
                ),
            )
            val doc = RichDoc.fromJson(v.body)
            val derived = derive(v.title, doc)
            speechDao.updateContent(speechId, v.title, v.body, derived.search, derived.preview, derived.words, now())
        }
    }

    suspend fun deleteVersion(versionId: Long) = versionDao.delete(versionId)

    suspend fun setPinned(id: Long, pinned: Boolean) = speechDao.setPinned(id, pinned)
    suspend fun setFolder(id: Long, folderId: Long?) = speechDao.setFolder(id, folderId)
    suspend fun setColor(id: Long, color: Int) = speechDao.setColor(id, color)
    suspend fun setTargetMinutes(id: Long, minutes: Int) = speechDao.setTarget(id, minutes.coerceIn(0, 600))

    suspend fun duplicate(id: Long): Long? {
        val e = speechDao.get(id) ?: return null
        val t = now()
        return speechDao.insert(e.copy(id = 0, title = copyTitle(e.title), pinned = false, createdAt = t, updatedAt = t, trashedAt = null, lastTalkAt = null))
    }

    suspend fun moveToTrash(id: Long) = speechDao.setTrashed(id, now())
    suspend fun restoreFromTrash(id: Long) = speechDao.setTrashed(id, null)
    suspend fun deletePermanently(id: Long) = speechDao.delete(id)
    suspend fun emptyTrash() = speechDao.deleteAllTrashed()

    /** Removes speeches that have been in the trash for more than [days] days. */
    suspend fun purgeOldTrash(days: Int = 30) =
        speechDao.deleteTrashedBefore(now() - days * 24L * 60 * 60 * 1000)

    suspend fun count(): Int = speechDao.count()

    // --- folders ----------------------------------------------------------------------------

    suspend fun createFolder(name: String, color: Int): Long =
        folderDao.insert(FolderEntity(name = name.trim().ifEmpty { "Carpeta" }, color = color, createdAt = now()))

    suspend fun updateFolder(folder: FolderEntity) = folderDao.update(folder.copy(name = folder.name.trim().ifEmpty { "Carpeta" }))

    /** Deletes a folder; its speeches stay and simply have no folder. */
    suspend fun deleteFolder(id: Long) = folderDao.delete(id)

    // --- Talks sessions ---------------------------------------------------------------------

    suspend fun recordSession(session: TalkSessionEntity) {
        sessionDao.insert(session)
        speechDao.setLastTalk(session.speechId, session.startedAt)
    }

    /** The user's own speaking pace from recent Talks runs, or [fallback] when there is no data. */
    suspend fun typicalWpm(fallback: Int): Int {
        val recent = sessionDao.recentPace(5).filter { it in 60..260 }
        return if (recent.isEmpty()) fallback else recent.average().toInt()
    }

    // --- helpers ----------------------------------------------------------------------------

    private class Derived(val search: String, val preview: String, val words: Int)

    private fun derive(title: String, doc: RichDoc): Derived {
        val preview = doc.text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(3)
            .joinToString("  ")
            .take(180)
        return Derived(
            search = SpanishText.foldForSearch(title + "\n" + doc.text),
            preview = preview,
            words = doc.spokenWordCount(readHeadings = false),
        )
    }

    private fun copyTitle(title: String): String = if (title.isBlank()) "Copia" else "$title (copia)"

    private companion object {
        const val SNAPSHOT_INTERVAL_MS = 10L * 60 * 1000
        const val MAX_AUTOMATIC_VERSIONS = 40
    }
}
