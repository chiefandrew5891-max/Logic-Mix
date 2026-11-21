package io.github.chiefandrew5891max.logicmix.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlaylistTest {
    @Test fun noFilesMeansEmptyPlaylist() {
        assertTrue(MusicPlaylist.resolve { 0 }.isEmpty())
    }

    @Test fun existingTracksKeepOrderAndMissingOnesAreSkipped() {
        val ids = mapOf("music_01" to 11, "music_03" to 13, "music_02" to 12)
        assertEquals(listOf(11, 12, 13), MusicPlaylist.resolve { ids[it] ?: 0 })
    }

    @Test fun numberedCandidatesStartAtMusic01() {
        assertEquals("music_01", MusicPlaylist.candidates.first())
        assertEquals("music_05", MusicPlaylist.candidates[4])
    }

    @Test fun playsOnlyWhenEnabledVisibleAndHasTracks() {
        assertTrue(shouldPlayMusic(true, true, 1))
        assertFalse(shouldPlayMusic(false, true, 1))
        assertFalse(shouldPlayMusic(true, false, 1))
        assertFalse(shouldPlayMusic(true, true, 0))
    }

    @Test fun volumeIsClampedToPlayerRange() {
        assertEquals(0.64f, musicPlayerVolume(0.8f), 0.0001f)
        assertEquals(1f, musicPlayerVolume(2f), 0f)
        assertEquals(0.25f, musicPlayerVolume(0.5f), 0.0001f)
        assertTrue(musicPlayerVolume(0.01f) < 0.001f)
        assertEquals(0f, musicPlayerVolume(-1f), 0f)
    }

    @Test fun navigationWrapsAround() {
        assertEquals(0, MusicNavigation.next(2, 3))
        assertEquals(2, MusicNavigation.previous(0, 3))
        assertEquals(1, MusicNavigation.next(0, 3))
        assertEquals(0, MusicNavigation.previous(1, 3))
    }

    @Test fun startTrackIsRandomButNeverTheLastPlayedOne() {
        val names = listOf("music_01", "music_02", "music_03")
        val random = kotlin.random.Random(7)
        val starts = (1..200).map { MusicNavigation.startIndex(names, "music_02", random) }
        assertTrue(1 !in starts)
        assertEquals(setOf(0, 2), starts.toSet())
    }

    @Test fun startTrackWithoutHistoryCoversEveryTrackAndSingleTrackIsAllowed() {
        val random = kotlin.random.Random(3)
        val names = listOf("music_01", "music_02", "music_03")
        assertEquals(setOf(0, 1, 2), (1..200).map { MusicNavigation.startIndex(names, null, random) }.toSet())
        assertEquals(0, MusicNavigation.startIndex(listOf("music_01"), "music_01", random))
        assertEquals(0, MusicNavigation.startIndex(emptyList(), null, random))
    }

    @Test fun entriesKeepNamesForPersistence() {
        val ids = mapOf("music_01" to 11, "music_04" to 14)
        assertEquals(listOf("music_01", "music_04"), MusicPlaylist.resolveEntries { ids[it] ?: 0 }.map { it.name })
    }
}
