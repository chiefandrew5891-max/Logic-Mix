package com.akynex.logicmix.feature.splash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Near-black splash backdrop with slow translucent smoke drifting behind the cube. */
internal val SplashBase = Color(0xFF030408)

/** All periods divide [LOOP_SECONDS], so the motion repeats exactly; smoke never touches the cube layers drawn after it. */
private const val LOOP_SECONDS = 12.0
private const val UNIT_RADIUS = 100f

private class Puff(
    val cx: Float, val cy: Float, val orbit: Float, val period: Double, val phase: Double,
    val size: Float, val sx: Float, val sy: Float, val color: Color, val alpha: Float, val breath: Double,
)

/** Gradients are built once around the origin and only moved/scaled while drawing. */
internal class SplashSmokeBrushes {
    private val graphite = Color(0xFF3A4458)
    private val cold = Color(0xFF223457)
    private val violet = Color(0xFF3B2A78)
    private val puffs = listOf(
        Puff(0.30f, 0.42f, 0.10f, 12.0, 0.0, 0.62f, 1.5f, 0.8f, graphite, 0.20f, 12.0),
        Puff(0.72f, 0.58f, 0.12f, -12.0, 1.3, 0.58f, 1.4f, 0.75f, cold, 0.24f, 6.0),
        Puff(0.50f, 0.78f, 0.08f, 6.0, 2.4, 0.50f, 1.7f, 0.7f, graphite, 0.16f, 12.0),
        Puff(0.55f, 0.28f, 0.09f, -6.0, 4.1, 0.46f, 1.3f, 0.8f, cold, 0.18f, 4.0),
        Puff(0.45f, 0.52f, 0.06f, 12.0, 5.0, 0.40f, 1.2f, 1.0f, violet, 0.12f, 12.0),
    )
    private val brushes = puffs.map {
        Brush.radialGradient(0f to it.color, 0.55f to it.color.copy(alpha = 0.45f), 1f to Color.Transparent, center = Offset.Zero, radius = UNIT_RADIUS)
    }

    fun draw(scope: DrawScope, t: Float, alpha: Float) = with(scope) {
        val side = min(size.width, size.height)
        val seconds = t.toDouble() % LOOP_SECONDS
        puffs.forEachIndexed { i, p ->
            val a = 2.0 * PI * seconds / p.period + p.phase
            val x = size.width * p.cx + (cos(a) * p.orbit * side).toFloat()
            val y = size.height * p.cy + (sin(a * 1.0) * p.orbit * side * 0.7).toFloat()
            val breath = 0.65f + 0.35f * sin(2.0 * PI * seconds / p.breath + p.phase).toFloat()
            val k = side * p.size / UNIT_RADIUS
            translate(x, y) {
                scale(k * p.sx, k * p.sy, pivot = Offset.Zero) {
                    drawCircle(brushes[i], radius = UNIT_RADIUS, center = Offset.Zero, alpha = p.alpha * breath * alpha)
                }
            }
        }
    }
}
