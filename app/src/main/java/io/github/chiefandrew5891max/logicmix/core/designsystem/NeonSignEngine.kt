package io.github.chiefandrew5891max.logicmix.core.designsystem

import kotlin.random.Random

enum class NeonEffect { SoftFlicker, Surge, Malfunction }

/** Where the letters of `logo_logic_mix_player.png` are, in normalized image coordinates, and how to find their pixels. */
object NeonSignGeometry {
    const val LOGIC_LEFT = 0.08f
    const val LOGIC_RIGHT = 0.58f
    const val MIX_LEFT = 0.55f
    const val MIX_RIGHT = 0.955f
    const val TOP = 0.19f
    const val BOTTOM = 0.68f
    /** Below this line of the MIX area only the underline swoosh lives, not letters. */
    const val MIX_BOTTOM = 0.545f
    /** Letters I and X of MIX start here; M is to the left. Used by the malfunction effect. */
    const val TUBE_LEFT = 0.72f

    /** 1 = pixel of a LOGIC letter (cool blue or white face). `x`, `y` are normalized. */
    fun isLogic(x: Float, y: Float, r: Int, g: Int, b: Int): Boolean =
        x in LOGIC_LEFT..LOGIC_RIGHT && y in 0.3f..BOTTOM && b > 190 && g > 140 && r < 240 && b - r > 10

    /** Warm gold/orange pixel of a MIX letter. */
    fun isMix(x: Float, y: Float, r: Int, g: Int, b: Int): Boolean =
        x in MIX_LEFT..MIX_RIGHT && y in TOP..MIX_BOTTOM && r > 200 && g > 120 && b < 130 && r - b > 110

    /** Maps a normalized point into the rectangle (width x height) where the PNG is drawn. */
    fun toPixels(x: Float, y: Float, width: Float, height: Float) = x * width to y * height
}

/** Letter masks of a downscaled logo as 8-bit coverage: [core] follows the letters exactly, [halo] is the same blurred. */
class NeonMasks(val width: Int, val height: Int, val logicCore: ByteArray, val logicHalo: ByteArray, val mixCore: ByteArray, val mixHalo: ByteArray) {
    companion object {
        /** [argb] is the logo downscaled to width x height. Pure and cheap enough to run once on a background thread. */
        fun build(argb: IntArray, width: Int, height: Int, haloRadius: Int = 2): NeonMasks {
            val logic = ByteArray(width * height)
            val mix = ByteArray(width * height)
            for (py in 0 until height) for (px in 0 until width) {
                val c = argb[py * width + px]
                if ((c ushr 24) < 200) continue
                val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
                val x = (px + 0.5f) / width; val y = (py + 0.5f) / height
                if (NeonSignGeometry.isLogic(x, y, r, g, b)) logic[py * width + px] = 127
                else if (NeonSignGeometry.isMix(x, y, r, g, b)) mix[py * width + px] = 127
            }
            return NeonMasks(width, height, logic, blur(logic, width, height, haloRadius), mix, blur(mix, width, height, haloRadius))
        }

        private fun blur(src: ByteArray, w: Int, h: Int, radius: Int): ByteArray {
            val out = ByteArray(w * h)
            val side = 2 * radius + 1
            for (y in 0 until h) for (x in 0 until w) {
                var sum = 0
                for (dy in -radius..radius) for (dx in -radius..radius) {
                    val sx = x + dx; val sy = y + dy
                    if (sx in 0 until w && sy in 0 until h) sum += src[sy * w + sx].toInt()
                }
                out[y * w + x] = (sum / (side * side) * 2).coerceAtMost(127).toByte()
            }
            return out
        }
    }
}

/**
 * Time-driven model of the neon sign. No Android/Compose dependencies. Values are the EXTRA glow drawn over the PNG:
 * [BASE] is the quiet normal state; the PNG itself is never darkened. Only one episode runs at a time.
 * [logic] / [mix] apply to the words, [tube] to the letters I and X of MIX (it equals [mix] except in a malfunction).
 */
class NeonSignEngine(private val random: Random, private val intro: Boolean = true) {
    var logic = BASE; private set
    var mix = BASE; private set
    var tube = BASE; private set
    var effect: NeonEffect? = null; private set
    var introDone = !intro; private set

    private var introTime = 0f
    private val mixDelay = INTRO_MIX_DELAY_MIN + random.nextFloat() * (INTRO_MIX_DELAY_MAX - INTRO_MIX_DELAY_MIN)
    private var episodeTime = 0f
    private var episodeLength = 0f
    private var lastEffect: NeonEffect? = null
    private var idle = firstIdle()
    private var sinceSurge = 0f
    private var sinceMalfunction = 0f
    private var surgeGap = gap(SURGE_MIN, SURGE_MAX)
    private var malfunctionGap = gap(MALFUNCTION_MIN, MALFUNCTION_MAX)
    private var pulseAge = -1f

    /** Short boost of MIX, e.g. on a track change. Ignored while another pulse is running. */
    fun pulse() { if (pulseAge < 0f) pulseAge = 0f }

