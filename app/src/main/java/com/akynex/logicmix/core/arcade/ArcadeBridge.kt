package com.akynex.logicmix.core.arcade

import com.akynex.logicmix.domain.arcade.ArcadeStage
import com.akynex.logicmix.domain.arcade.StageReport
import com.akynex.logicmix.domain.content.GameCategory

/**
 * Мост между Arcade и обычными экранами игр. Пока открыт хост Arcade, [stage] задаёт текущий этап: экран категории
 * подхватывает его при создании ViewModel и сообщает о решении через GameAnalytics.completed. Вне Arcade поле null,
 * поэтому Career и остальные режимы работают как раньше.
 */
class ArcadeBridge {
    @Volatile var stage: ArcadeStage? = null
    @Volatile var onCompleted: ((ArcadeStage, StageReport) -> Unit)? = null
    @Volatile var onExhausted: ((ArcadeStage) -> Unit)? = null

    /** Этап Arcade, если он сейчас играется именно в этой категории. */
    fun activeFor(category: GameCategory): ArcadeStage? = stage?.takeIf { it.category == category }

    fun report(report: StageReport) {
        val current = activeFor(report.category) ?: return
        onCompleted?.invoke(current, report)
    }

    /** Контент категории закончился и повторить его нечем: этап пропускается без штрафа. */
    fun exhausted(category: GameCategory) {
        val current = activeFor(category) ?: return
        onExhausted?.invoke(current)
    }
}
