package com.hypermarker.markdown

import android.annotation.SuppressLint
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.*
import com.hypermarker.storage.Document
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.viewer.DocumentWebView
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownScreen(document: Document, repository: LibraryRepository, onClose: () -> Unit) {
    val model: MarkdownViewModel = viewModel(key = "markdown-${document.id}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = MarkdownViewModel(document.id, repository) as T
    })
    val scope = rememberCoroutineScope()
    var warning by remember { mutableStateOf(false) }
    var visualWeb by remember { mutableStateOf<WebView?>(null) }
    var visualGeneration by remember { mutableIntStateOf(0) }
    var webReady by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun flushVisual(then: () -> Unit) {
        val editor = visualWeb
        if (model.visual && webReady && editor != null) editor.evaluateJavascript("window.editorMarkdown?.()") { value ->
            runCatching { val text = org.json.JSONTokener(value).nextValue(); if (text is String) model.edit(text) }
            then()
        } else then()
    }
    fun close() { flushVisual { scope.launch { model.save(); if (model.saved) onClose() } } }
    BackHandler { close() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) model.saveSoon() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer); model.saveSoon() }
    }
    Scaffold(topBar = { TopAppBar(title = { Column { Text(document.title, maxLines = 1); Text(if (model.saved) "Guardado" else "Guardando…", style = MaterialTheme.typography.labelSmall) } },
        navigationIcon = { IconButton(onClick = ::close) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Guardar y volver") } }, actions = {
            IconButton(enabled = model.canUndo, onClick = { model.undo(); if (model.visual) visualGeneration++ }) { Icon(Icons.AutoMirrored.Outlined.Undo, "Deshacer") }
            IconButton(enabled = model.canRedo, onClick = { model.redo(); if (model.visual) visualGeneration++ }) { Icon(Icons.AutoMirrored.Outlined.Redo, "Rehacer") }
        }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!model.visual, { flushVisual { model.visual = false } }, { Text("Source") })
                FilterChip(model.visual, {
                    if (!model.visual) {
                        if (model.unsupported()) warning = true
                        else if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) model.error = "Actualiza Android System WebView para utilizar el editor visual."
                        else model.visual = true
                    }
                }, { Text("Visual") })
            }
            if (!model.visual) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                    listOf("H" to "\n## ", "B" to " **texto**", "I" to " *texto*", "Lista" to "\n- ", "Enlace" to " [texto](https://)", "Código" to " `código`").forEach { (label, value) ->
                        TextButton(onClick = { model.edit(model.source + value) }) { Text(label) }
                    }
                }
                OutlinedTextField(model.source, model::edit, Modifier.fillMaxSize().padding(16.dp), enabled = model.loaded,
                    placeholder = { Text("# Una idea nueva\n\nEmpieza a escribir…") }, textStyle = MaterialTheme.typography.bodyLarge)
            } else key(visualGeneration) {
                val session = remember { UUID.randomUUID().toString() }
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                    webReady = false; initialized = false
                    WebView(context).apply {
                        visualWeb = this
                        settings.javaScriptEnabled = true; settings.allowFileAccess = false; settings.allowContentAccess = false
                        settings.domStorageEnabled = false; settings.blockNetworkLoads = true
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        val origin = "https://editor.hypermarker.invalid"
                        val assets = WebViewAssetLoader.Builder().setDomain("editor.hypermarker.invalid")
                            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(context)).build()
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.addWebMessageListener(this, "MarkdownBridge", setOf(origin)) { _, message, sourceOrigin, main, _ ->
                            if (main && sourceOrigin.toString().trimEnd('/') == origin && (message.data?.length ?: 0) <= 2_200_000) runCatching {
                                val data = JSONObject(message.data.orEmpty())
                                if (data.optString("sessionId") != session) return@runCatching
                                when (data.optString("type")) {
                                    "change" -> if (initialized) model.edit(data.getJSONObject("payload").getString("source"))
                                    "loaded" -> { initialized = true; webReady = true }
                                    "error" -> { model.error = data.getJSONObject("payload").optString("message"); model.visual = false }
                                }
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse =
                                if (request.url.host == "editor.hypermarker.invalid") assets.shouldInterceptRequest(request.url) ?: DocumentWebView.denied() else DocumentWebView.denied()
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest) = true
                            override fun onPageFinished(view: WebView?, url: String?) {
                                evaluateJavascript("window.startEditor(${JSONObject.quote(model.source)},${JSONObject.quote(session)});", null)
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) { model.error = "Editor visual: ${consoleMessage.message().take(500)}" }
                                return true
                            }
                        }
                        loadUrl("$origin/editor/index.html")
                    }
                }, onRelease = { it.stopLoading(); it.destroy(); if (visualWeb === it) visualWeb = null })
            }
        }
    }
    if (warning) AlertDialog(onDismissRequest = { warning = false }, title = { Text("Conserva esta nota en Source") },
        text = { Text("La nota contiene sintaxis que no podemos preservar fielmente en Visual (por ejemplo HTML, imágenes, tablas o extensiones). El texto fuente se conserva completo; puedes seguir editándolo aquí.") },
        confirmButton = { TextButton(onClick = { warning = false }) { Text("Continuar en Source") } })
    model.error?.let { error -> AlertDialog(onDismissRequest = { model.error = null }, title = { Text("Aviso de Markdown") }, text = { Text(error) }, confirmButton = { TextButton(onClick = { model.error = null }) { Text("Entendido") } }) }
}
