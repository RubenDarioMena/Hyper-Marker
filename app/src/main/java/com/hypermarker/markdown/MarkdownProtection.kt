package com.hypermarker.markdown

import java.util.UUID

/**
 * Acota en Visual los fragmentos que Milkdown no conserva fielmente (imágenes, HTML, definiciones de
 * enlaces, tablas irregulares, matemáticas, contenedores…), sin bloquear el resto de la nota.
 *
 * - Un bloque no compatible se envuelve en un bloque de código con lenguaje `hm-raw-<nonce>`: se muestra
 *   como fuente editable y al volver a Markdown se desenvuelve tal cual.
 * - Un fragmento en línea se envuelve en código en línea `⟦…⟧`, con la fuente original visible.
 *
 * La restauración sólo quita los envoltorios; el texto que contienen es la fuente Markdown original o lo
 * que el usuario haya escrito dentro de ellos. Si no puede acotarse todo, [protect] devuelve null y la
 * nota sigue en Fuente.
 */
object MarkdownProtection {
    const val OPEN = "⟦"
    const val CLOSE = "⟧"
    class Protected(val text: String, val nonce: String, val blocks: Int, val inline: Int) {
        val count get() = blocks + inline
        val language get() = "hm-raw-$nonce"
    }

    private val fenceStart = Regex("^ {0,3}(`{3,}|~{3,})")
    private val blockTags = setOf("address", "article", "aside", "blockquote", "body", "center", "details", "dialog", "dd", "div", "dl", "dt",
        "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header", "hr", "html", "iframe",
        "legend", "li", "main", "menu", "nav", "ol", "p", "pre", "script", "section", "style", "summary", "table", "tbody", "td", "textarea",
        "tfoot", "th", "thead", "title", "tr", "ul", "video", "audio", "picture", "canvas", "svg", "math")
    private val htmlBlockStart = Regex("^ {0,3}<(?:!|\\?|/?([a-zA-Z][a-zA-Z0-9-]*))")
    private val loneTag = Regex("^ {0,3}</?[a-zA-Z][a-zA-Z0-9-]*(?:\\s[^<>]*)?/?>\\s*$")
    private val blockMarker = Regex("^ {0,3}(?:\\[[^\\]]+\\]:|\\$\\$|:::|[-*+] \\[[ xX]\\])")
    private val definition = Regex("^ {0,3}\\[([^\\]]+)\\]:")
    private val inlineCode = Regex("(`+)(?:(?!\\1)[^\\n])*?\\1")
    private val inlineRaw = listOf(
        "!\\[[^\\]\\n]*\\](?:\\([^)\\n]*\\)|\\[[^\\]\\n]*\\])?", // imágenes en línea, por referencia o abreviadas
        "<([a-zA-Z][a-zA-Z0-9-]*)(?:\\s[^<>\\n]*)?>[^<\\n]*</\\1\\s*>", // elemento HTML en línea completo
        "<[/!]?[a-zA-Z][^<>\\n]*>",                             // etiqueta suelta y autoenlaces
        "\\{[#.:][^}\\n]*\\}",                                   // atributos de extensiones
    ).joinToString("|")

