package com.hypermarker.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class Item(val id: String, val folder: Boolean, val title: String)

private fun kindLabel(type: String) = when (type) { "HTML" -> "HTML interactivo"; "FLOW" -> "Flow Dots"; else -> "Markdown" }
private fun kindIcon(type: String) = when (type) { "HTML" -> Icons.Outlined.Code; "FLOW" -> Icons.Outlined.Hub; else -> Icons.Outlined.Description }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(model: LibraryViewModel) {
    val folders by model.folders.collectAsState()
    val documents by model.documents.collectAsState()
    var search by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Item?>(null) }
    var operation by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) model.receive(it) }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { model.receive(listOf(it), true) } }
    val folderLabels = folders.associate { folder ->
        val path = mutableListOf<String>(); val seen = mutableSetOf<String>(); var node: com.hypermarker.storage.Folder? = folder
        while (node != null && seen.add(node.id)) { path.add(0, node.name); node = folders.firstOrNull { it.id == node?.parentId } }
        folder.id to ("Biblioteca / " + path.joinToString(" / "))
    }
    val current = folders.firstOrNull { it.id == model.folderId }
    val crumbs = buildList {
        var node = current; val seen = mutableSetOf<String>()
        while (node != null && seen.add(node.id)) { add(0, node); node = folders.firstOrNull { it.id == node?.parentId } }
    }
    val entries = folders.filter { if (search.isBlank()) it.parentId == model.folderId else it.name.contains(search, true) }
        .map { Item(it.id, true, it.name) } + documents.filter { if (search.isBlank()) it.folderId == model.folderId else it.title.contains(search, true) }
        .map { Item(it.id, false, it.title) }
    BackHandler(model.folderId != null) { model.folderId = current?.parentId; search = "" }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Hyper-Marker", fontWeight = FontWeight.SemiBold) }, actions = {
            Box {
                IconButton(onClick = { adding = true }, enabled = !model.busy) { Icon(Icons.Outlined.Add, "Crear o importar") }
                DropdownMenu(adding, { adding = false }) {
                    listOf("folder" to "Nueva carpeta", "markdown" to "Nueva nota Markdown", "flow" to "Nuevo Flow").forEach { (action, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { adding = false; operation = action; title = "" })
                    }
                    DropdownMenuItem(text = { Text("Importar HTML, ZIP o Markdown") }, onClick = { adding = false; filePicker.launch(arrayOf("*/*")) })
                    DropdownMenuItem(text = { Text("Importar carpeta HTML") }, onClick = { adding = false; treePicker.launch(null) })
                    DropdownMenuItem(text = { Text("Licencias de código abierto") }, onClick = { adding = false; licenses = true })
                }
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Text(if (search.isBlank()) "Tu biblioteca" else "Resultados en toda la biblioteca", style = MaterialTheme.typography.headlineMedium)
            Text("Ideas que puedes explorar, anotar y conservar.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = { Text("Buscar notas y carpetas") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { model.folderId = null; search = "" }) { Text("Biblioteca") }
                crumbs.forEach { folder ->
                    Text("/")
                    TextButton(onClick = { model.folderId = folder.id; search = "" }) { Text(folder.name) }
                }
            }
            if (entries.isEmpty()) {
                Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.AutoStories, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (search.isBlank()) "Tu próxima idea empieza aquí" else "No hay coincidencias", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                    Text("Importa una experiencia HTML o escribe una nota.", style = MaterialTheme.typography.bodyMedium)
                    if (search.isBlank()) TextButton(onClick = { model.action { model.openedId = model.repository.installSample().id } }, enabled = !model.busy) { Text("Explorar un ejemplo interactivo") }
                }
            } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(entries, key = { it.id }) { item ->
                    ListItem(headlineContent = { Text(item.title) },
                        supportingContent = { Text(if (item.folder) "Carpeta" else kindLabel(documents.first { it.id == item.id }.type)) },
                        leadingContent = { Icon(if (item.folder) Icons.Outlined.Folder else kindIcon(documents.first { it.id == item.id }.type), null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { IconButton(onClick = { selected = item; operation = "menu" }) { Icon(Icons.Outlined.MoreVert, "Acciones de ${item.title}") } },
                        modifier = Modifier.combinedClickable(onClick = { if (item.folder) { model.folderId = item.id; search = "" } else model.openedId = item.id },
                            onLongClick = { selected = item; operation = "menu" }))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                }
            }
        }
    }
    if (licenses) LicenseDialog { licenses = false }
    if (model.busy) AlertDialog(onDismissRequest = {}, title = { Text("Preparando tu biblioteca") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = model::cancel) { Text("Cancelar") } })
    model.error?.let { error -> AlertDialog(onDismissRequest = { model.error = null }, title = { Text("No se pudo completar") }, text = { Text(error) }, confirmButton = { TextButton(onClick = { model.error = null }) { Text("Entendido") } }) }
    model.pending?.let { pending -> AlertDialog(onDismissRequest = model::cancelImport, title = { Text("Elige la página inicial") },
        text = { LazyColumn { items(pending.entries) { path -> TextButton(onClick = { model.chooseEntry(path) }) { Text(path) } } } },
        confirmButton = {}, dismissButton = { TextButton(onClick = model::cancelImport) { Text("Cancelar") } }) }
    if (operation == "menu" && selected != null) AlertDialog(onDismissRequest = { operation = null }, title = { Text(selected!!.title) },
        text = { Column {
            listOf("rename" to "Renombrar", "move" to "Mover", "duplicate" to "Duplicar", "delete" to "Eliminar").forEach { (action, label) ->
                TextButton(onClick = {
                    title = selected!!.title
                    if (action == "duplicate") { val item = selected!!; model.action { model.repository.duplicate(item.id, item.folder) }; operation = null }
                    else operation = action
                }) { Text(label) }
            }
        } }, confirmButton = { TextButton(onClick = { operation = null }) { Text("Cerrar") } })
    if (operation in listOf("folder", "markdown", "flow", "rename")) AlertDialog(onDismissRequest = { operation = null }, title = { Text(when (operation) { "rename" -> "Renombrar"; "folder" -> "Crear carpeta"; "flow" -> "Crear Flow"; else -> "Crear nota" }) },
        text = { OutlinedTextField(title, { title = it }, label = { Text("Nombre") }, singleLine = true) },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = {
            val action = operation; val item = selected; operation = null
            model.action { when (action) {
                "folder" -> model.repository.createFolder(model.folderId, title)
                "markdown" -> { model.openedId = model.repository.createMarkdown(model.folderId, title).id }
                "flow" -> { model.openedId = model.repository.createFlow(model.folderId, title).id }
                "rename" -> model.repository.rename(item!!.id, item.folder, title)
            } }
        }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = { operation = null }) { Text("Cancelar") } })
    if (operation == "delete" && selected != null) AlertDialog(onDismissRequest = { operation = null }, title = { Text("¿Eliminar ${selected!!.title}?") },
        text = { Text(if (selected!!.folder) "Se eliminarán esta carpeta, sus subcarpetas, documentos y anotaciones. Esta acción no se puede deshacer." else "Se eliminará el documento y sus anotaciones. Esta acción no se puede deshacer.") },
        confirmButton = { TextButton(onClick = { val item = selected!!; operation = null; model.action { model.repository.delete(item.id, item.folder) } }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { operation = null }) { Text("Cancelar") } })
    if (operation == "move" && selected != null) AlertDialog(onDismissRequest = { operation = null }, title = { Text("Mover a…") },
        text = { LazyColumn {
            item { TextButton(onClick = { val item = selected!!; operation = null; model.action { model.repository.move(item.id, item.folder, null) } }) { Text("Biblioteca (raíz)") } }
            items(folders) { folder -> TextButton(onClick = { val item = selected!!; operation = null; model.action { model.repository.move(item.id, item.folder, folder.id) } }) { Text(folderLabels[folder.id].orEmpty()) } }
        } }, confirmButton = { TextButton(onClick = { operation = null }) { Text("Cancelar") } })
}
