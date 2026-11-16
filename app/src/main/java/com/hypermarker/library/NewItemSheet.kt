package com.hypermarker.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hypermarker.ui.*
import com.hypermarker.ui.theme.LocalHm

/** Hoja «Nuevo» (pantalla 2): crear, importar del teléfono o traer de internet. */
@Composable
fun NewItemSheet(folderName: String?, onDismiss: () -> Unit, onCreate: (String) -> Unit, onImportFiles: () -> Unit, onImportTree: () -> Unit,
                 onWeb: () -> Unit, onGitHub: () -> Unit) {
    val hm = LocalHm.current
    HmSheet(onDismiss = onDismiss, title = if (folderName == null) "Nuevo en Biblioteca" else "Nuevo en «$folderName»") {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SectionLabel("Crear", Modifier.padding(top = 8.dp, bottom = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CreateCard(Icons.Outlined.Folder, "Carpeta", Modifier.weight(1f)) { onCreate("folder") }
                CreateCard(Icons.Outlined.Description, "Nota", Modifier.weight(1f)) { onCreate("markdown") }
                CreateCard(Icons.Outlined.Hub, "Flow", Modifier.weight(1f)) { onCreate("flow") }
            }
            SectionLabel("Importar del teléfono", Modifier.padding(top = 20.dp))
            HmRow("Archivos", "HTML, ZIP o Markdown", leading = { Icon(Icons.Outlined.FileUpload, null, tint = hm.ink) }, onClick = onImportFiles)
            HmDivider()
            HmRow("Carpeta HTML", "Un proyecto completo con sus recursos", leading = { Icon(Icons.Outlined.DriveFolderUpload, null, tint = hm.ink) }, onClick = onImportTree)
            SectionLabel("Traer de internet", Modifier.padding(top = 16.dp))
            HmRow("Página web", "Navega y guarda una copia sin conexión", leading = { Icon(Icons.Outlined.Language, null, tint = hm.ink) }, onClick = onWeb)
            HmDivider()
            HmRow("Carpeta de GitHub", "Solo lectura, se actualiza sola", leading = { Icon(Icons.Outlined.Cloud, null, tint = hm.ink) }, onClick = onGitHub)
        }
    }
}

@Composable
private fun CreateCard(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val hm = LocalHm.current
    Column(modifier.heightIn(min = 96.dp).clip(RoundedCornerShape(16.dp)).background(hm.surface).border(1.dp, hm.border, RoundedCornerShape(16.dp))
        .clickable(role = Role.Button, onClickLabel = "Crear $label", onClick = onClick).padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(26.dp), tint = hm.accent)
        Text(label, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium, color = hm.ink)
    }
}
