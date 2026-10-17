package com.arianna.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Colores principales que el usuario puede elegir.
 * `hue`: tono HSL en grados. `darken`: cuánto oscurecer el color principal, porque el turquesa,
 * el verde o el ámbar se perciben más claros que el violeta y el texto blanco encima perdería contraste.
 */
enum class AccentColor(val label: String, val hue: Float, val darken: Float = 0f) {
    VIOLET("Violeta", 252f),
    BLUE("Azul", 215f, darken = 0.08f),
    TEAL("Turquesa", 180f, darken = 0.22f),
    GREEN("Verde", 150f, darken = 0.22f),
    AMBER("Ámbar", 35f, darken = 0.16f),
    CORAL("Coral", 8f, darken = 0.08f),
    PINK("Rosa", 330f, darken = 0.08f),
}

/**
 * Paleta derivada de un solo tono. Los valores de saturación y luminosidad reproducen
 * el diseño "noche violeta" original; al cambiar el tono se obtiene el mismo estilo en otro color.
 */
class AccentPalette(private val hue: Float, darken: Float = 0f) {
    private fun hsl(s: Float, l: Float, alpha: Float = 1f) = Color.hsl(hue, s, l, alpha)

    val nightTop = hsl(0.62f, 0.10f)
    val nightMid = hsl(0.60f, 0.17f)
    val nightBottom = hsl(0.58f, 0.25f)
    val mountainFar = hsl(0.57f, 0.27f)
    val mountainNear = hsl(0.57f, 0.36f)
    val star = hsl(1f, 0.77f)

    val accent = hsl(0.71f, 0.59f - darken)
    val accentDeep = hsl(0.57f, 0.45f - darken * 0.7f)
    val accentBright = hsl(1f, 0.74f)
    val glass = hsl(0.60f, 0.29f, 0.20f)
    val glassStrong = hsl(0.57f, 0.45f, 0.40f)
    val glassBorder = hsl(1f, 0.80f, 0.25f)

    val lavender = hsl(1f, 0.94f)
    val lavenderSoft = hsl(1f, 0.97f)
    val ink = hsl(0.52f, 0.16f)
    val inkSoft = hsl(0.16f, 0.49f)
    val divider = hsl(0.30f, 0.91f)
    val dayBox = hsl(0.16f, 0.90f)
    val onDarkSoft = hsl(0.62f, 0.84f)
}

/** Color elegido actualmente. Es estado de Compose: al cambiarlo, toda la UI se redibuja. */
object ThemeState {
    var accent: AccentColor by mutableStateOf(AccentColor.VIOLET)
    private val cache = mutableMapOf<AccentColor, AccentPalette>()
    val palette: AccentPalette get() = cache.getOrPut(accent) { AccentPalette(accent.hue, accent.darken) }
}

/*
 * Paleta de la app. Los nombres "Violet…" se conservan por compatibilidad, pero ahora
 * siguen el color principal que elija el usuario (ver ThemeState).
 */
object Palette {
    private val p get() = ThemeState.palette

    val NightTop: Color get() = p.nightTop
    val NightMid: Color get() = p.nightMid
    val NightBottom: Color get() = p.nightBottom
    val MountainFar: Color get() = p.mountainFar
    val MountainNear: Color get() = p.mountainNear
    val Star: Color get() = p.star

    val Violet: Color get() = p.accent
    val VioletDeep: Color get() = p.accentDeep
    val VioletBright: Color get() = p.accentBright
    val Glass: Color get() = p.glass
    val GlassStrong: Color get() = p.glassStrong
    val GlassBorder: Color get() = p.glassBorder

    val Sheet = Color(0xFFFFFFFF)
    val Lavender: Color get() = p.lavender
    val LavenderSoft: Color get() = p.lavenderSoft
    val Ink: Color get() = p.ink
    val InkSoft: Color get() = p.inkSoft
    val Divider: Color get() = p.divider
    val DayBox: Color get() = p.dayBox

    val OnDark = Color(0xFFFFFFFF)
    val OnDarkSoft: Color get() = p.onDarkSoft
    val Success = Color(0xFF34C77B)
    /** Días de pago en el calendario (como la estrella del logo). */
    val Gold = Color(0xFFF2B33D)
    val Danger = Color(0xFFE5486A)
}

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
)

@Composable
fun AriannaTheme(content: @Composable () -> Unit) {
    // Se lee ThemeState aquí: al cambiar el color, los componentes de Material también se actualizan.
    val colorScheme = darkColorScheme(
        primary = Palette.Violet,
        onPrimary = Palette.OnDark,
        secondary = Palette.VioletBright,
        background = Palette.NightTop,
        onBackground = Palette.OnDark,
        surface = Palette.Sheet,
        onSurface = Palette.Ink,
        surfaceVariant = Palette.LavenderSoft,
        onSurfaceVariant = Palette.InkSoft,
        error = Palette.Danger,
        outline = Palette.Divider,
    )
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}
