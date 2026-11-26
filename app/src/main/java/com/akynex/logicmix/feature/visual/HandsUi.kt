package com.akynex.logicmix.feature.visual

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.GameTool
import com.akynex.logicmix.core.designsystem.GameToolRow
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.core.designsystem.XpLine
import com.akynex.logicmix.domain.visual.place.InteractiveFamily
import com.akynex.logicmix.domain.visual.place.PlaceFamily
import com.akynex.logicmix.feature.game.ActionButton
import com.akynex.logicmix.feature.game.Card

/** Each family has its own colour mood so the puzzles feel different at a glance. */
internal class FamilyPalette(val accent: Color, private val pieces: List<Color>, private val tint: Color) {
    fun piece(i: Int): Color = pieces[((i % pieces.size) + pieces.size) % pieces.size]
    fun surfaceTop(surface: Color): Color = lerp(surface, tint, 0.16f)
    fun surfaceBottom(surface: Color): Color = lerp(surface, tint, 0.04f)
}

private fun c(v: Long) = Color(v)

internal fun paletteOf(f: InteractiveFamily): FamilyPalette = when (f) {
    InteractiveFamily.POLYOMINO -> FamilyPalette(c(0xFF9B7BFF), listOf(c(0xFF8F6CFF), c(0xFF3EE6D6), c(0xFFFFB300), c(0xFFFF6B7D), c(0xFF4C9BFF), c(0xFF3DDC97)), c(0xFF9B7BFF))
    InteractiveFamily.SILHOUETTE -> FamilyPalette(c(0xFFFF9F43), listOf(c(0xFFFF9F43), c(0xFFFFD66B), c(0xFFFF6B7D), c(0xFFB8860B), c(0xFFE76F51), c(0xFFFFC857)), c(0xFFFFB300))
    InteractiveFamily.DISSECTION -> FamilyPalette(c(0xFF3EE6D6), listOf(c(0xFF1FB5C9), c(0xFF4C9BFF), c(0xFF3EE6D6), c(0xFF6C7BFF), c(0xFF22C3A6), c(0xFF5AB8F5)), c(0xFF3EE6D6))
    InteractiveFamily.MOSAIC -> FamilyPalette(c(0xFFFF6B7D), listOf(c(0xFFFF6B7D), c(0xFF3EE6D6), c(0xFFFFB300), c(0xFF9B7BFF), c(0xFF3DDC97), c(0xFF4C9BFF)), c(0xFFFF6B7D))
    InteractiveFamily.HEX -> FamilyPalette(c(0xFF3DDC97), listOf(c(0xFF3DDC97), c(0xFF2BB5B8), c(0xFF8BD450), c(0xFF35A7FF), c(0xFFB0E57C), c(0xFF1FCFA1)), c(0xFF3DDC97))
    InteractiveFamily.PARADOX -> FamilyPalette(c(0xFFFFB300), listOf(c(0xFF4C9BFF), c(0xFFFF6B7D)), c(0xFFFF6B7D))
    InteractiveFamily.RUSH_HOUR -> FamilyPalette(c(0xFFE5484D), listOf(c(0xFF4C9BFF), c(0xFF3DDC97), c(0xFF9B7BFF), c(0xFFFFB300), c(0xFF3EE6D6), c(0xFF8E9AAF)), c(0xFF8E9AAF))
    InteractiveFamily.KLOTSKI -> FamilyPalette(c(0xFFE5484D), listOf(c(0xFFC98B4B), c(0xFFB5733D), c(0xFFD9A066), c(0xFFA8643A), c(0xFFE0B27A), c(0xFF9C6B3F)), c(0xFFC98B4B))
    InteractiveFamily.CUBE_NET -> FamilyPalette(c(0xFF6C7BFF), listOf(c(0xFF6C7BFF), c(0xFF9B7BFF), c(0xFF4C9BFF), c(0xFF3EE6D6), c(0xFFFFB300), c(0xFFFF6B7D)), c(0xFF6C7BFF))
}

private fun InteractiveFamily.title() = when (this) {
    InteractiveFamily.POLYOMINO -> R.string.vi_fam_polyomino
    InteractiveFamily.SILHOUETTE -> R.string.vi_fam_silhouette
    InteractiveFamily.DISSECTION -> R.string.vi_fam_dissection
    InteractiveFamily.MOSAIC -> R.string.vi_fam_mosaic
    InteractiveFamily.HEX -> R.string.vi_fam_hex
    InteractiveFamily.PARADOX -> R.string.vi_fam_paradox
    InteractiveFamily.RUSH_HOUR -> R.string.vi_fam_rush
    InteractiveFamily.KLOTSKI -> R.string.vi_fam_klotski
    InteractiveFamily.CUBE_NET -> R.string.vi_fam_net
}

