package com.hypermarker

import com.hypermarker.markdown.MarkdownPolicy
import org.junit.Assert.*
import org.junit.Test

class MarkdownPolicyTest {
    @Test fun ordinaryMarkdownAndFencedHtmlCanUseVisual() {
        assertFalse(MarkdownPolicy.unsupported("# Idea\n\n- Uno\n- Dos\n\n**Fuerte** *énfasis* [enlace](https://example.com)\n\n```html\n<div>ejemplo</div>\n```\n"))
    }
    @Test fun unsupportedSyntaxAndIncompleteFencesStayInSource() {
        listOf("<script>alert(1)</script>", "[ref]: https://example.com", "| A | B |", "- [ ] tarea", "![imagen](remote.png)", "```html\nCódigo sin cerrar",
            "| A | B |\n| --- | --- |\n| <b>x</b> | y |", "| A | B |\n| --- | --- |\n| solo una |").forEach { assertTrue(it, MarkdownPolicy.unsupported(it)) }
    }
    @Test fun wellFormedTablesCanUseVisual() {
        assertFalse(MarkdownPolicy.unsupported("Intro\n\n| Columna 1 | Columna 2 |\n| :--- | ---: |\n| **a** | `b \\| c` |\n\nFin"))
    }
}
