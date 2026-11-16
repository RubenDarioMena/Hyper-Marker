package com.hypermarker.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hypermarker.browser.WebAddress
import com.hypermarker.picker.*
import com.hypermarker.storage.Document
import com.hypermarker.ui.*
import com.hypermarker.ui.theme.LocalHm

private data class Item(val id: String, val folder: Boolean, val title: String, val type: String)

@Composable
fun LibraryScreen(model: LibraryViewModel) {
    val hm = LocalHm.current
    val folders by model.folders.collectAsState()
    val documents by model.documents.collectAsState()
    val remoteFolders by model.remoteFolders.collectAsState()
    val remoteEntries by model.remoteEntries.collectAsState()
    val remoteByLibraryId = remoteFolders.associateBy { it.folderId }
    val remoteDocumentIds = remoteEntries.mapNotNull { it.documentId }.toSet()
    val retiredDocumentIds = remoteEntries.groupBy { it.documentId }.filter { (id, entries) -> id != null && entries.all { it.state == "RETIRED" } }.keys
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(KindFilter.ALL) }
    var creating by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Item?>(null) }
    var operation by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) model.receive(it) }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { model.receive(listOf(it), true) } }
    val current = folders.firstOrNull { it.id == model.folderId }
    val byId = documents.associateBy { it.id }
    val entries = run {
        val folderRows = if (filter != KindFilter.ALL) emptyList() else folders.filter { if (search.isBlank()) it.parentId == model.folderId else it.name.contains(search.trim(), true) }
            .map { Item(it.id, true, it.name, "FOLDER") }
        folderRows + documents.filter { (if (search.isBlank()) it.folderId == model.folderId else it.title.contains(search.trim(), true)) && filter.accepts(it.type) }
            .map { Item(it.id, false, it.title, it.type) }
    }
    val continueDoc = documents.filter { it.lastOpenedAt != null }.maxByOrNull { it.lastOpenedAt!! }
    fun openItem(item: Item) { if (item.folder) { model.enterFolder(item.id); search = "" } else model.open(item.id) }
    fun subtitle(item: Item): String {
        val remote = if (item.folder) remoteByLibraryId[item.id] else null
        return when {
            remote != null -> "GitHub · ${remote.branch} · ${com.hypermarker.github.remoteStatusLabel(remote.status)}"
            item.folder -> folders.count { it.parentId == item.id }.plus(documents.count { it.folderId == item.id }).let { if (it == 0) "Carpeta vacía" else if (it == 1) "Carpeta · 1 elemento" else "Carpeta · $it elementos" }
            item.id in retiredDocumentIds -> "Retirado de origen · copia offline"
            item.type == "REMOTE_MARKDOWN" -> "Markdown · GitHub · solo lectura"
            else -> kindLabel(item.type)
        }
    }

    BackHandler(model.folderId != null || model.returnStack.isNotEmpty() || search.isNotEmpty()) {
        when {
            search.isNotEmpty() -> search = ""
            model.returnStack.isNotEmpty() -> model.closeDocument()
            else -> model.folderId = current?.parentId
        }
    }

    Box(Modifier.fillMaxSize().background(hm.paper)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            // Cabecera: título o carpeta actual, estado de GitHub y ajustes.
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (current != null) IconButton(onClick = { model.folderId = current.parentId; search = "" }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Subir a la carpeta anterior", tint = hm.ink) }
                else Spacer(Modifier.width(12.dp))
                Text(current?.name ?: "Hyper-Marker", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (remoteFolders.isNotEmpty()) {
                    val failing = remoteFolders.any { it.status in listOf("AUTH", "ERROR", "FAILED") }
                    IconButton(onClick = { usage = true }) {
                        Icon(if (failing) Icons.Outlined.CloudOff else Icons.Outlined.CloudDone, if (failing) "GitHub: revisar sincronización" else "GitHub al día · consumo", tint = if (failing) hm.danger else hm.accent)
                    }
                }
                IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.Tune, "Ajustes", tint = hm.ink) }
            }
            // Un solo campo: busca en la biblioteca o abre una web.
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), singleLine = true, shape = RoundedCornerShape(50),
                placeholder = { Text("Buscar aquí o abrir una web", color = hm.muted) }, leadingIcon = { Icon(Icons.Outlined.Search, null, tint = hm.muted) },
                trailingIcon = {
                    Box(Modifier.padding(end = 6.dp).size(40.dp).clip(RoundedCornerShape(50)).background(hm.mint)
                        .clickable(enabled = !model.busy, role = Role.Button) {
                            runCatching { search.takeIf { it.isNotBlank() }?.let(WebAddress::resolve) }.onSuccess { model.browse(it) }.onFailure { model.error = it.message }
                        }.semantics { contentDescription = if (search.isBlank()) "Abrir el navegador" else "Abrir «$search» en la web" }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Language, null, tint = hm.accent)
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (entries.isEmpty() && search.isNotBlank()) runCatching { WebAddress.resolve(search) }.onSuccess { model.browse(it) } }),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = hm.surface, focusedContainerColor = hm.surface, unfocusedBorderColor = hm.border))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindFilter.entries.forEach { kind -> HmChip(kind.label, filter == kind, { filter = kind }) }
            }
            if (entries.isEmpty() && continueDoc == null) {
                Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    IconTile(Icons.Outlined.AutoStories, size = 72.dp)
                    Text(if (search.isBlank()) "Tu próxima idea empieza aquí" else "No hay coincidencias", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                    Text(if (search.isBlank()) "Importa una experiencia HTML o escribe una nota." else "Pulsa el globo para buscar «$search» en la web.", color = hm.muted, style = MaterialTheme.typography.bodyMedium)
                    if (search.isBlank() && filter == KindFilter.ALL && current == null)
                        TextButton(onClick = { model.action { model.open(model.repository.installSample().id) } }, enabled = !model.busy) { Text("Explorar un ejemplo interactivo", color = hm.accent) }
                }
            } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 112.dp)) {
                if (continueDoc != null && search.isBlank() && current == null && filter == KindFilter.ALL) {
                    item { SectionLabel("Continuar", Modifier.padding(bottom = 8.dp)) }
                    item { ContinueCard(continueDoc) { model.open(continueDoc.id) } }
                }
                item { SectionLabel(when { search.isNotBlank() -> "Resultados en toda la biblioteca"; current != null -> "En «${current.name}»"; else -> "Biblioteca" }, Modifier.padding(top = 20.dp, bottom = 4.dp)) }
                if (entries.isEmpty()) item { Text("Nada por aquí con este filtro.", Modifier.padding(vertical = 16.dp), color = hm.muted) }
                items(entries, key = { it.id }) { item ->
                    val remote = item.folder && remoteByLibraryId[item.id] != null
                    HmRow(item.title, subtitle(item),
                        leading = { IconTile(if (remote) Icons.Outlined.Cloud else kindIcon(item.type), background = if (remote) hm.mint else hm.divider) },
                        trailing = { IconButton(onClick = { selected = item; operation = "menu" }) { Icon(Icons.Outlined.MoreVert, "Acciones de ${item.title}", tint = hm.muted) } },
                        onLongClick = { selected = item; operation = "menu" }, onClick = { openItem(item) })
                    HmDivider()
                }
            }
        }
        // Botón flotante «Nuevo».
        Row(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp).heightIn(min = 56.dp).clip(RoundedCornerShape(50)).background(hm.ink)
            .clickable(enabled = !model.busy, role = Role.Button) { creating = true }.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Add, null, tint = hm.onInk)
            Text("Nuevo", Modifier.padding(start = 10.dp), color = hm.onInk, style = MaterialTheme.typography.labelLarge)
        }
    }

    if (creating) NewItemSheet(current?.name, onDismiss = { creating = false }, onCreate = { kind -> creating = false; operation = kind; title = "" },
        onImportFiles = { creating = false; filePicker.launch(arrayOf("*/*")) }, onImportTree = { creating = false; treePicker.launch(null) },
        onWeb = { creating = false; model.browse() }, onGitHub = { creating = false; model.githubScreen = true })
    if (settings) HmSheet(onDismiss = { settings = false }, title = "Ajustes") {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SectionLabel("GitHub", Modifier.padding(top = 8.dp))
            HmRow("Sincronización y consumo", "Límites, pausa y exportación anonimizada", leading = { IconTile(Icons.Outlined.CloudSync) }, onClick = { settings = false; usage = true })
            HmDivider()
            HmRow("Añadir carpeta de GitHub", "Solo lectura, se actualiza sola", leading = { IconTile(Icons.Outlined.Cloud) }, onClick = { settings = false; model.githubScreen = true })
            SectionLabel("Acerca de", Modifier.padding(top = 16.dp))
            HmRow("Licencias de código abierto", "Componentes y tipografías incluidos", leading = { IconTile(Icons.Outlined.Gavel, background = hm.divider) }, onClick = { settings = false; licenses = true })
            HmDivider()
            HmRow("Ejemplo interactivo", "Instala un documento para probar pines y dibujos", leading = { IconTile(Icons.Outlined.AutoStories, background = hm.divider) },
                onClick = { settings = false; model.action { model.open(model.repository.installSample().id) } })
        }
    }
    if (licenses) LicenseDialog { licenses = false }
    if (usage) com.hypermarker.github.GitHubUsageDialog(model) { usage = false }
    if (model.busy) AlertDialog(onDismissRequest = {}, title = { Text("Preparando tu biblioteca") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth(), color = hm.accent) },
        confirmButton = { TextButton(onClick = model::cancel) { Text("Cancelar", color = hm.ink) } })
    model.error?.let { error -> AlertDialog(onDismissRequest = { model.error = null }, title = { Text("No se pudo completar") }, text = { Text(error) }, confirmButton = { TextButton(onClick = { model.error = null }) { Text("Entendido", color = hm.accent) } }) }
    model.githubMessage?.let { message -> AlertDialog(onDismissRequest = { model.githubMessage = null }, title = { Text("GitHub") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { model.githubMessage = null }) { Text("Entendido", color = hm.accent) } }) }
    model.pending?.let { pending ->
        HmSheet(onDismiss = model::cancelImport, title = "Elige la página inicial") {
            LazyColumn(Modifier.padding(horizontal = 20.dp).heightIn(max = 520.dp)) {
                items(pending.entries) { path -> HmRow(path.substringAfterLast('/'), path, leading = { IconTile(Icons.Outlined.Code, background = hm.divider) }, onClick = { model.chooseEntry(path) }); HmDivider() }
            }
        }
    }

    val item = selected
    if (operation == "menu" && item != null) {
        val remote = if (item.folder) remoteByLibraryId[item.id] else null
        HmSheet(onDismiss = { operation = null }, header = {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(if (remote != null) Icons.Outlined.Cloud else kindIcon(item.type), background = if (remote != null) hm.mint else hm.divider)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle(item), color = hm.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                @Composable fun action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, detail: String? = null, danger: Boolean = false, onClick: () -> Unit) {
                    HmRow(label, detail, leading = { Icon(icon, null, tint = if (danger) hm.danger else hm.ink) }, onClick = onClick)
                }
                when {
                    remote != null -> {
                        Text("${remote.owner}/${remote.repo} · ${remote.branch} / ${remote.repoPath.ifBlank { "raíz" }}", color = hm.muted, style = MaterialTheme.typography.bodySmall)
                        Text("Última actualización: ${if (remote.lastSuccess == 0L) "pendiente" else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(remote.lastSuccess))}",
                            color = hm.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                        action(Icons.Outlined.Refresh, "Actualizar ahora") { operation = null; model.refreshGitHub(remote.id) }
                        action(Icons.Outlined.Schedule, if (remote.autoSync) "Desactivar sincronización automática" else "Activar sincronización automática") { operation = null; model.toggleGitHubAuto(remote.id) }
                        action(Icons.Outlined.AltRoute, "Elegir otra rama", "Se añade como copia separada") { operation = null; model.githubScreen = true }
                        action(Icons.Outlined.LinkOff, "Desvincular y conservar copias", danger = true) { operation = "unlink" }
                    }
                    item.id in remoteDocumentIds -> {
                        Text("GitHub · solo lectura. Los cambios remotos no modifican tus notas locales.", color = hm.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                        action(Icons.Outlined.FileCopy, "Crear copia local", "Editable, fuera de la carpeta de GitHub") {
                            operation = null; if (item.type == "REMOTE_MARKDOWN") model.copyRemoteMarkdown(item.id) else model.copyRemoteHtml(item.id)
                        }
                        if (item.id in retiredDocumentIds) action(Icons.Outlined.Delete, "Eliminar copia retirada", danger = true) { operation = "delete-retired" }
                    }
                    else -> {
                        if (!item.folder) action(Icons.Outlined.OpenInNew, "Abrir") { operation = null; model.open(item.id) }
                        action(Icons.Outlined.DriveFileRenameOutline, "Renombrar") { title = item.title; operation = "rename" }
                        action(Icons.Outlined.DriveFileMove, "Mover a…", "Elige la carpeta de destino") { operation = "move" }
                        action(Icons.Outlined.ContentCopy, "Duplicar") { operation = null; model.action { model.repository.duplicate(item.id, item.folder) } }
                        action(Icons.Outlined.Delete, "Eliminar", danger = true) { operation = "delete" }
                    }
                }
            }
        }
    }
    if (operation == "unlink" && item != null) HmConfirm("Desvincular GitHub",
        "La carpeta dejará de actualizarse. Sus documentos y anotaciones quedarán como copias locales; la credencial de la cuenta seguirá disponible para otras carpetas.",
        "Desvincular", { operation = null; remoteByLibraryId[item.id]?.let { model.unlinkGitHub(it.id) } }, { operation = null }, danger = true)
    if (operation == "delete-retired" && item != null) HmConfirm("¿Eliminar copia retirada?", "Se borrarán este documento y sus anotaciones locales. Esta acción no se puede deshacer.",
        "Eliminar", { operation = null; model.deleteRetiredRemoteDocument(item.id) }, { operation = null }, danger = true)
    if (operation == "delete" && item != null) HmConfirm("¿Eliminar ${item.title}?",
        if (item.folder) "Se eliminarán esta carpeta, sus subcarpetas, documentos y anotaciones. Esta acción no se puede deshacer." else "Se eliminará el documento y sus anotaciones. Esta acción no se puede deshacer.",
        "Eliminar", { operation = null; model.delete(item.id, item.folder) }, { operation = null }, danger = true)
    if (operation in listOf("folder", "markdown", "flow", "rename")) AlertDialog(onDismissRequest = { operation = null }, containerColor = hm.paper, shape = RoundedCornerShape(28.dp),
        title = { Text(when (operation) { "rename" -> "Renombrar"; "folder" -> "Nueva carpeta"; "flow" -> "Nuevo Flow"; else -> "Nueva nota" }, style = MaterialTheme.typography.titleLarge) },
        text = { OutlinedTextField(title, { title = it }, label = { Text("Nombre") }, singleLine = true) },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = {
            val action = operation; val target = selected; operation = null
            model.action { when (action) {
                "folder" -> model.repository.createFolder(model.folderId, title)
                "markdown" -> model.open(model.repository.createMarkdown(model.folderId, title).id)
                "flow" -> model.open(model.repository.createFlow(model.folderId, title).id)
                "rename" -> model.repository.rename(target!!.id, target.folder, title)
            } }
        }) { Text(if (operation == "rename") "Guardar" else "Crear", color = hm.accent, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = { operation = null }) { Text("Cancelar", color = hm.ink) } })
    if (operation == "move" && item != null) {
        var keep by remember(item) { mutableStateOf<String?>(null) }
        val state = remember(item, folders, documents) {
            ItemPickerState(PickerMode.DESTINATION, folders.map { PickFolder(it.id, it.parentId, it.name) }, documents.map { PickDocument(it.id, it.folderId, it.title, it.type) },
                readOnlyRoots = remoteFolders.map { it.folderId }.toSet(), moving = item.id to item.folder,
                currentParent = if (item.folder) folders.firstOrNull { it.id == item.id }?.parentId else byId[item.id]?.folderId)
                .apply { keep?.let { runCatching { enter(it) } } }
        }
        ItemPickerSheet(state, PickerSubject("Mover", item.title, icon = kindIcon(item.type)), recent = remember { model.recentFolders.load() },
            onDismiss = { operation = null }, onDestination = { parent -> operation = null; model.move(item.id, item.folder, parent) },
            onCreateFolder = { parent, name -> keep = parent; model.action { model.repository.createFolder(parent, name) } })
    }
}

/** Tarjeta «Continuar»: el último documento abierto. */
@Composable
private fun ContinueCard(document: Document, onClick: () -> Unit) {
    val hm = LocalHm.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(hm.accent).clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) {}.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(kindIcon(document.type), background = Color.White.copy(alpha = .14f), tint = hm.onAccent)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(document.title, color = hm.onAccent, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val ago = document.lastOpenedAt?.let { openedAgo(System.currentTimeMillis() - it) }
            Text(kindLabel(document.type) + (ago?.let { " · $it" } ?: ""), color = hm.onAccent.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = hm.onAccent)
    }
}

/** «abierto hace 5 min»: en español, sin depender del idioma del sistema. */
internal fun openedAgo(elapsed: Long): String {
    val minutes = (elapsed / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "abierto ahora"
        minutes < 60 -> "abierto hace $minutes min"
        minutes < 24 * 60 -> "abierto hace ${minutes / 60} h"
        minutes < 2 * 24 * 60 -> "abierto ayer"
        else -> "abierto hace ${minutes / (24 * 60)} días"
    }
}
