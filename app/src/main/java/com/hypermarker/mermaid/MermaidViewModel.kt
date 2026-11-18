package com.hypermarker.mermaid

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.storage.MERMAID_MAX_CHARS
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Reúne las respuestas del visor que llegan en fragmentos (`index`/`total`). Rechaza saltos, totales cambiantes y
 * cualquier respuesta que exceda `maxChars`: el contenido del WebView no es de confianza.
 */
class ChunkAssembler(private val maxChars: Int) {
    private val parts = StringBuilder()
    private var expected = -1
    private var next = 0
    /** Devuelve el texto completo al recibir el último fragmento; `null` mientras faltan. */
    fun accept(index: Int, total: Int, data: String): String? {
        require(total in 1..10_000 && (expected == -1 || expected == total)) { "Respuesta del visor inválida" }
        require(index == next) { "Fragmento fuera de orden" }
        require(parts.length + data.length <= maxChars) { "La exportación supera el tamaño permitido" }
        expected = total; next++; parts.append(data)
        return if (next == total) parts.toString() else null
    }
}

/** Tamaño real de un PNG: misma regla que `pngSize` del visor (lado ≤ 8192 px y ≤ 40 MP, proporción intacta). */
fun pngSize(width: Double, height: Double, requested: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return 0 to 0
    val scale = minOf(requested.coerceIn(1, 8).toDouble(), 8192 / width, 8192 / height, sqrt(40_000_000 / (width * height)))
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

data class RenderState(val ok: Boolean, val type: String? = null, val width: Double = 0.0, val height: Double = 0.0, val error: String? = null)
data class CopyResult(val flowId: String, val flowTitle: String, val created: Boolean, val summary: String)

class MermaidViewModel(val id: String, val repository: LibraryRepository) : ViewModel() {
    var source by mutableStateOf(""); private set
    var loaded by mutableStateOf(false); private set
    var saved by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var code by mutableStateOf(false)
    var render by mutableStateOf<RenderState?>(null)
    var busy by mutableStateOf<String?>(null)
    var copied by mutableStateOf<CopyResult?>(null)
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    val flows = repository.dao.observeDocuments().map { docs -> docs.filter { it.type == "FLOW" }.sortedByDescending { maxOf(it.updatedAt, it.lastOpenedAt ?: 0) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()
    private var debounce: Job? = null
    private var saving: Job? = null
    private var version = 0
    init { viewModelScope.launch {
        try { source = repository.readMermaid(id); loaded = true; code = source.isBlank() } catch (e: Exception) { error = e.message }
    } }

    fun edit(value: String, history: Boolean = true) {
        if (value == source || !loaded) return
        if (value.length > MERMAID_MAX_CHARS) { error = "El diagrama supera $MERMAID_MAX_CHARS caracteres"; return }
        if (history) { undo.addLast(source); while (undo.size > 100) undo.removeFirst(); redo.clear() }
        source = value; version++; saved = false
        canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty()
        debounce?.cancel(); debounce = viewModelScope.launch { delay(500); save() }
    }
    fun undo() { if (undo.isNotEmpty()) { redo.addLast(source); edit(undo.removeLast(), false) } }
    fun redo() { if (redo.isNotEmpty()) { undo.addLast(source); edit(redo.removeLast(), false) } }
    suspend fun save() {
        if (!loaded || saved) return
        val text = source; val revision = version; val previous = saving
        val job = viewModelScope.launch {
            previous?.join()
            try { repository.saveMermaid(id, text); if (version == revision) saved = true }
            catch (e: Exception) { error = "No se pudo guardar: ${e.message}" }
        }
        saving = job; job.join()
    }
    fun saveSoon() { viewModelScope.launch { save() } }

    /** Copia el flowchart (estructura y posiciones del visor) a un Flow nuevo junto al diagrama o a uno existente. */
    fun copyToFlow(graphJson: String, target: String?, title: String, folder: String?) {
        viewModelScope.launch {
            busy = "Copiando al Flow…"
            try {
                val graph = withContext(Dispatchers.Default) { MermaidFlow.parse(graphJson) }
                if (!graph.supported) { notice = "Sólo los flowcharts, diagramas de estados y mapas mentales se convierten en Flow. Este es «${diagramName(graph.type)}»."; return@launch }
                val date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date())
                val (flow, plan) = repository.copyMermaidToFlow(graph, target, folder, title, title to "Copia del diagrama Mermaid «$title» · $date")
                copied = CopyResult(flow.id, flow.title, target == null, plan.summary())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "No se pudo copiar al Flow" }
            finally { busy = null }
        }
    }
}

