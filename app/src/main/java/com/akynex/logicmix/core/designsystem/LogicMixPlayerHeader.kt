package com.akynex.logicmix.core.designsystem

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import com.akynex.logicmix.R
import com.akynex.logicmix.core.audio.withTap
import kotlinx.coroutines.delay

/**
 * Geometry of `logo_logic_mix_player.png` (1774x887) in normalized image coordinates (0..1, origin top-left).
 * Measured on the oval plate: the three buttons share one axis, the lights below the plate stay visible.
 */
object LogoPlayerGeometry {
    const val ASPECT = 1774f / 887f
    const val AXIS_Y = 0.735f
    const val SIDE_DX = 0.16f
    /** Diameters are fractions of the image HEIGHT. */
    const val CENTER_DIAMETER = 0.21f
    const val SIDE_DIAMETER = 0.165f

    data class Slot(val cx: Float, val cy: Float, val diameter: Float)

    val previous = Slot(0.5f - SIDE_DX, AXIS_Y, SIDE_DIAMETER)
    val playPause = Slot(0.5f, AXIS_Y, CENTER_DIAMETER)
    val next = Slot(0.5f + SIDE_DX, AXIS_Y, SIDE_DIAMETER)
    /** Bottom-right corner, right of the plate and below the X of MIX. */
    val settings = Slot(0.91f, 0.84f, 0.1f)

    /** Rightmost visible pixel of the logo (neon outline), as a fraction of the image width; the PNG has transparent margins. */
    const val LOGO_RIGHT = 0.99f
    /** Gear drawn at 1.5x its former 30dp; its outline spans 11..85 (+2.25 stroke) of the 96 viewport, so the centre sits this far from the right edge. */
    const val GEAR_ICON_DP = 45f
    const val GEAR_HALF_EXTENT_DP = GEAR_ICON_DP * 39.25f / 96f

    /** Rectangle actually painted by ContentScale.Fit inside a container; slots must be mapped into it. */
    data class Fit(val left: Float, val top: Float, val width: Float, val height: Float)

    fun fit(containerW: Float, containerH: Float): Fit {
        val s = minOf(containerW, containerH * ASPECT)
        val h = s / ASPECT
        return Fit((containerW - s) / 2f, (containerH - h) / 2f, s, h)
    }

    /** Touch size: at least the visual size, up to 48dp, but never so big that neighbours overlap. */
    fun touchSize(imageWidth: Float, visual: Float, minTouch: Float = 48f): Float =
        maxOf(visual, minOf(minTouch, SIDE_DX * imageWidth))
}

private val Teal = Color(0xFF2EE6D6)
private val Gold = Color(0xFFFFB41F)
private val Surface = Brush.verticalGradient(listOf(Color(0xFF2B2A7A), Color(0xFF12103A)))

/** Logo PNG with the three real, tappable player buttons laid over its plate; falls back to nothing if the PNG is missing. */
@Composable
fun LogicMixPlayerHeader(
    playing: Boolean,
    available: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    previousLabel: String,
    playPauseLabel: String,
    nextLabel: String,
    logoLabel: String,
    onSettings: (() -> Unit)? = null,
    settingsLabel: String = "",
    modifier: Modifier = Modifier,
    maxWidth: Dp = 560.dp,
) {
    val logoId = rememberDrawableId("logo_logic_mix_player")
    var kickTick by remember { mutableStateOf(0) }
    var kickForward by remember { mutableStateOf(true) }
    BoxWithConstraints(modifier.widthIn(max = maxWidth).fillMaxWidth().aspectRatio(LogoPlayerGeometry.ASPECT)) {
        val w = this.maxWidth
        val h = this.maxHeight
        if (logoId != 0) {
            Image(painterResource(logoId), logoLabel, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
        }
        if (logoId != 0) {
            LogicMixNeonOverlay(logoId, kickTick, Modifier.matchParentSize())
            LogicMixGarlandOverlay(playing, kickTick, kickForward, Modifier.matchParentSize())
        }
        val slots = listOf(
            Triple(LogoPlayerGeometry.previous, R.drawable.ic_music_prev, Triple(previousLabel, { kickForward = false; kickTick++; onPrevious() }, Teal)),
            Triple(
                LogoPlayerGeometry.playPause,
                if (playing) R.drawable.ic_music_pause else R.drawable.ic_play,
                Triple(playPauseLabel, onTogglePlayback, Gold),
            ),
            Triple(LogoPlayerGeometry.next, R.drawable.ic_music_next, Triple(nextLabel, { kickForward = true; kickTick++; onNext() }, Teal)),
        )
        if (onSettings != null) {
            val gear = LogoPlayerGeometry.settings
            val size = maxOf(48f, w.value * 0.08f).dp
            val cx = w * LogoPlayerGeometry.LOGO_RIGHT - LogoPlayerGeometry.GEAR_HALF_EXTENT_DP.dp
            SettingsGear(
                label = settingsLabel,
                onClick = onSettings,
                touch = size,
                modifier = Modifier.offset(cx - size / 2, h * gear.cy - size / 2),
            )
        }
        slots.forEachIndexed { index, (slot, icon, spec) ->
            val visual = h * slot.diameter
            val touch = LogoPlayerGeometry.touchSize(w.value, visual.value).dp
            PlayerButton(
                icon = icon,
                label = spec.first,
                rim = spec.third,
                visual = visual,
                touch = touch,
                enabled = available,
                active = index == 1 && playing,
                flashOnClick = index != 1,
                onClick = spec.second,
                modifier = Modifier.offset(w * slot.cx - touch / 2, h * slot.cy - touch / 2),
            )
        }
    }
}

@Composable
private fun PlayerButton(
    @DrawableRes icon: Int,
    label: String,
    rim: Color,
    visual: Dp,
    touch: Dp,
    enabled: Boolean,
    active: Boolean,
    flashOnClick: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var flash by remember { mutableStateOf(false) }
    var clicks by remember { mutableStateOf(0) }
    LaunchedEffect(clicks) {
        if (clicks > 0 && flashOnClick) {
            flash = true
            delay(260)
            flash = false
        }
    }
    val spec = tween<Float>(if (animate) 120 else 0)
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spec, label = "playerScale")
    val glow by animateFloatAsState(
        if (pressed || flash) 1f else if (active) 0.85f else 0.6f, spec, label = "playerGlow",
    )
    val tap = withTap { clicks++; onClick() }
    Box(
        modifier
            .size(touch)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { contentDescription = label }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = tap),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(visual)
                .scale(scale)
                .clip(CircleShape)
                .background(Surface)
                .border(BorderStroke(visual * 0.07f, rim.copy(alpha = glow)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val tint = if (rim == Gold) Gold else Color(0xFFE6F6FF)
            LogicMixIcon(icon, tint.copy(alpha = if (pressed || flash) 1f else 0.9f), size = visual * 0.5f)
        }
    }
}

/** Bare gear icon: no frame or background; a press only dims and shrinks it. */
@Composable
private fun SettingsGear(label: String, onClick: () -> Unit, touch: Dp, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, tween(120), label = "gearScale")
    val tap = withTap(onClick)
    Box(
        modifier
            .size(touch)
            .semantics { contentDescription = label }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = tap),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.scale(scale).alpha(if (pressed) 0.7f else 1f)) { LogicMixSettingsIcon(size = LogoPlayerGeometry.GEAR_ICON_DP.dp) }
    }
}
