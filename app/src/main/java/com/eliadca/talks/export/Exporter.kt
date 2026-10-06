package com.eliadca.talks.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.eliadca.talks.core.doc.RichDoc
import com.eliadca.talks.core.doc.RichSpan
import com.eliadca.talks.core.doc.SpanType
import com.eliadca.talks.core.doc.toPlainText
import com.eliadca.talks.editor.EditorStyle
import com.eliadca.talks.editor.SpannableCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Turns a speech into something to share: plain text or a printable PDF. */
class Exporter(private val context: Context) {

    /** A chooser that sends the speech as plain text. */
    fun textIntent(title: String, doc: RichDoc): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, (if (title.isNotBlank()) title + "\n\n" else "") + doc.toPlainText())
        }
        return Intent.createChooser(send, "Compartir discurso")
    }

    /** Writes a PDF of the speech to the cache and returns a chooser that shares it. */
    suspend fun pdfIntent(title: String, doc: RichDoc): Intent {
        val file = withContext(Dispatchers.Default) { writePdf(title, doc) }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Compartir PDF")
    }

    private fun writePdf(title: String, doc: RichDoc): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Old exports are of no use once shared.
        dir.listFiles()?.forEach { if (it.isFile && it.lastModified() < System.currentTimeMillis() - 24 * 3600 * 1000L) it.delete() }
        val file = File(dir, safeName(title) + ".pdf")

        val pdf = PdfDocument()
        try {
            render(pdf, withTitle(title, doc))
            file.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return file
    }

    /** Puts the title in front of the text as a big heading. */
    private fun withTitle(title: String, doc: RichDoc): RichDoc {
        if (title.isBlank()) return doc
        val shift = title.length + 1
        val shifted = doc.spans.map { it.copy(start = it.start + shift, end = it.end + shift) }
        return RichDoc(title + "\n" + doc.text, listOf(RichSpan(SpanType.H1, 0, title.length)) + shifted)
    }

    private fun render(pdf: PdfDocument, doc: RichDoc) {
        val pageW = 595
        val pageH = 842
        val margin = 56
        val contentW = pageW - 2 * margin
        val contentH = pageH - 2 * margin

        val style = EditorStyle(density = 1f, accent = 0xFF3F4FD8.toInt(), muted = 0xFF7A7C88.toInt())
        val text = SpannableCodec.toSpannable(doc, style, withMarker = false)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.SERIF
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.3f)
            .setIncludePad(false)
            .build()

        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF888888.toInt()
            textSize = 9f
            textAlign = Paint.Align.CENTER
        }

        var pageNumber = 1
        var y = 0
        do {
            var end = y + contentH
            if (end < layout.height) {
                // Break between lines, never through one.
                val line = layout.getLineForVertical(end)
                end = if (layout.getLineBottom(line) <= end) layout.getLineBottom(line) else layout.getLineTop(line)
                if (end <= y) end = y + contentH
            } else {
                end = layout.height
            }
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create())
            val canvas = page.canvas
            canvas.save()
            canvas.clipRect(margin.toFloat(), margin.toFloat(), (margin + contentW).toFloat(), (margin + (end - y)).toFloat())
            canvas.translate(margin.toFloat(), (margin - y).toFloat())
            layout.draw(canvas)
            canvas.restore()
            canvas.drawText(pageNumber.toString(), pageW / 2f, pageH - 28f, footer)
            pdf.finishPage(page)
            pageNumber++
            y = end
        } while (y < layout.height)
    }

    private fun safeName(title: String): String {
        val base = title.trim().ifEmpty { "Discurso" }
            .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(60)
        return base.ifEmpty { "Discurso" }
    }
}
