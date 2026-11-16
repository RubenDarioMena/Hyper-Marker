package com.hypermarker.flow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermarker.storage.FlowEdge
import com.hypermarker.storage.FlowNode
import com.hypermarker.ui.*
import com.hypermarker.ui.theme.FlowInk
import com.hypermarker.ui.theme.LightHm
import com.hypermarker.ui.theme.LightSheetTheme
import com.hypermarker.ui.theme.LocalHm
import kotlin.math.roundToInt

/** Velo, progreso de entrada y punto de partida de la transición (posición del punto en pantalla). */
@Composable
internal fun rememberEntrance(key: Any?): Animatable<Float, *> {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow)) }
    return progress
}

/** Círculo con la foto del punto (o el punto de color si no tiene). */
@Composable
internal fun NodeAvatar(node: FlowNode, image: ImageBitmap?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val ring = node.ringColor?.let { Color(it.toInt()) } ?: FlowInk.mint
    Box(modifier.size(size + 12.dp).clip(CircleShape).background(FlowInk.canvas).padding(3.dp).border(3.dp, ring, CircleShape).padding(4.5.dp).clip(CircleShape)
        .background(if (image == null) FlowInk.raised else Color.Transparent), contentAlignment = Alignment.Center) {
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.size(size * .32f).clip(CircleShape).background(ring))
    }
}

private fun linkKind(link: LinkView) = kindLabel(link.type)

/**
 * Tarjeta de un punto (pantallas 10 y nota sin relaciones). Con una relación muestra su vista previa
 * como imagen: no ejecuta la página; el contenido real sólo se abre con «Abrir».
 */
