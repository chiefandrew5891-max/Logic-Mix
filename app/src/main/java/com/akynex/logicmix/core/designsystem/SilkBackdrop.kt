package com.akynex.logicmix.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * One silk ribbon: a bundle of [lines] hairline Bezier curves fanned out between two guide curves A and B. The guides
 * sway on their own sine periods, so the bundle twists, pinches into a bright knot and opens again like translucent
 * fabric. Every period divides [LOOP_CLOCK_WRAP_SECONDS], so the loop has no seam.
 */
private class SilkRibbon(
    val color: Color, val core: Color, val lines: Int, val y0: Float, val gap: Float, val slope: Float,
    val ampA: Float, val ampB: Float, val periodA: Double, val periodB: Double, val phaseA: Float, val phaseB: Float,
    val breathSeconds: Double, val alpha: Float,
)

private fun ribbon(
    color: Long, lines: Int, y0: Float, gap: Float, slope: Float, ampA: Float, ampB: Float,
    periodA: Double, periodB: Double, phaseA: Float, phaseB: Float, breath: Double, alpha: Float, coreMix: Float = 0.55f,
): SilkRibbon {
    val c = Color(color)
    return SilkRibbon(c, lerp(c, Color.White, coreMix), lines, y0, gap, slope, ampA, ampB, periodA, periodB, phaseA, phaseB, breath, alpha)
}

// Dark theme: electric blue, cyan, violet and a touch of magenta on near-black, fine dense bundles.
private val DarkSilk = listOf(
    ribbon(0xFF2A4FD0, 30, 0.28f, 0.14f, 0.07f, 0.07f, 0.06f, 48.0, 36.0, 1.0f, 5.0f, 40.0, 0.12f),
    ribbon(0xFF8E4DFF, 44, 0.52f, 0.20f, 0.09f, 0.09f, 0.11f, 60.0, 45.0, 4.1f, 0.7f, 36.0, 0.20f),
    ribbon(0xFF2F6BFF, 44, 0.62f, 0.16f, 0.05f, 0.10f, 0.08f, 36.0, 48.0, 0.0f, 1.4f, 40.0, 0.22f),
    ribbon(0xFF35D0FF, 36, 0.70f, 0.10f, -0.06f, 0.07f, 0.06f, 30.0, 40.0, 2.0f, 3.3f, 45.0, 0.20f),
    ribbon(0xFFE23CD0, 30, 0.80f, 0.12f, -0.04f, 0.06f, 0.07f, 72.0, 90.0, 5.2f, 2.2f, 60.0, 0.14f),
)

// Light theme: a different, wider and softer pattern in lilac, blue and teal plus white veils; coloured lines are drawn
// normally (not additively) so they stay visible on the pale base.
private val LightSilk = listOf(
    ribbon(0xFF8D6BE0, 34, 0.18f, 0.24f, 0.05f, 0.08f, 0.07f, 45.0, 60.0, 0.5f, 2.9f, 36.0, 0.20f, 0.25f),
    ribbon(0xFFFFFFFF, 26, 0.40f, 0.12f, -0.05f, 0.06f, 0.08f, 40.0, 72.0, 3.0f, 1.1f, 48.0, 0.55f, 0f),
    ribbon(0xFF4F86EE, 34, 0.52f, 0.18f, 0.06f, 0.09f, 0.06f, 60.0, 36.0, 1.8f, 4.4f, 30.0, 0.20f, 0.25f),
    ribbon(0xFF1FB0C8, 34, 0.76f, 0.16f, -0.05f, 0.07f, 0.09f, 36.0, 45.0, 4.8f, 0.3f, 40.0, 0.22f, 0.25f),
    ribbon(0xFFFFFFFF, 22, 0.88f, 0.08f, 0.04f, 0.05f, 0.06f, 90.0, 60.0, 2.4f, 5.5f, 72.0, 0.45f, 0f),
)

