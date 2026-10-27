package com.hypermarker.github

import android.util.Base64
import androidx.room.withTransaction
import com.hypermarker.security.PathGuard
import com.hypermarker.storage.Document
import com.hypermarker.storage.Folder
import com.hypermarker.storage.ImportBudget
import com.hypermarker.storage.ImportLimits
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.storage.RemoteEntry
import com.hypermarker.storage.RemoteFolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import android.os.SystemClock
import android.os.Process
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest
import java.util.UUID

private data class TreeEntry(val path: String, val type: String, val mode: String, val sha: String, val size: Long)

class GitHubSyncEngine(
    private val library: LibraryRepository,
    private val api: GitHubApiClient,
    private val auth: GitHubAuthStore,
    private val governor: ResourceGovernor,
    private val availableBytes: () -> Long = { library.context.filesDir.usableSpace },
    private val storageLimit: Long = 500L * 1024 * 1024,
    private val beforeCommit: suspend () -> Unit = {},
    private val afterCommit: suspend () -> Unit = {},
) {
    private val mutex = Mutex()
    @Volatile private var activeJob: Job? = null
    @Volatile private var activeId: String? = null
    fun cancelActive(id: String? = null) { if (id == null || id == activeId) activeJob?.cancel() }
    private val shaPattern = Regex("[a-f0-9]{40}")
    private val htmlExtensions = setOf("html", "htm")
    private val assetExtensions = setOf("css", "js", "mjs", "json", "png", "jpg", "jpeg", "gif", "svg", "webp", "avif", "ico", "woff", "woff2", "ttf", "otf", "wasm", "txt")
    private fun identity(folder: String, path: String) = UUID.nameUUIDFromBytes("$folder:$path".toByteArray()).toString()

    suspend fun add(repo: GitHubRepo, branch: String, path: String, parent: String?): RemoteFolder = library.remoteMutation {
        require(branch.isNotBlank() && branch.length <= 255 && branch.split('/').all { it.isNotBlank() && it != "." && it != ".." })
        val canonical = if (path.isBlank()) "" else PathGuard.normalize(path)
        require(library.dao.remoteFolders().none { it.repositoryId == repo.id && it.branch == branch && it.repoPath == canonical }) {
            "Esa rama y carpeta ya están en la biblioteca"
        }
        require(parent == null || library.dao.folder(parent) != null)
        val folderId = UUID.randomUUID().toString()
        val source = RemoteFolder(UUID.randomUUID().toString(), folderId, repo.id, repo.owner, repo.name, branch, canonical)
        library.database.withTransaction {
            library.dao.put(Folder(folderId, parent, "${repo.name} · ${canonical.ifBlank { "/" }}".take(200)))
            library.dao.put(source)
        }
        source
    }

    suspend fun sync(id: String, manual: Boolean = false, background: Boolean = false): String {
        if (!mutex.tryLock()) return "Ya hay una sincronización en curso; se conserva la copia local"
        activeId = id; activeJob = currentCoroutineContext()[Job]
        val started = SystemClock.elapsedRealtime()
        val cpu = Process.getElapsedCpuTime()
        val battery = library.context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        var outcome = "ERROR"
        reusedBytes = 0; publishedFiles = 0
        try {
            library.recover()
            governor.beginRun()
            val message = withTimeout(5 * 60_000L) { library.remoteMutation { syncOnce(id, manual) } }
            outcome = "OK"
            return message
        } catch (e: CancellationException) { outcome = "CANCELLED"; throw e }
        catch (e: Exception) {
            outcome = when (e) {
                is GitHubApiException -> "HTTP_${e.status}"
                is ResourceBlocked -> "BUDGET_OR_PAUSE"
                is java.io.IOException -> "NETWORK"
                is IllegalArgumentException -> "VALIDATION"
                else -> "ERROR"
            }
            throw e
        }
        finally {
            governor.endRun()
            try {
                governor.recordRun(SyncResourceRun(System.currentTimeMillis(), id,
                    SystemClock.elapsedRealtime() - started, (Process.getElapsedCpuTime() - cpu).coerceAtLeast(0),
                    outcome, reusedBytes, publishedFiles, background,
                    if (level >= 0 && scale > 0) level * 100 / scale else null,
                    battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.let { it != 0 }))
            } finally { activeJob = null; activeId = null; mutex.unlock() }
        }
    }
    private var reusedBytes = 0L
    private var publishedFiles = 0

    private suspend fun syncOnce(id: String, manual: Boolean): String = withContext(Dispatchers.IO) {
        val dao = library.dao
        val source = requireNotNull(dao.remoteFolder(id)) { "La carpeta GitHub ya no existe" }
        source.htmlDocumentId?.let { document -> source.latestCommitSha?.let { commit ->
            if (File(library.directory(document), "remote-versions/$commit").isDirectory)
                library.installRemoteVersion(document, commit)
        } }
        val now = System.currentTimeMillis()
        if (manual && source.lastAttempt > 0 && now - source.lastAttempt < 60_000)
            throw ResourceBlocked("Espera 60 segundos entre actualizaciones manuales")
        if (now < source.nextRetryAt) throw ResourceBlocked("La fuente está en pausa hasta ${source.nextRetryAt}", source.nextRetryAt)
        if (!manual && source.lastCheck > 0 && now - source.lastCheck < 10 * 60_000)
            return@withContext "La copia local está al día según el intervalo de apertura"
        dao.put(source.copy(status = "SYNCING", lastAttempt = now))
        val stage = File(library.context.filesDir, "remote-sync/$id").apply { deleteRecursively(); mkdirs() }
        try {
            val base = "/repos/${segment(source.owner)}/${segment(source.repo)}"
            val token = auth.read()
            val branchPath = source.branch.split('/').joinToString("/") { segment(it) }
            val refResponse = api.get("$base/git/ref/heads/$branchPath", token, id,
                etag = source.refEtag, maxBytes = 128_000)
            if (refResponse.status == 304) {
                dao.put(source.copy(status = "CURRENT", lastAttempt = now, lastCheck = now, failureCount = 0, nextRetryAt = 0))
                return@withContext "Sin cambios; se conservó la copia local"
            }
            val ref = refResponse.json()
            val refEtag = refResponse.header("ETag")
            val commit = ref.getJSONObject("object").getString("sha")
            require(commit.matches(shaPattern)) { "GitHub entregó una revisión inválida" }
            if (commit == source.latestCommitSha) {
                dao.put(source.copy(status = "CURRENT", lastAttempt = now, lastCheck = now, refEtag = refEtag,
                    failureCount = 0, nextRetryAt = 0))
                return@withContext "Sin cambios; se conservó la copia local"
            }
            val treeSha = api.get("$base/git/commits/$commit", token, id, maxBytes = 256_000)
                .json().getJSONObject("tree").getString("sha")
            require(treeSha.matches(shaPattern))
            val entries = enumerate(base, treeSha, source.repoPath, token, id)
            val html = entries.filter { it.path.substringAfterLast('.').lowercase() in htmlExtensions }
            val included = entries.filter { entry ->
                val extension = entry.path.substringAfterLast('.', "").lowercase()
                extension in setOf("md", "markdown") || (html.isNotEmpty() && (extension in htmlExtensions || extension in assetExtensions))
            }
            require(included.size <= 10_000) { "Demasiados archivos en la carpeta" }
            require(included.all { it.size in 0..governor.limits.fileBytes }) { "Un archivo supera el límite de ${governor.limits.fileBytes / 1024 / 1024} MiB" }
            require(included.sumOf { it.size } <= governor.limits.bytesPerRun) { "La carpeta supera ${governor.limits.bytesPerRun / 1024 / 1024} MiB por sincronización" }
            val old = dao.remoteEntries(id).associateBy { it.path }
            val previousHtml = source.htmlDocumentId?.let { library.original(it) }
            val budget = ImportBudget(stage, ImportLimits(files = 10_000, perFile = governor.limits.fileBytes,
                total = governor.limits.bytesPerRun))
            for (item in included) {
                currentCoroutineContext().ensureActive()
                val dest = budget.destination(item.path)
                val previous = old[item.path]?.takeIf { it.sha == item.sha && it.state == "ACTIVE" }
                val cached = if (previous == null) null else if (previous.documentId != null && previous.documentId != source.htmlDocumentId)
                    dao.document(previous.documentId)?.entryPath?.let { PathGuard.resolve(library.directory(previous.documentId), it) }
                else previousHtml?.let { PathGuard.resolve(it, item.path) }
                val bytes = if (cached?.isFile == true) { reusedBytes += item.size; cached.readBytes() } else blob(base, item.sha, item.size, token, id)
                require(bytes.size.toLong() == item.size && gitBlobSha(bytes) == item.sha) { "El archivo cambió durante la descarga" }
                budget.copy(bytes.inputStream(), dest)
            }
            validateLocalAssets(stage, included)
            val stagedBytes = stage.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val remoteDocumentIds = dao.remoteFolders().flatMap { remote ->
                listOfNotNull(remote.htmlDocumentId) + dao.remoteEntries(remote.id).mapNotNull { it.documentId }
            }.toSet()
            val storedBytes = remoteDocumentIds.sumOf { docId -> library.directory(docId).walkTopDown().filter { it.isFile }.sumOf { it.length() } }
            require(storedBytes + stagedBytes * 2 <= storageLimit) { "El almacenamiento remoto alcanzó el límite de 500 MiB" }
            require(availableBytes() > stagedBytes * 2 + 20L * 1024 * 1024) { "No hay espacio suficiente para conservar el snapshot anterior" }
            val htmlEntry = html.map { it.path }.sortedWith(compareBy<String> { it.substringAfterLast('/') != "index.html" }.thenBy { it }).firstOrNull()
            val htmlId = if (htmlEntry == null) null else source.htmlDocumentId ?: identity(id, "html-package")
            val installed = mutableListOf<File>()
            var committed = false
            try {
                // Copias privadas terminadas antes de cambiar cualquier fila visible de Room.
                if (htmlId != null) {
                    val version = File(library.directory(htmlId), "remote-versions/$commit")
                    require(!version.exists())
                    installed += version
                    version.parentFile!!.mkdirs()
                    require(stage.copyRecursively(version, overwrite = false))
                }
                val markdown = included.filter { it.path.substringAfterLast('.').lowercase() in setOf("md", "markdown") }
                for (item in markdown) {
                    require(item.size <= 2L * 1024 * 1024) { "Una nota Markdown supera 2 MiB" }
                    val docId = old[item.path]?.documentId ?: identity(id, item.path)
                    val version = File(library.directory(docId), "versions/$commit/source.md")
                    require(!version.exists())
                    installed += version.parentFile!!
                    version.parentFile!!.mkdirs()
                    File(stage, item.path).copyTo(version)
                }
                beforeCommit()
                currentCoroutineContext().ensureActive()
                library.database.withTransaction {
                    if (htmlId != null && htmlEntry != null) {
                        val prior = dao.document(htmlId)
                        dao.put(Document(htmlId, source.folderId, "${source.repo} · HTML", "HTML", htmlEntry,
                            createdAt = prior?.createdAt ?: now, updatedAt = now, revision = (prior?.revision ?: 0) + 1,
                            networkAllowed = prior?.networkAllowed ?: false))
                    }
                    for (item in markdown) {
                        val docId = old[item.path]?.documentId ?: identity(id, item.path)
                        val prior = dao.document(docId)
                        dao.put(Document(docId, source.folderId, item.path.substringAfterLast('/'), "REMOTE_MARKDOWN",
                            "versions/$commit/source.md", createdAt = prior?.createdAt ?: now,
                            updatedAt = now, revision = (prior?.revision ?: 0) + 1))
                    }
                    dao.clearRemoteEntries(id)
                    for (item in included) {
                        val docId = if (item.path.substringAfterLast('.').lowercase() in setOf("md", "markdown"))
                            old[item.path]?.documentId ?: identity(id, item.path) else htmlId
                        dao.put(RemoteEntry(identity(id, item.path), id, item.path, item.sha, item.size, docId))
                    }
                    for (prior in old.values.filter { it.path !in included.map { item -> item.path }.toSet() })
                        dao.put(prior.copy(state = "RETIRED"))
                    dao.put(source.copy(latestCommitSha = commit, status = "CURRENT", lastAttempt = now,
                        lastSuccess = now, lastCheck = now, htmlDocumentId = htmlId, htmlEntryPath = htmlEntry,
                        refEtag = refEtag, failureCount = 0, nextRetryAt = 0))
                }
                committed = true
                afterCommit()
                publishedFiles = included.size
                if (htmlId != null) library.installRemoteVersion(htmlId, commit)
                // HTML previo puede seguir abierto. Se limpia en el siguiente arranque bajo el mutex compartido.
                markdown.forEach { item ->
                    val docId = old[item.path]?.documentId ?: identity(id, item.path)
                    File(library.directory(docId), "versions").listFiles()
                        ?.filter { it.name != commit }?.forEach { it.deleteRecursively() }
                }
                "Actualizado: ${markdown.size} Markdown y ${html.size} HTML"
            } catch (error: Throwable) {
                if (!committed) installed.forEach { it.deleteRecursively() }
                throw error
            }
        } catch (error: Exception) {
            val state = when (error) {
                is GitHubApiException -> when (error.status) { 401 -> "AUTH"; 403, 429 -> "PAUSED"; else -> "ERROR" }
                is ResourceBlocked -> "PAUSED"
                is CancellationException -> "PENDING"
                is java.io.IOException -> "OFFLINE"
                else -> "ERROR"
            }
            withContext(NonCancellable) { dao.remoteFolder(id)?.let { current ->
                val failures = (current.failureCount + 1).coerceAtMost(10)
                val delay = (60_000L shl (failures - 1).coerceAtMost(5)) + (id.hashCode().toLong().and(0x7fff) % 30_000)
                val retryAt = when {
                    error is GitHubApiException && error.status == 401 -> Long.MAX_VALUE
                    error is ResourceBlocked && error.until > now -> error.until
                    else -> now + delay
                }
                dao.put(current.copy(status = if (failures >= 3) "PAUSED" else state,
                    failureCount = failures, nextRetryAt = retryAt, lastAttempt = now))
            }
            }
            throw error
        } finally { stage.deleteRecursively() }
    }

    private suspend fun enumerate(base: String, rootSha: String, folderPath: String, token: String?, folderId: String): List<TreeEntry> {
        suspend fun tree(sha: String): List<TreeEntry> {
            val json = api.get("$base/git/trees/$sha", token, folderId, maxBytes = 2L * 1024 * 1024).json()
            require(!json.optBoolean("truncated", false)) { "Árbol GitHub truncado; elige una carpeta más pequeña" }
            val array: JSONArray = json.getJSONArray("tree")
            return (0 until array.length()).map { index ->
                val value = array.getJSONObject(index)
                TreeEntry(value.getString("path"), value.getString("type"), value.getString("mode"),
                    value.getString("sha"), value.optLong("size", 0))
            }
        }
        var selected = rootSha
        for (part in folderPath.split('/').filter { it.isNotBlank() }) {
            val child = tree(selected).singleOrNull { it.path == part && it.type == "tree" }
                ?: throw IllegalArgumentException("La carpeta elegida no existe en esta rama")
            require(child.mode == "040000" && child.sha.matches(shaPattern)) { "Carpeta Git inválida" }
            selected = child.sha
        }
        val result = mutableListOf<TreeEntry>()
        val names = mutableSetOf<String>()
        var visited = 0
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add("" to selected)
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (prefix, sha) = queue.removeFirst()
            require(++visited <= 10_000) { "Demasiados directorios en la carpeta" }
            for (entry in tree(sha)) {
                val path = PathGuard.normalize(prefix + entry.path)
                require(names.add(java.text.Normalizer.normalize(path, java.text.Normalizer.Form.NFC).lowercase(java.util.Locale.ROOT))) { "Colisión de rutas remotas" }
                require(entry.path.isNotEmpty() && '/' !in entry.path && entry.mode in setOf("040000", "100644", "100755") && entry.type != "commit") { "Symlink o submódulo no permitido: $path" }
                require(entry.sha.matches(shaPattern))
                when (entry.type) {
                    "tree" -> { require(entry.mode == "040000"); queue.add("$path/" to entry.sha) }
                    "blob" -> { require(entry.mode in setOf("100644", "100755")); result += entry.copy(path = path) }
                    else -> throw IllegalArgumentException("Tipo Git no permitido")
                }
                require(result.size + queue.size <= 10_000) { "Demasiados elementos en la carpeta" }
            }
        }
        return result
    }

    private suspend fun blob(base: String, sha: String, size: Long, token: String?, folderId: String): ByteArray {
        val response = api.get("$base/git/blobs/$sha", token, folderId,
            maxBytes = minOf(28L * 1024 * 1024, (size * 4 / 3) + 200_000))
        val json = response.json()
        require(json.getString("encoding") == "base64" && json.getString("sha") == sha)
        return Base64.decode(json.getString("content"), Base64.DEFAULT)
    }

    private fun gitBlobSha(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun validateLocalAssets(stage: File, files: List<TreeEntry>) {
        val linkPattern = Regex("(?:src|href)\\s*=\\s*['\"]([^'\"]+)['\"]|url\\(\\s*['\"]?([^)'\"]+)", RegexOption.IGNORE_CASE)
        for (entry in files.filter { it.path.substringAfterLast('.').lowercase() in setOf("html", "htm", "css") }) {
            val source = PathGuard.resolve(stage, entry.path)
            require(source.length() <= 5L * 1024 * 1024) { "HTML o CSS demasiado grande para validar recursos relativos" }
            val text = source.readText()
            for (match in linkPattern.findAll(text)) {
                val link = (match.groupValues[1].ifBlank { match.groupValues[2] }).trim()
                if (link.isBlank() || link.startsWith('#') || link.startsWith("data:") ||
                    link.startsWith("http://") || link.startsWith("https://") || link.startsWith("mailto:") ||
                    link.startsWith("javascript:")) continue
                require(!link.startsWith('/')) { "Una vista usa ruta absoluta incompatible: $link" }
                val withoutQuery = link.substringBefore('#').substringBefore('?')
                val path = source.parentFile!!.toPath().resolve(android.net.Uri.decode(withoutQuery)).normalize()
                require(path.startsWith(stage.canonicalFile.toPath()) && path.toFile().isFile) { "Falta un recurso relativo: $link" }
            }
        }
    }
}
