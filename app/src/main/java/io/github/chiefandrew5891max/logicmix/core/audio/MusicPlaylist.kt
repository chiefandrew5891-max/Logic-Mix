package io.github.chiefandrew5891max.logicmix.core.audio

/**
 * Background-music tracks are optional raw resources named music_01, music_02, ... (see AUDIO_ASSETS.md).
 * Tracks are found by name at runtime, so the app compiles and runs with none of them present.
 */
object MusicPlaylist {
    const val PREFIX = "music_"
    private const val MAX_NUMBERED = 50

    /** Extra names can be appended here to register tracks that do not follow the music_NN pattern. */
    val extraTrackNames: List<String> = emptyList()

    /** Candidate names in playback order: music_01..music_50, then [extraTrackNames]. */
    val candidates: List<String> =
        (1..MAX_NUMBERED).map { PREFIX + it.toString().padStart(2, '0') } + extraTrackNames

    /** Keeps only the tracks whose resource exists ([lookup] returns 0 when missing), in order. */
    fun resolve(names: List<String> = candidates, lookup: (String) -> Int): List<Int> =
        names.map(lookup).filter { it != 0 }.distinct()
}

/** Music plays only when the user enabled it, the app is visible and there is something to play. */
fun shouldPlayMusic(enabled: Boolean, inForeground: Boolean, trackCount: Int): Boolean =
    enabled && inForeground && trackCount > 0

/** Music and effects have independent volume paths; this is the single place the music one is mapped. */
fun musicPlayerVolume(settingsVolume: Float): Float = settingsVolume.coerceIn(0f, 1f)