/**
 * Shared screen backdrop: a base gradient plus slowly flowing silk ribbons. Dark: near-black navy with blue, cyan and
 * violet ribbons. Light: lilac at the top fading through white to a blue-teal at the bottom, with its own lighter ribbon
 * pattern. It draws in its own render layer and reads the loop clock only in the draw phase, so the content above it is
 * never recomposed or re-recorded. The clock stops when the screen leaves composition or the app is in the background.
 */
@Composable
fun LogicMixBackdrop(modifier: Modifier = Modifier) {
    val dark = LogicMixTokens.colors.isDark
    val clock = rememberLoopClock()
    Box(
        modifier.graphicsLayer().drawWithCache {
            val w = size.width
            val h = size.height
            val base = if (dark) Brush.verticalGradient(listOf(PremiumTokens.Bg, PremiumTokens.BgDeep, PremiumTokens.Bg))
            else Brush.verticalGradient(0f to Color(0xFFB7A2EB), 0.42f to Color(0xFFF6F7FF), 1f to Color(0xFF63BCDB))
            val glow = Brush.radialGradient(
                listOf((if (dark) PremiumTokens.BgLit else Color.White).copy(alpha = if (dark) 0.55f else 0.5f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.3f), radius = w * 0.9f,
            )
            val set = if (dark) DarkSilk else LightSilk
            val mode = if (dark) BlendMode.Plus else BlendMode.SrcOver
            val hair = 1.dp.toPx()
            val xs = floatArrayOf(-0.2f * w, 0.28f * w, 0.72f * w, 1.2f * w)
            val ay = FloatArray(4)
            val by = FloatArray(4)
            val path = Path()
            val twoPi = 2.0 * PI
            fun fan(r: SilkRibbon, s: Float) {
                path.reset()
                path.moveTo(xs[0], ay[0] + (by[0] - ay[0]) * s)
                path.cubicTo(
                    xs[1], ay[1] + (by[1] - ay[1]) * s,
                    xs[2], ay[2] + (by[2] - ay[2]) * s,
                    xs[3], ay[3] + (by[3] - ay[3]) * s,
                )
            }
            onDrawBehind {
                val t = clock.doubleValue
                drawRect(base)
                drawRect(glow)
                for (r in set) {
                    for (k in 0..3) {
                        val slope = r.slope * (k - 1.5f)
                        ay[k] = h * (r.y0 + slope + r.ampA * sin(twoPi * t / r.periodA + r.phaseA + k * 1.3).toFloat())
                        by[k] = h * (r.y0 + r.gap + slope * 0.6f + r.ampB * sin(twoPi * t / r.periodB + r.phaseB + k * 0.9).toFloat())
                    }
                    val breath = 0.85f + 0.15f * sin(twoPi * t / r.breathSeconds + r.phaseA).toFloat()
                    for (i in 0..r.lines) {
                        val u = i.toFloat() / r.lines
                        fan(r, u.pow(1.35f))
                        drawPath(path, r.color, alpha = (r.alpha * breath * (0.25f + 0.75f * u * u)).coerceAtMost(1f), style = Stroke(hair), blendMode = mode)
                    }
                    // Bright leading edge with a soft halo.
                    fan(r, 1f)
                    drawPath(path, r.color, alpha = 0.12f * breath, style = Stroke(hair * 6f, cap = StrokeCap.Round), blendMode = mode)
                    drawPath(path, r.core, alpha = (r.alpha * 3f * breath).coerceAtMost(0.9f), style = Stroke(hair * 1.5f, cap = StrokeCap.Round), blendMode = mode)
                }
            }
        },
    )
}

/** Full-screen container: the backdrop sits behind, [content] (with [modifier], e.g. system-bar padding) over it. */
@Composable
fun LogicMixScreen(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LogicMixBackdrop(Modifier.matchParentSize())
        Box(modifier.fillMaxSize(), content = content)
    }
}
