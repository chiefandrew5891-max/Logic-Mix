package com.akynex.logicmix.feature.home

import com.akynex.logicmix.core.audio.withTap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.akynex.logicmix.core.designsystem.rememberDrawableId
import com.akynex.logicmix.core.designsystem.LogicMixPlayerHeader
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.graphics.asImageBitmap
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
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.LogicMixIcon
import com.akynex.logicmix.core.designsystem.LogicMixSettingsIcon
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.domain.content.GameCategory
import com.akynex.logicmix.domain.game.GameMode

@Composable
fun HomeRoute(
    onPlay: (GameMode) -> Unit,
    onRewards: () -> Unit,
    onSettings: () -> Unit,
    onWordSearch: () -> Unit = {},
    onCategory: (GameCategory) -> Unit = {},
    onPrimary: (HomeUiState) -> Unit = { onPlay(GameMode.CAREER) },
    onLeaderboards: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val music by viewModel.music.collectAsState()
    val games by viewModel.games.collectAsState()
    HomeScreen(
        state, onPlay, onRewards, onSettings, onWordSearch, onCategory, { onPrimary(state) },
        onLeaderboards = onLeaderboards,
        games = games,
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
    onLeaderboards: () -> Unit = {},
    games: com.akynex.logicmix.core.games.GamesState = com.akynex.logicmix.core.games.GamesState(),
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
            PlayerCard(state, games)
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
            if (games.status != com.akynex.logicmix.core.games.GamesStatus.DISABLED) {
                FeatureCard(
                    icon = R.drawable.ic_rewards,
                    art = "icon_leaderboards",
                    title = stringResource(R.string.home_leaderboards),
                    subtitle = stringResource(R.string.home_leaderboards_sub),
                    onClick = onLeaderboards,
                    modifier = Modifier.fillMaxWidth(),
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
        onSettings = onSettings,
        settingsLabel = stringResource(R.string.settings),
    )
}

@Composable
private fun PlayerCard(state: HomeUiState, games: com.akynex.logicmix.core.games.GamesState) {
    val colors = LogicMixTokens.colors
    CardSurface(Modifier.fillMaxWidth(), LogicMixTokens.radii.large) {
        Row(Modifier.padding(LogicMixTokens.spacing), verticalAlignment = Alignment.CenterVertically) {
            if (games.status == com.akynex.logicmix.core.games.GamesStatus.SIGNED_IN && games.avatar != null) {
                Image(
                    games.avatar.asImageBitmap(), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(end = LogicMixTokens.spacingM).size(48.dp).clip(androidx.compose.foundation.shape.CircleShape),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS)) {
                if (games.status != com.akynex.logicmix.core.games.GamesStatus.DISABLED) {
                    Text(
                        text = if (games.status == com.akynex.logicmix.core.games.GamesStatus.SIGNED_IN && games.name != null) games.name else stringResource(R.string.games_profile_guest),
                        style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
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
                val fire = rememberDrawableId("icon_streak_fire")
                if (fire != 0) {
                    Image(painterResource(fire), null, contentScale = ContentScale.Fit, modifier = Modifier.size(32.dp))
                } else {
                    LogicMixIcon(R.drawable.ic_streak, colors.gold, size = 28.dp)
                }
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
        Row(horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS), verticalAlignment = Alignment.CenterVertically) {
            LogicMixIcon(R.drawable.ic_play, colors.onGold, size = 28.dp)
            Text(
                text = label.uppercase(),
                style = LogicMixTokens.typography.headlineMedium,
                color = colors.onGold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// Paired cards: equal width via weight(1f) and equal height via IntrinsicSize.Min + fillMaxHeight() on children.
@Composable
private fun EqualHeightRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM), content = content)
}

@Composable
private fun FeatureCard(
    @DrawableRes icon: Int,
    art: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
) {
    val brush = if (gold) LogicMixTokens.colors.rewardBrush else LogicMixTokens.colors.cardBrush
    CardSurface(modifier.clickable(role = Role.Button, onClick = onClick), LogicMixTokens.radii.large, brush) {
        Column(Modifier.fillMaxSize().padding(LogicMixTokens.spacingM), verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingXs)) {
            CardArt(art, icon, if (gold) LogicMixTokens.colors.gold else MaterialTheme.colorScheme.secondary)
            Text(title, style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = LogicMixTokens.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModeCard(@DrawableRes icon: Int, art: String, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardSurface(modifier.clickable(role = Role.Button, onClick = onClick), LogicMixTokens.radii.large) {
        Column(
            Modifier.fillMaxSize().padding(LogicMixTokens.spacingM),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingXs),
        ) {
            CardArt(art, icon, MaterialTheme.colorScheme.primary)
            Text(title, style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Text(subtitle, style = LogicMixTokens.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** The 3D PNG from res/drawable-nodpi when it exists (untinted, proportions kept); otherwise the old tinted vector. */
@Composable
private fun CardArt(art: String, @DrawableRes fallback: Int, tint: Color) {
    val id = rememberDrawableId(art)
    // Centered across the whole card width, not the text column.
    Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
        if (id != 0) {
            Image(painterResource(id), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(52.dp))
        } else {
            LogicMixIcon(fallback, tint, size = 32.dp)
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

/** Shared by the progress block and the four feature/mode cards (was 1dp). */
private val CARD_BORDER = 2.dp

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
            .border(BorderStroke(CARD_BORDER, LogicMixTokens.colors.cardBorder), shape),
    ) { content() }
}
