package com.akynex.logicmix.feature.twodots

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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.domain.twodots.TwoDotsRules
import com.akynex.logicmix.feature.orb.drawOrb
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/** Game pieces keep their hue in both themes; the board around them follows the global theme. */
object TwoDotsPalette {
    val colors = listOf(
        Color(0xFF1EA7FF), // blue
        Color(0xFFA24DFF), // purple
        Color(0xFFFFC61A), // yellow
        Color(0xFF22C768), // green
        Color(0xFFFF5A5F), // coral
    )

    fun of(code: Int): Color = colors[code.coerceIn(0, colors.lastIndex)]
}

/** Pure geometry shared by drawing, hit testing and tests. */
object TwoDotsGeometry {
    private const val N = TwoDotsRules.SIZE
    fun pad(side: Float) = side * 0.03f
    fun gap(side: Float) = side * 0.012f
    fun cell(side: Float) = (side - 2 * pad(side) - (N - 1) * gap(side)) / N

    fun origin(index: Int, side: Float): Offset {
        val step = cell(side) + gap(side)
        return Offset(pad(side) + (index % N) * step, pad(side) + (index / N) * step)
    }

    fun center(index: Int, side: Float): Offset = origin(index, side) + Offset(cell(side) / 2, cell(side) / 2)

    /** Only the inscribed circle of a cell counts, so a finger cutting a corner never jumps diagonally. */
    fun cellAt(x: Float, y: Float, side: Float): Int? {
        val step = cell(side) + gap(side)
        val col = floor((x - pad(side)) / step).toInt()
        val row = floor((y - pad(side)) / step).toInt()
        if (col !in 0 until N || row !in 0 until N) return null
        val index = row * N + col
        val c = center(index, side)
        return if (hypot(x - c.x, y - c.y) <= cell(side) * 0.55f) index else null
    }
}

@Composable
fun TwoDotsBoard(
    state: TwoDotsUiState,
    onBegin: (Int) -> Unit,
    onDrag: (Int) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = LogicMixTokens.colors
    val begin by rememberUpdatedState(onBegin)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    val stage = state.stage
    val progress = remember { Animatable(0f) }
    LaunchedEffect(stage?.key) {
        progress.snapTo(0f)
        if (stage != null && stage.millis > 0) progress.animateTo(1f, tween(stage.millis, easing = LinearEasing)) else progress.snapTo(1f)
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart), label = "pulse",
    )
    val enabled = state.game?.over == false && !state.busy
    val container = scheme.surface
    val cellColor = scheme.surfaceVariant
    val line = colors.cardBorder
    val accent = scheme.secondary
    val cells = state.shownCells
    val path = state.path

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(LogicMixTokens.radii.large)
            .background(container)
            .border(BorderStroke(1.dp, LogicMixTokens.colors.cardBorder), LogicMixTokens.radii.large)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val side = size.width.toFloat()
                    val first = TwoDotsGeometry.cellAt(down.position.x, down.position.y, side)
                    down.consume()
                    if (first == null) return@awaitEachGesture
                    begin(first)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (change == null) { cancel(); break }
                        change.consume()
                        if (!change.pressed) { end(); break }
                        TwoDotsGeometry.cellAt(change.position.x, change.position.y, side)?.let(drag)
                    }
                }
            },
    ) {
        val side = size.width
        val cell = TwoDotsGeometry.cell(side)
        val step = cell + TwoDotsGeometry.gap(side)
        val corner = CornerRadius(cell * 0.2f)
        val p = progress.value
        val eased = 1f - (1f - p) * (1f - p) * (1f - p)
        for (i in 0 until TwoDotsRules.CELLS) {
            val o = TwoDotsGeometry.origin(i, side)
            drawRoundRect(cellColor, o, Size(cell, cell), corner)
            drawRoundRect(line, o, Size(cell, cell), corner, style = Stroke(1f))
        }
        val hint = state.hint?.path.orEmpty().toSet()
        for (i in hint) {
            drawCircle(accent, cell * 0.42f, TwoDotsGeometry.center(i, side), style = Stroke(cell * 0.06f))
        }
        val chainColor = path.firstOrNull()?.let { cells.getOrNull(it) }
        val closed = path.size >= 5 && path.first() == path.last()
        if (path.size >= 2 && chainColor != null) {
            val line2 = Path().apply {
                path.forEachIndexed { k, idx ->
                    val c = TwoDotsGeometry.center(idx, side)
                    if (k == 0) moveTo(c.x, c.y) else lineTo(c.x, c.y)
                }
            }
            drawPath(line2, TwoDotsPalette.of(chainColor).copy(alpha = 0.85f), style = Stroke(cell * 0.17f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        for (i in 0 until TwoDotsRules.CELLS) {
            val code = cells[i]
            var scale = 1f
            var glow = 0f
            var alpha = 1f
            var dy = 0f
            if (stage != null) {
                when {
                    i in stage.clearing -> {
                        val d = if (stage.wave) {
                            val a = TwoDotsGeometry.center(stage.origin, side)
                            val b = TwoDotsGeometry.center(i, side)
                            (abs(a.x - b.x) + abs(a.y - b.y)) / (side * 2f)
                        } else 0f
                        val local = ((p * 1.5f) - d).coerceIn(0f, 1f)
                        scale = 1f - local
                        glow = sin(local * Math.PI.toFloat())
                    }
                    stage.drop != null -> {
                        dy = -stage.drop[i] * step * (1f - eased)
                        if (i in stage.fresh) {
                            scale = 0.6f + 0.4f * eased
                            alpha = eased
                        }
                    }
                }
            } else if (i in path) {
                val last = i == path.last()
                scale = 1.14f + if (last) 0.06f * sin(pulse * 2f * Math.PI.toFloat()) else 0f
                glow = 0.6f
            } else if (closed && chainColor == code) {
                glow = 0.5f
            }
            val c = TwoDotsGeometry.center(i, side)
            drawOrb(Offset(c.x, c.y + dy), cell * 0.3f * scale, TwoDotsPalette.of(code), glow, alpha)
        }
    }
}
