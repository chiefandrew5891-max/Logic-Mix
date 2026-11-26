package com.akynex.logicmix.feature.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.domain.visual.place.Cell
import com.akynex.logicmix.domain.visual.place.NetGame
import com.akynex.logicmix.domain.visual.place.SlideGame
import com.akynex.logicmix.domain.visual.place.SlideKind
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

@Composable
internal fun SlideBoard(game: SlideGame, palette: FamilyPalette, onMove: (Int, Int, Int) -> Boolean, modifier: Modifier = Modifier) {
    val p = game.puzzle
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val colors = LogicMixTokens.colors
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val current by rememberUpdatedState(game)
    val move by rememberUpdatedState(onMove)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wPx = with(density) { maxWidth.toPx() }
        val pad = with(density) { 14.dp.toPx() }
        val s = min((wPx - pad * 2 - (if (p.kind == SlideKind.RUSH_HOUR) pad else 0f)) / p.w, wPx * 0.95f / p.h)
        val boardW = s * p.w + pad * 2
        val left = (wPx - boardW) / 2f
        val totalH = s * p.h + pad * 2
        Canvas(
            Modifier.fillMaxWidth().height(with(density) { totalH.toDp() }).pointerInput(p.id, wPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val g = current
                    if (g.solved) return@awaitEachGesture
                    val cx = floor((down.position.x - left - pad) / s).toInt()
                    val cy = floor((down.position.y - pad) / s).toInt()
                    val id = p.blocks.indices.firstOrNull { i ->
                        val b = p.blocks[i]; val o = g.pos[i]
                        cx in o.x until o.x + b.w && cy in o.y until o.y + b.h
                    } ?: return@awaitEachGesture
                    val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
                    var accX = slop.position.x - down.position.x
                    var accY = slop.position.y - down.position.y
                    var last = slop.position
                    fun step() {
                        while (true) {
                            val horizontal = abs(accX) >= abs(accY)
                            val dx = if (horizontal && abs(accX) >= s * 0.55f) (if (accX > 0) 1 else -1) else 0
                            val dy = if (!horizontal && abs(accY) >= s * 0.55f) (if (accY > 0) 1 else -1) else 0
                            if (dx == 0 && dy == 0) return
                            if (move(id, dx, dy)) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); accX -= dx * s; accY -= dy * s } else { accX = 0f; accY = 0f; return }
                        }
                    }
                    step()
                    drag(slop.id) { change ->
                        change.consume()
                        accX += change.position.x - last.x; accY += change.position.y - last.y; last = change.position
                        step()
                    }
                }
            },
        ) {
            val corner = CornerRadius(18f)
            drawRoundRect(Brush.verticalGradient(listOf(palette.surfaceTop(surface), palette.surfaceBottom(surface))), Offset(left, 0f), Size(boardW, totalH), corner)
            drawRoundRect(colors.cardBorder, Offset(left, 0f), Size(boardW, totalH), corner, style = Stroke(1.5f))
            for (y in 0 until p.h) for (x in 0 until p.w) {
                drawRoundRect(onSurface.copy(alpha = 0.07f), Offset(left + pad + x * s + 2f, pad + y * s + 2f), Size(s - 4f, s - 4f), CornerRadius(s * 0.12f))
            }
            if (p.kind == SlideKind.RUSH_HOUR) {
                val y = pad + p.goal.y * s + s / 2
                val x = left + pad + p.w * s + 4f
                drawPath(Path().apply { moveTo(x, y - s * 0.3f); lineTo(x + pad * 0.9f, y); lineTo(x, y + s * 0.3f); close() }, colors.gold)
            } else {
                val tl = Offset(left + pad + p.goal.x * s, pad + p.goal.y * s)
                drawRoundRect(colors.gold, tl + Offset(3f, 3f), Size(s * p.blocks[0].w - 6f, s * p.blocks[0].h - 6f), CornerRadius(s * 0.16f), style = Stroke(3f))
            }
            for ((i, b) in p.blocks.withIndex()) {
                val o = game.pos[i]
                val base = if (i == 0) palette.accent else palette.piece(i)
                val tl = Offset(left + pad + o.x * s + 3f, pad + o.y * s + 3f)
                val size = Size(b.w * s - 6f, b.h * s - 6f)
                val r = CornerRadius(s * 0.2f)
                drawRoundRect(Color.Black.copy(alpha = 0.25f), tl + Offset(0f, s * 0.05f), size, r)
                drawRoundRect(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.35f), base, lerp(base, Color.Black, 0.3f)), tl.y, tl.y + size.height), tl, size, r)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(tl.x + size.width * 0.12f, tl.y + size.height * 0.07f), Offset(tl.x + size.width * 0.88f, tl.y + size.height * 0.07f), strokeWidth = 3f)
                if (i == 0) drawCircle(Color.White.copy(alpha = 0.85f), min(size.width, size.height) * 0.14f, tl + Offset(size.width / 2, size.height / 2))
            }
            if (game.solved) drawRoundRect(colors.success, Offset(left, 0f), Size(boardW, totalH), corner, style = Stroke(4f))
        }
    }
}

