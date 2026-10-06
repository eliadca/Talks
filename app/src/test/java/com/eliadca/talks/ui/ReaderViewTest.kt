package com.eliadca.talks.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.eliadca.talks.core.doc.Markup
import com.eliadca.talks.core.sample.SampleContent
import com.eliadca.talks.core.track.CharSpan
import com.eliadca.talks.data.ReaderTheme
import com.eliadca.talks.ui.talks.ReaderConfig
import com.eliadca.talks.ui.talks.ReaderPalette
import com.eliadca.talks.ui.talks.ReaderView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderViewTest {

    private fun reader(width: Int = 1600, height: Int = 900): ReaderView {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return ReaderView(ctx).apply {
            setDocument(SampleContent.practice)
            configure(ReaderConfig(40f, 1.35f, false, ReaderPalette.of(ReaderTheme.NIGHT), 0.35f, true))
            measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            layout(0, 0, width, height)
        }
    }

    @Test fun laysOutAndDrawsTheWholeSpeechWithoutCrashing() {
        val r = reader()
        val child = r.getChildAt(0)
        assertTrue("the content is taller than the screen", child.height > r.height)
        val bmp = Bitmap.createBitmap(r.width, r.height, Bitmap.Config.ARGB_8888)
        r.draw(Canvas(bmp))
    }

    @Test fun scrollsToKeepTheCurrentLineAtTheAnchor() {
        val r = reader()
        val text = SampleContent.practice.text
        val offset = text.indexOf("Hace algunos años")
        assertTrue(offset > 0)
        r.setProgress(offset, offset, offset + 40, jump = true)
        r.resumeFollow(animated = false)
        assertTrue("scrolled down to the paragraph", r.scrollY > 0)
        // Drawing the highlighted state must work too.
        val bmp = Bitmap.createBitmap(r.width, r.height, Bitmap.Config.ARGB_8888)
        r.draw(Canvas(bmp))
    }

    @Test fun progressReportedFromABackgroundThreadDoesNotCrash() {
        val r = reader()
        val offset = SampleContent.practice.text.indexOf("Hace algunos años")
        var failure: Throwable? = null
        val worker = Thread {
            try {
                r.resumeFollow()
                r.setProgress(offset, offset, offset + 40)
            } catch (t: Throwable) {
                failure = t
            }
        }
        worker.start()
        worker.join()
        assertEquals(null, failure)
    }

    @Test fun theStartOfTheSpeechNeedsNoScroll() {
        val r = reader()
        r.setProgress(0, 0, 20, jump = true)
        r.resumeFollow(animated = false)
        assertEquals(0, r.scrollY)
    }

    @Test fun listsHeadingsAndNotesRenderInTheReader() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val r = ReaderView(ctx).apply {
            setDocument(Markup.parse("# Título\n- uno\n- dos\n1. a\n2. b\n- [x] hecho\n> cita\nTexto [pausa] normal"))
            configure(ReaderConfig(30f, 1.3f, true, ReaderPalette.of(ReaderTheme.DAY), 0.3f, false))
            measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            layout(0, 0, 1200, 800)
        }
        val bmp = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        r.draw(Canvas(bmp))
        assertFalse(r.getChildAt(0).height == 0)
    }

    @Test fun userScrollingSuspendsFollowing() {
        val r = reader()
        var scrolled = false
        r.listener = object : ReaderView.Listener {
            override fun onLongPress(offset: Int) {}
            override fun onTap() {}
            override fun onUserScroll() { scrolled = true }
            override fun onFollowResumed() {}
        }
        r.scrollTo(0, 400) // not programmatic
        assertTrue(scrolled)
        assertFalse(r.autoFollow)
        r.resumeFollow(animated = false)
        assertTrue(r.autoFollow)
    }

    @Test fun repeatingTheSameProgressLetsTheGlideFinish() {
        val r = reader()
        val offset = SampleContent.practice.text.indexOf("Hace algunos años")
        val marks = listOf(CharSpan(offset, offset + 40))
        r.setProgress(offset, offset, marks) // a glide starts
        // The session republishes every clock tick; the glide must not restart or stop short.
        repeat(20) {
            r.setProgress(offset, offset, marks)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30))
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        val jumped = reader().apply { setProgress(offset, offset, marks, jump = true) }
        assertTrue(jumped.scrollY > 0)
        assertEquals(jumped.scrollY, r.scrollY)
    }

    @Test fun onlyTheMarkedWordsGetTheAccentAndTheReadingLineCanShowAlone() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val doc = Markup.parse("Uno dos tres [una nota] cuatro cinco.\nSeis siete ocho.")
        val text = doc.text
        val palette = ReaderPalette.of(ReaderTheme.NIGHT)
        fun accentPixels(marks: List<CharSpan>, guide: Boolean): Int {
            val r = ReaderView(ctx).apply {
                setDocument(doc)
                configure(ReaderConfig(40f, 1.35f, false, palette, 0.35f, true, guide = guide))
                // Twice, like a real screen: the room above the text depends on the reader's own
                // height, which it only knows after the first layout (it then asks for another).
                repeat(2) {
                    requestLayout()
                    measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                    layout(0, 0, 1200, 800)
                }
            }
            r.setProgress(0, 0, marks, jump = true)
            val bmp = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
            r.draw(Canvas(bmp))
            val px = IntArray(1200 * 800)
            bmp.getPixels(px, 0, 1200, 0, 0, 1200, 800)
            // The accent itself, give or take the rounding of anti-aliased edges.
            fun near(a: Int, b: Int) = kotlin.math.abs(a - b) <= 6
            val accent = palette.accent
            return px.count {
                Color.alpha(it) == 255 && near(Color.red(it), Color.red(accent)) &&
                    near(Color.green(it), Color.green(accent)) && near(Color.blue(it), Color.blue(accent))
            }
        }
        val marks = listOf(CharSpan(0, text.indexOf(" [")), CharSpan(text.indexOf("cuatro"), text.indexOf("cinco") + 5))
        assertTrue("marked words are underlined", accentPixels(marks, guide = false) > 0)
        assertEquals("nothing marked, nothing underlined", 0, accentPixels(emptyList(), guide = false))
        assertTrue("the reading line shows without marks", accentPixels(emptyList(), guide = true) > 0)
    }
}
