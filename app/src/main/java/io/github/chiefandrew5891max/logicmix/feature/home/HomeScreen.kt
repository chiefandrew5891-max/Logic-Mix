package io.github.chiefandrew5891max.logicmix.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.chiefandrew5891max.logicmix.R
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixTokens
import io.github.chiefandrew5891max.logicmix.domain.game.GameMode

@Composable
fun HomeRoute(
    onPlay: (GameMode) -> Unit,
    onRewards: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    HomeScreen(state, onPlay, onRewards, onSettings)
}

private val Categories = listOf(
    "🔥" to R.string.category_matchstick,
    "🔢" to R.string.category_number_sequence,
    "🔤" to R.string.category_word_logic,
    "🧩" to R.string.category_mini_crossword,
    "🔷" to R.string.category_visual_logic,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onPlay: (GameMode) -> Unit,
    onRewards: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = LogicMixTokens.colors
    Box(Modifier.fillMaxSize().background(colors.backgroundBrush)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LogicMixTokens.spacing, vertical = LogicMixTokens.spacing)
                .padding(top = LogicMixTokens.spacingL)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacing),
        ) {
            TopBar(onSettings)
            PlayerCard(state)
            PrimaryButton(
                label = stringResource(
                    if (state.primaryAction == PrimaryAction.CONTINUE) R.string.home_continue else R.string.home_start_game,
                ),
                onClick = { onPlay(GameMode.CAREER) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM)) {
                FeatureCard(
                    glyph = "📅",
                    title = stringResource(R.string.home_daily_title),
                    subtitle = stringResource(R.string.home_daily_subtitle),
                    onClick = { onPlay(GameMode.DAILY) },
                    modifier = Modifier.weight(1f),
                )
                FeatureCard(
                    glyph = "🏆",
                    title = stringResource(R.string.home_rewards_title),
                    subtitle = stringResource(R.string.home_rewards_subtitle),
                    onClick = onRewards,
                    modifier = Modifier.weight(1f),
                    gold = true,
                )
            }
            SectionTitle(stringResource(R.string.home_modes_title))
            Row(horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM)) {
                ModeCard(
                    "🏔️", stringResource(R.string.mode_career), stringResource(R.string.mode_career_subtitle),
                    { onPlay(GameMode.CAREER) }, Modifier.weight(1f),
                )
                ModeCard(
                    "♾️", stringResource(R.string.mode_endless), stringResource(R.string.mode_endless_subtitle),
                    { onPlay(GameMode.ENDLESS) }, Modifier.weight(1f),
                )
            }
            SectionTitle(stringResource(R.string.home_categories_title))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Categories.forEach { (glyph, label) -> CategoryChip(glyph, stringResource(label)) }
            }
        }
    }
}

@Composable
private fun TopBar(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.home_title),
            style = LogicMixTokens.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(LogicMixTokens.minTouchTarget)
                .clip(LogicMixTokens.radii.medium)
                .background(LogicMixTokens.colors.cardBrush)
                .border(BorderStroke(1.dp, LogicMixTokens.colors.cardBorder), LogicMixTokens.radii.medium)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.settings), onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Text("⚙️", style = LogicMixTokens.typography.titleLarge)
        }
    }
}

@Composable
private fun PlayerCard(state: HomeUiState) {
    val colors = LogicMixTokens.colors
    CardSurface(Modifier.fillMaxWidth(), LogicMixTokens.radii.large) {
        Row(Modifier.padding(LogicMixTokens.spacing), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS)) {
                Text(
                    text = stringResource(R.string.home_level, state.level),
                    style = LogicMixTokens.typography.titleLarge,
                    color = colors.gold,
                )
                XpBar(state.xpFraction)
                Text(
                    text = state.xpForNextLevel?.let { stringResource(R.string.home_xp_progress, state.xpIntoLevel, it) }
                        ?: stringResource(R.string.home_xp_max),
                    style = LogicMixTokens.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                Modifier.padding(start = LogicMixTokens.spacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🔥", style = LogicMixTokens.typography.headlineMedium)
                Text(
                    text = state.streak.toString(),
                    style = LogicMixTokens.typography.headlineMedium,
                    color = colors.gold,
                )
                Text(
                    text = stringResource(R.string.home_streak),
                    style = LogicMixTokens.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun XpBar(fraction: Float) {
    val colors = LogicMixTokens.colors
    val shape = RoundedCornerShape(50)
    Box(Modifier.fillMaxWidth().height(12.dp).clip(shape).background(colors.xpTrack)) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction).height(12.dp).clip(shape).background(colors.xpFill))
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    val colors = LogicMixTokens.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LogicMixTokens.buttonHeight)
            .clip(LogicMixTokens.radii.extraLarge)
            .background(colors.primaryActionBrush)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = LogicMixTokens.spacingL, vertical = LogicMixTokens.spacingM),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "▶  $label".uppercase(),
            style = LogicMixTokens.typography.headlineMedium,
            color = colors.onGold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FeatureCard(
    glyph: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
) {
    val brush = if (gold) LogicMixTokens.colors.rewardBrush else LogicMixTokens.colors.cardBrush
    CardSurface(modifier.clickable(role = Role.Button, onClick = onClick), LogicMixTokens.radii.large, brush) {
        Column(Modifier.padding(LogicMixTokens.spacingM), verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingXs)) {
            Text(glyph, style = LogicMixTokens.typography.headlineMedium)
            Text(title, style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = LogicMixTokens.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModeCard(glyph: String, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardSurface(modifier.clickable(role = Role.Button, onClick = onClick), LogicMixTokens.radii.large) {
        Column(
            Modifier.fillMaxWidth().padding(LogicMixTokens.spacingM),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingXs),
        ) {
            Text(glyph, style = LogicMixTokens.typography.headlineMedium)
            Text(title, style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Text(subtitle, style = LogicMixTokens.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

// Informational only: category selection belongs to the settings screen, so this is deliberately not clickable.
@Composable
private fun CategoryChip(glyph: String, label: String) {
    CardSurface(shape = LogicMixTokens.radii.extraLarge) {
        Row(
            Modifier.padding(horizontal = LogicMixTokens.spacingM, vertical = LogicMixTokens.spacingS),
            horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(glyph, style = LogicMixTokens.typography.bodyLarge)
            Text(label, style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = LogicMixTokens.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CardSurface(
    modifier: Modifier = Modifier,
    shape: Shape = LogicMixTokens.radii.large,
    brush: Brush = LogicMixTokens.colors.cardBrush,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(brush)
            .border(BorderStroke(1.dp, LogicMixTokens.colors.cardBorder), shape),
    ) { content() }
}
