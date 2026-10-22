package com.hypermarker

import com.hypermarker.markdown.MarkdownFormatter
import com.hypermarker.markdown.TextEdit
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownFormatterTest {
    private fun select(text: String, part: String): TextEdit { val start = text.indexOf(part); return TextEdit(text, start, start + part.length) }

    @Test fun inlineStylesWrapTheSelectionAndToggleOff() {
        val bold = MarkdownFormatter.bold(select("Una idea clave aquí", "idea clave"))
        assertEquals("Una **idea clave** aquí", bold.text)
        assertEquals("idea clave", bold.selected)
        assertEquals("Una idea clave aquí", MarkdownFormatter.bold(bold).text)
        assertEquals("Una ***idea clave*** aquí", MarkdownFormatter.italic(bold).text)
        assertEquals("Una *idea* aquí", MarkdownFormatter.italic(select("Una idea aquí", "idea")).text)
        assertEquals("Una idea aquí", MarkdownFormatter.italic(select("Una *idea* aquí", "*idea*")).text)
        assertEquals("Una `x = 1` aquí", MarkdownFormatter.code(select("Una x = 1 aquí", "x = 1")).text)
        assertEquals("~~viejo~~", MarkdownFormatter.strike(select("viejo", "viejo")).text)
    }
    @Test fun selectionSpacesStayOutsideMarkersAndEmptySelectionInsertsPlaceholder() {
        assertEquals("Una **idea** aquí", MarkdownFormatter.bold(select("Una idea aquí", " idea ")).text)
        val empty = MarkdownFormatter.bold(TextEdit("Hola ", 5))
        assertEquals("Hola **texto**", empty.text); assertEquals("texto", empty.selected)
    }
    @Test fun multilineInlineStylesWrapEachLine() {
        assertEquals("**uno**\n\n**dos**", MarkdownFormatter.bold(TextEdit("uno\n\ndos", 0, 8)).text)
    }
    @Test fun listsQuotesAndHeadingsApplyToSelectedLines() {
        val text = "Primero\nSegundo\nFuera"
        val bullets = MarkdownFormatter.bullets(TextEdit(text, 2, 10))
        assertEquals("- Primero\n- Segundo\nFuera", bullets.text)
        assertEquals("Primero\nSegundo\nFuera", MarkdownFormatter.bullets(bullets).text)
        assertEquals("1. Primero\n2. Segundo\nFuera", MarkdownFormatter.numbered(bullets).text)
        assertEquals("> Primero\n> Segundo\nFuera", MarkdownFormatter.quote(TextEdit(text, 0, 12)).text)
        assertEquals("## Primero\nSegundo\nFuera", MarkdownFormatter.heading(TextEdit(text, 3), 2).text)
        assertEquals("Primero", MarkdownFormatter.heading(TextEdit("## Primero", 4), 2).text)
        assertEquals("# Primero", MarkdownFormatter.heading(TextEdit("### Primero", 4), 1).text)
        assertEquals("Primero", MarkdownFormatter.heading(TextEdit("### Primero", 4), 0).text)
        val empty = MarkdownFormatter.bullets(TextEdit("Texto\n", 6))
        assertEquals("Texto\n- ", empty.text); assertEquals(8, empty.start)
    }
    @Test fun linksBlocksAndTablesInsertAtSelection() {
        assertEquals("Ver [docs](https://ejemplo.com) hoy", MarkdownFormatter.link(select("Ver docs hoy", "docs"), "https://ejemplo.com").text)
        val code = MarkdownFormatter.codeBlock(TextEdit("Antes", 5))
        assertEquals("Antes\n\n```\n\n```\n", code.text); assertEquals(11, code.start)
        assertEquals("```\nuno\ndos\n```\n", MarkdownFormatter.code(TextEdit("uno\ndos", 0, 7)).text)
        val table = MarkdownFormatter.table(TextEdit("Intro", 5))
        assertEquals("Intro\n\n| Columna 1 | Columna 2 | Columna 3 |\n| --- | --- | --- |\n|  |  |  |\n", table.text)
        assertEquals("Columna 1", table.selected)
        val named = MarkdownFormatter.table(TextEdit("Plan", 0, 4))
        assertEquals("| Plan | Columna 2 | Columna 3 |\n| --- | --- | --- |\n|  |  |  |\n", named.text)
        assertEquals("|  |  |  |", named.text.substring(named.start - 2, named.start + 8))
        assertEquals("Uno\n\n---\n", MarkdownFormatter.rule(TextEdit("Uno", 3)).text)
    }
}
