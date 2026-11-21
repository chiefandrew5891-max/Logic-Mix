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

    /** Hook for a future "next track" button. */
    fun skipToNext() { player?.seekToNextMediaItem() }

    fun release() {
        player?.release()
        player = null
    }

    private fun apply(audio: AudioSettings, inForeground: Boolean) {
        val tracks = MusicPlaylist.resolve { name ->
            context.resources.getIdentifier(name, "raw", context.packageName)
        }
        if (!shouldPlayMusic(audio.musicEnabled, inForeground, tracks.size)) {
            if (tracks.isEmpty() && audio.musicEnabled && inForeground) {
                Log.i(TAG, "No music_NN files in res/raw; see AUDIO_ASSETS.md")
            }
            player?.pause()
            return
        }
        val exo = player ?: createPlayer(tracks).also { player = it }
        exo.volume = musicPlayerVolume(audio.musicVolume)
        exo.play()
    }

    private fun createPlayer(tracks: List<Int>): ExoPlayer =
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
            setMediaItems(
                tracks.map { MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/$it")) },
            )
            addListener(object : Player.Listener {
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
    }
}
