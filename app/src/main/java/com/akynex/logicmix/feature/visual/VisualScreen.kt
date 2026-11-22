package com.akynex.logicmix.feature.visual

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.CategoryScaffold
import com.akynex.logicmix.core.designsystem.ChoiceCard
import com.akynex.logicmix.core.designsystem.ChoiceState
import com.akynex.logicmix.core.designsystem.GameHeader
import com.akynex.logicmix.core.designsystem.GameTool
import com.akynex.logicmix.core.designsystem.GameToolRow
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.core.designsystem.XpLine
import com.akynex.logicmix.domain.visual.ShapeKind
import com.akynex.logicmix.domain.visual.Tile
import com.akynex.logicmix.domain.visual.VisualRule
import com.akynex.logicmix.feature.game.ActionButton
import com.akynex.logicmix.feature.game.Card

private val Palette = listOf(Color(0xFF9B7BFF), Color(0xFF3EE6D6), Color(0xFFFFB300), Color(0xFFFF6B7D))

private fun VisualRule.hint(): Int = when (this) {
    VisualRule.SHAPE -> R.string.vl_hint_shape
    VisualRule.COUNT -> R.string.vl_hint_count
    VisualRule.ROTATION -> R.string.vl_hint_rotation
    VisualRule.COLOR -> R.string.vl_hint_color
    VisualRule.POSITION -> R.string.vl_hint_position
    VisualRule.COMBINED -> R.string.vl_hint_combined
    VisualRule.MATRIX -> R.string.vl_hint_matrix
}

@Composable
fun VisualRoute(onBack: () -> Unit, viewModel: VisualViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val game = state.game
    CategoryScaffold {
        GameHeader(
            title = stringResource(R.string.category_visual_logic),
            onBack = onBack,
            subtitle = if (game != null) stringResource(R.string.cat_level, state.index + 1, state.total) else null,
            onRestart = viewModel::restart,
            restartEnabled = game != null && !game.solved,
        )
        if (state.allDone) {
            Card {
                Text(stringResource(R.string.game_all_done), style = LogicMixTokens.typography.headlineMedium, color = LogicMixTokens.colors.success, textAlign = TextAlign.Center)
                Text(stringResource(R.string.game_all_done_body), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            }
            ActionButton(stringResource(R.string.cat_play_again), viewModel::playAgain, filled = true)
        }
        if (game != null) {
            val puzzle = game.puzzle
            Text(stringResource(R.string.vl_prompt), style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Card {
                Column(Modifier.widthIn(max = 64.dp * puzzle.columns).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingXs)) {
                    puzzle.tiles.indices.chunked(puzzle.columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingXs)) {
                            row.forEach { i ->
                                val shown = if (i == puzzle.gapIndex) puzzle.answer.takeIf { game.solved } else puzzle.tiles[i]
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).clip(LogicMixTokens.radii.small)
                                        .border(BorderStroke(1.dp, LogicMixTokens.colors.cardBorder), LogicMixTokens.radii.small)
                                        .background(Color.White.copy(alpha = 0.06f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (shown != null) TileCanvas(shown, Modifier.fillMaxWidth().aspectRatio(1f).padding(2.dp))
                                    else Text("?", style = LogicMixTokens.typography.headlineMedium, color = LogicMixTokens.colors.gold)
                                }
                            }
                        }
                    }
                }
            }
            puzzle.options.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM)) {
                    pair.forEach { option ->
                        val choice = when {
                            game.solved && option.id == puzzle.correctId -> ChoiceState.CORRECT
                            option.id in game.wrong -> ChoiceState.WRONG
                            option.id in game.eliminated || game.solved -> ChoiceState.DISABLED
                            else -> ChoiceState.IDLE
                        }
                        ChoiceCard(choice, { viewModel.choose(option.id) }, Modifier.weight(1f)) {
                            TileCanvas(option.tile, Modifier.widthIn(max = 96.dp).fillMaxWidth().aspectRatio(1f))
                        }
                    }
                }
            }
            if (game.hintShown && !game.solved) {
                Text(stringResource(R.string.cat_hint_line, stringResource(puzzle.rule.hint())), color = LogicMixTokens.colors.gold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (game.solved) {
                Card {
                    Text(stringResource(R.string.game_correct), style = LogicMixTokens.typography.headlineMedium, color = LogicMixTokens.colors.success, textAlign = TextAlign.Center)
                    XpLine(state.xpAwarded)
                }
                ActionButton(stringResource(R.string.game_next), viewModel::next, filled = true)
            } else {
                GameToolRow(listOf(GameTool(stringResource(R.string.cat_hint), viewModel::hint)))
            }
        }
    }
}

