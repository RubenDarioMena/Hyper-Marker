package com.hypermarker.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset as IOff
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermarker.storage.FlowNode
import com.hypermarker.ui.theme.FlowInk
import com.hypermarker.ui.theme.Inter
import com.hypermarker.ui.theme.SpaceGrotesk
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val GRID = 28f
private const val HIT_DP = 28f
/** Por debajo de este zoom todos los puntos se dibujan simples y sin títulos (umbral previo de Flow Dots). */
const val DETAIL_SCALE = .45f

private enum class Press { NONE, TAP, DRAG, MULTI, CANCEL }

internal fun FlowNode.label() = title.ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim().take(80) }

/** Geometría compartida entre dibujo, toque y accesibilidad. Radios en px de pantalla. */
internal class FlowLook(val scale: Float, val density: Float, val roots: Set<String>) {
    val detail = scale >= DETAIL_SCALE
    fun hasPhoto(node: FlowNode) = detail && node.avatarPath != null
    fun radius(node: FlowNode): Float {
        val s = sqrt(scale)
        return if (hasPhoto(node)) ((if (node.id in roots) 44f else 32f) * s).coerceIn(28f, 44f) * density
        else (14f * s).coerceIn(7f, 20f) * density
    }
}

/**
 * Lienzo de tinta del Flow (pantalla 9). Conserva los gestos de Flow Dots: tocar vacío crea, tocar un
 * punto lo abre, mantener y soltar pliega, mantener y deslizar conecta, Reordenar arrastra, pellizcar y
 * arrastrar el vacío mueven la cámara.
 */
