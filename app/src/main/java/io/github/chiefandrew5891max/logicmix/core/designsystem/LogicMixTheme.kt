package io.github.chiefandrew5891max.logicmix.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object LogicMixTokens {
    val spacing = 16.dp
    val elevation = 2.dp
    val radii: Shapes @Composable get() = MaterialTheme.shapes
    val typography: Typography @Composable get() = MaterialTheme.typography
    val colorPrimary: Color @Composable get() = MaterialTheme.colorScheme.primary
    val colorBackground: Color @Composable get() = MaterialTheme.colorScheme.background
    val colorSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
    val colorSuccess: Color @Composable get() = MaterialTheme.colorScheme.tertiary
    val colorError: Color @Composable get() = MaterialTheme.colorScheme.error
    val colorPuzzleAccent: Color @Composable get() = MaterialTheme.colorScheme.secondary
}

@Composable
fun LogicMixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
