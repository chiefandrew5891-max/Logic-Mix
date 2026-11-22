package com.akynex.logicmix.feature.crossword

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.akynex.logicmix.domain.crossword.Viewport
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.CategoryScaffold
import com.akynex.logicmix.core.designsystem.GameHeader
import com.akynex.logicmix.core.designsystem.GameTool
import com.akynex.logicmix.core.designsystem.GameToolRow
import com.akynex.logicmix.core.designsystem.InfoNote
import com.akynex.logicmix.core.designsystem.LetterKeyboard
import com.akynex.logicmix.core.designsystem.LetterLayouts
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.core.designsystem.XpLine
import com.akynex.logicmix.domain.crossword.CrosswordGame
import com.akynex.logicmix.domain.crossword.Direction
import com.akynex.logicmix.feature.game.ActionButton
import com.akynex.logicmix.feature.game.Card

@Composable
fun CrosswordRoute(onBack: () -> Unit, viewModel: CrosswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val game = state.game
    CategoryScaffold {
        GameHeader(
            title = stringResource(R.string.category_mini_crossword),
            onBack = onBack,
            subtitle = if (game != null) stringResource(R.string.cat_level, state.index + 1, state.puzzles.size) else null,
            onRestart = viewModel::restart,
            restartEnabled = game != null && !game.solved,
        )
        if (state.unsupported) {
            InfoNote(stringResource(R.string.cw_unsupported_language))
            return@CategoryScaffold
        }
        if (state.allDone) {
            Card {
                Text(stringResource(R.string.game_all_done), style = LogicMixTokens.typography.headlineMedium, color = LogicMixTokens.colors.success, textAlign = TextAlign.Center)
                Text(stringResource(R.string.game_all_done_body), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            }
            ActionButton(stringResource(R.string.cat_play_again), viewModel::playAgain, filled = true)
        }
        if (game == null) return@CategoryScaffold
        Grid(game, viewModel::selectCell)
        val active = game.activeEntry
        Text(
            active?.let { "${game.puzzle.numberOf(it)}. ${it.clue}" } ?: stringResource(R.string.cw_pick_clue),
            color = LogicMixTokens.colors.gold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        when (state.check) {
            CheckResult.MISTAKES -> Text(stringResource(R.string.cw_incorrect), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            CheckResult.OK -> Text(stringResource(R.string.cw_check_ok), color = LogicMixTokens.colors.success, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            CheckResult.NONE -> Unit
        }
        if (game.solved) {
            Card {
                Text(stringResource(R.string.cw_solved), style = LogicMixTokens.typography.headlineMedium, color = LogicMixTokens.colors.success, textAlign = TextAlign.Center)
                XpLine(state.xpAwarded)
            }
            ActionButton(stringResource(R.string.game_next), viewModel::next, filled = true)
        } else {
            LetterKeyboard(LetterLayouts.forLanguage(state.language), viewModel::letter, viewModel::delete)
            GameToolRow(listOf(GameTool(stringResource(R.string.cw_hint_letter), viewModel::hintLetter), GameTool(stringResource(R.string.cw_hint_word), viewModel::hintWord)))
            GameToolRow(listOf(GameTool(stringResource(R.string.cw_check_word), viewModel::checkWord), GameTool(stringResource(R.string.cw_check_all), viewModel::checkAll)))
        }
        Clues(game, Direction.ACROSS, R.string.cw_across, viewModel)
        Clues(game, Direction.DOWN, R.string.cw_down, viewModel)
    }
}

@Composable
private fun Grid(game: CrosswordGame, onCell: (Int) -> Unit) {
    val puzzle = game.puzzle
    val size = puzzle.size
    val density = LocalDensity.current
    val cellDp = 44.dp
    val gapDp = 4.dp
    val cellPx = with(density) { cellDp.toPx() }
    val gapPx = with(density) { gapDp.toPx() }
    val contentDp = cellDp * size + gapDp * (size - 1)
    val contentPx = cellPx * size + gapPx * (size - 1)
    var box by remember { mutableStateOf(IntSize.Zero) }
    // Zoom and offset survive screen recreation; they are keyed by puzzle so a new puzzle starts fitted.
    var saved by rememberSaveable(puzzle.id) { mutableStateOf<FloatArray?>(null) }
    var vp by remember(puzzle.id, box) {
        val base = Viewport().withSizes(box.width.toFloat(), box.height.toFloat(), contentPx, contentPx).fit()
        mutableStateOf(Viewport.restore(saved?.toList(), base))
    }
    fun change(next: Viewport) { vp = next; saved = Viewport.encode(next).toFloatArray() }
    val latest by rememberUpdatedState(vp)
    val latestOnCell by rememberUpdatedState(onCell)

    Column(verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS)) {
        Box(
            Modifier.fillMaxWidth().height(minOf(contentDp, 360.dp)).clipToBounds()
                .onSizeChanged { box = it }
                .pointerInput(puzzle.id, box) {
                    viewportGestures(
                        current = { latest },
                        onChange = { change(it) },
                        onTap = { p ->
                            val cell = latest.cellAt(p.x, p.y, cellPx, gapPx, size)
                            if (cell != null && cell in puzzle.solution) latestOnCell(cell)
                        },
                    )
                },
        ) {
            Box(
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(contentDp, contentDp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = vp.scale; scaleY = vp.scale
                        translationX = vp.tx; translationY = vp.ty
                    },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(gapDp)) {
                    repeat(size) { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gapDp)) {
                            repeat(size) { c -> GridCell(game, r * size + c, cellDp) }
                        }
                    }
                }
            }
        }
        GameToolRow(listOf(GameTool(stringResource(R.string.cw_zoom_fit), { change(vp.fit()) }), GameTool(stringResource(R.string.cw_zoom_reset), { change(vp.reset()) })))
    }
}