    /** With [animated] = false the sign settles to the static state at once and no episode ever starts. */
    fun advance(dt: Float, animated: Boolean) {
        if (!animated) {
            logic = BASE; mix = BASE; tube = BASE; effect = null; pulseAge = -1f
            return
        }
        val step = dt.coerceIn(0f, 0.1f)
        var l = 0f
        var m = 0f
        var t = 0f
        if (!introDone) {
            introTime += step
            l = bell(introTime / INTRO_PULSE) * INTRO_GAIN
            m = bell((introTime - mixDelay) / INTRO_PULSE) * INTRO_GAIN
            if (introTime >= mixDelay + INTRO_PULSE) introDone = true
        } else {
            sinceSurge += step; sinceMalfunction += step
            if (effect == null) {
                idle -= step
                if (idle <= 0f) startEpisode(pick())
            }
            effect?.let { e ->
                episodeTime += step
                val f = (episodeTime / episodeLength).coerceAtMost(1f)
                when (e) {
                    NeonEffect.SoftFlicker -> m = curve(SOFT, f)
                    NeonEffect.Surge -> m = curve(SURGE, f)
                    NeonEffect.Malfunction -> t = curve(MALFUNCTION, f)
                }
                if (episodeTime >= episodeLength) { effect = null; idle = gap(SOFT_MIN, SOFT_MAX) }
            }
        }
        if (pulseAge >= 0f) {
            pulseAge += step
            m = maxOf(m, bell(pulseAge / PULSE_SECONDS) * PULSE_GAIN)
            if (pulseAge >= PULSE_SECONDS) pulseAge = -1f
        }
        logic = (BASE + l).coerceIn(0f, 1f)
        mix = (BASE + m).coerceIn(0f, 1f)
        tube = (BASE + m + t).coerceIn(0f, 1f)
    }

    private fun pick(): NeonEffect {
        val roll = random.nextFloat()
        val malfunctionOk = sinceMalfunction >= malfunctionGap
        val surgeOk = sinceSurge >= surgeGap
        val wanted = when {
            malfunctionOk && roll < 0.3f -> NeonEffect.Malfunction
            surgeOk && roll < 0.7f -> NeonEffect.Surge
            else -> NeonEffect.SoftFlicker
        }
        // Never the same effect twice in a row; a soft flicker is always a legal alternative.
        return if (wanted == lastEffect) NeonEffect.SoftFlicker.takeIf { it != lastEffect } ?: NeonEffect.Surge.takeIf { surgeOk } ?: wanted else wanted
    }

    private fun startEpisode(e: NeonEffect) {
        effect = e
        lastEffect = e
        episodeTime = 0f
        episodeLength = when (e) {
            NeonEffect.SoftFlicker -> 0.3f + random.nextFloat() * 0.4f
            NeonEffect.Surge -> 0.2f + random.nextFloat() * 0.2f
            NeonEffect.Malfunction -> 0.4f + random.nextFloat() * 0.5f
        }
        when (e) {
            NeonEffect.Surge -> { sinceSurge = 0f; surgeGap = gap(SURGE_MIN, SURGE_MAX) }
            NeonEffect.Malfunction -> { sinceMalfunction = 0f; malfunctionGap = gap(MALFUNCTION_MIN, MALFUNCTION_MAX) }
            NeonEffect.SoftFlicker -> Unit
        }
    }

    private fun firstIdle() = INTRO_QUIET + random.nextFloat() * (SOFT_MAX - SOFT_MIN)
    private fun gap(min: Float, max: Float) = min + random.nextFloat() * (max - min)

    companion object {
        const val BASE = 0.2f
        const val INTRO_PULSE = 0.5f
        const val INTRO_GAIN = 0.55f
        const val INTRO_MIX_DELAY_MIN = 0.15f
        const val INTRO_MIX_DELAY_MAX = 0.25f
        const val INTRO_QUIET = 4f
        const val SOFT_MIN = 8f
        const val SOFT_MAX = 18f
        const val SURGE_MIN = 15f
        const val SURGE_MAX = 30f
        const val MALFUNCTION_MIN = 40f
        const val MALFUNCTION_MAX = 90f
        const val PULSE_SECONDS = 0.3f
        const val PULSE_GAIN = 0.4f

        /** (fraction of the episode, extra glow relative to BASE); eased between keys. */
        val SOFT = listOf(0f to 0f, 0.3f to -0.1f, 0.55f to 0.22f, 1f to 0f)
        val SURGE = listOf(0f to 0f, 0.5f to 0.45f, 1f to 0f)
        val MALFUNCTION = listOf(
            0f to 0f, 0.12f to -0.17f, 0.24f to 0f, 0.36f to 0.02f, 0.46f to -0.17f, 0.58f to 0.05f,
            0.7f to 0.35f, 0.88f to 0.1f, 1f to 0f,
        )

        fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

        /** 0 outside 0..1, rises to 1 at 0.4 and eases back to 0. */
        fun bell(x: Float): Float = when {
            x <= 0f || x >= 1f -> 0f
            x < 0.4f -> smooth(x / 0.4f)
            else -> smooth((1f - x) / 0.6f)
        }

        fun curve(keys: List<Pair<Float, Float>>, f: Float): Float {
            if (f <= keys.first().first) return keys.first().second
            for (i in 1 until keys.size) {
                val (f1, v1) = keys[i]
                if (f <= f1) {
                    val (f0, v0) = keys[i - 1]
                    return v0 + (v1 - v0) * smooth((f - f0) / (f1 - f0))
                }
            }
            return keys.last().second
        }
    }
}
