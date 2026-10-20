package com.hypermarker.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.hypermarker.security.PathGuard
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID

data class ImportLimits(val files: Int = 10_000, val perFile: Long = 100L * 1024 * 1024,
    val total: Long = 500L * 1024 * 1024, val ratio: Long = 200)
data class PendingImport(val id: String, val directory: File, val title: String, val entries: List<String>, val preferred: String?)

class ImportBudget(private val root: File, private val limits: ImportLimits) {
    private var total = 0L
    private var count = 0
    private val paths = mutableSetOf<String>()
    fun destination(path: String, directory: Boolean = false): File {
        val normalized = PathGuard.normalize(path)
        require(++count <= limits.files) { "Demasiados archivos" }
        require(paths.add(normalized.lowercase(Locale.ROOT))) { "Nombres duplicados en el proyecto: $path" }
        return PathGuard.resolve(root, normalized).also {
            if (directory) require(it.mkdirs() || it.isDirectory) { "No se pudo crear directorio" }
            else { require(!it.exists()) { "Colisión de archivo" }; it.parentFile!!.mkdirs() }
        }
    }
    suspend fun copy(input: InputStream, destination: File): Long {
        var size = 0L
        destination.outputStream().use { out ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                size += read; total += read
                require(size <= limits.perFile && total <= limits.total) { "El proyecto supera los límites de tamaño" }
                out.write(buffer, 0, read)
            }
        }
        return size
    }
}

class SafeImporter(private val context: Context, private val limits: ImportLimits = ImportLimits()) {
    private val stagingRoot get() = File(context.filesDir, "imports").apply { mkdirs() }
    fun displayName(uri: Uri): String = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: "documento.html"

    suspend fun prepare(uris: List<Uri>, tree: Boolean = false): PendingImport {
        require(uris.isNotEmpty()) { "No se recibió ningún archivo" }
        val id = UUID.randomUUID().toString()
        val operation = File(stagingRoot, id).apply { mkdirs() }
        val root = File(operation, "original").apply { mkdirs() }
        val budget = ImportBudget(root, limits)
        try {
            val title = if (tree) DocumentFile.fromTreeUri(context, uris.first())?.name ?: "Proyecto" else displayName(uris.first()).substringBeforeLast('.')
            if (tree) {
                val node = requireNotNull(DocumentFile.fromTreeUri(context, uris.first())) { "No se pudo abrir la carpeta" }
                copyTree(node, "", budget)
            } else if (uris.size == 1 && displayName(uris.first()).endsWith(".zip", true)) {
                val archive = File(operation, "bundle.zip")
                context.contentResolver.openInputStream(uris.first()).use { input ->
                    requireNotNull(input) { "No se pudo abrir el ZIP" }
                    ImportBudget(operation, limits.copy(perFile = limits.total)).copy(input, archive)
                }
                extractZip(archive, root, limits)
                archive.delete()
            } else {
                for (uri in uris) {
                    val target = budget.destination(displayName(uri))
                    context.contentResolver.openInputStream(uri).use { input -> budget.copy(requireNotNull(input) { "No se pudo leer archivo" }, target) }
                }
            }
            val entries = root.walkTopDown().filter { it.isFile && (it.extension.equals("html", true) || it.extension.equals("htm", true)) }
                .map { it.relativeTo(root).invariantSeparatorsPath }.sorted().toList()
            require(entries.isNotEmpty()) { "No se encontró HTML; importa la carpeta o el ZIP que contiene index.html" }
            val indexes = entries.filter { it.substringAfterLast('/').equals("index.html", true) }
            val preferred = entries.firstOrNull { it.equals("index.html", true) }
                ?: indexes.singleOrNull()?.takeIf { candidate -> entries.all { it.startsWith(candidate.substringBeforeLast('/', "") + "/") } }
                ?: entries.singleOrNull()
            return PendingImport(id, operation, title.take(200), entries, preferred)
        } catch (error: Throwable) { operation.deleteRecursively(); throw error }
    }

    private suspend fun copyTree(node: DocumentFile, prefix: String, budget: ImportBudget) {
        currentCoroutineContext().ensureActive()
        for (child in node.listFiles()) {
            val path = prefix + requireNotNull(child.name) { "Archivo sin nombre" }
            if (child.isDirectory) { budget.destination(path, true); copyTree(child, "$path/", budget) }
            else {
                val destination = budget.destination(path)
                context.contentResolver.openInputStream(child.uri).use { budget.copy(requireNotNull(it), destination) }
            }
        }
    }

    companion object {
        suspend fun extractZip(archive: File, root: File, limits: ImportLimits = ImportLimits()) {
            val budget = ImportBudget(root, limits)
            @Suppress("DEPRECATION")
            ZipFile(archive).use { zip ->
                val entries = zip.entries
                while (entries.hasMoreElements()) {
                    currentCoroutineContext().ensureActive()
                    val entry = entries.nextElement()
                    require(!entry.isUnixSymlink && (entry.unixMode and 0xF000 == 0 || entry.unixMode and 0xF000 in listOf(0x8000, 0x4000))) { "Enlaces o entradas especiales no permitidas" }
                    require(zip.canReadEntryData(entry)) { "Formato de entrada ZIP no soportado" }
                    val target = budget.destination(entry.name, entry.isDirectory)
                    if (!entry.isDirectory) {
                        var crc = 0L
                        val size = java.util.zip.CheckedInputStream(zip.getInputStream(entry), java.util.zip.CRC32()).use { stream ->
                            val bytes = budget.copy(stream, target); crc = stream.checksum.value; bytes
                        }
                        require(entry.crc >= 0 && crc == entry.crc) { "El ZIP contiene datos corruptos (CRC)" }
                        require(size <= maxOf(1L, entry.compressedSize) * limits.ratio) { "Relación de compresión excesiva" }
                    }
                }
            }
        }
    }
}
