package com.hypermarker.picker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hypermarker.ui.*
import com.hypermarker.ui.theme.LocalHm

/** Qué hace la hoja y sobre qué: se muestra en la cabecera. */
data class PickerSubject(val verb: String, val title: String, val icon: ImageVector? = null, val image: (suspend () -> ImageBitmap?)? = null, val imageKey: String = title)

/**
 * Selector de elementos de toda la app (pantallas 13–14). Sólo muestra la biblioteca: nunca rutas del
 * sistema de archivos. `thumbnail` resuelve la miniatura de un documento desde `PreviewStore`.
 */
@Composable
fun ItemPickerSheet(
    state: ItemPickerState,
    subject: PickerSubject,
    recent: List<String> = emptyList(),
    thumbnail: (String) -> (suspend () -> ImageBitmap?)? = { null },
    onDismiss: () -> Unit,
    /** Texto del botón principal en DESTINATION, a partir del nombre del destino. */
    actionLabel: (String) -> String = { "Mover a «$it»" },
    onDestination: (String?) -> Unit = {},
    onCreateFolder: ((parent: String?, name: String) -> Unit)? = null,
    onRelate: (documents: List<String>, folders: List<String>) -> Unit = { _, _ -> },
    onImage: (() -> Unit)? = null,
    onOpen: (String) -> Unit = {},
) {
    val hm = LocalHm.current
    var tick by remember { mutableIntStateOf(0) }
    fun changed() { tick++ }
    var creating by remember { mutableStateOf(false) }
    val entries = remember(tick, state.folders.size, state.documents.size) { state.entries() }
    val location = state.location
    val locationName = location?.let { state.folders[it]?.name } ?: "Biblioteca"
    val problem = if (state.mode == PickerMode.DESTINATION) state.destinationProblem(location) else null

    HmSheet(onDismiss = onDismiss, fullHeight = true, header = {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (subject.image != null) Thumbnail(subject.imageKey, "IMAGE", Modifier.size(52.dp).clip(CircleShape).border(2.dp, hm.pin, CircleShape), subject.image)
            else IconTile(subject.icon ?: Icons.Outlined.Download, size = 52.dp, background = hm.divider)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp).semantics(mergeDescendants = true) { heading() }) {
                Text(subject.verb.uppercase(), color = hm.muted, style = MaterialTheme.typography.labelSmall)
                Text(subject.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RoundIconButton(Icons.Outlined.Close, "Cerrar", onDismiss)
        }
    }, footer = when (state.mode) {
        PickerMode.DESTINATION -> ({
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onCreateFolder != null) RoundIconButton(Icons.Outlined.CreateNewFolder, "Crear carpeta en $locationName", { creating = true },
                    size = 52.dp, background = hm.surface, outlined = true, enabled = location == null || !state.isReadOnly(location))
                Spacer(Modifier.width(10.dp))
                PrimaryButton(if (problem == null) actionLabel(locationName) else problem, { onDestination(location) }, Modifier.weight(1f), enabled = problem == null)
            }
        })
        PickerMode.RELATE -> ({
            Row(verticalAlignment = Alignment.CenterVertically) {
                val chosen = state.selection.toList()
                Box(Modifier.width((22 + 20 * chosen.size.coerceAtMost(3)).dp).height(42.dp)) {
                    chosen.take(3).forEachIndexed { index, id ->
                        Thumbnail(id, state.documents[id]?.type ?: "FOLDER", Modifier.offset(x = (index * 20).dp).size(42.dp).clip(RoundedCornerShape(10.dp))
                            .border(1.dp, hm.border, RoundedCornerShape(10.dp)), thumbnail(id))
                    }
                }
                Text(when (chosen.size) { 0 -> "Elige uno o varios"; 1 -> "1 elegido"; else -> "${chosen.size} elegidos" }, Modifier.weight(1f).padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodyMedium)
                PrimaryButton("Relacionar ${chosen.size}", { onRelate(state.selectedDocuments(), state.selectedFolders()) }, enabled = chosen.isNotEmpty())
            }
        })
        PickerMode.LIST -> null
    }) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            var query by remember { mutableStateOf(state.query) }
            OutlinedTextField(query, { query = it; state.query = it; changed() }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(50),
                placeholder = { Text(if (state.mode == PickerMode.DESTINATION) "Buscar una carpeta" else "Buscar en toda la biblioteca", color = hm.muted) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = hm.muted) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = ""; state.query = ""; changed() }) { Icon(Icons.Outlined.Close, "Borrar búsqueda") } }) else null,
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = hm.surface, focusedContainerColor = hm.surface, unfocusedBorderColor = hm.border))
            if (state.mode != PickerMode.DESTINATION) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindFilter.entries.forEach { kind -> HmChip(kind.label, state.filter == kind, { state.filter = kind; changed() }) }
            }
            if (state.query.isBlank() && state.mode != PickerMode.LIST && (state.mode == PickerMode.DESTINATION || location != null)) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                HmChip("Biblioteca", location == null, { state.enter(null); changed() })
                state.crumbs().forEach { folder ->
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.padding(horizontal = 4.dp), tint = hm.muted)
                    HmChip(folder.name, folder.id == location, { state.enter(folder.id); changed() })
                }
            }
        }
        val columns = if (state.mode == PickerMode.DESTINATION) 1 else 2
        LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(if (columns == 1) 0.dp else 12.dp)) {
            val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }
            if (state.mode == PickerMode.DESTINATION && state.query.isBlank() && location == null) {
                val usable = recent.filter { it in state.folders && state.destinationProblem(it) == null }
                if (usable.isNotEmpty()) {
                    item(span = full) { SectionLabel("Usadas hace poco", Modifier.padding(bottom = 8.dp)) }
                    item(span = full) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            usable.forEach { id -> RecentChip(state.folders.getValue(id).name) { state.enter(id); changed() } }
                        }
                    }
                }
            }
            if (state.mode == PickerMode.RELATE && onImage != null && state.query.isBlank() && location == null) item(span = full) {
                HmRow("Imagen de la galería", "Se guarda una copia privada reescalada", leading = { IconTile(Icons.Outlined.AddPhotoAlternate) },
                    trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = hm.muted) }, onClick = onImage)
            }
            if (state.mode != PickerMode.LIST) item(span = full) {
                SectionLabel(if (state.query.isNotBlank()) "Resultados en toda la biblioteca" else if (location == null) "En la biblioteca" else "Dentro de $locationName",
                    Modifier.padding(top = 4.dp, bottom = 4.dp))
            }
            if (entries.isEmpty()) item(span = full) {
                Text(if (state.query.isNotBlank()) "No hay coincidencias." else if (state.mode == PickerMode.DESTINATION) "No hay subcarpetas aquí." else "Esta carpeta está vacía.",
                    Modifier.padding(vertical = 24.dp), color = hm.muted)
            }
            entries.forEach { entry ->
                when {
                    entry is PickEntry.FolderEntry && state.mode == PickerMode.DESTINATION -> item(key = entry.id, span = full) {
                        Column {
                            FolderRow(entry, state.isReadOnly(entry.id), selectable = false, selected = false,
                                onEnter = { state.enter(entry.id); changed() }, onToggle = {})
                            HmDivider()
                        }
                    }
                    entry is PickEntry.FolderEntry && state.mode == PickerMode.RELATE -> item(key = entry.id, span = full) {
                        Column {
                            FolderRow(entry, false, selectable = true, selected = entry.id in state.selection,
                                onEnter = { state.enter(entry.id); changed() }, onToggle = { state.toggle(entry.id); changed() })
                            HmDivider()
                        }
                    }
                    else -> item(key = entry.id) {
                        val type = (entry as? PickEntry.DocumentEntry)?.document?.type ?: "FOLDER"
                        ItemCard(entry, type, thumbnail(entry.id), selectable = state.mode == PickerMode.RELATE, selected = entry.id in state.selection) {
                            when {
                                state.mode == PickerMode.LIST -> onOpen(entry.id)
                                entry.disabledReason == null -> { state.toggle(entry.id); changed() }
                            }
                        }
                    }
                }
            }
            if (state.mode == PickerMode.DESTINATION && state.query.isBlank() && state.documentsHere() > 0) item(span = full) {
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Description, null, Modifier.size(20.dp), tint = hm.muted)
                    Text(if (state.documentsHere() == 1) "También hay 1 documento en esta carpeta" else "También hay ${state.documentsHere()} documentos en esta carpeta",
                        Modifier.padding(start = 10.dp), color = hm.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { creating = false }, containerColor = hm.paper, shape = RoundedCornerShape(28.dp),
            title = { Text("Nueva carpeta en «$locationName»", style = MaterialTheme.typography.titleLarge) },
            text = { OutlinedTextField(name, { name = it.take(200) }, label = { Text("Nombre") }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { creating = false; onCreateFolder?.invoke(location, name.trim()) }) { Text("Crear", color = hm.accent) } },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancelar", color = hm.ink) } })
    }
}

