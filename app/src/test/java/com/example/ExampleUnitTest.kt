package com.example

import com.example.data.model.SongItem
import com.example.data.repository.MusicRepository
import com.example.ui.components.formatDurationTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun lrcParser_extractsSyncedTimestampsAndTextCorrectly() {
        val repo = MusicRepository(musicDao = null)
        val rawLrc = """
            [00:04.50]I've been tryna call
            [00:08.25]I've been on my own for long enough
            [01:12.100]City lights in the dark
        """.trimIndent()

        val parsed = repo.parseLrcString(rawLrc)
        assertEquals(3, parsed.size)
        assertEquals(4_500L, parsed[0].startTimeMs)
        assertEquals("I've been tryna call", parsed[0].text)
        assertEquals(8_250L, parsed[1].startTimeMs)
        assertEquals(72_100L, parsed[2].startTimeMs)
    }

    @Test
    fun scoreSongRelevance_ranksExactSongAndArtistAboveUnrelatedTracks() {
        val repo = MusicRepository(musicDao = null)
        val exactMatch = SongItem(
            id = "1",
            title = "Starboy",
            artist = "The Weeknd",
            albumName = "Starboy",
            artworkUrl = "",
            durationMs = 230_000L,
            streamUrl = "https://example.com/starboy.m4a"
        )
        val unrelated = SongItem(
            id = "2",
            title = "Random Acoustic Guitar",
            artist = "Unknown Artist",
            albumName = "Instrumental",
            artworkUrl = "",
            durationMs = 180_000L,
            streamUrl = "https://example.com/other.m4a"
        )

        val exactScore = repo.scoreSongRelevance(exactMatch, "starboy the weeknd")
        val unrelatedScore = repo.scoreSongRelevance(unrelated, "starboy the weeknd")
        assertTrue("Expected exact match score ($exactScore) > unrelated ($unrelatedScore)", exactScore > unrelatedScore)
        assertEquals(0, unrelatedScore)
    }

    @Test
    fun formatDurationTimestamp_formatsMinutesAndSeconds() {
        assertEquals("00:00", formatDurationTimestamp(0L))
        assertEquals("03:20", formatDurationTimestamp(200_000L))
        assertEquals("04:03", formatDurationTimestamp(243_000L))
    }

    @Test
    fun streamUrlFilter_rejects30SecondPreviewUrlsAndAcceptsFullStreams() {
        val repo = MusicRepository(musicDao = null)
        // 30-second preview URLs from iTunes and Deezer must be rejected
        assertTrue(repo.isPreviewClipUrl("https://cdnt-preview.dzcdn.net/api/1/1/abc123_0.mp3"))
        assertTrue(repo.isPreviewClipUrl("https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview116/v4/track.m4a"))
        assertEquals(false, repo.isFullLengthStreamUrl("https://cdnt-preview.dzcdn.net/api/1/1/abc123_0.mp3"))
        assertEquals(false, repo.isFullLengthStreamUrl("https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview116/v4/track.m4a"))

        // Full-length JioSaavn AAC and Piped/InnerTube streams must be accepted
        assertTrue(repo.isFullLengthStreamUrl("https://aac.saavncdn.com/123/full_track_320.mp4"))
    }

    @Test
    fun filterMatchingLocalPlaylists_returnsMatchingPlaylistsForIndieIndiaQuery() {
        val repo = MusicRepository(musicDao = null)
        val matched = repo.filterMatchingLocalPlaylists("indie india", emptyList())
        assertTrue("Expected playlist results for 'indie india'", matched.isNotEmpty())
        assertTrue(
            "Expected top playlist to match Indie India",
            matched.any { it.title.contains("Indie", ignoreCase = true) }
        )
        assertTrue(
            "Expected Spotify sourcePlatform for editorial Indie India playlists",
            matched.all { it.sourcePlatform == "Spotify" }
        )
    }

    @Test
    fun deduplicatePlaylistTracks_removesDuplicateSongsWithMovieTagsOrArtistVariations() {
        val repo = MusicRepository(musicDao = null)
        val tracks = listOf(
            SongItem(
                id = "saavn_101",
                title = "Kesariya",
                artist = "Arijit Singh, Pritam",
                durationMs = 268_000L,
                artworkUrl = "https://example.com/1.jpg",
                streamUrl = "https://aac.saavncdn.com/101/kesariya_320.mp4"
            ),
            SongItem(
                id = "saavn_102",
                title = "Kesariya (From \"Brahmastra\")",
                artist = "Pritam, Arijit Singh, Amitabh Bhattacharya",
                durationMs = 268_000L,
                artworkUrl = "https://example.com/2.jpg",
                streamUrl = "https://aac.saavncdn.com/102/kesariya_alt_320.mp4"
            ),
            SongItem(
                id = "saavn_103",
                title = "Kesariya - From Brahmastra",
                artist = "Arijit Singh",
                durationMs = 268_000L,
                artworkUrl = "https://example.com/3.jpg",
                streamUrl = "https://aac.saavncdn.com/103/kesariya_v3_320.mp4"
            ),
            SongItem(
                id = "saavn_201",
                title = "Husn",
                artist = "Anuv Jain",
                durationMs = 218_000L,
                artworkUrl = "https://example.com/4.jpg",
                streamUrl = "https://aac.saavncdn.com/201/husn_320.mp4"
            )
        )

        val deduped = repo.deduplicatePlaylistTracks(tracks)
        assertEquals(2, deduped.size)
        assertEquals("saavn_101", deduped[0].id)
        assertEquals("saavn_201", deduped[1].id)
    }

    @Test
    fun cleanTrackTitleAndArtistForLyrics_stripsSoundtrackNoiseAndExtractsPrimaryArtist() {
        val repo = MusicRepository(musicDao = null)
        assertEquals(
            "Apna Bana Le",
            repo.cleanTrackTitleForLyrics("Apna Bana Le (From \"Bhediya\") [Official Video]")
        )
        assertEquals(
            "Chaleya",
            repo.cleanTrackTitleForLyrics("Chaleya - From \"Jawan\" feat. Shilpa Rao")
        )
        assertEquals(
            "Arijit Singh",
            repo.extractPrimaryArtistForLyrics("Arijit Singh, Sachin-Jigar, Amitabh Bhattacharya")
        )
    }

    @Test
    fun extractPlaylistUrlAndSpotifyEmbedTracks_parsesShareLinksAndTrackPairs() {
        val repo = MusicRepository(musicDao = null)
        val rawShareText = "https://open.spotify.com/playlist/7cHWpjlm7bWzOSAsLSxz7h?si=mpIhG477TFyrm2-vf9kB3w&utm_source=copy-link"
        assertEquals(
            "https://open.spotify.com/playlist/7cHWpjlm7bWzOSAsLSxz7h?si=mpIhG477TFyrm2-vf9kB3w&utm_source=copy-link",
            repo.extractPlaylistUrlFromInput(rawShareText)
        )

        val sampleEmbedHtml = """
            <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"state":{"data":{"entity":{
                "title":"Trending Hindi Songs 2026",
                "coverArt":{"sources":[{"url":"https://image-cdn-ak.spotifycdn.com/image/ab67706c0000da84c58372a4c4b7956d6c86642e"}]},
                "trackList":[
                    {"uri":"spotify:track:0tW2gwaRzFQQrgr2UCO56G","uid":"333b","title":"Alfaaz","subtitle":"Hamza Malik,\u00a0Zain Zohaib","contentRatings":{"labels":[]},"duration":184373,"audioPreview":{"format":"MP3_96","url":"https://p.scdn.co/mp3-preview/123"}},
                    {"uri":"spotify:track:3gixnmepHSsyAuho34rprN","uid":"7266","title":"Khat","subtitle":"Navjot Ahuja","contentRatings":{"labels":[]},"duration":296222,"audioPreview":{"format":"MP3_96","url":"https://p.scdn.co/mp3-preview/456"}}
                ]
            }}}}}}
            </script>
        """.trimIndent()
        val parsed = repo.extractSpotifyEmbedPlaylist(sampleEmbedHtml)
        assertEquals("Trending Hindi Songs 2026", parsed.title)
        assertEquals(2, parsed.tracks.size)
        assertEquals("Alfaaz", parsed.tracks[0].title)
        assertEquals("Hamza Malik, Zain Zohaib", parsed.tracks[0].artist)
        assertEquals("Khat", parsed.tracks[1].title)
        assertEquals("Navjot Ahuja", parsed.tracks[1].artist)
    }
}
