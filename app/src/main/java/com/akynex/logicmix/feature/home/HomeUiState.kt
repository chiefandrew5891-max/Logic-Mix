package com.akynex.logicmix.feature.home

import com.akynex.logicmix.domain.content.GameCategory
import com.akynex.logicmix.domain.content.GameStylePrefs
import com.akynex.logicmix.domain.progression.PlayerProgress
import com.akynex.logicmix.domain.progression.Progression

enum class PrimaryAction { START, CONTINUE }

data class HomeUiState(
    val level: Int = Progression.MIN_LEVEL,
    val xpIntoLevel: Int = 0,
    // Null at the maximum level, where there is no next threshold.
    val xpForNextLevel: Int? = Progression.requiredXp(Progression.MIN_LEVEL),
    val streak: Int = 0,
    val primaryAction: PrimaryAction = PrimaryAction.START,
    val categories: Set<GameCategory> = GameCategory.entries.toSet(),
    val gameStyle: GameStylePrefs = GameStylePrefs(),
) {
    val xpFraction: Float
        get() = xpForNextLevel?.let { (xpIntoLevel.toFloat() / it).coerceIn(0f, 1f) } ?: 1f
}

object HomeStateMapper {
    fun primaryAction(hasActiveSession: Boolean): PrimaryAction =
        if (hasActiveSession) PrimaryAction.CONTINUE else PrimaryAction.START

    fun map(
        progress: PlayerProgress,
        streak: Int,
        hasActiveSession: Boolean,
        categories: Set<GameCategory> = GameCategory.entries.toSet(),
        gameStyle: GameStylePrefs = GameStylePrefs(),
    ) = HomeUiState(
        level = progress.level,
        xpIntoLevel = progress.xpIntoLevel,
        xpForNextLevel = if (progress.level < Progression.MAX_LEVEL) Progression.requiredXp(progress.level) else null,
        streak = streak,
        primaryAction = primaryAction(hasActiveSession),
        categories = categories,
        gameStyle = gameStyle,
    )
}
