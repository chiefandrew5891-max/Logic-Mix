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
        assertEquals(0.8f, musicPlayerVolume(0.8f), 0f)
        assertEquals(1f, musicPlayerVolume(2f), 0f)
        assertEquals(0f, musicPlayerVolume(-1f), 0f)
    }
}
