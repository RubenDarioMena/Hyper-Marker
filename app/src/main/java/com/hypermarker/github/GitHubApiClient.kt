package com.hypermarker.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.math.min

data class GitHubResponse(val status: Int, val headers: Map<String, String>, val body: ByteArray) {
    fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
    fun json() = JSONObject(body.toString(Charsets.UTF_8))
}

interface GitHubTransport {
    fun get(path: String, token: String?, etag: String?, maxBytes: Long, onBytes: (Int) -> Unit): GitHubResponse
}

class HttpsGitHubTransport : GitHubTransport {
    override fun get(path: String, token: String?, etag: String?, maxBytes: Long, onBytes: (Int) -> Unit): GitHubResponse {
        require(path.startsWith('/') && !path.startsWith("//") && '#' !in path && '\\' !in path && ".." !in path)
        val url = URL("https://api.github.com$path")
        require(url.host == "api.github.com" && url.protocol == "https")
        val connection = url.openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "Hyper-Marker-Android")
        if (!token.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
        if (etag != null) connection.setRequestProperty("If-None-Match", etag)
        try {
            val status = connection.responseCode
            val headers = connection.headerFields.filterKeys { it != null }.mapValues { it.value.joinToString(", ") }
            val declared = connection.contentLengthLong
            if (declared > maxBytes) throw ResourceBlocked("Respuesta demasiado grande")
            val input = if (status in 200..299 || status == 304) connection.inputStream else connection.errorStream
            val output = java.io.ByteArrayOutputStream()
            input?.use { stream ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val allowance = minOf(buffer.size.toLong(), maxBytes - output.size()).toInt()
                    if (allowance <= 0) {
                        if (declared == output.size().toLong()) break
                        throw ResourceBlocked("Respuesta alcanzó el límite de datos")
                    }
                    val count = stream.read(buffer, 0, allowance)
                    if (count < 0) break
                    if (count > maxBytes - output.size()) throw ResourceBlocked("Respuesta demasiado grande")
                    onBytes(count)
                    output.write(buffer, 0, count)
                }
            }
            return GitHubResponse(status, headers, output.toByteArray())
        } finally { connection.disconnect() }
    }
}

/** Todas las llamadas GitHub pasan por este cliente: reserva, medición y pausa remota. */
class GitHubApiClient(
    private val governor: ResourceGovernor,
    private val transport: GitHubTransport = HttpsGitHubTransport(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    suspend fun get(path: String, token: String?, folderId: String? = null, etag: String? = null,
        maxBytes: Long = 14L * 1024 * 1024, authProbe: Boolean = false): GitHubResponse = mutex.withLock {
        withContext(Dispatchers.IO) {
            val allowance = minOf(maxBytes, governor.remainingBytes())
            if (allowance <= 0L) governor.reserveBytes(1) // Registrar el bloqueo sin abrir una conexión.
            governor.reserveRequest(folderId, authenticated = !token.isNullOrBlank(), probe = authProbe)
            var status = -1
            try {
                val response = transport.get(path, token, etag, allowance) { governor.reserveBytes(it.toLong()) }
                status = response.status
                val retry = retryAt(response)
                if (status == 401 && token != null) governor.suspendAuthentication(true)
                if (status == 200 && authProbe) governor.suspendAuthentication(false)
                governor.recordResponse(status, retryAfter = retry, headers = response.headers)
                when (status) {
                    200, 304 -> response
                    401 -> throw GitHubApiException(status, "La credencial ya no es válida. Revisa GitHub; la copia offline sigue disponible")
                    403 -> throw GitHubApiException(status, if (retry > 0) "GitHub limitó las peticiones" else "Sin permiso para este repositorio")
                    404 -> throw GitHubApiException(status, "No se encontró la fuente o el token no tiene acceso")
                    429 -> throw GitHubApiException(status, "GitHub pidió esperar antes de reintentar")
                    else -> throw GitHubApiException(status, "GitHub respondió con HTTP $status")
                }
            } catch (error: Exception) {
                if (status == -1) governor.recordResponse(-1)
                throw error
            }
        }
    }

    private fun retryAt(response: GitHubResponse): Long {
        if (response.status !in listOf(403, 429)) return 0
        val rateLimited = response.status == 429 || response.header("X-RateLimit-Remaining")?.trim() == "0" ||
            response.header("Retry-After") != null || response.body.toString(Charsets.UTF_8).contains("rate limit", true)
        if (!rateLimited) return 0
        val now = clock()
        val retryValue = response.header("Retry-After")?.trim()
        val retry = retryValue?.toLongOrNull()?.let { now + min(it, 86_400) * 1000 }
            ?: retryValue?.let { runCatching { java.time.ZonedDateTime.parse(it, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() }
        val reset = response.header("X-RateLimit-Reset")?.trim()?.toLongOrNull()?.let { it * 1000 }
        return maxOf(now + 60_000, retry ?: 0, reset ?: 0)
    }
}

class GitHubApiException(val status: Int, message: String) : Exception(message)
