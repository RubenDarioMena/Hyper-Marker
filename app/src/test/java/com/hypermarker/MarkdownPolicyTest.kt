package com.hypermarker

import com.hypermarker.markdown.MarkdownPolicy
import org.junit.Assert.*
import org.junit.Test

class MarkdownPolicyTest {
    @Test fun ordinaryMarkdownAndFencedHtmlCanUseVisual() {
        assertFalse(MarkdownPolicy.unsupported("# Idea\n\n- Uno\n- Dos\n\n**Fuerte** *énfasis* [enlace](https://example.com)\n\n```html\n<div>ejemplo</div>\n```\n"))
    }
    @Test fun unsupportedSyntaxAndIncompleteFencesStayInSource() {
        listOf("<script>alert(1)</script>", "[ref]: https://example.com", "| A | B |", "- [ ] tarea", "![imagen](remote.png)", "```html\nCódigo sin cerrar").forEach { assertTrue(it, MarkdownPolicy.unsupported(it)) }
    }
}
