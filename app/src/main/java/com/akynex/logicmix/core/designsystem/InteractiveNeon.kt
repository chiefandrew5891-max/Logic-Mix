package com.akynex.logicmix.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Central parameters of the interactive states. Default = no glow; Pressed = transient neon rim with outer and inner glow
 * (set at touch down); Selected = persistent, shown as a gradient fill plus a softer glow. Dark and light themes have
 * separate values: the light theme uses a deeper cyan and much lower glow opacity.
 */
object InteractiveTokens {
    const val TransitionMs = 140

    /** Rise time of Pressed: short, so even a quick tap reaches a bright rim; release uses [TransitionMs]. */
    const val PressInMs = 50
    const val SelectedGlow = 0.8f
    private const val RimStrokeDp = 2f

    fun neon(dark: Boolean) = if (dark) Color(0xFF19E6F2) else Color(0xFF0097B8)
    fun outerAlpha(dark: Boolean) = if (dark) 0.26f else 0.14f
    fun innerAlpha(dark: Boolean) = if (dark) 0.36f else 0.18f
    fun rimAlpha(dark: Boolean) = if (dark) 0.95f else 0.85f
    fun fillTop(dark: Boolean) = if (dark) Color(0xFF14D8E4) else Color(0xFF16B5CC)
    fun fillBottom(dark: Boolean) = if (dark) Color(0xFF0A8FB5) else Color(0xFF0A7FA8)

    /** Text and icon colour on top of the selected fill. */
    fun onSelected(dark: Boolean) = if (dark) Color(0xFF04202F) else Color.White

    internal fun rimStroke(density: Float) = RimStrokeDp * density
}

/** Soft halo outside the card edge. */
internal fun DrawScope.drawOuterNeon(glow: Float, r: Float, color: Color, dark: Boolean) {
    for (i in 1..4) {
        val s = i * 3.dp.toPx()
        drawRoundRect(
            color.copy(alpha = InteractiveTokens.outerAlpha(dark) * glow * (5 - i) / 4f),
            topLeft = Offset(-s / 2f, -s / 2f), size = Size(size.width + s, size.height + s),
            cornerRadius = CornerRadius(r + s / 2f), style = Stroke(s),
        )
    }
}

/** Glow growing from the edge towards the centre and fading out, plus a bright rim. Cheap: a few strokes. */
internal fun DrawScope.drawInnerNeon(glow: Float, r: Float, color: Color, dark: Boolean) {
    val step = 3.dp.toPx()
    val n = 6
    for (i in 0 until n) {
        val inset = i * step
        val fall = 1f - i / n.toFloat()
        drawRoundRect(
            color.copy(alpha = InteractiveTokens.innerAlpha(dark) * glow * fall * fall),
            topLeft = Offset(inset + step / 2f, inset + step / 2f),
            size = Size(size.width - 2 * inset - step, size.height - 2 * inset - step),
            cornerRadius = CornerRadius((r - inset).coerceAtLeast(0f)), style = Stroke(step),
        )
    }
    val rim = InteractiveTokens.rimStroke(density)
    drawRoundRect(
        color.copy(alpha = InteractiveTokens.rimAlpha(dark) * glow),
        topLeft = Offset(rim / 2f, rim / 2f), size = Size(size.width - rim, size.height - rim),
        cornerRadius = CornerRadius(r), style = Stroke(rim),
    )
}

/**
 * Shared interactive state for selectable or tappable surfaces that draw their own background: neon rim, outer and inner
 * glow while pressed, and a gradient fill with a softer glow while [selected]. Values animate in [InteractiveTokens.TransitionMs]
 * and are read in the draw phase, so no recomposition and no layout change.
 */
@Composable
fun Modifier.interactiveNeon(source: InteractionSource, selected: Boolean, radius: Dp, motion: Boolean = true): Modifier {
    val dark = LogicMixTokens.colors.isDark
    val pressed by source.collectIsPressedAsState()
    val spec = if (motion) tween<Float>(if (pressed) InteractiveTokens.PressInMs else InteractiveTokens.TransitionMs) else snap()
    val press = animateFloatAsState(if (pressed) 1f else 0f, spec, label = "neonPress")
    val sel = animateFloatAsState(if (selected) 1f else 0f, spec, label = "neonSelected")
    return drawWithCache {
        val r = radius.toPx()
        val fill = Brush.verticalGradient(listOf(InteractiveTokens.fillTop(dark), InteractiveTokens.fillBottom(dark)))
        val color = InteractiveTokens.neon(dark)
        onDrawWithContent {
            val s = sel.value
            if (s > 0.01f) drawRoundRect(fill, cornerRadius = CornerRadius(r), alpha = s)
            drawContent()
            val g = maxOf(press.value, s * InteractiveTokens.SelectedGlow)
            if (g > 0.01f) {
                drawOuterNeon(g, r, color, dark)
                drawInnerNeon(g, r, color, dark)
            }
        }
    }
}

/**
 * Emits Press into [source] at the very first touch. clickable inside a scrolling parent delays its own Press by the tap
 * timeout, so a quick tap would show no feedback; this makes Pressed start on touch down and end on release, cancel, or
 * when the parent takes the gesture over to scroll. The touch is not consumed, so clicking and scrolling are unchanged.
 */
fun Modifier.instantPress(source: MutableInteractionSource): Modifier = pointerInput(source) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val press = PressInteraction.Press(down.position)
        source.tryEmit(press)
        val up = waitForUpOrCancellation()
        source.tryEmit(if (up != null) PressInteraction.Release(press) else PressInteraction.Cancel(press))
    }
}
