package io.github.chiefandrew5891max.logicmix.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.chiefandrew5891max.logicmix.R
import io.github.chiefandrew5891max.logicmix.core.designsystem.AssetImage
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixTokens
import io.github.chiefandrew5891max.logicmix.domain.content.GameStyle
import io.github.chiefandrew5891max.logicmix.feature.gamestyle.titleRes

/**
 * Honest screen for styles whose mixed-puzzle engine does not exist yet. It never starts an unrelated game on
 * its own; playing an implemented category is an explicit choice.
 */
@Composable
fun StyleInfoScreen(style: GameStyle, onHome: () -> Unit, onPlayAvailable: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(LogicMixTokens.colors.backgroundBrush)
            .verticalScroll(rememberScrollState())
            .padding(LogicMixTokens.spacingL)
            .widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacing, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AssetImage(style.imageName, Modifier.widthIn(max = 160.dp).fillMaxWidth().padding(bottom = LogicMixTokens.spacingS))
        Text(
            stringResource(style.titleRes()), style = LogicMixTokens.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center,
        )
        Text(
            stringResource(if (style == GameStyle.ARCADE) R.string.style_info_arcade_body else R.string.style_info_trainer_body),
            style = LogicMixTokens.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.coming_soon_body), style = LogicMixTokens.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Button(onPlayAvailable, Modifier.fillMaxWidth()) { Text(stringResource(R.string.style_info_play)) }
        OutlinedButton(onHome, Modifier.fillMaxWidth()) { Text(stringResource(R.string.style_info_home)) }
    }
}
