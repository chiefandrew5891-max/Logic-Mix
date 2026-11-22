package com.akynex.logicmix.feature.arcade

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.feature.game.Card

/** Goal and progress of an Arcade stage, shown above the board of games that have no natural finish. */
@Composable
fun ArcadeGoalBanner(text: String, progress: Int, goal: Int) {
    Card {
        Text(
            text,
            style = LogicMixTokens.typography.titleMedium,
            color = LogicMixTokens.colors.gold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        LinearProgressIndicator(
            progress = { (progress.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = LogicMixTokens.colors.gold,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        )
    }
}
