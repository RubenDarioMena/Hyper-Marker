package com.hypermarker.markdown

/** Texto con selección [start, end). Independiente de Compose para probarlo en JVM. */
data class TextEdit(val text: String, val start: Int, val end: Int = start) {
    init { require(start in 0..end && end <= text.length) }
    val selected get() = text.substring(start, end)
}

/** Aplica formatos Markdown sobre la selección o en el cursor; repetir un formato lo quita. */
object MarkdownFormatter {
    private val listPrefix = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+")
    private val quotePrefix = Regex("^(\\s*)>\\s?")
    private val headingPrefix = Regex("^#{1,6}\\s+")

    fun bold(edit: TextEdit) = inline(edit, "**")
    fun italic(edit: TextEdit) = inline(edit, "*")
    fun strike(edit: TextEdit) = inline(edit, "~~")
    fun code(edit: TextEdit) = if (edit.selected.contains('\n')) codeBlock(edit) else inline(edit, "`", "código")

    fun inline(edit: TextEdit, marker: String, placeholder: String = "texto"): TextEdit {
        if (edit.start == edit.end) {
            val text = edit.text.replaceRange(edit.start, edit.end, marker + placeholder + marker)
            return TextEdit(text, edit.start + marker.length, edit.start + marker.length + placeholder.length)
        }
        if (edit.selected.contains('\n')) return inlineLines(edit, marker)
        // No incluir espacios de los extremos: `** palabra**` no es Markdown válido.
        var start = edit.start; var end = edit.end
        while (start < end && edit.text[start].isWhitespace()) start++
        while (end > start && edit.text[end - 1].isWhitespace()) end--
        if (start == end) return edit
        if (wrapped(edit.text, start, end, marker)) {
            val text = edit.text.removeRange(end, end + marker.length).removeRange(start - marker.length, start)
            return TextEdit(text, start - marker.length, end - marker.length)
        }
        val inner = edit.text.substring(start, end)
        if (inner.length > marker.length * 2 && wrapped(inner, marker.length, inner.length - marker.length, marker)) {
            val text = edit.text.replaceRange(start, end, inner.substring(marker.length, inner.length - marker.length))
            return TextEdit(text, start, end - marker.length * 2)
        }
        val text = edit.text.replaceRange(start, end, marker + inner + marker)
        return TextEdit(text, start + marker.length, end + marker.length)
    }

    private fun run(text: String, from: Int, step: Int, char: Char): Int {
        var count = 0; var index = from
        while (index in text.indices && text[index] == char) { count++; index += step }
        return count
    }
    /** Para `*`, una racha impar (1 o 3) indica cursiva; para el resto basta con la longitud del marcador. */
    private fun wrapped(text: String, start: Int, end: Int, marker: String): Boolean {
        if (start < marker.length || end + marker.length > text.length) return false
        if (!text.regionMatches(start - marker.length, marker, 0, marker.length) || !text.regionMatches(end, marker, 0, marker.length)) return false
        if (marker.length != 1) return true
        val before = run(text, start - 1, -1, marker[0]); val after = run(text, end, 1, marker[0])
        return before % 2 == 1 && after % 2 == 1
    }

    private fun inlineLines(edit: TextEdit, marker: String): TextEdit {
        val (from, to) = lineRange(edit)
        val lines = edit.text.substring(from, to).split('\n')
        val content = lines.filter { it.isNotBlank() }
        val remove = content.isNotEmpty() && content.all { line -> val t = line.trim(); t.length > marker.length * 2 && wrapped(t, marker.length, t.length - marker.length, marker) }
        val result = lines.joinToString("\n") { line ->
            if (line.isBlank()) line else {
                val lead = line.takeWhile { it.isWhitespace() }; val body = line.trim(); val trail = line.substring(lead.length + body.length)
                lead + (if (remove) body.substring(marker.length, body.length - marker.length) else marker + body + marker) + trail
            }
        }
        return TextEdit(edit.text.replaceRange(from, to, result), from, from + result.length)
    }

    private fun lineRange(edit: TextEdit): Pair<Int, Int> {
        val from = edit.text.lastIndexOf('\n', (edit.start - 1).coerceAtLeast(0)).let { if (edit.start == 0 || it < 0) 0 else it + 1 }
        val endAnchor = if (edit.end > edit.start && edit.text.getOrNull(edit.end - 1) == '\n') edit.end - 1 else edit.end
        val to = edit.text.indexOf('\n', endAnchor).let { if (it < 0) edit.text.length else it }
        return from to to.coerceAtLeast(from)
    }

