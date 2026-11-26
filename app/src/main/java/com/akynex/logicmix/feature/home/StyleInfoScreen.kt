package com.akynex.logicmix.feature.home

import com.akynex.logicmix.core.designsystem.BrandFooter
import com.akynex.logicmix.core.designsystem.backdropVerticalScroll
import com.akynex.logicmix.core.designsystem.LogicMixScreen
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
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.AssetImage
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.domain.content.GameStyle
import com.akynex.logicmix.feature.gamestyle.titleRes

/**
 * Honest screen for styles whose mixed-puzzle engine does not exist yet. It never starts an unrelated game on
 * its own; playing an implemented category is an explicit choice.
 */
@Composable
fun StyleInfoScreen(style: GameStyle, onHome: () -> Unit, onPlayAvailable: () -> Unit) {
    LogicMixScreen {
        Column(
            Modifier
                .fillMaxSize()
                .backdropVerticalScroll()
                .padding(LogicMixTokens.spacingL)
                .widthIn(max = 560.dp),
            verticalArrangement = com.akynex.logicmix.core.designsystem.stickyFooterArrangement(LogicMixTokens.spacing),
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
            BrandFooter()
        }
    }
}
