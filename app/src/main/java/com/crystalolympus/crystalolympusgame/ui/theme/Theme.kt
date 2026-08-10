package com.crystalolympus.crystalolympusgame.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Palette taken from the game's art direction: night-blue panels, gold trim, crystal glow. */
object OlympusColors {
    val Night = Color(0xFF05070F)
    val DeepBlue = Color(0xFF0B1428)
    val PanelTop = Color(0xFF14224A)
    val PanelBottom = Color(0xFF0A1330)
    val PanelBorder = Color(0xFF3A5C9E)

    val Gold = Color(0xFFF2C14E)
    val GoldBright = Color(0xFFFFE9A8)
    val GoldDark = Color(0xFF8A5F16)

    val Sky = Color(0xFF6FD3FF)
    val Lightning = Color(0xFF4FA8FF)
    val LightningDeep = Color(0xFF1B4FCC)

    val Ember = Color(0xFFFF6B3D)
    val Nature = Color(0xFF57D68A)
    val Magic = Color(0xFFB47BFF)
    val Divine = Color(0xFFFFD34F)

    val TextPrimary = Color(0xFFF2F6FF)
    val TextSecondary = Color(0xFF9FB3D9)
    val TextMuted = Color(0xFF6B7FA6)

    val Danger = Color(0xFFE23B4B)
    val Success = Color(0xFF3FD37A)

    val Scrim = Color(0xCC030610)
}

/** Gradients reused across panels, buttons and bars. */
object OlympusBrushes {
    val Panel = Brush.verticalGradient(listOf(OlympusColors.PanelTop, OlympusColors.PanelBottom))
    val PanelRaised = Brush.verticalGradient(listOf(Color(0xFF1C2E60), Color(0xFF0D1738)))
    val GoldButton = Brush.verticalGradient(
        listOf(OlympusColors.GoldBright, OlympusColors.Gold, OlympusColors.GoldDark),
    )
    val BlueButton = Brush.verticalGradient(
        listOf(Color(0xFF3C7BE0), Color(0xFF1E49A8), Color(0xFF122D6B)),
    )
    val DangerButton = Brush.verticalGradient(
        listOf(Color(0xFFE8524F), Color(0xFFB4232B), Color(0xFF6E1218)),
    )
    val GoldTrim = Brush.horizontalGradient(
        listOf(OlympusColors.GoldDark, OlympusColors.GoldBright, OlympusColors.GoldDark),
    )
    val HealthBar = Brush.verticalGradient(listOf(Color(0xFFFF7A6E), Color(0xFFC01B27)))
    val EnergyBar = Brush.verticalGradient(listOf(Color(0xFF8FE0FF), Color(0xFF1E6FD8)))
    val ProgressBar = Brush.horizontalGradient(
        listOf(Color(0xFF7FE4FF), Color(0xFF3F9BFF), Color(0xFF9C7BFF)),
    )
}

private val olympusColorScheme = darkColorScheme(
    primary = OlympusColors.Gold,
    onPrimary = Color(0xFF2A1B00),
    secondary = OlympusColors.Lightning,
    onSecondary = OlympusColors.TextPrimary,
    background = OlympusColors.Night,
    onBackground = OlympusColors.TextPrimary,
    surface = OlympusColors.PanelTop,
    onSurface = OlympusColors.TextPrimary,
    error = OlympusColors.Danger,
)

private fun display(size: Int, weight: FontWeight = FontWeight.Black) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = 1.5.sp,
)

private val olympusTypography = Typography(
    displayLarge = display(40),
    displayMedium = display(30),
    headlineLarge = display(24),
    headlineMedium = display(19, FontWeight.ExtraBold),
    titleLarge = display(16, FontWeight.Bold),
    titleMedium = display(14, FontWeight.Bold),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.3.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp,
    ),
    labelLarge = display(13, FontWeight.Bold),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        letterSpacing = 0.6.sp,
    ),
)

@Composable
fun CrystalOlympusTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = olympusColorScheme,
        typography = olympusTypography,
        content = content,
    )
}
