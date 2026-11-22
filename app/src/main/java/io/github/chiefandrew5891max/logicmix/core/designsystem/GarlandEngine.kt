package io.github.chiefandrew5891max.logicmix.core.designsystem

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class GarlandMode { SoftTwinkle, RunningLights, ReverseChase, BreathingGlow, RandomSparkle, DoubleWave, GoldenCascade }

/** Bulbs painted in `logo_logic_mix_player.png`, in normalized image coordinates (0..1); radius is a fraction of image height. */
object GarlandGeometry {
    data class Bulb(val x: Float, val y: Float)

    /** Measured on the PNG (1774x887): 8 bulbs along the lower arc, left to right. */
    val bulbs = listOf(
        Bulb(0.2241f, 0.8738f), Bulb(0.3081f, 0.9101f), Bulb(0.3867f, 0.9261f), Bulb(0.4610f, 0.9331f),
        Bulb(0.5353f, 0.9299f), Bulb(0.6105f, 0.9179f), Bulb(0.6823f, 0.8953f), Bulb(0.7429f, 0.8624f),
    )
    const val RADIUS = 0.0165f

    /** Maps normalized points into the rectangle (width x height) where the PNG is drawn. */
    fun toPixels(width: Float, height: Float): List<Pair<Float, Float>> = bulbs.map { it.x * width to it.y * height }
}

/** Picks the next mode at random: never the current one, and avoids the one before it when possible. */
class GarlandScheduler(private val random: Random) {
    private var last: GarlandMode? = null
    private var beforeLast: GarlandMode? = null

    fun nextMode(): GarlandMode {
        val all = GarlandMode.entries
        val pool = all.filter { it != last && it != beforeLast }.ifEmpty { all.filter { it != last } }
        val mode = pool[random.nextInt(pool.size)]
        beforeLast = last
        last = mode
        return mode
    }

    fun nextDurationSeconds(): Float = MIN_SECONDS + random.nextFloat() * (MAX_SECONDS - MIN_SECONDS)

    companion object {
        const val MIN_SECONDS = 4f
        const val MAX_SECONDS = 9f
    }
}

/**
 * Time-driven brightness model of the garland. No Android/Compose dependencies.
 * [levels] hold the extra glow of every bulb in 0..1; [REST] is the calm level used while paused or with animations off.
 */
class GarlandEngine(private val random: Random, private val count: Int = GarlandGeometry.bulbs.size) {
    val levels = FloatArray(count) { REST }
    val scheduler = GarlandScheduler(random)

    var mode: GarlandMode = scheduler.nextMode(); private set
    var live = 0f; private set
    private var previous: GarlandMode? = null
    private var fade = 1f
    private var modeTime = 0f
    private var previousTime = 0f
    private var modeLength = scheduler.nextDurationSeconds()
    private var speed = 1f
    private var direction = 1
    private var previousDirection = 1
    private var wasPlaying = false
    private var kickAge = -1f
    private var kickDirection = 1
    private val phases = FloatArray(count) { random.nextFloat() }
    private val rates = FloatArray(count) { 0.25f + random.nextFloat() * 0.35f }
    private val sparks = FloatArray(count)
    private val sparkShown = FloatArray(count)
    private var sparkCooldown = 0f

    fun kick(forward: Boolean) {
        kickAge = 0f
        kickDirection = if (forward) 1 else -1
    }

    /** Advances the model by [dt] seconds. With [animated] = false everything settles to the static soft glow. */
    fun advance(dt: Float, playing: Boolean, animated: Boolean) {
        if (!animated) {
            live = 0f
            kickAge = -1f
            levels.fill(REST)
            return
        }
        val step = dt.coerceIn(0f, 0.1f)
        if (playing && !wasPlaying) startMode(scheduler.nextMode())
        wasPlaying = playing
        live = (live + if (playing) step / WAKE_SECONDS else -step / SLEEP_SECONDS).coerceIn(0f, 1f)

        modeTime += step * speed
        previousTime += step * speed
        if (fade < 1f) fade = (fade + step / CROSSFADE_SECONDS).coerceAtMost(1f)
        if (playing && modeTime / speed >= modeLength) startMode(scheduler.nextMode())
        updateSparks(step)
        if (kickAge >= 0f) {
            kickAge += step
            if (kickAge > KICK_SECONDS) kickAge = -1f
        }

        for (i in 0 until count) {
            val now = modeLevel(mode, i, modeTime, direction)
            val blended = previous?.let { p -> modeLevel(p, i, previousTime, previousDirection) * (1f - fade) + now * fade } ?: now
            var v = REST + (blended - REST) * smooth(live)
            if (kickAge >= 0f) v = maxOf(v, kickLevel(i) * 0.9f)
            levels[i] = v.coerceIn(0f, 1f)
        }
    }

