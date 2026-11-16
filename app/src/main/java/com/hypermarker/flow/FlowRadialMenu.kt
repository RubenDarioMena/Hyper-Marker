package com.hypermarker.flow

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermarker.storage.FlowNode
import com.hypermarker.ui.*
import com.hypermarker.ui.theme.FlowInk
import com.hypermarker.ui.theme.LightHm
import com.hypermarker.ui.theme.LightSheetTheme
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Menú radial de un punto con 2 o más relaciones (pantalla 11). El punto crece al centro y lo rodean
 * hasta 6 mini-vistas; con más, un pétalo «+N» y el giro del anillo pasan de página. Tocar el centro,
 * el velo o Atrás cierra. «Ver los N» abre la lista completa (alternativa accesible al anillo).
 */
@Composable
internal fun FlowRadialMenu(model: FlowViewModel, node: FlowNode, start: Offset, onOpen: (LinkView) -> Unit, onClose: () -> Unit, onDelete: () -> Unit) {
    val links = model.linksOf(node.id)
    val progress = rememberEntrance(node.id)
    val t = progress.value
    val density = LocalDensity.current
    val pages = RadialLayout.pages(links.size)
    val petals = RadialLayout.petals(links.size, model.radialPage)
    val focus = model.radialFocus?.takeIf { it in links.indices } ?: petals.firstOrNull()?.item ?: 0
    val focused = links.getOrNull(focus)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat(); val height = constraints.maxHeight.toFloat()
        val center = Offset(width / 2, height * .42f)
        val ring = with(density) { minOf(128.dp.toPx(), width * .34f) }
        val cx = start.x + (center.x - start.x) * t; val cy = start.y + (center.y - start.y) * t
        // Velo y giro del anillo.
        Box(Modifier.fillMaxSize().background(FlowInk.canvas.copy(alpha = .9f * t.coerceIn(0f, 1f)))
            .clickable(remember { MutableInteractionSource() }, null, onClickLabel = "Cerrar", onClick = onClose)
            .pointerInput(pages) {
                if (pages <= 1) return@pointerInput
                var last = 0.0; var accumulated = 0.0
                detectDragGestures(onDragStart = { p -> last = Math.toDegrees(atan2((p.y - center.y).toDouble(), (p.x - center.x).toDouble())); accumulated = 0.0 }) { change, _ ->
                    val now = Math.toDegrees(atan2((change.position.y - center.y).toDouble(), (change.position.x - center.x).toDouble()))
                    var delta = now - last; if (delta > 180) delta -= 360; if (delta < -180) delta += 360
                    last = now; accumulated += delta
                    val shift = RadialLayout.pageShift(accumulated)
                    if (shift != 0) { model.radialPage += shift; model.radialFocus = null; accumulated -= shift * 50.0 }
                    change.consume()
                }
            })
        // Anillo guía.
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawCircle(FlowInk.outline.copy(alpha = t.coerceIn(0f, 1f)), ring * t, Offset(cx, cy), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
        }
        Text(if (pages > 1) "Gira el anillo para ver más · toca el centro para cerrar" else "Toca el centro para cerrar",
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 76.dp, start = 24.dp, end = 24.dp).graphicsLayer { alpha = t.coerceIn(0f, 1f) },
            color = FlowInk.soft, fontSize = 13.sp, textAlign = TextAlign.Center)
        petals.forEachIndexed { index, petal ->
            val appear by animateFloatAsState(if (t > .2f) 1f else 0f, spring(dampingRatio = .7f, stiffness = 380f - index * 30f), label = "petal")
            val size = 76.dp
            val half = with(density) { (size / 2).toPx() }
            val px = cx + petal.x(ring) * appear; val py = cy + petal.y(ring) * appear
            val item = petal.item
            Box(Modifier.offset { IntOffset((px - half).roundToInt(), (py - half).roundToInt()) }.size(size).graphicsLayer { scaleX = appear; scaleY = appear; alpha = appear }) {
                if (item == null) MorePetal(petal.more) { model.radialPage += 1; model.radialFocus = null }
                else {
                    val link = links[item]
                    Petal(model, link, focused = item == focus) { model.radialFocus = item }
                }
            }
        }
        // Centro: el punto agrandado; tocarlo cierra.
        val centerSize = 112.dp
        val centerHalf = with(density) { ((centerSize + 12.dp) / 2).toPx() }
        Column(Modifier.offset { IntOffset((cx - centerHalf).roundToInt(), (cy - centerHalf).roundToInt()) }.width(centerSize + 12.dp)
            .clickable(remember { MutableInteractionSource() }, null, onClickLabel = "Cerrar menú", role = Role.Button, onClick = onClose)
            .semantics(mergeDescendants = true) { contentDescription = "${node.label()}, ${links.size} elementos relacionados. Cerrar menú" },
            horizontalAlignment = Alignment.CenterHorizontally) {
            NodeAvatar(node, node.avatarPath?.let { model.images[it] }, centerSize)
            Text("${node.label().ifBlank { "Punto" }} · ${links.size}", Modifier.offset(y = (-14).dp).clip(RoundedCornerShape(50)).background(FlowInk.pin)
                .padding(horizontal = 10.dp, vertical = 4.dp), color = FlowInk.canvas, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Panel inferior con el elemento en foco.
        LightSheetTheme {
            val hm = LightHm
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).widthIn(max = 560.dp).fillMaxWidth()
                .graphicsLayer { alpha = t.coerceIn(0f, 1f); translationY = (1f - t) * 60.dp.toPx() }
                .clip(RoundedCornerShape(28.dp)).background(hm.paper).clickable(remember { MutableInteractionSource() }, null) {}.padding(16.dp)) {
                if (focused != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    IconTile(kindIcon(focused.type), size = 52.dp)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(focused.title, style = MaterialTheme.typography.titleMedium, color = hm.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${kindShort(focused.type)} · ${focus + 1} de ${links.size}", color = hm.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(if (focused?.type == "FOLDER") "Abrir carpeta" else if (focused?.type == "IMAGE") "Ver imagen" else "Abrir", { focused?.let(onOpen) }, Modifier.weight(1f), enabled = focused != null)
                    SecondaryButton("Ver los ${links.size}", { model.listing = node.id }, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    val cell = Modifier.weight(1f)
                    StackedAction(Icons.Outlined.EditNote, "Nota", { model.startEdit(node.id) }, cell)
                    StackedAction(Icons.Outlined.AddBox, "Relacionar", { model.relating = node.id }, cell)
                    StackedAction(Icons.Outlined.AccountCircle, "Foto", { model.avatarFor = node.id }, cell)
                    if (focused != null) StackedAction(Icons.Outlined.LinkOff, "Quitar", { model.unlink(focused); model.radialFocus = null }, cell)
                    StackedAction(Icons.Outlined.Delete, "Eliminar", onDelete, cell, color = hm.danger)
                }
            }
        }
    }
}

@Composable
private fun Petal(model: FlowViewModel, link: LinkView, focused: Boolean, onFocus: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    LightSheetTheme {
        Column(Modifier.fillMaxSize().clip(shape).background(LightHm.paper).border(if (focused) 3.dp else 0.dp, if (focused) FlowInk.mint else LightHm.paper, shape)
            .clickable(role = Role.Button, onClickLabel = "Enfocar", onClick = onFocus)
            .semantics(mergeDescendants = true) { contentDescription = "${link.title}, ${kindShort(link.type)}"; selected = focused }) {
            Thumbnail(link.id, link.type, Modifier.fillMaxWidth().weight(1f).padding(6.dp).clip(RoundedCornerShape(10.dp)), loader(model, link))
            Text(link.title, Modifier.padding(horizontal = 6.dp).padding(bottom = 5.dp), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = LightHm.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MorePetal(more: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxSize().clip(shape).background(FlowInk.raised).border(1.5.dp, FlowInk.mint, shape)
        .clickable(role = Role.Button, onClickLabel = "Siguiente página", onClick = onClick).semantics(mergeDescendants = true) { contentDescription = "$more más. Siguiente página" },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("+$more", color = FlowInk.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("más", color = FlowInk.soft, fontSize = 12.sp)
    }
}
