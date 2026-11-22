package com.akynex.logicmix.core.arcade

import com.akynex.logicmix.core.analytics.AnalyticsEvent
import com.akynex.logicmix.core.analytics.AnalyticsTracker
import com.akynex.logicmix.core.analytics.analyticsKey
import com.akynex.logicmix.domain.arcade.ArcadeRun
import com.akynex.logicmix.domain.arcade.ArcadeStage
import com.akynex.logicmix.domain.arcade.StageAward
import com.akynex.logicmix.domain.arcade.StageReport
import com.akynex.logicmix.domain.content.GameCategory

object ArcadeEv {
    const val START = "arcade_start"
    const val STAGE_COMPLETE = "arcade_stage_complete"
    const val STAGE_FAIL = "arcade_stage_fail"
    const val STREAK = "arcade_streak"
    const val LEVEL_UP = "arcade_level_up"
    const val RESUME = "arcade_resume"
    const val EXIT = "arcade_exit"
    const val CONFIG_CHANGED = "arcade_configuration_changed"
}

/** Arcade events through the shared tracker, so they obey the same consent gate as every other event. Carries no personal data. */
class ArcadeAnalytics(private val tracker: AnalyticsTracker) {
    private fun log(name: String, run: ArcadeRun, extra: Map<String, Any> = emptyMap()) {
        val p = HashMap<String, Any>(extra)
        p["categories_selected"] = run.categories.size.toLong()
        p["stage_number"] = run.stage.toLong()
        p["streak"] = run.streak.toLong()
        tracker.log(AnalyticsEvent(name, p))
    }

    private fun stageParams(stage: ArcadeStage) = mapOf<String, Any>("game_category" to stage.category.analyticsKey)

    fun start(run: ArcadeRun) = log(ArcadeEv.START, run)
    fun resume(run: ArcadeRun) = log(ArcadeEv.RESUME, run, stageParams(run.current))
    fun exit(run: ArcadeRun) = log(ArcadeEv.EXIT, run, stageParams(run.current) + ("score" to run.score.toLong()))

    fun stageComplete(run: ArcadeRun, stage: ArcadeStage, report: StageReport, award: StageAward) =
        log(
            ArcadeEv.STAGE_COMPLETE, run,
            stageParams(stage) + mapOf(
                "score_awarded" to award.total.toLong(),
                "difficulty" to (report.difficultyTier ?: -1).toLong(),
                "duration_ms" to report.durationMs,
                "mistakes" to report.mistakes.toLong(),
                "hints" to report.hints.toLong(),
            ),
        )

    fun stageFail(run: ArcadeRun, stage: ArcadeStage, reason: String) =
        log(ArcadeEv.STAGE_FAIL, run, stageParams(stage) + ("reason" to reason))

    fun streak(run: ArcadeRun) = log(ArcadeEv.STREAK, run)
    fun levelUp(run: ArcadeRun) = log(ArcadeEv.LEVEL_UP, run, mapOf("level" to run.level.toLong(), "score" to run.score.toLong()))
    fun configurationChanged(selected: Set<GameCategory>) =
        tracker.log(AnalyticsEvent(ArcadeEv.CONFIG_CHANGED, mapOf("categories_selected" to selected.size.toLong())))
}
