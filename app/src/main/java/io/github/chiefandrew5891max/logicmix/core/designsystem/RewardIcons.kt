package io.github.chiefandrew5891max.logicmix.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared gold reward icons, drawn as vectors so they stay crisp at any size.
 * Placeholders for final artwork: to swap in PNG/WebP, change only these two composables (see IMAGE_ASSETS.md).
 */
object RewardPalette {
    val GoldLight = Color(0xFFFFEE9A)
    val Gold = Color(0xFFFFC72C)
    val GoldDeep = Color(0xFFE08A00)
    val Outline = Color(0xFF9A5A00)
}

private fun DrawScope.starPath(center: Offset, outer: Float, inner: Float): Path {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = -Math.PI / 2 + i * Math.PI / 5
        val x = center.x + (r * cos(a)).toFloat()
        val y = center.y + (r * sin(a)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

@Composable
fun GoldStar(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val art = rememberDrawableId("icon_bonus_star")
    if (art != 0) {
        Image(painterResource(art), null, modifier.size(size), contentScale = ContentScale.Fit)
        return
    }
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height * 0.54f)
        val outer = this.size.minDimension * 0.5f
        val star = starPath(c, outer, outer * 0.46f)
        val stroke = outer * 0.1f
        drawPath(star, Brush.linearGradient(listOf(RewardPalette.GoldLight, RewardPalette.Gold, RewardPalette.GoldDeep), Offset(c.x - outer, c.y - outer), Offset(c.x + outer, c.y + outer)))
        drawPath(star, RewardPalette.Outline, style = Stroke(stroke, join = StrokeJoin.Round))
        drawPath(starPath(c + Offset(-outer * 0.08f, -outer * 0.1f), outer * 0.5f, outer * 0.22f), Color.White.copy(alpha = 0.35f))
    }
}

@Composable
fun GoldTrophy(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val art = rememberDrawableId("icon_rewards_trophy")
    if (art != 0) {
        Image(painterResource(art), null, modifier.size(size), contentScale = ContentScale.Fit)
        return
    }
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val line = w * 0.07f
        val gold = Brush.linearGradient(listOf(RewardPalette.GoldLight, RewardPalette.Gold, RewardPalette.GoldDeep), Offset(0f, 0f), Offset(w, h))
        // Handles first, so the cup overlaps their inner ends.
        for (side in listOf(0.12f to 0.3f, 0.88f to 0.7f)) {
            val x = w * side.first
            val handle = Path().apply {
                moveTo(w * if (side.first < 0.5f) 0.26f else 0.74f, h * 0.17f)
                cubicTo(x - w * 0.14f * (if (side.first < 0.5f) 1 else -1), h * 0.17f, x - w * 0.14f * (if (side.first < 0.5f) 1 else -1), h * 0.5f, w * if (side.first < 0.5f) 0.3f else 0.7f, h * 0.5f)
            }
            drawPath(handle, RewardPalette.GoldDeep, style = Stroke(line * 1.6f, cap = StrokeCap.Round))
        }
        val cup = Path().apply {
            moveTo(w * 0.24f, h * 0.08f)
            lineTo(w * 0.76f, h * 0.08f)
            cubicTo(w * 0.76f, h * 0.5f, w * 0.68f, h * 0.6f, w * 0.5f, h * 0.64f)
            cubicTo(w * 0.32f, h * 0.6f, w * 0.24f, h * 0.5f, w * 0.24f, h * 0.08f)
            close()
        }
        drawPath(cup, gold)
        drawPath(cup, RewardPalette.Outline, style = Stroke(line, join = StrokeJoin.Round))
        drawRect(gold, Offset(w * 0.44f, h * 0.62f), Size(w * 0.12f, h * 0.18f))
        val base = Path().apply {
            moveTo(w * 0.28f, h * 0.92f)
            lineTo(w * 0.34f, h * 0.78f)
            lineTo(w * 0.66f, h * 0.78f)
            lineTo(w * 0.72f, h * 0.92f)
            close()
        }
        drawPath(base, gold)
        drawPath(base, RewardPalette.Outline, style = Stroke(line, join = StrokeJoin.Round))
        drawPath(starPath(Offset(w * 0.5f, h * 0.33f), w * 0.11f, w * 0.05f), Color.White.copy(alpha = 0.7f))
        drawOval(Color.White.copy(alpha = 0.4f), Offset(w * 0.3f, h * 0.14f), Size(w * 0.07f, h * 0.22f))
    }
}
