package com.hypermarker.browser

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermarker.storage.LibraryRepository
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

class BrowserViewModel(private val repository: LibraryRepository) : ViewModel() {
    var address by mutableStateOf("")
    var currentUrl by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    var loading by mutableStateOf(false)
    var back by mutableStateOf(false)
    var forward by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
    var savedId by mutableStateOf<String?>(null)
    var generation by mutableIntStateOf(0)
    var failed by mutableStateOf(false)
    private var failedLoad = false
    fun started(view: BrowserWebView, started: Boolean) {
        if (started) { loading = true; failedLoad = false; error = null; savedId = null }
        if (view.progress == 100) loading = false
        if (view.url != null) { currentUrl = view.url; if (started || !loading) address = view.url.orEmpty() }
        title = view.title.orEmpty(); back = view.canGoBack(); forward = view.canGoForward()
    }
    fun loadProblem(message: String) { failedLoad = true; loading = false; error = message }
    val canSave get() = !saving && !loading && !failed && !failedLoad && currentUrl?.let(WebAddress::permitted) == true
    fun save(view: BrowserWebView, folder: String?) {
        if (!canSave) return
        val source = currentUrl ?: return
        val pageTitle = title.ifBlank { source }.take(200)
        saving = true; view.capturing = true; error = null; savedId = null
        viewModelScope.launch {
            val archive = File(repository.context.cacheDir, "web-captures/${UUID.randomUUID()}.mhtml")
            try {
                repository.recover()
                withContext(Dispatchers.IO) { require(archive.parentFile!!.isDirectory || archive.parentFile!!.mkdirs()) }
                withTimeout(45_000) {
                    suspendCancellableCoroutine { continuation ->
                        view.saveWebArchive(archive.absolutePath, false) { result ->
                            if (continuation.isActive) continuation.resume(result)
                            else archive.delete()
                        }
                    }.also { require(it == archive.absolutePath) { "No se pudo descargar esta página" } }
                }
                savedId = repository.saveWebSnapshot(archive, source, pageTitle, folder).id
            } catch (_: TimeoutCancellationException) { error = "La descarga tardó demasiado. Vuelve a intentarlo." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "No se pudo guardar la página" }
            finally { withContext(NonCancellable + Dispatchers.IO) { archive.delete() }; view.capturing = false; saving = false }
        }
    }
}
