package com.hypermarker.storage

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.room.Room
import androidx.room.withTransaction
import com.hypermarker.flow.FlowGraph
import com.hypermarker.mermaid.FlowMermaid
import com.hypermarker.mermaid.FlowPlan
import com.hypermarker.mermaid.MermaidFlow
import com.hypermarker.mermaid.MermaidGraph
import com.hypermarker.security.FolderRules
import com.hypermarker.security.PathGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

const val MAX_FLOW_LINKS = 64
const val FORM_BUFFER_LIMIT = 4_000_000
/** Igual que `maxTextSize` del visor (`web/src/mermaid-viewer.js`). */
const val MERMAID_MAX_CHARS = 300_000
const val MERMAID_TEMPLATE = "flowchart TB\n    idea[\"Idea\"] --> plan[\"Plan\"]\n    plan --> hecho[\"Hecho\"]\n"

class LibraryRepository(val context: Context, database: LibraryDatabase? = null) {
    val database = database ?: Room.databaseBuilder(context, LibraryDatabase::class.java, "library.db")
        .addMigrations(LibraryDatabase.MIGRATION_1_2, LibraryDatabase.MIGRATION_2_3, LibraryDatabase.MIGRATION_3_4, LibraryDatabase.MIGRATION_4_5).build()
    val dao = this.database.dao()
    private val mutex = Mutex()
    private var recovered = false
    val importer = SafeImporter(context)
    private fun id() = UUID.randomUUID().toString()
    fun directory(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}"))) { "Identidad inválida" }
        return File(context.filesDir, "documents/$id")
    }
    fun original(id: String): File {
        val root = directory(id)
        val version = File(root, "remote-current").takeIf { it.isFile }?.readText()?.trim()
        if (version != null && version.matches(Regex("[a-f0-9]{40}"))) {
            val selected = File(root, "remote-versions/$version")
            if (selected.isDirectory) return selected
        }
        return File(root, "original")
    }
    fun installRemoteVersion(id: String, sha: String) {
        require(sha.matches(Regex("[a-f0-9]{40}")))
        require(File(directory(id), "remote-versions/$sha").isDirectory)
        atomicWrite(File(directory(id), "remote-current"), sha)
    }
    private fun markdown(id: String) = File(directory(id), "markdown/source.md")
    /** Respaldo de campos de formulario del lector, fuera de la raíz servida al WebView. */
    private fun formBuffer(id: String) = File(directory(id), "form-buffer.json")
    suspend fun readFormBuffer(id: String): org.json.JSONObject = withContext(Dispatchers.IO) {
        runCatching { org.json.JSONObject(formBuffer(id).readText()) }.getOrDefault(org.json.JSONObject())
    }
    suspend fun writeFormBuffer(id: String, value: org.json.JSONObject) = write {
        if (dao.document(id) == null) return@write
        val text = value.toString()
        require(text.length <= FORM_BUFFER_LIMIT) { "El respaldo de formularios supera el límite" }
        if (value.length() == 0) formBuffer(id).delete() else atomicWrite(formBuffer(id), text)
    }
    private fun name(value: String) = value.trim().also { require(it.isNotEmpty() && it.length <= 200) { "Usa un nombre de 1 a 200 caracteres" } }
    private suspend fun <T> write(block: suspend () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }
    internal suspend fun <T> remoteMutation(block: suspend () -> T): T = write(block)
    private suspend fun checkParent(parent: String?) { require(parent == null || dao.folder(parent) != null) { "La carpeta ya no existe" } }
    private suspend fun includesRemoteFolder(folderId: String): Boolean {
        val parents = dao.folders().associate { it.id to it.parentId }
        return dao.remoteFolders().any { remote ->
            var current: String? = remote.folderId
            val seen = mutableSetOf<String>()
            while (current != null && seen.add(current)) {
                if (current == folderId) return@any true
                current = parents[current]
            }
            false
        }
    }

    suspend fun recover() = write {
        if (recovered) return@write
        File(context.filesDir, "imports").deleteRecursively()
        File(context.cacheDir, "web-captures").deleteRecursively()
        File(context.cacheDir, "feedback-exports").deleteRecursively()
        val existing = dao.documents().map { it.id }.toSet()
        File(context.filesDir, "documents").listFiles()?.filter { it.name !in existing }?.forEach { it.deleteRecursively() }
        dao.remoteFolders().forEach { remote ->
            val document = remote.htmlDocumentId
            val sha = remote.latestCommitSha
            if (document != null && sha != null && File(directory(document), "remote-versions/$sha").isDirectory)
                installRemoteVersion(document, sha)
        }
        // Este mutex también se usa en sync: nunca limpiar una generación en instalación.
        File(context.filesDir, "remote-sync").deleteRecursively()
        dao.documents().forEach { doc ->
            val root = directory(doc.id)
            val markdownVersion = doc.entryPath?.takeIf { doc.type == "REMOTE_MARKDOWN" }?.split('/')?.getOrNull(1)
            File(root, "versions").listFiles()?.filter { it.name != markdownVersion }?.forEach { it.deleteRecursively() }
            val htmlVersion = File(root, "remote-current").takeIf { it.isFile }?.readText()?.trim()
            File(root, "remote-versions").listFiles()?.filter { it.name != htmlVersion }?.forEach { it.deleteRecursively() }
        }
        dao.remoteFolders().filter { it.status == "SYNCING" }.forEach { dao.put(it.copy(status = "PENDING")) }
        recovered = true
    }
    suspend fun createFolder(parent: String?, title: String) = write { checkParent(parent); dao.put(Folder(id(), parent, name(title))) }
    suspend fun createMarkdown(parent: String?, title: String, source: String = ""): Document = write {
        createMarkdownLocked(parent, title, source)
    }
    private suspend fun createMarkdownLocked(parent: String?, title: String, source: String): Document {
        checkParent(parent)
        val document = Document(id(), parent, name(title), "MARKDOWN")
        return try { atomicWrite(markdown(document.id), source); dao.put(document); document }
        catch (error: Throwable) { directory(document.id).deleteRecursively(); throw error }
    }
    suspend fun readMarkdown(id: String) = withContext(Dispatchers.IO) {
        val doc = requireNotNull(dao.document(id))
        require(doc.type in listOf("MARKDOWN", "REMOTE_MARKDOWN"))
        if (doc.type == "REMOTE_MARKDOWN") PathGuard.resolve(directory(id), requireNotNull(doc.entryPath)).readText()
        else AtomicFile(markdown(id)).readFully().toString(Charsets.UTF_8)
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
    private suspend fun readLimited(uri: Uri, maxBytes: Int, tooLarge: String): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri).use { input ->
            val stream = requireNotNull(input)
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maxBytes) { tooLarge }
                output.write(buffer, 0, count)
            }
            output.toByteArray().toString(Charsets.UTF_8)
        }
    }
    suspend fun importMarkdown(uri: Uri, parent: String?): Document =
        createMarkdown(parent, importer.displayName(uri).substringBeforeLast('.'), readLimited(uri, 2 * 1024 * 1024, "La nota supera 2 MiB"))

    // ---- Diagramas Mermaid: el código es la autoridad; el dibujo se genera en el visor local ----

    private fun mermaidSource(id: String) = File(directory(id), "mermaid/source.mmd")
    private fun mermaidText(source: String) = source.also { require(it.length <= MERMAID_MAX_CHARS) { "El diagrama supera $MERMAID_MAX_CHARS caracteres" } }
    suspend fun createMermaid(parent: String?, title: String, source: String = MERMAID_TEMPLATE): Document = write {
        checkParent(parent)
        val document = Document(id(), parent, name(title), "MERMAID")
        try { atomicWrite(mermaidSource(document.id), mermaidText(source)); dao.put(document); document }
        catch (error: Throwable) { directory(document.id).deleteRecursively(); throw error }
    }
    suspend fun readMermaid(id: String): String = withContext(Dispatchers.IO) {
        require(dao.document(id)?.type == "MERMAID") { "El diagrama ya no existe" }
        AtomicFile(mermaidSource(id)).readFully().toString(Charsets.UTF_8)
    }
    suspend fun saveMermaid(id: String, source: String) = write {
        val doc = requireNotNull(dao.document(id)?.takeIf { it.type == "MERMAID" }) { "El diagrama ya no existe" }
        atomicWrite(mermaidSource(id), mermaidText(source))
        dao.put(doc.copy(updatedAt = System.currentTimeMillis(), revision = doc.revision + 1))
    }
    suspend fun importMermaid(uri: Uri, parent: String?): Document {
        val source = readLimited(uri, MERMAID_MAX_CHARS * 4, "El diagrama supera $MERMAID_MAX_CHARS caracteres").removePrefix("\uFEFF")
        return createMermaid(parent, importer.displayName(uri).substringBeforeLast('.'), source)
    }
    /** Flow → nuevo documento Mermaid (copia) junto al Flow, para compartirlo o exportarlo como SVG/PNG. */
    suspend fun createMermaidFromFlow(flowId: String): Document {
        val (flow, code) = withContext(Dispatchers.IO) {
            val flow = flowDocument(flowId)
            flow to FlowMermaid.generate(flow.title, dao.flowNodes(flowId), dao.flowEdges(flowId))
        }
        return createMermaid(flow.folderId, "Diagrama · ${flow.title}".take(200), code)
    }
    /**
     * Copia (no enlaza) un flowchart en un Flow: uno nuevo en `parent` si `target` es null, o al lado derecho del
     * contenido de un Flow existente, con un punto de cabecera para comparar varios diagramas. Todo o nada.
     */
    suspend fun copyMermaidToFlow(graph: MermaidGraph, target: String?, parent: String?, title: String, header: Pair<String, String>?): Pair<Document, FlowPlan> = write {
        val document = if (target == null) { checkParent(parent); Document(id(), parent, name(title), "FLOW") } else flowDocument(target)
        val existing = if (target == null) emptyList() else dao.flowNodes(target)
        val plan = if (existing.isEmpty()) MermaidFlow.plan(graph, 0f, 0f, header.takeIf { target != null })
            else MermaidFlow.plan(graph, coordinate(existing.maxOf { it.x } + 360f), coordinate(existing.minOf { it.y }), header)
        require(existing.size + plan.nodes.size <= 5_000) { "El Flow admite hasta 5000 puntos" }
        require(dao.flowEdges(document.id).size + plan.edges.size <= 20_000) { "El Flow admite hasta 20000 conexiones" }
        val now = System.currentTimeMillis()
        val nodes = plan.nodes.map { FlowNode(id(), document.id, it.title.trim(), it.body, coordinate(it.x), coordinate(it.y), createdAt = now, updatedAt = now, ringColor = it.ring) }
        val edges = plan.edges.map { e -> FlowEdge(id(), document.id, nodes[e.from].id, nodes[e.to].id, createdAt = now, updatedAt = now, label = e.label, loop = e.loop) }
        try {
            if (target == null) require(directory(document.id).mkdirs()) { "No se pudo crear el documento" }
            database.withTransaction {
                if (target == null) dao.put(document) else dao.put(document.copy(updatedAt = now))
                dao.putFlowNodes(nodes); dao.putFlowEdges(edges)
            }
        } catch (error: Throwable) { if (target == null) directory(document.id).deleteRecursively(); throw error }
        document to plan
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
    suspend fun saveWebSnapshot(archive: File, source: String, title: String, parent: String?): Document = write {
        checkParent(parent)
        val document = Document(id(), parent, name(title), "WEB_SNAPSHOT", "index.html", networkAllowed = false)
        try {
            val root = directory(document.id)
            com.hypermarker.browser.WebSnapshot.extract(archive, original(document.id), source)
            val preserved = File(root, "capture/page.mhtml").apply { parentFile!!.mkdirs() }
            archive.copyTo(preserved)
            atomicWrite(File(root, "web-source.json"), org.json.JSONObject().put("url", source)
                .put("savedAt", document.createdAt).toString())
            dao.put(document)
            document
        } catch (error: Throwable) { directory(document.id).deleteRecursively(); throw error }
    }
    suspend fun webSource(id: String): String? = withContext(Dispatchers.IO) {
        if (dao.document(id)?.type != "WEB_SNAPSHOT") return@withContext null
        runCatching { org.json.JSONObject(File(directory(id), "web-source.json").readText()).getString("url") }
            .getOrNull()?.takeIf(com.hypermarker.browser.WebAddress::permitted)
    }
    suspend fun rename(id: String, folder: Boolean, title: String) = write {
        require(if (folder) dao.remoteFolderForLibrary(id) == null else dao.remoteEntryForDocument(id) == null) { "El contenido GitHub es de solo lectura" }
        if (folder) dao.put(requireNotNull(dao.folder(id)).copy(name = name(title)))
        else dao.put(requireNotNull(dao.document(id)).copy(title = name(title), updatedAt = System.currentTimeMillis()))
    }
    suspend fun move(id: String, folder: Boolean, parent: String?) = write {
        require(if (folder) dao.remoteFolderForLibrary(id) == null else dao.remoteEntryForDocument(id) == null) { "El contenido GitHub es de solo lectura" }
        checkParent(parent)
        if (folder) {
            FolderRules.validateMove(id, parent, dao.folders().associate { it.id to it.parentId })
            dao.put(requireNotNull(dao.folder(id)).copy(parentId = parent))
        } else dao.put(requireNotNull(dao.document(id)).copy(folderId = parent, updatedAt = System.currentTimeMillis()))
    }
    suspend fun delete(id: String, folder: Boolean) = write {
        require(if (folder) dao.remoteFolderForLibrary(id) == null else dao.remoteEntryForDocument(id) == null) { "Desvincula la carpeta GitHub antes de eliminar su contenido" }
        require(!folder || !includesRemoteFolder(id)) { "Esta carpeta contiene una fuente GitHub; desvincúlala primero" }
        val before = dao.documents().map { it.id }.toSet()
        if (folder) dao.deleteFolder(id) else dao.deleteDocument(id)
        val after = dao.documents().map { it.id }.toSet()
        (before - after).forEach { directory(it).deleteRecursively() }
    }
    suspend fun duplicate(id: String, folder: Boolean) = write {
        require(if (folder) dao.remoteFolderForLibrary(id) == null else dao.remoteEntryForDocument(id) == null) { "Crea una copia local editable desde el documento remoto" }
        require(!folder || !includesRemoteFolder(id)) { "No se puede duplicar una carpeta que contiene una fuente GitHub" }
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
            // Las fotos y adjuntos viajan con la copia del directorio; las relaciones apuntan a los mismos elementos.
            val links = dao.flowLinks(doc.id).filter { it.nodeId in nodes }.associate { it.id to id() }
            dao.putFlowLinks(dao.flowLinks(doc.id).filter { it.nodeId in nodes }.map { it.copy(id = links.getValue(it.id), documentId = next, nodeId = nodes.getValue(it.nodeId)) })
            dao.putFlowNodes(dao.flowNodes(next).map { node -> node.copy(avatarLinkId = node.avatarLinkId?.let(links::get)) })
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
    suspend fun setNetwork(id: String, allowed: Boolean) = write {
        val doc = requireNotNull(dao.document(id))
        require(doc.type != "WEB_SNAPSHOT" || !allowed) { "Las copias descargadas permanecen sin conexión; abre la página original para navegar" }
        dao.put(doc.copy(networkAllowed = allowed))
    }
    suspend fun copyRemoteMarkdown(id: String): Document = write {
        val doc = requireNotNull(dao.document(id)) { "El documento ya no existe" }
        require(doc.type == "REMOTE_MARKDOWN")
        val parent = doc.folderId?.let { dao.folder(it)?.parentId }
        createMarkdownLocked(parent, "${doc.title.take(185)} (copia local)", readMarkdown(id))
    }
    suspend fun copyRemoteHtml(id: String): Document = write {
        val doc = requireNotNull(dao.document(id)) { "El documento ya no existe" }
        require(doc.type == "HTML" && dao.remoteEntryForDocument(id) != null)
        val parent = doc.folderId?.let { dao.folder(it)?.parentId }
        val copy = doc.copy(id = id(), folderId = parent, title = "${doc.title.take(185)} (copia local)",
            networkAllowed = false, revision = 1, createdAt = System.currentTimeMillis())
        val destination = original(copy.id)
        try {
            require(original(id).copyRecursively(destination, overwrite = false))
            dao.put(copy)
            copy
        } catch (error: Throwable) { directory(copy.id).deleteRecursively(); throw error }
    }
    suspend fun unlinkRemoteFolder(id: String) = write {
        val remote = requireNotNull(dao.remoteFolder(id)) { "La carpeta GitHub ya no existe" }
        val docs = dao.remoteEntries(id).mapNotNull { it.documentId }.toSet()
        val markdownDocs = docs.mapNotNull { dao.document(it) }.filter { it.type == "REMOTE_MARKDOWN" }
        for (doc in markdownDocs) atomicWrite(markdown(doc.id), readMarkdown(doc.id))
        val htmlDocs = docs.mapNotNull { dao.document(it) }.filter { it.type == "HTML" }
        htmlDocs.forEach { htmlDoc ->
            val htmlId = htmlDoc.id
            val target = File(directory(htmlId), "original")
            if (target.exists()) target.deleteRecursively() // copia incompleta de un intento previo
            require(original(htmlId).copyRecursively(target, overwrite = false)) { "No se pudo conservar el HTML local" }
        }
        database.withTransaction {
            markdownDocs.forEach { dao.put(it.copy(type = "MARKDOWN", entryPath = null)) }
            dao.deleteRemoteFolder(id)
        }
        // Los originales convertidos y las anotaciones mantienen sus IDs.
        htmlDocs.forEach { File(directory(it.id), "remote-current").delete(); File(directory(it.id), "remote-versions").deleteRecursively() }
        markdownDocs.forEach { File(directory(it.id), "versions").deleteRecursively() }
    }
    suspend fun deleteRetiredRemoteDocument(id: String) = write {
        val entries = dao.remoteEntriesForDocument(id)
        require(entries.isNotEmpty() && entries.all { it.state == "RETIRED" }) { "El documento no está retirado de origen" }
        database.withTransaction { dao.clearRemoteEntriesForDocument(id); dao.deleteDocument(id) }
        directory(id).deleteRecursively()
    }
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
    /**
     * Conecta `from` (padre) con `to`. Rechaza autoconexiones y duplicados. Si la conexión cerraría un ciclo en el
     * árbol, se guarda como retorno (`loop`): sólo visual, fuera de plegado y raíces.
     */
    suspend fun connectFlowNodes(document: String, from: String, to: String, label: String? = null): FlowEdge = write {
        flowNode(document, from); flowNode(document, to)
        require(from != to) { "Un punto no puede conectarse consigo mismo" }
        val edges = dao.flowEdges(document)
        require(edges.none { it.fromId == from && it.toId == to }) { "Esos puntos ya están conectados" }
        require(edges.size < 20_000) { "El Flow admite hasta 20000 conexiones" }
        val loop = FlowGraph.wouldCreateCycle(edges.filter { !it.loop }.map { it.fromId to it.toId }, from, to)
        FlowEdge(id(), document, from, to, label = edgeLabel(label), loop = loop).also { dao.put(it) }
    }
    private fun edgeLabel(value: String?) = value?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
        ?.also { require(it.length <= 200) { "El texto de la flecha admite 200 caracteres" } }
    /** Texto en medio de la flecha; vacío lo quita. */
    suspend fun labelFlowEdge(document: String, edge: String, label: String?) = write {
        val value = requireNotNull(dao.flowEdges(document).firstOrNull { it.id == edge }) { "La conexión ya no existe" }
        dao.put(value.copy(label = edgeLabel(label), updatedAt = System.currentTimeMillis()))
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
            dao.putFlowLinks(dao.flowLinks(document).filter { it.nodeId == node }.map { it.copy(deletedAt = now, updatedAt = now) })
        }
        flowFile(document, value.avatarPath)?.delete()
    }

    // ---- Fotos y relaciones de puntos (rediseño, Room v4) ----

    /** Archivo privado dentro del Flow; rechaza rutas fuera de `avatars/` o `attachments/`. */
    fun flowFile(document: String, relative: String?): File? {
        if (relative == null) return null
        require(relative.matches(Regex("(avatars|attachments)/[A-Za-z0-9-]{1,80}\\.webp"))) { "Ruta de imagen inválida" }
        return PathGuard.resolve(directory(document), relative)
    }
    suspend fun markOpened(id: String) = write { dao.markOpened(id, System.currentTimeMillis()) }

    /** Relaciona documentos y/o carpetas con un punto. Revive relaciones borradas en lugar de duplicarlas. */
    suspend fun linkFlowNode(document: String, node: String, documents: List<String>, folders: List<String>): Int = write {
        flowDocument(document); flowNode(document, node)
        require(document !in documents) { "Un Flow no puede relacionarse consigo mismo" }
        documents.forEach { require(dao.document(it) != null) { "El documento ya no existe" } }
        folders.forEach { require(dao.folder(it) != null) { "La carpeta ya no existe" } }
        val all = dao.allFlowLinks(document).filter { it.nodeId == node }
        val active = all.filter { it.deletedAt == null }
        val fresh = documents.distinct().filter { id -> active.none { it.targetDocumentId == id } }.map { it to true } +
            folders.distinct().filter { id -> active.none { it.targetFolderId == id } }.map { it to false }
        require(active.size + fresh.size <= MAX_FLOW_LINKS) { "Un punto admite hasta $MAX_FLOW_LINKS elementos relacionados" }
        var position = (active.maxOfOrNull { it.position } ?: -1) + 1
        val now = System.currentTimeMillis()
        database.withTransaction {
            fresh.forEach { (target, isDocument) ->
                val old = all.firstOrNull { if (isDocument) it.targetDocumentId == target else it.targetFolderId == target }
                dao.put(old?.copy(deletedAt = null, position = position++, updatedAt = now)
                    ?: FlowNodeLink(id(), document, node, targetDocumentId = target.takeIf { isDocument }, targetFolderId = target.takeIf { !isDocument }, position = position++))
            }
        }
        fresh.size
    }

    /** Copia privada reescalada de una imagen del selector del sistema como relación del punto. */
    suspend fun linkFlowImage(document: String, node: String, uri: Uri, title: String?): FlowNodeLink {
        val bitmap = withContext(Dispatchers.IO) { PrivateImages.fit(PrivateImages.decode(context, uri, PrivateImages.ATTACHMENT_SIDE, crop = false), PrivateImages.ATTACHMENT_SIDE) }
        return write {
            flowDocument(document); flowNode(document, node)
            val active = dao.flowLinks(document).filter { it.nodeId == node }
            require(active.size < MAX_FLOW_LINKS) { "Un punto admite hasta $MAX_FLOW_LINKS elementos relacionados" }
            val link = FlowNodeLink(id(), document, node, imagePath = "attachments/${id()}.webp",
                imageTitle = title?.trim()?.take(120)?.ifBlank { null } ?: "Imagen", position = (active.maxOfOrNull { it.position } ?: -1) + 1)
            val file = requireNotNull(flowFile(document, link.imagePath))
            try { PrivateImages.writeWebp(bitmap, file); dao.put(link); link } catch (error: Throwable) { file.delete(); throw error }
        }
    }

    suspend fun unlinkFlowNode(document: String, link: String) = write {
        val value = requireNotNull(dao.flowLinks(document).firstOrNull { it.id == link }) { "La relación ya no existe" }
        val now = System.currentTimeMillis()
        dao.put(value.copy(deletedAt = now, updatedAt = now))
        // La imagen es una copia privada sólo de esta relación.
        flowFile(document, value.imagePath)?.delete()
    }

    suspend fun reorderFlowLinks(document: String, node: String, ordered: List<String>) = write {
        val links = dao.flowLinks(document).filter { it.nodeId == node }.associateBy { it.id }
        require(ordered.toSet() == links.keys && ordered.size == links.size) { "La lista de relaciones cambió" }
        val now = System.currentTimeMillis()
        dao.putFlowLinks(ordered.mapIndexed { index, id -> links.getValue(id).copy(position = index, updatedAt = now) })
    }

    /** Foto desde el Photo Picker (`uri`) o desde una miniatura ya generada (`file`); se guarda recortada a 512 px. */
    suspend fun setFlowAvatar(document: String, node: String, uri: Uri? = null, file: File? = null, linkId: String? = null, ring: Long? = null) {
        require((uri == null) != (file == null)) { "Elige una sola imagen" }
        val bitmap = withContext(Dispatchers.IO) {
            val source = if (uri != null) PrivateImages.decode(context, uri, PrivateImages.AVATAR_SIDE, crop = true)
                else requireNotNull(PrivateImages.decodeFile(requireNotNull(file), PrivateImages.AVATAR_SIDE)) { "La vista previa no está disponible" }
            PrivateImages.square(source, PrivateImages.AVATAR_SIDE)
        }
        write {
            val value = flowNode(document, node)
            val path = "avatars/${id()}.webp"
            val target = requireNotNull(flowFile(document, path))
            try {
                PrivateImages.writeWebp(bitmap, target)
                dao.put(value.copy(avatarPath = path, avatarLinkId = linkId, ringColor = ring ?: value.ringColor, updatedAt = System.currentTimeMillis()))
            } catch (error: Throwable) { target.delete(); throw error }
            flowFile(document, value.avatarPath)?.delete()
        }
    }

    suspend fun setFlowRing(document: String, node: String, ring: Long?) = write {
        dao.put(flowNode(document, node).copy(ringColor = ring, updatedAt = System.currentTimeMillis()))
    }

    suspend fun clearFlowAvatar(document: String, node: String) = write {
        val value = flowNode(document, node)
        dao.put(value.copy(avatarPath = null, avatarLinkId = null, updatedAt = System.currentTimeMillis()))
        flowFile(document, value.avatarPath)?.delete()
    }
    suspend fun installSample(): Document = withContext(Dispatchers.IO) {
        val next = id(); val operation = File(context.filesDir, "imports/$next").apply { mkdirs() }
        val root = File(operation, "original").apply { mkdirs() }
        context.assets.open("demo.html").use { input -> File(root, "index.html").outputStream().use { input.copyTo(it) } }
        finishImport(PendingImport(next, operation, "Cuaderno interactivo", listOf("index.html"), "index.html"), "index.html", null)
    }
}
