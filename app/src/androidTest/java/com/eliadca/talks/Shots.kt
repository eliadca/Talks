package com.eliadca.talks

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream

/**
 * Real screenshots for the CI log, which is all that can be read remotely: a JPEG of the screen,
 * base64-encoded and split into numbered chunks under the log tag SHOT.
 */
object Shots {
    private const val TAG = "SHOT"
    private const val CHUNK = 3000

    fun take(label: String, longSide: Int = 1280, quality: Int = 70) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bmp = instrumentation.uiAutomation.takeScreenshot() ?: run {
            Log.i(TAG, "$label 0/0 none")
            return
        }
        val scale = minOf(1f, longSide.toFloat() / maxOf(bmp.width, bmp.height))
        val img = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        } else {
            bmp
        }
        val out = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        val n = (b64.length + CHUNK - 1) / CHUNK
        for (i in 0 until n) {
            Log.i(TAG, "$label ${i + 1}/$n ${b64.substring(i * CHUNK, minOf(b64.length, (i + 1) * CHUNK))}")
        }
    }
}
