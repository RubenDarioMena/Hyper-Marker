package com.hypermarker.github

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hypermarker.library.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(topBar = { TopAppBar(title = { Text("Añadir carpeta de GitHub") }, navigationIcon = {
        IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            when (step) {
                0 -> {
                    Icon(Icons.Outlined.Cloud, null, Modifier.size(40.dp))
                    Text("Conecta GitHub", style = MaterialTheme.typography.headlineSmall)
                    Text("Crea un token fine-grained con Contents: Read-only y solo los repositorios que elijas. Se cifra con Android Keystore; el HTML importado no puede leerlo.")
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("Token de GitHub") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password), singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    Button(enabled = token.isNotBlank() && !model.busy, onClick = {
                        model.connectGitHub(token); token = ""; step = 1
                    }) { Text("Conectar") }
                    TextButton(onClick = { step = 1 }) { Text("Usar solo repositorios públicos sin token") }
                }
                1 -> {
                    Text("Elige repositorio", style = MaterialTheme.typography.headlineSmall)
                    Text("La lista depende de los permisos de tu token. También puedes escribir owner/repo.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(owner, { owner = it }, Modifier.weight(1f), label = { Text("Owner") }, singleLine = true)
                        OutlinedTextField(repoName, { repoName = it }, Modifier.weight(1f), label = { Text("Repo") }, singleLine = true)
                    }
                    TextButton(enabled = owner.isNotBlank() && repoName.isNotBlank() && !model.busy, onClick = {
                        model.findGitHubRepo(owner, repoName) { selected ->
                            repo = selected; branch = selected.defaultBranch; step = 2
                        }
                    }) { Text("Buscar repositorio") }
                    Row {
                        if (model.hasGitHubToken) TextButton(onClick = { model.loadGitHubRepos() }) { Text("Refrescar lista") }
                        TextButton(onClick = { step = 0 }) { Text(if (model.hasGitHubToken) "Reemplazar token" else "Conectar cuenta") }
                    }
                    if (model.githubListCapped) Text("Se alcanzó el límite de 500 resultados. Escribe owner/repo para elegir otro.")
                    LazyColumn(Modifier.weight(1f)) {
                        items(model.githubRepos, key = { it.id }) { item ->
                            ListItem(headlineContent = { Text("${item.owner}/${item.name}") },
                                supportingContent = { Text(if (item.private) "Privado · ${item.defaultBranch}" else "Público · ${item.defaultBranch}") },
                                leadingContent = { Icon(Icons.Outlined.Folder, null) },
                                modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = { repo = item; branch = item.defaultBranch; step = 2; model.loadGitHubBranches(item) }) { Text("Elegir ${item.name}") }
                        }
                        if (model.githubRepoNext != null)
                            item { TextButton(onClick = { model.githubRepoNext?.let { model.loadGitHubRepos(it) } }) { Text("Cargar más") } }
                    }
                }
                2 -> {
                    val selected = repo
                    Text("Elige rama", style = MaterialTheme.typography.headlineSmall)
                    Text("${selected?.owner}/${selected?.name}. Una rama de PR no aparece en main hasta fusionarla.")
                    OutlinedTextField(branch, { branch = it }, Modifier.fillMaxWidth(), label = { Text("Nombre de rama") }, singleLine = true)
                    Button(enabled = selected != null && branch.isNotBlank() && !model.busy, onClick = {
                        if (selected != null) { path = ""; model.loadGitHubDirectory(selected, branch, path); step = 3 }
                    }) { Text("Explorar rama") }
                    if (model.githubListCapped) Text("Se alcanzó el límite de 500 ramas. Escribe el nombre de otra rama.")
                    LazyColumn {
                        items(model.githubBranches, key = { it.name }) { item ->
                            TextButton(onClick = {
                                branch = item.name; path = ""; if (selected != null) model.loadGitHubDirectory(selected, branch, path); step = 3
                            }) { Text(item.name) }
                        }
                        if (model.githubBranchNext != null) item {
                            TextButton(onClick = { model.githubBranchNext?.let { page -> selected?.let { model.loadGitHubBranches(it, page) } } }) { Text("Cargar más ramas") }
                        }
                    }
                }
                else -> {
                    val selected = repo
                    Text("Elige carpeta", style = MaterialTheme.typography.headlineSmall)
                    Text("${selected?.owner}/${selected?.name} · $branch / ${path.ifBlank { "raíz" }}")
                    if (path.isNotBlank()) TextButton(onClick = {
                        path = path.substringBeforeLast('/', ""); if (selected != null) model.loadGitHubDirectory(selected, branch, path)
                    }) { Text("Subir una carpeta") }
                    LazyColumn(Modifier.weight(1f)) {
                        items(model.githubItems.filter { it.type == "dir" }, key = { it.path }) { item ->
                            TextButton(onClick = { path = item.path; if (selected != null) model.loadGitHubDirectory(selected, branch, path) }) {
                                Icon(Icons.Outlined.Folder, null); Spacer(Modifier.width(8.dp)); Text(item.name)
                            }
                        }
                        item { Text("${model.githubItems.count { it.type == "file" }} archivos en este nivel; los subdirectorios se revisan al sincronizar.") }
                    }
                    Button(enabled = selected != null && !model.busy, onClick = {
                        if (selected != null) model.addGitHubFolder(selected, branch, path)
                    }) { Text("Añadir esta carpeta") }
                }
            }
        }
    }
    if (model.busy) AlertDialog(onDismissRequest = {}, title = { Text("Consultando GitHub") },
        text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {})
    model.error?.let { value -> AlertDialog(onDismissRequest = { model.error = null }, title = { Text("No se pudo completar") },
        text = { Text(value) }, confirmButton = { TextButton(onClick = { model.error = null }) { Text("Entendido") } }) }
}
