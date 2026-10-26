package com.hypermarker.storage

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.room.Room
import androidx.room.withTransaction
import com.hypermarker.flow.FlowGraph
import com.hypermarker.security.FolderRules
import com.hypermarker.security.PathGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LibraryRepository(val context: Context, database: LibraryDatabase? = null) {
    val database = database ?: Room.databaseBuilder(context, LibraryDatabase::class.java, "library.db")
        .addMigrations(LibraryDatabase.MIGRATION_1_2).build()
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
            val nodes = dao.flowNodes(doc.id).associate { it.id to id() }
            dao.putFlowNodes(dao.flowNodes(doc.id).map { it.copy(id = nodes.getValue(it.id), documentId = next) })
            dao.putFlowEdges(dao.flowEdges(doc.id).filter { it.fromId in nodes && it.toId in nodes }
                .map { it.copy(id = id(), documentId = next, fromId = nodes.getValue(it.fromId), toId = nodes.getValue(it.toId)) })
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
        require(dao.document(id) != null && values.size <= 10_000 && values.map { it.id }.toSet().size == values.size && values.sumOf { it.payload.length.toLong() * 2 } <= 32L * 1024 * 1024 && values.all { it.documentId == id && it.kind in listOf("PIN", "STROKE", "TEXT") && it.payload.length <= 512_000 })
        database.withTransaction { dao.clearAnnotations(id); dao.putAnnotations(values) }
    }
    suspend fun preference(id: String) = withContext(Dispatchers.IO) { dao.preference(id) ?: ViewerPreference(id) }
    suspend fun preference(value: ViewerPreference) = write { if (dao.document(value.documentId) != null) dao.put(value) }
    suspend fun createFlow(parent: String?, title: String): Document = write {
        checkParent(parent)
        val document = Document(id(), parent, name(title), "FLOW")
        try { require(directory(document.id).mkdirs()) { "No se pudo crear el documento" }; dao.put(document); document }
        catch (error: Throwable) { directory(document.id).deleteRecursively(); throw error }
    }
    private suspend fun flowDocument(id: String) = requireNotNull(dao.document(id)?.takeIf { it.type == "FLOW" }) { "El Flow ya no existe" }
    private suspend fun flowNode(document: String, node: String) =
        requireNotNull(dao.flowNodes(document).firstOrNull { it.id == node }) { "El punto ya no existe" }
    private fun noteText(title: String, body: String) {
        require(title.length <= 200 && body.length <= 256_000) { "El título admite 200 caracteres y el cuerpo 256 000" }
        require(title.isNotBlank() || body.isNotBlank()) { "Escribe un título o un texto" }
    }
    private fun coordinate(value: Float) = value.also { require(it.isFinite() && kotlin.math.abs(it) <= 1_000_000f) { "Posición fuera del canvas" } }
    suspend fun createFlowNode(document: String, x: Float, y: Float, title: String, body: String): FlowNode = write {
        flowDocument(document); noteText(title, body)
        require(dao.flowNodes(document).size < 5_000) { "El Flow admite hasta 5000 puntos" }
        FlowNode(id(), document, title.trim(), body, coordinate(x), coordinate(y)).also { dao.put(it) }
    }
    suspend fun updateFlowNode(document: String, node: String, title: String, body: String) = write {
        noteText(title, body)
        dao.put(flowNode(document, node).copy(title = title.trim(), body = body, updatedAt = System.currentTimeMillis()))
    }
    suspend fun moveFlowNode(document: String, node: String, x: Float, y: Float) = write {
        dao.put(flowNode(document, node).copy(x = coordinate(x), y = coordinate(y), updatedAt = System.currentTimeMillis()))
    }
    suspend fun setFlowCollapsed(document: String, node: String, collapsed: Boolean) = write {
        dao.put(flowNode(document, node).copy(collapsed = collapsed, updatedAt = System.currentTimeMillis()))
    }
    /** Conecta `from` (padre) con `to`. Rechaza autoconexiones, duplicados y ciclos. */
    suspend fun connectFlowNodes(document: String, from: String, to: String): FlowEdge = write {
        flowNode(document, from); flowNode(document, to)
        val edges = dao.flowEdges(document)
        require(edges.none { it.fromId == from && it.toId == to }) { "Esos puntos ya están conectados" }
        require(!FlowGraph.wouldCreateCycle(edges.map { it.fromId to it.toId }, from, to)) { "La conexión crearía un ciclo" }
        require(edges.size < 20_000) { "El Flow admite hasta 20000 conexiones" }
        FlowEdge(id(), document, from, to).also { dao.put(it) }
    }
    suspend fun deleteFlowEdge(document: String, edge: String) = write {
        val value = requireNotNull(dao.flowEdges(document).firstOrNull { it.id == edge }) { "La conexión ya no existe" }
        val now = System.currentTimeMillis(); dao.put(value.copy(deletedAt = now, updatedAt = now))
    }
    /** Marca el punto y sus conexiones como borrados (tombstones), en una transacción. */
    suspend fun deleteFlowNode(document: String, node: String) = write {
        val value = flowNode(document, node); val now = System.currentTimeMillis()
        database.withTransaction {
            dao.put(value.copy(deletedAt = now, updatedAt = now))
            dao.putFlowEdges(dao.flowEdges(document).filter { it.fromId == node || it.toId == node }.map { it.copy(deletedAt = now, updatedAt = now) })
        }
    }
    suspend fun installSample(): Document = withContext(Dispatchers.IO) {
        val next = id(); val operation = File(context.filesDir, "imports/$next").apply { mkdirs() }
        val root = File(operation, "original").apply { mkdirs() }
        context.assets.open("demo.html").use { input -> File(root, "index.html").outputStream().use { input.copyTo(it) } }
        finishImport(PendingImport(next, operation, "Cuaderno interactivo", listOf("index.html"), "index.html"), "index.html", null)
    }
}