@Composable
private fun RecentChip(name: String, onClick: () -> Unit) {
    val hm = LocalHm.current
    Row(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(hm.mint).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Folder, null, Modifier.size(20.dp), tint = hm.ink)
        Text(name, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = hm.ink)
    }
}

@Composable
private fun FolderRow(entry: PickEntry.FolderEntry, readOnly: Boolean, selectable: Boolean, selected: Boolean, onEnter: () -> Unit, onToggle: () -> Unit) {
    val hm = LocalHm.current
    val count = entry.folders + entry.documents
    val subtitle = entry.disabledReason ?: when {
        selectable -> "Carpeta · entra para elegir dentro"
        count == 0 -> "Vacía"
        count == 1 -> "1 elemento"
        else -> "$count elementos"
    }
    HmRow(entry.title, subtitle, enabled = entry.enterable || selectable,
        leading = { IconTile(if (readOnly) Icons.Outlined.Cloud else Icons.Outlined.Folder, background = hm.divider) },
        trailing = {
            if (selectable) SelectionMark(selected, entry.disabledReason == null, "Elegir ${entry.title}", onToggle)
            if (!entry.enterable) Icon(Icons.Outlined.Lock, "No disponible", tint = hm.muted)
            else Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = hm.muted)
        },
        onClick = if (entry.enterable) onEnter else null)
}

