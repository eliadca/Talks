package com.eliadca.talks.core.doc

/**
 * Talks' Markdown. It is what the bundled samples are written in, what imported `.md` files and
 * pasted or shared text are read as, and what [toMarkdown] writes.
 *
 * Every line is a paragraph; an empty line is an empty paragraph.
 *
 * Blocks: `# `, `## `, `### ` headings (deeper levels count as `###`); `- `, `* `, `+ ` or `• `
 * bullets; `1. ` or `1) ` numbered; `- [ ] ` / `- [x] ` checklist; `> ` quote; a line with only
 * `---`, `***` or `___` is a centred separator. Two spaces (or a tab) before a list item per level
 * of indentation.
 *
 * Inline: `**bold**` or `__bold__`, `*italic*` or `_italic_`, `~~strike~~`, `++underline++`,
 * `==highlight==`. A marker opens only before a non-space and when it is closed later on the same
 * line; underscores do not open or close inside a word. A backslash escapes the next character.
 * Text in `[square brackets]` is left as is: it is a note for the speaker, never read aloud.
 *
 * Markdown written for other apps (by an AI assistant, for instance) imports cleanly too: code
 * fences and inline code marks are dropped, links keep their text and images are left out.
 */
object Markup {

    const val HIGHLIGHT_YELLOW = 0x66FFD54F

    /** What a separator line (`---`) becomes: a centred row of dots. */
    const val SEPARATOR = "· · ·"

    private val heading = Regex("^(#{1,6})[ \\t]+(.*?)(?:[ \\t]+#+)?[ \\t]*$")
    private val emptyHeading = Regex("^(#{1,6})[ \\t]*$")
    private val checklist = Regex("^[-*+•][ \\t]+\\[([ xX])\\](?:[ \\t]+|$)")
    private val bullet = Regex("^[-*+•][ \\t]+")
    private val numbered = Regex("^(\\d{1,3})[.)][ \\t]+")
    private val quote = Regex("^(?:>[ \\t]?)+")
    private val separator = Regex("^([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$")
    private val fence = Regex("^[ \\t]*(```|~~~)")
    private val link = Regex("(?<!\\\\)(!?)\\[([^\\]\\n]*)\\]\\(([^()\\s]+)(?:[ \\t]+\"[^\"]*\")?\\)")
    private val imageFile = Regex("(?i)\\.(png|jpe?g|gif|webp|svg|bmp)$")

    fun parse(source: String): RichDoc {
        val text = StringBuilder()
        val spans = ArrayList<RichSpan>()
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            // Code fences only wrap the text (assistants like to put whole answers in one).
            .filterNot { fence.containsMatchIn(it) }
        for ((li, raw) in lines.withIndex()) {
            if (li > 0) text.append('\n')
            val leading = raw.takeWhile { it == ' ' || it == '\t' }
            val rest = raw.substring(leading.length)
            var line: String
            var block: String? = null
            var arg = 0
            var indent = 0

            val h = heading.find(rest) ?: emptyHeading.find(rest)
            val c = checklist.find(rest)
            when {
                separator.matches(rest) -> {
                    val s = text.length
                    text.append(SEPARATOR)
                    spans += RichSpan(SpanType.ALIGN_CENTER, s, text.length)
                    continue
                }
                h != null -> {
                    block = when (h.groupValues[1].length) { 1 -> SpanType.H1; 2 -> SpanType.H2; else -> SpanType.H3 }
                    line = h.groupValues.getOrElse(2) { "" }
                }
                c != null -> {
                    block = SpanType.CHECK
                    arg = if (c.groupValues[1] == " ") 0 else 1
                    line = rest.substring(c.value.length)
                    indent = indentOf(leading)
                }
                bullet.containsMatchIn(rest) -> {
                    block = SpanType.BULLET
                    line = rest.substring(bullet.find(rest)!!.value.length)
                    indent = indentOf(leading)
                }
                numbered.containsMatchIn(rest) -> {
                    block = SpanType.NUMBER
                    line = rest.substring(numbered.find(rest)!!.value.length)
                    indent = indentOf(leading)
                }
                rest.startsWith(">") -> {
                    block = SpanType.QUOTE
                    line = rest.substring(quote.find(rest)!!.value.length)
                }
                else -> line = raw
            }

            val start = text.length
            parseInline(stripLinks(line), text, spans)
            val end = text.length
            if (block != null) spans += RichSpan(block, start, end, arg)
            if (indent > 0) spans += RichSpan(SpanType.INDENT, start, end, indent)
        }
        return RichDoc(text.toString(), spans).normalized()
    }

