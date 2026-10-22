package com.hypermarker.markdown

object MarkdownPolicy {
    private val tableRow = Regex("^ {0,3}\\|.*\\|\\s*$")
    private val tableDelimiter = Regex("^ {0,3}\\|(\\s*:?-{3,}:?\\s*\\|)+\\s*$")
    private fun cells(line: String) = line.trim().removePrefix("|").removeSuffix("|").split(Regex("(?<!\\\\)\\|"))

    fun unsupported(source: String): Boolean {
        var fence: Char? = null
        var fenceSize = 0
        val outside = mutableListOf<String>()
        source.lineSequence().forEach { line ->
            val match = Regex("^ {0,3}(`{3,}|~{3,})").find(line)
            if (fence == null && match != null) {
                fence = match.groupValues[1].first(); fenceSize = match.groupValues[1].length
            } else if (fence != null) {
                if (match != null && match.groupValues[1].first() == fence && match.groupValues[1].length >= fenceSize && line.substring(match.range.last + 1).isBlank()) fence = null
            } else outside.add(line)
        }
        if (fence != null) return true
        val text = tablesAsText(outside).joinToString("\n").replace(Regex("`+[^`\\n]*`+"), "")
        return Regex("(?m)(^ {0,3}<|^ {0,3}\\[.+\\]:|^ {0,3}\\$\\$|^ {0,3}:::|^ {0,3}\\|.*\\||^ {0,3}[-*+] \\[|\\{[#.:]|!\\[|<[/!a-zA-Z])").containsMatchIn(text)
    }

    /** Las tablas GFM bien formadas son editables en Visual; su contenido de celdas se sigue revisando. */
    private fun tablesAsText(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            var end = index
            while (end < lines.size && tableRow.matches(lines[end])) end++
            val block = lines.subList(index, end)
            val columns = block.firstOrNull()?.let { cells(it).size } ?: 0
            if (block.size >= 2 && tableDelimiter.matches(block[1]) && block.all { cells(it).size == columns }) {
                block.forEachIndexed { row, line -> if (row != 1) result.add("  " + cells(line).joinToString(" ") { it.trim() }) }
                index = end
            } else { result.add(lines[index]); index++ }
        }
        return result
    }
}
