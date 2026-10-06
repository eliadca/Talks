package com.eliadca.talks.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.eliadca.talks.core.doc.Markup
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorDrawingTest {

    @Test fun noteBackgroundsStayInsideTheVisiblePage() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val style = EditorStyle(1f, 0xFF3F4FD8.toInt(), 0xFF888888.toInt(), noteColor = 0xFFFF0000.toInt())
        val edit = RichEditText(ctx, style)
        edit.loadDocument(Markup.parse((1..80).joinToString("\n") { "Línea $it [nota $it] y algo más de texto" }))
        val w = 600
        val h = 300
        edit.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        edit.layout(0, 0, w, h)
        edit.scrollTo(0, 1200)
        assertTrue(edit.scrollY > 0)

        // Draw the page with room above and below it: whatever lands there would cover the
        // toolbar or the status line in the app.
        val bmp = Bitmap.createBitmap(w, h * 3, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.translate(0f, (h - edit.scrollY).toFloat())
        edit.draw(canvas)

        fun band(from: Int, to: Int) = IntArray(w * (to - from)).also { bmp.getPixels(it, 0, w, 0, from, w, to - from) }
        assertTrue("nothing drawn above the page", band(0, h).all { Color.alpha(it) == 0 })
        assertTrue("nothing drawn below the page", band(2 * h, 3 * h).all { Color.alpha(it) == 0 })
        assertTrue("the notes in view are drawn", band(h, 2 * h).any { Color.red(it) > 200 && Color.green(it) < 60 && Color.alpha(it) > 0 })
    }
}
