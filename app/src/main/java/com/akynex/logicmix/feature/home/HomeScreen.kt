package com.akynex.logicmix.feature.home

import com.akynex.logicmix.core.designsystem.BrandFooter
import com.akynex.logicmix.core.designsystem.PremiumAuroraBackground
import com.akynex.logicmix.core.designsystem.PremiumAvatar
import com.akynex.logicmix.core.designsystem.PremiumCard
import com.akynex.logicmix.core.designsystem.PremiumGoldButton
import com.akynex.logicmix.core.designsystem.PremiumIconButton
import com.akynex.logicmix.core.designsystem.PremiumProgressBar
import com.akynex.logicmix.core.designsystem.PremiumSectionHeader
import com.akynex.logicmix.core.designsystem.PremiumTokens
import com.akynex.logicmix.core.designsystem.PremiumTone
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
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
    PremiumAuroraBackground(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // All proportions derive from the usable width (capped like the column), never from pixel positions.
            val pad = 12.dp
            val w = minOf(maxWidth, 560.dp) - pad * 2
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = pad, vertical = LogicMixTokens.spacingS)
                    .padding(top = LogicMixTokens.spacingL)
                    .widthIn(max = 560.dp - pad * 2)
                    .align(Alignment.TopCenter),
                verticalArrangement = com.akynex.logicmix.core.designsystem.stickyFooterArrangement(LogicMixTokens.spacingM),
            ) {
                TopBar(onSettings, music, onPreviousTrack, onTogglePlayback, onNextTrack)
                PlayerCard(state, games, w)
                // The cube overlaps the top of the main button so the two read as one composition.
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(-(w * 0.06f))) {
                    HeroCube(Modifier.height(w * 0.42f))
                    BoxWithConstraints(Modifier.fillMaxWidth(0.76f)) {
                        PremiumGoldButton(
                            label = stringResource(
                                if (state.primaryAction == PrimaryAction.CONTINUE) R.string.home_continue else R.string.home_start_game,
                            ),
                            onClick = withTap(onPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            minHeight = w * 0.155f,
                            icon = { LogicMixIcon(R.drawable.ic_play, PremiumTokens.OnGold, size = 26.dp) },
                        )
                    }
                }
                EqualHeightRow {
                    FeatureCard(
                        icon = R.drawable.ic_daily,
                        art = "icon_daily_challenge",
                        title = stringResource(R.string.home_daily_title),
                        subtitle = stringResource(R.string.home_daily_subtitle),
                        onClick = { onPlay(GameMode.DAILY) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        minHeight = w * 0.235f,
                    )
                    FeatureCard(
                        icon = R.drawable.ic_rewards,
                        art = "icon_rewards_trophy",
                        title = stringResource(R.string.home_rewards_title),
                        subtitle = stringResource(R.string.home_rewards_subtitle),
                        onClick = onRewards,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        gold = true,
                        minHeight = w * 0.235f,
                    )
                }
                if (games.status != com.akynex.logicmix.core.games.GamesStatus.DISABLED) {
                    LeaderboardCard(onLeaderboards)
                }
                PremiumSectionHeader(stringResource(R.string.home_modes_title))
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
                PremiumSectionHeader(stringResource(R.string.home_categories_title))
                CategoryGrid(categories = GameCategory.entries.filter { it in state.categories }, onClick = onCategory)
                BrandFooter()
            }
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

/** Compact horizontal profile: avatar | name, level, XP capsule | divider | streak. */
@Composable
private fun PlayerCard(state: HomeUiState, games: com.akynex.logicmix.core.games.GamesState, w: androidx.compose.ui.unit.Dp) {
    val colors = LogicMixTokens.colors
    val signedIn = games.status == com.akynex.logicmix.core.games.GamesStatus.SIGNED_IN
    PremiumCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = w * 0.17f).padding(horizontal = LogicMixTokens.spacingM, vertical = LogicMixTokens.spacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PremiumAvatar(w * 0.15f, image = if (signedIn) games.avatar?.asImageBitmap() else null)
            Column(Modifier.weight(1f).padding(horizontal = LogicMixTokens.spacingM), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (games.status != com.akynex.logicmix.core.games.GamesStatus.DISABLED) {
                    Text(
                        text = if (signedIn && games.name != null) games.name else stringResource(R.string.games_profile_guest),
                        style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(R.string.home_level, state.level),
                    style = LogicMixTokens.typography.titleLarge, color = colors.gold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                PremiumProgressBar(
                    state.xpFraction, Modifier.fillMaxWidth(),
                    label = state.xpForNextLevel?.let { stringResource(R.string.home_xp_progress, state.xpIntoLevel, it) } ?: stringResource(R.string.home_xp_max),
                )
            }
            Box(Modifier.width(1.dp).height(w * 0.1f).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)))
            Column(Modifier.width(w * 0.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                val fire = rememberDrawableId("icon_streak_fire")
                if (fire != 0) {
                    Image(painterResource(fire), null, contentScale = ContentScale.Fit, modifier = Modifier.size(30.dp))
                } else {
                    LogicMixIcon(R.drawable.ic_streak, colors.gold, size = 28.dp)
                }
                Text(text = state.streak.toString(), style = LogicMixTokens.typography.titleLarge, color = colors.gold)
                Text(
                    text = stringResource(R.string.home_streak), style = LogicMixTokens.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The approved cube (pre-composed from the splash layers) with a soft glow, a very slow breathing pulse and a few light motes. */
@Composable
private fun HeroCube(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val motion = remember { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val phase = if (motion) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "heroCube")
        t.animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(7000, easing = androidx.compose.animation.core.LinearEasing)),
            label = "heroPhase",
        ).value
    } else 0f
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.5f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF00D8E8).copy(alpha = 0.28f), Color.Transparent), center = c, radius = size.height * 0.85f),
                radius = size.height * 0.85f, center = c,
            )
            for (i in 0 until 5) {
                val a = (phase + i / 5f) % 1f
                val ang = i * 1.9f
                val x = c.x + kotlin.math.cos(ang) * size.height * (0.55f + 0.1f * i % 3)
                val y = c.y - size.height * 0.45f + (i % 3) * size.height * 0.4f - a * size.height * 0.1f
                drawCircle(Color.White.copy(alpha = 0.6f * kotlin.math.sin(a * Math.PI.toFloat())), radius = 1.6.dp.toPx() + (i % 2) * 0.8.dp.toPx(), center = Offset(x, y))
            }
        }
        Image(
            painterResource(R.drawable.home_cube), null,
            Modifier.fillMaxHeight().graphicsLayer { val s = 1f + 0.015f * kotlin.math.sin(phase * 2f * Math.PI.toFloat()); scaleX = s; scaleY = s },
            contentScale = ContentScale.Fit,
        )
    }
}

