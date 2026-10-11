package com.eliadca.talks.notes

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eliadca.talks.core.doc.Markup
import com.eliadca.talks.core.ink.InkDocument
import com.eliadca.talks.core.ink.InkPoint
import com.eliadca.talks.core.ink.PenKind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.eliadca.talks.data.BackupManager
import com.eliadca.talks.data.NotesRepository
import com.eliadca.talks.data.SpeechRepository
import com.eliadca.talks.data.db.NoteEntity
import com.eliadca.talks.data.db.TalkSessionEntity
import com.eliadca.talks.data.db.TalksDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotesPersistenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun migrationAddsNotesAndKeepsEveryExistingSpeechFolderVersionAndSession() = runBlocking {
        val name = "migration-notes.db"
        context.deleteDatabase(name)
        val original = Room.databaseBuilder(context, TalksDatabase::class.java, name).build()
        val speeches = SpeechRepository(original)
        val folder = speeches.createFolder("Mis carpetas", 0)
        val id = speeches.create("Mi discurso", Markup.parse("Hola **mundo**"), folder)
        speeches.saveVersion(id, "Conservar")
        original.sessions().insert(TalkSessionEntity(speechId = id, startedAt = 12, durationMs = 1000, wordsSpoken = 2, wpm = 120, completion = 1f, engine = "prueba"))
        val speech = original.speeches().get(id)
        val versions = original.versions().all()
        val sessions = original.sessions().all()
        original.close()
        // Version 1 had exactly these four tables, no notes and no other schema changes.
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE notes"); it.execSQL("DELETE FROM room_master_table"); it.version = 1
        }
        val upgraded = Room.databaseBuilder(context, TalksDatabase::class.java, name)
            .addMigrations(TalksDatabase.MIGRATION_1_2, TalksDatabase.MIGRATION_2_3).build()
        try {
            assertEquals(speech, upgraded.speeches().get(id))
            assertEquals(folder, upgraded.folders().all().single().id)
            assertEquals(versions, upgraded.versions().all())
            assertEquals(sessions, upgraded.sessions().all())
            val noteId = upgraded.notes().insert(NoteEntity(title = "Nueva nota", createdAt = 1, updatedAt = 1))
            assertEquals("Nueva nota", upgraded.notes().get(noteId)!!.title)
        } finally { upgraded.close() }
    }

    @Test fun migrationAddsInkWithoutChangingAnExistingListeningNote() = runBlocking {
        val name = "migration-ink.db"
        context.deleteDatabase(name)
        val original = Room.databaseBuilder(context, TalksDatabase::class.java, name).build()
        val saved = NoteEntity(id = 7, title = "Ideas", body = "Conservar texto", transcript = "[]", automatic = true, createdAt = 5, updatedAt = 8)
        original.notes().insert(saved); original.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("ALTER TABLE notes RENAME TO old_notes")
            it.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, transcript TEXT NOT NULL, automatic INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            it.execSQL("INSERT INTO notes SELECT id, title, body, transcript, automatic, createdAt, updatedAt FROM old_notes")
            it.execSQL("DROP TABLE old_notes")
            it.execSQL("CREATE INDEX index_notes_updatedAt ON notes (updatedAt)")
            it.execSQL("DELETE FROM room_master_table"); it.version = 2
        }
        val upgraded = Room.databaseBuilder(context, TalksDatabase::class.java, name).addMigrations(TalksDatabase.MIGRATION_2_3).build()
        try { assertEquals(saved, upgraded.notes().get(7)) } finally { upgraded.close() }
    }

    @Test fun orderedSavesCannotOverwriteNewerEditsAndDeletionWaitsForPendingWrites() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TalksDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repo = NotesRepository(db.notes(), scope)
            val note = repo.create()
            repeat(50) { repo.save(note.copy(body = "Texto $it")) }
            assertEquals("Texto 49", repo.load(note.id)!!.body)
            repo.save(note.copy(body = "Último texto"))
            repo.delete(note.id)
            assertNull(repo.load(note.id))
            assertNull(repo.error.value)
        } finally { scope.cancel(); db.close() }
    }

    @Test fun backupKeepsNotesAndTranscriptAndOldBackupsStillRestore() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TalksDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, TalksDatabase::class.java).build()
        try {
            val drawing = InkDocument(wholePageRuled = true).addStroke(PenKind.FOUNTAIN, 0xff123456.toInt(), 7f,
                listOf(InkPoint(20f, 90f, .2f), InkPoint(90f, 170f, .8f)))
            db.notes().insert(NoteEntity(title = "Reunión", body = "Mis ideas", transcript = "[{\"id\":1,\"text\":\"Original\"}]", automatic = true, createdAt = 100, updatedAt = 200,
                ink = Json.encodeToString(drawing)))
            SpeechRepository(db).create("Discurso previo", Markup.parse("Conservar"))
            val file = File(context.cacheDir, "notes-backup.json")
            val uri = Uri.fromFile(file)
            assertEquals(1, BackupManager(db, context.contentResolver).export(uri).notes)
            val restore = BackupManager(destination, context.contentResolver)
            assertEquals(1, restore.import(uri).notes)
            assertEquals(db.notes().all().single().copy(id = 1), destination.notes().all().single())
            assertEquals(0, restore.import(uri).notes)
            assertEquals(1, destination.notes().all().size)
            val old = File(context.cacheDir, "old-backup.json")
            old.writeText("""{"format":1,"app":"Talks","exportedAt":1,"folders":[],"speeches":[]}""")
            assertEquals(0, restore.import(Uri.fromFile(old)).notes)
            assertEquals("Discurso previo", destination.speeches().observeActive().first().single().title)
        } finally { db.close(); destination.close() }
    }
}
