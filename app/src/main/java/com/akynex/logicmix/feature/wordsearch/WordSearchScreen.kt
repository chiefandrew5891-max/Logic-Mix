package com.akynex.logicmix.feature.wordsearch

import com.akynex.logicmix.core.designsystem.BrandFooter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.GameHeader
import com.akynex.logicmix.core.designsystem.LogicMixIcon
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.domain.wordsearch.Cell
import com.akynex.logicmix.domain.wordsearch.WordSearchLevel
import com.akynex.logicmix.domain.wordsearch.WordSearchRewards
import com.akynex.logicmix.domain.wordsearch.WordSearchSelection
import com.akynex.logicmix.feature.game.ActionButton
import com.akynex.logicmix.feature.game.Card
import kotlin.math.hypot

@Composable
fun WordSearchRoute(onBack: () -> Unit, viewModel: WordSearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    // The language changed to one without word search: progress is already saved, so just leave.
    LaunchedEffect(state.available) { if (!state.available) onBack() }
    WordSearchScreen(state, onBack, viewModel::submit, viewModel::useHint, viewModel::next, viewModel::onTrace, viewModel::restart)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordSearchScreen(
    state: WordSearchUiState,
    onBack: () -> Unit,
    onSubmit: (List<Cell>) -> Unit,
    onHint: () -> Unit,
    onNext: () -> Unit,
    onTrace: (Int) -> Unit = {},
    onRestart: () -> Unit = {},
) {
    val colors = LogicMixTokens.colors
    Box(Modifier.fillMaxSize().background(colors.backgroundBrush).safeDrawingPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LogicMixTokens.spacing)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacing),
        ) {
            Header(state, onBack, onRestart)
            val level = state.level
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(LogicMixTokens.spacingL), Alignment.Center) { CircularProgressIndicator() }
                state.allDone || level == null -> {
                    Card {
                        Text(stringResource(R.string.ws_all_done), style = LogicMixTokens.typography.headlineMedium, color = colors.gold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.ws_all_done_body), style = LogicMixTokens.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    ActionButton(stringResource(R.string.back), onBack, filled = true)
                }
                else -> {
                    var path by remember(level.id) { mutableStateOf(emptyList<Cell>()) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.ws_words_progress, state.mainFound.size, level.mainWords.size),
                            style = LogicMixTokens.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            stringResource(R.string.ws_extra_count, state.extraFound.size),
                            style = LogicMixTokens.typography.titleMedium,
                            color = colors.gold,
                        )
                    }
                    Text(
                        if (path.isEmpty()) stringResource(R.string.ws_instruction)
                        else stringResource(R.string.ws_current_word, level.wordOf(path)),
                        style = LogicMixTokens.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    LetterGrid(level, state, path, {
                        // Sound only when the gesture grows; holding on a letter or backtracking is silent.
                        if (it.size > path.size) onTrace(it.size)
                        path = it
                    }, onSubmit)
                    FeedbackLine(state)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        level.mainWords.sorted().forEach { WordChip(it, it in state.mainFound) }
                    }
                    if (state.completed) {
                        Card {
                            Text(stringResource(R.string.ws_level_complete), style = LogicMixTokens.typography.headlineMedium, color = colors.success, textAlign = TextAlign.Center)
                            Text(
                                if (state.xpAwarded > 0) stringResource(R.string.game_xp_earned, state.xpAwarded) else stringResource(R.string.game_no_xp),
                                style = LogicMixTokens.typography.titleMedium,
                                color = colors.gold,
                                textAlign = TextAlign.Center,
                            )
                        }
                        ActionButton(stringResource(R.string.ws_next_level), onNext, filled = true)
                    } else {
                        ActionButton(
                            stringResource(R.string.ws_hint_button, state.hintsUsed, WordSearchRewards.MAX_HINTS),
                            onHint,
                            filled = false,
                            enabled = state.canUseHint,
                            icon = R.drawable.ic_info,
                        )
                    }
                }
            }
            BrandFooter()
        }
    }
}

@Composable
private fun Header(state: WordSearchUiState, onBack: () -> Unit, onRestart: () -> Unit) {
    val level = state.level
    GameHeader(
        title = stringResource(R.string.ws_title),
        onBack = onBack,
        subtitle = level?.let { stringResource(R.string.ws_level_info, state.levelNumber, state.totalLevels, it.difficulty) },
        onRestart = onRestart,
        restartEnabled = level != null && !state.completed && !state.loading,
    )
}

