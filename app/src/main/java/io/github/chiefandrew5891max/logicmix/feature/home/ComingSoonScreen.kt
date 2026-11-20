package io.github.chiefandrew5891max.logicmix.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.github.chiefandrew5891max.logicmix.R
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixTokens

/** Honest destination for modes whose engines are not implemented yet; replaced by the game screen later. */
@Composable
fun ComingSoonScreen(onBack: () -> Unit, @StringRes categoryTitle: Int? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LogicMixTokens.colors.backgroundBrush)
            .padding(LogicMixTokens.spacingL),
        verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacing, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        categoryTitle?.let {
            Text(stringResource(it), style = LogicMixTokens.typography.titleLarge, color = LogicMixTokens.colors.gold, textAlign = TextAlign.Center)
        }
        Text(
            stringResource(R.string.coming_soon_title),
            style = LogicMixTokens.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.coming_soon_body),
            style = LogicMixTokens.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}
