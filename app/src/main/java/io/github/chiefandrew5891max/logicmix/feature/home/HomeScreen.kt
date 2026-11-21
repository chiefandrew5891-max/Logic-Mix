package io.github.chiefandrew5891max.logicmix.feature.home

import io.github.chiefandrew5891max.logicmix.core.audio.withTap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import io.github.chiefandrew5891max.logicmix.core.designsystem.rememberDrawableId
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixPlayerHeader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.chiefandrew5891max.logicmix.R
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixIcon
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixSettingsIcon
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixTokens
import io.github.chiefandrew5891max.logicmix.domain.content.GameCategory
import io.github.chiefandrew5891max.logicmix.domain.game.GameMode

@Composable
fun HomeRoute(
    onPlay: (GameMode) -> Unit,
    onRewards: () -> Unit,
    onSettings: () -> Unit,
    onWordSearch: () -> Unit = {},
    onCategory: (GameCategory) -> Unit = {},
    onPrimary: (HomeUiState) -> Unit = { onPlay(GameMode.CAREER) },
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val music by viewModel.music.collectAsState()
    HomeScreen(
        state, onPlay, onRewards, onSettings, onWordSearch, onCategory, { onPrimary(state) },
        music = music,
        onPreviousTrack = viewModel::previousTrack,
        onTogglePlayback = viewModel::togglePlayback,
        onNextTrack = viewModel::nextTrack,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onPlay: (GameMode) -> Unit,
    onRewards: () -> Unit,
    onSettings: () -> Unit,
    onWordSearch: () -> Unit = {},
    onCategory: (GameCategory) -> Unit = {},
    onPrimary: () -> Unit = { onPlay(GameMode.CAREER) },
    music: MusicPanelState = MusicPanelState(),
    onPreviousTrack: () -> Unit = {},
    onTogglePlayback: () -> Unit = {},
    onNextTrack: () -> Unit = {},
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
            TopBar(onSettings, music, onPreviousTrack, onTogglePlayback, onNextTrack)
            PlayerCard(state)
            PrimaryButton(
                label = stringResource(
                    if (state.primaryAction == PrimaryAction.CONTINUE) R.string.home_continue else R.string.home_start_game,
                ),
                onClick = onPrimary,
            )
            EqualHeightRow {
                FeatureCard(
                    icon = R.drawable.ic_daily,
                    art = "icon_daily_challenge",
                    title = stringResource(R.string.home_daily_title),
                    subtitle = stringResource(R.string.home_daily_subtitle),
                    onClick = { onPlay(GameMode.DAILY) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                FeatureCard(
                    icon = R.drawable.ic_rewards,
                    art = "icon_rewards_trophy",
                    title = stringResource(R.string.home_rewards_title),
                    subtitle = stringResource(R.string.home_rewards_subtitle),
                    onClick = onRewards,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    gold = true,
                )
            }
            SectionTitle(stringResource(R.string.home_modes_title))
            EqualHeightRow {
                ModeCard(
                    R.drawable.ic_career, "icon_career", stringResource(R.string.mode_career), stringResource(R.string.mode_career_subtitle),
                    { onPlay(GameMode.CAREER) }, Modifier.weight(1f).fillMaxHeight(),
                )
                ModeCard(
                    R.drawable.ic_endless, "icon_endless_mode", stringResource(R.string.mode_endless), stringResource(R.string.mode_endless_subtitle),
                    { onPlay(GameMode.ENDLESS) }, Modifier.weight(1f).fillMaxHeight(),
                )
            }
            SectionTitle(stringResource(R.string.home_categories_title))
            CategoryGrid(categories = GameCategory.entries.filter { it in state.categories }, onClick = onCategory)
        }
    }
}

@Composable
private fun TopBar(onSettings: () -> Unit, music: MusicPanelState, onPrevious: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    val settingsLabel = stringResource(R.string.settings)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS)) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
            LogicMixPlayerHeader(
                playing = music.playing,
                available = music.available,
                onPrevious = onPrevious,
                onTogglePlayback = onToggle,
                onNext = onNext,
                previousLabel = stringResource(R.string.music_previous),
                playPauseLabel = stringResource(if (music.playing) R.string.music_pause else R.string.music_play),
                nextLabel = stringResource(R.string.music_next),
                logoLabel = stringResource(R.string.app_name),
            )
        }
        Box(
            modifier = Modifier
                .size(LogicMixTokens.minTouchTarget)
                .clip(LogicMixTokens.radii.medium)
                .background(LogicMixTokens.colors.cardBrush)
                .border(BorderStroke(1.dp, LogicMixTokens.colors.cardBorder), LogicMixTokens.radii.medium)
                .semantics { contentDescription = settingsLabel }
                .clickable(role = Role.Button, onClickLabel = settingsLabel, onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            LogicMixSettingsIcon(size = 28.dp)
        }
    }
}

/** The 3D PNG from res/drawable-nodpi when it exists (untinted, proportions kept); otherwise the old tinted vector. */
@Composable
private fun CardArt(art: String, @DrawableRes fallback: Int, tint: Color) {
    val id = rememberDrawableId(art)
    if (id != 0) {
        Image(painterResource(id), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(40.dp))
    } else {
        LogicMixIcon(fallback, tint, size = 32.dp)
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
