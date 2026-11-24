package com.example.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.R
import com.example.data.local.MusicDatabase
import com.example.data.local.PlaybackSessionStore
import com.example.data.model.PlayerRepeatMode
import com.example.data.model.SongItem
import com.example.data.repository.MusicRepository
import com.example.widget.MiniPlayerWidgetProvider
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var crossfadePlayer: ExoPlayer? = null
    private lateinit var cacheDataSourceFactory: CacheDataSource.Factory
    private lateinit var loadControl: DefaultLoadControl
    private lateinit var audioAttributes: AudioAttributes
    private lateinit var forwardingPlayer: SpiralForwardingPlayer
    private lateinit var repository: MusicRepository

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var isEqEnabled: Boolean = false
    private val savedBandLevelsMb = ShortArray(5) { idx ->
        when (idx) {
            0 -> 300
            1 -> 150
            2 -> 0
            3 -> 200
            else -> 350
        }
    }
    private var savedBassBoostStrength: Short = 250

    private var crossfadeSeconds: Int = 0
    private var gaplessPlaybackEnabled: Boolean = true
    private var isServiceShuffled: Boolean = false
    private var serviceRepeatMode: PlayerRepeatMode = PlayerRepeatMode.OFF

    private var transitionWakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var fadeOutJob: Job? = null
    private var backgroundSkipJob: Job? = null
    private var lookaheadPreResolveJob: Job? = null
    private var backgroundAutoQueueJob: Job? = null
    private var transitionMonitorJob: Job? = null
    private var activeCrossfadeJob: Job? = null
    private var preparedCrossfadeSongId: String? = null
    private var preparedCrossfadeUrl: String? = null
    private var crossfadeTriggeredTrackId: String? = null
    private var serviceConsecutiveFailures: Int = 0
    private val servicePlaySeq = java.util.concurrent.atomic.AtomicLong(0L)

    override fun onCreate() {
        super.onCreate()

        val musicDao = runCatching {
            MusicDatabase.getInstance(applicationContext).musicDao()
        }.getOrNull()
        repository = MusicRepository(musicDao)

        // Restore saved audio/EQ preferences immediately so cold-boot widget playback uses user profile
        val savedAudio = PlaybackSessionStore.loadAudioSettings(applicationContext)
        isEqEnabled = savedAudio.isEnabled
        savedAudio.bands.forEachIndexed { idx, band ->
            if (idx in savedBandLevelsMb.indices) {
                savedBandLevelsMb[idx] = band.currentLevelMb
            }
        }
        savedBassBoostStrength = savedAudio.bassBoostStrength.toShort()
        crossfadeSeconds = 0
        gaplessPlaybackEnabled = true

        // Restore persisted queue/session if service started fresh (e.g. from widget or notification)
        if (activeServiceSong == null && serviceQueue.isEmpty()) {
            PlaybackSessionStore.loadSession(applicationContext)?.let { session ->
                activeServiceSong = session.currentTrack
                serviceQueue = session.queue
                isServiceShuffled = session.isShuffled
                serviceRepeatMode = runCatching {
                    PlayerRepeatMode.valueOf(session.repeatModeName)
                }.getOrDefault(PlayerRepeatMode.OFF)
            }
        }

        // 1. Configure 500MB LRU Disk Cache wrapped in CacheDataSource.Factory
        // Use ResolvingDataSource so YouTube googlevideo.com streams (c=ANDROID_VR, c=IOS, etc.)
        // receive the exact matching client User-Agent header instead of getting HTTP 403 Forbidden.
        val simpleCache = getOrCreateCache(applicationContext)
        val upstreamHttpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(null)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(12_000)
            .setAllowCrossProtocolRedirects(true)
        val baseUpstreamFactory = DefaultDataSource.Factory(applicationContext, upstreamHttpFactory)
        val resolvingUpstreamFactory = ResolvingDataSource.Factory(baseUpstreamFactory) { dataSpec: DataSpec ->
            val urlString = dataSpec.uri.toString()
            val dynamicUserAgent = MusicRepository.resolveStreamUserAgent(urlString)
            dataSpec.withAdditionalHeaders(mapOf("User-Agent" to dynamicUserAgent))
        }

        cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(resolvingUpstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // 2. Configure DefaultLoadControl tuned for ultra-fast 500ms audio start
        loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 3. Configure AudioAttributes with automatic audio focus & ducking
        audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                attachHardwareAudioEffects(audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val activeSong = activeServiceSong ?: extractSongFromMediaItem(player.currentMediaItem)
                MiniPlayerWidgetProvider.syncWidget(applicationContext, activeSong, isPlaying)
                if (!isPlaying && activeSong != null) {
                    persistServiceSessionSnapshot()
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                val activeSong = activeServiceSong ?: extractSongFromMediaItem(player.currentMediaItem)
                updateNotificationActionButtons(
                    isFavorite = activeSong?.isFavorite == true,
                    isShuffled = isServiceShuffled || shuffleModeEnabled
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val transitionedSong = extractSongFromMediaItem(mediaItem)
                if (transitionedSong != null) {
                    val prevId = activeServiceSong?.id
                    activeServiceSong = transitionedSong
                    crossfadeTriggeredTrackId = null
                    updateNotificationActionButtons(
                        isFavorite = transitionedSong.isFavorite,
                        isShuffled = isServiceShuffled
                    )
                    MiniPlayerWidgetProvider.syncWidget(applicationContext, transitionedSong, player.isPlaying)
                    persistServiceSessionSnapshot()
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && prevId != transitionedSong.id) {
                        serviceScope.launch {
                            _serviceEvents.emit(ServicePlaybackEvent.TrackTransitioned(transitionedSong))
                        }
                    }
                    scheduleLookaheadPreResolve(transitionedSong)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    serviceConsecutiveFailures = 0
                    val activeSong = activeServiceSong ?: extractSongFromMediaItem(player.currentMediaItem)
                    if (activeSong != null) {
                        MiniPlayerWidgetProvider.syncWidget(applicationContext, activeSong, player.isPlaying)
                        scheduleLookaheadPreResolve(activeSong)
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    if (stopAfterCurrentTrackFlag) {
                        cancelActiveCrossfade()
                        stopAfterCurrentTrackFlag = false
                        player.pause()
                        MiniPlayerWidgetProvider.syncWidget(
                            applicationContext,
                            activeServiceSong ?: extractSongFromMediaItem(player.currentMediaItem),
                            false
                        )
                        persistServiceSessionSnapshot()
                        serviceScope.launch {
                            _serviceEvents.emit(ServicePlaybackEvent.SleepTimerEndedTrack)
                        }
                        return
                    }
                    if (activeCrossfadeJob?.isActive == true || backgroundSkipJob?.isActive == true) {
                        // Track transition is already actively running
                        return
                    }
                    if (serviceRepeatMode == PlayerRepeatMode.ONE || player.repeatMode == Player.REPEAT_MODE_ONE) {
                        player.seekTo(0L)
                        player.playWhenReady = true
                        player.play()
                    } else {
                        // Autonomous background auto-advance (foreground, background, or screen-off)
                        handleBackgroundSkipNext(autoTriggered = true)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                cancelActiveCrossfade()
                val activeSong = activeServiceSong ?: extractSongFromMediaItem(player.currentMediaItem) ?: return
                val failedUrl = activeSong.streamUrl
                repository.invalidateStreamUrl(activeSong.id, failedUrl)
                replaceSongInServiceQueue(activeSong.copy(streamUrl = ""))
                acquireTransitionWakeLock()

                val seq = servicePlaySeq.incrementAndGet()
                backgroundSkipJob?.cancel()
                backgroundSkipJob = serviceScope.launch {
                    try {
                        val alternateUrl = withContext(Dispatchers.IO) {
                            repository.getAlternateStreamUrl(activeSong, failedUrl)
                        }
                        if (seq != servicePlaySeq.get()) return@launch
                        if (!alternateUrl.isNullOrBlank()) {
                            serviceConsecutiveFailures = 0
                            val recoveredSong = activeSong.copy(streamUrl = alternateUrl)
                            activeServiceSong = recoveredSong
                            replaceSongInServiceQueue(recoveredSong)
                            playMediaItemAtomically(recoveredSong)
                            MiniPlayerWidgetProvider.syncWidget(applicationContext, recoveredSong, true)
                            persistServiceSessionSnapshot()
                            _serviceEvents.emit(ServicePlaybackEvent.TrackTransitioned(recoveredSong))
                            scheduleLookaheadPreResolve(recoveredSong)
                        } else {
                            serviceConsecutiveFailures++
                            if (serviceConsecutiveFailures < 5 && (serviceQueue.size > 1 || autoQueueEnabled)) {
                                handleBackgroundSkipNext(autoTriggered = true)
                            } else {
                                MiniPlayerWidgetProvider.syncWidget(applicationContext, activeSong, false)
                                _serviceEvents.emit(ServicePlaybackEvent.StreamUnavailable(activeSong))
                            }
                        }
                    } finally {
                        releaseTransitionWakeLock()
                    }
                }
            }
        })

        applyExoPlayerRepeatMode()

        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            attachHardwareAudioEffects(player.audioSessionId)
        }

        startTransitionMonitorLoop()

        // 4. Wrap ExoPlayer in SpiralForwardingPlayer so Lock Screen, Notification,
        // and Bluetooth headsets always show active Next & Previous buttons
        forwardingPlayer = SpiralForwardingPlayer(player)

        // 5. PendingIntent to open MainActivity from lock-screen / system notification
        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(SpiralSessionCallback())
            .setCustomLayout(
                buildMediaActionCommandLayout(
                    isFavorite = activeServiceSong?.isFavorite == true,
                    isShuffled = isServiceShuffled
                )
            )
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_notif_spiral)
            }
        setMediaNotificationProvider(notificationProvider)
    }

    private fun isTrackTransitionInFlight(): Boolean {
        return backgroundSkipJob?.isActive == true || activeCrossfadeJob?.isActive == true
    }

    private fun acquireTransitionWakeLock(timeoutMs: Long = 25_000L) {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            val wl = transitionWakeLock ?: pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Echoic:PlaybackTransitionWakeLock"
            ).apply {
                setReferenceCounted(false)
                transitionWakeLock = this
            }
            wl.acquire(timeoutMs)
        }
    }

    private fun releaseTransitionWakeLock() {
        runCatching {
            transitionWakeLock?.let { if (it.isHeld) it.release() }
        }
    }

    /**
     * Atomically swaps the active MediaItem on ExoPlayer WITHOUT calling player.stop() or player.clearMediaItems().
     * This prevents MediaSessionService from seeing mediaItemCount == 0 and dismissing the notification panel.
     */
    private fun playMediaItemAtomically(song: SongItem, startPositionMs: Long = 0L) {
        if (!::player.isInitialized) return
        player.volume = 1.0f
        val item = buildMediaItemForSong(song)
        player.setMediaItem(item, startPositionMs.coerceAtLeast(0L))
        player.playWhenReady = true
        player.prepare()
        player.play()
    }

    private fun applyExoPlayerRepeatMode() {
        if (!::player.isInitialized) return
        // Only REPEAT_MODE_ONE is delegated to ExoPlayer natively; REPEAT_MODE_ALL is managed over the full
        // serviceQueue so a 1-2 item ExoPlayer timeline never loops on a single track.
        player.repeatMode = if (serviceRepeatMode == PlayerRepeatMode.ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    private fun ensureQueueLoadedFromStorage() {
        if (serviceQueue.isEmpty() || activeServiceSong == null) {
            PlaybackSessionStore.loadSession(applicationContext)?.let { saved ->
                if (serviceQueue.isEmpty() && saved.queue.isNotEmpty()) {
                    serviceQueue = saved.queue
                }
                if (activeServiceSong == null && saved.currentTrack != null) {
                    activeServiceSong = saved.currentTrack
                }
                isServiceShuffled = saved.isShuffled
                serviceRepeatMode = runCatching {
                    PlayerRepeatMode.valueOf(saved.repeatModeName)
                }.getOrDefault(serviceRepeatMode)
            }
        }
    }

    private fun getOrCreateCrossfadePlayer(): ExoPlayer {
        crossfadePlayer?.let { return it }
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { cf ->
                cf.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        val failedId = preparedCrossfadeSongId
                        val failedUrl = preparedCrossfadeUrl
                        if (!failedId.isNullOrBlank()) {
                            repository.invalidateStreamUrl(failedId, failedUrl.orEmpty())
                        }
                        preparedCrossfadeSongId = null
                        preparedCrossfadeUrl = null
                    }
                })
                crossfadePlayer = cf
            }
    }

    private fun cancelActiveCrossfade() {
        activeCrossfadeJob?.cancel()
        activeCrossfadeJob = null
        runCatching {
            crossfadePlayer?.let { cf ->
                cf.pause()
                cf.stop()
                cf.clearMediaItems()
                cf.volume = 0f
            }
        }
        if (::player.isInitialized && fadeOutJob?.isActive != true) {
            player.volume = 1.0f
        }
    }

    /**
     * Monitors playback progress inside PlaybackService to drive:
     * 1. Pre-buffering of the next song when Crossfade is enabled
     * 2. Equal-power dual-player audio crossfade overlap
     * 3. Watchdog recovery if ExoPlayer ever rests in STATE_ENDED without advancing
     * 4. Periodic session state checkpointing for "Resume Where You Left Off"
     */
    private fun startTransitionMonitorLoop() {
        transitionMonitorJob?.cancel()
        transitionMonitorJob = serviceScope.launch {
            var tickCounter = 0
            while (isActive) {
                delay(250L)
                if (!::player.isInitialized) continue
                val playing = player.isPlaying
                val activeSong = activeServiceSong ?: extractSongFromMediaItem(player.currentMediaItem)

                // Safety watchdog: never allow a finished track to sit stuck/paused in STATE_ENDED
                if (player.playbackState == Player.STATE_ENDED &&
                    !stopAfterCurrentTrackFlag &&
                    !isTrackTransitionInFlight() &&
                    activeSong != null
                ) {
                    if (serviceRepeatMode == PlayerRepeatMode.ONE) {
                        player.seekTo(0L)
                        player.playWhenReady = true
                        player.play()
                    } else {
                        handleBackgroundSkipNext(autoTriggered = true)
                    }
                    continue
                }

                if (playing && activeSong != null) {
                    tickCounter++
                    if (tickCounter % 16 == 0) { // ~every 4 seconds
                        persistServiceSessionSnapshot()
                    }

                    // Proactively replenish upcoming queue inside service so Crossfade & Gapless always have next tracks ready
                    val queueSnapshot = serviceQueue
                    val curIdx = queueSnapshot.indexOfFirst { it.id == activeSong.id }
                    val remainingAhead = if (curIdx >= 0) (queueSnapshot.lastIndex - curIdx) else queueSnapshot.lastIndex
                    if (autoQueueEnabled &&
                        serviceRepeatMode == PlayerRepeatMode.OFF &&
                        remainingAhead < 4 &&
                        backgroundAutoQueueJob?.isActive != true
                    ) {
                        val seed = queueSnapshot.lastOrNull() ?: activeSong
                        backgroundAutoQueueJob = serviceScope.launch {
                            fetchAndAppendServiceRadioBatch(seed, serviceQueue)
                        }
                    }

                    val cfSec = crossfadeSeconds
                    val totalDur = player.duration
                    val currentPos = player.currentPosition.coerceAtLeast(0L)
                    if (cfSec > 0 &&
                        totalDur > 25_000L &&
                        serviceRepeatMode != PlayerRepeatMode.ONE &&
                        !stopAfterCurrentTrackFlag &&
                        activeCrossfadeJob?.isActive != true &&
                        backgroundSkipJob?.isActive != true &&
                        crossfadeTriggeredTrackId != activeSong.id
                    ) {
                        val remainingMs = totalDur - currentPos
                        val cfWindowMs = cfSec * 1_000L
                        val nextSong = peekNextQueueSong(activeSong.id)

                        // Warm up secondary player ~14s ahead of transition
                        if (nextSong != null &&
                            remainingMs in (cfWindowMs + 500L)..(cfWindowMs + 14_000L) &&
                            preparedCrossfadeSongId != nextSong.id
                        ) {
                            prepareCrossfadeNextTrack(nextSong)
                        }

                        // Launch smooth equal-power dual-player overlap fade inside crossfade window
                        if (nextSong != null && remainingMs in 350L..cfWindowMs) {
                            crossfadeTriggeredTrackId = activeSong.id
                            launchDualPlayerCrossfade(
                                incomingSong = nextSong,
                                fadeDurationMs = remainingMs.coerceIn(800L, cfWindowMs)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun peekNextQueueSong(currentId: String): SongItem? {
        val queueSnapshot = serviceQueue
        if (queueSnapshot.size <= 1) return null
        val currentIdx = queueSnapshot.indexOfFirst { it.id == currentId }
        if (currentIdx < 0) return queueSnapshot.firstOrNull()
        if (currentIdx >= queueSnapshot.lastIndex && serviceRepeatMode == PlayerRepeatMode.OFF) {
            return null
        }
        val nextIdx = (currentIdx + 1) % queueSnapshot.size
        return queueSnapshot.getOrNull(nextIdx)?.takeIf { it.id != currentId }
    }

    private fun prepareCrossfadeNextTrack(nextSong: SongItem) {
        preparedCrossfadeSongId = nextSong.id
        serviceScope.launch {
            val resolvedUrl = withContext(Dispatchers.IO) {
                val localUri = repository.getOfflineLocalFileUri(nextSong.id)
                when {
                    !localUri.isNullOrBlank() -> localUri
                    repository.isFullLengthStreamUrl(nextSong.streamUrl) -> nextSong.streamUrl
                    else -> repository.getCachedValidStreamUrl(nextSong.id)
                        ?: repository.getStreamUrl(
                            videoId = nextSong.id,
                            trackTitle = nextSong.title,
                            artistName = nextSong.artist
                        ).getOrDefault("")
                }
            }
            if (resolvedUrl.isBlank() || preparedCrossfadeSongId != nextSong.id) {
                if (preparedCrossfadeSongId == nextSong.id) {
                    preparedCrossfadeSongId = null
                    preparedCrossfadeUrl = null
                }
                return@launch
            }
            val updatedNext = nextSong.copy(streamUrl = resolvedUrl)
            preparedCrossfadeUrl = resolvedUrl
            replaceSongInServiceQueue(updatedNext)

            val cfPlayer = getOrCreateCrossfadePlayer()
            cfPlayer.volume = 0f
            cfPlayer.playWhenReady = false
            cfPlayer.setMediaItem(buildMediaItemForSong(updatedNext), 0L)
            cfPlayer.prepare()
        }
    }

    private fun launchDualPlayerCrossfade(incomingSong: SongItem, fadeDurationMs: Long) {
        activeCrossfadeJob?.cancel()
        acquireTransitionWakeLock()
        activeCrossfadeJob = serviceScope.launch {
            var handoffCompleted = false
            var userPausedDuringFade = false
            try {
                val resolvedUrl = if (preparedCrossfadeSongId == incomingSong.id &&
                    !preparedCrossfadeUrl.isNullOrBlank() &&
                    repository.isFullLengthStreamUrl(preparedCrossfadeUrl!!)
                ) {
                    preparedCrossfadeUrl!!
                } else {
                    withContext(Dispatchers.IO) {
                        val localUri = repository.getOfflineLocalFileUri(incomingSong.id)
                        when {
                            !localUri.isNullOrBlank() -> localUri
                            repository.isFullLengthStreamUrl(incomingSong.streamUrl) -> incomingSong.streamUrl
                            else -> repository.getCachedValidStreamUrl(incomingSong.id)
                                ?: repository.getStreamUrl(
                                    videoId = incomingSong.id,
                                    trackTitle = incomingSong.title,
                                    artistName = incomingSong.artist
                                ).getOrDefault("")
                        }
                    }
                }

                if (resolvedUrl.isBlank()) {
                    return@launch
                }

                val updatedNext = incomingSong.copy(streamUrl = resolvedUrl)
                replaceSongInServiceQueue(updatedNext)

                val cfPlayer = getOrCreateCrossfadePlayer()
                if (preparedCrossfadeSongId != updatedNext.id || cfPlayer.mediaItemCount == 0 || cfPlayer.playerError != null) {
                    cfPlayer.setMediaItem(buildMediaItemForSong(updatedNext), 0L)
                    cfPlayer.prepare()
                }

                // Wait briefly (up to 600ms) for cfPlayer to be buffered before fading outgoing track down
                var waitMs = 0L
                while (isActive &&
                    cfPlayer.playbackState == Player.STATE_BUFFERING &&
                    cfPlayer.playerError == null &&
                    waitMs < 600L &&
                    player.playbackState != Player.STATE_ENDED
                ) {
                    delay(40L)
                    waitMs += 40L
                }

                val canOverlapFade = cfPlayer.playbackState == Player.STATE_READY &&
                    cfPlayer.playerError == null &&
                    player.isPlaying

                if (canOverlapFade) {
                    cfPlayer.volume = 0.02f
                    cfPlayer.play()

                    val stepMs = 40L
                    val steps = (fadeDurationMs / stepMs).toInt().coerceIn(10, 240)
                    for (step in 1..steps) {
                        if (!isActive) return@launch
                        if (!player.playWhenReady || stopAfterCurrentTrackFlag) {
                            userPausedDuringFade = true
                            cfPlayer.pause()
                            player.volume = 1.0f
                            return@launch
                        }
                        if (player.playbackState == Player.STATE_ENDED ||
                            player.playbackState == Player.STATE_IDLE ||
                            !player.isPlaying ||
                            cfPlayer.playerError != null
                        ) {
                            break
                        }
                        val t = step.toFloat() / steps.toFloat()
                        // Equal-power fade curve avoids volume dip in the middle of overlap
                        val outgoingVol = cos(t * (PI / 2.0)).toFloat().coerceIn(0f, 1f)
                        val incomingVol = sin(t * (PI / 2.0)).toFloat().coerceIn(0f, 1f)
                        player.volume = outgoingVol
                        cfPlayer.volume = incomingVol
                        delay(stepMs)
                    }
                }

                val handoffPosMs = if (canOverlapFade && cfPlayer.playerError == null) {
                    cfPlayer.currentPosition.coerceAtLeast(0L)
                } else {
                    0L
                }
                cfPlayer.pause()
                cfPlayer.stop()
                cfPlayer.clearMediaItems()
                cfPlayer.volume = 0f
                preparedCrossfadeSongId = null
                preparedCrossfadeUrl = null

                // Atomically hand playback to primary MediaSession player WITHOUT clearing items
                serviceConsecutiveFailures = 0
                activeServiceSong = updatedNext
                playMediaItemAtomically(
                    song = updatedNext,
                    startPositionMs = if (handoffPosMs > 200L) handoffPosMs else 0L
                )
                handoffCompleted = true

                updateNotificationActionButtons(
                    isFavorite = updatedNext.isFavorite,
                    isShuffled = isServiceShuffled
                )
                MiniPlayerWidgetProvider.syncWidget(applicationContext, updatedNext, true)
                persistServiceSessionSnapshot()
                _serviceEvents.emit(ServicePlaybackEvent.TrackTransitioned(updatedNext))
                scheduleLookaheadPreResolve(updatedNext)
            } finally {
                releaseTransitionWakeLock()
                if (!handoffCompleted) {
                    if (::player.isInitialized && fadeOutJob?.isActive != true) {
                        player.volume = 1.0f
                    }
                    runCatching {
                        crossfadePlayer?.let { cf ->
                            cf.pause()
                            cf.stop()
                            cf.clearMediaItems()
                            cf.volume = 0f
                        }
                    }
                    // Guarantee playback never stalls at STATE_ENDED if crossfade failed mid-way
                    if (!userPausedDuringFade &&
                        !stopAfterCurrentTrackFlag &&
                        ::player.isInitialized &&
                        player.playbackState == Player.STATE_ENDED
                    ) {
                        serviceScope.launch {
                            handleBackgroundSkipNext(autoTriggered = true)
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MiniPlayerWidgetProvider.ACTION_WIDGET_PLAY_PAUSE -> {
                if (::player.isInitialized) {
                    if (player.isPlaying) {
                        player.pause()
                        persistServiceSessionSnapshot()
                    } else if (player.mediaItemCount > 0) {
                        player.play()
                    } else {
                        val savedSession = PlaybackSessionStore.loadSession(applicationContext)
                        if (activeServiceSong == null && savedSession != null) {
                            serviceQueue = savedSession.queue
                            activeServiceSong = savedSession.currentTrack
                            isServiceShuffled = savedSession.isShuffled
                        }
                        val targetSong = activeServiceSong ?: savedSession?.currentTrack
                        val resumePos = savedSession?.takeIf { it.currentTrack?.id == targetSong?.id }?.progressMs ?: 0L
                        targetSong?.let { resolveAndPlayInService(it, startPositionMs = resumePos) }
                    }
                    MiniPlayerWidgetProvider.syncWidget(
                        applicationContext,
                        activeServiceSong ?: extractSongFromMediaItem(player.currentMediaItem),
                        player.isPlaying
                    )
                }
            }
            MiniPlayerWidgetProvider.ACTION_WIDGET_NEXT -> {
                if (::player.isInitialized) {
                    if (activeServiceSong == null && serviceQueue.isEmpty()) {
                        PlaybackSessionStore.loadSession(applicationContext)?.let { saved ->
                            serviceQueue = saved.queue
                            activeServiceSong = saved.currentTrack
                            isServiceShuffled = saved.isShuffled
                        }
                    }
                    handleBackgroundSkipNext(autoTriggered = false)
                }
            }
            MiniPlayerWidgetProvider.ACTION_WIDGET_FAVORITE -> {
                if (::player.isInitialized) {
                    if (activeServiceSong == null) {
                        PlaybackSessionStore.loadSession(applicationContext)?.let { saved ->
                            serviceQueue = saved.queue
                            activeServiceSong = saved.currentTrack
                        }
                    }
                    toggleCurrentTrackFavoriteFromNotification()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * ForwardingPlayer that enables Next and Previous skip actions on Lock Screen,
     * Notification Shade, Android Auto, and Bluetooth headsets even when ExoPlayer
     * holds a single dynamically resolved MediaItem.
     */
    private inner class SpiralForwardingPlayer(wrapped: ExoPlayer) : ForwardingPlayer(wrapped) {

        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands()
                .buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                else -> super.isCommandAvailable(command)
            }
        }

        override fun hasNextMediaItem(): Boolean = true

        override fun hasPreviousMediaItem(): Boolean = true

        override fun play() {
            if (mediaItemCount == 0) {
                ensureQueueLoadedFromStorage()
                val targetSong = activeServiceSong ?: serviceQueue.firstOrNull()
                if (targetSong != null) {
                    val savedSession = PlaybackSessionStore.loadSession(applicationContext)
                    val resumePos = savedSession?.takeIf { it.currentTrack?.id == targetSong.id }?.progressMs ?: 0L
                    resolveAndPlayInService(targetSong, startPositionMs = resumePos)
                    return
                }
            }
            super.play()
        }

        override fun seekToNext() {
            handleBackgroundSkipNext(autoTriggered = false)
        }

        override fun seekToNextMediaItem() {
            handleBackgroundSkipNext(autoTriggered = false)
        }

        override fun seekToPrevious() {
            if (currentPosition > 3_500L) {
                seekTo(0L)
            } else {
                handleBackgroundSkipPrevious()
            }
        }

        override fun seekToPreviousMediaItem() {
            if (currentPosition > 3_500L) {
                seekTo(0L)
            } else {
                handleBackgroundSkipPrevious()
            }
        }
    }

    /**
     * Resolves and plays the next song in [serviceQueue] directly inside [PlaybackService]
     * so background auto-advance and lock-screen/Bluetooth/Notification Next work reliably even when the app is closed or paused.
     */
    private fun handleBackgroundSkipNext(autoTriggered: Boolean) {
        cancelActiveCrossfade()
        if (!autoTriggered) {
            serviceConsecutiveFailures = 0
        }
        ensureQueueLoadedFromStorage()
        val queueSnapshot = serviceQueue.ifEmpty {
            listOfNotNull(activeServiceSong)
        }
        if (queueSnapshot.isEmpty()) return

        val currentId = activeServiceSong?.id ?: player.currentMediaItem?.mediaId
        val currentIndex = queueSnapshot.indexOfFirst { it.id == currentId }
        val remainingAhead = if (currentIndex >= 0) (queueSnapshot.lastIndex - currentIndex) else queueSnapshot.lastIndex

        if (serviceRepeatMode == PlayerRepeatMode.OFF &&
            (currentIndex >= queueSnapshot.lastIndex || queueSnapshot.size <= 1)
        ) {
            val seed = activeServiceSong ?: queueSnapshot.lastOrNull() ?: return
            val seq = servicePlaySeq.incrementAndGet()
            backgroundSkipJob?.cancel()
            acquireTransitionWakeLock()

            // NEVER call player.stop() or player.clearMediaItems() here — doing so dismisses the
            // system media notification and causes Android to kill the background service!
            if (!autoTriggered && ::player.isInitialized) {
                if (player.isPlaying) {
                    player.volume = 0f
                } else {
                    mediaSession?.let { runCatching { onUpdateNotification(it, true) } }
                }
            }
            backgroundSkipJob = serviceScope.launch {
                try {
                    val added = fetchAndAppendServiceRadioBatch(seed, queueSnapshot)
                    if (seq != servicePlaySeq.get()) return@launch
                    if (added.isNotEmpty()) {
                        resolveAndPlayInService(added.first())
                    } else {
                        val otherTracks = queueSnapshot.filter { it.id != currentId }
                        val fallbackTrack = if (otherTracks.isNotEmpty()) {
                            if (isServiceShuffled) otherTracks.random() else otherTracks.first()
                        } else {
                            queueSnapshot.first()
                        }
                        resolveAndPlayInService(fallbackTrack)
                    }
                } finally {
                    releaseTransitionWakeLock()
                }
            }
            return
        }

        if (autoQueueEnabled &&
            serviceRepeatMode == PlayerRepeatMode.OFF &&
            remainingAhead < 5 &&
            backgroundAutoQueueJob?.isActive != true
        ) {
            val seed = queueSnapshot.lastOrNull() ?: activeServiceSong
            if (seed != null) {
                backgroundAutoQueueJob = serviceScope.launch {
                    fetchAndAppendServiceRadioBatch(seed, serviceQueue)
                }
            }
        }

        val nextIndex = if (currentIndex >= 0) {
            (currentIndex + 1) % queueSnapshot.size
        } else {
            0
        }
        val targetSong = queueSnapshot[nextIndex]
        resolveAndPlayInService(targetSong)
    }

    private suspend fun fetchAndAppendServiceRadioBatch(
        seed: SongItem,
        snapshot: List<SongItem>
    ): List<SongItem> {
        val excludeIds = snapshot.map { it.id }.toSet()
        val evolvingSeeds = snapshot.takeLast(3).reversed()
        val fetched = runCatching {
            repository.fetchSimilarRadioTracks(
                seedSong = seed,
                excludeIds = excludeIds,
                excludeSignatures = excludedSignaturesSnapshot,
                evolvingSeedSongs = evolvingSeeds,
                tasteGenres = tasteGenresSnapshot,
                tasteArtists = tasteArtistsSnapshot
            )
        }.getOrDefault(emptyList())

        val orderedFetched = if (isServiceShuffled) fetched.shuffled() else fetched
        if (orderedFetched.isNotEmpty()) {
            val merged = (serviceQueue + orderedFetched).distinctBy { it.id }
            syncQueueFromUi(merged, activeServiceSong)
            _serviceEvents.emit(ServicePlaybackEvent.AutoQueueReplenished(orderedFetched))
        }
        return orderedFetched
    }

    /**
     * Resolves and plays the previous song in [serviceQueue] directly inside [PlaybackService].
     */
    private fun handleBackgroundSkipPrevious() {
        cancelActiveCrossfade()
        serviceConsecutiveFailures = 0
        ensureQueueLoadedFromStorage()
        val queueSnapshot = serviceQueue
        if (queueSnapshot.isEmpty()) {
            player.seekTo(0L)
            return
        }

        val currentId = activeServiceSong?.id ?: player.currentMediaItem?.mediaId
        val currentIndex = queueSnapshot.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val prevIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else queueSnapshot.lastIndex

        val targetSong = queueSnapshot[prevIndex]
        resolveAndPlayInService(targetSong)
    }

    private fun resolveAndPlayInService(targetSong: SongItem, startPositionMs: Long = 0L) {
        val seq = servicePlaySeq.incrementAndGet()
        backgroundSkipJob?.cancel()
        cancelActiveCrossfade()
        preparedCrossfadeSongId = null
        preparedCrossfadeUrl = null
        crossfadeTriggeredTrackId = null

        // 1. INSTANT SYNCHRONOUS FAST-PATH:
        // If this track's full-length stream (or offline file) is already cached or pre-resolved,
        // swap atomically on the main thread right inside the button click / STATE_ENDED callback!
        val fastStreamUrl = targetSong.streamUrl.takeIf { repository.isFullLengthStreamUrl(it) }
            ?: repository.getCachedValidStreamUrl(targetSong.id)

        if (!fastStreamUrl.isNullOrBlank()) {
            serviceConsecutiveFailures = 0
            val updatedSong = targetSong.copy(streamUrl = fastStreamUrl)
            activeServiceSong = updatedSong
            replaceSongInServiceQueue(updatedSong)

            playMediaItemAtomically(updatedSong, startPositionMs)
            updateNotificationActionButtons(
                isFavorite = updatedSong.isFavorite,
                isShuffled = isServiceShuffled
            )
            MiniPlayerWidgetProvider.syncWidget(applicationContext, updatedSong, true)
            persistServiceSessionSnapshot()
            serviceScope.launch {
                _serviceEvents.emit(ServicePlaybackEvent.TrackTransitioned(updatedSong))
            }
            scheduleLookaheadPreResolve(updatedSong)
            return
        }

        // 2. NOTIFICATION-SAFE ASYNC PATH (when the track needs live network resolution):
        // NEVER call player.stop() or player.clearMediaItems() — keep the current MediaItem intact so
        // MediaSessionService keeps the notification alive while we resolve in ~300-600ms!
        acquireTransitionWakeLock()
        if (::player.isInitialized) {
            if (player.isPlaying) {
                player.volume = 0f
            } else {
                // Anchor foreground service immediately while the notification button PendingIntent exemption is active
                mediaSession?.let { runCatching { onUpdateNotification(it, true) } }
            }
        }

        backgroundSkipJob = serviceScope.launch {
            try {
                activeServiceSong = targetSong
                updateNotificationActionButtons(
                    isFavorite = targetSong.isFavorite,
                    isShuffled = isServiceShuffled
                )
                MiniPlayerWidgetProvider.syncWidget(applicationContext, targetSong, true)
                _serviceEvents.emit(ServicePlaybackEvent.TrackResolving(targetSong))

                val resolvedUrl = withContext(Dispatchers.IO) {
                    val localUri = repository.getOfflineLocalFileUri(targetSong.id)
                    when {
                        !localUri.isNullOrBlank() -> localUri
                        repository.isFullLengthStreamUrl(targetSong.streamUrl) -> targetSong.streamUrl
                        else -> repository.getStreamUrl(
                            videoId = targetSong.id,
                            trackTitle = targetSong.title,
                            artistName = targetSong.artist
                        ).getOrDefault("")
                    }
                }

                if (seq != servicePlaySeq.get()) {
                    return@launch
                }

                if (resolvedUrl.isBlank()) {
                    serviceConsecutiveFailures++
                    if (serviceConsecutiveFailures < 5 && (serviceQueue.size > 1 || autoQueueEnabled)) {
                        handleBackgroundSkipNext(autoTriggered = true)
                    } else {
                        if (::player.isInitialized) {
                            player.volume = 1.0f
                            player.pause()
                        }
                        MiniPlayerWidgetProvider.syncWidget(applicationContext, targetSong, false)
                        _serviceEvents.emit(ServicePlaybackEvent.StreamUnavailable(targetSong))
                    }
                    return@launch
                }

                serviceConsecutiveFailures = 0
                val updatedSong = targetSong.copy(streamUrl = resolvedUrl)
                activeServiceSong = updatedSong
                replaceSongInServiceQueue(updatedSong)

                playMediaItemAtomically(updatedSong, startPositionMs)

                updateNotificationActionButtons(
                    isFavorite = updatedSong.isFavorite,
                    isShuffled = isServiceShuffled
                )
                MiniPlayerWidgetProvider.syncWidget(applicationContext, updatedSong, true)
                persistServiceSessionSnapshot()
                _serviceEvents.emit(ServicePlaybackEvent.TrackTransitioned(updatedSong))
                scheduleLookaheadPreResolve(updatedSong)
            } finally {
                releaseTransitionWakeLock()
            }
        }
    }

    /**
     * Proactively pre-resolves the next 2 upcoming songs' full-length stream URLs in the background
     * so automatic track progression and notification/headset Next skips start instantaneously (0ms wait).
     * When Gapless Playback is enabled and Crossfade is off, attaches the next MediaItem natively onto ExoPlayer.
     */
    private fun scheduleLookaheadPreResolve(currentSong: SongItem) {
        lookaheadPreResolveJob?.cancel()
        lookaheadPreResolveJob = serviceScope.launch(Dispatchers.IO) {
            delay(350L)
            var queueSnapshot = serviceQueue
            if (queueSnapshot.isEmpty()) return@launch
            val currentIdx = queueSnapshot.indexOfFirst { it.id == currentSong.id }
            if (currentIdx < 0) return@launch

            // On Repeat OFF near the tail of the queue, proactively refill Auto-Queue Radio
            if (currentIdx >= queueSnapshot.lastIndex - 1 && serviceRepeatMode == PlayerRepeatMode.OFF) {
                if (autoQueueEnabled) {
                    val added = fetchAndAppendServiceRadioBatch(queueSnapshot.last(), queueSnapshot)
                    if (added.isNotEmpty()) {
                        queueSnapshot = serviceQueue
                    } else if (currentIdx >= queueSnapshot.lastIndex) {
                        return@launch
                    }
                } else if (currentIdx >= queueSnapshot.lastIndex) {
                    return@launch
                }
            }

            val updatedCurrentIdx = queueSnapshot.indexOfFirst { it.id == currentSong.id }
            if (updatedCurrentIdx < 0 || queueSnapshot.size <= 1) return@launch

            val lookaheadOffsets = if (queueSnapshot.size >= 3) listOf(1, 2) else listOf(1)
            for (offset in lookaheadOffsets) {
                if (!isActive) return@launch
                val targetIdx = if (serviceRepeatMode == PlayerRepeatMode.OFF) {
                    val candidateIdx = updatedCurrentIdx + offset
                    if (candidateIdx > queueSnapshot.lastIndex) continue
                    candidateIdx
                } else {
                    (updatedCurrentIdx + offset) % queueSnapshot.size
                }
                val upcomingSong = queueSnapshot[targetIdx]
                if (upcomingSong.id == currentSong.id) continue

                val localUri = repository.getOfflineLocalFileUri(upcomingSong.id)
                val resolvedUrl = when {
                    !localUri.isNullOrBlank() -> localUri
                    repository.isFullLengthStreamUrl(upcomingSong.streamUrl) -> upcomingSong.streamUrl
                    else -> repository.getCachedValidStreamUrl(upcomingSong.id)
                        ?: repository.getStreamUrl(
                            videoId = upcomingSong.id,
                            trackTitle = upcomingSong.title,
                            artistName = upcomingSong.artist
                        ).getOrDefault("")
                }
                if (resolvedUrl.isBlank()) continue

                val updatedUpcoming = upcomingSong.copy(streamUrl = resolvedUrl)
                replaceSongInServiceQueue(updatedUpcoming)

                // Queue the immediate next track natively into ExoPlayer for true 0ms gapless transition when Crossfade == 0
                if (offset == 1) {
                    withContext(Dispatchers.Main) {
                        if (!::player.isInitialized) return@withContext
                        val activeId = activeServiceSong?.id ?: player.currentMediaItem?.mediaId
                        if (activeId == currentSong.id &&
                            crossfadeSeconds == 0 &&
                            gaplessPlaybackEnabled &&
                            serviceRepeatMode != PlayerRepeatMode.ONE &&
                            !stopAfterCurrentTrackFlag
                        ) {
                            val curMediaIdx = player.currentMediaItemIndex
                            val nextMediaIdx = curMediaIdx + 1
                            val existingNextId = if (nextMediaIdx < player.mediaItemCount) {
                                player.getMediaItemAt(nextMediaIdx).mediaId
                            } else {
                                null
                            }
                            if (existingNextId != updatedUpcoming.id) {
                                if (nextMediaIdx < player.mediaItemCount) {
                                    player.removeMediaItems(nextMediaIdx, player.mediaItemCount)
                                }
                                player.addMediaItem(buildMediaItemForSong(updatedUpcoming))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun refreshGaplessMediaQueue() {
        if (!::player.isInitialized || player.mediaItemCount == 0) return
        val curIdx = player.currentMediaItemIndex
        val nextStart = curIdx + 1
        if (crossfadeSeconds > 0 || !gaplessPlaybackEnabled || serviceRepeatMode == PlayerRepeatMode.ONE) {
            if (nextStart < player.mediaItemCount) {
                player.removeMediaItems(nextStart, player.mediaItemCount)
            }
        } else {
            activeServiceSong?.let { scheduleLookaheadPreResolve(it) }
        }
    }

    private fun persistServiceSessionSnapshot() {
        val active = activeServiceSong ?: return
        val pos = if (::player.isInitialized) player.currentPosition.coerceAtLeast(0L) else 0L
        PlaybackSessionStore.saveSession(
            context = applicationContext,
            currentTrack = active,
            queue = serviceQueue,
            progressMs = pos,
            repeatMode = serviceRepeatMode,
            isShuffled = isServiceShuffled,
            autoQueuedIds = emptySet()
        )
    }

    private fun toggleCurrentTrackFavoriteFromNotification() {
        val currentSong = activeServiceSong ?: extractSongFromMediaItem(player.currentMediaItem) ?: return
        serviceScope.launch {
            val isNowFav = withContext(Dispatchers.IO) {
                repository.toggleFavorite(currentSong)
            }
            val updatedSong = currentSong.copy(isFavorite = isNowFav)
            activeServiceSong = updatedSong
            replaceSongInServiceQueue(updatedSong)
            updateNotificationActionButtons(
                isFavorite = isNowFav,
                isShuffled = isServiceShuffled
            )
            MiniPlayerWidgetProvider.syncWidget(applicationContext, updatedSong, player.isPlaying)
            _serviceEvents.emit(ServicePlaybackEvent.FavoriteToggled(updatedSong.id, isNowFav))
        }
    }

    private fun toggleShuffleFromNotification() {
        val nextShuffle = !isServiceShuffled
        isServiceShuffled = nextShuffle
        val activeSong = activeServiceSong ?: extractSongFromMediaItem(player.currentMediaItem)
        updateNotificationActionButtons(
            isFavorite = activeSong?.isFavorite == true,
            isShuffled = nextShuffle
        )
        serviceScope.launch {
            _serviceEvents.emit(ServicePlaybackEvent.ShuffleToggled(nextShuffle))
        }
    }

    private fun updateNotificationActionButtons(
        isFavorite: Boolean,
        isShuffled: Boolean = isServiceShuffled
    ) {
        val session = mediaSession ?: return
        session.setCustomLayout(buildMediaActionCommandLayout(isFavorite, isShuffled))
    }

    /**
     * Places Favorite in the left custom slot and Shuffle in the right custom slot
     * around Previous / Play-Pause / Next in Android lock-screen & notification media controls.
     */
    private fun buildMediaActionCommandLayout(
        isFavorite: Boolean,
        isShuffled: Boolean
    ): ImmutableList<CommandButton> {
        val favButton = CommandButton.Builder()
            .setDisplayName(if (isFavorite) "Remove from Liked" else "Save to Liked")
            .setIconResId(
                if (isFavorite) R.drawable.ic_notif_heart_filled
                else R.drawable.ic_notif_heart_outline
            )
            .setSessionCommand(SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
            .build()

        val shuffleButton = CommandButton.Builder()
            .setDisplayName(if (isShuffled) "Shuffle On" else "Shuffle Off")
            .setIconResId(
                if (isShuffled) R.drawable.ic_notif_shuffle_on
                else R.drawable.ic_notif_shuffle_off
            )
            .setSessionCommand(SessionCommand(CMD_TOGGLE_SHUFFLE, Bundle.EMPTY))
            .build()

        return ImmutableList.of(favButton, shuffleButton)
    }

    private fun attachHardwareAudioEffects(audioSessionId: Int) {
        if (!isEqEnabled) {
            disableAndReleaseHardwareAudioEffects()
            return
        }
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) return
        runCatching {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply {
                val bandCount = numberOfBands.toInt().coerceAtMost(savedBandLevelsMb.size)
                for (i in 0 until bandCount) {
                    setBandLevel(i.toShort(), savedBandLevelsMb[i])
                }
                enabled = true
            }
        }
        runCatching {
            bassBoost?.release()
            bassBoost = BassBoost(0, audioSessionId).apply {
                if (strengthSupported) {
                    setStrength(savedBassBoostStrength)
                }
                enabled = true
            }
        }
    }

    private fun disableAndReleaseHardwareAudioEffects() {
        runCatching {
            equalizer?.let { eq ->
                val bandCount = eq.numberOfBands.toInt()
                for (i in 0 until bandCount) {
                    runCatching { eq.setBandLevel(i.toShort(), 0.toShort()) }
                }
                eq.enabled = false
                eq.release()
            }
            equalizer = null
        }
        runCatching {
            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    runCatching { bb.setStrength(0.toShort()) }
                }
                bb.enabled = false
                bb.release()
            }
            bassBoost = null
        }
        runCatching {
            if (::player.isInitialized) {
                player.playbackParameters = PlaybackParameters(1.0f, 1.0f)
            }
        }
    }

    private fun startSmoothSleepFadeOut() {
        fadeOutJob?.cancel()
        fadeOutJob = serviceScope.launch {
            val steps = 40
            val stepDelayMs = 250L // 10 seconds total
            val initialVolume = player.volume.coerceIn(0.1f, 1.0f)

            for (step in steps downTo 0) {
                if (!player.isPlaying) break
                val fraction = step.toFloat() / steps.toFloat()
                player.volume = (initialVolume * fraction * fraction).coerceIn(0f, 1f)
                delay(stepDelayMs)
            }
            player.pause()
            player.volume = 1.0f
            persistServiceSessionSnapshot()
        }
    }

    private fun cancelFadeOutAndRestoreVolume() {
        fadeOutJob?.cancel()
        fadeOutJob = null
        player.volume = 1.0f
    }

    private inner class SpiralSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_START_FADE_OUT, Bundle.EMPTY))
                .add(SessionCommand(CMD_CANCEL_FADE_OUT, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_EQ_BAND, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_BASS_BOOST, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_EQ_ENABLED, Bundle.EMPTY))
                .add(SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(CMD_REFRESH_FAVORITE_ICON, Bundle.EMPTY))
                .add(SessionCommand(CMD_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(CMD_REFRESH_SHUFFLE_ICON, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_STOP_AFTER_CURRENT, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_AUTO_QUEUE_RADIO, Bundle.EMPTY))
                .add(SessionCommand(CMD_CANCEL_PENDING_LOAD, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_REPEAT_MODE, Bundle.EMPTY))
                .build()

            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_START_FADE_OUT -> startSmoothSleepFadeOut()
                CMD_CANCEL_FADE_OUT -> cancelFadeOutAndRestoreVolume()
                CMD_CANCEL_PENDING_LOAD -> {
                    servicePlaySeq.incrementAndGet()
                    backgroundSkipJob?.cancel()
                    backgroundSkipJob = null
                    cancelActiveCrossfade()
                    preparedCrossfadeSongId = null
                    preparedCrossfadeUrl = null
                    crossfadeTriggeredTrackId = null
                    // Never clearMediaItems() or stop() here so the system notification stays intact while resolving
                    if (args.getBoolean(ARG_STOP_PLAYER, false) && ::player.isInitialized && player.isPlaying) {
                        player.pause()
                    }
                }
                CMD_SET_REPEAT_MODE -> {
                    val modeName = args.getString(ARG_REPEAT_MODE).orEmpty()
                    serviceRepeatMode = runCatching {
                        PlayerRepeatMode.valueOf(modeName)
                    }.getOrDefault(PlayerRepeatMode.OFF)
                    applyExoPlayerRepeatMode()
                    refreshGaplessMediaQueue()
                }
                CMD_TOGGLE_FAVORITE -> toggleCurrentTrackFavoriteFromNotification()
                CMD_REFRESH_FAVORITE_ICON -> {
                    val isFav = args.getBoolean(ARG_IS_FAVORITE, activeServiceSong?.isFavorite == true)
                    activeServiceSong = activeServiceSong?.copy(isFavorite = isFav)
                    updateNotificationActionButtons(
                        isFavorite = isFav,
                        isShuffled = isServiceShuffled
                    )
                    MiniPlayerWidgetProvider.syncWidget(applicationContext, activeServiceSong, player.isPlaying)
                }
                CMD_TOGGLE_SHUFFLE -> toggleShuffleFromNotification()
                CMD_REFRESH_SHUFFLE_ICON -> {
                    val isShuffled = args.getBoolean(ARG_IS_SHUFFLED, isServiceShuffled)
                    isServiceShuffled = isShuffled
                    updateNotificationActionButtons(
                        isFavorite = activeServiceSong?.isFavorite == true,
                        isShuffled = isShuffled
                    )
                    refreshGaplessMediaQueue()
                }
                CMD_SET_STOP_AFTER_CURRENT -> {
                    stopAfterCurrentTrackFlag = args.getBoolean(ARG_STOP_AFTER_CURRENT, false)
                    refreshGaplessMediaQueue()
                }
                CMD_SET_AUTO_QUEUE_RADIO -> {
                    autoQueueEnabled = true
                }
                CMD_SET_EQ_BAND -> {
                    val bandIndex = args.getShort(ARG_BAND_INDEX, 0)
                    val levelMb = args.getShort(ARG_BAND_LEVEL, 0)
                    if (bandIndex in 0 until savedBandLevelsMb.size) {
                        savedBandLevelsMb[bandIndex.toInt()] = levelMb
                    }
                    if (isEqEnabled) {
                        runCatching {
                            equalizer?.let { eq ->
                                if (bandIndex in 0 until eq.numberOfBands) {
                                    eq.setBandLevel(bandIndex, levelMb)
                                }
                            }
                        }
                    }
                }
                CMD_SET_BASS_BOOST -> {
                    val strength = args.getInt(ARG_BASS_STRENGTH, 250).coerceIn(0, 1000).toShort()
                    savedBassBoostStrength = strength
                    if (isEqEnabled) {
                        runCatching {
                            bassBoost?.let { bb ->
                                if (bb.strengthSupported) {
                                    bb.setStrength(strength)
                                }
                            }
                        }
                    }
                }
                CMD_SET_EQ_ENABLED -> {
                    val enabled = args.getBoolean(ARG_EQ_ENABLED, false)
                    isEqEnabled = enabled
                    if (enabled) {
                        attachHardwareAudioEffects(player.audioSessionId)
                    } else {
                        disableAndReleaseHardwareAudioEffects()
                    }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val keepForeground = startInForegroundRequired || isTrackTransitionInFlight()
        runCatching {
            super.onUpdateNotification(session, keepForeground)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistServiceSessionSnapshot()
        if (isTrackTransitionInFlight()) return
        val sessionPlayer = mediaSession?.player
        if (sessionPlayer == null || !sessionPlayer.playWhenReady || sessionPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        persistServiceSessionSnapshot()
        releaseTransitionWakeLock()
        fadeOutJob?.cancel()
        backgroundSkipJob?.cancel()
        lookaheadPreResolveJob?.cancel()
        backgroundAutoQueueJob?.cancel()
        transitionMonitorJob?.cancel()
        cancelActiveCrossfade()
        runCatching { crossfadePlayer?.release() }
        crossfadePlayer = null
        serviceScope.cancel()
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    sealed interface ServicePlaybackEvent {
        data class TrackResolving(val song: SongItem) : ServicePlaybackEvent
        data class TrackTransitioned(val song: SongItem) : ServicePlaybackEvent
        data class StreamUnavailable(val song: SongItem) : ServicePlaybackEvent
        data class FavoriteToggled(val songId: String, val isFavorite: Boolean) : ServicePlaybackEvent
        data class ShuffleToggled(val isShuffled: Boolean) : ServicePlaybackEvent
        data class AutoQueueReplenished(val songs: List<SongItem>) : ServicePlaybackEvent
        data object SleepTimerEndedTrack : ServicePlaybackEvent
    }

    companion object {
        const val CMD_START_FADE_OUT = "com.example.somify.CMD_START_FADE_OUT"
        const val CMD_CANCEL_FADE_OUT = "com.example.somify.CMD_CANCEL_FADE_OUT"
        const val CMD_SET_EQ_BAND = "com.example.somify.CMD_SET_EQ_BAND"
        const val CMD_SET_BASS_BOOST = "com.example.somify.CMD_SET_BASS_BOOST"
        const val CMD_SET_EQ_ENABLED = "com.example.somify.CMD_SET_EQ_ENABLED"
        const val CMD_TOGGLE_FAVORITE = "com.example.spiral.CMD_TOGGLE_FAVORITE"
        const val CMD_REFRESH_FAVORITE_ICON = "com.example.spiral.CMD_REFRESH_FAVORITE_ICON"
        const val CMD_TOGGLE_SHUFFLE = "com.example.spiral.CMD_TOGGLE_SHUFFLE"
        const val CMD_REFRESH_SHUFFLE_ICON = "com.example.spiral.CMD_REFRESH_SHUFFLE_ICON"
        const val CMD_SET_STOP_AFTER_CURRENT = "com.example.spiral.CMD_SET_STOP_AFTER_CURRENT"
        const val CMD_SET_AUTO_QUEUE_RADIO = "com.example.spiral.CMD_SET_AUTO_QUEUE_RADIO"
        const val CMD_CANCEL_PENDING_LOAD = "com.example.spiral.CMD_CANCEL_PENDING_LOAD"
        const val CMD_SET_REPEAT_MODE = "com.example.spiral.CMD_SET_REPEAT_MODE"

        const val ARG_BAND_INDEX = "arg_band_index"
        const val ARG_BAND_LEVEL = "arg_band_level"
        const val ARG_BASS_STRENGTH = "arg_bass_strength"
        const val ARG_EQ_ENABLED = "arg_eq_enabled"
        const val ARG_IS_FAVORITE = "arg_is_favorite"
        const val ARG_IS_SHUFFLED = "arg_is_shuffled"
        const val ARG_STOP_AFTER_CURRENT = "arg_stop_after_current"
        const val ARG_AUTO_QUEUE_ENABLED = "arg_auto_queue_enabled"
        const val ARG_STOP_PLAYER = "arg_stop_player"
        const val ARG_REPEAT_MODE = "arg_repeat_mode"

        private const val EXTRA_SONG_ID = "extra_song_id"
        private const val EXTRA_SONG_TITLE = "extra_song_title"
        private const val EXTRA_SONG_ARTIST = "extra_song_artist"
        private const val EXTRA_SONG_ALBUM = "extra_song_album"
        private const val EXTRA_SONG_ARTWORK = "extra_song_artwork"
        private const val EXTRA_SONG_DURATION = "extra_song_duration"
        private const val EXTRA_SONG_STREAM_URL = "extra_song_stream_url"
        private const val EXTRA_SONG_MOOD = "extra_song_mood"
        private const val EXTRA_SONG_FAVORITE = "extra_song_favorite"

        private const val MAX_CACHE_BYTES = 500L * 1024L * 1024L // 500MB LRU Disk Cache

        @Volatile
        private var simpleCacheInstance: SimpleCache? = null

        @Volatile
        var serviceQueue: List<SongItem> = emptyList()
            private set

        @Volatile
        var activeServiceSong: SongItem? = null
            private set

        @Volatile
        var stopAfterCurrentTrackFlag: Boolean = false

        @Volatile
        var autoQueueEnabled: Boolean = true

        @Volatile
        var tasteGenresSnapshot: List<String> = emptyList()

        @Volatile
        var tasteArtistsSnapshot: List<String> = emptyList()

        @Volatile
        var excludedSignaturesSnapshot: Set<String> = emptySet()

        private val _serviceEvents = MutableSharedFlow<ServicePlaybackEvent>(extraBufferCapacity = 16)
        val serviceEvents: SharedFlow<ServicePlaybackEvent> = _serviceEvents.asSharedFlow()

        @Synchronized
        fun syncQueueFromUi(queue: List<SongItem>, activeSong: SongItem? = null) {
            serviceQueue = queue
            if (activeSong != null) {
                activeServiceSong = activeSong
            }
        }

        fun syncTasteAndHistoryForAutoQueue(
            enabled: Boolean,
            tasteGenres: List<String>,
            tasteArtists: List<String>,
            excludedSignatures: Set<String>
        ) {
            autoQueueEnabled = true
            tasteGenresSnapshot = tasteGenres
            tasteArtistsSnapshot = tasteArtists
            excludedSignaturesSnapshot = excludedSignatures
        }

        @Synchronized
        private fun replaceSongInServiceQueue(updatedSong: SongItem) {
            serviceQueue = serviceQueue.map {
                if (it.id == updatedSong.id) updatedSong else it
            }
        }

        fun buildMediaItemForSong(song: SongItem): MediaItem {
            val extras = Bundle().apply {
                putString(EXTRA_SONG_ID, song.id)
                putString(EXTRA_SONG_TITLE, song.title)
                putString(EXTRA_SONG_ARTIST, song.artist)
                putString(EXTRA_SONG_ALBUM, song.albumName)
                putString(EXTRA_SONG_ARTWORK, song.artworkUrl)
                putLong(EXTRA_SONG_DURATION, song.durationMs)
                putString(EXTRA_SONG_STREAM_URL, song.streamUrl)
                putString(EXTRA_SONG_MOOD, song.moodCategory)
                putBoolean(EXTRA_SONG_FAVORITE, song.isFavorite)
            }
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.albumName)
                .setArtworkUri(Uri.parse(song.artworkUrl))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(extras)
                .build()

            return MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(Uri.parse(song.streamUrl))
                .setMediaMetadata(metadata)
                .build()
        }

        fun extractSongFromMediaItem(mediaItem: MediaItem?): SongItem? {
            if (mediaItem == null) return null
            val extras = mediaItem.mediaMetadata.extras
            if (extras != null && extras.containsKey(EXTRA_SONG_ID)) {
                return SongItem(
                    id = extras.getString(EXTRA_SONG_ID).orEmpty(),
                    title = extras.getString(EXTRA_SONG_TITLE).orEmpty(),
                    artist = extras.getString(EXTRA_SONG_ARTIST).orEmpty(),
                    durationMs = extras.getLong(EXTRA_SONG_DURATION, 200_000L),
                    artworkUrl = extras.getString(EXTRA_SONG_ARTWORK).orEmpty(),
                    streamUrl = extras.getString(EXTRA_SONG_STREAM_URL).orEmpty(),
                    albumName = extras.getString(EXTRA_SONG_ALBUM, "Single"),
                    moodCategory = extras.getString(EXTRA_SONG_MOOD, "Trending"),
                    isFavorite = extras.getBoolean(EXTRA_SONG_FAVORITE, false)
                )
            }
            val meta = mediaItem.mediaMetadata
            val id = mediaItem.mediaId.ifBlank { return null }
            return SongItem(
                id = id,
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                durationMs = 200_000L,
                artworkUrl = meta.artworkUri?.toString().orEmpty(),
                streamUrl = mediaItem.localConfiguration?.uri?.toString().orEmpty(),
                albumName = meta.albumTitle?.toString() ?: "Single"
            )
        }

        @Synchronized
        fun getOrCreateCache(context: Context): SimpleCache {
            simpleCacheInstance?.let { return it }
            val cacheFolder = File(context.cacheDir, "somify_audio_lru_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            return SimpleCache(cacheFolder, evictor, databaseProvider).also {
                simpleCacheInstance = it
            }
        }
    }
}
