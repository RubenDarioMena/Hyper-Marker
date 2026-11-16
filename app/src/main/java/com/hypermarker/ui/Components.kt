package com.hypermarker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermarker.ui.theme.LocalHm

/** Etiqueta de sección en mayúsculas («CONTINUAR», «BIBLIOTECA»…). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier.semantics { heading() }, color = LocalHm.current.muted, style = MaterialTheme.typography.labelSmall)
}

/** Chip en píldora: activo en tinta, inactivo con contorno. */
@Composable
fun HmChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val hm = LocalHm.current
    Row(modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50))
        .background(if (selected) hm.ink else Color.Transparent)
        .border(1.dp, if (selected) hm.ink else hm.border, RoundedCornerShape(50))
        .clickable(role = Role.Tab, onClick = onClick)
        .semantics { this.selected = selected }
        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = if (selected) hm.onInk else hm.ink); Spacer(Modifier.width(6.dp)) }
        Text(label, color = if (selected) hm.onInk else hm.ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

/** Baldosa cuadrada redondeada con icono (filas de lista, cabeceras de hoja). */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 44.dp, background: Color = LocalHm.current.mint, tint: Color = LocalHm.current.ink) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * .3f)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * .5f), tint = tint)
    }
}

/** Fila de lista con baldosa, título, subtítulo y acción final. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HmRow(title: String, subtitle: String?, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null,
          trailing: (@Composable () -> Unit)? = null, enabled: Boolean = true, onLongClick: (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val hm = LocalHm.current
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp)
        .let { if (onClick != null) it.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick) else it }
        .alpha(if (enabled) 1f else .55f)
        .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) { leading(); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = hm.ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = hm.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun HmDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier, color = LocalHm.current.divider)

/** Acción principal en píldora con el acento. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val hm = LocalHm.current
    Button(onClick, modifier.heightIn(min = 52.dp), enabled = enabled, shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = hm.accent, contentColor = hm.onAccent,
            disabledContainerColor = hm.accent.copy(alpha = .35f), disabledContentColor = hm.onAccent.copy(alpha = .8f)),
        contentPadding = PaddingValues(horizontal = 24.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Acción secundaria con contorno. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color? = null) {
    val hm = LocalHm.current
    OutlinedButton(onClick, modifier.heightIn(min = 52.dp), enabled = enabled, shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, hm.border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = hm.surface, contentColor = color ?: hm.ink)) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Botón redondo de 44–48 dp (cerrar hoja, crear carpeta…). */
@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp,
                    background: Color = LocalHm.current.divider, tint: Color = LocalHm.current.ink, outlined: Boolean = false, enabled: Boolean = true) {
    val hm = LocalHm.current
    Box(modifier.size(size).clip(CircleShape).background(background)
        .let { if (outlined) it.border(1.dp, hm.border, CircleShape) else it }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }
        .alpha(if (enabled) 1f else .45f), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
    }
}

/** Acción con icono y texto debajo (fila de acciones de una tarjeta). */
@Composable
fun StackedAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = LocalHm.current.ink, enabled: Boolean = true) {
    Column(modifier.widthIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .alpha(if (enabled) 1f else .4f).padding(horizontal = 2.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(22.dp), tint = color)
        Text(label, Modifier.padding(top = 4.dp), color = color, fontSize = 11.5.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Control segmentado en píldora (Ver/Editar/Dibujar, Visual/Fuente). */
@Composable
fun Segmented(options: List<Pair<String, ImageVector?>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
              container: Color = LocalHm.current.divider, active: Color = LocalHm.current.ink, onActive: Color = LocalHm.current.onInk,
              content: Color = LocalHm.current.ink, height: Dp = 44.dp) {
    Row(modifier.clip(RoundedCornerShape(50)).background(container).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { index, (label, icon) ->
            val on = index == selected
            Row(Modifier.heightIn(min = height).clip(RoundedCornerShape(50)).background(if (on) active else Color.Transparent)
                .clickable(role = Role.Tab, onClick = { onSelect(index) }).semantics { this.selected = on }
                .padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = if (on) onActive else content); Spacer(Modifier.width(8.dp)) }
                Text(label, color = if (on) onActive else content, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

/** Aviso transitorio en píldora oscura. */
@Composable
fun Toast(text: String, modifier: Modifier = Modifier, background: Color = LocalHm.current.ink, color: Color = LocalHm.current.onInk) {
    Text(text, modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 18.dp, vertical = 10.dp),
        color = color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
}

/**
 * Hoja inferior del rediseño: papel, esquinas de 28 dp, asa y cabecera con cierre.
 * `header` sustituye la cabecera estándar (título + cerrar) cuando se necesita una propia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HmSheet(onDismiss: () -> Unit, title: String? = null, modifier: Modifier = Modifier, fullHeight: Boolean = false,
            header: (@Composable () -> Unit)? = null, footer: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val hm = LocalHm.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = hm.paper, contentColor = hm.ink, scrimColor = hm.scrim,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(hm.border)) }) {
        Column(modifier.fillMaxWidth().let { if (fullHeight) it.fillMaxHeight(.94f) else it }) {
            if (header != null) header()
            else if (title != null) Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge, color = hm.ink)
                RoundIconButton(Icons.Outlined.Close, "Cerrar", onDismiss)
            }
            Column(Modifier.weight(1f, fill = false), content = content)
            if (footer != null) { HmDivider(); Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) { footer() } }
        }
    }
}

/** Diálogo de confirmación con el estilo del rediseño. */
@Composable
fun HmConfirm(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    val hm = LocalHm.current
    AlertDialog(onDismissRequest = onDismiss, containerColor = hm.paper, shape = RoundedCornerShape(28.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) }, text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = hm.muted) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = if (danger) hm.danger else hm.accent, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = hm.ink) } })
}
