package com.akynex.logicmix.core.analytics

/**
 * Состояние аналитики одной игровой сессии/экрана (создаётся во ViewModel, не в Composable — рекомпозиция
 * не может породить событий). Все методы идемпотентны на уровне реальных действий.
 */
class GameAnalytics(
    private val tracker: AnalyticsTracker,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Active(val key: String, val ctx: GameContext, val startedAt: Long) {
        var attempts = 0
        var hints = 0
    }

    private var sessionCtx: GameContext? = null
    private var sessionStart = 0L
    private var puzzlesPlayed = 0
    private var cur: Active? = null
    private var ended = false

    private fun log(name: String, ctx: GameContext, extra: Map<String, Any> = emptyMap()) =
        tracker.log(AnalyticsEvent(name, ctx.toParams() + extra))

    /** Открыта категория: category_open + game_start, один раз за жизнь экрана. */
    fun openCategory(ctx: GameContext) {
        if (sessionCtx != null) return
        sessionCtx = ctx; sessionStart = clock()
        log(Ev.CATEGORY_OPEN, ctx)
        log(Ev.GAME_START, ctx)
    }

    fun selectDifficulty(ctx: GameContext) = log(Ev.DIFFICULTY_SELECT, ctx)
    fun selectMode(ctx: GameContext) = log(Ev.GAME_MODE_SELECT, ctx)

    /** key — любой стабильный идентификатор головоломки; он остаётся локально и в Firebase не уходит. */
    fun puzzleStarted(key: String, ctx: GameContext, resumed: Boolean = false, attempts: Int = 0, hints: Int = 0) {
        if (cur?.key == key) return
        cur?.let { abandonActive(ExitReason.SWITCHED) }
        cur = Active(key, ctx, clock()).also { it.attempts = attempts; it.hints = hints }
        puzzlesPlayed++
        log(Ev.PUZZLE_START, ctx, mapOf(P.IS_RESUME to resumed))
    }

    fun hintRequested() {
        val a = cur ?: return
        log(Ev.HINT_REQUEST, a.ctx, mapOf(P.HINT_COUNT to a.hints.toLong()))
    }

    fun hintUsed() {
        val a = cur ?: return
        a.hints++
        log(Ev.HINT_USED, a.ctx, mapOf(P.HINT_COUNT to a.hints.toLong()))
    }

    fun attemptFailed() {
        val a = cur ?: return
        a.attempts++
        log(Ev.PUZZLE_FAIL, a.ctx, mapOf(P.ATTEMPT_COUNT to a.attempts.toLong(), P.RESULT to PuzzleResult.FAILED.key))
    }

    fun restarted() {
        val a = cur ?: return
        log(Ev.LEVEL_RESTART, a.ctx, mapOf(P.ATTEMPT_COUNT to a.attempts.toLong(), P.HINT_COUNT to a.hints.toLong()))
    }

    fun completed(xp: Int = 0) {
        val a = cur ?: return
        cur = null
        val attempts = (a.attempts + 1).toLong()
        log(Ev.PUZZLE_COMPLETE, a.ctx, mapOf(
            P.DURATION_MS to (clock() - a.startedAt).coerceAtLeast(0), P.ATTEMPT_COUNT to attempts,
            P.HINT_COUNT to a.hints.toLong(), P.RESULT to PuzzleResult.SOLVED.key,
        ))
        if (xp > 0) log(Ev.XP_EARNED, a.ctx, mapOf(P.XP to xp.toLong()))
    }

    fun skipped() {
        val a = cur ?: return
        cur = null
        log(Ev.PUZZLE_SKIP, a.ctx, mapOf(
            P.DURATION_MS to (clock() - a.startedAt).coerceAtLeast(0), P.ATTEMPT_COUNT to a.attempts.toLong(),
            P.HINT_COUNT to a.hints.toLong(), P.RESULT to PuzzleResult.SKIPPED.key,
        ))
    }

    fun abandoned(reason: ExitReason = ExitReason.EXPLICIT) = abandonActive(reason)

    private fun abandonActive(reason: ExitReason) {
        val a = cur ?: return
        cur = null
        log(Ev.PUZZLE_ABANDON, a.ctx, mapOf(
            P.DURATION_MS to (clock() - a.startedAt).coerceAtLeast(0), P.ATTEMPT_COUNT to a.attempts.toLong(),
            P.HINT_COUNT to a.hints.toLong(), P.RESULT to PuzzleResult.ABANDONED.key, P.EXIT_REASON to reason.key,
        ))
    }

    /**
     * Явный уход с экрана категории (ViewModel.onCleared). Фон приложения сюда не попадает; если процесс убит системой,
     * session_end просто не будет отправлен — мы не выдумываем его.
     */
    fun leave() {
        if (ended) return
        abandonActive(ExitReason.EXPLICIT)
        val ctx = sessionCtx ?: return
        ended = true
        log(Ev.SESSION_END, ctx, mapOf(P.DURATION_MS to (clock() - sessionStart).coerceAtLeast(0), P.PUZZLES_PLAYED to puzzlesPlayed.toLong()))
    }
}
