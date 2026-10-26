package com.hypermarker.viewer

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermarker.storage.*
import com.hypermarker.storage.Annotation
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Pulsación prolongada validada: abre la cruceta en `x`,`y` (px de la vista) o ejecuta `direct` si vino del menú. */
data class CrossRequest(val mode: String, val payload: JSONObject, val pageKey: String, val x: Float, val y: Float, val direct: ReaderAction? = null) {
    val hasTarget get() = if (mode == "EDIT") payload.has("anchor") else payload.optJSONObject("target") != null
    fun ids(key: String): List<String> = payload.optJSONArray(key)?.let { list -> (0 until list.length()).map { list.optString(it) } }.orEmpty()
}

class ReaderViewModel(val document: Document, private val repository: LibraryRepository, private val crossStore: CrossLayoutStore? = null) : ViewModel() {
    var annotations by mutableStateOf<List<Annotation>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set
    var saved by mutableStateOf(true); private set
    var mode by mutableStateOf("VIEW")
    var tool by mutableStateOf("pen")
    var color by mutableStateOf("#176b5b")
    var width by mutableFloatStateOf(3f)
    var speed by mutableFloatStateOf(60f)
    var direction by mutableIntStateOf(1)
    var playing by mutableStateOf(false)
    var fullscreen by mutableStateOf(true)
    var preference by mutableStateOf(ViewerPreference(document.id))
    var editor by mutableStateOf<JSONObject?>(null)
    var editingId by mutableStateOf<String?>(null)
    var reanchoringId by mutableStateOf<String?>(null)
    var textEditor by mutableStateOf<JSONObject?>(null)
    var cross by mutableStateOf<CrossRequest?>(null)
    var pendingAction by mutableStateOf<ReaderAction?>(null)
    var layout by mutableStateOf(crossStore?.load() ?: CrossLayout.defaults); private set
    var wheelsEnabled by mutableStateOf(crossStore?.wheelsEnabled() ?: true); private set
    var pinArmed by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    val logs = mutableStateListOf<String>()
    private val undo = ArrayDeque<List<Annotation>>()
    private val redo = ArrayDeque<List<Annotation>>()
    private var saveJob: Job? = null
    init { viewModelScope.launch {
        try { annotations = repository.annotations(document.id); preference = repository.preference(document.id); speed = preference.speed; direction = preference.direction; loaded = true }
        catch (e: Exception) { error = e.message }
    } }
    fun log(value: String) { logs.add(value.take(2200)); while (logs.size > 300) logs.removeAt(0) }
    private fun update(values: List<Annotation>, history: Boolean = true) {
        if (!loaded || values == annotations) return
        if (values.size > 10_000 || values.sumOf { it.payload.length.toLong() * 2 } > 32L * 1024 * 1024) {
            error = "Las anotaciones de este documento alcanzaron el límite de almacenamiento. Elimina trazos o notas antes de continuar."; return
        }
        if (history) { undo.addLast(annotations); while (undo.size > 100) undo.removeFirst(); redo.clear() }
        annotations = values
        canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty()
        persist(values)
    }
    private fun persist(values: List<Annotation>) {
        saved = false
        val previous = saveJob
        saveJob = viewModelScope.launch {
            previous?.join()
            try { repository.replaceAnnotations(document.id, values); if (annotations == values) saved = true } catch (e: Exception) { error = "No se guardaron las anotaciones: ${e.message}" }
        }
    }
    fun undo() { if (undo.isNotEmpty()) { val previous = undo.removeLast(); redo.addLast(annotations); update(previous, false) } }
    fun redo() { if (redo.isNotEmpty()) { val next = redo.removeLast(); undo.addLast(annotations); update(next, false) } }
    fun clearDrawing(page: String) = update(annotations.filterNot { it.kind == "STROKE" && it.pageKey == page })
    fun delete(id: String) = update(annotations.filterNot { it.id == id })
    /** Sólo elimina ids del tipo y página indicados; la lista proviene de la página y no es confiable. */
    fun deleteRelated(ids: List<String>, kind: String, page: String) = update(annotations.filterNot { it.id in ids && it.kind == kind && it.pageKey == page })
    fun count(ids: List<String>, kind: String, page: String) = annotations.count { it.id in ids && it.kind == kind && it.pageKey == page }
    fun assign(mode: String, slot: Int, action: ReaderAction) {
        val next = CrossLayout.assign(layout.getValue(mode), slot, action)
        layout = layout + (mode to next); crossStore?.save(mode, next)
    }
    fun resetLayout(mode: String) { val next = CrossLayout.defaults.getValue(mode); layout = layout + (mode to next); crossStore?.save(mode, next) }
    fun updateWheelsEnabled(enabled: Boolean) {
        wheelsEnabled = enabled; pinArmed = false; playing = false; cross = null; crossStore?.setWheelsEnabled(enabled)
    }
    fun jsonAnnotations(): JSONArray = JSONArray().apply {
        annotations.forEach { put(JSONObject().put("id", it.id).put("kind", it.kind).put("pageKey", it.pageKey).put("payload", JSONObject(it.payload))) }
    }
    fun openPin(id: String) {
        val pin = annotations.firstOrNull { it.id == id && it.kind == "PIN" } ?: return
        editingId = id; editor = JSONObject(pin.payload).put("pageKey", pin.pageKey)
    }
    fun openPinEditor(payload: JSONObject, page: String) {
        val old = reanchoringId?.let { id -> annotations.firstOrNull { it.id == id } }
        editingId = old?.id
        editor = JSONObject().put("anchor", payload.getJSONObject("anchor")).put("pageKey", page)
            .put("boundary", payload.optString("boundary")).put("title", old?.let { JSONObject(it.payload).optString("title") } ?: "")
            .put("body", old?.let { JSONObject(it.payload).optString("body") } ?: "")
        reanchoringId = null
    }
    fun openTextEditor(request: CrossRequest) {
        val text = request.payload.optJSONObject("text")
        if (text?.optBoolean("editable") != true) { error = "Este elemento no tiene texto editable. Mantén presionado un párrafo, título, enlace o botón."; return }
        textEditor = JSONObject().put("anchor", request.payload.getJSONObject("anchor")).put("pageKey", request.pageKey).put("text", text)
    }
    /** Guarda un override separado del HTML original; volver al texto original lo elimina. */
    fun saveText(value: String) {
        val data = textEditor ?: return
        val info = data.getJSONObject("text"); val page = data.getString("pageKey")
        require(value.length <= 20_000) { "Texto demasiado largo" }
        val existing = annotations.firstOrNull { it.id == info.optString("overrideId") && it.kind == "TEXT" && it.pageKey == page }
        textEditor = null
        if (value.trim() == info.optString("original").trim()) { if (existing != null) delete(existing.id); return }
        val payload = existing?.let { JSONObject(it.payload).put("text", value) }
            ?: JSONObject().put("anchor", data.getJSONObject("anchor")).put("original", info.getString("raw")).put("text", value)
        update(annotations.filterNot { it.id == existing?.id } + Annotation(existing?.id ?: UUID.randomUUID().toString(), document.id, "TEXT", page, payload.toString()))
    }
    fun event(message: JSONObject, x: Float = 0f, y: Float = 0f) {
        if (!loaded) return
        val payload = message.getJSONObject("payload")
        val page = payload.optString("pageKey").take(4096)
        when (message.getString("type")) {
            "annotation.activated" -> openPin(payload.optString("id"))
            "longpress" -> if (wheelsEnabled && (mode == "VIEW" || mode == "DRAW")) cross = CrossRequest(mode, payload, page, x, y)
            "target.selected" -> {
                if (mode != "EDIT") return
                if (reanchoringId != null) openPinEditor(payload, page)
                else if (!wheelsEnabled && pinArmed) { pinArmed = false; openPinEditor(payload, page) }
                else { cross = CrossRequest(mode, payload, page, x, y, pendingAction); pendingAction = null }
            }
            "stroke.completed" -> if (mode == "DRAW") update(annotations + Annotation(UUID.randomUUID().toString(), document.id, "STROKE", page, payload.toString()))
            "stroke.erase" -> if (mode == "DRAW") {
                val id = payload.optString("id")
                if (annotations.any { it.id == id && it.kind == "STROKE" && it.pageKey == page }) delete(id)
            }
            "anchor.unresolved" -> {
                val ids = payload.optJSONArray("ids") ?: return
                if (ids.length() > 10_000) return
                val missing = (0 until ids.length()).map { ids.optString(it) }.toSet()
                update(annotations.map { item ->
                    if (item.kind == "STROKE" || item.pageKey != page) item
                    else {
                        val data = JSONObject(item.payload); val anchor = data.getJSONObject("anchor")
                        val status = if (item.id in missing) "UNANCHORED" else "RESOLVED"
                        if (anchor.optString("status") == status) item else { anchor.put("status", status); item.copy(payload = data.toString()) }
                    }
                }, false)
            }
        }
    }
    fun savePin(title: String, body: String) {
        require(title.length <= 500 && body.length <= 100_000) { "Nota demasiado larga" }
        if (title.isBlank() && body.isBlank()) { editor = null; editingId = null; return }
        val data = editor ?: return
        val id = editingId ?: UUID.randomUUID().toString()
        val payload = JSONObject().put("title", title.trim()).put("body", body).put("anchor", data.getJSONObject("anchor"))
        update(annotations.filterNot { it.id == id } + Annotation(id, document.id, "PIN", data.getString("pageKey"), payload.toString()))
        editor = null; editingId = null
    }
    fun retrySave() { error = null; persist(annotations) }
    suspend fun flush(): Boolean { saveJob?.join(); if (!saved) { retrySave(); saveJob?.join() }; return saved }
    fun network(allowed: Boolean) { playing = false; pinArmed = false; mode = "VIEW"; viewModelScope.launch { try { repository.setNetwork(document.id, allowed) } catch (e: Exception) { error = e.message } } }
    fun savePosition(y: Int, zoom: Int, path: String?) {
        preference = ViewerPreference(document.id, zoom, y, speed, direction, path)
        viewModelScope.launch { try { repository.preference(preference) } catch (e: Exception) { error = e.message } }
    }
}
