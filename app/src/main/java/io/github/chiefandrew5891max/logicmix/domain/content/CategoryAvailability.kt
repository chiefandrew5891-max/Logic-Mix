package io.github.chiefandrew5891max.logicmix.domain.content

import io.github.chiefandrew5891max.logicmix.core.locale.AppLanguage

/** Declaration order is the display order on the home screen; ids are persisted by name. */
enum class GameCategory {
    MATCHSTICK, NUMBER_SEQUENCE, WORD_LOGIC, MINI_CROSSWORD, VISUAL_LOGIC, WORD_SEARCH, SUDOKU, ORB_LINES;

    /** Name of the optional PNG in res/drawable-nodpi; see IMAGE_ASSETS.md. */
    val imageName: String
        get() = when (this) {
            MATCHSTICK -> "game_matchsticks"
            NUMBER_SEQUENCE -> "game_number_sequences"
            WORD_LOGIC -> "game_word_logic"
            MINI_CROSSWORD -> "game_mini_crosswords"
            VISUAL_LOGIC -> "game_visual_logic"
            WORD_SEARCH -> "game_fillwords"
            SUDOKU -> "game_sudoku"
            ORB_LINES -> "game_orb_lines"
        }

    /** Categories with a real playable screen; the rest open the localized coming-soon screen. */
    val isPlayable: Boolean get() = this == NUMBER_SEQUENCE || this == WORD_LOGIC || this == MINI_CROSSWORD || this == VISUAL_LOGIC || this == WORD_SEARCH || this == SUDOKU || this == MATCHSTICK || this == ORB_LINES
}

/**
 * Which UI languages a category is offered for. A hidden category has no entry point at all.
 * Chinese word search needs its own character-based mechanic, so it is off until that exists:
 * remove CHINESE_SIMPLIFIED from the set below and add a ContentLanguage dictionary to enable it.
 */
object CategoryAvailability {
    private val unavailable: Map<GameCategory, Set<AppLanguage>> = mapOf(
        GameCategory.WORD_SEARCH to setOf(AppLanguage.CHINESE_SIMPLIFIED),
    )

    fun isAvailable(category: GameCategory, language: AppLanguage): Boolean =
        language !in unavailable[category].orEmpty()

    fun available(language: AppLanguage): Set<GameCategory> =
        GameCategory.entries.filter { isAvailable(it, language) }.toSet()

    /** Available categories in display order. */
    fun ordered(language: AppLanguage): List<GameCategory> =
        GameCategory.entries.filter { isAvailable(it, language) }
}