@Composable
private fun TileCanvas(tile: Tile, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    Canvas(modifier) { drawTile(tile, Palette[tile.color.coerceIn(0, Palette.lastIndex)], outline) }
}

private fun itemCenters(tile: Tile): Pair<List<Offset>, Float> = when {
    tile.slot in 0..3 -> listOf(listOf(.28f, .28f), listOf(.72f, .28f), listOf(.72f, .72f), listOf(.28f, .72f))[tile.slot].let { listOf(Offset(it[0], it[1])) to .3f }
    tile.count == 2 -> listOf(Offset(.3f, .5f), Offset(.7f, .5f)) to .34f
    tile.count == 3 -> listOf(Offset(.5f, .3f), Offset(.3f, .7f), Offset(.7f, .7f)) to .3f
    tile.count >= 4 -> listOf(Offset(.3f, .3f), Offset(.7f, .3f), Offset(.3f, .7f), Offset(.7f, .7f)) to .3f
    else -> listOf(Offset(.5f, .5f)) to .52f
}

private fun DrawScope.drawTile(tile: Tile, fill: Color, outline: Color) {
    val (centers, relSize) = itemCenters(tile)
    val side = size.minDimension
    val s = side * relSize
    val stroke = Stroke(width = side * 0.035f)
    centers.forEach { rel ->
        val c = Offset(rel.x * side, rel.y * side)
        rotate(tile.rotation.toFloat(), c) {
            when (tile.kind) {
                ShapeKind.CIRCLE -> { drawCircle(fill, s / 2, c); drawCircle(outline, s / 2, c, style = stroke) }
                ShapeKind.SQUARE -> {
                    val tl = Offset(c.x - s / 2, c.y - s / 2)
                    drawRoundRect(fill, tl, Size(s, s), CornerRadius(s * 0.1f))
                    drawRoundRect(outline, tl, Size(s, s), CornerRadius(s * 0.1f), style = stroke)
                }
                ShapeKind.TRIANGLE -> fillPath(trianglePath(c, s), fill, outline, stroke)
                ShapeKind.DIAMOND -> fillPath(diamondPath(c, s), fill, outline, stroke)
                ShapeKind.ARROW -> fillPath(arrowPath(c, s), fill, outline, stroke)
                ShapeKind.LINE -> drawLine(fill, Offset(c.x, c.y - s / 2), Offset(c.x, c.y + s / 2), strokeWidth = s * 0.18f)
            }
        }
    }
}

private fun DrawScope.fillPath(path: Path, fill: Color, outline: Color, stroke: Stroke) {
    drawPath(path, fill)
    drawPath(path, outline, style = stroke)
}

private fun trianglePath(c: Offset, s: Float) = Path().apply {
    moveTo(c.x, c.y - s / 2); lineTo(c.x + s / 2, c.y + s / 2); lineTo(c.x - s / 2, c.y + s / 2); close()
}

private fun diamondPath(c: Offset, s: Float) = Path().apply {
    moveTo(c.x, c.y - s / 2); lineTo(c.x + s / 2, c.y); lineTo(c.x, c.y + s / 2); lineTo(c.x - s / 2, c.y); close()
}

// Points up at rotation 0, so the 90-degree steps are easy to read.
private fun arrowPath(c: Offset, s: Float) = Path().apply {
    val w = s * 0.22f
    moveTo(c.x, c.y - s / 2)
    lineTo(c.x + s / 2, c.y)
    lineTo(c.x + w, c.y)
    lineTo(c.x + w, c.y + s / 2)
    lineTo(c.x - w, c.y + s / 2)
    lineTo(c.x - w, c.y)
    lineTo(c.x - s / 2, c.y)
    close()
}
