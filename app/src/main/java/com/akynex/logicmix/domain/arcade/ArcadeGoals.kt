package com.akynex.logicmix.domain.arcade

import kotlin.random.Random

/**
 * Finite Arcade goals for the two games that are endless or score-driven in normal play. They only apply to a hosted
 * Arcade stage; normal Lines and Two Dots keep their own rules. Values come from simulated play (see ArcadeGoalsTest),
 * and vary slightly per stage instead of growing every time.
 */
object ArcadeGoals {
    /** Lines removed (a move or spawn that clears at least one five-in-a-row): 2 on the first stage of a run, then 2 or 3. */
    fun orbLines(stage: ArcadeStage): Int =
        if (stage.ordinal == 0) 2 else 2 + Random(stage.seed * 17 + stage.ordinal).nextInt(2)

    /** Score within the 20 moves of a classic game: 150 on the first stage, then 150..250 in steps of 25. */
    fun twoDotsScore(stage: ArcadeStage): Int =
        if (stage.ordinal == 0) 150 else 150 + 25 * Random(stage.seed * 19 + stage.ordinal).nextInt(5)
}
