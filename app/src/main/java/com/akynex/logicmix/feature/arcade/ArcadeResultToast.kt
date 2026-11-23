package com.akynex.logicmix.feature.arcade

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.domain.arcade.ArcadeAchievement
import kotlinx.coroutines.delay

private val Gold = Color(0xFFF5C84B)
private val GoldSoft = Color(0xFFFFE9A8)
private const val TOAST_MS = 1300L

/**
 * Compact result of an Arcade stage: "+20 XP" (and Arcade points) in a small card with a short pop. No full-screen scene and
 * no long sound; the big fanfare is reserved for a new player level (see LevelUpHost). Tap to continue at once.
 */
@Composable
fun ArcadeResultToast(c: Celebration, viewModel: ArcadeViewModel) {
    val context = LocalContext.current
    val reduced = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val pop = remember(c.id) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(c.id) { viewModel.playCelebrationSounds(c) }
    LaunchedEffect(c.id) {
        if (!reduced) pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(c.id) {
        delay(if (c.skipped) 900 else if (c.achievements.isEmpty()) TOAST_MS else TOAST_MS + 900)
        viewModel.celebrationDone(c.id)
    }
    Box(
        Modifier.fillMaxSize().safeDrawingPadding().clickable { viewModel.celebrationDone(c.id) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .padding(bottom = 96.dp)
                .graphicsLayer { val k = 0.7f + 0.3f * pop.value; scaleX = k; scaleY = k; alpha = pop.value.coerceIn(0f, 1f) }
                .background(Color(0xE6101A33), RoundedCornerShape(20.dp))
                .padding(horizontal = 28.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (c.skipped) {
                Text(stringResource(R.string.arcade_stage_skipped), style = LogicMixTokens.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
            } else {
                Text(
                    if (c.xp > 0) stringResource(R.string.xp_gain_compact, c.xp) else stringResource(R.string.xp_gain_none),
                    style = LogicMixTokens.typography.headlineMedium, color = Gold,
                )
                Text(stringResource(R.string.arcade_points_compact, c.award), style = LogicMixTokens.typography.labelMedium, color = GoldSoft)
                c.achievements.forEach {
                    Text(stringResource(R.string.arcade_achievement_unlocked, stringResource(it.nameRes())), style = LogicMixTokens.typography.labelMedium, color = GoldSoft, textAlign = TextAlign.Center)
                }
            }
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
