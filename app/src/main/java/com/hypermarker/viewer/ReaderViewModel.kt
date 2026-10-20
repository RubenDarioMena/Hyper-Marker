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

class ReaderViewModel(val document: Document, private val repository: LibraryRepository) : ViewModel() {
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
    fun jsonAnnotations(): JSONArray = JSONArray().apply {
        annotations.forEach { put(JSONObject().put("id", it.id).put("kind", it.kind).put("pageKey", it.pageKey).put("payload", JSONObject(it.payload))) }
    }
    fun openPin(id: String) {
        val pin = annotations.firstOrNull { it.id == id && it.kind == "PIN" } ?: return
        editingId = id; editor = JSONObject(pin.payload).put("pageKey", pin.pageKey)
    }
    fun event(message: JSONObject) {
        if (!loaded) return
        val payload = message.getJSONObject("payload")
        val page = payload.optString("pageKey").take(4096)
        when (message.getString("type")) {
            "annotation.activated" -> openPin(payload.optString("id"))
            "target.selected" -> {
                if (mode != "PIN") return
                val old = reanchoringId?.let { id -> annotations.firstOrNull { it.id == id } }
                editingId = old?.id
                editor = JSONObject().put("anchor", payload.getJSONObject("anchor")).put("pageKey", page)
                    .put("boundary", payload.optString("boundary")).put("title", old?.let { JSONObject(it.payload).optString("title") } ?: "")
                    .put("body", old?.let { JSONObject(it.payload).optString("body") } ?: "")
                reanchoringId = null
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
                    if (item.kind != "PIN" || item.pageKey != page) item
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
    fun network(allowed: Boolean) { playing = false; mode = "VIEW"; viewModelScope.launch { try { repository.setNetwork(document.id, allowed) } catch (e: Exception) { error = e.message } } }
    fun savePosition(y: Int, zoom: Int, path: String?) {
        preference = ViewerPreference(document.id, zoom, y, speed, direction, path)
        viewModelScope.launch { try { repository.preference(preference) } catch (e: Exception) { error = e.message } }
    }
}