    private fun startMode(next: GarlandMode) {
        previous = mode
        previousTime = modeTime
        previousDirection = direction
        mode = next
        modeTime = 0f
        fade = 0f
        modeLength = scheduler.nextDurationSeconds()
        speed = 0.9f + random.nextFloat() * 0.2f
        direction = if (random.nextBoolean()) 1 else -1
        sparks.fill(0f)
    }

    private fun updateSparks(dt: Float) {
        val decay = exp(-dt * 3.2f)
        val rise = (dt * 8f).coerceAtMost(1f)
        for (i in sparks.indices) {
            sparks[i] *= decay
            sparkShown[i] += (sparks[i] - sparkShown[i]) * rise
        }
        if (mode != GarlandMode.RandomSparkle) return
        sparkCooldown -= dt
        if (sparkCooldown <= 0f) {
            val i = random.nextInt(count)
            sparks[i] = 1f
            if (i > 0) sparks[i - 1] = maxOf(sparks[i - 1], 0.35f)
            if (i < count - 1) sparks[i + 1] = maxOf(sparks[i + 1], 0.35f)
            sparkCooldown = 0.25f + random.nextFloat() * 0.45f
        }
    }

    private fun position(i: Int) = if (count > 1) i / (count - 1f) else 0.5f

    private fun modeLevel(m: GarlandMode, i: Int, t: Float, dir: Int): Float = when (m) {
        GarlandMode.SoftTwinkle ->
            0.5f + 0.3f * sin(2f * PI.toFloat() * (rates[i] * t + phases[i]))
        GarlandMode.RunningLights -> sweep(position(i), t, 1)
        GarlandMode.ReverseChase -> sweep(position(i), t, -1)
        GarlandMode.BreathingGlow -> {
            val e = 0.5f - 0.5f * cos(2f * PI.toFloat() * t / BREATH_SECONDS)
            0.2f + 0.7f * smooth(e)
        }
        GarlandMode.RandomSparkle -> 0.2f + 0.8f * sparkShown[i]
        GarlandMode.DoubleWave -> {
            val f = (t % DOUBLE_PERIOD) / DOUBLE_PERIOD
            val reach = -WAVE_WIDTH + minOf(f / 0.8f, 1f) * (0.5f + WAVE_WIDTH)
            val amp = 1f - smooth((f - 0.8f) / 0.2f)
            val p = position(i)
            val waves = 0.25f + 0.65f * amp * maxOf(bump(p - reach), bump(p - (1f - reach)))
            if (i == (count - 1) / 2 || i == count / 2) maxOf(waves, centerGlow(t)) else waves
        }
        GarlandMode.GoldenCascade -> {
            val group = minOf(i * CASCADE_GROUPS / count, CASCADE_GROUPS - 1)
            val g = if (dir > 0) group.toFloat() else (CASCADE_GROUPS - 1 - group).toFloat()
            val head = (t % (CASCADE_PERIOD * (CASCADE_GROUPS + 2.4f))) / CASCADE_PERIOD - 1.2f
            0.25f + 0.65f * smooth((1f - kotlin.math.abs(g - head) / 1.2f).coerceIn(0f, 1f))
        }
    }

    private fun sweep(p: Float, t: Float, dir: Int): Float {
        val period = (1f + 2f * WAVE_WIDTH) / SWEEP_SPEED
        val head = -WAVE_WIDTH + (t % period) * SWEEP_SPEED
        val pos = if (dir > 0) p else 1f - p
        return 0.2f + 0.7f * bump(pos - head)
    }

    private fun centerGlow(t: Float): Float {
        val f = (t % DOUBLE_PERIOD) / DOUBLE_PERIOD
        return if (f < 0.72f) 0f else if (f < 0.8f) (f - 0.72f) / 0.08f else 1f - (f - 0.8f) / 0.2f
    }

    private fun kickLevel(i: Int): Float {
        val head = (kickAge / KICK_SECONDS) * (1f + 2f * 0.15f) - 0.15f
        val p = if (kickDirection > 0) position(i) else 1f - position(i)
        return bump((p - head) * (WAVE_WIDTH / 0.15f))
    }

    private fun bump(d: Float) = smooth((1f - kotlin.math.abs(d) / WAVE_WIDTH).coerceIn(0f, 1f))

    companion object {
        const val REST = 0.5f
        const val WAVE_WIDTH = 0.2f
        const val SWEEP_SPEED = 0.33f
        const val BREATH_SECONDS = 4.5f
        const val DOUBLE_PERIOD = 3.2f
        const val CASCADE_GROUPS = 3
        const val CASCADE_PERIOD = 0.9f
        const val CROSSFADE_SECONDS = 1.2f
        const val WAKE_SECONDS = 1.5f
        const val SLEEP_SECONDS = 1.2f
        const val KICK_SECONDS = 0.45f

        fun smooth(x: Float): Float {
            val c = x.coerceIn(0f, 1f)
            return c * c * (3f - 2f * c)
        }
    }
}
