package com.akynex.logicmix.domain.arcade

import com.akynex.logicmix.domain.content.GameCategory
import kotlin.random.Random

/** Lifetime achievements; each is unlocked once and is based on real stage results. */
enum class ArcadeAchievement { STREAK_3, STREAK_5, STREAK_10, FIRST_CYCLE, FULL_SET_CYCLE, NO_HINTS, NEW_RECORD }

object ArcadeRules {
    const val BASE = 100
    const val MEDIUM_BONUS = 50
    const val HIGH_BONUS = 100
    const val NO_HINT_BONUS = 25

    /** Exact milestones 3 and 5, then every tenth stage in a row. */
    fun streakBonus(streak: Int): Int = when {
        streak == 3 -> 50
        streak == 5 -> 100
        streak >= 10 && streak % 10 == 0 -> 250
        else -> 0
    }

    /**
     * Real difficulty only: Sudoku names and 1..5 levels of the generated families. Categories without a difficulty
     * (and unknown values) return null, so no bonus is paid for it.
     */
    fun tierOf(category: GameCategory, difficulty: String?): Int? {
        val d = difficulty?.lowercase() ?: return null
        if (category == GameCategory.SUDOKU) {
            return when (d) { "easy" -> 0; "medium" -> 1; "hard", "expert" -> 2; else -> null }
        }
        val level = d.toIntOrNull() ?: return null
        return when {
            level <= 0 -> null
            level <= 2 -> 0
            level == 3 -> 1
            else -> 2
        }
    }

    /** Score needed to reach [level]; level 1 starts at 0. */
    fun levelThreshold(level: Int): Int = if (level <= 1) 0 else 250 * (level - 1) * level / 2

    fun levelOf(score: Int): Int {
        var level = 1
        while (score >= levelThreshold(level + 1)) level++
        return level
    }

