package com.eliadca.talks.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "speeches",
    indices = [Index("folderId"), Index("trashedAt"), Index("updatedAt")],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class SpeechEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** The [com.eliadca.talks.core.doc.RichDoc] as JSON. */
    val body: String,
    /** Accent-free lower-case title + text, for search. */
    val searchText: String,
    /** First lines of the text, shown in lists. */
    val preview: String,
    val wordCount: Int,
    val folderId: Long?,
    val pinned: Boolean,
    /** Label colour (ARGB), 0 for none. */
    val color: Int,
    /** Intended length in minutes, 0 for none. */
    val targetMinutes: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val trashedAt: Long?,
    val lastTalkAt: Long?,
)

/** Everything the list needs, without the (large) body. */
data class SpeechListItem(
    val id: Long,
    val title: String,
    val preview: String,
    val wordCount: Int,
    val folderId: Long?,
    val pinned: Boolean,
    val color: Int,
    val targetMinutes: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val trashedAt: Long?,
    val lastTalkAt: Long?,
)

@Entity(
    tableName = "versions",
    indices = [Index("speechId", "createdAt")],
    foreignKeys = [
        ForeignKey(
            entity = SpeechEntity::class,
            parentColumns = ["id"],
            childColumns = ["speechId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class VersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val speechId: Long,
    val createdAt: Long,
    val title: String,
    val body: String,
    val wordCount: Int,
    /** Set when the user saved this version on purpose (such versions are never pruned). */
    val label: String?,
)

data class VersionItem(
    val id: Long,
    val createdAt: Long,
    val title: String,
    val wordCount: Int,
    val label: String?,
)

/** A completed or abandoned run of Talks mode, used to learn the user's speaking pace. */
@Entity(
    tableName = "talk_sessions",
    indices = [Index("speechId", "startedAt")],
    foreignKeys = [
        ForeignKey(
            entity = SpeechEntity::class,
            parentColumns = ["id"],
            childColumns = ["speechId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TalkSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val speechId: Long,
    val startedAt: Long,
    val durationMs: Long,
    val wordsSpoken: Int,
    /** Words per minute over the stretch of the speech that was actually covered. */
    val wpm: Int,
    /** Fraction of the speech covered, 0..1. */
    val completion: Float,
    val engine: String,
)