@Composable
internal fun FlowDotCard(model: FlowViewModel, node: FlowNode, start: Offset, edges: List<FlowEdge>, collapsible: Boolean,
                         onOpen: (LinkView) -> Unit, onClose: () -> Unit, onDelete: () -> Unit, onConnections: () -> Unit) {
    val links = model.linksOf(node.id)
    val link = links.singleOrNull()
    val progress = rememberEntrance(node.id)
    val t = progress.value
    val density = LocalDensity.current
    LightSheetTheme {
        val hm = LightHm
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Velo del 55 % sobre el lienzo; tocarlo cierra.
            Box(Modifier.fillMaxSize().background(FlowInk.canvas.copy(alpha = .55f * t.coerceIn(0f, 1f)))
                .clickable(remember { MutableInteractionSource() }, null, onClickLabel = "Cerrar", onClick = onClose))
            val cardHeight = maxHeight * .72f
            val avatarSize = 72.dp
            var cardOrigin by remember { mutableStateOf<Offset?>(null) }
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp).widthIn(max = 560.dp).fillMaxWidth()
                .let { if (link != null) it.height(cardHeight) else it }
                .onGloballyPositioned { cardOrigin = it.positionInParent() }
                .graphicsLayer { alpha = t.coerceIn(0f, 1f); translationY = (1f - t) * 80.dp.toPx() }
                .clip(RoundedCornerShape(28.dp)).background(hm.paper).border(1.5.dp, FlowInk.mint, RoundedCornerShape(28.dp))
                .clickable(remember { MutableInteractionSource() }, null) {}) {
                Row(Modifier.fillMaxWidth().padding(start = if (node.avatarPath != null) (avatarSize + 40.dp) else 20.dp, end = 14.dp, top = 16.dp),
                    verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).semantics { heading() }) {
                        Text(node.label().ifBlank { "Punto sin título" }, style = MaterialTheme.typography.titleLarge, color = hm.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(when (links.size) { 0 -> "Nota del punto"; 1 -> "${linkKind(links[0])} · 1 elemento"; else -> "${links.size} elementos" },
                            color = hm.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    RoundIconButton(Icons.Outlined.Close, "Cerrar", onClose)
                }
                Column(Modifier.weight(1f, fill = link != null).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    if (link != null) {
                        LinkPreview(model, link, Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 180.dp).weight(1f, fill = false))
                        if (node.body.isNotBlank()) Text("Nota del punto: " + node.body, Modifier.padding(top = 12.dp), color = hm.muted, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    } else {
                        if (node.body.isNotBlank()) Text(node.body, Modifier.padding(top = 10.dp), color = hm.ink, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
                        else Text("Este punto aún no tiene nota. Relaciona documentos, carpetas o imágenes para guardarlos aquí.", Modifier.padding(top = 10.dp), color = hm.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
                    if (link != null) PrimaryButton(when (link.type) { "FOLDER" -> "Abrir carpeta"; "IMAGE" -> "Ver imagen"; else -> "Abrir documento" }, { onOpen(link) }, Modifier.fillMaxWidth())
                    else PrimaryButton("Editar nota", { model.startEdit(node.id) }, Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
                    val cell = Modifier.weight(1f)
                    if (link != null) StackedAction(Icons.Outlined.EditNote, "Nota", { model.startEdit(node.id) }, cell)
                    StackedAction(Icons.Outlined.Link, "Conectar", { model.connecting = node.id }, cell)
                    StackedAction(Icons.Outlined.AddBox, "Relacionar", { model.relating = node.id }, cell)
                    StackedAction(Icons.Outlined.AccountCircle, "Foto", { model.avatarFor = node.id }, cell)
                    if (link == null) StackedAction(if (node.collapsed) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess, if (node.collapsed) "Desplegar" else "Plegar",
                        { model.toggleCollapsed(node.id) }, cell, enabled = collapsible)
                    StackedAction(Icons.Outlined.Delete, "Eliminar", onDelete, cell, color = hm.danger)
                }
                val connections = edges.count { it.fromId == node.id || it.toId == node.id }
                if (connections > 0 || link != null) Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.Center) {
                    if (connections > 0) TextButton(onClick = onConnections) { Text(if (connections == 1) "1 conexión" else "$connections conexiones", color = hm.muted, fontSize = 13.sp) }
                    if (link != null) TextButton(onClick = { model.unlink(link) }) {
                        Text("Quitar relación", color = hm.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // La foto viaja desde el punto hasta la esquina de la tarjeta.
            val origin = cardOrigin
            if (node.avatarPath != null && origin != null) {
                val destX = origin.x + with(density) { (12.dp + (avatarSize + 12.dp) / 2).toPx() }
                val destY = origin.y + with(density) { 30.dp.toPx() }
                val half = with(density) { ((avatarSize + 12.dp) / 2).toPx() }
                val cx = start.x + (destX - start.x) * t; val cy = start.y + (destY - start.y) * t
                NodeAvatar(node, model.images[node.avatarPath], avatarSize, Modifier.offset { IntOffset((cx - half).roundToInt(), (cy - half).roundToInt()) }
                    .graphicsLayer { val s = .7f + .3f * t; scaleX = s; scaleY = s })
            }
        }
    }
}

/** Vista previa de una relación: miniatura de `PreviewStore`, nunca un WebView vivo. */
@Composable
internal fun LinkPreview(model: FlowViewModel, link: LinkView, modifier: Modifier = Modifier) {
    val hm = LocalHm.current
    Box(modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, hm.border, RoundedCornerShape(18.dp))) {
        Thumbnail(link.id + (link.link.updatedAt), link.type, Modifier.fillMaxSize(), loader(model, link))
        Text(when (link.type) { "FOLDER" -> "Carpeta"; "IMAGE" -> "Imagen privada"; else -> "Vista previa" }, Modifier.align(Alignment.BottomStart).padding(10.dp)
            .clip(RoundedCornerShape(50)).background(hm.ink).padding(horizontal = 10.dp, vertical = 4.dp), color = hm.onInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** Cargador de miniatura para una relación. */
internal fun loader(model: FlowViewModel, link: LinkView): (suspend () -> ImageBitmap?)? = when {
    link.link.imagePath != null -> ({ model.previews.flowImage(model.documentId, link.link.imagePath, 480) })
    link.link.targetDocumentId != null -> ({ model.repository.dao.document(link.link.targetDocumentId)?.let { model.previews.document(it) } })
    else -> null
}

/** Editor de la nota del punto, en hoja clara. */
@Composable
internal fun NoteEditorSheet(draft: FlowDraft, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    LightSheetTheme {
        val hm = LocalHm.current
        var title by remember(draft) { mutableStateOf(draft.title) }
        var body by remember(draft) { mutableStateOf(draft.body) }
        HmSheet(onDismiss = onDismiss, title = if (draft.node == null) "Nuevo punto" else "Nota del punto", footer = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancelar", color = hm.ink) }
                Spacer(Modifier.weight(1f))
                PrimaryButton("Guardar", { onSave(title, body) }, enabled = title.isNotBlank() || body.isNotBlank())
            }
        }) {
            Column(Modifier.padding(horizontal = 20.dp).imePadding()) {
                OutlinedTextField(title, { title = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Título") }, singleLine = true)
                OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), label = { Text("Nota (Markdown)") }, minLines = 4, maxLines = 10)
            }
        }
    }
}

/** Lista de puntos con los que se puede conectar. */
@Composable
internal fun ConnectSheet(targets: List<FlowNode>, onPick: (FlowNode) -> Unit, onDismiss: () -> Unit) {
    LightSheetTheme {
        val hm = LocalHm.current
        HmSheet(onDismiss = onDismiss, title = "Conectar con…") {
            if (targets.isEmpty()) Text("No hay puntos disponibles: ya están conectados o formarían un ciclo.", Modifier.padding(20.dp), color = hm.muted)
            else LazyColumn(Modifier.padding(horizontal = 20.dp).heightIn(max = 520.dp)) {
                items(targets, key = { it.id }) { node ->
                    HmRow(node.label().ifEmpty { "Punto sin título" }, "Será hijo de este punto", leading = { IconTile(Icons.Outlined.Circle, size = 36.dp) }, onClick = { onPick(node) })
                    HmDivider()
                }
            }
        }
    }
}

/** Conexiones de un punto, para quitarlas. */
@Composable
internal fun ConnectionsSheet(model: FlowViewModel, id: String, edges: List<FlowEdge>, onDismiss: () -> Unit) {
    LightSheetTheme {
        val hm = LocalHm.current
        val related = edges.filter { it.fromId == id || it.toId == id }
        HmSheet(onDismiss = onDismiss, title = "Conexiones") {
            if (related.isEmpty()) Text("Este punto no tiene conexiones.", Modifier.padding(20.dp), color = hm.muted)
            else LazyColumn(Modifier.padding(horizontal = 20.dp).heightIn(max = 520.dp)) {
                items(related, key = { it.id }) { edge ->
                    val child = edge.fromId == id
                    val other = model.node(if (child) edge.toId else edge.fromId)?.label()?.ifEmpty { null } ?: "Punto sin título"
                    HmRow(other, if (child) "Hijo" else "Padre", trailing = { TextButton(onClick = { model.disconnect(edge) }) { Text("Quitar", color = hm.danger) } })
                    HmDivider()
                }
            }
        }
    }
}

/** Visor a pantalla completa de una imagen relacionada (copia privada). */
@Composable
internal fun ImageViewer(model: FlowViewModel, link: LinkView, onDismiss: () -> Unit) {
    val image by produceState<ImageBitmap?>(null, link.id) { value = link.link.imagePath?.let { model.previews.flowImage(model.documentId, it, 1600) } }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(remember { MutableInteractionSource() }, null) {}) {
        image?.let { Image(it, link.title, Modifier.fillMaxSize().padding(vertical = 72.dp), contentScale = ContentScale.Fit) }
            ?: CircularProgressIndicator(Modifier.align(Alignment.Center), color = FlowInk.mint)
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Outlined.Close, "Cerrar imagen", onDismiss, background = FlowInk.raised, tint = FlowInk.text)
            Text(link.title, Modifier.weight(1f).padding(horizontal = 12.dp), color = FlowInk.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { model.unlink(link); onDismiss() }) { Text("Quitar relación", color = FlowInk.danger) }
        }
    }
}
