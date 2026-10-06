package com.eliadca.talks.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FolderEntity::class,
        SpeechEntity::class,
        VersionEntity::class,
        TalkSessionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class TalksDatabase : RoomDatabase() {
    abstract fun speeches(): SpeechDao
    abstract fun folders(): FolderDao
    abstract fun versions(): VersionDao
    abstract fun sessions(): SessionDao

    companion object {
        fun build(context: Context): TalksDatabase =
            Room.databaseBuilder(context.applicationContext, TalksDatabase::class.java, "talks.db").build()
    }
}