@Composable
internal fun FlowCanvas(model: FlowViewModel, nodes: List<FlowNode>, size: IntSize, onTap: (FlowNode) -> Unit) {
    val edges by model.edges.collectAsState()
    val linkMap by model.links.collectAsState()
    val density = LocalDensity.current.density
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer(cacheSize = 256)
    val pairs = remember(edges) { edges.map { it.fromId to it.toId } }
    val collapsed = remember(nodes) { nodes.filter { it.collapsed }.map { it.id }.toSet() }
    val hidden = remember(nodes, pairs) { FlowGraph.hidden(nodes.map { it.id }, pairs, collapsed) }
    val counts = remember(pairs, collapsed, hidden) { FlowGraph.hiddenCounts(pairs, collapsed, hidden) }
    val visible = remember(nodes, hidden) { nodes.filter { it.id !in hidden } }
    val parents = remember(pairs) { pairs.map { it.first }.toSet() }
    val roots = remember(pairs, nodes) { val children = pairs.map { it.second }.toSet(); nodes.map { it.id }.filter { it !in children }.toSet() }
    val currentVisible by rememberUpdatedState(visible)
    val currentParents by rememberUpdatedState(parents)
    val currentRoots by rememberUpdatedState(roots)
    val currentTap by rememberUpdatedState(onTap)
    var link by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    var linkTarget by remember { mutableStateOf<String?>(null) }

    // Fotos visibles: carga perezosa sólo cuando el zoom las muestra.
    val camera0 = model.camera
    LaunchedEffect(visible, camera0?.scale?.let { it >= DETAIL_SCALE }) {
        if ((camera0?.scale ?: 1f) >= DETAIL_SCALE) visible.mapNotNull { it.avatarPath }.distinct().take(300).forEach { model.ensureImage(it) }
    }

    fun hit(screen: Offset, except: String? = null): FlowNode? {
        val camera = model.camera ?: return null
        val look = FlowLook(camera.scale, density, currentRoots)
        val unit = camera.unit(density)
        val x = camera.toWorldX(screen.x, density); val y = camera.toWorldY(screen.y, density)
        return FlowGraph.nearestWithin(currentVisible.filter { it.id != except }, x, y, { model.position(it) }) {
            max(HIT_DP * density, look.radius(it) + 8 * density) / unit
        }
    }

    Canvas(Modifier.fillMaxSize()
        .semantics { contentDescription = "Lienzo con ${nodes.size} puntos. Toca un espacio vacío para crear un punto." }
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
                        if (node != null) currentTap(node)
                        else if (model.selected != null) model.select(null)
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
        drawRect(FlowInk.canvas)
        val camera = model.camera ?: return@Canvas
        val look = FlowLook(camera.scale, density, roots)
        fun screen(id: String): Offset? {
            val value = model.node(id) ?: return null
            val (x, y) = model.position(value)
            return Offset(camera.toScreenX(x, density), camera.toScreenY(y, density))
        }
        drawGrid(camera, density)
        val margin = 220.dp.toPx()
        fun inside(p: Offset) = p.x > -margin && p.y > -margin && p.x < this.size.width + margin && p.y < this.size.height + margin
        val selected = model.selected
        val byId = visible.associateBy { it.id }
        for (edge in edges) {
            if (edge.fromId in hidden || edge.toId in hidden) continue
            val from = byId[edge.fromId] ?: continue; val to = byId[edge.toId] ?: continue
            val a = screen(edge.fromId) ?: continue; val b = screen(edge.toId) ?: continue
            if (!inside(a) && !inside(b) && !crosses(a, b, this.size.width, this.size.height)) continue
            val hot = selected != null && (edge.fromId == selected || edge.toId == selected)
            drawEdge(a, b, look.radius(from), look.radius(to), if (hot) FlowInk.pin else FlowInk.mint.copy(alpha = .85f),
                max(1.5f, 2.5.dp.toPx() * sqrt(camera.scale).coerceAtMost(1.2f)), dashed = to.collapsed)
        }
        link?.let { (from, point) ->
            screen(from)?.let { drawLine(FlowInk.pin, it, point, 2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))) }
            linkTarget?.let { target ->
                val ok = model.canConnect(from, target)
                val node = byId[target]
                if (node != null) screen(target)?.let { drawCircle(if (ok) FlowInk.pin else FlowInk.danger, look.radius(node) + 10.dp.toPx(), it, style = Stroke(3.dp.toPx())) }
            }
        }
        val pill = TextStyle(color = FlowInk.canvas, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = Inter)
        val plain = TextStyle(color = FlowInk.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = Inter, shadow = Shadow(FlowInk.canvas, blurRadius = 8f))
        val heading = TextStyle(color = FlowInk.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = SpaceGrotesk, shadow = Shadow(FlowInk.canvas, blurRadius = 10f))
        for (node in visible) {
            val p = screen(node.id) ?: continue
            if (!inside(p)) continue
            val r = look.radius(node)
            val isSelected = node.id == selected || node.id == link?.first
            val photo = look.hasPhoto(node)
            val ring = node.ringColor?.let { Color(it.toInt()) } ?: FlowInk.mint
            if (photo) {
                if (isSelected) drawCircle(FlowInk.pin.copy(alpha = .25f), r + 14.dp.toPx(), p)
                val image = model.images[node.avatarPath]
                if (image != null) {
                    val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(p, r)) }
                    clipPath(circle) {
                        val side = r * 2
                        drawImage(image, srcOffset = IOff.Zero, srcSize = IntSize(image.width, image.height),
                            dstOffset = IntOffset((p.x - r).roundToInt(), (p.y - r).roundToInt()), dstSize = IntSize(side.roundToInt(), side.roundToInt()))
                    }
                } else drawCircle(FlowInk.raised, r, p)
                drawCircle(if (isSelected) FlowInk.pin else ring, r + 4.5.dp.toPx(), p, style = Stroke(3.dp.toPx()))
            } else {
                if (isSelected) drawCircle(FlowInk.mint.copy(alpha = .25f), r + 9.dp.toPx(), p)
                if (node.collapsed) {
                    drawCircle(FlowInk.raised, r, p)
                    drawCircle(FlowInk.mint, r, p, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))))
                } else drawCircle(if (isSelected) FlowInk.pin else node.ringColor?.let { Color(it.toInt()) } ?: FlowInk.mint, r, p)
            }
            if (model.rearrange) drawCircle(FlowInk.text.copy(alpha = .4f), r + 8.dp.toPx(), p, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
            val related = linkMap[node.id].orEmpty().size
            if (look.detail && related >= 2) drawBadge(measurer, related, Offset(p.x + r * .78f, p.y - r * .78f))
            if (!look.detail) continue
            val label = node.label() + (counts[node.id]?.let { " · +$it" } ?: "")
            if (label.isBlank()) continue
            val maxWidth = Constraints(maxWidth = (170.dp.toPx() * camera.scale.coerceIn(.7f, 1.3f)).toInt().coerceAtLeast(1))
            when {
                photo && node.id in roots -> {
                    val text = measurer.measure(label, heading, overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1, constraints = maxWidth)
                    drawText(text, topLeft = Offset(p.x - text.size.width / 2f, p.y - r - 10.dp.toPx() - text.size.height))
                }
                photo -> {
                    val text = measurer.measure(label, pill, overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1, constraints = maxWidth)
                    val w = text.size.width + 20.dp.toPx(); val h = text.size.height + 8.dp.toPx()
                    val top = Offset(p.x - w / 2, p.y + r - 6.dp.toPx())
                    drawRoundRect(Color.Black.copy(alpha = .3f), top + Offset(0f, 2.dp.toPx()), Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                    drawRoundRect(FlowInk.text, top, Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                    drawText(text, topLeft = top + Offset(10.dp.toPx(), 4.dp.toPx()))
                }
                else -> {
                    val text = measurer.measure(label, plain, overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1, constraints = maxWidth)
                    drawText(text, topLeft = Offset(p.x - text.size.width / 2f, p.y + r + 6.dp.toPx()))
                }
            }
        }
    }

    // Accesibilidad: un nodo semántico por punto visible en pantalla, sin interceptar toques.
    val camera = model.camera
    if (camera != null && size != IntSize.Zero) {
        val look = FlowLook(camera.scale, density, roots)
        visible.asSequence().map { it to model.position(it) }
            .map { (node, pos) -> Triple(node, camera.toScreenX(pos.first, density), camera.toScreenY(pos.second, density)) }
            .filter { (_, x, y) -> x >= 0 && y >= 0 && x <= size.width && y <= size.height }
            .take(150).toList().forEach { (node, x, y) ->
                val r = look.radius(node)
                val related = linkMap[node.id].orEmpty().size
                val description = buildString {
                    append(if (node.collapsed) "Punto plegado: " else "Punto: "); append(node.label().ifBlank { "sin título" })
                    if (node.avatarPath != null) append(", con foto")
                    if (related > 0) append(if (related == 1) ", 1 elemento relacionado" else ", $related elementos relacionados")
                    counts[node.id]?.let { append(", oculta $it puntos") }
                }
                key(node.id) {
                    Box(Modifier.offset { IntOffset((x - r).roundToInt(), (y - r).roundToInt()) }.size(with(LocalDensity.current) { (r * 2).toDp() })
                        .clearAndSetSemantics {
                            contentDescription = description
                            role = Role.Button
                            onClick("Abrir") { onTap(node); true }
                            customActions = listOfNotNull(
                                CustomAccessibilityAction("Editar nota") { model.startEdit(node.id); true },
                                CustomAccessibilityAction("Conectar con…") { model.connecting = node.id; true },
                                CustomAccessibilityAction("Relacionar elementos") { model.relating = node.id; true },
                                CustomAccessibilityAction("Foto del punto") { model.avatarFor = node.id; true },
                                if (node.id in parents) CustomAccessibilityAction(if (node.collapsed) "Desplegar" else "Plegar") { model.toggleCollapsed(node.id); true } else null,
                                if (related > 0) CustomAccessibilityAction("Ver los $related elementos en lista") { model.listing = node.id; true } else null,
                            )
                        })
                }
            }
    }
}

