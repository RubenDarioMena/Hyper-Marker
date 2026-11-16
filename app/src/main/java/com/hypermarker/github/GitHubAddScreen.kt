package com.hypermarker.github

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Check
import com.hypermarker.ui.*
import com.hypermarker.ui.theme.LocalHm
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hypermarker.library.LibraryViewModel

@Composable
fun GitHubAddScreen(model: LibraryViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(context) {
        val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()
        val window = activity?.window
        val flag = android.view.WindowManager.LayoutParams.FLAG_SECURE
        val alreadySecure = window?.attributes?.flags?.and(flag) != 0
        window?.addFlags(flag)
        onDispose { if (!alreadySecure) window?.clearFlags(flag) }
    }
    var token by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var repoName by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf<GitHubRepo?>(null) }
    var branch by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(if (model.hasGitHubToken) 1 else 0) }
    fun back() { when (step) { 3 -> step = 2; 2 -> step = 1; else -> model.githubScreen = false } }
    BackHandler { back() }
    LaunchedEffect(Unit) { if (model.hasGitHubToken) model.loadGitHubRepos() }
    val hm = LocalHm.current
    val steps = listOf("Cuenta", "Repositorio", "Rama", "Carpeta")
    val selected = repo
    Column(Modifier.fillMaxSize().background(hm.paper).windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding()) {
        // Cabecera con migas de pasos (pantalla 4).
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = hm.ink) }
            Text("Carpeta de GitHub", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("Paso ${step + 1} de 4", color = hm.muted, style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { index, label ->
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "$label: " + when { index < step -> "hecho"; index == step -> "paso actual"; else -> "pendiente" } }) {
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(when { index < step -> hm.accent; index == step -> hm.ink; else -> hm.divider }))
                    Text(label, Modifier.padding(top = 6.dp), color = if (index <= step) (if (index == step) hm.ink else hm.accent) else hm.muted,
                        style = MaterialTheme.typography.bodySmall, fontWeight = if (index == step) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 20.dp).padding(top = 12.dp)) {
            when (step) {
                0 -> {
                    Text("Conecta GitHub", style = MaterialTheme.typography.headlineSmall)
                    Text("Crea un token fine-grained con Contents: Read-only y solo los repositorios que elijas. Se cifra con Android Keystore; el HTML importado no puede leerlo.",
                        Modifier.padding(top = 8.dp), color = hm.muted, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth().padding(top = 16.dp), label = { Text("Token de GitHub") },
                        visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password), singleLine = true)
                    TextButton(onClick = { step = 1 }, Modifier.padding(top = 8.dp)) { Text("Usar solo repositorios públicos sin token", color = hm.accent) }
                }
                1 -> {
                    Text("Elige el repositorio", style = MaterialTheme.typography.headlineSmall)
                    Text("La lista depende de los permisos de tu token. También puedes escribir owner/repo.", Modifier.padding(top = 6.dp), color = hm.muted, style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(owner, { owner = it }, Modifier.weight(1f), label = { Text("Owner") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                        OutlinedTextField(repoName, { repoName = it }, Modifier.weight(1f), label = { Text("Repo") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                    }
                    Row {
                        TextButton(enabled = owner.isNotBlank() && repoName.isNotBlank() && !model.busy, onClick = {
                            model.findGitHubRepo(owner, repoName) { found -> repo = found; branch = found.defaultBranch; step = 2 }
                        }) { Text("Buscar repositorio", color = hm.accent) }
                        if (model.hasGitHubToken) TextButton(onClick = { model.loadGitHubRepos() }) { Text("Refrescar", color = hm.ink) }
                        TextButton(onClick = { step = 0 }) { Text(if (model.hasGitHubToken) "Reemplazar token" else "Conectar cuenta", color = hm.ink) }
                    }
                    if (model.githubListCapped) Text("Se alcanzó el límite de 500 resultados. Escribe owner/repo para elegir otro.", color = hm.muted, style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.weight(1f)) {
                        items(model.githubRepos, key = { it.id }) { item ->
                            HmRow("${item.owner}/${item.name}", (if (item.private) "Privado" else "Público") + " · ${item.defaultBranch}",
                                leading = { IconTile(Icons.Outlined.Book, background = hm.divider) },
                                trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = hm.muted) },
                                onClick = { repo = item; branch = item.defaultBranch; step = 2; model.loadGitHubBranches(item) })
                            HmDivider()
                        }
                        if (model.githubRepoNext != null) item { TextButton(onClick = { model.githubRepoNext?.let { model.loadGitHubRepos(it) } }) { Text("Cargar más", color = hm.accent) } }
                    }
                }
                2 -> {
                    Text("Elige la rama", style = MaterialTheme.typography.headlineSmall)
                    RepoChip(Icons.Outlined.Book, "${selected?.owner}/${selected?.name}")
                    Text("Una rama de PR no aparece en main hasta fusionarla.", Modifier.padding(top = 8.dp), color = hm.muted, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(branch, { branch = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Nombre de rama") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                    if (model.githubListCapped) Text("Se alcanzó el límite de 500 ramas. Escribe el nombre de otra rama.", color = hm.muted, style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                        items(model.githubBranches, key = { it.name }) { item ->
                            HmRow(item.name, if (item.name == selected?.defaultBranch) "Rama principal" else null, leading = { Icon(Icons.Outlined.AltRoute, null, tint = hm.ink) },
                                trailing = { if (item.name == branch) Icon(Icons.Outlined.Check, "Elegida", tint = hm.accent) },
                                onClick = { branch = item.name; path = ""; if (selected != null) model.loadGitHubDirectory(selected, branch, path); step = 3 })
                            HmDivider()
                        }
                        if (model.githubBranchNext != null) item {
                            TextButton(onClick = { model.githubBranchNext?.let { page -> selected?.let { model.loadGitHubBranches(it, page) } } }) { Text("Cargar más ramas", color = hm.accent) }
                        }
                    }
                }
                else -> {
                    Text("Elige la carpeta", style = MaterialTheme.typography.headlineSmall)
                    RepoChip(Icons.Outlined.Book, "${selected?.owner}/${selected?.name}")
                    RepoChip(Icons.Outlined.AltRoute, branch)
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (path.isBlank()) "raíz" else path, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable(enabled = path.isNotBlank()) { path = path.substringBeforeLast('/', ""); if (selected != null) model.loadGitHubDirectory(selected, branch, path) })
                        Text("  /  toca una carpeta para entrar", color = hm.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (path.isNotBlank()) TextButton(onClick = { path = path.substringBeforeLast('/', ""); if (selected != null) model.loadGitHubDirectory(selected, branch, path) }) { Text("Subir una carpeta", color = hm.accent) }
                    LazyColumn(Modifier.weight(1f)) {
                        items(model.githubItems.filter { it.type == "dir" }, key = { it.path }) { item ->
                            HmRow(item.name, null, leading = { Icon(Icons.Outlined.Folder, null, tint = hm.ink) },
                                trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = hm.muted) },
                                onClick = { path = item.path; if (selected != null) model.loadGitHubDirectory(selected, branch, path) })
                            HmDivider()
                        }
                    }
                }
            }
        }
        // Pie fijo con la acción del paso.
        Column(Modifier.fillMaxWidth()) {
            HmDivider()
            if (step == 3) Text("${model.githubItems.count { it.type == "file" }} archivos en este nivel. Las subcarpetas se revisan al sincronizar; el contenido llega en solo lectura.",
                Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = hm.muted, style = MaterialTheme.typography.bodySmall)
            when (step) {
                0 -> PrimaryButton("Conectar", { model.connectGitHub(token); token = ""; step = 1 }, Modifier.fillMaxWidth().padding(16.dp), enabled = token.isNotBlank() && !model.busy)
                2 -> PrimaryButton("Explorar «$branch»", { if (selected != null) { path = ""; model.loadGitHubDirectory(selected, branch, path); step = 3 } },
                    Modifier.fillMaxWidth().padding(16.dp), enabled = selected != null && branch.isNotBlank() && !model.busy)
                3 -> PrimaryButton("Añadir «${path.substringAfterLast('/').ifBlank { "raíz" }}» a la biblioteca", { if (selected != null) model.addGitHubFolder(selected, branch, path) },
                    Modifier.fillMaxWidth().padding(16.dp), enabled = selected != null && !model.busy)
                else -> Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (model.busy) AlertDialog(onDismissRequest = {}, title = { Text("Consultando GitHub") },
        text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {})
    model.error?.let { value -> AlertDialog(onDismissRequest = { model.error = null }, title = { Text("No se pudo completar") },
        text = { Text(value) }, confirmButton = { TextButton(onClick = { model.error = null }) { Text("Entendido") } }) }
}

@Composable
private fun RepoChip(icon: ImageVector, label: String) {
    val hm = LocalHm.current
    Row(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(50)).background(hm.surface).border(1.dp, hm.border, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = hm.ink)
        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