@Composable
private fun FeedbackLine(state: WordSearchUiState) {
    val message = when (state.feedback) {
        WordFeedback.MAIN -> R.string.ws_fb_main
        WordFeedback.EXTRA -> R.string.ws_fb_extra
        WordFeedback.DUPLICATE -> R.string.ws_fb_duplicate
        WordFeedback.UNKNOWN -> R.string.ws_fb_unknown
        WordFeedback.TOO_SHORT -> R.string.ws_fb_short
        WordFeedback.NONE -> null
    }
    val colors = LogicMixTokens.colors
    val color = when (state.feedback) {
        WordFeedback.MAIN -> colors.success
        WordFeedback.EXTRA -> colors.gold
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    AnimatedVisibility(message != null) {
        Text(
            stringResource(message ?: R.string.ws_fb_main) +
                if (state.feedback == WordFeedback.EXTRA && state.extraXp > 0) "  " + stringResource(R.string.game_xp_earned, state.extraXp) else "",
            style = LogicMixTokens.typography.titleMedium,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun WordChip(word: String, found: Boolean) {
    val colors = LogicMixTokens.colors
    val shape = LogicMixTokens.radii.medium
    Box(
        Modifier
            .clip(shape)
            .background(if (found) colors.rewardBrush else colors.cardBrush)
            .border(BorderStroke(1.dp, if (found) colors.gold else colors.cardBorder), shape)
            .padding(horizontal = LogicMixTokens.spacingM, vertical = LogicMixTokens.spacingS),
    ) {
        Text(
            if (found) word else "•".repeat(word.length),
            style = LogicMixTokens.typography.labelMedium,
            color = if (found) colors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LetterGrid(
    level: WordSearchLevel,
    state: WordSearchUiState,
    path: List<Cell>,
    onPathChange: (List<Cell>) -> Unit,
    onSubmit: (List<Cell>) -> Unit,
) {
    val enabled = !state.completed
    val colors = LogicMixTokens.colors
    val scheme = MaterialTheme.colorScheme
    val cellColor = scheme.surfaceVariant
    val letterColor = scheme.onSurface
    val selectedColor = scheme.secondary
    val onSelected = scheme.onSecondary
    val goldColor = colors.gold
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val currentOnChange by rememberUpdatedState(onPathChange)
    val currentOnSubmit by rememberUpdatedState(onSubmit)
    val description = stringResource(R.string.ws_grid_desc, level.size)
    val flashAlpha = remember { Animatable(0f) }
    var shownFlash by remember { mutableStateOf(emptyList<Cell>()) }
    LaunchedEffect(state.flash) {
        if (state.flash.isNotEmpty()) {
            shownFlash = state.flash
            flashAlpha.snapTo(1f)
            flashAlpha.animateTo(0f, tween(700))
        }
        shownFlash = emptyList()
    }

    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = description }) {
        val sizePx = with(density) { maxWidth.toPx() }
        val cellPx = sizePx / level.size
        val family = MaterialTheme.typography.titleLarge.fontFamily
        val style = remember(cellPx, family) {
            TextStyle(fontFamily = family, fontSize = with(density) { (cellPx * 0.46f).toSp() }, fontWeight = FontWeight.Bold)
        }
        val layouts = remember(level.id, style) { level.letters.map { measurer.measure(it.toString(), style) } }

        // The dead zone at cell corners keeps a diagonal swipe from selecting a corner cell by accident.
        fun hit(o: Offset): Cell? {
            val col = (o.x / cellPx).toInt()
            val row = (o.y / cellPx).toInt()
            if (o.x < 0 || o.y < 0 || col !in 0 until level.size || row !in 0 until level.size) return null
            val dx = o.x - (col + 0.5f) * cellPx
            val dy = o.y - (row + 0.5f) * cellPx
            return if (hypot(dx, dy) <= cellPx * 0.46f) Cell(row, col) else null
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(level.id, enabled, cellPx) {
                    if (!enabled) return@pointerInput
                    var current = emptyList<Cell>()
                    detectDragGestures(
                        onDragStart = { o ->
                            current = listOfNotNull(hit(o))
                            currentOnChange(current)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            current = WordSearchSelection.extend(current, hit(change.position))
                            currentOnChange(current)
                        },
                        onDragEnd = {
                            currentOnSubmit(current)
                            current = emptyList()
                            currentOnChange(current)
                        },
                        onDragCancel = {
                            current = emptyList()
                            currentOnChange(current)
                        },
                    )
                },
        ) {
            val n = level.size
            val gap = cellPx * 0.06f
            val center = { c: Cell -> Offset((c.col + 0.5f) * cellPx, (c.row + 0.5f) * cellPx) }
            val selected = path.toSet()
            for (r in 0 until n) for (c in 0 until n) {
                val cell = Cell(r, c)
                drawRoundRect(
                    color = if (cell in selected) selectedColor.copy(alpha = 0.35f) else cellColor,
                    topLeft = Offset(c * cellPx + gap, r * cellPx + gap),
                    size = Size(cellPx - 2 * gap, cellPx - 2 * gap),
                    cornerRadius = CornerRadius(cellPx * 0.22f),
                )
            }
            state.foundPlacements.forEach { p ->
                drawPath(
                    smoothPath(p.path.map(center)),
                    goldColor.copy(alpha = 0.45f),
                    style = Stroke(cellPx * 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            state.hintCell?.let {
                drawRoundRect(
                    color = goldColor,
                    topLeft = Offset(it.col * cellPx + gap, it.row * cellPx + gap),
                    size = Size(cellPx - 2 * gap, cellPx - 2 * gap),
                    cornerRadius = CornerRadius(cellPx * 0.22f),
                    style = Stroke(cellPx * 0.07f),
                )
            }
            if (shownFlash.size > 1 && flashAlpha.value > 0f) {
                drawPath(
                    smoothPath(shownFlash.map(center)),
                    goldColor.copy(alpha = 0.8f * flashAlpha.value),
                    style = Stroke(cellPx * 0.3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            if (path.size > 1) {
                drawPath(
                    smoothPath(path.map(center)),
                    selectedColor,
                    style = Stroke(cellPx * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            for (r in 0 until n) for (c in 0 until n) {
                val layout = layouts[r * n + c]
                drawText(
                    layout,
                    color = if (Cell(r, c) in selected) onSelected else letterColor,
                    topLeft = Offset(
                        (c + 0.5f) * cellPx - layout.size.width / 2f,
                        (r + 0.5f) * cellPx - layout.size.height / 2f,
                    ),
                )
            }
        }
    }
}

private fun smoothPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    if (points.size == 1) {
        lineTo(points[0].x + 0.01f, points[0].y)
        return@apply
    }
    for (i in 1 until points.size - 1) {
        val mid = Offset((points[i].x + points[i + 1].x) / 2f, (points[i].y + points[i + 1].y) / 2f)
        quadraticTo(points[i].x, points[i].y, mid.x, mid.y)
    }
    lineTo(points.last().x, points.last().y)
}
