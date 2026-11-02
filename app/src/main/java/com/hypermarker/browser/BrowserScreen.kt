package com.hypermarker.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, enabled = !model.saving) { Icon(Icons.AutoMirrored.Outlined.LibraryBooks, "Volver a la biblioteca") }
            OutlinedTextField(model.address, { model.address = it }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Buscar o escribir URL HTTPS") }, enabled = !model.saving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { go() }),
                trailingIcon = { IconButton(onClick = ::go, enabled = !model.saving && model.address.isNotBlank()) { Icon(Icons.Outlined.Search, "Abrir dirección o buscar en la web") } })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { web?.goBack() }, enabled = model.back && !model.saving) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Página anterior") }
            IconButton(onClick = { web?.goForward() }, enabled = model.forward && !model.saving) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Página siguiente") }
            IconButton(onClick = { if (model.failed) { model.failed = false; model.generation++ } else web?.reload() }, enabled = !model.saving && model.currentUrl != null) { Icon(Icons.Outlined.Refresh, "Recargar página") }
            TextButton(onClick = { web?.let { model.save(it, folderId) } }, enabled = model.canSave) {
                Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(6.dp)); Text(if (model.saving) "Descargando…" else "Descargar página")
            }
        }
        if (model.loading || model.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        model.error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
        model.savedId?.let { id ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Guardada sin conexión", Modifier.weight(1f))
                TextButton(onClick = { onSaved(id) }) { Text("Estudiar copia") }
            }
        }
        if (model.currentUrl == null) {
            Text("Explora una página y guárdala en esta carpeta para estudiarla sin conexión.", Modifier.padding(20.dp))
        }
        Text("Se guardan el contenido y los recursos ya cargados. Vídeos y funciones del servidor pueden no estar disponibles en la copia.",
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            // No cambiar de página durante la captura asíncrona.
            if (model.saving) androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
        }
    }
}
