package io.github.chiefandrew5891max.logicmix.core.designsystem

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

private val Amber = Color(0xFFFFA726)
private val Gold = Color(0xFFFFC94D)
private val SoftYellow = Color(0xFFFFE9A8)
private val WarmWhite = Color(0xFFFFFBEA)

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
            val level = engine.levels[i].coerceIn(0f, 1f)
            val c = Offset(points[i].x * size.width, points[i].y * h)
            val haloR = radius * (2.2f + 1.6f * level)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Gold.copy(alpha = 0.55f), Amber.copy(alpha = 0.22f), Color.Transparent),
                    center = c, radius = haloR,
                ),
                radius = haloR, center = c, alpha = (0.2f + 0.8f * level).coerceIn(0f, 1f), blendMode = BlendMode.Plus,
            )
            val coreR = radius * (0.75f + 0.35f * level)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(if (level > 0.75f) WarmWhite else SoftYellow, Gold, Color.Transparent),
                    center = c, radius = coreR,
                ),
                radius = coreR, center = c, alpha = (0.15f + 0.6f * level).coerceIn(0f, 1f), blendMode = BlendMode.Plus,
            )
        }
    }
}
