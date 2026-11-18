package com.hypermarker.mermaid

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Base64
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.hypermarker.preview.PreviewStore
import com.hypermarker.storage.Document
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.ui.*
import com.hypermarker.ui.theme.LocalHm
import com.hypermarker.viewer.DocumentWebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

private const val HOST = "mermaid.hypermarker.invalid"
private const val ORIGIN = "https://$HOST"
private const val MAX_MESSAGE = 1_000_000
private const val MAX_PNG_BASE64 = 96_000_000
private const val MAX_SVG = 24_000_000
private const val MAX_GRAPH = 16_000_000

/** Petición en curso al visor: reúne fragmentos y entrega el texto completo. */
private class Pending(val assembler: ChunkAssembler, val done: (String, JSONObject) -> Unit)

/**
 * Diagrama Mermaid: el código es la autoridad (se guarda solo), el dibujo lo hace Mermaid local en un WebView
 * aislado sin red. Desde aquí se copia a un Flow nuevo o existente y se exporta a SVG o PNG por SAF.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidScreen(document: Document, repository: LibraryRepository, previews: PreviewStore, onClose: () -> Unit, onOpen: (String) -> Unit) {
    val model: MermaidViewModel = viewModel(key = "mermaid-${document.id}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = MermaidViewModel(document.id, repository) as T
    })
    val hm = LocalHm.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { UUID.randomUUID().toString() }
    val pending = remember { HashMap<String, Pending>() }
    var web by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var flowPicker by remember { mutableStateOf(false) }
    var pngChooser by remember { mutableStateOf(false) }
    var pngScale by remember { mutableIntStateOf(3) }
    val field = rememberTextFieldState()

    fun call(script: String) { web?.evaluateJavascript(script, null) }
    fun request(kind: String, max: Int, script: (String) -> String, done: (String, JSONObject) -> Unit) {
        val view = web
        if (view == null || !ready) { model.error = "El visor todavía se está cargando"; return }
        val id = UUID.randomUUID().toString()
        pending[id] = Pending(ChunkAssembler(max), done)
        model.busy = when (kind) { "graph" -> "Leyendo el diagrama…"; "png" -> "Generando PNG…"; else -> "Generando SVG…" }
        view.evaluateJavascript(script(id), null)
    }
    fun write(uri: Uri, bytes: ByteArray, label: String) = scope.launch {
        try {
            withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "No se pudo abrir el destino" }.use { it.write(bytes) } }
            model.notice = "$label guardado"
        } catch (e: Exception) { model.error = "No se pudo guardar: ${e.message}" }
        finally { model.busy = null }
    }
    val quoted = { value: String -> JSONObject.quote(value) }
    val svgTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")) { uri ->
        if (uri != null) request("svg", MAX_SVG, { id -> "window.exportDiagram(${quoted(model.source)},${quoted(id)},'svg')" }) { text, _ ->
            write(uri, text.toByteArray(Charsets.UTF_8), "SVG")
        }
    }
    val pngTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) request("png", MAX_PNG_BASE64, { id -> "window.exportDiagram(${quoted(model.source)},${quoted(id)},'png',$pngScale)" }) { text, meta ->
            val bytes = runCatching { Base64.decode(text, Base64.DEFAULT) }.getOrNull()
            if (bytes == null || bytes.size < 8 || bytes[1] != 'P'.code.toByte()) { model.busy = null; model.error = "El visor devolvió un PNG inválido" }
            else write(uri, bytes, "PNG de ${meta.optInt("width")} × ${meta.optInt("height")} px")
        }
    }
    fun copyTo(target: String?) = request("graph", MAX_GRAPH, { id -> "window.requestGraph(${quoted(model.source)},${quoted(id)})" }) { json, _ ->
        model.busy = null; model.copyToFlow(json, target, document.title, document.folderId)
    }
    val fileName = remember(document.title) { document.title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(80).ifBlank { "diagrama" } }

    fun close() {
        scope.launch {
            model.save()
            if (!model.saved) return@launch
            web?.let { runCatching { previews.captureView(document, it) } }
            onClose()
        }
    }
    BackHandler { close() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) model.saveSoon() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer); model.saveSoon() }
    }
    // Código ⇄ campo de texto (undo/redo y carga cambian la fuente fuera del campo).
    LaunchedEffect(model.source) { if (field.text.toString() != model.source) field.edit {
        val caret = selection.end.coerceAtMost(model.source.length); replace(0, length, model.source); selection = TextRange(caret)
    } }
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect { value ->
        if (value != model.source) { model.edit(value); if (model.source != value) field.edit { replace(0, length, model.source) } }
    } }
    // Redibuja poco después de dejar de escribir; el visor conserva el último diagrama válido si hay un error.
    LaunchedEffect(model.source, ready, model.loaded) {
        if (!ready || !model.loaded) return@LaunchedEffect
        delay(350)
        call("window.renderDiagram(${quoted(model.source)},${quoted(session)})")
    }
    LaunchedEffect(model.notice) { if (model.notice != null) { delay(4_000); model.notice = null } }

    Column(Modifier.fillMaxSize().background(hm.paper).windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::close) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Guardar y volver", tint = hm.ink) }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(document.title, color = hm.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                val render = model.render
                val status = buildString {
                    append(if (model.saved) "Guardado" else "Guardando…")
                    when {
                        render == null -> {}
                        render.ok -> append(" · ").append(diagramName(render.type))
                        else -> append(" · Error en el código")
                    }
                }
                Text(status, color = if (render?.ok == false) hm.danger else hm.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (model.code) {
                IconButton(enabled = model.canUndo, onClick = model::undo) { Icon(Icons.AutoMirrored.Outlined.Undo, "Deshacer", tint = if (model.canUndo) hm.ink else hm.muted) }
                IconButton(enabled = model.canRedo, onClick = model::redo) { Icon(Icons.AutoMirrored.Outlined.Redo, "Rehacer", tint = if (model.canRedo) hm.ink else hm.muted) }
            }
        }
        Segmented(listOf("Diagrama" to Icons.Outlined.AccountTree, "Código" to Icons.Outlined.Code), if (model.code) 1 else 0,
            onSelect = { model.code = it == 1 }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp), height = 40.dp)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                ready = false
                WebView(context).apply {
                    web = this
                    setBackgroundColor(android.graphics.Color.WHITE)
                    settings.javaScriptEnabled = true; settings.allowFileAccess = false; settings.allowContentAccess = false
                    settings.domStorageEnabled = false; settings.blockNetworkLoads = true; settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW; settings.setGeolocationEnabled(false)
                    settings.setSupportZoom(true); settings.builtInZoomControls = true; settings.displayZoomControls = false
                    settings.useWideViewPort = true; settings.loadWithOverviewMode = false
                    val assets = WebViewAssetLoader.Builder().setDomain(HOST).addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(context)).build()
                    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) model.error = "Actualiza Android System WebView para ver diagramas."
                    else WebViewCompat.addWebMessageListener(this, "MermaidBridge", setOf(ORIGIN)) { _, message, sourceOrigin, main, _ ->
                        val text = message.data ?: return@addWebMessageListener
                        if (!main || sourceOrigin.toString().trimEnd('/') != ORIGIN || text.length > MAX_MESSAGE) return@addWebMessageListener
                        runCatching {
                            val data = JSONObject(text)
                            if (data.optString("sessionId") != session) return@runCatching
                            val payload = data.getJSONObject("payload")
                            when (data.optString("type")) {
                                "rendered" -> model.render = if (payload.optBoolean("ok")) RenderState(true, payload.optString("diagramType").take(80),
                                    payload.optDouble("width").takeIf { it.isFinite() } ?: 0.0, payload.optDouble("height").takeIf { it.isFinite() } ?: 0.0)
                                    else RenderState(false, error = payload.optString("error").take(2_000))
                                "export" -> {
                                    val id = data.optString("requestId"); val target = pending[id] ?: return@runCatching
                                    val complete = try { target.assembler.accept(payload.getInt("index"), payload.getInt("total"), payload.getString("data")) }
                                        catch (e: Exception) { pending.remove(id); model.busy = null; model.error = e.message; return@runCatching }
                                    if (complete != null) { pending.remove(id); target.done(complete, payload) }
                                }
                                "failed" -> { pending.remove(data.optString("requestId")); model.busy = null; model.error = payload.optString("message").take(2_000) }
                            }
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse =
                            if (request.url.host == HOST && request.url.path.orEmpty().startsWith("/mermaid/")) assets.shouldInterceptRequest(request.url) ?: DocumentWebView.denied()
                            else DocumentWebView.denied()
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest) = true
                        override fun onPageFinished(view: WebView?, url: String?) { ready = true }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                    }
                    loadUrl("$ORIGIN/mermaid/index.html")
                }
            }, onRelease = { it.stopLoading(); it.destroy(); if (web === it) web = null; ready = false })
            if (model.code) Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState())) {
                BasicTextField(state = field, enabled = model.loaded,
                    textStyle = TextStyle(color = Color(0xFF14201C), fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 21.sp),
                    cursorBrush = SolidColor(Color(0xFF14201C)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp).padding(horizontal = 16.dp, vertical = 16.dp))
                Spacer(Modifier.height(96.dp))
            }
            val failure = model.render?.takeIf { !it.ok }?.error
            if (model.code && failure != null) Text(failure, color = Color(0xFF8A1C12), fontSize = 13.sp, fontFamily = FontFamily.Monospace, maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFDECEA)).padding(12.dp))
            (model.busy ?: model.notice)?.let { Toast(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) }
        }
        if (!model.code) {
            HmDivider()
            val canAct = model.loaded && model.busy == null && model.render?.ok == true
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                StackedAction(Icons.Outlined.Hub, "Flow nuevo", onClick = { copyTo(null) }, enabled = canAct)
                StackedAction(Icons.Outlined.AddCircleOutline, "Añadir a Flow", onClick = { flowPicker = true }, enabled = canAct)
                StackedAction(Icons.Outlined.Polyline, "SVG", onClick = { svgTarget.launch("$fileName.svg") }, enabled = canAct)
                StackedAction(Icons.Outlined.Image, "PNG", onClick = { pngChooser = true }, enabled = canAct)
            }
        }
    }

    if (flowPicker) {
        val flows by model.flows.collectAsState()
        HmSheet(onDismiss = { flowPicker = false }, title = "Añadir a un Flow") {
            Text("Se copia el diagrama a la derecha de lo que ya hay, con un punto de cabecera para compararlo. El Flow no queda enlazado al código.",
                Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), color = hm.muted, style = MaterialTheme.typography.bodySmall)
            if (flows.isEmpty()) Text("Todavía no hay Flows. Usa «Flow nuevo».", Modifier.padding(20.dp), color = hm.muted)
            LazyColumn(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                items(flows, key = { it.id }) { flow ->
                    HmRow(flow.title, "Flow", leading = { IconTile(kindIcon("FLOW")) }, onClick = { flowPicker = false; copyTo(flow.id) })
                    HmDivider()
                }
            }
        }
    }
    if (pngChooser) {
        val render = model.render
        AlertDialog(onDismissRequest = { pngChooser = false }, containerColor = hm.paper, title = { Text("Exportar PNG") }, text = {
            Column {
                listOf(2, 3, 4).forEach { scale ->
                    val (w, h) = pngSize(render?.width ?: 0.0, render?.height ?: 0.0, scale)
                    Row(Modifier.fillMaxWidth().selectable(pngScale == scale, role = Role.RadioButton) { pngScale = scale }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(pngScale == scale, onClick = null)
                        Text("${scale}×  ·  $w × $h px", Modifier.padding(start = 12.dp), color = hm.ink)
                    }
                }
                Text("Fondo blanco. Los diagramas muy grandes se reducen hasta 8192 px por lado.", Modifier.padding(top = 8.dp), color = hm.muted, style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = { pngChooser = false; pngTarget.launch("$fileName.png") }) { Text("Elegir destino") } },
            dismissButton = { TextButton(onClick = { pngChooser = false }) { Text("Cancelar") } })
    }
    model.copied?.let { result ->
        AlertDialog(onDismissRequest = { model.copied = null }, containerColor = hm.paper,
            title = { Text(if (result.created) "Flow creado" else "Añadido a «${result.flowTitle}»") }, text = { Text(result.summary) },
            confirmButton = { TextButton(onClick = { model.copied = null; scope.launch { model.save(); onOpen(result.flowId) } }) { Text("Abrir Flow") } },
            dismissButton = { TextButton(onClick = { model.copied = null }) { Text("Seguir aquí") } })
    }
    model.error?.let { error ->
        AlertDialog(onDismissRequest = { model.error = null }, containerColor = hm.paper, title = { Text("Diagrama") }, text = { Text(error) },
            confirmButton = { TextButton(onClick = { model.error = null }) { Text("Entendido") } })
    }
}

/** Nombre legible del tipo que devuelve `mermaid.parse`. */
fun diagramName(type: String?): String = when {
    type == null -> "Diagrama"
    type.startsWith("flowchart") -> "Flowchart"
    type.startsWith("sequence") -> "Secuencia"
    type.startsWith("class") -> "Clases"
    type.startsWith("state") -> "Estados"
    type == "er" -> "Entidad-relación"
    type == "gantt" -> "Gantt"
    type == "mindmap" -> "Mapa mental"
    type == "pie" -> "Circular"
    type == "timeline" -> "Línea de tiempo"
    else -> type.replaceFirstChar { it.uppercase() }
}