@Composable
private fun GridCell(game: CrosswordGame, index: Int, cell: Dp) {
    val colors = LogicMixTokens.colors
    val used = index in game.puzzle.solution
    val shape = LogicMixTokens.radii.small
    // Light theme: opaque white base so the app gradient never shows through the cells.
    val base = if (colors.isDark) Color.Transparent else Color.White
    val bg = when {
        !used -> Color.Transparent
        index == game.selected -> colors.gold.copy(alpha = 0.55f).compositeOver(base)
        index in game.wrong -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f).compositeOver(base)
        index in game.activeCells -> colors.gold.copy(alpha = 0.22f).compositeOver(base)
        else -> if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.White
    }
    Box(
        Modifier.size(cell).clip(shape).background(bg)
            .border(BorderStroke(if (used) 1.dp else 0.dp, if (used) colors.cardBorder else Color.Transparent), shape),
        contentAlignment = Alignment.Center,
    ) {
        game.puzzle.numbers[index]?.let {
            Text("$it", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 1.dp))
        }
        game.letters[index]?.let {
            Text(it.toString(), style = LogicMixTokens.typography.titleLarge, color = if (index in game.revealed) colors.gold else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * Pinch-zoom with two fingers, and a one-finger drag that pans only while the zoomed grid is larger than the
 * viewport. Everything else is left unconsumed so the page's vertical scroll keeps working; a short touch is a tap.
 */
private suspend fun PointerInputScope.viewportGestures(current: () -> Viewport, onChange: (Viewport) -> Unit, onTap: (Offset) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dragging = false
        var multi = false
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                multi = true
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = false)
                if ((zoom != 1f || pan != Offset.Zero) && centroid.isSpecified) {
                    onChange(current().zoomAt(centroid.x, centroid.y, zoom).panBy(pan.x, pan.y))
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } else if (pressed.size == 1 && !multi) {
                val change = pressed[0]
                if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                if (dragging && current().canPan) {
                    val d = change.positionChange()
                    onChange(current().panBy(d.x, d.y))
                    change.consume()
                }
            }
        } while (event.changes.any { it.pressed })
        if (!dragging && !multi) onTap(down.position)
    }
}

@Composable
private fun Clues(game: CrosswordGame, direction: Direction, title: Int, vm: CrosswordViewModel) {
    val list = game.puzzle.entries.filter { it.direction == direction }.sortedBy { game.puzzle.numberOf(it) }
    if (list.isEmpty()) return
    Text(stringResource(title), style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    list.forEach { entry ->
        val done = game.isEntryCorrect(entry)
        val isActive = entry == game.activeEntry
        Row(
            Modifier.fillMaxWidth().clip(LogicMixTokens.radii.small)
                .background(if (isActive) LogicMixTokens.colors.gold.copy(alpha = 0.18f) else Color.Transparent)
                .clickable(onClick = { vm.selectEntry(entry) })
                .padding(LogicMixTokens.spacingS),
            horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS),
        ) {
            Text("${game.puzzle.numberOf(entry)}.", color = LogicMixTokens.colors.gold)
            Text(
                "${entry.clue} (${entry.length})",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (done) 0.5f else 1f),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
