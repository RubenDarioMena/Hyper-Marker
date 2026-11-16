package com.hypermarker.markdown

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.*
import com.hypermarker.storage.Document
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.viewer.DocumentWebView
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.UUID

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownScreen(document: Document, repository: LibraryRepository, onClose: () -> Unit) {
    val model: MarkdownViewModel = viewModel(key = "markdown-${document.id}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = MarkdownViewModel(document.id, repository) as T
    })
    val context = LocalContext.current
    val style = remember { SheetStyleStore(context) }
    var dark by remember { mutableStateOf(style.dark) }
    var font by remember { mutableStateOf(style.font) }
    val colors = if (dark) SheetColors.Dark else SheetColors.Light
    val scope = rememberCoroutineScope()
    var warning by remember { mutableStateOf(false) }
    var linkFor by remember { mutableStateOf<TextEdit?>(null) }
    var visualLink by remember { mutableStateOf(false) }
    var visualWeb by remember { mutableStateOf<WebView?>(null) }
    var visualGeneration by remember { mutableIntStateOf(0) }
    var webReady by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    val field = rememberTextFieldState()
    val sourceScroll = rememberScrollState()
    var visualScrollFraction by remember { mutableFloatStateOf(0f) }
    var sourceScrollTarget by remember { mutableStateOf<Float?>(null) }
    var sourcePositionFromVisual by remember { mutableStateOf<Float?>(null) }
    var toolsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableIntStateOf(0) }
    var toolbarMenuOpen by remember { mutableStateOf(false) }
    var fingerDown by remember { mutableStateOf(false) }
    var headerHeight by remember { mutableStateOf(0.dp) }
    var visualProtection by remember { mutableStateOf<MarkdownProtection.Protected?>(null) }
    var protectedHint by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    LaunchedEffect(protectedHint) { if (protectedHint != null) { delay(4_500); protectedHint = null } }
    val touchExploration = remember(context) {
        (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).isTouchExplorationEnabled
    }
    fun touched() { toolsVisible = true; lastInteraction++ }
    LaunchedEffect(lastInteraction, touchExploration, toolbarMenuOpen, fingerDown) {
        if (!touchExploration && !toolbarMenuOpen && !fingerDown) { delay(5_000); toolsVisible = false }
    }
    LaunchedEffect(model.visual, sourceScrollTarget) {
        val target = sourceScrollTarget
        if (!model.visual && target != null) {
            if (target > 0f && model.source.isNotEmpty()) withTimeoutOrNull(2_000) {
                snapshotFlow { sourceScroll.maxValue }.first { it > 0 }
            }
            withFrameNanos { }
            if (sourceScroll.maxValue > 0) sourceScroll.scrollTo((sourceScroll.maxValue * target).toInt().coerceIn(0, sourceScroll.maxValue))
            sourceScrollTarget = null
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Undo/redo, carga y sincronización desde Visual cambian la fuente fuera del campo.
    LaunchedEffect(model.source) { if (field.text.toString() != model.source) field.edit {
        val caret = selection.end.coerceAtMost(model.source.length)
        replace(0, length, model.source)
        selection = TextRange(caret)
    } }
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect { value ->
        if (value != model.source) {
            touched()
            if (!model.visual) sourcePositionFromVisual = null
            model.edit(value)
            if (model.source != value) field.edit { replace(0, length, model.source) }
        }
    } }
    fun fieldValue() = TextFieldValue(field.text.toString(), field.selection)
    fun updateField(value: TextFieldValue) {
        if (!model.visual) sourcePositionFromVisual = null
        field.edit { replace(0, length, value.text); selection = value.selection }
        model.edit(value.text)
    }
    fun flushVisual(then: () -> Unit) {
        val editor = visualWeb
        if (model.visual && webReady && editor != null) editor.evaluateJavascript("window.editorSnapshot?.()") { value ->
            runCatching {
                val snapshot = JSONObject(org.json.JSONTokener(value).nextValue() as String)
                val source = MarkdownProtection.restore(snapshot.getString("source"), visualProtection)
                val fraction = snapshot.optDouble("fraction", 0.0).toFloat().coerceIn(0f, 1f)
                field.edit {
                    replace(0, length, source)
                    selection = TextRange((source.length * fraction).toInt().coerceIn(0, source.length))
                }
                model.edit(source)
                sourceScrollTarget = fraction
                sourcePositionFromVisual = fraction
            }
            then()
        } else then()
    }
    fun close() { flushVisual { scope.launch { model.save(); if (model.saved) onClose() } } }
    fun applyStyle() { visualWeb?.evaluateJavascript("window.setEditorStyle?.(${JSONObject().put("dark", dark).put("font", font)})", null) }
    fun visualCommand(name: String, value: Any? = null) {
        val argument = when (value) { null -> "null"; is String -> JSONObject.quote(value); else -> value.toString() }
        visualWeb?.evaluateJavascript("window.editorCommand?.(${JSONObject.quote(name)},$argument)", null)
    }
    fun command(command: FormatCommand) {
        touched()
        if (!model.visual) {
            if (command == FormatCommand.Link) { linkFor = fieldValue().edit(); return }
            updateField(command.applyTo(fieldValue().edit()).value()); return
        }
        when (command) {
            FormatCommand.Link -> visualLink = true
            is FormatCommand.Heading -> visualCommand("heading", command.level)
            is FormatCommand.Inline -> visualCommand(command.name)
            is FormatCommand.Block -> visualCommand(command.name)
        }
    }
    fun openVisual() {
        if (model.visual) return
        val protection = MarkdownProtection.protect(model.source)
        if (protection == null) warning = true
        else if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) model.error = "Actualiza Android System WebView para utilizar el editor visual."
        else {
            visualScrollFraction = sourcePositionFromVisual ?: if (sourceScroll.maxValue > 0) sourceScroll.value.toFloat() / sourceScroll.maxValue
                else if (model.source.isNotEmpty()) field.selection.end.toFloat() / model.source.length else 0f
            sourcePositionFromVisual = null
            if (protection.count > 0) protectedHint = "${protection.count} fragmento(s) con imágenes, HTML o tablas especiales se muestran como fuente protegida; el resto es editable en Visual."
            model.visual = true
        }
    }
    BackHandler { close() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) model.saveSoon() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer); model.saveSoon() }
    }
    LaunchedEffect(dark, font, webReady) { if (webReady) applyStyle() }
    LaunchedEffect(headerHeight, webReady) { if (webReady) visualWeb?.evaluateJavascript(topInsetScript(headerHeight.value), null) }
    val activity = LocalActivity.current
    DisposableEffect(dark) {
        val bars = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        val status = bars?.isAppearanceLightStatusBars; val navigation = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = !dark; bars?.isAppearanceLightNavigationBars = !dark
        onDispose { status?.let { bars?.isAppearanceLightStatusBars = it }; navigation?.let { bars?.isAppearanceLightNavigationBars = it } }
    }
    val selection = TextSelectionColors(handleColor = colors.content, backgroundColor = colors.content.copy(alpha = .22f))
    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding()) {
        val icons = IconButtonDefaults.iconButtonColors(contentColor = colors.content, disabledContentColor = colors.muted.copy(alpha = .5f))
        // La cabecera flota sobre la hoja: mostrarla u ocultarla nunca desplaza el texto.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val sheetHeight = maxHeight
        val tools: @Composable () -> Unit = { Column(Modifier.fillMaxWidth().background(colors.background)
            .onSizeChanged { size -> with(density) { size.height.toDp() }.let { if (it != headerHeight) headerHeight = it } }) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { touched(); close() }, colors = icons) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Guardar y volver") }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(document.title, color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (model.saved) Icon(Icons.Outlined.Check, null, Modifier.size(14.dp), tint = colors.accent)
                    Text(if (model.saved) "Guardado" else "Guardando…", Modifier.padding(start = 3.dp), color = colors.muted, fontSize = 12.sp)
                }
            }
            IconButton(enabled = model.canUndo, colors = icons, onClick = { touched(); model.undo(); if (model.visual) visualGeneration++ }) { Icon(Icons.AutoMirrored.Outlined.Undo, "Deshacer") }
            IconButton(enabled = model.canRedo, colors = icons, onClick = { touched(); model.redo(); if (model.visual) visualGeneration++ }) { Icon(Icons.AutoMirrored.Outlined.Redo, "Rehacer") }
            IconButton(colors = icons, onClick = { touched(); dark = !dark; style.dark = dark }) { Icon(if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, if (dark) "Hoja clara" else "Hoja oscura") }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).clip(RoundedCornerShape(50)).background(colors.selected).padding(4.dp)) {
            listOf(true to "Visual", false to "Fuente").forEach { (visual, label) ->
                val active = model.visual == visual
                Text(label, color = if (active) colors.background else colors.content, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (active) colors.content else colors.selected)
                        .clickable(role = Role.Tab) { touched(); if (visual) openVisual() else flushVisual { model.visual = false } }
                        .semantics { this.selected = active }
                        .padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }
        } }
        if (!model.visual) CompositionLocalProvider(LocalTextSelectionColors provides selection) {
          // El hueco superior forma parte del contenido desplazable: al ocultar las herramientas el texto queda quieto.
          Column(Modifier.fillMaxSize().verticalScroll(sourceScroll)) {
           Spacer(Modifier.height(headerHeight))
           Box(Modifier.fillMaxWidth().heightIn(min = (sheetHeight - headerHeight).coerceAtLeast(0.dp))) {
            if (field.text.isEmpty()) Text("# Una idea nueva\n\nEmpieza a escribir…", color = colors.muted, fontFamily = sheetFont(font), fontSize = 17.sp, lineHeight = 27.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp))
            BasicTextField(state = field, enabled = model.loaded,
                textStyle = TextStyle(color = colors.content, fontFamily = sheetFont(font), fontSize = 17.sp, lineHeight = 27.sp),
                cursorBrush = SolidColor(colors.content),
                modifier = Modifier.fillMaxWidth().heightIn(min = (sheetHeight - headerHeight).coerceAtLeast(0.dp)).pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed && !it.previousPressed }) { fingerDown = true; sourcePositionFromVisual = null; touched() }
                            if (event.changes.none { it.pressed }) fingerDown = false
                        }
                    }
                }.padding(horizontal = 24.dp, vertical = 20.dp))
           }
          }
        } else key(visualGeneration) {
            val session = remember { UUID.randomUUID().toString() }
            // Imágenes, HTML y otras construcciones no compatibles se acotan como fuente protegida.
            val protection = remember { MarkdownProtection.protect(model.source) }
            SideEffect { visualProtection = protection }
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                webReady = false; initialized = false
                WebView(context).apply {
                    visualWeb = this
                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> { fingerDown = true; touched() }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> fingerDown = false
                        }
                        false
                    }
                    setBackgroundColor(colors.background.toArgb())
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
                                "change" -> if (initialized) model.edit(MarkdownProtection.restore(data.getJSONObject("payload").getString("source"), protection))
                                "loaded" -> {
                                    initialized = true; webReady = true
                                    evaluateJavascript(topInsetScript(headerHeight.value), null)
                                    evaluateJavascript("window.editorSetScrollFraction?.($visualScrollFraction)", null)
                                }
                                "error" -> { model.error = data.getJSONObject("payload").optString("message"); model.visual = false }
                            }
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse =
                            if (request.url.host == "editor.hypermarker.invalid") assets.shouldInterceptRequest(request.url) ?: DocumentWebView.denied() else DocumentWebView.denied()
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest) = true
                        override fun onPageFinished(view: WebView?, url: String?) {
                            applyStyle()
                            evaluateJavascript("window.startEditor(${JSONObject.quote(protection?.text ?: model.source)},${JSONObject.quote(session)});", null)
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
            }, update = { it.setBackgroundColor(colors.background.toArgb()) }, onRelease = { it.stopLoading(); it.destroy(); if (visualWeb === it) visualWeb = null })
        }
        if (toolsVisible) tools()
        else IconButton(onClick = ::touched, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(50)).background(colors.background.copy(alpha = .85f)), colors = icons) {
            Icon(Icons.Outlined.ExpandMore, "Mostrar herramientas")
        }
        protectedHint?.let { message ->
            Text(message, color = colors.background, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                .clip(RoundedCornerShape(12.dp)).background(colors.content.copy(alpha = .9f)).padding(horizontal = 14.dp, vertical = 10.dp))
        }
        }
        // Barra de formato abajo, sobre el teclado (pantalla 7). Ocupa su propio espacio para que el cursor nunca quede
        // tapado; al ocultarla solo crece el borde inferior de la hoja, el texto no se desplaza.
        if (toolsVisible) {
            HorizontalDivider(color = colors.divider)
            MarkdownToolbar(colors, font, model.loaded && (!model.visual || webReady), onFont = { font = it; style.font = it }, onCommand = ::command,
                onMenuExpandedChange = { toolbarMenuOpen = it })
        }
    }
    linkFor?.let { edit -> LinkDialog(edit.selected.isNotEmpty(), onDismiss = { linkFor = null }) { url ->
        updateField(MarkdownFormatter.link(edit, url).value()); linkFor = null
    } }
    if (visualLink) LinkDialog(true, onDismiss = { visualLink = false }) { url -> visualCommand("link", url); visualLink = false }
    if (warning) AlertDialog(onDismissRequest = { warning = false }, title = { Text("Conserva esta nota en Fuente") },
        text = { Text("La nota contiene sintaxis que no podemos acotar de forma segura en Visual (por ejemplo un bloque de código sin cerrar o los caracteres ⟦ ⟧). El texto fuente se conserva completo; puedes seguir editándolo aquí.") },
        confirmButton = { TextButton(onClick = { warning = false }) { Text("Continuar en Fuente") } })
    model.error?.let { error -> AlertDialog(onDismissRequest = { model.error = null }, title = { Text("Aviso de Markdown") }, text = { Text(error) }, confirmButton = { TextButton(onClick = { model.error = null }) { Text("Entendido") } }) }
}

/** Reserva arriba del editor visual el alto de las herramientas flotantes (CSS px ≈ dp en el WebView). */
private fun topInsetScript(dp: Float) = "document.body&&(document.body.style.paddingTop='${dp.coerceIn(0f, 600f)}px');"

@Composable
private fun LinkDialog(hasSelection: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("https://") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Insertar enlace") }, text = {
        Column {
            Text(if (hasSelection) "Se enlazará el texto seleccionado." else "Se insertará un texto de enlace que puedes reemplazar.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(url, { url = it.take(2000) }, label = { Text("Dirección") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(url.trim()) }, enabled = url.trim().length > 8 || url.trim().startsWith("#")) { Text("Insertar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
