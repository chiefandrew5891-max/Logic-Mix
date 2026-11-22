package com.akynex.logicmix.feature.matchstick

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.GameHeader
import com.akynex.logicmix.core.designsystem.GameTool
import com.akynex.logicmix.core.designsystem.GameToolRow
import com.akynex.logicmix.core.designsystem.LogicMixIcon
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.domain.matchstick.MatchGame
import com.akynex.logicmix.domain.matchstick.MatchPuzzles
import com.akynex.logicmix.domain.matchstick.MatchSlots
import com.akynex.logicmix.feature.game.ActionButton
import com.akynex.logicmix.feature.game.Card
import kotlin.math.hypot

private val Wood = Color(0xFFE2B66B)
private val WoodDark = Color(0xFFB98A45)
private val Head = Color(0xFFD9433B)

internal class Stick(val center: Offset, val horizontal: Boolean)

/** Pixel layout of the 23 slots for a canvas of the given width; also the source of the canvas height. */
internal class Layout(val width: Float) {
    // 5 cells + 4 gaps = 6.8 w; the extra 0.7 w is a symmetric margin that keeps edge sticks, heads and shadows inside.
    val w = width / 7.5f
    val gap = w * 0.45f
    val h = w * 1.9f
    val top = w * 0.35f
    val height = h + top * 2
    val length = w * 0.8f
    val thick = w * 0.12f
    private val left = (width - (5 * w + 4 * gap)) / 2f

    /** Horizontal and vertical extent of a stick including the drag-lift scale, for bounds checks. */
    fun extent(s: Stick, scale: Float = 1f): Pair<ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>> {
        val halfLong = length * scale / 2
        val halfShort = thick * scale / 2
        val hx = if (s.horizontal) halfLong else halfShort
        val hy = if (s.horizontal) halfShort else halfLong
        return (s.center.x - hx)..(s.center.x + hx) to (s.center.y - hy)..(s.center.y + hy)
    }

    private fun cellX(k: Int) = left + k * (w + gap)

    fun slot(i: Int): Stick {
        if (i == MatchSlots.OP_H) return Stick(Offset(cellX(1) + w / 2, top + h / 2), true)
        if (i == MatchSlots.OP_V) return Stick(Offset(cellX(1) + w / 2, top + h / 2), false)
        val k: Int
        val s: Int
        when {
            i < 7 -> { k = 0; s = i }
            i < 16 -> { k = 2; s = i - 9 }
            else -> { k = 4; s = i - 16 }
        }
        val x = cellX(k)
        val y = top
        return when (s) {
            0 -> Stick(Offset(x + w / 2, y), true)
            1 -> Stick(Offset(x + w, y + h / 4), false)
            2 -> Stick(Offset(x + w, y + 3 * h / 4), false)
            3 -> Stick(Offset(x + w / 2, y + h), true)
            4 -> Stick(Offset(x, y + 3 * h / 4), false)
            5 -> Stick(Offset(x, y + h / 4), false)
            else -> Stick(Offset(x + w / 2, y + h / 2), true)
        }
    }

    fun equalsSticks(): List<Stick> {
        val c = cellX(3) + w / 2
        return listOf(Stick(Offset(c, top + h / 2 - w * 0.18f), true), Stick(Offset(c, top + h / 2 + w * 0.18f), true))
    }

    private fun dist(i: Int, p: Offset) = slot(i).center.let { hypot(it.x - p.x, it.y - p.y) }

    fun nearest(p: Offset, candidates: List<Int>): Int? =
        candidates.minByOrNull { dist(it, p) }?.takeIf { dist(it, p) <= w * 0.7f }
}

private fun DrawScope.drawStick(l: Layout, s: Stick, center: Offset = s.center, scale: Float = 1f) {
    val len = l.length * scale
    val t = l.thick * scale
    val size = if (s.horizontal) Size(len, t) else Size(t, len)
    val tl = Offset(center.x - size.width / 2, center.y - size.height / 2)
    val r = CornerRadius(t / 2)
    drawRoundRect(Color.Black.copy(alpha = 0.35f), tl + Offset(t * 0.18f, t * 0.28f), size, r)
    drawRoundRect(Wood, tl, size, r)
    val shine = if (s.horizontal) Size(len * 0.85f, t * 0.28f) else Size(t * 0.28f, len * 0.85f)
    drawRoundRect(Color.White.copy(alpha = 0.28f), tl + Offset(t * 0.12f, t * 0.12f), shine, CornerRadius(t / 4))
    drawRoundRect(WoodDark, tl, size, r, style = Stroke(t * 0.06f))
    val headCenter = if (s.horizontal) Offset(tl.x + t * 0.55f, center.y) else Offset(center.x, tl.y + t * 0.55f)
    drawCircle(Head, t * 0.62f, headCenter)
    drawCircle(Color.White.copy(alpha = 0.35f), t * 0.2f, headCenter - Offset(t * 0.15f, t * 0.15f))
}

@Composable
fun MatchstickRoute(onBack: () -> Unit, viewModel: MatchstickViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    MatchstickScreen(state, onBack, viewModel)
}

