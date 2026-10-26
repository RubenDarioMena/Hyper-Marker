package com.hypermarker.flow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hypermarker.storage.Document
import com.hypermarker.storage.FlowNode
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.viewer.GlassButton
import com.hypermarker.viewer.GlassTone
import com.hypermarker.viewer.glass
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private const val GRID = 40f
private const val HIT_DP = 28f

private enum class Press { NONE, TAP, DRAG, MULTI, CANCEL }

private fun FlowNode.label() = title.ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim().take(80) }

@Composable
fun FlowScreen(document: Document, repository: LibraryRepository, onClose: () -> Unit) {
    val model: FlowViewModel = viewModel(key = "flow-${document.id}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = FlowViewModel(document.id, repository) as T
    })
    val loaded by model.nodes.collectAsState()
    val nodes = loaded.orEmpty()
    val edges by model.edges.collectAsState()
    val density = LocalDensity.current.density
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val tone = if (isSystemInDarkTheme()) GlassTone.DARK else GlassTone.LIGHT
    val measurer = rememberTextMeasurer(cacheSize = 256)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val pairs = remember(edges) { edges.map { it.fromId to it.toId } }
    val collapsed = remember(nodes) { nodes.filter { it.collapsed }.map { it.id }.toSet() }
    val hidden = remember(nodes, pairs) { FlowGraph.hidden(nodes.map { it.id }, pairs, collapsed) }
    val counts = remember(pairs, collapsed, hidden) { FlowGraph.hiddenCounts(pairs, collapsed, hidden) }
    val visible = remember(nodes, hidden) { nodes.filter { it.id !in hidden } }
    val currentVisible by rememberUpdatedState(visible)
    val parents = remember(pairs) { pairs.map { it.first }.toSet() }
    // El detector de gestos vive mientras dure la pantalla: lee el estado actual, no el de la primera composición.
    val currentParents by rememberUpdatedState(parents)
    // Conexión elástica en curso: punto de origen y posición del dedo en pantalla.
    var link by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    var linkTarget by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var managing by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(size, loaded != null) {
        if (model.camera == null && loaded != null && size != IntSize.Zero)
            model.camera = FlowCamera.fit(nodes.map { it.x to it.y }, size.width.toFloat(), size.height.toFloat(), density, 96 * density)
    }
    LaunchedEffect(model.message) { if (model.message != null) { delay(2800); model.message = null } }
    LaunchedEffect(model.selected, nodes) { if (model.selected != null && model.node(model.selected) == null) model.selected = null }
    BackHandler(model.rearrange || model.selected != null) { if (model.rearrange) model.rearrange = false else model.selected = null }
    BackHandler(!model.rearrange && model.selected == null) { onClose() }

    fun hit(screen: Offset, except: String? = null): FlowNode? {
        val camera = model.camera ?: return null
        val x = camera.toWorldX(screen.x, density); val y = camera.toWorldY(screen.y, density)
        return FlowGraph.nearest(currentVisible.filter { it.id != except }, x, y, HIT_DP / camera.scale) { model.position(it) }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        Canvas(Modifier.fillMaxSize()
            .semantics { contentDescription = "Canvas con ${nodes.size} puntos. Toca un espacio vacío para crear un punto." }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                val timeout = max(500L, viewConfiguration.longPressTimeoutMillis)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    val node = hit(start)
                    val press = withTimeoutOrNull(timeout) {
                        var result = Press.NONE
                        while (result == Press.NONE) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            result = when {
                                event.changes.count { it.pressed } > 1 -> Press.MULTI
                                change == null -> Press.CANCEL
                                !change.pressed -> Press.TAP
                                (change.position - start).getDistance() > slop -> Press.DRAG
                                else -> Press.NONE
                            }
                        }
                        result
                    }

                    suspend fun AwaitPointerEventScope.transform() {
                        do {
                            val event = awaitPointerEvent()
                            val camera = model.camera ?: break
                            val centroid = event.calculateCentroid(useCurrent = false)
                            if (centroid.isSpecified) {
                                val pan = event.calculatePan()
                                model.camera = camera.zoom(event.calculateZoom(), centroid.x, centroid.y).pan(pan.x, pan.y)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                    suspend fun AwaitPointerEventScope.move(target: FlowNode) {
                        val (x0, y0) = model.position(target)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val unit = model.camera?.unit(density) ?: break
                            model.drag(target.id, x0 + (change.position.x - start.x) / unit, y0 + (change.position.y - start.y) / unit)
                            change.consume()
                        } while (change.pressed)
                        model.drop(target.id)
                    }

                    when (press) {
                        Press.TAP -> if (!model.rearrange) {
                            val camera = model.camera
                            if (node != null) model.selected = node.id
                            else if (model.selected != null) model.selected = null
                            else if (camera != null) model.startCreate(camera.toWorldX(start.x, density), camera.toWorldY(start.y, density))
                        }
                        Press.DRAG, Press.MULTI -> if (press == Press.DRAG && model.rearrange && node != null) move(node) else transform()
                        Press.CANCEL, Press.NONE -> Unit
                        null -> when {
                            node == null -> transform()
                            model.rearrange -> { haptics.performHapticFeedback(HapticFeedbackType.LongPress); move(node) }
                            else -> {
                                // Mantener: soltar sin moverse pliega; deslizar extiende una conexión.
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                var dragging = false; var cancelled = false
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || event.changes.count { it.pressed } > 1) { cancelled = true; break }
                                    if (!dragging && (change.position - start).getDistance() > slop) dragging = true
                                    if (dragging) {
                                        link = node.id to change.position
                                        val over = hit(change.position, except = node.id)?.id
                                        if (over != linkTarget && over != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        linkTarget = over
                                    }
                                    change.consume()
                                } while (change.pressed)
                                val target = linkTarget
                                link = null; linkTarget = null
                                when {
                                    cancelled -> Unit
                                    !dragging -> if (node.id in currentParents) model.toggleCollapsed(node.id) else model.message = "Este punto no tiene puntos conectados que plegar"
                                    target == null -> Unit
                                    model.canConnect(node.id, target) -> model.connect(node.id, target)
                                    else -> model.message = model.reasonNotConnectable(node.id, target)
                                }
                            }
                        }
                    }
                }
            }) {
            val camera = model.camera ?: return@Canvas
            fun screen(id: String): Offset? {
                val value = model.node(id) ?: return null
                val (x, y) = model.position(value)
                return Offset(camera.toScreenX(x, density), camera.toScreenY(y, density))
            }
            drawGrid(camera, density, colors.outlineVariant.copy(alpha = .55f))
            val radius = (9.dp.toPx() * sqrt(camera.scale)).coerceIn(5.dp.toPx(), 16.dp.toPx())
            val margin = 200.dp.toPx()
            val labels = camera.scale >= .45f
            // Las flechas que llegan desde arriba se detienen antes del título del hijo.
            val clearance = if (labels) 6.dp.toPx() + 13.sp.toPx() * 1.4f else 0f
            fun inside(p: Offset) = p.x > -margin && p.y > -margin && p.x < size.width + margin && p.y < size.height + margin
            for (edge in edges) {
                if (edge.fromId in hidden || edge.toId in hidden) continue
                val a = screen(edge.fromId) ?: continue; val b = screen(edge.toId) ?: continue
                if (!inside(a) && !inside(b) && !crosses(a, b, size.width.toFloat(), size.height.toFloat())) continue
                drawEdge(a, b, radius, clearance, colors.onSurfaceVariant.copy(alpha = .7f), max(1.5f, 1.5.dp.toPx() * sqrt(camera.scale)))
            }
            link?.let { (from, point) ->
                screen(from)?.let { drawLine(colors.primary, it, point, 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))) }
                linkTarget?.let { target ->
                    val ok = model.canConnect(from, target)
                    screen(target)?.let { drawCircle(if (ok) colors.primary else colors.error, radius + 8.dp.toPx(), it, style = Stroke(3.dp.toPx())) }
                }
            }
            val titleStyle = TextStyle(color = colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium, shadow = Shadow(colors.background, blurRadius = 8f))
            for (node in visible) {
                val p = screen(node.id) ?: continue
                if (!inside(p)) continue
                val selected = node.id == model.selected || node.id == link?.first
                if (selected) drawCircle(colors.primary.copy(alpha = .22f), radius + 9.dp.toPx(), p)
                drawCircle(if (node.collapsed) colors.secondary else colors.primary, radius, p)
                if (node.collapsed) drawCircle(colors.secondary, radius + 4.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                if (model.rearrange) drawCircle(colors.onSurface.copy(alpha = .35f), radius + 4.dp.toPx(), p, style = Stroke(1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                counts[node.id]?.let { count ->
                    val badge = measurer.measure("+$count", titleStyle.copy(fontSize = 12.sp, color = colors.secondary))
                    drawText(badge, topLeft = Offset(p.x + radius + 8.dp.toPx(), p.y - badge.size.height / 2f))
                }
                if (labels) {
                    val label = node.label()
                    if (label.isNotEmpty()) {
                        val text = measurer.measure(label, titleStyle, overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
                            constraints = Constraints(maxWidth = (180.dp.toPx() * camera.scale.coerceAtMost(1.4f)).toInt().coerceAtLeast(1)))
                        drawText(text, topLeft = Offset(p.x - text.size.width / 2f, p.y - radius - 6.dp.toPx() - text.size.height))
                    }
                }
            }
        }

        // Cromo: atrás, título y herramientas.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(Icons.AutoMirrored.Outlined.ArrowBack, "Volver a la biblioteca", tone, onClick = onClose)
            Text(document.title, Modifier.weight(1f).padding(horizontal = 12.dp), color = tone.content, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold, style = TextStyle(shadow = tone.halo))
            GlassButton(Icons.Outlined.CenterFocusStrong, "Encuadrar todos los puntos", tone, onClick = {
                model.camera = FlowCamera.fit(visible.map { model.position(it) }, size.width.toFloat(), size.height.toFloat(), density, 96 * density)
            })
            Spacer(Modifier.width(8.dp))
            GlassButton(Icons.Outlined.OpenWith, if (model.rearrange) "Terminar de reordenar" else "Reordenar puntos", tone,
                label = if (model.rearrange) "Listo" else "Reordenar", selected = model.rearrange, onClick = { model.rearrange = !model.rearrange; model.selected = null })
        }

        if (loaded != null && nodes.isEmpty()) Text("Toca en cualquier lugar para crear tu primer punto", Modifier.align(Alignment.Center).padding(32.dp),
            color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            model.message?.let { Text(it, Modifier.glass(RoundedCornerShape(50), tone, solid = true).padding(horizontal = 18.dp, vertical = 10.dp), color = tone.content) }
            if (model.rearrange) Text("Arrastra los puntos para reacomodarlos", Modifier.padding(top = 8.dp).glass(RoundedCornerShape(50), tone, solid = true)
                .padding(horizontal = 18.dp, vertical = 10.dp), color = tone.content)
            model.node(model.selected)?.takeIf { !model.rearrange }?.let { node ->
                NodeCard(node, tone, collapsible = node.id in parents, edges = edges.count { it.fromId == node.id || it.toId == node.id },
                    onOpen = { model.startEdit(node.id) }, onCollapse = { model.toggleCollapsed(node.id) }, onConnect = { model.connecting = node.id },
                    onConnections = { managing = node.id }, onDelete = { deleting = node.id })
            }
        }
    }

    model.draft?.let { draft ->
        var title by remember(draft) { mutableStateOf(draft.title) }
        var body by remember(draft) { mutableStateOf(draft.body) }
        AlertDialog(onDismissRequest = { model.draft = null }, title = { Text(if (draft.node == null) "Nuevo punto" else "Editar punto") },
            text = { Column {
                OutlinedTextField(title, { title = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Título") }, singleLine = true)
                OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Nota (Markdown)") }, minLines = 4, maxLines = 10)
            } },
            confirmButton = { TextButton(enabled = title.isNotBlank() || body.isNotBlank(), onClick = { model.saveDraft(title, body) }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { model.draft = null }) { Text("Cancelar") } })
    }
    model.connecting?.let { from ->
        val targets = model.targets(from)
        AlertDialog(onDismissRequest = { model.connecting = null }, title = { Text("Conectar con…") },
            text = { if (targets.isEmpty()) Text("No hay puntos disponibles: ya están conectados o formarían un ciclo.")
                else LazyColumn { items(targets, key = { it.id }) { node -> TextButton(onClick = { model.connect(from, node.id) }) { Text(node.label().ifEmpty { "Punto sin título" }) } } } },
            confirmButton = { TextButton(onClick = { model.connecting = null }) { Text("Cancelar") } })
    }
    managing?.let { id ->
        val related = edges.filter { it.fromId == id || it.toId == id }
        AlertDialog(onDismissRequest = { managing = null }, title = { Text("Conexiones") },
            text = { if (related.isEmpty()) Text("Este punto no tiene conexiones.") else LazyColumn { items(related, key = { it.id }) { edge ->
                val child = edge.fromId == id
                val other = model.node(if (child) edge.toId else edge.fromId)?.label()?.ifEmpty { null } ?: "Punto sin título"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (child) "Hijo: " else "Padre: ") + other, Modifier.weight(1f))
                    TextButton(onClick = { model.disconnect(edge) }) { Text("Quitar") }
                }
            } } },
            confirmButton = { TextButton(onClick = { managing = null }) { Text("Cerrar") } })
    }
    deleting?.let { id ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("¿Eliminar este punto?") },
            text = { Text("Se eliminarán el punto y sus conexiones. Los puntos conectados se conservan.") },
            confirmButton = { TextButton(onClick = { deleting = null; model.delete(id) }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } })
    }
}

