package com.akynex.logicmix.feature.privacy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.LogicMixIcon
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.core.privacy.PolicyBlock
import com.akynex.logicmix.core.privacy.PrivacyPolicyParser

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, viewModel: PrivacyPolicyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState(initial = null)
    val colors = LogicMixTokens.colors
    val uriHandler = LocalUriHandler.current
    Box(Modifier.fillMaxSize().background(colors.backgroundBrush).safeDrawingPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LogicMixTokens.spacing)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM)) {
                Box(
                    Modifier
                        .size(LogicMixTokens.minTouchTarget)
                        .clip(LogicMixTokens.radii.medium)
                        .background(colors.cardBrush)
                        .border(BorderStroke(1.dp, colors.cardBorder), LogicMixTokens.radii.medium)
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    LogicMixIcon(R.drawable.ic_back, MaterialTheme.colorScheme.onSurface, contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.privacy_policy_title), style = LogicMixTokens.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            }
            val s = state ?: return@Column
            val url = s.config.onlineUrl
            TextButton(onClick = { url?.let(uriHandler::openUri) }, enabled = url != null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.privacy_policy_view_online))
            }
            if (url == null) {
                Text(stringResource(R.string.privacy_policy_online_unavailable), style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.isDraft) {
                Text(stringResource(R.string.privacy_policy_draft), style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
            s.blocks.forEach { b ->
                when (b) {
                    is PolicyBlock.Title -> Unit
                    is PolicyBlock.Meta -> Text(b.text, style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is PolicyBlock.Heading -> Text(b.text, style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = LogicMixTokens.spacingM))
                    is PolicyBlock.Paragraph -> Text(b.text, style = LogicMixTokens.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    is PolicyBlock.Bullet -> {
                        val link = PrivacyPolicyParser.firstUrl(b.text)
                        Text(
                            "• " + b.text,
                            style = LogicMixTokens.typography.bodyMedium,
                            color = if (link != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                            modifier = if (link != null) Modifier.clickable(role = Role.Button) { uriHandler.openUri(link) } else Modifier,
                        )
                    }
                }
            }
        }
    }
}
