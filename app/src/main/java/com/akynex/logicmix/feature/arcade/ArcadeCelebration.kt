package com.akynex.logicmix.feature.arcade

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.GoldStar
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.domain.arcade.ArcadeAchievement
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Gold = Color(0xFFF5C84B)
private val GoldSoft = Color(0xFFFFE9A8)
private val ConfettiColors = listOf(Gold, GoldSoft, Color(0xFF4FD1C5), Color(0xFF63B3ED), Color(0xFFF6AD55), Color(0xFFFFFFFF))

/** Short celebration over the finished stage. Draws on Canvas only; a tap ends it early and it ends itself after 1.8 s (LEVEL UP: 2.8 s). */
@Composable
fun ArcadeCelebration(c: Celebration, viewModel: ArcadeViewModel) {
    val progress = remember(c.id) { Animatable(0f) }
    val pop = remember(c.id) { Animatable(0f) }
    val points = remember(c.id) { Animatable(0f) }
    val duration = if (c.levelUp) 2800 else 1800
    LaunchedEffect(c.id) {
        viewModel.playCelebrationSounds(c)
    }
    LaunchedEffect(c.id) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(c.id) {
        points.animateTo(c.award.toFloat(), tween(900))
    }
    LaunchedEffect(c.id) {
        progress.animateTo(1f, tween(duration))
        viewModel.celebrationDone(c.id)
    }
    val confetti = remember(c.id) { buildConfetti(c.id.hashCode(), if (c.levelUp) 90 else 36) }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(onClick = { viewModel.celebrationDone(c.id) })
            .background(Brush.radialGradient(listOf(Gold.copy(alpha = if (c.skipped) 0f else 0.30f), Color(0xCC050B1F)))),
        contentAlignment = Alignment.Center,
    ) {
        if (!c.skipped) {
            Canvas(Modifier.fillMaxSize()) {
                drawConfetti(confetti, progress.value)
                if (c.levelUp) drawFanfares(progress.value)
            }
        }
        Column(Modifier.padding(LogicMixTokens.spacingL).scale(0.8f + 0.2f * pop.value), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS)) {
            if (c.skipped) {
                Text(stringResource(R.string.arcade_stage_skipped), style = LogicMixTokens.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
            } else {
                GoldStar(Modifier.scale(pop.value), size = if (c.levelUp) 72.dp else 56.dp)
                Text(
                    stringResource(if (c.levelUp) R.string.arcade_level_up else R.string.arcade_stage_done),
                    style = LogicMixTokens.typography.headlineMedium, color = Gold, textAlign = TextAlign.Center,
                )
                Text("+${points.value.toInt()}", style = LogicMixTokens.typography.headlineMedium, color = Color.White)
                if (c.bonus > 0) {
                    Text(stringResource(R.string.arcade_bonus, c.bonus), style = LogicMixTokens.typography.titleMedium, color = GoldSoft)
                }
                if (c.levelUp) {
                    Text(stringResource(R.string.arcade_level_n, c.level), style = LogicMixTokens.typography.headlineMedium, color = Color.White)
                }
                c.achievements.forEach {
                    Text(stringResource(R.string.arcade_achievement_unlocked, stringResource(it.nameRes())), style = LogicMixTokens.typography.labelMedium, color = GoldSoft, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private class Piece(val x: Float, val delay: Float, val speed: Float, val sway: Float, val size: Float, val color: Color, val spin: Float)

private fun buildConfetti(seed: Int, count: Int): List<Piece> {
    val r = Random(seed)
    return List(count) {
        Piece(r.nextFloat(), r.nextFloat() * 0.35f, 0.7f + r.nextFloat() * 0.6f, r.nextFloat() * 6f, 5f + r.nextFloat() * 7f, ConfettiColors[r.nextInt(ConfettiColors.size)], r.nextFloat() * 360f)
    }
}

private fun DrawScope.drawConfetti(pieces: List<Piece>, t: Float) {
    pieces.forEach { p ->
        val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
        if (local <= 0f || local >= 1f) return@forEach
        val x = p.x * size.width + sin(local * p.sway * 3f) * 24f
        val y = -20f + local * p.speed * (size.height + 40f)
        val alpha = if (local > 0.8f) (1f - local) / 0.2f else 1f
        rotate(p.spin + local * 360f, Offset(x, y)) {
            drawRect(p.color.copy(alpha = alpha), Offset(x - p.size / 2, y - p.size / 4), Size(p.size, p.size / 2))
        }
    }
}

/** Two horn-shaped fanfares at the screen edges, with short rays that flash as the effect starts. */
private fun DrawScope.drawFanfares(t: Float) {
    val appear = (t * 4f).coerceIn(0f, 1f)
    val fade = if (t > 0.85f) (1f - t) / 0.15f else 1f
    val alpha = appear * fade
    val h = size.height * 0.42f
    listOf(1f, -1f).forEach { dir ->
        // dir = +1 draws the horn on the left edge pointing inward, -1 on the right edge.
        val base = Offset(if (dir > 0) 0f else size.width, h)
        val mouth = Offset(base.x + dir * size.width * 0.16f, base.y)
        val horn = Path().apply {
            moveTo(base.x, base.y)
            lineTo(mouth.x, mouth.y - 38f)
            lineTo(mouth.x, mouth.y + 38f)
            close()
        }
        drawPath(horn, Brush.linearGradient(listOf(GoldSoft.copy(alpha = alpha), Gold.copy(alpha = alpha))))
        repeat(5) { i ->
            val angle = Math.toRadians(-50.0 + i * 25.0)
            val end = Offset(mouth.x + cos(angle).toFloat() * dir * 46f * appear + dir * 8f, mouth.y + sin(angle).toFloat() * 46f * appear)
            drawLine(Gold.copy(alpha = alpha * 0.8f), mouth, end, strokeWidth = 4f)
        }
    }
}

private fun ArcadeAchievement.nameRes(): Int = when (this) {
    ArcadeAchievement.STREAK_3 -> R.string.arcade_ach_streak_3
    ArcadeAchievement.STREAK_5 -> R.string.arcade_ach_streak_5
    ArcadeAchievement.STREAK_10 -> R.string.arcade_ach_streak_10
    ArcadeAchievement.FIRST_CYCLE -> R.string.arcade_ach_first_cycle
    ArcadeAchievement.FULL_SET_CYCLE -> R.string.arcade_ach_full_set
    ArcadeAchievement.NO_HINTS -> R.string.arcade_ach_no_hints
    ArcadeAchievement.NEW_RECORD -> R.string.arcade_ach_record
}
