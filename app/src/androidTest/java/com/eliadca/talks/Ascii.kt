package com.eliadca.talks

import android.graphics.Bitmap
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Prints a coarse text rendering of the screen to the log. The CI logs are all that can be read
 * remotely, and even a crude picture shows whether panes, text and highlights are where they
 * should be. Letters mark colours: Y amber/yellow, C cyan, P indigo, R red, G green.
 */
object Ascii {
    private const val TAG = "ASCII"
    private const val RAMP = " .:-=+*#%@"

    fun shot(label: String, columns: Int = 120) {
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: run {
            Log.i(TAG, "[$label] no screenshot available")
            return
        }
        val rows = (columns * bmp.height.toFloat() / bmp.width / 2.1f).toInt().coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(bmp, columns * 3, rows * 3, true)
        Log.i(TAG, "===== $label (${bmp.width}x${bmp.height}) =====")
        for (r in 0 until rows) {
            val sb = StringBuilder(columns)
            for (c in 0 until columns) {
                var rs = 0; var gs = 0; var bs = 0
                for (dy in 0 until 3) for (dx in 0 until 3) {
                    val px = small.getPixel(c * 3 + dx, r * 3 + dy)
                    rs += (px shr 16) and 0xFF; gs += (px shr 8) and 0xFF; bs += px and 0xFF
                }
                val red = rs / 9; val green = gs / 9; val blue = bs / 9
                sb.append(glyph(red, green, blue))
            }
            Log.i(TAG, sb.toString().trimEnd())
        }
        Log.i(TAG, "===== end $label =====")
    }

    private fun glyph(r: Int, g: Int, b: Int): Char {
        val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val sat = if (max == 0) 0 else (max - min) * 100 / max
        if (sat > 45 && max > 120) {
            return when {
                r > 180 && g > 120 && b < 110 && r >= g -> 'Y'
                r > 150 && g < 110 && b < 110 -> 'R'
                g > 150 && r < 120 -> 'G'
                b > 170 && r < 120 && g > 150 -> 'C'
                b > 140 && r < 140 && g < 140 -> 'P'
                else -> RAMP[(lum * (RAMP.length - 1) / 255).coerceIn(0, RAMP.length - 1)]
            }
        }
        return RAMP[(lum * (RAMP.length - 1) / 255).coerceIn(0, RAMP.length - 1)]
    }
}
