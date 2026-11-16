package com.hypermarker.markdown

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun TextFieldValue.edit() = TextEdit(text, selection.min, selection.max)
fun TextEdit.value() = TextFieldValue(text, TextRange(start, end))

/** Hoja de papel monocroma: blanca por defecto y oscura opcional. */
data class SheetColors(val background: Color, val content: Color, val muted: Color, val divider: Color, val selected: Color, val accent: Color) {
    companion object {
        /** Papel y tinta del rediseño. */
        val Light = SheetColors(Color(0xFFFFFDF7), Color(0xFF14201C), Color(0xFF56615C), Color(0xFFECE9DE), Color(0xFFECE9DE), Color(0xFF176B5B))
        val Dark = SheetColors(Color(0xFF121212), Color(0xFFEDEDED), Color(0xFF9C9C9C), Color(0xFF2B2B2B), Color(0xFF262626), Color(0xFF89D5BC))
    }
}

val SHEET_FONTS = listOf("sans" to "Sans", "serif" to "Serif", "mono" to "Mono")
fun sheetFont(name: String) = when (name) { "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace; else -> FontFamily.SansSerif }

class SheetStyleStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("markdown_sheet", Context.MODE_PRIVATE)
    var dark: Boolean
        get() = preferences.getBoolean("dark", false)
        set(value) { preferences.edit().putBoolean("dark", value).apply() }
    var font: String
        get() = preferences.getString("font", "sans")?.takeIf { value -> SHEET_FONTS.any { it.first == value } } ?: "sans"
        set(value) { preferences.edit().putString("font", value).apply() }
}

/** Comandos de la barra superior; `level` sólo aplica a títulos (0 = párrafo). */
sealed interface FormatCommand {
    data class Heading(val level: Int) : FormatCommand
    data class Inline(val name: String) : FormatCommand
    data class Block(val name: String) : FormatCommand
    data object Link : FormatCommand
}

/** Aplica el comando en Source sobre la selección actual. Los enlaces requieren URL y se resuelven aparte. */
fun FormatCommand.applyTo(edit: TextEdit): TextEdit = when (this) {
    is FormatCommand.Heading -> MarkdownFormatter.heading(edit, level)
    is FormatCommand.Inline -> when (name) { "bold" -> MarkdownFormatter.bold(edit); "italic" -> MarkdownFormatter.italic(edit); "strike" -> MarkdownFormatter.strike(edit); else -> MarkdownFormatter.code(edit) }
    is FormatCommand.Block -> when (name) {
        "bullets" -> MarkdownFormatter.bullets(edit); "numbered" -> MarkdownFormatter.numbered(edit); "quote" -> MarkdownFormatter.quote(edit)
        "codeblock" -> MarkdownFormatter.codeBlock(edit); "table" -> MarkdownFormatter.table(edit); else -> MarkdownFormatter.rule(edit)
    }
    FormatCommand.Link -> edit
}

/** Siempre visibles en la barra inferior. */
private val TOOLS: List<Triple<FormatCommand, ImageVector, String>> = listOf(
    Triple(FormatCommand.Inline("bold"), Icons.Outlined.FormatBold, "Negrita"),
    Triple(FormatCommand.Inline("italic"), Icons.Outlined.FormatItalic, "Cursiva"),
    Triple(FormatCommand.Link, Icons.Outlined.Link, "Enlace"),
    Triple(FormatCommand.Block("bullets"), Icons.AutoMirrored.Outlined.FormatListBulleted, "Lista con viñetas"),
    Triple(FormatCommand.Block("quote"), Icons.Outlined.FormatQuote, "Cita"),
    Triple(FormatCommand.Inline("code"), Icons.Outlined.Code, "Código en línea"),
)
/** Detrás del botón «+». */
private val MORE: List<Triple<FormatCommand, ImageVector, String>> = listOf(
    Triple(FormatCommand.Inline("strike"), Icons.Outlined.FormatStrikethrough, "Tachado"),
    Triple(FormatCommand.Block("numbered"), Icons.Outlined.FormatListNumbered, "Lista numerada"),
    Triple(FormatCommand.Block("codeblock"), Icons.Outlined.DataObject, "Bloque de código"),
    Triple(FormatCommand.Block("table"), Icons.Outlined.TableChart, "Insertar tabla"),
    Triple(FormatCommand.Block("rule"), Icons.Outlined.HorizontalRule, "Separador"),
)

@Composable
fun MarkdownToolbar(colors: SheetColors, font: String, enabled: Boolean, onFont: (String) -> Unit, onCommand: (FormatCommand) -> Unit,
                    onMenuExpandedChange: (Boolean) -> Unit = {}) {
    var styles by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    LaunchedEffect(styles, more) { onMenuExpandedChange(styles || more) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(Modifier.size(44.dp).clip(CircleShape).background(colors.selected).clickable(enabled = enabled, role = Role.Button) { styles = true }
                    .semantics { contentDescription = "Estilo y tipo de letra" }, contentAlignment = Alignment.Center) {
                    Text("Aa", color = colors.content, fontFamily = sheetFont(font), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                DropdownMenu(styles, { styles = false }, containerColor = colors.background) {
                    listOf(0 to "Texto normal", 1 to "Título 1", 2 to "Título 2", 3 to "Título 3").forEach { (level, label) ->
                        DropdownMenuItem(text = { Text(label, color = colors.content, fontSize = when (level) { 1 -> 22.sp; 2 -> 19.sp; 3 -> 17.sp; else -> 15.sp }, fontWeight = if (level > 0) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { styles = false; onCommand(FormatCommand.Heading(level)) })
                    }
                    HorizontalDivider(color = colors.divider)
                    Text("TIPO DE LETRA", color = colors.muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    SHEET_FONTS.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label, color = colors.content, fontFamily = sheetFont(value)) }, onClick = { styles = false; onFont(value) },
                            trailingIcon = { if (value == font) Icon(Icons.Outlined.Check, "Seleccionado", tint = colors.content) })
                    }
                }
            }
            TOOLS.forEach { (command, icon, label) ->
                IconButton(onClick = { onCommand(command) }, enabled = enabled, colors = IconButtonDefaults.iconButtonColors(contentColor = colors.content, disabledContentColor = colors.muted)) { Icon(icon, label) }
            }
        }
        Box {
            Box(Modifier.size(44.dp).clip(CircleShape).background(colors.content).clickable(enabled = enabled, role = Role.Button) { more = true }
                .semantics { contentDescription = "Más formatos" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Add, null, tint = colors.background)
            }
            DropdownMenu(more, { more = false }, containerColor = colors.background) {
                MORE.forEach { (command, icon, label) ->
                    DropdownMenuItem(text = { Text(label, color = colors.content) }, leadingIcon = { Icon(icon, null, tint = colors.content) }, onClick = { more = false; onCommand(command) })
                }
            }
        }
    }
}
