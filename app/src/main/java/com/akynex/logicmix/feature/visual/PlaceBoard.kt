package com.akynex.logicmix.feature.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.domain.visual.place.Cell
import com.akynex.logicmix.domain.visual.place.Lattice
import com.akynex.logicmix.domain.visual.place.PlaceGame
import com.akynex.logicmix.domain.visual.place.Placement
import com.akynex.logicmix.domain.visual.place.shapeOf
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val SQ3 = 1.7320508f

/** Pixel geometry of a lattice. [s] is the cell side (squares) or the circumradius (pointy-top hexagons). */
internal class Grid(val lattice: Lattice, val s: Float) {
    val halfW: Float get() = if (lattice == Lattice.SQUARE) s / 2 else SQ3 * s / 2
    val halfH: Float get() = if (lattice == Lattice.SQUARE) s / 2 else s

    fun px(c: Cell): Offset = if (lattice == Lattice.SQUARE) Offset(c.x * s, c.y * s) else Offset(s * SQ3 * (c.x + c.y / 2f), s * 1.5f * c.y)

    fun cellAt(p: Offset): Cell {
        if (lattice == Lattice.SQUARE) return Cell(floor((p.x + s / 2) / s).toInt(), floor((p.y + s / 2) / s).toInt())
        val q = (SQ3 / 3f * p.x - p.y / 3f) / s
        val r = (2f / 3f * p.y) / s
        var rq = q.roundToInt(); var rr = r.roundToInt(); val rs = (-q - r).roundToInt()
        val dq = abs(rq - q); val dr = abs(rr - r); val ds = abs(rs - (-q - r))
        if (dq > dr && dq > ds) rq = -rr - rs else if (dr > ds) rr = -rq - rs
        return Cell(rq, rr)
    }
}

internal class PieceShape(val centers: List<Offset>, val size: Size)

internal fun shapePx(grid: Grid, shape: List<Cell>): PieceShape {
    val raw = shape.map { grid.px(it) }
    val minX = raw.minOf { it.x } - grid.halfW
    val minY = raw.minOf { it.y } - grid.halfH
    val maxX = raw.maxOf { it.x } + grid.halfW
    val maxY = raw.maxOf { it.y } + grid.halfH
    return PieceShape(raw.map { Offset(it.x - minX, it.y - minY) }, Size(maxX - minX, maxY - minY))
}

