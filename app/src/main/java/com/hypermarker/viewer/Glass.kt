package com.hypermarker.viewer

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tono del cristal según el contenido que queda detrás. LIGHT: fondo claro, iconos oscuros.
 * Compose no puede desenfocar el WebView que está debajo, así que el efecto se aproxima con
 * relleno translúcido, borde especular y halo suave, y el tono se adapta muestreando la página.
 */
enum class GlassTone {
    LIGHT, DARK;
    val content get() = if (this == LIGHT) Color(0xFF111114) else Color.White
    val muted get() = content.copy(alpha = .62f)
    /** Halo del texto: separa la etiqueta del contenido que se transparenta detrás del cristal. */
    val halo get() = Shadow(if (this == LIGHT) Color.White else Color.Black.copy(alpha = .85f), blurRadius = 10f)
    @Composable fun sliderColors() = SliderDefaults.colors(thumbColor = content, activeTrackColor = content, inactiveTrackColor = content.copy(alpha = .18f),
        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
    companion object { fun of(luminance: Float?) = if (luminance == null || luminance > .5f) LIGHT else DARK }
}

fun Modifier.glass(shape: Shape, tone: GlassTone, selected: Boolean = false, solid: Boolean = false): Modifier {
    val light = tone == GlassTone.LIGHT
    val base = if (solid && !light) Color(0xFF1C1C1E) else Color.White
    // Los paneles con texto largo necesitan casi opacidad; los botones siguen siendo mayormente transparentes.
    val top = when { solid -> if (light) .95f else .93f; selected -> if (light) .72f else .40f; else -> if (light) .44f else .22f }
    return drawBehind {
        // Halo difuso para separar el cristal de fondos de su mismo color.
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeWidth = 2.dp.toPx()
                color = android.graphics.Color.argb(if (light) 40 else 70, 0, 0, 0)
                maskFilter = BlurMaskFilter(6.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
            }
            canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
        }
    }
        .clip(shape)
        .background(Brush.verticalGradient(listOf(base.copy(alpha = top), base.copy(alpha = top * if (solid) .97f else .62f))))
        .drawWithContent {
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = if (light) .35f else .18f), Color.Transparent),
                center = Offset(size.width * .28f, 0f), radius = size.maxDimension * .75f))
            drawContent()
        }
        .border(if (selected) 1.5.dp else 1.dp, if (selected) SolidColor(tone.content.copy(alpha = .7f))
            else Brush.linearGradient(listOf(Color.White.copy(alpha = .9f), Color.White.copy(alpha = .12f), Color.White.copy(alpha = .5f))), shape)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassButton(icon: ImageVector, description: String, tone: GlassTone, modifier: Modifier = Modifier, label: String? = null, selected: Boolean = false,
                enabled: Boolean = true, size: Int = 48, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val shape = if (label == null) CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(50)
    Row(modifier
        .heightIn(min = size.dp).let { if (label == null) it.size(size.dp) else it.widthIn(min = 88.dp) }
        .glass(shape, tone, selected)
        .combinedClickable(enabled = enabled, role = Role.Button, onLongClick = onLongClick, onClick = onClick)
        .semantics { contentDescription = description; this.selected = selected }
        .alpha(if (enabled) 1f else .45f)
        .padding(horizontal = if (label == null) 0.dp else 16.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tone.content, modifier = Modifier.size(22.dp))
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(label, color = tone.content, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, style = TextStyle(shadow = tone.halo))
        }
    }
}
