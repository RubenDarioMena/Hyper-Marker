package com.hypermarker.browser

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.Base64

/** Convierte el MHTML de Chromium en un proyecto privado servido por el lector habitual.
 * Conserva el archivo MHTML aparte; ninguna ruta del MIME se usa como ruta de disco.
 */
object WebSnapshot {
    const val MAX_ARCHIVE = 32L * 1024 * 1024
    private const val MAX_PART = 10 * 1024 * 1024
    private data class Part(val headers: Map<String, String>, val bytes: ByteArray) {
        val mime get() = headers["content-type"].orEmpty().substringBefore(';').trim().lowercase()
        val location get() = headers["content-location"].orEmpty()
    }

    fun extract(archive: File, destination: File, source: String) {
        require(WebAddress.permitted(source)) { "Origen HTTPS inválido" }
        require(archive.length() in 1..MAX_ARCHIVE) { "La página supera el límite de 32 MiB" }
        val parts = mutableListOf<Part>()
        var total = 0L
        archive.bufferedReader(Charsets.ISO_8859_1).use { reader ->
            fun headers(): Map<String, String> {
                val lines = mutableListOf<String>()
                var size = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    size += line.length
                    require(size <= 32_768) { "Cabecera web demasiado grande" }
                    if (line.startsWith(' ') || line.startsWith('\t')) {
                        require(lines.isNotEmpty()); lines[lines.lastIndex] += " " + line.trim()
                    } else lines += line
                }
                return lines.associate { it.substringBefore(':').lowercase() to it.substringAfter(':', "").trim() }
            }
            val top = headers()
            require(top["content-type"].orEmpty().startsWith("multipart/related", true)) { "WebView no produjo un archivo web compatible" }
            val boundary = Regex("boundary\\s*=\\s*(?:\"([^\"]+)\"|([^;\\s]+))", RegexOption.IGNORE_CASE)
                .find(top["content-type"].orEmpty())?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            require(!boundary.isNullOrBlank() && boundary.length <= 200) { "Archivo web sin delimitador" }
            val delimiter = "--$boundary"
            var line = reader.readLine()
            while (line != null && line != delimiter) line = reader.readLine()
            var closed = false
            while (line == delimiter) {
                require(parts.size < 2000) { "La página contiene demasiados recursos" }
                val head = headers()
                val body = StringBuilder()
                line = reader.readLine()
                while (line != null && line != delimiter && line != "$delimiter--") {
                    require(body.length + line.length + 2 <= MAX_PART * 2) { "Recurso web demasiado grande" }
                    body.append(line).append("\r\n")
                    line = reader.readLine()
                }
                val bytes = when (head["content-transfer-encoding"].orEmpty().lowercase()) {
                    "base64" -> Base64.getMimeDecoder().decode(body.toString())
                    "quoted-printable" -> quotedPrintable(body.toString())
                    "", "7bit", "8bit", "binary" -> body.toString().removeSuffix("\r\n").toByteArray(Charsets.ISO_8859_1)
                    else -> error("Codificación de archivo web no compatible")
                }
                total += bytes.size
                require(bytes.size <= MAX_PART && total <= MAX_ARCHIVE) { "La página supera los límites de descarga" }
                parts += Part(head, bytes)
                if (line == "$delimiter--") { closed = true; break }
            }
            require(closed) { "La descarga web está incompleta" }
        }
        require(parts.isNotEmpty() && parts.first().mime in listOf("text/html", "application/xhtml+xml")) { "La página no contiene HTML guardable" }
        val names = parts.mapIndexed { index, part ->
            if (index == 0) "index.html" else "assets/resource-$index." + when (part.mime) {
                "text/html", "application/xhtml+xml" -> "html"; "text/css" -> "css"; "image/svg+xml" -> "svg"
                "image/png" -> "png"; "image/jpeg" -> "jpg"; "image/webp" -> "webp"; "image/gif" -> "gif"
                "font/woff2" -> "woff2"; "font/woff" -> "woff"; else -> "bin"
            }
        }
        val locations = mutableMapOf<String, String>()
        parts.forEachIndexed { index, part ->
            if (part.location.isNotEmpty()) locations.putIfAbsent(absolute(source, part.location), names[index])
            part.headers["content-id"]?.trim('<', '>')?.let { locations["cid:$it"] = names[index] }
        }
        locations[absolute(source, source)] = names.first()
        destination.mkdirs()
        parts.forEachIndexed { index, part ->
            val file = File(destination, names[index]); file.parentFile!!.mkdirs()
            if (part.mime in listOf("text/html", "application/xhtml+xml", "text/css", "image/svg+xml")) {
                val charset = Regex("charset\\s*=\\s*[\"']?([^;\\s\"']+)", RegexOption.IGNORE_CASE).find(part.headers["content-type"].orEmpty())?.groupValues?.get(1)
                val text = part.bytes.toString(runCatching { charset?.let { java.nio.charset.Charset.forName(it) } ?: Charsets.UTF_8 }.getOrDefault(Charsets.UTF_8))
                val base = part.location.ifBlank { source }
                fun local(value: String): String {
                    if (value.startsWith('#') || value.startsWith("data:", true)) return value
                    val decoded = value.replace("&amp;", "&")
                    val absolute = absolute(base, decoded)
                    val path = locations[absolute.substringBefore('#')]
                    return if (path != null) (if (index == 0) "" else "../") + path + absolute.substringAfter('#', "").let { if (it.isEmpty()) "" else "#$it" }
                        else absolute // faltantes permanecen remotos; el lector offline los bloquea
                }
                var rewritten = Regex("url\\(\\s*([\"']?)(.*?)\\1\\s*\\)", setOf(RegexOption.IGNORE_CASE)).replace(text) { "url(\"${local(it.groupValues[2])}\")" }
                rewritten = Regex("(@import\\s+)([\"'])(.*?)\\2", RegexOption.IGNORE_CASE).replace(rewritten) { it.groupValues[1] + "\"" + local(it.groupValues[3]) + "\"" }
                if (part.mime != "text/css") {
                    // El serializador de Chromium emite atributos entre comillas. srcset puede apuntar a recursos aún no cargados.
                    rewritten = Regex("(\\s+srcset\\s*=\\s*)([\"'])(.*?)\\2", RegexOption.IGNORE_CASE).replace(rewritten) { match ->
                        val candidates = match.groupValues[3].split(',').mapNotNull { candidate ->
                            val items = candidate.trim().split(Regex("\\s+"), limit = 2)
                            val rewrittenUrl = local(items.first())
                            if (rewrittenUrl.startsWith("assets/") || rewrittenUrl.startsWith("../assets/"))
                                rewrittenUrl + items.getOrNull(1)?.let { " $it" }.orEmpty()
                            else null
                        }
                        if (candidates.isEmpty()) "" else " srcset=\"${candidates.joinToString(", ")}\""
                    }
                    rewritten = Regex("(<base\\b[^>]*>|<meta\\b[^>]*(?:charset\\s*=|http-equiv\\s*=)[^>]*>)", RegexOption.IGNORE_CASE).replace(rewritten, "")
                    rewritten = Regex("(\\b(?:src|href|poster|background|data|xlink:href)\\s*=\\s*)([\"'])(.*?)\\2", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(rewritten) {
                        it.groupValues[1] + "\"" + local(it.groupValues[3]).replace("\"", "%22").replace("<", "%3C").replace("&", "&amp;") + "\""
                    }
                    // La declaración va primero incluso si el documento usaba otro charset.
                    if (part.mime != "image/svg+xml") rewritten = "<meta charset=\"utf-8\">" + rewritten
                }
                file.writeText(rewritten)
            } else file.writeBytes(part.bytes)
        }
    }

    private fun absolute(base: String, value: String): String = runCatching { URI(base).resolve(value).toString() }.getOrDefault(value)
    private fun quotedPrintable(value: String): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            if (value[i] == '=') {
                if (value.startsWith("=\r\n", i)) { i += 3; continue }
                require(i + 2 < value.length) { "Archivo web truncado" }
                val byte = value.substring(i + 1, i + 3).toIntOrNull(16) ?: error("Archivo web mal codificado")
                out.write(byte); i += 3
            } else { out.write(value[i].code); i++ }
        }
        return out.toByteArray()
    }
}
