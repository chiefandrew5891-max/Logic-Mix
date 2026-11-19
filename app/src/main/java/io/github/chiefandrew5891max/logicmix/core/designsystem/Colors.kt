package io.github.chiefandrew5891max.logicmix.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Raw palette: used only to build the color schemes below, never directly by screens.
private object Palette {
    val Navy950 = Color(0xFF061430)
    val Navy900 = Color(0xFF0A1F44)
    val Navy800 = Color(0xFF112B5A)
    val Navy700 = Color(0xFF1B3A73)
    val Violet400 = Color(0xFF9B7BFF)
    val Violet600 = Color(0xFF6C3FE0)
    val Violet700 = Color(0xFF5530BF)
    val Teal300 = Color(0xFF3EE6D6)
    val Teal700 = Color(0xFF0B8F88)
    val Gold300 = Color(0xFFFFD66B)
    val Gold500 = Color(0xFFFFB300)
    val Gold700 = Color(0xFF9A6A00)
    val Red400 = Color(0xFFFF6B7D)
    val Red700 = Color(0xFFC62840)
    val Green400 = Color(0xFF3DDC97)
    val Green700 = Color(0xFF138A5B)
    val Mist50 = Color(0xFFF2F6FF)
    val Mist100 = Color(0xFFE3ECFB)
    val Ink900 = Color(0xFF0B1B3B)
}

/** Semantic colors that Material 3 roles do not cover. Read them through [LogicMixTokens.colors]. */
data class LogicMixColors(
    val gold: Color,
    val onGold: Color,
    val success: Color,
    val xpTrack: Color,
    val xpFill: Color,
    val glow: Color,
    val cardBorder: Color,
    val backgroundBrush: Brush,
    val primaryActionBrush: Brush,
    val rewardBrush: Brush,
    val cardBrush: Brush,
    val isDark: Boolean,
)

internal val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Palette.Violet400,
    onPrimary = Palette.Navy950,
    primaryContainer = Palette.Violet700,
    onPrimaryContainer = Palette.Mist50,
    secondary = Palette.Teal300,
    onSecondary = Palette.Navy950,
    secondaryContainer = Palette.Teal700,
    onSecondaryContainer = Palette.Mist50,
    tertiary = Palette.Gold500,
    onTertiary = Palette.Navy950,
    background = Palette.Navy900,
    onBackground = Palette.Mist50,
    surface = Palette.Navy800,
    onSurface = Palette.Mist50,
    surfaceVariant = Palette.Navy700,
    onSurfaceVariant = Palette.Mist100,
    outline = Palette.Violet400,
    error = Palette.Red400,
    onError = Palette.Navy950,
)

internal val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Palette.Violet600,
    onPrimary = Color.White,
    primaryContainer = Palette.Mist100,
    onPrimaryContainer = Palette.Violet700,
    secondary = Palette.Teal700,
    onSecondary = Color.White,
    secondaryContainer = Palette.Mist100,
    onSecondaryContainer = Palette.Navy900,
    tertiary = Palette.Gold700,
    onTertiary = Color.White,
    background = Palette.Mist50,
    onBackground = Palette.Ink900,
    surface = Color.White,
    onSurface = Palette.Ink900,
    surfaceVariant = Palette.Mist100,
    onSurfaceVariant = Palette.Navy700,
    outline = Palette.Violet600,
    error = Palette.Red700,
    onError = Color.White,
)

internal val DarkLogicMixColors = LogicMixColors(
    gold = Palette.Gold500,
    onGold = Palette.Navy950,
    success = Palette.Green400,
    xpTrack = Palette.Navy700,
    xpFill = Palette.Teal300,
    glow = Palette.Violet400.copy(alpha = 0.35f),
    cardBorder = Palette.Teal300.copy(alpha = 0.45f),
    backgroundBrush = Brush.verticalGradient(listOf(Palette.Navy800, Palette.Navy900, Palette.Navy950)),
    primaryActionBrush = Brush.verticalGradient(listOf(Palette.Gold300, Palette.Gold500)),
    rewardBrush = Brush.linearGradient(listOf(Palette.Violet600, Palette.Violet700)),
    cardBrush = Brush.verticalGradient(listOf(Palette.Navy700, Palette.Navy800)),
    isDark = true,
)

internal val LightLogicMixColors = LogicMixColors(
    gold = Palette.Gold700,
    onGold = Color.White,
    success = Palette.Green700,
    xpTrack = Palette.Mist100,
    xpFill = Palette.Teal700,
    glow = Palette.Violet600.copy(alpha = 0.18f),
    cardBorder = Palette.Violet600.copy(alpha = 0.25f),
    backgroundBrush = Brush.verticalGradient(listOf(Color.White, Palette.Mist50, Palette.Mist100)),
    primaryActionBrush = Brush.verticalGradient(listOf(Palette.Gold300, Palette.Gold500)),
    rewardBrush = Brush.linearGradient(listOf(Palette.Violet400, Palette.Violet600)),
    cardBrush = Brush.verticalGradient(listOf(Color.White, Palette.Mist50)),
    isDark = false,
)

internal val LocalLogicMixColors = staticCompositionLocalOf { DarkLogicMixColors }
