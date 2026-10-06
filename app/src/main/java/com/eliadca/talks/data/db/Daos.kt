package com.eliadca.talks.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val LIST_COLUMNS =
    "id, title, preview, wordCount, folderId, pinned, color, targetMinutes, createdAt, updatedAt, trashedAt, lastTalkAt"

@Dao
interface SpeechDao {
    @Query("SELECT $LIST_COLUMNS FROM speeches WHERE trashedAt IS NULL")
    fun observeActive(): Flow<List<SpeechListItem>>

    @Query("SELECT $LIST_COLUMNS FROM speeches WHERE trashedAt IS NOT NULL ORDER BY trashedAt DESC")
    fun observeTrashed(): Flow<List<SpeechListItem>>

    @Query("SELECT $LIST_COLUMNS FROM speeches WHERE trashedAt IS NULL AND searchText LIKE :pattern ESCAPE '\\'")
    fun search(pattern: String): Flow<List<SpeechListItem>>

    @Query("SELECT $LIST_COLUMNS FROM speeches WHERE id = :id")
    fun observeItem(id: Long): Flow<SpeechListItem?>

    @Query("SELECT * FROM speeches WHERE id = :id")
    suspend fun get(id: Long): SpeechEntity?

    @Query("SELECT * FROM speeches")
    suspend fun all(): List<SpeechEntity>

    @Insert
    suspend fun insert(speech: SpeechEntity): Long

    @Update
    suspend fun update(speech: SpeechEntity)

    @Query(
        "UPDATE speeches SET title = :title, body = :body, searchText = :search, preview = :preview, " +
            "wordCount = :words, updatedAt = :now WHERE id = :id",
    )
    suspend fun updateContent(id: Long, title: String, body: String, search: String, preview: String, words: Int, now: Long)

    @Query("UPDATE speeches SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE speeches SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?)

    @Query("UPDATE speeches SET color = :color WHERE id = :id")
    suspend fun setColor(id: Long, color: Int)

    @Query("UPDATE speeches SET targetMinutes = :minutes WHERE id = :id")
    suspend fun setTarget(id: Long, minutes: Int)

    @Query("UPDATE speeches SET trashedAt = :at WHERE id = :id")
    suspend fun setTrashed(id: Long, at: Long?)

    @Query("UPDATE speeches SET lastTalkAt = :at WHERE id = :id")
    suspend fun setLastTalk(id: Long, at: Long)

    @Query("DELETE FROM speeches WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM speeches WHERE trashedAt IS NOT NULL")
    suspend fun deleteAllTrashed()

    @Query("DELETE FROM speeches WHERE trashedAt IS NOT NULL AND trashedAt < :before")
    suspend fun deleteTrashedBefore(before: Long)

    @Query("SELECT COUNT(*) FROM speeches")
    suspend fun count(): Int
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun all(): List<FolderEntity>

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface VersionDao {
    @Query("SELECT id, createdAt, title, wordCount, label FROM versions WHERE speechId = :speechId ORDER BY createdAt DESC")
    fun observe(speechId: Long): Flow<List<VersionItem>>

    @Query("SELECT * FROM versions WHERE id = :id")
    suspend fun get(id: Long): VersionEntity?

    @Query("SELECT MAX(createdAt) FROM versions WHERE speechId = :speechId")
    suspend fun latestTime(speechId: Long): Long?

    @Query("SELECT body FROM versions WHERE speechId = :speechId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestBody(speechId: Long): String?

    @Insert
    suspend fun insert(version: VersionEntity): Long

    @Query("DELETE FROM versions WHERE id = :id")
    suspend fun delete(id: Long)

    /** Keeps the newest [keep] automatic versions of a speech; labelled versions are never removed. */
    @Query(
        "DELETE FROM versions WHERE speechId = :speechId AND label IS NULL AND id NOT IN " +
            "(SELECT id FROM versions WHERE speechId = :speechId AND label IS NULL ORDER BY createdAt DESC LIMIT :keep)",
    )
    suspend fun prune(speechId: Long, keep: Int)

    @Query("SELECT * FROM versions")
    suspend fun all(): List<VersionEntity>
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: TalkSessionEntity): Long

    @Query("SELECT * FROM talk_sessions WHERE speechId = :speechId ORDER BY startedAt DESC LIMIT :limit")
    fun observeForSpeech(speechId: Long, limit: Int): Flow<List<TalkSessionEntity>>

    /** Pace of the most recent substantial runs, to estimate how long speeches take. */
    @Query("SELECT wpm FROM talk_sessions WHERE completion >= 0.3 AND durationMs >= 60000 ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recentPace(limit: Int): List<Int>

    @Query("SELECT * FROM talk_sessions")
    suspend fun all(): List<TalkSessionEntity>
}
