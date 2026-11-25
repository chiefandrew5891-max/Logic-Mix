package com.akynex.logicmix.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Premium settings icon (96x96 viewport, transparent background). Decorative: the parent control carries the description. */
@Composable
fun LogicMixSettingsIcon(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val dark = LogicMixTokens.colors.isDark
    Canvas(modifier = modifier.size(size)) {
        val factor = this.size.minDimension / 96f
        val gearPath = Path().apply {
            moveTo(43f, 11f)
            lineTo(53f, 11f)
            lineTo(55.3f, 20.1f)
            cubicTo(57.8f, 20.8f, 60.2f, 21.8f, 62.5f, 23.1f)
            lineTo(70.6f, 18.3f)
            lineTo(77.7f, 25.4f)
            lineTo(72.9f, 33.5f)
            cubicTo(74.2f, 35.8f, 75.2f, 38.2f, 75.9f, 40.7f)
            lineTo(85f, 43f)
            lineTo(85f, 53f)
            lineTo(75.9f, 55.3f)
            cubicTo(75.2f, 57.8f, 74.2f, 60.2f, 72.9f, 62.5f)
            lineTo(77.7f, 70.6f)
            lineTo(70.6f, 77.7f)
            lineTo(62.5f, 72.9f)
            cubicTo(60.2f, 74.2f, 57.8f, 75.2f, 55.3f, 75.9f)
            lineTo(53f, 85f)
            lineTo(43f, 85f)
            lineTo(40.7f, 75.9f)
            cubicTo(38.2f, 75.2f, 35.8f, 74.2f, 33.5f, 72.9f)
            lineTo(25.4f, 77.7f)
            lineTo(18.3f, 70.6f)
            lineTo(23.1f, 62.5f)
            cubicTo(21.8f, 60.2f, 20.8f, 57.8f, 20.1f, 55.3f)
            lineTo(11f, 53f)
            lineTo(11f, 43f)
            lineTo(20.1f, 40.7f)
            cubicTo(20.8f, 38.2f, 21.8f, 35.8f, 23.1f, 33.5f)
            lineTo(18.3f, 25.4f)
            lineTo(25.4f, 18.3f)
            lineTo(33.5f, 23.1f)
            cubicTo(35.8f, 21.8f, 38.2f, 20.8f, 40.7f, 20.1f)
            lineTo(43f, 11f)
            close()
        }
        // Light theme only: deep navy outline and violet core for contrast on a light background.
        val light = !dark
        val outline = if (light) listOf(Color(0xFF050B24), Color(0xFF142266)) else listOf(Color(0xFFE9E4FF), Color(0xFF9E79FF))
        val ring = if (light) Color(0xFF7A1FE0) else Color(0xFF35D6CC)
        val dot = if (light) Color(0xFF7A1FE0) else Color(0xFFE9E4FF)
        scale(factor, pivot = Offset.Zero) {
            drawPath(
                path = gearPath,
                brush = Brush.linearGradient(
                    colors = outline,
                    start = Offset(16f, 14f),
                    end = Offset(78f, 82f),
                ),
                style = Stroke(width = 4.5f, join = StrokeJoin.Round, cap = StrokeCap.Round),
            )
            drawCircle(ring, radius = 16f, center = Offset(48f, 48f), style = Stroke(width = 5f))
            drawCircle(dot, radius = 5f, center = Offset(48f, 48f))
        }
    }
}