    fun protect(source: String, nonce: String = UUID.randomUUID().toString().replace("-", "").take(8)): Protected? {
        if (!MarkdownPolicy.unsupported(source)) return Protected(source, nonce, 0, 0)
        if (source.contains(OPEN) || source.contains(CLOSE) || source.contains("hm-raw-")) return null
        val lines = source.split("\n")
        val labels = lines.mapNotNull { definition.find(it)?.groupValues?.get(1)?.lowercase()?.trim() }.toSet()
        // Si una definición queda acotada, sus referencias también, para que Visual no las escape como texto.
        val references = if (labels.isEmpty()) null else
            Regex("\\[[^\\]\\n]+\\]\\[[^\\]\\n]*\\]|(?i:\\[(?:" + labels.joinToString("|") { Regex.escape(it) } + ")\\])(?![(\\[:])")
        val inlinePattern = Regex(inlineRaw + (references?.let { "|" + it.pattern } ?: ""))
        val out = ArrayList<String>(lines.size)
        var blocks = 0; var inline = 0
        var index = 0
        while (index < lines.size) {
            val fence = fenceStart.find(lines[index])
            if (fence != null) {
                // Los bloques de código existentes ya son compatibles; se copian sin tocar.
                val marker = fence.groupValues[1]
                out += lines[index++]
                while (index < lines.size) {
                    val line = lines[index++]; out += line
                    val close = fenceStart.find(line)
                    if (close != null && close.groupValues[1].first() == marker.first() && close.groupValues[1].length >= marker.length &&
                        line.substring(close.range.last + 1).isBlank()) break
                }
                continue
            }
            if (lines[index].isBlank()) { out += lines[index++]; continue }
            var end = index
            while (end < lines.size && lines[end].isNotBlank() && fenceStart.find(lines[end]) == null) end++
            val chunk = lines.subList(index, end)
            index = end
            val text = chunk.joinToString("\n")
            if (!MarkdownPolicy.unsupported(text) && references?.containsMatchIn(text) != true) { out += chunk; continue }
            val attempt = if (chunk.any(::isBlockLine)) null else inlineProtect(chunk, inlinePattern)
            if (attempt != null && !MarkdownPolicy.unsupported(attempt.first.joinToString("\n"))) {
                out += attempt.first; inline += attempt.second
            } else {
                val ticks = "`".repeat(maxOf(3, (Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0) + 1))
                out += "${ticks}hm-raw-$nonce"; out += chunk; out += ticks
                blocks++
            }
        }
        val text = out.joinToString("\n")
        return if (MarkdownPolicy.unsupported(text)) null else Protected(text, nonce, blocks, inline)
    }

    private fun isBlockLine(line: String): Boolean {
        if (blockMarker.containsMatchIn(line)) return true
        val html = htmlBlockStart.find(line) ?: return false
        val tag = html.groupValues[1].lowercase()
        return tag.isEmpty() || tag in blockTags || loneTag.matches(line)
    }

    /** Envuelve en código en línea los fragmentos no compatibles fuera del código existente. */
    private fun inlineProtect(chunk: List<String>, pattern: Regex): Pair<List<String>, Int>? {
        var count = 0
        val table = chunk.size >= 2 && chunk.all { it.trimStart().startsWith("|") }
        val result = chunk.map { line ->
            val builder = StringBuilder()
            var last = 0
            val code = inlineCode.findAll(line).map { it.range }.toList()
            pattern.findAll(line).forEach { match ->
                if (match.range.first < last || code.any { it.first <= match.range.last && match.range.first <= it.last }) return@forEach
                if (table && match.value.contains('|')) return null
                builder.append(line, last, match.range.first).append(wrapInline(match.value))
                last = match.range.last + 1; count++
            }
            builder.append(line, last, line.length).toString()
        }
        return if (count == 0) null else result to count
    }

    private fun wrapInline(value: String): String {
        val ticks = "`".repeat((Regex("`+").findAll(value).maxOfOrNull { it.value.length } ?: 0) + 1)
        return "$ticks$OPEN$value$CLOSE$ticks"
    }

    /** Quita los envoltorios de un Markdown serializado por Visual y devuelve la fuente a guardar. */
    fun restore(markdown: String, protection: Protected?): String {
        if (protection == null || protection.count == 0) return markdown
        val language = Regex.escape(protection.language)
        var text = Regex("(?m)^(`{3,}|~{3,})$language[ \\t]*\\n([\\s\\S]*?)\\n?^\\1[ \\t]*$").replace(markdown) { it.groupValues[2] }
        text = Regex("(`+)$OPEN([^\\n]*?)$CLOSE\\1(?!`)").replace(text) { it.groupValues[2] }
        return text
    }
}
