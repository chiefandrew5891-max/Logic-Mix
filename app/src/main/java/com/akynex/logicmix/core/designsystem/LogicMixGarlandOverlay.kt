package com.akynex.logicmix.core.designsystem

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlin.random.Random

private val Amber = Color(0xFFFF9D00)
private val Gold = Color(0xFFFFC400)
private val Yellow = Color(0xFFFFE84A)
private val GlowAmber = Color(0xFFFFB000)
private val WarmWhite = Color(0xFFFFF8D0)

/**
 * Transparent, non-interactive light layer drawn over the bulbs already painted in the logo PNG.
 * Must fill exactly the rectangle in which the PNG is drawn. Time is read only in the draw phase.
 * [kickTick] changes on every Next/Previous; [kickForward] gives the direction of that wave.
 */
@Composable
fun LogicMixGarlandOverlay(
    playing: Boolean,
    kickTick: Int,
    kickForward: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animated = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val engine = remember { GarlandEngine(Random.Default) }
    var visible by remember { mutableStateOf(true) }
    val frame = remember { mutableLongStateOf(0L) }
    val isPlaying by rememberUpdatedState(playing)

    DisposableEffect(Unit) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        visible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val observer = LifecycleEventObserver { _, _ -> visible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(kickTick) { if (kickTick > 0) engine.kick(kickForward) }
    LaunchedEffect(animated, visible) {
        if (!animated) {
            engine.advance(0f, isPlaying, false)
            frame.longValue++
            return@LaunchedEffect
        }
        if (!visible) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                engine.advance(dt, isPlaying, true)
                frame.longValue++
            }
        }
    }

    Canvas(modifier) {
        frame.longValue
        val h = size.height
        val radius = GarlandGeometry.RADIUS * h
        val points = GarlandGeometry.bulbs
        for (i in points.indices) {
            val look = BulbLook.of(engine.levels[i])
            if (look.bodyAlpha <= 0.002f) continue
            val c = Offset(points[i].x * size.width, points[i].y * h)
            val glowR = radius * look.glowRadius
            drawCircle(
                brush = Brush.radialGradient(
                    0f to GlowAmber, 0.4f to GlowAmber.copy(alpha = 0.55f), 1f to Color.Transparent,
                    center = c, radius = glowR,
                ),
                radius = glowR, center = c, alpha = look.glowAlpha, blendMode = BlendMode.Plus,
            )
            val haloR = radius * look.haloRadius
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Gold, 0.5f to Amber.copy(alpha = 0.9f), 1f to Color.Transparent,
                    center = c, radius = haloR,
                ),
                radius = haloR, center = c, alpha = look.haloAlpha,
            )
            val bodyR = radius * look.bodyRadius
            drawCircle(
                brush = Brush.radialGradient(
                    0f to WarmWhite, 0.35f to Yellow, 0.7f to Gold, 0.92f to Amber, 1f to Amber.copy(alpha = 0f),
                    center = c, radius = bodyR,
                ),
                radius = bodyR, center = c, alpha = look.bodyAlpha,
            )
            val coreR = radius * look.coreRadius
            drawCircle(
                brush = Brush.radialGradient(0f to Color.White, 0.6f to Color.White, 1f to Color.White.copy(alpha = 0f), center = c, radius = coreR),
                radius = coreR, center = c, alpha = look.coreAlpha,
            )
            val specR = radius * look.specRadius
            val specC = Offset(c.x - radius * 0.3f, c.y - radius * 0.35f)
            drawCircle(color = Color.White, radius = specR, center = specC, alpha = look.specAlpha)
        }
    }
}
