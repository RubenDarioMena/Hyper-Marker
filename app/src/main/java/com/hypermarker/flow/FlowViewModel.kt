package com.hypermarker.flow

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermarker.storage.FlowEdge
import com.hypermarker.storage.FlowNode
import com.hypermarker.storage.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Borrador de nota: `node == null` significa un punto nuevo que sólo se crea al guardar. */
data class FlowDraft(val node: String?, val x: Float, val y: Float, val title: String, val body: String)

class FlowViewModel(val documentId: String, private val repository: LibraryRepository) : ViewModel() {
    /** `null` hasta la primera lectura de Room, para no encuadrar la cámara sobre un grafo aún vacío. */
    val nodes = repository.dao.observeFlowNodes(documentId).stateIn<List<FlowNode>?>(viewModelScope, SharingStarted.Eagerly, null)
    val edges = repository.dao.observeFlowEdges(documentId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** La cámara es estado de sesión: sobrevive a la rotación pero no se guarda en el documento. */
    var camera by mutableStateOf<FlowCamera?>(null)
    var rearrange by mutableStateOf(false)
    var selected by mutableStateOf<String?>(null)
    var draft by mutableStateOf<FlowDraft?>(null)
    var connecting by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    /** Posiciones provisionales mientras se arrastra en Reordenar; se guardan al soltar. */
    val moving = mutableStateMapOf<String, Pair<Float, Float>>()

    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { message = e.message ?: "No se pudo completar" }
    }
    fun node(id: String?): FlowNode? = nodes.value.orEmpty().firstOrNull { it.id == id }
    fun position(node: FlowNode) = moving[node.id] ?: (node.x to node.y)

    fun startCreate(x: Float, y: Float) { selected = null; draft = FlowDraft(null, x, y, "", "") }
    fun startEdit(id: String) { node(id)?.let { draft = FlowDraft(it.id, it.x, it.y, it.title, it.body) } }
    fun saveDraft(title: String, body: String) {
        val value = draft ?: return
        if (title.isBlank() && body.isBlank()) { draft = null; return }
        draft = null
        run {
            if (value.node == null) selected = repository.createFlowNode(documentId, value.x, value.y, title, body).id
            else repository.updateFlowNode(documentId, value.node, title, body)
        }
    }
    fun toggleCollapsed(id: String) { val node = node(id) ?: return; run { repository.setFlowCollapsed(documentId, id, !node.collapsed) } }
    fun delete(id: String) { if (selected == id) selected = null; run { repository.deleteFlowNode(documentId, id) } }
    fun disconnect(edge: FlowEdge) = run { repository.deleteFlowEdge(documentId, edge.id) }
    fun connect(from: String, to: String) { connecting = null; run { repository.connectFlowNodes(documentId, from, to) } }
    fun drag(id: String, x: Float, y: Float) { moving[id] = x to y }
    fun drop(id: String) {
        val (x, y) = moving[id] ?: return
        run { try { repository.moveFlowNode(documentId, id, x, y) } finally { moving.remove(id) } }
    }

    /** Destinos válidos para conectar desde `from`: excluye conexiones existentes y las que crearían un ciclo. */
    fun targets(from: String): List<FlowNode> {
        val pairs = edges.value.map { it.fromId to it.toId }
        return nodes.value.orEmpty().filter { it.id != from && (from to it.id) !in pairs && !FlowGraph.wouldCreateCycle(pairs, from, it.id) }
    }
    fun canConnect(from: String, to: String) = targets(from).any { it.id == to }
    fun reasonNotConnectable(from: String, to: String) = when {
        from == to -> null
        edges.value.any { it.fromId == from && it.toId == to } -> "Esos puntos ya están conectados"
        else -> "La conexión crearía un ciclo"
    }
}
