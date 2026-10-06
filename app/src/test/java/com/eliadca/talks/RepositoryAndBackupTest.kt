package com.eliadca.talks

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eliadca.talks.core.doc.Markup
import com.eliadca.talks.core.sample.SampleContent
import com.eliadca.talks.data.BackupManager
import com.eliadca.talks.data.SpeechRepository
import com.eliadca.talks.data.db.TalkSessionEntity
import com.eliadca.talks.data.db.TalksDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryAndBackupTest {

    private lateinit var context: Context
    private lateinit var db: TalksDatabase
    private lateinit var repo: SpeechRepository
    private var clock = 1_000_000_000L

    private fun newDb() = Room.inMemoryDatabaseBuilder(context, TalksDatabase::class.java).allowMainThreadQueries().build()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = newDb()
        repo = SpeechRepository(db) { clock }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test fun createLoadAndSaveContent() = runBlocking {
        val id = repo.create("Mi discurso", Markup.parse("Hola **mundo**"))
        val loaded = repo.load(id)!!
        assertEquals("Mi discurso", loaded.title)
        assertEquals("Hola mundo", loaded.doc.text)

        clock += 1000
        repo.saveContent(id, "Nuevo título", Markup.parse("Otro texto con más palabras aquí"))
        val again = repo.load(id)!!
        assertEquals("Nuevo título", again.title)
        assertEquals("Otro texto con más palabras aquí", again.doc.text)
        val item = repo.observeItem(id).first()!!
        assertEquals(6, item.wordCount)
        assertTrue(item.updatedAt > item.createdAt)
    }

    @Test fun searchIgnoresCaseAndAccentsAndMatchesTitleOrBody() = runBlocking {
        repo.create("La Canción del Pirata", Markup.parse("Con diez cañones por banda"))
        repo.create("Otro", Markup.parse("Nada que ver"))
        assertEquals(1, repo.search("cancion").first().size)
        assertEquals(1, repo.search("CAÑONES").first().size)
        assertEquals("ñ is a different letter from n", 0, repo.search("canones").first().size)
        assertEquals("every word must match, in order", 1, repo.search("diez banda").first().size)
        assertEquals(0, repo.search("zzz").first().size)
        assertEquals("SQL wildcards in the query are escaped", 0, repo.search("100%_").first().size)
    }

    @Test fun trashRestoreAndPurge() = runBlocking {
        val a = repo.create("A")
        val b = repo.create("B")
        repo.moveToTrash(a)
        assertEquals(listOf(b), repo.observeActive().first().map { it.id })
        assertEquals(listOf(a), repo.observeTrashed().first().map { it.id })
        repo.restoreFromTrash(a)
        assertEquals(2, repo.observeActive().first().size)

        repo.moveToTrash(a)
        clock += 31L * 24 * 3600 * 1000
        repo.purgeOldTrash(30)
        assertEquals(0, repo.observeTrashed().first().size)
        assertEquals(null, repo.load(a))
    }

    @Test fun foldersKeepSpeechesWhenDeleted() = runBlocking {
        val folder = repo.createFolder("Conferencia", 0xFF1E88E5.toInt())
        val id = repo.create("Charla", folderId = folder)
        assertEquals(folder, repo.observeItem(id).first()!!.folderId)
        repo.deleteFolder(folder)
        assertEquals(null, repo.observeItem(id).first()!!.folderId)
        assertNotNull(repo.load(id))
    }

    @Test fun versionsAreSnapshottedOncePerIntervalAndRestoreKeepsTheCurrentText() = runBlocking {
        val id = repo.create("T", Markup.parse("uno"))
        repo.saveContent(id, "T", Markup.parse("uno dos"))
        assertEquals(1, repo.observeVersions(id).first().size) // first save snapshots
        clock += 60_000
        repo.saveContent(id, "T", Markup.parse("uno dos tres"))
        assertEquals("less than 10 minutes: no new snapshot", 1, repo.observeVersions(id).first().size)
        clock += 11 * 60_000
        repo.saveContent(id, "T", Markup.parse("uno dos tres cuatro"))
        val versions = repo.observeVersions(id).first()
        assertEquals(2, versions.size)

        val oldest = versions.last()
        repo.restoreVersion(id, oldest.id)
        assertEquals("uno dos", repo.load(id)!!.doc.text)
        // The text before restoring was kept.
        assertTrue(repo.observeVersions(id).first().any { it.label == "Antes de restaurar" })
    }

    @Test fun namedVersionsAreNeverPruned() = runBlocking {
        val id = repo.create("T", Markup.parse("x"))
        repo.saveVersion(id, "Importante")
        repeat(50) { i ->
            clock += 11 * 60_000
            repo.saveContent(id, "T", Markup.parse("texto $i"))
        }
        val versions = repo.observeVersions(id).first()
        assertTrue(versions.any { it.label == "Importante" })
        assertTrue("automatic versions are capped", versions.count { it.label == null } <= 40)
    }

    @Test fun duplicateCopiesContent() = runBlocking {
        val id = repo.create("Original", Markup.parse("# Hola\ntexto"))
        val copy = repo.duplicate(id)!!
        assertEquals("Original (copia)", repo.load(copy)!!.title)
        assertEquals(repo.load(id)!!.doc, repo.load(copy)!!.doc)
    }

    @Test fun typicalPaceUsesRecentSubstantialRuns() = runBlocking {
        val id = repo.create("Charla")
        assertEquals(130, repo.typicalWpm(130))
        repo.recordSession(TalkSessionEntity(speechId = id, startedAt = 1, durationMs = 120_000, wordsSpoken = 300, wpm = 150, completion = 0.9f, engine = "x"))
        repo.recordSession(TalkSessionEntity(speechId = id, startedAt = 2, durationMs = 120_000, wordsSpoken = 250, wpm = 110, completion = 0.9f, engine = "x"))
        repo.recordSession(TalkSessionEntity(speechId = id, startedAt = 3, durationMs = 5_000, wordsSpoken = 5, wpm = 400, completion = 0.01f, engine = "x")) // too short
        assertEquals(130, repo.typicalWpm(100))
    }

    @Test fun backupRoundTripsEverythingAndRestoringTwiceDoesNotDuplicate() = runBlocking {
        val folder = repo.createFolder("Eventos", 0xFF43A047.toInt())
        val a = repo.create("Discurso A", SampleContent.practice, folderId = folder)
        repo.create("Discurso B", Markup.parse("- uno\n- dos"))
        repo.saveVersion(a, "Antes del evento")
        repo.recordSession(TalkSessionEntity(speechId = a, startedAt = 5, durationMs = 90_000, wordsSpoken = 200, wpm = 133, completion = 0.5f, engine = "Fake"))

        val file = File.createTempFile("talks-backup", ".json")
        val uri = Uri.fromFile(file)
        val export = BackupManager(db, context.contentResolver).export(uri)
        assertEquals(2, export.speeches)
        assertTrue(file.length() > 100)

        val other = newDb()
        try {
            val restored = BackupManager(other, context.contentResolver).import(uri)
            assertEquals(2, restored.speeches)
            val otherRepo = SpeechRepository(other) { clock }
            val items = otherRepo.observeActive().first()
            assertEquals(setOf("Discurso A", "Discurso B"), items.map { it.title }.toSet())
            val restoredA = items.first { it.title == "Discurso A" }
            assertEquals(SampleContent.practice.text, otherRepo.load(restoredA.id)!!.doc.text)
            assertNotNull(restoredA.folderId)
            assertTrue(otherRepo.observeVersions(restoredA.id).first().any { it.label == "Antes del evento" })

            val again = BackupManager(other, context.contentResolver).import(uri)
            assertEquals(0, again.speeches)
            assertEquals(2, again.skipped)
            assertEquals(2, otherRepo.observeActive().first().size)
        } finally {
            other.close()
            file.delete()
        }
    }

    @Test fun importingGarbageGivesAReadableError() = runBlocking {
        val file = File.createTempFile("not-a-backup", ".json").apply { writeText("hola, esto no es json") }
        try {
            BackupManager(db, context.contentResolver).import(Uri.fromFile(file))
            throw AssertionError("expected an error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("copia de seguridad"))
        } finally {
            file.delete()
        }
    }
}
