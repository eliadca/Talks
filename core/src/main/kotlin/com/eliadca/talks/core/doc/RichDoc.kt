package com.eliadca.talks.core.doc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One formatting run over [start, end) of the document text.
 *
 * Inline types ([SpanType.BOLD] ... [SpanType.STAGE]) may start and end anywhere. Paragraph types
 * (headings, lists, quote, alignment, indent) cover a whole paragraph, excluding its newline;
 * an empty paragraph is stored as a zero-length span.
 */
@Serializable
data class RichSpan(
    @SerialName("t") val type: String,
    @SerialName("s") val start: Int,
    @SerialName("e") val end: Int,
    /** Colour (ARGB) for [SpanType.COLOR]/[SpanType.HIGHLIGHT]; percent for [SpanType.SIZE]; 0/1 for [SpanType.CHECK]; level for [SpanType.INDENT]. */
    @SerialName("a") val arg: Int = 0,
)

/** The stored form of a speech: plain text plus formatting runs. */
@Serializable
data class RichDoc(
    val text: String = "",
    val spans: List<RichSpan> = emptyList(),
    @SerialName("v") val version: Int = 1,
) {
    fun toJson(): String = codec.encodeToString(serializer(), this)

    companion object {
        private val codec = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        val EMPTY = RichDoc()

        /** Parses stored JSON; returns an empty document if the data is damaged. */
        fun fromJson(json: String): RichDoc = try {
            codec.decodeFromString(serializer(), json)
        } catch (_: Exception) {
            EMPTY
        }
    }
}

object SpanType {
    // Inline
    const val BOLD = "b"
    const val ITALIC = "i"
    const val UNDERLINE = "u"
    const val STRIKE = "s"
    const val COLOR = "fg"
    const val HIGHLIGHT = "bg"
    const val SIZE = "sz"
    /** A note to the speaker that is shown but never read aloud. */
    const val STAGE = "st"

    // Paragraph
    const val H1 = "h1"
    const val H2 = "h2"
    const val H3 = "h3"
    const val BULLET = "ul"
    const val NUMBER = "ol"
    const val CHECK = "ck"
    const val QUOTE = "qt"
    const val ALIGN_CENTER = "ac"
    const val ALIGN_END = "ae"
    const val ALIGN_JUSTIFY = "aj"
    const val INDENT = "in"

    val inline = setOf(BOLD, ITALIC, UNDERLINE, STRIKE, COLOR, HIGHLIGHT, SIZE, STAGE)
    val block = setOf(H1, H2, H3, BULLET, NUMBER, CHECK, QUOTE)
    val alignment = setOf(ALIGN_CENTER, ALIGN_END, ALIGN_JUSTIFY)
    val paragraph = block + alignment + INDENT

    fun isParagraph(type: String) = type in paragraph
}

enum class BlockType { NORMAL, H1, H2, H3, BULLET, NUMBER, CHECK, QUOTE;
    val isHeading get() = this == H1 || this == H2 || this == H3
    val isList get() = this == BULLET || this == NUMBER || this == CHECK
}

enum class Align { START, CENTER, END, JUSTIFY }

/** A paragraph of a document with its paragraph-level formatting resolved. */
data class Paragraph(
    /** Offset of the first character. */
    val start: Int,
    /** Offset just past the last character, excluding the newline. */
    val end: Int,
    val block: BlockType,
    val align: Align,
    val indent: Int,
    val checked: Boolean,
) {
    val isEmpty get() = start == end
}

data class OutlineEntry(val level: Int, val title: String, val offset: Int)
