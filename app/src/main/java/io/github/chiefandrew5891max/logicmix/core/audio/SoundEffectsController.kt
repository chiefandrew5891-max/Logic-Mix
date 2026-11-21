package io.github.chiefandrew5891max.logicmix.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chiefandrew5891max.logicmix.core.data.AudioSettings
import io.github.chiefandrew5891max.logicmix.core.data.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One app-wide SoundPool with every WAV from assets/sfx decoded once. SoundPool takes no audio focus,
 * so effects mix over the music (a separate ExoPlayer) without interrupting it. Missing or broken files
 * are logged and skipped; playing never throws.
 */
@Singleton
class SoundEffectsController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : UiFeedback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = SfxGate()
    private val soundIds = java.util.concurrent.ConcurrentHashMap<Sfx, Int>()
    private val ready = HashSet<Int>()
    @Volatile private var pool: SoundPool? = null
    private var started = false

    @Volatile private var audio = AudioSettings()

    /** Idempotent; called from Application.onCreate. Loading is asynchronous. */
    fun start() {
        if (started) return
        started = true
        scope.launch { settings.audio.collect { audio = it } }
        scope.launch { load() }
    }

    private fun load() {
        val sp = try {
            SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
        } catch (e: RuntimeException) {
            Log.w(TAG, "SoundPool unavailable, effects disabled", e)
            return
        }
        sp.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) synchronized(ready) { ready.add(id) } else Log.w(TAG, "Failed to decode sound id=$id")
        }
        val missing = mutableListOf<String>()
        for (sfx in Sfx.entries) {
            try {
                context.assets.openFd("$DIR/${sfx.file}").use { soundIds[sfx] = sp.load(it, 1) }
            } catch (e: IOException) {
                missing += sfx.file
            }
        }
        if (missing.isNotEmpty()) Log.w(TAG, "Missing sound effects in assets/$DIR: $missing")
        pool = sp
    }

    override fun tap() = play(Sfx.BUTTON_TAP)
    override fun back() = play(Sfx.MENU_CLOSE)

    /** [force] plays even when effects are switched off (the confirmation click of the switch itself). */
    fun play(sfx: Sfx, rate: Float = 1f, force: Boolean = false) {
        if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (!gate.allow(sfx, SystemClock.elapsedRealtime())) return
        vibrate(sfx)
        val sp = pool ?: return
        val id = soundIds[sfx] ?: return
        if (!(audio.effectsEnabled || force) || audio.effectsVolume <= 0f) return
        if (!synchronized(ready) { id in ready }) return
        try {
            val v = volumeGain(audio.effectsVolume)
            sp.play(id, v, v, sfx.priority.ordinal, 0, rate.coerceIn(SfxPitch.MIN_RATE, SfxPitch.MAX_RATE))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Effect playback failed: ${sfx.file}", e)
        }
    }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Haptics follow only the vibration switch, independent of sound settings. */
    private fun vibrate(sfx: Sfx) {
        if (!audio.vibrationEnabled) return
        val ms = when (sfx) {
            Sfx.WRONG, Sfx.WORD_INVALID -> 40L
            else -> when (sfx.priority) {
                SfxPriority.UI -> 15L
                SfxPriority.MAJOR -> 60L
                SfxPriority.GAME -> return
            }
        }
        try {
            vibrator?.takeIf { it.hasVibrator() }?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    /** Letter [index] (zero-based) of the current finger gesture; each next letter is higher. */
    fun playTrace(index: Int) = play(Sfx.WORD_TRACE_BASE, SfxPitch.traceRate(index))

    /** Combo multiplier x2, x3...; the pitch resets by itself when a new combo starts at x2. */
    fun playCombo(multiplier: Int) = play(Sfx.COMBO, SfxPitch.comboRate(multiplier))

    fun release() {
        pool?.release()
        pool = null
        soundIds.clear()
        synchronized(ready) { ready.clear() }
    }

    private companion object {
        const val TAG = "SoundEffects"
        const val DIR = "sfx"
        const val MAX_STREAMS = 8
    }
}
