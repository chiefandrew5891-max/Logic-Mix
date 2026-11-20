package io.github.chiefandrew5891max.logicmix.domain.content

import io.github.chiefandrew5891max.logicmix.core.locale.AppLanguage

enum class GameCategory { WORD_SEARCH }

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
}