private fun InteractiveFamily.how() = when (this) {
    InteractiveFamily.POLYOMINO -> R.string.vi_how_polyomino
    InteractiveFamily.SILHOUETTE -> R.string.vi_how_silhouette
    InteractiveFamily.DISSECTION -> R.string.vi_how_dissection
    InteractiveFamily.MOSAIC -> R.string.vi_how_mosaic
    InteractiveFamily.HEX -> R.string.vi_how_hex
    InteractiveFamily.PARADOX -> R.string.vi_how_paradox
    InteractiveFamily.RUSH_HOUR -> R.string.vi_how_rush
    InteractiveFamily.KLOTSKI -> R.string.vi_how_klotski
    InteractiveFamily.CUBE_NET -> R.string.vi_how_net
}

@Composable
internal fun HandsContent(state: VisualUiState, vm: VisualViewModel) {
    val family = state.family ?: return
    val palette = paletteOf(family)
    val solved = state.place?.solved ?: state.slide?.solved ?: state.net?.solved ?: false
    Text(stringResource(family.title()), style = LogicMixTokens.typography.titleLarge, color = palette.accent, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(stringResource(family.how()), style = LogicMixTokens.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

    state.place?.let { game ->
        val orient = remember(game.puzzle.id) { newOrientMap() }
        var selected by remember(game.puzzle.id) { mutableStateOf<Int?>(null) }
        PlaceBoard(game, palette, vm::placePiece, vm::liftPiece, selected, { selected = it }, orient)
        val lattice = game.puzzle.lattice
        val piece = selected?.let { id -> game.puzzle.pieces.firstOrNull { it.id == id } }
        if (!solved) {
            val tools = buildList {
                add(GameTool(stringResource(R.string.vi_rotate), {
                    piece?.let { p ->
                        val cur = orient[p.id] ?: game.placed[p.id]?.let { it.rot to it.flip } ?: (0 to false)
                        if (p.id in game.placed) vm.liftPiece(p.id)
                        orient[p.id] = turnOf(lattice, cur)
                    }
                }, enabled = piece?.canRotate == true))
                if (game.puzzle.pieces.any { it.canFlip }) add(GameTool(stringResource(R.string.vi_flip), {
                    piece?.let { p ->
                        val cur = orient[p.id] ?: game.placed[p.id]?.let { it.rot to it.flip } ?: (0 to false)
                        if (p.id in game.placed) vm.liftPiece(p.id)
                        orient[p.id] = cur.first to !cur.second
                    }
                }, enabled = piece?.canFlip == true))
                add(GameTool(stringResource(R.string.vi_undo), vm::undoPlace, enabled = game.history.isNotEmpty()))
                add(GameTool(stringResource(R.string.vi_reset), vm::resetPlace, enabled = game.placed.isNotEmpty()))
                add(GameTool(stringResource(R.string.cat_hint), vm::hint))
            }
            GameToolRow(tools)
        }
        if (solved && game.puzzle.family == PlaceFamily.PARADOX) {
            val cells = game.puzzle.pieces.sumOf { it.cells.size }
            Card {
                Text(stringResource(R.string.vi_paradox_title), style = LogicMixTokens.typography.titleMedium, color = LogicMixTokens.colors.gold, textAlign = TextAlign.Center)
                Text(stringResource(R.string.vi_paradox_body, cells, game.puzzle.board.size), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            }
        }
    }
    state.slide?.let { game ->
        SlideBoard(game, palette, vm::slideMove)
        if (!solved) {
            Text(stringResource(R.string.vi_moves, game.moves), color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            GameToolRow(listOf(
                GameTool(stringResource(R.string.vi_undo), vm::slideUndo, enabled = game.history.isNotEmpty()),
                GameTool(stringResource(R.string.vi_reset), vm::slideReset, enabled = game.history.isNotEmpty()),
                GameTool(stringResource(R.string.cat_hint), vm::hint),
            ))
        }
    }
    state.net?.let { game ->
        NetBoard(game, palette, vm::netTap)
        if (!solved) GameToolRow(listOf(GameTool(stringResource(R.string.cat_hint), vm::hint)))
    }
    if (solved) {
        Card {
            Text(stringResource(R.string.game_correct), style = LogicMixTokens.typography.headlineMedium, color = LogicMixTokens.colors.success, textAlign = TextAlign.Center)
            XpLine(state.xpAwarded)
        }
        ActionButton(stringResource(R.string.game_next), vm::next, filled = true)
    }
}
