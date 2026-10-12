package com.eliadca.talks.notes

import android.app.Application
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eliadca.talks.core.ink.*
import com.eliadca.talks.ui.notes.NoteArtworkExport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class NoteArtworkRuntimeTest {
    @Test fun pdfContainsTheImageAndPaginatesTheWholeSheet() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val bitmap=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.GREEN)}
        val bytes=ByteArrayOutputStream().apply {bitmap.compress(Bitmap.CompressFormat.PNG,100,this)}.toByteArray();bitmap.recycle()
        val doc=InkDocument(height=3000f,images=listOf(NoteImage(1,Base64.encodeToString(bytes,Base64.NO_WRAP),x=50f,y=50f,width=300f,height=150f)))
        val file=File(context.cacheDir,"note-artwork-test.pdf")
        try {
            file.outputStream().use {NoteArtworkExport.write(context,doc,"Una nota exportada completa","pdf",it)}
            assertTrue(file.length()>1000)
            val descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
            val pdf=PdfRenderer(descriptor)
            try {
                assertEquals(3,pdf.pageCount)
                val page=pdf.openPage(0);val rendered=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                try {page.render(rendered,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);assertEquals(Color.GREEN,rendered.getPixel(72,76))}
                finally {rendered.recycle();page.close()}
            } finally {pdf.close()}
        } finally {file.delete()}
    }
}
