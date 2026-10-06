package com.eliadca.talks.core.doc

/**
 * The document written in Talks' Markdown (see [Markup]), so that `Markup.parse(doc.toMarkdown())`
 * gives the same document back: headings, lists, checklists, quotes, separators, bold, italic,
 * underline, strike and highlight. Colours, sizes and other alignments have no Markdown and are left
 * out; text marked as a note is written in brackets, the way notes are typed.
 *
 * A document without any formatting is written exactly as it is, so Markdown typed by hand (such as
 * the template for an AI assistant) is copied untouched.
 */
fun RichDoc.toMarkdown(): String {
    if (spans.isEmpty()) return text
    return MarkdownWriter(this).write()
}

/** [toMarkdown] with [title] as a `# ` heading on top, unless the text already starts with one. */
fun RichDoc.toMarkdown(title: String): String {
    val body = toMarkdown()
    val first = body.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart()
    if (title.isBlank() || first?.startsWith("# ") == true) return body
    return "# " + MarkdownWriter.escapeText(title.trim()) + "\n\n" + body
}

private class MarkdownWriter(private val doc: RichDoc) {

    private val text = doc.text
    private val inline = doc.spans.filter { it.type in MARKERS && it.end > it.start }

    fun write(): String {
        val sb = StringBuilder()
        val numbers = IntArray(MAX_LEVELS)
        for ((i, p) in doc.paragraphs().withIndex()) {
            if (i > 0) sb.append('\n')
            val content = text.substring(p.start, p.end)
            if (p.block == BlockType.NORMAL && p.align == Align.CENTER && content == Markup.SEPARATOR) {
                sb.append("---")
                numbers.fill(0)
                continue
            }
            val level = p.indent.coerceIn(0, MAX_LEVELS - 1)
            when {
                p.block == BlockType.NUMBER -> {
                    numbers[level]++
                    for (k in level + 1 until MAX_LEVELS) numbers[k] = 0
                }
                p.block.isList -> for (k in level until MAX_LEVELS) numbers[k] = 0
                else -> numbers.fill(0)
            }
            if (p.block.isList) repeat(level) { sb.append("  ") }
            sb.append(
                when (p.block) {
                    BlockType.H1 -> "# "
                    BlockType.H2 -> "## "
                    BlockType.H3 -> "### "
                    BlockType.BULLET -> "- "
                    BlockType.NUMBER -> "${numbers[level]}. "
                    BlockType.CHECK -> if (p.checked) "- [x] " else "- [ ] "
                    BlockType.QUOTE -> "> "
                    BlockType.NORMAL -> ""
                },
            )
            writeContent(sb, p)
        }
        return sb.toString()
    }

    /** One paragraph's text with its inline markers, escaped so that it reads back the same. */
    private fun writeContent(sb: StringBuilder, p: Paragraph) {
        val start = p.start
        val len = p.end - p.start
        // Markers to write before character k (k == len: at the end), closing ones first.
        val opens = arrayOfNulls<MutableList<Pair<Int, String>>>(len + 1)
        val closes = arrayOfNulls<MutableList<Pair<Int, String>>>(len + 1)
        for (s in inline) {
            var a = maxOf(s.start, p.start) - start
            var b = minOf(s.end, p.end) - start
            // A marker must touch the text it formats: "**word** ", never "**word **".
            while (a < b && text[start + a].isWhitespace()) a++
            while (b > a && text[start + b - 1].isWhitespace()) b--
            if (b <= a) continue
            var open = MARKERS.getValue(s.type)
            var close = open
            if (s.type == SpanType.STAGE) {
                // Already bracketed text needs no more brackets.
                if (text[start + a] == '[' && text[start + b - 1] == ']') continue
                open = "["; close = "]"
            }
            (opens[a] ?: ArrayList<Pair<Int, String>>().also { opens[a] = it }) += b to open
            (closes[b] ?: ArrayList<Pair<Int, String>>().also { closes[b] = it }) += a to close
        }
        val at = Array(len + 1) { k ->
            val c = closes[k]?.sortedByDescending { it.first }?.joinToString("") { it.second } ?: ""
            val o = opens[k]?.sortedByDescending { it.first }?.joinToString("") { it.second } ?: ""
            c + o
        }
        val blockEscape = firstCharNeedingEscape(p)
        for (k in 0..len) {
            sb.append(at[k])
            if (k == len) break
            val c = text[start + k]
            val prev = if (k > 0) text[start + k - 1] else null
            val next = if (k + 1 < len) text[start + k + 1] else null
            val escape = k == blockEscape || when (c) {
                '\\', '*', '_', '`' -> true
                // Doubled, or touching a marker made of the same character.
                '~', '+', '=' -> prev == c || next == c || c in at[k] || c in at[k + 1]
                '[' -> LINK.containsMatchIn(text.substring(start + k, start + len))
                '(' -> at[k].endsWith("]") || (prev == ']' && at[k].isEmpty())
                else -> false
            }
            if (escape) sb.append('\\')
            sb.append(c)
        }
    }

    /**
     * Index in the paragraph of a first character that would otherwise be read as Markdown for the
     * paragraph itself ("# " in a normal line, "[ ] " at the start of a bullet...), or -1.
     */
    private fun firstCharNeedingEscape(p: Paragraph): Int {
        val content = text.substring(p.start, p.end)
        val lead = content.indexOfFirst { it != ' ' && it != '\t' }
        if (lead < 0) return -1
        val rest = content.substring(lead)
        return when (p.block) {
            BlockType.NORMAL -> if (BLOCK_START.containsMatchIn(rest) || SEPARATOR_LINE.matches(rest)) lead else -1
            BlockType.BULLET -> if (CHECKBOX.containsMatchIn(rest)) lead else -1
            BlockType.QUOTE -> if (rest.startsWith(">")) lead else -1
            BlockType.H1, BlockType.H2, BlockType.H3 -> {
                // Trailing " ##" would be read as closing hashes.
                val m = CLOSING_HASHES.find(content)
                m?.range?.first?.let { it + m.value.indexOf('#') } ?: -1
            }
            else -> -1
        }
    }

    companion object {
        const val MAX_LEVELS = 8

        val MARKERS = mapOf(
            SpanType.BOLD to "**",
            SpanType.ITALIC to "*",
            SpanType.UNDERLINE to "++",
            SpanType.STRIKE to "~~",
            SpanType.HIGHLIGHT to "==",
            SpanType.STAGE to "[",
        )

        private val BLOCK_START = Regex("^(#{1,6}([ \\t]|$)|[-*+•][ \\t]|\\d{1,3}[.)][ \\t]|>|```|~~~)")
        private val SEPARATOR_LINE = Regex("^([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$")
        private val CHECKBOX = Regex("^\\[[ xX]\\]([ \\t]|$)")
        private val CLOSING_HASHES = Regex("[ \\t]+#+[ \\t]*$")
        private val LINK = Regex("^\\[[^\\]\\n]*\\]\\(")

        /** Plain text (a title, say) escaped so that it reads back as it is. */
        fun escapeText(s: String): String {
            val sb = StringBuilder()
            for ((k, c) in s.withIndex()) {
                val doubled = (k > 0 && s[k - 1] == c) || (k + 1 < s.length && s[k + 1] == c)
                if (c in "\\*_`" || (c in "~+=" && doubled)) sb.append('\\')
                sb.append(c)
            }
            return sb.toString()
        }
    }
}
