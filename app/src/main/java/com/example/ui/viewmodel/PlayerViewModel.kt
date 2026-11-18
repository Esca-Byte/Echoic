package com.example.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import android.content.Context
import com.example.data.local.MusicDatabase
import com.example.data.local.PlaybackSessionStore
import com.example.data.model.AlbumDetailData
import com.example.data.model.AlbumReleaseItem
import com.example.data.model.AppThemeState
import com.example.data.model.ArtistProfileData
import com.example.data.model.AudioOutputRoute
import com.example.data.model.BatchDownloadProgress
import com.example.data.model.BottomNavTab
import com.example.data.model.EqualizerState
import com.example.data.model.HomeDiscoveryData
import com.example.data.model.LazyPlatformShelfKey
import com.example.data.model.ListeningStatsData
import com.example.data.model.LyricsData
import com.example.data.model.MoodCategoryItem
import com.example.data.model.OnboardingArtistOption
import com.example.data.model.OnboardingGenreOption
import com.example.data.model.PlayerRepeatMode
import com.example.data.model.PlaylistItem
import com.example.data.model.RelatedArtistItem
import com.example.data.model.SearchArtistItem
import com.example.data.model.SearchFilterType
import com.example.data.model.SleepTimerState
import com.example.data.model.SongItem
import com.example.data.model.StudioThemePreset
import com.example.data.model.TimeOfDayShelfData
import com.example.data.model.defaultEqBands
import com.example.data.repository.MusicRepository
import com.example.service.PlaybackService
import com.example.widget.MiniPlayerWidgetProvider
import java.util.Calendar
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MusicDatabase.getInstance(application)
    private val repository = MusicRepository(database.musicDao())

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // Bottom Navigation Tab State
    private val _selectedBottomTab = MutableStateFlow(BottomNavTab.HOME)
    val selectedBottomTab: StateFlow<BottomNavTab> = _selectedBottomTab.asStateFlow()

    // Core Player Reactive States
    private val _currentTrack = MutableStateFlow<SongItem?>(null)
    val currentTrack: StateFlow<SongItem?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isResolvingStream = MutableStateFlow(false)
    val isResolvingStream: StateFlow<Boolean> = _isResolvingStream.asStateFlow()

    private val _progressMs = MutableStateFlow(0L)
    val progressMs: StateFlow<Long> = _progressMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<SongItem>>(emptyList())
    val queue: StateFlow<List<SongItem>> = _queue.asStateFlow()

    private val _autoQueuedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val autoQueuedSongIds: StateFlow<Set<String>> = _autoQueuedSongIds.asStateFlow()

    private val _isRefillingAutoQueue = MutableStateFlow(false)
    val isRefillingAutoQueue: StateFlow<Boolean> = _isRefillingAutoQueue.asStateFlow()

    private val sessionAutoQueuedIds = LinkedHashSet<String>()
    private val sessionAutoQueuedSignatures = LinkedHashSet<String>()

    private val _repeatMode = MutableStateFlow(PlayerRepeatMode.OFF)
    val repeatMode: StateFlow<PlayerRepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()

    // Synchronized Lyrics State
    private val _currentLyrics = MutableStateFlow<LyricsData?>(null)
    val currentLyrics: StateFlow<LyricsData?> = _currentLyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    private val playRequestSeq = java.util.concurrent.atomic.AtomicLong(0L)
    private var lyricsFetchedForTrackId: String? = null
    private var lyricsFetchedDurationSec: Long = 0L

    // Smart Sleep Timer & Audio DSP State
    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(1.0f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    // Real Live Discovery, Playlists, Search & Library State
    private val _homeDiscoveryData = MutableStateFlow(HomeDiscoveryData())
    val homeDiscoveryData: StateFlow<HomeDiscoveryData> = _homeDiscoveryData.asStateFlow()

    private val _homeFeedSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val homeFeedSongs: StateFlow<List<SongItem>> = _homeFeedSongs.asStateFlow()

    private val _isHomeLoading = MutableStateFlow(true)
    val isHomeLoading: StateFlow<Boolean> = _isHomeLoading.asStateFlow()

    private val _loadingLazyShelves = MutableStateFlow<Set<LazyPlatformShelfKey>>(emptySet())
    val loadingLazyShelves: StateFlow<Set<LazyPlatformShelfKey>> = _loadingLazyShelves.asStateFlow()

    private val _loadedLazyShelves = MutableStateFlow<Set<LazyPlatformShelfKey>>(emptySet())
    val loadedLazyShelves: StateFlow<Set<LazyPlatformShelfKey>> = _loadedLazyShelves.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<PlaylistItem?>(null)
    val selectedPlaylist: StateFlow<PlaylistItem?> = _selectedPlaylist.asStateFlow()

    private val _selectedPlaylistTracks = MutableStateFlow<List<SongItem>>(emptyList())
    val selectedPlaylistTracks: StateFlow<List<SongItem>> = _selectedPlaylistTracks.asStateFlow()

    private val _isPlaylistTracksLoading = MutableStateFlow(false)
    val isPlaylistTracksLoading: StateFlow<Boolean> = _isPlaylistTracksLoading.asStateFlow()

    private val _hasMorePlaylistTracks = MutableStateFlow(false)
    val hasMorePlaylistTracks: StateFlow<Boolean> = _hasMorePlaylistTracks.asStateFlow()

    private val _isPlaylistLoadingMore = MutableStateFlow(false)
    val isPlaylistLoadingMore: StateFlow<Boolean> = _isPlaylistLoadingMore.asStateFlow()

    private var currentPlaylistNextPage: Int? = null

    // Dedicated Artist Hub State
    private val _selectedArtistProfile = MutableStateFlow<ArtistProfileData?>(null)
    val selectedArtistProfile: StateFlow<ArtistProfileData?> = _selectedArtistProfile.asStateFlow()

    private val _isArtistLoading = MutableStateFlow(false)
    val isArtistLoading: StateFlow<Boolean> = _isArtistLoading.asStateFlow()

    private val artistHistoryStack = ArrayDeque<String>()

    // Dedicated Album Detail State
    private val _selectedAlbumDetail = MutableStateFlow<AlbumDetailData?>(null)
    val selectedAlbumDetail: StateFlow<AlbumDetailData?> = _selectedAlbumDetail.asStateFlow()

    private val _isAlbumLoading = MutableStateFlow(false)
    val isAlbumLoading: StateFlow<Boolean> = _isAlbumLoading.asStateFlow()

    // Personalization: First-Launch Onboarding, Followed Artists, Listening Stats & Smart Auto-Playlists
    private val personalizationPrefs = application.getSharedPreferences("echoic_personalization_prefs", Context.MODE_PRIVATE)
    private val personalizationJson = Json { ignoreUnknownKeys = true }

    val onboardingGenres: List<OnboardingGenreOption> = repository.getOnboardingGenres()

    private val _onboardingArtists = MutableStateFlow(repository.getOnboardingArtists())
    val onboardingArtists: StateFlow<List<OnboardingArtistOption>> = _onboardingArtists.asStateFlow()

    private val _onboardingArtistSearchQuery = MutableStateFlow("")
    val onboardingArtistSearchQuery: StateFlow<String> = _onboardingArtistSearchQuery.asStateFlow()

    private val _onboardingArtistSearchResults = MutableStateFlow<List<OnboardingArtistOption>>(emptyList())
    val onboardingArtistSearchResults: StateFlow<List<OnboardingArtistOption>> = _onboardingArtistSearchResults.asStateFlow()

    private val _isSearchingOnboardingArtists = MutableStateFlow(false)
    val isSearchingOnboardingArtists: StateFlow<Boolean> = _isSearchingOnboardingArtists.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(
        personalizationPrefs.getBoolean("has_completed_onboarding", false)
    )
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private val _isEditingTaste = MutableStateFlow(false)
    val isEditingTaste: StateFlow<Boolean> = _isEditingTaste.asStateFlow()

    private val _selectedTasteGenres = MutableStateFlow<List<String>>(emptyList())
    val selectedTasteGenres: StateFlow<List<String>> = _selectedTasteGenres.asStateFlow()

    private val _selectedTasteArtistNames = MutableStateFlow<List<String>>(emptyList())
    val selectedTasteArtistNames: StateFlow<List<String>> = _selectedTasteArtistNames.asStateFlow()

    private val _personalizedForYouSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val personalizedForYouSongs: StateFlow<List<SongItem>> = _personalizedForYouSongs.asStateFlow()

    private val _personalizedForYouPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val personalizedForYouPlaylists: StateFlow<List<PlaylistItem>> = _personalizedForYouPlaylists.asStateFlow()

    private val _isPersonalizingForYou = MutableStateFlow(false)
    val isPersonalizingForYou: StateFlow<Boolean> = _isPersonalizingForYou.asStateFlow()

    private val _timeOfDayShelf = MutableStateFlow<TimeOfDayShelfData?>(null)
    val timeOfDayShelf: StateFlow<TimeOfDayShelfData?> = _timeOfDayShelf.asStateFlow()

    private val _isTimeOfDayLoading = MutableStateFlow(false)
    val isTimeOfDayLoading: StateFlow<Boolean> = _isTimeOfDayLoading.asStateFlow()

    private val _isKaraokeModeEnabled = MutableStateFlow(false)
    val isKaraokeModeEnabled: StateFlow<Boolean> = _isKaraokeModeEnabled.asStateFlow()
    private var preKaraokeEqSnapshot: EqualizerState? = null

    private val _followedArtists = MutableStateFlow<List<RelatedArtistItem>>(emptyList())
    val followedArtists: StateFlow<List<RelatedArtistItem>> = _followedArtists.asStateFlow()

    private val _listeningStats = MutableStateFlow(ListeningStatsData())
    val listeningStats: StateFlow<ListeningStatsData> = _listeningStats.asStateFlow()

    private val _smartAutoPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val smartAutoPlaylists: StateFlow<List<PlaylistItem>> = _smartAutoPlaylists.asStateFlow()

    private val playCountsBySongId = LinkedHashMap<String, Int>()
    private val cachedStatSongsById = LinkedHashMap<String, SongItem>()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SongItem>>(emptyList())
    val searchResults: StateFlow<List<SongItem>> = _searchResults.asStateFlow()

    private val _searchPlaylistResults = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val searchPlaylistResults: StateFlow<List<PlaylistItem>> = _searchPlaylistResults.asStateFlow()

    private val _searchArtistResults = MutableStateFlow<List<SearchArtistItem>>(emptyList())
    val searchArtistResults: StateFlow<List<SearchArtistItem>> = _searchArtistResults.asStateFlow()

    private val _lastSearchedQuery = MutableStateFlow("")
    val lastSearchedQuery: StateFlow<String> = _lastSearchedQuery.asStateFlow()

    private val _lastSearchResults = MutableStateFlow<List<SongItem>>(emptyList())
    val lastSearchResults: StateFlow<List<SongItem>> = _lastSearchResults.asStateFlow()

    private val _lastSearchPlaylistResults = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val lastSearchPlaylistResults: StateFlow<List<PlaylistItem>> = _lastSearchPlaylistResults.asStateFlow()

    private val _lastSearchArtistResults = MutableStateFlow<List<SearchArtistItem>>(emptyList())
    val lastSearchArtistResults: StateFlow<List<SearchArtistItem>> = _lastSearchArtistResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _selectedMoodFilter = MutableStateFlow<String?>(null)
    val selectedMoodFilter: StateFlow<String?> = _selectedMoodFilter.asStateFlow()

    private val _selectedSearchFilter = MutableStateFlow(SearchFilterType.ALL)
    val selectedSearchFilter: StateFlow<SearchFilterType> = _selectedSearchFilter.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _themeState = MutableStateFlow(PlaybackSessionStore.loadThemeSettings(application))
    val themeState: StateFlow<AppThemeState> = _themeState.asStateFlow()

    val moodCategories: List<MoodCategoryItem> = repository.getMoodCategories()

    val favoriteSongs: StateFlow<List<SongItem>> =
        (repository.favoriteSongsFlow ?: MutableStateFlow(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val offlineSongs: StateFlow<List<SongItem>> =
        (repository.offlineSongsFlow ?: MutableStateFlow(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _downloadingSongIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingSongIds: StateFlow<Set<String>> = _downloadingSongIds.asStateFlow()

    private val _batchDownloadProgress = MutableStateFlow<BatchDownloadProgress?>(null)
    val batchDownloadProgress: StateFlow<BatchDownloadProgress?> = _batchDownloadProgress.asStateFlow()

    val recentSearches: StateFlow<List<String>> =
        (repository.recentSearchesFlow ?: MutableStateFlow(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentlyPlayedSongs: StateFlow<List<SongItem>> =
        (repository.recentlyPlayedSongsFlow ?: MutableStateFlow(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val importedPlaylists: StateFlow<List<PlaylistItem>> =
        (repository.importedPlaylistsFlow ?: MutableStateFlow(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isImportingPlaylist = MutableStateFlow(false)
    val isImportingPlaylist: StateFlow<Boolean> = _isImportingPlaylist.asStateFlow()

    private val _isSyncingPlaylist = MutableStateFlow(false)
    val isSyncingPlaylist: StateFlow<Boolean> = _isSyncingPlaylist.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var tickerJob: Job? = null
    private var lyricsFetchJob: Job? = null
    private var playSongJob: Job? = null
    private var searchJob: Job? = null
    private var homeLoadJob: Job? = null
    private val lazyShelfJobs = mutableMapOf<LazyPlatformShelfKey, Job>()
    private var playlistLoadJob: Job? = null
    private var playlistLoadMoreJob: Job? = null
    private var artistLoadJob: Job? = null
    private var albumLoadJob: Job? = null
    private var batchDownloadJob: Job? = null
    private val activeDownloadJobs = mutableMapOf<String, Job>()
    private var autoRadioJob: Job? = null
    private var onboardingArtistSearchJob: Job? = null
    private var personalizationSeedJob: Job? = null
    private var timeOfDayShelfJob: Job? = null
    private var unshuffledQueueBackup: List<SongItem> = emptyList()
    private val playHistoryStack = ArrayDeque<SongItem>()
    private var consecutiveStreamFailures: Int = 0

    init {
        restoreAudioConsoleSettings()
        restoreSavedSession()
        loadPersonalizationData()
        reconcileOfflineVaultOnStartup()
        connectToPlaybackService()
        observeBackgroundServiceEvents()
        loadRealHomeDiscovery()
        refreshTimeOfDayShelf()
        observeDebouncedSearch()
        observeLibraryForSmartPlaylists()
        startSmooth100msTicker()
        if (!_hasCompletedOnboarding.value) {
            hydrateOnboardingArtistPortraits()
        }
    }

    private fun restoreAudioConsoleSettings() {
        val app = getApplication<Application>()
        val detectedRoute = PlaybackService.detectCurrentAudioOutputRoute(app)
        val savedAudio = PlaybackSessionStore.loadAudioSettings(app)
        _equalizerState.value = savedAudio.copy(
            activeOutputRoute = detectedRoute,
            autoQueueRadio = true
        )
    }

    private fun persistAudioConsoleSettings() {
        PlaybackSessionStore.saveAudioSettings(getApplication(), _equalizerState.value)
    }

    private fun persistPlaybackSession() {
        val active = _currentTrack.value ?: return
        PlaybackSessionStore.saveSession(
            context = getApplication(),
            currentTrack = active,
            queue = _queue.value,
            progressMs = _progressMs.value,
            repeatMode = _repeatMode.value,
            isShuffled = _isShuffled.value,
            autoQueuedIds = _autoQueuedSongIds.value
        )
    }

    private fun restoreSavedSession() {
        val app = getApplication<Application>()
        if (PlaybackService.activeServiceSong != null || _currentTrack.value != null) return
        val session = PlaybackSessionStore.loadSession(app) ?: return
        val restoredTrack = session.currentTrack ?: return
        val restoredQueue = session.queue.ifEmpty { listOf(restoredTrack) }

        _currentTrack.value = restoredTrack
        _queue.value = restoredQueue
        _progressMs.value = session.progressMs.coerceAtLeast(0L)
        _durationMs.value = restoredTrack.durationMs.coerceAtLeast(30_000L)
        _repeatMode.value = runCatching {
            PlayerRepeatMode.valueOf(session.repeatModeName)
        }.getOrDefault(PlayerRepeatMode.OFF)
        _isShuffled.value = session.isShuffled
        _isPlaying.value = false
        _autoQueuedSongIds.value = session.autoQueuedIds.toSet()
        sessionAutoQueuedIds.addAll(session.autoQueuedIds)
        if (!session.isShuffled) {
            unshuffledQueueBackup = emptyList()
        }

        PlaybackService.syncQueueFromUi(restoredQueue, restoredTrack)
        MiniPlayerWidgetProvider.syncWidget(app, restoredTrack, false)
        fetchLyricsForTrack(restoredTrack)
    }

    private fun reconcileOfflineVaultOnStartup() {
        val filesDir = getApplication<Application>().filesDir
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.reconcileOfflineDownloads(filesDir) }
        }
    }

    private fun observeBackgroundServiceEvents() {
        viewModelScope.launch {
            PlaybackService.serviceEvents.collect { event ->
                when (event) {
                    is PlaybackService.ServicePlaybackEvent.TrackResolving -> {
                        val song = event.song
                        if (_currentTrack.value?.id != song.id) {
                            playRequestSeq.incrementAndGet()
                            playSongJob?.cancel()
                            _currentTrack.value = song
                            _progressMs.value = 0L
                            _durationMs.value = song.durationMs.coerceAtLeast(30_000L)
                            _isPlaying.value = true
                            _isResolvingStream.value = true
                            fetchLyricsForTrack(song)
                        }
                    }
                    is PlaybackService.ServicePlaybackEvent.TrackTransitioned -> {
                        val song = event.song
                        val previousId = _currentTrack.value?.id
                        _currentTrack.value = song
                        _isResolvingStream.value = false
                        _isPlaying.value = true
                        _queue.value = _queue.value.map {
                            if (it.id == song.id) song else it
                        }
                        if (previousId != song.id || lyricsFetchedForTrackId != song.id) {
                            _progressMs.value = 0L
                            _durationMs.value = song.durationMs.coerceAtLeast(30_000L)
                            fetchLyricsForTrack(song)
                            recordSongPlayForStats(song)
                            sessionAutoQueuedSignatures.add(repository.songDeduplicationSignature(song))
                            syncAutoQueueMetadataToService()
                            maybeReplenishEndlessQueue()
                            viewModelScope.launch {
                                repository.recordRecentlyPlayed(song)
                            }
                        }
                    }
                    is PlaybackService.ServicePlaybackEvent.AutoQueueReplenished -> {
                        if (event.songs.isNotEmpty()) {
                            appendAutoQueuedTracks(event.songs)
                        }
                    }
                    is PlaybackService.ServicePlaybackEvent.StreamUnavailable -> {
                        _isResolvingStream.value = false
                        _isPlaying.value = false
                        consecutiveStreamFailures++
                        if (consecutiveStreamFailures < 3 && _queue.value.size > 1) {
                            showStatusMessage("Unavailable: \"${event.song.title}\" — skipping next")
                            skipNext(isAutoRecovery = true)
                        } else {
                            showStatusMessage("Stream unavailable for \"${event.song.title}\"")
                        }
                    }
                    is PlaybackService.ServicePlaybackEvent.FavoriteToggled -> {
                        applyFavoriteStateToUi(event.songId, event.isFavorite)
                        showStatusMessage(
                            if (event.isFavorite) "Saved to Liked Tracks" else "Removed from Liked Tracks"
                        )
                    }
                    is PlaybackService.ServicePlaybackEvent.ShuffleToggled -> {
                        toggleShuffle(forceValue = event.isShuffled)
                        showStatusMessage(if (event.isShuffled) "Shuffle On" else "Shuffle Off")
                    }
                    is PlaybackService.ServicePlaybackEvent.AudioRouteChanged -> {
                        val route = event.route
                        _equalizerState.value = _equalizerState.value.copy(activeOutputRoute = route)
                        if (_equalizerState.value.autoDeviceProfilesEnabled && !_isKaraokeModeEnabled.value) {
                            val targetPreset = presetForAudioRoute(_equalizerState.value, route)
                            applyEqualizerPreset(targetPreset)
                            showStatusMessage("${route.label} → $targetPreset")
                        }
                    }
                    PlaybackService.ServicePlaybackEvent.SleepTimerEndedTrack -> {
                        _isPlaying.value = false
                        _sleepTimerState.value = SleepTimerState()
                        persistPlaybackSession()
                    }
                }
            }
        }
    }

    private fun applyFavoriteStateToUi(songId: String, isNowFav: Boolean) {
        if (_currentTrack.value?.id == songId) {
            _currentTrack.value = _currentTrack.value?.copy(isFavorite = isNowFav)
        }
        _queue.value = _queue.value.map {
            if (it.id == songId) it.copy(isFavorite = isNowFav) else it
        }
        _searchResults.value = _searchResults.value.map {
            if (it.id == songId) it.copy(isFavorite = isNowFav) else it
        }
        _lastSearchResults.value = _lastSearchResults.value.map {
            if (it.id == songId) it.copy(isFavorite = isNowFav) else it
        }
        _homeFeedSongs.value = _homeFeedSongs.value.map {
            if (it.id == songId) it.copy(isFavorite = isNowFav) else it
        }
        _personalizedForYouSongs.value = _personalizedForYouSongs.value.map {
            if (it.id == songId) it.copy(isFavorite = isNowFav) else it
        }
        _timeOfDayShelf.value = _timeOfDayShelf.value?.let { shelf ->
            shelf.copy(
                songs = shelf.songs.map {
                    if (it.id == songId) it.copy(isFavorite = isNowFav) else it
                }
            )
        }
        _selectedPlaylistTracks.value = _selectedPlaylistTracks.value.map {
            if (it.id == songId) it.copy(isFavorite = isNowFav) else it
        }
        _selectedArtistProfile.value = _selectedArtistProfile.value?.let { artist ->
            artist.copy(
                topSongs = artist.topSongs.map {
                    if (it.id == songId) it.copy(isFavorite = isNowFav) else it
                }
            )
        }
        _selectedAlbumDetail.value = _selectedAlbumDetail.value?.let { album ->
            album.copy(
                tracks = album.tracks.map {
                    if (it.id == songId) it.copy(isFavorite = isNowFav) else it
                }
            )
        }
    }

    fun refreshHomeDiscovery() {
        loadRealHomeDiscovery()
        refreshTimeOfDayShelf()
    }

    private fun loadRealHomeDiscovery() {
        homeLoadJob?.cancel()
        lazyShelfJobs.values.forEach { it.cancel() }
        lazyShelfJobs.clear()
        _loadedLazyShelves.value = emptySet()
        _loadingLazyShelves.value = emptySet()

        homeLoadJob = viewModelScope.launch {
            _isHomeLoading.value = true
            val discovery = runCatching { repository.fetchCoreHomeDiscoveryData() }
                .getOrDefault(HomeDiscoveryData())
            val favIds = favoriteSongs.value.map { it.id }.toSet()
            val chartWithFavs = discovery.globalChartSongs.map {
                it.copy(isFavorite = favIds.contains(it.id))
            }
            val indiaWithFavs = discovery.indianChartSongs.map {
                it.copy(isFavorite = favIds.contains(it.id))
            }
            val blendedFeed = buildBlendedHomeFeed(indiaWithFavs, chartWithFavs)

            _homeDiscoveryData.value = discovery.copy(
                globalChartSongs = chartWithFavs,
                indianChartSongs = indiaWithFavs
            )
            _homeFeedSongs.value = blendedFeed.ifEmpty { chartWithFavs }
            if (_searchQuery.value.isBlank() && _searchResults.value.isEmpty()) {
                _searchResults.value = _homeFeedSongs.value
            }
            _isHomeLoading.value = false
        }
    }

    private fun buildBlendedHomeFeed(
        indiaSongs: List<SongItem>,
        globalSongs: List<SongItem>
    ): List<SongItem> = buildList {
        val maxLen = maxOf(indiaSongs.size, globalSongs.size)
        for (i in 0 until maxLen) {
            if (i < indiaSongs.size) add(indiaSongs[i])
            if (i < globalSongs.size) add(globalSongs[i])
        }
    }.distinctBy { it.id }

    fun loadLazyHomeShelf(shelfKey: LazyPlatformShelfKey) {
        if (shelfKey in _loadedLazyShelves.value || lazyShelfJobs[shelfKey]?.isActive == true) {
            return
        }
        lazyShelfJobs[shelfKey] = viewModelScope.launch {
            _loadingLazyShelves.value = _loadingLazyShelves.value + shelfKey
            try {
                val updatedSlice = runCatching {
                    repository.fetchLazyPlatformShelf(shelfKey, _homeDiscoveryData.value)
                }.getOrNull() ?: return@launch

                val current = _homeDiscoveryData.value
                val favIds = favoriteSongs.value.map { it.id }.toSet()
                val merged = when (shelfKey) {
                    LazyPlatformShelfKey.INDIAN_GENRES -> current.copy(
                        indianGenrePlaylists = updatedSlice.indianGenrePlaylists
                    )
                    LazyPlatformShelfKey.GLOBAL_TOP_EXPANDED -> {
                        val enrichedGlobalCharts = updatedSlice.globalChartSongs.map {
                            it.copy(isFavorite = favIds.contains(it.id))
                        }
                        val refreshedFeed = buildBlendedHomeFeed(
                            current.indianChartSongs,
                            enrichedGlobalCharts
                        )
                        _homeFeedSongs.value = refreshedFeed.ifEmpty { enrichedGlobalCharts }
                        current.copy(
                            topHitsPlaylists = updatedSlice.topHitsPlaylists,
                            globalChartSongs = enrichedGlobalCharts
                        )
                    }
                    LazyPlatformShelfKey.GLOBAL_MOODS -> current.copy(
                        newAndMoodPlaylists = updatedSlice.newAndMoodPlaylists
                    )
                    LazyPlatformShelfKey.GLOBAL_GENRES -> current.copy(
                        genrePlaylists = updatedSlice.genrePlaylists
                    )
                }
                _homeDiscoveryData.value = merged
                _loadedLazyShelves.value = _loadedLazyShelves.value + shelfKey
            } finally {
                _loadingLazyShelves.value = _loadingLazyShelves.value - shelfKey
            }
        }
    }

    fun selectBottomTab(tab: BottomNavTab) {
        _selectedBottomTab.value = tab
    }

    /**
     * Opens the dedicated Playlist Detail screen and loads page 1 (15 tracks) first for fast loading on slow connections.
     */
    fun openPlaylist(playlist: PlaylistItem) {
        _selectedPlaylist.value = playlist
        playlistLoadJob?.cancel()
        playlistLoadMoreJob?.cancel()
        _hasMorePlaylistTracks.value = false
        _isPlaylistLoadingMore.value = false
        currentPlaylistNextPage = null

        playlistLoadJob = viewModelScope.launch {
            _isPlaylistTracksLoading.value = true
            val favIds = favoriteSongs.value.map { it.id }.toSet()
            val previewTracks = repository.deduplicatePlaylistTracks(playlist.preloadedTracks)
                .take(15)
                .map { it.copy(isFavorite = favIds.contains(it.id)) }
            _selectedPlaylistTracks.value = previewTracks

            val pageResult = runCatching {
                repository.fetchPlaylistTracksPage(playlist, page = 1, pageSize = 15)
            }.getOrNull()

            if (_selectedPlaylist.value?.id == playlist.id) {
                if (pageResult != null) {
                    val deduped = repository.deduplicatePlaylistTracks(
                        pageResult.tracks.ifEmpty { playlist.preloadedTracks }
                    )
                    val latestFavIds = favoriteSongs.value.map { it.id }.toSet()
                    _selectedPlaylistTracks.value = deduped.map {
                        it.copy(isFavorite = latestFavIds.contains(it.id))
                    }
                    _hasMorePlaylistTracks.value = pageResult.hasMore && pageResult.nextPage != null
                    currentPlaylistNextPage = pageResult.nextPage
                } else {
                    _hasMorePlaylistTracks.value = false
                    currentPlaylistNextPage = null
                }
                _isPlaylistTracksLoading.value = false
            }
        }
    }

    fun loadNextPlaylistPage() {
        if (_isPlaylistTracksLoading.value || _isPlaylistLoadingMore.value || !_hasMorePlaylistTracks.value) {
            return
        }
        val nextPage = currentPlaylistNextPage ?: return
        val activePlaylist = _selectedPlaylist.value ?: return

        playlistLoadMoreJob?.cancel()
        playlistLoadMoreJob = viewModelScope.launch {
            _isPlaylistLoadingMore.value = true
            try {
                val result = runCatching {
                    repository.fetchPlaylistTracksPage(activePlaylist, page = nextPage, pageSize = 15)
                }.getOrNull()

                if (_selectedPlaylist.value?.id != activePlaylist.id) return@launch

                if (result != null && result.tracks.isNotEmpty()) {
                    val favIds = favoriteSongs.value.map { it.id }.toSet()
                    val merged = repository.deduplicatePlaylistTracks(
                        _selectedPlaylistTracks.value + result.tracks
                    ).map { it.copy(isFavorite = favIds.contains(it.id)) }
                    _selectedPlaylistTracks.value = merged
                    _hasMorePlaylistTracks.value = result.hasMore && result.nextPage != null
                    currentPlaylistNextPage = result.nextPage
                } else {
                    _hasMorePlaylistTracks.value = false
                    currentPlaylistNextPage = null
                }
            } finally {
                _isPlaylistLoadingMore.value = false
            }
        }
    }

    fun closePlaylistDetail() {
        playlistLoadJob?.cancel()
        playlistLoadMoreJob?.cancel()
        _isPlaylistLoadingMore.value = false
        _hasMorePlaylistTracks.value = false
        currentPlaylistNextPage = null
        _selectedPlaylist.value = null
    }

    /**
     * Opens the Dedicated Artist Hub screen for [rawArtistName].
     * Supports navigating across Similar Artists with backstack history.
     */
    fun openArtistHub(rawArtistName: String, pushCurrentToBackStack: Boolean = true) {
        val cleaned = rawArtistName
            .split(Regex("[,&/]|\\b(?:feat|ft|featuring|with)\\b", RegexOption.IGNORE_CASE))
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
            ?: rawArtistName.trim()
        if (cleaned.isBlank()) return

        val existingArtist = _selectedArtistProfile.value
        if (existingArtist != null &&
            existingArtist.name.equals(cleaned, ignoreCase = true) &&
            (existingArtist.topSongs.isNotEmpty() || _isArtistLoading.value)
        ) {
            _isPlayerExpanded.value = false
            return
        }

        val currentArtist = existingArtist?.name
        if (pushCurrentToBackStack && !currentArtist.isNullOrBlank() && !currentArtist.equals(cleaned, ignoreCase = true)) {
            artistHistoryStack.addLast(currentArtist)
        }

        _isPlayerExpanded.value = false
        _selectedArtistProfile.value = ArtistProfileData(
            name = cleaned,
            subtitle = "Loading Artist Profile..."
        )
        artistLoadJob?.cancel()
        artistLoadJob = viewModelScope.launch {
            _isArtistLoading.value = true
            val profile = runCatching { repository.fetchArtistProfile(cleaned) }
                .getOrDefault(ArtistProfileData(name = cleaned))
            val favIds = favoriteSongs.value.map { it.id }.toSet()
            val offIds = offlineSongs.value.map { it.id }.toSet()
            _selectedArtistProfile.value = profile.copy(
                topSongs = profile.topSongs.map { song ->
                    song.copy(
                        isFavorite = favIds.contains(song.id),
                        isDownloaded = offIds.contains(song.id)
                    )
                }
            )
            _isArtistLoading.value = false
        }
    }

    fun closeArtistHub() {
        val previousArtist = artistHistoryStack.removeLastOrNull()
        if (!previousArtist.isNullOrBlank()) {
            openArtistHub(previousArtist, pushCurrentToBackStack = false)
        } else {
            _selectedArtistProfile.value = null
        }
    }

    fun playArtistTopSongs(shuffle: Boolean = false) {
        val profile = _selectedArtistProfile.value ?: return
        val songs = profile.topSongs
        if (songs.isEmpty()) {
            showStatusMessage("Loading top songs for ${profile.name}...")
            return
        }
        val playQueue = if (shuffle) {
            unshuffledQueueBackup = songs
            songs.shuffled()
        } else {
            unshuffledQueueBackup = emptyList()
            songs
        }
        _isShuffled.value = shuffle
        sendServiceCustomCommand(
            PlaybackService.CMD_REFRESH_SHUFFLE_ICON,
            Bundle().apply { putBoolean(PlaybackService.ARG_IS_SHUFFLED, shuffle) }
        )
        playSong(playQueue.first(), playQueue)
    }

    fun startArtistRadio(artistProfile: ArtistProfileData? = _selectedArtistProfile.value) {
        val profile = artistProfile ?: return
        val seed = profile.topSongs.firstOrNull()
        if (seed != null) {
            showStatusMessage("Starting ${profile.name} Radio...")
            playSong(seed, emptyList())
        } else {
            showStatusMessage("Loading ${profile.name} Radio...")
        }
    }

    /**
     * Opens the Dedicated Album Detail screen from an [AlbumReleaseItem] (e.g. from Artist Hub).
     */
    fun openAlbumDetail(albumRelease: AlbumReleaseItem) {
        _isPlayerExpanded.value = false
        _selectedAlbumDetail.value = AlbumDetailData(
            id = albumRelease.id,
            title = albumRelease.title,
            artist = albumRelease.artist,
            year = albumRelease.year,
            artworkUrl = albumRelease.artworkUrl,
            subtitle = "Loading Album Tracks...",
            tracks = albumRelease.preloadedTracks
        )
        albumLoadJob?.cancel()
        albumLoadJob = viewModelScope.launch {
            _isAlbumLoading.value = true
            val detail = runCatching { repository.fetchAlbumDetails(albumRelease) }
                .getOrDefault(
                    AlbumDetailData(
                        id = albumRelease.id,
                        title = albumRelease.title,
                        artist = albumRelease.artist,
                        year = albumRelease.year,
                        artworkUrl = albumRelease.artworkUrl,
                        tracks = albumRelease.preloadedTracks
                    )
                )
            val favIds = favoriteSongs.value.map { it.id }.toSet()
            val offIds = offlineSongs.value.map { it.id }.toSet()
            _selectedAlbumDetail.value = detail.copy(
                tracks = detail.tracks.map { song ->
                    song.copy(
                        isFavorite = favIds.contains(song.id),
                        isDownloaded = offIds.contains(song.id)
                    )
                }
            )
            _isAlbumLoading.value = false
        }
    }

    /**
     * Opens the Dedicated Album Detail screen by album name + artist (e.g. from Expanded Player Sheet).
     */
    fun openAlbumByName(albumTitle: String, artistName: String, artworkUrl: String = "") {
        val trimmedTitle = albumTitle.trim().takeIf { it.isNotBlank() && !it.equals("Single", ignoreCase = true) }
            ?: _currentTrack.value?.title.orEmpty()
        if (trimmedTitle.isBlank()) return
        openAlbumDetail(
            AlbumReleaseItem(
                id = "album_lookup_${trimmedTitle.lowercase().replace(Regex("[^a-z0-9]"), "_")}",
                title = trimmedTitle,
                artist = artistName,
                artworkUrl = artworkUrl,
                releaseType = "Album"
            )
        )
    }

    fun closeAlbumDetail() {
        _selectedAlbumDetail.value = null
    }

    fun playAlbum(shuffle: Boolean = false) {
        val album = _selectedAlbumDetail.value ?: return
        val tracks = album.tracks
        if (tracks.isEmpty()) {
            showStatusMessage("Loading tracks for ${album.title}...")
            return
        }
        val playQueue = if (shuffle) {
            unshuffledQueueBackup = tracks
            tracks.shuffled()
        } else {
            unshuffledQueueBackup = emptyList()
            tracks
        }
        _isShuffled.value = shuffle
        sendServiceCustomCommand(
            PlaybackService.CMD_REFRESH_SHUFFLE_ICON,
            Bundle().apply { putBoolean(PlaybackService.ARG_IS_SHUFFLED, shuffle) }
        )
        playSong(playQueue.first(), playQueue)
    }

    /**
     * Toggles following/pinning an artist so they appear in the Library Favorite Artists row.
     */
    fun toggleFollowArtist(artistName: String, artworkUrl: String = "") {
        val clean = artistName.trim()
        if (clean.isBlank()) return
        val current = _followedArtists.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.name.equals(clean, ignoreCase = true) }
        val isNowFollowed = if (existingIdx >= 0) {
            current.removeAt(existingIdx)
            false
        } else {
            val resolvedArt = artworkUrl.ifBlank {
                _selectedArtistProfile.value?.takeIf { it.name.equals(clean, ignoreCase = true) }?.artworkUrl.orEmpty()
            }
            current.add(0, RelatedArtistItem(name = clean, artworkUrl = resolvedArt, subtitle = "Following"))
            true
        }
        _followedArtists.value = current
        saveFollowedArtistsToPrefs(current)
        showStatusMessage(if (isNowFollowed) "Following $clean" else "Unfollowed $clean")
    }

    private fun loadPersonalizationData() {
        runCatching {
            val followedJson = personalizationPrefs.getString("followed_artists_json", null)
            if (!followedJson.isNullOrBlank()) {
                _followedArtists.value = personalizationJson.decodeFromString(
                    ListSerializer(RelatedArtistItem.serializer()),
                    followedJson
                )
            }
            val genresJson = personalizationPrefs.getString("selected_onboarding_genres_json", null)
            val savedGenres = if (!genresJson.isNullOrBlank()) {
                personalizationJson.decodeFromString(
                    ListSerializer(String.serializer()),
                    genresJson
                )
            } else {
                emptyList()
            }
            _selectedTasteGenres.value = savedGenres

            val artistsJson = personalizationPrefs.getString("selected_onboarding_artists_json", null)
            val savedArtistNames = if (!artistsJson.isNullOrBlank()) {
                personalizationJson.decodeFromString(
                    ListSerializer(String.serializer()),
                    artistsJson
                )
            } else {
                _followedArtists.value.map { it.name }
            }
            _selectedTasteArtistNames.value = savedArtistNames

            val countsJson = personalizationPrefs.getString("play_counts_json", null)
            if (!countsJson.isNullOrBlank()) {
                val map = personalizationJson.decodeFromString(
                    MapSerializer(String.serializer(), Int.serializer()),
                    countsJson
                )
                playCountsBySongId.putAll(map)
            }
            val songsJson = personalizationPrefs.getString("stat_songs_json", null)
            if (!songsJson.isNullOrBlank()) {
                val list = personalizationJson.decodeFromString(
                    ListSerializer(SongItem.serializer()),
                    songsJson
                )
                list.forEach { cachedStatSongsById[it.id] = it }
            }
            recomputeListeningStatsAndSmartMixes()

            // Endless Auto-Queue is permanently enabled
            personalizationPrefs.edit().putBoolean("auto_queue_radio_enabled", true).apply()
            _equalizerState.value = _equalizerState.value.copy(autoQueueRadio = true)
            syncAutoQueueMetadataToService()

            if (_hasCompletedOnboarding.value && (savedGenres.isNotEmpty() || savedArtistNames.isNotEmpty())) {
                hydratePersonalizedRecommendations(savedGenres, savedArtistNames)
            }
        }
    }

    private fun hydrateOnboardingArtistPortraits() {
        viewModelScope.launch {
            val hydrated = runCatching {
                repository.hydrateArtistPortraitUrls(_onboardingArtists.value)
            }.getOrNull() ?: return@launch
            _onboardingArtists.value = hydrated
        }
    }

    fun openTasteEditor() {
        hydrateOnboardingArtistPortraits()
        _onboardingArtistSearchQuery.value = ""
        _onboardingArtistSearchResults.value = emptyList()
        _isEditingTaste.value = true
    }

    fun dismissTasteEditor() {
        _isEditingTaste.value = false
    }

    fun onOnboardingArtistSearchQueryChange(query: String) {
        _onboardingArtistSearchQuery.value = query
        onboardingArtistSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _onboardingArtistSearchResults.value = emptyList()
            _isSearchingOnboardingArtists.value = false
            return
        }
        onboardingArtistSearchJob = viewModelScope.launch {
            _isSearchingOnboardingArtists.value = true
            delay(280L)
            val results = runCatching { repository.searchOnboardingArtists(trimmed) }
                .getOrDefault(emptyList())
            _onboardingArtistSearchResults.value = results
            _isSearchingOnboardingArtists.value = false
        }
    }

    fun completeOnboarding(genres: List<String>, artists: List<OnboardingArtistOption>) {
        if (genres.size < 3 || artists.size < 3) return
        val cleanGenres = genres.distinct()
        val cleanArtists = artists.distinctBy { it.name.lowercase() }
        val cleanArtistNames = cleanArtists.map { it.name }

        // Auto-follow selected artists without duplicating already-followed artists
        val updatedFollowed = buildList {
            cleanArtists.forEach { chosen ->
                add(
                    RelatedArtistItem(
                        name = chosen.name,
                        artworkUrl = chosen.artworkUrl,
                        subtitle = "Following"
                    )
                )
            }
            _followedArtists.value.forEach { existing ->
                if (none { it.name.equals(existing.name, ignoreCase = true) }) {
                    add(existing)
                }
            }
        }
        _followedArtists.value = updatedFollowed
        saveFollowedArtistsToPrefs(updatedFollowed)

        _selectedTasteGenres.value = cleanGenres
        _selectedTasteArtistNames.value = cleanArtistNames
        syncAutoQueueMetadataToService()
        runCatching {
            val genresEncoded = personalizationJson.encodeToString(
                ListSerializer(String.serializer()),
                cleanGenres
            )
            val artistNamesEncoded = personalizationJson.encodeToString(
                ListSerializer(String.serializer()),
                cleanArtistNames
            )
            personalizationPrefs.edit()
                .putBoolean("has_completed_onboarding", true)
                .putString("selected_onboarding_genres_json", genresEncoded)
                .putString("selected_onboarding_artists_json", artistNamesEncoded)
                .apply()
        }
        _hasCompletedOnboarding.value = true
        _isEditingTaste.value = false
        hydratePersonalizedRecommendations(cleanGenres, cleanArtistNames)
        refreshTimeOfDayShelf()
        showStatusMessage("Tailored Home & Mixes for ${cleanArtists.take(2).joinToString(", ") { it.name }}")
    }

    fun refreshTimeOfDayShelf() {
        timeOfDayShelfJob?.cancel()
        timeOfDayShelfJob = viewModelScope.launch {
            _isTimeOfDayLoading.value = true
            try {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val data = runCatching {
                    repository.fetchTimeOfDayShelf(hour, _selectedTasteGenres.value)
                }.getOrNull()
                if (data != null) {
                    val favIds = favoriteSongs.value.map { it.id }.toSet()
                    val offIds = offlineSongs.value.map { it.id }.toSet()
                    _timeOfDayShelf.value = data.copy(
                        songs = data.songs.map {
                            it.copy(
                                isFavorite = favIds.contains(it.id),
                                isDownloaded = offIds.contains(it.id)
                            )
                        }
                    )
                }
            } finally {
                _isTimeOfDayLoading.value = false
            }
        }
    }

    private fun hydratePersonalizedRecommendations(genres: List<String>, artistNames: List<String>) {
        personalizationSeedJob?.cancel()
        personalizationSeedJob = viewModelScope.launch {
            _isPersonalizingForYou.value = true
            try {
                val (songs, playlists) = runCatching {
                    repository.buildPersonalizedForYouSeed(genres, artistNames)
                }.getOrDefault(emptyList<SongItem>() to emptyList())
                val favIds = favoriteSongs.value.map { it.id }.toSet()
                val offIds = offlineSongs.value.map { it.id }.toSet()
                _personalizedForYouSongs.value = songs.map {
                    it.copy(isFavorite = favIds.contains(it.id), isDownloaded = offIds.contains(it.id))
                }
                _personalizedForYouPlaylists.value = playlists
                recomputeListeningStatsAndSmartMixes()
            } finally {
                _isPersonalizingForYou.value = false
            }
        }
    }

    private fun saveFollowedArtistsToPrefs(list: List<RelatedArtistItem>) {
        runCatching {
            val encoded = personalizationJson.encodeToString(
                ListSerializer(RelatedArtistItem.serializer()),
                list
            )
            personalizationPrefs.edit().putString("followed_artists_json", encoded).apply()
        }
    }

    private fun recordSongPlayForStats(song: SongItem) {
        val newCount = (playCountsBySongId[song.id] ?: 0) + 1
        playCountsBySongId[song.id] = newCount
        cachedStatSongsById[song.id] = song
        runCatching {
            val countsEncoded = personalizationJson.encodeToString(
                MapSerializer(String.serializer(), Int.serializer()),
                playCountsBySongId
            )
            val songsEncoded = personalizationJson.encodeToString(
                ListSerializer(SongItem.serializer()),
                cachedStatSongsById.values.take(60).toList()
            )
            personalizationPrefs.edit()
                .putString("play_counts_json", countsEncoded)
                .putString("stat_songs_json", songsEncoded)
                .apply()
        }
        recomputeListeningStatsAndSmartMixes()
    }

    private fun observeLibraryForSmartPlaylists() {
        viewModelScope.launch {
            recentlyPlayedSongs.collectLatest {
                recomputeListeningStatsAndSmartMixes()
            }
        }
        viewModelScope.launch {
            favoriteSongs.collectLatest {
                recomputeListeningStatsAndSmartMixes()
            }
        }
        viewModelScope.launch {
            offlineSongs.collectLatest {
                recomputeListeningStatsAndSmartMixes()
            }
        }
    }

    private fun recomputeListeningStatsAndSmartMixes() {
        val recent = recentlyPlayedSongs.value
        val favs = favoriteSongs.value
        val offline = offlineSongs.value
        val allKnownSongs = (recent + favs + offline + cachedStatSongsById.values + _homeFeedSongs.value)
            .distinctBy { it.id }
        val songMap = allKnownSongs.associateBy { it.id }

        // Ensure every recently played song has at least 1 recorded play
        recent.forEach { s ->
            if (s.id !in playCountsBySongId) {
                playCountsBySongId[s.id] = 1
            }
            if (s.id !in cachedStatSongsById) {
                cachedStatSongsById[s.id] = s
            }
        }

        val totalPlays = playCountsBySongId.values.sum().coerceAtLeast(recent.size)
        val totalMs = playCountsBySongId.entries.sumOf { (id, count) ->
            val dur = songMap[id]?.durationMs ?: 200_000L
            dur * count
        }
        val totalMinutes = ((totalMs / 60_000L).toInt()).coerceAtLeast(totalPlays * 3)

        // Compute Top Song
        val topSongEntry = playCountsBySongId.entries.maxByOrNull { it.value }
        val topSong = topSongEntry?.key?.let { songMap[it] } ?: recent.firstOrNull() ?: favs.firstOrNull()

        // Compute Top Artist
        val artistScores = LinkedHashMap<String, Int>()
        val artistArt = LinkedHashMap<String, String>()
        for ((songId, count) in playCountsBySongId) {
            val song = songMap[songId] ?: continue
            val primary = song.artist.split(Regex("[,&/]|\\b(?:feat|ft)\\b", RegexOption.IGNORE_CASE))
                .firstOrNull()?.trim().orEmpty()
            if (primary.isNotBlank() && !primary.equals("Unknown Artist", ignoreCase = true)) {
                artistScores[primary] = (artistScores[primary] ?: 0) + count
                if (primary !in artistArt && song.artworkUrl.isNotBlank()) {
                    artistArt[primary] = song.artworkUrl
                }
            }
        }
        val topArtistName = artistScores.maxByOrNull { it.value }?.key
            ?: topSong?.artist?.split(",")?.firstOrNull()?.trim().orEmpty()
        val topArtistArtwork = artistArt[topArtistName].orEmpty()
            .ifBlank { topSong?.artworkUrl.orEmpty() }

        _listeningStats.value = ListeningStatsData(
            totalPlays = totalPlays,
            totalMinutes = totalMinutes,
            topArtistName = topArtistName,
            topArtistArtwork = topArtistArtwork,
            topSong = topSong
        )

        // Build Smart Auto-Playlists:
        // 1. On Repeat (Top Most Played Songs)
        val onRepeatTracks = playCountsBySongId.entries
            .sortedByDescending { it.value }
            .mapNotNull { songMap[it.key] }
            .let { ranked -> (ranked + recent + favs).distinctBy { it.id }.take(25) }

        // 2. Recently Added (Newest Liked & Downloaded Tracks)
        val recentlyAddedTracks = (favs + offline + recent).distinctBy { it.id }.take(25)

        // 3. Late Night Mix (Chill, Indie, Acoustic & Atmospheric Tracks)
        val lateNightKeywords = listOf("chill", "lofi", "lo-fi", "indie", "acoustic", "night", "love", "sufi", "unplugged", "slow", "rain", "dream")
        val lateNightFiltered = allKnownSongs.filter { s ->
            val blob = "${s.title} ${s.artist} ${s.albumName} ${s.moodCategory}".lowercase()
            lateNightKeywords.any { blob.contains(it) }
        }
        val lateNightTracks = (lateNightFiltered + recent.reversed() + favs).distinctBy { it.id }.take(25)

        val tasteSeedTracks = _personalizedForYouSongs.value
        val smartMixes = buildList {
            if (tasteSeedTracks.isNotEmpty()) {
                val genreHint = _selectedTasteGenres.value.take(2).joinToString(" • ")
                val subtitle = if (genreHint.isNotBlank()) {
                    "Built from $genreHint & favorite artists • Auto-Mix"
                } else {
                    "Tailored from your followed artists & taste • Auto-Mix"
                }
                add(
                    PlaylistItem(
                        id = "smart_mix_your_taste",
                        title = "Your Taste Mix",
                        subtitle = subtitle,
                        artworkUrl = tasteSeedTracks.first().artworkUrl,
                        category = "Smart Mix",
                        sourcePlatform = "Echoic Mix",
                        trackCount = tasteSeedTracks.size,
                        preloadedTracks = tasteSeedTracks
                    )
                )
            }
            if (onRepeatTracks.isNotEmpty()) {
                add(
                    PlaylistItem(
                        id = "smart_mix_on_repeat",
                        title = "On Repeat",
                        subtitle = "Your top played tracks right now • Auto-Mix",
                        artworkUrl = onRepeatTracks.first().artworkUrl,
                        category = "Smart Mix",
                        sourcePlatform = "Echoic Mix",
                        trackCount = onRepeatTracks.size,
                        preloadedTracks = onRepeatTracks
                    )
                )
            }
            if (recentlyAddedTracks.isNotEmpty()) {
                add(
                    PlaylistItem(
                        id = "smart_mix_recently_added",
                        title = "Recently Added",
                        subtitle = "Freshly liked & downloaded tracks • Auto-Mix",
                        artworkUrl = recentlyAddedTracks.first().artworkUrl,
                        category = "Smart Mix",
                        sourcePlatform = "Echoic Mix",
                        trackCount = recentlyAddedTracks.size,
                        preloadedTracks = recentlyAddedTracks
                    )
                )
            }
            if (lateNightTracks.isNotEmpty()) {
                add(
                    PlaylistItem(
                        id = "smart_mix_late_night",
                        title = "Late Night Mix",
                        subtitle = "Mellow, indie & unplugged favorites • Auto-Mix",
                        artworkUrl = lateNightTracks.first().artworkUrl,
                        category = "Smart Mix",
                        sourcePlatform = "Echoic Mix",
                        trackCount = lateNightTracks.size,
                        preloadedTracks = lateNightTracks
                    )
                )
            }
        }
        _smartAutoPlaylists.value = smartMixes
    }

    /**
     * Plays all songs from a standalone playlist immediately (either in order or shuffled).
     */
    fun playPlaylist(playlist: PlaylistItem, shuffle: Boolean = false) {
        viewModelScope.launch {
            val currentLoaded = when {
                _selectedPlaylist.value?.id == playlist.id && _selectedPlaylistTracks.value.isNotEmpty() -> {
                    _selectedPlaylistTracks.value
                }
                playlist.preloadedTracks.isNotEmpty() -> {
                    playlist.preloadedTracks
                }
                else -> {
                    _isResolvingStream.value = true
                    runCatching {
                        repository.fetchPlaylistTracksPage(playlist, page = 1, pageSize = 15).tracks
                    }.getOrDefault(emptyList())
                }
            }

            val deduped = repository.deduplicatePlaylistTracks(currentLoaded)
            if (deduped.isEmpty()) {
                _isResolvingStream.value = false
                showStatusMessage("Loading playlist tracks...")
                return@launch
            }

            val favIds = favoriteSongs.value.map { it.id }.toSet()
            val enriched = deduped.map { it.copy(isFavorite = favIds.contains(it.id)) }
            val playQueue = if (shuffle) {
                unshuffledQueueBackup = enriched
                enriched.shuffled()
            } else {
                unshuffledQueueBackup = emptyList()
                enriched
            }
            _isShuffled.value = shuffle
            sendServiceCustomCommand(
                PlaybackService.CMD_REFRESH_SHUFFLE_ICON,
                Bundle().apply { putBoolean(PlaybackService.ARG_IS_SHUFFLED, shuffle) }
            )
            playSong(playQueue.first(), playQueue)
        }
    }

    /**
     * Imports a playlist from a pasted Spotify, YouTube Music, or JioSaavn URL and saves it to Room.
     */
    fun importPlaylistFromLink(rawUrl: String, onSuccess: () -> Unit = {}) {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty() || _isImportingPlaylist.value) return
        viewModelScope.launch {
            _isImportingPlaylist.value = true
            val result = repository.importPlaylistFromUrl(trimmed)
            _isImportingPlaylist.value = false
            result.onSuccess { imported ->
                showStatusMessage("Added \"${imported.title}\"")
                onSuccess()
            }.onFailure { err ->
                showStatusMessage(err.message ?: "Could not import playlist link")
            }
        }
    }

    fun removeImportedPlaylist(playlistId: String) {
        viewModelScope.launch {
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = null
            }
            repository.deleteImportedPlaylist(playlistId)
            showStatusMessage("Removed from Your Playlists")
        }
    }

    /**
     * Toggles favorite/saved status for any searched or browsed playlist so it is saved in Library & Home.
     */
    fun toggleFavoritePlaylist(playlist: PlaylistItem) {
        viewModelScope.launch {
            val isAlreadySaved = importedPlaylists.value.any {
                it.id == playlist.id ||
                    (it.remotePlaylistId.isNotBlank() && it.remotePlaylistId == playlist.remotePlaylistId)
            }
            if (isAlreadySaved) {
                val matchingId = importedPlaylists.value.firstOrNull {
                    it.id == playlist.id ||
                        (it.remotePlaylistId.isNotBlank() && it.remotePlaylistId == playlist.remotePlaylistId)
                }?.id ?: playlist.id
                repository.deleteImportedPlaylist(matchingId)
                showStatusMessage("Removed \"${playlist.title}\" from Your Playlists")
            } else {
                val loadedTracks = if (_selectedPlaylist.value?.id == playlist.id) {
                    _selectedPlaylistTracks.value
                } else {
                    emptyList()
                }
                showStatusMessage("Saving \"${playlist.title}\"...")
                runCatching {
                    repository.savePlaylistToFavorites(playlist, loadedTracks)
                }.onSuccess {
                    showStatusMessage("Saved \"${playlist.title}\" to Your Playlists")
                }.onFailure {
                    showStatusMessage("Could not save playlist")
                }
            }
        }
    }

    /**
     * Creates a new user-authored Custom Playlist and optionally adds an initial song.
     */
    fun createCustomPlaylist(
        title: String,
        description: String = "",
        initialSong: SongItem? = null
    ) {
        viewModelScope.launch {
            runCatching {
                repository.createCustomPlaylist(title, description, initialSong)
            }.onSuccess { created ->
                if (initialSong != null) {
                    showStatusMessage("Created \"${created.title}\" with \"${initialSong.title}\"")
                } else {
                    showStatusMessage("Created playlist \"${created.title}\"")
                }
            }.onFailure {
                showStatusMessage("Could not create custom playlist")
            }
        }
    }

    /**
     * Adds a song to an existing saved or custom playlist in Library.
     */
    fun addSongToPlaylist(playlistId: String, song: SongItem) {
        viewModelScope.launch {
            val updated = repository.addSongToPlaylist(playlistId, song)
            if (updated != null) {
                if (_selectedPlaylist.value?.id == playlistId) {
                    _selectedPlaylist.value = updated
                    val favIds = favoriteSongs.value.map { it.id }.toSet()
                    _selectedPlaylistTracks.value = updated.preloadedTracks.map {
                        it.copy(isFavorite = favIds.contains(it.id))
                    }
                }
                showStatusMessage("Added \"${song.title}\" to \"${updated.title}\"")
            } else {
                showStatusMessage("Could not add song to playlist")
            }
        }
    }

    /**
     * Renames a user-created custom playlist in Library.
     */
    fun renamePlaylist(playlistId: String, newTitle: String, newSubtitle: String = "") {
        viewModelScope.launch {
            val updated = repository.renamePlaylist(playlistId, newTitle, newSubtitle)
            if (updated != null) {
                if (_selectedPlaylist.value?.id == playlistId) {
                    _selectedPlaylist.value = updated
                }
                showStatusMessage("Renamed playlist to \"${updated.title}\"")
            } else {
                showStatusMessage("Could not rename playlist")
            }
        }
    }

    /**
     * Removes a song from a saved or custom playlist in Library.
     */
    fun removeTrackFromPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            val updated = repository.removeSongFromPlaylist(playlistId, songId)
            if (updated != null) {
                if (_selectedPlaylist.value?.id == playlistId) {
                    _selectedPlaylist.value = updated
                    _selectedPlaylistTracks.value = enrichTrackFlags(updated.preloadedTracks)
                }
                showStatusMessage("Removed track from \"${updated.title}\"")
            } else {
                showStatusMessage("Could not remove track")
            }
        }
    }

    /**
     * Reorders tracks inside a saved or custom playlist in Library.
     */
    fun moveTrackInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val updated = repository.reorderPlaylistTracks(playlistId, fromIndex, toIndex)
            if (updated != null && _selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = updated
                _selectedPlaylistTracks.value = enrichTrackFlags(updated.preloadedTracks)
            }
        }
    }

    /**
     * Re-fetches and syncs the currently opened playlist from its remote platform.
     */
    fun syncSelectedPlaylist() {
        val currentPl = _selectedPlaylist.value ?: return
        if (_isSyncingPlaylist.value) return
        viewModelScope.launch {
            _isSyncingPlaylist.value = true
            val result = repository.syncPlaylistFromRemote(currentPl)
            _isSyncingPlaylist.value = false
            result.onSuccess { refreshed ->
                _selectedPlaylist.value = refreshed
                val favIds = favoriteSongs.value.map { it.id }.toSet()
                _selectedPlaylistTracks.value = refreshed.preloadedTracks.map {
                    it.copy(isFavorite = favIds.contains(it.id))
                }
                _hasMorePlaylistTracks.value = false
                currentPlaylistNextPage = null
                showStatusMessage("Synced ${refreshed.preloadedTracks.size} tracks from ${refreshed.sourcePlatform}")
            }.onFailure {
                showStatusMessage("Could not sync playlist right now")
            }
        }
    }

    /**
     * Toggles offline download for a song: cancels if currently downloading, deletes if already
     * downloaded, or downloads to local storage atomically via a `.tmp` file.
     */
    fun toggleOfflineDownload(song: SongItem) {
        if (_downloadingSongIds.value.contains(song.id)) {
            cancelOfflineDownload(song.id, song.title)
            return
        }
        val isAlreadyDownloaded = offlineSongs.value.any { it.id == song.id } || song.isDownloaded
        val filesDir = getApplication<Application>().filesDir

        if (isAlreadyDownloaded) {
            viewModelScope.launch {
                repository.removeOfflineSong(filesDir, song.id)
                applyDownloadedStateToUi(song.id, false, "")
                showStatusMessage("Removed \"${song.title}\" from Offline Downloads")
            }
            return
        }

        val job = viewModelScope.launch {
            _downloadingSongIds.value = _downloadingSongIds.value + song.id
            showStatusMessage("Downloading \"${song.title}\" for offline playback...")
            try {
                val result = repository.downloadSongToOfflineStorage(filesDir, song)
                result.onSuccess { downloadedSong ->
                    applyDownloadedStateToUi(song.id, true, downloadedSong.localFilePath)
                    showStatusMessage("Downloaded \"${song.title}\" for Offline Playback")
                }.onFailure { err ->
                    if (err is CancellationException) throw err
                    showStatusMessage("Could not download \"${song.title}\"")
                }
            } catch (_: CancellationException) {
                showStatusMessage("Cancelled download for \"${song.title}\"")
            } finally {
                activeDownloadJobs.remove(song.id)
                _downloadingSongIds.value = _downloadingSongIds.value - song.id
            }
        }
        activeDownloadJobs[song.id] = job
    }

    fun cancelOfflineDownload(songId: String, title: String? = null) {
        activeDownloadJobs.remove(songId)?.cancel()
        _downloadingSongIds.value = _downloadingSongIds.value - songId
        if (!title.isNullOrBlank()) {
            showStatusMessage("Cancelled download for \"$title\"")
        }
    }

    fun cancelBatchDownload() {
        val currentBatch = _batchDownloadProgress.value
        batchDownloadJob?.cancel()
        batchDownloadJob = null
        activeDownloadJobs.values.forEach { it.cancel() }
        activeDownloadJobs.clear()
        _downloadingSongIds.value = emptySet()
        _batchDownloadProgress.value = null
        if (currentBatch != null) {
            showStatusMessage("Cancelled download of \"${currentBatch.collectionTitle}\"")
        }
    }

    fun clearAllOfflineDownloads() {
        cancelBatchDownload()
        val filesDir = getApplication<Application>().filesDir
        val downloadedIdsSnapshot = offlineSongs.value.map { it.id }
        viewModelScope.launch {
            repository.clearAllOfflineDownloads(filesDir)
            downloadedIdsSnapshot.forEach { id ->
                applyDownloadedStateToUi(id, false, "")
            }
            showStatusMessage("Cleared all offline downloads")
        }
    }

    private fun applyDownloadedStateToUi(songId: String, isNowDownloaded: Boolean, localPath: String) {
        if (_currentTrack.value?.id == songId) {
            _currentTrack.value = _currentTrack.value?.copy(
                isDownloaded = isNowDownloaded,
                localFilePath = localPath
            )
        }
        _queue.value = _queue.value.map {
            if (it.id == songId) it.copy(isDownloaded = isNowDownloaded, localFilePath = localPath) else it
        }
        _searchResults.value = _searchResults.value.map {
            if (it.id == songId) it.copy(isDownloaded = isNowDownloaded, localFilePath = localPath) else it
        }
        _lastSearchResults.value = _lastSearchResults.value.map {
            if (it.id == songId) it.copy(isDownloaded = isNowDownloaded, localFilePath = localPath) else it
        }
        _homeFeedSongs.value = _homeFeedSongs.value.map {
            if (it.id == songId) it.copy(isDownloaded = isNowDownloaded, localFilePath = localPath) else it
        }
        _selectedPlaylistTracks.value = _selectedPlaylistTracks.value.map {
            if (it.id == songId) it.copy(isDownloaded = isNowDownloaded, localFilePath = localPath) else it
        }
        _selectedArtistProfile.value = _selectedArtistProfile.value?.let { artist ->
            artist.copy(
                topSongs = artist.topSongs.map {
                    if (it.id == songId) it.copy(isDownloaded = isNowDownloaded, localFilePath = localPath) else it
                }
            )
        }
        _selectedAlbumDetail.value = _selectedAlbumDetail.value?.let { album ->
            album.copy(
                tracks = album.tracks.map {
                    if (it.id == songId) it.copy(isDownloaded = isNowDownloaded, localFilePath = localPath) else it
                }
            )
        }
    }

    /**
     * One-Tap Batch Offline Download for an entire Playlist or Liked Songs collection.
     */
    fun downloadEntireCollection(collectionTitle: String, songs: List<SongItem>) {
        if (songs.isEmpty()) {
            showStatusMessage("No songs to download in \"$collectionTitle\"")
            return
        }
        val alreadyOffIds = offlineSongs.value.map { it.id }.toSet()
        val pendingSongs = songs.filter { it.id !in alreadyOffIds && !it.isDownloaded }
        if (pendingSongs.isEmpty()) {
            showStatusMessage("All songs in \"$collectionTitle\" are already downloaded")
            return
        }
        if (batchDownloadJob?.isActive == true) {
            showStatusMessage("Batch download already in progress...")
            return
        }

        val filesDir = getApplication<Application>().filesDir
        batchDownloadJob = viewModelScope.launch {
            val total = pendingSongs.size
            var completed = 0
            _batchDownloadProgress.value = BatchDownloadProgress(
                collectionTitle = collectionTitle,
                completedCount = 0,
                totalCount = total,
                isActive = true
            )
            showStatusMessage("Downloading $total songs from \"$collectionTitle\"...")

            try {
                for (song in pendingSongs) {
                    if (!isActive) break
                    _downloadingSongIds.value = _downloadingSongIds.value + song.id
                    val result = repository.downloadSongToOfflineStorage(filesDir, song)
                    _downloadingSongIds.value = _downloadingSongIds.value - song.id
                    result.onSuccess { downloaded ->
                        completed++
                        applyDownloadedStateToUi(song.id, true, downloaded.localFilePath)
                    }
                    _batchDownloadProgress.value = BatchDownloadProgress(
                        collectionTitle = collectionTitle,
                        completedCount = completed,
                        totalCount = total,
                        isActive = true
                    )
                }
                showStatusMessage("Downloaded $completed of $total songs from \"$collectionTitle\"")
            } catch (_: CancellationException) {
                _downloadingSongIds.value = emptySet()
            } finally {
                _batchDownloadProgress.value = null
            }
        }
    }

    fun selectSearchFilter(filter: SearchFilterType) {
        _selectedSearchFilter.value = filter
    }

    private fun connectToPlaybackService() {
        runCatching {
            val context = getApplication<Application>()
            val sessionToken = SessionToken(
                context,
                ComponentName(context, PlaybackService::class.java)
            )
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    runCatching {
                        val controller = future.get()
                        mediaController = controller
                        attachControllerListener(controller)
                        syncStateFromController(controller)
                    }
                },
                MoreExecutors.directExecutor()
            )
        }
    }

    private fun attachControllerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (!isPlaying) {
                    persistPlaybackSession()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val activeSong = _currentTrack.value ?: return
                viewModelScope.launch {
                    val alternateUrl = repository.getAlternateStreamUrl(activeSong, activeSong.streamUrl)
                    if (!alternateUrl.isNullOrBlank()) {
                        val recoveredSong = activeSong.copy(streamUrl = alternateUrl)
                        _currentTrack.value = recoveredSong
                        controller.stop()
                        controller.clearMediaItems()
                        controller.setMediaItem(buildMediaItem(recoveredSong))
                        controller.prepare()
                        controller.play()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val transitioned = PlaybackService.extractSongFromMediaItem(mediaItem) ?: return
                if (_currentTrack.value?.id != transitioned.id) {
                    _currentTrack.value = transitioned
                    _progressMs.value = 0L
                    _durationMs.value = transitioned.durationMs.coerceAtLeast(30_000L)
                    fetchLyricsForTrack(transitioned)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    consecutiveStreamFailures = 0
                    val d = controller.duration
                    val activeSong = _currentTrack.value
                    if (d in 1..35_000L && activeSong != null && activeSong.durationMs > 45_000L) {
                        // A short preview clip was loaded for a full-length song; upgrade to a full stream immediately
                        viewModelScope.launch {
                            val fullUrl = repository.getAlternateStreamUrl(activeSong, activeSong.streamUrl)
                            if (!fullUrl.isNullOrBlank() && fullUrl != activeSong.streamUrl) {
                                val upgradedSong = activeSong.copy(streamUrl = fullUrl)
                                _currentTrack.value = upgradedSong
                                PlaybackService.syncQueueFromUi(_queue.value, upgradedSong)
                                controller.stop()
                                controller.clearMediaItems()
                                controller.setMediaItem(buildMediaItem(upgradedSong))
                                controller.prepare()
                                controller.play()
                            } else {
                                _durationMs.value = d
                            }
                        }
                    } else if (d > 0) {
                        _durationMs.value = d
                        val actualDurationSec = d / 1000L
                        if (activeSong != null &&
                            lyricsFetchedForTrackId == activeSong.id &&
                            kotlin.math.abs(actualDurationSec - lyricsFetchedDurationSec) >= 8L &&
                            (_currentLyrics.value?.isSynced != true || kotlin.math.abs(actualDurationSec - lyricsFetchedDurationSec) >= 15L)
                        ) {
                            fetchLyricsForTrack(activeSong, overrideDurationSec = actualDurationSec)
                        }
                    }
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = when (repeatMode) {
                    Player.REPEAT_MODE_ALL -> PlayerRepeatMode.ALL
                    Player.REPEAT_MODE_ONE -> PlayerRepeatMode.ONE
                    else -> PlayerRepeatMode.OFF
                }
            }
        })
    }

    private fun syncStateFromController(controller: MediaController) {
        // Push persisted/current EQ, Crossfade, Gapless, Repeat & Shuffle UI state to PlaybackService
        val eq = _equalizerState.value
        eq.bands.forEach { band ->
            sendServiceCustomCommand(
                PlaybackService.CMD_SET_EQ_BAND,
                Bundle().apply {
                    putShort(PlaybackService.ARG_BAND_INDEX, band.index)
                    putShort(PlaybackService.ARG_BAND_LEVEL, band.currentLevelMb)
                }
            )
        }
        sendServiceCustomCommand(
            PlaybackService.CMD_SET_BASS_BOOST,
            Bundle().apply { putInt(PlaybackService.ARG_BASS_STRENGTH, eq.bassBoostStrength) }
        )
        sendServiceCustomCommand(
            PlaybackService.CMD_SET_EQ_ENABLED,
            Bundle().apply { putBoolean(PlaybackService.ARG_EQ_ENABLED, eq.isEnabled) }
        )
        sendServiceCustomCommand(
            PlaybackService.CMD_SET_CROSSFADE_SECONDS,
            Bundle().apply { putInt(PlaybackService.ARG_CROSSFADE_SECONDS, eq.crossfadeSeconds) }
        )
        sendServiceCustomCommand(
            PlaybackService.CMD_SET_GAPLESS_PLAYBACK,
            Bundle().apply { putBoolean(PlaybackService.ARG_GAPLESS_ENABLED, eq.gaplessPlayback) }
        )
        sendServiceCustomCommand(
            PlaybackService.CMD_REFRESH_SHUFFLE_ICON,
            Bundle().apply { putBoolean(PlaybackService.ARG_IS_SHUFFLED, _isShuffled.value) }
        )
        controller.repeatMode = when (_repeatMode.value) {
            PlayerRepeatMode.OFF -> Player.REPEAT_MODE_OFF
            PlayerRepeatMode.ALL -> Player.REPEAT_MODE_ALL
            PlayerRepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }

        // Keep ExoPlayer internal shuffle OFF because Echoic reorders _queue & serviceQueue directly
        if (controller.shuffleModeEnabled) {
            controller.shuffleModeEnabled = false
        }

        // If playback is already active in PlaybackService when UI reconnects, restore live service state
        val existingServiceSong = PlaybackService.activeServiceSong
            ?: PlaybackService.extractSongFromMediaItem(controller.currentMediaItem)
        if (controller.mediaItemCount > 0 && existingServiceSong != null) {
            _isPlaying.value = controller.isPlaying
            _currentTrack.value = existingServiceSong
            if (PlaybackService.serviceQueue.isNotEmpty()) {
                _queue.value = PlaybackService.serviceQueue
            }
            _durationMs.value = controller.duration.takeIf { it > 0 }
                ?: existingServiceSong.durationMs.coerceAtLeast(30_000L)
            _progressMs.value = controller.currentPosition.coerceAtLeast(0L)
            fetchLyricsForTrack(existingServiceSong)
            return
        }

        // Restored cold-start session stays paused in MiniPlayer with saved position ready for Tap-to-Resume
        _isPlaying.value = controller.isPlaying
    }

    private fun observeDebouncedSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(140L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    val trimmed = query.trim()
                    if (trimmed.length >= 2) {
                        _searchSuggestions.value = runCatching {
                            repository.fetchSearchAutocompleteSuggestions(trimmed)
                        }.getOrDefault(emptyList())
                    } else {
                        _searchSuggestions.value = emptyList()
                    }
                }
        }
        viewModelScope.launch {
            _searchQuery
                .debounce(220L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isNotBlank()) {
                        executeSearch(query)
                    }
                }
        }
        viewModelScope.launch {
            _searchQuery
                .debounce(1000L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    val trimmed = query.trim()
                    if (trimmed.length >= 2) {
                        repository.recordSearchQuery(trimmed)
                    }
                }
        }
        viewModelScope.launch {
            recentSearches.collectLatest { history ->
                val latestSaved = history.firstOrNull()?.trim().orEmpty()
                if (latestSaved.isNotEmpty() && _lastSearchedQuery.value.isBlank() && _searchQuery.value.isBlank()) {
                    _lastSearchedQuery.value = latestSaved
                    launch {
                        val artists = runCatching {
                            repository.searchArtists(latestSaved)
                        }.getOrDefault(emptyList())
                        if (_lastSearchedQuery.value == latestSaved) {
                            _lastSearchArtistResults.value = artists
                        }
                    }
                    launch {
                        val playlists = runCatching {
                            repository.searchPlaylists(latestSaved, _homeDiscoveryData.value.allPlaylists)
                        }.getOrDefault(emptyList())
                        if (_lastSearchedQuery.value == latestSaved) {
                            _lastSearchPlaylistResults.value = playlists
                        }
                    }
                    repository.search(latestSaved).collectLatest { items ->
                        val favIds = favoriteSongs.value.map { it.id }.toSet()
                        val enriched = items.map { it.copy(isFavorite = favIds.contains(it.id)) }
                        if (_lastSearchedQuery.value == latestSaved) {
                            _lastSearchResults.value = enriched
                        }
                    }
                }
            }
        }
    }

    private fun executeSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                _searchResults.value = emptyList()
                _searchPlaylistResults.value = emptyList()
                _searchArtistResults.value = emptyList()
                _isSearching.value = false
                return@launch
            }
            _isSearching.value = true
            _lastSearchedQuery.value = trimmed
            _searchResults.value = emptyList()
            _searchArtistResults.value = emptyList()
            // Immediately surface matching local/curated playlists while live playlist search runs
            val localMatches = repository.filterMatchingLocalPlaylists(
                trimmed,
                _homeDiscoveryData.value.allPlaylists
            )
            _searchPlaylistResults.value = localMatches
            if (localMatches.isNotEmpty()) {
                _lastSearchPlaylistResults.value = localMatches
            }

            launch {
                val artists = runCatching {
                    repository.searchArtists(trimmed)
                }.getOrDefault(emptyList())
                _searchArtistResults.value = artists
                _lastSearchArtistResults.value = artists
            }

            launch {
                val playlists = runCatching {
                    repository.searchPlaylists(trimmed, _homeDiscoveryData.value.allPlaylists)
                }.getOrDefault(_searchPlaylistResults.value)
                _searchPlaylistResults.value = playlists
                _lastSearchPlaylistResults.value = playlists
            }

            repository.search(trimmed).collectLatest { items ->
                val favIds = favoriteSongs.value.map { it.id }.toSet()
                val enriched = items.map { it.copy(isFavorite = favIds.contains(it.id)) }
                _searchResults.value = enriched
                _lastSearchResults.value = enriched
                _isSearching.value = false
            }
        }
    }

    private fun startSmooth100msTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            var sessionSaveAccumMs = 0L
            while (isActive) {
                val expanded = _isPlayerExpanded.value
                val tickIntervalMs = if (expanded) 80L else 250L
                if (_isPlaying.value && !_isResolvingStream.value) {
                    val controller = mediaController
                    val currentPos = if (controller != null && controller.isConnected && controller.mediaItemCount > 0) {
                        controller.currentPosition.coerceAtLeast(0L)
                    } else {
                        val step = (tickIntervalMs * _playbackSpeed.value).toLong()
                        (_progressMs.value + step).coerceAtMost(_durationMs.value.coerceAtLeast(180_000L))
                    }
                    if (_progressMs.value != currentPos) {
                        _progressMs.value = currentPos
                    }

                    val totalDur = controller?.duration?.takeIf { it > 0L } ?: _durationMs.value
                    if (totalDur > 0L && _durationMs.value != totalDur) {
                        _durationMs.value = totalDur
                    }

                    val timer = _sleepTimerState.value
                    if (timer.isActive && timer.stopAfterCurrentTrack && totalDur > 12_000L) {
                        val remainingMs = totalDur - currentPos
                        if (remainingMs in 1..10_000L && !timer.isFadingOut) {
                            _sleepTimerState.value = timer.copy(isFadingOut = true)
                            sendServiceCustomCommand(PlaybackService.CMD_START_FADE_OUT)
                        }
                    }

                    // Dual-player Crossfade and Gapless playback run natively inside PlaybackService;
                    // UI keeps Endless Auto-Queue topped up and periodically checkpoints the session.
                    if (_currentTrack.value != null) {
                        maybeReplenishEndlessQueue()
                        sessionSaveAccumMs += tickIntervalMs
                        if (sessionSaveAccumMs >= 4_000L) {
                            sessionSaveAccumMs = 0L
                            persistPlaybackSession()
                        }
                    }

                    updateSyncedLyricIndex(currentPos)
                }
                delay(tickIntervalMs)
            }
        }
    }

    private fun updateSyncedLyricIndex(positionMs: Long) {
        val lyrics = _currentLyrics.value ?: return
        if (lyrics.lines.isEmpty() || !lyrics.isSynced) {
            _currentLyricIndex.value = -1
            return
        }
        val effectivePos = positionMs.coerceAtLeast(0L)
        val activeIdx = lyrics.lines.indexOfLast { effectivePos >= it.startTimeMs }
        if (_currentLyricIndex.value != activeIdx) {
            _currentLyricIndex.value = activeIdx
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        if (newQuery.isNotBlank()) {
            _selectedMoodFilter.value = null
        } else {
            executeSearch("")
        }
    }

    fun submitSearchQuery(query: String) {
        val trimmed = query.trim()
        val alreadyShowingQuery = trimmed.isNotEmpty() &&
            _lastSearchedQuery.value.equals(trimmed, ignoreCase = true) &&
            (_searchResults.value.isNotEmpty() || _isSearching.value)
        if (_searchQuery.value != trimmed) {
            _searchQuery.value = trimmed
        }
        if (trimmed.isNotEmpty()) {
            _selectedMoodFilter.value = null
            viewModelScope.launch {
                repository.recordSearchQuery(trimmed)
            }
        }
        if (!alreadyShowingQuery) {
            executeSearch(trimmed)
        }
    }

    fun saveSearchQueryToHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            viewModelScope.launch {
                repository.recordSearchQuery(trimmed)
            }
        }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            repository.removeSearchQuery(query)
            if (_lastSearchedQuery.value.equals(query, ignoreCase = true)) {
                _lastSearchedQuery.value = ""
                _lastSearchResults.value = emptyList()
                _lastSearchPlaylistResults.value = emptyList()
                _lastSearchArtistResults.value = emptyList()
            }
        }
    }

    fun clearAllRecentSearches() {
        viewModelScope.launch {
            repository.clearSearchHistory()
            _lastSearchedQuery.value = ""
            _lastSearchResults.value = emptyList()
            _lastSearchPlaylistResults.value = emptyList()
            _lastSearchArtistResults.value = emptyList()
            showStatusMessage("Cleared recent searches")
        }
    }

    fun clearRecentlyPlayedSongs() {
        viewModelScope.launch {
            repository.clearRecentlyPlayedSongs()
            showStatusMessage("Cleared Recently Played history")
        }
    }

    fun selectMoodCategory(moodTitle: String?) {
        val nextMood = if (moodTitle == null || _selectedMoodFilter.value == moodTitle) null else moodTitle
        _selectedMoodFilter.value = nextMood
        _searchQuery.value = ""
        if (nextMood == null) {
            _searchResults.value = _homeFeedSongs.value
        } else {
            when {
                nextMood in setOf("India Hits", "Bollywood", "Punjabi & Hip-Hop", "South & Sufi") -> {
                    loadLazyHomeShelf(LazyPlatformShelfKey.INDIAN_GENRES)
                }
                nextMood in setOf("Moods", "New Releases") -> {
                    loadLazyHomeShelf(LazyPlatformShelfKey.GLOBAL_MOODS)
                }
                nextMood.equals("Genres", ignoreCase = true) -> {
                    loadLazyHomeShelf(LazyPlatformShelfKey.GLOBAL_GENRES)
                }
                nextMood in setOf("Top Hits", "Viral") -> {
                    loadLazyHomeShelf(LazyPlatformShelfKey.GLOBAL_TOP_EXPANDED)
                }
            }
            val matchedCategory = moodCategories.firstOrNull {
                it.title.equals(nextMood, ignoreCase = true)
            }
            val queryKeyword = matchedCategory?.queryKeyword ?: nextMood
            executeSearch(queryKeyword)
        }
    }

    /**
     * Resolves and plays the exact clicked song immediately.
     * - When [customQueue] is provided (e.g. Playlists, Top Charts, Liked Songs, Offline Downloads),
     *   preserves that collection's tracklist.
     * - When [customQueue] is empty (e.g. Search or Recently Played), starts with [song] and immediately
     *   hydrates the Up Next queue with a Smart Artist & Genre Radio mix.
     */
    private fun syncAutoQueueMetadataToService() {
        PlaybackService.syncTasteAndHistoryForAutoQueue(
            enabled = _equalizerState.value.autoQueueRadio,
            tasteGenres = _selectedTasteGenres.value,
            tasteArtists = _selectedTasteArtistNames.value,
            excludedSignatures = sessionAutoQueuedSignatures.toSet()
        )
    }

    private fun enrichTrackFlags(songs: List<SongItem>): List<SongItem> {
        val favIds = favoriteSongs.value.map { it.id }.toSet()
        val offIds = offlineSongs.value.map { it.id }.toSet()
        return songs.map {
            it.copy(
                isFavorite = favIds.contains(it.id),
                isDownloaded = offIds.contains(it.id)
            )
        }
    }

    private fun appendAutoQueuedTracks(newTracks: List<SongItem>) {
        if (newTracks.isEmpty()) return
        val existingIds = _queue.value.map { it.id }.toSet()
        val existingSigs = _queue.value.map { repository.songDeduplicationSignature(it) }.toSet()
        val uniqueNew = enrichTrackFlags(newTracks).filter { candidate ->
            candidate.id !in existingIds &&
                repository.songDeduplicationSignature(candidate) !in existingSigs
        }
        if (uniqueNew.isEmpty()) return

        uniqueNew.forEach { song ->
            sessionAutoQueuedIds.add(song.id)
            sessionAutoQueuedSignatures.add(repository.songDeduplicationSignature(song))
        }
        if (sessionAutoQueuedIds.size > 350) {
            val trimCount = sessionAutoQueuedIds.size - 300
            repeat(trimCount) {
                sessionAutoQueuedIds.firstOrNull()?.let { sessionAutoQueuedIds.remove(it) }
            }
        }
        _autoQueuedSongIds.value = _autoQueuedSongIds.value + uniqueNew.map { it.id }
        val updatedQueue = (_queue.value + uniqueNew).distinctBy { it.id }
        _queue.value = updatedQueue
        PlaybackService.syncQueueFromUi(updatedQueue, _currentTrack.value)
        syncAutoQueueMetadataToService()
    }

    /**
     * Keeps the queue endless: whenever Autoplay is enabled, Repeat is OFF, and fewer than 5 tracks
     * remain after the currently playing song, fetches a fresh batch seeded by recent tracks & Music Taste.
     */
    private fun maybeReplenishEndlessQueue(forceImmediate: Boolean = false) {
        if (_repeatMode.value != PlayerRepeatMode.OFF && !forceImmediate) return
        val currentQueue = _queue.value
        val activeSong = _currentTrack.value ?: return
        val currentIdx = currentQueue.indexOfFirst { it.id == activeSong.id }
        val remainingAhead = if (currentIdx >= 0) (currentQueue.lastIndex - currentIdx) else currentQueue.lastIndex
        if (!forceImmediate && remainingAhead >= 5) return
        if (autoRadioJob?.isActive == true) return

        val anchorTail = currentQueue.lastOrNull() ?: activeSong
        val evolvingSeeds = (currentQueue.takeLast(3).reversed() + recentlyPlayedSongs.value.take(3))
            .distinctBy { it.id }
            .filter { it.id != anchorTail.id }
            .take(2)
        val excludeIds = currentQueue.map { it.id }.toSet() + sessionAutoQueuedIds
        val excludeSignatures = sessionAutoQueuedSignatures +
            currentQueue.map { repository.songDeduplicationSignature(it) }

        autoRadioJob = viewModelScope.launch {
            _isRefillingAutoQueue.value = true
            try {
                val radioSongs = repository.fetchSimilarRadioTracks(
                    seedSong = anchorTail,
                    excludeIds = excludeIds,
                    excludeSignatures = excludeSignatures,
                    evolvingSeedSongs = evolvingSeeds,
                    tasteGenres = _selectedTasteGenres.value,
                    tasteArtists = _selectedTasteArtistNames.value
                )
                if (radioSongs.isNotEmpty()) {
                    appendAutoQueuedTracks(radioSongs)
                }
            } finally {
                _isRefillingAutoQueue.value = false
            }
        }
    }

    fun playSong(
        song: SongItem,
        customQueue: List<SongItem> = emptyList(),
        startPositionMs: Long = 0L,
        recordHistory: Boolean = true,
        isAutoRecovery: Boolean = false
    ) {
        val requestSeq = playRequestSeq.incrementAndGet()
        playSongJob?.cancel()
        if (!isAutoRecovery) {
            consecutiveStreamFailures = 0
        }

        val previousSong = _currentTrack.value
        if (recordHistory && previousSong != null && previousSong.id != song.id) {
            if (playHistoryStack.lastOrNull()?.id != previousSong.id) {
                playHistoryStack.addLast(previousSong)
                while (playHistoryStack.size > 50) {
                    playHistoryStack.removeFirst()
                }
            }
        }

        // Immediately silence old track and cancel any in-flight background skip resolution
        mediaController?.let { controller ->
            if (controller.isConnected) {
                controller.sendCustomCommand(
                    SessionCommand(PlaybackService.CMD_CANCEL_PENDING_LOAD, Bundle.EMPTY),
                    Bundle().apply { putBoolean(PlaybackService.ARG_STOP_PLAYER, true) }
                )
                controller.pause()
                controller.stop()
                controller.clearMediaItems()
            }
        }

        val existingQueue = _queue.value
        val isNavigatingExistingQueue = customQueue.isNotEmpty() &&
            customQueue.size == existingQueue.size &&
            customQueue.zip(existingQueue).all { (a, b) -> a.id == b.id }

        val effectiveQueue = if (customQueue.isNotEmpty()) {
            if (customQueue.any { it.id == song.id }) customQueue else listOf(song) + customQueue
        } else {
            listOf(song)
        }

        if (!isNavigatingExistingQueue) {
            autoRadioJob?.cancel()
            _isRefillingAutoQueue.value = false
            _autoQueuedSongIds.value = emptySet()
            if (!_isShuffled.value) {
                unshuffledQueueBackup = emptyList()
            }
            if (sessionAutoQueuedSignatures.size > 250) {
                sessionAutoQueuedSignatures.clear()
                sessionAutoQueuedIds.clear()
            }
        } else {
            // Once a track becomes actively played, consider it part of the active history/explicit head
            _autoQueuedSongIds.value = _autoQueuedSongIds.value - song.id
        }

        sessionAutoQueuedIds.add(song.id)
        sessionAutoQueuedSignatures.add(repository.songDeduplicationSignature(song))
        effectiveQueue.forEach { sessionAutoQueuedSignatures.add(repository.songDeduplicationSignature(it)) }

        _queue.value = effectiveQueue
        _currentTrack.value = song
        _progressMs.value = startPositionMs.coerceAtLeast(0L)
        _durationMs.value = song.durationMs.coerceAtLeast(30_000L)
        _isPlaying.value = true
        _isResolvingStream.value = true
        PlaybackService.syncQueueFromUi(effectiveQueue, song)
        syncAutoQueueMetadataToService()
        persistPlaybackSession()

        fetchLyricsForTrack(song)
        recordSongPlayForStats(song)
        viewModelScope.launch {
            repository.recordRecentlyPlayed(song)
        }

        // Proactively hydrate endless queue if < 5 tracks remain ahead (or customQueue is empty)
        maybeReplenishEndlessQueue()

        playSongJob = viewModelScope.launch {
            val localOfflineUri = repository.getOfflineLocalFileUri(song.id)
            val resolvedStreamUrl = when {
                !localOfflineUri.isNullOrBlank() -> localOfflineUri
                repository.isFullLengthStreamUrl(song.streamUrl) -> song.streamUrl
                else -> repository.getStreamUrl(
                    videoId = song.id,
                    trackTitle = song.title,
                    artistName = song.artist
                ).getOrDefault("")
            }

            // Guard against stale async completion after another rapid skip/selection
            if (requestSeq != playRequestSeq.get() || _currentTrack.value?.id != song.id) {
                return@launch
            }

            _isResolvingStream.value = false

            if (resolvedStreamUrl.isBlank()) {
                _isPlaying.value = false
                consecutiveStreamFailures++
                if (consecutiveStreamFailures < 3 && _queue.value.size > 1) {
                    showStatusMessage("Unavailable: \"${song.title}\" — skipping next")
                    skipNext(isAutoRecovery = true)
                } else {
                    showStatusMessage("Stream unavailable for \"${song.title}\"")
                }
                return@launch
            }

            consecutiveStreamFailures = 0
            val updatedSong = song.copy(
                streamUrl = resolvedStreamUrl,
                isDownloaded = !localOfflineUri.isNullOrBlank() || song.isDownloaded
            )
            _currentTrack.value = updatedSong
            _queue.value = _queue.value.map {
                if (it.id == updatedSong.id) updatedSong else it
            }
            PlaybackService.syncQueueFromUi(_queue.value, updatedSong)
            persistPlaybackSession()
            sendServiceCustomCommand(
                PlaybackService.CMD_REFRESH_FAVORITE_ICON,
                Bundle().apply { putBoolean(PlaybackService.ARG_IS_FAVORITE, updatedSong.isFavorite) }
            )

            val controller = mediaController
            if (controller != null && controller.isConnected && requestSeq == playRequestSeq.get()) {
                controller.stop()
                controller.clearMediaItems()
                controller.setMediaItem(buildMediaItem(updatedSong))
                if (startPositionMs > 0L) {
                    controller.seekTo(startPositionMs)
                }
                controller.prepare()
                controller.play()
            }
        }
    }

    fun addToQueue(song: SongItem) {
        val currentList = _queue.value.toMutableList()
        if (currentList.isEmpty()) {
            playSong(song, listOf(song))
            return
        }
        val activeId = _currentTrack.value?.id
        if (activeId == song.id) {
            showStatusMessage("\"${song.title}\" is already playing")
            return
        }

        val autoIds = _autoQueuedSongIds.value
        val existingIdx = currentList.indexOfFirst { it.id == song.id }
        if (existingIdx >= 0 && existingIdx !in currentList.indices.filter { currentList[it].id in autoIds }) {
            showStatusMessage("Already in queue")
            return
        }

        // Remove if it was sitting in the Auto-Queued tail and promote it to explicit Up Next
        if (existingIdx >= 0) {
            currentList.removeAt(existingIdx)
        }
        _autoQueuedSongIds.value = autoIds - song.id

        val activeIndex = currentList.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        val firstAutoAfterActive = (activeIndex + 1 until currentList.size)
            .firstOrNull { currentList[it].id in autoIds }
        val insertIndex = firstAutoAfterActive ?: currentList.size

        currentList.add(insertIndex, song)
        _queue.value = currentList
        PlaybackService.syncQueueFromUi(currentList, _currentTrack.value)
        persistPlaybackSession()
        showStatusMessage("Added to queue")
    }

    /**
     * Slots [song] immediately after the currently playing track so it plays next.
     */
    fun playNext(song: SongItem) {
        val currentList = _queue.value.toMutableList()
        val activeSong = _currentTrack.value
        if (currentList.isEmpty() || activeSong == null) {
            playSong(song, listOf(song))
            return
        }
        if (activeSong.id == song.id) {
            showStatusMessage("\"${song.title}\" is already playing")
            return
        }
        currentList.removeAll { it.id == song.id }
        _autoQueuedSongIds.value = _autoQueuedSongIds.value - song.id
        val activeIndex = currentList.indexOfFirst { it.id == activeSong.id }.coerceAtLeast(0)
        val insertIndex = (activeIndex + 1).coerceAtMost(currentList.size)
        currentList.add(insertIndex, song)
        _queue.value = currentList
        PlaybackService.syncQueueFromUi(currentList, activeSong)
        persistPlaybackSession()
        showStatusMessage("Playing \"${song.title}\" next")
    }

    /**
     * Replaces the Up Next queue with a fresh Song Radio station seeded by [song] and begins playing immediately.
     */
    fun startSongRadio(song: SongItem) {
        val currentActive = _currentTrack.value
        if (currentActive != null && currentActive.id == song.id && repository.isFullLengthStreamUrl(currentActive.streamUrl)) {
            autoRadioJob?.cancel()
            _isRefillingAutoQueue.value = false
            _autoQueuedSongIds.value = emptySet()
            _queue.value = listOf(currentActive)
            PlaybackService.syncQueueFromUi(_queue.value, currentActive)
            if (!_isPlaying.value) {
                togglePlayPause()
            }
            showStatusMessage("Started Radio for \"${song.title}\"")
            maybeReplenishEndlessQueue(forceImmediate = true)
        } else {
            showStatusMessage("Starting Radio for \"${song.title}\"")
            playSong(song, customQueue = emptyList())
        }
    }

    /**
     * Saves the active Up Next queue into the user's Library as a custom playlist.
     */
    fun saveCurrentQueueAsPlaylist(title: String) {
        val snapshot = _queue.value
        if (snapshot.isEmpty()) {
            showStatusMessage("Queue is empty")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.createCustomPlaylistWithTracks(
                    title = title,
                    description = "Saved Queue • ${snapshot.size} tracks",
                    tracks = snapshot
                )
            }.onSuccess { created ->
                showStatusMessage("Saved ${snapshot.size} tracks to \"${created.title}\"")
            }.onFailure {
                showStatusMessage("Could not save queue playlist")
            }
        }
    }

    fun clearUpcomingQueue() {
        val activeSong = _currentTrack.value ?: return
        autoRadioJob?.cancel()
        _isRefillingAutoQueue.value = false
        _autoQueuedSongIds.value = emptySet()
        unshuffledQueueBackup = emptyList()
        val singleQueue = listOf(activeSong)
        _queue.value = singleQueue
        PlaybackService.syncQueueFromUi(singleQueue, activeSong)
        persistPlaybackSession()
        showStatusMessage("Cleared upcoming • Generating fresh queue")
        maybeReplenishEndlessQueue()
    }

    private fun buildMediaItem(song: SongItem): MediaItem {
        return PlaybackService.buildMediaItemForSong(song)
    }

    private fun fetchLyricsForTrack(song: SongItem, overrideDurationSec: Long? = null) {
        lyricsFetchJob?.cancel()
        val isDurationRefetch = overrideDurationSec != null && lyricsFetchedForTrackId == song.id
        lyricsFetchedForTrackId = song.id
        val durationSec = (overrideDurationSec ?: (song.durationMs / 1000L)).coerceAtLeast(30L)
        lyricsFetchedDurationSec = durationSec

        if (!isDurationRefetch) {
            _isLyricsLoading.value = true
            _currentLyrics.value = null
            _currentLyricIndex.value = -1
        }
        lyricsFetchJob = viewModelScope.launch {
            val result = repository.getSyncedLyrics(
                trackTitle = song.title,
                artist = song.artist,
                durationSec = durationSec,
                songId = song.id
            )
            // Never overwrite lyrics if user has already skipped to another track
            if (_currentTrack.value?.id != song.id) {
                return@launch
            }
            val loaded = result.getOrNull()
            if (!isDurationRefetch || (loaded != null && loaded.lines.isNotEmpty())) {
                _currentLyrics.value = loaded
                _isLyricsLoading.value = false
                updateSyncedLyricIndex(_progressMs.value)
            }
        }
    }

    fun togglePlayPause() {
        val controller = mediaController
        val activeSong = _currentTrack.value
        if (activeSong == null) {
            val first = _homeFeedSongs.value.firstOrNull() ?: _searchResults.value.firstOrNull() ?: return
            playSong(first, _homeFeedSongs.value.ifEmpty { _searchResults.value })
            return
        }
        val nextPlaying = !_isPlaying.value
        if (nextPlaying && (controller == null || !controller.isConnected || controller.mediaItemCount == 0)) {
            playSong(
                song = activeSong,
                customQueue = _queue.value.ifEmpty { listOf(activeSong) },
                startPositionMs = _progressMs.value,
                recordHistory = false
            )
            return
        }
        _isPlaying.value = nextPlaying
        if (controller != null && controller.isConnected) {
            if (nextPlaying) {
                controller.play()
            } else {
                controller.pause()
                persistPlaybackSession()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(1L))
        _progressMs.value = clamped
        updateSyncedLyricIndex(clamped)
        mediaController?.seekTo(clamped)
        persistPlaybackSession()
    }

    fun skipNext(isAutoRecovery: Boolean = false) {
        val currentList = _queue.value
        if (currentList.isEmpty()) return
        val currentId = _currentTrack.value?.id
        val currentIndex = currentList.indexOfFirst { it.id == currentId }

        if (currentIndex >= currentList.lastIndex) {
            if (_repeatMode.value == PlayerRepeatMode.OFF) {
                val seed = _currentTrack.value ?: currentList.lastOrNull() ?: return
                val requestSeq = playRequestSeq.incrementAndGet()
                playSongJob?.cancel()
                mediaController?.let { controller ->
                    if (controller.isConnected) {
                        controller.sendCustomCommand(
                            SessionCommand(PlaybackService.CMD_CANCEL_PENDING_LOAD, Bundle.EMPTY),
                            Bundle().apply { putBoolean(PlaybackService.ARG_STOP_PLAYER, true) }
                        )
                        controller.pause()
                        controller.stop()
                        controller.clearMediaItems()
                    }
                }
                _progressMs.value = 0L
                _isResolvingStream.value = true

                val evolvingSeeds = (currentList.takeLast(3).reversed() + recentlyPlayedSongs.value.take(3))
                    .distinctBy { it.id }
                    .filter { it.id != seed.id }
                    .take(2)
                val excludeIds = currentList.map { it.id }.toSet() + sessionAutoQueuedIds
                val excludeSignatures = sessionAutoQueuedSignatures +
                    currentList.map { repository.songDeduplicationSignature(it) }

                autoRadioJob?.cancel()
                autoRadioJob = viewModelScope.launch {
                    _isRefillingAutoQueue.value = true
                    showStatusMessage("Finding more similar tracks...")
                    val fetched = try {
                        repository.fetchSimilarRadioTracks(
                            seedSong = seed,
                            excludeIds = excludeIds,
                            excludeSignatures = excludeSignatures,
                            evolvingSeedSongs = evolvingSeeds,
                            tasteGenres = _selectedTasteGenres.value,
                            tasteArtists = _selectedTasteArtistNames.value
                        )
                    } finally {
                        _isRefillingAutoQueue.value = false
                    }
                    if (requestSeq != playRequestSeq.get()) {
                        return@launch
                    }
                    if (fetched.isNotEmpty()) {
                        appendAutoQueuedTracks(fetched)
                        val updated = _queue.value
                        val target = updated.firstOrNull { it.id == fetched.first().id } ?: updated.lastOrNull()
                        if (target != null) {
                            playSong(target, updated, isAutoRecovery = isAutoRecovery)
                        }
                    } else {
                        playSong(currentList.first(), currentList, isAutoRecovery = isAutoRecovery)
                    }
                }
                return
            }
        }

        val nextIndex = (currentIndex + 1).coerceAtLeast(0) % currentList.size
        playSong(currentList[nextIndex], currentList, isAutoRecovery = isAutoRecovery)
    }

    fun skipPrevious() {
        if (_progressMs.value > 4_000L) {
            seekTo(0L)
            return
        }
        val currentList = _queue.value
        val currentId = _currentTrack.value?.id

        var previousFromHistory: SongItem? = null
        while (playHistoryStack.isNotEmpty()) {
            val candidate = playHistoryStack.removeLast()
            if (candidate.id != currentId) {
                previousFromHistory = candidate
                break
            }
        }
        if (previousFromHistory != null) {
            val targetQueue = if (currentList.any { it.id == previousFromHistory.id }) {
                currentList
            } else {
                listOf(previousFromHistory) + currentList
            }
            playSong(previousFromHistory, targetQueue, recordHistory = false)
            return
        }

        if (currentList.isEmpty()) return
        val currentIndex = currentList.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val prevIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else currentList.lastIndex
        playSong(currentList[prevIndex], currentList, recordHistory = false)
    }

    fun toggleShuffle(forceValue: Boolean? = null) {
        val nextShuffle = forceValue ?: !_isShuffled.value
        if (forceValue != null && nextShuffle == _isShuffled.value) {
            return
        }
        _isShuffled.value = nextShuffle

        val currentList = _queue.value
        val activeSong = _currentTrack.value
        val autoIds = _autoQueuedSongIds.value

        if (nextShuffle) {
            if (currentList.isNotEmpty() && unshuffledQueueBackup.isEmpty()) {
                unshuffledQueueBackup = currentList
            }
            if (currentList.size > 1) {
                val reordered = if (activeSong != null) {
                    val others = currentList.filter { it.id != activeSong.id }
                    val explicitUpcoming = others.filter { it.id !in autoIds }.shuffled()
                    val autoUpcoming = others.filter { it.id in autoIds }.shuffled()
                    listOf(activeSong) + explicitUpcoming + autoUpcoming
                } else {
                    currentList.shuffled()
                }
                _queue.value = reordered
                PlaybackService.syncQueueFromUi(reordered, activeSong)
            }
        } else {
            if (unshuffledQueueBackup.isNotEmpty()) {
                val currentById = currentList.associateBy { it.id }
                val restoredCore = unshuffledQueueBackup.mapNotNull { currentById[it.id] ?: it.takeIf { s -> s.id == activeSong?.id } }
                val restoredIds = restoredCore.map { it.id }.toSet()
                val appendedExtras = currentList.filter { it.id !in restoredIds }
                val restoredQueue = (restoredCore + appendedExtras).distinctBy { it.id }
                _queue.value = restoredQueue
                PlaybackService.syncQueueFromUi(restoredQueue, activeSong)
                unshuffledQueueBackup = emptyList()
            }
        }

        mediaController?.shuffleModeEnabled = false
        sendServiceCustomCommand(
            PlaybackService.CMD_REFRESH_SHUFFLE_ICON,
            Bundle().apply { putBoolean(PlaybackService.ARG_IS_SHUFFLED, nextShuffle) }
        )
        persistPlaybackSession()
    }

    fun cycleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            PlayerRepeatMode.OFF -> PlayerRepeatMode.ALL
            PlayerRepeatMode.ALL -> PlayerRepeatMode.ONE
            PlayerRepeatMode.ONE -> PlayerRepeatMode.OFF
        }
        _repeatMode.value = nextMode
        mediaController?.repeatMode = when (nextMode) {
            PlayerRepeatMode.OFF -> Player.REPEAT_MODE_OFF
            PlayerRepeatMode.ALL -> Player.REPEAT_MODE_ALL
            PlayerRepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        persistPlaybackSession()
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val currentList = _queue.value.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices || fromIndex == toIndex) {
            return
        }
        val movedItem = currentList.removeAt(fromIndex)
        currentList.add(toIndex, movedItem)

        // If user drags an Auto-Queued song up above the remaining Auto-Queued boundary, promote it to explicit queue
        val autoIds = _autoQueuedSongIds.value.toMutableSet()
        if (movedItem.id in autoIds) {
            val activeIdx = currentList.indexOfFirst { it.id == _currentTrack.value?.id }.coerceAtLeast(0)
            val hasExplicitAfter = (toIndex + 1 until currentList.size).any {
                it > activeIdx && currentList[it].id !in autoIds
            }
            if (hasExplicitAfter || toIndex <= activeIdx) {
                autoIds.remove(movedItem.id)
                _autoQueuedSongIds.value = autoIds
            }
        }

        _queue.value = currentList
        PlaybackService.syncQueueFromUi(currentList, _currentTrack.value)
        persistPlaybackSession()
    }

    fun removeFromQueue(index: Int) {
        val currentList = _queue.value.toMutableList()
        if (index !in currentList.indices) return
        val removed = currentList.removeAt(index)
        _autoQueuedSongIds.value = _autoQueuedSongIds.value - removed.id
        _queue.value = currentList
        PlaybackService.syncQueueFromUi(currentList, _currentTrack.value)

        if (removed.id == _currentTrack.value?.id) {
            if (currentList.isNotEmpty()) {
                val nextIdx = index.coerceAtMost(currentList.lastIndex)
                playSong(currentList[nextIdx], currentList)
            } else {
                _currentTrack.value = null
                _isPlaying.value = false
                PlaybackService.syncQueueFromUi(emptyList(), null)
                PlaybackSessionStore.clearSession(getApplication())
                mediaController?.stop()
            }
        } else {
            persistPlaybackSession()
            maybeReplenishEndlessQueue()
        }
    }

    fun startSleepTimer(minutes: Int, stopAfterCurrent: Boolean = false) {
        sleepTimerJob?.cancel()
        sendServiceCustomCommand(PlaybackService.CMD_CANCEL_FADE_OUT)
        PlaybackService.stopAfterCurrentTrackFlag = stopAfterCurrent
        sendServiceCustomCommand(
            PlaybackService.CMD_SET_STOP_AFTER_CURRENT,
            Bundle().apply { putBoolean(PlaybackService.ARG_STOP_AFTER_CURRENT, stopAfterCurrent) }
        )

        if (stopAfterCurrent) {
            val remainingTrackSec = ((_durationMs.value - _progressMs.value).coerceAtLeast(0L)) / 1000L
            _sleepTimerState.value = SleepTimerState(
                isActive = true,
                remainingSeconds = remainingTrackSec,
                stopAfterCurrentTrack = true,
                totalDurationSeconds = remainingTrackSec,
                isFadingOut = false
            )
            return
        }

        val totalSeconds = (minutes.coerceAtLeast(1) * 60L)
        _sleepTimerState.value = SleepTimerState(
            isActive = true,
            remainingSeconds = totalSeconds,
            stopAfterCurrentTrack = false,
            totalDurationSeconds = totalSeconds,
            isFadingOut = false
        )

        sleepTimerJob = viewModelScope.launch {
            var secondsLeft = totalSeconds
            while (secondsLeft > 0 && isActive) {
                delay(1_000L)
                secondsLeft--
                val isFading = secondsLeft <= 10L
                if (secondsLeft == 10L) {
                    sendServiceCustomCommand(PlaybackService.CMD_START_FADE_OUT)
                }
                _sleepTimerState.value = _sleepTimerState.value.copy(
                    remainingSeconds = secondsLeft,
                    isFadingOut = isFading
                )
            }
            if (secondsLeft <= 0L) {
                _isPlaying.value = false
                mediaController?.pause()
                _sleepTimerState.value = SleepTimerState()
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        PlaybackService.stopAfterCurrentTrackFlag = false
        sendServiceCustomCommand(
            PlaybackService.CMD_SET_STOP_AFTER_CURRENT,
            Bundle().apply { putBoolean(PlaybackService.ARG_STOP_AFTER_CURRENT, false) }
        )
        sendServiceCustomCommand(PlaybackService.CMD_CANCEL_FADE_OUT)
        _sleepTimerState.value = SleepTimerState()
    }

    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.75f, 1.5f)
        _playbackSpeed.value = clampedSpeed
        if (_equalizerState.value.isEnabled) {
            val pitch = _playbackPitch.value
            mediaController?.playbackParameters = PlaybackParameters(clampedSpeed, pitch)
        }
    }

    fun setPlaybackPitch(pitch: Float) {
        val clampedPitch = pitch.coerceIn(0.75f, 1.5f)
        _playbackPitch.value = clampedPitch
        if (_equalizerState.value.isEnabled) {
            val speed = _playbackSpeed.value
            mediaController?.playbackParameters = PlaybackParameters(speed, clampedPitch)
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _equalizerState.value = _equalizerState.value.copy(isEnabled = enabled)
        if (enabled) {
            _equalizerState.value.bands.forEach { band ->
                sendServiceCustomCommand(
                    PlaybackService.CMD_SET_EQ_BAND,
                    Bundle().apply {
                        putShort(PlaybackService.ARG_BAND_INDEX, band.index)
                        putShort(PlaybackService.ARG_BAND_LEVEL, band.currentLevelMb)
                    }
                )
            }
            sendServiceCustomCommand(
                PlaybackService.CMD_SET_BASS_BOOST,
                Bundle().apply { putInt(PlaybackService.ARG_BASS_STRENGTH, _equalizerState.value.bassBoostStrength) }
            )
            sendServiceCustomCommand(
                PlaybackService.CMD_SET_EQ_ENABLED,
                Bundle().apply { putBoolean(PlaybackService.ARG_EQ_ENABLED, true) }
            )
            mediaController?.playbackParameters = PlaybackParameters(_playbackSpeed.value, _playbackPitch.value)
        } else {
            mediaController?.playbackParameters = PlaybackParameters(1.0f, 1.0f)
            sendServiceCustomCommand(
                PlaybackService.CMD_SET_EQ_ENABLED,
                Bundle().apply { putBoolean(PlaybackService.ARG_EQ_ENABLED, false) }
            )
        }
        persistAudioConsoleSettings()
    }

    fun setEqualizerBandLevel(bandIndex: Short, levelMb: Short) {
        val clamped = levelMb.coerceIn(-1500, 1500)
        val updatedBands = _equalizerState.value.bands.map { band ->
            if (band.index == bandIndex) band.copy(currentLevelMb = clamped) else band
        }
        _equalizerState.value = _equalizerState.value.copy(
            bands = updatedBands,
            activePreset = "Custom"
        )
        sendServiceCustomCommand(
            PlaybackService.CMD_SET_EQ_BAND,
            Bundle().apply {
                putShort(PlaybackService.ARG_BAND_INDEX, bandIndex)
                putShort(PlaybackService.ARG_BAND_LEVEL, clamped)
            }
        )
        persistAudioConsoleSettings()
    }

    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _equalizerState.value = _equalizerState.value.copy(bassBoostStrength = clamped)
        sendServiceCustomCommand(
            PlaybackService.CMD_SET_BASS_BOOST,
            Bundle().apply { putInt(PlaybackService.ARG_BASS_STRENGTH, clamped) }
        )
        persistAudioConsoleSettings()
    }

    fun applyEqualizerPreset(presetName: String) {
        val presetLevels: List<Short> = when (presetName) {
            "Flat" -> listOf(0, 0, 0, 0, 0)
            "Bass Boost", "Bass Heavy" -> listOf(950, 680, 100, 150, 250)
            "Desi Bass" -> listOf(1050, 720, -50, 350, 500)
            "Vocal Clarity", "Vocal Boost" -> listOf(-200, 150, 750, 650, 200)
            "Treble Sparkle", "Electronic" -> listOf(650, 300, -100, 500, 900)
            "Late Night" -> listOf(450, 200, -100, 250, -200)
            else -> listOf(300, 150, 0, 200, 350) // Studio Warmth
        }
        val bassStrength = when (presetName) {
            "Flat" -> 0
            "Bass Boost", "Bass Heavy" -> 780
            "Desi Bass" -> 850
            "Treble Sparkle", "Electronic" -> 520
            "Vocal Clarity", "Vocal Boost" -> 150
            "Late Night" -> 350
            else -> 300
        }

        val updatedBands = defaultEqBands().mapIndexed { idx, band ->
            val level = presetLevels.getOrElse(idx) { 0 }
            sendServiceCustomCommand(
                PlaybackService.CMD_SET_EQ_BAND,
                Bundle().apply {
                    putShort(PlaybackService.ARG_BAND_INDEX, band.index)
                    putShort(PlaybackService.ARG_BAND_LEVEL, level)
                }
            )
            band.copy(currentLevelMb = level)
        }
        _equalizerState.value = _equalizerState.value.copy(
            bands = updatedBands,
            bassBoostStrength = bassStrength,
            activePreset = presetName
        )
        sendServiceCustomCommand(
            PlaybackService.CMD_SET_BASS_BOOST,
            Bundle().apply { putInt(PlaybackService.ARG_BASS_STRENGTH, bassStrength) }
        )
        persistAudioConsoleSettings()
    }

    fun setCrossfadeSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        _equalizerState.value = _equalizerState.value.copy(crossfadeSeconds = clamped)
        sendServiceCustomCommand(
            PlaybackService.CMD_SET_CROSSFADE_SECONDS,
            Bundle().apply { putInt(PlaybackService.ARG_CROSSFADE_SECONDS, clamped) }
        )
        persistAudioConsoleSettings()
    }

    fun setGaplessPlayback(enabled: Boolean) {
        _equalizerState.value = _equalizerState.value.copy(gaplessPlayback = enabled)
        sendServiceCustomCommand(
            PlaybackService.CMD_SET_GAPLESS_PLAYBACK,
            Bundle().apply { putBoolean(PlaybackService.ARG_GAPLESS_ENABLED, enabled) }
        )
        persistAudioConsoleSettings()
    }

    private fun presetForAudioRoute(state: EqualizerState, route: AudioOutputRoute): String = when (route) {
        AudioOutputRoute.SPEAKER -> state.speakerPreset
        AudioOutputRoute.WIRED_HEADPHONES -> state.wiredPreset
        AudioOutputRoute.BLUETOOTH -> state.bluetoothPreset
    }

    fun setAutoDeviceProfilesEnabled(enabled: Boolean) {
        val currentRoute = PlaybackService.detectCurrentAudioOutputRoute(getApplication())
        _equalizerState.value = _equalizerState.value.copy(
            autoDeviceProfilesEnabled = enabled,
            activeOutputRoute = currentRoute
        )
        if (enabled && !_isKaraokeModeEnabled.value) {
            val mappedPreset = presetForAudioRoute(_equalizerState.value, currentRoute)
            applyEqualizerPreset(mappedPreset)
            showStatusMessage("Auto-Profiles On (${currentRoute.label} → $mappedPreset)")
        } else {
            persistAudioConsoleSettings()
        }
    }

    fun setOutputRoutePreset(route: AudioOutputRoute, presetName: String) {
        val current = _equalizerState.value
        val updated = when (route) {
            AudioOutputRoute.SPEAKER -> current.copy(speakerPreset = presetName)
            AudioOutputRoute.WIRED_HEADPHONES -> current.copy(wiredPreset = presetName)
            AudioOutputRoute.BLUETOOTH -> current.copy(bluetoothPreset = presetName)
        }
        _equalizerState.value = updated
        if (updated.autoDeviceProfilesEnabled &&
            updated.activeOutputRoute == route &&
            !_isKaraokeModeEnabled.value
        ) {
            applyEqualizerPreset(presetName)
        } else {
            persistAudioConsoleSettings()
        }
    }

    fun setAutoQueueRadio(enabled: Boolean) {
        // Endless Autoplay stays always enabled
        _equalizerState.value = _equalizerState.value.copy(autoQueueRadio = true)
        personalizationPrefs.edit().putBoolean("auto_queue_radio_enabled", true).apply()
        syncAutoQueueMetadataToService()
        sendServiceCustomCommand(
            PlaybackService.CMD_SET_AUTO_QUEUE_RADIO,
            Bundle().apply { putBoolean(PlaybackService.ARG_AUTO_QUEUE_ENABLED, true) }
        )
        maybeReplenishEndlessQueue()
    }

    fun toggleFavorite(song: SongItem) {
        viewModelScope.launch {
            val isNowFav = repository.toggleFavorite(song)
            applyFavoriteStateToUi(song.id, isNowFav)
            PlaybackService.syncQueueFromUi(_queue.value, _currentTrack.value)
            if (_currentTrack.value?.id == song.id) {
                sendServiceCustomCommand(
                    PlaybackService.CMD_REFRESH_FAVORITE_ICON,
                    Bundle().apply { putBoolean(PlaybackService.ARG_IS_FAVORITE, isNowFav) }
                )
            }
        }
    }

    fun toggleKaraokeVocalDucking() {
        val enabling = !_isKaraokeModeEnabled.value
        if (enabling) {
            preKaraokeEqSnapshot = _equalizerState.value
            val karaokeLevels = listOf<Short>(150, 250, -1150, -950, 450)
            val karaokeBands = _equalizerState.value.bands.mapIndexed { idx, band ->
                band.copy(currentLevelMb = karaokeLevels.getOrElse(idx) { 0 })
            }
            _equalizerState.value = _equalizerState.value.copy(
                isEnabled = true,
                bands = karaokeBands,
                bassBoostStrength = 250,
                activePreset = "Karaoke Vocal Duck"
            )
            setEqualizerEnabled(true)
            _isKaraokeModeEnabled.value = true
            showStatusMessage("Sing-Along Vocal Ducking On")
        } else {
            val restored = preKaraokeEqSnapshot ?: EqualizerState()
            preKaraokeEqSnapshot = null
            _equalizerState.value = restored
            setEqualizerEnabled(restored.isEnabled)
            _isKaraokeModeEnabled.value = false
            showStatusMessage("Restored ${restored.activePreset} EQ")
        }
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _isPlayerExpanded.value = expanded
        if (expanded) {
            updateSyncedLyricIndex(_progressMs.value)
        }
    }

    fun dismissMiniPlayer() {
        _isPlayerExpanded.value = false
        _isPlaying.value = false
        _currentTrack.value = null
        _autoQueuedSongIds.value = emptySet()
        _isRefillingAutoQueue.value = false
        unshuffledQueueBackup = emptyList()
        playHistoryStack.clear()
        mediaController?.stop()
        PlaybackSessionStore.clearSession(getApplication())
        MiniPlayerWidgetProvider.syncWidget(getApplication(), null, false)
    }

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }

    private fun showStatusMessage(message: String) {
        _statusBannerMessage.value = message
    }

    fun selectThemePreset(preset: StudioThemePreset) {
        val updated = _themeState.value.copy(selectedTheme = preset)
        _themeState.value = updated
        PlaybackSessionStore.saveThemeSettings(getApplication(), updated)
    }

    fun setAmoledPureBlack(enabled: Boolean) {
        val updated = _themeState.value.copy(amoledPureBlack = enabled)
        _themeState.value = updated
        PlaybackSessionStore.saveThemeSettings(getApplication(), updated)
    }

    fun setDynamicArtworkTint(enabled: Boolean) {
        val updated = _themeState.value.copy(dynamicArtworkTint = enabled)
        _themeState.value = updated
        PlaybackSessionStore.saveThemeSettings(getApplication(), updated)
    }

    private fun sendServiceCustomCommand(action: String, args: Bundle = Bundle.EMPTY) {
        val controller = mediaController ?: return
        if (controller.isConnected) {
            controller.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
        }
    }

    override fun onCleared() {
        persistPlaybackSession()
        persistAudioConsoleSettings()
        sleepTimerJob?.cancel()
        tickerJob?.cancel()
        lyricsFetchJob?.cancel()
        playSongJob?.cancel()
        batchDownloadJob?.cancel()
        activeDownloadJobs.values.forEach { it.cancel() }
        activeDownloadJobs.clear()
        searchJob?.cancel()
        homeLoadJob?.cancel()
        lazyShelfJobs.values.forEach { it.cancel() }
        lazyShelfJobs.clear()
        playlistLoadJob?.cancel()
        playlistLoadMoreJob?.cancel()
        onboardingArtistSearchJob?.cancel()
        personalizationSeedJob?.cancel()
        timeOfDayShelfJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
