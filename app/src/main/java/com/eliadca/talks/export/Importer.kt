package com.eliadca.talks.export

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.eliadca.talks.core.doc.Markup
import com.eliadca.talks.core.doc.RichDoc
import com.eliadca.talks.core.doc.RichSpan
import com.eliadca.talks.core.doc.SpanType
import com.eliadca.talks.core.doc.firstLine
import com.eliadca.talks.core.doc.leadingTitle
import com.eliadca.talks.core.doc.normalized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ImportedSpeech(val title: String, val doc: RichDoc)

/**
 * Reads speeches from Markdown, plain text and Word (.docx) files. Text files are read as Markdown
 * (see [Markup]), so what an AI assistant writes keeps its headings, emphasis, lists and notes; a
 * file that starts with a `# ` title takes its name from it.
 */
class Importer(private val resolver: ContentResolver) {

    suspend fun read(uri: Uri): ImportedSpeech = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("No se pudo abrir el archivo.")
        val lower = name.lowercase()
        val fileTitle = name.substringBeforeLast('.').replace('_', ' ').trim()
        val word = lower.endsWith(".docx") || bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val doc = if (word) DocxReader.read(bytes) else Markup.parse(decodeText(bytes, name))
        val title = (if (word) null else doc.leadingTitle()) ?: fileTitle.ifBlank { doc.firstLine() }
        ImportedSpeech(title.ifBlank { "Discurso importado" }, doc)
    }

    private fun decodeText(bytes: ByteArray, name: String): String {
        if (bytes.size >= 2 && (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() || bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())) {
            return String(bytes, Charsets.UTF_16) // "Unicode" text from Windows Notepad
        }
        val head = bytes.copyOfRange(0, minOf(bytes.size, 4096))
        if (head.size >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "%PDF") {
            throw IllegalStateException("«$name» es un PDF y no se puede importar. Usa Markdown (.md), texto (.txt) o Word (.docx).")
        }
        if (head.any { it == 0.toByte() }) {
            throw IllegalStateException("«$name» no es un archivo de texto, Markdown ni Word.")
        }
        return decode(bytes)
    }

    private fun decode(bytes: ByteArray): String {
        // Honour a UTF-8 byte-order mark; fall back to Latin-1 for old files that are not valid UTF-8.
        val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes.copyOfRange(3, bytes.size)
        } else bytes
        val utf8 = body.toString(Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) body.toString(Charsets.ISO_8859_1) else utf8
    }

    private fun displayName(uri: Uri): String {
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: ""
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment ?: ""
    }
}

/** A small reader for the parts of Word documents that matter in a speech. */
internal object DocxReader {

    /**
     * Black, dark greys and near-white are just the ink of the document (often written by Google Docs
     * or a dark-mode editor): kept, they would vanish on a page of the opposite colour. Real colours stay.
     */
    fun isPlainInk(rgb: Int): Boolean {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        val grey = maxOf(r, g, b) - minOf(r, g, b) <= 0x14
        return grey && (maxOf(r, g, b) <= 0x59 || minOf(r, g, b) >= 0xE6)
    }

