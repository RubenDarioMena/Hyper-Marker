package com.hypermarker.library

import android.app.Application
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hypermarker.HyperMarkerApplication
import com.hypermarker.storage.PendingImport
import com.hypermarker.github.GitHubRepo
import com.hypermarker.github.GitHubBranch
import com.hypermarker.github.GitHubItem
import com.hypermarker.github.GitHubSyncWorker
import com.hypermarker.github.safeExport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HyperMarkerApplication
    val repository = (application as HyperMarkerApplication).repository
    val folders = repository.dao.observeFolders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val documents = repository.dao.observeDocuments().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val remoteFolders = repository.dao.observeRemoteFolders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val remoteEntries = repository.dao.observeRemoteEntries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var githubScreen by mutableStateOf(false)
    var githubMessage by mutableStateOf<String?>(null)
    var githubRepos by mutableStateOf<List<GitHubRepo>>(emptyList())
    var githubBranches by mutableStateOf<List<GitHubBranch>>(emptyList())
    var githubItems by mutableStateOf<List<GitHubItem>>(emptyList())
    var githubRepoPage by mutableIntStateOf(1)
    var githubRepoNext by mutableStateOf<Int?>(null)
    var githubBranchNext by mutableStateOf<Int?>(null)
    var githubListCapped by mutableStateOf(false)
    val hasGitHubToken get() = app.githubAuth.read() != null
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
    fun connectGitHub(token: String) = action {
        val clean = token.trim()
        require(clean.length in 20..512 && clean.none { it.isWhitespace() }) { "Credencial inválida" }
        app.githubApi.get("/user", clean, maxBytes = 128_000, authProbe = true)
        app.githubAuth.save(clean)
        repository.dao.remoteFolders().forEach { remote -> if (remote.status == "AUTH")
            repository.dao.put(remote.copy(status = "PENDING", failureCount = 0, nextRetryAt = 0)) }
        githubMessage = "Cuenta conectada. El token está cifrado en este dispositivo."
        loadGitHubReposInternal(1)
    }
    private suspend fun loadGitHubReposInternal(page: Int) {
        val result = app.githubBrowser.repositoryPage(page)
        githubRepos = (if (page == 1) result.items else githubRepos + result.items).distinctBy { it.id }
        githubRepoNext = result.nextPage; githubListCapped = result.capped
        githubRepoPage = page
    }
    fun loadGitHubRepos(page: Int = 1) = action { loadGitHubReposInternal(page) }
    fun findGitHubRepo(owner: String, repo: String, onFound: (GitHubRepo) -> Unit) = action {
        val selected = app.githubBrowser.repository(owner.trim(), repo.trim())
        loadGitHubBranchesInternal(selected, 1)
        onFound(selected)
    }
    fun loadGitHubBranches(repo: GitHubRepo, page: Int = 1) = action { loadGitHubBranchesInternal(repo, page) }
    private suspend fun loadGitHubBranchesInternal(repo: GitHubRepo, page: Int) {
        val result = app.githubBrowser.branchPage(repo.owner, repo.name, page)
        githubBranches = (if (page == 1) result.items else githubBranches + result.items).distinctBy { it.name }
        githubBranchNext = result.nextPage; githubListCapped = result.capped
    }
    fun loadGitHubDirectory(repo: GitHubRepo, branch: String, path: String) = action {
        githubItems = app.githubBrowser.directory(repo.owner, repo.name, branch, path)
    }
    fun addGitHubFolder(repo: GitHubRepo, branch: String, path: String) = action {
        val remote = app.githubSync.add(repo, branch, path, folderId)
        githubScreen = false
        folderId = remote.folderId
        githubMessage = app.githubSync.sync(remote.id, manual = true)
    }
    fun refreshGitHub(id: String, manual: Boolean = true) = action {
        githubMessage = app.githubSync.sync(id, manual)
    }
    fun disconnectGitHub() = action {
        GitHubSyncWorker.cancel(getApplication())
        app.githubSync.cancelActive()
        app.githubAuth.clear()
        repository.dao.remoteFolders().filter { it.autoSync }.forEach { repository.dao.put(it.copy(autoSync = false)) }
        githubRepos = emptyList(); githubBranches = emptyList(); githubItems = emptyList()
        githubMessage = "Cuenta desconectada. Las copias offline siguen disponibles."
    }
    fun copyRemoteMarkdown(id: String) = action { openedId = repository.copyRemoteMarkdown(id).id }
    fun copyRemoteHtml(id: String) = action { openedId = repository.copyRemoteHtml(id).id }
    fun deleteRetiredRemoteDocument(id: String) = action { repository.deleteRetiredRemoteDocument(id) }
    fun enterFolder(id: String) {
        folderId = id
        viewModelScope.launch {
            val remote = repository.dao.remoteFolderForLibrary(id) ?: return@launch
            val network = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = network.getNetworkCapabilities(network.activeNetwork)
            if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                repository.dao.put(remote.copy(status = "OFFLINE")); return@launch
            }
            if (remote.lastCheck > 0 && System.currentTimeMillis() - remote.lastCheck < 10 * 60_000) return@launch
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                repository.dao.put(remote.copy(status = "PAUSED")); return@launch
            }
            val battery = getApplication<Application>().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return@launch
            val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0 || level * 100 / scale < 20) {
                repository.dao.put(remote.copy(status = "PAUSED")); return@launch
            }
            try { app.githubSync.sync(remote.id) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "No se pudo actualizar; la copia local sigue disponible" }
        }
    }
    suspend fun githubDiskUsage(): Map<String, Long> = withContext(Dispatchers.IO) {
        repository.dao.remoteFolders().associate { remote ->
            val ids = (listOfNotNull(remote.htmlDocumentId) + repository.dao.remoteEntries(remote.id).mapNotNull { it.documentId }).toSet()
            remote.id to ids.sumOf { repository.directory(it).walkTopDown().filter { f -> f.isFile }.sumOf { f -> f.length() } }
        }
    }
    fun exportGitHubUsage(uri: Uri) = action {
        val report = app.githubGovernor.snapshot().safeExport(System.currentTimeMillis())
        withContext(Dispatchers.IO) { requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")).use { it.write(report.toByteArray()) } }
        githubMessage = "Exportación agregada guardada sin nombres de repositorio, rutas ni credenciales."
    }
    fun repairGitHubBudget() { app.githubGovernor.repairCorruptState() }
    fun githubUsage() = app.githubGovernor.snapshot()
    fun githubLimits() = app.githubGovernor.limits
    fun setGitHubPaused(value: Boolean) { app.githubGovernor.setPaused(value) }
    fun setGitHubLimits(fileMiB: Int, runMiB: Int) {
        app.githubGovernor.updateLimits(app.githubResourceSettings.save(fileMiB, runMiB))
    }
    fun toggleGitHubAuto(id: String) = action {
        val remote = requireNotNull(repository.dao.remoteFolder(id))
        repository.dao.put(remote.copy(autoSync = !remote.autoSync))
        if (repository.dao.remoteFolders().any { it.autoSync }) GitHubSyncWorker.schedule(getApplication())
        else GitHubSyncWorker.cancel(getApplication())
        githubMessage = if (remote.autoSync) "Sincronización automática desactivada" else "Sincronización automática cada 60 min como mínimo, solo sin red medida y batería suficiente"
    }
    fun unlinkGitHub(id: String) = action {
        app.githubSync.cancelActive(id)
        repository.unlinkRemoteFolder(id)
        if (repository.dao.remoteFolders().none { it.autoSync }) GitHubSyncWorker.cancel(getApplication())
        githubMessage = "Carpeta desvinculada. Documentos y anotaciones quedaron como copias locales."
    }
}
