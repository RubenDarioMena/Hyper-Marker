package com.hypermarker.markdown

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermarker.storage.LibraryRepository
import kotlinx.coroutines.*

class MarkdownViewModel(private val id: String, private val repository: LibraryRepository) : ViewModel() {
    var source by mutableStateOf(""); private set
    var loaded by mutableStateOf(false); private set
    var saved by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null)
    var visual by mutableStateOf(false)
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()
    private var debounce: Job? = null
    private var saving: Job? = null
    private var version = 0
    init { viewModelScope.launch {
        try { source = repository.readMarkdown(id); loaded = true } catch (e: Exception) { error = e.message }
    } }
    fun edit(value: String, history: Boolean = true) {
        if (value == source || !loaded) return
        if (value.toByteArray().size > 2 * 1024 * 1024) { error = "La nota supera 2 MiB"; return }
        if (history) { undo.addLast(source); while (undo.size > 100 || (undo.size > 1 && undo.sumOf { it.length.toLong() * 2 } > 8L * 1024 * 1024)) undo.removeFirst(); redo.clear() }
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
            try { repository.saveMarkdown(id, text); if (version == revision) saved = true }
            catch (e: Exception) { error = "No se pudo guardar: ${e.message}" }
        }
        saving = job; job.join()
    }
    fun saveSoon() { viewModelScope.launch { save() } }
    fun unsupported(): Boolean = MarkdownPolicy.unsupported(source)
}
