package com.akynex.logicmix.feature.splash

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.rememberLoopClock
import kotlin.math.PI

private const val LAYER = 1024f
private const val CUBE_SIZE = 560f
private const val BBOX_CX = 510f // visible bounds of the seven layers: x 69..951, y 110..912
private const val BBOX_CY = 511f
private const val BBOX_H = 803f
private val Gold = Color(0xFFFFC94A)
private val GoldHot = Color(0xFFFFF1B8)

/**
 * The splash cube (same seven PNG layers, orbit and glow) as a reusable, permanently running component. The visible cube
 * is fitted to the component height; breathing and the orbit lights are drawn outside the layout bounds, so nothing around
 * it moves. The clock is read in the draw phase only and pauses while the app is in the background.
 */
@Composable
fun AnimatedCube(modifier: Modifier = Modifier, config: CubeMotionConfig = CubeMotionConfig.Home) {
    val core = ImageBitmap.imageResource(R.drawable.splash_cube_core)
    val top = ImageBitmap.imageResource(R.drawable.splash_cube_top)
    val left = ImageBitmap.imageResource(R.drawable.splash_cube_left)
    val right = ImageBitmap.imageResource(R.drawable.splash_cube_right)
    val bottom = ImageBitmap.imageResource(R.drawable.splash_cube_bottom)
    val orbitBack = ImageBitmap.imageResource(R.drawable.splash_orbit_back)
    val orbitFront = ImageBitmap.imageResource(R.drawable.splash_orbit_front)
    val clock = rememberLoopClock()
    val brushes = remember { CubeBrushes() }

    // Own render layer: per-frame invalidation redraws only the cube, not the screen around it.
    Canvas(modifier.graphicsLayer()) {
        val seconds = clock.doubleValue
        val t = CubeMotion.pulseTime(seconds, config.pulseCycleSeconds)
        val turns = CubeMotion.orbitTurns(seconds, config.orbitSecondsPerTurn)
        val k = size.height / BBOX_H
        translate(size.width / 2f - BBOX_CX * k, size.height / 2f - BBOX_CY * k) {
            scale(k, k, pivot = Offset.Zero) {
                drawCube(t, turns, config, brushes, core, top, left, right, bottom, orbitBack, orbitFront)
            }
        }
    }
}

private fun DrawScope.layer(img: ImageBitmap, dx: Float = 0f, dy: Float = 0f) {
    translate(dx, dy) {
        drawImage(img, dstSize = IntSize(LAYER.toInt(), LAYER.toInt()), filterQuality = FilterQuality.Medium)
    }
}

private fun DrawScope.drawCube(
    t: Float, turns: Float, config: CubeMotionConfig, brushes: CubeBrushes,
    core: ImageBitmap, top: ImageBitmap, left: ImageBitmap, right: ImageBitmap, bottom: ImageBitmap,
    orbitBack: ImageBitmap, orbitFront: ImageBitmap,
) {
    val c = Offset(LAYER / 2f, LAYER / 2f)
    val amp = config.pulseAmplitude
    val glow = CubeMotion.coreGlow(t)
    val s = CubeMotion.breathScale(t, amp)
    scale(s, s, pivot = c) {
        translate(c.x, c.y) { drawCircle(brushes.halo, radius = 520f, center = Offset.Zero, alpha = 0.16f + 0.14f * glow) }
        layer(orbitBack)
        drawOrbitLights(turns, config.orbitLights, brushes, front = false)

        val cs = CubeMotion.coreScale(t, amp)
        scale(cs, cs, pivot = c) { layer(core) }
        translate(c.x, c.y) { drawCircle(brushes.coreGlow, radius = 190f, center = Offset.Zero, alpha = 0.28f * glow, blendMode = BlendMode.Plus) }

        fun seg(img: ImageBitmap, seg: SplashMotion.Segment) {
            val d = CubeMotion.travel(seg, t, amp) * CUBE_SIZE
            val len = kotlin.math.sqrt(seg.dx * seg.dx + seg.dy * seg.dy).coerceAtLeast(0.001f)
            val z = CubeMotion.segmentScale(seg, t, amp)
            scale(z, z, pivot = c) { layer(img, seg.dx / len * d, seg.dy / len * d) }
        }
        seg(bottom, SplashMotion.Segment.Bottom)
        seg(left, SplashMotion.Segment.Left)
        seg(right, SplashMotion.Segment.Right)
        seg(top, SplashMotion.Segment.Top)

        layer(orbitFront)
        drawOrbitLights(turns, config.orbitLights, brushes, front = true)
    }
}

private const val TAIL = 20
private const val TAIL_SPAN = 0.10f // in revolutions

/** Gradients built once, around the origin; drawing moves them with translate instead of rebuilding them per frame. */
private class CubeBrushes {
    val halo = Brush.radialGradient(listOf(Color(0xFF5B3BFF), Color.Transparent), center = Offset.Zero, radius = 520f)
    val coreGlow = Brush.radialGradient(listOf(Color(0xFFFFE08A), Color.Transparent), center = Offset.Zero, radius = 190f)
    val radii = FloatArray(TAIL) { j -> (9f + 20f * (1f - j / TAIL.toFloat())) * 2.2f }
    val tail = Array(TAIL) { j ->
        val fall = 1f - j / TAIL.toFloat()
        val a = fall * fall * 0.6f
        Brush.radialGradient(listOf(GoldHot.copy(alpha = a), Gold.copy(alpha = a * 0.5f), Color.Transparent), center = Offset.Zero, radius = radii[j])
    }
}

/** Soft comets at evenly spaced orbit positions; the angle is linear in [turns], so the speed is constant. */
private fun DrawScope.drawOrbitLights(turns: Float, lights: Int, brushes: CubeBrushes, front: Boolean) {
    val twoPi = (2.0 * PI).toFloat()
    for (p in 0 until lights) {
        val head = turns + p / lights.toFloat()
        for (j in 0 until TAIL) {
            val theta = (head - j * TAIL_SPAN / TAIL) * twoPi
            if (SplashMotion.isFront(theta) != front) continue
            val (x, y) = SplashMotion.orbitPoint(theta)
            translate(x, y) { drawCircle(brushes.tail[j], radius = brushes.radii[j], center = Offset.Zero, blendMode = BlendMode.Plus) }
        }
    }
}
