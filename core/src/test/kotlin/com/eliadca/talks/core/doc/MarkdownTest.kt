package com.eliadca.talks.core.doc

import com.eliadca.talks.core.sample.SampleContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    private fun RichDoc.blocks() = paragraphs().map { it.block }
    private fun RichDoc.textOf(type: String) = spans.filter { it.type == type }.map { text.substring(it.start, it.end) }

    @Test fun markdownWrittenByAnAssistantImportsCleanly() {
        val md = """
            ```markdown
            # Título del discurso ##
            #### Un detalle
            __Fuerte__ y *suave* con `código`, [un enlace](https://example.com) y una foto ![foto](https://x.com/a.png)
            + uno
              * dos
            * [X] hecho
            >cita
            ___
            1) primero
            ```
        """.trimIndent()
        val doc = Markup.parse(md)
        assertEquals(
            "Título del discurso\nUn detalle\nFuerte y suave con código, un enlace y una foto \nuno\ndos\nhecho\ncita\n· · ·\nprimero",
            doc.text,
        )
        assertEquals(
            listOf(BlockType.H1, BlockType.H3, BlockType.NORMAL, BlockType.BULLET, BlockType.BULLET, BlockType.CHECK, BlockType.QUOTE, BlockType.NORMAL, BlockType.NUMBER),
            doc.blocks(),
        )
        assertEquals(1, doc.paragraphs()[4].indent)
        assertTrue(doc.paragraphs()[5].checked)
        assertEquals(Align.CENTER, doc.paragraphs()[7].align)
        assertEquals(listOf("Fuerte"), doc.textOf(SpanType.BOLD))
        assertEquals(listOf("suave"), doc.textOf(SpanType.ITALIC))
    }

    @Test fun markersInsideWordsAndEscapesStayLiteral() {
        val doc = Markup.parse("snake_case_name, 2 * 3, a \\*b\\* c, *abierto y C# y \\_no\\_")
        assertEquals("snake_case_name, 2 * 3, a *b* c, *abierto y C# y _no_", doc.text)
        assertTrue(doc.spans.isEmpty())
    }

    @Test fun anEscapedMarkerRightAfterAnOpeningOneDoesNotCloseIt() {
        val doc = Markup.parse("*\\*hola* y **\\*** fin")
        assertEquals("*hola y * fin", doc.text)
        assertEquals(listOf("*hola"), doc.textOf(SpanType.ITALIC))
        assertEquals(listOf("*"), doc.textOf(SpanType.BOLD))
    }

    @Test fun everyStyleCanBeCombined() {
        val doc = Markup.parse("***las dos*** y **==fuerte y amarilla==** y ++~~raro~~++")
        assertEquals("las dos y fuerte y amarilla y raro", doc.text)
        assertEquals(listOf("las dos", "fuerte y amarilla"), doc.textOf(SpanType.BOLD))
        assertEquals(listOf("las dos"), doc.textOf(SpanType.ITALIC))
        assertEquals(listOf("fuerte y amarilla"), doc.textOf(SpanType.HIGHLIGHT))
        assertEquals(listOf("raro"), doc.textOf(SpanType.UNDERLINE))
        assertEquals(listOf("raro"), doc.textOf(SpanType.STRIKE))
    }

    @Test fun plainTextIsNotTakenForMarkdown() {
        assertNull(Markup.parseIfMarkdown("Hola a todos.\nGracias por venir [pausa] y 2 * 3 = 6."))
        assertNotNull(Markup.parseIfMarkdown("Hola **a todos**"))
        assertNotNull(Markup.parseIfMarkdown("- uno\n- dos"))
        assertNotNull(Markup.parseIfMarkdown("```\nHola\n```"))
    }

    @Test fun formattedDocumentsRoundTripThroughMarkdown() {
        for (doc in listOf(SampleContent.practice, SampleContent.welcome)) {
            assertEquals(doc, Markup.parse(doc.toMarkdown()))
        }
        val rich = Markup.parse(
            "# Título\n## Parte\n### Detalle\n**negrita** *cursiva* ++subrayado++ ~~tachado~~ ==resaltado==\n" +
                "***ambas*** y **==mezcla==**\n- uno\n  - dos\n    - tres\n1. a\n2. b\n  1. b1\n- [ ] por hacer\n- [x] hecho\n> cita **fuerte**\n---\n\nfin",
        )
        val md = rich.toMarkdown()
        assertEquals(rich, Markup.parse(md))
        assertTrue(md, md.contains("  - dos\n    - tres"))
        assertTrue(md, md.contains("1. a\n2. b\n  1. b1"))
    }

    @Test fun literalMarkdownCharactersAreEscaped() {
        val text = "# no es título\n- no es lista\n1. tampoco\n> ni cita\n---\n" +
            "2 * 3 = 6, a_b, ~~x~~, ==y==, ++z++, `c` y \\ barra\n[Pausa](risas) y [web](https://x.com)\nC# y fin"
        val doc = RichDoc(text, listOf(RichSpan(SpanType.BOLD, text.length - 3, text.length))).normalized()
        assertEquals(doc, Markup.parse(doc.toMarkdown()))
        val heading = RichDoc("Uso de C #", listOf(RichSpan(SpanType.H2, 0, 10)))
        assertEquals(heading, Markup.parse(heading.toMarkdown()))
        val checkLike = RichDoc("[ ] no es tarea", listOf(RichSpan(SpanType.BULLET, 0, 15)))
        assertEquals(checkLike, Markup.parse(checkLike.toMarkdown()))
    }

    @Test fun markersHugTheTextAndTouchingSymbolsAreEscaped() {
        val doc = RichDoc(
            "Hola mundo ~ fin",
            listOf(RichSpan(SpanType.BOLD, 4, 11), RichSpan(SpanType.STRIKE, 11, 12)),
        )
        val md = doc.toMarkdown()
        assertEquals("Hola **mundo** ~~\\~~~ fin", md)
        assertEquals(listOf("mundo"), Markup.parse(md).textOf(SpanType.BOLD))
        assertEquals(listOf("~"), Markup.parse(md).textOf(SpanType.STRIKE))
    }

    @Test fun notesMarkedWithTheNoteStyleAreWrittenInBrackets() {
        val doc = RichDoc("Hola pausa adiós [ya] fin", listOf(RichSpan(SpanType.STAGE, 5, 10), RichSpan(SpanType.STAGE, 17, 21)))
        assertEquals("Hola [pausa] adiós [ya] fin", doc.toMarkdown())
    }

    @Test fun anUnformattedDocumentIsWrittenAsItIs() {
        assertEquals("**ya es Markdown** y \\* nada más", RichDoc("**ya es Markdown** y \\* nada más").toMarkdown())
        assertEquals(SampleContent.AGENT_TEMPLATE, SampleContent.agentTemplate.toMarkdown())
    }

    @Test fun theTitleGoesOnTopUnlessTheTextHasOne() {
        assertEquals("# Mi \\*charla\\*\n\ntexto", RichDoc("texto").toMarkdown("Mi *charla*"))
        val titled = Markup.parse("# Mi charla\ntexto")
        assertEquals("# Mi charla\ntexto", titled.toMarkdown("Otro nombre"))
        assertEquals(SampleContent.AGENT_TEMPLATE, SampleContent.agentTemplate.toMarkdown(SampleContent.AGENT_TEMPLATE_TITLE))
    }

    @Test fun theLeadingTitleIsTheFirstHeading() {
        assertEquals("Mi charla", Markup.parse("\n# Mi charla\ntexto").leadingTitle())
        assertNull(Markup.parse("Hola\n# Después").leadingTitle())
        assertNull(Markup.parse("## Sección\ntexto").leadingTitle())
    }

    @Test fun theAgentTemplateDocumentsEveryFormatAndItsExampleUsesThem() {
        val t = SampleContent.AGENT_TEMPLATE
        for (syntax in listOf(
            "`# Título`", "`## Sección`", "`### Subsección`", "`**negrita**`", "`__negrita__`", "`*cursiva*`", "`_cursiva_`",
            "`++subrayado++`", "`~~tachado~~`", "`==resaltado==`", "`[Pausa]`", "`- `", "`* `", "`+ `", "`1. `", "`1) `",
            "`- [ ] `", "`- [x] `", "`> `", "`---`", "`\\*`", "dos espacios",
        )) {
            assertTrue("the template does not explain $syntax", t.contains(syntax))
        }
        // The example answer, as the assistant would send it back.
        val example = t.substringAfter("```\n").substringBefore("\n```")
        val doc = Markup.parse(example)
        assertEquals("El valor de empezar", doc.leadingTitle())
        val blocks = doc.blocks().toSet()
        for (b in listOf(BlockType.H1, BlockType.H2, BlockType.NUMBER, BlockType.BULLET, BlockType.QUOTE)) {
            assertTrue("the example has no $b", b in blocks)
        }
        assertTrue(doc.paragraphs().any { it.block == BlockType.BULLET && it.indent == 1 })
        assertTrue(doc.paragraphs().any { it.align == Align.CENTER && doc.text.substring(it.start, it.end) == Markup.SEPARATOR })
        for (type in listOf(SpanType.BOLD, SpanType.ITALIC, SpanType.UNDERLINE, SpanType.STRIKE, SpanType.HIGHLIGHT)) {
            assertTrue("the example has no $type", doc.spans.any { it.type == type })
        }
        // Notes and headings are not expected to be said.
        val said = doc.spokenWordCount()
        assertTrue("$said", said in 80..140)
        assertTrue(doc.silentRanges().isNotEmpty())
        // The whole template also imports (its code fence is dropped).
        assertEquals("Plantilla: discursos para Talks", Markup.parse(t).leadingTitle())
    }

    @Test fun aSliceKeepsItsFormatting() {
        val doc = Markup.parse("# Título\nHola **mundo** feliz\n- uno\n- dos")
        val s = doc.text.indexOf("mundo")
        val e = doc.text.indexOf("uno") + 3
        val part = doc.slice(s, e)
        assertEquals("mundo feliz\nuno", part.text)
        assertEquals(listOf(BlockType.NORMAL, BlockType.BULLET), part.blocks())
        assertEquals(listOf("mundo"), part.textOf(SpanType.BOLD))
        // Ending right at the start of a heading does not carry the heading along.
        val upToHeading = Markup.parse("uno\n# Dos").slice(0, 4)
        assertEquals(listOf(BlockType.NORMAL, BlockType.NORMAL), upToHeading.blocks())
    }

    @Test fun pastingIntoAnEmptyDocumentGivesTheFragment() {
        val fragment = Markup.parse("# T\n**b** y *c*\n- x\n> y")
        assertEquals(fragment, RichDoc("").replaceRange(0, 0, fragment))
    }

    @Test fun pastingInsideAParagraphKeepsItsFormatting() {
        val doc = Markup.parse("- hola mundo")
        val pasted = doc.replaceRange(5, 5, Markup.parse("# **gran**"))
        assertEquals("hola granmundo", pasted.text)
        assertEquals(listOf(BlockType.BULLET), pasted.blocks())
        assertEquals(listOf("gran"), pasted.textOf(SpanType.BOLD))
    }

    @Test fun pastedTextDoesNotTakeTheStyleAroundIt() {
        val doc = Markup.parse("**abcdef**").replaceRange(3, 3, RichDoc("X"))
        assertEquals("abcXdef", doc.text)
        assertEquals(listOf("abc", "def"), doc.textOf(SpanType.BOLD))
        val replaced = Markup.parse("uno dos tres").replaceRange(4, 7, Markup.parse("**DOS**"))
        assertEquals(Markup.parse("uno **DOS** tres"), replaced)
    }

    @Test fun pastingSeveralParagraphsKeepsTheirFormattingAndWhatFollows() {
        val doc = Markup.parse("a\n- b").replaceRange(1, 1, Markup.parse("x\n## y"))
        assertEquals(Markup.parse("ax\n## y\n- b"), doc)
        val atEnd = Markup.parse("Hola").replaceRange(4, 4, Markup.parse("\n# T\ntexto"))
        assertEquals(Markup.parse("Hola\n# T\ntexto"), atEnd)
        val onEmptyLine = Markup.parse("uno\n\ndos").replaceRange(4, 4, Markup.parse("# T\n- x"))
        assertEquals(Markup.parse("uno\n# T\n- x\ndos"), onEmptyLine)
    }
}
