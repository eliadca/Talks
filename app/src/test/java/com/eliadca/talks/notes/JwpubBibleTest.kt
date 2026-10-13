package com.eliadca.talks.notes

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.eliadca.talks.bible.JwpubBible
import com.eliadca.talks.core.bible.BibleReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class JwpubBibleTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clear() { File(context.filesDir, "study-bible.sqlite").delete() }

    private fun zip(vararg files: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> files.forEach { (name, bytes) -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } }
    }.toByteArray()

    private fun fixture(year: Int = 2026): ByteArray {
        val dbFile = File(context.cacheDir, "fixture.db").also { it.delete() }
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL("CREATE TABLE Publication(MepsLanguageIndex INTEGER, Symbol TEXT, Year INTEGER, IssueTagNumber INTEGER, Title TEXT)")
            db.execSQL("INSERT INTO Publication VALUES (1, 'nwtsty', 2026, 0, 'Biblia de prueba')")
            db.execSQL("CREATE TABLE BibleVerse(BibleVerseId INTEGER, Content BLOB)")
            val blobs = listOf("qIVD5kxWICNjpJTA/1JOdN2TtTF3dN4+MliShiM5sPQiRVTQBveDt+NkurtdTlLC/sklHwNpDB7D6AKjlRuaWXjS5T77obo8cMx4qjgP26g=",
                "qIVD5kxWICNjpJTA/1JOdGccBRZqAz8l5/2fcDBa4VAE4Q+UqbwFuWzws6HvwJLomXv0EbMdndCirtclS74r5rM+mFjq+FiWMJG5u8A9N3M=")
            blobs.forEachIndexed { i, blob -> db.execSQL("INSERT INTO BibleVerse VALUES (?, ?)", arrayOf(i, Base64.getDecoder().decode(blob))) }
        }
        val manifest = """{"publication":{"fileName":"../untrusted.db","symbol":"nwtsty","language":1,"year":$year,"issueId":0}}"""
        return zip("manifest.json" to manifest.toByteArray(), "contents" to zip("../untrusted.db" to dbFile.readBytes(), "../ignore.txt" to "unused".toByteArray()))
    }

    @Test fun nestedZipImportsAndReadsExactRangesWithoutExtractingOtherFiles() = runBlocking {
        val bible = JwpubBible(context)
        val info = bible.import(ByteArrayInputStream(fixture()))
        assertEquals(2, info.verses)
        assertEquals("Biblia de prueba", info.title)
        assertEquals("16  Texto de prueba con amor.\n17  Segundo versículo de prueba.", bible.passage(BibleReference(43, 3, 16, 17))!!.text)
        assertNull(bible.passage(BibleReference(43, 3, 16, 18)))
        assertNull(bible.passage(BibleReference(43, 99, 1)))
        assertFalse(File(context.cacheDir, "untrusted.db").exists())
        assertEquals(info, JwpubBible(context).info())
    }
    @Test fun corruptImportPreservesThePreviouslyWorkingBibleAndCleansTemporaryFiles() = runBlocking {
        val bible = JwpubBible(context)
        val original = bible.import(ByteArrayInputStream(fixture()))
        assertTrue(runCatching { bible.import(ByteArrayInputStream(fixture(2025))) }.isFailure)
        assertEquals(original, bible.info())
        assertNotNull(bible.passage(BibleReference(43, 3, 16)))
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("jwpub-") })
    }
    @Test fun realStudyBibleImportsLocallyWhenAvailable() = runBlocking {
        val file = File(System.getenv("TALKS_TEST_JWPUB") ?: "/nonexistent")
        assumeTrue("Real publication provided only for local verification", file.isFile)
        val bible = JwpubBible(context)
        val info = file.inputStream().use { bible.import(it) }
        assertTrue(info.title.contains("estudio"))
        assertTrue(info.verses > 30_000)
        for (ref in listOf(BibleReference(1, 1, 1), BibleReference(43, 3, 16), BibleReference(19, 119, 105), BibleReference(66, 22, 21))) {
            assertTrue(ref.label, bible.passage(ref)!!.text.isNotBlank())
        }
    }
}
