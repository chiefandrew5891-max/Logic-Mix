package io.github.chiefandrew5891max.logicmix.core.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chiefandrew5891max.logicmix.core.data.AudioSettings
import io.github.chiefandrew5891max.logicmix.core.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * Application-wide background music. One ExoPlayer for the whole process, so navigation never restarts it.
 * Reacts to the existing music switch and volume, to the app going to the background, and to audio focus
 * (calls and other apps), which ExoPlayer handles itself.
 */
@Singleton
class MusicController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val foreground = MutableStateFlow(false)
    private var player: ExoPlayer? = null
    private var started = false
    private val prefs = context.getSharedPreferences("music_state", Context.MODE_PRIVATE)
    private val entries: List<MusicEntry> by lazy {
        MusicPlaylist.resolveEntries { name -> context.resources.getIdentifier(name, "raw", context.packageName) }
    }

    /** Whether any music_NN file is bundled; the player buttons are disabled otherwise. */
    val hasTracks: Boolean get() = entries.isNotEmpty()

    /** Idempotent; called once from Application.onCreate. */
    fun start() {
        if (started) return
        started = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { foreground.value = true }
            override fun onStop(owner: LifecycleOwner) { foreground.value = false }
        })
        scope.launch {
            combine(settings.audio, foreground) { audio, fg -> audio to fg }
                .collect { (audio, fg) -> apply(audio, fg) }
        }
    }

    /** Play/Pause is the existing music switch, so the settings screen and the player never disagree. */
    fun togglePlayback() {
        scope.launch { settings.setMusicEnabled(!settings.audio.first().musicEnabled) }
    }

    fun skipToNext() = skip(forward = true)

    fun skipToPrevious() = skip(forward = false)

    // One player and an explicit seek: the old track stops as the new one starts, so nothing overlaps.
    private fun skip(forward: Boolean) {
        if (!hasTracks) return
        scope.launch {
            if (!settings.audio.first().musicEnabled) settings.setMusicEnabled(true)
            val exo = player ?: createPlayer().also { player = it }
            val i = exo.currentMediaItemIndex
            val count = exo.mediaItemCount
            exo.seekTo(if (forward) MusicNavigation.next(i, count) else MusicNavigation.previous(i, count), 0L)
            exo.play()
        }
    }

    fun release() {
        player?.release()
        player = null
    }

    private fun apply(audio: AudioSettings, inForeground: Boolean) {
        if (!shouldPlayMusic(audio.musicEnabled, inForeground, entries.size)) {
            if (entries.isEmpty() && audio.musicEnabled && inForeground) {
                Log.i(TAG, "No music_NN files in res/raw; see AUDIO_ASSETS.md")
            }
            player?.pause()
            return
        }
        val exo = player ?: createPlayer().also { player = it }
        exo.volume = musicPlayerVolume(audio.musicVolume)
        exo.play()
    }

    private fun createPlayer(): ExoPlayer =
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_GAME)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
            repeatMode = Player.REPEAT_MODE_ALL
            shuffleModeEnabled = false
            val start = MusicNavigation.startIndex(entries.map { it.name }, prefs.getString(KEY_LAST, null), Random.Default)
            setMediaItems(
                entries.map { MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/${it.id}")) },
                start,
                0L,
            )
            prefs.edit().putString(KEY_LAST, entries[start].name).apply()
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    entries.getOrNull(currentMediaItemIndex)?.let { prefs.edit().putString(KEY_LAST, it.name).apply() }
                }

                override fun onPlayerError(error: PlaybackException) {
                    // A broken file is skipped; if every file is broken playback simply stays silent.
                    Log.w(TAG, "Music playback error, skipping track", error)
                    if (hasNextMediaItem()) {
                        seekToNextMediaItem()
                        prepare()
                    }
                }
            })
            prepare()
        }

    private companion object {
        const val TAG = "MusicController"
        const val KEY_LAST = "last_track"
    }
}
