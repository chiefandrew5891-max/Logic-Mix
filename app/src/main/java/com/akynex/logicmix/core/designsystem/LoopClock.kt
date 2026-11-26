package com.akynex.logicmix.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/** Every looping animation period must divide this, so the clock wrap is invisible. */
const val LOOP_CLOCK_WRAP_SECONDS = 3600.0

/**
 * Continuous animation time in seconds, wrapped at [LOOP_CLOCK_WRAP_SECONDS]. It advances only while the app is visible
 * (it pauses in the background and the screen leaving composition stops it) and resumes without a jump. The value must be
 * read in the draw phase only, so ticking never recomposes. Stays at 0 when system animations are disabled.
 */
@Composable
fun rememberLoopClock(): MutableDoubleState {
    val clock = remember { mutableDoubleStateOf(0.0) }
    val motion = rememberMotionEnabled()
    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000.0).coerceIn(0.0, 0.1)
                last = now
                clock.doubleValue = (clock.doubleValue + dt) % LOOP_CLOCK_WRAP_SECONDS
            }
        }
    }
    return clock
}
