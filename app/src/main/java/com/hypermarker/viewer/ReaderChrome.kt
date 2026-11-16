package com.hypermarker.viewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val DRAW_COLORS = listOf("#176b5b" to "Verde", "#334c8f" to "Azul", "#d54f46" to "Rojo", "#e5b62f" to "Amarillo", "#ff8c1a" to "Naranja", "#8e44ad" to "Morado", "#202020" to "Negro", "#ffffff" to "Blanco")
val DRAW_TOOLS = listOf("pen" to "Pluma", "highlighter" to "Resaltador", "eraser" to "Borrador")

fun ReaderAction.icon(playing: Boolean = false): ImageVector = when (this) {
    ReaderAction.AUTOSCROLL -> if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow
    ReaderAction.ZOOM_FIT -> Icons.Outlined.ZoomIn
    ReaderAction.ZOOM_RESET -> Icons.Outlined.ZoomOut
    ReaderAction.SETTINGS -> Icons.Outlined.Settings
    ReaderAction.RELOAD -> Icons.Outlined.Refresh
    ReaderAction.BACK -> Icons.AutoMirrored.Outlined.ArrowBack
    ReaderAction.FORWARD -> Icons.AutoMirrored.Outlined.ArrowForward
    ReaderAction.FULLSCREEN -> Icons.Outlined.Fullscreen
    ReaderAction.NOTES -> Icons.AutoMirrored.Outlined.StickyNote2
    ReaderAction.PIN -> Icons.Outlined.PushPin
    ReaderAction.EDIT_TEXT -> Icons.Outlined.TextFields
    ReaderAction.DELETE_PINS -> Icons.Outlined.LinkOff
    ReaderAction.RESTORE_TEXT -> Icons.Outlined.Restore
    ReaderAction.COLOR -> Icons.Outlined.Palette
    ReaderAction.WIDTH -> Icons.Outlined.LineWeight
    ReaderAction.TOOL -> Icons.Outlined.Brush
    ReaderAction.STABILIZER -> Icons.Outlined.Gesture
    ReaderAction.CLEAR -> Icons.Outlined.DeleteSweep
}

fun toolIcon(tool: String): ImageVector = when (tool) { "highlighter" -> Icons.Outlined.BorderColor; "eraser" -> Icons.Outlined.AutoFixNormal; else -> Icons.Outlined.Create }

/** Barra segmentada de tinta (pantalla 5): legible sobre cualquier página sin depender del muestreo. */
@Composable
fun ModeBar(mode: String, tone: GlassTone, annotationsEnabled: Boolean, onMode: (String) -> Unit, modifier: Modifier = Modifier) {
    InkBar(modifier) {
        listOf(Triple("VIEW", "Ver", Icons.Outlined.Visibility), Triple("EDIT", "Editar", Icons.Outlined.Edit), Triple("DRAW", "Dibujar", Icons.Outlined.Draw)).forEach { (key, label, icon) ->
            InkSegment(icon, label, "Modo $label", selected = mode == key, enabled = key == "VIEW" || annotationsEnabled) { onMode(key) }
        }
    }
}

@Composable
fun QuickBar(playing: Boolean, pinArmed: Boolean, drawing: Boolean, tone: GlassTone, annotationsEnabled: Boolean,
             onAutoscroll: () -> Unit, onPin: () -> Unit, onDraw: () -> Unit, modifier: Modifier = Modifier) {
    InkBar(modifier) {
        InkSegment(ReaderAction.AUTOSCROLL.icon(playing), "Autoscroll", "Autoscroll", selected = playing, onClick = onAutoscroll)
        InkSegment(ReaderAction.PIN.icon(), "Pin", "Poner pin: toca un elemento", selected = pinArmed, enabled = annotationsEnabled, onClick = onPin)
        InkSegment(Icons.Outlined.Draw, "Dibujar", "Dibujo básico", selected = drawing, enabled = annotationsEnabled, onClick = onDraw)
    }
}

/** Contenedor en píldora de tinta para los segmentos del lector. */
@Composable
fun InkBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.clip(RoundedCornerShape(50)).background(INK).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun InkSegment(icon: ImageVector, label: String, description: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).background(if (selected) PAPER else Color.Transparent)
        .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
        .semantics { contentDescription = description; this.selected = selected }
        .alpha(if (enabled) 1f else .45f).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (selected) INK else PAPER)
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (selected) INK else PAPER, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

