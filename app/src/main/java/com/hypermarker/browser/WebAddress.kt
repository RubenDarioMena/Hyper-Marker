package com.hypermarker.browser

import java.net.URI
import java.net.URLEncoder

/** La barra acepta direcciones HTTPS o búsquedas; nunca transforma esquemas privilegiados en navegación. */
object WebAddress {
    fun permitted(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            !uri.host.equals("hypermarker.invalid", true) && !uri.host.endsWith(".hypermarker.invalid", true)
    }.getOrDefault(false)

    fun resolve(input: String): String {
        val value = input.trim()
        require(value.isNotEmpty() && value.length <= 8192) { "Escribe una dirección o una búsqueda" }
        if (value.contains(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")) || Regex("^(?i:file|content|data|javascript|intent|about):").containsMatchIn(value)) {
            require(permitted(value)) { "Sólo se pueden abrir direcciones HTTPS sin credenciales" }
            return value
        }
        if (value.none { it.isWhitespace() } && (value.substringBefore('/').contains('.') || value.startsWith("localhost"))) {
            val url = "https://$value"
            require(permitted(url)) { "Dirección HTTPS inválida" }
            return url
        }
        return "https://duckduckgo.com/?q=" + URLEncoder.encode(value, "UTF-8")
    }
}
