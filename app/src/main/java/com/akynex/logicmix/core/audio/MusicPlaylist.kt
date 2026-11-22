package com.akynex.logicmix.core.audio

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
        resolveEntries(names, lookup).map { it.id }

    fun resolveEntries(names: List<String> = candidates, lookup: (String) -> Int): List<MusicEntry> =
        names.map { MusicEntry(it, lookup(it)) }.filter { it.id != 0 }.distinctBy { it.id }
}

/** A track found in res/raw: [name] identifies it across launches, [id] is the resource. */
data class MusicEntry(val name: String, val id: Int)

/** Pure playlist navigation, kept free of Android so it is unit-testable. */
object MusicNavigation {
    fun next(index: Int, count: Int): Int = if (count <= 0) 0 else (index + 1) % count

    fun previous(index: Int, count: Int): Int = if (count <= 0) 0 else (index - 1 + count) % count

    /** Random first track; with several tracks the one played last time is skipped. */
    fun startIndex(names: List<String>, lastName: String?, random: kotlin.random.Random): Int {
        if (names.isEmpty()) return 0
        val last = names.indexOf(lastName)
        if (names.size == 1 || last < 0) return random.nextInt(names.size)
        val pick = random.nextInt(names.size - 1)
        return if (pick >= last) pick + 1 else pick
    }
}

/** Music plays only when the user enabled it, the app is visible and there is something to play. */
fun shouldPlayMusic(enabled: Boolean, inForeground: Boolean, trackCount: Int): Boolean =
    enabled && inForeground && trackCount > 0

/** Music and effects have independent volume paths; this is the single place the music one is mapped. */
fun musicPlayerVolume(settingsVolume: Float): Float = volumeGain(settingsVolume)
