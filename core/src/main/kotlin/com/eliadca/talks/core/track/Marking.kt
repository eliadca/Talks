package com.eliadca.talks.core.track

/** How the text the speaker says next is marked on the reader. */
enum class MarkUnit {
    /** The whole phrase (up to a comma, a full stop or a note); it stays still until it has been said. */
    PHRASE,

    /** The whole sentence: longer blocks, fewer changes. */
    SENTENCE,

    /** A few words starting at the next word, moving along word by word. */
    WORD,

    /** No marking: only the reading line, with what has been said dimmed phrase by phrase. */
    NONE,
}

/**
 * The marking chosen by the speaker. [lead] moves it ahead of the voice (positive, in words) or
 * keeps it behind (negative); [maxWords] is the longest stretch marked in [MarkUnit.WORD] mode.
 */
data class Marking(
    val unit: MarkUnit = MarkUnit.PHRASE,
    val lead: Int = 0,
    val maxWords: Int = 9,
) {
    companion object {
        const val MIN_LEAD = -3
        const val MAX_LEAD = 3
    }
}

/**
 * What to show for a speaker at a given place, in script tokens: [from, until) is marked as what to
 * say next, everything before [dimUntil] has been said, and [focus] is the word whose line should
 * sit at the reading line.
 */
data class Mark(val from: Int, val until: Int, val dimUntil: Int, val focus: Int)

/** A stretch of the text, [start, end) in characters. */
data class CharSpan(val start: Int, val end: Int)
