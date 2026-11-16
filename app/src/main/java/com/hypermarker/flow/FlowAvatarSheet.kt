package com.hypermarker.flow

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermarker.storage.FlowNode
import com.hypermarker.storage.PrivateImages
import com.hypermarker.ui.*
import com.hypermarker.ui.theme.FlowInk
import com.hypermarker.ui.theme.LightSheetTheme
import com.hypermarker.ui.theme.LocalHm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val RING_NAMES = listOf("Menta", "Amarillo", "Coral", "Azul", "Blanco")

/**
 * Hoja «Foto del punto» (pantalla 12): galería (Photo Picker del sistema, sin permisos), vista previa de
 * un elemento relacionado o sin foto, y color del anillo. Los cambios se aplican al pulsar Guardar.
 */
@Composable
internal fun FlowAvatarSheet(model: FlowViewModel, node: FlowNode, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val links = model.linksOf(node.id).filter { it.type != "FOLDER" }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingLink by remember { mutableStateOf<LinkView?>(null) }
    var ring by remember { mutableStateOf(node.ringColor ?: FlowInk.rings[0]) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) { pendingUri = uri; pendingLink = null } }
    val pendingImage by produceState<ImageBitmap?>(null, pendingUri, pendingLink) {
        value = when {
            pendingUri != null -> withContext(Dispatchers.IO) { runCatching { PrivateImages.square(PrivateImages.decode(context, pendingUri!!, 256, crop = true), 256).asImageBitmap() }.getOrNull() }
            pendingLink != null -> loader(model, pendingLink!!)?.invoke()
            else -> null
        }
    }
    val shown = pendingImage ?: node.avatarPath?.let { model.images[it] }
    LightSheetTheme {
        val hm = LocalHm.current
        HmSheet(onDismiss = onDismiss, title = "Foto del punto", footer = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { model.clearAvatar(node.id); onDismiss() }, enabled = node.avatarPath != null) {
                    Text("Quitar foto", color = if (node.avatarPath != null) hm.danger else hm.muted, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton("Guardar", {
                    when {
                        pendingUri != null -> model.setAvatar(node.id, pendingUri!!, ring)
                        pendingLink != null -> model.setAvatarFrom(node.id, pendingLink!!, ring)
                        else -> model.setRing(node.id, ring)
                    }
                    onDismiss()
                })
            }
        }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                // Cómo quedará en el lienzo.
                Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp)).background(FlowInk.canvas)
                    .semantics { contentDescription = "Vista previa del punto en el lienzo" }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(96.dp).clip(CircleShape).background(FlowInk.canvas).border(3.dp, Color(ring.toInt()), CircleShape).padding(6.dp).clip(CircleShape)
                            .background(FlowInk.raised), contentAlignment = Alignment.Center) {
                            shown?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                        Text(node.label().ifBlank { "Punto" }, Modifier.offset(y = (-12).dp).clip(RoundedCornerShape(50)).background(FlowInk.text)
                            .padding(horizontal = 10.dp, vertical = 3.dp), color = FlowInk.canvas, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                SectionLabel("Imagen", Modifier.padding(top = 18.dp, bottom = 8.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(hm.surface).border(1.dp, hm.border, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.PhotoLibrary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Elegir de la galería", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("Se guarda una copia privada en la app", color = hm.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = hm.muted)
                }
                if (links.isNotEmpty()) {
                    Text("O usa la vista previa de un elemento relacionado", Modifier.padding(top = 14.dp, bottom = 8.dp), color = hm.muted, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        links.forEach { link ->
                            val chosen = pendingLink?.id == link.id
                            Thumbnail(link.id, link.type, Modifier.size(56.dp).clip(CircleShape).border(if (chosen) 3.dp else 1.dp, if (chosen) hm.accent else hm.border, CircleShape)
                                .clickable(role = Role.RadioButton) { pendingLink = link; pendingUri = null }
                                .semantics { contentDescription = "Usar la vista previa de ${link.title}"; selected = chosen }, loader(model, link))
                        }
                    }
                }
                SectionLabel("Anillo", Modifier.padding(top = 18.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                    FlowInk.rings.forEachIndexed { index, color ->
                        val chosen = ring == color
                        Box(Modifier.size(48.dp).clip(CircleShape).border(if (chosen) 2.dp else 0.dp, hm.ink, CircleShape).padding(if (chosen) 4.dp else 0.dp).clip(CircleShape)
                            .background(Color(color.toInt())).border(1.dp, hm.border, CircleShape)
                            .clickable(role = Role.RadioButton) { ring = color }.semantics { contentDescription = "Anillo ${RING_NAMES[index]}"; selected = chosen },
                            contentAlignment = Alignment.Center) { if (chosen) Icon(Icons.Outlined.Check, null, tint = FlowInk.canvas) }
                    }
                }
            }
        }
    }
}
