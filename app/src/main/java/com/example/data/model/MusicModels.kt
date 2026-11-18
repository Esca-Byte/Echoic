package com.example.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SongItem(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkUrl: String,
    val streamUrl: String = "",
    val albumName: String = "Single",
    val moodCategory: String = "Trending",
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val localFilePath: String = ""
)

@Immutable
@Serializable
data class PlaylistItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val category: String,
    val sourcePlatform: String,
    val remotePlaylistId: String = "",
    val searchFallbackQuery: String = "",
    val trackCount: Int = 25,
    val preloadedTracks: List<SongItem> = emptyList()
)

@Immutable
data class HomeDiscoveryData(
    val topHitsPlaylists: List<PlaylistItem> = emptyList(),
    val indianTopPlaylists: List<PlaylistItem> = emptyList(),
    val indianGenrePlaylists: List<PlaylistItem> = emptyList(),
    val newAndMoodPlaylists: List<PlaylistItem> = emptyList(),
    val genrePlaylists: List<PlaylistItem> = emptyList(),
    val globalChartSongs: List<SongItem> = emptyList(),
    val indianChartSongs: List<SongItem> = emptyList()
) {
    val allPlaylists: List<PlaylistItem> =
        (indianTopPlaylists + topHitsPlaylists + indianGenrePlaylists + newAndMoodPlaylists + genrePlaylists)
            .distinctBy { it.id }
}

enum class LazyPlatformShelfKey {
    INDIAN_GENRES,
    GLOBAL_TOP_EXPANDED,
    GLOBAL_MOODS,
    GLOBAL_GENRES
}

@Immutable
data class PagedPlaylistTracksResult(
    val tracks: List<SongItem>,
    val nextPage: Int?,
    val hasMore: Boolean,
    val totalCount: Int
)

@Immutable
@Serializable
data class LyricLine(
    val startTimeMs: Long,
    val text: String
)

@Immutable
@Serializable
data class LyricsVariant(
    val languageLabel: String,
    val lines: List<LyricLine>,
    val isSynced: Boolean
)

@Immutable
@Serializable
data class LyricsData(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
    val languageLabel: String = "Original",
    val alternateVariants: List<LyricsVariant> = emptyList()
)

@Immutable
data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingSeconds: Long = 0L,
    val stopAfterCurrentTrack: Boolean = false,
    val totalDurationSeconds: Long = 0L,
    val isFadingOut: Boolean = false
)

enum class PlayerRepeatMode {
    OFF,
    ALL,
    ONE
}

enum class BottomNavTab(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
    LIBRARY("Library"),
    DOWNLOADS("Downloads"),
    SETTINGS("Settings & EQ")
}

