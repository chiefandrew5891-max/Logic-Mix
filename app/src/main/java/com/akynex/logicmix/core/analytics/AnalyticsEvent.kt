package com.akynex.logicmix.core.analytics

import com.akynex.logicmix.domain.content.GameCategory

/** Стабильные ключи категорий для отчётов (по существующему domain.content.GameCategory). */
val GameCategory.analyticsKey: String
    get() = when (this) {
        GameCategory.NUMBER_SEQUENCE -> "number_logic"
        GameCategory.VISUAL_LOGIC -> "visual_logic"
        GameCategory.MINI_CROSSWORD -> "mini_crossword"
        GameCategory.WORD_SEARCH -> "word_search"
        GameCategory.WORD_LOGIC -> "word_logic"
        GameCategory.SUDOKU -> "sudoku"
        GameCategory.MATCHSTICK -> "matchstick"
        GameCategory.ORB_LINES -> "orb_lines"
        GameCategory.TWO_DOTS -> "two_dots"
    }

enum class PuzzleResult(val key: String) { SOLVED("solved"), FAILED("failed"), SKIPPED("skipped"), ABANDONED("abandoned") }

/** Выход из задания. Уход в фон и убийство процесса сюда не входят: это не надёжный отказ. */
enum class ExitReason(val key: String) { EXPLICIT("explicit_exit"), SWITCHED("switched_task") }

object Ev {
    const val CATEGORY_OPEN = "category_open"
    const val GAME_START = "game_start"
    const val PUZZLE_START = "puzzle_start"
    const val PUZZLE_COMPLETE = "puzzle_complete"
    const val PUZZLE_FAIL = "puzzle_fail"
    const val PUZZLE_SKIP = "puzzle_skip"
    const val PUZZLE_ABANDON = "puzzle_abandon"
    const val HINT_REQUEST = "hint_request"
    const val HINT_USED = "hint_used"
    const val DIFFICULTY_SELECT = "difficulty_select"
    const val GAME_MODE_SELECT = "game_mode_select"
    const val LEVEL_RESTART = "level_restart"
    const val XP_EARNED = "xp_earned"
    const val PLAYER_LEVEL_UP = "player_level_up"
    const val SESSION_END = "session_end"

    val game = setOf(
        CATEGORY_OPEN, GAME_START, PUZZLE_START, PUZZLE_COMPLETE, PUZZLE_FAIL, PUZZLE_SKIP, PUZZLE_ABANDON,
        HINT_REQUEST, HINT_USED, DIFFICULTY_SELECT, GAME_MODE_SELECT, LEVEL_RESTART, XP_EARNED, SESSION_END,
    )
}

object P {
    const val GAME_CATEGORY = "game_category"
    const val PUZZLE_FAMILY = "puzzle_family"
    const val GAME_MODE = "game_mode"
    const val DIFFICULTY = "difficulty"
    const val LEVEL_INDEX = "level_index"
    const val DURATION_MS = "duration_ms"
    const val ATTEMPT_COUNT = "attempt_count"
    const val HINT_COUNT = "hint_count"
    const val RESULT = "result"
    const val CONTENT_LANGUAGE = "content_language"
    const val GENERATOR_VERSION = "generator_version"
    const val CONTENT_PACK_VERSION = "content_pack_version"
    const val IS_RESUME = "is_resume"
    const val EXIT_REASON = "exit_reason"
    const val XP = "xp"
    const val XP_SOURCE = "xp_source"
    const val PLAYER_LEVEL = "player_level"
    const val TOTAL_XP = "total_xp"
    const val PUZZLES_PLAYED = "puzzles_played"
}

/**
 * Контекст игры. puzzle_id намеренно НЕ отправляется в Firebase: уникальное значение на каждую головоломку даёт
 * неограниченную кардинальность. Для анализа достаточно puzzle_family + level_index (ограниченный набор).
 */
data class GameContext(
    val category: GameCategory,
    val family: String? = null,
    val mode: String? = null,
    val difficulty: String? = null,
    val levelIndex: Int? = null,
    val contentLanguage: String? = null,
    val generatorVersion: String = "1",
    val contentPackVersion: Int = 0,   // 0 = встроенные данные
) {
    fun toParams(): Map<String, Any> = buildMap {
        put(P.GAME_CATEGORY, category.analyticsKey)
        family?.let { put(P.PUZZLE_FAMILY, it) }
        mode?.let { put(P.GAME_MODE, it) }
        difficulty?.let { put(P.DIFFICULTY, it) }
        levelIndex?.let { put(P.LEVEL_INDEX, it.toLong()) }
        contentLanguage?.let { put(P.CONTENT_LANGUAGE, it) }
        put(P.GENERATOR_VERSION, generatorVersion)
        put(P.CONTENT_PACK_VERSION, contentPackVersion.toLong())
    }
}

class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap(), val dedupeKey: String? = null)

/** Низкоуровневый получатель (Firebase или тест). */
interface AnalyticsSink {
    fun send(name: String, params: Map<String, Any>)
    fun setCollectionEnabled(enabled: Boolean)
}

interface AnalyticsTracker {
    fun log(event: AnalyticsEvent)
}

object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun log(event: AnalyticsEvent) = Unit
}

/** Ограничения Firebase Analytics: имя ≤40, ≤25 параметров, ключ ≤40, строка ≤100, нельзя префиксы firebase_/google_/ga_. */
object EventValidator {
    private val name = Regex("^[A-Za-z][A-Za-z0-9_]{0,39}$")
    private val reserved = listOf("firebase_", "google_", "ga_")
    const val MAX_PARAMS = 25
    const val MAX_VALUE = 100

    fun validate(e: AnalyticsEvent): String? {
        if (!name.matches(e.name) || reserved.any { e.name.startsWith(it) }) return "bad event name '${e.name}'"
        if (e.params.size > MAX_PARAMS) return "too many params"
        for ((k, v) in e.params) {
            if (!name.matches(k) || reserved.any { k.startsWith(it) }) return "bad param key '$k'"
            when (v) {
                is String -> if (v.length > MAX_VALUE) return "param '$k' too long"
                is Long, is Double, is Boolean -> Unit
                else -> return "param '$k' has unsupported type ${v::class.simpleName}"
            }
        }
        return null
    }
}

/**
 * Единая точка отправки: проверка схемы, выключатель/согласие, защита от исключений SDK, дедупликация.
 * Аналитика никогда не бросает исключений в игровой код.
 */
class GuardedAnalyticsTracker(
    private val sink: AnalyticsSink,
    private val allowed: () -> Boolean,
    private val onProblem: (String, Throwable?) -> Unit = { _, _ -> },
    private val dedupeCapacity: Int = 512,
) : AnalyticsTracker {
    private val seen = object : LinkedHashSet<String>() {}
    private val lock = Any()

    override fun log(event: AnalyticsEvent) {
        if (!allowed()) return
        EventValidator.validate(event)?.let { onProblem(it, null); return }
        event.dedupeKey?.let { key ->
            synchronized(lock) {
                if (!seen.add(key)) return
                if (seen.size > dedupeCapacity) seen.remove(seen.first())
            }
        }
        try { sink.send(event.name, event.params) } catch (t: Throwable) { onProblem("sink failed: ${event.name}", t) }
    }
}
