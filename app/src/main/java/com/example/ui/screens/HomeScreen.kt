package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.NightlightRound
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Verified
import com.example.data.model.AlbumDetailData
import com.example.data.model.AlbumReleaseItem
import com.example.data.model.AppThemeState
import com.example.data.model.ArtistProfileData
import com.example.data.model.AudioOutputRoute
import com.example.data.model.BatchDownloadProgress
import com.example.data.model.BottomNavTab
import java.io.File
import com.example.data.model.EqualizerState
import com.example.data.model.HomeDiscoveryData
import com.example.data.model.LazyPlatformShelfKey
import com.example.data.model.LibraryFilterCategory
import com.example.data.model.ListeningStatsData
import com.example.data.model.MoodCategoryItem
import com.example.data.model.PlaylistItem
import com.example.data.model.RelatedArtistItem
import com.example.data.model.SearchArtistItem
import com.example.data.model.SearchFilterType
import com.example.data.model.SleepTimerState
import com.example.data.model.SongItem
import com.example.data.model.StudioThemePreset
import com.example.data.model.TimeOfDayShelfData
import com.example.data.model.TrackSortOrder
import com.example.ui.components.AnimatedFavoriteButton
import com.example.ui.components.ExpandedPlayerSheet
import com.example.ui.components.MiniPlayer
import com.example.ui.components.MiniWaveformIndicator
import com.example.ui.components.OptimizedArtworkImage
import com.example.ui.components.PlatformBadgePill
import com.example.ui.components.SoftTonalCircleIconButton
import com.example.ui.components.StudioEqualizerConsole
import com.example.ui.components.rememberDynamicArtworkGlow
import com.example.ui.theme.AlabasterWhite
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.ChillGradientEnd
import com.example.ui.theme.ChillGradientStart
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.FocusGradientEnd
import com.example.ui.theme.FocusGradientStart
import com.example.ui.theme.JetBrainsMonoFontFamily
import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.MidnightGradientEnd
import com.example.ui.theme.MidnightGradientStart
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.PhotoForegroundColor
import com.example.ui.theme.PhotoMutedColor
import com.example.ui.theme.PhotoScrimColor
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.ThemeOnPrimary
import com.example.ui.theme.WorkoutGradientEnd
import com.example.ui.theme.WorkoutGradientStart
import com.example.ui.viewmodel.PlayerViewModel
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val selectedBottomTab by viewModel.selectedBottomTab.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isResolvingStream by viewModel.isResolvingStream.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val playbackPitch by viewModel.playbackPitch.collectAsStateWithLifecycle()
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val themeState by viewModel.themeState.collectAsStateWithLifecycle()

    val homeDiscoveryData by viewModel.homeDiscoveryData.collectAsStateWithLifecycle()
    val homeFeedSongs by viewModel.homeFeedSongs.collectAsStateWithLifecycle()
    val isHomeLoading by viewModel.isHomeLoading.collectAsStateWithLifecycle()
    val loadingLazyShelves by viewModel.loadingLazyShelves.collectAsStateWithLifecycle()
    val loadedLazyShelves by viewModel.loadedLazyShelves.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val selectedPlaylistTracks by viewModel.selectedPlaylistTracks.collectAsStateWithLifecycle()
    val isPlaylistTracksLoading by viewModel.isPlaylistTracksLoading.collectAsStateWithLifecycle()
    val hasMorePlaylistTracks by viewModel.hasMorePlaylistTracks.collectAsStateWithLifecycle()
    val isPlaylistLoadingMore by viewModel.isPlaylistLoadingMore.collectAsStateWithLifecycle()
    val selectedArtistProfile by viewModel.selectedArtistProfile.collectAsStateWithLifecycle()
    val isArtistLoading by viewModel.isArtistLoading.collectAsStateWithLifecycle()
    val selectedAlbumDetail by viewModel.selectedAlbumDetail.collectAsStateWithLifecycle()
    val isAlbumLoading by viewModel.isAlbumLoading.collectAsStateWithLifecycle()
    val followedArtists by viewModel.followedArtists.collectAsStateWithLifecycle()
    val listeningStats by viewModel.listeningStats.collectAsStateWithLifecycle()
    val smartAutoPlaylists by viewModel.smartAutoPlaylists.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchSuggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchPlaylistResults by viewModel.searchPlaylistResults.collectAsStateWithLifecycle()
    val searchArtistResults by viewModel.searchArtistResults.collectAsStateWithLifecycle()
    val lastSearchedQuery by viewModel.lastSearchedQuery.collectAsStateWithLifecycle()
    val lastSearchResults by viewModel.lastSearchResults.collectAsStateWithLifecycle()
    val lastSearchPlaylistResults by viewModel.lastSearchPlaylistResults.collectAsStateWithLifecycle()
    val lastSearchArtistResults by viewModel.lastSearchArtistResults.collectAsStateWithLifecycle()
    val selectedSearchFilter by viewModel.selectedSearchFilter.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val importedPlaylists by viewModel.importedPlaylists.collectAsStateWithLifecycle()
    val isImportingPlaylist by viewModel.isImportingPlaylist.collectAsStateWithLifecycle()
    val isSyncingPlaylist by viewModel.isSyncingPlaylist.collectAsStateWithLifecycle()
    val offlineSongs by viewModel.offlineSongs.collectAsStateWithLifecycle()
    val downloadingSongIds by viewModel.downloadingSongIds.collectAsStateWithLifecycle()
    val batchDownloadProgress by viewModel.batchDownloadProgress.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val recentlyPlayedSongs by viewModel.recentlyPlayedSongs.collectAsStateWithLifecycle()
    val selectedMoodFilter by viewModel.selectedMoodFilter.collectAsStateWithLifecycle()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val statusBannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()
    val isEditingTaste by viewModel.isEditingTaste.collectAsStateWithLifecycle()
    val onboardingArtists by viewModel.onboardingArtists.collectAsStateWithLifecycle()
    val onboardingArtistSearchQuery by viewModel.onboardingArtistSearchQuery.collectAsStateWithLifecycle()
    val onboardingArtistSearchResults by viewModel.onboardingArtistSearchResults.collectAsStateWithLifecycle()
    val isSearchingOnboardingArtists by viewModel.isSearchingOnboardingArtists.collectAsStateWithLifecycle()
    val selectedTasteGenres by viewModel.selectedTasteGenres.collectAsStateWithLifecycle()
    val selectedTasteArtistNames by viewModel.selectedTasteArtistNames.collectAsStateWithLifecycle()
    val personalizedForYouSongs by viewModel.personalizedForYouSongs.collectAsStateWithLifecycle()
    val personalizedForYouPlaylists by viewModel.personalizedForYouPlaylists.collectAsStateWithLifecycle()
    val isPersonalizingForYou by viewModel.isPersonalizingForYou.collectAsStateWithLifecycle()
    val timeOfDayShelf by viewModel.timeOfDayShelf.collectAsStateWithLifecycle()
    val isTimeOfDayLoading by viewModel.isTimeOfDayLoading.collectAsStateWithLifecycle()

    var songPendingAddToPlaylist by remember { mutableStateOf<SongItem?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val currentTrackId = currentTrack?.id
    val downloadedSongIds = remember(offlineSongs) { offlineSongs.map { it.id }.toSet() }
    val showTasteScreen = !hasCompletedOnboarding || isEditingTaste

    BackHandler(enabled = !showTasteScreen && selectedAlbumDetail != null && !isPlayerExpanded) {
        viewModel.closeAlbumDetail()
    }

    BackHandler(enabled = !showTasteScreen && selectedAlbumDetail == null && selectedArtistProfile != null && !isPlayerExpanded) {
        viewModel.closeArtistHub()
    }

    BackHandler(enabled = !showTasteScreen && selectedAlbumDetail == null && selectedArtistProfile == null && selectedPlaylist != null && !isPlayerExpanded) {
        viewModel.closePlaylistDetail()
    }

    BackHandler(enabled = !showTasteScreen && selectedAlbumDetail == null && selectedArtistProfile == null && selectedPlaylist == null && selectedBottomTab != BottomNavTab.HOME && !isPlayerExpanded) {
        viewModel.selectBottomTab(BottomNavTab.HOME)
    }

    LaunchedEffect(statusBannerMessage) {
        if (statusBannerMessage != null) {
            delay(2_200L)
            viewModel.clearStatusBanner()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PitchBlack)
    ) {
        Scaffold(
            containerColor = PitchBlack,
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AnimatedVisibility(
                        visible = statusBannerMessage != null,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Surface(
                            color = CarbonElevated,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 4.dp)
                                .border(0.5.dp, ElectricMint.copy(alpha = 0.45f), RoundedCornerShape(50))
                        ) {
                            Text(
                                text = statusBannerMessage.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricMint,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = currentTrack != null && !isPlayerExpanded,
                        enter = slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialOffsetY = { it }
                        ) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        currentTrack?.let { activeSong ->
                            MiniPlayerStateBridge(
                                viewModel = viewModel,
                                activeSong = activeSong,
                                isPlaying = isPlaying
                            )
                        }
                    }

                    // Minimal Icon-Only Bottom Navigation Bar with Tonal Circular Indicator
                    SomifyBottomNavigationBar(
                        selectedTab = selectedBottomTab,
                        onSelectTab = { tab ->
                            viewModel.closeAlbumDetail()
                            viewModel.closeArtistHub()
                            viewModel.closePlaylistDetail()
                            viewModel.selectBottomTab(tab)
                        }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedBottomTab) {
                    BottomNavTab.HOME -> {
                        RichHomeTabContent(
                            moodCategories = viewModel.moodCategories,
                            discoveryData = homeDiscoveryData,
                            importedPlaylists = importedPlaylists,
                            recentlyPlayedSongs = recentlyPlayedSongs,
                            homeFeedSongs = homeFeedSongs,
                            selectedTasteGenres = selectedTasteGenres,
                            personalizedForYouSongs = personalizedForYouSongs,
                            personalizedForYouPlaylists = personalizedForYouPlaylists,
                            isPersonalizingForYou = isPersonalizingForYou,
                            timeOfDayShelf = timeOfDayShelf,
                            isTimeOfDayLoading = isTimeOfDayLoading,
                            isHomeLoading = isHomeLoading,
                            loadingLazyShelves = loadingLazyShelves,
                            loadedLazyShelves = loadedLazyShelves,
                            currentTrackId = currentTrackId,
                            isPlaying = isPlaying,
                            isResolvingStream = isResolvingStream,
                            selectedMoodFilter = selectedMoodFilter,
                            innerPadding = innerPadding,
                            onRefreshDiscovery = viewModel::refreshHomeDiscovery,
                            onLoadLazyShelf = viewModel::loadLazyHomeShelf,
                            onSelectMood = viewModel::selectMoodCategory,
                            onOpenPlaylist = viewModel::openPlaylist,
                            onPlayPlaylist = { pl -> viewModel.playPlaylist(pl, shuffle = false) },
                            onPlaySong = viewModel::playSong,
                            onAddToQueue = viewModel::addToQueue,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onClearRecentlyPlayed = viewModel::clearRecentlyPlayedSongs,
                            onOpenSearchTab = { viewModel.selectBottomTab(BottomNavTab.SEARCH) },
                            onOpenArtist = viewModel::openArtistHub,
                            onOpenTasteEditor = viewModel::openTasteEditor
                        )
                    }
                    BottomNavTab.SEARCH -> {
                        SearchTabScreenContent(
                            currentTrackId = currentTrackId,
                            isPlaying = isPlaying,
                            isResolvingStream = isResolvingStream,
                            searchQuery = searchQuery,
                            searchSuggestions = searchSuggestions,
                            searchResults = searchResults,
                            searchPlaylistResults = searchPlaylistResults,
                            searchArtistResults = searchArtistResults,
                            lastSearchedQuery = lastSearchedQuery,
                            lastSearchResults = lastSearchResults,
                            lastSearchPlaylistResults = lastSearchPlaylistResults,
                            lastSearchArtistResults = lastSearchArtistResults,
                            selectedSearchFilter = selectedSearchFilter,
                            importedPlaylists = importedPlaylists,
                            downloadedSongIds = downloadedSongIds,
                            downloadingSongIds = downloadingSongIds,
                            recentSearches = recentSearches,
                            isSearching = isSearching,
                            innerPadding = innerPadding,
                            onQueryChange = viewModel::onSearchQueryChange,
                            onSubmitSearch = viewModel::submitSearchQuery,
                            onRecordSearchHistory = viewModel::saveSearchQueryToHistory,
                            onSelectSearchFilter = viewModel::selectSearchFilter,
                            onRemoveRecentSearch = viewModel::removeRecentSearch,
                            onClearAllRecentSearches = viewModel::clearAllRecentSearches,
                            onPlaySong = viewModel::playSong,
                            onAddToQueue = viewModel::addToQueue,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onToggleDownload = viewModel::toggleOfflineDownload,
                            onAddToPlaylist = { song -> songPendingAddToPlaylist = song },
                            onOpenArtist = { artistName ->
                                focusManager.clearFocus()
                                viewModel.openArtistHub(artistName)
                            },
                            onOpenPlaylist = { playlist ->
                                focusManager.clearFocus()
                                viewModel.saveSearchQueryToHistory(searchQuery)
                                viewModel.openPlaylist(playlist)
                            },
                            onPlayPlaylist = { playlist ->
                                focusManager.clearFocus()
                                viewModel.saveSearchQueryToHistory(searchQuery)
                                viewModel.playPlaylist(playlist, shuffle = false)
                            },
                            onToggleFavoritePlaylist = viewModel::toggleFavoritePlaylist,
                            onClearFocus = { focusManager.clearFocus() }
                        )
                    }
                    BottomNavTab.LIBRARY -> {
                        LibraryTabScreenContent(
                            favoriteSongs = favoriteSongs,
                            listeningStats = listeningStats,
                            followedArtists = followedArtists,
                            smartAutoPlaylists = smartAutoPlaylists,
                            downloadedSongIds = downloadedSongIds,
                            downloadingSongIds = downloadingSongIds,
                            batchDownloadProgress = batchDownloadProgress,
                            importedPlaylists = importedPlaylists,
                            isImportingPlaylist = isImportingPlaylist,
                            currentTrackId = currentTrackId,
                            isPlaying = isPlaying,
                            isResolvingStream = isResolvingStream,
                            innerPadding = innerPadding,
                            onPlaySong = { song, list -> viewModel.playSong(song, list) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onAddToQueue = viewModel::addToQueue,
                            onToggleDownload = viewModel::toggleOfflineDownload,
                            onDownloadAllLiked = {
                                viewModel.downloadEntireCollection("Liked Songs", favoriteSongs)
                            },
                            onAddToPlaylist = { song -> songPendingAddToPlaylist = song },
                            onOpenArtist = viewModel::openArtistHub,
                            onOpenCreatePlaylistDialog = { showCreatePlaylistDialog = true },
                            onImportPlaylistUrl = { rawUrl, onDone ->
                                focusManager.clearFocus()
                                viewModel.importPlaylistFromLink(rawUrl, onDone)
                            },
                            onOpenPlaylist = viewModel::openPlaylist,
                            onPlayPlaylist = { pl -> viewModel.playPlaylist(pl, shuffle = false) },
                            onRemoveImportedPlaylist = viewModel::removeImportedPlaylist
                        )
                    }
                    BottomNavTab.DOWNLOADS -> {
                        OfflineDownloadsTabScreenContent(
                            offlineSongs = offlineSongs,
                            downloadingSongIds = downloadingSongIds,
                            batchDownloadProgress = batchDownloadProgress,
                            currentTrackId = currentTrackId,
                            isPlaying = isPlaying,
                            isResolvingStream = isResolvingStream,
                            innerPadding = innerPadding,
                            onPlaySong = { song, list -> viewModel.playSong(song, list) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onAddToQueue = viewModel::addToQueue,
                            onToggleDownload = viewModel::toggleOfflineDownload,
                            onCancelBatchDownload = viewModel::cancelBatchDownload,
                            onClearAllDownloads = viewModel::clearAllOfflineDownloads,
                            onAddToPlaylist = { song -> songPendingAddToPlaylist = song },
                            onOpenArtist = viewModel::openArtistHub,
                            onDiscoverMusicClick = { viewModel.selectBottomTab(BottomNavTab.SEARCH) }
                        )
                    }
                    BottomNavTab.SETTINGS -> {
                        SettingsAndEqTabContent(
                            equalizerState = equalizerState,
                            themeState = themeState,
                            playbackSpeed = playbackSpeed,
                            playbackPitch = playbackPitch,
                            sleepTimerState = sleepTimerState,
                            selectedTasteGenres = selectedTasteGenres,
                            innerPadding = innerPadding,
                            onSetEqEnabled = viewModel::setEqualizerEnabled,
                            onSetBandLevel = viewModel::setEqualizerBandLevel,
                            onSetBassBoost = viewModel::setBassBoostStrength,
                            onApplyPreset = viewModel::applyEqualizerPreset,
                            onSetSpeed = viewModel::setPlaybackSpeed,
                            onSetPitch = viewModel::setPlaybackPitch,
                            onSetCrossfadeSeconds = viewModel::setCrossfadeSeconds,
                            onSetGaplessPlayback = viewModel::setGaplessPlayback,
                            onSetAutoQueueRadio = viewModel::setAutoQueueRadio,
                            onSetAutoDeviceProfilesEnabled = viewModel::setAutoDeviceProfilesEnabled,
                            onSetOutputRoutePreset = viewModel::setOutputRoutePreset,
                            onSelectThemePreset = viewModel::selectThemePreset,
                            onSetAmoledPureBlack = viewModel::setAmoledPureBlack,
                            onSetDynamicArtworkTint = viewModel::setDynamicArtworkTint,
                            onStartSleepTimer = viewModel::startSleepTimer,
                            onCancelSleepTimer = viewModel::cancelSleepTimer,
                            onOpenTasteEditor = viewModel::openTasteEditor
                        )
                    }
                }

                // Dedicated Playlist Detail Screen Overlay
                AnimatedVisibility(
                    visible = selectedPlaylist != null,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    selectedPlaylist?.let { activePlaylist ->
                        val isPlaylistSaved = remember(importedPlaylists, activePlaylist.id, activePlaylist.remotePlaylistId) {
                            importedPlaylists.any {
                                it.id == activePlaylist.id ||
                                    (it.remotePlaylistId.isNotBlank() && it.remotePlaylistId == activePlaylist.remotePlaylistId)
                            }
                        }
                        val canRenamePlaylist = activePlaylist.id.startsWith("custom_")
                        PlaylistDetailScreen(
                            playlist = activePlaylist,
                            tracks = selectedPlaylistTracks,
                            downloadedSongIds = downloadedSongIds,
                            batchDownloadProgress = batchDownloadProgress,
                            isLoadingTracks = isPlaylistTracksLoading,
                            hasMoreTracks = hasMorePlaylistTracks,
                            isLoadingMoreTracks = isPlaylistLoadingMore,
                            isSyncingPlaylist = isSyncingPlaylist,
                            isPlaylistFavorite = isPlaylistSaved,
                            canEditTracks = isPlaylistSaved,
                            canRenamePlaylist = canRenamePlaylist,
                            currentTrackId = currentTrackId,
                            isPlaying = isPlaying,
                            isResolvingStream = isResolvingStream,
                            innerPadding = innerPadding,
                            onBack = viewModel::closePlaylistDetail,
                            onPlayAll = { viewModel.playPlaylist(activePlaylist, shuffle = false) },
                            onShufflePlay = { viewModel.playPlaylist(activePlaylist, shuffle = true) },
                            onDownloadAll = {
                                viewModel.downloadEntireCollection(activePlaylist.title, selectedPlaylistTracks)
                            },
                            onCancelBatchDownload = viewModel::cancelBatchDownload,
                            onLoadMoreTracks = viewModel::loadNextPlaylistPage,
                            onSyncPlaylist = viewModel::syncSelectedPlaylist,
                            onToggleFavoritePlaylist = { viewModel.toggleFavoritePlaylist(activePlaylist) },
                            onRenamePlaylist = { newTitle, newSubtitle ->
                                viewModel.renamePlaylist(activePlaylist.id, newTitle, newSubtitle)
                            },
                            onRemoveTrack = { song ->
                                viewModel.removeTrackFromPlaylist(activePlaylist.id, song.id)
                            },
                            onMoveTrack = { fromIdx, toIdx ->
                                viewModel.moveTrackInPlaylist(activePlaylist.id, fromIdx, toIdx)
                            },
                            onPlaySong = { song ->
                                viewModel.playSong(song, selectedPlaylistTracks)
                            },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onAddToQueue = viewModel::addToQueue,
                            onOpenArtist = viewModel::openArtistHub
                        )
                    }
                }

                // Dedicated Artist Hub Screen Overlay
                AnimatedVisibility(
                    visible = selectedArtistProfile != null,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    selectedArtistProfile?.let { activeArtist ->
                        val isFollowing = remember(followedArtists, activeArtist.name) {
                            followedArtists.any { it.name.equals(activeArtist.name, ignoreCase = true) }
                        }
                        ArtistHubScreen(
                            artistProfile = activeArtist,
                            isLoading = isArtistLoading,
                            isFollowing = isFollowing,
                            currentTrackId = currentTrackId,
                            isPlaying = isPlaying,
                            isResolvingStream = isResolvingStream,
                            downloadedSongIds = downloadedSongIds,
                            downloadingSongIds = downloadingSongIds,
                            innerPadding = innerPadding,
                            onBack = viewModel::closeArtistHub,
                            onPlayAll = { viewModel.playArtistTopSongs(shuffle = false) },
                            onShufflePlay = { viewModel.playArtistTopSongs(shuffle = true) },
                            onStartArtistRadio = { viewModel.startArtistRadio(activeArtist) },
                            onToggleFollow = {
                                viewModel.toggleFollowArtist(activeArtist.name, activeArtist.artworkUrl)
                            },
                            onOpenAlbum = { album -> viewModel.openAlbumDetail(album) },
                            onPlaySong = { song ->
                                viewModel.playSong(song, activeArtist.topSongs)
                            },
                            onAddToQueue = viewModel::addToQueue,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onToggleDownload = viewModel::toggleOfflineDownload,
                            onAddToPlaylist = { song -> songPendingAddToPlaylist = song },
                            onSelectSimilarArtist = { similarName ->
                                viewModel.openArtistHub(similarName, pushCurrentToBackStack = true)
                            }
                        )
                    }
                }

                // Dedicated Album / Single Detail Screen Overlay
                AnimatedVisibility(
                    visible = selectedAlbumDetail != null,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    selectedAlbumDetail?.let { activeAlbum ->
                        AlbumDetailScreen(
                            albumDetail = activeAlbum,
                            isLoading = isAlbumLoading,
                            downloadedSongIds = downloadedSongIds,
                            downloadingSongIds = downloadingSongIds,
                            batchDownloadProgress = batchDownloadProgress,
                            currentTrackId = currentTrackId,
                            isPlaying = isPlaying,
                            isResolvingStream = isResolvingStream,
                            innerPadding = innerPadding,
                            onBack = viewModel::closeAlbumDetail,
                            onPlayAlbum = { viewModel.playAlbum(shuffle = false) },
                            onShuffleAlbum = { viewModel.playAlbum(shuffle = true) },
                            onDownloadAlbum = {
                                viewModel.downloadEntireCollection(activeAlbum.title, activeAlbum.tracks)
                            },
                            onOpenArtist = { artistName ->
                                viewModel.closeAlbumDetail()
                                viewModel.openArtistHub(artistName)
                            },
                            onPlaySong = { song ->
                                viewModel.playSong(song, activeAlbum.tracks)
                            },
                            onAddToQueue = viewModel::addToQueue,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onToggleDownload = viewModel::toggleOfflineDownload,
                            onAddToPlaylist = { song -> songPendingAddToPlaylist = song }
                        )
                    }
                }
            }
        }

        // Full-Screen Spring-Physics Expanded Player Sheet
        AnimatedVisibility(
            visible = isPlayerExpanded && currentTrack != null,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it }
            ) + fadeIn(),
            exit = slideOutVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                targetOffsetY = { it }
            ) + fadeOut()
        ) {
            currentTrack?.let { activeSong ->
                ExpandedPlayerStateBridge(
                    viewModel = viewModel,
                    activeSong = activeSong,
                    isPlaying = isPlaying,
                    isDownloaded = downloadedSongIds.contains(activeSong.id),
                    isDownloading = downloadingSongIds.contains(activeSong.id),
                    sleepTimerState = sleepTimerState,
                    playbackSpeed = playbackSpeed,
                    playbackPitch = playbackPitch,
                    equalizerState = equalizerState,
                    onOpenAddToPlaylist = { song -> songPendingAddToPlaylist = song }
                )
            }
        }

        if (showCreatePlaylistDialog) {
            CreateCustomPlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onCreate = { name, subtitle ->
                    viewModel.createCustomPlaylist(name, subtitle)
                    showCreatePlaylistDialog = false
                }
            )
        }

        songPendingAddToPlaylist?.let { targetSong ->
            AddToPlaylistBottomSheet(
                song = targetSong,
                playlists = importedPlaylists,
                onDismiss = { songPendingAddToPlaylist = null },
                onPlayNext = {
                    viewModel.playNext(targetSong)
                    songPendingAddToPlaylist = null
                },
                onAddToQueue = {
                    viewModel.addToQueue(targetSong)
                    songPendingAddToPlaylist = null
                },
                onStartRadio = {
                    viewModel.startSongRadio(targetSong)
                    songPendingAddToPlaylist = null
                },
                onSelectPlaylist = { playlist ->
                    viewModel.addSongToPlaylist(playlist.id, targetSong)
                    songPendingAddToPlaylist = null
                },
                onCreateNewPlaylist = { name, subtitle ->
                    viewModel.createCustomPlaylist(name, subtitle, targetSong)
                    songPendingAddToPlaylist = null
                }
            )
        }

        AnimatedVisibility(
            visible = showTasteScreen,
            enter = fadeIn(),
            exit = fadeOut() + slideOutVertically { it / 6 }
        ) {
            TasteOnboardingScreen(
                genres = viewModel.onboardingGenres,
                artists = onboardingArtists,
                artistSearchQuery = onboardingArtistSearchQuery,
                artistSearchResults = onboardingArtistSearchResults,
                isSearchingArtists = isSearchingOnboardingArtists,
                onArtistSearchQueryChange = viewModel::onOnboardingArtistSearchQueryChange,
                onCompleteOnboarding = viewModel::completeOnboarding,
                initialGenreTitles = selectedTasteGenres,
                initialArtistNames = selectedTasteArtistNames,
                isEditMode = hasCompletedOnboarding && isEditingTaste,
                onDismissEdit = viewModel::dismissTasteEditor,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Isolates MiniPlayer progress & duration collection so HomeScreen never recomposes on progress ticks.
 */
@Composable
private fun MiniPlayerStateBridge(
    viewModel: PlayerViewModel,
    activeSong: SongItem,
    isPlaying: Boolean
) {
    val progressState = viewModel.progressMs.collectAsStateWithLifecycle()
    val durationState = viewModel.durationMs.collectAsStateWithLifecycle()

    MiniPlayer(
        currentTrack = activeSong,
        isPlaying = isPlaying,
        progressProvider = { progressState.value },
        durationProvider = { durationState.value },
        onTogglePlayPause = viewModel::togglePlayPause,
        onSkipNext = viewModel::skipNext,
        onSkipPrevious = viewModel::skipPrevious,
        onExpand = { viewModel.setPlayerExpanded(true) },
        onDismiss = viewModel::dismissMiniPlayer,
        onToggleFavorite = { viewModel.toggleFavorite(activeSong) }
    )
}

/**
 * Isolates ExpandedPlayerSheet high-frequency state collection (progressMs, lyrics, queue)
 * so these flows are only collected while the full-screen player sheet is visible.
 */
@Composable
private fun ExpandedPlayerStateBridge(
    viewModel: PlayerViewModel,
    activeSong: SongItem,
    isPlaying: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    sleepTimerState: SleepTimerState,
    playbackSpeed: Float,
    playbackPitch: Float,
    equalizerState: EqualizerState,
    onOpenAddToPlaylist: (SongItem) -> Unit
) {
    val progressState = viewModel.progressMs.collectAsStateWithLifecycle()
    val durationState = viewModel.durationMs.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val isShuffled by viewModel.isShuffled.collectAsStateWithLifecycle()
    val currentLyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val currentLyricIndex by viewModel.currentLyricIndex.collectAsStateWithLifecycle()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsStateWithLifecycle()
    val isKaraokeModeEnabled by viewModel.isKaraokeModeEnabled.collectAsStateWithLifecycle()
    val autoQueuedSongIds by viewModel.autoQueuedSongIds.collectAsStateWithLifecycle()
    val isRefillingAutoQueue by viewModel.isRefillingAutoQueue.collectAsStateWithLifecycle()

    ExpandedPlayerSheet(
        currentTrack = activeSong,
        isPlaying = isPlaying,
        progressProvider = { progressState.value },
        durationProvider = { durationState.value },
        queue = queue,
        repeatMode = repeatMode,
        isShuffled = isShuffled,
        currentLyrics = currentLyrics,
        currentLyricIndex = currentLyricIndex,
        isLyricsLoading = isLyricsLoading,
        sleepTimerState = sleepTimerState,
        playbackSpeed = playbackSpeed,
        playbackPitch = playbackPitch,
        equalizerState = equalizerState,
        isTrackDownloaded = isDownloaded,
        isTrackDownloading = isDownloading,
        isKaraokeModeEnabled = isKaraokeModeEnabled,
        autoQueuedIds = autoQueuedSongIds,
        isRefillingAutoQueue = isRefillingAutoQueue,
        onCollapse = { viewModel.setPlayerExpanded(false) },
        onTogglePlayPause = viewModel::togglePlayPause,
        onSeekTo = viewModel::seekTo,
        onSkipNext = viewModel::skipNext,
        onSkipPrevious = viewModel::skipPrevious,
        onToggleShuffle = viewModel::toggleShuffle,
        onCycleRepeat = viewModel::cycleRepeatMode,
        onToggleFavorite = viewModel::toggleFavorite,
        onToggleDownloadTrack = { viewModel.toggleOfflineDownload(activeSong) },
        onOpenAddToPlaylist = { onOpenAddToPlaylist(activeSong) },
        onOpenArtist = { artistName -> viewModel.openArtistHub(artistName) },
        onOpenAlbum = { albumTitle, artistName, artworkUrl ->
            viewModel.openAlbumByName(albumTitle, artistName, artworkUrl)
        },
        onPlayNextInQueue = viewModel::playNext,
        onClearUpcomingQueue = viewModel::clearUpcomingQueue,
        onToggleKaraokeMode = viewModel::toggleKaraokeVocalDucking,
        onStartSongRadio = { viewModel.startSongRadio(activeSong) },
        onSaveQueueAsPlaylist = viewModel::saveCurrentQueueAsPlaylist,
        onPlaySongFromQueue = { chosen ->
            viewModel.playSong(chosen, queue)
        },
        onReorderQueue = viewModel::reorderQueue,
        onRemoveFromQueue = viewModel::removeFromQueue,
        onStartSleepTimer = viewModel::startSleepTimer,
        onCancelSleepTimer = viewModel::cancelSleepTimer,
        onSetPlaybackSpeed = viewModel::setPlaybackSpeed,
        onSetPlaybackPitch = viewModel::setPlaybackPitch,
        onSetEqualizerEnabled = viewModel::setEqualizerEnabled,
        onSetEqualizerBand = viewModel::setEqualizerBandLevel,
        onSetBassBoost = viewModel::setBassBoostStrength,
        onApplyEqPreset = viewModel::applyEqualizerPreset
    )
}

/**
 * Minimal Icon-Only Bottom Navigation Bar with Soft Tonal Circular Selection (No Text Labels)
 */
@Composable
private fun SomifyBottomNavigationBar(
    selectedTab: BottomNavTab,
    onSelectTab: (BottomNavTab) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = SubtleBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
        NavigationBar(
            containerColor = PitchBlack,
            contentColor = AlabasterWhite,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bottom_navigation_bar")
        ) {
            BottomNavTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val icon = when (tab) {
                    BottomNavTab.HOME -> if (isSelected) Icons.Filled.Home else Icons.Outlined.Home
                    BottomNavTab.SEARCH -> if (isSelected) Icons.Filled.Search else Icons.Outlined.Search
                    BottomNavTab.LIBRARY -> if (isSelected) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic
                    BottomNavTab.DOWNLOADS -> if (isSelected) Icons.Filled.Download else Icons.Outlined.Download
                    BottomNavTab.SETTINGS -> if (isSelected) Icons.Filled.Settings else Icons.Outlined.Settings
                }

                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) ElectricMint.copy(alpha = 0.16f) else Color.Transparent
                                )
                                .border(
                                    width = if (isSelected) 0.75.dp else 0.dp,
                                    color = if (isSelected) ElectricMint.copy(alpha = 0.55f) else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) ElectricMint else MutedSlate,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    label = null,
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ElectricMint,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MutedSlate
                    ),
                    modifier = Modifier.testTag("bottom_nav_tab_${tab.name.lowercase()}")
                )
            }
        }
    }
}