enum class SearchFilterType(val label: String) {
    ALL("All"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    SONGS("Songs"),
    SPOTIFY("Spotify"),
    YOUTUBE_MUSIC("YouTube Music"),
    JIOSAAVN("JioSaavn"),
    DEEZER("Deezer")
}

enum class LibraryFilterCategory(val label: String) {
    ALL("All"),
    PLAYLISTS("Playlists"),
    LIKED("Liked Songs"),
    ARTISTS("Artists"),
    MIXES("Smart Mixes")
}

@Immutable
@Serializable
data class SearchArtistItem(
    val id: String = "",
    val name: String,
    val artworkUrl: String = "",
    val subtitle: String = "Artist",
    val fanCount: Long = 0L,
    val relevanceScore: Int = 0
)

@Immutable
data class EqualizerBand(
    val index: Short,
    val centerFreqHz: Int,
    val label: String,
    val minLevelMb: Short,
    val maxLevelMb: Short,
    val currentLevelMb: Short
)

enum class AudioOutputRoute(val key: String, val label: String) {
    SPEAKER("speaker", "Phone Speaker"),
    WIRED_HEADPHONES("wired", "Wired Headphones"),
    BLUETOOTH("bluetooth", "Bluetooth Audio")
}

@Immutable
data class EqualizerState(
    val isEnabled: Boolean = false,
    val bands: List<EqualizerBand> = defaultEqBands(),
    val bassBoostStrength: Int = 250, // 0..1000
    val activePreset: String = "Studio Warmth",
    val crossfadeSeconds: Int = 4, // 0..12s
    val gaplessPlayback: Boolean = true,
    val autoQueueRadio: Boolean = true,
    val autoDeviceProfilesEnabled: Boolean = true,
    val activeOutputRoute: AudioOutputRoute = AudioOutputRoute.SPEAKER,
    val speakerPreset: String = "Treble Sparkle",
    val wiredPreset: String = "Studio Warmth",
    val bluetoothPreset: String = "Bass Boost"
)

fun defaultEqBands(): List<EqualizerBand> = listOf(
    EqualizerBand(index = 0, centerFreqHz = 60, label = "60Hz", minLevelMb = -1500, maxLevelMb = 1500, currentLevelMb = 300),
    EqualizerBand(index = 1, centerFreqHz = 230, label = "230Hz", minLevelMb = -1500, maxLevelMb = 1500, currentLevelMb = 150),
    EqualizerBand(index = 2, centerFreqHz = 910, label = "910Hz", minLevelMb = -1500, maxLevelMb = 1500, currentLevelMb = 0),
    EqualizerBand(index = 3, centerFreqHz = 3600, label = "3.6kHz", minLevelMb = -1500, maxLevelMb = 1500, currentLevelMb = 200),
    EqualizerBand(index = 4, centerFreqHz = 14000, label = "14kHz", minLevelMb = -1500, maxLevelMb = 1500, currentLevelMb = 350)
)

@Immutable
data class MoodCategoryItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val queryKeyword: String
)

@Immutable
@Serializable
data class RelatedArtistItem(
    val name: String,
    val artworkUrl: String = "",
    val subtitle: String = "Similar Artist"
)

@Immutable
@Serializable
data class AlbumReleaseItem(
    val id: String,
    val title: String,
    val artist: String,
    val year: String = "",
    val artworkUrl: String = "",
    val releaseType: String = "Album",
    val trackCount: Int = 0,
    val preloadedTracks: List<SongItem> = emptyList()
)

@Immutable
data class AlbumDetailData(
    val id: String,
    val title: String,
    val artist: String,
    val year: String = "",
    val artworkUrl: String = "",
    val subtitle: String = "Album",
    val tracks: List<SongItem> = emptyList()
)

@Immutable
data class ArtistProfileData(
    val name: String,
    val subtitle: String = "Verified Artist",
    val artworkUrl: String = "",
    val topSongs: List<SongItem> = emptyList(),
    val albums: List<AlbumReleaseItem> = emptyList(),
    val similarArtists: List<RelatedArtistItem> = emptyList()
)

@Immutable
data class BatchDownloadProgress(
    val collectionTitle: String,
    val completedCount: Int,
    val totalCount: Int,
    val isActive: Boolean = true
)

@Immutable
data class ListeningStatsData(
    val totalPlays: Int = 0,
    val totalMinutes: Int = 0,
    val topArtistName: String = "",
    val topArtistArtwork: String = "",
    val topSong: SongItem? = null
)

enum class TrackSortOrder(val label: String) {
    DEFAULT("Default"),
    TITLE_ASC("Title A–Z"),
    ARTIST_ASC("Artist"),
    DOWNLOADED_FIRST("Downloaded")
}

enum class LyricCardTheme(val label: String) {
    ALBUM_GLOW("Album Glow"),
    ELECTRIC_MINT("Electric Mint"),
    SUNSET_CORAL("Sunset Coral"),
    CYBER_VIOLET("Cyber Violet"),
    MIDNIGHT_OLED("Midnight OLED")
}

@Immutable
data class OnboardingGenreOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val queryKeyword: String
)

@Immutable
data class OnboardingArtistOption(
    val name: String,
    val regionTag: String,
    val artworkUrl: String = "",
    val queryKeyword: String = name
)

@Immutable
data class TimeOfDayShelfData(
    val title: String,
    val subtitle: String,
    val songs: List<SongItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList()
)

