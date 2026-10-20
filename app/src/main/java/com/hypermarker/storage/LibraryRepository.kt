package com.hypermarker.storage

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.room.Room
import androidx.room.withTransaction
import com.hypermarker.security.FolderRules
import com.hypermarker.security.PathGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LibraryRepository(val context: Context, database: LibraryDatabase? = null) {
    val database = database ?: Room.databaseBuilder(context, LibraryDatabase::class.java, "library.db").build()
    val dao = this.database.dao()
    private val mutex = Mutex()
    private var recovered = false
    val importer = SafeImporter(context)
    private fun id() = UUID.randomUUID().toString()
    fun directory(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}"))) { "Identidad inválida" }
        return File(context.filesDir, "documents/$id")
    }
    fun original(id: String) = File(directory(id), "original")
    private fun markdown(id: String) = File(directory(id), "markdown/source.md")
    private fun name(value: String) = value.trim().also { require(it.isNotEmpty() && it.length <= 200) { "Usa un nombre de 1 a 200 caracteres" } }
    private suspend fun <T> write(block: suspend () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }
    private suspend fun checkParent(parent: String?) { require(parent == null || dao.folder(parent) != null) { "La carpeta ya no existe" } }

    suspend fun recover() = write {
        if (recovered) return@write
        File(context.filesDir, "imports").deleteRecursively()
        val existing = dao.documents().map { it.id }.toSet()
        File(context.filesDir, "documents").listFiles()?.filter { it.name !in existing }?.forEach { it.deleteRecursively() }
        recovered = true
    }
    suspend fun createFolder(parent: String?, title: String) = write { checkParent(parent); dao.put(Folder(id(), parent, name(title))) }
    suspend fun createMarkdown(parent: String?, title: String, source: String = ""): Document = write {
        checkParent(parent)
        val document = Document(id(), parent, name(title), "MARKDOWN")
        try { atomicWrite(markdown(document.id), source); dao.put(document); document }
        catch (error: Throwable) { directory(document.id).deleteRecursively(); throw error }
    }
    suspend fun readMarkdown(id: String) = withContext(Dispatchers.IO) {
        require(dao.document(id)?.type == "MARKDOWN")
        AtomicFile(markdown(id)).readFully().toString(Charsets.UTF_8)
    }
    suspend fun saveMarkdown(id: String, source: String) = write {
        require(source.toByteArray().size <= 2 * 1024 * 1024) { "La nota supera 2 MiB" }
        val doc = requireNotNull(dao.document(id))
        require(doc.type == "MARKDOWN")
        atomicWrite(markdown(id), source)
        dao.put(doc.copy(updatedAt = System.currentTimeMillis(), revision = doc.revision + 1))
    }
    private fun atomicWrite(file: File, value: String) {
        file.parentFile!!.mkdirs()
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(value.toByteArray()); atomic.finishWrite(stream) }
        catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }
    suspend fun importMarkdown(uri: Uri, parent: String?): Document {
        val source = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri).use { input ->
                val stream = requireNotNull(input)
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 2 * 1024 * 1024) { "La nota supera 2 MiB" }
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                require(bytes.size <= 2 * 1024 * 1024) { "La nota supera 2 MiB" }; bytes.toString(Charsets.UTF_8)
            }
        }
        return createMarkdown(parent, importer.displayName(uri).substringBeforeLast('.'), source)
    }
    suspend fun finishImport(pending: PendingImport, entry: String, parent: String?): Document = write {
        checkParent(parent)
        require(entry in pending.entries && PathGuard.resolve(File(pending.directory, "original"), entry).isFile)
        val doc = Document(pending.id, parent, name(pending.title), "HTML", entry)
        val destination = directory(doc.id)
        destination.parentFile!!.mkdirs()
        try {
            require(!destination.exists() && pending.directory.renameTo(destination)) { "No se pudo confirmar la importación" }
            dao.put(doc); doc
        } catch (error: Throwable) { destination.deleteRecursively(); pending.directory.deleteRecursively(); throw error }
    }
    suspend fun rename(id: String, folder: Boolean, title: String) = write {
        if (folder) dao.put(requireNotNull(dao.folder(id)).copy(name = name(title)))
        else dao.put(requireNotNull(dao.document(id)).copy(title = name(title), updatedAt = System.currentTimeMillis()))
    }
    suspend fun move(id: String, folder: Boolean, parent: String?) = write {
        checkParent(parent)
        if (folder) {
            FolderRules.validateMove(id, parent, dao.folders().associate { it.id to it.parentId })
            dao.put(requireNotNull(dao.folder(id)).copy(parentId = parent))
        } else dao.put(requireNotNull(dao.document(id)).copy(folderId = parent, updatedAt = System.currentTimeMillis()))
    }
    suspend fun delete(id: String, folder: Boolean) = write {
        val before = dao.documents().map { it.id }.toSet()
        if (folder) dao.deleteFolder(id) else dao.deleteDocument(id)
        val after = dao.documents().map { it.id }.toSet()
        (before - after).forEach { directory(it).deleteRecursively() }
    }
    suspend fun duplicate(id: String, folder: Boolean) = write {
        val created = mutableListOf<String>()
        suspend fun copyDocument(doc: Document, parent: String?) {
            val next = id(); created += next
            require(directory(doc.id).copyRecursively(directory(next), overwrite = false)) { "No se pudo copiar el documento" }
            dao.put(doc.copy(id = next, folderId = parent, networkAllowed = false, title = doc.title.take(192) + " (copia)", createdAt = System.currentTimeMillis()))
            dao.putAnnotations(dao.annotations(doc.id).map { it.copy(id = id(), documentId = next) })
        }
        try {
            database.withTransaction {
                if (!folder) { val doc = requireNotNull(dao.document(id)); copyDocument(doc, doc.folderId) }
                else {
                    val folders = dao.folders(); val documents = dao.documents()
                    suspend fun copyFolder(old: Folder, parent: String?, top: Boolean) {
                        val next = id()
                        dao.put(old.copy(id = next, parentId = parent, name = if (top) old.name.take(192) + " (copia)" else old.name))
                        documents.filter { it.folderId == old.id }.forEach { copyDocument(it, next) }
                        folders.filter { it.parentId == old.id }.forEach { copyFolder(it, next, false) }
                    }
                    val source = requireNotNull(dao.folder(id)); copyFolder(source, source.parentId, true)
                }
            }
        } catch (error: Throwable) { created.forEach { directory(it).deleteRecursively() }; throw error }
    }
    suspend fun setNetwork(id: String, allowed: Boolean) = write { dao.put(requireNotNull(dao.document(id)).copy(networkAllowed = allowed)) }
    suspend fun annotations(id: String) = withContext(Dispatchers.IO) { dao.annotations(id) }
    suspend fun replaceAnnotations(id: String, values: List<Annotation>) = write {
        require(dao.document(id) != null && values.size <= 10_000 && values.map { it.id }.toSet().size == values.size && values.sumOf { it.payload.length.toLong() * 2 } <= 32L * 1024 * 1024 && values.all { it.documentId == id && it.kind in listOf("PIN", "STROKE") && it.payload.length <= 512_000 })
        database.withTransaction { dao.clearAnnotations(id); dao.putAnnotations(values) }
    }
    suspend fun preference(id: String) = withContext(Dispatchers.IO) { dao.preference(id) ?: ViewerPreference(id) }
    suspend fun preference(value: ViewerPreference) = write { if (dao.document(value.documentId) != null) dao.put(value) }
    suspend fun installSample(): Document = withContext(Dispatchers.IO) {
        val next = id(); val operation = File(context.filesDir, "imports/$next").apply { mkdirs() }
        val root = File(operation, "original").apply { mkdirs() }
        context.assets.open("demo.html").use { input -> File(root, "index.html").outputStream().use { input.copyTo(it) } }
        finishImport(PendingImport(next, operation, "Cuaderno interactivo", listOf("index.html"), "index.html"), "index.html", null)
    }
}
