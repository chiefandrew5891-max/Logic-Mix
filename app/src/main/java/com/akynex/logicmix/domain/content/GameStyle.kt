package com.akynex.logicmix.domain.content

/** How the player prefers to play. Stored by name; never restricts access to any category. */
enum class GameStyle {
    ARCADE, TRAINER, CATEGORIES;

    val imageName: String
        get() = when (this) {
            ARCADE -> "mode_arcade"
            TRAINER -> "mode_trainer"
            CATEGORIES -> "mode_categories"
        }

    companion object {
        fun fromStored(value: String?): GameStyle? = entries.firstOrNull { it.name == value }
    }
}

/** Persisted game-style choices. [style] is null until onboarding is confirmed. */
data class GameStylePrefs(
    val onboardingDone: Boolean = false,
    val style: GameStyle? = null,
    val trainerCategories: Set<GameCategory> = emptySet(),
    val arcadeCategories: Set<GameCategory> = emptySet(),
    val preferredCategory: GameCategory? = null,
)

/** Where the main "Start/Continue" button leads, given the saved style. Pure so it can be unit-tested. */
sealed interface StartTarget {
    /** Existing behaviour: resume or start the playable career (number sequences) game. */
    data object Default : StartTarget
    data class Category(val category: GameCategory) : StartTarget
    data class StyleInfo(val style: GameStyle) : StartTarget
    data object Arcade : StartTarget

    companion object {
        fun resolve(
            hasActiveSession: Boolean,
            prefs: GameStylePrefs,
            available: Set<GameCategory>,
        ): StartTarget {
            // An unfinished session is always resumed, whatever the preferred style is.
            if (hasActiveSession || !prefs.onboardingDone) return Default
            return when (prefs.style) {
                GameStyle.ARCADE -> Arcade
                GameStyle.TRAINER -> StyleInfo(prefs.style)
                GameStyle.CATEGORIES -> prefs.preferredCategory?.takeIf { it in available }?.let(::Category) ?: Default
                null -> Default
            }
        }
    }
}