@Composable
internal fun NetBoard(game: NetGame, palette: FamilyPalette, onTap: (Cell) -> Unit, modifier: Modifier = Modifier) {
    val p = game.puzzle
    val density = LocalDensity.current
    val colors = LogicMixTokens.colors
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    var wrong by remember(p.id) { mutableStateOf<Cell?>(null) }
    LaunchedEffect(wrong) { if (wrong != null) { kotlinx.coroutines.delay(450); wrong = null } }
    val tap by rememberUpdatedState(onTap)
    val solved = game.solved
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wPx = with(density) { maxWidth.toPx() }
        val s = min(wPx / p.w, with(density) { 64.dp.toPx() })
        val boardW = s * p.w
        val left = (wPx - boardW) / 2f
        val totalH = s * p.h
        Canvas(
            Modifier.fillMaxWidth().height(with(density) { totalH.toDp() }).pointerInput(p.id, wPx) {
                detectTapGestures { pos ->
                    val c = Cell(floor((pos.x - left) / s).toInt(), floor(pos.y / s).toInt())
                    if (c.x in 0 until p.w && c.y in 0 until p.h && c !in p.given) {
                        wrong = c
                        tap(c)
                    }
                }
            },
        ) {
            drawRoundRect(Brush.verticalGradient(listOf(palette.surfaceTop(surface), palette.surfaceBottom(surface))), Offset(left, 0f), Size(boardW, totalH), CornerRadius(18f))
            drawRoundRect(colors.cardBorder, Offset(left, 0f), Size(boardW, totalH), CornerRadius(18f), style = Stroke(1.5f))
            var n = 0
            for (y in 0 until p.h) for (x in 0 until p.w) {
                val c = Cell(x, y)
                val tl = Offset(left + x * s + 3f, y * s + 3f)
                val sz = Size(s - 6f, s - 6f)
                val r = CornerRadius(s * 0.14f)
                val chosen = game.chosen == c
                when {
                    c in p.given || chosen -> {
                        val base = if (chosen) colors.success else palette.piece(n++)
                        drawRoundRect(Color.Black.copy(alpha = 0.22f), tl + Offset(0f, s * 0.05f), sz, r)
                        drawRoundRect(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.35f), base, lerp(base, Color.Black, 0.3f)), tl.y, tl.y + sz.height), tl, sz, r)
                        drawLine(Color.White.copy(alpha = 0.5f), Offset(tl.x + sz.width * 0.15f, tl.y + sz.height * 0.07f), Offset(tl.x + sz.width * 0.85f, tl.y + sz.height * 0.07f), strokeWidth = 3f)
                    }
                    wrong == c -> drawRoundRect(Color(0xFFFF6B7D).copy(alpha = 0.55f), tl, sz, r)
                    else -> drawRoundRect(onSurface.copy(alpha = 0.18f), tl, sz, r, style = Stroke(1.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
                }
            }
            if (solved) drawRoundRect(colors.success, Offset(left, 0f), Size(boardW, totalH), CornerRadius(18f), style = Stroke(4f))
        }
    }
}
