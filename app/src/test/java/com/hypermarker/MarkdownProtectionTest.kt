package com.hypermarker

import com.hypermarker.markdown.MarkdownPolicy
import com.hypermarker.markdown.MarkdownProtection
import org.junit.Assert.*
import org.junit.Test

class MarkdownProtectionTest {
    private fun roundTrip(source: String): MarkdownProtection.Protected {
        val protected = requireNotNull(MarkdownProtection.protect(source, "n0nce123")) { source }
        assertFalse(protected.text, MarkdownPolicy.unsupported(protected.text))
        assertEquals(source, MarkdownProtection.restore(protected.text, protected))
        return protected
    }

    @Test fun supportedNotesAreUntouched() {
        val source = "# Idea\n\n- [Docs](https://example.com)\n- [Repo](https://github.com)\n\n| A | B |\n|:-|-:|\n| 1 | 2 |\n"
        assertFalse(MarkdownPolicy.unsupported(source))
        val protected = roundTrip(source)
        assertEquals(0, protected.count); assertEquals(source, protected.text)
    }

    @Test fun imagesAndInlineHtmlAreWrappedInlineAndTheRestStaysEditable() {
        val source = "# Título\n\nMira ![foto](img/a.png) y pulsa <kbd>Ctrl</kbd>, ver <https://example.com>.\n\nPárrafo **normal**.\n"
        val protected = roundTrip(source)
        assertEquals(3, protected.inline); assertEquals(0, protected.blocks)
        assertTrue(protected.text.contains("`⟦![foto](img/a.png)⟧`"))
        assertTrue(protected.text.contains("Párrafo **normal**."))
    }

    @Test fun htmlBlocksDefinitionsMathAndBrokenTablesBecomeRawBlocks() {
        val source = "Intro\n\n<div align=\"center\">\n  <img src=\"logo.png\">\n</div>\n\nVer [la guía][g].\n\n[g]: https://example.com/guia\n\n" +
            "$$\nx^2\n$$\n\n| A | B |\n| solo |\n\n- [ ] tarea\n- [x] hecha\n\nFin"
        val protected = roundTrip(source)
        assertEquals(5, protected.blocks)
        assertTrue(protected.text.contains("```hm-raw-n0nce123\n<div align=\"center\">"))
        // La referencia que depende de una definición protegida se acota para no escaparse como texto.
        assertTrue(protected.text.contains("`⟦[la guía][g]⟧`"))
        assertTrue(protected.text.startsWith("Intro\n\n"))
        assertTrue(protected.text.endsWith("\n\nFin"))
    }

    @Test fun tableCellsKeepTheirStructure() {
        val source = "| A | B |\n| --- | --- |\n| <b>x</b> | ![i](i.png) |\n"
        val protected = roundTrip(source)
        assertEquals(2, protected.inline)
        assertTrue(protected.text.contains("| `⟦<b>x</b>⟧` | `⟦![i](i.png)⟧` |"))
    }

    @Test fun existingCodeIsLeftAloneAndFencesAreLongEnough() {
        val source = "```html\n<div>ejemplo</div>\n```\n\nUsa `<br>` o <br>\n\n<pre>\nusa ``` aquí\n</pre>\n"
        val protected = roundTrip(source)
        assertTrue(protected.text.startsWith("```html\n<div>ejemplo</div>\n```"))
        assertTrue(protected.text.contains("Usa `<br>` o `⟦<br>⟧`"))
        assertTrue(protected.text.contains("````hm-raw-n0nce123\n<pre>"))
    }

    @Test fun editsInsideFragmentsAreKeptAndForeignBlocksIgnored() {
        val protected = requireNotNull(MarkdownProtection.protect("<p>hola</p>\n\nTexto ![a](a.png)", "n0nce123"))
        val edited = "```hm-raw-n0nce123\n<p>hola mundo</p>\n```\n\nTexto `⟦![b](b.png)⟧` editado\n\n```hm-raw-otro\nx\n```\n"
        assertEquals("<p>hola mundo</p>\n\nTexto ![b](b.png) editado\n\n```hm-raw-otro\nx\n```\n", MarkdownProtection.restore(edited, protected))
    }

    @Test fun unsafeNotesStayInSource() {
        assertNull(MarkdownProtection.protect("```js\nsin cerrar"))
        assertNull(MarkdownProtection.protect("Usa ⟦esto⟧ y ![a](a.png)"))
    }
}
