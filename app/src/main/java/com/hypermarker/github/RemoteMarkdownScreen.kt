package com.hypermarker.github

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewAssetLoader
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.PermissionRequest
import com.hypermarker.markdown.MarkdownPolicy
import com.hypermarker.storage.Document
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.viewer.DocumentWebView
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteMarkdownScreen(document: Document, library: LibraryRepository, onClose: () -> Unit,
    onCopy: (String) -> Unit) {
    var source by remember(document.id, document.revision) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var visual by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(document.id, document.revision) {
        try { source = library.readMarkdown(document.id) } catch (e: Exception) { error = e.message }
    }
    BackHandler { onClose() }
    Scaffold(topBar = { TopAppBar(title = { Text(document.title) }, navigationIcon = {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
    }, actions = { TextButton(onClick = { scope.launch { try { onCopy(library.copyRemoteMarkdown(document.id).id) }
        catch (e: Exception) { error = e.message } } }) { Text("Crear copia editable") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            Text("GitHub · solo lectura · copia offline", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (source != null && !MarkdownPolicy.unsupported(source!!)) {
                TextButton(onClick = { visual = !visual }) { Text(if (visual) "Ver fuente" else "Ver visual") }
            } else Spacer(Modifier.height(12.dp))
            val markdown = source
            if (visual && markdown != null && !MarkdownPolicy.unsupported(markdown)) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = {
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false; settings.allowContentAccess = false
                        settings.domStorageEnabled = false; settings.blockNetworkLoads = true
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        val origin = "https://editor.hypermarker.invalid"
                        val assets = WebViewAssetLoader.Builder().setDomain("editor.hypermarker.invalid")
                            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(context)).build()
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse =
                                if (request.url.host == "editor.hypermarker.invalid") assets.shouldInterceptRequest(request.url)
                                    ?: DocumentWebView.denied() else DocumentWebView.denied()
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest) = true
                            override fun onPageFinished(view: WebView?, url: String?) {
                                evaluateJavascript("window.startEditor(${JSONObject.quote(markdown)},'remote-readonly',true);", null)
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                        }
                        loadUrl("$origin/editor/index.html")
                    }
                }, onRelease = { it.stopLoading(); it.destroy() })
            } else SelectionContainer {
                Text(markdown ?: "Cargando…", Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
            }
        }
    }
    error?.let { AlertDialog(onDismissRequest = { error = null }, title = { Text("No se pudo abrir") },
        text = { Text(it) }, confirmButton = { TextButton(onClick = { error = null }) { Text("Entendido") } }) }
}
