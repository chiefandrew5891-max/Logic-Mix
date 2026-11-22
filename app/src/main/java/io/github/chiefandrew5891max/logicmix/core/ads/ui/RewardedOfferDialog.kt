package io.github.chiefandrew5891max.logicmix.core.ads.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.chiefandrew5891max.logicmix.R
import io.github.chiefandrew5891max.logicmix.core.ads.OfferState
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixTokens

/**
 * Добровольное предложение rewarded-рекламы. Компонент без бизнес-логики: состояние и действия приходят снаружи.
 * Игрок всегда может отказаться; недоступность или ошибка рекламы игру не блокирует.
 * [onShown] вызывается один раз за появление диалога (повторная композиция событие не дублирует).
 */
@Composable
fun RewardedOfferDialog(
    state: OfferState,
    onWatch: () -> Unit,
    onDismiss: () -> Unit,
    onShown: () -> Unit = {},
    message: String = stringResource(R.string.ad_offer_hint),
) {
    LaunchedEffect(Unit) { onShown() }
    val colors = LogicMixTokens.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .background(colors.cardBrush, LogicMixTokens.radii.large)
                .border(BorderStroke(1.dp, colors.cardBorder), LogicMixTokens.radii.large)
                .padding(LogicMixTokens.spacing),
            verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM),
        ) {
            Text(
                stringResource(R.string.ad_label),
                style = LogicMixTokens.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(message, style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            when (state) {
                OfferState.LOADING, OfferState.SHOWING -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM),
                ) {
                    CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.ad_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OfferState.UNAVAILABLE -> Text(stringResource(R.string.ad_unavailable), color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = LogicMixTokens.minTouchTarget)) {
                    Text(stringResource(R.string.ad_not_now))
                }
                Button(
                    onClick = onWatch,
                    enabled = state == OfferState.READY,
                    modifier = Modifier.weight(1f).heightIn(min = LogicMixTokens.minTouchTarget),
                ) { Text(stringResource(R.string.ad_watch)) }
            }
        }
    }
}
