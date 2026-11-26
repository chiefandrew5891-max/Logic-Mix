package com.akynex.logicmix.feature.orb

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.domain.orb.OrbRules
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/** The seven orb colors (1..7), saturated with a slight neon feel. They are game pieces, so they do not change with the theme. */
object OrbPalette {
    val colors = listOf(
        Color(0xFFFF3B52), // red
        Color(0xFF2F6BFF), // blue
        Color(0xFF22D36B), // green
        Color(0xFFFFC61A), // yellow
        Color(0xFFA24DFF), // purple
        Color(0xFF14D9E6), // cyan
        Color(0xFFFF5DBA), // pink
    )

    fun of(code: Int): Color = colors[(code - 1).coerceIn(0, colors.lastIndex)]
}

/** Pure geometry shared by drawing, hit testing and tests: 81 identical square cells inside a padded square. */
object OrbBoardGeometry {
    fun pad(side: Float) = side * 0.025f
    fun gap(side: Float) = side * 0.008f
    fun cell(side: Float) = (side - 2 * pad(side) - 8 * gap(side)) / OrbRules.SIZE

    fun origin(index: Int, side: Float): Offset {
        val step = cell(side) + gap(side)
        return Offset(pad(side) + (index % OrbRules.SIZE) * step, pad(side) + (index / OrbRules.SIZE) * step)
    }

    fun center(index: Int, side: Float): Offset = origin(index, side) + Offset(cell(side) / 2, cell(side) / 2)

    /** Taps on the padding or gaps resolve to the nearest cell, so no touch is lost near an edge. */
    fun cellAt(x: Float, y: Float, side: Float): Int {
        val step = cell(side) + gap(side)
        val c = floor((x - pad(side)) / step).toInt().coerceIn(0, OrbRules.SIZE - 1)
        val r = floor((y - pad(side)) / step).toInt().coerceIn(0, OrbRules.SIZE - 1)
        return r * OrbRules.SIZE + c
    }
}

/** Glossy sphere with a soft drop shadow; [glow] (0..1) adds the selection halo. */
fun DrawScope.drawOrb(center: Offset, radius: Float, base: Color, glow: Float = 0f, alpha: Float = 1f) {
    if (radius <= 0f) return
    if (glow > 0f) {
        drawCircle(Brush.radialGradient(listOf(base.copy(alpha = 0.55f * glow), Color.Transparent), center, radius * 1.45f), radius * 1.45f, center)
    }
    drawOval(Color.Black.copy(alpha = 0.28f * alpha), Offset(center.x - radius * 0.78f, center.y + radius * 0.68f), Size(radius * 1.56f, radius * 0.36f))
    val light = lerp(base, Color.White, 0.45f)
    val dark = lerp(base, Color.Black, 0.55f)
    val lit = Offset(center.x - radius * 0.3f, center.y - radius * 0.35f)
    drawCircle(Brush.radialGradient(listOf(light.copy(alpha = alpha), base.copy(alpha = alpha), dark.copy(alpha = alpha)), lit, radius * 1.6f), radius, center)
    // Thin darkening along the lower rim.
    drawCircle(
        Brush.radialGradient(0.72f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.3f * alpha), center = center, radius = radius),
        radius, center,
    )
    // Softer bounce light bottom right.
    val bounce = Offset(center.x + radius * 0.38f, center.y + radius * 0.5f)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.28f * alpha), Color.Transparent), bounce, radius * 0.5f), radius * 0.5f, bounce)
    // Main highlight top left.
    val hi = Offset(center.x - radius * 0.38f, center.y - radius * 0.42f)
    drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f * alpha), Color.White.copy(alpha = 0f)), hi, radius * 0.36f), Offset(hi.x - radius * 0.3f, hi.y - radius * 0.2f), Size(radius * 0.6f, radius * 0.4f))
}

/** Reusable orb for panels and previews; the board draws orbs directly with [drawOrb]. [scale] drives appear/disappear animations. */
@Composable
fun OrbBall(
    color: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    scale: Float = 1f,
) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension * 0.42f * scale
        drawOrb(Offset(this.size.width / 2, this.size.height / 2), r, OrbPalette.of(color), glow = if (selected) 1f else 0f)
    }
}

@Composable
fun OrbBoard(state: OrbUiState, onTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = LogicMixTokens.colors
    val tap by rememberUpdatedState(onTap)
    val stage = state.stage
    val progress = remember { Animatable(0f) }
    LaunchedEffect(stage?.key) {
        progress.snapTo(0f)
        if (stage != null) progress.animateTo(1f, tween(stage.millis, easing = LinearEasing))
    }
    val bounce by rememberInfiniteTransition(label = "bounce").animateFloat(
        0f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Restart), label = "bounce",
    )
    val container = scheme.surface
    val cellColor = scheme.surfaceVariant
    val line = colors.cardBorder
    val accent = scheme.secondary
    val cells = state.shownCells

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(LogicMixTokens.radii.large)
            .background(container)
            .border(BorderStroke(1.dp, LogicMixTokens.colors.cardBorder), LogicMixTokens.radii.large)
            .pointerInput(Unit) {
                detectTapGestures { o -> tap(OrbBoardGeometry.cellAt(o.x, o.y, size.width.toFloat())) }
            },
    ) {
        val side = size.width
        val cell = OrbBoardGeometry.cell(side)
        val corner = CornerRadius(cell * 0.18f)
        val p = progress.value
        for (i in 0 until OrbRules.CELLS) {
            val o = OrbBoardGeometry.origin(i, side)
            drawRoundRect(cellColor, o, Size(cell, cell), corner)
            drawRoundRect(line, o, Size(cell, cell), corner, style = Stroke(1f))
        }
        state.hint?.let { (_, to) ->
            drawRoundRect(accent, OrbBoardGeometry.origin(to, side), Size(cell, cell), corner, style = Stroke(cell * 0.07f))
        }
        for (i in 0 until OrbRules.CELLS) {
            val code = cells[i]
            if (code == 0) continue
            var scale = 1f
            var glow = 0f
            var lift = 0f
            when {
                stage != null && i in stage.clearing -> { scale = 1f - p; glow = 1f - p }
                stage != null && i in stage.spawning -> scale = p
                i == state.selected -> { glow = 0.7f; lift = abs(sin(bounce * Math.PI.toFloat())) * cell * 0.1f }
            }
            val c = OrbBoardGeometry.center(i, side)
            drawOrb(Offset(c.x, c.y - lift), cell * 0.4f * scale, OrbPalette.of(code), glow)
        }
        stage?.moving?.let { m ->
            val t = p * (m.path.size - 1)
            val a = t.toInt().coerceIn(0, m.path.size - 1)
            val b = (a + 1).coerceAtMost(m.path.size - 1)
            val ca = OrbBoardGeometry.center(m.path[a], side)
            val cb = OrbBoardGeometry.center(m.path[b], side)
            val f = t - a
            drawOrb(Offset(ca.x + (cb.x - ca.x) * f, ca.y + (cb.y - ca.y) * f), cell * 0.4f, OrbPalette.of(m.color), glow = 0.4f)
        }
    }
}