/** Botón redondo de tinta del cromo del lector. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InkCircle(icon: ImageVector, description: String, modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    Box(modifier.size(48.dp).clip(CircleShape).background(if (selected) PAPER else INK).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description; this.selected = selected }.alpha(if (enabled) 1f else .45f), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (selected) INK else PAPER)
    }
}

/** Píldora de tinta con icono y texto («Notas y cambios»). */
@Composable
fun InkPill(icon: ImageVector, label: String, iconTint: Color = PIN, onClick: () -> Unit) {
    Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).background(INK).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = iconTint)
        Text(label, Modifier.padding(start = 8.dp), color = PAPER, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Aviso en píldora: claro con borde (consejos) o acento (selección activa). */
@Composable
fun ReaderNotice(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(text, modifier.clip(RoundedCornerShape(50)).background(if (accent) ACCENT else PAPER).border(1.dp, if (accent) ACCENT else BORDER, RoundedCornerShape(50))
        .padding(horizontal = 16.dp, vertical = 9.dp), color = if (accent) Color.White else INK, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

internal val INK = Color(0xFF14201C)
internal val PAPER = Color(0xFFFFFDF7)
internal val PIN = Color(0xFFE5B62F)
internal val ACCENT = Color(0xFF176B5B)
internal val BORDER = Color(0xFFD9D5C8)

/** Cruceta: cuatro acciones alrededor del punto mantenido. Mantener una opción permite reasignarla. */
@Composable
fun CrossMenu(request: CrossRequest, actions: List<ReaderAction>, tone: GlassTone, playing: Boolean, onAction: (ReaderAction) -> Unit, onReassign: (Int) -> Unit, onDismiss: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }.semantics { contentDescription = "Cruceta de acciones" }) {
        val density = LocalDensity.current
        val radius = 78.dp; val item = 72.dp; val reach = radius + item / 2 + 4.dp
        val cx = with(density) { request.x.toDp() }.coerceIn(reach, (maxWidth - reach).coerceAtLeast(reach))
        val cy = with(density) { request.y.toDp() }.coerceIn(reach, (maxHeight - reach).coerceAtLeast(reach))
        Box(Modifier.offset(cx - 8.dp, cy - 8.dp).size(16.dp).clip(CircleShape).background(Color.White).border(2.dp, INK, CircleShape))
        val directions = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)
        actions.forEachIndexed { slot, action ->
            val (dx, dy) = directions[slot]
            val enabled = action.available && (!action.target || request.hasTarget)
            CrossItem(action, tone, enabled, playing, Modifier.offset(cx + radius * dx - item / 2, cy + radius * dy - item / 2).size(item),
                onClick = { onAction(action) }, onLongClick = { onReassign(slot) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CrossItem(action: ReaderAction, tone: GlassTone, enabled: Boolean, playing: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    // Tarjetas sólidas (pantalla 6); la acción Pin destaca en amarillo.
    val pin = action == ReaderAction.PIN
    Column(modifier.shadow(6.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(if (pin) PIN else Color.White)
        .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClickLabel = action.label, onClick = onClick)
        .semantics { contentDescription = action.label + if (enabled) "" else " (no disponible)" }
        .alpha(if (enabled) 1f else .42f).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(action.icon(playing), null, tint = INK, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(action.label, color = INK, fontSize = 11.sp, lineHeight = 12.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
    }
}

/** Selector de posición: muestra la cruceta actual y coloca `action` en la posición elegida. */
@Composable
fun SlotPicker(title: String, cross: List<ReaderAction>, highlight: ReaderAction?, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Toca la posición de la cruceta. La acción desplazada vuelve al menú.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            @Composable fun slot(index: Int) = OutlinedButton(onClick = { onPick(index) }, modifier = Modifier.width(118.dp), contentPadding = PaddingValues(6.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(cross[index].icon(), null, Modifier.size(20.dp))
                    Text(cross[index].label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (cross[index] == highlight) FontWeight.Bold else FontWeight.Normal)
                    Text(CrossLayout.SLOTS[index], fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
            slot(0)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { slot(3); slot(1) }
            slot(2)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

/** Lista de acciones del modo para reemplazar una posición desde la propia cruceta. */
@Composable
fun ActionPicker(mode: String, slot: Int, cross: List<ReaderAction>, onPick: (ReaderAction) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Posición ${CrossLayout.SLOTS[slot].lowercase()}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            CrossLayout.pool(mode).forEach { action ->
                val index = cross.indexOf(action)
                ListItem(headlineContent = { Text(action.label) }, leadingContent = { Icon(action.icon(), null) },
                    supportingContent = { Text(when { index == slot -> "Asignada aquí"; index >= 0 -> "Intercambiar con ${CrossLayout.SLOTS[index].lowercase()}"; !action.available -> "Próximamente"; else -> "Ahora en el menú" }) },
                    modifier = Modifier.clickable(enabled = index != slot) { onPick(action) })
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }, dismissButton = { TextButton(onClick = onReset) { Text("Restablecer") } })
}

/** Panel de cristal anclado bajo el botón de opciones. */
@Composable
fun MenuPanel(tone: GlassTone, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Cerrar menú", onClick = onDismiss)) {
        Column(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).padding(top = 68.dp, end = 12.dp, start = 12.dp)
            .widthIn(max = 340.dp).heightIn(max = maxHeight * .78f)
            .glass(RoundedCornerShape(26.dp), tone, solid = true)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .verticalScroll(rememberScrollState()).padding(vertical = 10.dp), content = content)
    }
}

@Composable
fun MenuHeader(text: String, tone: GlassTone) {
    Text(text.uppercase(), color = tone.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
fun MenuRow(icon: ImageVector, label: String, tone: GlassTone, supporting: String? = null, enabled: Boolean = true, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).alpha(if (enabled) 1f else .45f).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tone.content, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(label, color = tone.content, fontSize = 15.sp)
            if (supporting != null) Text(supporting, color = tone.muted, fontSize = 12.sp)
        }
        trailing?.invoke()
    }
}

/** Paneles de Dibujar: color, grosor y herramienta, sobre la barra de modos. */
@Composable
fun DrawPanel(kind: ReaderAction, tone: GlassTone, color: String, width: Float, tool: String, onColor: (String) -> Unit, onWidth: (Float) -> Unit, onTool: (String) -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Cerrar panel", onClick = onDismiss)) {
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 84.dp).widthIn(max = 420.dp)
            .glass(RoundedCornerShape(28.dp), tone, solid = true)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(18.dp)) {
            Text(kind.label, color = tone.content, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            when (kind) {
                ReaderAction.COLOR -> FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DRAW_COLORS.forEach { (value, label) ->
                        val parsed = Color(android.graphics.Color.parseColor(value))
                        Box(Modifier.size(44.dp).clip(CircleShape).border(if (value == color) 3.dp else 1.dp, if (value == color) tone.content else tone.muted, CircleShape)
                            .padding(5.dp).clip(CircleShape).let { it.then(Modifier.semantics { contentDescription = "Color $label" }) }
                            .clickable(role = Role.Button) { onColor(value) }) {
                            Canvas(Modifier.fillMaxSize()) { drawCircle(parsed) }
                        }
                    }
                }
                ReaderAction.WIDTH -> {
                    Canvas(Modifier.fillMaxWidth().height(36.dp)) {
                        drawLine(Color(android.graphics.Color.parseColor(color)).copy(alpha = if (tool == "highlighter") .35f else 1f), Offset(24f, size.height / 2), Offset(size.width - 24f, size.height / 2),
                            strokeWidth = (if (tool == "highlighter") width * 3 else width).dp.toPx(), cap = StrokeCap.Round)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(width, onWidth, valueRange = 1f..15f, colors = tone.sliderColors(), modifier = Modifier.weight(1f).semantics { contentDescription = "Grosor del trazo" })
                        Text("${width.toInt()} px", color = tone.content, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DRAW_TOOLS.forEach { (value, label) -> GlassButton(toolIcon(value), label, tone, label = label, selected = tool == value) { onTool(value) } }
                }
            }
        }
    }
}
