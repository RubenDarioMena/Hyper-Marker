package com.hypermarker.viewer

import android.graphics.RectF
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hypermarker.markdown.MarkdownFormatter
import com.hypermarker.markdown.TextEdit
import com.hypermarker.markdown.edit
import com.hypermarker.markdown.value
import com.hypermarker.storage.Document
import com.hypermarker.storage.LibraryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val MODE_LABELS = mapOf("VIEW" to "Ver", "EDIT" to "Editar", "DRAW" to "Dibujar")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(document: Document, repository: LibraryRepository, onClose: () -> Unit, onMarkdown: () -> Unit) {
    val context = LocalContext.current
    val model: ReaderViewModel = viewModel(key = "reader-${document.id}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ReaderViewModel(document, repository, CrossLayoutStore(context)) as T
    })
    var web by remember { mutableStateOf<DocumentWebView?>(null) }
    var chrome by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var console by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var customize by remember { mutableStateOf(false) }
    var targets by remember { mutableStateOf<JSONArray?>(null) }
    var centerCross by remember { mutableStateOf(false) }
    var drawPanel by remember { mutableStateOf<ReaderAction?>(null) }
    var swapAction by remember { mutableStateOf<ReaderAction?>(null) }
    var reassignSlot by remember { mutableStateOf<Int?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, CrossRequest>?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var tones by remember { mutableStateOf(GlassTone.LIGHT to GlassTone.LIGHT) }
    var crossTone by remember { mutableStateOf(GlassTone.LIGHT) }
    var generation by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var pendingInspect by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val activity = checkNotNull(LocalActivity.current)
    val density = LocalDensity.current.density
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
    fun dismissCross() { model.cross = null; web?.command("cancel") }
    fun changeMode(value: String) { model.mode = value; model.reanchoringId = null; model.pendingAction = null; drawPanel = null; menu = false; model.cross = null; chrome = true }
    fun pickTarget(action: ReaderAction?) {
        model.pendingAction = action; centerCross = action == null; model.mode = "EDIT"; menu = false
        web?.changeMode("EDIT")
        web?.evaluateJavascript("JSON.stringify(window.HyperMarker?.accessibleTargets()||[])") { value ->
            runCatching { targets = JSONArray(org.json.JSONTokener(value).nextValue() as String) }.onFailure { model.pendingAction = null; model.error = "No se pudieron listar los elementos" }
        }
    }
    fun perform(action: ReaderAction, request: CrossRequest?) {
        model.cross = null; web?.command("cancel")
        if (!action.available) { hint = "${action.label}: próximamente."; return }
        if (action.target && request == null) {
            if (action == ReaderAction.ZOOM_FIT) hint = "Mantén presionado un elemento para acercarlo." else pickTarget(action)
            return
        }
        when (action) {
            ReaderAction.AUTOSCROLL -> { model.mode = "VIEW"; model.playing = !model.playing; chrome = false }
            ReaderAction.ZOOM_FIT -> request?.payload?.optJSONObject("target")?.optJSONObject("rect")?.let { r ->
                model.playing = false; web?.zoomToFit(r.getDouble("x"), r.getDouble("y"), r.getDouble("width"), r.getDouble("height"))
            } ?: run { hint = "No hay un elemento para acercar en ese punto." }
            ReaderAction.ZOOM_RESET -> web?.resetZoom()
            ReaderAction.RELOAD -> { ready = false; model.mode = "VIEW"; model.playing = false; generation++ }
            ReaderAction.BACK -> if (web?.canGoBack() == true) web?.goBack() else hint = "No hay páginas anteriores."
            ReaderAction.FORWARD -> if (web?.canGoForward() == true) web?.goForward() else hint = "No hay páginas siguientes."
            ReaderAction.FULLSCREEN -> model.fullscreen = !model.fullscreen
            ReaderAction.NOTES -> notes = true
            ReaderAction.PIN -> request?.let { model.openPinEditor(it.payload, it.pageKey) }
            ReaderAction.EDIT_TEXT -> request?.let(model::openTextEditor)
            ReaderAction.DELETE_PINS, ReaderAction.RESTORE_TEXT -> request?.let { selection ->
                val kind = if (action == ReaderAction.DELETE_PINS) "PIN" else "TEXT"
                if (model.count(selection.ids(if (kind == "PIN") "pins" else "texts"), kind, selection.pageKey) == 0)
                    hint = if (kind == "PIN") "Este elemento no tiene notas adjuntas." else "Este elemento no tiene cambios de texto."
                else confirm = kind to selection
            }
            ReaderAction.COLOR, ReaderAction.WIDTH, ReaderAction.TOOL -> { drawPanel = action; chrome = true }
            ReaderAction.STABILIZER -> Unit
            ReaderAction.CLEAR -> clear = true
        }
    }
    fun sampleTones() {
        val view = web ?: return
        if (view.width == 0) return
        val w = view.width.toFloat(); val h = view.height.toFloat(); val d = density
        val values = view.luminance(listOf(RectF(w - 200 * d, 0f, w, 120 * d), RectF(w / 2 - 170 * d, h - 110 * d, w / 2 + 170 * d, h)))
        tones = GlassTone.of(values[0]) to GlassTone.of(values[1])
    }
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
        when {
            model.cross != null -> dismissCross()
            menu -> menu = false
            drawPanel != null -> drawPanel = null
            model.mode != "VIEW" -> changeMode("VIEW")
            web?.canGoBack() == true -> web?.goBack()
            else -> close()
        }
    }
    LaunchedEffect(web, model.mode, ready) { if (ready) { if (model.mode != "VIEW") model.playing = false; web?.changeMode(model.mode) } }
    LaunchedEffect(web, model.annotations, ready) { if (ready) web?.command("annotations", model.jsonAnnotations()) }
    LaunchedEffect(web, model.tool, model.color, model.width, ready) { if (ready) web?.command("tool", JSONObject().put("tool", model.tool).put("color", model.color).put("width", model.width)) }
    LaunchedEffect(web, model.playing, model.speed, model.direction, ready) { if (ready) web?.command("autoscroll", JSONObject().put("speed", if (model.playing) model.speed * model.direction else 0)) }
    LaunchedEffect(chrome, model.mode, ready) { if (chrome && ready) sampleTones() }
    // En Ver el cromo se oculta solo; en Editar y Dibujar permanece hasta otro toque.
    LaunchedEffect(chrome, menu, model.mode, model.cross, drawPanel) {
        if (chrome && !menu && model.mode == "VIEW" && model.cross == null && drawPanel == null) { delay(5000); chrome = false }
    }
    LaunchedEffect(model.cross) {
        val request = model.cross ?: return@LaunchedEffect
        if (request.direct != null) { perform(request.direct, request); return@LaunchedEffect }
        val view = web
        if (centerCross && view != null) { centerCross = false; model.cross = request.copy(x = view.width / 2f, y = view.height / 2f); return@LaunchedEffect }
        val reach = 120 * density
        crossTone = GlassTone.of(view?.luminance(listOf(RectF(request.x - reach, request.y - reach, request.x + reach, request.y + reach)))?.firstOrNull())
    }
    LaunchedEffect(hint) { if (hint != null) { delay(2600); hint = null } }
    Box(Modifier.fillMaxSize()) {
        if (model.loaded) key(document.id, document.networkAllowed, generation) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                ready = false
                DocumentWebView(context, document, repository,
                    event = { event ->
                        when (event.optString("type")) {
                            "controller.ready" -> {
                                ready = true; web?.command("annotations", model.jsonAnnotations())
                                pendingInspect?.let { id -> web?.command("inspect", JSONObject().put("id", id)); pendingInspect = null }
                            }
                            "tap" -> if (model.cross == null) { chrome = !chrome; menu = false; drawPanel = null }
                            "hold" -> web?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                        model.event(event, web?.lastTouchX ?: 0f, web?.lastTouchY ?: 0f)
                    }, console = model::log,
                    finished = { view ->
                        model.mode = "VIEW"; model.playing = false
                        if (!view.bridgeAvailable) model.error = "Actualiza Android System WebView para habilitar JavaScript seguro y anotaciones. El HTML estático sigue disponible."
                    }, crashed = { ready = false; model.error = "El visor se cerró. Tus originales y anotaciones guardadas siguen disponibles; vuelve a cargar el documento." }
                ).also { view ->
                    web = view; var restoreAllowed = true
                    view.touched = { model.playing = false; restoreAllowed = false }
                    view.tapped = { chrome = !chrome; menu = false }
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
        val insets = Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
        AnimatedVisibility(chrome && model.cross == null, Modifier.align(Alignment.TopEnd), enter = fadeIn(), exit = fadeOut()) {
            Row(insets.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (model.mode != "VIEW") {
                    GlassButton(Icons.AutoMirrored.Outlined.Undo, "Deshacer", tones.first, enabled = model.canUndo, size = 44, onClick = model::undo)
                    GlassButton(Icons.AutoMirrored.Outlined.Redo, "Rehacer", tones.first, enabled = model.canRedo, size = 44, onClick = model::redo)
                }
                GlassButton(Icons.Outlined.MoreHoriz, "Opciones del documento", tones.first, selected = menu, size = 44) { menu = !menu; drawPanel = null }
            }
        }
        AnimatedVisibility(chrome && model.cross == null, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            ModeBar(model.mode, tones.second, web?.bridgeAvailable == true, ::changeMode, insets.padding(bottom = 16.dp))
        }
        model.cross?.takeIf { it.direct == null }?.let { request ->
            val actions = model.layout.getValue(request.mode)
            CrossMenu(request, actions, crossTone, model.playing, onAction = { perform(it, request) }, onReassign = { reassignSlot = it }, onDismiss = ::dismissCross)
        }
        drawPanel?.let { kind ->
            DrawPanel(kind, tones.second, model.color, model.width, model.tool,
                onColor = { model.color = it; if (model.tool == "eraser") model.tool = "pen"; drawPanel = null },
                onWidth = { model.width = it }, onTool = { model.tool = it; drawPanel = null }, onDismiss = { drawPanel = null })
        }
        if (menu) MenuPanel(tones.first, onDismiss = { menu = false }) {
            val tone = tones.first
            Text(document.title, color = tone.content, style = MaterialTheme.typography.titleMedium, maxLines = 2, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            Text(if (document.networkAllowed) "Recursos HTTPS permitidos" else "Sin acceso a red · archivos locales", color = tone.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
            val cross = model.layout.getValue(model.mode)
            MenuHeader("Más acciones · ${MODE_LABELS[model.mode]}", tone)
            CrossLayout.rest(model.mode, cross).forEach { action ->
                MenuRow(action.icon(model.playing), action.label, tone,
                    supporting = when { !action.available -> "Próximamente"; action == ReaderAction.ZOOM_FIT -> "Mantén presionado un elemento"; action.target -> "Elegir elemento de una lista"; else -> null },
                    enabled = action.available && action != ReaderAction.ZOOM_FIT,
                    trailing = { IconButton(onClick = { swapAction = action }) { Icon(Icons.Outlined.SwapHoriz, "Mover ${action.label} a la cruceta", tint = tone.content) } }) { menu = false; perform(action, null) }
            }
            MenuRow(Icons.Outlined.Tune, "Personalizar cruceta", tone, supporting = cross.joinToString(" · ") { it.label }) { menu = false; customize = true }
            if (model.mode == "EDIT") MenuRow(Icons.Outlined.Accessibility, "Elegir elemento sin mantener", tone, supporting = "Abre la cruceta para un elemento de la lista") { pickTarget(null) }
            if (model.mode == "DRAW") MenuRow(toolIcon(model.tool), "Herramienta: ${DRAW_TOOLS.first { it.first == model.tool }.second}", tone) { menu = false; drawPanel = ReaderAction.TOOL }
            if (model.mode == "VIEW") {
                MenuHeader("Autoscroll", tone)
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${model.speed.toInt()} px/s", color = tone.content, fontSize = 13.sp, modifier = Modifier.width(64.dp))
                    Slider(model.speed, { model.speed = it }, valueRange = 10f..250f, colors = tone.sliderColors(), modifier = Modifier.weight(1f).semantics { contentDescription = "Velocidad del autoscroll" })
                }
                MenuRow(Icons.Outlined.SwapVert, if (model.direction == 1) "Dirección: hacia abajo" else "Dirección: hacia arriba", tone) { model.direction *= -1 }
            }
            MenuHeader("Documento", tone)
            MenuRow(Icons.Outlined.Terminal, "Consola (${model.logs.size})", tone) { menu = false; console = true }
            MenuRow(Icons.Outlined.EditNote, "Crear nota Markdown", tone) { menu = false; rememberPosition(); scope.launch { if (model.flush()) onMarkdown() } }
            MenuRow(Icons.Outlined.Language, "Permitir recursos HTTPS", tone, trailing = { Switch(document.networkAllowed, { ready = false; model.network(it) }, colors = SwitchDefaults.colors(checkedTrackColor = tone.content, checkedThumbColor = if (tone == GlassTone.LIGHT) Color.White else Color.Black, uncheckedThumbColor = tone.muted, uncheckedTrackColor = Color.Transparent, uncheckedBorderColor = tone.muted)) }) { ready = false; model.network(!document.networkAllowed) }
            MenuRow(Icons.AutoMirrored.Outlined.LibraryBooks, "Volver a la biblioteca", tone) { menu = false; close() }
            Text("Toca la página para mostrar u ocultar los controles. Mantén presionado para abrir la cruceta; mantén una opción para reasignarla. Los dibujos siguen coordenadas de página y pueden desalinearse si el texto se reacomoda.",
                color = tone.muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        }
        hint?.let { message ->
            Text(message, color = tones.first.content, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopCenter).then(insets).padding(top = 72.dp, start = 24.dp, end = 24.dp)
                .glass(RoundedCornerShape(50), tones.first, solid = true).padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
    swapAction?.let { action ->
        SlotPicker("Mover ${action.label} a la cruceta", model.layout.getValue(model.mode), action, onPick = { slot -> model.assign(model.mode, slot, action); swapAction = null }, onDismiss = { swapAction = null })
    }
    if (customize) SlotPicker("Personalizar cruceta · ${MODE_LABELS[model.mode]}", model.layout.getValue(model.mode), null, onPick = { customize = false; reassignSlot = it }, onDismiss = { customize = false })
    reassignSlot?.let { slot ->
        val mode = model.cross?.mode ?: model.mode
        ActionPicker(mode, slot, model.layout.getValue(mode), onPick = { model.assign(mode, slot, it); reassignSlot = null }, onReset = { model.resetLayout(mode); reassignSlot = null }, onDismiss = { reassignSlot = null })
    }
    if (console) ModalBottomSheet(onDismissRequest = { console = false }) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Consola del documento", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { model.logs.clear() }) { Text("Limpiar") }
            SelectionContainer { LazyColumn(Modifier.heightIn(max = 440.dp)) { items(model.logs.toList()) { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 5.dp)) } } }
        }
    }
    if (notes) ModalBottomSheet(onDismissRequest = { notes = false }) {
        Text("Notas y cambios de texto", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
        val items = model.annotations.filter { it.kind == "PIN" || it.kind == "TEXT" }
        if (items.isEmpty()) Text("Aún no hay notas ni cambios. En Editar, mantén presionado un elemento.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        LazyColumn(Modifier.heightIn(max = 480.dp)) { items(items, key = { it.id }) { item ->
            val data = JSONObject(item.payload)
            val unanchored = data.getJSONObject("anchor").optString("status") == "UNANCHORED"
            if (item.kind == "PIN") ListItem(headlineContent = { Text(data.optString("title").ifBlank { "Nota" }) }, leadingContent = { Icon(Icons.Outlined.PushPin, null) },
                supportingContent = { Text(if (unanchored) "Sin ancla · reanclar manualmente" else "Anclada · ${item.pageKey}") },
                trailingContent = { TextButton(onClick = { notes = false; model.openPin(item.id) }) { Text("Abrir") } })
            else ListItem(headlineContent = { Text(data.optString("text").take(120), maxLines = 2) }, leadingContent = { Icon(Icons.Outlined.TextFields, null) },
                supportingContent = { Text(if (unanchored) "Sin coincidencia en la página · ${item.pageKey}" else "Texto sustituido · ${item.pageKey}") },
                trailingContent = { TextButton(onClick = { model.delete(item.id) }) { Text("Restaurar") } })
        } }
    }
    targets?.let { list -> AlertDialog(onDismissRequest = { targets = null; model.pendingAction = null; centerCross = false }, title = { Text("Selecciona un elemento visible") }, text = {
        LazyColumn { items((0 until list.length()).toList()) { index -> val item = list.getJSONObject(index)
            TextButton(onClick = { targets = null; web?.command("selectTarget", item) }) { Text(item.getString("label")) }
        } }
    }, confirmButton = { TextButton(onClick = { targets = null; model.pendingAction = null; centerCross = false }) { Text("Cancelar") } }) }
    model.editor?.let { data ->
        var title by remember(data) { mutableStateOf(data.optString("title")) }
        var body by remember(data) { mutableStateOf(TextFieldValue(data.optString("body"))) }
        fun format(transform: (TextEdit) -> TextEdit) { body = transform(body.edit()).value() }
        ModalBottomSheet(onDismissRequest = { model.editor = null; model.editingId = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(20.dp)) {
                Text(if (model.editingId == null) "Nueva nota anclada" else "Nota anclada", style = MaterialTheme.typography.titleLarge)
                Text(data.optString("boundary").ifBlank { "Anotación independiente del HTML original" }, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(title, { if (it.length <= 500) title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                OutlinedTextField(body, { if (it.text.length <= 100_000) body = it }, label = { Text("Nota · Markdown") }, placeholder = { Text("## Idea\nEscribe **énfasis**, listas o enlaces…") }, minLines = 4, maxLines = 10, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { format(MarkdownFormatter::bold) }) { Text("Negrita") }
                    TextButton(onClick = { format(MarkdownFormatter::italic) }) { Text("Cursiva") }
                    TextButton(onClick = { format(MarkdownFormatter::bullets) }) { Text("Lista") }
                    TextButton(onClick = { format { MarkdownFormatter.link(it, "https://") } }) { Text("Enlace") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { model.editor = null; model.editingId = null }) { Text("Cancelar") }
                    Button(onClick = { runCatching { model.savePin(title, body.text) }.onFailure { model.error = it.message } }, enabled = title.isNotBlank() || body.text.isNotBlank()) { Text("Guardar") }
                }
                model.editingId?.let { id ->
                    Row {
                        TextButton(onClick = { model.editor = null; model.reanchoringId = id; model.mode = "EDIT"; chrome = true; hint = "Mantén presionado el nuevo elemento" }) { Text("Mover / reanclar") }
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
    model.textEditor?.let { data ->
        val info = data.getJSONObject("text")
        val overrideId = info.optString("overrideId")
        var value by remember(data) { mutableStateOf(info.optString("text")) }
        ModalBottomSheet(onDismissRequest = { model.textEditor = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(20.dp)) {
                Text("Editar texto", style = MaterialTheme.typography.titleLarge)
                Text("El HTML original no se modifica: el cambio se guarda aparte y puedes restaurarlo cuando quieras.", style = MaterialTheme.typography.bodySmall)
                if (info.optBoolean("formatted")) Text("Este bloque contiene formato interno (negritas, enlaces…). Con el cambio se mostrará como texto simple.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                OutlinedTextField(value, { if (it.length <= 20_000) value = it }, label = { Text("Texto visible") }, minLines = 3, maxLines = 12, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
                if (overrideId.isNotBlank()) Text("Original: " + info.optString("original").take(400), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { model.textEditor = null }) { Text("Cancelar") }
                    if (overrideId.isNotBlank()) TextButton(onClick = { model.deleteRelated(listOf(overrideId), "TEXT", data.getString("pageKey")); model.textEditor = null }) { Text("Restaurar original") }
                    Button(onClick = { runCatching { model.saveText(value) }.onFailure { model.error = it.message } }, enabled = value.isNotBlank()) { Text("Guardar") }
                }
            }
        }
    }
    confirm?.let { (kind, request) ->
        val ids = request.ids(if (kind == "PIN") "pins" else "texts")
        val count = model.count(ids, kind, request.pageKey)
        AlertDialog(onDismissRequest = { confirm = null },
            title = { Text(if (kind == "PIN") "¿Borrar $count ${if (count == 1) "nota adjunta" else "notas adjuntas"}?" else "¿Restaurar el texto original?") },
            text = { Text(if (kind == "PIN") "Se eliminan los pines de este elemento. Puedes recuperarlos con Deshacer." else "Se descartan los cambios de texto de este elemento. Puedes recuperarlos con Deshacer.") },
            confirmButton = { TextButton(onClick = { model.deleteRelated(ids, kind, request.pageKey); confirm = null }) { Text(if (kind == "PIN") "Borrar" else "Restaurar") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } })
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("¿Borrar todos los dibujos de esta página?") },
        confirmButton = { TextButton(onClick = { model.clearDrawing(pageKey()); clear = false }) { Text("Borrar") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancelar") } })
    model.error?.let { error -> AlertDialog(onDismissRequest = { model.error = null }, title = { Text("Aviso del lector") }, text = { Text(error) }, confirmButton = { TextButton(onClick = { if (!model.saved) model.retrySave() else model.error = null }) { Text(if (!model.saved) "Reintentar guardado" else "Entendido") } }) }
}
