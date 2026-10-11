package com.eliadca.talks.bible

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.text.Html
import com.eliadca.talks.core.bible.BibleReference
import com.eliadca.talks.core.bible.JwpubCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

data class BibleInfo(val title: String, val verses: Int)
data class BiblePassage(val reference: BibleReference, val text: String, val edition: String) {
    val forNote: String get() = "${reference.label}\n$text"
}

/** Imports only the verse text from a user-selected JWPUB, never its media or executable markup. */
class JwpubBible(private val context: Context) {
    private val target = File(context.filesDir, "study-bible.sqlite")
    private val lock = Mutex()
    private val verseId = Regex("\\bid=[\"']v(\\d+)-(\\d+)-(\\d+)[\"']")

    suspend fun info(): BibleInfo? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!target.exists()) return@withLock null
            readDatabase { db -> db.rawQuery("SELECT title, verses FROM edition", null).use {
                if (it.moveToFirst()) BibleInfo(it.getString(0), it.getInt(1)) else null
            } }
        }
    }

    suspend fun import(uri: Uri, onProgress: (Int) -> Unit): BibleInfo =
        context.contentResolver.openInputStream(uri)?.use { import(it, onProgress) }
            ?: error("No se pudo abrir el archivo JWPUB.")

    suspend fun import(input: InputStream, onProgress: (Int) -> Unit = {}): BibleInfo = withContext(Dispatchers.IO) {
        lock.withLock {
            val work = Files.createTempDirectory(context.cacheDir.toPath(), "jwpub-").toFile()
            try {
                val outer = File(work, "publication.zip")
                input.copyLimited(outer, 256L * 1024 * 1024)
                val inner = File(work, "contents.zip")
                val database = File(work, "source.db")
                val identity = ZipFile(outer).use { zip ->
                    val manifest = zip.getEntry("manifest.json") ?: error("Falta manifest.json en el JWPUB.")
                    require(manifest.size in 1..1024 * 1024) { "Manifest JWPUB no válido." }
                    val publication = zip.getInputStream(manifest).use {
                        Json.parseToJsonElement(it.reader().readText()).jsonObject["publication"]?.jsonObject
                    } ?: error("El archivo no contiene una publicación JWPUB.")
                    val contents = zip.getEntry("contents") ?: error("Falta contents en el JWPUB.")
                    zip.getInputStream(contents).use { it.copyLimited(inner, 512L * 1024 * 1024) }
                    publication
                }
                val dbName = identity["fileName"]?.jsonPrimitive?.content
                ZipFile(inner).use { zip ->
                    val entry = dbName?.let(zip::getEntry) ?: zip.entries().asSequence()
                        .firstOrNull { it.name.endsWith(".db", true) || it.name.endsWith(".sqlite", true) }
                        ?: error("No se encontró la base de datos de la Biblia.")
                    zip.getInputStream(entry).use { it.copyLimited(database, 256L * 1024 * 1024) }
                }
                val staged = File(work, "verses.sqlite")
                val result = SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { source ->
                    val metadata = source.rawQuery("SELECT MepsLanguageIndex, Symbol, Year, IssueTagNumber, Title FROM Publication LIMIT 1", null).use {
                        require(it.moveToFirst()) { "La publicación no tiene identificación." }
                        require(it.getInt(0) == 1) { "Importa una Biblia en español para reconocer las citas habladas." }
                        val language = identity["language"]?.jsonPrimitive?.content?.toIntOrNull() ?: it.getInt(0)
                        val symbol = identity["symbol"]?.jsonPrimitive?.content ?: it.getString(1)
                        val year = identity["year"]?.jsonPrimitive?.content?.toIntOrNull() ?: it.getInt(2)
                        val issue = identity["issueId"]?.jsonPrimitive?.content?.toIntOrNull() ?: it.getInt(3)
                        JwpubCrypto(language, symbol, year, issue) to it.getString(4)
                    }
                    val total = source.rawQuery("SELECT count(*) FROM BibleVerse WHERE Content IS NOT NULL", null).use {
                        it.moveToFirst(); it.getInt(0)
                    }
                    require(total > 0) { "El JWPUB no contiene versículos de una Biblia." }
                    SQLiteDatabase.openOrCreateDatabase(staged, null).use { output ->
                        output.execSQL("CREATE TABLE verses (book INTEGER, chapter INTEGER, verse INTEGER, text TEXT NOT NULL, PRIMARY KEY(book, chapter, verse))")
                        output.execSQL("CREATE TABLE edition (title TEXT NOT NULL, verses INTEGER NOT NULL)")
                        var imported = 0
                        var scanned = 0
                        output.beginTransaction()
                        try {
                            output.compileStatement("INSERT INTO verses VALUES (?, ?, ?, ?)").use { insert ->
                                source.rawQuery("SELECT Content FROM BibleVerse WHERE Content IS NOT NULL ORDER BY BibleVerseId", null).use { rows ->
                                    while (rows.moveToNext()) {
                                        coroutineContext.ensureActive()
                                        val html = metadata.first.decrypt(rows.getBlob(0))
                                        val match = verseId.find(html)
                                        if (match != null) {
                                            val book = match.groupValues[1].toInt()
                                            val chapter = match.groupValues[2].toInt()
                                            val verse = match.groupValues[3].toInt()
                                            if (book in 1..66 && chapter in 1..150 && verse in 1..176) {
                                                val text = verseText(html)
                                                if (text.isNotBlank()) {
                                                    insert.bindLong(1, book.toLong()); insert.bindLong(2, chapter.toLong())
                                                    insert.bindLong(3, verse.toLong()); insert.bindString(4, text)
                                                    insert.executeInsert(); imported++
                                                }
                                            }
                                        }
                                        scanned++
                                        if (scanned % 300 == 0) onProgress(scanned * 100 / total)
                                    }
                                }
                            }
                            require(imported > 0) { "No se pudieron leer los versículos de esta edición." }
                            output.execSQL("INSERT INTO edition VALUES (?, ?)", arrayOf(metadata.second, imported))
                            output.setTransactionSuccessful()
                        } finally { output.endTransaction() }
                        BibleInfo(metadata.second, imported)
                    }
                }
                // A bad import leaves the previously working Bible intact.
                Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                onProgress(100)
                result
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                throw IllegalArgumentException("No se pudo importar la Biblia. ${e.message.orEmpty()}", e)
            } finally { work.deleteRecursively() }
        }
    }

    suspend fun passage(reference: BibleReference): BiblePassage? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!target.exists()) return@withLock null
            readDatabase { db ->
                val lines = db.rawQuery("SELECT verse, text FROM verses WHERE book = ? AND chapter = ? AND verse BETWEEN ? AND ? ORDER BY verse",
                    arrayOf(reference.book.toString(), reference.chapter.toString(), reference.firstVerse.toString(), reference.lastVerse.toString()))
                    .use { rows -> buildList { while (rows.moveToNext()) add(rows.getInt(0) to rows.getString(1)) } }
                // Never present an incomplete or non-existent range as the requested citation.
                if (lines.map { it.first } != (reference.firstVerse..reference.lastVerse).toList()) return@readDatabase null
                val title = db.rawQuery("SELECT title FROM edition", null).use { it.moveToFirst(); it.getString(0) }
                BiblePassage(reference, lines.joinToString("\n") { "${it.first}  ${it.second}" }, title)
            }
        }
    }

    private fun <T> readDatabase(block: (SQLiteDatabase) -> T): T =
        SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY).use(block)

    private suspend fun InputStream.copyLimited(file: File, limit: Long) {
        file.outputStream().use { output ->
            val buffer = ByteArray(65536)
            var total = 0L
            var count = read(buffer)
            while (count != -1) {
                coroutineContext.ensureActive()
                total += count
                require(total <= limit) { "El archivo JWPUB supera el tamaño permitido." }
                output.write(buffer, 0, count)
                count = read(buffer)
            }
        }
    }

    private fun verseText(html: String): String {
        val clean = html.replace(Regex("<(span|sup|a)\\b[^>]*class=[\"'][^\"']*\\b(?:tt|vl|cl|fn|m)\\b[^\"']*[\"'][^>]*>.*?</\\1>", RegexOption.DOT_MATCHES_ALL), "")
        return Html.fromHtml(clean, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
    }
}
