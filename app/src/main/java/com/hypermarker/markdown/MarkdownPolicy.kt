package com.hypermarker.markdown

object MarkdownPolicy {
    fun unsupported(source: String): Boolean {
        var fence: Char? = null
        var fenceSize = 0
        val outside = StringBuilder()
        source.lineSequence().forEach { line ->
            val match = Regex("^ {0,3}(`{3,}|~{3,})").find(line)
            if (fence == null && match != null) {
                fence = match.groupValues[1].first(); fenceSize = match.groupValues[1].length
            } else if (fence != null) {
                if (match != null && match.groupValues[1].first() == fence && match.groupValues[1].length >= fenceSize && line.substring(match.range.last + 1).isBlank()) fence = null
            } else outside.appendLine(line)
        }
        if (fence != null) return true
        val text = outside.toString().replace(Regex("`+[^`\\n]*`+"), "")
        return Regex("(?m)(^ {0,3}<|^ {0,3}\\[.+\\]:|^ {0,3}\\$\\$|^ {0,3}:::|^ {0,3}\\|.*\\||^ {0,3}[-*+] \\[|\\{[#.:]|!\\[|<[/!a-zA-Z])").containsMatchIn(text)
    }
}
