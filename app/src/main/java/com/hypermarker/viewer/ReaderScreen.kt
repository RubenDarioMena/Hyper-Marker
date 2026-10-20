package com.hypermarker.viewer

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hypermarker.storage.Document
import com.hypermarker.storage.LibraryRepository
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(document: Document, repository: LibraryRepository, onClose: () -> Unit, onMarkdown: () -> Unit) {
    val model: ReaderViewModel = viewModel(key = "reader-${document.id}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ReaderViewModel(document, repository) as T
    })
    var web by remember { mutableStateOf<DocumentWebView?>(null) }
    var tools by remember { mutableStateOf(false) }
    var console by remember { mutableStateOf(false) }
    var pins by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var targets by remember { mutableStateOf<JSONArray?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var pendingInspect by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val activity = checkNotNull(LocalActivity.current)
    fun pageKey(): String {
        val uri = Uri.parse(web?.url ?: "")
        val hash = uri.encodedFragment.orEmpty()
        return uri.encodedPath.orEmpty() + (uri.encodedQuery?.let { "?$it" } ?: "") + if (hash.startsWith('/') || hash.startsWith('!')) "#$hash" else ""
    }
    fun rememberPosition() { web?.let { view ->
        @Suppress("DEPRECATION") val zoom = (view.scale / activity.resources.displayMetrics.density * 100).toInt().coerceIn(25, 500)
        val uri = Uri.parse(view.url)
        model.savePosition(view.scrollY, zoom, uri.encodedPath + (uri.encodedQuery?.let { "?$it" } ?: "") + (uri.encodedFragment?.let { "#$it" } ?: ""))
    } }
    fun close() { rememberPosition(); scope.launch { if (model.flush()) onClose() } }
    DisposableEffect(model.fullscreen) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (model.fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, web) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { model.playing = false; rememberPosition(); web?.onPause() }
            if (event == Lifecycle.Event.ON_RESUME) web?.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler {
        if (model.mode != "VIEW") { model.mode = "VIEW"; model.reanchoringId = null }
        else if (web?.canGoBack() == true) web?.goBack() else close()
    }
    LaunchedEffect(web, model.mode, ready) { if (ready) { if (model.mode != "VIEW") model.playing = false; web?.changeMode(model.mode) } }
    LaunchedEffect(web, model.annotations, ready) { if (ready) web?.command("annotations", model.jsonAnnotations()) }
    LaunchedEffect(web, model.tool, model.color, model.width, ready) { if (ready) web?.command("tool", JSONObject().put("tool", model.tool).put("color", model.color).put("width", model.width)) }
    LaunchedEffect(web, model.playing, model.speed, model.direction, ready) { if (ready) web?.command("autoscroll", JSONObject().put("speed", if (model.playing) model.speed * model.direction else 0)) }
    Box(Modifier.fillMaxSize()) {
        if (model.loaded) key(document.id, document.networkAllowed, generation) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                ready = false
                DocumentWebView(context, document, repository,
                    event = { event ->
                        if (event.optString("type") == "controller.ready") {
                            ready = true; web?.command("annotations", model.jsonAnnotations())
                            pendingInspect?.let { id -> web?.command("inspect", JSONObject().put("id", id)); pendingInspect = null }
                        }
                        model.event(event)
                    }, console = model::log,
                    finished = { view ->
                        model.mode = "VIEW"; model.playing = false
                        if (!view.bridgeAvailable) model.error = "Actualiza Android System WebView para habilitar JavaScript seguro y anotaciones. El HTML estático sigue disponible."
                    }, crashed = { ready = false; model.error = "El visor se cerró. Tus originales y anotaciones guardadas siguen disponibles; vuelve a cargar el documento." }
                ).also { view ->
                    web = view; var restoreAllowed = true
                    view.touched = { model.playing = false; restoreAllowed = false }
                    view.setInitialScale(model.preference.zoom)
                    val lastPage = model.preference.pagePath
                    if (lastPage != null) runCatching { view.openRelativePage(lastPage) }.onFailure { view.open() }
                    else view.open()
                    view.postDelayed({ if (restoreAllowed && view.scrollY == 0) view.scrollTo(0, model.preference.scrollY) }, 900)
                }
            }, onRelease = { view ->
                view.stopLoading(); view.command("cancel"); view.removeAllViews(); view.destroy()
                if (web === view) web = null
            })
        }
        if (model.mode != "VIEW") Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(8.dp), shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (model.mode == "PIN") "Pin · mantén y desliza" else "Dibujar", Modifier.weight(1f))
                    IconButton(onClick = model::undo, enabled = model.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, "Deshacer") }
                    IconButton(onClick = model::redo, enabled = model.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, "Rehacer") }
                    IconButton(onClick = { model.mode = "VIEW"; model.reanchoringId = null }) { Icon(Icons.Outlined.Close, "Salir del modo") }
                }
                if (model.mode == "DRAW") {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("pen" to "Pluma", "highlighter" to "Resaltador", "eraser" to "Borrador").forEach { (tool, label) -> FilterChip(model.tool == tool, { model.tool = tool }, { Text(label) }, Modifier.padding(end = 4.dp)) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("#176b5b" to "Verde", "#d54f46" to "Rojo", "#e5b62f" to "Amarillo", "#334c8f" to "Azul", "#202020" to "Negro").forEach { (color, label) ->
                            FilterChip(model.color == color, { model.color = color }, { Text(label) }, Modifier.padding(end = 4.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ancho")
                        Slider(model.width, { model.width = it }, valueRange = 1f..15f, modifier = Modifier.weight(1f))
                        IconButton(onClick = { clear = true }) { Icon(Icons.Outlined.DeleteSweep, "Borrar dibujos de esta página") }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { tools = true }, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(18.dp), containerColor = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Outlined.Tune, "Herramientas del documento") }
    }
    if (tools) ModalBottomSheet(onDismissRequest = { tools = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 30.dp)) {
            Text(document.title, style = MaterialTheme.typography.titleLarge)
            Text(if (document.networkAllowed) "Recursos HTTPS permitidos para este documento" else "Sin acceso a red · archivos locales", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("VIEW" to "Ver", "PIN" to "Pin", "DRAW" to "Dibujar").forEach { (mode, label) ->
                    FilterChip(model.mode == mode, { model.mode = mode; tools = false }, { Text(label) }, enabled = mode == "VIEW" || web?.bridgeAvailable == true)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { web?.goBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás en la página") }
                IconButton(onClick = { web?.goForward() }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Adelante") }
                IconButton(onClick = { ready = false; model.mode = "VIEW"; model.playing = false; generation++; tools = false }) { Icon(Icons.Outlined.Refresh, "Recargar") }
                IconButton(onClick = { web?.zoomOut() }) { Icon(Icons.Outlined.ZoomOut, "Alejar") }
                IconButton(onClick = { web?.zoomIn() }) { Icon(Icons.Outlined.ZoomIn, "Acercar") }
                IconButton(onClick = { model.fullscreen = !model.fullscreen }) { Icon(Icons.Outlined.Fullscreen, "Alternar pantalla completa") }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Desplazamiento automático", Modifier.weight(1f))
                Switch(model.playing, { model.mode = "VIEW"; model.playing = it })
            }
            Text("${model.speed.toInt()} px/s · ${if (model.direction == 1) "hacia abajo" else "hacia arriba"}")
            Slider(model.speed, { model.speed = it }, valueRange = 10f..250f)
            TextButton(onClick = { model.direction *= -1 }) { Text("Cambiar dirección") }
            TextButton(onClick = {
                tools = false; model.mode = "PIN"
                web?.changeMode("PIN")
                web?.evaluateJavascript("JSON.stringify(window.HyperMarker?.accessibleTargets()||[])") { value ->
                    runCatching { targets = JSONArray(org.json.JSONTokener(value).nextValue() as String) }.onFailure { model.error = "No se pudieron listar los elementos" }
                }
            }, enabled = web?.bridgeAvailable == true) { Text("Elegir elemento para pin sin mantener") }
            TextButton(onClick = { pins = true; tools = false }) { Text("Notas ancladas y sin ancla (${model.annotations.count { it.kind == "PIN" }})") }
            TextButton(onClick = { tools = false; rememberPosition(); scope.launch { if (model.flush()) onMarkdown() } }) { Text("Crear nota Markdown") }
            TextButton(onClick = { console = true; tools = false }) { Text("Consola (${model.logs.size})") }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Permitir recursos de red HTTPS", Modifier.weight(1f)); Switch(document.networkAllowed, { ready = false; model.network(it) }) }
            Text("Los dibujos siguen coordenadas de página. Si el texto cambia de tamaño o posición, pueden desalinearse.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { tools = false; close() }) { Text("Volver a la biblioteca") }
        }
    }
    if (console) ModalBottomSheet(onDismissRequest = { console = false }) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Consola del documento", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { model.logs.clear() }) { Text("Limpiar") }
            SelectionContainer { LazyColumn(Modifier.heightIn(max = 440.dp)) { items(model.logs.toList()) { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 5.dp)) } } }
        }
    }
    if (pins) ModalBottomSheet(onDismissRequest = { pins = false }) {
        Text("Pines del documento", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
        LazyColumn(Modifier.heightIn(max = 480.dp)) { items(model.annotations.filter { it.kind == "PIN" }, key = { it.id }) { item ->
            val data = JSONObject(item.payload)
            ListItem(headlineContent = { Text(data.optString("title").ifBlank { "Nota" }) }, supportingContent = { Text(if (data.getJSONObject("anchor").optString("status") == "UNANCHORED") "Sin ancla · reanclar manualmente" else "Anclada · ${item.pageKey}") },
                trailingContent = { TextButton(onClick = { pins = false; model.openPin(item.id) }) { Text("Abrir") } })
        } }
    }
    targets?.let { list -> AlertDialog(onDismissRequest = { targets = null }, title = { Text("Selecciona un elemento visible") }, text = {
        LazyColumn { items((0 until list.length()).toList()) { index -> val item = list.getJSONObject(index)
            TextButton(onClick = { targets = null; web?.command("selectTarget", item) }) { Text(item.getString("label")) }
        } }
    }, confirmButton = { TextButton(onClick = { targets = null }) { Text("Cancelar") } }) }
    model.editor?.let { data ->
        var title by remember(data) { mutableStateOf(data.optString("title")) }
        var body by remember(data) { mutableStateOf(data.optString("body")) }
        ModalBottomSheet(onDismissRequest = { model.editor = null; model.editingId = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(20.dp)) {
                Text(if (model.editingId == null) "Nueva nota anclada" else "Nota anclada", style = MaterialTheme.typography.titleLarge)
                Text(data.optString("boundary").ifBlank { "Anotación independiente del HTML original" }, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(title, { if (it.length <= 500) title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                OutlinedTextField(body, { if (it.length <= 100_000) body = it }, label = { Text("Nota · Markdown") }, placeholder = { Text("## Idea\nEscribe **énfasis**, listas o enlaces…") }, minLines = 4, maxLines = 10, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { body += " **texto**" }) { Text("Negrita") }
                    TextButton(onClick = { body += "\n- " }) { Text("Lista") }
                    TextButton(onClick = { body += " [texto](https://)" }) { Text("Enlace") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { model.editor = null; model.editingId = null }) { Text("Cancelar") }
                    Button(onClick = { runCatching { model.savePin(title, body) }.onFailure { model.error = it.message } }, enabled = title.isNotBlank() || body.isNotBlank()) { Text("Guardar") }
                }
                model.editingId?.let { id ->
                    Row {
                        TextButton(onClick = { model.editor = null; model.reanchoringId = id; model.mode = "PIN" }) { Text("Mover / reanclar") }
                        TextButton(onClick = { model.delete(id); model.editor = null; model.editingId = null }) { Text("Eliminar pin") }
                        TextButton(onClick = { model.editor = null
                            val pin = model.annotations.first { it.id == id }
                            if (pin.pageKey == pageKey()) web?.command("inspect", JSONObject().put("id", id))
                            else runCatching { pendingInspect = id; web?.openRelativePage(pin.pageKey) }.onFailure { pendingInspect = null; model.error = it.message } }) { Text("Mostrar ancla") }
                    }
                }
            }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("¿Borrar todos los dibujos de esta página?") },
        confirmButton = { TextButton(onClick = { model.clearDrawing(pageKey()); clear = false }) { Text("Borrar") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancelar") } })
    model.error?.let { error -> AlertDialog(onDismissRequest = { model.error = null }, title = { Text("Aviso del lector") }, text = { Text(error) }, confirmButton = { TextButton(onClick = { if (!model.saved) model.retrySave() else model.error = null }) { Text(if (!model.saved) "Reintentar guardado" else "Entendido") } }) }
}