/** Tarjeta flotante provisional; F2 la sustituye por la nota rica que se expande a página completa. */
@Composable
private fun NodeCard(node: FlowNode, tone: GlassTone, collapsible: Boolean, edges: Int, onOpen: () -> Unit, onCollapse: () -> Unit,
                     onConnect: () -> Unit, onConnections: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.padding(top = 8.dp).widthIn(max = 520.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp), tone, solid = true)) {
        Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Editar nota", onClick = onOpen).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp)) {
            Text(node.title.ifBlank { "Sin título" }, color = tone.content, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (node.body.isNotBlank()) Text(node.body, Modifier.padding(top = 6.dp), color = tone.muted, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = onOpen) { Icon(Icons.Outlined.Edit, "Editar", tint = tone.content) }
            IconButton(onClick = onCollapse, enabled = collapsible) {
                Icon(if (node.collapsed) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess, if (node.collapsed) "Desplegar" else "Plegar",
                    tint = tone.content.copy(alpha = if (collapsible) 1f else .35f))
            }
            IconButton(onClick = onConnect) { Icon(Icons.Outlined.AddLink, "Conectar con…", tint = tone.content) }
            IconButton(onClick = onConnections, enabled = edges > 0) { Icon(Icons.Outlined.LinkOff, "Quitar conexiones", tint = tone.content.copy(alpha = if (edges > 0) 1f else .35f)) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Eliminar punto", tint = tone.content) }
        }
    }
}