private fun hexPath(c: Offset, r: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val a = Math.toRadians(60.0 * i - 30.0)
        val x = c.x + r * kotlin.math.cos(a).toFloat()
        val y = c.y + r * kotlin.math.sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** One raised tile: soft drop shadow, vertical gradient, bright top edge and a dark bottom edge. */
internal fun DrawScope.drawTile(grid: Grid, center: Offset, color: Color, alpha: Float = 1f, lift: Float = 1f) {
    val gap = grid.s * 0.04f + 0.5f
    val light = lerp(color, Color.White, 0.38f)
    val dark = lerp(color, Color.Black, 0.28f)
    if (grid.lattice == Lattice.SQUARE) {
        val side = grid.s - gap * 2
        val tl = Offset(center.x - side / 2, center.y - side / 2)
        val corner = CornerRadius(side * 0.2f)
        drawRoundRect(Color.Black.copy(alpha = 0.22f * alpha * lift), tl + Offset(0f, side * 0.07f * lift), Size(side, side), corner)
        drawRoundRect(Brush.verticalGradient(listOf(light, color, dark), tl.y, tl.y + side), tl, Size(side, side), corner, alpha = alpha)
        drawLine(Color.White.copy(alpha = 0.55f * alpha), Offset(tl.x + side * 0.2f, tl.y + side * 0.07f), Offset(tl.x + side * 0.8f, tl.y + side * 0.07f), strokeWidth = side * 0.05f)
    } else {
        val r = grid.s - gap * 1.6f
        val path = hexPath(center, r)
        drawPath(hexPath(center + Offset(0f, r * 0.07f * lift), r), Color.Black.copy(alpha = 0.22f * alpha * lift))
        drawPath(path, Brush.verticalGradient(listOf(light, color, dark), center.y - r, center.y + r), alpha = alpha)
        drawPath(path, Color.White.copy(alpha = 0.28f * alpha), style = Stroke(width = r * 0.05f))
    }
}

internal fun DrawScope.drawSocket(grid: Grid, center: Offset, fill: Color, edge: Color) {
    val gap = grid.s * 0.06f + 0.5f
    if (grid.lattice == Lattice.SQUARE) {
        val side = grid.s - gap * 2
        val tl = Offset(center.x - side / 2, center.y - side / 2)
        drawRoundRect(fill, tl, Size(side, side), CornerRadius(side * 0.2f))
        drawRoundRect(edge, tl, Size(side, side), CornerRadius(side * 0.2f), style = Stroke(1.2f))
    } else {
        val path = hexPath(center, grid.s - gap * 1.6f)
        drawPath(path, fill)
        drawPath(path, edge, style = Stroke(1.2f))
    }
}

private class TrayItem(val id: Int, val topLeft: Offset, val shape: PieceShape)

private fun nextRot(lattice: Lattice, rot: Int) = (rot + 1) % lattice.turns

/**
 * Puzzle surface for every placement family: a board with recessed sockets, placed pieces and a tray. Drag a piece to
 * move it (a snap preview shows green where it fits and red where it does not), tap to select it and use the tools to turn or mirror it.
 */
@Composable
internal fun PlaceBoard(
    game: PlaceGame,
    palette: FamilyPalette,
    onPlace: (Int, Placement) -> Boolean,
    onLift: (Int) -> Unit,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    orient: MutableMap<Int, Pair<Int, Boolean>>,
    modifier: Modifier = Modifier,
) {
    val puzzle = game.puzzle
    val lattice = puzzle.lattice
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val solvedGlow by androidx.compose.animation.core.animateFloatAsState(if (game.solved) 1f else 0f, androidx.compose.animation.core.tween(600), label = "glow")
    val colors = LogicMixTokens.colors
    val surface = androidx.compose.material3.MaterialTheme.colorScheme.surface
    val onSurface = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val currentGame by rememberUpdatedState(game)
    val currentSelected by rememberUpdatedState(selected)
    var dragId by remember(puzzle.id) { mutableStateOf<Int?>(null) }
    var dragPos by remember(puzzle.id) { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wPx = with(density) { maxWidth.toPx() }
        val pad = with(density) { 10.dp.toPx() }
        val unit = Grid(lattice, 1f)
        val boardShape = shapePx(unit, puzzle.board.toList())
        val s = min((wPx - pad * 2) / boardShape.size.width, (wPx * 0.95f) / boardShape.size.height)
        val grid = Grid(lattice, s)
        val bs = shapePx(grid, puzzle.board.toList())
        val bMin = Offset(
            puzzle.board.minOf { grid.px(it).x } - grid.halfW,
            puzzle.board.minOf { grid.px(it).y } - grid.halfH,
        )
        val shift = Offset(pad, pad) - bMin
        val boardBottom = bs.size.height + pad * 2

        val tg = Grid(lattice, s * 0.62f)
        val gapPx = pad
        fun orientation(id: Int): Pair<Int, Boolean> = orient[id] ?: game.placed[id]?.let { it.rot to it.flip } ?: (0 to false)
        fun shapeCells(id: Int): List<Cell> { val (r, f) = orientation(id); return shapeOf(lattice, puzzle.piece(id).cells, r, f) }

        val tray = ArrayList<TrayItem>()
        var cx = pad; var cy = boardBottom + gapPx + pad; var rowH = 0f
        for (piece in puzzle.pieces) {
            if (piece.id in game.placed || piece.id == dragId) continue
            val sh = shapePx(tg, shapeCells(piece.id))
            if (cx + sh.size.width > wPx - pad && cx > pad) { cx = pad; cy += rowH + pad; rowH = 0f }
            tray += TrayItem(piece.id, Offset(cx, cy), sh)
            cx += sh.size.width + pad; rowH = max(rowH, sh.size.height)
        }
        val totalH = max(cy + rowH + pad, boardBottom + gapPx + pad * 2)

        fun placedCells(id: Int, p: Placement) = puzzle.cellsOf(id, p)

        // Best snapped anchor for the dragged piece plus whether it is legal there.
        fun snap(id: Int, pos: Offset): Pair<Placement, Boolean>? {
            val (rot, flip) = orientation(id)
            val cells = shapeCells(id)
            val sh = shapePx(grid, cells)
            val topLeft = pos - Offset(sh.size.width / 2, sh.size.height * 0.5f + with(density) { 36.dp.toPx() })
            val mean = Offset(sh.centers.map { it.x }.average().toFloat(), sh.centers.map { it.y }.average().toFloat())
            val target = topLeft + mean
            val bx = puzzle.board.map { it.x }; val by = puzzle.board.map { it.y }
            val spanX = cells.maxOf { it.x }; val spanY = cells.maxOf { it.y }
            var best: Placement? = null; var bestD = Float.MAX_VALUE
            for (oy in (by.min() - spanY - 1)..(by.max() + 1)) for (ox in (bx.min() - spanX - 1)..(bx.max() + 1)) {
                val m = Offset(
                    cells.map { grid.px(Cell(it.x + ox, it.y + oy)).x }.average().toFloat() + shift.x,
                    cells.map { grid.px(Cell(it.x + ox, it.y + oy)).y }.average().toFloat() + shift.y,
                )
                val d = (m - target).getDistance()
                if (d < bestD) { bestD = d; best = Placement(rot, flip, ox, oy) }
            }
            val b = best ?: return null
            if (bestD > s * 1.4f) return null
            return b to currentGame.canPlace(id, b)
        }

        val trayNow by rememberUpdatedState(tray as List<TrayItem>)
        val snapNow by rememberUpdatedState({ id: Int, pos: Offset -> snap(id, pos) })
        val placeNow by rememberUpdatedState(onPlace)
        val liftNow by rememberUpdatedState(onLift)
        val selectNow by rememberUpdatedState(onSelect)
        val ghost = dragId?.let { id -> snap(id, dragPos)?.let { id to it } }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(with(density) { totalH.toDp() })
                .pointerInput(puzzle.id, wPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val g = currentGame
                        if (g.solved) return@awaitEachGesture
                        val onBoard = grid.cellAt(down.position - shift)
                        val boardHit = g.occupied().entries.firstOrNull { it.key == onBoard }?.value
                        val trayHit = trayNow.firstOrNull { down.position.x in it.topLeft.x - 8..it.topLeft.x + it.shape.size.width + 8 && down.position.y in it.topLeft.y - 8..it.topLeft.y + it.shape.size.height + 8 }?.id
                        val hit = boardHit ?: trayHit ?: return@awaitEachGesture
                        val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (slop == null) { selectNow(hit); return@awaitEachGesture }
                        selectNow(hit)
                        if (boardHit != null) {
                            g.placed[hit]?.let { orient[hit] = it.rot to it.flip }
                            liftNow(hit)
                        }
                        dragId = hit
                        dragPos = slop.position
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        drag(slop.id) { change -> change.consume(); dragPos = change.position }
                        val result = snapNow(hit, dragPos)
                        dragId = null
                        if (result != null && placeNow(hit, result.first)) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
        ) {
            val boardCorner = CornerRadius(18f)
            drawRoundRect(Brush.verticalGradient(listOf(palette.surfaceTop(surface), palette.surfaceBottom(surface))), size = Size(size.width, boardBottom), cornerRadius = boardCorner)
            drawRoundRect(colors.cardBorder, size = Size(size.width, boardBottom), cornerRadius = boardCorner, style = Stroke(1.5f))
            val socketFill = onSurface.copy(alpha = if (colors.isDark) 0.07f else 0.09f)
            val socketEdge = onSurface.copy(alpha = 0.2f)
            for (c in puzzle.board) {
                val tint = puzzle.targetColors?.get(c)?.let { palette.piece(it).copy(alpha = 0.28f) }
                drawSocket(grid, grid.px(c) + shift, tint ?: socketFill, socketEdge)
            }
            // Placed pieces.
            for ((id, p) in game.placed) {
                val color = palette.piece(puzzle.piece(id).color)
                for (c in placedCells(id, p)) drawTile(grid, grid.px(c) + shift, color, lift = if (id == selected) 1.6f else 1f)
                if (id == selected && !game.solved) for (c in placedCells(id, p)) drawSocket(grid, grid.px(c) + shift, Color.Transparent, colors.gold)
            }
            if (solvedGlow > 0f) drawRoundRect(colors.success.copy(alpha = 0.9f * solvedGlow), size = Size(size.width, boardBottom), cornerRadius = boardCorner, style = Stroke(4f))
            // Tray.
            if (tray.isNotEmpty() || dragId != null) {
                drawRoundRect(onSurface.copy(alpha = 0.06f), Offset(0f, boardBottom + gapPx), Size(size.width, size.height - boardBottom - gapPx), boardCorner)
            }
            for (item in tray) {
                val color = palette.piece(puzzle.piece(item.id).color)
                if (item.id == selected) drawRoundRect(colors.gold, item.topLeft - Offset(6f, 6f), Size(item.shape.size.width + 12f, item.shape.size.height + 12f), CornerRadius(12f), style = Stroke(2.5f))
                for (c in item.shape.centers) drawTile(tg, item.topLeft + c, color)
            }
            // Snap preview and the piece under the finger.
            ghost?.let { (id, snapped) ->
                val (p, ok) = snapped
                val tint = if (ok) colors.success else Color(0xFFFF6B7D)
                for (c in placedCells(id, p)) drawTile(grid, grid.px(c) + shift, tint, alpha = 0.45f, lift = 0f)
            }
            dragId?.let { id ->
                val sh = shapePx(grid, shapeCells(id))
                val topLeft = dragPos - Offset(sh.size.width / 2, sh.size.height * 0.5f + with(density) { 36.dp.toPx() })
                val color = palette.piece(puzzle.piece(id).color)
                for (c in sh.centers) drawTile(grid, topLeft + c, color, lift = 2.4f)
            }
        }
    }
}

internal fun newOrientMap() = mutableStateMapOf<Int, Pair<Int, Boolean>>()
internal fun turnOf(lattice: Lattice, o: Pair<Int, Boolean>) = nextRot(lattice, o.first) to o.second
