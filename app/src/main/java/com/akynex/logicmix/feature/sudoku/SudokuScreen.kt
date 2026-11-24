package com.akynex.logicmix.feature.sudoku

import com.akynex.logicmix.core.designsystem.BrandFooter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.GameHeader
import com.akynex.logicmix.core.designsystem.GameTool
import com.akynex.logicmix.core.designsystem.GameToolRow
import com.akynex.logicmix.core.designsystem.LogicMixIcon
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.domain.sudoku.SudokuDifficulty
import com.akynex.logicmix.domain.sudoku.SudokuGame
import com.akynex.logicmix.domain.sudoku.SudokuGeometry
import com.akynex.logicmix.feature.game.ActionButton
import com.akynex.logicmix.feature.game.Card

private fun SudokuDifficulty.label() = when (this) {
    SudokuDifficulty.EASY -> R.string.sudoku_easy
    SudokuDifficulty.MEDIUM -> R.string.sudoku_medium
    SudokuDifficulty.HARD -> R.string.sudoku_hard
    SudokuDifficulty.EXPERT -> R.string.sudoku_expert
}

@Composable
fun SudokuRoute(onBack: () -> Unit, viewModel: SudokuViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    SudokuScreen(state, onBack, viewModel)
}

@Composable
private fun SudokuScreen(state: SudokuUiState, onBack: () -> Unit, vm: SudokuViewModel) {
    val colors = LogicMixTokens.colors
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
            Header(state, onBack, vm::reset)
            val game = state.game
            when {
                state.phase == SudokuPhase.LOADING ->
                    Column(Modifier.fillMaxWidth().padding(LogicMixTokens.spacingL), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.sudoku_loading), color = MaterialTheme.colorScheme.onBackground)
                    }
                state.phase == SudokuPhase.PICK || game == null -> {
                    Text(
                        stringResource(R.string.sudoku_pick),
                        style = LogicMixTokens.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SudokuDifficulty.entries.forEach { d ->
                        ActionButton(stringResource(d.label()), { vm.start(d) }, filled = d == SudokuDifficulty.EASY)
                    }
                }
                else -> {
                    Board(game, state.selected, vm::select, enabled = !state.solved)
                    if (state.solved) {
                        Card {
                            Text(stringResource(R.string.sudoku_solved), style = LogicMixTokens.typography.headlineMedium, color = colors.success, textAlign = TextAlign.Center)
                            Text(stringResource(R.string.sudoku_time, formatTime(state.elapsedSec)), color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                if (state.xpAwarded > 0) stringResource(R.string.game_xp_earned, state.xpAwarded) else stringResource(R.string.game_no_xp),
                                style = LogicMixTokens.typography.titleMedium,
                                color = colors.gold,
                            )
                        }
                        ActionButton(stringResource(R.string.sudoku_new), vm::newGame, filled = true)
                    } else {
                        GameToolRow(
                            listOf(
                                GameTool(stringResource(R.string.sudoku_undo), vm::undo, game.canUndo),
                                GameTool(stringResource(R.string.sudoku_redo), vm::redo, game.canRedo),
                                GameTool(stringResource(R.string.sudoku_erase), vm::erase),
                                GameTool(stringResource(R.string.sudoku_hint), vm::hint),
                            ),
                        )
                        GameToolRow(listOf(GameTool(stringResource(R.string.sudoku_notes), vm::toggleNotes, selected = state.notesMode)))
                        NumberPad(vm::input)
                        Text(
                            stringResource(R.string.sudoku_hints_used, game.hintsUsed),
                            style = LogicMixTokens.typography.labelMedium,
                            color = colors.gold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            BrandFooter()
        }
    }
}

/** Board constants shared by drawing and tests: the square is split into nine equal cells; the frame sits inside it. */
internal object SudokuBoardGeometry {
    val CORNER = 16.dp
    val FRAME = 3.dp
    val BLOCK_LINE = 2.dp
    val CELL_LINE = 1.dp

    fun cell(side: Float) = side / 9f

    fun cellAt(x: Float, y: Float, side: Float): Int {
        val c = (x / cell(side)).toInt().coerceIn(0, 8)
        val r = (y / cell(side)).toInt().coerceIn(0, 8)
        return r * 9 + c
    }
}

private fun formatTime(sec: Long) = "%d:%02d".format(sec / 60, sec % 60)

@Composable
private fun Header(state: SudokuUiState, onBack: () -> Unit, onRestart: () -> Unit) {
    GameHeader(
        title = stringResource(R.string.sudoku_title),
        onBack = onBack,
        subtitle = state.difficulty?.let { stringResource(it.label()) },
        onRestart = onRestart,
        restartEnabled = state.game != null && !state.solved,
        trailing = {
            if (state.game != null) {
                Text(formatTime(state.elapsedSec), style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            }
        },
    )
}

@Composable
private fun NumberPad(onDigit: (Int) -> Unit) {
    val colors = LogicMixTokens.colors
    val shape = LogicMixTokens.radii.medium
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (d in 1..9) {
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(shape)
                    .background(colors.cardBrush)
                    .border(BorderStroke(1.dp, colors.cardBorder), shape)
                    .clickable(role = Role.Button) { onDigit(d) },
                contentAlignment = Alignment.Center,
            ) {
                Text("$d", style = LogicMixTokens.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Board(game: SudokuGame, selected: Int?, onSelect: (Int) -> Unit, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val colors = LogicMixTokens.colors
    val measurer = rememberTextMeasurer()
    val family = MaterialTheme.typography.titleLarge.fontFamily
    val density = LocalDensity.current
    val conflicts = game.conflicts
    val selectedValue = selected?.let { game.values[it] } ?: 0
    val cellBg = scheme.surfaceVariant
    val related = scheme.primary.copy(alpha = 0.16f)
    val same = scheme.secondary.copy(alpha = 0.32f)
    val sel = scheme.primary.copy(alpha = 0.5f)
    val bad = scheme.error.copy(alpha = 0.3f)
    val given = scheme.onSurface
    val typed = scheme.secondary
    val thin = scheme.onSurface.copy(alpha = 0.25f)
    val thick = colors.cardBorder.copy(alpha = 1f)
    val frameColor = scheme.secondary
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(SudokuBoardGeometry.CORNER))
            .background(cellBg)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o ->
                    onSelect(SudokuBoardGeometry.cellAt(o.x, o.y, size.width.toFloat()))
                }
            },
    ) {
        val cell = size.width / 9f
        val bigStyle = TextStyle(fontFamily = family, fontSize = with(density) { (cell * 0.58f).toSp() }, fontWeight = FontWeight.Bold)
        val noteStyle = TextStyle(fontFamily = family, fontSize = with(density) { (cell * 0.24f).toSp() }, fontWeight = FontWeight.SemiBold)
        for (i in 0 until SudokuGeometry.CELLS) {
            val r = SudokuGeometry.row(i)
            val c = SudokuGeometry.col(i)
            val v = game.values[i]
            val tl = Offset(c * cell, r * cell)
            val fill = when {
                i == selected -> sel
                i in conflicts -> bad
                v != 0 && v == selectedValue -> same
                selected != null && (r == SudokuGeometry.row(selected) || c == SudokuGeometry.col(selected) || SudokuGeometry.box(i) == SudokuGeometry.box(selected)) -> related
                else -> Color.Transparent
            }
            if (fill != Color.Transparent) drawRect(fill, tl, Size(cell, cell))
            if (v != 0) {
                val layout = measurer.measure(v.toString(), bigStyle.copy(color = if (game.isGiven(i)) given else if (i in conflicts) scheme.error else typed))
                drawText(layout, topLeft = Offset(tl.x + (cell - layout.size.width) / 2f, tl.y + (cell - layout.size.height) / 2f))
            } else if (game.notes[i] != 0) {
                for (d in 1..9) {
                    if (game.notes[i] and (1 shl d) == 0) continue
                    val layout = measurer.measure(d.toString(), noteStyle.copy(color = given.copy(alpha = 0.7f)))
                    val nx = tl.x + ((d - 1) % 3 + 0.5f) * cell / 3f - layout.size.width / 2f
                    val ny = tl.y + ((d - 1) / 3 + 0.5f) * cell / 3f - layout.size.height / 2f
                    drawText(layout, topLeft = Offset(nx, ny))
                }
            }
        }
        for (k in 1..8) {
            val big = k % 3 == 0
            val w = if (big) SudokuBoardGeometry.BLOCK_LINE.toPx() else SudokuBoardGeometry.CELL_LINE.toPx()
            val color = if (big) thick else thin
            drawRect(color, Offset(k * cell - w / 2f, 0f), Size(w, size.height))
            drawRect(color, Offset(0f, k * cell - w / 2f), Size(size.width, w))
        }
        // Outer frame is stroked fully inside the canvas so the clip never trims it, and drawn last to cover line ends.
        val frame = SudokuBoardGeometry.FRAME.toPx()
        drawRoundRect(
            frameColor,
            topLeft = Offset(frame / 2f, frame / 2f),
            size = Size(size.width - frame, size.height - frame),
            cornerRadius = CornerRadius(SudokuBoardGeometry.CORNER.toPx() - frame / 2f),
            style = Stroke(frame),
        )
    }
}
