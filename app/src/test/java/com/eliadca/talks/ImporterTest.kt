package com.eliadca.talks

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.eliadca.talks.core.doc.BlockType
import com.eliadca.talks.core.doc.SpanType
import com.eliadca.talks.core.doc.paragraphs
import com.eliadca.talks.export.Importer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImporterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val importer = Importer(context.contentResolver)

    private fun temp(name: String, bytes: ByteArray): Uri {
        val dir = File(context.cacheDir, "import-test").apply { mkdirs() }
        val f = File(dir, name)
        f.writeBytes(bytes)
        return Uri.fromFile(f)
    }

    @Test fun plainTextKeepsLinesAndAccents() = runBlocking {
        val r = importer.read(temp("Mi_discurso.txt", "Línea uno\r\nLínea dos ñandú".toByteArray()))
        assertEquals("Mi discurso", r.title)
        assertEquals("Línea uno\nLínea dos ñandú", r.doc.text)
    }

    @Test fun oldLatin1TextIsReadCorrectly() = runBlocking {
        val r = importer.read(temp("viejo.txt", "Canción".toByteArray(Charsets.ISO_8859_1)))
        assertEquals("Canción", r.doc.text)
    }

    @Test fun markdownBecomesFormattedText() = runBlocking {
        val r = importer.read(temp("charla.md", "# Título\nTexto **fuerte**\n- punto".toByteArray()))
        val p = r.doc.paragraphs()
        assertEquals(listOf(BlockType.H1, BlockType.NORMAL, BlockType.BULLET), p.map { it.block })
        assertTrue(r.doc.spans.any { it.type == SpanType.BOLD })
    }

    @Test fun textFilesAreReadAsMarkdownAndNamedByTheirTitle() = runBlocking {
        val r = importer.read(temp("respuesta.txt", "# Mi charla\n**Hola** a todos [Pausa]\n- uno".toByteArray()))
        assertEquals("Mi charla", r.title)
        assertEquals("Mi charla\nHola a todos [Pausa]\nuno", r.doc.text)
        assertEquals(listOf(BlockType.H1, BlockType.NORMAL, BlockType.BULLET), r.doc.paragraphs().map { it.block })
    }

    @Test fun anAssistantsAnswerImportsCleanly() = runBlocking {
        val answer = "```markdown\n# Discurso\n#### Idea\n__Clave__ y [enlace](https://x.com)\n```\n"
        val r = importer.read(temp("discurso.md", answer.toByteArray()))
        assertEquals("Discurso", r.title)
        assertEquals("Discurso\nIdea\nClave y enlace\n", r.doc.text)
        assertTrue(r.doc.spans.any { it.type == SpanType.BOLD })
    }

    @Test fun windowsUnicodeTextIsRead() = runBlocking {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Hola ñandú".toByteArray(Charsets.UTF_16LE)
        assertEquals("Hola ñandú", importer.read(temp("unicode.txt", bytes)).doc.text)
    }

    @Test fun pdfAndBinaryFilesAreRefusedWithAClearMessage() {
        val pdf = runCatching { runBlocking { importer.read(temp("charla.pdf", "%PDF-1.7 ...".toByteArray())) } }
        assertTrue(pdf.exceptionOrNull()?.message.orEmpty().contains("PDF"))
        val binary = runCatching { runBlocking { importer.read(temp("foto.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0, 0, 1, 2))) } }
        assertTrue(binary.exceptionOrNull()?.message.orEmpty().contains("no es un archivo de texto"))
    }

    @Test fun plainInkColoursFromWordAreDroppedButRealColoursStay() = runBlocking {
        val w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        fun run(color: String, text: String) = "<w:r><w:rPr><w:color w:val=\"$color\"/></w:rPr><w:t xml:space=\"preserve\">$text</w:t></w:r>"
        val document = """<?xml version="1.0" encoding="UTF-8"?>
<w:document xmlns:w="$w"><w:body><w:p>${run("434343", "gris ")}${run("F2F2F2", "blanco ")}${run("E53935", "rojo")}</w:p></w:body></w:document>"""
        val bytes = java.io.ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("word/document.xml")); z.write(document.toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        val r = importer.read(temp("colores.docx", bytes))
        val colours = r.doc.spans.filter { it.type == SpanType.COLOR }
        assertEquals(1, colours.size)
        assertEquals("rojo", r.doc.text.substring(colours[0].start, colours[0].end))
    }

    @Test fun wordDocumentKeepsHeadingsStylesAndLists() = runBlocking {
        val w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val document = """<?xml version="1.0" encoding="UTF-8"?>
<w:document xmlns:w="$w"><w:body>
<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Gran título</w:t></w:r></w:p>
<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>negrita</w:t></w:r><w:r><w:rPr><w:i/><w:color w:val="E53935"/></w:rPr><w:t xml:space="preserve"> cursiva roja</w:t></w:r></w:p>
<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t>centrado</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>primero</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>segundo</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="2"/></w:numPr></w:pPr><w:r><w:t>viñeta</w:t></w:r></w:p>
</w:body></w:document>"""
        val numbering = """<?xml version="1.0" encoding="UTF-8"?>
<w:numbering xmlns:w="$w">
<w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/></w:lvl></w:abstractNum>
<w:abstractNum w:abstractNumId="1"><w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/></w:lvl></w:abstractNum>
<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
<w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>
</w:numbering>"""
        val bytes = java.io.ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("word/document.xml")); z.write(document.toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("word/numbering.xml")); z.write(numbering.toByteArray()); z.closeEntry()
            }
        }.toByteArray()

        val r = importer.read(temp("Discurso Word.docx", bytes))
        assertEquals("Discurso Word", r.title)
        assertEquals("Gran título\nnegrita cursiva roja\ncentrado\nprimero\nsegundo\nviñeta", r.doc.text)
        val p = r.doc.paragraphs()
        assertEquals(
            listOf(BlockType.H1, BlockType.NORMAL, BlockType.NORMAL, BlockType.NUMBER, BlockType.NUMBER, BlockType.BULLET),
            p.map { it.block },
        )
        assertEquals(com.eliadca.talks.core.doc.Align.CENTER, p[2].align)
        val bold = r.doc.spans.single { it.type == SpanType.BOLD }
        assertEquals("negrita", r.doc.text.substring(bold.start, bold.end))
        val italic = r.doc.spans.single { it.type == SpanType.ITALIC }
        assertEquals(" cursiva roja", r.doc.text.substring(italic.start, italic.end))
        assertTrue(r.doc.spans.any { it.type == SpanType.COLOR && it.arg == 0xFFE53935.toInt() })
    }

    @Test fun aNonWordZipGivesAReadableError() = runBlocking {
        val bytes = java.io.ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("otra/cosa.txt")); z.write(1); z.closeEntry() }
        }.toByteArray()
        try {
            importer.read(temp("falso.docx", bytes))
            throw AssertionError("expected an error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Word"))
        }
    }
}
