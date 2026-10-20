package com.hypermarker.library

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hypermarker.HyperMarkerApplication
import com.hypermarker.storage.PendingImport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as HyperMarkerApplication).repository
    val folders = repository.dao.observeFolders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val documents = repository.dao.observeDocuments().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var folderId by mutableStateOf<String?>(null)
    var openedId by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var pending by mutableStateOf<PendingImport?>(null)
    private var pendingParent: String? = null
    private val ready = CompletableDeferred<Unit>()
    private var job: Job? = null
    init { viewModelScope.launch { try { repository.recover(); ready.complete(Unit) } catch (e: Exception) { error = e.message; ready.completeExceptionally(e) } } }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        job = viewModelScope.launch {
            busy = true
            try { ready.await(); block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "No se pudo completar la operación" }
            finally { busy = false }
        }
    }
    fun receive(uris: List<Uri>, tree: Boolean = false) = action {
        openedId = null
        require(pending == null) { "Elige una entrada o cancela la importación anterior" }
        pendingParent = folderId
        if (!tree && uris.size == 1 && repository.importer.displayName(uris.first()).endsWith(".md", true)) {
            openedId = repository.importMarkdown(uris.first(), folderId).id
        } else {
            val prepared = withContext(Dispatchers.IO) { repository.importer.prepare(uris, tree) }
            if (prepared.preferred != null) openedId = repository.finishImport(prepared, prepared.preferred, pendingParent).id
            else pending = prepared
        }
    }
    fun chooseEntry(entry: String) = action {
        val value = pending ?: return@action
        pending = null
        openedId = repository.finishImport(value, entry, pendingParent).id
    }
    fun cancel() { job?.cancel() }
    fun cancelImport() { val value = pending; pending = null; viewModelScope.launch(Dispatchers.IO) { value?.directory?.deleteRecursively() } }
}
