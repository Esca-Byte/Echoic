package com.example

import com.example.data.model.BottomNavTab
import com.example.data.model.HomeDiscoveryData
import com.example.data.model.PlayerRepeatMode
import com.example.data.model.PlaylistItem
import com.example.data.model.SongItem
import com.example.data.model.defaultEqBands
import com.example.data.repository.MusicRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun `verify real playlist categories, bottom nav tabs, and zero static demo catalog`() {
        val repository = MusicRepository(musicDao = null)
        // Static demo catalog is removed; initial in-memory cache is empty until live playlists & charts are fetched
        val initialCache = repository.getCuratedCatalog()
        assertTrue(initialCache.isEmpty())

        val categories = repository.getMoodCategories()
        assertEquals(9, categories.size)
        assertEquals("India Hits", categories[0].title)
        assertEquals("Bollywood", categories[1].title)
        assertEquals("Punjabi & Hip-Hop", categories[2].title)
        assertEquals("South & Sufi", categories[3].title)
        assertEquals("Top Hits", categories[4].title)

        val samplePlaylist = PlaylistItem(
            id = "deezer_pl_1",
            title = "Today's Top Hits",
            subtitle = "Global Charts • 30 tracks",
            artworkUrl = "https://example.com/playlist.jpg",
            category = "Top Hits",
            sourcePlatform = "Spotify Charts",
            remotePlaylistId = "1",
            trackCount = 30
        )
        val indiaPlaylist = PlaylistItem(
            id = "live_pl_top_50_india",
            title = "Top 50 — India",
            subtitle = "India Charts • 30 tracks",
            artworkUrl = "https://example.com/india.jpg",
            category = "India Hits",
            sourcePlatform = "India Charts",
            trackCount = 30
        )
        val discoveryData = HomeDiscoveryData(
            topHitsPlaylists = listOf(samplePlaylist),
            indianTopPlaylists = listOf(indiaPlaylist)
        )
        assertEquals(2, discoveryData.allPlaylists.size)
        assertEquals("Top 50 — India", discoveryData.allPlaylists.first().title)

        val bottomTabs = BottomNavTab.entries
        assertEquals(4, bottomTabs.size)
        assertEquals("Home", bottomTabs[0].label)
        assertEquals("Search", bottomTabs[1].label)
        assertEquals("Library", bottomTabs[2].label)
        assertEquals("Settings & EQ", bottomTabs[3].label)

        val bands = defaultEqBands()
        assertEquals(5, bands.size)
        assertEquals("60Hz", bands.first().label)
        assertEquals("14kHz", bands.last().label)
        assertFalse("Equalizer must default to OFF", com.example.data.model.EqualizerState().isEnabled)

        val sampleSong = SongItem(
            id = "test_01",
            title = "Midnight City",
            artist = "M83",
            durationMs = 243_000L,
            artworkUrl = "https://example.com/art.jpg"
        )
        assertFalse(sampleSong.isFavorite)
        assertEquals(PlayerRepeatMode.OFF, PlayerRepeatMode.entries.first())

        // Verify background PlaybackService queue synchronization
        com.example.service.PlaybackService.syncQueueFromUi(listOf(sampleSong), sampleSong)
        assertEquals(1, com.example.service.PlaybackService.serviceQueue.size)
        assertEquals("test_01", com.example.service.PlaybackService.activeServiceSong?.id)
    }
}
