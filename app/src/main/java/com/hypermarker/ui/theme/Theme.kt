package com.hypermarker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermarker.R

/**
 * Tokens del rediseño (docs/proposals/ui-redesign.md). La app es clara; el modo oscuro del sistema
 * usa una variante derivada. El lienzo de Flow es siempre oscuro (decisión 4) y usa [FlowInk].
 */
@Immutable
data class HmColors(
    val paper: Color, val surface: Color, val ink: Color, val onInk: Color,
    val accent: Color, val accentPressed: Color, val onAccent: Color,
    val muted: Color, val border: Color, val divider: Color, val mint: Color,
    val pin: Color, val pinSoft: Color, val danger: Color, val scrim: Color, val dark: Boolean,
)

val LightHm = HmColors(
    paper = Color(0xFFFFFDF7), surface = Color(0xFFFFFFFF), ink = Color(0xFF14201C), onInk = Color(0xFFFFFDF7),
    accent = Color(0xFF176B5B), accentPressed = Color(0xFF0F4D41), onAccent = Color(0xFFFFFFFF),
    muted = Color(0xFF56615C), border = Color(0xFFD9D5C8), divider = Color(0xFFECE9DE), mint = Color(0xFFE3F0EB),
    pin = Color(0xFFE5B62F), pinSoft = Color(0xFFFBE7A1), danger = Color(0xFFA3302A), scrim = Color(0x8C14201C), dark = false,
)

val DarkHm = HmColors(
    paper = Color(0xFF101815), surface = Color(0xFF18221E), ink = Color(0xFFEDF2EF), onInk = Color(0xFF14201C),
    accent = Color(0xFF89D5BC), accentPressed = Color(0xFF6CBFA4), onAccent = Color(0xFF0B2A22),
    muted = Color(0xFFA9B6B0), border = Color(0xFF3A4641), divider = Color(0xFF26312C), mint = Color(0xFF1F3A31),
    pin = Color(0xFFE5B62F), pinSoft = Color(0xFF4A3E14), danger = Color(0xFFFF8A80), scrim = Color(0xB3000000), dark = true,
)

/** Paleta fija del lienzo de Flow: siempre oscuro, independiente del tema del sistema. */
object FlowInk {
    val canvas = Color(0xFF14201C)
    val raised = Color(0xFF1E2A26)
    val outline = Color(0xFF3A4641)
    val grid = Color(0xFF33403B)
    val mint = Color(0xFF89D5BC)
    val text = Color(0xFFFFFDF7)
    val soft = Color(0xFFD6DDD9)
    val pin = Color(0xFFE5B62F)
    val danger = Color(0xFFFF8A80)
    /** Colores de anillo elegibles para un punto con foto (pantalla 12). */
    val rings = listOf(0xFF89D5BC, 0xFFE5B62F, 0xFFE9826E, 0xFF5E86D8, 0xFFFFFDF7)
}

val LocalHm = staticCompositionLocalOf { LightHm }

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(R.font.inter_variable, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
@OptIn(ExperimentalTextApi::class)
private fun grotesk(weight: Int) = Font(R.font.space_grotesk_variable, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))
val SpaceGrotesk = FontFamily(grotesk(500), grotesk(600), grotesk(700))

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.inter() = copy(fontFamily = Inter)
    fun TextStyle.display() = copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold)
    return Typography(
        displayLarge = base.displayLarge.display(), displayMedium = base.displayMedium.display(), displaySmall = base.displaySmall.display(),
        headlineLarge = base.headlineLarge.display(), headlineMedium = base.headlineMedium.display().copy(fontSize = 28.sp, lineHeight = 34.sp),
        headlineSmall = base.headlineSmall.display().copy(fontSize = 22.sp, lineHeight = 28.sp),
        titleLarge = base.titleLarge.display().copy(fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = base.titleMedium.inter().copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = base.titleSmall.inter().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.inter().copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.inter().copy(fontSize = 15.sp, lineHeight = 21.sp),
        bodySmall = base.bodySmall.inter().copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.inter().copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        labelMedium = base.labelMedium.inter().copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
        labelSmall = base.labelSmall.inter().copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.sp),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
)

fun colorScheme(hm: HmColors) = if (hm.dark) darkColorScheme(
    primary = hm.accent, onPrimary = hm.onAccent, primaryContainer = hm.mint, onPrimaryContainer = hm.ink,
    secondary = hm.muted, secondaryContainer = hm.mint, onSecondaryContainer = hm.ink, tertiary = hm.pin, tertiaryContainer = hm.pinSoft,
    background = hm.paper, onBackground = hm.ink, surface = hm.paper, onSurface = hm.ink, surfaceVariant = hm.surface,
    onSurfaceVariant = hm.muted, outline = hm.border, outlineVariant = hm.divider, error = hm.danger,
    surfaceContainerLowest = hm.paper, surfaceContainerLow = hm.surface, surfaceContainer = hm.surface,
    surfaceContainerHigh = hm.surface, surfaceContainerHighest = hm.surface, inverseSurface = hm.ink, inverseOnSurface = hm.onInk,
) else lightColorScheme(
    primary = hm.accent, onPrimary = hm.onAccent, primaryContainer = hm.mint, onPrimaryContainer = hm.ink,
    secondary = hm.muted, secondaryContainer = hm.mint, onSecondaryContainer = hm.ink, tertiary = hm.pin, tertiaryContainer = hm.pinSoft,
    background = hm.paper, onBackground = hm.ink, surface = hm.paper, onSurface = hm.ink, surfaceVariant = hm.surface,
    onSurfaceVariant = hm.muted, outline = hm.border, outlineVariant = hm.divider, error = hm.danger,
    surfaceContainerLowest = hm.surface, surfaceContainerLow = hm.paper, surfaceContainer = hm.paper,
    surfaceContainerHigh = hm.paper, surfaceContainerHighest = hm.surface, inverseSurface = hm.ink, inverseOnSurface = hm.onInk,
)

@Composable
fun HyperMarkerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val hm = if (dark) DarkHm else LightHm
    CompositionLocalProvider(LocalHm provides hm) {
        MaterialTheme(colorScheme = colorScheme(hm), typography = typography(), shapes = shapes, content = content)
    }
}

/** Tema de las hojas sobre el lienzo de Flow: claras como en las maquetas, aunque el sistema esté en oscuro. */
@Composable
fun LightSheetTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHm provides LightHm) {
        MaterialTheme(colorScheme = colorScheme(LightHm), typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
    }
}
