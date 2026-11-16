package com.hypermarker.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import com.hypermarker.picker.*
import com.hypermarker.ui.*
import com.hypermarker.ui.theme.LocalHm
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hypermarker.storage.LibraryRepository

@Composable
fun BrowserScreen(repository: LibraryRepository, initialUrl: String?, folderId: String?, onClose: () -> Unit, onSaved: (String) -> Unit) {
    val model: BrowserViewModel = viewModel(key = "browser", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = BrowserViewModel(repository) as T
    })
    var web by remember { mutableStateOf<BrowserWebView?>(null) }
    var lastInitial by rememberSaveable { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    fun go() {
        if (model.saving) return
        runCatching { WebAddress.resolve(model.address) }.onSuccess { url ->
            keyboard?.hide(); model.error = null
            if (model.failed) { model.currentUrl = url; model.failed = false; model.generation++ }
            else web?.open(url)
        }.onFailure { model.error = it.message }
    }
    BackHandler { if (!model.saving) { if (web?.canGoBack() == true) web?.goBack() else onClose() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, web) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) web?.onPause()
            if (event == Lifecycle.Event.ON_RESUME) web?.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val hm = LocalHm.current
    val folders by repository.dao.observeFolders().collectAsState(emptyList())
    val documents by repository.dao.observeDocuments().collectAsState(emptyList())
    val remote by repository.dao.observeRemoteFolders().collectAsState(emptyList())
    var choosing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(hm.paper).windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding()) {
        if (model.loading || model.saving) LinearProgressIndicator(Modifier.fillMaxWidth(), color = hm.accent, trackColor = hm.divider)
        Box(Modifier.weight(1f)) {
            if (!model.failed) key(model.generation) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                    BrowserWebView(context, model::started, model::loadProblem, crashed = {
                        model.failed = true; model.loading = false; model.error = "El navegador se cerró. Pulsa Recargar para volver a abrir la página."
                    }).also { view ->
                        web = view
                        val url = if (initialUrl != null && initialUrl != lastInitial) initialUrl else model.currentUrl
                        lastInitial = initialUrl
                        url?.takeIf(WebAddress::permitted)?.let(view::open)
                    }
                }, onRelease = { view -> view.stopLoading(); view.destroy(); if (web === view) web = null })
            }
            if (model.currentUrl == null) Column(Modifier.fillMaxSize().background(hm.paper).padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                IconTile(Icons.Outlined.Language, size = 72.dp)
                Text("Explora una página y guárdala para estudiarla sin conexión.", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text("Escribe una dirección HTTPS o una búsqueda abajo.", Modifier.padding(top = 6.dp), color = hm.muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            // No cambiar de página durante la captura asíncrona.
            if (model.saving) Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
        }
        // Barra inferior (pantalla 3): guardar, aviso, dirección y navegación.
        Column(Modifier.fillMaxWidth().background(hm.paper)) {
            HmDivider()
            model.error?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = hm.danger, style = MaterialTheme.typography.bodySmall) }
            val saved = model.savedId
            if (saved != null) PrimaryButton("Estudiar la copia guardada", { onSaved(saved) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), icon = Icons.Outlined.MenuBook)
            else PrimaryButton(if (model.saving) "Guardando copia…" else "Guardar copia para estudiar", { choosing = true },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp), enabled = model.canSave, icon = Icons.Outlined.Download)
            Text("Se guarda lo ya cargado. Vídeos y funciones del servidor pueden faltar.", Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                color = hm.muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            OutlinedTextField(model.address, { model.address = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, shape = RoundedCornerShape(50),
                placeholder = { Text("Buscar o escribir URL HTTPS", color = hm.muted) }, enabled = !model.saving,
                leadingIcon = { Icon(if (model.currentUrl != null) Icons.Outlined.Lock else Icons.Outlined.Search, null, tint = hm.muted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { go() }),
                trailingIcon = {
                    if (model.address != model.currentUrl.orEmpty() && model.address.isNotBlank()) IconButton(onClick = ::go, enabled = !model.saving) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Abrir dirección o buscar en la web", tint = hm.accent) }
                    else IconButton(onClick = { if (model.failed) { model.failed = false; model.generation++ } else web?.reload() }, enabled = !model.saving && model.currentUrl != null) { Icon(Icons.Outlined.Refresh, "Recargar página", tint = hm.ink) }
                },
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = hm.surface, focusedContainerColor = hm.surface, unfocusedBorderColor = hm.border))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { web?.goBack() }, enabled = model.back && !model.saving) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Página anterior", tint = if (model.back) hm.ink else hm.border) }
                IconButton(onClick = { web?.goForward() }, enabled = model.forward && !model.saving) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Página siguiente", tint = if (model.forward) hm.ink else hm.border) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose, enabled = !model.saving) {
                    Icon(Icons.AutoMirrored.Outlined.LibraryBooks, null, tint = hm.ink); Spacer(Modifier.width(8.dp)); Text("Biblioteca", color = hm.ink)
                }
            }
        }
    }
    if (choosing) {
        var keep by remember { mutableStateOf(folderId) }
        val state = remember(folders, documents, remote) {
            ItemPickerState(PickerMode.DESTINATION, folders.map { PickFolder(it.id, it.parentId, it.name) }, documents.map { PickDocument(it.id, it.folderId, it.title, it.type) },
                readOnlyRoots = remote.map { it.folderId }.toSet()).apply { keep?.takeIf { id -> folders.any { it.id == id } && !isReadOnly(id) }?.let(::enter) }
        }
        val recent = remember { RecentFolders(repository.context) }
        ItemPickerSheet(state, PickerSubject("Guardar copia de", model.title.ifBlank { model.currentUrl.orEmpty() }, icon = Icons.Outlined.Download), recent = remember { recent.load() },
            actionLabel = { "Guardar en «$it»" }, onDismiss = { choosing = false },
            onDestination = { parent -> choosing = false; recent.use(parent); web?.let { model.save(it, parent) } },
            onCreateFolder = { parent, name -> keep = parent; scope.launch { runCatching { repository.createFolder(parent, name) }.onFailure { model.error = it.message } } })
    }
}