    private fun prefixLines(edit: TextEdit, has: (String) -> Boolean, strip: (String) -> String, add: (String, Int) -> String): TextEdit {
        val (from, to) = lineRange(edit)
        val lines = edit.text.substring(from, to).split('\n')
        val content = lines.filter { it.isNotBlank() }
        val remove = content.isNotEmpty() && content.all(has)
        var index = 0
        val result = lines.joinToString("\n") { line ->
            when { remove -> strip(line); line.isBlank() && lines.size > 1 -> line; else -> add(strip(line), index++) }
        }
        val text = edit.text.replaceRange(from, to, result)
        return if (edit.start == edit.end) (from + result.length).let { TextEdit(text, it, it) } else TextEdit(text, from, from + result.length)
    }
    private fun stripList(line: String) = listPrefix.find(line)?.let { line.substring(it.range.last + 1).let { rest -> it.groupValues[1] + rest } } ?: line

    fun bullets(edit: TextEdit) = prefixLines(edit, { Regex("^\\s*[-*+]\\s+").containsMatchIn(it) }, ::stripList) { line, _ -> line.takeWhile { it == ' ' } + "- " + line.trimStart(' ') }
    fun numbered(edit: TextEdit) = prefixLines(edit, { Regex("^\\s*\\d+[.)]\\s+").containsMatchIn(it) }, ::stripList) { line, index -> line.takeWhile { it == ' ' } + "${index + 1}. " + line.trimStart(' ') }
    fun quote(edit: TextEdit) = prefixLines(edit, { quotePrefix.containsMatchIn(it) }, { line -> quotePrefix.find(line)?.let { it.groupValues[1] + line.substring(it.range.last + 1) } ?: line }) { line, _ -> "> $line" }
    /** Nivel 0 convierte en párrafo; repetir el mismo nivel también lo quita. */
    fun heading(edit: TextEdit, level: Int): TextEdit {
        require(level in 0..6)
        val marks = "#".repeat(level) + " "
        val same = { line: String -> level > 0 && headingPrefix.find(line)?.value?.trimEnd() == marks.trim() }
        return prefixLines(edit, same, { it.replace(headingPrefix, "") }) { line, _ -> if (level == 0) line else marks + line.trimStart() }
    }

    fun link(edit: TextEdit, url: String): TextEdit {
        val target = url.trim().ifBlank { "https://" }.replace(")", "%29").replace(" ", "%20")
        if (edit.start == edit.end) {
            val text = edit.text.replaceRange(edit.start, edit.end, "[texto]($target)")
            return TextEdit(text, edit.start + 1, edit.start + 6)
        }
        val label = edit.selected.replace("[", "\\[").replace("]", "\\]")
        val value = "[$label]($target)"
        val text = edit.text.replaceRange(edit.start, edit.end, value)
        return (edit.start + value.length).let { TextEdit(text, it, it) }
    }

    /** Inserta un bloque separado por líneas en blanco y deja el cursor en `cursor` dentro del bloque. */
    private fun block(edit: TextEdit, content: String, cursor: Int, selectionEnd: Int = cursor): TextEdit {
        val before = edit.text.substring(0, edit.start); val after = edit.text.substring(edit.end)
        val lead = when { before.isEmpty() || before.endsWith("\n\n") -> ""; before.endsWith("\n") -> "\n"; else -> "\n\n" }
        val trail = when { after.isEmpty() -> "\n"; after.startsWith("\n\n") -> ""; after.startsWith("\n") -> "\n"; else -> "\n\n" }
        val text = before + lead + content + trail + after
        val base = before.length + lead.length
        return TextEdit(text, base + cursor, base + selectionEnd)
    }
    fun codeBlock(edit: TextEdit): TextEdit {
        if (edit.start == edit.end) return block(edit, "```\n\n```", 4)
        val (from, to) = lineRange(edit)
        val body = edit.text.substring(from, to)
        val fenced = TextEdit(edit.text, from, to)
        return block(fenced, "```\n$body\n```", 4, 4 + body.length)
    }
    fun table(edit: TextEdit): TextEdit {
        val selected = edit.selected.trim().takeIf { it.isNotEmpty() && !it.contains('\n') }?.replace("|", "\\|")
        val header = "| ${selected ?: "Columna 1"} | Columna 2 | Columna 3 |"
        val divider = "| --- | --- | --- |"
        val content = "$header\n$divider\n|  |  |  |"
        if (selected == null) return block(edit, content, 2, 2 + "Columna 1".length)
        return block(edit, content, header.length + divider.length + 4)
    }
    fun rule(edit: TextEdit) = block(TextEdit(edit.text, edit.end, edit.end), "---", 3)
}
