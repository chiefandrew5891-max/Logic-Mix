package com.akynex.logicmix.feature.splash

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import com.akynex.logicmix.R
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

private val SplashBackground = Color(0xFF08112C)
private val Gold = Color(0xFFFFC94A)
private val GoldHot = Color(0xFFFFF1B8)
private const val LAYER = 1024f
private const val CUBE_SIZE = 560f // cube side inside the 1024 px layer, the reference for segment travel

/**
 * Full-screen cold-start splash: a cube whose segments breathe apart and back while energy pulses race along
 * its golden orbit. Drawn entirely on a Compose Canvas from seven aligned PNG layers; fully offline.
 * The overlay sits over an already composed home screen, which is therefore ready when the splash fades out.
 */
@Composable
fun AnimatedSplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val reduced = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val total = if (reduced) SplashMotion.REDUCED_DURATION else SplashMotion.DURATION
    val finished by rememberUpdatedState(onFinished)

    val core = ImageBitmap.imageResource(R.drawable.splash_cube_core)
    val top = ImageBitmap.imageResource(R.drawable.splash_cube_top)
    val left = ImageBitmap.imageResource(R.drawable.splash_cube_left)
    val right = ImageBitmap.imageResource(R.drawable.splash_cube_right)
    val bottom = ImageBitmap.imageResource(R.drawable.splash_cube_bottom)
    val orbitBack = ImageBitmap.imageResource(R.drawable.splash_orbit_back)
    val orbitFront = ImageBitmap.imageResource(R.drawable.splash_orbit_front)
    val logo = ImageBitmap.imageResource(R.drawable.splash_logo_text)

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween((total * 1000).toInt(), easing = LinearEasing))
        finished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } } // swallow touches until the home screen is revealed
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // The timeline is read here, in the draw phase only, so no recomposition happens per frame.
            val t = progress.value * total
            val overlay = SplashMotion.overlayAlpha(t, total)
            drawRect(SplashBackground, alpha = overlay)
            if (overlay <= 0f) return@Canvas
            val motionT = if (reduced) 0f else t
            val alpha = SplashMotion.appear(t) * overlay
            val side = min(size.width, size.height) * 0.94f
            val k = side / LAYER
            translate((size.width - side) / 2f, (size.height - side) / 2f) {
                scale(k, k, pivot = Offset.Zero) {
                    drawScene(motionT, alpha, core, top, left, right, bottom, orbitBack, orbitFront)
                    drawLogo(if (reduced) 1f else SplashMotion.logoProgress(t), logo, alpha)
                }
            }
        }
    }
}

private fun DrawScope.layer(img: ImageBitmap, alpha: Float, dx: Float = 0f, dy: Float = 0f) {
    translate(dx, dy) {
        drawImage(img, dstSize = IntSize(LAYER.toInt(), LAYER.toInt()), alpha = alpha, filterQuality = FilterQuality.High)
    }
}

private fun DrawScope.drawScene(
    t: Float, alpha: Float,
    core: ImageBitmap, top: ImageBitmap, left: ImageBitmap, right: ImageBitmap, bottom: ImageBitmap,
    orbitBack: ImageBitmap, orbitFront: ImageBitmap,
) {
    val c = Offset(LAYER / 2f, LAYER / 2f)
    val energy = SplashMotion.orbitEnergy(t)
    val glow = SplashMotion.coreGlow(t)

    val s = SplashMotion.breathScale(t)
    scale(s, s, pivot = c) {
        run {
            // Soft halo behind the cube; breathes with the core.
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFF5B3BFF).copy(alpha = (0.16f + 0.14f * glow) * alpha), Color.Transparent),
                    center = c, radius = 520f,
                ),
                radius = 520f, center = c,
            )
            layer(orbitBack, alpha)
            drawOrbitPulses(t, energy, alpha, front = false)

            val cs = SplashMotion.coreScale(t)
            scale(cs, cs, pivot = c) { layer(core, alpha) }
            if (glow > 0f) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFFFFE08A).copy(alpha = 0.28f * glow * alpha), Color.Transparent),
                        center = c, radius = 190f,
                    ),
                    radius = 190f, center = c, blendMode = BlendMode.Plus,
                )
            }

            fun seg(img: ImageBitmap, seg: SplashMotion.Segment) {
                val d = SplashMotion.spread(seg, t) * SplashMotion.amplitudeFor(seg) * CUBE_SIZE
                val len = max(0.001f, kotlin.math.sqrt(seg.dx * seg.dx + seg.dy * seg.dy))
                val z = SplashMotion.segmentScale(seg, t)
                scale(z, z, pivot = c) { layer(img, alpha, seg.dx / len * d, seg.dy / len * d) }
            }
            seg(bottom, SplashMotion.Segment.Bottom)
            seg(left, SplashMotion.Segment.Left)
            seg(right, SplashMotion.Segment.Right)
            seg(top, SplashMotion.Segment.Top)

            layer(orbitFront, alpha)
            drawOrbitPulses(t, energy, alpha, front = true)
        }
    }
}