/**
 * Rich Spotify & Amazon Music Inspired Home Tab Powered by Real Live Playlists & Charts
 */
@Composable
private fun RichHomeTabContent(
    moodCategories: List<MoodCategoryItem>,
    discoveryData: HomeDiscoveryData,
    importedPlaylists: List<PlaylistItem>,
    recentlyPlayedSongs: List<SongItem>,
    homeFeedSongs: List<SongItem>,
    selectedTasteGenres: List<String>,
    personalizedForYouSongs: List<SongItem>,
    personalizedForYouPlaylists: List<PlaylistItem>,
    isPersonalizingForYou: Boolean,
    timeOfDayShelf: TimeOfDayShelfData?,
    isTimeOfDayLoading: Boolean,
    isHomeLoading: Boolean,
    loadingLazyShelves: Set<LazyPlatformShelfKey>,
    loadedLazyShelves: Set<LazyPlatformShelfKey>,
    currentTrackId: String?,
    isPlaying: Boolean,
    isResolvingStream: Boolean,
    selectedMoodFilter: String?,
    innerPadding: PaddingValues,
    onRefreshDiscovery: () -> Unit,
    onLoadLazyShelf: (LazyPlatformShelfKey) -> Unit,
    onSelectMood: (String?) -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onPlayPlaylist: (PlaylistItem) -> Unit,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onAddToQueue: (SongItem) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onClearRecentlyPlayed: () -> Unit,
    onOpenSearchTab: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
    onOpenTasteEditor: () -> Unit = {}
) {
    val allPlaylists = discoveryData.allPlaylists
    val isIndianFilter = remember(selectedMoodFilter) {
        selectedMoodFilter != null && selectedMoodFilter in setOf(
            "India Hits",
            "Bollywood",
            "Punjabi & Hip-Hop",
            "South & Sufi"
        )
    }

    val filteredIndianTopPlaylists = remember(discoveryData, selectedMoodFilter) {
        if (selectedMoodFilter == null) {
            discoveryData.indianTopPlaylists
        } else {
            (discoveryData.indianTopPlaylists + discoveryData.indianGenrePlaylists).filter {
                it.category.equals(selectedMoodFilter, ignoreCase = true) ||
                    it.title.contains(selectedMoodFilter, ignoreCase = true)
            }.ifEmpty {
                if (isIndianFilter) discoveryData.indianTopPlaylists else emptyList()
            }
        }
    }

    val filteredIndianGenrePlaylists = remember(discoveryData, selectedMoodFilter) {
        if (selectedMoodFilter == null) {
            discoveryData.indianGenrePlaylists
        } else {
            discoveryData.indianGenrePlaylists.filter {
                it.category.equals(selectedMoodFilter, ignoreCase = true) ||
                    it.title.contains(selectedMoodFilter, ignoreCase = true)
            }
        }
    }

    val filteredTopPlaylists = remember(discoveryData, selectedMoodFilter, isIndianFilter) {
        when {
            selectedMoodFilter == null -> discoveryData.topHitsPlaylists
            isIndianFilter -> emptyList()
            else -> allPlaylists.filter { it.category.equals(selectedMoodFilter, ignoreCase = true) }
                .ifEmpty { discoveryData.topHitsPlaylists }
        }
    }

    val filteredMoodPlaylists = remember(discoveryData, selectedMoodFilter) {
        if (selectedMoodFilter == null) {
            discoveryData.newAndMoodPlaylists
        } else {
            allPlaylists.filter {
                it.category.equals(selectedMoodFilter, ignoreCase = true) ||
                    it.title.contains(selectedMoodFilter, ignoreCase = true)
            }.ifEmpty { discoveryData.newAndMoodPlaylists }
        }
    }

    val filteredGenrePlaylists = remember(discoveryData, selectedMoodFilter) {
        discoveryData.genrePlaylists
    }

    val quickAccessPlaylists = remember(importedPlaylists, discoveryData, allPlaylists, selectedMoodFilter) {
        if (selectedMoodFilter == null) {
            val indiaQuick = (discoveryData.indianTopPlaylists.take(2) + discoveryData.indianGenrePlaylists.take(1))
            val globalQuick = (discoveryData.topHitsPlaylists.take(2) + discoveryData.newAndMoodPlaylists.take(1))
            (importedPlaylists.take(2) + indiaQuick + globalQuick).distinctBy { it.id }.ifEmpty { allPlaylists }.take(6)
        } else {
            allPlaylists.filter { it.category.equals(selectedMoodFilter, ignoreCase = true) }
                .ifEmpty { allPlaylists }
                .take(6)
        }
    }

    val spotlightPlaylist = remember(filteredIndianTopPlaylists, filteredTopPlaylists, allPlaylists, isIndianFilter) {
        if (isIndianFilter) {
            filteredIndianTopPlaylists.firstOrNull() ?: allPlaylists.firstOrNull()
        } else {
            filteredIndianTopPlaylists.firstOrNull()
                ?: filteredTopPlaylists.firstOrNull()
                ?: allPlaylists.firstOrNull()
        }
    }

    val topChartsSongs = remember(homeFeedSongs, discoveryData, isIndianFilter) {
        if (isIndianFilter && discoveryData.indianChartSongs.isNotEmpty()) {
            discoveryData.indianChartSongs.take(15)
        } else {
            homeFeedSongs.take(15)
        }
    }

    val homeTintPrimary = ElectricMint
    val homeTintSecondary = NeonViolet
    val homeBgColor = PitchBlack

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val bgBrush = Brush.verticalGradient(
                    colors = listOf(
                        homeTintPrimary.copy(alpha = 0.08f),
                        homeTintSecondary.copy(alpha = 0.04f),
                        homeBgColor
                    ),
                    startY = 0f,
                    endY = 580f
                )
                onDrawBehind {
                    drawRect(bgBrush)
                }
            }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 720.dp)
                .testTag("home_lazy_column"),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // 1. Minimal Brand Logo + Soft Tonal Circular Actions + Category Filter Chips
            item(key = "home_brand_and_chips_header", contentType = "home_header") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(PitchBlack)
                                    .border(1.dp, ElectricMint.copy(alpha = 0.55f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.spiral_logo),
                                    contentDescription = "Echoic Logo",
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Text(
                                text = "Echoic",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = AlabasterWhite
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SoftTonalCircleIconButton(
                                icon = Icons.Outlined.Refresh,
                                contentDescription = "Refresh Live Playlists",
                                onClick = onRefreshDiscovery,
                                size = 40.dp,
                                iconSize = 19.dp,
                                inactiveTint = MutedSlate,
                                modifier = Modifier.testTag("home_refresh_button")
                            )

                            SoftTonalCircleIconButton(
                                icon = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.search_placeholder),
                                onClick = onOpenSearchTab,
                                size = 40.dp,
                                iconSize = 19.dp,
                                inactiveTint = AlabasterWhite,
                                modifier = Modifier.testTag("home_quick_search_button")
                            )
                        }
                    }

                    // Spotify-Style Category Filter Pills Row (India + Global)
                    CategoryFilterPillsRow(
                        moods = moodCategories,
                        selectedMood = selectedMoodFilter,
                        onSelectMood = onSelectMood
                    )
                }
            }

            if (isHomeLoading && allPlaylists.isEmpty() && topChartsSongs.isEmpty()) {
                item(key = "home_live_loading_state", contentType = "loading_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = ElectricMint,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = "Syncing live Indian & Global playlists...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSlate
                            )
                        }
                    }
                }
            }

            // 1.5. 2-Column Recently Played Songs Quick-Play Grid near the top of Home
            if (recentlyPlayedSongs.isNotEmpty()) {
                item(key = "home_recently_played_grid", contentType = "recently_played_grid") {
                    RecentlyPlayedSongsQuickGrid(
                        songs = recentlyPlayedSongs,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        isResolvingStream = isResolvingStream,
                        onPlaySong = { clicked -> onPlaySong(clicked, emptyList()) },
                        onClearHistory = onClearRecentlyPlayed,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // 2. Spotify-Style 2-Column Quick-Access Real Playlists Grid
            if (quickAccessPlaylists.isNotEmpty()) {
                item(key = "spotify_quick_access_playlist_grid", contentType = "quick_access_grid") {
                    SpotifyQuickAccessPlaylistGrid(
                        playlists = quickAccessPlaylists,
                        onPlaylistClick = onOpenPlaylist,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // 2.5. User's Saved & Imported Playlists Carousel on Home Screen
            if (importedPlaylists.isNotEmpty()) {
                item(key = "home_imported_playlists_carousel", contentType = "live_playlist_carousel") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeaderRow(
                            title = "Your Saved & Imported Playlists",
                            subtitle = "Favorited from Search or added via playlist links"
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.testTag("home_imported_playlist_carousel_row")
                        ) {
                            items(
                                items = importedPlaylists,
                                key = { "home_imported_pl_${it.id}" },
                                contentType = { "live_playlist_card" }
                            ) { playlist ->
                                LivePlaylistCarouselCard(
                                    playlist = playlist,
                                    onCardClick = { onOpenPlaylist(playlist) },
                                    onQuickPlayClick = { onPlayPlaylist(playlist) }
                                )
                            }
                        }
                    }
                }
            }

            // 2.7. Personalized "Picked For You" Shelf from User Taste Onboarding
            if (selectedMoodFilter == null &&
                (personalizedForYouSongs.isNotEmpty() || personalizedForYouPlaylists.isNotEmpty() || isPersonalizingForYou)
            ) {
                item(key = "home_picked_for_you_section", contentType = "picked_for_you_carousel") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val tasteSubtitle = if (selectedTasteGenres.isNotEmpty()) {
                            "Tailored around ${selectedTasteGenres.take(3).joinToString(" • ")}"
                        } else {
                            "Tailored around your followed artists & taste"
                        }
                        SectionHeaderRow(
                            title = "Picked For You",
                            subtitle = tasteSubtitle,
                            isLoadingInline = isPersonalizingForYou,
                            actionLabel = "Edit Taste",
                            onActionClick = onOpenTasteEditor
                        )

                        if (personalizedForYouSongs.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.testTag("home_picked_for_you_songs_row")
                            ) {
                                items(
                                    items = personalizedForYouSongs,
                                    key = { "picked_song_${it.id}" },
                                    contentType = { "picked_song_card" }
                                ) { song ->
                                    val isCurrentTrack = currentTrackId == song.id
                                    PickedForYouTrackCard(
                                        song = song,
                                        isCurrentTrack = isCurrentTrack,
                                        isPlaying = isCurrentTrack && isPlaying,
                                        onPlayClick = { onPlaySong(song, personalizedForYouSongs) },
                                        onArtistClick = { onOpenArtist(song.artist) }
                                    )
                                }
                            }
                        }

                        if (personalizedForYouPlaylists.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.testTag("home_picked_for_you_playlists_row")
                            ) {
                                items(
                                    items = personalizedForYouPlaylists,
                                    key = { "picked_pl_${it.id}" },
                                    contentType = { "live_playlist_card" }
                                ) { playlist ->
                                    LivePlaylistCarouselCard(
                                        playlist = playlist,
                                        onCardClick = { onOpenPlaylist(playlist) },
                                        onQuickPlayClick = { onPlayPlaylist(playlist) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2.8. Contextual Time-of-Day Smart Shelf (Morning Focus / Afternoon Boost / Evening Drive / Late Night Chill)
            if (selectedMoodFilter == null &&
                ((timeOfDayShelf != null && (timeOfDayShelf.songs.isNotEmpty() || timeOfDayShelf.playlists.isNotEmpty())) || isTimeOfDayLoading)
            ) {
                item(key = "home_time_of_day_smart_shelf", contentType = "time_of_day_carousel") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeaderRow(
                            title = timeOfDayShelf?.title ?: "Tuned For This Hour",
                            subtitle = timeOfDayShelf?.subtitle ?: "Adapting your smart shelf to local time & taste",
                            isLoadingInline = isTimeOfDayLoading
                        )

                        val timeSongs = timeOfDayShelf?.songs.orEmpty()
                        if (timeSongs.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.testTag("home_time_of_day_songs_row")
                            ) {
                                items(
                                    items = timeSongs,
                                    key = { "tod_song_${it.id}" },
                                    contentType = { "picked_song_card" }
                                ) { song ->
                                    val isCurrentTrack = currentTrackId == song.id
                                    PickedForYouTrackCard(
                                        song = song,
                                        isCurrentTrack = isCurrentTrack,
                                        isPlaying = isCurrentTrack && isPlaying,
                                        onPlayClick = { onPlaySong(song, timeSongs) },
                                        onArtistClick = { onOpenArtist(song.artist) }
                                    )
                                }
                            }
                        }

                        val timePlaylists = timeOfDayShelf?.playlists.orEmpty()
                        if (timePlaylists.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.testTag("home_time_of_day_playlists_row")
                            ) {
                                items(
                                    items = timePlaylists,
                                    key = { "tod_pl_${it.id}" },
                                    contentType = { "live_playlist_card" }
                                ) { playlist ->
                                    LivePlaylistCarouselCard(
                                        playlist = playlist,
                                        onCardClick = { onOpenPlaylist(playlist) },
                                        onQuickPlayClick = { onPlayPlaylist(playlist) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Amazon Music-Style Featured Spotlight Playlist Hero Card
            spotlightPlaylist?.let { heroPlaylist ->
                item(key = "amazon_spotlight_hero_playlist", contentType = "spotlight_hero") {
                    SpotlightPlaylistHeroCard(
                        playlist = heroPlaylist,
                        onOpenPlaylist = { onOpenPlaylist(heroPlaylist) },
                        onPlayAllClick = { onPlayPlaylist(heroPlaylist) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // 4. Horizontal Carousel: Trending in India & Bollywood Hot Hits
            if (filteredIndianTopPlaylists.isNotEmpty() && (selectedMoodFilter == null || isIndianFilter)) {
                item(key = "indian_top_hits_playlists_carousel", contentType = "live_playlist_carousel") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeaderRow(
                            title = if (isIndianFilter && selectedMoodFilter != null) {
                                "$selectedMoodFilter Playlists"
                            } else {
                                "Trending in India & Bollywood Hot Hits"
                            },
                            subtitle = "Top 50 India, Bollywood Blockbusters & Hindi Romance"
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.testTag("indian_top_playlist_carousel_row")
                        ) {
                            items(
                                items = filteredIndianTopPlaylists,
                                key = { "india_top_pl_${it.id}" },
                                contentType = { "live_playlist_card" }
                            ) { playlist ->
                                LivePlaylistCarouselCard(
                                    playlist = playlist,
                                    onCardClick = { onOpenPlaylist(playlist) },
                                    onQuickPlayClick = { onPlayPlaylist(playlist) }
                                )
                            }
                        }
                    }
                }
            }

            // 5. Horizontal Carousel: Indian Genres — Lazy-Loaded on Scroll or Filter Selection
            val showIndianGenreShelf = selectedMoodFilter == null || isIndianFilter
            if (showIndianGenreShelf && (filteredIndianGenrePlaylists.isNotEmpty() || LazyPlatformShelfKey.INDIAN_GENRES !in loadedLazyShelves)) {
                item(key = "indian_genres_playlists_carousel", contentType = "editorial_playlist_carousel") {
                    LaunchedEffect(Unit) {
                        onLoadLazyShelf(LazyPlatformShelfKey.INDIAN_GENRES)
                    }
                    if (filteredIndianGenrePlaylists.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeaderRow(
                                title = "Indian Genres & Regional Hits",
                                subtitle = "Punjabi Pop, Desi Hip-Hop, Indian Indie, Tamil, Telugu, Malayalam & Sufi",
                                isLoadingInline = LazyPlatformShelfKey.INDIAN_GENRES in loadingLazyShelves
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.testTag("indian_genre_playlist_carousel_row")
                            ) {
                                items(
                                    items = filteredIndianGenrePlaylists,
                                    key = { "india_genre_pl_${it.id}" },
                                    contentType = { "editorial_playlist_card" }
                                ) { playlist ->
                                    EditorialPlaylistCarouselCard(
                                        playlist = playlist,
                                        onCardClick = { onOpenPlaylist(playlist) },
                                        onQuickPlayClick = { onPlayPlaylist(playlist) }
                                    )
                                }
                            }
                        }
                    } else {
                        LazyPlatformShelfSkeletonSection(
                            title = "Indian Genres & Regional Hits",
                            subtitle = "Punjabi Pop, Desi Hip-Hop, Indian Indie, Tamil, Telugu, Malayalam & Sufi",
                            isLoading = LazyPlatformShelfKey.INDIAN_GENRES in loadingLazyShelves,
                            modifier = Modifier.testTag("lazy_shelf_loading_indian_genres")
                        )
                    }
                }
            }

            // 6. Horizontal Carousel: Global Top Hits & Viral Charts Playlists (Expands Lazily from Deezer/YT Music/JioSaavn)
            if (filteredTopPlaylists.isNotEmpty() || (!isIndianFilter && LazyPlatformShelfKey.GLOBAL_TOP_EXPANDED !in loadedLazyShelves)) {
                item(key = "top_hits_playlists_carousel", contentType = "live_playlist_carousel") {
                    LaunchedEffect(Unit) {
                        onLoadLazyShelf(LazyPlatformShelfKey.GLOBAL_TOP_EXPANDED)
                    }
                    if (filteredTopPlaylists.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeaderRow(
                                title = if (selectedMoodFilter != null && !isIndianFilter) {
                                    "$selectedMoodFilter Playlists"
                                } else {
                                    "Global Top Hits & Viral Charts"
                                },
                                subtitle = "Live playlists from Spotify, YouTube Music, Deezer & JioSaavn",
                                isLoadingInline = LazyPlatformShelfKey.GLOBAL_TOP_EXPANDED in loadingLazyShelves
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.testTag("top_hits_playlist_carousel_row")
                            ) {
                                items(
                                    items = filteredTopPlaylists,
                                    key = { "top_pl_${it.id}" },
                                    contentType = { "live_playlist_card" }
                                ) { playlist ->
                                    LivePlaylistCarouselCard(
                                        playlist = playlist,
                                        onCardClick = { onOpenPlaylist(playlist) },
                                        onQuickPlayClick = { onPlayPlaylist(playlist) }
                                    )
                                }
                            }
                        }
                    } else {
                        LazyPlatformShelfSkeletonSection(
                            title = "Global Top Hits & Viral Charts",
                            subtitle = "Live playlists from Spotify, YouTube Music, Deezer & JioSaavn",
                            isLoading = LazyPlatformShelfKey.GLOBAL_TOP_EXPANDED in loadingLazyShelves,
                            modifier = Modifier.testTag("lazy_shelf_loading_global_top")
                        )
                    }
                }
            }

            // 7. Horizontal Carousel: New Releases & Mood Playlists — Lazy-Loaded on Scroll or Filter Selection
            val isMoodOrNewFilter = selectedMoodFilter in setOf("Moods", "New Releases")
            if (selectedMoodFilter == null || isMoodOrNewFilter) {
                if (filteredMoodPlaylists.isNotEmpty() || LazyPlatformShelfKey.GLOBAL_MOODS !in loadedLazyShelves) {
                    item(key = "new_and_mood_playlists_carousel", contentType = "editorial_playlist_carousel") {
                        LaunchedEffect(Unit) {
                            onLoadLazyShelf(LazyPlatformShelfKey.GLOBAL_MOODS)
                        }
                        if (filteredMoodPlaylists.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionHeaderRow(
                                    title = "New Releases & Mood Playlists",
                                    subtitle = "New Music Friday, Late Night Vibes, Deep Focus & Workout",
                                    isLoadingInline = LazyPlatformShelfKey.GLOBAL_MOODS in loadingLazyShelves
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.testTag("mood_playlist_carousel_row")
                                ) {
                                    items(
                                        items = filteredMoodPlaylists,
                                        key = { "mood_pl_${it.id}" },
                                        contentType = { "editorial_playlist_card" }
                                    ) { playlist ->
                                        EditorialPlaylistCarouselCard(
                                            playlist = playlist,
                                            onCardClick = { onOpenPlaylist(playlist) },
                                            onQuickPlayClick = { onPlayPlaylist(playlist) }
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyPlatformShelfSkeletonSection(
                                title = "New Releases & Mood Playlists",
                                subtitle = "New Music Friday, Late Night Vibes, Deep Focus & Workout",
                                isLoading = LazyPlatformShelfKey.GLOBAL_MOODS in loadingLazyShelves,
                                modifier = Modifier.testTag("lazy_shelf_loading_global_moods")
                            )
                        }
                    }
                }
            }

            // 8. Horizontal Carousel: Global Genre Playlists — Lazy-Loaded on Scroll or Genres Filter
            if (selectedMoodFilter == null || selectedMoodFilter.equals("Genres", ignoreCase = true)) {
                if (filteredGenrePlaylists.isNotEmpty() || LazyPlatformShelfKey.GLOBAL_GENRES !in loadedLazyShelves) {
                    item(key = "genre_playlists_carousel", contentType = "live_playlist_carousel") {
                        LaunchedEffect(Unit) {
                            onLoadLazyShelf(LazyPlatformShelfKey.GLOBAL_GENRES)
                        }
                        if (filteredGenrePlaylists.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionHeaderRow(
                                    title = "Global Genre Playlists",
                                    subtitle = "Pop Rising, RapCaviar Hip-Hop, Electronic & Lo-Fi",
                                    isLoadingInline = LazyPlatformShelfKey.GLOBAL_GENRES in loadingLazyShelves
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.testTag("genre_playlist_carousel_row")
                                ) {
                                    items(
                                        items = filteredGenrePlaylists,
                                        key = { "genre_pl_${it.id}" },
                                        contentType = { "live_playlist_card" }
                                    ) { playlist ->
                                        LivePlaylistCarouselCard(
                                            playlist = playlist,
                                            onCardClick = { onOpenPlaylist(playlist) },
                                            onQuickPlayClick = { onPlayPlaylist(playlist) }
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyPlatformShelfSkeletonSection(
                                title = "Global Genre Playlists",
                                subtitle = "Pop Rising, RapCaviar Hip-Hop, Electronic & Lo-Fi",
                                isLoading = LazyPlatformShelfKey.GLOBAL_GENRES in loadingLazyShelves,
                                modifier = Modifier.testTag("lazy_shelf_loading_global_genres")
                            )
                        }
                    }
                }
            }

            // 9. Numbered Live India & Global Top Charts Songs (01..15)
            if (topChartsSongs.isNotEmpty()) {
                item(key = "top_charts_header", contentType = "section_header") {
                    SectionHeaderRow(
                        title = if (isIndianFilter) "Live Top India Charts" else "Live India & Global Top Charts",
                        subtitle = "Real-time top streamed tracks across India & Worldwide"
                    )
                }

                itemsIndexed(
                    items = topChartsSongs,
                    key = { _, song -> "chart_${song.id}" },
                    contentType = { _, _ -> "chart_song_row" }
                ) { index, song ->
                    val isCurrentTrack = currentTrackId == song.id
                    TopChartSongRow(
                        rank = index + 1,
                        song = song,
                        isCurrentTrack = isCurrentTrack,
                        isPlaying = isCurrentTrack && isPlaying,
                        isResolving = isCurrentTrack && isResolvingStream,
                        onPlayClick = { onPlaySong(song, topChartsSongs) },
                        onAddToQueueClick = { onAddToQueue(song) },
                        onToggleFavoriteClick = { onToggleFavorite(song) },
                        onOpenArtistClick = { onOpenArtist(song.artist) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Playlist Detail Screen (Opens when tapping any Playlist Card on the Home Screen)
 */
@Composable
private fun PlaylistDetailScreen(
    playlist: PlaylistItem,
    tracks: List<SongItem>,
    downloadedSongIds: Set<String>,
    batchDownloadProgress: BatchDownloadProgress?,
    isLoadingTracks: Boolean,
    hasMoreTracks: Boolean,
    isLoadingMoreTracks: Boolean,
    isSyncingPlaylist: Boolean,
    isPlaylistFavorite: Boolean,
    canEditTracks: Boolean = false,
    canRenamePlaylist: Boolean = false,
    currentTrackId: String?,
    isPlaying: Boolean,
    isResolvingStream: Boolean,
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onDownloadAll: () -> Unit,
    onCancelBatchDownload: () -> Unit = {},
    onLoadMoreTracks: () -> Unit,
    onSyncPlaylist: () -> Unit,
    onToggleFavoritePlaylist: () -> Unit,
    onRenamePlaylist: (String, String) -> Unit = { _, _ -> },
    onRemoveTrack: (SongItem) -> Unit = {},
    onMoveTrack: (Int, Int) -> Unit = { _, _ -> },
    onPlaySong: (SongItem) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onAddToQueue: (SongItem) -> Unit,
    onOpenArtist: (String) -> Unit = {}
) {
    val (ambientPrimary, ambientSecondary) = rememberDynamicArtworkGlow(playlist.artworkUrl)
    val allDownloaded = remember(tracks, downloadedSongIds) {
        tracks.isNotEmpty() && tracks.all { it.id in downloadedSongIds || it.isDownloaded }
    }
    val activeBatchForThis = batchDownloadProgress?.takeIf {
        it.isActive && it.collectionTitle.equals(playlist.title, ignoreCase = true)
    }
    var selectedSortOrder by remember { mutableStateOf(TrackSortOrder.DEFAULT) }
    var isEditMode by remember(playlist.id) { mutableStateOf(false) }
    var showRenameDialog by remember(playlist.id) { mutableStateOf(false) }
    val sortedTracks = remember(tracks, selectedSortOrder, downloadedSongIds, isEditMode) {
        if (isEditMode) tracks else sortTracksByOrder(tracks, selectedSortOrder, downloadedSongIds)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(listState, sortedTracks.size, hasMoreTracks, isLoadingMoreTracks) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to layoutInfo.totalItemsCount
        }.collect { (lastVisible, totalItems) ->
            if (!isLoadingMoreTracks && hasMoreTracks && totalItems > 0 && lastVisible >= totalItems - 4) {
                onLoadMoreTracks()
            }
        }
    }

    val playlistBgColor = PitchBlack

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(playlistBgColor)
            .drawWithCache {
                val bgBrush = Brush.verticalGradient(
                    colors = listOf(
                        ambientPrimary.copy(alpha = 0.22f),
                        ambientSecondary.copy(alpha = 0.10f),
                        playlistBgColor
                    ),
                    startY = 0f,
                    endY = 780f
                )
                onDrawBehind {
                    drawRect(bgBrush)
                }
            }
            .testTag("playlist_detail_screen")
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Bar with Soft Tonal Back Button + Source Platform Badge + Rename/Sync/Favorite Controls
            item(key = "playlist_detail_top_bar", contentType = "playlist_top_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SoftTonalCircleIconButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        size = 42.dp,
                        iconSize = 20.dp,
                        modifier = Modifier.testTag("playlist_detail_back_button")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PlatformBadgePill(
                            platform = playlist.sourcePlatform
                        )

                        if (canRenamePlaylist) {
                            SoftTonalCircleIconButton(
                                icon = Icons.Outlined.Edit,
                                contentDescription = "Rename playlist",
                                onClick = { showRenameDialog = true },
                                size = 42.dp,
                                iconSize = 18.dp,
                                activeTint = ElectricMint,
                                inactiveTint = AlabasterWhite,
                                containerColor = CarbonSurface,
                                modifier = Modifier.testTag("playlist_detail_rename_button")
                            )
                        }

                        SoftTonalCircleIconButton(
                            icon = Icons.Outlined.Refresh,
                            contentDescription = "Sync Playlist from ${playlist.sourcePlatform}",
                            onClick = onSyncPlaylist,
                            size = 42.dp,
                            iconSize = 19.dp,
                            isActive = isSyncingPlaylist,
                            activeTint = CyberCyan,
                            inactiveTint = AlabasterWhite,
                            containerColor = CarbonSurface,
                            modifier = Modifier.testTag("playlist_detail_sync_button")
                        )

                        AnimatedFavoriteButton(
                            isFavorite = isPlaylistFavorite,
                            onClick = onToggleFavoritePlaylist,
                            contentDescription = if (isPlaylistFavorite) "Remove playlist from favorites" else "Save playlist to favorites",
                            size = 42.dp,
                            iconSize = 20.dp,
                            activeTint = SunsetCoral,
                            inactiveTint = AlabasterWhite,
                            containerColor = CarbonSurface,
                            modifier = Modifier.testTag("playlist_detail_favorite_button")
                        )
                    }
                }
            }

            // Playlist Cover Art, Title, Subtitle & Play All / Shuffle / Download All Controls
            item(key = "playlist_detail_hero_header", contentType = "playlist_hero_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OptimizedArtworkImage(
                        artworkUrl = playlist.artworkUrl,
                        contentDescription = playlist.title,
                        targetSizeDp = 204.dp,
                        modifier = Modifier
                            .size(204.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(CarbonSurface)
                            .border(
                                width = 0.85.dp,
                                color = ambientPrimary.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(22.dp)
                            )
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = playlist.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = AlabasterWhite,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = playlist.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSlate,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (tracks.isNotEmpty()) {
                            Text(
                                text = if (hasMoreTracks) {
                                    "${tracks.size}+ tracks • Scroll for more"
                                } else {
                                    "${tracks.size} tracks • Live Stream Ready"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = CyberCyan
                            )
                        }
                    }

                    // Spacious Solid Mint Play All, Matte Shuffle & Circular Download Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPlayAll,
                            enabled = tracks.isNotEmpty() || !isLoadingTracks,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricMint,
                                contentColor = ThemeOnPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
                            modifier = Modifier.testTag("playlist_play_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play All",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = onShufflePlay,
                            enabled = tracks.isNotEmpty() || !isLoadingTracks,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(0.85.dp, SubtleBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CarbonSurface,
                                contentColor = AlabasterWhite
                            ),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                            modifier = Modifier.testTag("playlist_shuffle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = null,
                                tint = ElectricMint,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Shuffle",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        // Compact Circular Matte Download Collection Icon Button (Tap again while active to cancel)
                        if (activeBatchForThis != null) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan.copy(alpha = 0.16f))
                                    .border(0.85.dp, CyberCyan.copy(alpha = 0.65f), CircleShape)
                                    .clickable(onClick = onCancelBatchDownload)
                                    .testTag("playlist_download_all_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = CyberCyan,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            SoftTonalCircleIconButton(
                                icon = if (allDownloaded) Icons.Filled.DownloadDone else Icons.Outlined.Download,
                                contentDescription = if (allDownloaded) "Playlist downloaded" else "Download entire playlist",
                                onClick = {
                                    if (tracks.isNotEmpty() && !allDownloaded) {
                                        onDownloadAll()
                                    }
                                },
                                size = 44.dp,
                                iconSize = 20.dp,
                                isActive = allDownloaded,
                                activeTint = CyberCyan,
                                inactiveTint = AlabasterWhite,
                                containerColor = CarbonSurface,
                                modifier = Modifier.testTag("playlist_download_all_button")
                            )
                        }
                    }

                    // Uncongested Batch Progress Pill Beneath Action Row with Cancel button
                    if (activeBatchForThis != null) {
                        Surface(
                            color = CyberCyan.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.border(
                                width = 0.5.dp,
                                color = CyberCyan.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(50)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Downloading ${activeBatchForThis.completedCount}/${activeBatchForThis.totalCount} tracks...",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = CyberCyan
                                )
                                Text(
                                    text = "Cancel",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = SunsetCoral,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(SunsetCoral.copy(alpha = 0.16f))
                                        .clickable(onClick = onCancelBatchDownload)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .testTag("playlist_cancel_batch_download_button")
                                )
                            }
                        }
                    }
                }
            }

            if (tracks.size > 1 || (canEditTracks && tracks.isNotEmpty())) {
                item(key = "playlist_detail_sort_chips", contentType = "sort_chips_row") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (canEditTracks && tracks.isNotEmpty()) {
                            Surface(
                                color = if (isEditMode) ElectricMint.copy(alpha = 0.18f) else CarbonSurface,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(
                                        width = 0.75.dp,
                                        color = if (isEditMode) ElectricMint else SubtleBorder,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable {
                                        if (!isEditMode) {
                                            selectedSortOrder = TrackSortOrder.DEFAULT
                                        }
                                        isEditMode = !isEditMode
                                    }
                                    .testTag("playlist_edit_mode_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isEditMode) Icons.Filled.CheckCircle else Icons.Outlined.Edit,
                                        contentDescription = if (isEditMode) "Done editing tracks" else "Edit playlist tracks",
                                        tint = if (isEditMode) ElectricMint else AlabasterWhite,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (isEditMode) "Done" else "Edit Tracks",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isEditMode) ElectricMint else AlabasterWhite
                                    )
                                }
                            }
                        }

                        if (!isEditMode && tracks.size > 1) {
                            TrackSortOrderChipsRow(
                                selectedSortOrder = selectedSortOrder,
                                onSelectSortOrder = { selectedSortOrder = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (isLoadingTracks && tracks.isEmpty()) {
                item(key = "playlist_tracks_loading", contentType = "playlist_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = ElectricMint,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "Loading playlist tracks...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedSlate
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = sortedTracks,
                    key = { idx, song -> "pl_track_${song.id}_$idx" },
                    contentType = { _, _ -> "chart_song_row" }
                ) { index, song ->
                    val isCurrent = currentTrackId == song.id
                    TopChartSongRow(
                        rank = index + 1,
                        song = song,
                        isCurrentTrack = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        isResolving = isCurrent && isResolvingStream,
                        isEditMode = isEditMode && canEditTracks,
                        canMoveUp = index > 0,
                        canMoveDown = index < sortedTracks.lastIndex,
                        onMoveUpClick = { onMoveTrack(index, index - 1) },
                        onMoveDownClick = { onMoveTrack(index, index + 1) },
                        onRemoveClick = { onRemoveTrack(song) },
                        onPlayClick = { onPlaySong(song) },
                        onAddToQueueClick = { onAddToQueue(song) },
                        onToggleFavoriteClick = { onToggleFavorite(song) },
                        onOpenArtistClick = { onOpenArtist(song.artist) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (isLoadingMoreTracks || hasMoreTracks) {
                    item(key = "playlist_pagination_footer", contentType = "pagination_footer") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingMoreTracks) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(CarbonSurface)
                                        .border(0.75.dp, SubtleBorder, RoundedCornerShape(50))
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = ElectricMint,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Loading next 15 tracks...",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MutedSlate
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onLoadMoreTracks,
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(0.85.dp, SubtleBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = CarbonSurface,
                                        contentColor = AlabasterWhite
                                    ),
                                    modifier = Modifier.testTag("playlist_load_more_button")
                                ) {
                                    Text(
                                        text = "Load More Tracks",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showRenameDialog) {
            RenamePlaylistDialog(
                initialTitle = playlist.title,
                initialSubtitle = playlist.subtitle,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newTitle, newSubtitle ->
                    onRenamePlaylist(newTitle, newSubtitle)
                    showRenameDialog = false
                }
            )
        }
    }
}

@Composable
private fun RenamePlaylistDialog(
    initialTitle: String,
    initialSubtitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var subtitle by remember(initialSubtitle) { mutableStateOf(initialSubtitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        titleContentColor = AlabasterWhite,
        textContentColor = MutedSlate,
        title = {
            Text(
                text = "Rename Playlist",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricMint,
                        unfocusedBorderColor = SubtleBorder,
                        focusedTextColor = AlabasterWhite,
                        unfocusedTextColor = AlabasterWhite,
                        cursorColor = ElectricMint
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_playlist_title_input")
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Description") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricMint,
                        unfocusedBorderColor = SubtleBorder,
                        focusedTextColor = AlabasterWhite,
                        unfocusedTextColor = AlabasterWhite,
                        cursorColor = ElectricMint
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_playlist_subtitle_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, subtitle) },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricMint,
                    contentColor = ThemeOnPrimary
                ),
                modifier = Modifier.testTag("rename_playlist_confirm_button")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedSlate)
            }
        }
    )
}

@Composable
private fun TrackSortOrderChipsRow(
    selectedSortOrder: TrackSortOrder,
    onSelectSortOrder: (TrackSortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("track_sort_chips_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(end = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Sort,
                contentDescription = null,
                tint = MutedSlate,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "SORT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MutedSlate
            )
        }

        TrackSortOrder.entries.forEach { order ->
            val isSelected = selectedSortOrder == order
            Surface(
                color = if (isSelected) ElectricMint.copy(alpha = 0.16f) else CarbonSurface,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(
                        width = if (isSelected) 0.85.dp else 0.6.dp,
                        color = if (isSelected) ElectricMint else SubtleBorder.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(50)
                    )
                    .clickable { onSelectSortOrder(order) }
                    .testTag("sort_chip_${order.name.lowercase()}")
            ) {
                Text(
                    text = order.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) ElectricMint else AlabasterWhite.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun sortTracksByOrder(
    tracks: List<SongItem>,
    sortOrder: TrackSortOrder,
    downloadedSongIds: Set<String>
): List<SongItem> {
    return when (sortOrder) {
        TrackSortOrder.DEFAULT -> tracks
        TrackSortOrder.TITLE_ASC -> tracks.sortedBy { it.title.lowercase() }
        TrackSortOrder.ARTIST_ASC -> tracks.sortedWith(
            compareBy<SongItem> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
        )
        TrackSortOrder.DOWNLOADED_FIRST -> tracks.sortedByDescending {
            it.id in downloadedSongIds || it.isDownloaded
        }
    }
}

/**
 * Dedicated Artist Hub Screen:
 * - Full-bleed Ambient Glow Hero Header with Circular Artist Portrait, Verified Badge & Follow Button
 * - One-Tap "Artist Radio", "Play All", and "Shuffle" actions
 * - Horizontal "Albums & Singles" Discography Carousel (tapping any release opens AlbumDetailScreen)
 * - Numbered Popular Songs list (verified artist top songs)
 * - Horizontal "Similar Artists" Carousel (tapping any similar artist opens their Artist Hub)
 */
@Composable
private fun ArtistHubScreen(
    artistProfile: ArtistProfileData,
    isLoading: Boolean,
    isFollowing: Boolean,
    currentTrackId: String?,
    isPlaying: Boolean,
    isResolvingStream: Boolean,
    downloadedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onStartArtistRadio: () -> Unit,
    onToggleFollow: () -> Unit,
    onOpenAlbum: (AlbumReleaseItem) -> Unit,
    onPlaySong: (SongItem) -> Unit,
    onAddToQueue: (SongItem) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onToggleDownload: (SongItem) -> Unit,
    onAddToPlaylist: (SongItem) -> Unit,
    onSelectSimilarArtist: (String) -> Unit
) {
    val (ambientPrimary, ambientSecondary) = rememberDynamicArtworkGlow(artistProfile.artworkUrl)
    val artistListState = rememberLazyListState()

    val artistBgColor = PitchBlack

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(artistBgColor)
            .drawWithCache {
                val bgBrush = Brush.verticalGradient(
                    colors = listOf(
                        ambientPrimary.copy(alpha = 0.28f),
                        ambientSecondary.copy(alpha = 0.14f),
                        artistBgColor
                    ),
                    startY = 0f,
                    endY = 820f
                )
                onDrawBehind {
                    drawRect(bgBrush)
                }
            }
            .testTag("artist_hub_screen")
    ) {
        LazyColumn(
            state = artistListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top Navigation Bar with Follow / Following Pill
            item(key = "artist_hub_top_bar", contentType = "artist_top_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SoftTonalCircleIconButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        size = 42.dp,
                        iconSize = 20.dp,
                        modifier = Modifier.testTag("artist_hub_back_button")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = if (isFollowing) ElectricMint.copy(alpha = 0.18f) else CarbonSurface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(
                                    width = 0.85.dp,
                                    color = if (isFollowing) ElectricMint else SubtleBorder,
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable(onClick = onToggleFollow)
                                .testTag("artist_hub_follow_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFollowing) Icons.Filled.CheckCircle else Icons.Outlined.PersonAdd,
                                    contentDescription = if (isFollowing) "Following" else "Follow Artist",
                                    tint = if (isFollowing) ElectricMint else AlabasterWhite,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (isFollowing) "Following" else "Follow",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isFollowing) ElectricMint else AlabasterWhite
                                )
                            }
                        }

                        Surface(
                            color = CarbonSurface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.border(
                                0.75.dp,
                                ambientPrimary.copy(alpha = 0.5f),
                                RoundedCornerShape(50)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Verified,
                                    contentDescription = null,
                                    tint = ElectricMint,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "ARTIST HUB",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = AlabasterWhite
                                )
                            }
                        }
                    }
                }
            }

            // 2. Artist Hero Portrait, Name, Subtitle & Action Pills (Play All, Artist Radio, Shuffle)
            item(key = "artist_hub_hero_header", contentType = "artist_hero_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(156.dp)
                            .clip(CircleShape)
                            .background(CarbonSurface)
                            .border(2.dp, ambientPrimary.copy(alpha = 0.65f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artistProfile.artworkUrl.isNotBlank()) {
                            OptimizedArtworkImage(
                                artworkUrl = artistProfile.artworkUrl,
                                contentDescription = artistProfile.name,
                                targetSizeDp = 156.dp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = artistProfile.name,
                                tint = ambientPrimary,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = artistProfile.name,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = AlabasterWhite,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artistProfile.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSlate,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Solid Mint & Matte Dark Action Buttons Row: Play All | Artist Radio | Shuffle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPlayAll,
                            enabled = artistProfile.topSongs.isNotEmpty(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricMint,
                                contentColor = ThemeOnPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 11.dp),
                            modifier = Modifier.testTag("artist_hub_play_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Play All",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = onStartArtistRadio,
                            enabled = artistProfile.topSongs.isNotEmpty(),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(0.85.dp, SubtleBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CarbonSurface,
                                contentColor = AlabasterWhite
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
                            modifier = Modifier.testTag("artist_hub_radio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Radio,
                                contentDescription = null,
                                tint = ElectricMint,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Artist Radio",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        SoftTonalCircleIconButton(
                            icon = Icons.Default.Shuffle,
                            contentDescription = "Shuffle ${artistProfile.name}",
                            onClick = onShufflePlay,
                            size = 44.dp,
                            iconSize = 20.dp,
                            activeTint = ElectricMint,
                            containerColor = CarbonSurface,
                            enabled = artistProfile.topSongs.isNotEmpty(),
                            modifier = Modifier.testTag("artist_hub_shuffle_button")
                        )
                    }
                }
            }

            // 3. Albums & Singles Discography Carousel
            if (artistProfile.albums.isNotEmpty()) {
                item(key = "artist_hub_albums_section", contentType = "artist_albums_row") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SectionHeaderRow(
                            title = "Albums & Singles",
                            subtitle = "Tap any release to view full tracklist & download"
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.testTag("artist_hub_albums_row")
                        ) {
                            items(
                                items = artistProfile.albums,
                                key = { "artist_album_${it.id}_${it.title}" }
                            ) { album ->
                                Column(
                                    modifier = Modifier
                                        .width(142.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(CarbonSurface)
                                        .border(0.75.dp, SubtleBorder.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                                        .clickable { onOpenAlbum(album) }
                                        .padding(10.dp)
                                        .testTag("artist_album_card_${album.title}"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(122.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(CarbonElevated)
                                    ) {
                                        if (album.artworkUrl.isNotBlank()) {
                                            OptimizedArtworkImage(
                                                artworkUrl = album.artworkUrl,
                                                contentDescription = album.title,
                                                targetSizeDp = 122.dp,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Outlined.Album,
                                                contentDescription = album.title,
                                                tint = CyberCyan,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .align(Alignment.Center)
                                            )
                                        }

                                        Surface(
                                            color = PhotoScrimColor.copy(alpha = 0.78f),
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(6.dp)
                                        ) {
                                            Text(
                                                text = if (album.year.isNotBlank()) {
                                                    "${album.releaseType.uppercase()} • ${album.year}"
                                                } else {
                                                    album.releaseType.uppercase()
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = CyberCyan,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = album.title,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = AlabasterWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Text(
                                        text = album.artist,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedSlate,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Similar Artists Horizontal Row
            if (artistProfile.similarArtists.isNotEmpty()) {
                item(key = "artist_hub_similar_artists_section", contentType = "similar_artists_row") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SectionHeaderRow(
                            title = "Similar Artists & Collaborators",
                            subtitle = "Tap any artist to explore their top hits & radio"
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.testTag("artist_hub_similar_artists_row")
                        ) {
                            items(
                                items = artistProfile.similarArtists,
                                key = { "similar_artist_${it.name}" }
                            ) { related ->
                                Column(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(CarbonSurface)
                                        .border(0.75.dp, SubtleBorder.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                                        .clickable { onSelectSimilarArtist(related.name) }
                                        .padding(vertical = 12.dp, horizontal = 8.dp)
                                        .testTag("similar_artist_card_${related.name}"),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(CarbonElevated)
                                            .border(1.dp, ElectricMint.copy(alpha = 0.4f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (related.artworkUrl.isNotBlank()) {
                                            OptimizedArtworkImage(
                                                artworkUrl = related.artworkUrl,
                                                contentDescription = related.name,
                                                targetSizeDp = 64.dp,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Text(
                                                text = related.name.take(2).uppercase(),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.ExtraBold
                                                ),
                                                color = ElectricMint
                                            )
                                        }
                                    }

                                    Text(
                                        text = related.name,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = AlabasterWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )

                                    Text(
                                        text = related.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedSlate,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Verified Top Songs Section
            item(key = "artist_hub_top_songs_header", contentType = "section_header") {
                SectionHeaderRow(
                    title = "Top Songs by ${artistProfile.name}",
                    subtitle = "Verified catalog hits sorted by popularity"
                )
            }

            if (isLoading && artistProfile.topSongs.isEmpty()) {
                item(key = "artist_hub_loading_state", contentType = "loading_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = ElectricMint,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "Loading ${artistProfile.name}'s top songs...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedSlate
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = artistProfile.topSongs,
                    key = { idx, song -> "artist_top_${song.id}_$idx" },
                    contentType = { _, _ -> "song_track_row" }
                ) { _, song ->
                    val isCurrent = currentTrackId == song.id
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SongTrackRowCard(
                            song = song,
                            isCurrentTrack = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            isResolving = isCurrent && isResolvingStream,
                            isDownloaded = downloadedSongIds.contains(song.id),
                            isDownloading = downloadingSongIds.contains(song.id),
                            onPlayClick = { onPlaySong(song) },
                            onAddToQueueClick = { onAddToQueue(song) },
                            onToggleFavoriteClick = { onToggleFavorite(song) },
                            onToggleDownloadClick = { onToggleDownload(song) },
                            onAddToPlaylistClick = { onAddToPlaylist(song) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Album / Single Detail Screen:
 * - High-res Album Cover with Dynamic Ambient Glow
 * - Clickable Artist Badge to jump directly to Artist Hub
 * - Play Album, Shuffle, and One-Tap Batch Offline Download
 * - Full Numbered Album Tracklist
 */
@Composable
private fun AlbumDetailScreen(
    albumDetail: AlbumDetailData,
    isLoading: Boolean,
    downloadedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    batchDownloadProgress: BatchDownloadProgress?,
    currentTrackId: String?,
    isPlaying: Boolean,
    isResolvingStream: Boolean,
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit,
    onDownloadAlbum: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onPlaySong: (SongItem) -> Unit,
    onAddToQueue: (SongItem) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onToggleDownload: (SongItem) -> Unit,
    onAddToPlaylist: (SongItem) -> Unit
) {
    val (ambientPrimary, ambientSecondary) = rememberDynamicArtworkGlow(albumDetail.artworkUrl)
    val allDownloaded = remember(albumDetail.tracks, downloadedSongIds) {
        albumDetail.tracks.isNotEmpty() && albumDetail.tracks.all { it.id in downloadedSongIds || it.isDownloaded }
    }
    val activeBatchForAlbum = batchDownloadProgress?.takeIf {
        it.isActive && it.collectionTitle.equals(albumDetail.title, ignoreCase = true)
    }

    val albumBgColor = PitchBlack

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(albumBgColor)
            .drawWithCache {
                val bgBrush = Brush.verticalGradient(
                    colors = listOf(
                        ambientPrimary.copy(alpha = 0.26f),
                        ambientSecondary.copy(alpha = 0.12f),
                        albumBgColor
                    ),
                    startY = 0f,
                    endY = 820f
                )
                onDrawBehind {
                    drawRect(bgBrush)
                }
            }
            .testTag("album_detail_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Top Bar
            item(key = "album_detail_top_bar", contentType = "album_top_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SoftTonalCircleIconButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        size = 42.dp,
                        iconSize = 20.dp,
                        modifier = Modifier.testTag("album_detail_back_button")
                    )

                    Surface(
                        color = CarbonSurface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.border(
                            0.75.dp,
                            CyberCyan.copy(alpha = 0.5f),
                            RoundedCornerShape(50)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Album,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (albumDetail.year.isNotBlank()) {
                                    "ALBUM • ${albumDetail.year}"
                                } else {
                                    "ALBUM RELEASE"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = AlabasterWhite
                            )
                        }
                    }
                }
            }

            // 2. Album Cover Art, Title, Clickable Artist Badge & Play / Shuffle / Download Controls
            item(key = "album_detail_hero_header", contentType = "album_hero_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OptimizedArtworkImage(
                        artworkUrl = albumDetail.artworkUrl,
                        contentDescription = albumDetail.title,
                        targetSizeDp = 204.dp,
                        modifier = Modifier
                            .size(204.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(CarbonSurface)
                            .border(
                                width = 0.85.dp,
                                color = ambientPrimary.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(22.dp)
                            )
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = albumDetail.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = AlabasterWhite,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Surface(
                            color = CarbonSurface,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(0.65.dp, SunsetCoral.copy(alpha = 0.5f), RoundedCornerShape(50))
                                .clickable { onOpenArtist(albumDetail.artist) }
                                .testTag("album_detail_artist_link")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = SunsetCoral,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = albumDetail.artist,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = AlabasterWhite
                                )
                            }
                        }

                        Text(
                            text = albumDetail.subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = CyberCyan
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPlayAlbum,
                            enabled = albumDetail.tracks.isNotEmpty(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricMint,
                                contentColor = ThemeOnPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            modifier = Modifier.testTag("album_play_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play Album",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = onShuffleAlbum,
                            enabled = albumDetail.tracks.isNotEmpty(),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(0.85.dp, SubtleBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CarbonSurface,
                                contentColor = AlabasterWhite
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            modifier = Modifier.testTag("album_shuffle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = null,
                                tint = ElectricMint,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Shuffle",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        if (activeBatchForAlbum != null) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan.copy(alpha = 0.16f))
                                    .border(0.85.dp, CyberCyan.copy(alpha = 0.65f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = CyberCyan,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            SoftTonalCircleIconButton(
                                icon = if (allDownloaded) Icons.Filled.DownloadDone else Icons.Outlined.Download,
                                contentDescription = if (allDownloaded) "Album downloaded" else "Download entire album",
                                onClick = {
                                    if (albumDetail.tracks.isNotEmpty() && !allDownloaded) {
                                        onDownloadAlbum()
                                    }
                                },
                                size = 44.dp,
                                iconSize = 20.dp,
                                isActive = allDownloaded,
                                activeTint = CyberCyan,
                                inactiveTint = AlabasterWhite,
                                containerColor = CarbonSurface,
                                modifier = Modifier.testTag("album_download_all_button")
                            )
                        }
                    }

                    if (activeBatchForAlbum != null) {
                        Surface(
                            color = CyberCyan.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.border(
                                width = 0.5.dp,
                                color = CyberCyan.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(50)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Downloading ${activeBatchForAlbum.completedCount}/${activeBatchForAlbum.totalCount} tracks...",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = CyberCyan
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading && albumDetail.tracks.isEmpty()) {
                item(key = "album_tracks_loading", contentType = "loading_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = ElectricMint,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "Loading album tracks...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedSlate
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = albumDetail.tracks,
                    key = { idx, song -> "album_track_${song.id}_$idx" },
                    contentType = { _, _ -> "song_track_row" }
                ) { _, song ->
                    val isCurrent = currentTrackId == song.id
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SongTrackRowCard(
                            song = song,
                            isCurrentTrack = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            isResolving = isCurrent && isResolvingStream,
                            isDownloaded = downloadedSongIds.contains(song.id),
                            isDownloading = downloadingSongIds.contains(song.id),
                            onPlayClick = { onPlaySong(song) },
                            onAddToQueueClick = { onAddToQueue(song) },
                            onToggleFavoriteClick = { onToggleFavorite(song) },
                            onToggleDownloadClick = { onToggleDownload(song) },
                            onAddToPlaylistClick = { onAddToPlaylist(song) },
                            onOpenArtistClick = { onOpenArtist(song.artist) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Spotify-Style Category Filter Pills Row ("All", "Top Hits", "Viral", "New Releases", "Moods", "Genres")
 */
@Composable
private fun CategoryFilterPillsRow(
    moods: List<MoodCategoryItem>,
    selectedMood: String?,
    onSelectMood: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("category_filter_pills_row")
    ) {
        item(key = "chip_all", contentType = "filter_chip") {
            val isAllSelected = selectedMood == null
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isAllSelected) ElectricMint.copy(alpha = 0.18f) else CarbonSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelectMood(null) }
                    .border(
                        width = 0.75.dp,
                        color = if (isAllSelected) ElectricMint else SubtleBorder.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(50)
                    )
                    .testTag("filter_chip_all")
            ) {
                Text(
                    text = "All",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isAllSelected) ElectricMint else AlabasterWhite,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }

        items(
            items = moods,
            key = { "chip_${it.id}" },
            contentType = { "filter_chip" }
        ) { mood ->
            val isSelected = selectedMood.equals(mood.title, ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) ElectricMint.copy(alpha = 0.18f) else CarbonSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelectMood(mood.title) }
                    .border(
                        width = 0.75.dp,
                        color = if (isSelected) ElectricMint else SubtleBorder.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(50)
                    )
                    .testTag("filter_chip_${mood.id}")
            ) {
                Text(
                    text = mood.title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) ElectricMint else AlabasterWhite,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * 2-Column Compact Quick-Play Grid for Recently Played Songs near the top of Home Screen
 */
@Composable
private fun RecentlyPlayedSongsQuickGrid(
    songs: List<SongItem>,
    currentTrackId: String?,
    isPlaying: Boolean,
    isResolvingStream: Boolean,
    onPlaySong: (SongItem) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember(songs) { songs.take(6).chunked(2) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_recently_played_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = ElectricMint,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Recently Played",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = AlabasterWhite
                )
            }

            Text(
                text = "Clear",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MutedSlate,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onClearHistory)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("home_clear_recently_played_button")
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { song ->
                        val isCurrent = currentTrackId == song.id
                        RecentlyPlayedQuickTile(
                            song = song,
                            isCurrentTrack = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            isResolving = isCurrent && isResolvingStream,
                            onClick = { onPlaySong(song) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentlyPlayedQuickTile(
    song: SongItem,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isResolving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrentTrack) CarbonElevated else CarbonSurface)
            .border(
                width = if (isCurrentTrack) 0.9.dp else 0.75.dp,
                color = if (isCurrentTrack) ElectricMint.copy(alpha = 0.65f) else SubtleBorder.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag("recently_played_tile_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(CarbonElevated),
            contentAlignment = Alignment.Center
        ) {
            OptimizedArtworkImage(
                artworkUrl = song.artworkUrl,
                contentDescription = song.title,
                targetSizeDp = 56.dp,
                modifier = Modifier.fillMaxSize()
            )

            if (isResolving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PhotoScrimColor.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = ElectricMint,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else if (isCurrentTrack) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PhotoScrimColor.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    MiniWaveformIndicator(isPlaying = isPlaying)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = if (isCurrentTrack) ElectricMint else AlabasterWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.labelSmall,
                color = MutedSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Spotify-Style 2-Column Quick-Access Real Playlists Grid
 */
@Composable
private fun SpotifyQuickAccessPlaylistGrid(
    playlists: List<PlaylistItem>,
    onPlaylistClick: (PlaylistItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember(playlists) { playlists.take(6).chunked(2) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { playlist ->
                    SpotifyQuickAccessPlaylistTile(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SpotifyQuickAccessPlaylistTile(
    playlist: PlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CarbonSurface)
            .border(
                width = 0.75.dp,
                color = SubtleBorder.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag("quick_access_playlist_${playlist.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OptimizedArtworkImage(
            artworkUrl = playlist.artworkUrl,
            contentDescription = playlist.title,
            targetSizeDp = 56.dp,
            modifier = Modifier
                .size(56.dp)
                .background(CarbonElevated)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = AlabasterWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = playlist.sourcePlatform,
                style = MaterialTheme.typography.labelSmall,
                color = ElectricMint.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Amazon Music-Style Featured Spotlight Playlist Hero Card
 */
@Composable
private fun SpotlightPlaylistHeroCard(
    playlist: PlaylistItem,
    onOpenPlaylist: () -> Unit,
    onPlayAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(206.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CarbonSurface)
            .border(
                width = 0.75.dp,
                color = SubtleBorder,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onOpenPlaylist)
            .testTag("spotlight_hero_card")
    ) {
        val spotlightScrim = PhotoScrimColor
        OptimizedArtworkImage(
            artworkUrl = playlist.artworkUrl,
            contentDescription = playlist.title,
            targetSizeDp = 240.dp,
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val horizScrim = Brush.horizontalGradient(
                        colors = listOf(
                            spotlightScrim.copy(alpha = 0.94f),
                            spotlightScrim.copy(alpha = 0.78f),
                            spotlightScrim.copy(alpha = 0.35f)
                        )
                    )
                    val vertScrim = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            spotlightScrim.copy(alpha = 0.9f)
                        )
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(horizScrim)
                        drawRect(vertScrim)
                    }
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ElectricMint.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.border(
                        width = 0.75.dp,
                        color = ElectricMint.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricMint,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${playlist.sourcePlatform.uppercase()} • FEATURED PLAYLIST",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            ),
                            color = ElectricMint
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = playlist.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = PhotoForegroundColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = playlist.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhotoMutedColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Soft Minimal Tonal Circular Halo Play Button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(ElectricMint.copy(alpha = 0.18f))
                        .border(1.dp, ElectricMint.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(ElectricMint)
                            .clickable(onClick = onPlayAllClick)
                            .testTag("spotlight_play_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Playlist",
                            tint = ThemeOnPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    subtitle: String,
    isLoadingInline: Boolean = false,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = AlabasterWhite,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoadingInline) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(CarbonSurface)
                            .border(0.6.dp, SubtleBorder, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            color = ElectricMint,
                            strokeWidth = 1.6.dp,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Loading",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp
                            ),
                            color = MutedSlate
                        )
                    }
                }
                if (!actionLabel.isNullOrBlank() && onActionClick != null) {
                    Surface(
                        color = CarbonSurface,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(0.75.dp, ElectricMint.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .clickable(onClick = onActionClick)
                            .testTag("section_header_action_${title.lowercase().replace(" ", "_")}")
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = ElectricMint,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MutedSlate
        )
    }
}

@Composable
private fun LazyPlatformShelfSkeletonSection(
    title: String,
    subtitle: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeaderRow(
            title = title,
            subtitle = subtitle,
            isLoadingInline = isLoading
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false
        ) {
            items(count = 3, key = { "skeleton_$it" }) {
                Column(
                    modifier = Modifier.width(156.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(156.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CarbonSurface)
                            .border(0.75.dp, SubtleBorder.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = ElectricMint.copy(alpha = 0.8f),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(CarbonElevated)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.54f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(CarbonSurface)
                    )
                }
            }
        }
    }
}

/**
 * Horizontal Carousel Card for Real Playlists with Soft Tonal Play Button
 */
@Composable
private fun LivePlaylistCarouselCard(
    playlist: PlaylistItem,
    onCardClick: () -> Unit,
    onQuickPlayClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onCardClick)
            .testTag("playlist_card_${playlist.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(156.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CarbonSurface)
                .border(
                    width = 0.75.dp,
                    color = SubtleBorder.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            OptimizedArtworkImage(
                artworkUrl = playlist.artworkUrl,
                contentDescription = playlist.title,
                targetSizeDp = 156.dp,
                modifier = Modifier.fillMaxSize()
            )

            // Source platform pill in top-left corner
            PlatformBadgePill(
                platform = playlist.sourcePlatform,
                darkBackgroundOverlay = true,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )

            // Soft Minimal Tonal Circular Quick-Play badge in bottom-right corner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PhotoScrimColor.copy(alpha = 0.78f))
                    .border(0.75.dp, ElectricMint.copy(alpha = 0.65f), CircleShape)
                    .clickable(onClick = onQuickPlayClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Playlist",
                    tint = ElectricMint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = AlabasterWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = playlist.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MutedSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PickedForYouTrackCard(
    song: SongItem,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onArtistClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlayClick)
            .testTag("picked_for_you_song_${song.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CarbonSurface)
                .border(
                    width = if (isCurrentTrack) 1.2.dp else 0.75.dp,
                    color = if (isCurrentTrack) ElectricMint else SubtleBorder.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            OptimizedArtworkImage(
                artworkUrl = song.artworkUrl,
                contentDescription = song.title,
                targetSizeDp = 148.dp,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isCurrentTrack) ElectricMint else PhotoScrimColor.copy(alpha = 0.78f))
                    .border(
                        width = 0.75.dp,
                        color = ElectricMint.copy(alpha = 0.75f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCurrentTrack && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play ${song.title}",
                    tint = if (isCurrentTrack) ThemeOnPrimary else ElectricMint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = if (isCurrentTrack) ElectricMint else AlabasterWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MutedSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onArtistClick)
            )
        }
    }
}

/**
 * Editorial Playlist Card for New Releases & Moods Carousel
 */
@Composable
private fun EditorialPlaylistCarouselCard(
    playlist: PlaylistItem,
    onCardClick: () -> Unit,
    onQuickPlayClick: () -> Unit
) {
    val accentColors = remember(playlist.category) {
        when (playlist.category.lowercase()) {
            "new releases" -> listOf(WorkoutGradientStart, WorkoutGradientEnd)
            "viral" -> listOf(FocusGradientStart, FocusGradientEnd)
            "moods" -> listOf(MidnightGradientStart, MidnightGradientEnd)
            else -> listOf(ChillGradientStart, ChillGradientEnd)
        }
    }

    Box(
        modifier = Modifier
            .width(176.dp)
            .height(202.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CarbonSurface)
            .border(
                width = 0.75.dp,
                color = SubtleBorder.copy(alpha = 0.75f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onCardClick)
            .testTag("editorial_playlist_card_${playlist.id}")
    ) {
        val cardScrim = PhotoScrimColor
        OptimizedArtworkImage(
            artworkUrl = playlist.artworkUrl,
            contentDescription = playlist.title,
            targetSizeDp = 176.dp,
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val overlayBrush = Brush.verticalGradient(
                        colors = listOf(
                            cardScrim.copy(alpha = 0.25f),
                            cardScrim.copy(alpha = 0.65f),
                            cardScrim.copy(alpha = 0.94f)
                        )
                    )
                    val topStripeBrush = Brush.horizontalGradient(accentColors)
                    val stripeHeight = 4.dp.toPx()
                    onDrawWithContent {
                        drawContent()
                        drawRect(overlayBrush)
                        drawRect(
                            brush = topStripeBrush,
                            size = size.copy(height = stripeHeight)
                        )
                    }
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformBadgePill(
                    platform = playlist.sourcePlatform.ifBlank { playlist.category },
                    darkBackgroundOverlay = true
                )

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(PhotoScrimColor.copy(alpha = 0.75f))
                        .border(0.75.dp, ElectricMint.copy(alpha = 0.6f), CircleShape)
                        .clickable(onClick = onQuickPlayClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = ElectricMint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = PhotoForegroundColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = playlist.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PhotoMutedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Numbered Top Charts Row with Spring-Animated Like Button & Soft Minimal Tonal Circular Actions
 */
@Composable
private fun TopChartSongRow(
    rank: Int,
    song: SongItem,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isResolving: Boolean,
    isEditMode: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUpClick: () -> Unit = {},
    onMoveDownClick: () -> Unit = {},
    onRemoveClick: () -> Unit = {},
    onPlayClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onToggleFavoriteClick: () -> Unit,
    onOpenArtistClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rankColor = when {
        isCurrentTrack -> ElectricMint
        rank <= 3 -> CyberCyan
        else -> MutedSlate
    }
    val rankText = if (rank < 10) "0$rank" else "$rank"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCurrentTrack) CarbonElevated else CarbonSurface)
            .border(
                width = if (isCurrentTrack) 0.75.dp else 0.5.dp,
                color = if (isCurrentTrack) ElectricMint.copy(alpha = 0.5f) else SubtleBorder.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onPlayClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag("chart_row_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rankText,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = rankColor,
            modifier = Modifier.width(28.dp)
        )

        Box(contentAlignment = Alignment.Center) {
            OptimizedArtworkImage(
                artworkUrl = song.artworkUrl,
                contentDescription = song.title,
                targetSizeDp = 48.dp,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CarbonElevated)
            )

            if (isResolving) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PhotoScrimColor.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = ElectricMint,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else if (isCurrentTrack) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PhotoScrimColor.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    MiniWaveformIndicator(isPlaying = isPlaying)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = if (isCurrentTrack) ElectricMint else AlabasterWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = if (onOpenArtistClick != null && song.artist.isNotBlank()) {
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenArtistClick)
                        .padding(vertical = 1.dp)
                        .testTag("chart_row_artist_${song.id}")
                } else {
                    Modifier
                }
            ) {
                Text(
                    text = "${song.artist} • ${song.albumName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isEditMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SoftTonalCircleIconButton(
                    icon = Icons.Outlined.ArrowUpward,
                    contentDescription = "Move track up",
                    onClick = { if (canMoveUp) onMoveUpClick() },
                    size = 34.dp,
                    iconSize = 17.dp,
                    inactiveTint = if (canMoveUp) AlabasterWhite else MutedSlate.copy(alpha = 0.35f),
                    containerColor = CarbonElevated,
                    modifier = Modifier.testTag("move_up_track_${song.id}")
                )
                SoftTonalCircleIconButton(
                    icon = Icons.Outlined.ArrowDownward,
                    contentDescription = "Move track down",
                    onClick = { if (canMoveDown) onMoveDownClick() },
                    size = 34.dp,
                    iconSize = 17.dp,
                    inactiveTint = if (canMoveDown) AlabasterWhite else MutedSlate.copy(alpha = 0.35f),
                    containerColor = CarbonElevated,
                    modifier = Modifier.testTag("move_down_track_${song.id}")
                )
                SoftTonalCircleIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = "Remove track from playlist",
                    onClick = onRemoveClick,
                    size = 34.dp,
                    iconSize = 17.dp,
                    inactiveTint = SunsetCoral,
                    containerColor = SunsetCoral.copy(alpha = 0.14f),
                    modifier = Modifier.testTag("remove_track_${song.id}")
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AnimatedFavoriteButton(
                    isFavorite = song.isFavorite,
                    onClick = onToggleFavoriteClick,
                    contentDescription = stringResource(R.string.favorite_cd),
                    size = 36.dp,
                    iconSize = 17.dp,
                    activeTint = SunsetCoral,
                    inactiveTint = MutedSlate,
                    containerColor = CarbonElevated,
                    modifier = Modifier.testTag("favorite_button_${song.id}")
                )

                SoftTonalCircleIconButton(
                    icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                    contentDescription = stringResource(R.string.add_to_queue_cd),
                    onClick = onAddToQueueClick,
                    size = 36.dp,
                    iconSize = 18.dp,
                    inactiveTint = AlabasterWhite.copy(alpha = 0.85f),
                    containerColor = CarbonElevated,
                    modifier = Modifier.testTag("add_queue_button_${song.id}")
                )
            }
        }
    }
}

/**
 * Returns true only when the user's search query explicitly asks for playlists, charts,
 * mood collections, or curated mixes. Single-song and artist searches return false so
 * Songs appear immediately at the top of the Search results without playlist clutter.
 */
private fun hasPlaylistSearchIntent(query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return false
    val playlistKeywords = listOf(
        "playlist",
        "playlists",
        "top 50",
        "top 100",
        "top hits",
        "biggest hits",
        "all out",
        "best of",
        "chart",
        "charts",
        "viral",
        "trending",
        "workout",
        "gym",
        "party",
        "chill",
        "study",
        "focus",
        "sleep",
        "lofi",
        "lo-fi",
        "mashup",
        "jukebox",
        "nonstop",
        "non-stop",
        "collection",
        "mix",
        "mixes",
        "80s",
        "90s",
        "2000s",
        "instagram",
        "insta",
        "reels",
        "road trip",
        "drive",
        "romantic hits",
        "sad songs",
        "love songs",
        "punjabi hits",
        "bollywood hits",
        "indie india"
    )
    return playlistKeywords.any { keyword ->
        if (keyword.contains(" ")) {
            q.contains(keyword)
        } else {
            Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(q)
        }
    }
}

/**
 * Search Tab Content (Live Multi-Source Search + Last Searched History & Results)
 */
@Composable
private fun SearchTabScreenContent(
    currentTrackId: String?,
    isPlaying: Boolean,
    isResolvingStream: Boolean,
    searchQuery: String,
    searchSuggestions: List<String>,
    searchResults: List<SongItem>,
    searchPlaylistResults: List<PlaylistItem>,
    searchArtistResults: List<SearchArtistItem>,
    lastSearchedQuery: String,
    lastSearchResults: List<SongItem>,
    lastSearchPlaylistResults: List<PlaylistItem>,
    lastSearchArtistResults: List<SearchArtistItem>,
    selectedSearchFilter: SearchFilterType,
    importedPlaylists: List<PlaylistItem>,
    downloadedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    recentSearches: List<String>,
    isSearching: Boolean,
    innerPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onRecordSearchHistory: (String) -> Unit,
    onSelectSearchFilter: (SearchFilterType) -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
    onClearAllRecentSearches: () -> Unit,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onAddToQueue: (SongItem) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onToggleDownload: (SongItem) -> Unit,
    onAddToPlaylist: (SongItem) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onPlayPlaylist: (PlaylistItem) -> Unit,
    onToggleFavoritePlaylist: (PlaylistItem) -> Unit,
    onClearFocus: () -> Unit
) {
    val isHistoryIdleView = searchQuery.isBlank()
    val activeQuery = if (!isHistoryIdleView) searchQuery else lastSearchedQuery
    val activeArtistsSource = if (!isHistoryIdleView) searchArtistResults else lastSearchArtistResults
    val activePlaylistsSource = if (!isHistoryIdleView) searchPlaylistResults else lastSearchPlaylistResults
    val activeSongsSource = if (!isHistoryIdleView) searchResults else lastSearchResults
    val queryHasPlaylistIntent = remember(activeQuery) {
        hasPlaylistSearchIntent(activeQuery)
    }

    val filteredArtistResults = remember(activeArtistsSource, selectedSearchFilter) {
        when (selectedSearchFilter) {
            SearchFilterType.ALL, SearchFilterType.ARTISTS -> activeArtistsSource
            else -> emptyList()
        }
    }

    val filteredPlaylistResults = remember(activePlaylistsSource, selectedSearchFilter, queryHasPlaylistIntent) {
        when (selectedSearchFilter) {
            SearchFilterType.ALL -> if (queryHasPlaylistIntent) activePlaylistsSource else emptyList()
            SearchFilterType.ARTISTS -> emptyList()
            SearchFilterType.PLAYLISTS -> activePlaylistsSource
            SearchFilterType.SONGS -> emptyList()
            SearchFilterType.SPOTIFY -> activePlaylistsSource.filter {
                it.sourcePlatform.equals("Spotify", ignoreCase = true)
            }
            SearchFilterType.YOUTUBE_MUSIC -> activePlaylistsSource.filter {
                it.sourcePlatform.contains("YouTube", ignoreCase = true)
            }
            SearchFilterType.JIOSAAVN -> activePlaylistsSource.filter {
                it.sourcePlatform.contains("JioSaavn", ignoreCase = true) ||
                    it.sourcePlatform.contains("Saavn", ignoreCase = true)
            }
            SearchFilterType.DEEZER -> activePlaylistsSource.filter {
                it.sourcePlatform.contains("Deezer", ignoreCase = true)
            }
        }
    }

    val filteredSongResults = remember(activeSongsSource, selectedSearchFilter) {
        when (selectedSearchFilter) {
            SearchFilterType.ARTISTS, SearchFilterType.PLAYLISTS, SearchFilterType.SPOTIFY -> emptyList()
            else -> activeSongsSource
        }
    }

    val topMatchedArtist = filteredArtistResults.firstOrNull()
    val shouldPromoteArtistFirst = remember(
        topMatchedArtist,
        activeQuery,
        filteredSongResults,
        selectedSearchFilter
    ) {
        if (topMatchedArtist == null) {
            false
        } else if (selectedSearchFilter == SearchFilterType.ARTISTS) {
            true
        } else if (selectedSearchFilter != SearchFilterType.ALL) {
            false
        } else {
            val songMatchCount = filteredSongResults.count {
                it.artist.contains(topMatchedArtist.name, ignoreCase = true)
            }
            val topSongExactTitleMatch = filteredSongResults.firstOrNull()?.let { firstSong ->
                firstSong.title.equals(activeQuery.trim(), ignoreCase = true)
            } ?: false

            // Promote Prominent Artist Hero Card first whenever query matches an artist strongly or dominates returned songs
            (topMatchedArtist.relevanceScore >= 85 && (!topSongExactTitleMatch || topMatchedArtist.relevanceScore >= 170)) ||
                songMatchCount >= 2 ||
                filteredSongResults.isEmpty()
        }
    }

    val searchListState = rememberLazyListState()

    LazyColumn(
        state = searchListState,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .widthIn(max = 680.dp)
            .testTag("search_tab_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "search_tab_bar", contentType = "search_bar") {
            MinimalSearchBar(
                searchQuery = searchQuery,
                isSearching = isSearching,
                onQueryChange = onQueryChange,
                onSubmitSearch = { q ->
                    onClearFocus()
                    onSubmitSearch(q)
                },
                onClearSearch = { onQueryChange("") }
            )
        }

        // Live Autocomplete Suggestions Pills while typing
        if (searchQuery.trim().length >= 2 && searchSuggestions.isNotEmpty()) {
            item(key = "search_live_autocomplete_row", contentType = "search_suggestions") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_autocomplete_suggestions_row")
                ) {
                    items(
                        items = searchSuggestions,
                        key = { "suggestion_$it" }
                    ) { suggestion ->
                        Surface(
                            color = CarbonElevated,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(0.75.dp, ElectricMint.copy(alpha = 0.35f), RoundedCornerShape(50))
                                .clickable {
                                    onClearFocus()
                                    onSubmitSearch(suggestion)
                                }
                                .testTag("search_suggestion_chip_$suggestion")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = ElectricMint,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = AlabasterWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Horizontal Search Filter Chips (All, Artists, Playlists, Songs, Spotify, YouTube Music, JioSaavn, Deezer)
        item(key = "search_filter_chips_row", contentType = "search_filter_chips") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_filter_chips_row")
            ) {
                items(
                    items = SearchFilterType.entries,
                    key = { "search_filter_${it.name}" }
                ) { filter ->
                    val isSelected = selectedSearchFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectSearchFilter(filter) },
                        label = {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CarbonSurface,
                            labelColor = MutedSlate,
                            selectedContainerColor = ElectricMint.copy(alpha = 0.16f),
                            selectedLabelColor = ElectricMint
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = SubtleBorder,
                            selectedBorderColor = ElectricMint,
                            borderWidth = 0.75.dp,
                            selectedBorderWidth = 1.dp
                        ),
                        modifier = Modifier.testTag("search_filter_chip_${filter.name.lowercase()}")
                    )
                }
            }
        }

        // Idle / Empty Query State: Container-less & Borderless Search History
        if (isHistoryIdleView) {
            if (recentSearches.isNotEmpty()) {
                item(key = "last_searched_header", contentType = "section_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = null,
                                tint = ElectricMint,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Recent Searches",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = AlabasterWhite,
                                modifier = Modifier.testTag("last_searched_section_header")
                            )
                        }

                        Text(
                            text = "Clear All",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MutedSlate,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onClearAllRecentSearches)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("clear_all_recent_searches_button")
                        )
                    }
                }

                itemsIndexed(
                    items = recentSearches.take(8),
                    key = { _, query -> "recent_query_$query" },
                    contentType = { _, _ -> "recent_search_row" }
                ) { _, recent ->
                    LastSearchedQueryRowCard(
                        query = recent,
                        isLatest = recent.equals(lastSearchedQuery, ignoreCase = true),
                        onSelectQuery = {
                            onClearFocus()
                            onSubmitSearch(recent)
                        },
                        onRemoveQuery = { onRemoveRecentSearch(recent) }
                    )
                }
            } else if (filteredPlaylistResults.isEmpty() && filteredSongResults.isEmpty() && filteredArtistResults.isEmpty()) {
                item(key = "empty_recent_searches_state", contentType = "empty_state") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 36.dp)
                            .testTag("empty_recent_searches_card"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(ElectricMint.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = null,
                                tint = ElectricMint,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                        Text(
                            text = "No Recent Searches Yet",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = AlabasterWhite
                        )
                        Text(
                            text = "Search for artists, songs, or playlists across Spotify, YouTube Music, JioSaavn & Deezer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSlate,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // 1. Artist-First Prominent Hero Card when an artist matches (always above playlists & songs in live search)
        if (!isHistoryIdleView && shouldPromoteArtistFirst && topMatchedArtist != null) {
            val artistTopSong = filteredSongResults.firstOrNull {
                it.artist.contains(topMatchedArtist.name, ignoreCase = true)
            } ?: filteredSongResults.firstOrNull()

            item(key = "search_top_artist_hero_${topMatchedArtist.id}", contentType = "top_artist_hero") {
                SearchArtistHeroCard(
                    artist = topMatchedArtist,
                    topSong = artistTopSong,
                    onOpenArtistClick = {
                        onClearFocus()
                        onRecordSearchHistory(searchQuery)
                        onOpenArtist(topMatchedArtist.name)
                    },
                    onPlayTopSongClick = { song ->
                        onClearFocus()
                        onRecordSearchHistory(searchQuery)
                        onPlaySong(song, filteredSongResults)
                    }
                )
            }

            if (selectedSearchFilter == SearchFilterType.ARTISTS && filteredArtistResults.size > 1) {
                item(key = "search_more_artists_header", contentType = "section_header") {
                    Text(
                        text = "More Artists",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = AlabasterWhite,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                itemsIndexed(
                    items = filteredArtistResults.drop(1),
                    key = { _, artist -> "search_artist_${artist.id}_${artist.name}" },
                    contentType = { _, _ -> "search_artist_row" }
                ) { _, artist ->
                    SearchArtistRowCard(
                        artist = artist,
                        onOpenArtistClick = {
                            onClearFocus()
                            onRecordSearchHistory(searchQuery)
                            onOpenArtist(artist.name)
                        }
                    )
                }
            }
        }

        if (filteredPlaylistResults.isNotEmpty()) {
            item(key = "search_playlists_header", contentType = "section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (!isHistoryIdleView) {
                            "Playlists"
                        } else if (lastSearchedQuery.isNotBlank()) {
                            "Last Searched Playlists • \"$lastSearchedQuery\""
                        } else {
                            "Last Searched Playlists"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = AlabasterWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .testTag("search_playlists_section_header")
                    )
                    Text(
                        text = "${filteredPlaylistResults.size} found",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricMint
                    )
                }
            }

            itemsIndexed(
                items = filteredPlaylistResults,
                key = { _, playlist -> "search_pl_${playlist.id}" },
                contentType = { _, _ -> "search_playlist_row" }
            ) { _, playlist ->
                val isFavoritePlaylist = remember(importedPlaylists, playlist.id, playlist.remotePlaylistId) {
                    importedPlaylists.any {
                        it.id == playlist.id ||
                            (it.remotePlaylistId.isNotBlank() && it.remotePlaylistId == playlist.remotePlaylistId)
                    }
                }
                SearchPlaylistRowCard(
                    playlist = playlist,
                    isFavorite = isFavoritePlaylist,
                    isContainerless = isHistoryIdleView,
                    onOpenClick = { onOpenPlaylist(playlist) },
                    onPlayClick = { onPlayPlaylist(playlist) },
                    onToggleFavoriteClick = { onToggleFavoritePlaylist(playlist) }
                )
            }
        }

        if (filteredSongResults.isNotEmpty()) {
            // In live search without a promoted artist hero, show Top Song Hero Card first;
            // In idle search history or when an Artist Hero Card is already promoted at the top, keep all song rows clean & flat!
            val showSongHeroCard = !isHistoryIdleView && !shouldPromoteArtistFirst
            val songsListItems = if (showSongHeroCard) filteredSongResults.drop(1) else filteredSongResults

            if (showSongHeroCard) {
                val topSong = filteredSongResults.first()
                item(key = "search_top_result_hero_${topSong.id}", contentType = "top_result_hero") {
                    val isCurrentTop = currentTrackId == topSong.id
                    SearchTopResultHeroCard(
                        song = topSong,
                        isCurrentTrack = isCurrentTop,
                        isPlaying = isCurrentTop && isPlaying,
                        isResolving = isCurrentTop && isResolvingStream,
                        isDownloaded = downloadedSongIds.contains(topSong.id),
                        isDownloading = downloadingSongIds.contains(topSong.id),
                        onPlayClick = {
                            onClearFocus()
                            onRecordSearchHistory(searchQuery)
                            onPlaySong(topSong, emptyList())
                        },
                        onOpenArtistClick = { onOpenArtist(topSong.artist) },
                        onAddToQueueClick = { onAddToQueue(topSong) },
                        onToggleFavoriteClick = { onToggleFavorite(topSong) },
                        onToggleDownloadClick = { onToggleDownload(topSong) }
                    )
                }
            }

            if (songsListItems.isNotEmpty()) {
                item(key = "search_songs_header", contentType = "section_header") {
                    Text(
                        text = if (!isHistoryIdleView) {
                            if (shouldPromoteArtistFirst && topMatchedArtist != null) {
                                "Top Tracks • ${topMatchedArtist.name}"
                            } else {
                                "Songs"
                            }
                        } else if (lastSearchedQuery.isNotBlank()) {
                            "Recently Searched Songs • \"$lastSearchedQuery\""
                        } else {
                            "Recently Searched Songs"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = AlabasterWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .testTag("search_songs_section_header")
                    )
                }

                itemsIndexed(
                    items = songsListItems,
                    key = { _, song -> "search_${song.id}" },
                    contentType = { _, _ -> "song_track_row" }
                ) { _, song ->
                    val isCurrentTrack = currentTrackId == song.id
                    SongTrackRowCard(
                        song = song,
                        isCurrentTrack = isCurrentTrack,
                        isPlaying = isCurrentTrack && isPlaying,
                        isResolving = isCurrentTrack && isResolvingStream,
                        isDownloaded = downloadedSongIds.contains(song.id),
                        isDownloading = downloadingSongIds.contains(song.id),
                        isContainerless = isHistoryIdleView,
                        onPlayClick = {
                            onClearFocus()
                            onRecordSearchHistory(searchQuery)
                            onPlaySong(song, emptyList())
                        },
                        onAddToQueueClick = { onAddToQueue(song) },
                        onToggleFavoriteClick = { onToggleFavorite(song) },
                        onToggleDownloadClick = { onToggleDownload(song) },
                        onAddToPlaylistClick = { onAddToPlaylist(song) },
                        onOpenArtistClick = { onOpenArtist(song.artist) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchArtistHeroCard(
    artist: SearchArtistItem,
    topSong: SongItem?,
    onOpenArtistClick: () -> Unit,
    onPlayTopSongClick: (SongItem) -> Unit
) {
    val (glowPrimary, glowSecondary) = rememberDynamicArtworkGlow(artist.artworkUrl)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        glowPrimary.copy(alpha = 0.28f),
                        glowSecondary.copy(alpha = 0.14f),
                        CarbonSurface
                    )
                )
            )
            .border(
                width = 0.85.dp,
                color = glowPrimary.copy(alpha = 0.50f),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onOpenArtistClick)
            .padding(16.dp)
            .testTag("search_top_artist_hero_card")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ElectricMint.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.border(
                        width = 0.5.dp,
                        color = ElectricMint.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Verified,
                            contentDescription = null,
                            tint = ElectricMint,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "TOP RESULT • ARTIST",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = ElectricMint
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Open Artist Hub",
                    tint = MutedSlate,
                    modifier = Modifier.size(22.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(CarbonElevated)
                        .border(1.5.dp, ElectricMint.copy(alpha = 0.65f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (artist.artworkUrl.isNotBlank()) {
                        OptimizedArtworkImage(
                            artworkUrl = artist.artworkUrl,
                            contentDescription = artist.name,
                            targetSizeDp = 80.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = artist.name.take(2).uppercase(),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = ElectricMint
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = AlabasterWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenArtistClick,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricMint,
                        contentColor = ThemeOnPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("top_artist_open_hub_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open Artist Hub",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        maxLines = 1
                    )
                }

                if (topSong != null) {
                    OutlinedButton(
                        onClick = { onPlayTopSongClick(topSong) },
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(
                            0.85.dp,
                            ElectricMint.copy(alpha = 0.6f)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("top_artist_play_song_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play top song",
                            tint = ElectricMint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Play Top",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = AlabasterWhite,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchArtistRowCard(
    artist: SearchArtistItem,
    onOpenArtistClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenArtistClick)
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .testTag("search_artist_row_${artist.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(CarbonElevated),
            contentAlignment = Alignment.Center
        ) {
            if (artist.artworkUrl.isNotBlank()) {
                OptimizedArtworkImage(
                    artworkUrl = artist.artworkUrl,
                    contentDescription = artist.name,
                    targetSizeDp = 52.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = artist.name.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ElectricMint
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = AlabasterWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MutedSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "Open ${artist.name}",
            tint = MutedSlate,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Spotify-Style "Top Result" Hero Card for the #1 Best Match in Search
 */
@Composable
private fun SearchTopResultHeroCard(
    song: SongItem,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isResolving: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onPlayClick: () -> Unit,
    onOpenArtistClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onToggleFavoriteClick: () -> Unit,
    onToggleDownloadClick: () -> Unit
) {
    val (glowPrimary, glowSecondary) = rememberDynamicArtworkGlow(song.artworkUrl)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        glowPrimary.copy(alpha = 0.24f),
                        glowSecondary.copy(alpha = 0.14f),
                        CarbonSurface
                    )
                )
            )
            .border(
                width = if (isCurrentTrack) 1.dp else 0.75.dp,
                color = if (isCurrentTrack) ElectricMint.copy(alpha = 0.7f) else glowPrimary.copy(alpha = 0.45f),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onPlayClick)
            .padding(16.dp)
            .testTag("search_top_result_hero_card")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ElectricMint.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.border(
                        width = 0.5.dp,
                        color = ElectricMint.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50)
                    )
                ) {
                    Text(
                        text = "TOP RESULT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.9.sp
                        ),
                        color = ElectricMint,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Quick Artist Profile Pill Button
                if (song.artist.isNotBlank()) {
                    Surface(
                        color = CarbonElevated,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(0.5.dp, SubtleBorder, RoundedCornerShape(50))
                            .clickable(onClick = onOpenArtistClick)
                            .testTag("top_result_open_artist_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = "View Artist",
                                tint = CyberCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Artist Hub",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = AlabasterWhite
                            )
                        }
                    }
                }
            }

            // Full-width Artwork + Song Metadata Row (uncongested)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    OptimizedArtworkImage(
                        artworkUrl = song.artworkUrl,
                        contentDescription = song.title,
                        targetSizeDp = 76.dp,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CarbonElevated)
                    )

                    if (isResolving) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(PhotoScrimColor.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = ElectricMint,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else if (isCurrentTrack) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(PhotoScrimColor.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            MiniWaveformIndicator(isPlaying = isPlaying)
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = if (isCurrentTrack) ElectricMint else AlabasterWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenArtistClick)
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = CyberCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "Song • ${song.albumName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Spacious Bottom Action Bar inside Hero Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isDownloading) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(CarbonElevated)
                                .border(0.75.dp, CyberCyan.copy(alpha = 0.55f), CircleShape)
                                .clickable(onClick = onToggleDownloadClick)
                                .testTag("cancel_download_top_result_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = CyberCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        SoftTonalCircleIconButton(
                            icon = if (isDownloaded) Icons.Filled.DownloadDone else Icons.Outlined.Download,
                            contentDescription = "Download top result",
                            onClick = onToggleDownloadClick,
                            size = 38.dp,
                            iconSize = 18.dp,
                            isActive = isDownloaded,
                            activeTint = CyberCyan,
                            inactiveTint = AlabasterWhite.copy(alpha = 0.85f),
                            containerColor = CarbonElevated
                        )
                    }

                    AnimatedFavoriteButton(
                        isFavorite = song.isFavorite,
                        onClick = onToggleFavoriteClick,
                        contentDescription = stringResource(R.string.favorite_cd),
                        size = 38.dp,
                        iconSize = 18.dp,
                        activeTint = SunsetCoral,
                        inactiveTint = AlabasterWhite.copy(alpha = 0.85f),
                        containerColor = CarbonElevated
                    )

                    SoftTonalCircleIconButton(
                        icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                        contentDescription = stringResource(R.string.add_to_queue_cd),
                        onClick = onAddToQueueClick,
                        size = 38.dp,
                        iconSize = 18.dp,
                        inactiveTint = AlabasterWhite.copy(alpha = 0.85f),
                        containerColor = CarbonElevated
                    )
                }

                Button(
                    onClick = onPlayClick,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricMint,
                        contentColor = ThemeOnPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("top_result_play_button")
                ) {
                    Icon(
                        imageVector = if (isCurrentTrack && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play Top Result",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCurrentTrack && isPlaying) "Pause" else "Play Now",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LastSearchedQueryRowCard(
    query: String,
    isLatest: Boolean,
    onSelectQuery: () -> Unit,
    onRemoveQuery: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onSelectQuery)
                .padding(horizontal = 4.dp, vertical = 10.dp)
                .testTag("recent_search_item_$query"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = if (isLatest) ElectricMint else MutedSlate,
                    modifier = Modifier.size(19.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = AlabasterWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isLatest) {
                        Text(
                            text = "Latest search",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricMint.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            SoftTonalCircleIconButton(
                icon = Icons.Outlined.Clear,
                contentDescription = "Remove \"$query\" from search history",
                onClick = onRemoveQuery,
                size = 34.dp,
                iconSize = 17.dp,
                inactiveTint = MutedSlate,
                containerColor = Color.Transparent,
                modifier = Modifier.testTag("remove_recent_search_$query")
            )
        }
        HorizontalDivider(
            color = SubtleBorder.copy(alpha = 0.22f),
            thickness = 0.5.dp
        )
    }
}

@Composable
private fun SearchPlaylistRowCard(
    playlist: PlaylistItem,
    isFavorite: Boolean,
    isContainerless: Boolean = false,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit,
    onToggleFavoriteClick: () -> Unit
) {
    val rowModifier = if (isContainerless) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpenClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CarbonSurface)
            .border(
                width = 0.75.dp,
                color = if (isFavorite) ElectricMint.copy(alpha = 0.5f) else SubtleBorder.copy(alpha = 0.65f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onOpenClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    }

    Row(
        modifier = rowModifier.testTag("search_playlist_card_${playlist.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OptimizedArtworkImage(
            artworkUrl = playlist.artworkUrl,
            contentDescription = playlist.title,
            targetSizeDp = 52.dp,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CarbonElevated)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PlatformBadgePill(
                    platform = playlist.sourcePlatform
                )

                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = AlabasterWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            Text(
                text = playlist.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MutedSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedFavoriteButton(
                isFavorite = isFavorite,
                onClick = onToggleFavoriteClick,
                contentDescription = if (isFavorite) "Remove ${playlist.title} from Favorites" else "Save ${playlist.title} to Favorites",
                size = 38.dp,
                iconSize = 18.dp,
                activeTint = SunsetCoral,
                inactiveTint = MutedSlate,
                containerColor = if (isContainerless) Color.Transparent else CarbonElevated,
                modifier = Modifier.testTag("search_playlist_fav_${playlist.id}")
            )

            SoftTonalCircleIconButton(
                icon = Icons.Default.PlayArrow,
                contentDescription = "Play ${playlist.title}",
                onClick = onPlayClick,
                size = 38.dp,
                iconSize = 20.dp,
                isActive = true,
                activeTint = ElectricMint,
                containerColor = if (isContainerless) Color.Transparent else CarbonElevated,
                modifier = Modifier.testTag("search_playlist_play_${playlist.id}")
            )
        }
    }
}

/**
 * Redesigned Editorial Library Screen:
 * - Clean editorial header with compact + New & Import Link triggers
 * - Quick filter pills (All, Playlists, Liked Songs, Artists, Smart Mixes)
 * - Compact editorial Listening Snapshot strip
 * - Container-light circular artists, wide smart mixes, sleek playlist & liked track feeds
 */
@Composable
private fun LibraryTabScreenContent(
    favoriteSongs: List<SongItem>,
    listeningStats: ListeningStatsData,
    followedArtists: List<RelatedArtistItem>,
    smartAutoPlaylists: List<PlaylistItem>,
    downloadedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    batchDownloadProgress: BatchDownloadProgress?,
    importedPlaylists: List<PlaylistItem>,
    isImportingPlaylist: Boolean,
    currentTrackId: String?,
    isPlaying: Boolean,
    isResolvingStream: Boolean,
    innerPadding: PaddingValues,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onAddToQueue: (SongItem) -> Unit,
    onToggleDownload: (SongItem) -> Unit,
    onDownloadAllLiked: () -> Unit,
    onAddToPlaylist: (SongItem) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenCreatePlaylistDialog: () -> Unit,
    onImportPlaylistUrl: (String, () -> Unit) -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onPlayPlaylist: (PlaylistItem) -> Unit,
    onRemoveImportedPlaylist: (String) -> Unit
) {
    var playlistLinkInput by remember { mutableStateOf("") }
    var isImporterExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(LibraryFilterCategory.ALL) }
    var likedSortOrder by remember { mutableStateOf(TrackSortOrder.DEFAULT) }
    val sortedFavoriteSongs = remember(favoriteSongs, likedSortOrder, downloadedSongIds) {
        sortTracksByOrder(favoriteSongs, likedSortOrder, downloadedSongIds)
    }
    val clipboardManager = LocalClipboardManager.current
    val libraryListState = rememberLazyListState()

    LazyColumn(
        state = libraryListState,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .widthIn(max = 680.dp)
            .testTag("library_tab_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Clean Editorial Header + Compact Action Triggers + Expandable URL Importer
        item(key = "library_editorial_header", contentType = "library_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_import_playlist_card"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Your Library",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = AlabasterWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${importedPlaylists.size} playlists · ${favoriteSongs.size} liked · ${followedArtists.size} artists",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = MutedSlate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (isImporterExpanded) ElectricMint.copy(alpha = 0.16f) else CarbonSurface,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { isImporterExpanded = !isImporterExpanded }
                                .testTag("library_toggle_import_bar_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = "Import playlist URL",
                                    tint = ElectricMint,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Import",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = if (isImporterExpanded) ElectricMint else AlabasterWhite,
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            color = ElectricMint,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onOpenCreatePlaylistDialog() }
                                .testTag("library_create_custom_playlist_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "New playlist",
                                    tint = ThemeOnPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = "New",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = ThemeOnPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Expandable Drawer for Pasting Playlist Links
                AnimatedVisibility(
                    visible = isImporterExpanded || isImportingPlaylist,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = CarbonSurface,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 0.75.dp,
                                color = ElectricMint.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(18.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Paste a public Spotify, YouTube Music, JioSaavn, or Deezer playlist URL",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedSlate
                            )

                            OutlinedTextField(
                                value = playlistLinkInput,
                                onValueChange = { playlistLinkInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("library_playlist_link_input"),
                                placeholder = {
                                    Text(
                                        text = "https://open.spotify.com/playlist/...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MutedSlate.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = CarbonElevated,
                                    unfocusedContainerColor = CarbonElevated,
                                    focusedBorderColor = ElectricMint,
                                    unfocusedBorderColor = SubtleBorder,
                                    focusedTextColor = AlabasterWhite,
                                    unfocusedTextColor = AlabasterWhite,
                                    cursorColor = ElectricMint
                                ),
                                trailingIcon = {
                                    if (playlistLinkInput.isNotBlank()) {
                                        SoftTonalCircleIconButton(
                                            icon = Icons.Outlined.Clear,
                                            contentDescription = "Clear link",
                                            onClick = { playlistLinkInput = "" },
                                            size = 32.dp,
                                            iconSize = 16.dp,
                                            containerColor = Color.Transparent
                                        )
                                    } else {
                                        SoftTonalCircleIconButton(
                                            icon = Icons.Outlined.ContentPaste,
                                            contentDescription = "Paste link from clipboard",
                                            onClick = {
                                                val clipText = clipboardManager.getText()?.text?.trim().orEmpty()
                                                if (clipText.isNotBlank()) {
                                                    playlistLinkInput = clipText
                                                }
                                            },
                                            size = 34.dp,
                                            iconSize = 17.dp,
                                            inactiveTint = ElectricMint,
                                            containerColor = CarbonElevated,
                                            modifier = Modifier.testTag("library_paste_link_button")
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (playlistLinkInput.isNotBlank() && !isImportingPlaylist) {
                                            onImportPlaylistUrl(playlistLinkInput) {
                                                playlistLinkInput = ""
                                                isImporterExpanded = false
                                            }
                                        }
                                    }
                                )
                            )

                            Button(
                                onClick = {
                                    onImportPlaylistUrl(playlistLinkInput) {
                                        playlistLinkInput = ""
                                        isImporterExpanded = false
                                    }
                                },
                                enabled = playlistLinkInput.isNotBlank() && !isImportingPlaylist,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricMint,
                                    contentColor = ThemeOnPrimary,
                                    disabledContainerColor = CarbonElevated,
                                    disabledContentColor = MutedSlate
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("library_import_playlist_button")
                            ) {
                                if (isImportingPlaylist) {
                                    CircularProgressIndicator(
                                        color = ThemeOnPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Importing Playlist...",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Add Playlist to Library",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Quick Filter Pills Row (All, Playlists, Liked Songs, Artists, Smart Mixes)
        item(key = "library_quick_filter_pills", contentType = "library_filter_pills") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_quick_filter_pills")
            ) {
                items(
                    items = LibraryFilterCategory.entries,
                    key = { "lib_filter_${it.name}" }
                ) { category ->
                    val isSelected = selectedFilter == category
                    val countSuffix = when (category) {
                        LibraryFilterCategory.ALL -> ""
                        LibraryFilterCategory.PLAYLISTS -> if (importedPlaylists.isNotEmpty()) " · ${importedPlaylists.size}" else ""
                        LibraryFilterCategory.LIKED -> if (favoriteSongs.isNotEmpty()) " · ${favoriteSongs.size}" else ""
                        LibraryFilterCategory.ARTISTS -> if (followedArtists.isNotEmpty()) " · ${followedArtists.size}" else ""
                        LibraryFilterCategory.MIXES -> if (smartAutoPlaylists.isNotEmpty()) " · ${smartAutoPlaylists.size}" else ""
                    }
                    Surface(
                        color = if (isSelected) ElectricMint else CarbonSurface,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { selectedFilter = category }
                            .testTag("library_filter_${category.name.lowercase()}")
                    ) {
                        Text(
                            text = "${category.label}$countSuffix",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            ),
                            color = if (isSelected) ThemeOnPrimary else AlabasterWhite.copy(alpha = 0.85f),
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // 3. Compact Editorial Listening Snapshot Strip (Only in ALL view)
        if (selectedFilter == LibraryFilterCategory.ALL) {
            item(key = "library_listening_stats_recap", contentType = "listening_stats_card") {
                val playsLabel = if (listeningStats.totalPlays == 1) "1 play" else "${listeningStats.totalPlays} plays"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CarbonSurface)
                        .border(0.6.dp, SubtleBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("library_listening_stats_card"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BarChart,
                                contentDescription = null,
                                tint = ElectricMint,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "LISTENING SNAPSHOT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = ElectricMint
                            )
                        }

                        Text(
                            text = "$playsLabel · ${listeningStats.totalMinutes} min",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = CyberCyan
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Top Artist inline editorial tile
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = listeningStats.topArtistName != "Explore Music") {
                                    onOpenArtist(listeningStats.topArtistName)
                                }
                                .padding(vertical = 4.dp)
                                .testTag("stats_top_artist_card"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CarbonSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (listeningStats.topArtistArtwork.isNotBlank()) {
                                    OptimizedArtworkImage(
                                        artworkUrl = listeningStats.topArtistArtwork,
                                        contentDescription = listeningStats.topArtistName,
                                        targetSizeDp = 36.dp,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = null,
                                        tint = SunsetCoral,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Top Artist",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SunsetCoral
                                )
                                Text(
                                    text = listeningStats.topArtistName,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = AlabasterWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(30.dp)
                                .background(SubtleBorder.copy(alpha = 0.45f))
                        )

                        // Top Track inline editorial tile
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = listeningStats.topSong != null) {
                                    listeningStats.topSong?.let { top ->
                                        onPlaySong(top, listOf(top))
                                    }
                                }
                                .padding(vertical = 4.dp)
                                .testTag("stats_top_song_card"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(CarbonSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                val topArt = listeningStats.topSong?.artworkUrl.orEmpty()
                                if (topArt.isNotBlank()) {
                                    OptimizedArtworkImage(
                                        artworkUrl = topArt,
                                        contentDescription = listeningStats.topSong?.title,
                                        targetSizeDp = 36.dp,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = ElectricMint,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Top Track",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricMint
                                )
                                Text(
                                    text = listeningStats.topSong?.title ?: "No plays yet",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = AlabasterWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Favorite Artists Section
        val showArtistsSection = (selectedFilter == LibraryFilterCategory.ALL && followedArtists.isNotEmpty()) ||
            selectedFilter == LibraryFilterCategory.ARTISTS
        if (showArtistsSection) {
            item(key = "library_artists_header", contentType = "section_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Favorite Artists",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = AlabasterWhite
                    )
                    if (followedArtists.isNotEmpty()) {
                        Text(
                            text = "${followedArtists.size} followed",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = ElectricMint
                        )
                    }
                }
            }

            if (followedArtists.isEmpty()) {
                item(key = "library_empty_artists", contentType = "empty_state") {
                    Text(
                        text = "Follow artists from Search or Artist Hubs to build your circle here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else if (selectedFilter == LibraryFilterCategory.ALL) {
                item(key = "library_followed_artists_row", contentType = "followed_artists_section") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.testTag("library_favorite_artists_row")
                    ) {
                        items(
                            items = followedArtists,
                            key = { "fav_artist_${it.name}" }
                        ) { artist ->
                            Column(
                                modifier = Modifier
                                    .width(76.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenArtist(artist.name) }
                                    .padding(vertical = 4.dp)
                                    .testTag("library_favorite_artist_${artist.name}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(66.dp)
                                        .clip(CircleShape)
                                        .background(CarbonSurface)
                                        .border(1.dp, ElectricMint.copy(alpha = 0.55f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (artist.artworkUrl.isNotBlank()) {
                                        OptimizedArtworkImage(
                                            artworkUrl = artist.artworkUrl,
                                            contentDescription = artist.name,
                                            targetSizeDp = 66.dp,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Text(
                                            text = artist.name.take(2).uppercase(),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = ElectricMint
                                        )
                                    }
                                }
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = AlabasterWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                items(
                    items = followedArtists,
                    key = { "fav_artist_list_${it.name}" }
                ) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenArtist(artist.name) }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .testTag("library_favorite_artist_${artist.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(CarbonSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (artist.artworkUrl.isNotBlank()) {
                                OptimizedArtworkImage(
                                    artworkUrl = artist.artworkUrl,
                                    contentDescription = artist.name,
                                    targetSizeDp = 50.dp,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Text(
                                    text = artist.name.take(2).uppercase(),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ElectricMint
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AlabasterWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Followed Artist",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedSlate
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = "Open ${artist.name}",
                            tint = MutedSlate
                        )
                    }
                }
            }
        }

        // 5. Smart Auto-Mixes Section
        val showMixesSection = (selectedFilter == LibraryFilterCategory.ALL && smartAutoPlaylists.isNotEmpty()) ||
            selectedFilter == LibraryFilterCategory.MIXES
        if (showMixesSection) {
            item(key = "library_smart_mixes_header", contentType = "section_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Smart Mixes",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = AlabasterWhite
                    )
                    Text(
                        text = "Auto-Curated",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = CyberCyan
                    )
                }
            }

            if (smartAutoPlaylists.isEmpty()) {
                item(key = "library_empty_mixes", contentType = "empty_state") {
                    Text(
                        text = "Listen to a few tracks and your personalized Smart Mixes will appear automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else if (selectedFilter == LibraryFilterCategory.ALL) {
                item(key = "library_smart_mixes_row", contentType = "smart_mixes_section") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.testTag("library_smart_mixes_row")
                    ) {
                        items(
                            items = smartAutoPlaylists,
                            key = { "smart_mix_${it.id}" }
                        ) { mix ->
                            val trackUnit = if (mix.trackCount == 1) "track" else "tracks"
                            Row(
                                modifier = Modifier
                                    .width(246.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(CarbonSurface)
                                    .clickable { onOpenPlaylist(mix) }
                                    .padding(10.dp)
                                    .testTag("library_smart_mix_card_${mix.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CarbonElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (mix.artworkUrl.isNotBlank()) {
                                        OptimizedArtworkImage(
                                            artworkUrl = mix.artworkUrl,
                                            contentDescription = mix.title,
                                            targetSizeDp = 50.dp,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint = CyberCyan,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = mix.title,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = AlabasterWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${mix.trackCount} $trackUnit",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = CyberCyan,
                                        maxLines = 1
                                    )
                                }

                                SoftTonalCircleIconButton(
                                    icon = Icons.Filled.PlayArrow,
                                    contentDescription = "Play ${mix.title}",
                                    onClick = { onPlayPlaylist(mix) },
                                    size = 36.dp,
                                    iconSize = 18.dp,
                                    isActive = false,
                                    inactiveTint = ThemeOnPrimary,
                                    containerColor = ElectricMint
                                )
                            }
                        }
                    }
                }
            } else {
                items(
                    items = smartAutoPlaylists,
                    key = { "smart_mix_vert_${it.id}" }
                ) { mix ->
                    val trackUnit = if (mix.trackCount == 1) "track" else "tracks"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenPlaylist(mix) }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .testTag("library_smart_mix_card_${mix.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CarbonElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            if (mix.artworkUrl.isNotBlank()) {
                                OptimizedArtworkImage(
                                    artworkUrl = mix.artworkUrl,
                                    contentDescription = mix.title,
                                    targetSizeDp = 52.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mix.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AlabasterWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${mix.trackCount} $trackUnit",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberCyan
                            )
                        }
                        SoftTonalCircleIconButton(
                            icon = Icons.Filled.PlayArrow,
                            contentDescription = "Play ${mix.title}",
                            onClick = { onPlayPlaylist(mix) },
                            size = 36.dp,
                            iconSize = 18.dp,
                            isActive = false,
                            inactiveTint = ThemeOnPrimary,
                            containerColor = ElectricMint
                        )
                    }
                }
            }
        }

        // 6. Playlists Section (Editorial borderless list)
        if (selectedFilter == LibraryFilterCategory.ALL || selectedFilter == LibraryFilterCategory.PLAYLISTS) {
            item(key = "library_playlists_header", contentType = "section_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = AlabasterWhite
                    )
                    Text(
                        text = "${importedPlaylists.size} saved",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = MutedSlate
                    )
                }
            }

            if (importedPlaylists.isEmpty()) {
                item(key = "library_empty_playlists", contentType = "empty_state") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(ElectricMint.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LibraryMusic,
                                contentDescription = null,
                                tint = ElectricMint,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                        Text(
                            text = "Build Your Collection",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = AlabasterWhite
                        )
                        Text(
                            text = "Tap + New or Import above to create or bring playlists from Spotify, YouTube Music, or JioSaavn.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSlate,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(
                    items = importedPlaylists,
                    key = { pl -> "lib_imported_pl_${pl.id}" },
                    contentType = { "library_playlist_row" }
                ) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenPlaylist(playlist) }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .testTag("library_imported_playlist_row_${playlist.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CarbonSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (playlist.artworkUrl.isNotBlank()) {
                                OptimizedArtworkImage(
                                    artworkUrl = playlist.artworkUrl,
                                    contentDescription = playlist.title,
                                    targetSizeDp = 54.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.LibraryMusic,
                                    contentDescription = null,
                                    tint = AlabasterWhite,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PlatformBadgePill(
                                    platform = playlist.sourcePlatform
                                )
                                Text(
                                    text = playlist.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = AlabasterWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            Text(
                                text = "${playlist.sourcePlatform.ifBlank { "Echoic" }} • ${playlist.trackCount} Songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedSlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SoftTonalCircleIconButton(
                                icon = Icons.Filled.PlayArrow,
                                contentDescription = "Play ${playlist.title}",
                                onClick = { onPlayPlaylist(playlist) },
                                size = 36.dp,
                                iconSize = 19.dp,
                                isActive = false,
                                inactiveTint = ThemeOnPrimary,
                                containerColor = ElectricMint
                            )
                            SoftTonalCircleIconButton(
                                icon = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove ${playlist.title}",
                                onClick = { onRemoveImportedPlaylist(playlist.id) },
                                size = 36.dp,
                                iconSize = 18.dp,
                                inactiveTint = MutedSlate,
                                containerColor = Color.Transparent
                            )
                        }
                    }
                }
            }
        }

        // 7. Liked Songs Section
        if (selectedFilter == LibraryFilterCategory.ALL || selectedFilter == LibraryFilterCategory.LIKED) {
            item(key = "library_liked_section_header", contentType = "section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = SunsetCoral,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Liked Songs",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = AlabasterWhite
                        )
                    }

                    Text(
                        text = "${favoriteSongs.size} tracks",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = SunsetCoral
                    )
                }
            }

            if (favoriteSongs.isEmpty()) {
                item(key = "library_empty_favorites", contentType = "empty_state") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(SunsetCoral.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = SunsetCoral,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                        Text(
                            text = "No Liked Songs Yet",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = AlabasterWhite
                        )
                        Text(
                            text = "Tap the heart icon on any track to keep your favorites right here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSlate,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val allLikedDownloaded = favoriteSongs.isNotEmpty() &&
                    favoriteSongs.all { downloadedSongIds.contains(it.id) }
                val isLikedBatchDownloading = batchDownloadProgress?.isActive == true &&
                    batchDownloadProgress.collectionTitle == "Liked Songs"

                item(key = "library_liked_quick_actions", contentType = "liked_actions_row") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = ElectricMint,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        sortedFavoriteSongs.firstOrNull()?.let { first ->
                                            onPlaySong(first, sortedFavoriteSongs)
                                        }
                                    }
                                    .testTag("library_liked_play_all_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = ThemeOnPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Play All",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                        color = ThemeOnPrimary
                                    )
                                }
                            }

                            Surface(
                                color = CarbonSurface,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        val shuffled = sortedFavoriteSongs.shuffled()
                                        shuffled.firstOrNull()?.let { first ->
                                            onPlaySong(first, shuffled)
                                        }
                                    }
                                    .testTag("library_liked_shuffle_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Shuffle,
                                        contentDescription = null,
                                        tint = ElectricMint,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Shuffle",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = AlabasterWhite
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            if (isLikedBatchDownloading) {
                                Surface(
                                    color = CyberCyan.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("library_liked_download_all_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = CyberCyan,
                                            strokeWidth = 1.8.dp,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "${batchDownloadProgress?.completedCount ?: 0}/${batchDownloadProgress?.totalCount ?: favoriteSongs.size}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = CyberCyan
                                        )
                                    }
                                }
                            } else {
                                SoftTonalCircleIconButton(
                                    icon = if (allLikedDownloaded) Icons.Filled.DownloadDone else Icons.Outlined.Download,
                                    contentDescription = if (allLikedDownloaded) "All liked songs downloaded" else "Download all liked songs",
                                    onClick = onDownloadAllLiked,
                                    size = 38.dp,
                                    iconSize = 18.dp,
                                    isActive = allLikedDownloaded,
                                    activeTint = CyberCyan,
                                    inactiveTint = AlabasterWhite,
                                    containerColor = CarbonSurface,
                                    modifier = Modifier.testTag("library_liked_download_all_button")
                                )
                            }
                        }

                        if (favoriteSongs.size > 1) {
                            TrackSortOrderChipsRow(
                                selectedSortOrder = likedSortOrder,
                                onSelectSortOrder = { likedSortOrder = it }
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = sortedFavoriteSongs,
                    key = { _, song -> "lib_fav_${song.id}" },
                    contentType = { _, _ -> "song_track_row" }
                ) { _, song ->
                    val isCurrentTrack = currentTrackId == song.id
                    SongTrackRowCard(
                        song = song,
                        isCurrentTrack = isCurrentTrack,
                        isPlaying = isCurrentTrack && isPlaying,
                        isResolving = isCurrentTrack && isResolvingStream,
                        isDownloaded = downloadedSongIds.contains(song.id),
                        isDownloading = downloadingSongIds.contains(song.id),
                        isContainerless = true,
                        onPlayClick = { onPlaySong(song, sortedFavoriteSongs) },
                        onAddToQueueClick = { onAddToQueue(song) },
                        onToggleFavoriteClick = { onToggleFavorite(song) },
                        onToggleDownloadClick = { onToggleDownload(song) },
                        onAddToPlaylistClick = { onAddToPlaylist(song) },
                        onOpenArtistClick = { onOpenArtist(song.artist) }
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Offline Downloads Tab Screen:
 * - Hero Header with Play All, Shuffle, and Storage Stats (Track Count, Total Duration, Storage MB)
 * - Cached Track List for zero-network offline playback
 */
@Composable
private fun OfflineDownloadsTabScreenContent(
    offlineSongs: List<SongItem>,
    downloadingSongIds: Set<String>,
    batchDownloadProgress: BatchDownloadProgress?,
    currentTrackId: String?,
    isPlaying: Boolean,
    isResolvingStream: Boolean,
    innerPadding: PaddingValues,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onAddToQueue: (SongItem) -> Unit,
    onToggleDownload: (SongItem) -> Unit,
    onCancelBatchDownload: () -> Unit,
    onClearAllDownloads: () -> Unit,
    onAddToPlaylist: (SongItem) -> Unit,
    onOpenArtist: (String) -> Unit,
    onDiscoverMusicClick: () -> Unit
) {
    var showClearAllDialog by remember { mutableStateOf(false) }

    val totalMinutes = remember(offlineSongs) {
        val totalMs = offlineSongs.sumOf { it.durationMs.coerceAtLeast(180_000L) }
        (totalMs / 60_000L).coerceAtLeast(if (offlineSongs.isNotEmpty()) 1L else 0L)
    }

    val totalStorageMbText = remember(offlineSongs) {
        val actualBytes = offlineSongs.sumOf { song ->
            if (song.localFilePath.isNotBlank()) {
                runCatching { File(song.localFilePath).length() }.getOrDefault(0L)
            } else {
                0L
            }
        }
        val mb = if (actualBytes > 0L) {
            actualBytes.toDouble() / (1024.0 * 1024.0)
        } else {
            offlineSongs.size * 3.4
        }
        String.format(Locale.US, "%.1f MB", mb)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .widthIn(max = 680.dp)
            .testTag("offline_downloads_tab_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Offline Downloads Hero Header Card with Play All, Shuffle, and Storage Stats
        item(key = "offline_downloads_hero_card", contentType = "offline_hero_card") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                CyberCyan.copy(alpha = 0.22f),
                                ElectricMint.copy(alpha = 0.14f),
                                CarbonSurface
                            )
                        )
                    )
                    .border(
                        width = 0.75.dp,
                        color = CyberCyan.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp)
                    .testTag("offline_downloads_hero_card")
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                color = CyberCyan.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.border(
                                    width = 0.5.dp,
                                    color = CyberCyan.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(50)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DownloadDone,
                                        contentDescription = null,
                                        tint = CyberCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "OFFLINE VAULT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = CyberCyan
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "Offline Downloads",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = AlabasterWhite
                            )
                            Text(
                                text = "Cached in high-bitrate M4A for instant playback without internet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedSlate
                            )
                        }
                    }

                    // Storage Stats Telemetry Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OfflineStatBadge(
                            label = "CACHED TRACKS",
                            value = "${offlineSongs.size} Songs",
                            accentColor = ElectricMint,
                            modifier = Modifier.weight(1f)
                        )
                        OfflineStatBadge(
                            label = "PLAYTIME",
                            value = "$totalMinutes Mins",
                            accentColor = CyberCyan,
                            modifier = Modifier.weight(1f)
                        )
                        OfflineStatBadge(
                            label = "STORAGE USED",
                            value = totalStorageMbText,
                            accentColor = AlabasterWhite,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Play All & Shuffle Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                offlineSongs.firstOrNull()?.let { first ->
                                    onPlaySong(first, offlineSongs)
                                }
                            },
                            enabled = offlineSongs.isNotEmpty(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricMint,
                                contentColor = ThemeOnPrimary,
                                disabledContainerColor = CarbonElevated,
                                disabledContentColor = MutedSlate
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("offline_play_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play All",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val shuffled = offlineSongs.shuffled()
                                shuffled.firstOrNull()?.let { first ->
                                    onPlaySong(first, shuffled)
                                }
                            },
                            enabled = offlineSongs.isNotEmpty(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CarbonSurface,
                                contentColor = AlabasterWhite,
                                disabledContainerColor = CarbonElevated,
                                disabledContentColor = MutedSlate
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("offline_shuffle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = null,
                                tint = if (offlineSongs.isNotEmpty()) CyberCyan else MutedSlate,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Shuffle",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }

        // Active Batch Download Progress Banner with Cancel Action
        if (batchDownloadProgress != null && batchDownloadProgress.isActive) {
            item(key = "offline_active_batch_card", contentType = "active_batch_banner") {
                Surface(
                    color = CyberCyan.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.75.dp, CyberCyan.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = CyberCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Downloading ${batchDownloadProgress.collectionTitle}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = AlabasterWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${batchDownloadProgress.completedCount} of ${batchDownloadProgress.totalCount} tracks completed",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = CyberCyan
                            )
                        }
                        TextButton(
                            onClick = onCancelBatchDownload,
                            modifier = Modifier.testTag("offline_cancel_batch_button")
                        ) {
                            Text(
                                text = "Cancel",
                                color = SunsetCoral,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. Downloaded Tracks List or Empty State
        if (offlineSongs.isEmpty()) {
            item(key = "offline_empty_state", contentType = "empty_state") {
                Surface(
                    color = CarbonSurface,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, SubtleBorder, RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.14f))
                                .border(0.75.dp, CyberCyan.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = "No Offline Downloads Yet",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = AlabasterWhite
                        )

                        Text(
                            text = "Tap the download icon on any track in Search, Liked Songs, or the Full-Screen Player to save it for offline listening.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSlate,
                            textAlign = TextAlign.Center
                        )

                        OutlinedButton(
                            onClick = onDiscoverMusicClick,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CarbonElevated,
                                contentColor = ElectricMint
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = ElectricMint,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Browse & Download Songs",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        } else {
            item(key = "offline_tracks_section_header", contentType = "section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Downloaded Tracks",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = AlabasterWhite
                        )
                        Text(
                            text = "Ready for offline play",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan
                        )
                    }

                    TextButton(
                        onClick = { showClearAllDialog = true },
                        modifier = Modifier.testTag("offline_clear_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Clear all downloads",
                            tint = SunsetCoral,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Clear All",
                            color = SunsetCoral,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            itemsIndexed(
                items = offlineSongs,
                key = { _, song -> "offline_tab_${song.id}" },
                contentType = { _, _ -> "song_track_row" }
            ) { _, song ->
                val isCurrentTrack = currentTrackId == song.id
                SongTrackRowCard(
                    song = song,
                    isCurrentTrack = isCurrentTrack,
                    isPlaying = isCurrentTrack && isPlaying,
                    isResolving = isCurrentTrack && isResolvingStream,
                    isDownloaded = true,
                    isDownloading = downloadingSongIds.contains(song.id),
                    onPlayClick = { onPlaySong(song, offlineSongs) },
                    onAddToQueueClick = { onAddToQueue(song) },
                    onToggleFavoriteClick = { onToggleFavorite(song) },
                    onToggleDownloadClick = { onToggleDownload(song) },
                    onAddToPlaylistClick = { onAddToPlaylist(song) },
                    onOpenArtistClick = { onOpenArtist(song.artist) }
                )
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            containerColor = CarbonSurface,
            titleContentColor = AlabasterWhite,
            textContentColor = MutedSlate,
            title = {
                Text(
                    text = "Clear All Downloads?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "This will remove all ${offlineSongs.size} cached tracks and free $totalStorageMbText of local storage.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDialog = false
                        onClearAllDownloads()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SunsetCoral,
                        contentColor = ThemeOnPrimary
                    ),
                    modifier = Modifier.testTag("confirm_clear_downloads_button")
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel", color = MutedSlate)
                }
            }
        )
    }
}

@Composable
private fun OfflineStatBadge(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CarbonElevated,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(
            width = 0.5.dp,
            color = SubtleBorder.copy(alpha = 0.7f),
            shape = RoundedCornerShape(14.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
                ),
                color = MutedSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Redesigned Pro Studio Equalizer & DSP Console Tab
 */
@Composable
private fun SettingsAndEqTabContent(
    equalizerState: EqualizerState,
    themeState: AppThemeState,
    playbackSpeed: Float,
    playbackPitch: Float,
    sleepTimerState: SleepTimerState,
    selectedTasteGenres: List<String>,
    innerPadding: PaddingValues,
    onSetEqEnabled: (Boolean) -> Unit,
    onSetBandLevel: (Short, Short) -> Unit,
    onSetBassBoost: (Int) -> Unit,
    onApplyPreset: (String) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onSetCrossfadeSeconds: (Int) -> Unit,
    onSetGaplessPlayback: (Boolean) -> Unit,
    onSetAutoQueueRadio: (Boolean) -> Unit,
    onSetAutoDeviceProfilesEnabled: (Boolean) -> Unit,
    onSetOutputRoutePreset: (AudioOutputRoute, String) -> Unit,
    onSelectThemePreset: (StudioThemePreset) -> Unit,
    onSetAmoledPureBlack: (Boolean) -> Unit,
    onSetDynamicArtworkTint: (Boolean) -> Unit,
    onStartSleepTimer: (Int, Boolean) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onOpenTasteEditor: () -> Unit
) {
    StudioEqualizerConsole(
        equalizerState = equalizerState,
        playbackSpeed = playbackSpeed,
        playbackPitch = playbackPitch,
        sleepTimerState = sleepTimerState,
        innerPadding = innerPadding,
        onSetEqEnabled = onSetEqEnabled,
        onSetBandLevel = onSetBandLevel,
        onSetBassBoost = onSetBassBoost,
        onApplyPreset = onApplyPreset,
        onSetSpeed = onSetSpeed,
        onSetPitch = onSetPitch,
        onSetCrossfadeSeconds = onSetCrossfadeSeconds,
        onSetGaplessPlayback = onSetGaplessPlayback,
        onSetAutoQueueRadio = onSetAutoQueueRadio,
        onSetAutoDeviceProfilesEnabled = onSetAutoDeviceProfilesEnabled,
        onSetOutputRoutePreset = onSetOutputRoutePreset,
        themeState = themeState,
        onSelectThemePreset = onSelectThemePreset,
        onSetAmoledPureBlack = onSetAmoledPureBlack,
        onSetDynamicArtworkTint = onSetDynamicArtworkTint,
        selectedTasteGenres = selectedTasteGenres,
        onOpenTasteEditor = onOpenTasteEditor,
        onStartSleepTimer = onStartSleepTimer,
        onCancelSleepTimer = onCancelSleepTimer
    )
}

@Composable
private fun MinimalSearchBar(
    searchQuery: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onClearSearch: () -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = stringResource(R.string.search_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MutedSlate
            )
        },
        leadingIcon = {
            if (isSearching) {
                CircularProgressIndicator(
                    color = ElectricMint,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MutedSlate
                )
            }
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                SoftTonalCircleIconButton(
                    icon = Icons.Outlined.Clear,
                    contentDescription = stringResource(R.string.clear_search),
                    onClick = onClearSearch,
                    size = 32.dp,
                    iconSize = 16.dp,
                    containerColor = CarbonElevated,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .testTag("clear_search_button")
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmitSearch(searchQuery) }),
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CarbonSurface,
            unfocusedContainerColor = CarbonSurface,
            focusedBorderColor = ElectricMint,
            unfocusedBorderColor = SubtleBorder.copy(alpha = 0.7f),
            focusedTextColor = AlabasterWhite,
            unfocusedTextColor = AlabasterWhite,
            cursorColor = ElectricMint
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_input_field")
    )
}



@Composable
private fun SongTrackRowCard(
    song: SongItem,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isResolving: Boolean = false,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    isContainerless: Boolean = false,
    onPlayClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onToggleFavoriteClick: () -> Unit,
    onToggleDownloadClick: (() -> Unit)? = null,
    onAddToPlaylistClick: (() -> Unit)? = null,
    onOpenArtistClick: (() -> Unit)? = null
) {
    val rowModifier = if (isContainerless) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrentTrack) ElectricMint.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onPlayClick)
            .padding(horizontal = 4.dp, vertical = 7.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCurrentTrack) CarbonElevated else CarbonSurface)
            .border(
                width = if (isCurrentTrack) 0.75.dp else 0.5.dp,
                color = if (isCurrentTrack) ElectricMint.copy(alpha = 0.5f) else SubtleBorder.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onPlayClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    }

    Row(
        modifier = rowModifier.testTag("track_card_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            OptimizedArtworkImage(
                artworkUrl = song.artworkUrl,
                contentDescription = song.title,
                targetSizeDp = 48.dp,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CarbonElevated)
            )

            if (isResolving) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PhotoScrimColor.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = ElectricMint,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else if (isCurrentTrack) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PhotoScrimColor.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    MiniWaveformIndicator(isPlaying = isPlaying)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Downloaded for offline",
                        tint = CyberCyan,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isCurrentTrack) ElectricMint else AlabasterWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = if (onOpenArtistClick != null && song.artist.isNotBlank()) {
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenArtistClick)
                        .padding(vertical = 1.dp)
                        .testTag("track_artist_link_${song.id}")
                } else {
                    Modifier
                }
            ) {
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val actionButtonContainer = if (isContainerless) Color.Transparent else CarbonElevated

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onToggleDownloadClick != null) {
                if (isDownloading) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(actionButtonContainer)
                            .clickable(onClick = onToggleDownloadClick)
                            .testTag("download_button_${song.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = CyberCyan,
                            strokeWidth = 1.8.dp,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else {
                    SoftTonalCircleIconButton(
                        icon = if (isDownloaded) Icons.Filled.DownloadDone else Icons.Outlined.Download,
                        contentDescription = if (isDownloaded) "Remove offline download" else "Download for offline",
                        onClick = onToggleDownloadClick,
                        size = 36.dp,
                        iconSize = 17.dp,
                        isActive = isDownloaded,
                        activeTint = CyberCyan,
                        inactiveTint = MutedSlate,
                        containerColor = actionButtonContainer,
                        modifier = Modifier.testTag("download_button_${song.id}")
                    )
                }
            }

            AnimatedFavoriteButton(
                isFavorite = song.isFavorite,
                onClick = onToggleFavoriteClick,
                contentDescription = stringResource(R.string.favorite_cd),
                size = 36.dp,
                iconSize = 17.dp,
                activeTint = SunsetCoral,
                inactiveTint = MutedSlate,
                containerColor = actionButtonContainer,
                modifier = Modifier.testTag("favorite_button_${song.id}")
            )

            SoftTonalCircleIconButton(
                icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                contentDescription = stringResource(R.string.add_to_queue_cd),
                onClick = onAddToPlaylistClick ?: onAddToQueueClick,
                size = 36.dp,
                iconSize = 18.dp,
                inactiveTint = AlabasterWhite.copy(alpha = 0.85f),
                containerColor = actionButtonContainer,
                modifier = Modifier.testTag("add_queue_button_${song.id}")
            )
        }
    }
}

@Composable
private fun CreateCustomPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        titleContentColor = AlabasterWhite,
        textContentColor = MutedSlate,
        title = {
            Text(
                text = "Create Custom Playlist",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Playlist Name") },
                    placeholder = { Text("Late Night Drives") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricMint,
                        unfocusedBorderColor = SubtleBorder,
                        focusedTextColor = AlabasterWhite,
                        unfocusedTextColor = AlabasterWhite,
                        cursorColor = ElectricMint
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_playlist_name_input")
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("My favorite synth & chill vibes") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricMint,
                        unfocusedBorderColor = SubtleBorder,
                        focusedTextColor = AlabasterWhite,
                        unfocusedTextColor = AlabasterWhite,
                        cursorColor = ElectricMint
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_playlist_subtitle_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, subtitle) },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricMint,
                    contentColor = ThemeOnPrimary
                ),
                modifier = Modifier.testTag("create_playlist_confirm_button")
            ) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedSlate)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistBottomSheet(
    song: SongItem,
    playlists: List<PlaylistItem>,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onStartRadio: () -> Unit = {},
    onSelectPlaylist: (PlaylistItem) -> Unit,
    onCreateNewPlaylist: (String, String) -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        contentColor = AlabasterWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Queue & Playlist Actions for \"${song.title}\"",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = AlabasterWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Quick Queue Actions Row: Play Next vs. Add to End of Queue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = ElectricMint.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(0.75.dp, ElectricMint.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                        .clickable(onClick = onPlayNext)
                        .testTag("sheet_play_next_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SkipNext,
                            contentDescription = null,
                            tint = ElectricMint,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Play Next",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = ElectricMint
                        )
                    }
                }

                Surface(
                    color = CyberCyan.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(0.75.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable(onClick = onAddToQueue)
                        .testTag("sheet_add_to_queue_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Add to Queue",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = CyberCyan
                        )
                    }
                }
            }

            // Start Song Radio Pill Row (replaces queue & starts playback immediately)
            Surface(
                color = CarbonElevated,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(0.75.dp, NeonViolet.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onStartRadio)
                    .testTag("sheet_start_song_radio_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Radio,
                        contentDescription = null,
                        tint = ElectricMint,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Start Song Radio",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = AlabasterWhite
                        )
                        Text(
                            text = "Replaces Up Next queue with similar tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedSlate
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("New custom playlist name...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricMint,
                        unfocusedBorderColor = SubtleBorder,
                        focusedTextColor = AlabasterWhite,
                        unfocusedTextColor = AlabasterWhite,
                        cursorColor = ElectricMint
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreateNewPlaylist(newPlaylistName, "Custom Echoic Playlist")
                        }
                    },
                    enabled = newPlaylistName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricMint,
                        contentColor = ThemeOnPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Create")
                }
            }

            if (playlists.isEmpty()) {
                Text(
                    text = "No playlists saved yet. Type a name above to create one!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlate
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(220.dp)
                ) {
                    items(
                        items = playlists,
                        key = { "sheet_pl_${it.id}" }
                    ) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CarbonElevated)
                                .clickable { onSelectPlaylist(playlist) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                PlatformBadgePill(platform = playlist.sourcePlatform)
                                Text(
                                    text = playlist.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = AlabasterWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            Text(
                                text = "${playlist.trackCount} tracks",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedSlate
                            )
                        }
                    }
                }
            }
        }
    }
}