    /**
     * [source] read as Markdown when that changes anything (formatting, list markers, separators),
     * or null when it is plain text, which is then best pasted as it is.
     */
    fun parseIfMarkdown(source: String): RichDoc? {
        val doc = parse(source)
        val plain = source.replace("\r\n", "\n").replace('\r', '\n')
        return if (doc.spans.isEmpty() && doc.text == plain) null else doc
    }

    private fun indentOf(leading: String): Int =
        (leading.count { it == ' ' } / 2 + leading.count { it == '\t' }).coerceAtMost(4)

    /** Links keep their text; images (pictures cannot be spoken) are left out. */
    private fun stripLinks(line: String): String {
        if ('[' !in line) return line
        return link.replace(line) { m ->
            val target = m.groupValues[3]
            val url = target.contains("://") || target.startsWith("www.") || target.startsWith("mailto:") ||
                target.startsWith("#") || target.startsWith("/")
            when {
                m.groupValues[1] == "!" -> if (url || imageFile.containsMatchIn(target)) "" else m.value
                url -> m.groupValues[2]
                else -> m.value
            }
        }
    }

    /** Longest first, so that `**` wins over `*`. A backtick only drops inline code marks. */
    private val markers = listOf("**", "__", "~~", "++", "==", "*", "_", "`")

    private fun markerAt(line: String, i: Int): String? = markers.firstOrNull { line.startsWith(it, i) }

    private fun isUnderscore(marker: String) = marker[0] == '_'

    private fun canOpen(line: String, i: Int, marker: String): Boolean {
        val after = i + marker.length
        if (after >= line.length || line[after].isWhitespace()) return false
        if (isUnderscore(marker) && i > 0 && line[i - 1].isLetterOrDigit()) return false
        return closerAfter(line, marker, after)
    }

    private fun canClose(line: String, i: Int, marker: String): Boolean {
        if (!isUnderscore(marker)) return true
        val after = i + marker.length
        return after >= line.length || !line[after].isLetterOrDigit()
    }

    /**
     * Whether a run opened just before [from] is closed by [marker] later on the line, after at
     * least one character, reading the line the way [parseInline] does (escapes included).
     */
    private fun closerAfter(line: String, marker: String, from: Int): Boolean {
        var j = from
        while (j < line.length) {
            if (line[j] == '\\') { j += 2; continue }
            val m = markerAt(line, j)
            if (m == null) { j++; continue }
            if (m == marker && j > from && canClose(line, j, m)) return true
            j += m.length
        }
        return false
    }

    private fun parseInline(line: String, out: StringBuilder, spans: MutableList<RichSpan>) {
        val open = HashMap<String, Int>()
        var i = 0
        val n = line.length
        while (i < n) {
            val c = line[i]
            if (c == '\\' && i + 1 < n) {
                out.append(line[i + 1]); i += 2; continue
            }
            val marker = markerAt(line, i)
            if (marker == null) {
                out.append(c); i++; continue
            }
            val startOffset = open[marker]
            if (startOffset != null && canClose(line, i, marker)) {
                if (out.length > startOffset) styleFor(marker, startOffset, out.length)?.let { spans += it }
                open.remove(marker)
            } else if (startOffset == null && canOpen(line, i, marker)) {
                open[marker] = out.length
            } else {
                out.append(marker)
            }
            i += marker.length
        }
    }

    private fun styleFor(marker: String, start: Int, end: Int): RichSpan? = when (marker) {
        "**", "__" -> RichSpan(SpanType.BOLD, start, end)
        "*", "_" -> RichSpan(SpanType.ITALIC, start, end)
        "~~" -> RichSpan(SpanType.STRIKE, start, end)
        "++" -> RichSpan(SpanType.UNDERLINE, start, end)
        "==" -> RichSpan(SpanType.HIGHLIGHT, start, end, HIGHLIGHT_YELLOW)
        else -> null
    }
}