private const val LOGO_WIDTH = 780f // layer units; the height follows the image proportions

private fun DrawScope.drawLogo(p: Float, logo: ImageBitmap, alpha: Float) {
    val a = SplashMotion.logoAlpha(p) * alpha
    if (a <= 0f) return
    val c = Offset(LAYER / 2f, LAYER / 2f)
    val h = LOGO_WIDTH * logo.height / logo.width
    val sc = SplashMotion.logoScale(p)
    rotate(SplashMotion.logoAngle(p), pivot = c) {
        scale(sc, sc, pivot = c) {
            drawImage(
                logo,
                dstOffset = androidx.compose.ui.unit.IntOffset((c.x - LOGO_WIDTH / 2f).toInt(), (c.y - h / 2f).toInt()),
                dstSize = IntSize(LOGO_WIDTH.toInt(), h.toInt()),
                alpha = a, filterQuality = FilterQuality.High,
            )
        }
    }
}

private const val PULSES = 3
private const val TAIL = 16

private fun DrawScope.drawOrbitPulses(t: Float, energy: Float, alpha: Float, front: Boolean) {
    if (energy <= 0f) return
    val turns = SplashMotion.orbitTurns(t)
    val twoPi = (2.0 * PI).toFloat()
    for (p in 0 until PULSES) {
        val headTurn = turns + p / PULSES.toFloat()
        for (j in 0 until TAIL) {
            val theta = (headTurn - j * 0.012f * (1f + turns * 0.15f)) * twoPi
            if (SplashMotion.isFront(theta) != front) continue
            val (x, y) = SplashMotion.orbitPoint(theta)
            val fall = 1f - j / TAIL.toFloat()
            val a = energy * alpha * fall * fall
            val r = 9f + 20f * fall
            drawCircle(
                Brush.radialGradient(
                    listOf(GoldHot.copy(alpha = a), Gold.copy(alpha = a * 0.5f), Color.Transparent),
                    center = Offset(x, y), radius = r * 2.2f,
                ),
                radius = r * 2.2f, center = Offset(x, y), blendMode = BlendMode.Plus,
            )
        }
        // Sparks thrown off the pulse head: deterministic, ~0.5 s life.
        if (front) {
            for (n in 0 until 6) {
                val life = ((t * 2f + SplashMotion.hash01(p * 7 + n, 1)) % 1f)
                val theta = (headTurn - life * 0.05f) * twoPi
                val (x, y) = SplashMotion.orbitPoint(theta)
                val ang = SplashMotion.hash01(p * 7 + n, 2) * twoPi
                val dist = life * 46f
                val a = energy * alpha * (1f - life)
                val o = Offset(x + kotlin.math.cos(ang) * dist, y + kotlin.math.sin(ang) * dist)
                drawCircle(GoldHot.copy(alpha = a * 0.8f), radius = 3.5f * (1f - life) + 1f, center = o, blendMode = BlendMode.Plus)
            }
        }
    }
}