// Paired cards: equal width via weight(1f) and equal height via IntrinsicSize.Min + fillMaxHeight() on children.
@Composable
private fun EqualHeightRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS), content = content)
}

/** Compact card: art + title on top, short description and a round arrow below. */
@Composable
private fun FeatureCard(
    @DrawableRes icon: Int,
    art: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = 88.dp,
) {
    PremiumCard(modifier.defaultMinSize(minHeight = minHeight), tone = if (gold) PremiumTone.Violet else PremiumTone.Blue, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(LogicMixTokens.spacingS + 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS)) {
                CardArt(art, icon, if (gold) LogicMixTokens.colors.gold else MaterialTheme.colorScheme.secondary, 44.dp)
                Text(title, Modifier.weight(1f), style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
            }
            Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingXs)) {
                Text(subtitle, Modifier.weight(1f), style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                PremiumIconButton(24.dp, gold = gold)
            }
        }
    }
}

@Composable
private fun LeaderboardCard(onClick: () -> Unit) {
    PremiumCard(Modifier.fillMaxWidth(), tone = PremiumTone.Gold, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = LogicMixTokens.spacingM, vertical = LogicMixTokens.spacingS),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM),
        ) {
            CardArt("icon_leaderboards", R.drawable.ic_rewards, LogicMixTokens.colors.gold, 48.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_leaderboards), style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                Text(stringResource(R.string.home_leaderboards_sub), style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            PremiumIconButton(28.dp, gold = true)
        }
    }
}

@Composable
private fun ModeCard(@DrawableRes icon: Int, art: String, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PremiumCard(modifier, onClick = onClick) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = LogicMixTokens.spacingS, vertical = LogicMixTokens.spacingM),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CardArt(art, icon, MaterialTheme.colorScheme.primary, 52.dp)
            Text(title, style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Text(subtitle, style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** The 3D PNG from res/drawable-nodpi when it exists (untinted, proportions kept); otherwise the old tinted vector. */
@Composable
private fun CardArt(art: String, @DrawableRes fallback: Int, tint: Color, size: androidx.compose.ui.unit.Dp) {
    val id = rememberDrawableId(art)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (id != 0) {
            Image(painterResource(id), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size))
        } else {
            LogicMixIcon(fallback, tint, size = size * 0.62f)
        }
    }
}
