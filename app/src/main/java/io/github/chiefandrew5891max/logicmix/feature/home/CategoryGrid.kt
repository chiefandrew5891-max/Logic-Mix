package io.github.chiefandrew5891max.logicmix.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.chiefandrew5891max.logicmix.R
import io.github.chiefandrew5891max.logicmix.core.designsystem.CategoryCard
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixTokens
import io.github.chiefandrew5891max.logicmix.domain.content.GameCategory

const val CATEGORY_COLUMNS = 3

@StringRes
fun GameCategory.titleRes(): Int = when (this) {
    GameCategory.MATCHSTICK -> R.string.category_matchstick
    GameCategory.NUMBER_SEQUENCE -> R.string.category_number_sequence
    GameCategory.WORD_LOGIC -> R.string.category_word_logic
    GameCategory.MINI_CROSSWORD -> R.string.category_mini_crossword
    GameCategory.VISUAL_LOGIC -> R.string.category_visual_logic
    GameCategory.WORD_SEARCH -> R.string.category_word_search
    GameCategory.SUDOKU -> R.string.category_sudoku
    GameCategory.ORB_LINES -> R.string.category_orb_lines
    GameCategory.TWO_DOTS -> R.string.category_two_dots
}

/** Splits categories into rows of [CATEGORY_COLUMNS]; the last row is never padded with fake items. */
fun categoryRows(categories: List<GameCategory>): List<List<GameCategory>> = categories.chunked(CATEGORY_COLUMNS)

/**
 * Exactly three columns of equal-width weight(1f) cells; every card is square (aspectRatio 1:1), so the row height
 * follows the width (no LazyGrid, no fixed heights, no space reserved for titles).
 * [selected] != null turns the grid into a selection list ([multi] picks checkbox vs radio semantics).
 */
@Composable
fun CategoryGrid(
    categories: List<GameCategory>,
    onClick: (GameCategory) -> Unit,
    modifier: Modifier = Modifier,
    selected: Set<GameCategory>? = null,
    multi: Boolean = false,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS)) {
        categoryRows(categories).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS),
            ) {
                row.forEach { category ->
                    CategoryCard(
                        category = category,
                        title = stringResource(category.titleRes()),
                        onClick = { onClick(category) },
                        modifier = Modifier.weight(1f),
                        selected = selected?.let { category in it },
                        role = if (selected == null) Role.Button else if (multi) Role.Checkbox else Role.RadioButton,
                    )
                }
                repeat(CATEGORY_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