    /** 0..1 progress from the current level to the next one. */
    fun levelProgress(score: Int): Float {
        val level = levelOf(score)
        val from = levelThreshold(level)
        val to = levelThreshold(level + 1)
        return ((score - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    }
}

/** What a finished puzzle reports. [difficultyTier] is null when the category has no real difficulty (0 low, 1 medium, 2 high). */
data class StageReport(
    val category: GameCategory,
    val hints: Int,
    val mistakes: Int,
    val durationMs: Long,
    val difficultyTier: Int? = null,
)

data class StageAward(val base: Int, val difficultyBonus: Int, val noHintBonus: Int, val streakBonus: Int) {
    val total: Int get() = base + difficultyBonus + noHintBonus + streakBonus
    val bonus: Int get() = total - base
}

enum class StageOutcome { NONE, SOLVED, SKIPPED }

/** The stage the host screen must open. [seed] is stable for a given run and stage; [ordinal] counts earlier stages of this category in the run. */
data class ArcadeStage(val category: GameCategory, val number: Int, val seed: Long, val ordinal: Int) {
    val id: String get() = "arcade-$seed-$number"
}

data class ArcadeStats(
    val bestScore: Int = 0,
    val bestStreak: Int = 0,
    val totalStages: Int = 0,
    val runsStarted: Int = 0,
    val achievements: Set<ArcadeAchievement> = emptySet(),
)

/**
 * One marathon. The category set is frozen at start, so changing the saved selection later only affects the next run.
 * [queue] is the rest of the current cycle; its head is the category being played.
 */
data class ArcadeRun(
    val categories: List<GameCategory>,
    val seed: Long,
    val queue: List<GameCategory>,
    val stage: Int = 1,
    val cycle: Int = 0,
    val cycleClean: Boolean = true,
    val score: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val solved: Int = 0,
    val skipped: Int = 0,
    val cyclesDone: Int = 0,
    val recordBeaten: Boolean = false,
    val outcome: StageOutcome = StageOutcome.NONE,
    val lastAward: StageAward? = null,
    val perCategory: Map<GameCategory, Int> = emptyMap(),
) {
    val current: ArcadeStage
        get() = ArcadeStage(queue.first(), stage, seed * 1_000_003L + stage, perCategory[queue.first()] ?: 0)
    val level: Int get() = ArcadeRules.levelOf(score)
}

data class StageResult(
    val run: ArcadeRun,
    val stats: ArcadeStats,
    val accepted: Boolean,
    val award: StageAward? = null,
    val levelUp: Boolean = false,
    val achievements: List<ArcadeAchievement> = emptyList(),
)

object ArcadeEngine {
    /** One cycle contains every category once; the first one never equals [previousLast] when there is a choice. */
    fun cycleOrder(categories: List<GameCategory>, previousLast: GameCategory?, seed: Long, cycle: Int): List<GameCategory> {
        val order = categories.distinct().shuffled(Random(seed * 31 + cycle)).toMutableList()
        if (order.size > 1 && order.first() == previousLast) {
            val last = order.lastIndex
            order[0] = order[last].also { order[last] = order[0] }
        }
        return order
    }

    fun start(categories: List<GameCategory>, seed: Long): ArcadeRun? {
        val set = categories.distinct()
        if (set.isEmpty()) return null
        return ArcadeRun(set, seed, cycleOrder(set, null, seed, 0))
    }

    /** Applies a finished puzzle. Repeated or foreign reports are rejected, so a callback can never pay twice. */
    fun complete(run: ArcadeRun, stats: ArcadeStats, report: StageReport): StageResult {
        if (run.outcome != StageOutcome.NONE || run.queue.firstOrNull() != report.category) {
            return StageResult(run, stats, accepted = false)
        }
        val streak = run.streak + 1
        val award = StageAward(
            base = ArcadeRules.BASE,
            difficultyBonus = when (report.difficultyTier) { 1 -> ArcadeRules.MEDIUM_BONUS; 2 -> ArcadeRules.HIGH_BONUS; else -> 0 },
            noHintBonus = if (report.hints == 0) ArcadeRules.NO_HINT_BONUS else 0,
            streakBonus = ArcadeRules.streakBonus(streak),
        )
        val score = run.score + award.total
        val cycleEnds = run.queue.size == 1
        val cleanCycleEnds = cycleEnds && run.cycleClean
        val newRecord = stats.bestScore > 0 && score > stats.bestScore && !run.recordBeaten
        val next = run.copy(
            score = score, streak = streak, bestStreak = maxOf(run.bestStreak, streak), solved = run.solved + 1,
            cyclesDone = run.cyclesDone + if (cleanCycleEnds) 1 else 0,
            recordBeaten = run.recordBeaten || newRecord,
            outcome = StageOutcome.SOLVED, lastAward = award,
            perCategory = run.perCategory + (report.category to ((run.perCategory[report.category] ?: 0) + 1)),
        )
        val earned = buildList {
            if (streak >= 3) add(ArcadeAchievement.STREAK_3)
            if (streak >= 5) add(ArcadeAchievement.STREAK_5)
            if (streak >= 10) add(ArcadeAchievement.STREAK_10)
            if (report.hints == 0) add(ArcadeAchievement.NO_HINTS)
            if (newRecord) add(ArcadeAchievement.NEW_RECORD)
            if (cleanCycleEnds) {
                add(ArcadeAchievement.FIRST_CYCLE)
                if (run.categories.size == GameCategory.entries.size) add(ArcadeAchievement.FULL_SET_CYCLE)
            }
        }.filter { it !in stats.achievements }
        val nextStats = stats.copy(
            bestScore = maxOf(stats.bestScore, score), bestStreak = maxOf(stats.bestStreak, streak),
            totalStages = stats.totalStages + 1, achievements = stats.achievements + earned,
        )
        return StageResult(next, nextStats, true, award, next.level > run.level, earned)
    }

    /** Gives the stage up: no points, the streak ends. Ignored once the stage is resolved. */
    fun skip(run: ArcadeRun): ArcadeRun =
        if (run.outcome != StageOutcome.NONE) run
        else run.copy(streak = 0, skipped = run.skipped + 1, cycleClean = false, outcome = StageOutcome.SKIPPED, lastAward = null)

    /** Moves to the next stage after a resolved one; a new cycle starts when the queue is used up. */
    fun advance(run: ArcadeRun): ArcadeRun {
        if (run.outcome == StageOutcome.NONE) return run
        val rest = run.queue.drop(1)
        return if (rest.isNotEmpty()) {
            run.copy(queue = rest, stage = run.stage + 1, outcome = StageOutcome.NONE, lastAward = null)
        } else {
            val cycle = run.cycle + 1
            run.copy(
                queue = cycleOrder(run.categories, run.queue.first(), run.seed, cycle), stage = run.stage + 1, cycle = cycle,
                cycleClean = true, outcome = StageOutcome.NONE, lastAward = null,
            )
        }
    }

    /** Removes categories that cannot be played now (language, exhausted content). Null when nothing is left. */
    fun restrictTo(run: ArcadeRun, playable: Set<GameCategory>): ArcadeRun? {
        val categories = run.categories.filter { it in playable }
        if (categories.isEmpty()) return null
        if (categories.size == run.categories.size) return run
        val queue = run.queue.filter { it in playable }
        if (queue.isEmpty()) {
            val cycle = run.cycle + 1
            return run.copy(
                categories = categories, queue = cycleOrder(categories, run.queue.firstOrNull(), run.seed, cycle), cycle = cycle,
                cycleClean = true, outcome = StageOutcome.NONE, lastAward = null,
            )
        }
        val sameHead = queue.first() == run.queue.first()
        return run.copy(
            categories = categories, queue = queue,
            outcome = if (sameHead) run.outcome else StageOutcome.NONE, lastAward = if (sameHead) run.lastAward else null,
        )
    }
}

/** Plain-text storage format: no Android classes, so it is unit-testable and independent of Room. */
object ArcadeCodec {
    private fun List<GameCategory>.enc() = joinToString(",") { it.name }
    private fun String.cats() = split(",").filter { it.isNotEmpty() }.mapNotNull { n -> GameCategory.entries.firstOrNull { it.name == n } }

    fun encode(run: ArcadeRun): String = listOf(
        "v1", run.categories.enc(), run.seed, run.queue.enc(), run.stage, run.cycle, run.cycleClean, run.score, run.streak, run.bestStreak,
        run.solved, run.skipped, run.cyclesDone, run.recordBeaten, run.outcome.name,
        run.lastAward?.let { "${it.base}:${it.difficultyBonus}:${it.noHintBonus}:${it.streakBonus}" }.orEmpty(),
        run.perCategory.entries.joinToString(",") { "${it.key.name}:${it.value}" },
    ).joinToString("|")

    fun decode(text: String?): ArcadeRun? = runCatching {
        val f = text!!.split("|")
        require(f.size == 17 && f[0] == "v1")
        val categories = f[1].cats()
        val queue = f[3].cats()
        require(categories.isNotEmpty() && queue.isNotEmpty() && queue.all { it in categories })
        ArcadeRun(
            categories, f[2].toLong(), queue, f[4].toInt(), f[5].toInt(), f[6].toBoolean(), f[7].toInt(), f[8].toInt(), f[9].toInt(),
            f[10].toInt(), f[11].toInt(), f[12].toInt(), f[13].toBoolean(), StageOutcome.valueOf(f[14]),
            f[15].takeIf { it.isNotEmpty() }?.split(":")?.map { it.toInt() }?.let { StageAward(it[0], it[1], it[2], it[3]) },
            f[16].split(",").filter { it.isNotEmpty() }.associate { p ->
                val (n, c) = p.split(":")
                GameCategory.valueOf(n) to c.toInt()
            },
        )
    }.getOrNull()

    fun encode(stats: ArcadeStats): String =
        "v1|${stats.bestScore}|${stats.bestStreak}|${stats.totalStages}|${stats.runsStarted}|${stats.achievements.joinToString(",") { it.name }}"

    fun decodeStats(text: String?): ArcadeStats = runCatching {
        val f = text!!.split("|")
        require(f.size == 6 && f[0] == "v1")
        ArcadeStats(
            f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(),
            f[5].split(",").mapNotNull { n -> ArcadeAchievement.entries.firstOrNull { it.name == n } }.toSet(),
        )
    }.getOrDefault(ArcadeStats())
}