    private const val NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    fun read(bytes: ByteArray): RichDoc {
        var documentXml: ByteArray? = null
        var numberingXml: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                when (entry.name) {
                    "word/document.xml" -> documentXml = zis.readBytes()
                    "word/numbering.xml" -> numberingXml = zis.readBytes()
                }
            }
        }
        val doc = documentXml ?: throw IllegalStateException("El archivo no parece un documento de Word.")
        val numbered = numberingXml?.let { parseNumbering(it) } ?: emptySet()
        return parseDocument(doc, numbered)
    }

    /** Ids of lists whose first level is numbered (everything else is treated as bullets). */
    private fun parseNumbering(xml: ByteArray): Set<String> {
        val parser = Xml.newPullParser().apply { setInput(ByteArrayInputStream(xml), "UTF-8") }
        val abstractNumbered = HashSet<String>()
        val numToAbstract = HashMap<String, String>()
        var currentAbstract: String? = null
        var currentNum: String? = null
        var level: String? = null
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && parser.namespace == NS) {
                when (parser.name) {
                    "abstractNum" -> currentAbstract = parser.getAttributeValue(NS, "abstractNumId")
                    "lvl" -> level = parser.getAttributeValue(NS, "ilvl")
                    "numFmt" -> if (level == "0" && currentAbstract != null) {
                        val fmt = parser.getAttributeValue(NS, "val")
                        if (fmt != null && fmt != "bullet" && fmt != "none") abstractNumbered += currentAbstract
                    }
                    "num" -> currentNum = parser.getAttributeValue(NS, "numId")
                    "abstractNumId" -> if (currentNum != null) {
                        val a = parser.getAttributeValue(NS, "val")
                        if (a != null) numToAbstract[currentNum] = a
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && parser.namespace == NS) {
                when (parser.name) {
                    "abstractNum" -> currentAbstract = null
                    "num" -> currentNum = null
                    "lvl" -> level = null
                }
            }
            ev = parser.next()
        }
        return numToAbstract.filter { it.value in abstractNumbered }.keys
    }

    private class RunStyle {
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        var color: Int = 0
        var highlight: Int = 0
    }

    private fun parseDocument(xml: ByteArray, numberedLists: Set<String>): RichDoc {
        val parser = Xml.newPullParser().apply { setInput(ByteArrayInputStream(xml), "UTF-8") }
        val text = StringBuilder()
        val spans = ArrayList<RichSpan>()

        var paragraphs = 0
        var paraStart = 0
        var block: String? = null
        var align: String? = null
        var indent = 0
        var numId: String? = null
        var inParaProps = false
        var inRunProps = false
        var inText = false
        var run = RunStyle()
        var runStart = 0

        fun attr(name: String): String? = parser.getAttributeValue(NS, name)

        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> if (parser.namespace == NS) when (parser.name) {
                    "p" -> {
                        if (paragraphs > 0) text.append('\n')
                        paragraphs++
                        paraStart = text.length
                        block = null; align = null; indent = 0; numId = null
                    }
                    "pPr" -> inParaProps = true
                    "pStyle" -> if (inParaProps) {
                        val v = attr("val")?.lowercase() ?: ""
                        block = when {
                            v == "title" || v == "heading1" || v == "ttulo1" -> SpanType.H1
                            v == "heading2" || v == "subtitle" || v == "ttulo2" -> SpanType.H2
                            v.startsWith("heading") || v.startsWith("ttulo") -> SpanType.H3
                            v.contains("quote") || v.contains("cita") -> SpanType.QUOTE
                            v.contains("listparagraph") -> block
                            else -> block
                        }
                    }
                    "numPr" -> if (inParaProps && block == null) block = SpanType.BULLET
                    "ilvl" -> if (inParaProps) indent = (attr("val")?.toIntOrNull() ?: 0).coerceIn(0, 4)
                    "numId" -> if (inParaProps) {
                        numId = attr("val")
                        if (numId == "0") {
                            if (block == SpanType.BULLET) block = null
                        } else if (block == SpanType.BULLET && numId in numberedLists) {
                            block = SpanType.NUMBER
                        }
                    }
                    "jc" -> if (inParaProps) align = attr("val")
                    "r" -> { run = RunStyle(); runStart = text.length }
                    "rPr" -> inRunProps = true
                    "b" -> if (inRunProps) run.bold = attr("val") != "0" && attr("val") != "false"
                    "i" -> if (inRunProps) run.italic = attr("val") != "0" && attr("val") != "false"
                    "u" -> if (inRunProps) run.underline = attr("val") != "none"
                    "strike" -> if (inRunProps) run.strike = attr("val") != "0" && attr("val") != "false"
                    "color" -> if (inRunProps) {
                        val v = attr("val")
                        if (v != null && v.length == 6) v.toIntOrNull(16)?.let { if (!isPlainInk(it)) run.color = 0xFF000000.toInt() or it }
                    }
                    "highlight" -> if (inRunProps) run.highlight = Markup.HIGHLIGHT_YELLOW
                    "t" -> inText = true
                    "tab" -> if (!inRunProps && !inParaProps) text.append("    ")
                    "br", "cr" -> if (!inRunProps) text.append(' ')
                }
                XmlPullParser.TEXT -> if (inText) text.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.namespace == NS) when (parser.name) {
                    "pPr" -> inParaProps = false
                    "rPr" -> inRunProps = false
                    "t" -> inText = false
                    "r" -> if (text.length > runStart) {
                        val s = runStart
                        val e = text.length
                        if (run.bold) spans += RichSpan(SpanType.BOLD, s, e)
                        if (run.italic) spans += RichSpan(SpanType.ITALIC, s, e)
                        if (run.underline) spans += RichSpan(SpanType.UNDERLINE, s, e)
                        if (run.strike) spans += RichSpan(SpanType.STRIKE, s, e)
                        if (run.color != 0) spans += RichSpan(SpanType.COLOR, s, e, run.color)
                        if (run.highlight != 0) spans += RichSpan(SpanType.HIGHLIGHT, s, e, run.highlight)
                    }
                    "p" -> {
                        val end = text.length
                        block?.let { spans += RichSpan(it, paraStart, end) }
                        when (align) {
                            "center" -> spans += RichSpan(SpanType.ALIGN_CENTER, paraStart, end)
                            "right", "end" -> spans += RichSpan(SpanType.ALIGN_END, paraStart, end)
                        }
                        if (indent > 0 && block != null) spans += RichSpan(SpanType.INDENT, paraStart, end, indent)
                    }
                }
            }
            ev = parser.next()
        }
        return RichDoc(text.toString(), spans).normalized()
    }
}
