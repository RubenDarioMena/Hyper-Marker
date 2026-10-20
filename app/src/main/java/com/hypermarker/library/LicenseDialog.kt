package com.hypermarker.library

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LicenseDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val paragraphs by produceState<List<String>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            context.assets.list("licenses").orEmpty().sorted().flatMap { name ->
                context.assets.open("licenses/$name").bufferedReader().use { it.readText() }.split("\n\n")
            }
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Licencias de código abierto") },
        text = { LazyColumn(Modifier.heightIn(max = 480.dp)) { items(paragraphs) { Text(it + "\n", style = MaterialTheme.typography.bodySmall) } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}
