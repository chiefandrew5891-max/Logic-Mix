package io.github.chiefandrew5891max.logicmix.feature.home

import io.github.chiefandrew5891max.logicmix.core.locale.AppLanguage
import io.github.chiefandrew5891max.logicmix.domain.content.CategoryAvailability
import io.github.chiefandrew5891max.logicmix.domain.content.GameCategory
import io.github.chiefandrew5891max.logicmix.domain.content.GameStyle
import io.github.chiefandrew5891max.logicmix.domain.content.GameStylePrefs
import io.github.chiefandrew5891max.logicmix.domain.content.StartTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryGridTest {
    @Test fun sevenCategoriesInApprovedOrderUseThreeColumnsWithoutFiller() {
        val all = CategoryAvailability.ordered(AppLanguage.ENGLISH)
        assertEquals(
            listOf("MATCHSTICK", "NUMBER_SEQUENCE", "WORD_LOGIC", "MINI_CROSSWORD", "VISUAL_LOGIC", "WORD_SEARCH", "SUDOKU"),
            all.map { it.name },
        )
        assertEquals(listOf(3, 3, 1), categoryRows(all).map { it.size })
    }

    @Test fun chineseHidesFillwordsAndOthersRemain() {
        val zh = CategoryAvailability.ordered(AppLanguage.CHINESE_SIMPLIFIED)
        assertTrue(GameCategory.WORD_SEARCH !in zh)
        assertEquals(6, zh.size)
        assertEquals(listOf(3, 3), categoryRows(zh).map { it.size })
        assertTrue(GameCategory.WORD_SEARCH in CategoryAvailability.ordered(AppLanguage.FRENCH))
    }

    @Test fun imageNamesMatchTheAssetManifestAndAreUnique() {
        val names = GameCategory.entries.map { it.imageName } + GameStyle.entries.map { it.imageName }
        assertEquals(
            setOf(
                "game_matchsticks", "game_number_sequences", "game_word_logic", "game_mini_crosswords",
                "game_visual_logic", "game_fillwords", "game_sudoku", "mode_arcade", "mode_trainer", "mode_categories",
            ),
            names.toSet(),
        )
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun startTargetFollowsStyleButNeverBreaksAnActiveSession() {
        val all = GameCategory.entries.toSet()
        val arcade = GameStylePrefs(onboardingDone = true, style = GameStyle.ARCADE)
        assertEquals(StartTarget.StyleInfo(GameStyle.ARCADE), StartTarget.resolve(false, arcade, all))
        assertEquals(StartTarget.Default, StartTarget.resolve(true, arcade, all))
        assertEquals(StartTarget.Default, StartTarget.resolve(false, GameStylePrefs(), all))
        val single = GameStylePrefs(true, GameStyle.CATEGORIES, preferredCategory = GameCategory.SUDOKU)
        assertEquals(StartTarget.Category(GameCategory.SUDOKU), StartTarget.resolve(false, single, all))
        val fill = single.copy(preferredCategory = GameCategory.WORD_SEARCH)
        val zh = CategoryAvailability.available(AppLanguage.CHINESE_SIMPLIFIED)
        assertEquals(StartTarget.Default, StartTarget.resolve(false, fill, zh))
    }
}
