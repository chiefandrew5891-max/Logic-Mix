package com.akynex.logicmix.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LogicMixShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

/** Single entry point for design tokens. Screens must not hard-code colors, sizes or shapes. */
object LogicMixTokens {
    val spacingXs: Dp = 4.dp
    val spacingS: Dp = 8.dp
    val spacingM: Dp = 12.dp
    val spacing: Dp = 16.dp
    val spacingL: Dp = 24.dp
    val spacingXl: Dp = 32.dp
    val elevation: Dp = 2.dp
    val glowElevation: Dp = 12.dp
    val buttonHeight: Dp = 64.dp
    val minTouchTarget: Dp = 48.dp

    val radii: Shapes @Composable get() = MaterialTheme.shapes
    val typography: Typography @Composable get() = MaterialTheme.typography
    val colors: LogicMixColors @Composable get() = LocalLogicMixColors.current
    val colorPrimary: Color @Composable get() = MaterialTheme.colorScheme.primary
    val colorBackground: Color @Composable get() = MaterialTheme.colorScheme.background
    val colorSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
    val colorSuccess: Color @Composable get() = colors.success
    val colorError: Color @Composable get() = MaterialTheme.colorScheme.error
    val colorPuzzleAccent: Color @Composable get() = MaterialTheme.colorScheme.secondary
    val colorGold: Color @Composable get() = colors.gold
}

@Composable
fun LogicMixTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = themeMode.isDark(isSystemInDarkTheme())
    CompositionLocalProvider(LocalLogicMixColors provides if (dark) DarkLogicMixColors else LightLogicMixColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColorScheme else LightColorScheme,
            typography = LogicMixTypography,
            shapes = LogicMixShapes,
            content = content,
        )
    }
}
