package com.akynex.logicmix.core.audio

import kotlin.math.pow

enum class SfxPriority { UI, GAME, MAJOR }

/** All 38 effects; [file] is the exact name in assets/sfx. [minGapMs] drops accidental double triggers. */
enum class Sfx(val file: String, val priority: SfxPriority = SfxPriority.GAME, val minGapMs: Long = 30) {
    BUTTON_TAP("sfx_button_tap.wav", SfxPriority.UI),
    MENU_OPEN("sfx_menu_open.wav", SfxPriority.UI),
    MENU_CLOSE("sfx_menu_close.wav", SfxPriority.UI),
    SCREEN_TRANSITION("sfx_screen_transition.wav", SfxPriority.UI),
    TOGGLE("sfx_toggle.wav", SfxPriority.UI),
    CARD_SELECT("sfx_card_select.wav", SfxPriority.UI),
    CORRECT("sfx_correct.wav"),
    WRONG("sfx_wrong.wav"),
    MATCHSTICK_PICKUP("sfx_matchstick_pickup.wav"),
    MATCHSTICK_MOVE("sfx_matchstick_move.wav", minGapMs = 120),
    MATCHSTICK_PLACE("sfx_matchstick_place.wav"),
    LETTER_SELECT("sfx_letter_select.wav"),
    WORD_TRACE_BASE("sfx_word_trace_base.wav", minGapMs = 40),
    WORD_FOUND("sfx_word_found.wav"),
    WORD_INVALID("sfx_word_invalid.wav"),
    PUZZLE_MOVE("sfx_puzzle_move.wav", minGapMs = 120),
    PUZZLE_PLACE("sfx_puzzle_place.wav"),
    XP_GAIN("sfx_xp_gain.wav"),
    BONUS_POINTS("sfx_bonus_points.wav"),
    COIN_REWARD("sfx_coin_reward.wav"),
    COMBO("sfx_combo.wav"),
    STREAK("sfx_streak.wav"),
    ACHIEVEMENT("sfx_achievement.wav", SfxPriority.MAJOR),
    LEVEL_COMPLETE("sfx_level_complete.wav", SfxPriority.MAJOR),
    DAILY_COMPLETE("sfx_daily_complete.wav", SfxPriority.MAJOR),
    LEVEL_UP("sfx_level_up.wav", SfxPriority.MAJOR),
    HINT("sfx_hint.wav"),
    REWARD("sfx_reward.wav", SfxPriority.MAJOR),
    TIMER_WARNING("sfx_timer_warning.wav", minGapMs = 1000),
    GAME_OVER("sfx_game_over.wav", SfxPriority.MAJOR),
    ORB_SELECT("sfx_orb_select.wav"),
    ORB_MOVE("sfx_orb_move.wav"),
    ORB_SPAWN("sfx_orb_spawn.wav", minGapMs = 120),
    ORB_POP("sfx_orb_pop.wav"),
    LETTER_INPUT("sfx_letter_input.wav", minGapMs = 15),
    LETTER_DELETE("sfx_letter_delete.wav", minGapMs = 15),
    NUMBER_INPUT("sfx_number_input.wav", minGapMs = 15),
    NUMBER_DELETE("sfx_number_delete.wav", minGapMs = 15),
}

object SfxPitch {
    /** SoundPool accepts playback rates 0.5..2.0. */
    const val MIN_RATE = 0.5f
    const val MAX_RATE = 2.0f

    fun semitonesToRate(semitones: Int): Float = 2.0.pow(semitones / 12.0).toFloat().coerceIn(MIN_RATE, MAX_RATE)

    // Major scale up to the octave; further letters stay on the top note so the sound never gets shrill.
    private val traceSteps = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)

    /** [index] is the zero-based position of the letter inside one gesture; a new gesture starts at 0. */
    fun traceRate(index: Int): Float = semitonesToRate(traceSteps[index.coerceIn(0, traceSteps.lastIndex)])

    /** x2 is the base pitch; x7 and above share the top step. */
    fun comboSemitones(multiplier: Int): Int = when {
        multiplier <= 2 -> 0
        multiplier == 3 -> 2
        multiplier == 4 -> 4
        multiplier == 5 -> 5
        multiplier == 6 -> 7
        else -> 9
    }

    fun comboRate(multiplier: Int): Float = semitonesToRate(comboSemitones(multiplier))
}

/** Pure anti-chaos policy: double-trigger guard, and quiet UI clicks never talk over a big reward or each other. */
class SfxGate(private val majorHoldMs: Long = 300, private val uiHoldMs: Long = 120) {
    private val last = HashMap<Sfx, Long>()
    private var lastMajor = Long.MIN_VALUE / 2
    private var lastUi = Long.MIN_VALUE / 2

    fun allow(sfx: Sfx, now: Long): Boolean {
        if (now - (last[sfx] ?: (Long.MIN_VALUE / 2)) < sfx.minGapMs) return false
        when (sfx.priority) {
            SfxPriority.UI -> {
                if (now - lastMajor < majorHoldMs || now - lastUi < uiHoldMs) return false
                lastUi = now
            }
            SfxPriority.MAJOR -> lastMajor = now
            SfxPriority.GAME -> Unit
        }
        last[sfx] = now
        return true
    }
}