@Composable
private fun SelectionMark(selected: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    val hm = LocalHm.current
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
        .semantics { contentDescription = description; this.selected = selected }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (selected) hm.accent else hm.surface)
            .border(if (selected) 0.dp else 1.5.dp, hm.border, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = hm.onAccent)
        }
    }
}

@Composable
private fun ItemCard(entry: PickEntry, type: String, load: (suspend () -> ImageBitmap?)?, selectable: Boolean, selected: Boolean, onClick: () -> Unit) {
    val hm = LocalHm.current
    val disabled = entry.disabledReason != null
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(if (disabled) hm.divider.copy(alpha = .6f) else hm.surface)
        .border(if (selected) BorderStroke(2.dp, hm.accent) else BorderStroke(1.dp, hm.border), shape)
        .clickable(enabled = !disabled || !selectable, role = if (selectable) Role.Checkbox else Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { if (selectable) this.selected = selected }
        .alpha(if (disabled) .7f else 1f)) {
        Box {
            Thumbnail(entry.id, type, Modifier.fillMaxWidth().aspectRatio(1.3f), load)
            if (selectable && !disabled) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape)
                .background(if (selected) hm.accent else Color.White.copy(alpha = .9f)).border(if (selected) 0.dp else 1.5.dp, hm.border, CircleShape),
                contentAlignment = Alignment.Center) { if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = hm.onAccent) }
        }
        HmDivider()
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(entry.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.disabledReason ?: kindLabel(type), color = hm.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