private fun DrawScope.drawBadge(measurer: TextMeasurer, count: Int, center: Offset) {
    val text = measurer.measure(if (count > 99) "99+" else count.toString(), TextStyle(color = FlowInk.canvas, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = Inter))
    val h = 26.dp.toPx(); val w = max(h, text.size.width + 12.dp.toPx())
    val ring = 3.dp.toPx()
    drawRoundRect(FlowInk.canvas, Offset(center.x - w / 2 - ring, center.y - h / 2 - ring), Size(w + 2 * ring, h + 2 * ring), androidx.compose.ui.geometry.CornerRadius(h / 2 + ring))
    drawRoundRect(FlowInk.pin, Offset(center.x - w / 2, center.y - h / 2), Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
    drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f))
}

private fun DrawScope.drawGrid(camera: FlowCamera, density: Float) {
    val step = GRID * camera.unit(density)
    if (step < 14f) return
    val startX = camera.offsetX.mod(step); val startY = camera.offsetY.mod(step)
    val points = ArrayList<Offset>()
    var x = startX
    while (x < size.width) { var y = startY; while (y < size.height) { points += Offset(x, y); y += step }; x += step }
    if (points.size <= 8000) drawPoints(points, PointMode.Points, FlowInk.grid, strokeWidth = 2.4.dp.toPx() * camera.scale.coerceIn(.6f, 1.2f), cap = StrokeCap.Round)
}

/** Curva padre → hijo que sale del borde del padre y termina, con una flecha discreta, en el borde del hijo. */
private fun DrawScope.drawEdge(a: Offset, b: Offset, ra: Float, rb: Float, color: Color, width: Float, dashed: Boolean) {
    val d = b - a
    val horizontal = abs(d.x) > abs(d.y) * 1.6f
    val c1 = if (horizontal) Offset(a.x + d.x / 2, a.y) else Offset(a.x, a.y + d.y / 2)
    val c2 = if (horizontal) Offset(b.x - d.x / 2, b.y) else Offset(b.x, b.y - d.y / 2)
    val toward = (b - c2).let { if (it.getDistance() < 1f) d else it }
    val dir = toward / max(toward.getDistance(), 1f)
    val gap = 6.dp.toPx()
    val end = b - dir * (rb + gap)
    val start = a + ((c1 - a).let { if (it.getDistance() < 1f) d else it }.let { it / max(it.getDistance(), 1f) }) * (ra + gap)
    drawPath(Path().apply { moveTo(start.x, start.y); cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y) }, color,
        style = Stroke(width, cap = StrokeCap.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 7.dp.toPx())) else null))
    val head = 7.dp.toPx(); val normal = Offset(-dir.y, dir.x)
    val base = end - dir * head
    drawPath(Path().apply {
        moveTo(end.x, end.y); lineTo(base.x + normal.x * head * .5f, base.y + normal.y * head * .5f)
        lineTo(base.x - normal.x * head * .5f, base.y - normal.y * head * .5f); close()
    }, color)
}

/** Aproximación barata: el segmento cruza la vista aunque ambos extremos queden fuera. */
private fun crosses(a: Offset, b: Offset, width: Float, height: Float) =
    !(max(a.x, b.x) < 0f || minOf(a.x, b.x) > width || max(a.y, b.y) < 0f || minOf(a.y, b.y) > height)