private fun DrawScope.drawGrid(camera: FlowCamera, density: Float, color: Color) {
    val step = GRID * camera.unit(density)
    if (step < 18f) return
    val startX = camera.offsetX.mod(step); val startY = camera.offsetY.mod(step)
    val points = ArrayList<Offset>()
    var x = startX
    while (x < size.width) { var y = startY; while (y < size.height) { points += Offset(x, y); y += step }; x += step }
    if (points.size <= 6000) drawPoints(points, PointMode.Points, color, strokeWidth = 2.dp.toPx() * camera.scale.coerceIn(.6f, 1.2f))
}

/** Curva padre → hijo con flecha que termina en el borde del punto hijo. */
private fun DrawScope.drawEdge(a: Offset, b: Offset, radius: Float, clearance: Float, color: Color, width: Float) {
    val d = b - a
    val horizontal = abs(d.x) >= abs(d.y)
    val c1 = if (horizontal) Offset(a.x + d.x / 2, a.y) else Offset(a.x, a.y + d.y / 2)
    val c2 = if (horizontal) Offset(b.x - d.x / 2, b.y) else Offset(b.x, b.y - d.y / 2)
    val toward = (b - c2).let { if (it.getDistance() < 1f) d else it }
    val dir = toward / max(toward.getDistance(), 1f)
    val end = b - dir * (radius + 3.dp.toPx() + if (dir.y > .5f) clearance else 0f)
    val start = a + ((c1 - a).let { if (it.getDistance() < 1f) d else it }.let { it / max(it.getDistance(), 1f) }) * radius
    drawPath(Path().apply { moveTo(start.x, start.y); cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y) }, color, style = Stroke(width))
    val head = 9.dp.toPx(); val normal = Offset(-dir.y, dir.x)
    val base = end - dir * head
    drawPath(Path().apply {
        moveTo(end.x, end.y); lineTo(base.x + normal.x * head * .55f, base.y + normal.y * head * .55f)
        lineTo(base.x - normal.x * head * .55f, base.y - normal.y * head * .55f); close()
    }, color)
}

/** Aproximación barata: el segmento cruza la vista aunque ambos extremos queden fuera. */
private fun crosses(a: Offset, b: Offset, width: Float, height: Float) =
    !(max(a.x, b.x) < 0f || minOf(a.x, b.x) > width || max(a.y, b.y) < 0f || minOf(a.y, b.y) > height)
