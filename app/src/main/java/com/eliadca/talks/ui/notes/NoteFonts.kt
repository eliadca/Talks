package com.eliadca.talks.ui.notes

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import com.eliadca.talks.R
import com.eliadca.talks.core.notes.NoteFont

/** Bundled OFL fonts: editing, previews and exports work without a network connection. */
internal object NoteFonts {
    fun label(font: NoteFont): String = when (font) {
        NoteFont.SYSTEM -> "Sistema"; NoteFont.INTER -> "Inter"; NoteFont.LORA -> "Lora"
        NoteFont.NUNITO -> "Nunito"; NoteFont.MONO -> "Roboto Mono"
        NoteFont.HAND -> "Patrick Hand"; NoteFont.CAVEAT -> "Caveat"
    }
    private fun resource(font: NoteFont): Int? = when (font) {
        NoteFont.SYSTEM -> null; NoteFont.INTER -> R.font.note_inter; NoteFont.LORA -> R.font.note_lora
        NoteFont.NUNITO -> R.font.note_nunito; NoteFont.MONO -> R.font.note_mono
        NoteFont.HAND -> R.font.note_hand; NoteFont.CAVEAT -> R.font.note_caveat
    }
    private val families = NoteFont.entries.associateWith { font ->
        resource(font)?.let { FontFamily(Font(it)) } ?: FontFamily.SansSerif
    }
    fun family(font: NoteFont) = families.getValue(font)
    private val native = java.util.concurrent.ConcurrentHashMap<NoteFont, Typeface>()
    fun typeface(context: Context?, font: NoteFont): Typeface = if (font == NoteFont.SYSTEM || context == null) Typeface.DEFAULT
        else native.getOrPut(font) { ResourcesCompat.getFont(context, resource(font)!!) ?: Typeface.DEFAULT }
    fun span(context: Context?, font: NoteFont, bold: Boolean, italic: Boolean, tracking: Float, size: Float) =
        object : MetricAffectingSpan() {
            private fun update(paint: TextPaint) {
                paint.typeface = Typeface.create(typeface(context, font),
                    if (bold && italic) Typeface.BOLD_ITALIC else if (bold) Typeface.BOLD else if (italic) Typeface.ITALIC else Typeface.NORMAL)
                paint.letterSpacing = tracking / size.coerceAtLeast(1f)
            }
            override fun updateDrawState(tp: TextPaint) = update(tp)
            override fun updateMeasureState(tp: TextPaint) = update(tp)
        }
    fun svgFamily(font: NoteFont) = if (font == NoteFont.SYSTEM) "sans-serif" else label(font)
    fun bytes(context: Context, font: NoteFont): ByteArray? = resource(font)?.let { context.resources.openRawResource(it).use { stream -> stream.readBytes() } }
}
