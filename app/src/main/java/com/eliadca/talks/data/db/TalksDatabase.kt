package com.eliadca.talks.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FolderEntity::class,
        SpeechEntity::class,
        VersionEntity::class,
        TalkSessionEntity::class,
        NoteEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class TalksDatabase : RoomDatabase() {
    abstract fun speeches(): SpeechDao
    abstract fun folders(): FolderDao
    abstract fun versions(): VersionDao
    abstract fun sessions(): SessionDao
    abstract fun notes(): NoteDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, transcript TEXT NOT NULL, automatic INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_updatedAt ON notes (updatedAt)")
            }
        }

        fun build(context: Context): TalksDatabase =
            Room.databaseBuilder(context.applicationContext, TalksDatabase::class.java, "talks.db")
                .addMigrations(MIGRATION_1_2).build()
    }
}