@Composable
private fun MatchstickScreen(state: MatchUiState, onBack: () -> Unit, vm: MatchstickViewModel) {
    val colors = LogicMixTokens.colors
    val game = state.game
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
            GameHeader(
                title = stringResource(R.string.match_title),
                onBack = onBack,
                subtitle = game?.let { stringResource(R.string.match_level, it.puzzle.index + 1, MatchPuzzles.COUNT) },
            )
            if (state.allDone) {
                Card { Text(stringResource(R.string.match_all_done), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center) }
            }
            if (game != null) {
                Text(
                    stringResource(R.string.match_instruction, game.puzzle.maxMoves),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.match_moves, game.movesUsed, game.puzzle.maxMoves),
                    style = LogicMixTokens.typography.titleMedium,
                    color = colors.gold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Board(state, game, vm)
                if (game.solved) {
                    Card {
                        Text(stringResource(R.string.match_solved), style = LogicMixTokens.typography.headlineMedium, color = colors.success, textAlign = TextAlign.Center)
                        Text(
                            if (state.xpAwarded > 0) stringResource(R.string.game_xp_earned, state.xpAwarded) else stringResource(R.string.game_no_xp),
                            style = LogicMixTokens.typography.titleMedium,
                            color = colors.gold,
                        )
                    }
                    ActionButton(stringResource(R.string.match_next), vm::next, filled = true)
                } else {
                    if (game.failed) {
                        Text(stringResource(R.string.match_wrong), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    if (state.noHint) {
                        Text(stringResource(R.string.match_no_hint), color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    GameToolRow(
                        listOf(
                            GameTool(stringResource(R.string.match_undo), vm::undo, game.canUndo),
                            GameTool(stringResource(R.string.match_hint), vm::hint),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun Board(state: MatchUiState, game: MatchGame, vm: MatchstickViewModel) {
    val currentGame by rememberUpdatedState(game)
    var width by remember { mutableStateOf(0f) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var dragTarget by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    val heightDp = with(density) { Layout(if (width > 0f) width else 100f).height.toDp() }
    val teal = MaterialTheme.colorScheme.secondary
    val gold = LogicMixTokens.colors.gold
    val locked = game.solved
    val dark = LogicMixTokens.colors.isDark

    fun targets(g: MatchGame, from: Int) = (0 until MatchSlots.COUNT).filter { g.canMove(from, it) }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(heightDp)
            .onSizeChanged { width = it.width.toFloat() }
            .clip(LogicMixTokens.radii.medium)
            .background(if (dark) Color(0xFF14264F) else Color(0xFFF7F5FF))
            .then(if (dark) Modifier else Modifier.border(BorderStroke(1.5.dp, Color(0xFFD8C7F4)), LogicMixTokens.radii.medium))
            .pointerInput(locked) {
                if (locked) return@pointerInput
                detectDragGestures(
                    onDragStart = { o ->
                        val g = currentGame
                        val occupied = (0 until MatchSlots.COUNT).filter { s -> g.current shr s and 1 == 1 && g.movesLeft > 0 }
                        val from = Layout(size.width.toFloat()).nearest(o, occupied)
                        if (from != null) {
                            dragFrom = from
                            dragPos = o
                            dragTarget = null
                            vm.pickUp()
                        }
                    },
                    onDrag = { change, delta ->
                        val from = dragFrom
                        if (from != null) {
                            change.consume()
                            dragPos += delta
                            val target = Layout(size.width.toFloat()).nearest(dragPos, targets(currentGame, from))
                            if (target != null && target != dragTarget) vm.dragMoved()
                            dragTarget = target
                        }
                    },
                    onDragEnd = {
                        val from = dragFrom
                        val to = dragTarget
                        dragFrom = null
                        dragTarget = null
                        if (from != null && to != null) vm.place(from, to)
                    },
                    onDragCancel = {
                        dragFrom = null
                        dragTarget = null
                    },
                )
            },
    ) {
        val l = Layout(size.width)
        val from = dragFrom
        val ghosts = if (from != null) targets(game, from) else emptyList()
        for (i in 0 until MatchSlots.COUNT) {
            if (game.current shr i and 1 != 0) continue
            val s = l.slot(i)
            val hot = i == dragTarget
            val hinted = state.hint?.to == i
            val sz = if (s.horizontal) Size(l.length, l.thick) else Size(l.thick, l.length)
            val tl = Offset(s.center.x - sz.width / 2, s.center.y - sz.height / 2)
            val color = when {
                hot || hinted -> teal
                i in ghosts -> if (dark) Color.White.copy(alpha = 0.22f) else Color(0xFF6C3FE0).copy(alpha = 0.45f)
                else -> if (dark) Color.White.copy(alpha = 0.08f) else Color(0xFF6C3FE0).copy(alpha = 0.22f)
            }
            drawRoundRect(color, tl, sz, CornerRadius(l.thick / 2), style = Stroke(if (hot || hinted) l.thick * 0.22f else l.thick * 0.1f))
        }
        l.equalsSticks().forEach { drawStick(l, it) }
        for (i in 0 until MatchSlots.COUNT) {
            if (game.current shr i and 1 == 0 || i == from) continue
            val s = l.slot(i)
            drawStick(l, s)
            if (state.hint?.from == i) drawCircle(gold, l.w * 0.3f, s.center, style = Stroke(l.thick * 0.25f))
        }
        if (from != null) drawStick(l, l.slot(from), dragPos, scale = 1.12f)
    }
}
