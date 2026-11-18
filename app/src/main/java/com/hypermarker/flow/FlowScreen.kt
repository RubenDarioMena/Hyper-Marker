package com.hypermarker.flow

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hypermarker.picker.*
import com.hypermarker.preview.PreviewStore
import com.hypermarker.storage.Document
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.ui.HmConfirm
import com.hypermarker.ui.theme.FlowInk
import com.hypermarker.ui.theme.LightSheetTheme
import kotlinx.coroutines.delay

/**
 * Flow atrevido: lienzo de tinta siempre oscuro (decisión 4) con puntos que pueden tener foto y
 * elementos relacionados. Tocar un punto abre su nota (0), su vista previa (1) o el menú radial (2+).
 */
@Composable
fun FlowScreen(document: Document, repository: LibraryRepository, previews: PreviewStore, onClose: () -> Unit,
               onOpen: (String) -> Unit = {}, onOpenFolder: (String) -> Unit = {}) {
    val model: FlowViewModel = viewModel(key = "flow-${document.id}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = FlowViewModel(document.id, repository, previews) as T
    })
    val loaded by model.nodes.collectAsState()
    val nodes = loaded.orEmpty()
    val edges by model.edges.collectAsState()
    val linkMap by model.links.collectAsState()
    val folders by model.folders.collectAsState()
    val documents by model.documents.collectAsState()
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var managing by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf(true) }
    var diagramming by remember { mutableStateOf(false) }
    val parents = remember(edges) { edges.map { it.fromId }.toSet() }
    val relateImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val node = model.relating
        if (uri != null && node != null) model.relateImage(node, uri)
    }

    LaunchedEffect(size, loaded != null) {
        if (model.camera == null && loaded != null && size != IntSize.Zero)
            model.camera = FlowCamera.fit(nodes.map { it.x to it.y }, size.width.toFloat(), size.height.toFloat(), density, 110 * density)
    }
    LaunchedEffect(model.message) { if (model.message != null) { delay(2800); model.message = null } }
    LaunchedEffect(model.selected, nodes) { if (model.selected != null && model.node(model.selected) == null) model.select(null) }
    LaunchedEffect(Unit) { delay(7000); hint = false }

    fun screenOf(id: String?): Offset {
        val node = model.node(id); val camera = model.camera
        if (node == null || camera == null) return Offset(size.width / 2f, size.height / 2f)
        val (x, y) = model.position(node)
        return Offset(camera.toScreenX(x, density), camera.toScreenY(y, density))
    }
    fun openLink(link: LinkView) {
        when {
            link.type == "IMAGE" -> model.viewingImage = link
            link.link.targetFolderId != null -> onOpenFolder(link.link.targetFolderId)
            link.link.targetDocumentId != null -> onOpen(link.link.targetDocumentId)
        }
    }

    val overlay = model.viewingImage != null || model.selected != null
    BackHandler(model.viewingImage != null || model.rearrange || model.selected != null) {
        when {
            model.viewingImage != null -> model.viewingImage = null
            model.rearrange -> model.rearrange = false
            else -> model.select(null)
        }
    }
    BackHandler(!overlay && !model.rearrange) { onClose() }

    Box(Modifier.fillMaxSize().background(FlowInk.canvas).onSizeChanged { size = it }) {
        FlowCanvas(model, nodes, size) { node -> hint = false; model.select(node.id) }

        // Cromo: atrás, título, encuadrar y reordenar.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            InkButton(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", onClick = onClose)
            Text(document.title, Modifier.weight(1f).padding(horizontal = 12.dp).semantics { heading() }, color = FlowInk.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
            if (nodes.isNotEmpty()) {
                InkButton(Icons.Outlined.AccountTree, "Crear diagrama Mermaid de este Flow", onClick = { diagramming = true })
                Spacer(Modifier.width(8.dp))
            }
            InkButton(Icons.Outlined.CenterFocusStrong, "Encuadrar todos los puntos", onClick = {
                model.camera = FlowCamera.fit(nodes.map { model.position(it) }, size.width.toFloat(), size.height.toFloat(), density, 110 * density)
            })
            Spacer(Modifier.width(8.dp))
            InkButton(Icons.Outlined.OpenWith, if (model.rearrange) "Terminar de reordenar" else "Reordenar puntos", label = if (model.rearrange) "Listo" else "Reordenar",
                selected = model.rearrange, onClick = { model.rearrange = !model.rearrange; model.select(null) })
        }

        if (loaded != null && nodes.isEmpty()) Text("Toca en cualquier lugar para crear tu primer punto", Modifier.align(Alignment.Center).padding(32.dp),
            color = FlowInk.soft, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            val text = model.message ?: when {
                model.rearrange -> "Arrastra los puntos para reacomodarlos"
                hint && nodes.isNotEmpty() && model.selected == null -> "Toca un punto para abrir lo que guarda. Mantén y desliza para conectar."
                else -> null
            }
            if (text != null && model.selected == null) Text(text, Modifier.clip(RoundedCornerShape(24.dp)).background(FlowInk.raised).border(1.dp, FlowInk.outline, RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp), color = FlowInk.soft, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        model.node(model.selected)?.takeIf { !model.rearrange }?.let { node ->
            val start = remember(node.id) { screenOf(node.id) }
            if (linkMap[node.id].orEmpty().size >= 2) FlowRadialMenu(model, node, start, ::openLink, onClose = { model.select(null) }, onDelete = { deleting = node.id })
            else FlowDotCard(model, node, start, edges, collapsible = node.id in parents, onOpen = ::openLink, onClose = { model.select(null) },
                onDelete = { deleting = node.id }, onConnections = { managing = node.id })
            model.message?.let { message ->
                Text(message, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp).clip(RoundedCornerShape(50)).background(FlowInk.raised)
                    .padding(horizontal = 18.dp, vertical = 10.dp), color = FlowInk.soft, fontSize = 13.sp)
            }
        }
        model.viewingImage?.let { link -> ImageViewer(model, link) { model.viewingImage = null } }
    }

    model.draft?.let { draft -> NoteEditorSheet(draft, onSave = model::saveDraft, onDismiss = { model.draft = null }) }
    model.connecting?.let { from -> ConnectSheet(model.targets(from), onPick = { model.connect(from, it.id) }, onDismiss = { model.connecting = null }) }
    managing?.let { id -> ConnectionsSheet(model, id, edges) { managing = null } }
    model.node(model.avatarFor)?.let { node -> FlowAvatarSheet(model, node) { model.avatarFor = null } }
    model.node(model.relating)?.let { node ->
        val linked = linkMap[node.id].orEmpty().mapNotNull { it.targetId }.toSet()
        val state = remember(node.id, folders, documents) {
            ItemPickerState(PickerMode.RELATE, folders.map { PickFolder(it.id, it.parentId, it.name) }, documents.map { PickDocument(it.id, it.folderId, it.title, it.type) },
                alreadyLinked = linked, excluded = setOf(document.id))
        }
        LightSheetTheme {
            ItemPickerSheet(state, PickerSubject("Relacionar con", node.label().ifBlank { "Punto" }, icon = Icons.Outlined.AddLink, image = node.avatarPath?.let { path -> { model.previews.flowImage(document.id, path, 256) } }),
                thumbnail = { id -> documents.firstOrNull { it.id == id }?.let { doc -> { model.previews.document(doc) } } },
                onDismiss = { model.relating = null }, onRelate = { docs, dirs -> model.relate(node.id, docs, dirs) },
                onImage = { relateImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
        }
    }
    model.node(model.listing)?.let { node ->
        val links = linkMap[node.id].orEmpty()
        val images = links.filter { it.type == "IMAGE" }.map { PickDocument(it.id, null, it.title, "IMAGE") }
        val state = remember(node.id, links, folders, documents) {
            ItemPickerState(PickerMode.LIST, folders.map { PickFolder(it.id, it.parentId, it.name) },
                documents.map { PickDocument(it.id, it.folderId, it.title, it.type) } + images, listed = links.map { it.targetId ?: it.id })
        }
        LightSheetTheme {
            ItemPickerSheet(state, PickerSubject("Relacionados con", node.label().ifBlank { "Punto" }, icon = Icons.Outlined.Hub, image = node.avatarPath?.let { path -> { model.previews.flowImage(document.id, path, 256) } }),
                thumbnail = { id ->
                    val link = links.firstOrNull { it.targetId == id || it.id == id }
                    link?.let { loader(model, it) }
                },
                onDismiss = { model.listing = null },
                onOpen = { id -> model.listing = null; links.firstOrNull { it.targetId == id || it.id == id }?.let(::openLink) })
        }
    }
    deleting?.let { id ->
        LightSheetTheme {
            HmConfirm("¿Eliminar este punto?", "Se eliminarán el punto, su foto y sus conexiones y relaciones. Los puntos conectados y los documentos relacionados se conservan.",
                "Eliminar", { deleting = null; model.delete(id) }, { deleting = null }, danger = true)
        }
    }
    if (diagramming) LightSheetTheme {
        HmConfirm("Crear diagrama Mermaid", "Se crea un documento Mermaid nuevo con una copia de este Flow: títulos, hasta dos líneas de cada nota, conexiones, " +
            "grupos y etiquetas de flecha que vinieron de Mermaid. Mermaid recalcula la disposición. Desde él puedes exportar SVG o PNG; el Flow no cambia.",
            "Crear y abrir", { diagramming = false; model.toDiagram(onOpen) }, { diagramming = false })
    }
}

/** Botón del cromo del lienzo: círculo de tinta elevado con contorno. */
@Composable
private fun InkButton(icon: ImageVector, description: String, label: String? = null, selected: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.height(48.dp).let { if (label == null) it.width(48.dp) else it }.clip(CircleShape)
        .background(if (selected) FlowInk.mint else FlowInk.raised).border(1.dp, FlowInk.outline, CircleShape)
        .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }
        .padding(horizontal = if (label == null) 0.dp else 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (selected) FlowInk.canvas else FlowInk.text)
        if (label != null) Text(label, Modifier.padding(start = 8.dp), color = if (selected) FlowInk.canvas else FlowInk.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
