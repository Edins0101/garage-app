package com.garaje.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Paleta "noche violeta": fondo azul-morado muy oscuro con montañas,
 * mosaicos violeta y hojas blancas con acentos morados.
 */
object Palette {
    val NightTop = Color(0xFF120A2B)
    val NightMid = Color(0xFF1C1145)
    val NightBottom = Color(0xFF2A1A63)
    val MountainFar = Color(0xFF2B1D6B)
    val MountainNear = Color(0xFF3A2790)
    val Star = Color(0xFF9C8CFF)

    val Violet = Color(0xFF6A4BE0)
    val VioletDeep = Color(0xFF4A31B6)
    val VioletBright = Color(0xFF8E78FF)
    val Glass = Color(0x332E1E78)
    val GlassStrong = Color(0x664A33B8)
    val GlassBorder = Color(0x40A898FF)

    val Sheet = Color(0xFFFFFFFF)
    val Lavender = Color(0xFFE6E0FF)
    val LavenderSoft = Color(0xFFF3F0FF)
    val Ink = Color(0xFF1B1440)
    val InkSoft = Color(0xFF6E6890)
    val Divider = Color(0xFFE4E1F0)
    val DayBox = Color(0xFFE2DFEA)

    val OnDark = Color(0xFFFFFFFF)
    val OnDarkSoft = Color(0xFFC7BFF0)
    val Success = Color(0xFF34C77B)
    val Danger = Color(0xFFE5486A)
}

private val ColorScheme = darkColorScheme(
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
fun GarajeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = AppTypography, content = content)
}
