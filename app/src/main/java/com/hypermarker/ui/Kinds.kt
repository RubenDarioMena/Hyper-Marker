package com.hypermarker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.hypermarker.ui.theme.FlowInk
import com.hypermarker.ui.theme.LocalHm

fun kindLabel(type: String) = when (type) {
    "HTML" -> "HTML interactivo"; "WEB_SNAPSHOT" -> "Página web sin conexión"; "FLOW" -> "Flow"; "MERMAID" -> "Diagrama Mermaid"
    "REMOTE_MARKDOWN" -> "Markdown · GitHub"; "FOLDER" -> "Carpeta"; "IMAGE" -> "Imagen"; else -> "Markdown"
}

fun kindShort(type: String) = when (type) {
    "HTML" -> "HTML"; "WEB_SNAPSHOT" -> "Web"; "FLOW" -> "Flow"; "MERMAID" -> "Mermaid"; "FOLDER" -> "Carpeta"; "IMAGE" -> "Imagen"; else -> "Markdown"
}

fun kindIcon(type: String): ImageVector = when (type) {
    "HTML" -> Icons.Outlined.Code; "WEB_SNAPSHOT" -> Icons.Outlined.Download; "FLOW" -> Icons.Outlined.Hub; "MERMAID" -> Icons.Outlined.AccountTree
    "FOLDER" -> Icons.Outlined.Folder; "IMAGE" -> Icons.Outlined.Image; "REMOTE_MARKDOWN" -> Icons.Outlined.CloudQueue
    else -> Icons.Outlined.Description
}

/**
 * Miniatura de un elemento: la imagen de `PreviewStore` cuando existe; si no, un dibujo
 * del tipo (líneas de texto, mini-grafo o carpeta) que nunca ejecuta contenido.
 */
@Composable
fun Thumbnail(key: String, type: String, modifier: Modifier = Modifier, load: (suspend () -> ImageBitmap?)? = null) {
    val image by produceState<ImageBitmap?>(null, key) { value = runCatching { load?.invoke() }.getOrNull() }
    val hm = LocalHm.current
    val background = when (type) { "FLOW" -> FlowInk.canvas; "FOLDER" -> hm.mint; else -> Color.White }
    Box(modifier.background(background), contentAlignment = Alignment.Center) {
        val bitmap = image
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        else when (type) {
            "FOLDER" -> Icon(Icons.Outlined.Folder, null, Modifier.size(36.dp), tint = hm.ink)
            "IMAGE" -> Icon(Icons.Outlined.Image, null, Modifier.size(36.dp), tint = hm.muted)
            "MERMAID" -> Canvas(Modifier.fillMaxSize(.62f)) {
                // Dibujo del tipo: cajas con flechas, sin ejecutar el diagrama.
                val ink = Color(0xFF28253D); val stroke = 2.dp.toPx(); val w = size.width * .42f; val h = size.height * .22f
                val boxes = listOf(Offset((size.width - w) / 2, 0f), Offset(0f, size.height - h), Offset(size.width - w, size.height - h))
                drawLine(ink, Offset(size.width / 2, h), Offset(w / 2, size.height - h), stroke)
                drawLine(ink, Offset(size.width / 2, h), Offset(size.width - w / 2, size.height - h), stroke)
                boxes.forEach { drawRect(Color.White, it, Size(w, h)); drawRect(ink, it, Size(w, h), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)) }
            }
            "FLOW" -> Canvas(Modifier.fillMaxSize(.6f)) {
                val top = Offset(size.width / 2, size.height * .2f); val l = Offset(size.width * .2f, size.height * .8f); val r = Offset(size.width * .8f, size.height * .8f)
                drawLine(FlowInk.mint, top, l, 3.dp.toPx()); drawLine(FlowInk.mint, top, r, 3.dp.toPx())
                drawCircle(FlowInk.pin, 7.dp.toPx(), top); drawCircle(FlowInk.mint, 6.dp.toPx(), l); drawCircle(FlowInk.mint, 6.dp.toPx(), r)
            }
            else -> Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                val bar = 6.dp.toPx(); val gap = 6.dp.toPx(); val r = CornerRadius(bar / 2)
                drawRoundRect(Color(0xFF14201C), Offset.Zero, Size(size.width * .7f, bar + 2), r)
                var y = bar + gap * 1.8f
                listOf(1f, .92f, .6f).forEach { w -> drawRoundRect(Color(0xFFCFCBBE), Offset(0f, y), Size(size.width * w, bar * .7f), r); y += bar + gap * .6f }
                if (type == "HTML" || type == "WEB_SNAPSHOT") drawRoundRect(Color(0xFFE3F0EB), Offset(0f, y + gap), Size(size.width, (size.height - y - gap).coerceAtLeast(0f)), CornerRadius(8.dp.toPx()))
            }
        }
    }
}
