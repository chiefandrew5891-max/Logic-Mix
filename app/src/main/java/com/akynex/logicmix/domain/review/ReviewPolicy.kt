package com.akynex.logicmix.domain.review

/** Safe local defaults; every field can be overridden through Remote Config (see AppConfig). */
data class ReviewPolicyConfig(
    val enabled: Boolean = true,
    val minDaysSinceFirstLaunch: Int = 3,
    val minCompletedTasks: Int = 15,
    val minSessions: Int = 3,
    val cooldownDays: Int = 60,
    val maxAttemptsPerYear: Int = 3,
)

/** Natural pauses between game actions in which a request is allowed at all. */
enum class ReviewMoment(val key: String) {
    TASK_COMPLETED("task_completed"),
    LEVEL_UP_CLOSED("level_up_closed"),
    MAIN_MENU("main_menu"),
}

enum class ReviewBlock { DISABLED, TOO_EARLY, FEW_TASKS, FEW_SESSIONS, COOLDOWN, YEARLY_LIMIT }

data class ReviewState(val firstLaunchMs: Long, val completedTasks: Int, val sessions: Int, val attempts: List<Long>)

object ReviewPolicy {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val YEAR_MS = 365L * DAY_MS

    /** null = a request is allowed. Timestamps from the future (clock changes) never loosen the limits. */
    fun check(state: ReviewState, config: ReviewPolicyConfig, now: Long): ReviewBlock? {
        if (!config.enabled) return ReviewBlock.DISABLED
        if (now - state.firstLaunchMs < config.minDaysSinceFirstLaunch * DAY_MS) return ReviewBlock.TOO_EARLY
        if (state.completedTasks < config.minCompletedTasks) return ReviewBlock.FEW_TASKS
        if (state.sessions < config.minSessions) return ReviewBlock.FEW_SESSIONS
        val last = state.attempts.maxOrNull()
        if (last != null && (now < last || now - last < config.cooldownDays * DAY_MS)) return ReviewBlock.COOLDOWN
        if (state.attempts.count { now - it < YEAR_MS } >= config.maxAttemptsPerYear) return ReviewBlock.YEARLY_LIMIT
        return null
    }
}

/** Persistent counters. Survive app updates; never reset by the app. */
interface ReviewStorage {
    var firstLaunchMs: Long?
    var completedTasks: Int
    var sessions: Int
    var lastActivityMs: Long
    var attempts: List<Long>
}

/**
 * Counters plus the atomic "may I ask now?" decision. [tryAcquire] records the attempt before the Play API is called, so a
 * crash, rotation or duplicate callback can never produce a second request inside the cooldown.
 */
class ReviewGate(
    private val storage: ReviewStorage,
    private val clock: () -> Long = System::currentTimeMillis,
    private val installTimeMs: () -> Long? = { null },
) {
    @Synchronized
    fun ensureFirstLaunch() {
        if (storage.firstLaunchMs == null) storage.firstLaunchMs = installTimeMs()?.takeIf { it in 1..clock() } ?: clock()
    }

    @Synchronized
    fun onTaskCompleted() {
        ensureFirstLaunch()
        storage.completedTasks = (storage.completedTasks + 1).coerceAtMost(MAX_COUNTER)
        touch()
    }

    /** Any game activity; a gap of more than [SESSION_GAP_MS] starts a new separate session. */
    @Synchronized
    fun onGameActivity() {
        ensureFirstLaunch()
        touch()
    }

    private fun touch() {
        val now = clock()
        val last = storage.lastActivityMs
        if (last == 0L || now - last > SESSION_GAP_MS || now < last) storage.sessions = (storage.sessions + 1).coerceAtMost(MAX_COUNTER)
        storage.lastActivityMs = now
    }

    @Synchronized
    fun snapshot(): ReviewState {
        ensureFirstLaunch()
        return ReviewState(storage.firstLaunchMs ?: clock(), storage.completedTasks, storage.sessions, storage.attempts)
    }

    @Synchronized
    fun check(config: ReviewPolicyConfig): ReviewBlock? = ReviewPolicy.check(snapshot(), config, clock())

    /** Returns null and persists the attempt when allowed; otherwise the reason. */
    @Synchronized
    fun tryAcquire(config: ReviewPolicyConfig): ReviewBlock? {
        check(config)?.let { return it }
        val now = clock()
        storage.attempts = (storage.attempts.filter { now - it < ReviewPolicy.YEAR_MS * 2 } + now)
        return null
    }

    val attemptCount: Int @Synchronized get() = storage.attempts.size

    companion object {
        const val SESSION_GAP_MS = 30L * 60 * 1000
        private const val MAX_COUNTER = 1_000_000
    }
}
