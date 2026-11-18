package com.hypermarker.flow

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermarker.preview.PreviewStore
import com.hypermarker.storage.FlowEdge
import com.hypermarker.storage.FlowNode
import com.hypermarker.storage.FlowNodeLink
import com.hypermarker.storage.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Borrador de nota: `node == null` significa un punto nuevo que sólo se crea al guardar. */
data class FlowDraft(val node: String?, val x: Float, val y: Float, val title: String, val body: String)

/** Relación resuelta contra la biblioteca para mostrarla. `type` es el tipo de documento, "FOLDER" o "IMAGE". */
data class LinkView(val link: FlowNodeLink, val title: String, val type: String) {
    val id get() = link.id
    val targetId get() = link.targetDocumentId ?: link.targetFolderId
}

class FlowViewModel(val documentId: String, val repository: LibraryRepository, val previews: PreviewStore) : ViewModel() {
    /** `null` hasta la primera lectura de Room, para no encuadrar la cámara sobre un grafo aún vacío. */
    val nodes = repository.dao.observeFlowNodes(documentId).stateIn<List<FlowNode>?>(viewModelScope, SharingStarted.Eagerly, null)
    val edges = repository.dao.observeFlowEdges(documentId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val documents = repository.dao.observeDocuments().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val folders = repository.dao.observeFolders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val remoteFolders = repository.dao.observeRemoteFolders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Relaciones por punto, resueltas y en orden. */
    val links = combine(repository.dao.observeFlowLinks(documentId), documents, folders) { links, docs, dirs ->
        val docById = docs.associateBy { it.id }; val dirById = dirs.associateBy { it.id }
        links.mapNotNull { link ->
            when {
                link.targetDocumentId != null -> docById[link.targetDocumentId]?.let { LinkView(link, it.title, it.type) }
                link.targetFolderId != null -> dirById[link.targetFolderId]?.let { LinkView(link, it.name, "FOLDER") }
                link.imagePath != null -> LinkView(link, link.imageTitle ?: "Imagen", "IMAGE")
                else -> null
            }
        }.groupBy { it.link.nodeId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** La cámara es estado de sesión: sobrevive a la rotación pero no se guarda en el documento. */
    var camera by mutableStateOf<FlowCamera?>(null)
    var rearrange by mutableStateOf(false)
    var selected by mutableStateOf<String?>(null)
    var draft by mutableStateOf<FlowDraft?>(null)
    var connecting by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    /** Hojas abiertas sobre un punto. */
    var avatarFor by mutableStateOf<String?>(null)
    var relating by mutableStateOf<String?>(null)
    var listing by mutableStateOf<String?>(null)
    var viewingImage by mutableStateOf<LinkView?>(null)
    var radialPage by mutableIntStateOf(0)
    var radialFocus by mutableStateOf<Int?>(null)
    /** Posiciones provisionales mientras se arrastra en Reordenar; se guardan al soltar. */
    val moving = mutableStateMapOf<String, Pair<Float, Float>>()
    /** Fotos decodificadas por ruta relativa; la caché LRU de `PreviewStore` limita la memoria real. */
    val images = mutableStateMapOf<String, ImageBitmap>()

    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { message = e.message ?: "No se pudo completar" }
    }
    fun node(id: String?): FlowNode? = nodes.value.orEmpty().firstOrNull { it.id == id }
    fun position(node: FlowNode) = moving[node.id] ?: (node.x to node.y)
    fun linksOf(node: String?) = links.value[node].orEmpty()

    fun select(id: String?) { selected = id; radialPage = 0; radialFocus = null }
    fun startCreate(x: Float, y: Float) { select(null); draft = FlowDraft(null, x, y, "", "") }
    fun startEdit(id: String) { node(id)?.let { draft = FlowDraft(it.id, it.x, it.y, it.title, it.body) } }
    fun saveDraft(title: String, body: String) {
        val value = draft ?: return
        if (title.isBlank() && body.isBlank()) { draft = null; return }
        draft = null
        run {
            if (value.node == null) select(repository.createFlowNode(documentId, value.x, value.y, title, body).id)
            else repository.updateFlowNode(documentId, value.node, title, body)
        }
    }
    /** Crea un diagrama Mermaid copiado de este Flow y lo entrega para abrirlo. */
    fun toDiagram(onCreated: (String) -> Unit) = run { onCreated(repository.createMermaidFromFlow(documentId).id) }
    fun toggleCollapsed(id: String) { val node = node(id) ?: return; run { repository.setFlowCollapsed(documentId, id, !node.collapsed) } }
    fun delete(id: String) { if (selected == id) select(null); run { repository.deleteFlowNode(documentId, id) } }
    fun disconnect(edge: FlowEdge) = run { repository.deleteFlowEdge(documentId, edge.id) }
    fun connect(from: String, to: String) {
        connecting = null
        run { if (repository.connectFlowNodes(documentId, from, to).loop) message = "Conexión de retorno: se dibuja punteada y no cambia el plegado" }
    }
    /** Conexión cuyo texto se está editando. */
    var editingEdge by mutableStateOf<String?>(null)
    fun edge(id: String?) = edges.value.firstOrNull { it.id == id }
    fun setEdgeLabel(id: String, label: String) { editingEdge = null; run { repository.labelFlowEdge(documentId, id, label) } }
    fun drag(id: String, x: Float, y: Float) { moving[id] = x to y }
    fun drop(id: String) {
        val (x, y) = moving[id] ?: return
        run { try { repository.moveFlowNode(documentId, id, x, y) } finally { moving.remove(id) } }
    }

    fun relate(node: String, documents: List<String>, folders: List<String>) {
        relating = null
        run {
            val added = repository.linkFlowNode(documentId, node, documents, folders)
            message = when (added) { 0 -> "Ya estaban relacionados"; 1 -> "1 elemento relacionado"; else -> "$added elementos relacionados" }
        }
    }
    fun relateImage(node: String, uri: Uri) { relating = null; run { repository.linkFlowImage(documentId, node, uri, null); message = "Imagen relacionada" } }
    fun unlink(link: LinkView) { if (viewingImage?.id == link.id) viewingImage = null; run { repository.unlinkFlowNode(documentId, link.id) } }
    fun setAvatar(node: String, uri: Uri, ring: Long?) = run { repository.setFlowAvatar(documentId, node, uri = uri, ring = ring); message = "Foto guardada" }
    fun setAvatarFrom(node: String, link: LinkView, ring: Long?) = run {
        val file = when {
            link.link.imagePath != null -> repository.flowFile(documentId, link.link.imagePath)
            link.link.targetDocumentId != null -> repository.dao.document(link.link.targetDocumentId)?.let { previews.file(it) }
            else -> null
        } ?: error("Este elemento todavía no tiene vista previa")
        repository.setFlowAvatar(documentId, node, file = file, linkId = link.id, ring = ring)
        message = "Foto guardada"
    }
    fun setRing(node: String, ring: Long?) = run { repository.setFlowRing(documentId, node, ring) }
    fun clearAvatar(node: String) = run { repository.clearFlowAvatar(documentId, node) }

    /** Carga perezosa de fotos visibles; no repite rutas ya cargadas. */
    fun ensureImage(path: String, side: Int = 256) {
        if (path in images || path in loading) return
        loading += path
        viewModelScope.launch {
            try { previews.flowImage(documentId, path, side)?.let { images[path] = it } } finally { loading -= path }
        }
    }
    private val loading = mutableSetOf<String>()

    /** Destinos válidos para conectar desde `from`: todos salvo él mismo y los ya conectados en ese sentido. */
    fun targets(from: String): List<FlowNode> {
        val pairs = edges.value.map { it.fromId to it.toId }.toSet()
        return nodes.value.orEmpty().filter { it.id != from && (from to it.id) !in pairs }
    }
    fun canConnect(from: String, to: String) = from != to && edges.value.none { it.fromId == from && it.toId == to } && node(to) != null
    /** `true` si `from → to` sería un retorno: cerraría un ciclo en el árbol, así que sólo se dibuja. */
    fun isLoop(from: String, to: String) = FlowGraph.wouldCreateCycle(edges.value.filter { !it.loop }.map { it.fromId to it.toId }, from, to)
    fun reasonNotConnectable(from: String, to: String) = when {
        from == to -> null
        else -> "Esos puntos ya están conectados"
    }
}
