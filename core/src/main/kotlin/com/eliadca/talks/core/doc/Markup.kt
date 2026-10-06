package com.eliadca.talks.core.doc

/**
 * Parses a small Markdown-like syntax into a [RichDoc]. Used for the bundled sample speeches and
 * for importing `.md` files.
 *
 * Blocks: `# `, `## `, `### ` headings; `- `/`* `/`• ` bullets; `1. ` numbered; `- [ ] `/`- [x] `
 * checklist; `> ` quote. Inline: `**bold**`, `*italic*`, `_italic_`, `~~strike~~`, `++underline++`,
 * `==highlight==`. A backslash escapes the next character. Text in `[square brackets]` is left as
 * is; the tracker treats it as a note that is not read aloud.
 */
object Markup {

    const val HIGHLIGHT_YELLOW = 0x66FFD54F

    private val numbered = Regex("^(\\d{1,3})[.)] ")

    fun parse(source: String): RichDoc {
        val text = StringBuilder()
        val spans = ArrayList<RichSpan>()
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        for ((li, raw) in lines.withIndex()) {
            if (li > 0) text.append('\n')
            var line = raw
            var block: String? = null
            var arg = 0
            var indent = 0

            val leading = line.takeWhile { it == ' ' || it == '\t' }
            val rest = line.substring(leading.length)

            when {
                rest.startsWith("### ") -> { block = SpanType.H3; line = rest.substring(4) }
                rest.startsWith("## ") -> { block = SpanType.H2; line = rest.substring(3) }
                rest.startsWith("# ") -> { block = SpanType.H1; line = rest.substring(2) }
                rest.startsWith("- [ ] ") || rest.startsWith("* [ ] ") -> {
                    block = SpanType.CHECK; line = rest.substring(6); indent = indentOf(leading)
                }
                rest.startsWith("- [x] ") || rest.startsWith("- [X] ") || rest.startsWith("* [x] ") -> {
                    block = SpanType.CHECK; arg = 1; line = rest.substring(6); indent = indentOf(leading)
                }
                rest.startsWith("- ") || rest.startsWith("* ") || rest.startsWith("• ") -> {
                    block = SpanType.BULLET; line = rest.substring(2); indent = indentOf(leading)
                }
                numbered.containsMatchIn(rest) -> {
                    val m = numbered.find(rest)!!
                    block = SpanType.NUMBER; line = rest.substring(m.value.length); indent = indentOf(leading)
                }
                rest.startsWith("> ") -> { block = SpanType.QUOTE; line = rest.substring(2) }
                rest == "---" || rest == "***" -> {
                    line = "· · ·"
                    val s = text.length
                    text.append(line)
                    spans += RichSpan(SpanType.ALIGN_CENTER, s, text.length)
                    continue
                }
                else -> line = raw
            }

            val start = text.length
            parseInline(line, text, spans)
            val end = text.length
            if (block != null) spans += RichSpan(block, start, end, arg)
            if (indent > 0) spans += RichSpan(SpanType.INDENT, start, end, indent)
        }
        return RichDoc(text.toString(), spans).normalized()
    }

    private fun indentOf(leading: String): Int =
        (leading.count { it == ' ' } / 2 + leading.count { it == '\t' }).coerceAtMost(4)

    private val markers = listOf("**", "~~", "++", "==", "*", "_")

    private fun parseInline(line: String, out: StringBuilder, spans: MutableList<RichSpan>) {
        val open = HashMap<String, Int>()
        var i = 0
        val n = line.length
        while (i < n) {
            val c = line[i]
            if (c == '\\' && i + 1 < n) {
                out.append(line[i + 1]); i += 2; continue
            }
            val marker = markers.firstOrNull { line.startsWith(it, i) }
            if (marker == null) {
                out.append(c); i++; continue
            }
            val after = i + marker.length
            val startOffset = open[marker]
            if (startOffset != null) {
                // Closing marker.
                if (out.length > startOffset) spans += styleFor(marker, startOffset, out.length)
                open.remove(marker)
                i = after
            } else {
                val canOpen = after < n && !line[after].isWhitespace() && line.indexOf(marker, after + 1) > after &&
                    (marker != "_" || i == 0 || !line[i - 1].isLetterOrDigit())
                if (canOpen) {
                    open[marker] = out.length
                    i = after
                } else {
                    out.append(marker); i = after
                }
            }
        }
    }

    private fun styleFor(marker: String, start: Int, end: Int): RichSpan = when (marker) {
        "**" -> RichSpan(SpanType.BOLD, start, end)
        "*", "_" -> RichSpan(SpanType.ITALIC, start, end)
        "~~" -> RichSpan(SpanType.STRIKE, start, end)
        "++" -> RichSpan(SpanType.UNDERLINE, start, end)
        else -> RichSpan(SpanType.HIGHLIGHT, start, end, HIGHLIGHT_YELLOW)
    }
}
