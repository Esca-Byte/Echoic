package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.local.FavoriteSongEntity
import com.example.data.local.ImportedPlaylistEntity
import com.example.data.local.MusicDao
import com.example.data.local.OfflineSongEntity
import com.example.data.local.RecentlyPlayedSongEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.model.AlbumDetailData
import com.example.data.model.AlbumReleaseItem
import com.example.data.model.ArtistProfileData
import com.example.data.model.AudioQualitySettings
import com.example.data.model.HomeDiscoveryData
import com.example.data.model.LazyPlatformShelfKey
import com.example.data.model.LyricLine
import com.example.data.model.LyricsData
import com.example.data.model.LyricsVariant
import com.example.data.model.MoodCategoryItem
import com.example.data.model.OnboardingArtistOption
import com.example.data.model.OnboardingGenreOption
import com.example.data.model.PagedPlaylistTracksResult
import com.example.data.model.PlaylistItem
import com.example.data.model.RelatedArtistItem
import com.example.data.model.SearchArtistItem
import com.example.data.model.SongItem
import com.example.data.model.StreamingQualityMode
import com.example.data.model.TimeOfDayShelfData
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class MusicRepository(
    private val musicDao: MusicDao? = null
) {

    companion object {
        // Process-wide shared caches so PlayerViewModel and PlaybackService share pre-resolved URLs instantaneously
        private val sharedResolvedStreamCache = ConcurrentHashMap<String, String>()
        private val sharedSongMetadataCache = ConcurrentHashMap<String, SongItem>()
        private val youtubeExpireRegex = Regex("""[?&]expire=(\d{9,12})""")

        @Volatile
        private var appContextRef: Context? = null

        @Volatile
        private var activeAudioQualitySettings: AudioQualitySettings = AudioQualitySettings()

        fun registerAppContext(context: Context) {
            appContextRef = context.applicationContext
        }

        fun updateAudioQualitySettings(settings: AudioQualitySettings, clearResolvedCache: Boolean = true) {
            val previous = activeAudioQualitySettings
            activeAudioQualitySettings = settings
            if (clearResolvedCache && previous != settings) {
                sharedResolvedStreamCache.clear()
            }
        }

        fun isOnUnmeteredWifi(): Boolean {
            val ctx = appContextRef ?: return true
            return runCatching {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return@runCatching true
                val activeNetwork = cm.activeNetwork ?: return@runCatching true
                val caps = cm.getNetworkCapabilities(activeNetwork) ?: return@runCatching true
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }.getOrDefault(true)
        }

        /**
         * Resolves the concrete bitrate tier (HIGH / BALANCED / DATA_SAVER) for the current network.
         */
        fun resolveEffectiveQualityTier(): StreamingQualityMode {
            val settings = activeAudioQualitySettings
            if (settings.dataSaverEnabled) return StreamingQualityMode.DATA_SAVER
            val onWifi = isOnUnmeteredWifi()
            val selected = if (onWifi) settings.wifiQuality else settings.cellularQuality
            return when (selected) {
                StreamingQualityMode.AUTO -> if (onWifi) StreamingQualityMode.HIGH else StreamingQualityMode.BALANCED
                else -> selected
            }
        }

        fun resolveStreamUserAgent(streamUrl: String): String {
            return when {
                streamUrl.contains("c=ANDROID_VR", ignoreCase = true) ->
                    "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                streamUrl.contains("c=TVHTML5_SIMPLY_EMBEDDED_PLAYER", ignoreCase = true) ->
                    "Mozilla/5.0 (PlayStation; PlayStation 5/2.26) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0 Safari/605.1.15"
                streamUrl.contains("c=IOS", ignoreCase = true) ->
                    "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
                streamUrl.contains("c=ANDROID_TESTSUITE", ignoreCase = true) ->
                    "com.google.android.youtube/1.9 (Linux; U; Android 14; Pixel 8 Pro) gzip"
                streamUrl.contains("c=ANDROID", ignoreCase = true) ->
                    "com.google.android.youtube/19.45.38 (Linux; U; Android 14; Pixel 8 Pro) gzip"
                else ->
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            }
        }
    }

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Low-latency HTTP client dedicated to real-time playback stream resolution so dead/slow mirrors fail fast.
     */
    private val fastStreamClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private val resolvedStreamCache: ConcurrentHashMap<String, String>
        get() = sharedResolvedStreamCache
    private val songMetadataCache: ConcurrentHashMap<String, SongItem>
        get() = sharedSongMetadataCache
    private val playlistTracksCache = ConcurrentHashMap<String, List<SongItem>>()
    private val lyricsCache = ConcurrentHashMap<String, LyricsData>()
    @Volatile
    private var cachedGlobalChartSongs: List<SongItem> = emptyList()

    /**
     * Extracts canonical base song title by stripping parenthetical/bracketed tags
     * (e.g. "(From \"Brahmastra\")", "[Official Audio]"), dash suffixes (" - From ...", " - Remastered"),
     * and featuring clauses ("feat.", "ft.").
     */
    internal fun canonicalSongTitle(rawTitle: String): String {
        val decoded = decodeHtmlEntities(rawTitle).lowercase()
        val withoutParens = decoded
            .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
            .replace(Regex("\\b(feat|ft|featuring)\\b.*$"), " ")
            .replace(
                Regex("\\s[-–|]\\s*(from\\b|remaster|original|motion picture|film|official|lyric|audio|video|version| soundtrack).*$"),
                " "
            )
            .split(" - ")
            .firstOrNull()
            .orEmpty()

        val cleaned = withoutParens
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return cleaned.ifEmpty { normalizeToken(rawTitle) }
    }

    internal fun artistWordSet(rawArtist: String): Set<String> {
        val stopWords = setOf("unknown", "artist", "various", "artists", "official", "music", "the", "and")
        return decodeHtmlEntities(rawArtist)
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()
    }

    /**
     * Strictly removes duplicate songs inside a playlist or chart list by checking:
     * 1. Song ID
     * 2. Non-blank stream URL
     * 3. Canonical base song title + overlapping artist credit (or identical title >= 4 chars within the playlist)
     */
    fun deduplicatePlaylistTracks(songs: List<SongItem>): List<SongItem> {
        if (songs.size <= 1) return songs
        val accepted = mutableListOf<SongItem>()
        val seenIds = HashSet<String>()
        val seenStreamUrls = HashSet<String>()

        for (candidate in songs) {
            if (!seenIds.add(candidate.id)) continue
            if (candidate.streamUrl.isNotBlank() && !seenStreamUrls.add(candidate.streamUrl)) continue

            val candidateTitle = canonicalSongTitle(candidate.title)
            val candidateArtists = artistWordSet(candidate.artist)

            val isDuplicate = accepted.any { existing ->
                val existingTitle = canonicalSongTitle(existing.title)
                if (candidateTitle.isEmpty() || existingTitle.isEmpty()) return@any false
                if (candidateTitle != existingTitle) return@any false

                val existingArtists = artistWordSet(existing.artist)
                val sharesArtistWord = candidateArtists.any { it in existingArtists }
                val eitherArtistMissing = candidateArtists.isEmpty() || existingArtists.isEmpty()
                val longIdenticalTitle = candidateTitle.length >= 4

                sharesArtistWord || eitherArtistMissing || longIdenticalTitle
            }

            if (!isDuplicate) {
                accepted.add(candidate)
            }
        }
        return accepted
    }

    /**
     * Returns true if the URL is a 30-second preview clip from Apple iTunes or Deezer.
     */
    fun isPreviewClipUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return lower.contains("dzcdn.net") ||
            lower.contains("cdns-preview") ||
            lower.contains("cdnt-preview") ||
            lower.contains("itunes.apple.com") ||
            lower.contains("mzstatic.com")
    }

    /**
     * Checks if a signed YouTube googlevideo.com stream URL has expired (or expires within 3 minutes).
     */
    fun isExpiredStreamUrl(url: String): Boolean {
        if (!url.contains("googlevideo.com", ignoreCase = true)) return false
        val expireSec = youtubeExpireRegex.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return false
        val nowSec = System.currentTimeMillis() / 1000L
        return expireSec <= nowSec + 180L
    }

    /**
     * Returns true only when [url] is a non-blank, non-expired, full-length audio stream (not a 30s preview clip).
     */
    fun isFullLengthStreamUrl(url: String): Boolean {
        return url.isNotBlank() && !isPreviewClipUrl(url) && !isExpiredStreamUrl(url)
    }

    /**
     * Synchronous O(1) lookup of a valid cached full-length or offline file stream URL for instant playback.
     */
    fun getCachedValidStreamUrl(videoId: String): String? {
        if (videoId.isBlank()) return null
        resolvedStreamCache[videoId]?.let { cached ->
            if (cached.startsWith("file://")) {
                val file = File(cached.removePrefix("file://"))
                if (file.exists() && file.length() > 16_384L) return cached
                resolvedStreamCache.remove(videoId)
            } else if (isFullLengthStreamUrl(cached)) {
                return cached
            } else {
                resolvedStreamCache.remove(videoId)
            }
        }
        songMetadataCache[videoId]?.streamUrl?.takeIf { isFullLengthStreamUrl(it) }?.let { metaUrl ->
            resolvedStreamCache[videoId] = metaUrl
            return metaUrl
        }
        return null
    }

    /**
     * Evicts a failed or expired stream URL from all in-memory caches so recovery fetches a fresh stream.
     */
    fun invalidateStreamUrl(videoId: String, failedUrl: String = "") {
        if (videoId.isBlank()) return
        if (failedUrl.isBlank() || resolvedStreamCache[videoId] == failedUrl) {
            resolvedStreamCache.remove(videoId)
        }
        songMetadataCache[videoId]?.let { existing ->
            if (failedUrl.isBlank() || existing.streamUrl == failedUrl) {
                songMetadataCache[videoId] = existing.copy(streamUrl = "")
            }
        }
    }

    /**
     * Decrypts JioSaavn's official `encrypted_media_url` (DES/ECB/PKCS5Padding with key "38346591")
     * into a direct full-length 320kbps MP4/AAC stream URL on `aac.saavncdn.com`.
     */
    internal fun decryptJioSaavnMediaUrl(encryptedBase64: String, prefer320: Boolean = true): String? {
        return runCatching {
            val cleaned = encryptedBase64.trim()
            if (cleaned.isEmpty()) return null
            val encryptedBytes = try {
                java.util.Base64.getDecoder().decode(cleaned)
            } catch (_: Throwable) {
                android.util.Base64.decode(cleaned, android.util.Base64.DEFAULT)
            }
            val keySpec = javax.crypto.spec.SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES")
            val cipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val rawUrl = String(decryptedBytes, Charsets.UTF_8)
                .trim()
                .replace("http://", "https://")
            if (rawUrl.isBlank()) return null
            val effectiveTier = resolveEffectiveQualityTier()
            val targetSuffix = when (effectiveTier) {
                StreamingQualityMode.DATA_SAVER -> "_96.mp4"
                StreamingQualityMode.BALANCED -> "_160.mp4"
                else -> if (prefer320) "_320.mp4" else "_160.mp4"
            }
            rawUrl
                .replace("_96_p.mp4", targetSuffix)
                .replace("_96.mp4", targetSuffix)
                .replace("_160.mp4", targetSuffix)
                .replace("_320.mp4", targetSuffix)
        }.getOrNull()
    }

    private fun JsonElement?.objOrNull(): JsonObject? =
        if (this is JsonObject) this else null

    private fun JsonElement?.arrOrNull(): JsonArray? =
        if (this is JsonArray) this else null

    private fun JsonElement?.primOrNull(): JsonPrimitive? =
        if (this is JsonPrimitive && this !is JsonNull) this else null

    private fun JsonElement?.strOrNull(): String? =
        this.primOrNull()?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonElement?.longValOrNull(): Long? =
        this.primOrNull()?.longOrNull

    private fun JsonElement?.intValOrNull(): Int? =
        this.primOrNull()?.intOrNull

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            runCatching { cancel() }
        }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    private suspend fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String =
        httpGetWithClient(okHttpClient, url, headers)

    private suspend fun httpGetFast(url: String, headers: Map<String, String> = emptyMap()): String =
        httpGetWithClient(fastStreamClient, url, headers)

    private suspend fun httpGetWithClient(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder()
            .url(url)
            .get()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
            .header("Accept", "application/json, text/plain, */*")
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }

        client.newCall(reqBuilder.build()).awaitResponse().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            response.body?.string().orEmpty()
        }
    }

    private suspend fun httpPostJson(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap()
    ): String = httpPostJsonWithClient(okHttpClient, url, jsonBody, headers)

    private suspend fun httpPostJsonFast(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap()
    ): String = httpPostJsonWithClient(fastStreamClient, url, jsonBody, headers)

    private suspend fun httpPostJsonWithClient(
        client: OkHttpClient,
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val reqBuilder = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(mediaType))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }

        client.newCall(reqBuilder.build()).awaitResponse().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            response.body?.string().orEmpty()
        }
    }

    /**
     * Runs candidate async tasks in parallel and returns the very first non-null result,
     * immediately cancelling all remaining slower network calls.
     */
    private suspend fun <T : Any> raceFirstNonNull(
        timeoutMs: Long,
        tasks: List<suspend () -> T?>
    ): T? {
        if (tasks.isEmpty()) return null
        return withTimeoutOrNull(timeoutMs) {
            supervisorScope {
                val resultChannel = Channel<T>(Channel.CONFLATED)
                val remaining = AtomicInteger(tasks.size)
                val jobs = tasks.map { task ->
                    launch(Dispatchers.IO) {
                        val value = runCatching { task() }.getOrNull()
                        if (value != null) {
                            resultChannel.trySend(value)
                        }
                        if (remaining.decrementAndGet() == 0) {
                            resultChannel.close()
                        }
                    }
                }
                val winner = runCatching { resultChannel.receive() }.getOrNull()
                jobs.forEach { it.cancel() }
                resultChannel.close()
                winner
            }
        }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    val favoriteSongsFlow: Flow<List<SongItem>>? = musicDao?.getAllFavorites()?.map { list ->
        list.map { entity ->
            entity.toSongItem().also { songMetadataCache[it.id] = it }
        }
    }

    val offlineSongsFlow: Flow<List<SongItem>>? = musicDao?.getAllOfflineSongs()?.map { list ->
        list.mapNotNull { entity ->
            val localFile = if (entity.localFilePath.isNotBlank()) File(entity.localFilePath) else null
            if (localFile != null && localFile.exists() && localFile.length() > 16_384L) {
                entity.toSongItem().also {
                    songMetadataCache[it.id] = it
                    resolvedStreamCache[it.id] = UriOrPathHelper.toFileUriString(entity.localFilePath)
                }
            } else {
                null
            }
        }
    }

    private object UriOrPathHelper {
        fun toFileUriString(path: String): String {
            return if (path.startsWith("file://")) path else "file://$path"
        }
    }

    val recentSearchesFlow: Flow<List<String>>? = musicDao?.getRecentSearches()?.map { list ->
        list.map { it.query }
    }

    val recentlyPlayedSongsFlow: Flow<List<SongItem>>? = musicDao?.getRecentlyPlayedSongs()?.map { list ->
        list.map { entity ->
            entity.toSongItem().also { songMetadataCache[it.id] = it }
        }
    }

    suspend fun recordRecentlyPlayed(song: SongItem) = withContext(Dispatchers.IO) {
        if (song.id.isNotBlank() && song.title.isNotBlank()) {
            musicDao?.insertRecentlyPlayedSong(RecentlyPlayedSongEntity.fromSongItem(song))
        }
    }

    suspend fun clearRecentlyPlayedSongs() = withContext(Dispatchers.IO) {
        musicDao?.clearRecentlyPlayedSongs()
    }

    val importedPlaylistsFlow: Flow<List<PlaylistItem>>? = musicDao?.getAllImportedPlaylists()?.map { list ->
        list.map { entity ->
            val rawPreloaded = runCatching {
                jsonParser.decodeFromString(ListSerializer(SongItem.serializer()), entity.tracksJson)
            }.getOrDefault(emptyList())

            // Detect legacy/fallback Spotify imports that didn't store real spotify_trk_ items
            val isStaleSpotifyImport = entity.id.startsWith("imported_spotify_") &&
                rawPreloaded.isNotEmpty() &&
                rawPreloaded.none { it.id.startsWith("spotify_trk_") }

            val deduped = if (isStaleSpotifyImport) {
                playlistTracksCache.remove(entity.id)
                emptyList()
            } else {
                deduplicatePlaylistTracks(rawPreloaded).also { validTracks ->
                    if (validTracks.isNotEmpty()) {
                        playlistTracksCache[entity.id] = validTracks
                        validTracks.forEach { songMetadataCache[it.id] = it }
                    }
                }
            }

            val resolvedPlatform = when {
                entity.id.startsWith("custom_pl_") -> "Custom"
                entity.id.startsWith("imported_spotify_") || entity.id.startsWith("spotify_pl_") -> "Spotify"
                entity.id.startsWith("imported_ytm_") || entity.id.startsWith("ytm_pl_") -> "YouTube Music"
                entity.id.startsWith("imported_saavn_") || entity.id.startsWith("saavn_pl_") -> "JioSaavn"
                entity.id.startsWith("deezer_pl_") -> "Deezer"
                entity.sourcePlatform.equals("Spiral", ignoreCase = true) ||
                    entity.sourcePlatform.equals("Echoic", ignoreCase = true) ||
                    entity.sourcePlatform.isBlank() -> "Spotify"
                else -> entity.sourcePlatform
            }

            PlaylistItem(
                id = entity.id,
                title = entity.title,
                subtitle = if (deduped.isNotEmpty() && entity.id.startsWith("custom_pl_")) {
                    "Custom Playlist • ${deduped.size} songs"
                } else if (deduped.isNotEmpty()) {
                    "${resolvedPlatform} • ${deduped.size} songs"
                } else {
                    entity.subtitle
                },
                artworkUrl = entity.artworkUrl,
                category = entity.category,
                sourcePlatform = resolvedPlatform,
                remotePlaylistId = entity.remotePlaylistId,
                searchFallbackQuery = entity.searchFallbackQuery,
                trackCount = if (deduped.isNotEmpty()) deduped.size else entity.trackCount,
                preloadedTracks = deduped
            )
        }
    }

    suspend fun deleteImportedPlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        playlistTracksCache.remove(playlistId)
        musicDao?.deleteImportedPlaylistById(playlistId)
    }

    /**
     * Creates a user-authored custom playlist in Room so the user can add songs from any platform.
     */
    suspend fun createCustomPlaylist(
        title: String,
        description: String = "",
        initialSong: SongItem? = null
    ): PlaylistItem = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().ifBlank { "My Playlist" }
        val playlistId = "custom_pl_${System.currentTimeMillis()}"
        val tracks = if (initialSong != null) listOf(initialSong) else emptyList()
        val artwork = initialSong?.artworkUrl
            ?.takeIf { it.isNotBlank() }
            ?: "https://c.saavncdn.com/editorial/IndieIndia_20230510113504_500x500.jpg"
        val subtitle = description.trim().ifBlank {
            "Custom Playlist • ${tracks.size} songs"
        }
        val serializedTracks = runCatching {
            jsonParser.encodeToString(ListSerializer(SongItem.serializer()), tracks)
        }.getOrDefault("[]")

        val item = PlaylistItem(
            id = playlistId,
            title = cleanTitle,
            subtitle = subtitle,
            artworkUrl = artwork,
            category = "Custom",
            sourcePlatform = "Custom",
            remotePlaylistId = "",
            searchFallbackQuery = cleanTitle,
            trackCount = tracks.size,
            preloadedTracks = tracks
        )

        if (tracks.isNotEmpty()) {
            playlistTracksCache[playlistId] = tracks
        }

        musicDao?.insertImportedPlaylist(
            ImportedPlaylistEntity(
                id = item.id,
                title = item.title,
                subtitle = item.subtitle,
                artworkUrl = item.artworkUrl,
                category = item.category,
                sourcePlatform = item.sourcePlatform,
                remotePlaylistId = item.remotePlaylistId,
                searchFallbackQuery = item.searchFallbackQuery,
                trackCount = item.trackCount,
                tracksJson = serializedTracks
            )
        )
        item
    }

    suspend fun createCustomPlaylistWithTracks(
        title: String,
        description: String = "",
        tracks: List<SongItem>
    ): PlaylistItem = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().ifBlank { "Queue Mix" }
        val uniqueTracks = deduplicatePlaylistTracks(tracks)
        val playlistId = "custom_queue_pl_${System.currentTimeMillis()}"
        val artwork = uniqueTracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl
            ?: "https://c.saavncdn.com/editorial/IndieIndia_20230510113504_500x500.jpg"
        val subtitle = description.trim().ifBlank {
            "Saved Queue • ${uniqueTracks.size} tracks"
        }
        val serializedTracks = runCatching {
            jsonParser.encodeToString(ListSerializer(SongItem.serializer()), uniqueTracks)
        }.getOrDefault("[]")

        val item = PlaylistItem(
            id = playlistId,
            title = cleanTitle,
            subtitle = subtitle,
            artworkUrl = artwork,
            category = "Custom",
            sourcePlatform = "Custom",
            remotePlaylistId = "",
            searchFallbackQuery = cleanTitle,
            trackCount = uniqueTracks.size,
            preloadedTracks = uniqueTracks
        )
        if (uniqueTracks.isNotEmpty()) {
            playlistTracksCache[playlistId] = uniqueTracks
        }
        musicDao?.insertImportedPlaylist(
            ImportedPlaylistEntity(
                id = item.id,
                title = item.title,
                subtitle = item.subtitle,
                artworkUrl = item.artworkUrl,
                category = item.category,
                sourcePlatform = item.sourcePlatform,
                remotePlaylistId = item.remotePlaylistId,
                searchFallbackQuery = item.searchFallbackQuery,
                trackCount = item.trackCount,
                tracksJson = serializedTracks
            )
        )
        item
    }

    /**
     * Appends a song to an existing saved or custom playlist in Room.
     */
    suspend fun addSongToPlaylist(
        playlistId: String,
        song: SongItem
    ): PlaylistItem? = withContext(Dispatchers.IO) {
        val existingEntity = musicDao?.getImportedPlaylistById(playlistId) ?: return@withContext null
        val currentTracks = runCatching {
            jsonParser.decodeFromString(ListSerializer(SongItem.serializer()), existingEntity.tracksJson)
        }.getOrDefault(emptyList())

        val updatedTracks = deduplicatePlaylistTracks(currentTracks + song)
        playlistTracksCache[playlistId] = updatedTracks
        songMetadataCache[song.id] = song

        val updatedArtwork = if (
            existingEntity.artworkUrl.isBlank() ||
            existingEntity.artworkUrl.contains("IndieIndia_20230510113504")
        ) {
            song.artworkUrl.ifBlank { existingEntity.artworkUrl }
        } else {
            existingEntity.artworkUrl
        }

        val serialized = runCatching {
            jsonParser.encodeToString(ListSerializer(SongItem.serializer()), updatedTracks)
        }.getOrDefault("[]")

        val updatedSubtitle = "${existingEntity.sourcePlatform.ifBlank { "Custom" }} • ${updatedTracks.size} songs"
        val updatedEntity = existingEntity.copy(
            artworkUrl = updatedArtwork,
            subtitle = updatedSubtitle,
            trackCount = updatedTracks.size,
            tracksJson = serialized
        )
        musicDao.insertImportedPlaylist(updatedEntity)

        PlaylistItem(
            id = updatedEntity.id,
            title = updatedEntity.title,
            subtitle = updatedEntity.subtitle,
            artworkUrl = updatedEntity.artworkUrl,
            category = updatedEntity.category,
            sourcePlatform = updatedEntity.sourcePlatform,
            remotePlaylistId = updatedEntity.remotePlaylistId,
            searchFallbackQuery = updatedEntity.searchFallbackQuery,
            trackCount = updatedTracks.size,
            preloadedTracks = updatedTracks
        )
    }

    suspend fun renamePlaylist(
        playlistId: String,
        newTitle: String,
        newSubtitle: String? = null
    ): PlaylistItem? = withContext(Dispatchers.IO) {
        val cleanTitle = newTitle.trim()
        if (cleanTitle.isEmpty()) return@withContext null
        val existingEntity = musicDao?.getImportedPlaylistById(playlistId) ?: return@withContext null
        val tracks = runCatching {
            jsonParser.decodeFromString(ListSerializer(SongItem.serializer()), existingEntity.tracksJson)
        }.getOrDefault(emptyList())
        val updatedSubtitle = newSubtitle?.trim()?.takeIf { it.isNotBlank() }
            ?: existingEntity.subtitle
        val updatedEntity = existingEntity.copy(
            title = cleanTitle,
            subtitle = updatedSubtitle,
            searchFallbackQuery = cleanTitle
        )
        musicDao.insertImportedPlaylist(updatedEntity)
        PlaylistItem(
            id = updatedEntity.id,
            title = updatedEntity.title,
            subtitle = updatedEntity.subtitle,
            artworkUrl = updatedEntity.artworkUrl,
            category = updatedEntity.category,
            sourcePlatform = updatedEntity.sourcePlatform,
            remotePlaylistId = updatedEntity.remotePlaylistId,
            searchFallbackQuery = updatedEntity.searchFallbackQuery,
            trackCount = tracks.size.coerceAtLeast(updatedEntity.trackCount),
            preloadedTracks = tracks
        )
    }

    suspend fun removeSongFromPlaylist(
        playlistId: String,
        songId: String
    ): PlaylistItem? = withContext(Dispatchers.IO) {
        val existingEntity = musicDao?.getImportedPlaylistById(playlistId) ?: return@withContext null
        val currentTracks = runCatching {
            jsonParser.decodeFromString(ListSerializer(SongItem.serializer()), existingEntity.tracksJson)
        }.getOrDefault(emptyList())
        val updatedTracks = currentTracks.filterNot { it.id == songId }
        playlistTracksCache[playlistId] = updatedTracks

        val serialized = runCatching {
            jsonParser.encodeToString(ListSerializer(SongItem.serializer()), updatedTracks)
        }.getOrDefault("[]")
        val platformLabel = existingEntity.sourcePlatform.ifBlank { "Custom" }
        val updatedSubtitle = if (existingEntity.id.startsWith("custom_")) {
            "Custom Playlist • ${updatedTracks.size} songs"
        } else {
            "$platformLabel • ${updatedTracks.size} songs"
        }
        val updatedArtwork = if (updatedTracks.isNotEmpty() &&
            updatedTracks.none { it.artworkUrl == existingEntity.artworkUrl } &&
            existingEntity.id.startsWith("custom_")
        ) {
            updatedTracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl ?: existingEntity.artworkUrl
        } else {
            existingEntity.artworkUrl
        }
        val updatedEntity = existingEntity.copy(
            artworkUrl = updatedArtwork,
            subtitle = updatedSubtitle,
            trackCount = updatedTracks.size,
            tracksJson = serialized
        )
        musicDao.insertImportedPlaylist(updatedEntity)
        PlaylistItem(
            id = updatedEntity.id,
            title = updatedEntity.title,
            subtitle = updatedEntity.subtitle,
            artworkUrl = updatedEntity.artworkUrl,
            category = updatedEntity.category,
            sourcePlatform = updatedEntity.sourcePlatform,
            remotePlaylistId = updatedEntity.remotePlaylistId,
            searchFallbackQuery = updatedEntity.searchFallbackQuery,
            trackCount = updatedTracks.size,
            preloadedTracks = updatedTracks
        )
    }

    suspend fun reorderPlaylistTracks(
        playlistId: String,
        fromIndex: Int,
        toIndex: Int
    ): PlaylistItem? = withContext(Dispatchers.IO) {
        val existingEntity = musicDao?.getImportedPlaylistById(playlistId) ?: return@withContext null
        val mutableTracks = runCatching {
            jsonParser.decodeFromString(ListSerializer(SongItem.serializer()), existingEntity.tracksJson)
        }.getOrDefault(emptyList()).toMutableList()
        if (fromIndex !in mutableTracks.indices || toIndex !in mutableTracks.indices || fromIndex == toIndex) {
            return@withContext null
        }
        val moved = mutableTracks.removeAt(fromIndex)
        mutableTracks.add(toIndex, moved)
        playlistTracksCache[playlistId] = mutableTracks

        val serialized = runCatching {
            jsonParser.encodeToString(ListSerializer(SongItem.serializer()), mutableTracks)
        }.getOrDefault("[]")
        val updatedEntity = existingEntity.copy(
            trackCount = mutableTracks.size,
            tracksJson = serialized
        )
        musicDao.insertImportedPlaylist(updatedEntity)
        PlaylistItem(
            id = updatedEntity.id,
            title = updatedEntity.title,
            subtitle = updatedEntity.subtitle,
            artworkUrl = updatedEntity.artworkUrl,
            category = updatedEntity.category,
            sourcePlatform = updatedEntity.sourcePlatform,
            remotePlaylistId = updatedEntity.remotePlaylistId,
            searchFallbackQuery = updatedEntity.searchFallbackQuery,
            trackCount = mutableTracks.size,
            preloadedTracks = mutableTracks
        )
    }

    /**
     * Re-fetches the latest tracks from the remote source platform (Spotify, JioSaavn, YouTube Music, Deezer)
     * and updates both the in-memory cache and Room database.
     */
    suspend fun syncPlaylistFromRemote(playlist: PlaylistItem): Result<PlaylistItem> = withContext(Dispatchers.IO) {
        runCatching {
            playlistTracksCache.remove(playlist.id)
            val freshPlaylist = when {
                (playlist.id.startsWith("imported_spotify_") || playlist.sourcePlatform.equals("Spotify", ignoreCase = true)) &&
                    playlist.remotePlaylistId.isNotBlank() -> {
                    fetchSpotifyPlaylistById(playlist.remotePlaylistId).copy(id = playlist.id)
                }
                else -> {
                    val freshTracks = fetchPlaylistTracks(playlist.copy(preloadedTracks = emptyList()))
                    playlist.copy(
                        trackCount = freshTracks.size.coerceAtLeast(playlist.trackCount),
                        subtitle = "${playlist.sourcePlatform} • ${freshTracks.size} songs",
                        preloadedTracks = freshTracks
                    )
                }
            }
            if (freshPlaylist.preloadedTracks.isNotEmpty()) {
                playlistTracksCache[freshPlaylist.id] = freshPlaylist.preloadedTracks
            }
            val existingInDb = musicDao?.getImportedPlaylistById(freshPlaylist.id)
            if (existingInDb != null) {
                savePlaylistToFavorites(freshPlaylist, freshPlaylist.preloadedTracks)
            }
            freshPlaylist
        }
    }

    /**
     * Downloads a song's full-length audio stream to device storage (`filesDir/offline_audio`)
     * and records it in `offline_songs` for instant zero-network offline playback.
     */
    suspend fun downloadSongToOfflineStorage(
        filesDir: File,
        song: SongItem
    ): Result<SongItem> = withContext(Dispatchers.IO) {
        runCatching {
            val streamUrl = if (isFullLengthStreamUrl(song.streamUrl) && !song.streamUrl.startsWith("file://")) {
                song.streamUrl
            } else {
                getStreamUrl(song.id, song.title, song.artist).getOrThrow()
            }

            val offlineDir = File(filesDir, "offline_audio").apply {
                if (!exists()) mkdirs()
            }
            val safeFileId = song.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val tempFile = File(offlineDir, "$safeFileId.m4a.tmp")
            val targetFile = File(offlineDir, "$safeFileId.m4a")

            val req = Request.Builder()
                .url(streamUrl)
                .get()
                .header(
                    "User-Agent",
                    resolveStreamUserAgent(streamUrl)
                )
                .build()

            try {
                okHttpClient.newCall(req).awaitResponse().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Failed to download audio: HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IOException("Empty audio stream response")
                    val buffer = ByteArray(16_384)
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { out ->
                            while (true) {
                                kotlinx.coroutines.yield()
                                val read = input.read(buffer)
                                if (read == -1) break
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                }

                require(tempFile.exists() && tempFile.length() > 16_384L) {
                    "Downloaded audio file was incomplete"
                }
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (t: Throwable) {
                runCatching { tempFile.delete() }
                throw t
            }

            val fileUri = UriOrPathHelper.toFileUriString(targetFile.absolutePath)
            resolvedStreamCache[song.id] = fileUri

            val entity = OfflineSongEntity(
                id = song.id,
                title = song.title,
                artist = song.artist,
                durationMs = song.durationMs,
                artworkUrl = song.artworkUrl,
                localFilePath = targetFile.absolutePath,
                originalStreamUrl = streamUrl,
                albumName = song.albumName,
                moodCategory = song.moodCategory
            )
            musicDao?.insertOfflineSong(entity)

            song.copy(
                streamUrl = fileUri,
                isDownloaded = true,
                localFilePath = targetFile.absolutePath
            ).also {
                songMetadataCache[it.id] = it
            }
        }
    }

    suspend fun removeOfflineSong(filesDir: File, songId: String) = withContext(Dispatchers.IO) {
        val existing = musicDao?.getOfflineSongById(songId)
        if (existing != null && existing.localFilePath.isNotBlank()) {
            runCatching { File(existing.localFilePath).delete() }
        } else {
            val safeFileId = songId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            runCatching { File(File(filesDir, "offline_audio"), "$safeFileId.m4a").delete() }
            runCatching { File(File(filesDir, "offline_audio"), "$safeFileId.m4a.tmp").delete() }
        }
        resolvedStreamCache.remove(songId)
        musicDao?.deleteOfflineSongById(songId)
    }

    suspend fun reconcileOfflineDownloads(filesDir: File): Int = withContext(Dispatchers.IO) {
        val offlineDir = File(filesDir, "offline_audio")
        if (offlineDir.exists()) {
            offlineDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp")) {
                    runCatching { file.delete() }
                }
            }
        }
        val rows = musicDao?.getAllOfflineSongs()?.firstOrNull().orEmpty()
        var removedCount = 0
        for (entity in rows) {
            val file = if (entity.localFilePath.isNotBlank()) File(entity.localFilePath) else null
            if (file == null || !file.exists() || file.length() <= 16_384L) {
                runCatching { file?.delete() }
                resolvedStreamCache.remove(entity.id)
                musicDao?.deleteOfflineSongById(entity.id)
                removedCount++
            }
        }
        removedCount
    }

    suspend fun clearAllOfflineDownloads(filesDir: File): Int = withContext(Dispatchers.IO) {
        val rows = musicDao?.getAllOfflineSongs()?.firstOrNull().orEmpty()
        for (entity in rows) {
            if (entity.localFilePath.isNotBlank()) {
                runCatching { File(entity.localFilePath).delete() }
            }
            resolvedStreamCache.remove(entity.id)
            musicDao?.deleteOfflineSongById(entity.id)
        }
        val offlineDir = File(filesDir, "offline_audio")
        if (offlineDir.exists()) {
            offlineDir.listFiles()?.forEach { runCatching { it.delete() } }
        }
        rows.size
    }

    suspend fun getOfflineLocalFileUri(songId: String): String? = withContext(Dispatchers.IO) {
        val entity = musicDao?.getOfflineSongById(songId) ?: return@withContext null
        val file = File(entity.localFilePath)
        if (file.exists() && file.length() > 16_384L) {
            UriOrPathHelper.toFileUriString(file.absolutePath)
        } else {
            resolvedStreamCache.remove(songId)
            musicDao?.deleteOfflineSongById(songId)
            null
        }
    }

    internal data class SeedVibeProfile(
        val saavnPid: String,
        val language: String,
        val isFilmSoundtrack: Boolean,
        val primaryArtist: String,
        val coArtists: List<String>,
        val seedArtistNorms: Set<String>
    )

    private val mainstreamBollywoodFilmArtists = setOf(
        "pritam", "tanishk bagchi", "vishal shekhar", "vishal-shekhar", "sachin jigar",
        "sachin-jigar", "mithoon", "arijit singh", "jubin nautiyal", "neha kakkar",
        "tony kakkar", "shreya ghoshal", "sunidhi chauhan", "sonu nigam", "udit narayan",
        "alka yagnik", "kumar sanu", "himesh reshammiya", "bappi lahiri", "amaal mallik",
        "meet bros", "jeet gannguli", "rochak kohli", "tulsi kumar", "palak muchhal",
        "asees kaur", "sachet tandon", "parampara tandon", "sachet-parampara",
        "vishal mishra", "javed-mohsin", "amitabh bhattacharya", "kumaar", "irshad kamil"
    )

    private val indianIndieScene = listOf(
        "Talwiinder", "Aditya Rikhari", "Anuv Jain", "Mitraz", "Abdul Hannan",
        "Hasan Raheem", "Prateek Kuhad", "Kushagra", "Zaeden", "The Local Train",
        "Bayaan", "Kaifi Khalil", "AUR", "Lifafa", "When Chai Met Toast", "King",
        "Harsh Likhari", "Paradox", "Bharat Chauhan", "Chaar Diwaari", "Arpit Bala",
        "Bharg", "Rawal", "Karun", "Nanku", "Jaiyaxh", "Dhanji", "Frappe Ash",
        "Yungsta", "Twin Strings", "Akanksha Sethi", "Taba Chake", "Osho Jain",
        "Raghav Meattle", "Ritviz", "nucleya", "ikk", "suzoon", "samarjit singh"
    )

    private val desiHipHopScene = listOf(
        "Karan Aujla", "Shubh", "DIVINE", "KR\$NA", "Seedhe Maut",
        "Talha Anjum", "Talhah Yunus", "Young Stunners", "Sidhu Moose Wala",
        "Raftaar", "Ikka", "Hanumankind", "Paradox", "MC Stan", "Karma",
        "Fotty Seven", "Bali", "Raga", "Muhfaad", "Emiway Bantai", "Sikander Kahlon"
    )

    private val punjabiPopScene = listOf(
        "Diljit Dosanjh", "AP Dhillon", "Karan Aujla", "The PropheC", "Shubh",
        "Gurinder Gill", "Amrinder Gill", "Harrdy Sandhu", "Jass Manak",
        "Talwiinder", "Harsh Likhari", "Wazir Patar", "Prem Dhillon",
        "Tegi Pannu", "Sukha", "Chani Nattan", "Inderpal Moga", "Navaan Sandhu"
    )

    private val sufiSoulScene = listOf(
        "Atif Aslam", "Ali Sethi", "Rahat Fateh Ali Khan", "Javed Ali",
        "Nusrat Fateh Ali Khan", "Mohit Chauhan", "Shafqat Amanat Ali", "Kailash Kher",
        "Hamza Malik", "Zain Zohaib", "Amit Trivedi", "Papon", "Abida Parveen",
        "Farhan Saeed", "Asim Azhar", "Mustafa Zahid", "Bayaan", "Kaifi Khalil"
    )

    private val southSuperhitsScene = listOf(
        "Anirudh Ravichander", "Sid Sriram", "A.R. Rahman", "Sushin Shyam",
        "GV Prakash Kumar", "Yuvan Shankar Raja", "Devi Sri Prasad", "Thaman S"
    )

    private val bollywoodModernScene = listOf(
        "Arijit Singh", "Vishal Mishra", "Sachin-Jigar", "Pritam",
        "Darshan Raval", "Jubin Nautiyal", "KK", "Atif Aslam", "Shreya Ghoshal", "Mithoon"
    )

    private val globalPopRnbScene = listOf(
        "The Weeknd", "Post Malone", "Drake", "Travis Scott",
        "Dua Lipa", "Billie Eilish", "Bruno Mars", "Ariana Grande"
    )

    /**
     * Inspects the seed song's real metadata (via JioSaavn `song.getDetails` if available) to
     * determine its exact language and whether it is a Non-Film/Indie track vs. a Bollywood Film Soundtrack.
     */
    private suspend fun resolveSeedVibeProfile(seedSong: SongItem): SeedVibeProfile {
        val artistParts = decodeHtmlEntities(seedSong.artist)
            .split(Regex("[,&/]|\\b(?:feat|ft|featuring|with|x)\\b", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }

        val primaryArtist = artistParts.firstOrNull() ?: extractPrimaryArtistForLyrics(seedSong.artist)
        val coArtists = artistParts.drop(1)
        val seedArtistNorms = artistParts.map { normalizeToken(it) }.filter { it.isNotEmpty() }.toSet()

        val resolvedPid = if (seedSong.id.startsWith("saavn_")) {
            seedSong.id.removePrefix("saavn_").trim()
        } else {
            val lookup = "${seedSong.title} $primaryArtist".trim()
            val matches = runCatching { searchJioSaavnCatalog(lookup) }.getOrDefault(emptyList())
            val best = findBestMatchingTrack(seedSong.title, seedSong.artist, matches) ?: matches.firstOrNull()
            best?.id?.removePrefix("saavn_")?.trim().orEmpty()
        }

        var detectedLanguage = "hindi"
        var hasFilmStarring = false
        var detailsAlbum = seedSong.albumName
        var detailsLabel = ""

        if (resolvedPid.isNotBlank()) {
            runCatching {
                val url = "https://www.jiosaavn.com/api.php?__call=song.getDetails&_format=json&_marker=0&api_version=4&pids=${urlEncode(resolvedPid)}"
                val text = httpGet(url)
                val root = jsonParser.parseToJsonElement(text).objOrNull()
                val songObj = root?.get("songs").arrOrNull()?.firstOrNull()?.objOrNull()
                    ?: root?.get(resolvedPid).objOrNull()
                if (songObj != null) {
                    val lang = songObj["language"].strOrNull()?.trim()?.lowercase()
                    if (!lang.isNullOrBlank()) {
                        detectedLanguage = lang
                    }
                    val moreInfo = songObj["more_info"].objOrNull()
                    detailsAlbum = moreInfo?.get("album")?.strOrNull() ?: detailsAlbum
                    detailsLabel = moreInfo?.get("label")?.strOrNull().orEmpty()
                    val starringStr = moreInfo?.get("starring")?.strOrNull()?.trim().orEmpty()
                    val starringArr = moreInfo?.get("artistMap").objOrNull()
                        ?.get("artists").arrOrNull()
                        ?.any { it.objOrNull()?.get("role").strOrNull().equals("starring", ignoreCase = true) } == true
                    hasFilmStarring = starringStr.isNotEmpty() || starringArr
                }
            }
        }

        val combinedLower = "${seedSong.artist} ${seedSong.title} $detailsAlbum ${seedSong.moodCategory}".lowercase()
        val isKnownIndieOrNonFilmArtist = (indianIndieScene + desiHipHopScene + punjabiPopScene + globalPopRnbScene)
            .any { sceneArtist ->
                val sNorm = normalizeToken(sceneArtist)
                sNorm.isNotEmpty() && seedArtistNorms.any { it.contains(sNorm) || sNorm.contains(it) }
            }

        val hasExplicitFilmTitle = combinedLower.contains("(from \"") ||
            combinedLower.contains("[from \"") ||
            combinedLower.contains("from \"") ||
            combinedLower.contains("original motion picture") ||
            combinedLower.contains("bollywood")

        val isSeedByMainstreamBollywoodArtist = artistParts.any { part ->
            mainstreamBollywoodFilmArtists.contains( part.lowercase().trim() )
        }

        val isFilmSoundtrack = when {
            hasExplicitFilmTitle -> true
            isKnownIndieOrNonFilmArtist -> false
            hasFilmStarring -> true
            isSeedByMainstreamBollywoodArtist -> true
            else -> false
        }

        return SeedVibeProfile(
            saavnPid = resolvedPid,
            language = detectedLanguage,
            isFilmSoundtrack = isFilmSoundtrack,
            primaryArtist = primaryArtist,
            coArtists = coArtists,
            seedArtistNorms = seedArtistNorms
        )
    }

    /**
     * Returns curated scene peer artists ONLY when the seed song explicitly matches a scene.
     * Never falls back to Bollywood when `seedSong.id.startsWith("saavn_")` so unrecognized
     * Indie, Punjabi, or Hip-Hop tracks stay pure to their own recommendation graph.
     */
    internal fun getCuratedScenePeers(
        seedSong: SongItem,
        primaryArtist: String,
        isFilmSoundtrack: Boolean = false
    ): List<String> {
        val combined = "${seedSong.artist} ${seedSong.title} ${seedSong.albumName} ${seedSong.moodCategory}".lowercase()
        val primaryLower = primaryArtist.lowercase().trim()

        val matchedPool = when {
            indianIndieScene.any { combined.contains(it.lowercase()) } ||
                combined.contains("talwinder") ||
                combined.contains("indie") ||
                combined.contains("haseen") ||
                combined.contains("majboor") ||
                combined.contains("khayaal") ||
                combined.contains("wishes") -> indianIndieScene

            desiHipHopScene.any { combined.contains(it.lowercase()) } ||
                combined.contains("krsna") ||
                combined.contains("hip hop") ||
                combined.contains("rap") -> desiHipHopScene

            punjabiPopScene.any { combined.contains(it.lowercase()) } ||
                combined.contains("punjabi") -> punjabiPopScene

            sufiSoulScene.any { combined.contains(it.lowercase()) } ||
                combined.contains("sufi") ||
                combined.contains("alfaaz") ||
                combined.contains("coke studio") -> sufiSoulScene

            southSuperhitsScene.any { combined.contains(it.lowercase()) } ||
                combined.contains("tamil") ||
                combined.contains("telugu") ||
                combined.contains("malayalam") -> southSuperhitsScene

            isFilmSoundtrack ||
                bollywoodModernScene.any { combined.contains(it.lowercase()) } ||
                combined.contains("bollywood") -> bollywoodModernScene

            else -> emptyList()
        }

        return matchedPool
            .filterNot { peer ->
                val pLower = peer.lowercase()
                pLower == primaryLower ||
                    primaryLower.contains(pLower) ||
                    pLower.contains(primaryLower)
            }
            .take(5)
    }

    /**
     * Enforces both audio quality filtering (blocking mashups, ringtones, karaoke, DJ remixes,
     * unwanted slowed/reverb edits) AND the Strict Indie / Non-Film Vibe Gate so mainstream
     * Bollywood movie soundtracks never bleed into Indie or non-film queues.
     */
    internal fun isCleanRadioCandidate(
        candidate: SongItem,
        seedSong: SongItem,
        seedCanonTitle: String,
        vibeProfile: SeedVibeProfile? = null
    ): Boolean {
        val candidateCanon = canonicalSongTitle(candidate.title)
        if (candidateCanon.isEmpty() || candidateCanon == seedCanonTitle) return false
        if (candidate.durationMs in 1..74_000L || candidate.durationMs > 720_000L) return false

        val lowerTitle = candidate.title.lowercase()
        val lowerAlbum = candidate.albumName.lowercase()
        val lowerArtist = candidate.artist.lowercase()
        val seedLower = seedSong.title.lowercase()

        val hardJunkKeywords = listOf(
            "karaoke", "ringtone", "instrumental", "8d audio", "mashup",
            "nonstop", "non-stop", "jukebox", "medley", "dialogue",
            "tribute to", "backing track", "dj remix", "workout mix",
            "party mix", "bollywood mix", "viral mix"
        )
        if (hardJunkKeywords.any { lowerTitle.contains(it) || lowerAlbum.contains(it) }) {
            return false
        }

        val seedIsSlowedOrLofi = seedLower.contains("slowed") ||
            seedLower.contains("reverb") ||
            seedLower.contains("lofi") ||
            seedLower.contains("lo-fi")
        if (!seedIsSlowedOrLofi) {
            val unwantedVariants = listOf("slowed", "reverb", "sped up", "nightcore")
            if (unwantedVariants.any { lowerTitle.contains(it) }) {
                return false
            }
        }

        // Strict Indie & Non-Film Vibe Separation Gate:
        // When the seed song is NOT a film soundtrack (e.g. "Majboor", "Haseen", "Alfaaz"),
        // block movie soundtrack tags and mainstream Bollywood playback/composer bleed.
        if (vibeProfile != null && !vibeProfile.isFilmSoundtrack) {
            val movieMarkers = listOf(
                "from \"", "from '", "(from ", "[from ",
                "original motion picture", "bollywood", "film version"
            )
            if (movieMarkers.any { lowerTitle.contains(it) || lowerAlbum.contains(it) }) {
                return false
            }

            val candidateArtistParts = lowerArtist
                .split(Regex("[,&/]|\\b(?:feat|ft|featuring|with)\\b"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val hasUninvitedBollywoodPlaybackArtist = candidateArtistParts.any { part ->
                val partNorm = normalizeToken(part)
                val isSeedOwnArtist = vibeProfile.seedArtistNorms.any { seedNorm ->
                    seedNorm == partNorm || seedNorm.contains(partNorm) || partNorm.contains(seedNorm)
                }
                !isSeedOwnArtist && mainstreamBollywoodFilmArtists.contains(part)
            }
            if (hasUninvitedBollywoodPlaybackArtist) {
                return false
            }
        }

        return true
    }

    /**
     * Filters a catalog search result list so only tracks where [targetArtist] is genuinely
     * credited in `song.artist` are kept (preventing title/album keyword collisions).
     */
    private fun filterVerifiedArtistSongs(
        songs: List<SongItem>,
        targetArtist: String
    ): List<SongItem> {
        val targetNorm = normalizeToken(targetArtist)
        if (targetNorm.length < 2) return emptyList()
        return songs.filter { song ->
            val artistNorm = normalizeToken(song.artist)
            artistNorm.contains(targetNorm)
        }
    }

    /**
     * Signal 1: Queries Official JioSaavn Song Radio / Collaborative-Filtering Recommendations
     * (`reco.getreco` with the seed track's resolved language, plus `/api/songs/{id}/suggestions`).
     */
    private suspend fun fetchJioSaavnSongRecommendations(
        vibeProfile: SeedVibeProfile
    ): List<SongItem> {
        val resolvedPid = vibeProfile.saavnPid
        if (resolvedPid.isBlank()) return emptyList()

        val collected = mutableListOf<SongItem>()

        // A. Language-aware Official JioSaavn `reco.getreco` endpoint
        val recoUrls = listOf(
            "https://www.jiosaavn.com/api.php?__call=reco.getreco&api_version=4&_format=json&_marker=0&ctx=web6dot0&pid=${urlEncode(resolvedPid)}&language=${urlEncode(vibeProfile.language)}",
            "https://www.jiosaavn.com/api.php?__call=reco.getreco&api_version=4&_format=json&_marker=0&ctx=web6dot0&pid=${urlEncode(resolvedPid)}"
        )
        for (url in recoUrls) {
            val batch = runCatching {
                val responseText = httpGet(url)
                val parsedElement = jsonParser.parseToJsonElement(responseText)
                val arr = parsedElement.arrOrNull()
                    ?: parsedElement.objOrNull()?.get("data").arrOrNull()
                    ?: return@runCatching emptyList()
                arr.mapNotNull { item ->
                    parseJioSaavnSongObject(item.objOrNull(), "Recommended")
                }
            }.getOrDefault(emptyList())
            collected.addAll(batch)
            if (collected.size >= 8) break
        }

        if (collected.size >= 6) {
            return collected.distinctBy { it.id }
        }

        // B. Fallback JioSaavn Suggestions endpoints (`saavn.dev` / `saavn.sumit.co`)
        val wrapperEndpoints = listOf(
            "https://saavn.dev/api/songs/${urlEncode(resolvedPid)}/suggestions?limit=16",
            "https://saavn.sumit.co/api/songs/${urlEncode(resolvedPid)}/suggestions?limit=16"
        )
        for (endpoint in wrapperEndpoints) {
            val suggestions = runCatching {
                val text = httpGet(endpoint)
                val root = jsonParser.parseToJsonElement(text).objOrNull() ?: return@runCatching emptyList()
                val dataArr = root["data"].arrOrNull() ?: return@runCatching emptyList()
                dataArr.mapNotNull { item ->
                    parseJioSaavnSongObject(item.objOrNull(), "Recommended")
                }
            }.getOrDefault(emptyList())
            if (suggestions.isNotEmpty()) {
                collected.addAll(suggestions)
                break
            }
        }

        return collected.distinctBy { it.id }
    }

    /**
     * Signal 2: Queries YouTube Music InnerTube Automix (`RDAMVM<videoId>`) for official Up Next radio tracks.
     */
    private suspend fun fetchInnerTubeAutomixTracks(seedSong: SongItem): List<SongItem> {
        val isDirectYtId = seedSong.id.length == 11 &&
            !seedSong.id.startsWith("saavn_") &&
            !seedSong.id.startsWith("itunes_") &&
            !seedSong.id.startsWith("deezer_") &&
            !seedSong.id.startsWith("spotify_")

        val ytVideoId = if (isDirectYtId) {
            seedSong.id
        } else {
            val lookup = "${seedSong.title} ${extractPrimaryArtistForLyrics(seedSong.artist)}".trim()
            val ytMatches = runCatching { searchInnerTubeWebRemix(lookup) }.getOrDefault(emptyList())
            (findBestMatchingTrack(seedSong.title, seedSong.artist, ytMatches) ?: ytMatches.firstOrNull())?.id.orEmpty()
        }

        if (ytVideoId.isBlank()) return emptyList()

        return runCatching {
            val payload = buildJsonObject {
                put("videoId", ytVideoId)
                put("playlistId", "RDAMVM$ytVideoId")
                put("isAudioOnly", true)
                put("enablePersistentPlaylistPanel", true)
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20241118.01.00")
                        put("hl", "en")
                        put("gl", "IN")
                    }
                }
            }
            val responseText = httpPostJson(
                url = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false",
                jsonBody = payload.toString(),
                headers = mapOf(
                    "Origin" to "https://music.youtube.com",
                    "Referer" to "https://music.youtube.com/",
                    "X-YouTube-Client-Name" to "67",
                    "X-YouTube-Client-Version" to "1.20241118.01.00"
                )
            )
            val rootElement = jsonParser.parseToJsonElement(responseText)
            val panelRenderers = mutableListOf<JsonObject>()
            collectRenderersByKey(rootElement, "playlistPanelVideoRenderer", panelRenderers)

            panelRenderers.mapNotNull { renderer ->
                val vId = renderer["videoId"].strOrNull() ?: return@mapNotNull null
                if (vId == ytVideoId) return@mapNotNull null
                val title = renderer["title"].objOrNull()
                    ?.get("runs").arrOrNull()
                    ?.firstOrNull().objOrNull()
                    ?.get("text").strOrNull()
                    ?: return@mapNotNull null
                val bylineRuns = renderer["longBylineText"].objOrNull()
                    ?.get("runs").arrOrNull()
                    ?: renderer["shortBylineText"].objOrNull()?.get("runs").arrOrNull()
                val artist = bylineRuns
                    ?.mapNotNull { it.objOrNull()?.get("text").strOrNull() }
                    ?.firstOrNull { it != " • " && it.isNotBlank() }
                    ?: "Unknown Artist"
                val lengthText = renderer["lengthText"].objOrNull()
                    ?.get("runs").arrOrNull()
                    ?.firstOrNull().objOrNull()
                    ?.get("text").strOrNull()
                    ?: "3:30"
                val thumbUrl = renderer["thumbnail"].objOrNull()
                    ?.get("thumbnails").arrOrNull()
                    ?.lastOrNull().objOrNull()
                    ?.get("url").strOrNull()
                    ?.replace(Regex("w\\d+-h\\d+"), "w600-h600")
                    ?: "https://i.ytimg.com/vi/$vId/hqdefault.jpg"

                SongItem(
                    id = vId,
                    title = decodeHtmlEntities(title),
                    artist = decodeHtmlEntities(artist),
                    durationMs = parseDurationMs(lengthText),
                    artworkUrl = thumbUrl,
                    streamUrl = "",
                    albumName = "Recommended"
                )
            }.take(14)
        }.getOrDefault(emptyList())
    }

    /**
     * Queries Deezer's artist graph (`/search/artist` -> `/artist/{id}/related`) to dynamically
     * discover real peer artists for [primaryArtist] without relying on hardcoded lists.
     */
    private suspend fun fetchDeezerRelatedArtists(
        primaryArtist: String,
        vibeProfile: SeedVibeProfile
    ): List<String> = runCatching {
        val targetNorm = normalizeToken(primaryArtist)
        if (targetNorm.length < 2) return@runCatching emptyList()

        val searchJson = httpGet("https://api.deezer.com/search/artist?q=${urlEncode(primaryArtist)}&limit=3")
        val searchData = jsonParser.parseToJsonElement(searchJson).objOrNull()?.get("data").arrOrNull()
            ?: return@runCatching emptyList()

        val matchedArtistObj = searchData.mapNotNull { it.objOrNull() }.firstOrNull { obj ->
            val name = obj["name"].strOrNull().orEmpty()
            val norm = normalizeToken(name)
            norm == targetNorm || norm.contains(targetNorm) || targetNorm.contains(norm)
        } ?: return@runCatching emptyList()

        val artistId = matchedArtistObj["id"].longValOrNull()?.toString()
            ?: matchedArtistObj["id"].strOrNull()
            ?: return@runCatching emptyList()

        val relatedJson = httpGet("https://api.deezer.com/artist/$artistId/related?limit=8")
        val relatedData = jsonParser.parseToJsonElement(relatedJson).objOrNull()?.get("data").arrOrNull()
            ?: return@runCatching emptyList()

        relatedData.mapNotNull { item ->
            val name = item.objOrNull()?.get("name").strOrNull()?.trim() ?: return@mapNotNull null
            val norm = normalizeToken(name)
            if (norm.isEmpty() || norm in vibeProfile.seedArtistNorms) return@mapNotNull null
            if (!vibeProfile.isFilmSoundtrack && mainstreamBollywoodFilmArtists.contains(name.lowercase())) {
                return@mapNotNull null
            }
            name
        }
    }.getOrDefault(emptyList())

    /**
     * Dynamically discovers peer artists from:
     * 1. Co-occurring artists inside the seed song's own collaborative radio tracks (`ytAutomix` + `saavnReco`)
     * 2. Deezer's live `/artist/{id}/related` graph
     * 3. Curated scene peers ONLY if the seed song explicitly matches a known scene
     */
    private fun discoverDynamicPeerArtists(
        seedSong: SongItem,
        vibeProfile: SeedVibeProfile,
        collaborativeRadioSongs: List<SongItem>,
        deezerRelatedArtists: List<String>
    ): List<String> {
        val discovered = LinkedHashSet<String>()

        // 1. Artists appearing directly in the seed song's collaborative radio mix
        for (track in collaborativeRadioSongs) {
            val primaryCandidateArtist = decodeHtmlEntities(track.artist)
                .split(Regex("[,&/]|\\b(?:feat|ft|featuring|with)\\b", RegexOption.IGNORE_CASE))
                .firstOrNull()
                ?.trim()
                .orEmpty()
            val norm = normalizeToken(primaryCandidateArtist)
            if (
                primaryCandidateArtist.length >= 3 &&
                !primaryCandidateArtist.equals("Unknown Artist", ignoreCase = true) &&
                !primaryCandidateArtist.equals("Various Artists", ignoreCase = true) &&
                vibeProfile.seedArtistNorms.none { it == norm || it.contains(norm) || norm.contains(it) } &&
                (vibeProfile.isFilmSoundtrack || !mainstreamBollywoodFilmArtists.contains(primaryCandidateArtist.lowercase()))
            ) {
                discovered.add(primaryCandidateArtist)
            }
        }

        // 2. Deezer live related artists for the seed artist
        for (related in deezerRelatedArtists) {
            val norm = normalizeToken(related)
            if (vibeProfile.seedArtistNorms.none { it == norm || it.contains(norm) || norm.contains(it) }) {
                discovered.add(related)
            }
        }

        // 3. Curated micro-scene peers (only when explicitly matched, never defaulting to Bollywood)
        for (scenePeer in getCuratedScenePeers(seedSong, vibeProfile.primaryArtist, vibeProfile.isFilmSoundtrack)) {
            val norm = normalizeToken(scenePeer)
            if (vibeProfile.seedArtistNorms.none { it == norm || it.contains(norm) || norm.contains(it) }) {
                discovered.add(scenePeer)
            }
        }

        return discovered.take(4)
    }

    internal fun songDeduplicationSignature(song: SongItem): String {
        val titleKey = canonicalSongTitle(song.title)
        val firstArtist = song.artist
            .split(Regex("[,&/]|\\b(?:feat|ft|featuring|with)\\b", RegexOption.IGNORE_CASE))
            .firstOrNull()
            .orEmpty()
        val artistKey = normalizeToken(firstArtist)
        return "$titleKey|$artistKey"
    }

    /**
     * Deep Collaborative & Vibe-Aware Autoplay Radio Queue Builder (Spotify & Amazon Music style):
     * 1. Profiles the seed song's vibe (language + Non-Film/Indie vs. Film Soundtrack posture).
     * 2. Fetches Song-to-Song Collaborative Radio Matches (`YouTube Music RDAMVM` + language-aware `JioSaavn reco.getreco`),
     *    plus optional evolving recent-track seeds so multi-wave endless queues keep shifting naturally.
     * 3. Fetches Verified Same-Artist & Collaborator Top Hits (strictly verifying artist credits).
     * 4. Dynamically discovers Peer Artists from the collaborative graph + Deezer related-artist API.
     * 5. Blends Music Taste genres/artists on deeper waves if primary pools are exhausted, and deduplicates
     *    by both track ID and normalized title+artist signature.
     */
    suspend fun fetchSimilarRadioTracks(
        seedSong: SongItem,
        excludeIds: Set<String>,
        excludeSignatures: Set<String> = emptySet(),
        evolvingSeedSongs: List<SongItem> = emptyList(),
        tasteGenres: List<String> = emptyList(),
        tasteArtists: List<String> = emptyList()
    ): List<SongItem> = withContext(Dispatchers.IO) {
        val vibeProfile = resolveSeedVibeProfile(seedSong)
        val primaryArtist = vibeProfile.primaryArtist
        val coArtist = vibeProfile.coArtists.firstOrNull()
        val seedCanon = canonicalSongTitle(seedSong.title)
        val blockedSignatures = excludeSignatures + songDeduplicationSignature(seedSong)
        val secondarySeed = evolvingSeedSongs.firstOrNull {
            it.id != seedSong.id && songDeduplicationSignature(it) != songDeduplicationSignature(seedSong)
        }

        // Stage 1: Fetch Song-to-Song Collaborative Graph, Same-Artist Catalogs, Deezer Related Artists & Evolving Seed in parallel
        val (firstBatch, secondBatch) = coroutineScope {
            val saavnRecoDef = async {
                runCatching { fetchJioSaavnSongRecommendations(vibeProfile) }.getOrDefault(emptyList())
            }
            val ytAutomixDef = async {
                runCatching { fetchInnerTubeAutomixTracks(seedSong) }.getOrDefault(emptyList())
            }
            val primaryDef = async {
                if (primaryArtist.isNotBlank() && !primaryArtist.equals("Unknown Artist", ignoreCase = true)) {
                    runCatching { searchJioSaavnCatalog(primaryArtist) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }
            val coArtistDef = async {
                if (!coArtist.isNullOrBlank()) {
                    runCatching { searchJioSaavnCatalog(coArtist) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }
            val deezerRelatedDef = async {
                runCatching { fetchDeezerRelatedArtists(primaryArtist, vibeProfile) }.getOrDefault(emptyList())
            }
            val evolvingDef = async {
                if (secondarySeed != null) {
                    runCatching { fetchInnerTubeAutomixTracks(secondarySeed) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }
            Pair(
                Triple(saavnRecoDef.await(), ytAutomixDef.await(), primaryDef.await()),
                Triple(coArtistDef.await(), deezerRelatedDef.await(), evolvingDef.await())
            )
        }
        val (saavnRecoList, ytAutomixList, primaryRawList) = firstBatch
        val (coArtistRawList, deezerPeers, evolvingYtList) = secondBatch

        val cleanVibeFilter = { list: List<SongItem> ->
            deduplicatePlaylistTracks(list)
                .distinctBy { songDeduplicationSignature(it) }
                .filter { candidate ->
                    candidate.id !in excludeIds &&
                        songDeduplicationSignature(candidate) !in blockedSignatures &&
                        isCleanRadioCandidate(candidate, seedSong, seedCanon, vibeProfile)
                }
        }

        val cleanSaavnReco = cleanVibeFilter(saavnRecoList)
        val cleanYtAutomix = cleanVibeFilter(ytAutomixList + evolvingYtList)

        // Interleave YouTube Music RDAMVM Automix and JioSaavn Song Radio for the Song-to-Song Radio Pool
        val songRadioMatches = cleanVibeFilter(
            buildList {
                val maxLen = maxOf(cleanSaavnReco.size, cleanYtAutomix.size)
                for (i in 0 until maxLen) {
                    if (i < cleanSaavnReco.size) add(cleanSaavnReco[i])
                    if (i < cleanYtAutomix.size) add(cleanYtAutomix[i])
                }
            }
        )

        // Strictly verify artist credits on same-artist and co-artist catalogs so title collisions never slip in
        val verifiedPrimaryHits = cleanVibeFilter(filterVerifiedArtistSongs(primaryRawList, primaryArtist)).take(6)
        val verifiedCoArtistHits = if (!coArtist.isNullOrBlank()) {
            cleanVibeFilter(filterVerifiedArtistSongs(coArtistRawList, coArtist)).take(4)
        } else {
            emptyList()
        }
        val sameArtistHits = deduplicatePlaylistTracks(verifiedPrimaryHits + verifiedCoArtistHits)

        // Stage 2: Dynamically discover peer artists from the song's radio graph + Deezer related graph
        val dynamicPeers = discoverDynamicPeerArtists(
            seedSong = seedSong,
            vibeProfile = vibeProfile,
            collaborativeRadioSongs = cleanYtAutomix + cleanSaavnReco,
            deezerRelatedArtists = deezerPeers
        )

        val peerLimit = if (songRadioMatches.size + sameArtistHits.size < 10) 5 else 3
        val peerArtistSongs = if (dynamicPeers.isNotEmpty()) {
            coroutineScope {
                dynamicPeers.take(peerLimit).map { peerName ->
                    async {
                        val raw = runCatching { searchJioSaavnCatalog(peerName) }.getOrDefault(emptyList())
                        cleanVibeFilter(filterVerifiedArtistSongs(raw, peerName)).take(4)
                    }
                }.flatMap { it.await() }
            }
        } else {
            emptyList()
        }
        val peerPool = cleanVibeFilter(peerArtistSongs)

        // Stage 3: Priority Ordering (Lead with Song-to-Song Radio + Same-Artist Hits, then Peer-Artist Hits)
        val blended = mutableListOf<SongItem>()
        var rIdx = 0
        var aIdx = 0
        var pIdx = 0

        repeat(2) {
            if (rIdx < songRadioMatches.size) blended.add(songRadioMatches[rIdx++])
            if (aIdx < sameArtistHits.size) blended.add(sameArtistHits[aIdx++])
        }

        while (blended.size < 24 && (rIdx < songRadioMatches.size || aIdx < sameArtistHits.size || pIdx < peerPool.size)) {
            if (rIdx < songRadioMatches.size) {
                blended.add(songRadioMatches[rIdx++])
            }
            if (pIdx < peerPool.size) {
                blended.add(peerPool[pIdx++])
            }
            if (aIdx < sameArtistHits.size) {
                blended.add(sameArtistHits[aIdx++])
            }
            if (rIdx < songRadioMatches.size) {
                blended.add(songRadioMatches[rIdx++])
            }
        }

        // Stage 4: Evolving Music Taste Expansion for deep waves (e.g. wave 3+ after 30+ songs played)
        val tasteExpansionPool = if (deduplicatePlaylistTracks(blended).size < 14) {
            val queries = buildList {
                tasteArtists.shuffled().take(2).forEach { add(it) }
                tasteGenres.shuffled().take(2).forEach { add("$it hits") }
                if (isEmpty() && primaryArtist.isNotBlank()) {
                    add("$primaryArtist mix")
                }
            }
            coroutineScope {
                queries.map { q ->
                    async { runCatching { searchJioSaavnCatalog(q) }.getOrDefault(emptyList()) }
                }.flatMap { it.await() }
            }.filter { candidate ->
                candidate.id !in excludeIds &&
                    songDeduplicationSignature(candidate) !in blockedSignatures &&
                    candidate.durationMs !in 1..74_000L &&
                    candidate.durationMs <= 720_000L
            }
        } else {
            emptyList()
        }

        val primaryFiltered = deduplicatePlaylistTracks(blended)
            .filter {
                it.id !in excludeIds &&
                    songDeduplicationSignature(it) !in blockedSignatures &&
                    isCleanRadioCandidate(it, seedSong, seedCanon, vibeProfile)
            }

        val combinedFinal = (primaryFiltered + tasteExpansionPool)
            .distinctBy { it.id }
            .distinctBy { songDeduplicationSignature(it) }
            .filter {
                it.id !in excludeIds &&
                    songDeduplicationSignature(it) !in blockedSignatures
            }
            .take(16)

        val guaranteedQueue = if (combinedFinal.isNotEmpty()) {
            combinedFinal
        } else {
            val relaxedPool = (saavnRecoList + ytAutomixList + primaryRawList + coArtistRawList + peerArtistSongs + tasteExpansionPool)
                .distinctBy { it.id }
                .filter { candidate ->
                    candidate.id != seedSong.id &&
                        candidate.id !in excludeIds &&
                        candidate.durationMs !in 1..60_000L &&
                        candidate.durationMs <= 720_000L
                }
                .take(16)

            if (relaxedPool.isNotEmpty()) {
                relaxedPool
            } else {
                val chartFallbackQuery = "${vibeProfile.language} trending hits".trim()
                runCatching { searchJioSaavnCatalog(chartFallbackQuery) }
                    .getOrDefault(emptyList())
                    .distinctBy { it.id }
                    .filter { candidate ->
                        candidate.id != seedSong.id &&
                            candidate.id !in excludeIds
                    }
                    .take(12)
            }
        }

        guaranteedQueue.also { finalQueue ->
            finalQueue.forEach { songMetadataCache[it.id] = it }
        }
    }

    /**
     * Fetches a complete, verified Artist Hub profile (`ArtistProfileData`) including:
     * - High-resolution artist portrait & metadata from Deezer / JioSaavn
     * - Verified Top Songs performed by the artist (`filterVerifiedArtistSongs`)
     * - Similar Artists with portraits from Deezer's `/artist/{id}/related` graph + micro-scene peers
     */
    suspend fun fetchArtistProfile(rawArtistName: String): ArtistProfileData = withContext(Dispatchers.IO) {
        val cleanArtist = decodeHtmlEntities(rawArtistName)
            .split(Regex("[,&/]|\\b(?:feat|ft|featuring|with)\\b", RegexOption.IGNORE_CASE))
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
            ?: rawArtistName.trim()

        val targetNorm = normalizeToken(cleanArtist)

        val (saavnSongsRaw, deezerArtistAndRelated) = coroutineScope {
            val songsDef = async {
                runCatching { searchJioSaavnCatalog(cleanArtist) }.getOrDefault(emptyList())
            }
            val deezerDef = async {
                runCatching {
                    val searchJson = httpGet("https://api.deezer.com/search/artist?q=${urlEncode(cleanArtist)}&limit=4")
                    val searchArr = jsonParser.parseToJsonElement(searchJson).objOrNull()?.get("data").arrOrNull()
                    val matchedObj = searchArr?.mapNotNull { it.objOrNull() }?.firstOrNull { obj ->
                        val name = obj["name"].strOrNull().orEmpty()
                        val norm = normalizeToken(name)
                        norm == targetNorm || norm.contains(targetNorm) || targetNorm.contains(norm)
                    } ?: searchArr?.firstOrNull()?.objOrNull()

                    val artistPic = matchedObj?.get("picture_xl")?.strOrNull()
                        ?: matchedObj?.get("picture_big")?.strOrNull()
                        ?: matchedObj?.get("picture_medium")?.strOrNull()
                        ?: ""
                    val artistId = matchedObj?.get("id")?.longValOrNull()?.toString()
                        ?: matchedObj?.get("id")?.strOrNull()
                        ?: ""

                    val relatedItems = if (artistId.isNotBlank()) {
                        val relJson = httpGet("https://api.deezer.com/artist/$artistId/related?limit=10")
                        val relArr = jsonParser.parseToJsonElement(relJson).objOrNull()?.get("data").arrOrNull()
                        relArr?.mapNotNull { item ->
                            val rObj = item.objOrNull() ?: return@mapNotNull null
                            val rName = rObj["name"].strOrNull()?.trim() ?: return@mapNotNull null
                            val rNorm = normalizeToken(rName)
                            if (rNorm.isEmpty() || rNorm == targetNorm) return@mapNotNull null
                            val rPic = rObj["picture_big"].strOrNull()
                                ?: rObj["picture_medium"].strOrNull()
                                ?: ""
                            RelatedArtistItem(
                                name = rName,
                                artworkUrl = rPic.replace("http://", "https://"),
                                subtitle = "Similar Artist"
                            )
                        }.orEmpty()
                    } else {
                        emptyList()
                    }

                    val deezerAlbums = if (artistId.isNotBlank()) {
                        runCatching {
                            val albumsJson = httpGet("https://api.deezer.com/artist/$artistId/albums?limit=12")
                            val albumsArr = jsonParser.parseToJsonElement(albumsJson).objOrNull()?.get("data").arrOrNull()
                            albumsArr?.mapNotNull { aElem ->
                                val aObj = aElem.objOrNull() ?: return@mapNotNull null
                                val aId = aObj["id"].longValOrNull()?.toString() ?: aObj["id"].strOrNull() ?: return@mapNotNull null
                                val aTitle = aObj["title"].strOrNull()?.trim() ?: return@mapNotNull null
                                val aCover = (aObj["cover_xl"].strOrNull()
                                    ?: aObj["cover_big"].strOrNull()
                                    ?: aObj["cover_medium"].strOrNull()
                                    ?: "").replace("http://", "https://")
                                val releaseDate = aObj["release_date"].strOrNull().orEmpty()
                                val year = releaseDate.take(4).takeIf { it.length == 4 } ?: ""
                                val recordType = aObj["record_type"].strOrNull()?.replaceFirstChar { it.uppercase() } ?: "Album"
                                AlbumReleaseItem(
                                    id = "deezer_album_$aId",
                                    title = decodeHtmlEntities(aTitle),
                                    artist = cleanArtist,
                                    year = year,
                                    artworkUrl = aCover,
                                    releaseType = recordType
                                )
                            }.orEmpty()
                        }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }

                    Triple(artistPic.replace("http://", "https://"), relatedItems, deezerAlbums)
                }.getOrDefault(Triple("", emptyList(), emptyList()))
            }

            Pair(songsDef.await(), deezerDef.await())
        }

        val verifiedTopSongs = deduplicatePlaylistTracks(
            filterVerifiedArtistSongs(saavnSongsRaw, cleanArtist).ifEmpty { saavnSongsRaw }
        ).take(20).also { list ->
            list.forEach { songMetadataCache[it.id] = it }
        }

        val seedReference = verifiedTopSongs.firstOrNull() ?: SongItem(
            id = "artist_seed_$targetNorm",
            title = cleanArtist,
            artist = cleanArtist,
            durationMs = 200_000L,
            artworkUrl = ""
        )

        val scenePeerNames = getCuratedScenePeers(seedReference, cleanArtist, isFilmSoundtrack = false)
        val combinedSimilar = LinkedHashMap<String, RelatedArtistItem>()

        for (item in deezerArtistAndRelated.second) {
            val key = normalizeToken(item.name)
            if (key.isNotEmpty() && key != targetNorm) {
                combinedSimilar[key] = item
            }
        }

        // Supplement with co-artists from the artist's top songs and curated scene peers
        val coArtistNames = verifiedTopSongs
            .flatMap { song ->
                decodeHtmlEntities(song.artist)
                    .split(Regex("[,&/]|\\b(?:feat|ft|featuring|with)\\b", RegexOption.IGNORE_CASE))
                    .map { it.trim() }
            }
            .filter { candidate ->
                val norm = normalizeToken(candidate)
                candidate.length >= 3 &&
                    norm.isNotEmpty() &&
                    norm != targetNorm &&
                    !norm.contains(targetNorm) &&
                    !targetNorm.contains(norm)
            }
            .distinct()

        for (peerName in (coArtistNames + scenePeerNames).take(8)) {
            val key = normalizeToken(peerName)
            if (key.isNotEmpty() && key !in combinedSimilar) {
                val cachedArt = songMetadataCache.values.firstOrNull {
                    normalizeToken(it.artist).contains(key)
                }?.artworkUrl.orEmpty()
                combinedSimilar[key] = RelatedArtistItem(
                    name = peerName,
                    artworkUrl = cachedArt,
                    subtitle = if (peerName in coArtistNames) "Collaborator" else "Similar Vibe"
                )
            }
        }

        // Build deduplicated Albums & Singles discography from Deezer + JioSaavn top songs
        val combinedAlbums = LinkedHashMap<String, AlbumReleaseItem>()
        for (album in deezerArtistAndRelated.third) {
            val key = canonicalSongTitle(album.title)
            if (key.isNotEmpty() && key !in combinedAlbums) {
                combinedAlbums[key] = album
            }
        }
        verifiedTopSongs
            .groupBy { it.albumName.trim() }
            .forEach { (albumName, tracksInAlbum) ->
                if (albumName.isNotBlank() && !albumName.equals("Single", ignoreCase = true)) {
                    val key = canonicalSongTitle(albumName)
                    if (key.isNotEmpty() && key !in combinedAlbums) {
                        val firstTrack = tracksInAlbum.first()
                        combinedAlbums[key] = AlbumReleaseItem(
                            id = "saavn_album_${normalizeToken(albumName)}",
                            title = albumName,
                            artist = cleanArtist,
                            artworkUrl = firstTrack.artworkUrl,
                            releaseType = if (tracksInAlbum.size > 1) "Album" else "Single / EP",
                            trackCount = tracksInAlbum.size,
                            preloadedTracks = tracksInAlbum
                        )
                    }
                }
            }

        val heroArtwork = deezerArtistAndRelated.first.takeIf { it.isNotBlank() }
            ?: verifiedTopSongs.firstOrNull()?.artworkUrl.orEmpty()

        val subtitleText = if (verifiedTopSongs.isNotEmpty()) {
            "Verified Artist • ${verifiedTopSongs.size} Top Songs"
        } else {
            "Verified Artist • Artist Radio Available"
        }

        ArtistProfileData(
            name = cleanArtist,
            subtitle = subtitleText,
            artworkUrl = heroArtwork,
            topSongs = verifiedTopSongs,
            albums = combinedAlbums.values.take(12).toList(),
            similarArtists = combinedSimilar.values.take(10).toList()
        )
    }

    /**
     * Loads full playable album details and tracklist for an `AlbumReleaseItem` or album title + artist.
     */
    suspend fun fetchAlbumDetails(albumRelease: AlbumReleaseItem): AlbumDetailData = withContext(Dispatchers.IO) {
        val rawId = albumRelease.id
        val cleanTitle = albumRelease.title.trim()
        val cleanArtist = extractPrimaryArtistForLyrics(albumRelease.artist)

        val loadedTracks = mutableListOf<SongItem>()
        var resolvedYear = albumRelease.year
        var resolvedArt = albumRelease.artworkUrl

        // 1. If it's a Deezer album ID, fetch official tracklist from Deezer and enrich with JioSaavn streams
        if (rawId.startsWith("deezer_album_")) {
            val deezerId = rawId.removePrefix("deezer_album_").trim()
            runCatching {
                val jsonText = httpGet("https://api.deezer.com/album/$deezerId")
                val root = jsonParser.parseToJsonElement(jsonText).objOrNull()
                if (root != null) {
                    if (resolvedYear.isBlank()) {
                        resolvedYear = root["release_date"].strOrNull()?.take(4).orEmpty()
                    }
                    if (resolvedArt.isBlank()) {
                        resolvedArt = (root["cover_xl"].strOrNull() ?: root["cover_big"].strOrNull().orEmpty())
                            .replace("http://", "https://")
                    }
                    val tracksArr = root["tracks"].objOrNull()?.get("data").arrOrNull()
                    tracksArr?.forEach { elem ->
                        val tObj = elem.objOrNull() ?: return@forEach
                        val tId = tObj["id"].longValOrNull()?.toString() ?: tObj["id"].strOrNull() ?: return@forEach
                        val tTitle = tObj["title"].strOrNull() ?: return@forEach
                        val durSec = tObj["duration"].longValOrNull() ?: 195L
                        val tArtist = tObj["artist"].objOrNull()?.get("name").strOrNull() ?: albumRelease.artist
                        val song = SongItem(
                            id = "deezer_$tId",
                            title = decodeHtmlEntities(tTitle),
                            artist = decodeHtmlEntities(tArtist),
                            durationMs = durSec * 1000L,
                            artworkUrl = resolvedArt,
                            streamUrl = "",
                            albumName = cleanTitle
                        )
                        songMetadataCache[song.id] = song
                        loadedTracks.add(song)
                    }
                }
            }
        }

        // 2. Also query JioSaavn for full 320kbps tracks matching this album
        val saavnQuery = if (cleanArtist.isNotBlank() && !cleanTitle.contains(cleanArtist, ignoreCase = true)) {
            "$cleanTitle $cleanArtist"
        } else {
            cleanTitle
        }
        val saavnMatches = runCatching { searchJioSaavnCatalog(saavnQuery) }.getOrDefault(emptyList())
        val targetCanonAlbum = canonicalSongTitle(cleanTitle)
        val exactAlbumMatches = saavnMatches.filter { song ->
            val songCanonAlbum = canonicalSongTitle(song.albumName)
            val songCanonTitle = canonicalSongTitle(song.title)
            songCanonAlbum == targetCanonAlbum ||
                (targetCanonAlbum.length >= 4 && songCanonAlbum.contains(targetCanonAlbum)) ||
                songCanonTitle == targetCanonAlbum
        }

        val finalTracks = when {
            exactAlbumMatches.size >= loadedTracks.size && exactAlbumMatches.isNotEmpty() -> {
                deduplicatePlaylistTracks(albumRelease.preloadedTracks + exactAlbumMatches)
            }
            loadedTracks.isNotEmpty() -> {
                deduplicatePlaylistTracks(loadedTracks)
            }
            albumRelease.preloadedTracks.isNotEmpty() -> {
                deduplicatePlaylistTracks(albumRelease.preloadedTracks + exactAlbumMatches)
            }
            else -> {
                deduplicatePlaylistTracks(
                    filterVerifiedArtistSongs(saavnMatches, cleanArtist).ifEmpty { saavnMatches }.take(12)
                )
            }
        }.also { list ->
            list.forEach { songMetadataCache[it.id] = it }
        }

        if (resolvedArt.isBlank()) {
            resolvedArt = finalTracks.firstOrNull()?.artworkUrl.orEmpty()
        }

        val subtitleParts = buildList {
            add(albumRelease.releaseType.ifBlank { "Album" })
            if (resolvedYear.isNotBlank()) add(resolvedYear)
            if (finalTracks.isNotEmpty()) add("${finalTracks.size} Songs")
        }

        AlbumDetailData(
            id = albumRelease.id,
            title = cleanTitle,
            artist = albumRelease.artist.ifBlank { cleanArtist },
            year = resolvedYear,
            artworkUrl = resolvedArt,
            subtitle = subtitleParts.joinToString(" • "),
            tracks = finalTracks
        )
    }

    /**
     * Fetches live search autocomplete suggestions as the user types in the Search bar.
     * Prioritizes popular & country-famous songs matching the query (formatted as "Title - Artist")
     * followed by YouTube Music India live suggestions and local taste matches.
     */
    suspend fun fetchSearchAutocompleteSuggestions(prefix: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = prefix.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val cleanQuery = stripSearchConnectorWords(trimmed).ifBlank { trimmed }
        val userCountry = resolveUserCountryCode()
        val suggestions = LinkedHashSet<String>()

        coroutineScope {
            val saavnSongsDef = async {
                runCatching { searchJioSaavnCatalog(cleanQuery, page = 1, limit = 6) }.getOrDefault(emptyList())
            }
            val itunesSongsDef = async {
                runCatching { searchITunesCatalog(cleanQuery, countryCode = userCountry, limit = 5) }.getOrDefault(emptyList())
            }
            val ytSuggestDef = async {
                runCatching {
                    val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&gl=$userCountry&hl=en&q=${urlEncode(cleanQuery)}"
                    val text = httpGet(url)
                    val rootArr = jsonParser.parseToJsonElement(text).arrOrNull()
                    val itemsArr = rootArr?.getOrNull(1)?.arrOrNull()
                    itemsArr?.mapNotNull { elem ->
                        val raw = (elem as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                        raw.replace(
                            Regex("\\b(lyrics|official video|video song|full video|mp3|download|status|audio)\\b", RegexOption.IGNORE_CASE),
                            ""
                        )
                            .replace(Regex("\\s+"), " ")
                            .trim()
                            .takeIf { it.length in 2..46 }
                    }.orEmpty()
                }.getOrDefault(emptyList())
            }

            val topCatalogSongs = (saavnSongsDef.await() + itunesSongsDef.await())
                .distinctBy { "${canonicalSongTitle(it.title)}|${extractPrimaryArtistForLyrics(it.artist).lowercase()}" }

            // 1. Put top country-famous & popular songs matching the query right at the top ("Song - Artist")
            topCatalogSongs
                .sortedByDescending { scoreSongRelevance(it, trimmed) }
                .take(4)
                .forEach { song ->
                    val shortTitle = song.title
                        .replace(Regex("\\s*\\(.*?\\)|\\s*\\[.*?]"), "")
                        .trim()
                    val primaryArtist = extractPrimaryArtistForLyrics(song.artist).trim()
                    if (shortTitle.isNotBlank() && primaryArtist.isNotBlank() && !primaryArtist.equals("Unknown Artist", ignoreCase = true)) {
                        val combined = "$shortTitle - $primaryArtist"
                        if (combined.length <= 48) {
                            suggestions.add(combined)
                        }
                    }
                }

            // 2. Add YouTube Music country-aware autocomplete suggestions
            ytSuggestDef.await().forEach { cleaned ->
                if (!cleaned.equals(trimmed, ignoreCase = true)) {
                    suggestions.add(cleaned)
                }
            }
        }

        // 3. Supplement with cached song/artist matches if needed
        if (suggestions.size < 6) {
            val lower = cleanQuery.lowercase()
            songMetadataCache.values.forEach { song ->
                if (song.title.lowercase().startsWith(lower)) {
                    val primary = extractPrimaryArtistForLyrics(song.artist)
                    if (primary.isNotBlank()) {
                        suggestions.add("${song.title} - $primary")
                    } else {
                        suggestions.add(song.title)
                    }
                }
                val primary = extractPrimaryArtistForLyrics(song.artist)
                if (primary.lowercase().startsWith(lower)) {
                    suggestions.add(primary)
                }
            }
        }

        suggestions.take(8).toList()
    }

    /**
     * Saves any searched or browsed playlist into Room (`imported_playlists`) so it appears
     * in Library ("Your Playlists") and on the Home Screen ("Your Saved & Imported Playlists").
     */
    suspend fun savePlaylistToFavorites(
        playlist: PlaylistItem,
        alreadyLoadedTracks: List<SongItem> = emptyList()
    ): PlaylistItem = withContext(Dispatchers.IO) {
        val resolvedTracks = when {
            alreadyLoadedTracks.isNotEmpty() -> deduplicatePlaylistTracks(alreadyLoadedTracks)
            playlist.preloadedTracks.isNotEmpty() -> deduplicatePlaylistTracks(playlist.preloadedTracks)
            else -> runCatching { fetchPlaylistTracks(playlist) }.getOrDefault(emptyList())
        }

        if (resolvedTracks.isNotEmpty()) {
            playlistTracksCache[playlist.id] = resolvedTracks
            resolvedTracks.forEach { songMetadataCache[it.id] = it }
        }

        val serializedTracks = runCatching {
            jsonParser.encodeToString(ListSerializer(SongItem.serializer()), resolvedTracks)
        }.getOrDefault("[]")

        val effectiveCount = if (resolvedTracks.isNotEmpty()) resolvedTracks.size else playlist.trackCount
        val effectiveSubtitle = if (resolvedTracks.isNotEmpty()) {
            "Saved Playlist • ${resolvedTracks.size} songs"
        } else {
            playlist.subtitle
        }
        val effectivePlatform = playlist.sourcePlatform
            .takeIf { it.isNotBlank() && !it.equals("Echoic", ignoreCase = true) && !it.equals("Spiral", ignoreCase = true) }
            ?: when {
                playlist.id.startsWith("imported_spotify_") || playlist.id.startsWith("spotify_pl_") -> "Spotify"
                playlist.id.startsWith("imported_ytm_") || playlist.id.startsWith("ytm_pl_") -> "YouTube Music"
                playlist.id.startsWith("deezer_pl_") -> "Deezer"
                else -> "JioSaavn"
            }

        musicDao?.insertImportedPlaylist(
            ImportedPlaylistEntity(
                id = playlist.id,
                title = playlist.title,
                subtitle = effectiveSubtitle,
                artworkUrl = playlist.artworkUrl,
                category = playlist.category.ifBlank { "Favorite" },
                sourcePlatform = effectivePlatform,
                remotePlaylistId = playlist.remotePlaylistId,
                searchFallbackQuery = playlist.searchFallbackQuery.ifBlank { playlist.title },
                trackCount = effectiveCount,
                tracksJson = serializedTracks
            )
        )

        playlist.copy(
            subtitle = effectiveSubtitle,
            sourcePlatform = effectivePlatform,
            trackCount = effectiveCount,
            preloadedTracks = resolvedTracks
        )
    }

    internal fun extractPlaylistUrlFromInput(rawInput: String): String {
        val trimmed = rawInput.trim()
        val urlMatch = Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE).find(trimmed)
        return urlMatch?.value?.trimEnd('.', ',', ')') ?: trimmed
    }

    internal data class SpotifyParsedTrack(
        val trackId: String,
        val title: String,
        val artist: String,
        val durationMs: Long,
        val artworkUrl: String = ""
    )

    internal data class SpotifyParsedPlaylist(
        val title: String,
        val artworkUrl: String,
        val tracks: List<SpotifyParsedTrack>
    )

    /**
     * Parses Spotify embed HTML (`<script id="__NEXT_DATA__">` JSON tree or fallback regex)
     * to extract all tracks (`uri`, `title`, `subtitle`, `duration`) even when nested objects
     * like `audioPreview` or `contentRatings` are present.
     */
    internal fun extractSpotifyEmbedPlaylist(
        embedHtml: String,
        pageHtml: String = ""
    ): SpotifyParsedPlaylist {
        // Map trackId -> 640x640 HD track cover art from the main Spotify playlist HTML if available
        val trackArtworkMap = mutableMapOf<String, String>()
        if (pageHtml.isNotBlank()) {
            val rowRegex = Regex(
                """src="(https://i\.scdn\.co/image/[a-zA-Z0-9]+)"[^>]*>.*?href="/track/([a-zA-Z0-9]+)"""",
                RegexOption.DOT_MATCHES_ALL
            )
            for (match in rowRegex.findAll(pageHtml)) {
                val rawImg = match.groupValues[1]
                    .replace("00004851", "0000b273")
                    .replace("00001e02", "0000b273")
                val trkId = match.groupValues[2]
                if (trkId.isNotBlank() && rawImg.isNotBlank()) {
                    trackArtworkMap[trkId] = rawImg
                }
            }
        }

        var parsedTitle = ""
        var parsedArtwork = ""
        val parsedTracks = mutableListOf<SpotifyParsedTrack>()

        // Strategy 1: Parse <script id="__NEXT_DATA__" type="application/json"> JSON tree
        val nextDataMatch = Regex(
            """<script\s+id="__NEXT_DATA__"[^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(embedHtml)

        if (nextDataMatch != null) {
            val jsonText = nextDataMatch.groupValues[1].trim()
            runCatching {
                val root = jsonParser.parseToJsonElement(jsonText).objOrNull()
                val entity = root?.get("props")?.objOrNull()
                    ?.get("pageProps")?.objOrNull()
                    ?.get("state")?.objOrNull()
                    ?.get("data")?.objOrNull()
                    ?.get("entity")?.objOrNull()

                if (entity != null) {
                    parsedTitle = decodeHtmlEntities(
                        entity["title"].strOrNull()
                            ?: entity["name"].strOrNull()
                            ?: ""
                    ).trim()

                    val sources = entity["coverArt"].objOrNull()?.get("sources").arrOrNull()
                    parsedArtwork = sources
                        ?.mapNotNull { it.objOrNull()?.get("url")?.strOrNull() }
                        ?.firstOrNull { it.isNotBlank() }
                        .orEmpty()

                    val trackListArr = entity["trackList"].arrOrNull()
                    trackListArr?.forEachIndexed { idx, itemElem ->
                        val itemObj = itemElem.objOrNull() ?: return@forEachIndexed
                        val uri = itemObj["uri"].strOrNull().orEmpty()
                        val trackId = uri.substringAfterLast(":").ifBlank { "trk_$idx" }
                        val rawTitle = decodeHtmlEntities(
                            itemObj["title"].strOrNull()
                                ?: itemObj["name"].strOrNull()
                                ?: ""
                        ).trim()
                        val rawArtist = decodeHtmlEntities(
                            itemObj["subtitle"].strOrNull().orEmpty()
                        )
                            .replace("\u00a0", " ")
                            .replace("\\u00a0", " ")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        val durationMs = itemObj["duration"].longValOrNull()
                            ?.takeIf { it > 0L }
                            ?: 210_000L

                        if (rawTitle.isNotBlank()) {
                            parsedTracks.add(
                                SpotifyParsedTrack(
                                    trackId = trackId,
                                    title = rawTitle,
                                    artist = rawArtist.ifBlank { "Spotify Artist" },
                                    durationMs = durationMs,
                                    artworkUrl = trackArtworkMap[trackId].orEmpty()
                                )
                            )
                        }
                    }
                }
            }
        }

        // Strategy 2: Fallback regex that tolerates nested objects (like audioPreview / contentRatings)
        if (parsedTracks.isEmpty()) {
            val itemRegex = Regex(
                """"uri"\s*:\s*"spotify:track:([a-zA-Z0-9]+)".*?"title"\s*:\s*"((?:\\.|[^"\\])+)".*?"subtitle"\s*:\s*"((?:\\.|[^"\\])+)""""
            )
            for (match in itemRegex.findAll(embedHtml)) {
                val trackId = match.groupValues[1].trim()
                val rawTitle = decodeHtmlEntities(
                    match.groupValues[2].replace("\\\"", "\"").replace("\\u0026", "&")
                ).trim()
                val rawArtist = decodeHtmlEntities(
                    match.groupValues[3]
                        .replace("\\\"", "\"")
                        .replace("\\u0026", "&")
                        .replace("\\u00a0", " ")
                        .replace("\u00a0", " ")
                ).replace(Regex("\\s+"), " ").trim()
                if (rawTitle.isNotBlank()) {
                    parsedTracks.add(
                        SpotifyParsedTrack(
                            trackId = trackId,
                            title = rawTitle,
                            artist = rawArtist.ifBlank { "Spotify Artist" },
                            durationMs = 210_000L,
                            artworkUrl = trackArtworkMap[trackId].orEmpty()
                        )
                    )
                }
            }
        }

        val distinctTracks = parsedTracks.distinctBy {
            "${canonicalSongTitle(it.title)}|${it.artist.lowercase()}"
        }

        return SpotifyParsedPlaylist(
            title = parsedTitle,
            artworkUrl = parsedArtwork,
            tracks = distinctTracks
        )
    }

    internal fun extractSpotifyEmbedTrackPairs(embedHtml: String): List<Pair<String, String>> {
        return extractSpotifyEmbedPlaylist(embedHtml).tracks.map { it.title to it.artist }
    }

    private suspend fun fetchSpotifyPlaylistById(playlistId: String): PlaylistItem = coroutineScope {
        val canonicalSpotifyUrl = "https://open.spotify.com/playlist/$playlistId"
        val oembedDef = async {
            runCatching {
                httpGet("https://open.spotify.com/oembed?url=${urlEncode(canonicalSpotifyUrl)}")
            }.getOrDefault("")
        }
        val embedDef = async {
            runCatching {
                httpGet("https://open.spotify.com/embed/playlist/$playlistId")
            }.getOrDefault("")
        }
        val pageDef = async {
            runCatching {
                httpGet(canonicalSpotifyUrl)
            }.getOrDefault("")
        }

        val oembedJson = oembedDef.await()
        val embedHtml = embedDef.await()
        val pageHtml = pageDef.await()

        val oembedObj = runCatching { jsonParser.parseToJsonElement(oembedJson).objOrNull() }.getOrNull()
        val parsedEmbed = extractSpotifyEmbedPlaylist(embedHtml, pageHtml)

        var title = parsedEmbed.title.ifBlank {
            decodeHtmlEntities(oembedObj?.get("title")?.strOrNull().orEmpty())
                .removeSuffix(" | Spotify")
                .removePrefix("Spotify – ")
                .trim()
        }
        if (title.isBlank()) {
            title = Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                .find(embedHtml)?.groupValues?.getOrNull(1)
                ?.let {
                    decodeHtmlEntities(it)
                        .removeSuffix(" - playlist by Spotify | Spotify")
                        .removeSuffix(" | Spotify")
                        .trim()
                }
                .orEmpty()
                .ifBlank { "Spotify Playlist" }
        }

        var artworkUrl = oembedObj?.get("thumbnail_url")?.strOrNull().orEmpty()
            .ifBlank { parsedEmbed.artworkUrl }

        // Build exact SongItems from the Spotify playlist's trackList so unrelated songs are NEVER substituted
        val exactSpotifyTracks: List<SongItem> = parsedEmbed.tracks.map { spTrack ->
            val effectiveArt = spTrack.artworkUrl.ifBlank {
                artworkUrl.ifBlank { "https://c.saavncdn.com/editorial/Top50India_20231012110000_500x500.jpg" }
            }
            SongItem(
                id = "spotify_trk_${spTrack.trackId}",
                title = spTrack.title,
                artist = spTrack.artist,
                durationMs = spTrack.durationMs,
                artworkUrl = effectiveArt,
                // Resolved on playback via getStreamUrl across JioSaavn (strict artist+title) & YouTube Music InnerTube
                streamUrl = "",
                albumName = title,
                moodCategory = "Imported"
            ).also {
                songMetadataCache[it.id] = it
            }
        }

        val standaloneTracks = deduplicatePlaylistTracks(exactSpotifyTracks)
        require(standaloneTracks.isNotEmpty()) {
            "Could not extract songs from this Spotify playlist link"
        }

        if (artworkUrl.isBlank()) {
            artworkUrl = standaloneTracks.firstOrNull()?.artworkUrl
                ?: "https://c.saavncdn.com/editorial/Top50India_20231012110000_500x500.jpg"
        }

        PlaylistItem(
            id = "imported_spotify_$playlistId",
            title = title,
            subtitle = "Imported Playlist • ${standaloneTracks.size} songs",
            artworkUrl = artworkUrl,
            category = "Imported",
            sourcePlatform = "Spotify",
            remotePlaylistId = playlistId,
            searchFallbackQuery = title,
            trackCount = standaloneTracks.size,
            preloadedTracks = standaloneTracks
        )
    }

    /**
     * Imports a standalone playlist from a pasted Spotify, YouTube Music, or JioSaavn playlist link,
     * resolves its cover art & deduplicated full-length tracklist, and persists it in Room.
     */
    suspend fun importPlaylistFromUrl(rawInput: String): Result<PlaylistItem> = withContext(Dispatchers.IO) {
        runCatching {
            val extractedUrl = extractPlaylistUrlFromInput(rawInput)
            require(extractedUrl.isNotBlank()) { "Please paste a valid playlist link" }

            // Resolve short links (e.g. spotify.link / spoti.fi) if needed
            val resolvedUrl = if (
                extractedUrl.contains("spotify.link", ignoreCase = true) ||
                extractedUrl.contains("spoti.fi", ignoreCase = true)
            ) {
                runCatching {
                    val req = Request.Builder().url(extractedUrl).get().build()
                    okHttpClient.newCall(req).awaitResponse().use { resp ->
                        resp.request.url.toString()
                    }
                }.getOrDefault(extractedUrl)
            } else {
                extractedUrl
            }

            val lowerUrl = resolvedUrl.lowercase()

            val importedPlaylist: PlaylistItem = when {
                // 1. Spotify Playlist Link (https://open.spotify.com/playlist/{id})
                lowerUrl.contains("spotify.com") -> {
                    val playlistId = Regex("playlist/([a-zA-Z0-9]+)").find(resolvedUrl)?.groupValues?.getOrNull(1)
                        ?: throw IllegalArgumentException("Could not find a Spotify playlist ID in this link")

                    fetchSpotifyPlaylistById(playlistId)
                }

                // 2. YouTube Music / YouTube Playlist Link (list=...)
                lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be") -> {
                    val listId = Regex("[?&]list=([a-zA-Z0-9_-]+)").find(resolvedUrl)?.groupValues?.getOrNull(1)
                        ?: throw IllegalArgumentException("Could not find a playlist 'list=' ID in this YouTube link")

                    val oembedJson = runCatching {
                        httpGet("https://www.youtube.com/oembed?url=${urlEncode("https://www.youtube.com/playlist?list=$listId")}&format=json")
                    }.getOrDefault("")
                    val oembedObj = runCatching { jsonParser.parseToJsonElement(oembedJson).objOrNull() }.getOrNull()

                    val title = decodeHtmlEntities(oembedObj?.get("title")?.strOrNull().orEmpty())
                        .ifBlank { "Imported YouTube Playlist" }
                    val thumb = oembedObj?.get("thumbnail_url")?.strOrNull().orEmpty()

                    val tracks = deduplicatePlaylistTracks(
                        runCatching { searchJioSaavnCatalog(title) }.getOrDefault(emptyList())
                    )
                    val finalArtwork = thumb.ifBlank {
                        tracks.firstOrNull()?.artworkUrl
                            ?: "https://c.saavncdn.com/editorial/Top50India_20231012110000_500x500.jpg"
                    }

                    PlaylistItem(
                        id = "imported_ytm_$listId",
                        title = title,
                        subtitle = "Imported Playlist • ${tracks.size.coerceAtLeast(1)} songs",
                        artworkUrl = finalArtwork,
                        category = "Imported",
                        sourcePlatform = "YouTube Music",
                        remotePlaylistId = listId,
                        searchFallbackQuery = title,
                        trackCount = tracks.size.coerceAtLeast(20),
                        preloadedTracks = tracks
                    )
                }

                // 3. JioSaavn Playlist Link (jiosaavn.com/featured/.../{token} or jiosaavn.com/s/playlist/...)
                lowerUrl.contains("jiosaavn.com") || lowerUrl.contains("saavn.com") -> {
                    val cleanPath = resolvedUrl.substringBefore("?").trimEnd('/')
                    val token = cleanPath.substringAfterLast("/")
                    val pathSegments = cleanPath.split("/").filter { it.isNotBlank() }
                    val slugSegment = if (pathSegments.size >= 2) pathSegments[pathSegments.size - 2] else token
                    val slugTitle = slugSegment.replace("-", " ").replace("_", " ").trim()

                    val webApiUrl = "https://www.jiosaavn.com/api.php?__call=webapi.get&token=$token&type=playlist&p=1&n=40&_format=json&_marker=0&api_version=4&ctx=web6dot0"
                    val webApiRoot = runCatching {
                        jsonParser.parseToJsonElement(httpGet(webApiUrl)).objOrNull()
                    }.getOrNull()

                    val directSongs = webApiRoot?.get("list")?.arrOrNull()
                        ?.mapNotNull { parseJioSaavnSongObject(it.objOrNull(), "Imported") }
                        .orEmpty()

                    val directTitle = decodeHtmlEntities(
                        webApiRoot?.get("title")?.strOrNull().orEmpty()
                    ).ifBlank {
                        slugTitle.split(" ").joinToString(" ") { word ->
                            word.replaceFirstChar { it.uppercase() }
                        }.ifBlank { "Imported JioSaavn Playlist" }
                    }

                    val directImage = webApiRoot?.get("image")?.strOrNull()
                        ?.replace("150x150", "500x500")
                        ?.replace("http://", "https://")
                        .orEmpty()

                    val finalTracks = if (directSongs.isNotEmpty()) {
                        deduplicatePlaylistTracks(directSongs)
                    } else {
                        val searchedPl = runCatching {
                            searchJioSaavnPlaylists(directTitle, "Imported", "JioSaavn").firstOrNull()
                        }.getOrNull()
                        val fromPl = if (searchedPl != null && searchedPl.remotePlaylistId.isNotBlank()) {
                            runCatching { fetchJioSaavnPlaylistDetails(searchedPl.remotePlaylistId, "Imported") }
                                .getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }
                        deduplicatePlaylistTracks(
                            fromPl.ifEmpty {
                                runCatching { searchJioSaavnCatalog(directTitle) }.getOrDefault(emptyList())
                            }
                        )
                    }

                    val finalArt = directImage.ifBlank {
                        finalTracks.firstOrNull()?.artworkUrl
                            ?: "https://c.saavncdn.com/editorial/Top50India_20231012110000_500x500.jpg"
                    }

                    PlaylistItem(
                        id = "imported_saavn_$token",
                        title = directTitle,
                        subtitle = "Imported Playlist • ${finalTracks.size.coerceAtLeast(1)} songs",
                        artworkUrl = finalArt,
                        category = "Imported",
                        sourcePlatform = "JioSaavn",
                        remotePlaylistId = token,
                        searchFallbackQuery = directTitle,
                        trackCount = finalTracks.size.coerceAtLeast(20),
                        preloadedTracks = finalTracks
                    )
                }

                // 4. Generic Playlist URL or Playlist Name
                else -> {
                    val query = resolvedUrl
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .substringAfterLast("/")
                        .substringBefore("?")
                        .replace("-", " ")
                        .replace("_", " ")
                        .trim()
                        .ifBlank { rawInput.trim() }

                    val matchedPl = runCatching {
                        searchJioSaavnPlaylists(query, "Imported", "JioSaavn").firstOrNull()
                    }.getOrNull()

                    val tracks = if (matchedPl != null && matchedPl.remotePlaylistId.isNotBlank()) {
                        deduplicatePlaylistTracks(
                            runCatching { fetchJioSaavnPlaylistDetails(matchedPl.remotePlaylistId, "Imported") }
                                .getOrDefault(emptyList())
                        )
                    } else {
                        deduplicatePlaylistTracks(
                            runCatching { searchJioSaavnCatalog(query) }.getOrDefault(emptyList())
                        )
                    }

                    val title = matchedPl?.title ?: query.replaceFirstChar { it.uppercase() }
                    val artwork = matchedPl?.artworkUrl
                        ?: tracks.firstOrNull()?.artworkUrl
                        ?: "https://c.saavncdn.com/editorial/Top50India_20231012110000_500x500.jpg"

                    PlaylistItem(
                        id = "imported_custom_${normalizeToken(title).replace(" ", "_")}",
                        title = title,
                        subtitle = "Imported Playlist • ${tracks.size.coerceAtLeast(1)} songs",
                        artworkUrl = artwork,
                        category = "Imported",
                        sourcePlatform = matchedPl?.sourcePlatform ?: "JioSaavn",
                        remotePlaylistId = matchedPl?.remotePlaylistId.orEmpty(),
                        searchFallbackQuery = title,
                        trackCount = tracks.size.coerceAtLeast(20),
                        preloadedTracks = tracks
                    )
                }
            }

            if (importedPlaylist.preloadedTracks.isNotEmpty()) {
                playlistTracksCache[importedPlaylist.id] = importedPlaylist.preloadedTracks
            }

            val serializedTracks = runCatching {
                jsonParser.encodeToString(ListSerializer(SongItem.serializer()), importedPlaylist.preloadedTracks)
            }.getOrDefault("[]")

            musicDao?.insertImportedPlaylist(
                ImportedPlaylistEntity(
                    id = importedPlaylist.id,
                    title = importedPlaylist.title,
                    subtitle = importedPlaylist.subtitle,
                    artworkUrl = importedPlaylist.artworkUrl,
                    category = importedPlaylist.category,
                    sourcePlatform = importedPlaylist.sourcePlatform,
                    remotePlaylistId = importedPlaylist.remotePlaylistId,
                    searchFallbackQuery = importedPlaylist.searchFallbackQuery,
                    trackCount = importedPlaylist.trackCount,
                    tracksJson = serializedTracks
                )
            )

            importedPlaylist
        }
    }

    suspend fun toggleFavorite(song: SongItem): Boolean = withContext(Dispatchers.IO) {
        songMetadataCache[song.id] = song
        val dao = musicDao ?: return@withContext !song.isFavorite
        val exists = dao.isFavorite(song.id)
        if (exists) {
            dao.deleteFavoriteById(song.id)
            false
        } else {
            dao.insertFavorite(FavoriteSongEntity.fromSongItem(song))
            true
        }
    }

    suspend fun recordSearchQuery(query: String) = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            musicDao?.insertSearchQuery(SearchHistoryEntity(query = trimmed))
        }
    }

    suspend fun removeSearchQuery(query: String) = withContext(Dispatchers.IO) {
        musicDao?.deleteSearchQuery(query)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        musicDao?.clearSearchHistory()
    }

    /**
     * Real playlist & chart filter categories for the Home screen and Search screen
     * (Includes both Indian & Global chart/genre categories)
     */
    fun getMoodCategories(): List<MoodCategoryItem> = listOf(
        MoodCategoryItem(
            id = "india_hits",
            title = "India Hits",
            subtitle = "Top 50 India",
            queryKeyword = "Top 50 India Trending Hindi Songs"
        ),
        MoodCategoryItem(
            id = "bollywood",
            title = "Bollywood",
            subtitle = "Hot Hindi Hits",
            queryKeyword = "Bollywood Hot Hits Arijit Singh Pritam"
        ),
        MoodCategoryItem(
            id = "punjabi_hiphop",
            title = "Punjabi & Hip-Hop",
            subtitle = "Punjabi Pop & Desi Rap",
            queryKeyword = "Diljit Dosanjh Karan Aujla Divine KRSNA Punjabi"
        ),
        MoodCategoryItem(
            id = "south_sufi",
            title = "South & Sufi",
            subtitle = "Tamil, Telugu, Malayalam & Sufi",
            queryKeyword = "Anirudh Ravichander AR Rahman Tamil Telugu Sufi"
        ),
        MoodCategoryItem(
            id = "top_hits",
            title = "Top Hits",
            subtitle = "Global Charts",
            queryKeyword = "Today's Top Hits Global"
        ),
        MoodCategoryItem(
            id = "viral",
            title = "Viral",
            subtitle = "Viral 50",
            queryKeyword = "Viral Hits Trending"
        ),
        MoodCategoryItem(
            id = "new_releases",
            title = "New Releases",
            subtitle = "Fresh Drops",
            queryKeyword = "New Music Friday Hits"
        ),
        MoodCategoryItem(
            id = "moods",
            title = "Moods",
            subtitle = "Chill & Focus",
            queryKeyword = "Chill Vibes Late Night"
        ),
        MoodCategoryItem(
            id = "genres",
            title = "Genres",
            subtitle = "Pop, Hip-Hop & EDM",
            queryKeyword = "Pop Hip Hop Electronic"
        )
    )

    fun getOnboardingGenres(): List<OnboardingGenreOption> = listOf(
        // Indian & Regional (12)
        OnboardingGenreOption("bollywood_hits", "Bollywood Hits", "Hindi Film & Romance", "Bollywood Hot Hits Hindi"),
        OnboardingGenreOption("retro_hindi", "90s & 2000s Retro", "Classic Hindi Melodies", "90s 2000s Bollywood Retro Hindi Hits"),
        OnboardingGenreOption("punjabi_pop", "Punjabi Pop", "Bhangra & Urban Desi", "Diljit Dosanjh Karan Aujla Punjabi Hits"),
        OnboardingGenreOption("desi_hiphop", "Desi Hip-Hop", "Indian Rap & Bars", "Divine KRSNA Seedhe Maut Desi Hip Hop"),
        OnboardingGenreOption("indian_indie", "Indian Indie", "Acoustic & Unplugged", "Anuv Jain Prateek Kuhad Indian Indie"),
        OnboardingGenreOption("indian_pop_viral", "Indian Pop & Viral", "Trending I-Pop & Reels", "Indian Pop Trending Hindi Hits"),
        OnboardingGenreOption("tamil_telugu", "Tamil & Telugu", "Kollywood & Tollywood", "Anirudh Ravichander Tamil Telugu Hits"),
        OnboardingGenreOption("malayalam_kannada", "Malayalam & Kannada", "Mollywood & Sandalwood", "Malayalam Kannada Super Hits"),
        OnboardingGenreOption("sufi_soul", "Sufi & Soul", "Ghazal, Folk & Sufi", "Rahat Fateh Ali Khan Sufi Hits"),
        OnboardingGenreOption("ghazal_classical", "Ghazals & Classical", "Timeless Raags & Ghazals", "Jagjit Singh Ghazal Classical Fusion"),
        OnboardingGenreOption("bhojpuri_haryanvi", "Haryanvi & Regional", "Desi Beats & Folk Pop", "Haryanvi Bhojpuri Trending Hits"),
        OnboardingGenreOption("devotional_spiritual", "Devotional & Peace", "Bhajans, Mantras & Soul", "Hindi Devotional Bhajan Aarti"),
        // Global & Moods (12)
        OnboardingGenreOption("global_pop", "Global Pop", "Charting Pop Anthems", "Today Top Hits Pop Global"),
        OnboardingGenreOption("hiphop_rap", "Hip-Hop & Rap", "Global Trap & Rap", "RapCaviar Drake Kendrick Lamar"),
        OnboardingGenreOption("rnb_soul", "R&B & Soul", "Late Night & Smooth", "The Weeknd SZA R&B Hits"),
        OnboardingGenreOption("kpop_hits", "K-Pop", "Korean Pop & Anthems", "BTS BLACKPINK NewJeans K-Pop Hits"),
        OnboardingGenreOption("afrobeats_vibe", "Afrobeats & Amapiano", "Rhythmic Global Grooves", "Rema Burna Boy Tyla Afrobeats"),
        OnboardingGenreOption("latin_reggaeton", "Latin & Reggaeton", "Urbano & Tropical Hits", "Bad Bunny Karol G Reggaeton Hits"),
        OnboardingGenreOption("phonk_gym", "Phonk & Gym", "High-Energy Workout & Drift", "Phonk Gym Workout Hardstyle"),
        OnboardingGenreOption("electronic_edm", "Electronic & EDM", "House, Synth & Dance", "Electronic Dance Hits Calvin Harris"),
        OnboardingGenreOption("lofi_chill", "Lo-Fi & Chill", "Study, Focus & Vibes", "Lofi Beats Chill Study"),
        OnboardingGenreOption("rock_alt", "Rock & Alt", "Indie Rock & Anthems", "Coldplay Imagine Dragons Rock Hits"),
        OnboardingGenreOption("jazz_acoustic", "Jazz & Acoustic", "Warm Unplugged & Instrumental", "Acoustic Chill Jazz Cafe"),
        OnboardingGenreOption("anime_gaming", "Anime & Gaming OSTs", "Epic Soundtracks & J-Rock", "Anime Openings Gaming Soundtrack")
    )

    fun getOnboardingArtists(): List<OnboardingArtistOption> = listOf(
        // Indian & South Asian (20)
        OnboardingArtistOption("Arijit Singh", "India • Bollywood"),
        OnboardingArtistOption("Diljit Dosanjh", "India • Punjabi"),
        OnboardingArtistOption("Karan Aujla", "India • Punjabi Pop"),
        OnboardingArtistOption("Anirudh Ravichander", "India • Tamil / South"),
        OnboardingArtistOption("A.R. Rahman", "India • Classical & Pop"),
        OnboardingArtistOption("Shreya Ghoshal", "India • Playback"),
        OnboardingArtistOption("Atif Aslam", "South Asia • Pop & Soul"),
        OnboardingArtistOption("Pritam", "India • Bollywood"),
        OnboardingArtistOption("Anuv Jain", "India • Indie"),
        OnboardingArtistOption("AP Dhillon", "India • Punjabi"),
        OnboardingArtistOption("Shubh", "India • Urban Punjabi"),
        OnboardingArtistOption("Sidhu Moose Wala", "India • Punjabi Hip-Hop"),
        OnboardingArtistOption("Divine", "India • Desi Rap"),
        OnboardingArtistOption("Seedhe Maut", "India • Hip-Hop"),
        OnboardingArtistOption("KR\$NA", "India • Desi Rap"),
        OnboardingArtistOption("KK", "India • Evergreen Hindi"),
        OnboardingArtistOption("Sonu Nigam", "India • Bollywood"),
        OnboardingArtistOption("Badshah", "India • Party Pop"),
        OnboardingArtistOption("Vishal Mishra", "India • Romance"),
        OnboardingArtistOption("Sid Sriram", "India • South Melody"),
        // Global (18)
        OnboardingArtistOption("The Weeknd", "Global • R&B / Pop"),
        OnboardingArtistOption("Taylor Swift", "Global • Pop"),
        OnboardingArtistOption("Drake", "Global • Hip-Hop"),
        OnboardingArtistOption("Travis Scott", "Global • Trap / Rap"),
        OnboardingArtistOption("Eminem", "Global • Hip-Hop"),
        OnboardingArtistOption("Kendrick Lamar", "Global • Rap"),
        OnboardingArtistOption("SZA", "Global • R&B"),
        OnboardingArtistOption("Billie Eilish", "Global • Alt Pop"),
        OnboardingArtistOption("Post Malone", "Global • Pop / Rock"),
        OnboardingArtistOption("Dua Lipa", "Global • Dance Pop"),
        OnboardingArtistOption("Bruno Mars", "Global • Pop & Funk"),
        OnboardingArtistOption("Coldplay", "Global • Alt Rock"),
        OnboardingArtistOption("Ed Sheeran", "Global • Acoustic Pop"),
        OnboardingArtistOption("Ariana Grande", "Global • Pop & R&B"),
        OnboardingArtistOption("Justin Bieber", "Global • Pop"),
        OnboardingArtistOption("Bad Bunny", "Global • Latin Urbano"),
        OnboardingArtistOption("BTS", "Global • K-Pop"),
        OnboardingArtistOption("Lana Del Rey", "Global • Indie Pop")
    )

    suspend fun hydrateArtistPortraitUrls(
        artists: List<OnboardingArtistOption>
    ): List<OnboardingArtistOption> = withContext(Dispatchers.IO) {
        val result = ArrayList<OnboardingArtistOption>(artists.size)
        for (chunk in artists.chunked(8)) {
            val hydratedChunk = coroutineScope {
                chunk.map { artist ->
                    async {
                        if (artist.artworkUrl.isNotBlank()) return@async artist
                        val picUrl = runCatching {
                            val json = httpGet("https://api.deezer.com/search/artist?q=${urlEncode(artist.name)}&limit=2")
                            val data = jsonParser.parseToJsonElement(json).objOrNull()?.get("data").arrOrNull()
                            val first = data?.firstOrNull()?.objOrNull()
                            (first?.get("picture_big")?.strOrNull()
                                ?: first?.get("picture_medium")?.strOrNull()
                                ?: "").replace("http://", "https://")
                        }.getOrDefault("")
                        if (picUrl.isNotBlank()) artist.copy(artworkUrl = picUrl) else artist
                    }
                }.map { it.await() }
            }
            result.addAll(hydratedChunk)
        }
        result
    }

    suspend fun searchOnboardingArtists(query: String): List<OnboardingArtistOption> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext emptyList()
        runCatching {
            val json = httpGet("https://api.deezer.com/search/artist?q=${urlEncode(clean)}&limit=8")
            val data = jsonParser.parseToJsonElement(json).objOrNull()?.get("data").arrOrNull().orEmpty()
            data.mapNotNull { elem ->
                val obj = elem.objOrNull() ?: return@mapNotNull null
                val name = obj["name"].strOrNull()?.trim() ?: return@mapNotNull null
                val pic = (obj["picture_big"].strOrNull()
                    ?: obj["picture_medium"].strOrNull()
                    ?: "").replace("http://", "https://")
                OnboardingArtistOption(
                    name = decodeHtmlEntities(name),
                    regionTag = "Artist",
                    artworkUrl = pic
                )
            }.distinctBy { normalizeToken(it.name) }
        }.getOrDefault(emptyList())
    }

    suspend fun searchArtists(query: String): List<SearchArtistItem> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext emptyList()
        val normQuery = normalizeToken(clean)
        val queryTokens = normQuery.split(" ").filter { it.isNotEmpty() }

        runCatching {
            val json = httpGet("https://api.deezer.com/search/artist?q=${urlEncode(clean)}&limit=8")
            val data = jsonParser.parseToJsonElement(json).objOrNull()?.get("data").arrOrNull().orEmpty()
            data.mapNotNull { elem ->
                val obj = elem.objOrNull() ?: return@mapNotNull null
                val rawName = obj["name"].strOrNull()?.trim() ?: return@mapNotNull null
                val name = decodeHtmlEntities(rawName)
                val normName = normalizeToken(name)
                if (normName.isEmpty()) return@mapNotNull null

                val id = obj["id"].longValOrNull()?.toString()
                    ?: obj["id"].strOrNull()
                    ?: normName
                val pic = (obj["picture_xl"].strOrNull()
                    ?: obj["picture_big"].strOrNull()
                    ?: obj["picture_medium"].strOrNull()
                    ?: "").replace("http://", "https://")
                val fans = obj["nb_fan"].longValOrNull() ?: 0L

                var score = 0
                if (normName == normQuery) {
                    score += 180
                } else if (normName.startsWith(normQuery)) {
                    score += 135
                } else if (normName.contains(normQuery)) {
                    score += 95
                }
                val matchedTokens = queryTokens.count { token -> normName.contains(token) }
                if (queryTokens.isNotEmpty() && matchedTokens == queryTokens.size) {
                    score += 65
                } else if (matchedTokens > 0) {
                    score += matchedTokens * 20
                }
                if (fans >= 1_000_000L) score += 22
                else if (fans >= 100_000L) score += 14
                else if (fans >= 10_000L) score += 8

                val subtitle = when {
                    fans >= 1_000_000L -> {
                        val millions = fans / 1_000_000.0
                        "Artist • ${String.format("%.1fM", millions)} listeners"
                    }
                    fans >= 1_000L -> "Artist • ${fans / 1_000L}K listeners"
                    else -> "Artist"
                }

                SearchArtistItem(
                    id = id,
                    name = name,
                    artworkUrl = pic,
                    subtitle = subtitle,
                    fanCount = fans,
                    relevanceScore = score
                )
            }
                .distinctBy { normalizeToken(it.name) }
                .sortedWith(compareByDescending<SearchArtistItem> { it.relevanceScore }.thenByDescending { it.fanCount })
        }.getOrDefault(emptyList())
    }

    suspend fun buildPersonalizedForYouSeed(
        genres: List<String>,
        artists: List<String>
    ): Pair<List<SongItem>, List<PlaylistItem>> = withContext(Dispatchers.IO) {
        val genreCatalog = getOnboardingGenres()
        val artistQueries = artists.take(3)
        val genreQueries = genres.take(2).map { genreTitle ->
            genreCatalog.firstOrNull { it.title.equals(genreTitle, ignoreCase = true) }?.queryKeyword ?: genreTitle
        }

        coroutineScope {
            val songJobs = (artistQueries + genreQueries).distinct().take(4).map { q ->
                async {
                    runCatching { searchJioSaavnCatalog(q, page = 1, limit = 8) }.getOrDefault(emptyList())
                }
            }
            val playlistJobs = (genreQueries.take(2) + artistQueries.take(1)).distinct().take(3).map { q ->
                async {
                    runCatching {
                        searchJioSaavnPlaylists(
                            query = q,
                            category = "For You",
                            sourcePlatform = "JioSaavn"
                        )
                    }.getOrDefault(emptyList())
                }
            }

            val allSongs = songJobs.flatMap { it.await() }
            val interleavedSongs = deduplicatePlaylistTracks(allSongs).take(20)
            val playlists = playlistJobs.flatMap { it.await() }
                .distinctBy { it.id }
                .take(8)
            interleavedSongs to playlists
        }
    }

    suspend fun fetchTimeOfDayShelf(
        hourOfDay: Int,
        tasteGenres: List<String>
    ): TimeOfDayShelfData = withContext(Dispatchers.IO) {
        val (title, subtitle, windowKeyword) = when (hourOfDay) {
            in 5..11 -> Triple(
                "Morning Focus",
                "Fresh acoustic, indie & uplifting starts for your morning",
                "Morning Acoustic Indie Chill"
            )
            in 12..16 -> Triple(
                "Afternoon Boost",
                "Upbeat anthems & midday energy to power your afternoon",
                "Upbeat Hits Pop Bollywood Workout"
            )
            in 17..21 -> Triple(
                "Evening Drive",
                "Cruising melodies & golden hour favorites",
                "Evening Drive Hindi English Vibes"
            )
            else -> Triple(
                "Late Night Chill",
                "Midnight lo-fi, soulful Sufi & soft late-night tracks",
                "Late Night Lofi Sufi Slow Chill"
            )
        }
        val genreSuffix = tasteGenres.firstOrNull()?.let { " $it" }.orEmpty()
        val songs = runCatching {
            searchJioSaavnCatalog("$windowKeyword$genreSuffix".trim(), page = 1, limit = 15)
        }.getOrDefault(emptyList())
        val playlists = runCatching {
            searchJioSaavnPlaylists(
                query = "$windowKeyword$genreSuffix".trim(),
                category = title,
                sourcePlatform = "JioSaavn"
            ).take(4)
        }.getOrDefault(emptyList())

        TimeOfDayShelfData(
            title = title,
            subtitle = subtitle,
            songs = deduplicatePlaylistTracks(songs).take(14),
            playlists = playlists
        )
    }

    fun getCuratedCatalog(): List<SongItem> = cachedGlobalChartSongs

    /**
     * Fetches 100% real live Global AND Indian playlists and charts from JioSaavn, YouTube Music, Deezer, and Apple Music.
     * No static demo songs are used.
     */
    private val playlistPageCache = ConcurrentHashMap<String, PagedPlaylistTracksResult>()
    private val playlistFullSnapshotCache = ConcurrentHashMap<String, List<SongItem>>()

    /**
     * Fast Core Home Discovery fetch optimized for slow/metered connections:
     * Fetches only lightweight core chart tracks & primary India/Global top hits first
     * so Home renders immediately without blocking on all 17 platform endpoints.
     */
    suspend fun fetchCoreHomeDiscoveryData(): HomeDiscoveryData = withContext(Dispatchers.IO) {
        coroutineScope {
            val deezerChartTracksDef = async {
                runCatching { fetchDeezerGlobalChartTracks() }.getOrDefault(emptyList())
            }
            val appleRssTracksDef = async {
                runCatching { fetchAppleMusicTopSongsRss() }.getOrDefault(emptyList())
            }
            val saavnTopHitsTracksDef = async {
                runCatching { searchJioSaavnCatalog("Top English Hits", page = 1, limit = 15) }.getOrDefault(emptyList())
            }
            val saavnIndiaHitsTracksDef = async {
                runCatching { searchJioSaavnCatalog("Top Hindi Hits Bollywood Arijit Singh", page = 1, limit = 15) }.getOrDefault(emptyList())
            }
            val saavnIndiaTopPlaylistsDef = async {
                runCatching {
                    searchJioSaavnPlaylists(
                        query = "Bollywood Top 50 Trending Hindi Hits",
                        category = "Bollywood",
                        sourcePlatform = "JioSaavn India"
                    )
                }.getOrDefault(emptyList())
            }

            val saavnTracks = deduplicatePlaylistTracks(saavnTopHitsTracksDef.await())
            val saavnIndiaTracks = deduplicatePlaylistTracks(saavnIndiaHitsTracksDef.await())
            val deezerTracks = deduplicatePlaylistTracks(deezerChartTracksDef.await())
            val appleTracks = deduplicatePlaylistTracks(appleRssTracksDef.await())
            val saavnIndiaTopPlaylists = saavnIndiaTopPlaylistsDef.await()

            val upgradedGlobalCharts = deduplicatePlaylistTracks(
                (saavnTracks + deezerTracks + appleTracks).map { song ->
                    if (song.id.startsWith("saavn_")) {
                        song
                    } else {
                        val fullMatch = findBestMatchingTrack(song.title, song.artist, saavnTracks)
                        if (fullMatch != null && isFullLengthStreamUrl(fullMatch.streamUrl)) {
                            song.copy(
                                streamUrl = fullMatch.streamUrl,
                                durationMs = fullMatch.durationMs.takeIf { it > 60_000L } ?: song.durationMs
                            ).also {
                                resolvedStreamCache[it.id] = fullMatch.streamUrl
                                songMetadataCache[it.id] = it
                            }
                        } else {
                            song.copy(streamUrl = "").also {
                                songMetadataCache[it.id] = it
                            }
                        }
                    }
                }
            ).take(20)

            val blendedChartFeed = deduplicatePlaylistTracks(
                buildList {
                    val maxLen = maxOf(saavnIndiaTracks.size, upgradedGlobalCharts.size)
                    for (i in 0 until maxLen) {
                        if (i < saavnIndiaTracks.size) add(saavnIndiaTracks[i])
                        if (i < upgradedGlobalCharts.size) add(upgradedGlobalCharts[i])
                    }
                }
            ).take(25)

            cachedGlobalChartSongs = blendedChartFeed.ifEmpty { upgradedGlobalCharts }

            val fallbackArt1 = upgradedGlobalCharts.getOrNull(0)?.artworkUrl.orEmpty()
            val fallbackArt2 = upgradedGlobalCharts.getOrNull(1)?.artworkUrl ?: fallbackArt1
            val indiaArt1 = saavnIndiaTracks.getOrNull(0)?.artworkUrl ?: fallbackArt1
            val indiaArt2 = saavnIndiaTracks.getOrNull(1)?.artworkUrl ?: indiaArt1
            val indiaArt3 = saavnIndiaTracks.getOrNull(2)?.artworkUrl ?: indiaArt1

            val liveIndianTopHits = listOfNotNull(
                if (indiaArt1.isNotBlank()) PlaylistItem(
                    id = "live_pl_top_50_india",
                    title = "Top 50 — India",
                    subtitle = "Daily India Chart • ${saavnIndiaTracks.size.coerceAtLeast(25)} songs",
                    artworkUrl = indiaArt1,
                    category = "India Hits",
                    sourcePlatform = "Spotify",
                    searchFallbackQuery = "Top 50 India Hindi Trending Hits Arijit Singh",
                    trackCount = saavnIndiaTracks.size.coerceAtLeast(25),
                    preloadedTracks = saavnIndiaTracks
                ) else null,
                if (indiaArt2.isNotBlank()) PlaylistItem(
                    id = "live_pl_bollywood_hot_hits",
                    title = "Bollywood Hot Hits",
                    subtitle = "Biggest Hindi Blockbusters • Arijit, Pritam & Shreya",
                    artworkUrl = indiaArt2,
                    category = "Bollywood",
                    sourcePlatform = "Spotify",
                    searchFallbackQuery = "Bollywood Hot Hits Latest Hindi Songs",
                    trackCount = saavnIndiaTracks.size.coerceAtLeast(25),
                    preloadedTracks = saavnIndiaTracks
                ) else null,
                if (indiaArt3.isNotBlank()) PlaylistItem(
                    id = "live_pl_bollywood_romance",
                    title = "Romantic Bollywood & Soul",
                    subtitle = "Arijit Singh, Vishal Mishra, Atif & Shreya Ghoshal",
                    artworkUrl = indiaArt3,
                    category = "Bollywood",
                    sourcePlatform = "JioSaavn",
                    searchFallbackQuery = "Romantic Hindi Songs Arijit Singh Vishal Mishra",
                    trackCount = 28
                ) else null
            )

            val liveEditorialTopHits = listOfNotNull(
                if (fallbackArt1.isNotBlank()) PlaylistItem(
                    id = "live_pl_global_top_50",
                    title = "Global Top 50",
                    subtitle = "Daily Global Chart • ${upgradedGlobalCharts.size.coerceAtLeast(25)} songs",
                    artworkUrl = fallbackArt1,
                    category = "Top Hits",
                    sourcePlatform = "Spotify",
                    searchFallbackQuery = "Global Top Hits English Trending",
                    trackCount = upgradedGlobalCharts.size.coerceAtLeast(25),
                    preloadedTracks = saavnTracks.ifEmpty { upgradedGlobalCharts }
                ) else null,
                if (fallbackArt2.isNotBlank()) PlaylistItem(
                    id = "live_pl_viral_50",
                    title = "Viral 50 — Global",
                    subtitle = "Trending Global Viral Hits",
                    artworkUrl = fallbackArt2,
                    category = "Viral",
                    sourcePlatform = "Spotify",
                    searchFallbackQuery = "Viral Hits Trending Songs",
                    trackCount = 30
                ) else null
            )

            val mergedIndianTopPlaylists = (liveIndianTopHits + saavnIndiaTopPlaylists)
                .filter { it.artworkUrl.isNotBlank() }
                .distinctBy { canonicalSongTitle(it.title) }
                .take(12)

            val mergedTopPlaylists = liveEditorialTopHits
                .filter { it.artworkUrl.isNotBlank() }
                .distinctBy { canonicalSongTitle(it.title) }

            HomeDiscoveryData(
                topHitsPlaylists = mergedTopPlaylists,
                indianTopPlaylists = mergedIndianTopPlaylists,
                indianGenrePlaylists = emptyList(),
                newAndMoodPlaylists = emptyList(),
                genrePlaylists = emptyList(),
                globalChartSongs = upgradedGlobalCharts,
                indianChartSongs = saavnIndiaTracks
            )
        }
    }

    /**
     * Lazy-loads a single platform shelf when the user scrolls near it or selects a category filter.
     */
    suspend fun fetchLazyPlatformShelf(
        shelfKey: LazyPlatformShelfKey,
        existingData: HomeDiscoveryData
    ): HomeDiscoveryData = withContext(Dispatchers.IO) {
        coroutineScope {
            when (shelfKey) {
                LazyPlatformShelfKey.INDIAN_GENRES -> {
                    val punjabiTracksDef = async {
                        runCatching { searchJioSaavnCatalog("Punjabi Hits Diljit Dosanjh Karan Aujla AP Dhillon", page = 1, limit = 15) }.getOrDefault(emptyList())
                    }
                    val southTracksDef = async {
                        runCatching { searchJioSaavnCatalog("Anirudh Ravichander AR Rahman Tamil Telugu Hits", page = 1, limit = 15) }.getOrDefault(emptyList())
                    }
                    val punjabiPlDef = async {
                        runCatching {
                            searchJioSaavnPlaylists(
                                query = "Punjabi Top Hits Desi Hip Hop Indian Indie",
                                category = "Punjabi & Hip-Hop",
                                sourcePlatform = "JioSaavn India"
                            )
                        }.getOrDefault(emptyList())
                    }
                    val southPlDef = async {
                        runCatching {
                            searchJioSaavnPlaylists(
                                query = "Tamil Hits Telugu Hits Malayalam Sufi",
                                category = "South & Sufi",
                                sourcePlatform = "JioSaavn India"
                            )
                        }.getOrDefault(emptyList())
                    }
                    val ytmIndiaDef = async {
                        runCatching {
                            searchInnerTubePlaylists(
                                query = "Top 50 India Bollywood Hot Hits Punjabi Desi Hip Hop Tamil",
                                category = "India Hits",
                                sourcePlatform = "YouTube Music India"
                            )
                        }.getOrDefault(emptyList())
                    }

                    val punjabiTracks = deduplicatePlaylistTracks(punjabiTracksDef.await())
                    val southTracks = deduplicatePlaylistTracks(southTracksDef.await())
                    val fallbackArt = existingData.indianChartSongs.firstOrNull()?.artworkUrl
                        ?: existingData.globalChartSongs.firstOrNull()?.artworkUrl.orEmpty()
                    val punjabiArt1 = punjabiTracks.getOrNull(0)?.artworkUrl ?: fallbackArt
                    val punjabiArt2 = punjabiTracks.getOrNull(1)?.artworkUrl ?: punjabiArt1
                    val punjabiArt3 = punjabiTracks.getOrNull(2)?.artworkUrl ?: punjabiArt1
                    val southArt1 = southTracks.getOrNull(0)?.artworkUrl ?: fallbackArt
                    val southArt2 = southTracks.getOrNull(1)?.artworkUrl ?: southArt1
                    val southArt3 = southTracks.getOrNull(2)?.artworkUrl ?: fallbackArt

                    val liveIndianGenres = listOfNotNull(
                        if (punjabiArt1.isNotBlank()) PlaylistItem(
                            id = "live_pl_punjabi_101",
                            title = "Punjabi 101 & Pop",
                            subtitle = "Diljit Dosanjh, Karan Aujla, AP Dhillon & Shubh",
                            artworkUrl = punjabiArt1,
                            category = "Punjabi & Hip-Hop",
                            sourcePlatform = "Spotify",
                            searchFallbackQuery = "Punjabi Hits Diljit Dosanjh Karan Aujla AP Dhillon Shubh",
                            trackCount = punjabiTracks.size.coerceAtLeast(25),
                            preloadedTracks = punjabiTracks
                        ) else null,
                        if (punjabiArt2.isNotBlank()) PlaylistItem(
                            id = "live_pl_desi_hip_hop",
                            title = "Desi Hip-Hop Hustle",
                            subtitle = "DIVINE, KR\$NA, Seedhe Maut, Badshah & Raftaar",
                            artworkUrl = punjabiArt2,
                            category = "Punjabi & Hip-Hop",
                            sourcePlatform = "Spotify",
                            searchFallbackQuery = "Desi Hip Hop Divine Kr\$na Seedhe Maut Raftaar",
                            trackCount = 25
                        ) else null,
                        if (punjabiArt3.isNotBlank()) PlaylistItem(
                            id = "live_pl_indian_indie",
                            title = "Indian Indie Now",
                            subtitle = "Anuv Jain, Prateek Kuhad, Aditya Rikhari & Mitraz",
                            artworkUrl = punjabiArt3,
                            category = "India Hits",
                            sourcePlatform = "Spotify",
                            searchFallbackQuery = "Anuv Jain Prateek Kuhad Aditya Rikhari Indian Indie",
                            trackCount = 25
                        ) else null,
                        if (southArt1.isNotBlank()) PlaylistItem(
                            id = "live_pl_south_kollywood_tollywood",
                            title = "South Superhits — Tamil & Telugu",
                            subtitle = "Anirudh Ravichander, A.R. Rahman, DSP & Thaman S",
                            artworkUrl = southArt1,
                            category = "South & Sufi",
                            sourcePlatform = "JioSaavn",
                            searchFallbackQuery = "Anirudh Ravichander AR Rahman Tamil Telugu Hits",
                            trackCount = southTracks.size.coerceAtLeast(25),
                            preloadedTracks = southTracks
                        ) else null,
                        if (southArt2.isNotBlank()) PlaylistItem(
                            id = "live_pl_malayalam_pearls",
                            title = "Malayalam Vibes & Hits",
                            subtitle = "Sushin Shyam, Jakes Bejoy, Vineeth Sreenivasan",
                            artworkUrl = southArt2,
                            category = "South & Sufi",
                            sourcePlatform = "JioSaavn",
                            searchFallbackQuery = "Malayalam Hits Sushin Shyam Aavesham Premalu",
                            trackCount = 25
                        ) else null,
                        if (southArt3.isNotBlank()) PlaylistItem(
                            id = "live_pl_sufi_soul_india",
                            title = "Sufi & Soulful India",
                            subtitle = "A.R. Rahman, Nusrat Fateh Ali Khan, Kailash Kher & Coke Studio",
                            artworkUrl = southArt3,
                            category = "South & Sufi",
                            sourcePlatform = "JioSaavn",
                            searchFallbackQuery = "Sufi Songs Kun Faya Kun AR Rahman Rahat Fateh Ali Khan",
                            trackCount = 25
                        ) else null
                    )

                    val mergedIndianGenres = (liveIndianGenres + punjabiPlDef.await() + southPlDef.await() + ytmIndiaDef.await())
                        .filter { it.artworkUrl.isNotBlank() }
                        .distinctBy { canonicalSongTitle(it.title) }
                        .take(14)

                    val updatedIndianCharts = deduplicatePlaylistTracks(
                        existingData.indianChartSongs + punjabiTracks + southTracks
                    ).take(25)

                    existingData.copy(
                        indianGenrePlaylists = mergedIndianGenres,
                        indianChartSongs = updatedIndianCharts
                    )
                }

                LazyPlatformShelfKey.GLOBAL_TOP_EXPANDED -> {
                    val deezerPlDef = async {
                        runCatching { fetchDeezerChartPlaylists() }.getOrDefault(emptyList())
                    }
                    val saavnTopPlDef = async {
                        runCatching {
                            searchJioSaavnPlaylists(
                                query = "Top Hits Viral Global",
                                category = "Top Hits",
                                sourcePlatform = "JioSaavn"
                            )
                        }.getOrDefault(emptyList())
                    }
                    val ytmTopPlDef = async {
                        runCatching {
                            searchInnerTubePlaylists(
                                query = "Today's Top Hits Global Top 50 Viral",
                                category = "Top Hits",
                                sourcePlatform = "YouTube Music"
                            )
                        }.getOrDefault(emptyList())
                    }

                    val mergedTop = (existingData.topHitsPlaylists + saavnTopPlDef.await() + deezerPlDef.await() + ytmTopPlDef.await())
                        .filter { it.artworkUrl.isNotBlank() }
                        .distinctBy { canonicalSongTitle(it.title) }
                        .take(12)

                    existingData.copy(topHitsPlaylists = mergedTop)
                }

                LazyPlatformShelfKey.GLOBAL_MOODS -> {
                    val saavnMoodDef = async {
                        runCatching {
                            searchJioSaavnPlaylists(
                                query = "New Releases Chill Workout Focus",
                                category = "Moods",
                                sourcePlatform = "JioSaavn"
                            )
                        }.getOrDefault(emptyList())
                    }
                    val ytmMoodDef = async {
                        runCatching {
                            searchInnerTubePlaylists(
                                query = "New Music Friday Workout Chill Focus",
                                category = "New Releases",
                                sourcePlatform = "YouTube Music"
                            )
                        }.getOrDefault(emptyList())
                    }

                    val fallback3 = existingData.globalChartSongs.getOrNull(2)?.artworkUrl
                        ?: existingData.globalChartSongs.firstOrNull()?.artworkUrl.orEmpty()
                    val fallback4 = existingData.globalChartSongs.getOrNull(3)?.artworkUrl ?: fallback3

                    val liveEditorialMoods = listOfNotNull(
                        if (fallback3.isNotBlank()) PlaylistItem(
                            id = "live_pl_new_music_friday",
                            title = "New Music Friday",
                            subtitle = "Fresh releases from top artists",
                            artworkUrl = fallback3,
                            category = "New Releases",
                            sourcePlatform = "Spotify",
                            searchFallbackQuery = "New Music Hits Latest Songs",
                            trackCount = 30
                        ) else null,
                        if (fallback4.isNotBlank()) PlaylistItem(
                            id = "live_pl_late_night_vibes",
                            title = "Late Night Vibes & Chill",
                            subtitle = "Atmospheric R&B, Synthwave & Lo-Fi",
                            artworkUrl = fallback4,
                            category = "Moods",
                            sourcePlatform = "Spotify",
                            searchFallbackQuery = "The Weeknd M83 Late Night Chill",
                            trackCount = 25
                        ) else null
                    )

                    val mergedMoods = (liveEditorialMoods + saavnMoodDef.await() + ytmMoodDef.await())
                        .filter { it.artworkUrl.isNotBlank() }
                        .distinctBy { canonicalSongTitle(it.title) }
                        .take(12)

                    existingData.copy(newAndMoodPlaylists = mergedMoods)
                }

                LazyPlatformShelfKey.GLOBAL_GENRES -> {
                    val saavnGenreDef = async {
                        runCatching {
                            searchJioSaavnPlaylists(
                                query = "Pop Hip Hop Electronic Rock Lo-Fi",
                                category = "Genres",
                                sourcePlatform = "JioSaavn"
                            )
                        }.getOrDefault(emptyList())
                    }
                    val ytmGenreDef = async {
                        runCatching {
                            searchInnerTubePlaylists(
                                query = "Pop Rising RapCaviar Electronic Lo-Fi",
                                category = "Genres",
                                sourcePlatform = "Spotify & YT Music"
                            )
                        }.getOrDefault(emptyList())
                    }

                    val fallback5 = existingData.globalChartSongs.getOrNull(4)?.artworkUrl
                        ?: existingData.globalChartSongs.firstOrNull()?.artworkUrl.orEmpty()
                    val fallback6 = existingData.globalChartSongs.getOrNull(5)?.artworkUrl ?: fallback5

                    val liveEditorialGenres = listOfNotNull(
                        if (fallback5.isNotBlank()) PlaylistItem(
                            id = "live_pl_pop_rising",
                            title = "Pop Rising & Hot Hits",
                            subtitle = "The biggest pop anthems right now",
                            artworkUrl = fallback5,
                            category = "Genres",
                            sourcePlatform = "Spotify",
                            searchFallbackQuery = "Pop Hits Taylor Swift Dua Lipa Weeknd",
                            trackCount = 30
                        ) else null,
                        if (fallback6.isNotBlank()) PlaylistItem(
                            id = "live_pl_hiphop_central",
                            title = "Hip-Hop Central",
                            subtitle = "Hip-Hop, Trap & R&B Essentials",
                            artworkUrl = fallback6,
                            category = "Genres",
                            sourcePlatform = "Spotify",
                            searchFallbackQuery = "Hip Hop Hits Drake Kendrick Travis Scott",
                            trackCount = 30
                        ) else null
                    )

                    val mergedGenres = (liveEditorialGenres + saavnGenreDef.await() + ytmGenreDef.await())
                        .filter { it.artworkUrl.isNotBlank() }
                        .distinctBy { canonicalSongTitle(it.title) }
                        .take(12)

                    existingData.copy(genrePlaylists = mergedGenres)
                }
            }
        }
    }

    suspend fun fetchRealHomeDiscoveryData(): HomeDiscoveryData = withContext(Dispatchers.IO) {
        coroutineScope {
            // 1. Live Global & Indian Chart Songs
            val deezerChartTracksDef = async {
                runCatching { fetchDeezerGlobalChartTracks() }.getOrDefault(emptyList())
            }
            val appleRssTracksDef = async {
                runCatching { fetchAppleMusicTopSongsRss() }.getOrDefault(emptyList())
            }
            val saavnTopHitsTracksDef = async {
                runCatching { searchJioSaavnCatalog("Top English Hits") }.getOrDefault(emptyList())
            }
            val saavnIndiaHitsTracksDef = async {
                runCatching { searchJioSaavnCatalog("Top Hindi Hits Bollywood Arijit Singh") }.getOrDefault(emptyList())
            }
            val saavnPunjabiTracksDef = async {
                runCatching { searchJioSaavnCatalog("Punjabi Hits Diljit Dosanjh Karan Aujla AP Dhillon") }.getOrDefault(emptyList())
            }
            val saavnSouthTracksDef = async {
                runCatching { searchJioSaavnCatalog("Anirudh Ravichander AR Rahman Tamil Telugu Hits") }.getOrDefault(emptyList())
            }

            // 2. Live Global Playlists from Deezer, JioSaavn & YouTube Music
            val deezerChartPlaylistsDef = async {
                runCatching { fetchDeezerChartPlaylists() }.getOrDefault(emptyList())
            }
            val saavnTopPlaylistsDef = async {
                runCatching {
                    searchJioSaavnPlaylists(
                        query = "Top Hits Viral Global",
                        category = "Top Hits",
                        sourcePlatform = "JioSaavn"
                    )
                }.getOrDefault(emptyList())
            }
            val saavnMoodPlaylistsDef = async {
                runCatching {
                    searchJioSaavnPlaylists(
                        query = "New Releases Chill Workout Focus",
                        category = "Moods",
                        sourcePlatform = "JioSaavn"
                    )
                }.getOrDefault(emptyList())
            }
            val saavnGenrePlaylistsDef = async {
                runCatching {
                    searchJioSaavnPlaylists(
                        query = "Pop Hip Hop Electronic Rock Lo-Fi",
                        category = "Genres",
                        sourcePlatform = "JioSaavn"
                    )
                }.getOrDefault(emptyList())
            }
            val ytmTopPlaylistsDef = async {
                runCatching {
                    searchInnerTubePlaylists(
                        query = "Today's Top Hits Global Top 50 Viral",
                        category = "Top Hits",
                        sourcePlatform = "YouTube Music"
                    )
                }.getOrDefault(emptyList())
            }
            val ytmMoodPlaylistsDef = async {
                runCatching {
                    searchInnerTubePlaylists(
                        query = "New Music Friday Workout Chill Focus",
                        category = "New Releases",
                        sourcePlatform = "YouTube Music"
                    )
                }.getOrDefault(emptyList())
            }
            val ytmGenrePlaylistsDef = async {
                runCatching {
                    searchInnerTubePlaylists(
                        query = "Pop Rising RapCaviar Electronic Lo-Fi",
                        category = "Genres",
                        sourcePlatform = "Spotify & YT Music"
                    )
                }.getOrDefault(emptyList())
            }

            // 3. Live Indian Charts & Regional Genre Playlists (Bollywood, Punjabi, Desi Hip-Hop, Indie, South Indian & Sufi)
            val saavnIndiaTopPlaylistsDef = async {
                runCatching {
                    searchJioSaavnPlaylists(
                        query = "Bollywood Top 50 Trending Hindi Hits",
                        category = "Bollywood",
                        sourcePlatform = "JioSaavn India"
                    )
                }.getOrDefault(emptyList())
            }
            val saavnPunjabiHipHopPlaylistsDef = async {
                runCatching {
                    searchJioSaavnPlaylists(
                        query = "Punjabi Top Hits Desi Hip Hop Indian Indie",
                        category = "Punjabi & Hip-Hop",
                        sourcePlatform = "JioSaavn India"
                    )
                }.getOrDefault(emptyList())
            }
            val saavnSouthSufiPlaylistsDef = async {
                runCatching {
                    searchJioSaavnPlaylists(
                        query = "Tamil Hits Telugu Hits Malayalam Sufi",
                        category = "South & Sufi",
                        sourcePlatform = "JioSaavn India"
                    )
                }.getOrDefault(emptyList())
            }
            val ytmIndiaPlaylistsDef = async {
                runCatching {
                    searchInnerTubePlaylists(
                        query = "Top 50 India Bollywood Hot Hits Punjabi Desi Hip Hop Tamil",
                        category = "India Hits",
                        sourcePlatform = "YouTube Music India"
                    )
                }.getOrDefault(emptyList())
            }

            val saavnTracks = deduplicatePlaylistTracks(saavnTopHitsTracksDef.await())
            val saavnIndiaTracks = deduplicatePlaylistTracks(saavnIndiaHitsTracksDef.await())
            val saavnPunjabiTracks = deduplicatePlaylistTracks(saavnPunjabiTracksDef.await())
            val saavnSouthTracks = deduplicatePlaylistTracks(saavnSouthTracksDef.await())
            val deezerTracks = deduplicatePlaylistTracks(deezerChartTracksDef.await())
            val appleTracks = deduplicatePlaylistTracks(appleRssTracksDef.await())

            val combinedIndianTracks = deduplicatePlaylistTracks(
                saavnIndiaTracks + saavnPunjabiTracks + saavnSouthTracks
            ).take(25)

            // Upgrade Deezer & Apple Music chart songs with full-length 320kbps JioSaavn streams where matched
            val upgradedGlobalCharts = deduplicatePlaylistTracks(
                (saavnTracks + deezerTracks + appleTracks).map { song ->
                    if (song.id.startsWith("saavn_")) {
                        song
                    } else {
                        val fullMatch = findBestMatchingTrack(song.title, song.artist, saavnTracks)
                        if (fullMatch != null && isFullLengthStreamUrl(fullMatch.streamUrl)) {
                            song.copy(
                                streamUrl = fullMatch.streamUrl,
                                durationMs = fullMatch.durationMs.takeIf { it > 60_000L } ?: song.durationMs
                            ).also {
                                resolvedStreamCache[it.id] = fullMatch.streamUrl
                                songMetadataCache[it.id] = it
                            }
                        } else {
                            song.copy(streamUrl = "").also {
                                songMetadataCache[it.id] = it
                            }
                        }
                    }
                }
            ).take(25)

            // Interleave top Indian & Global tracks for the master feed while keeping dedicated lists
            val blendedChartFeed = deduplicatePlaylistTracks(
                buildList {
                    val maxLen = maxOf(combinedIndianTracks.size, upgradedGlobalCharts.size)
                    for (i in 0 until maxLen) {
                        if (i < combinedIndianTracks.size) add(combinedIndianTracks[i])
                        if (i < upgradedGlobalCharts.size) add(upgradedGlobalCharts[i])
                    }
                }
            ).take(30)

            cachedGlobalChartSongs = blendedChartFeed.ifEmpty { upgradedGlobalCharts }

            val saavnTopPlaylists = saavnTopPlaylistsDef.await()
            val saavnMoodPlaylists = saavnMoodPlaylistsDef.await()
            val saavnGenrePlaylists = saavnGenrePlaylistsDef.await()

            val saavnIndiaTopPlaylists = saavnIndiaTopPlaylistsDef.await()
            val saavnPunjabiHipHopPlaylists = saavnPunjabiHipHopPlaylistsDef.await()
            val saavnSouthSufiPlaylists = saavnSouthSufiPlaylistsDef.await()

            // Dynamic artwork from live Indian & Global tracks
            val fallbackArt1 = upgradedGlobalCharts.getOrNull(0)?.artworkUrl.orEmpty()
            val fallbackArt2 = upgradedGlobalCharts.getOrNull(1)?.artworkUrl ?: fallbackArt1
            val fallbackArt3 = upgradedGlobalCharts.getOrNull(2)?.artworkUrl ?: fallbackArt1
            val fallbackArt4 = upgradedGlobalCharts.getOrNull(3)?.artworkUrl ?: fallbackArt1
            val fallbackArt5 = upgradedGlobalCharts.getOrNull(4)?.artworkUrl ?: fallbackArt1
            val fallbackArt6 = upgradedGlobalCharts.getOrNull(5)?.artworkUrl ?: fallbackArt1

            val indiaArt1 = saavnIndiaTracks.getOrNull(0)?.artworkUrl ?: fallbackArt1
            val indiaArt2 = saavnIndiaTracks.getOrNull(1)?.artworkUrl ?: indiaArt1
            val indiaArt3 = saavnIndiaTracks.getOrNull(2)?.artworkUrl ?: indiaArt1
            val punjabiArt1 = saavnPunjabiTracks.getOrNull(0)?.artworkUrl ?: indiaArt1
            val punjabiArt2 = saavnPunjabiTracks.getOrNull(1)?.artworkUrl ?: punjabiArt1
            val punjabiArt3 = saavnPunjabiTracks.getOrNull(2)?.artworkUrl ?: indiaArt2
            val southArt1 = saavnSouthTracks.getOrNull(0)?.artworkUrl ?: indiaArt1
            val southArt2 = saavnSouthTracks.getOrNull(1)?.artworkUrl ?: southArt1
            val southArt3 = saavnSouthTracks.getOrNull(2)?.artworkUrl ?: indiaArt3

            // Standalone Top India & Bollywood Playlists (No cross-platform mixing)
            val liveIndianTopHits = listOfNotNull(
                if (indiaArt1.isNotBlank()) PlaylistItem(
                    id = "live_pl_top_50_india",
                    title = "Top 50 — India",
                    subtitle = "Daily India Chart • ${combinedIndianTracks.size.coerceAtLeast(25)} songs",
                    artworkUrl = indiaArt1,
                    category = "India Hits",
                    sourcePlatform = "Spotify",
                    searchFallbackQuery = "Top 50 India Hindi Trending Hits Arijit Singh",
                    trackCount = combinedIndianTracks.size.coerceAtLeast(25),
                    preloadedTracks = combinedIndianTracks
                ) else null,
                if (indiaArt2.isNotBlank()) PlaylistItem(
                    id = "live_pl_bollywood_hot_hits",
                    title = "Bollywood Hot Hits",
                    subtitle = "Biggest Hindi Blockbusters • Arijit, Pritam & Shreya",
                    artworkUrl = indiaArt2,
                    category = "Bollywood",
                    sourcePlatform = "Spotify",
                    searchFallbackQuery = "Bollywood Hot Hits Latest Hindi Songs",
                    trackCount = saavnIndiaTracks.size.coerceAtLeast(25),
                    preloadedTracks = saavnIndiaTracks
                ) else null,
                if (indiaArt3.isNotBlank()) PlaylistItem(
                    id = "live_pl_bollywood_romance",
                    title = "Romantic Bollywood & Soul",
                    subtitle = "Arijit Singh, Vishal Mishra, Atif & Shreya Ghoshal",
                    artworkUrl = indiaArt3,
                    category = "Bollywood",
                    sourcePlatform = "JioSaavn",
                    searchFallbackQuery = "Romantic Hindi Songs Arijit Singh Vishal Mishra",
                    trackCount = 28
                ) else null
            )

            // Standalone Indian Genres (Punjabi Pop, Desi Hip-Hop, Indian Indie, South Indian, Sufi)
            val liveIndianGenres = listOfNotNull(
                if (punjabiArt1.isNotBlank()) PlaylistItem(
                    id = "live_pl_punjabi_101",
                    title = "Punjabi 101 & Pop",
                    subtitle = "Diljit Dosanjh, Karan Aujla, AP Dhillon & Shubh",
                    artworkUrl = punjabiArt1,
                    category = "Punjabi & Hip-Hop",
                    sourcePlatform = "Spotify",
                    searchFallbackQuery = "Punjabi Hits Diljit Dosanjh Karan Aujla AP Dhillon Shubh",
                    trackCount = saavnPunjabiTracks.size.coerceAtLeast(25),
                    preloadedTracks = saavnPunjabiTracks
                ) else null,
                if (punjabiArt2.isNotBlank()) PlaylistItem(
                    id = "live_pl_desi_hip_hop",
                    title = "Desi Hip-Hop Hustle",
                    subtitle = "DIVINE, KR\$NA, Seedhe Maut, Badshah & Raftaar",
                    artworkUrl = punjabiArt2,
                    category = "Punjabi & Hip-Hop",
                    sourcePlatform = "Spotify",
                    searchFallbackQuery = "Desi Hip Hop Divine Kr\$na Seedhe Maut Raftaar",
                    trackCount = 25
                ) else null,
                if (punjabiArt3.isNotBlank()) PlaylistItem(
                    id = "live_pl_indian_indie",
                    title = "Indian Indie Now",
                    subtitle = "Anuv Jain, Prateek Kuhad, Aditya Rikhari & Mitraz",
                    artworkUrl = punjabiArt3,
                    category = "India Hits",
                    sourcePlatform = "Spotify",
                    searchFallbackQuery = "Anuv Jain Prateek Kuhad Aditya Rikhari Indian Indie",
                    trackCount = 25
                ) else null,
                if (southArt1.isNotBlank()) PlaylistItem(
                    id = "live_pl_south_kollywood_tollywood",
                    title = "South Superhits — Tamil & Telugu",
                    subtitle = "Anirudh Ravichander, A.R. Rahman, DSP & Thaman S",
                    artworkUrl = southArt1,
                    category = "South & Sufi",
                    sourcePlatform = "JioSaavn",
                    searchFallbackQuery = "Anirudh Ravichander AR Rahman Tamil Telugu Hits",
                    trackCount = saavnSouthTracks.size.coerceAtLeast(25),
                    preloadedTracks = saavnSouthTracks
                ) else null,
                if (southArt2.isNotBlank()) PlaylistItem(
                    id = "live_pl_malayalam_pearls",
                    title = "Malayalam Vibes & Hits",
                    subtitle = "Sushin Shyam, Jakes Bejoy, Vineeth Sreenivasan",
                    artworkUrl = southArt2,
                    category = "South & Sufi",
                    sourcePlatform = "JioSaavn",
                    searchFallbackQuery = "Malayalam Hits Sushin Shyam Aavesham Premalu",
                    trackCount = 25
                ) else null,
                if (southArt3.isNotBlank()) PlaylistItem(
                    id = "live_pl_sufi_soul_india",
                    title = "Sufi & Soulful India",
                    subtitle = "A.R. Rahman, Nusrat Fateh Ali Khan, Kailash Kher & Coke Studio",
                    artworkUrl = southArt3,
                    category = "South & Sufi",
                    sourcePlatform = "JioSaavn",
                    searchFallbackQuery = "Sufi Songs Kun Faya Kun AR Rahman Rahat Fateh Ali Khan",
                    trackCount = 25
                ) else null
            )

            val liveEditorialTopHits = listOfNotNull(
                if (fallbackArt1.isNotBlank()) PlaylistItem(
                    id = "live_pl_global_top_50",
                    title = "Global Top 50",
                    subtitle = "Daily Global Chart • ${upgradedGlobalCharts.size.coerceAtLeast(25)} songs",
                    artworkUrl = fallbackArt1,
                    category = "Top Hits",
                    sourcePlatform = "Spotify",
                    searchFallbackQuery = "Global Top Hits English Trending",
                    trackCount = upgradedGlobalCharts.size.coerceAtLeast(25),
                    preloadedTracks = saavnTracks.ifEmpty { upgradedGlobalCharts }
                ) else null,
                if (fallbackArt2.isNotBlank()) PlaylistItem(
                    id = "live_pl_viral_50",
                    title = "Viral 50 — Global",
                    subtitle = "Trending Global Viral Hits",
                    artworkUrl = fallbackArt2,
                    category = "Viral",
                    sourcePlatform = "Spotify",
                    searchFallbackQuery = "Viral Hits Trending Songs",
                    trackCount = 30
                ) else null
            )

            val liveEditorialMoods = listOfNotNull(
                if (fallbackArt3.isNotBlank()) PlaylistItem(
                    id = "live_pl_new_music_friday",
                    title = "New Music Friday",
                    subtitle = "Fresh releases from top artists",
                    artworkUrl = fallbackArt3,
                    category = "New Releases",
                    sourcePlatform = "Spotify",
                    searchFallbackQuery = "New Music Hits Latest Songs",
                    trackCount = 30
                ) else null,
                if (fallbackArt4.isNotBlank()) PlaylistItem(
                    id = "live_pl_late_night_vibes",
                    title = "Late Night Vibes & Chill",
                    subtitle = "Atmospheric R&B, Synthwave & Lo-Fi",
                    artworkUrl = fallbackArt4,
                    category = "Moods",
                    sourcePlatform = "Spotify",
                    searchFallbackQuery = "The Weeknd M83 Late Night Chill",
                    trackCount = 25
                ) else null
            )

            val liveEditorialGenres = listOfNotNull(
                if (fallbackArt5.isNotBlank()) PlaylistItem(
                    id = "live_pl_pop_rising",
                    title = "Pop Rising & Hot Hits",
                    subtitle = "The biggest pop anthems right now",
                    artworkUrl = fallbackArt5,
                    category = "Genres",
                    sourcePlatform = "Spotify",
                    searchFallbackQuery = "Pop Hits Taylor Swift Dua Lipa Weeknd",
                    trackCount = 30
                ) else null,
                if (fallbackArt6.isNotBlank()) PlaylistItem(
                    id = "live_pl_hiphop_central",
                    title = "Hip-Hop Central",
                    subtitle = "Hip-Hop, Trap & R&B Essentials",
                    artworkUrl = fallbackArt6,
                    category = "Genres",
                    sourcePlatform = "Spotify",
                    searchFallbackQuery = "Hip Hop Hits Drake Kendrick Travis Scott",
                    trackCount = 30
                ) else null
            )

            // Keep every playlist row standalone (single catalog source, never mixing platforms)
            val mergedIndianTopPlaylists = (liveIndianTopHits + saavnIndiaTopPlaylists)
                .filter { it.artworkUrl.isNotBlank() }
                .distinctBy { canonicalSongTitle(it.title) }
                .take(12)

            val mergedIndianGenrePlaylists = (liveIndianGenres + saavnPunjabiHipHopPlaylists + saavnSouthSufiPlaylists)
                .filter { it.artworkUrl.isNotBlank() }
                .distinctBy { canonicalSongTitle(it.title) }
                .take(14)

            val mergedTopPlaylists = (liveEditorialTopHits + saavnTopPlaylists)
                .filter { it.artworkUrl.isNotBlank() }
                .distinctBy { canonicalSongTitle(it.title) }
                .take(12)

            val mergedMoodPlaylists = (liveEditorialMoods + saavnMoodPlaylists)
                .filter { it.artworkUrl.isNotBlank() }
                .distinctBy { canonicalSongTitle(it.title) }
                .take(12)

            val mergedGenrePlaylists = (liveEditorialGenres + saavnGenrePlaylists)
                .filter { it.artworkUrl.isNotBlank() }
                .distinctBy { canonicalSongTitle(it.title) }
                .take(12)

            HomeDiscoveryData(
                topHitsPlaylists = mergedTopPlaylists,
                indianTopPlaylists = mergedIndianTopPlaylists,
                indianGenrePlaylists = mergedIndianGenrePlaylists,
                newAndMoodPlaylists = mergedMoodPlaylists,
                genrePlaylists = mergedGenrePlaylists,
                globalChartSongs = upgradedGlobalCharts,
                indianChartSongs = combinedIndianTracks
            )
        }
    }

    /**
     * Paged lazy tracklist loader for Playlist Details (15 tracks per page by default)
     * so playlists on slow networks render the first tracks quickly and paginate as you scroll.
     */
    suspend fun fetchPlaylistTracksPage(
        playlist: PlaylistItem,
        page: Int = 1,
        pageSize: Int = 15
    ): PagedPlaylistTracksResult = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(5, 50)
        val pageCacheKey = "${playlist.id}_p${safePage}_s${safeSize}"

        playlistPageCache[pageCacheKey]?.let { return@withContext it }

        val cachedFull = playlistFullSnapshotCache[playlist.id] ?: playlistTracksCache[playlist.id]
        if (!cachedFull.isNullOrEmpty()) {
            val fromIndex = (safePage - 1) * safeSize
            if (fromIndex >= cachedFull.size) {
                return@withContext PagedPlaylistTracksResult(
                    tracks = emptyList(),
                    nextPage = null,
                    hasMore = false,
                    totalCount = cachedFull.size
                )
            }
            val toIndex = (fromIndex + safeSize).coerceAtMost(cachedFull.size)
            val slice = cachedFull.subList(fromIndex, toIndex).toList()
            val hasMore = toIndex < cachedFull.size
            return@withContext PagedPlaylistTracksResult(
                tracks = slice,
                nextPage = if (hasMore) safePage + 1 else null,
                hasMore = hasMore,
                totalCount = cachedFull.size
            ).also { playlistPageCache[pageCacheKey] = it }
        }

        // Imported Spotify playlists: resolve once cleanly, snapshot, then slice per page
        if (playlist.id.startsWith("imported_spotify_") && playlist.remotePlaylistId.isNotBlank()) {
            val existingSpotify = if (playlist.preloadedTracks.any { it.id.startsWith("spotify_trk_") }) {
                deduplicatePlaylistTracks(playlist.preloadedTracks)
            } else {
                val refreshed = runCatching { fetchSpotifyPlaylistById(playlist.remotePlaylistId) }.getOrNull()
                if (refreshed != null && refreshed.preloadedTracks.isNotEmpty()) {
                    val serialized = runCatching {
                        jsonParser.encodeToString(ListSerializer(SongItem.serializer()), refreshed.preloadedTracks)
                    }.getOrDefault("[]")
                    musicDao?.insertImportedPlaylist(
                        ImportedPlaylistEntity(
                            id = refreshed.id,
                            title = refreshed.title,
                            subtitle = refreshed.subtitle,
                            artworkUrl = refreshed.artworkUrl,
                            category = refreshed.category,
                            sourcePlatform = refreshed.sourcePlatform,
                            remotePlaylistId = refreshed.remotePlaylistId,
                            searchFallbackQuery = refreshed.searchFallbackQuery,
                            trackCount = refreshed.trackCount,
                            tracksJson = serialized
                        )
                    )
                    refreshed.preloadedTracks
                } else {
                    emptyList()
                }
            }
            if (existingSpotify.isNotEmpty()) {
                existingSpotify.forEach { songMetadataCache[it.id] = it }
                playlistFullSnapshotCache[playlist.id] = existingSpotify
                playlistTracksCache[playlist.id] = existingSpotify
                val fromIndex = (safePage - 1) * safeSize
                val toIndex = (fromIndex + safeSize).coerceAtMost(existingSpotify.size)
                val slice = if (fromIndex < existingSpotify.size) existingSpotify.subList(fromIndex, toIndex).toList() else emptyList()
                val hasMore = toIndex < existingSpotify.size
                return@withContext PagedPlaylistTracksResult(
                    tracks = slice,
                    nextPage = if (hasMore) safePage + 1 else null,
                    hasMore = hasMore,
                    totalCount = existingSpotify.size
                ).also { playlistPageCache[pageCacheKey] = it }
            }
        }

        // Preloaded tracks (e.g., Smart Mixes / Custom Playlists / Seeded Home lists):
        // Note: if preloadedTracks only has <= safeSize items on a live editorial playlist, allow page 2 to query catalog.
        if (playlist.preloadedTracks.isNotEmpty()) {
            val dedupedPreloaded = deduplicatePlaylistTracks(playlist.preloadedTracks)
            if (safePage == 1) {
                val slice = dedupedPreloaded.take(safeSize)
                val hasMore = dedupedPreloaded.size > safeSize ||
                    (playlist.id.startsWith("live_pl_") && playlist.trackCount > slice.size)
                if (dedupedPreloaded.size > safeSize) {
                    playlistFullSnapshotCache[playlist.id] = dedupedPreloaded
                }
                return@withContext PagedPlaylistTracksResult(
                    tracks = slice,
                    nextPage = if (hasMore) 2 else null,
                    hasMore = hasMore,
                    totalCount = maxOf(dedupedPreloaded.size, playlist.trackCount)
                ).also { playlistPageCache[pageCacheKey] = it }
            }
        }

        // True platform-paginated endpoints
        val pageResult: PagedPlaylistTracksResult = when {
            playlist.id.startsWith("deezer_pl_") && playlist.remotePlaylistId.isNotBlank() -> {
                fetchDeezerPlaylistDetailsPage(
                    playlistId = playlist.remotePlaylistId,
                    category = playlist.category,
                    page = safePage,
                    pageSize = safeSize
                )
            }
            playlist.id.startsWith("saavn_pl_") && playlist.remotePlaylistId.isNotBlank() -> {
                fetchJioSaavnPlaylistDetailsPage(
                    cacheOwnerId = playlist.id,
                    playlistId = playlist.remotePlaylistId,
                    category = playlist.category,
                    page = safePage,
                    pageSize = safeSize
                )
            }
            else -> {
                val query = playlist.searchFallbackQuery.ifBlank { playlist.title }
                val catalogTracks = deduplicatePlaylistTracks(
                    runCatching { searchJioSaavnCatalog(query, page = safePage, limit = safeSize) }
                        .getOrDefault(emptyList())
                        .map { it.copy(moodCategory = playlist.category) }
                )
                val hasMore = catalogTracks.size >= (safeSize - 2) && safePage < 4
                PagedPlaylistTracksResult(
                    tracks = catalogTracks,
                    nextPage = if (hasMore) safePage + 1 else null,
                    hasMore = hasMore,
                    totalCount = playlist.trackCount.coerceAtLeast(safePage * catalogTracks.size)
                )
            }
        }

        if (pageResult.tracks.isNotEmpty()) {
            playlistPageCache[pageCacheKey] = pageResult
            return@withContext pageResult
        }

        // Page 1 safety fallback
        if (safePage == 1) {
            val fallbackSlice = deduplicatePlaylistTracks(cachedGlobalChartSongs).take(safeSize)
            return@withContext PagedPlaylistTracksResult(
                tracks = fallbackSlice,
                nextPage = null,
                hasMore = false,
                totalCount = fallbackSlice.size
            )
        }

        PagedPlaylistTracksResult(
            tracks = emptyList(),
            nextPage = null,
            hasMore = false,
            totalCount = 0
        )
    }

    /**
     * Loads the full standalone tracklist for a clicked playlist without cross-platform mixing,
     * and strictly deduplicates songs so no track appears twice.
     */
    suspend fun fetchPlaylistTracks(playlist: PlaylistItem): List<SongItem> = withContext(Dispatchers.IO) {
        // If this is an imported Spotify playlist, ensure its tracks are real Spotify tracks (auto-heal any old fallback entries)
        if (playlist.id.startsWith("imported_spotify_") && playlist.remotePlaylistId.isNotBlank()) {
            val cached = playlistTracksCache[playlist.id]
            if (cached != null && cached.isNotEmpty() && cached.any { it.id.startsWith("spotify_trk_") }) {
                return@withContext cached
            }
            if (playlist.preloadedTracks.isNotEmpty() && playlist.preloadedTracks.any { it.id.startsWith("spotify_trk_") }) {
                val deduped = deduplicatePlaylistTracks(playlist.preloadedTracks)
                playlistTracksCache[playlist.id] = deduped
                deduped.forEach { songMetadataCache[it.id] = it }
                return@withContext deduped
            }
            val refreshedSpotifyPl = runCatching {
                fetchSpotifyPlaylistById(playlist.remotePlaylistId)
            }.getOrNull()
            if (refreshedSpotifyPl != null && refreshedSpotifyPl.preloadedTracks.isNotEmpty()) {
                playlistTracksCache[playlist.id] = refreshedSpotifyPl.preloadedTracks
                val serialized = runCatching {
                    jsonParser.encodeToString(ListSerializer(SongItem.serializer()), refreshedSpotifyPl.preloadedTracks)
                }.getOrDefault("[]")
                musicDao?.insertImportedPlaylist(
                    ImportedPlaylistEntity(
                        id = refreshedSpotifyPl.id,
                        title = refreshedSpotifyPl.title,
                        subtitle = refreshedSpotifyPl.subtitle,
                        artworkUrl = refreshedSpotifyPl.artworkUrl,
                        category = refreshedSpotifyPl.category,
                        sourcePlatform = refreshedSpotifyPl.sourcePlatform,
                        remotePlaylistId = refreshedSpotifyPl.remotePlaylistId,
                        searchFallbackQuery = refreshedSpotifyPl.searchFallbackQuery,
                        trackCount = refreshedSpotifyPl.trackCount,
                        tracksJson = serialized
                    )
                )
                return@withContext refreshedSpotifyPl.preloadedTracks
            }
        }

        playlistTracksCache[playlist.id]?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

        if (playlist.preloadedTracks.isNotEmpty()) {
            val dedupedPreloaded = deduplicatePlaylistTracks(playlist.preloadedTracks)
            playlistTracksCache[playlist.id] = dedupedPreloaded
            return@withContext dedupedPreloaded
        }

        val fetchedTracks: List<SongItem> = when {
            playlist.id.startsWith("saavn_pl_") && playlist.remotePlaylistId.isNotBlank() -> {
                runCatching { fetchJioSaavnPlaylistDetails(playlist.remotePlaylistId, playlist.category) }
                    .getOrDefault(emptyList())
            }
            playlist.id.startsWith("deezer_pl_") && playlist.remotePlaylistId.isNotBlank() -> {
                runCatching { fetchDeezerPlaylistDetails(playlist.remotePlaylistId, playlist.category) }
                    .getOrDefault(emptyList())
            }
            else -> emptyList()
        }

        if (fetchedTracks.isNotEmpty()) {
            val deduped = deduplicatePlaylistTracks(fetchedTracks)
            playlistTracksCache[playlist.id] = deduped
            return@withContext deduped
        }

        // Standalone single-source catalog fetch for curated Spiral playlists (never mixes platforms)
        val query = playlist.searchFallbackQuery.ifBlank { playlist.title }
        val standaloneCatalogTracks = runCatching { searchJioSaavnCatalog(query) }
            .getOrDefault(emptyList())
            .map { it.copy(moodCategory = playlist.category) }
        val dedupedSearched = deduplicatePlaylistTracks(standaloneCatalogTracks)
        if (dedupedSearched.isNotEmpty()) {
            playlistTracksCache[playlist.id] = dedupedSearched
            return@withContext dedupedSearched
        }

        deduplicatePlaylistTracks(cachedGlobalChartSongs)
    }

    private suspend fun fetchDeezerChartPlaylists(): List<PlaylistItem> {
        val responseText = httpGet("https://api.deezer.com/chart/0/playlists?limit=16")
        val root = jsonParser.parseToJsonElement(responseText).objOrNull() ?: return emptyList()
        val dataArray = root["data"].arrOrNull() ?: return emptyList()

        return dataArray.mapIndexedNotNull { idx, elem ->
            val obj = elem.objOrNull() ?: return@mapIndexedNotNull null
            val id = obj["id"].longValOrNull()?.toString() ?: obj["id"].strOrNull() ?: return@mapIndexedNotNull null
            val title = obj["title"].strOrNull() ?: return@mapIndexedNotNull null
            val artwork = obj["picture_xl"].strOrNull()
                ?: obj["picture_big"].strOrNull()
                ?: obj["picture_medium"].strOrNull()
                ?: return@mapIndexedNotNull null
            val trackCount = obj["nb_tracks"].intValOrNull() ?: 30
            val creator = obj["user"].objOrNull()?.get("name").strOrNull() ?: "Deezer Charts"

            val assignedCategory = when {
                title.contains("viral", ignoreCase = true) -> "Viral"
                title.contains("new", ignoreCase = true) || title.contains("fresh", ignoreCase = true) -> "New Releases"
                title.contains("chill", ignoreCase = true) || title.contains("mood", ignoreCase = true) || title.contains("workout", ignoreCase = true) -> "Moods"
                title.contains("pop", ignoreCase = true) || title.contains("rap", ignoreCase = true) || title.contains("hip", ignoreCase = true) || title.contains("dance", ignoreCase = true) || title.contains("rock", ignoreCase = true) -> "Genres"
                idx < 5 -> "Top Hits"
                idx < 9 -> "Moods"
                else -> "Genres"
            }

            PlaylistItem(
                id = "deezer_pl_$id",
                title = title,
                subtitle = "$creator • $trackCount tracks",
                artworkUrl = artwork,
                category = assignedCategory,
                sourcePlatform = "Deezer Chart",
                remotePlaylistId = id,
                searchFallbackQuery = title,
                trackCount = trackCount
            )
        }
    }

    private suspend fun fetchDeezerPlaylistDetailsPage(
        playlistId: String,
        category: String,
        page: Int,
        pageSize: Int
    ): PagedPlaylistTracksResult {
        val offset = (page - 1) * pageSize
        val responseText = runCatching {
            httpGet("https://api.deezer.com/playlist/$playlistId/tracks?index=$offset&limit=$pageSize")
        }.getOrDefault("")
        if (responseText.isBlank()) {
            return PagedPlaylistTracksResult(emptyList(), null, false, 0)
        }
        val root = jsonParser.parseToJsonElement(responseText).objOrNull()
            ?: return PagedPlaylistTracksResult(emptyList(), null, false, 0)
        val dataArray = root["data"].arrOrNull()
            ?: return PagedPlaylistTracksResult(emptyList(), null, false, 0)
        val reportedTotal = root["total"].intValOrNull() ?: 0
        val hasNextField = root["next"].strOrNull()?.isNotBlank() == true

        val rawTracks = deduplicatePlaylistTracks(
            dataArray.mapNotNull { elem ->
                val obj = elem.objOrNull() ?: return@mapNotNull null
                val id = obj["id"].longValOrNull()?.toString() ?: obj["id"].strOrNull() ?: return@mapNotNull null
                val title = obj["title"].strOrNull() ?: obj["title_short"].strOrNull() ?: return@mapNotNull null
                val durationSec = obj["duration"].longValOrNull() ?: 180L
                val artist = obj["artist"].objOrNull()?.get("name").strOrNull() ?: "Unknown Artist"
                val albumObj = obj["album"].objOrNull()
                val albumName = albumObj?.get("title").strOrNull() ?: "Single"
                val coverUrl = albumObj?.get("cover_xl").strOrNull()
                    ?: albumObj?.get("cover_big").strOrNull()
                    ?: albumObj?.get("cover_medium").strOrNull()
                    ?: return@mapNotNull null

                SongItem(
                    id = "deezer_$id",
                    title = title,
                    artist = artist,
                    durationMs = durationSec * 1000L,
                    artworkUrl = coverUrl,
                    // Full-length stream resolves lazily on playback so playlist paging stays fast on slow networks
                    streamUrl = "",
                    albumName = albumName,
                    moodCategory = category
                ).also {
                    songMetadataCache[it.id] = it
                }
            }
        )

        val hasMore = rawTracks.isNotEmpty() && (hasNextField || (reportedTotal > offset + rawTracks.size))
        return PagedPlaylistTracksResult(
            tracks = rawTracks,
            nextPage = if (hasMore) page + 1 else null,
            hasMore = hasMore,
            totalCount = reportedTotal.coerceAtLeast(offset + rawTracks.size)
        )
    }

    private suspend fun fetchDeezerPlaylistDetails(playlistId: String, category: String): List<SongItem> {
        val responseText = httpGet("https://api.deezer.com/playlist/$playlistId/tracks?limit=35")
        val root = jsonParser.parseToJsonElement(responseText).objOrNull() ?: return emptyList()
        val dataArray = root["data"].arrOrNull() ?: return emptyList()

        val rawTracks = dataArray.mapNotNull { elem ->
            val obj = elem.objOrNull() ?: return@mapNotNull null
            val id = obj["id"].longValOrNull()?.toString() ?: obj["id"].strOrNull() ?: return@mapNotNull null
            val title = obj["title"].strOrNull() ?: obj["title_short"].strOrNull() ?: return@mapNotNull null
            val previewUrl = obj["preview"].strOrNull() ?: ""
            val durationSec = obj["duration"].longValOrNull() ?: 180L
            val artist = obj["artist"].objOrNull()?.get("name").strOrNull() ?: "Unknown Artist"
            val albumObj = obj["album"].objOrNull()
            val albumName = albumObj?.get("title").strOrNull() ?: "Single"
            val coverUrl = albumObj?.get("cover_xl").strOrNull()
                ?: albumObj?.get("cover_big").strOrNull()
                ?: albumObj?.get("cover_medium").strOrNull()
                ?: return@mapNotNull null

            SongItem(
                id = "deezer_$id",
                title = title,
                artist = artist,
                durationMs = durationSec * 1000L,
                artworkUrl = coverUrl,
                // Never pre-fill 30-second Deezer preview URLs; resolve full-length stream on playback
                streamUrl = "",
                albumName = albumName,
                moodCategory = category
            ).also {
                songMetadataCache[it.id] = it
            }
        }

        // Enrich top tracks with full-length JioSaavn streams if available
        val firstArtist = rawTracks.firstOrNull()?.artist.orEmpty()
        if (firstArtist.isNotBlank()) {
            val saavnPool = runCatching { searchJioSaavnCatalog(firstArtist) }.getOrDefault(emptyList())
            if (saavnPool.isNotEmpty()) {
                return rawTracks.map { track ->
                    val matched = findBestMatchingTrack(track.title, track.artist, saavnPool)
                    if (matched != null && isFullLengthStreamUrl(matched.streamUrl)) {
                        track.copy(
                            streamUrl = matched.streamUrl,
                            durationMs = matched.durationMs.takeIf { it > 60_000L } ?: track.durationMs
                        ).also {
                            resolvedStreamCache[it.id] = matched.streamUrl
                            songMetadataCache[it.id] = it
                        }
                    } else {
                        track
                    }
                }
            }
        }

        return rawTracks
    }

    private suspend fun searchJioSaavnPlaylists(
        query: String,
        category: String,
        sourcePlatform: String
    ): List<PlaylistItem> {
        val encoded = urlEncode(query)
        val endpoints = listOf(
            "https://www.jiosaavn.com/api.php?__call=search.getPlaylistResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=12&p=1&q=$encoded",
            "https://saavn.sumit.co/api/search/playlists?query=$encoded&limit=10",
            "https://saavn.dev/api/search/playlists?query=$encoded&limit=10",
            "https://jiosaavn-api-privatecvc2.vercel.app/search/playlists?query=$encoded&limit=10"
        )

        for (endpoint in endpoints) {
            val playlists = runCatching {
                val text = httpGet(endpoint)
                val root = jsonParser.parseToJsonElement(text).objOrNull() ?: return@runCatching emptyList()
                val results = root["results"].arrOrNull()
                    ?: root["data"].objOrNull()?.get("results").arrOrNull()
                    ?: return@runCatching emptyList()

                results.mapNotNull { elem ->
                    val obj = elem.objOrNull() ?: return@mapNotNull null
                    val id = obj["id"].strOrNull() ?: return@mapNotNull null
                    val title = decodeHtmlEntities(
                        obj["title"].strOrNull() ?: obj["name"].strOrNull() ?: return@mapNotNull null
                    )
                    val moreInfo = obj["more_info"].objOrNull()
                    val songCount = obj["songCount"].intValOrNull()
                        ?: obj["count"].intValOrNull()
                        ?: moreInfo?.get("song_count")?.strOrNull()?.toIntOrNull()
                        ?: moreInfo?.get("song_count")?.intValOrNull()
                        ?: 25
                    val language = (
                        obj["language"].strOrNull()
                            ?: moreInfo?.get("language")?.strOrNull()
                            ?: "Editorial"
                        ).replaceFirstChar { it.uppercase() }

                    val rawArtwork = obj["image"].strOrNull()
                        ?.replace("150x150", "500x500")
                        ?.replace("50x50", "500x500")
                        ?: obj["image"].arrOrNull()
                            ?.mapNotNull { it.objOrNull() }
                            ?.lastOrNull()
                            ?.let { img -> img["url"].strOrNull() ?: img["link"].strOrNull() }

                    val artworkUrl = rawArtwork
                        ?.replace("http://", "https://")
                        ?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null

                    val resolvedCategory = when {
                        category in setOf("India Hits", "Bollywood", "Punjabi & Hip-Hop", "South & Sufi") -> category
                        title.contains("viral", ignoreCase = true) -> "Viral"
                        title.contains("new", ignoreCase = true) || title.contains("fresh", ignoreCase = true) -> "New Releases"
                        title.contains("chill", ignoreCase = true) || title.contains("focus", ignoreCase = true) || title.contains("workout", ignoreCase = true) -> "Moods"
                        title.contains("pop", ignoreCase = true) || title.contains("hip", ignoreCase = true) || title.contains("electronic", ignoreCase = true) || title.contains("rock", ignoreCase = true) -> "Genres"
                        else -> category
                    }

                    PlaylistItem(
                        id = "saavn_pl_$id",
                        title = title,
                        subtitle = "$language • $songCount songs",
                        artworkUrl = artworkUrl,
                        category = resolvedCategory,
                        sourcePlatform = sourcePlatform.ifBlank { "JioSaavn" },
                        remotePlaylistId = id,
                        searchFallbackQuery = title,
                        trackCount = songCount
                    )
                }
            }.getOrDefault(emptyList())

            if (playlists.isNotEmpty()) return playlists.distinctBy { canonicalSongTitle(it.title) }
        }
        return emptyList()
    }

    private suspend fun fetchJioSaavnPlaylistDetailsPage(
        cacheOwnerId: String,
        playlistId: String,
        category: String,
        page: Int,
        pageSize: Int
    ): PagedPlaylistTracksResult {
        playlistFullSnapshotCache[cacheOwnerId]?.let { cached ->
            val fromIndex = (page - 1) * pageSize
            if (fromIndex >= cached.size) {
                return PagedPlaylistTracksResult(emptyList(), null, false, cached.size)
            }
            val toIndex = (fromIndex + pageSize).coerceAtMost(cached.size)
            val slice = cached.subList(fromIndex, toIndex).toList()
            val hasMore = toIndex < cached.size
            return PagedPlaylistTracksResult(
                tracks = slice,
                nextPage = if (hasMore) page + 1 else null,
                hasMore = hasMore,
                totalCount = cached.size
            )
        }

        val endpoints = listOf(
            "https://www.jiosaavn.com/api.php?__call=playlist.getDetails&_format=json&_marker=0&api_version=4&ctx=web6dot0&listid=$playlistId&p=$page&n=$pageSize",
            "https://saavn.sumit.co/api/playlists?id=$playlistId&page=$page&limit=$pageSize",
            "https://saavn.dev/api/playlists?id=$playlistId&page=$page&limit=$pageSize",
            "https://jiosaavn-api-privatecvc2.vercel.app/playlists?id=$playlistId"
        )

        for (endpoint in endpoints) {
            val songs = runCatching {
                val text = httpGet(endpoint)
                val root = jsonParser.parseToJsonElement(text).objOrNull() ?: return@runCatching emptyList()
                val songsArray = root["list"].arrOrNull()
                    ?: root["songs"].arrOrNull()
                    ?: root["data"].objOrNull()?.get("songs").arrOrNull()
                    ?: return@runCatching emptyList()

                deduplicatePlaylistTracks(
                    songsArray.mapNotNull { elem ->
                        parseJioSaavnSongObject(elem.objOrNull(), category)
                    }
                )
            }.getOrDefault(emptyList())

            if (songs.isNotEmpty()) {
                // If endpoint ignored p/n and returned the entire playlist (> pageSize), cache full snapshot & slice
                return if (songs.size > pageSize) {
                    playlistFullSnapshotCache[cacheOwnerId] = songs
                    playlistTracksCache[cacheOwnerId] = songs
                    val fromIndex = (page - 1) * pageSize
                    val toIndex = (fromIndex + pageSize).coerceAtMost(songs.size)
                    val slice = if (fromIndex < songs.size) songs.subList(fromIndex, toIndex).toList() else emptyList()
                    val hasMore = toIndex < songs.size
                    PagedPlaylistTracksResult(
                        tracks = slice,
                        nextPage = if (hasMore) page + 1 else null,
                        hasMore = hasMore,
                        totalCount = songs.size
                    )
                } else {
                    val hasMore = songs.size >= pageSize
                    PagedPlaylistTracksResult(
                        tracks = songs,
                        nextPage = if (hasMore) page + 1 else null,
                        hasMore = hasMore,
                        totalCount = page * songs.size
                    )
                }
            }
        }
        return PagedPlaylistTracksResult(emptyList(), null, false, 0)
    }

    private suspend fun fetchJioSaavnPlaylistDetails(playlistId: String, category: String): List<SongItem> {
        val endpoints = listOf(
            "https://www.jiosaavn.com/api.php?__call=playlist.getDetails&_format=json&_marker=0&api_version=4&ctx=web6dot0&listid=$playlistId",
            "https://saavn.sumit.co/api/playlists?id=$playlistId&limit=40",
            "https://saavn.dev/api/playlists?id=$playlistId&limit=40",
            "https://jiosaavn-api-privatecvc2.vercel.app/playlists?id=$playlistId"
        )

        for (endpoint in endpoints) {
            val songs = runCatching {
                val text = httpGet(endpoint)
                val root = jsonParser.parseToJsonElement(text).objOrNull() ?: return@runCatching emptyList()
                val songsArray = root["list"].arrOrNull()
                    ?: root["songs"].arrOrNull()
                    ?: root["data"].objOrNull()?.get("songs").arrOrNull()
                    ?: return@runCatching emptyList()

                songsArray.mapNotNull { elem ->
                    parseJioSaavnSongObject(elem.objOrNull(), category)
                }
            }.getOrDefault(emptyList())

            if (songs.isNotEmpty()) return deduplicatePlaylistTracks(songs)
        }
        return emptyList()
    }

    private suspend fun searchInnerTubePlaylists(
        query: String,
        category: String,
        sourcePlatform: String
    ): List<PlaylistItem> {
        val payload = buildJsonObject {
            put("query", query)
            // Community & Official Playlists filter on YouTube Music WEB_REMIX
            put("params", "EgWKAQIoAWoKEAkQBRAKEAMQBA==")
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20241118.01.00")
                    put("hl", "en")
                    put("gl", "US")
                }
            }
        }

        val responseText = httpPostJson(
            url = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false",
            jsonBody = payload.toString(),
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Origin" to "https://music.youtube.com",
                "Referer" to "https://music.youtube.com/",
                "X-YouTube-Client-Name" to "67",
                "X-YouTube-Client-Version" to "1.20241118.01.00"
            )
        )

        val rootElement = jsonParser.parseToJsonElement(responseText)
        val renderers = mutableListOf<JsonObject>()
        collectRenderersByKey(rootElement, "musicResponsiveListItemRenderer", renderers)

        return renderers.mapNotNull { item ->
            val browseId = item["navigationEndpoint"].objOrNull()
                ?.get("browseEndpoint").objOrNull()
                ?.get("browseId").strOrNull()
                ?: findFirstVideoId(item)
                ?: return@mapNotNull null

            val flexColumns = item["flexColumns"].arrOrNull() ?: return@mapNotNull null
            val titleRuns = flexColumns.getOrNull(0).objOrNull()
                ?.get("musicResponsiveListItemFlexColumnRenderer").objOrNull()
                ?.get("text").objOrNull()
                ?.get("runs").arrOrNull()

            val title = titleRuns?.getOrNull(0).objOrNull()?.get("text").strOrNull()
                ?: return@mapNotNull null

            val subtitleRuns = flexColumns.getOrNull(1).objOrNull()
                ?.get("musicResponsiveListItemFlexColumnRenderer").objOrNull()
                ?.get("text").objOrNull()
                ?.get("runs").arrOrNull()

            val subtitleParts = subtitleRuns?.mapNotNull { run ->
                run.objOrNull()?.get("text").strOrNull()
            }?.filter { it != " • " && it != "Playlist" } ?: emptyList()

            val subtitle = subtitleParts.joinToString(" • ").ifBlank { "$sourcePlatform Official Playlist" }

            val thumbnails = item["thumbnail"].objOrNull()
                ?.get("musicThumbnailRenderer").objOrNull()
                ?.get("thumbnail").objOrNull()
                ?.get("thumbnails").arrOrNull()

            val rawThumb = thumbnails?.lastOrNull().objOrNull()?.get("url").strOrNull()
                ?: return@mapNotNull null

            val highResThumb = rawThumb.replace(Regex("w\\d+-h\\d+"), "w600-h600")

            PlaylistItem(
                id = "ytm_pl_$browseId",
                title = title,
                subtitle = subtitle,
                artworkUrl = highResThumb,
                category = category,
                sourcePlatform = sourcePlatform,
                remotePlaylistId = browseId,
                searchFallbackQuery = title,
                trackCount = 30
            )
        }
    }

    private suspend fun fetchDeezerGlobalChartTracks(): List<SongItem> {
        val responseText = httpGet("https://api.deezer.com/chart/0/tracks?limit=25")
        val root = jsonParser.parseToJsonElement(responseText).objOrNull() ?: return emptyList()
        val dataArray = root["data"].arrOrNull() ?: return emptyList()

        return dataArray.mapNotNull { elem ->
            val obj = elem.objOrNull() ?: return@mapNotNull null
            val id = obj["id"].longValOrNull()?.toString() ?: obj["id"].strOrNull() ?: return@mapNotNull null
            val title = obj["title"].strOrNull() ?: obj["title_short"].strOrNull() ?: return@mapNotNull null
            val previewUrl = obj["preview"].strOrNull() ?: return@mapNotNull null
            val durationSec = obj["duration"].longValOrNull() ?: 180L
            val artist = obj["artist"].objOrNull()?.get("name").strOrNull() ?: "Unknown Artist"
            val albumObj = obj["album"].objOrNull()
            val albumName = albumObj?.get("title").strOrNull() ?: "Single"
            val coverUrl = albumObj?.get("cover_xl").strOrNull()
                ?: albumObj?.get("cover_big").strOrNull()
                ?: albumObj?.get("cover_medium").strOrNull()
                ?: return@mapNotNull null

            SongItem(
                id = "deezer_$id",
                title = title,
                artist = artist,
                durationMs = durationSec * 1000L,
                artworkUrl = coverUrl,
                // Do not store 30s preview URL; resolve full-length stream on playback
                streamUrl = "",
                albumName = albumName,
                moodCategory = "Top Hits"
            ).also {
                songMetadataCache[it.id] = it
            }
        }
    }

    private suspend fun fetchAppleMusicTopSongsRss(): List<SongItem> {
        val responseText = httpGet("https://itunes.apple.com/us/rss/topsongs/limit=20/json")
        val root = jsonParser.parseToJsonElement(responseText).objOrNull() ?: return emptyList()
        val entries = root["feed"].objOrNull()?.get("entry").arrOrNull() ?: return emptyList()

        return entries.mapNotNull { elem ->
            val obj = elem.objOrNull() ?: return@mapNotNull null
            val id = obj["id"].objOrNull()
                ?.get("attributes").objOrNull()
                ?.get("im:id").strOrNull()
                ?: return@mapNotNull null
            val title = obj["im:name"].objOrNull()?.get("label").strOrNull() ?: return@mapNotNull null
            val artist = obj["im:artist"].objOrNull()?.get("label").strOrNull() ?: "Unknown Artist"
            val album = obj["im:collection"].objOrNull()
                ?.get("im:name").objOrNull()
                ?.get("label").strOrNull()
                ?: "Apple Music Chart"

            val images = obj["im:image"].arrOrNull()
            val artwork = images?.lastOrNull().objOrNull()?.get("label").strOrNull()
                ?.replace("170x170bb", "600x600bb")
                ?: return@mapNotNull null

            val durationMs = 210_000L

            SongItem(
                id = "itunes_$id",
                title = title,
                artist = artist,
                durationMs = durationMs,
                artworkUrl = artwork,
                // Never pre-fill 30s Apple preview URL; resolve full-length stream on playback
                streamUrl = "",
                albumName = album,
                moodCategory = "Top Hits"
            ).also {
                songMetadataCache[it.id] = it
            }
        }
    }

    /**
     * Filters local/curated playlists by matching query tokens (e.g., "indie india" matches "Indian Indie Now").
     */
    fun filterMatchingLocalPlaylists(
        query: String,
        localPlaylists: List<PlaylistItem>
    ): List<PlaylistItem> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return emptyList()

        val queryTokens = trimmed
            .split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }

        if (queryTokens.isEmpty()) return emptyList()

        val builtInFallbackCatalog = listOf(
            PlaylistItem(
                id = "live_pl_indian_indie",
                title = "Indian Indie Now",
                subtitle = "Anuv Jain, Prateek Kuhad, Aditya Rikhari & Mitraz",
                artworkUrl = "https://c.saavncdn.com/editorial/IndieIndia_20230510113504_500x500.jpg",
                category = "India Hits",
                sourcePlatform = "Spotify",
                remotePlaylistId = "37i9dQZF1DX5q67ZpWyRrZ",
                searchFallbackQuery = "Anuv Jain Prateek Kuhad Aditya Rikhari Indian Indie India",
                trackCount = 25
            ),
            PlaylistItem(
                id = "live_pl_indie_india_essentials",
                title = "Indie India Essentials",
                subtitle = "The Local Train, When Chai Met Toast, Zaeden & Twin Strings",
                artworkUrl = "https://c.saavncdn.com/editorial/IndianIndieHits_20230914102211_500x500.jpg",
                category = "India Hits",
                sourcePlatform = "Spotify",
                searchFallbackQuery = "Indie India The Local Train When Chai Met Toast Prateek Kuhad Anuv Jain",
                trackCount = 30
            ),
            PlaylistItem(
                id = "live_pl_insta_viral_reels",
                title = "Instagram Viral Reels Hits",
                subtitle = "Trending Instagram Reels & Viral Audio • 40 songs",
                artworkUrl = "https://c.saavncdn.com/editorial/Top50India_20231012110000_500x500.jpg",
                category = "Viral",
                sourcePlatform = "Spotify",
                searchFallbackQuery = "Instagram Viral Reels Trending Hindi English Hits",
                trackCount = 40
            ),
            PlaylistItem(
                id = "live_pl_viral_hits_india",
                title = "Viral 50 — India",
                subtitle = "Most Shared & Viral Tracks Across India • 50 songs",
                artworkUrl = "https://c.saavncdn.com/editorial/BollywoodHotHits_20230801120000_500x500.jpg",
                category = "Viral",
                sourcePlatform = "Spotify",
                searchFallbackQuery = "Viral India Trending Hindi Punjabi Songs",
                trackCount = 50
            ),
            PlaylistItem(
                id = "live_pl_top_50_india",
                title = "Top 50 — India",
                subtitle = "Daily India Chart • 50 songs",
                artworkUrl = "https://c.saavncdn.com/editorial/Top50India_20231012110000_500x500.jpg",
                category = "India Hits",
                sourcePlatform = "Spotify",
                searchFallbackQuery = "Top 50 India Hindi Trending Hits Arijit Singh",
                trackCount = 50
            ),
            PlaylistItem(
                id = "live_pl_bollywood_hot_hits",
                title = "Bollywood Hot Hits",
                subtitle = "Biggest Hindi Blockbusters • Arijit, Pritam & Shreya",
                artworkUrl = "https://c.saavncdn.com/editorial/BollywoodHotHits_20230801120000_500x500.jpg",
                category = "Bollywood",
                sourcePlatform = "Spotify",
                searchFallbackQuery = "Bollywood Hot Hits Latest Hindi Songs",
                trackCount = 30
            ),
            PlaylistItem(
                id = "live_pl_punjabi_101",
                title = "Punjabi 101 & Pop",
                subtitle = "Diljit Dosanjh, Karan Aujla, AP Dhillon & Shubh",
                artworkUrl = "https://c.saavncdn.com/editorial/Punjabi101_20230715100000_500x500.jpg",
                category = "Punjabi & Hip-Hop",
                sourcePlatform = "Spotify",
                searchFallbackQuery = "Punjabi Hits Diljit Dosanjh Karan Aujla AP Dhillon Shubh",
                trackCount = 30
            ),
            PlaylistItem(
                id = "live_pl_desi_hip_hop",
                title = "Desi Hip-Hop Hustle",
                subtitle = "DIVINE, KR\$NA, Seedhe Maut, Badshah & Raftaar",
                artworkUrl = "https://c.saavncdn.com/editorial/DesiHipHop_20230620100000_500x500.jpg",
                category = "Punjabi & Hip-Hop",
                sourcePlatform = "Spotify",
                searchFallbackQuery = "Desi Hip Hop Divine Kr\$na Seedhe Maut Raftaar",
                trackCount = 25
            ),
            PlaylistItem(
                id = "live_pl_workout_beast_mode",
                title = "Beast Mode Workout",
                subtitle = "High-Energy Gym, Phonk & Hip-Hop Anthems • 35 songs",
                artworkUrl = "https://c.saavncdn.com/editorial/DesiHipHop_20230620100000_500x500.jpg",
                category = "Moods",
                sourcePlatform = "Spotify",
                searchFallbackQuery = "Workout Gym Beast Mode Hip Hop Electronic Hits",
                trackCount = 35
            ),
            PlaylistItem(
                id = "live_pl_lofi_study_chill",
                title = "Lo-Fi Beats & Chill Study",
                subtitle = "Relaxing Lo-Fi Hip-Hop, Ambient & Late Night Focus",
                artworkUrl = "https://c.saavncdn.com/editorial/IndieIndia_20230510113504_500x500.jpg",
                category = "Moods",
                sourcePlatform = "Spotify",
                searchFallbackQuery = "Lo-Fi Chill Study Beats Late Night Vibes",
                trackCount = 30
            )
        )

        val candidatePool = (localPlaylists + builtInFallbackCatalog)
            .distinctBy { canonicalSongTitle(it.title) }

        return candidatePool
            .mapNotNull { playlist ->
                val searchableText = "${playlist.title} ${playlist.subtitle} ${playlist.category} ${playlist.searchFallbackQuery}".lowercase()
                val titleLower = playlist.title.lowercase()
                var score = 0
                if (titleLower.contains(trimmed)) score += 100
                val matchedTokens = queryTokens.count { token ->
                    searchableText.contains(token) ||
                        (token == "india" && searchableText.contains("indian")) ||
                        (token == "indian" && searchableText.contains("india")) ||
                        (token == "instagram" && searchableText.contains("insta")) ||
                        (token == "insta" && searchableText.contains("instagram"))
                }
                if (matchedTokens == 0) return@mapNotNull null
                if (matchedTokens == queryTokens.size) score += 60
                queryTokens.forEach { token ->
                    if (titleLower.contains(token) ||
                        (token == "india" && titleLower.contains("indian")) ||
                        (token == "indian" && titleLower.contains("india")) ||
                        (token == "instagram" && titleLower.contains("insta")) ||
                        (token == "insta" && titleLower.contains("instagram"))
                    ) {
                        score += 25
                    } else if (searchableText.contains(token)) {
                        score += 10
                    }
                }
                playlist to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * Keyless public Spotify playlist discovery:
     * Searches public web indices for `open.spotify.com/playlist/{id}` matching [query]
     * and hydrates discovered playlists via Spotify's keyless `/oembed` endpoint.
     */
    private suspend fun searchPublicSpotifyPlaylists(query: String): List<PlaylistItem> = coroutineScope {
        val searchUrls = listOf(
            "https://html.duckduckgo.com/html/?q=${urlEncode("site:open.spotify.com/playlist $query")}",
            "https://lite.duckduckgo.com/lite/?q=${urlEncode("site:open.spotify.com/playlist $query")}"
        )

        val discoveredIds = LinkedHashSet<String>()
        val playlistIdRegex = Regex("""open\.spotify\.com(?:/|%2F)playlist(?:/|%2F)([a-zA-Z0-9]{22})""")

        for (url in searchUrls) {
            if (discoveredIds.size >= 4) break
            val html = runCatching { httpGet(url) }.getOrDefault("")
            if (html.isNotBlank()) {
                val decodedHtml = runCatching { URLDecoder.decode(html, "UTF-8") }.getOrDefault(html)
                for (match in playlistIdRegex.findAll(decodedHtml)) {
                    val pid = match.groupValues[1].trim()
                    if (pid.length == 22) {
                        discoveredIds.add(pid)
                        if (discoveredIds.size >= 4) break
                    }
                }
            }
        }

        if (discoveredIds.isEmpty()) return@coroutineScope emptyList()

        discoveredIds.take(4).map { playlistId ->
            async {
                runCatching {
                    val canonicalUrl = "https://open.spotify.com/playlist/$playlistId"
                    val oembedText = httpGet("https://open.spotify.com/oembed?url=${urlEncode(canonicalUrl)}")
                    val obj = jsonParser.parseToJsonElement(oembedText).objOrNull() ?: return@runCatching null
                    val rawTitle = decodeHtmlEntities(obj["title"].strOrNull().orEmpty())
                        .removeSuffix(" | Spotify")
                        .removePrefix("Spotify – ")
                        .trim()
                    val thumb = obj["thumbnail_url"].strOrNull().orEmpty()
                    if (rawTitle.isBlank() || thumb.isBlank()) return@runCatching null

                    PlaylistItem(
                        id = "imported_spotify_$playlistId",
                        title = rawTitle,
                        subtitle = "Spotify Playlist • Tap to stream",
                        artworkUrl = thumb,
                        category = "Playlist",
                        sourcePlatform = "Spotify",
                        remotePlaylistId = playlistId,
                        searchFallbackQuery = "$rawTitle $query",
                        trackCount = 30
                    )
                }.getOrNull()
            }
        }.mapNotNull { it.await() }
    }

    /**
     * Standalone playlist search across Spotify (keyless discovery + editorial), JioSaavn, YouTube Music, and Deezer.
     * Interleaves results across platforms so Spotify, JioSaavn, YouTube Music, and Deezer are all represented.
     */
    suspend fun searchPlaylists(
        query: String,
        localPlaylists: List<PlaylistItem> = emptyList()
    ): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val localMatches = filterMatchingLocalPlaylists(trimmed, localPlaylists)
        val (remoteSpotify, remoteSaavn, remoteYtm, remoteDeezer) = coroutineScope {
            val spotifyDef = async {
                runCatching { searchPublicSpotifyPlaylists(trimmed) }.getOrDefault(emptyList())
            }
            val saavnDef = async {
                runCatching { searchJioSaavnPlaylists(trimmed, "Playlist", "JioSaavn") }.getOrDefault(emptyList())
            }
            val ytmDef = async {
                runCatching { searchInnerTubePlaylists(trimmed, "Playlist", "YouTube Music") }.getOrDefault(emptyList())
            }
            val deezerDef = async {
                runCatching { searchDeezerPlaylists(trimmed) }.getOrDefault(emptyList())
            }
            listOf(spotifyDef.await(), saavnDef.await(), ytmDef.await(), deezerDef.await())
        }

        val allSpotify = (remoteSpotify + localMatches.filter { it.sourcePlatform.equals("Spotify", ignoreCase = true) })
            .distinctBy { canonicalSongTitle(it.title) }
        val otherLocal = localMatches.filterNot { it.sourcePlatform.equals("Spotify", ignoreCase = true) }

        val interleaved = buildList {
            val maxLen = maxOf(allSpotify.size, remoteSaavn.size, remoteYtm.size, remoteDeezer.size, otherLocal.size)
            for (i in 0 until maxLen) {
                if (i < allSpotify.size) add(allSpotify[i])
                if (i < remoteSaavn.size) add(remoteSaavn[i])
                if (i < remoteYtm.size) add(remoteYtm[i])
                if (i < remoteDeezer.size) add(remoteDeezer[i])
                if (i < otherLocal.size) add(otherLocal[i])
            }
        }

        interleaved
            .filter { it.title.isNotBlank() && it.artworkUrl.isNotBlank() }
            .distinctBy { canonicalSongTitle(it.title) }
            .take(16)
    }

    private suspend fun searchDeezerPlaylists(query: String): List<PlaylistItem> {
        val encoded = urlEncode(query)
        val responseText = httpGet("https://api.deezer.com/search/playlist?q=$encoded&limit=8")
        val root = jsonParser.parseToJsonElement(responseText).objOrNull() ?: return emptyList()
        val dataArray = root["data"].arrOrNull() ?: return emptyList()

        return dataArray.mapNotNull { elem ->
            val obj = elem.objOrNull() ?: return@mapNotNull null
            val id = obj["id"].longValOrNull()?.toString() ?: obj["id"].strOrNull() ?: return@mapNotNull null
            val title = obj["title"].strOrNull() ?: return@mapNotNull null
            val artwork = obj["picture_xl"].strOrNull()
                ?: obj["picture_big"].strOrNull()
                ?: obj["picture_medium"].strOrNull()
                ?: return@mapNotNull null
            val trackCount = obj["nb_tracks"].intValOrNull() ?: 25
            val creator = obj["user"].objOrNull()?.get("name").strOrNull() ?: "Deezer"

            PlaylistItem(
                id = "deezer_pl_$id",
                title = title,
                subtitle = "$creator • $trackCount songs",
                artworkUrl = artwork,
                category = "Playlist",
                sourcePlatform = "Deezer",
                remotePlaylistId = id,
                searchFallbackQuery = title,
                trackCount = trackCount
            )
        }
    }

    private fun resolveUserCountryCode(): String {
        val localeCountry = runCatching { java.util.Locale.getDefault().country.uppercase() }.getOrDefault("")
        return if (localeCountry.length == 2 && localeCountry != "US") localeCountry else "IN"
    }

    private fun stripSearchConnectorWords(rawQuery: String): String {
        return rawQuery
            .replace(Regex("\\b(by|song|songs|track|play|listen to)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private data class ParsedSongSearchQuery(
        val rawQuery: String,
        val cleanApiQuery: String,
        val explicitTitlePart: String,
        val explicitArtistPart: String
    )

    private fun parseConversationalSearchQuery(rawQuery: String): ParsedSongSearchQuery {
        val trimmed = rawQuery.trim()
        val byMatch = Regex("^(.+?)\\s+by\\s+(.+)$", RegexOption.IGNORE_CASE).find(trimmed)
        if (byMatch != null) {
            val t = stripSearchConnectorWords(byMatch.groupValues[1]).ifBlank { byMatch.groupValues[1].trim() }
            val a = stripSearchConnectorWords(byMatch.groupValues[2]).ifBlank { byMatch.groupValues[2].trim() }
            val combined = "$t $a".replace(Regex("\\s+"), " ").trim()
            return ParsedSongSearchQuery(
                rawQuery = trimmed,
                cleanApiQuery = combined.ifBlank { trimmed },
                explicitTitlePart = t,
                explicitArtistPart = a
            )
        }

        val dashMatch = Regex("^(.+?)\\s+[-–—|]\\s+(.+)$").find(trimmed)
        if (dashMatch != null) {
            val t = stripSearchConnectorWords(dashMatch.groupValues[1]).ifBlank { dashMatch.groupValues[1].trim() }
            val a = stripSearchConnectorWords(dashMatch.groupValues[2]).ifBlank { dashMatch.groupValues[2].trim() }
            val combined = "$t $a".replace(Regex("\\s+"), " ").trim()
            return ParsedSongSearchQuery(
                rawQuery = trimmed,
                cleanApiQuery = combined.ifBlank { trimmed },
                explicitTitlePart = t,
                explicitArtistPart = a
            )
        }

        val cleaned = stripSearchConnectorWords(trimmed).ifBlank { trimmed }
        return ParsedSongSearchQuery(
            rawQuery = trimmed,
            cleanApiQuery = cleaned,
            explicitTitlePart = "",
            explicitArtistPart = ""
        )
    }

    /**
     * Live search across Official JioSaavn (full-length 320kbps MP4/AAC), YouTube Music InnerTube,
     * YouTube Video Music Catalog (regional/indie releases), Apple iTunes (country storefront), and Deezer.
     * Ranks results by exact song+artist match, country popularity rank, cross-catalog consensus, and user taste.
     */
    fun search(query: String): Flow<List<SongItem>> = flow {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            if (cachedGlobalChartSongs.isNotEmpty()) {
                emit(cachedGlobalChartSongs)
            } else {
                val fresh = runCatching { fetchRealHomeDiscoveryData().globalChartSongs }.getOrDefault(emptyList())
                emit(fresh)
            }
            return@flow
        }

        val parsedQuery = parseConversationalSearchQuery(trimmed)
        val apiQuery = parsedQuery.cleanApiQuery
        val userCountry = resolveUserCountryCode()

        val combinedResults = coroutineScope {
            val saavnDeferred = async {
                runCatching { searchJioSaavnCatalog(apiQuery, page = 1, limit = 30) }.getOrDefault(emptyList())
            }
            val saavnTitleOnlyDeferred = async {
                if (parsedQuery.explicitTitlePart.isNotBlank() &&
                    parsedQuery.explicitArtistPart.isNotBlank() &&
                    !parsedQuery.explicitTitlePart.equals(apiQuery, ignoreCase = true)
                ) {
                    val reqArtistTokens = lyricsArtistTokens(parsedQuery.explicitArtistPart)
                    runCatching {
                        searchJioSaavnCatalog(parsedQuery.explicitTitlePart, page = 1, limit = 20)
                            .filter { cand ->
                                val candTokens = lyricsArtistTokens("${cand.artist} ${cand.title}")
                                reqArtistTokens.isNotEmpty() && reqArtistTokens.any { req ->
                                    candTokens.any { c -> artistTokensMatch(req, c) }
                                }
                            }
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }
            val ytMusicDeferred = async {
                runCatching { searchInnerTubeWebRemix(apiQuery, countryCode = userCountry) }.getOrDefault(emptyList())
            }
            val ytVideoDeferred = async {
                runCatching { searchYouTubeVideoCatalog(apiQuery, countryCode = userCountry) }.getOrDefault(emptyList())
            }
            val itunesDeferred = async {
                runCatching { searchITunesCatalog(apiQuery, countryCode = userCountry, limit = 25) }.getOrDefault(emptyList())
            }
            val deezerDeferred = async {
                runCatching { searchDeezerCatalog(apiQuery) }.getOrDefault(emptyList())
            }

            val saavnSongs = (saavnDeferred.await() + saavnTitleOnlyDeferred.await()).distinctBy { it.id }
            val ytSongs = (ytMusicDeferred.await() + ytVideoDeferred.await()).distinctBy { it.id }
            val itunesSongs = itunesDeferred.await()
            val deezerSongs = deezerDeferred.await()

            // Track catalog rank & cross-platform popularity consensus for each canonical (title, primaryArtist)
            val popularityBonusMap = HashMap<String, Int>()
            fun canonicalSongKey(song: SongItem): String {
                val t = canonicalSongTitle(song.title).ifBlank { normalizeToken(song.title) }
                val a = normalizeToken(extractPrimaryArtistForLyrics(song.artist)).ifBlank { normalizeToken(song.artist) }
                return "$t|$a"
            }
            fun registerCatalogRanking(list: List<SongItem>, topWeight: Int, stepPenalty: Int) {
                list.take(15).forEachIndexed { idx, item ->
                    val key = canonicalSongKey(item)
                    val rankBonus = (topWeight - idx * stepPenalty).coerceAtLeast(15)
                    popularityBonusMap[key] = (popularityBonusMap[key] ?: 0) + rankBonus
                }
            }

            // JioSaavn & YouTube Music IN carry highest weight for country-famous & popular songs
            registerCatalogRanking(saavnSongs, topWeight = 155, stepPenalty = 9)
            registerCatalogRanking(ytSongs, topWeight = 150, stepPenalty = 8)
            registerCatalogRanking(itunesSongs, topWeight = 115, stepPenalty = 7)
            registerCatalogRanking(deezerSongs, topWeight = 95, stepPenalty = 6)

            // Only attach a JioSaavn stream to synthetic iTunes/Deezer items when title + artist + duration STRICTLY match.
            // Never overwrite a YouTube item's streamUrl so YouTube tracks always play their authentic videoId!
            val enrichSyntheticWithVerifiedSaavn = { song: SongItem ->
                val matched = findBestMatchingTrack(
                    targetTitle = song.title,
                    targetArtist = song.artist,
                    candidates = saavnSongs,
                    targetDurationMs = song.durationMs,
                    requireArtistMatch = true
                )
                if (matched != null && isFullLengthStreamUrl(matched.streamUrl)) {
                    song.copy(
                        streamUrl = matched.streamUrl,
                        durationMs = matched.durationMs.takeIf { it > 60_000L } ?: song.durationMs
                    ).also {
                        songMetadataCache[it.id] = it
                        resolvedStreamCache[it.id] = matched.streamUrl
                    }
                } else {
                    song.also { songMetadataCache[it.id] = it }
                }
            }

            ytSongs.forEach { songMetadataCache[it.id] = it }
            val enrichedItunes = itunesSongs.map(enrichSyntheticWithVerifiedSaavn)
            val enrichedDeezer = deezerSongs.map(enrichSyntheticWithVerifiedSaavn)

            val allCandidates = (saavnSongs + ytSongs + enrichedItunes + enrichedDeezer)
                .distinctBy { "${canonicalSongTitle(it.title).ifBlank { normalizeToken(it.title) }}|${normalizeToken(it.artist)}" }

            val countryFamousArtistsLower = (
                indianIndieScene + punjabiPopScene + desiHipHopScene +
                    bollywoodModernScene + southSuperhitsScene + sufiSoulScene
                ).map { it.lowercase() }.toSet()

            val scored = allCandidates.map { song ->
                val baseScore = scoreSongRelevance(song, trimmed)
                val popBonus = if (baseScore > 0) (popularityBonusMap[canonicalSongKey(song)] ?: 0) else 0
                val artistLower = song.artist.lowercase()
                val countryFamousBonus = if (baseScore > 0 && countryFamousArtistsLower.any { famous ->
                        artistLower.contains(famous)
                    }
                ) 85 else 0
                song to (baseScore + popBonus + countryFamousBonus)
            }
            val positiveMatches = scored.filter { (_, score) -> score > 0 }
            (if (positiveMatches.isNotEmpty()) positiveMatches else scored)
                .sortedByDescending { (_, score) -> score }
                .map { (song, _) -> song }
        }

        combinedResults.forEach { songMetadataCache[it.id] = it }
        emit(combinedResults)
    }.flowOn(Dispatchers.IO)

    internal fun scoreSongRelevance(song: SongItem, rawQuery: String): Int {
        val parsed = parseConversationalSearchQuery(rawQuery)
        val normCleanQuery = normalizeToken(parsed.cleanApiQuery)
        if (normCleanQuery.isEmpty()) return 1

        val normTitle = normalizeToken(song.title)
        val canonTitle = canonicalSongTitle(song.title).ifBlank { normTitle }
        val normArtist = normalizeToken(song.artist)
        val normAlbum = normalizeToken(song.albumName)
        val normCombined = "$normTitle $normArtist $normAlbum"

        val queryTokens = normCleanQuery.split(" ").filter { it.isNotEmpty() }
        val artistCandTokens = lyricsArtistTokens("${song.artist} ${song.title}")

        var score = 0

        // 1. Explicit "Title by Artist" or "Title - Artist" structure
        if (parsed.explicitTitlePart.isNotBlank() && parsed.explicitArtistPart.isNotBlank()) {
            val targetTitle = canonicalSongTitle(parsed.explicitTitlePart).ifBlank { normalizeToken(parsed.explicitTitlePart) }
            val targetArtistTokens = lyricsArtistTokens(parsed.explicitArtistPart)
            val titleExact = canonTitle == targetTitle || normTitle == targetTitle
            val titlePartial = canonTitle.contains(targetTitle) || targetTitle.contains(canonTitle)
            val artistMatched = targetArtistTokens.isNotEmpty() && targetArtistTokens.any { req ->
                artistCandTokens.any { cand -> artistTokensMatch(req, cand) } || normArtist.contains(req)
            }

            if (titleExact && artistMatched) {
                score += 750
            } else if (titlePartial && artistMatched) {
                score += 520
            } else if (titleExact && !artistMatched) {
                // User explicitly asked for a specific artist, so demote same-title songs by other artists
                score += 35
            }
        }

        // 2. Standard title & artist matching
        if (canonTitle == normCleanQuery || normTitle == normCleanQuery) {
            score += 240
        } else if (canonTitle.startsWith(normCleanQuery) || normTitle.startsWith(normCleanQuery)) {
            score += 145
        } else if (normTitle.contains(normCleanQuery)) {
            score += 105
        }

        if (normArtist == normCleanQuery) {
            score += 140
        } else if (normArtist.contains(normCleanQuery)) {
            score += 85
        }

        // 3. Multi-word query where some tokens match the song title and other tokens match the artist (e.g. "katana panjeta")
        val titleMatchedTokens = queryTokens.filter { tok ->
            canonTitle.contains(tok) || normTitle.contains(tok)
        }
        val artistMatchedTokens = queryTokens.filter { tok ->
            normArtist.contains(tok) || artistCandTokens.any { cand -> artistTokensMatch(tok, cand) }
        }
        if (queryTokens.size >= 2) {
            if (titleMatchedTokens.isNotEmpty() && artistMatchedTokens.isNotEmpty()) {
                val distinctCovered = (titleMatchedTokens + artistMatchedTokens).toSet()
                if (distinctCovered.size == queryTokens.size) {
                    score += 620
                    if (canonTitle == titleMatchedTokens.joinToString(" ")) {
                        score += 160
                    }
                } else {
                    score += 240
                }
            } else if (titleMatchedTokens.size < queryTokens.size && artistMatchedTokens.isEmpty()) {
                // Only matched part of the query (e.g. matched "katana" but missed "panjeta" completely)
                score -= 120
            }
        }

        val matchedTokens = queryTokens.count { token ->
            normCombined.contains(token) || artistCandTokens.any { cand -> artistTokensMatch(token, cand) }
        }
        if (queryTokens.isNotEmpty() && matchedTokens == queryTokens.size) {
            score += 110
        } else {
            score += matchedTokens * 20
        }

        if (score <= 0) return 0

        // Penalize karaoke / instrumental / 8d / ringtone covers when user didn't ask for them
        val lowerRawTitle = song.title.lowercase()
        val lowerRawQuery = rawQuery.lowercase()
        if ((lowerRawTitle.contains("karaoke") || lowerRawTitle.contains("ringtone") || lowerRawTitle.contains("instrumental")) &&
            !lowerRawQuery.contains("karaoke") && !lowerRawQuery.contains("instrumental")
        ) {
            score = (score - 140).coerceAtLeast(5)
        }

        if (isFullLengthStreamUrl(song.streamUrl)) {
            score += 30
        }
        if (song.id.startsWith("saavn_")) {
            score += 20
        }

        return score
    }

    private fun normalizeToken(text: String): String {
        return text.lowercase()
            .replace(Regex("\\(.*?\\)|\\[.*?]"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Strictly verifies that a candidate song matches BOTH the target song title AND the target artist
     * (plus duration proximity when available). Never matches unrelated artists who happen to have
     * a song with the same title.
     */
    private fun findBestMatchingTrack(
        targetTitle: String,
        targetArtist: String,
        candidates: List<SongItem>,
        targetDurationMs: Long = 0L,
        requireArtistMatch: Boolean = true
    ): SongItem? {
        if (candidates.isEmpty()) return null
        val cleanedTargetTitle = cleanTrackTitleForLyrics(targetTitle, targetArtist)
        val normTitle = canonicalSongTitle(cleanedTargetTitle).ifBlank { normalizeToken(cleanedTargetTitle) }
        if (normTitle.isEmpty()) return null

        val primaryArtist = extractPrimaryArtistForLyrics(targetArtist)
        val primaryArtistTokens = lyricsArtistTokens(primaryArtist)
        val allTargetArtistTokens = buildSet {
            addAll(primaryArtistTokens)
            extractAllArtistCandidatesForLyrics(targetTitle, targetArtist.ifBlank { primaryArtist })
                .forEach { addAll(lyricsArtistTokens(it)) }
        }

        if (requireArtistMatch && allTargetArtistTokens.isEmpty()) {
            return null
        }

        val scoredCandidates = candidates.mapNotNull { cand ->
            val candCleanedTitle = cleanTrackTitleForLyrics(cand.title, cand.artist)
            val cTitle = canonicalSongTitle(candCleanedTitle).ifBlank { normalizeToken(candCleanedTitle) }
            if (cTitle.isEmpty()) return@mapNotNull null

            val targetTitleWords = normTitle.split(" ").filter { it.length >= 2 }
            val candTitleWords = cTitle.split(" ").filter { it.length >= 2 }

            val exactTitleMatch = cTitle == normTitle
            val prefixOrTokenTitleMatch = !exactTitleMatch &&
                targetTitleWords.isNotEmpty() &&
                targetTitleWords.all { tw -> candTitleWords.any { cw -> cw == tw } } &&
                (cTitle.startsWith(normTitle) || normTitle.startsWith(cTitle))

            if (!exactTitleMatch && !prefixOrTokenTitleMatch) {
                return@mapNotNull null
            }

            // Duration check: reject if both durations are known (>40s) and differ by more than 20s
            if (targetDurationMs > 40_000L && cand.durationMs > 40_000L) {
                val diffMs = kotlin.math.abs(targetDurationMs - cand.durationMs)
                if (diffMs > 20_000L) return@mapNotNull null
            }

            val candArtistTokens = buildSet {
                addAll(lyricsArtistTokens(cand.artist))
                extractAllArtistCandidatesForLyrics(cand.title, cand.artist)
                    .forEach { addAll(lyricsArtistTokens(it)) }
            }

            if (allTargetArtistTokens.isNotEmpty()) {
                val primaryMatches = primaryArtistTokens.isNotEmpty() && primaryArtistTokens.any { req ->
                    candArtistTokens.any { cTok -> artistTokensMatch(req, cTok) }
                }
                val anyArtistMatches = allTargetArtistTokens.any { req ->
                    candArtistTokens.any { cTok -> artistTokensMatch(req, cTok) }
                }
                val matchedCount = allTargetArtistTokens.count { req ->
                    candArtistTokens.any { cTok -> artistTokensMatch(req, cTok) }
                }

                // If target specifies multiple artists (e.g. "Sumit Parta, Panjeta") or a specific artist,
                // require strong artist overlap
                if (!anyArtistMatches) return@mapNotNull null
                if (!primaryMatches && matchedCount < 1) return@mapNotNull null

                var matchScore = if (exactTitleMatch) 200 else 120
                if (primaryMatches) matchScore += 120
                matchScore += matchedCount * 60
                if (targetDurationMs > 40_000L && cand.durationMs > 40_000L) {
                    val diffSec = kotlin.math.abs(targetDurationMs - cand.durationMs) / 1000L
                    if (diffSec <= 4L) matchScore += 50
                    else if (diffSec <= 10L) matchScore += 25
                }
                cand to matchScore
            } else if (!requireArtistMatch) {
                cand to (if (exactTitleMatch) 100 else 60)
            } else {
                null
            }
        }.sortedByDescending { it.second }

        return scoredCandidates.firstOrNull()?.first
    }

    private suspend fun searchInnerTubeWebRemix(
        query: String,
        countryCode: String = resolveUserCountryCode()
    ): List<SongItem> = coroutineScope {
        suspend fun fetchWithParams(params: String?): List<SongItem> {
            val payload = buildJsonObject {
                put("query", query)
                if (params != null) {
                    put("params", params)
                }
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20241118.01.00")
                        put("hl", "en")
                        put("gl", countryCode)
                    }
                }
            }

            val responseText = httpPostJsonFast(
                url = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false",
                jsonBody = payload.toString(),
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Origin" to "https://music.youtube.com",
                    "Referer" to "https://music.youtube.com/",
                    "X-YouTube-Client-Name" to "67",
                    "X-YouTube-Client-Version" to "1.20241118.01.00"
                )
            )

            val rootElement = jsonParser.parseToJsonElement(responseText)
            val renderers = mutableListOf<JsonObject>()
            collectRenderersByKey(rootElement, "musicResponsiveListItemRenderer", renderers)

            return renderers.mapNotNull { item ->
                parseMusicResponsiveListItem(item)
            }
        }

        // Query both Songs-filtered and Unfiltered YouTube Music (which includes Top Results & regional Music Videos)
        val songsFilteredDef = async {
            runCatching { fetchWithParams("EgWKAQIIAWoKEAkQBRAKEAMQBA==") }.getOrDefault(emptyList())
        }
        val unfilteredTopDef = async {
            runCatching { fetchWithParams(null) }.getOrDefault(emptyList())
        }

        (unfilteredTopDef.await() + songsFilteredDef.await()).distinctBy { it.id }
    }

    /**
     * Searches YouTube's main catalog (`youtubei/v1/search`) with `gl = IN` to capture regional Indian,
     * Punjabi, Haryanvi, and indie music tracks (like "Katana" by Panjeta) that are distributed as official music videos.
     */
    private suspend fun searchYouTubeVideoCatalog(
        query: String,
        countryCode: String = resolveUserCountryCode()
    ): List<SongItem> {
        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20241118.01.00")
                    put("hl", "en")
                    put("gl", countryCode)
                }
            }
        }

        val responseText = httpPostJsonFast(
            url = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false",
            jsonBody = payload.toString(),
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com/"
            )
        )

        val rootElement = jsonParser.parseToJsonElement(responseText)
        val videoRenderers = mutableListOf<JsonObject>()
        collectRenderersByKey(rootElement, "videoRenderer", videoRenderers)

        return videoRenderers.take(15).mapNotNull { video ->
            val videoId = video["videoId"].strOrNull() ?: return@mapNotNull null
            val rawTitle = video["title"].objOrNull()
                ?.get("runs").arrOrNull()
                ?.mapNotNull { it.objOrNull()?.get("text").strOrNull() }
                ?.joinToString("")
                ?.trim()
                ?: return@mapNotNull null

            val durationText = video["lengthText"].objOrNull()?.get("simpleText").strOrNull()
                ?: video["lengthText"].objOrNull()?.get("runs").arrOrNull()?.firstOrNull()?.objOrNull()?.get("text").strOrNull()
                ?: return@mapNotNull null
            val durationMs = parseDurationMs(durationText)
            // Keep real song durations (60s .. 10m), skipping shorts & hour-long compilations
            if (durationMs !in 60_000L..600_000L) return@mapNotNull null

            val channelName = video["ownerText"].objOrNull()
                ?.get("runs").arrOrNull()
                ?.firstOrNull()?.objOrNull()
                ?.get("text").strOrNull()
                ?.replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
                ?.trim()
                .orEmpty()

            val (parsedTitle, parsedArtist) = splitVideoTitleAndArtist(rawTitle, channelName)
            val thumbs = video["thumbnail"].objOrNull()?.get("thumbnails").arrOrNull()
            val thumbUrl = thumbs?.lastOrNull()?.objOrNull()?.get("url").strOrNull()
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            SongItem(
                id = videoId,
                title = parsedTitle,
                artist = parsedArtist,
                durationMs = durationMs,
                artworkUrl = thumbUrl,
                streamUrl = "",
                albumName = "Single"
            )
        }.distinctBy { it.id }
    }

    private fun splitVideoTitleAndArtist(rawTitle: String, fallbackChannel: String): Pair<String, String> {
        val cleaned = decodeHtmlEntities(rawTitle)
            .replace(
                Regex("\\s*[(\\[](?:official\\s*(?:music\\s*)?(?:video|audio|lyric\\s*video|visualizer)|full\\s*(?:video|song|audio)|lyric\\s*video|audio\\s*song|4k|hd|latest\\s*[^)\\]]*|new\\s*[^)\\]]*)[)\\]]", RegexOption.IGNORE_CASE),
                ""
            )
            .trim()

        val rawSegments = cleaned.split(Regex("\\s+[-–—|:]\\s+"))
            .map { seg ->
                seg.replace(
                    Regex("\\b(?:official\\s*(?:music\\s*)?(?:video|audio|song)|full\\s*(?:video|song|audio)|lyric\\s*video|latest\\s*(?:haryanvi|punjabi|hindi|bhojpuri|rajasthani|indian)?\\s*songs?(?:\\s*20\\d\\d)?|new\\s*(?:haryanvi|punjabi|hindi)?\\s*songs?(?:\\s*20\\d\\d)?|4k|hd)\\b", RegexOption.IGNORE_CASE),
                    ""
                ).replace(Regex("\\s+"), " ").trim()
            }
            .filter { seg ->
                seg.length >= 2 &&
                    !seg.matches(Regex("(?i)^(official|full|video|audio|song|songs|20\\d\\d|hd|4k)$"))
            }

        val cleanChannel = fallbackChannel
            .replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
            .trim()
        val isGenericLabelChannel = cleanChannel.matches(
            Regex("(?i).*(records|films|studios|entertainment|production|series|haryanvi|punjabi|t-series|zee|sony|tips|saregama|speed|white hill).*")
        )

        return if (rawSegments.size >= 2) {
            val firstSeg = rawSegments[0]
            val secondSeg = rawSegments[1]
            // Detect if firstSeg is actually the artist name matching the channel (e.g. "Panjeta - Katana")
            val firstMatchesChannel = cleanChannel.isNotBlank() &&
                !isGenericLabelChannel &&
                (firstSeg.equals(cleanChannel, ignoreCase = true) ||
                    cleanChannel.contains(firstSeg, ignoreCase = true)) &&
                !secondSeg.equals(cleanChannel, ignoreCase = true)

            if (firstMatchesChannel) {
                val songTitle = secondSeg
                val artistsList = (listOf(firstSeg) + rawSegments.drop(2)).take(3).joinToString(", ")
                songTitle to artistsList.ifBlank { cleanChannel.ifBlank { "Unknown Artist" } }
            } else {
                val songTitle = firstSeg
                val restArtists = rawSegments.drop(1).take(3).joinToString(", ")
                val finalArtist = if (cleanChannel.isNotBlank() &&
                    !isGenericLabelChannel &&
                    !restArtists.contains(cleanChannel, ignoreCase = true)
                ) {
                    "$restArtists, $cleanChannel"
                } else {
                    restArtists
                }
                songTitle to finalArtist.ifBlank { cleanChannel.ifBlank { "Unknown Artist" } }
            }
        } else {
            val singleTitle = rawSegments.firstOrNull()?.takeIf { it.isNotBlank() } ?: cleaned
            singleTitle to cleanChannel.ifBlank { "Unknown Artist" }
        }
    }

    private fun collectRenderersByKey(
        element: JsonElement,
        targetKey: String,
        out: MutableList<JsonObject>
    ) {
        when (element) {
            is JsonObject -> {
                for ((key, value) in element) {
                    if (key == targetKey && value is JsonObject) {
                        out.add(value)
                    } else {
                        collectRenderersByKey(value, targetKey, out)
                    }
                }
            }
            is JsonArray -> {
                for (child in element) {
                    collectRenderersByKey(child, targetKey, out)
                }
            }
            else -> Unit
        }
    }

    private fun parseMusicResponsiveListItem(item: JsonObject): SongItem? {
        val videoId = item["playlistItemData"].objOrNull()?.get("videoId").strOrNull()
            ?: item["navigationEndpoint"].objOrNull()
                ?.get("watchEndpoint").objOrNull()
                ?.get("videoId").strOrNull()
            ?: findFirstVideoId(item)
            ?: return null

        val flexColumns = item["flexColumns"].arrOrNull() ?: return null
        val titleRuns = flexColumns.getOrNull(0).objOrNull()
            ?.get("musicResponsiveListItemFlexColumnRenderer").objOrNull()
            ?.get("text").objOrNull()
            ?.get("runs").arrOrNull()

        val rawTitle = titleRuns?.mapNotNull { it.objOrNull()?.get("text").strOrNull() }
            ?.joinToString("")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val subtitleRuns = flexColumns.getOrNull(1).objOrNull()
            ?.get("musicResponsiveListItemFlexColumnRenderer").objOrNull()
            ?.get("text").objOrNull()
            ?.get("runs").arrOrNull()

        // Group subtitle runs by " • " separator so multi-artist credits (e.g., "Sumit Parta & Panjeta") stay intact!
        val groupedSegments = mutableListOf<String>()
        val currentSegment = StringBuilder()
        subtitleRuns?.forEach { run ->
            val txt = run.objOrNull()?.get("text").strOrNull().orEmpty()
            if (txt == " • ") {
                val seg = currentSegment.toString().trim()
                if (seg.isNotEmpty()) groupedSegments.add(seg)
                currentSegment.clear()
            } else {
                currentSegment.append(txt)
            }
        }
        val trailingSeg = currentSegment.toString().trim()
        if (trailingSeg.isNotEmpty()) groupedSegments.add(trailingSeg)

        val contentSegments = groupedSegments.filter {
            !it.equals("Song", ignoreCase = true) &&
                !it.equals("Video", ignoreCase = true) &&
                !it.equals("Explicit", ignoreCase = true) &&
                !it.endsWith("views", ignoreCase = true) &&
                !it.endsWith("plays", ignoreCase = true)
        }

        val rawArtist = contentSegments.firstOrNull { !it.contains(":") } ?: "Unknown Artist"
        val (cleanTitle, enrichedArtist) = if (rawTitle.contains(" - ") || rawTitle.contains(" | ")) {
            val (t, a) = splitVideoTitleAndArtist(rawTitle, rawArtist)
            val mergedArtist = if (rawArtist != "Unknown Artist" && !a.contains(rawArtist, ignoreCase = true)) {
                "$a, $rawArtist"
            } else {
                a
            }
            t to mergedArtist
        } else {
            rawTitle to rawArtist
        }

        val albumName = contentSegments.drop(1).firstOrNull { !it.contains(":") } ?: "Single"
        val durationToken = contentSegments.lastOrNull { it.contains(":") } ?: "3:30"
        val durationMs = parseDurationMs(durationToken)

        val thumbnails = item["thumbnail"].objOrNull()
            ?.get("musicThumbnailRenderer").objOrNull()
            ?.get("thumbnail").objOrNull()
            ?.get("thumbnails").arrOrNull()

        val rawThumbUrl = thumbnails?.lastOrNull().objOrNull()?.get("url").strOrNull()
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        val highResArtwork = rawThumbUrl.replace(Regex("w\\d+-h\\d+"), "w600-h600")

        return SongItem(
            id = videoId,
            title = cleanTitle,
            artist = enrichedArtist,
            durationMs = durationMs,
            artworkUrl = highResArtwork,
            streamUrl = "",
            albumName = albumName
        )
    }

    private fun findFirstVideoId(element: JsonElement): String? {
        when (element) {
            is JsonObject -> {
                element["videoId"].strOrNull()?.let { return it }
                for ((_, v) in element) {
                    findFirstVideoId(v)?.let { return it }
                }
            }
            is JsonArray -> {
                for (item in element) {
                    findFirstVideoId(item)?.let { return it }
                }
            }
            else -> Unit
        }
        return null
    }

    /**
     * Parses both Official JioSaavn API (`www.jiosaavn.com/api.php` with `encrypted_media_url`)
     * and community JioSaavn wrapper (`saavn.sumit.co` / `saavn.dev` with `downloadUrl`) into a
     * full-length 320kbps `SongItem`. Preserves primary, featured, and lyricist/composer credits (e.g. Panjeta).
     */
    private fun parseJioSaavnSongObject(obj: JsonObject?, category: String = "Trending"): SongItem? {
        if (obj == null) return null
        val id = obj["id"].strOrNull() ?: return null
        val rawTitle = obj["title"].strOrNull()
            ?: obj["name"].strOrNull()
            ?: obj["song"].strOrNull()
            ?: return null
        val title = decodeHtmlEntities(rawTitle)

        val moreInfo = obj["more_info"].objOrNull()
        val artistMap = moreInfo?.get("artistMap").objOrNull()
        val artistsObj = obj["artists"].objOrNull()

        val collectedArtistNames = LinkedHashSet<String>()
        fun addArtistArray(arr: JsonArray?) {
            arr?.forEach { elem ->
                val name = elem.objOrNull()?.get("name").strOrNull()?.trim()
                if (!name.isNullOrBlank()) {
                    collectedArtistNames.add(decodeHtmlEntities(name))
                }
            }
        }

        addArtistArray(artistMap?.get("primary_artists").arrOrNull())
        addArtistArray(artistMap?.get("featured_artists").arrOrNull())
        addArtistArray(artistsObj?.get("primary").arrOrNull())
        addArtistArray(artistsObj?.get("featured").arrOrNull())
        if (collectedArtistNames.size < 3) {
            addArtistArray(artistMap?.get("artists").arrOrNull())
            addArtistArray(artistsObj?.get("all").arrOrNull())
        }

        val combinedMapArtists = collectedArtistNames.take(4).joinToString(", ")

        val artist = decodeHtmlEntities(
            combinedMapArtists.takeIf { it.isNotBlank() }
                ?: moreInfo?.get("music")?.strOrNull()
                ?: obj["primary_artists"].strOrNull()
                ?: obj["primaryArtists"].strOrNull()
                ?: obj["subtitle"].strOrNull()
                ?: "Unknown Artist"
        )

        val album = decodeHtmlEntities(
            moreInfo?.get("album")?.strOrNull()
                ?: obj["album"].objOrNull()?.get("name").strOrNull()
                ?: obj["album"].strOrNull()
                ?: "Single"
        )

        val durationSec = moreInfo?.get("duration")?.strOrNull()?.toLongOrNull()
            ?: moreInfo?.get("duration")?.longValOrNull()
            ?: obj["duration"].longValOrNull()
            ?: obj["duration"].strOrNull()?.toLongOrNull()
            ?: 210L

        val rawImage = obj["image"].strOrNull()
            ?.replace("150x150", "500x500")
            ?.replace("50x50", "500x500")
            ?: obj["image"].arrOrNull()
                ?.mapNotNull { it.objOrNull() }
                ?.lastOrNull()
                ?.let { img -> img["url"].strOrNull() ?: img["link"].strOrNull() }

        val artworkUrl = rawImage
            ?.replace("http://", "https://")
            ?.takeIf { it.isNotBlank() }
            ?: return null

        // 1. Check direct downloadUrl array (wrapper format), respecting active Audio Quality / Data Saver tier
        val downloadUrls = obj["downloadUrl"].arrOrNull()?.mapNotNull { it.objOrNull() }.orEmpty()
        val effectiveTier = resolveEffectiveQualityTier()
        val preferredQualityOrder = when (effectiveTier) {
            StreamingQualityMode.DATA_SAVER -> listOf("96kbps", "48kbps", "160kbps", "320kbps")
            StreamingQualityMode.BALANCED -> listOf("160kbps", "96kbps", "320kbps")
            else -> listOf("320kbps", "160kbps", "96kbps")
        }
        val selectedDownloadObj = preferredQualityOrder.firstNotNullOfOrNull { qTag ->
            downloadUrls.firstOrNull { dl ->
                dl["quality"].strOrNull()?.equals(qTag, ignoreCase = true) == true
            }
        } ?: if (effectiveTier == StreamingQualityMode.DATA_SAVER) {
            downloadUrls.firstOrNull()
        } else {
            downloadUrls.lastOrNull()
        }
        val directDownloadUrl = selectedDownloadObj
            ?.let { dl -> dl["url"].strOrNull() ?: dl["link"].strOrNull() }
            ?.replace("http://", "https://")

        // 2. Or decrypt official JioSaavn `encrypted_media_url` on-device (DES-ECB -> tier-matched MP4/AAC)
        val encryptedUrl = moreInfo?.get("encrypted_media_url")?.strOrNull()
            ?: obj["encrypted_media_url"].strOrNull()
        val has320 = (
            moreInfo?.get("320kbps")?.strOrNull()
                ?: obj["320kbps"].strOrNull()
            )?.equals("true", ignoreCase = true) != false

        val bestStreamUrl = directDownloadUrl?.takeIf { it.isNotBlank() }
            ?: encryptedUrl?.let { decryptJioSaavnMediaUrl(it, prefer320 = has320) }
            ?: return null

        return SongItem(
            id = "saavn_$id",
            title = title,
            artist = artist,
            durationMs = (durationSec.coerceAtLeast(60L)) * 1000L,
            artworkUrl = artworkUrl,
            streamUrl = bestStreamUrl,
            albumName = album,
            moodCategory = category
        ).also {
            songMetadataCache[it.id] = it
            resolvedStreamCache[it.id] = it.streamUrl
        }
    }

    private suspend fun searchJioSaavnCatalog(
        query: String,
        page: Int = 1,
        limit: Int = 25
    ): List<SongItem> {
        val encoded = urlEncode(query)
        val safePage = page.coerceAtLeast(1)
        val safeLimit = limit.coerceIn(5, 40)

        fun parseSaavnResponse(text: String): List<SongItem>? {
            val root = jsonParser.parseToJsonElement(text).objOrNull() ?: return null
            val results = root["results"].arrOrNull()
                ?: root["data"].objOrNull()?.get("results").arrOrNull()
                ?: return emptyList()
            return results.mapNotNull { elem -> parseJioSaavnSongObject(elem.objOrNull()) }
        }

        // 1. Official JioSaavn endpoint first with fast timeout.
        // If official JioSaavn responds with valid JSON (even 0 results), stop immediately
        // instead of stalling 20-40s on third-party mirrors of the exact same catalog.
        val officialUrl =
            "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=$safeLimit&p=$safePage&q=$encoded"
        val officialResult = runCatching {
            val text = httpGetFast(officialUrl)
            parseSaavnResponse(text)
        }.getOrNull()

        if (officialResult != null) {
            return officialResult
        }

        // 2. Only when official JioSaavn fails/errors, race backup mirrors in parallel
        val mirrorEndpoints = listOf(
            "https://saavn.sumit.co/api/search/songs?query=$encoded&page=$safePage&limit=$safeLimit",
            "https://saavn.dev/api/search/songs?query=$encoded&page=$safePage&limit=$safeLimit",
            "https://jiosaavn-api-privatecvc2.vercel.app/search/songs?query=$encoded&limit=$safeLimit"
        )
        return raceFirstNonNull(
            timeoutMs = 3_800L,
            tasks = mirrorEndpoints.map { endpoint ->
                suspend {
                    val text = httpGetFast(endpoint)
                    parseSaavnResponse(text)?.takeIf { it.isNotEmpty() }
                }
            }
        ) ?: emptyList()
    }

    private suspend fun fetchJioSaavnSongById(saavnId: String): SongItem? {
        val cleanId = saavnId.removePrefix("saavn_").trim()
        if (cleanId.isEmpty()) return null
        val url = "https://www.jiosaavn.com/api.php?__call=song.getDetails&_format=json&_marker=0&api_version=4&pids=$cleanId"
        return runCatching {
            val text = httpGetFast(url)
            val root = jsonParser.parseToJsonElement(text).objOrNull() ?: return@runCatching null
            val songObj = root["songs"].arrOrNull()?.firstOrNull()?.objOrNull()
                ?: root[cleanId].objOrNull()
                ?: return@runCatching null
            parseJioSaavnSongObject(songObj)
        }.getOrNull()
    }

    private suspend fun searchDeezerCatalog(query: String): List<SongItem> {
        val encoded = urlEncode(query)
        val responseText = httpGet("https://api.deezer.com/search?q=$encoded&limit=20")
        val root = jsonParser.parseToJsonElement(responseText).objOrNull() ?: return emptyList()
        val dataArray = root["data"].arrOrNull() ?: return emptyList()

        return dataArray.mapNotNull { elem ->
            val obj = elem.objOrNull() ?: return@mapNotNull null
            val id = obj["id"].longValOrNull()?.toString() ?: obj["id"].strOrNull() ?: return@mapNotNull null
            val title = obj["title"].strOrNull() ?: obj["title_short"].strOrNull() ?: return@mapNotNull null
            val durationSec = obj["duration"].longValOrNull() ?: 180L
            val artist = obj["artist"].objOrNull()?.get("name").strOrNull() ?: "Unknown Artist"
            val albumObj = obj["album"].objOrNull()
            val albumName = albumObj?.get("title").strOrNull() ?: "Single"
            val coverUrl = albumObj?.get("cover_xl").strOrNull()
                ?: albumObj?.get("cover_big").strOrNull()
                ?: albumObj?.get("cover_medium").strOrNull()
                ?: return@mapNotNull null

            SongItem(
                id = "deezer_$id",
                title = title,
                artist = artist,
                durationMs = durationSec * 1000L,
                artworkUrl = coverUrl,
                // Never pre-fill 30s preview URL; resolve full-length stream on playback
                streamUrl = "",
                albumName = albumName
            ).also {
                songMetadataCache[it.id] = it
            }
        }
    }

    private suspend fun searchITunesCatalog(
        query: String,
        countryCode: String = resolveUserCountryCode(),
        limit: Int = 25
    ): List<SongItem> {
        val encoded = urlEncode(query)
        val safeLimit = limit.coerceIn(5, 50)
        val responseText = httpGet(
            "https://itunes.apple.com/search?term=$encoded&country=$countryCode&media=music&entity=song&limit=$safeLimit"
        )
        val root = jsonParser.parseToJsonElement(responseText).objOrNull() ?: return emptyList()
        val resultsArray = root["results"].arrOrNull() ?: return emptyList()

        return resultsArray.mapNotNull { elem ->
            val obj = elem.objOrNull() ?: return@mapNotNull null
            val trackId = obj["trackId"].longValOrNull()?.toString()
                ?: obj["trackId"].strOrNull()
                ?: return@mapNotNull null
            val title = obj["trackName"].strOrNull() ?: return@mapNotNull null
            val artist = obj["artistName"].strOrNull() ?: "Unknown Artist"
            val album = obj["collectionName"].strOrNull() ?: "Single"
            val art = (obj["artworkUrl100"].strOrNull() ?: "")
                .replace("100x100bb", "600x600bb")
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val durationMs = obj["trackTimeMillis"].longValOrNull() ?: 180_000L

            SongItem(
                id = "itunes_$trackId",
                title = title,
                artist = artist,
                durationMs = durationMs,
                artworkUrl = art,
                // Never pre-fill 30s Apple preview URL; resolve full-length stream on playback
                streamUrl = "",
                albumName = album
            ).also {
                songMetadataCache[it.id] = it
            }
        }
    }

    /**
     * Strictly resolves the exact song's FULL-LENGTH playable audio stream with low latency
     * (Never returns 30-second iTunes or Deezer preview clips, and NEVER substitutes an unverified song by a different artist).
     */
    suspend fun getStreamUrl(
        videoId: String,
        trackTitle: String = "",
        artistName: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Instant O(1) lookup in shared process-wide cache (automatically rejects expired googlevideo URLs)
            getCachedValidStreamUrl(videoId)?.let {
                return@runCatching it
            }

            val cachedSong = songMetadataCache[videoId]

            // 2. If this is a JioSaavn song ID, fetch its official details directly
            if (videoId.startsWith("saavn_")) {
                val directSaavn = fetchJioSaavnSongById(videoId)
                if (directSaavn != null && isFullLengthStreamUrl(directSaavn.streamUrl)) {
                    resolvedStreamCache[videoId] = directSaavn.streamUrl
                    return@runCatching directSaavn.streamUrl
                }
            }

            val isDirectYouTubeId = videoId.isNotBlank() &&
                !videoId.startsWith("itunes_") &&
                !videoId.startsWith("saavn_") &&
                !videoId.startsWith("deezer_") &&
                !videoId.startsWith("spotify_")

            // 3. CRITICAL: If videoId is ALREADY a direct YouTube videoId (e.g. "Katana" by "Panjeta" from YouTube search),
            // race all InnerTube clients in parallel FIRST (~250-600ms) so we play the exact track immediately.
            if (isDirectYouTubeId) {
                val directYtUrl = tryInnerTubeDirectClients(videoId)
                    ?: tryPipedStreamInstances(videoId)
                if (!directYtUrl.isNullOrBlank()) {
                    resolvedStreamCache[videoId] = directYtUrl
                    return@runCatching directYtUrl
                }
            }

            val effectiveTitle = trackTitle.ifBlank { cachedSong?.title.orEmpty() }
            val effectiveArtist = artistName.ifBlank { cachedSong?.artist.orEmpty() }
            val targetDurationMs = cachedSong?.durationMs ?: 0L

            val cleanArtistForQuery = effectiveArtist
                .takeIf { !it.equals("Unknown Artist", ignoreCase = true) && !it.equals("Spotify Artist", ignoreCase = true) }
                ?.replace(",", " ")
                ?.replace("&", " ")
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                .orEmpty()

            val lookupQuery = buildString {
                if (effectiveTitle.isNotBlank()) {
                    append(cleanTrackTitleForLyrics(effectiveTitle, effectiveArtist).ifBlank { effectiveTitle })
                    if (cleanArtistForQuery.isNotBlank()) {
                        append(" ")
                        append(cleanArtistForQuery)
                    }
                } else if (isDirectYouTubeId) {
                    append(fetchYouTubeOEmbedTitle(videoId).ifBlank { videoId })
                }
            }.trim()

            // 4. For synthetic catalog IDs (iTunes/Deezer/Spotify) or restricted YouTube IDs, search JioSaavn + YouTube
            // in parallel with STRICT Title + Artist verification and IMMEDIATE cancellation of slower branches.
            if (lookupQuery.isNotBlank()) {
                val resolvedFullUrl = supervisorScope {
                    val saavnPrimaryDef = async {
                        runCatching { searchJioSaavnCatalog(lookupQuery, page = 1, limit = 15) }.getOrDefault(emptyList())
                    }
                    val ytMusicDef = async {
                        runCatching { searchInnerTubeWebRemix(lookupQuery) }.getOrDefault(emptyList())
                    }
                    val ytVideoDef = async {
                        runCatching { searchYouTubeVideoCatalog(lookupQuery) }.getOrDefault(emptyList())
                    }

                    val saavnCandidates = saavnPrimaryDef.await()
                        .filter { isFullLengthStreamUrl(it.streamUrl) }

                    // A. Try strict title + artist + duration match on JioSaavn 320kbps catalog
                    val matchedSaavn = if (effectiveTitle.isNotBlank()) {
                        findBestMatchingTrack(
                            targetTitle = effectiveTitle,
                            targetArtist = effectiveArtist,
                            candidates = saavnCandidates,
                            targetDurationMs = targetDurationMs,
                            requireArtistMatch = cleanArtistForQuery.isNotBlank()
                        )
                    } else {
                        null
                    }

                    if (matchedSaavn != null && isFullLengthStreamUrl(matchedSaavn.streamUrl)) {
                        // CRITICAL: Cancel slower YouTube searches immediately so supervisorScope exits in ~250ms!
                        ytMusicDef.cancel()
                        ytVideoDef.cancel()
                        return@supervisorScope matchedSaavn.streamUrl
                    }

                    // B. Check YouTube Music results first (usually cleaner & faster); cancel ytVideoDef if verified!
                    val ytMusicResults = ytMusicDef.await()
                    val verifiedYtMusic = if (effectiveTitle.isNotBlank()) {
                        findBestMatchingTrack(
                            targetTitle = effectiveTitle,
                            targetArtist = effectiveArtist,
                            candidates = ytMusicResults,
                            targetDurationMs = targetDurationMs,
                            requireArtistMatch = cleanArtistForQuery.isNotBlank()
                        )
                    } else {
                        ytMusicResults.firstOrNull()
                    }
                    if (verifiedYtMusic != null) {
                        val quickStream = tryInnerTubeDirectClients(verifiedYtMusic.id)
                        if (!quickStream.isNullOrBlank()) {
                            ytVideoDef.cancel()
                            return@supervisorScope quickStream
                        }
                    }

                    // C. Merge with YouTube Video results and try top 2 verified candidates
                    val ytAllResults = (ytMusicResults + ytVideoDef.await()).distinctBy { it.id }
                    val ytCandidateIds = buildList {
                        if (isDirectYouTubeId) add(videoId)
                        if (verifiedYtMusic != null) add(verifiedYtMusic.id)
                        if (effectiveTitle.isNotBlank()) {
                            ytAllResults.forEach { cand ->
                                val verified = findBestMatchingTrack(
                                    targetTitle = effectiveTitle,
                                    targetArtist = effectiveArtist,
                                    candidates = listOf(cand),
                                    targetDurationMs = targetDurationMs,
                                    requireArtistMatch = cleanArtistForQuery.isNotBlank()
                                )
                                if (verified != null) add(verified.id)
                            }
                        } else {
                            ytAllResults.firstOrNull()?.let { add(it.id) }
                        }
                    }.distinct().take(2)

                    for (ytId in ytCandidateIds) {
                        val directYtStream = tryInnerTubeDirectClients(ytId)
                            ?: tryPipedStreamInstances(ytId)
                        if (!directYtStream.isNullOrBlank()) {
                            return@supervisorScope directYtStream
                        }
                    }

                    null
                }

                if (!resolvedFullUrl.isNullOrBlank()) {
                    resolvedStreamCache[videoId] = resolvedFullUrl
                    return@runCatching resolvedFullUrl
                }
            }

            throw IllegalStateException("Full-length stream unavailable for $effectiveTitle")
        }
    }

    suspend fun getAlternateStreamUrl(song: SongItem, failedUrl: String): String? = withContext(Dispatchers.IO) {
        // Evict the failed/expired URL immediately so it is never served again
        invalidateStreamUrl(song.id, failedUrl)

        val isDirectYouTubeId = song.id.isNotBlank() &&
            !song.id.startsWith("itunes_") &&
            !song.id.startsWith("saavn_") &&
            !song.id.startsWith("deezer_") &&
            !song.id.startsWith("spotify_")

        // 1. If this is a YouTube song, first race alternate InnerTube clients / Piped for the EXACT same videoId
        if (isDirectYouTubeId) {
            val altSameVideo = tryInnerTubeDirectClients(song.id, excludeUrl = failedUrl)
                ?: tryPipedStreamInstances(song.id, excludeUrl = failedUrl)
            if (!altSameVideo.isNullOrBlank() && altSameVideo != failedUrl) {
                resolvedStreamCache[song.id] = altSameVideo
                return@withContext altSameVideo
            }
        }

        val cleanTitle = cleanTrackTitleForLyrics(song.title, song.artist).ifBlank { song.title }
        val cleanArtist = song.artist.replace(",", " ").replace("&", " ").replace(Regex("\\s+"), " ").trim()
        val query = "$cleanTitle $cleanArtist".trim()

        // 2. Run strictly verified JioSaavn & YouTube alternate recovery in parallel
        supervisorScope {
            val saavnDef = async {
                val saavn = runCatching { searchJioSaavnCatalog(query, page = 1, limit = 15) }.getOrDefault(emptyList())
                    .filter { isFullLengthStreamUrl(it.streamUrl) && it.streamUrl != failedUrl }
                findBestMatchingTrack(
                    targetTitle = song.title,
                    targetArtist = song.artist,
                    candidates = saavn,
                    targetDurationMs = song.durationMs,
                    requireArtistMatch = true
                )?.streamUrl
            }
            val ytDef = async {
                val ytResults = runCatching {
                    (searchInnerTubeWebRemix(query) + searchYouTubeVideoCatalog(query)).distinctBy { it.id }
                }.getOrDefault(emptyList())
                val verifiedYt = findBestMatchingTrack(
                    targetTitle = song.title,
                    targetArtist = song.artist,
                    candidates = ytResults,
                    targetDurationMs = song.durationMs,
                    requireArtistMatch = true
                )
                if (verifiedYt != null) {
                    tryInnerTubeDirectClients(verifiedYt.id, excludeUrl = failedUrl)
                        ?: tryPipedStreamInstances(verifiedYt.id, excludeUrl = failedUrl)
                } else {
                    null
                }
            }

            val chosenSaavn = saavnDef.await()
            if (!chosenSaavn.isNullOrBlank() && chosenSaavn != failedUrl) {
                ytDef.cancel()
                resolvedStreamCache[song.id] = chosenSaavn
                return@supervisorScope chosenSaavn
            }

            val chosenYt = ytDef.await()
            if (!chosenYt.isNullOrBlank() && chosenYt != failedUrl) {
                resolvedStreamCache[song.id] = chosenYt
                return@supervisorScope chosenYt
            }
            null
        }
    }

    private suspend fun tryInnerTubeDirectClients(
        videoId: String,
        excludeUrl: String = ""
    ): String? {
        data class InnerTubeClientSpec(
            val clientName: String,
            val clientVersion: String,
            val clientIdHeader: String,
            val userAgent: String,
            val deviceMake: String,
            val deviceModel: String,
            val osName: String,
            val osVersion: String,
            val androidSdkVersion: Int? = null,
            val isEmbeddedTv: Boolean = false
        )

        val userCountry = resolveUserCountryCode()

        val clients = listOf(
            InnerTubeClientSpec(
                clientName = "ANDROID_VR",
                clientVersion = "1.60.19",
                clientIdHeader = "28",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                osName = "Android",
                osVersion = "12L",
                androidSdkVersion = 32
            ),
            InnerTubeClientSpec(
                clientName = "IOS",
                clientVersion = "19.45.4",
                clientIdHeader = "5",
                userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                osName = "iPhone",
                osVersion = "18.1.0.22B83"
            ),
            InnerTubeClientSpec(
                clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientVersion = "2.0",
                clientIdHeader = "85",
                userAgent = "Mozilla/5.0 (PlayStation; PlayStation 5/2.26) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0 Safari/605.1.15",
                deviceMake = "Sony",
                deviceModel = "PlayStation 5",
                osName = "PlayStation",
                osVersion = "2.26",
                isEmbeddedTv = true
            ),
            InnerTubeClientSpec(
                clientName = "ANDROID_TESTSUITE",
                clientVersion = "1.9",
                clientIdHeader = "30",
                userAgent = "com.google.android.youtube/1.9 (Linux; U; Android 14; Pixel 8 Pro) gzip",
                deviceMake = "Google",
                deviceModel = "Pixel 8 Pro",
                osName = "Android",
                osVersion = "14",
                androidSdkVersion = 34
            )
        )

        // Race all 4 InnerTube clients concurrently and return the first valid audio stream (~250-600ms)
        return raceFirstNonNull(
            timeoutMs = 5_000L,
            tasks = clients.map { spec ->
                val task: suspend () -> String? = clientTask@{
                    val payload = buildJsonObject {
                        put("videoId", videoId)
                        put("racyCheckOk", true)
                        put("contentCheckOk", true)
                        putJsonObject("context") {
                            putJsonObject("client") {
                                put("clientName", spec.clientName)
                                put("clientVersion", spec.clientVersion)
                                put("deviceMake", spec.deviceMake)
                                put("deviceModel", spec.deviceModel)
                                put("osName", spec.osName)
                                put("osVersion", spec.osVersion)
                                spec.androidSdkVersion?.let { put("androidSdkVersion", it) }
                                put("hl", "en")
                                put("gl", userCountry)
                            }
                            if (spec.isEmbeddedTv) {
                                putJsonObject("thirdParty") {
                                    put("embedUrl", "https://www.youtube.com/")
                                }
                            }
                        }
                    }

                    val responseText = httpPostJsonFast(
                        url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                        jsonBody = payload.toString(),
                        headers = mapOf(
                            "User-Agent" to spec.userAgent,
                            "X-YouTube-Client-Name" to spec.clientIdHeader,
                            "X-YouTube-Client-Version" to spec.clientVersion
                        )
                    )

                    val root = jsonParser.parseToJsonElement(responseText).objOrNull() ?: return@clientTask null
                    val returnedVideoId = root["videoDetails"].objOrNull()?.get("videoId").strOrNull()
                    if (!returnedVideoId.isNullOrBlank() && returnedVideoId != videoId) {
                        return@clientTask null
                    }

                    val streamingData = root["streamingData"].objOrNull() ?: return@clientTask null
                    val adaptiveFormats = streamingData["adaptiveFormats"].arrOrNull()

                    val effectiveTier = resolveEffectiveQualityTier()
                    val bestAdaptiveAudio = adaptiveFormats
                        ?.mapNotNull { it.objOrNull() }
                        ?.filter { format ->
                            val mime = format["mimeType"].strOrNull() ?: ""
                            val url = format["url"].strOrNull().orEmpty()
                            val approxMs = format["approxDurationMs"].strOrNull()?.toLongOrNull() ?: 0L
                            mime.startsWith("audio/") &&
                                url.isNotBlank() &&
                                url != excludeUrl &&
                                (approxMs == 0L || approxMs > 30_000L)
                        }
                        ?.let { candidates ->
                            when (effectiveTier) {
                                StreamingQualityMode.DATA_SAVER -> {
                                    // Prefer efficient low-bitrate audio (<= 96kbps) for fastest start & minimal data
                                    candidates.sortedWith(
                                        compareByDescending<JsonObject> { format ->
                                            val bps = format["bitrate"].intValOrNull() ?: 128_000
                                            if (bps in 45_000..105_000) 2 else if (bps < 135_000) 1 else 0
                                        }.thenBy { format ->
                                            format["bitrate"].intValOrNull() ?: Int.MAX_VALUE
                                        }
                                    )
                                }
                                StreamingQualityMode.BALANCED -> {
                                    // Prefer crisp ~128-160kbps AAC/Opus
                                    candidates.sortedWith(
                                        compareBy<JsonObject> { format ->
                                            val bps = format["bitrate"].intValOrNull() ?: 128_000
                                            kotlin.math.abs(bps - 132_000)
                                        }
                                    )
                                }
                                else -> {
                                    // Studio High: prioritize M4A AAC (140) / Opus High (251) & highest bitrate
                                    candidates.sortedWith(
                                        compareByDescending<JsonObject> { format ->
                                            val itag = format["itag"].intValOrNull() ?: 0
                                            when (itag) {
                                                140 -> 3 // M4A AAC (universal hardware decoder support)
                                                251 -> 2 // WebM Opus High
                                                250, 249 -> 1 // WebM Opus Med/Low
                                                else -> 0
                                            }
                                        }.thenByDescending { format ->
                                            format["bitrate"].intValOrNull() ?: 0
                                        }
                                    )
                                }
                            }
                        }
                        ?.firstOrNull()
                        ?.get("url").strOrNull()

                    if (!bestAdaptiveAudio.isNullOrBlank()) {
                        return@clientTask bestAdaptiveAudio
                    }

                    // Fallback to progressive muxed MP4 format (e.g. itag 18) which has AAC audio and plays natively in ExoPlayer
                    val muxedFormats = streamingData["formats"].arrOrNull()
                    muxedFormats
                        ?.mapNotNull { it.objOrNull() }
                        ?.firstOrNull { format ->
                            val url = format["url"].strOrNull().orEmpty()
                            url.isNotBlank() && url != excludeUrl
                        }
                        ?.get("url").strOrNull()
                }
                task
            }
        )
    }

    private suspend fun tryPipedStreamInstances(
        videoId: String,
        excludeUrl: String = ""
    ): String? {
        val pipedInstances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.private.coffee",
            "https://pipedapi.adminforge.de",
            "https://pipedapi.drgns.space"
        )
        val invidiousInstances = listOf(
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://invidious.jing.rocks"
        )

        val effectiveTier = resolveEffectiveQualityTier()
        fun pickFormatForTier(items: List<JsonObject>, bitrateExtractor: (JsonObject) -> Int): JsonObject? {
            if (items.isEmpty()) return null
            return when (effectiveTier) {
                StreamingQualityMode.DATA_SAVER -> {
                    items.filter { bitrateExtractor(it) in 40_000..105_000 }
                        .minByOrNull(bitrateExtractor)
                        ?: items.minByOrNull(bitrateExtractor)
                }
                StreamingQualityMode.BALANCED -> {
                    items.minByOrNull { kotlin.math.abs(bitrateExtractor(it) - 132_000) }
                }
                else -> items.maxByOrNull(bitrateExtractor)
            }
        }

        val tasks = buildList<suspend () -> String?> {
            pipedInstances.forEach { base ->
                add {
                    val text = httpGetFast("$base/streams/$videoId")
                    val root = jsonParser.parseToJsonElement(text).objOrNull() ?: return@add null
                    val valid = root["audioStreams"].arrOrNull()
                        ?.mapNotNull { it.objOrNull() }
                        ?.filter {
                            val u = it["url"].strOrNull().orEmpty()
                            u.isNotBlank() && u != excludeUrl
                        }
                        .orEmpty()
                    pickFormatForTier(valid) { it["bitrate"].intValOrNull() ?: 0 }
                        ?.get("url").strOrNull()
                }
            }
            invidiousInstances.forEach { base ->
                add {
                    val text = httpGetFast("$base/api/v1/videos/$videoId")
                    val root = jsonParser.parseToJsonElement(text).objOrNull() ?: return@add null
                    val valid = root["adaptiveFormats"].arrOrNull()
                        ?.mapNotNull { it.objOrNull() }
                        ?.filter {
                            val type = it["type"].strOrNull().orEmpty()
                            val u = it["url"].strOrNull().orEmpty()
                            type.startsWith("audio/") && u.isNotBlank() && u != excludeUrl
                        }
                        .orEmpty()
                    pickFormatForTier(valid) { it["bitrate"].strOrNull()?.toIntOrNull() ?: 0 }
                        ?.get("url").strOrNull()
                }
            }
        }

        // Race all Piped & Invidious instances in parallel with a strict 4.5s budget
        return raceFirstNonNull(timeoutMs = 4_500L, tasks = tasks)
    }

    private suspend fun fetchYouTubeOEmbedTitle(videoId: String): String {
        return runCatching {
            val encodedUrl = urlEncode("https://www.youtube.com/watch?v=$videoId")
            val responseText = httpGet("https://www.youtube.com/oembed?url=$encodedUrl&format=json")
            val obj = jsonParser.parseToJsonElement(responseText).objOrNull() ?: return@runCatching ""
            val title = obj["title"].strOrNull().orEmpty()
            val author = obj["author_name"].strOrNull()
                ?.replace("- Topic", "")?.trim().orEmpty()
            "$title $author".trim()
        }.getOrDefault("")
    }

    internal fun cleanTrackTitleForLyrics(rawTitle: String, rawArtist: String = ""): String {
        val decoded = decodeHtmlEntities(rawTitle)
        val stripped = decoded
            .replace(Regex("\\((?:from|official|lyric|audio|video|feat|ft|prod|remaster|version|live|full|4k|hd|new|latest).*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[(?:from|official|lyric|audio|video|feat|ft|prod|remaster|version|live|full|4k|hd|new|latest).*?]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(.*?\\)|\\[.*?]"), "")
            .replace(Regex("\\s[|]\\s*.*$"), "")
            .replace(Regex("\\s[-–—]\\s*(?:from|official|lyric|lyrical|audio|video|remaster|original|soundtrack|motion picture|full|latest|new|prod).*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(?:feat|ft|featuring|prod\\.?(?:\\s+by)?)\\.?\\s+.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val base = stripped.ifBlank { decoded.trim() }
        // If the title still has "Part A - Part B" where one side is the artist name (e.g. "Katana - Panjeta" or "Panjeta - Katana"),
        // isolate the actual song title so lyrics search queries the clean song name + artist.
        val dashParts = base.split(Regex("\\s[-–—]\\s")).map { it.trim() }.filter { it.isNotBlank() }
        if (dashParts.size == 2 && rawArtist.isNotBlank()) {
            val artistTokens = lyricsArtistTokens(rawArtist)
            if (artistTokens.isNotEmpty()) {
                val leftTokens = lyricsArtistTokens(dashParts[0])
                val rightTokens = lyricsArtistTokens(dashParts[1])
                val leftIsArtist = leftTokens.isNotEmpty() && leftTokens.all { l -> artistTokens.any { a -> artistTokensMatch(l, a) } }
                val rightIsArtist = rightTokens.isNotEmpty() && rightTokens.all { r -> artistTokens.any { a -> artistTokensMatch(r, a) } }
                if (rightIsArtist && !leftIsArtist) return dashParts[0]
                if (leftIsArtist && !rightIsArtist) return dashParts[1]
            }
        }
        return base
    }

    internal fun extractPrimaryArtistForLyrics(rawArtist: String): String {
        val decoded = decodeHtmlEntities(rawArtist)
            .replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bVEVO\\b", RegexOption.IGNORE_CASE), "")
            .trim()
        val first = decoded
            .split(Regex("[,&/]|\\b(?:feat|ft|featuring|with|x)\\b", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .firstOrNull {
                it.isNotBlank() &&
                    !it.equals("Unknown Artist", ignoreCase = true) &&
                    !it.equals("Various Artists", ignoreCase = true)
            }
        return first ?: decoded.trim()
    }

    private fun extractAllArtistCandidatesForLyrics(rawTitle: String, rawArtist: String): List<String> {
        val decodedArtist = decodeHtmlEntities(rawArtist)
            .replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
        val splitArtists = decodedArtist
            .split(Regex("[,&/]|\\b(?:feat|ft|featuring|with|and|x)\\b", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter {
                it.length >= 2 &&
                    !it.equals("Unknown Artist", ignoreCase = true) &&
                    !it.equals("Various Artists", ignoreCase = true)
            }

        val decodedTitle = decodeHtmlEntities(rawTitle)
        val featMatch = Regex("\\b(?:feat|ft|featuring)\\.?\\s+([^)\\]|\\-–]+)", RegexOption.IGNORE_CASE)
            .find(decodedTitle)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
        val dashParts = decodedTitle
            .replace(Regex("\\(.*?\\)|\\[.*?]"), "")
            .split(Regex("\\s[-–—|]\\s"))
            .map { it.trim() }
            .filter { it.length in 2..36 }

        return buildList {
            addAll(splitArtists)
            if (featMatch.isNotBlank()) add(featMatch)
            if (dashParts.size >= 2) {
                add(dashParts[1])
            }
        }.distinctBy { it.lowercase() }
    }

    private fun lyricsArtistTokens(rawText: String): Set<String> {
        val stopWords = setOf(
            "unknown", "artist", "various", "artists", "official", "music", "the", "and",
            "feat", "ft", "featuring", "with", "prod", "by", "records", "entertainment",
            "studio", "studios", "topic", "vevo", "audio", "video", "song", "songs",
            "new", "latest", "full", "lyrical", "lyrics", "original", "soundtrack", "from"
        )
        return decodeHtmlEntities(rawText)
            .lowercase()
            .replace("$", "s")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopWords }
            .toSet()
    }

    private fun phoneticArtistStem(token: String): String {
        val lower = token.lowercase().trim()
        if (lower.length <= 3) return lower
        val mapped = lower
            .replace("ph", "f")
            .replace("th", "t")
            .replace("dh", "d")
            .replace("bh", "b")
            .replace("kh", "k")
            .replace("gh", "g")
            .replace("sh", "s")
            .replace("chh", "ch")
            .replace("aa", "a")
            .replace("ee", "i")
            .replace("oo", "u")
            .replace("ou", "u")
        val sb = StringBuilder(mapped.length)
        for (ch in mapped) {
            if (sb.isEmpty() || sb.last() != ch) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun isEditDistanceAtMostOne(a: String, b: String): Boolean {
        val lenDiff = kotlin.math.abs(a.length - b.length)
        if (lenDiff > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++
                j++
            } else {
                edits++
                if (edits > 1) return false
                if (a.length > b.length) {
                    i++
                } else if (b.length > a.length) {
                    j++
                } else {
                    i++
                    j++
                }
            }
        }
        if (i < a.length || j < b.length) edits++
        return edits <= 1
    }

    private fun artistTokensMatch(tokenA: String, tokenB: String): Boolean {
        if (tokenA == tokenB) return true
        if (tokenA.length >= 4 && tokenB.length >= 4) {
            val stemA = phoneticArtistStem(tokenA)
            val stemB = phoneticArtistStem(tokenB)
            if (stemA == stemB && stemA.length >= 3) return true
            if (tokenA.length >= 5 && tokenB.length >= 5 && isEditDistanceAtMostOne(tokenA, tokenB)) {
                return true
            }
        }
        return false
    }

    private fun anyArtistTokenMatches(targetTokens: Set<String>, candidateTokens: Set<String>): Boolean {
        if (targetTokens.isEmpty() || candidateTokens.isEmpty()) return false
        if (targetTokens.any { t -> candidateTokens.any { c -> artistTokensMatch(t, c) } }) {
            return true
        }
        // Also check compound single-word vs two-word artist spellings (e.g. "moosewala" vs "moose wala")
        val candidateJoined = candidateTokens.joinToString("")
        val targetJoined = targetTokens.joinToString("")
        return targetTokens.any { t -> t.length >= 5 && candidateJoined.contains(t) } ||
            candidateTokens.any { c -> c.length >= 5 && targetJoined.contains(c) }
    }

    /**
     * Strictly verifies and scores a lyrics candidate against both the song title and artist.
     * Returns `null` if the title does not match OR if the target track has a known artist that
     * does not match the candidate's artist, track name, or album credits.
     */
    private fun scoreAndVerifyLyricsCandidate(
        targetCleanTitle: String,
        primaryArtistTokens: Set<String>,
        allTargetArtistTokens: Set<String>,
        candidateTitle: String,
        candidateArtist: String,
        candidateAlbum: String = "",
        targetDurationSec: Long = 0L,
        candidateDurationSec: Long? = null,
        hasSynced: Boolean = false
    ): Int? {
        val targetCanonTitle = canonicalSongTitle(targetCleanTitle)
        if (targetCanonTitle.isEmpty()) return null

        val rawCandCanonTitle = canonicalSongTitle(candidateTitle)
        if (rawCandCanonTitle.isEmpty()) return null

        // Strip target artist tokens if candidateTitle embedded the artist (e.g. "Katana - Panjeta")
        val targetWords = targetCanonTitle.split(" ").filter { it.isNotBlank() }
        val candWords = rawCandCanonTitle.split(" ").filter { it.isNotBlank() }
        val candWordsWithoutArtist = candWords.filter { w ->
            !allTargetArtistTokens.any { a -> artistTokensMatch(w, a) }
        }
        val strippedCandCanonTitle = candWordsWithoutArtist.joinToString(" ").trim()
            .ifEmpty { rawCandCanonTitle }

        val allowedVersionWords = setOf(
            "remix", "slowed", "reverb", "lofi", "lo", "fi", "unplugged", "acoustic",
            "version", "edit", "live", "reprise", "ost", "original", "mix", "radio",
            "extended", "instrumental", "bonus", "track", "single", "song", "haryanvi",
            "punjabi", "hindi", "english", "translation", "romanized", "romanised"
        )

        val exactTitleMatch = rawCandCanonTitle == targetCanonTitle ||
            strippedCandCanonTitle == targetCanonTitle

        val wordBoundaryTitleContainment = if (!exactTitleMatch) {
            val candSet = candWords.toSet()
            val targetSet = targetWords.toSet()
            when {
                targetSet.all { it in candSet } -> {
                    val extraWords = candSet - targetSet
                    extraWords.all { extra ->
                        extra in allowedVersionWords ||
                            allTargetArtistTokens.any { a -> artistTokensMatch(extra, a) }
                    }
                }
                targetSet.size >= 2 && candSet.size >= 2 && candSet.all { it in targetSet } -> true
                else -> false
            }
        } else {
            true
        }

        if (!exactTitleMatch && !wordBoundaryTitleContainment) return null

        // Mandatory Artist Verification when target artist is known
        val candArtistTokens = lyricsArtistTokens(candidateArtist)
        val candTitleAndAlbumTokens = lyricsArtistTokens("$candidateTitle $candidateAlbum")

        val hasPrimaryArtistMatch = anyArtistTokenMatches(primaryArtistTokens, candArtistTokens)
        val hasCoArtistMatch = !hasPrimaryArtistMatch &&
            anyArtistTokenMatches(allTargetArtistTokens, candArtistTokens)
        val hasContextArtistMatch = !hasPrimaryArtistMatch && !hasCoArtistMatch &&
            anyArtistTokenMatches(allTargetArtistTokens, candTitleAndAlbumTokens)

        if (allTargetArtistTokens.isNotEmpty() &&
            !hasPrimaryArtistMatch &&
            !hasCoArtistMatch &&
            !hasContextArtistMatch
        ) {
            // Reject candidates from unrelated artists that happen to share the same song title
            return null
        }

        var score = 0
        score += if (exactTitleMatch) 150 else 85

        when {
            hasPrimaryArtistMatch -> {
                score += 240
                if (primaryArtistTokens.size > 1 &&
                    primaryArtistTokens.all { p -> candArtistTokens.any { c -> artistTokensMatch(p, c) } }
                ) {
                    score += 50
                }
            }
            hasCoArtistMatch -> score += 170
            hasContextArtistMatch -> score += 130
        }

        if (hasSynced) score += 210

        // Penalize slowed/remix/lofi lyrics if the user is playing the standard original version
        val targetLower = targetCleanTitle.lowercase()
        val candLower = "$candidateTitle $candidateAlbum".lowercase()
        for (modTag in listOf("slowed", "remix", "lofi", "lo-fi", "8d", "nightcore", "mashup")) {
            if (candLower.contains(modTag) && !targetLower.contains(modTag)) {
                score -= 90
            }
        }

        if (targetDurationSec > 0L && candidateDurationSec != null && candidateDurationSec > 0L) {
            val diffSec = kotlin.math.abs(candidateDurationSec - targetDurationSec)
            score += when {
                diffSec <= 2L -> 160
                diffSec <= 5L -> 115
                diffSec <= 10L -> 55
                diffSec <= 18L -> 10
                diffSec <= 30L -> -40
                else -> -160
            }
        }

        return score
    }

    private fun parsePlainLyricsLines(rawPlain: String): List<LyricLine> {
        val cleanedText = decodeHtmlEntities(
            rawPlain
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]+>"), "")
        )
        return cleanedText.lines()
            .map { it.trim() }
            .filter {
                it.isNotEmpty() &&
                    !it.startsWith("Paroles de la chanson", ignoreCase = true) &&
                    !it.startsWith("Lyricslicensed", ignoreCase = true)
            }
            .map { line -> LyricLine(startTimeMs = 0L, text = line) }
    }

    private fun parseLrclibObjectToLyricsData(obj: JsonObject?): LyricsData? {
        if (obj == null) return null
        val syncedRaw = obj["syncedLyrics"].strOrNull()
        if (!syncedRaw.isNullOrBlank()) {
            val parsed = parseLrcString(syncedRaw)
            if (parsed.size >= 2) {
                return LyricsData(lines = parsed, isSynced = true)
            }
        }
        val plainRaw = obj["plainLyrics"].strOrNull()
        if (!plainRaw.isNullOrBlank()) {
            val plainLines = parsePlainLyricsLines(plainRaw)
            if (plainLines.size >= 2) {
                return LyricsData(lines = plainLines, isSynced = false)
            }
        }
        return null
    }

    private val hinglishVocabularyTokens = setOf(
        "hai", "hain", "mein", "main", "tera", "teri", "tere", "mera", "meri", "mere",
        "tujhe", "tujhko", "mujhe", "mujhko", "dil", "pyar", "pyaar", "ishq", "mohabbat",
        "zindagi", "duniya", "raat", "shaam", "subah", "aankh", "aankhein", "ankhiyan",
        "baat", "baatein", "yaad", "yaadein", "saath", "kabhi", "kuch", "koi", "kya",
        "kyun", "kahan", "kaise", "jab", "tab", "abhi", "yahan", "wahan", "nahi", "nahin",
        "naa", "haan", "achha", "acha", "sachi", "sach", "jhooth", "sapna", "sapne",
        "apna", "apne", "apni", "tum", "tumhe", "tumhara", "hum", "humein", "humara",
        "jaana", "jaane", "aana", "aaja", "chhod", "chod", "dekh", "dekha", "dekho",
        "sun", "suna", "suno", "karde", "karna", "kiya", "ho", "hoga", "hota", "hoti",
        "jatt", "munda", "munde", "kudi", "kudiyan", "gabru", "yaar", "yaaran", "beli",
        "vailly", "asla", "bandook", "gaddi", "pind", "shehar", "chora", "chori", "haryanvi",
        "tau", "bhole", "bam", "kamar", "ghagra", "nakhra", "nakhre", "patola", "sohneya",
        "makhna", "mahिया", "mahiya", "rab", "rabba", "khuda", "maula", "sajna", "sajni",
        "piya", "o", "ve", "ni", "ke", "ka", "ki", "ko", "se", "pe", "par", "bhi", "toh"
    )

    private val commonEnglishTokens = setOf(
        "the", "and", "you", "that", "was", "for", "are", "with", "his", "they", "have",
        "this", "from", "what", "when", "where", "your", "all", "can", "Said", "there",
        "each", "which", "she", "how", "their", "will", "other", "about", "out", "many",
        "then", "them", "these", "some", "her", "would", "make", "like", "him", "into",
        "time", "has", "look", "two", "more", "write", "see", "number", "way", "could",
        "people", "than", "first", "water", "been", "call", "who", "oil", "its", "now",
        "find", "long", "down", "day", "did", "get", "come", "made", "may", "part",
        "love", "heart", "night", "baby", "never", "ever", "know", "feel", "want", "need",
        "take", "away", "stay", "life", "world", "eyes", "mind", "soul", "girl", "boy"
    )

    private fun detectLyricsScriptLabel(lines: List<LyricLine>, candidateTitle: String = ""): String {
        val sample = lines.take(28).joinToString(" ") { it.text }
        var devanagari = 0
        var gurmukhi = 0
        var bengali = 0
        var gujarati = 0
        var tamil = 0
        var telugu = 0
        var hangul = 0
        var japanese = 0
        var arabic = 0
        var latin = 0
        for (ch in sample) {
            when (ch.code) {
                in 0x0900..0x097F -> devanagari++
                in 0x0A00..0x0A7F -> gurmukhi++
                in 0x0980..0x09FF -> bengali++
                in 0x0A80..0x0AFF -> gujarati++
                in 0x0B80..0x0BFF -> tamil++
                in 0x0C00..0x0C7F -> telugu++
                in 0xAC00..0xD7AF, in 0x1100..0x11FF -> hangul++
                in 0x3040..0x30FF, in 0x4E00..0x9FFF -> japanese++
                in 0x0600..0x06FF -> arabic++
                in 'A'.code..'Z'.code, in 'a'.code..'z'.code -> latin++
            }
        }
        val lowerTitle = candidateTitle.lowercase()
        return when {
            lowerTitle.contains("romanized") || lowerTitle.contains("romanised") || lowerTitle.contains("hinglish") ->
                "Romanized (Hinglish)"
            lowerTitle.contains("english translation") || lowerTitle.contains("(english") ->
                "English"
            devanagari > 10 -> "Original (Hindi)"
            gurmukhi > 10 -> "Original (Punjabi)"
            bengali > 10 -> "Original (Bengali)"
            gujarati > 10 -> "Original (Gujarati)"
            tamil > 10 -> "Original (Tamil)"
            telugu > 10 -> "Original (Telugu)"
            hangul > 10 -> "Original (Korean)"
            japanese > 8 -> "Original (Japanese)"
            arabic > 10 -> "Original (Urdu)"
            latin > 12 -> {
                val words = sample.lowercase()
                    .replace(Regex("[^a-z\\s]"), " ")
                    .split(Regex("\\s+"))
                    .filter { it.length >= 2 }
                val hinglishCount = words.count { it in hinglishVocabularyTokens }
                val englishCount = words.count { it in commonEnglishTokens }
                if (hinglishCount >= 3 && hinglishCount * 2 >= englishCount) {
                    "Romanized (Hinglish)"
                } else {
                    "English"
                }
            }
            else -> "Original"
        }
    }

    internal fun lyricsLanguagePriority(label: String): Int {
        val lower = label.lowercase()
        return when {
            lower.contains("romanized") || lower.contains("hinglish") -> 0
            lower == "english" || lower.contains("english") -> 1
            else -> 2
        }
    }

    private fun containsIndicScript(lines: List<LyricLine>): Boolean {
        val sample = lines.take(24).joinToString(" ") { it.text }
        return sample.any { ch ->
            ch.code in 0x0900..0x0D7F
        }
    }

    /**
     * Converts Indic script lyrics (Devanagari Hindi/Haryanvi, Gurmukhi Punjabi, Bengali, Gujarati)
     * into natural Romanized (Hinglish) with word-final schwa deletion while preserving timestamps.
     */
    internal fun transliterateIndicLineToRomanizedHinglish(text: String): String {
        var hasIndic = false
        for (c in text) {
            if (c.code in 0x0900..0x0AFF) {
                hasIndic = true
                break
            }
        }
        if (!hasIndic) return text

        fun isIndicConsonant(offset: Int): Boolean {
            return offset in 0x15..0x39 || offset in 0x58..0x5F || offset == 0x79 || offset == 0x7A || offset == 0x7B || offset == 0x7E
        }

        fun isIndicMatraOrVirama(offset: Int): Boolean {
            return offset in 0x3E..0x4D || offset in 0x62..0x63 || offset == 0x3C || offset == 0x70 || offset == 0x71
        }

        fun consonantSound(offset: Int, nukta: Boolean): String = when (offset) {
            0x15 -> if (nukta) "q" else "k"
            0x16 -> if (nukta) "kh" else "kh"
            0x17 -> if (nukta) "gh" else "g"
            0x18 -> "gh"
            0x19 -> "ng"
            0x1A -> "ch"
            0x1B -> "chh"
            0x1C -> if (nukta) "z" else "j"
            0x1D -> "jh"
            0x1E -> "ny"
            0x1F -> "t"
            0x20 -> "th"
            0x21 -> if (nukta) "r" else "d"
            0x22 -> if (nukta) "rh" else "dh"
            0x23 -> "n"
            0x24 -> "t"
            0x25 -> "th"
            0x26 -> "d"
            0x27 -> "dh"
            0x28 -> "n"
            0x29 -> "n"
            0x2A -> "p"
            0x2B -> if (nukta) "f" else "ph"
            0x2C -> "b"
            0x2D -> "bh"
            0x2E -> "m"
            0x2F -> "y"
            0x30 -> "r"
            0x31 -> "r"
            0x32 -> "l"
            0x33 -> if (nukta) "sh" else "l"
            0x34 -> "l"
            0x35 -> "v"
            0x36 -> "sh"
            0x37 -> "sh"
            0x38 -> "s"
            0x39 -> "h"
            0x58 -> "q"
            0x59 -> "kh"
            0x5A -> "gh"
            0x5B -> "z"
            0x5C -> "r"
            0x5D -> "rh"
            0x5E -> "f"
            0x5F -> "y"
            0x79 -> "z"
            0x7A -> "y"
            0x7B -> "g"
            0x7E -> "d"
            else -> ""
        }

        fun independentVowelSound(offset: Int): String = when (offset) {
            0x04, 0x05 -> "a"
            0x06 -> "aa"
            0x07 -> "i"
            0x08 -> "ee"
            0x09 -> "u"
            0x0A -> "oo"
            0x0B -> "ri"
            0x0D, 0x0E, 0x0F -> "e"
            0x10 -> "ai"
            0x11, 0x12, 0x13 -> "o"
            0x14 -> "au"
            else -> ""
        }

        fun matraSound(offset: Int): String = when (offset) {
            0x3E -> "a"
            0x3F -> "i"
            0x40 -> "ee"
            0x41 -> "u"
            0x42 -> "oo"
            0x43, 0x44 -> "ri"
            0x45, 0x46, 0x47 -> "e"
            0x48 -> "ai"
            0x49, 0x4A, 0x4B -> "o"
            0x4C -> "au"
            else -> ""
        }

        val sb = StringBuilder()
        var i = 0
        val n = text.length
        while (i < n) {
            val ch = text[i]
            val code = ch.code
            val blockBase = when (code) {
                in 0x0900..0x097F -> 0x0900 // Devanagari
                in 0x0980..0x09FF -> 0x0980 // Bengali
                in 0x0A00..0x0A7F -> 0x0A00 // Gurmukhi
                in 0x0A80..0x0AFF -> 0x0A80 // Gujarati
                else -> -1
            }

            if (blockBase == -1) {
                sb.append(ch)
                i++
                continue
            }

            val offset = code - blockBase
            // Gurmukhi Adhak (0x71) doubles the next consonant, Tippi (0x70) nasalizes
            if (offset == 0x70 || offset == 0x01 || offset == 0x02) {
                sb.append("n")
                i++
                continue
            }
            if (offset == 0x03) {
                sb.append("h")
                i++
                continue
            }
            if (offset == 0x71 || offset == 0x3C || offset == 0x4D) {
                i++
                continue
            }
            if (offset in 0x66..0x6F) {
                sb.append(('0'.code + (offset - 0x66)).toChar())
                i++
                continue
            }
            if (offset == 0x64 || offset == 0x65) {
                sb.append(" ")
                i++
                continue
            }

            if (offset in 0x04..0x14) {
                sb.append(independentVowelSound(offset))
                i++
                continue
            }

            if (isIndicConsonant(offset)) {
                var nextIdx = i + 1
                var hasNukta = false
                if (nextIdx < n && (text[nextIdx].code - blockBase) == 0x3C) {
                    hasNukta = true
                    nextIdx++
                }
                // Skip Gurmukhi Adhak if attached after consonant
                if (nextIdx < n && (text[nextIdx].code - blockBase) == 0x71) {
                    nextIdx++
                }

                val cSound = consonantSound(offset, hasNukta)
                sb.append(cSound)

                val nextOffset = if (nextIdx < n && (text[nextIdx].code - blockBase) in 0x00..0x7F) {
                    text[nextIdx].code - blockBase
                } else {
                    -1
                }

                when {
                    nextOffset == 0x4D -> {
                        // Virama / Halant suppresses inherent vowel
                        i = nextIdx + 1
                    }
                    nextOffset in 0x3E..0x4C -> {
                        sb.append(matraSound(nextOffset))
                        i = nextIdx + 1
                    }
                    else -> {
                        // Inherent 'a' vowel: keep inside word, drop at word-end (natural Hindi/Punjabi schwa deletion)
                        val isWordEnd = nextIdx >= n ||
                            text[nextIdx].isWhitespace() ||
                            (text[nextIdx].code - blockBase) !in 0x00..0x7F ||
                            (text[nextIdx].code - blockBase) in 0x64..0x65
                        val isSingleLetterWord = (i == 0 || text[i - 1].isWhitespace()) && isWordEnd
                        if (!isWordEnd || isSingleLetterWord) {
                            sb.append("a")
                        }
                        i = nextIdx
                    }
                }
                continue
            }

            if (offset in 0x3E..0x4C) {
                sb.append(matraSound(offset))
                i++
                continue
            }

            i++
        }

        return sb.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    /**
     * Enforces user-requested Lyrics Language Priority:
     * 1st: Romanized (Hinglish)
     * 2nd: English (if Romanized is not available)
     * 3rd: Original (if neither Romanized nor English is available)
     * Also automatically generates a Romanized (Hinglish) variant when Indic-script lyrics are returned.
     */
    private fun prioritizeLyricsVariants(
        primary: LyricsData,
        extraVariants: List<LyricsVariant> = emptyList()
    ): LyricsData {
        if (primary.lines.isEmpty()) return primary

        val allVariants = mutableListOf<LyricsVariant>()
        allVariants.add(
            LyricsVariant(
                languageLabel = primary.languageLabel.ifBlank { detectLyricsScriptLabel(primary.lines) },
                lines = primary.lines,
                isSynced = primary.isSynced
            )
        )
        primary.alternateVariants.forEach { v ->
            if (v.lines.isNotEmpty()) allVariants.add(v)
        }
        extraVariants.forEach { v ->
            if (v.lines.isNotEmpty()) allVariants.add(v)
        }

        // If any variant is in an Indic script (Hindi/Punjabi/etc.) and no Romanized (Hinglish) variant exists yet,
        // generate a timestamp-synced Romanized (Hinglish) variant automatically!
        val hasRomanized = allVariants.any { lyricsLanguagePriority(it.languageLabel) == 0 }
        if (!hasRomanized) {
            val indicSource = allVariants
                .sortedByDescending { it.isSynced }
                .firstOrNull { containsIndicScript(it.lines) }
            if (indicSource != null) {
                val romanizedLines = indicSource.lines.map { line ->
                    line.copy(text = transliterateIndicLineToRomanizedHinglish(line.text))
                }
                allVariants.add(
                    0,
                    LyricsVariant(
                        languageLabel = "Romanized (Hinglish)",
                        lines = romanizedLines,
                        isSynced = indicSource.isSynced
                    )
                )
            }
        }

        // Deduplicate by language label (preferring synced variants when two have the same label)
        val deduplicated = allVariants
            .sortedWith(
                compareBy<LyricsVariant> { lyricsLanguagePriority(it.languageLabel) }
                    .thenByDescending { it.isSynced }
            )
            .distinctBy { it.languageLabel.lowercase() }

        val bestDefault = deduplicated.first()
        val remainingAlternates = deduplicated.drop(1)

        return LyricsData(
            lines = bestDefault.lines,
            isSynced = bestDefault.isSynced,
            languageLabel = bestDefault.languageLabel,
            alternateVariants = remainingAlternates
        )
    }

    private suspend fun searchLrclibCatalogWithVariants(
        rawTitle: String,
        cleanTitle: String,
        primaryArtist: String,
        fullArtist: String,
        durationSec: Long
    ): Pair<LyricsData?, List<LyricsVariant>> {
        val artistCandidates = extractAllArtistCandidatesForLyrics(rawTitle, fullArtist.ifBlank { primaryArtist })
        val primaryArtistTokens = lyricsArtistTokens(primaryArtist)
        val allTargetArtistTokens = buildSet {
            addAll(primaryArtistTokens)
            artistCandidates.forEach { addAll(lyricsArtistTokens(it)) }
        }

        val searchUrls = buildList {
            if (cleanTitle.isNotBlank() && primaryArtist.isNotBlank()) {
                add("https://lrclib.net/api/search?track_name=${urlEncode(cleanTitle)}&artist_name=${urlEncode(primaryArtist)}")
                add("https://lrclib.net/api/search?q=${urlEncode("$cleanTitle $primaryArtist")}")
                add("https://lrclib.net/api/search?q=${urlEncode("$cleanTitle $primaryArtist romanized")}")
            }
            for (altArtist in artistCandidates.drop(1).take(2)) {
                if (cleanTitle.isNotBlank() && altArtist.isNotBlank() && !altArtist.equals(primaryArtist, ignoreCase = true)) {
                    add("https://lrclib.net/api/search?q=${urlEncode("$cleanTitle $altArtist")}")
                }
            }
            // Fallback track_name query (still strictly gated by scoreAndVerifyLyricsCandidate artist verification!)
            if (cleanTitle.isNotBlank()) {
                add("https://lrclib.net/api/search?track_name=${urlEncode(cleanTitle)}")
            }
        }.distinct()

        val collectedParsed = mutableListOf<Pair<Int, LyricsData>>()

        for (url in searchUrls) {
            runCatching {
                val text = httpGet(url)
                val arr = jsonParser.parseToJsonElement(text).arrOrNull() ?: return@runCatching
                val candidates = arr.mapNotNull { it.objOrNull() }
                if (candidates.isEmpty()) return@runCatching

                val scored = candidates.mapNotNull { item ->
                    val itemTitle = item["trackName"].strOrNull() ?: item["name"].strOrNull() ?: return@mapNotNull null
                    val itemArtist = item["artistName"].strOrNull().orEmpty()
                    val itemAlbum = item["albumName"].strOrNull().orEmpty()
                    val hasSynced = !item["syncedLyrics"].strOrNull().isNullOrBlank()
                    val hasPlain = !item["plainLyrics"].strOrNull().isNullOrBlank()
                    if (!hasSynced && !hasPlain) return@mapNotNull null

                    val itemDurationSec = item["duration"].longValOrNull()
                        ?: item["duration"].strOrNull()?.toDoubleOrNull()?.toLong()

                    val score = scoreAndVerifyLyricsCandidate(
                        targetCleanTitle = cleanTitle,
                        primaryArtistTokens = primaryArtistTokens,
                        allTargetArtistTokens = allTargetArtistTokens,
                        candidateTitle = itemTitle,
                        candidateArtist = itemArtist,
                        candidateAlbum = itemAlbum,
                        targetDurationSec = durationSec,
                        candidateDurationSec = itemDurationSec,
                        hasSynced = hasSynced
                    ) ?: return@mapNotNull null

                    Triple(item, itemTitle, score)
                }.sortedByDescending { it.third }

                for ((bestObj, itemTitle, score) in scored.take(6)) {
                    parseLrclibObjectToLyricsData(bestObj)?.let { parsed ->
                        val label = detectLyricsScriptLabel(parsed.lines, itemTitle)
                        val langBonus = when (lyricsLanguagePriority(label)) {
                            0 -> 45 // Prefer Romanized (Hinglish)
                            1 -> 25 // Prefer English next
                            else -> 0
                        }
                        collectedParsed.add((score + langBonus) to parsed.copy(languageLabel = label))
                    }
                }
            }
            if (collectedParsed.any { it.second.isSynced && lyricsLanguagePriority(it.second.languageLabel) == 0 }) break
        }

        if (collectedParsed.isEmpty()) return null to emptyList()
        val sortedByBestScore = collectedParsed.sortedWith(
            compareBy<Pair<Int, LyricsData>> { lyricsLanguagePriority(it.second.languageLabel) }
                .thenByDescending { it.first }
        )
        val primary = sortedByBestScore.first().second
        val distinctVariants = sortedByBestScore
            .drop(1)
            .map { it.second }
            .filter { !it.languageLabel.equals(primary.languageLabel, ignoreCase = true) }
            .distinctBy { it.languageLabel.lowercase() }
            .map { variantData ->
                LyricsVariant(
                    languageLabel = variantData.languageLabel,
                    lines = variantData.lines,
                    isSynced = variantData.isSynced
                )
            }
        return primary to distinctVariants
    }

    private suspend fun fetchJioSaavnLyrics(
        songId: String,
        rawTitle: String,
        cleanTitle: String,
        primaryArtist: String,
        fullArtist: String,
        durationSec: Long
    ): LyricsData? {
        val primaryArtistTokens = lyricsArtistTokens(primaryArtist)
        val allTargetArtistTokens = buildSet {
            addAll(primaryArtistTokens)
            extractAllArtistCandidatesForLyrics(rawTitle, fullArtist.ifBlank { primaryArtist })
                .forEach { addAll(lyricsArtistTokens(it)) }
        }

        val candidateSaavnIds = mutableListOf<String>()
        if (songId.startsWith("saavn_")) {
            val directId = songId.removePrefix("saavn_").trim()
            if (directId.isNotBlank()) candidateSaavnIds.add(directId)
        }

        if (cleanTitle.isNotBlank()) {
            val searchQueries = buildList {
                if (primaryArtist.isNotBlank()) add("$cleanTitle $primaryArtist")
                if (fullArtist.isNotBlank() && !fullArtist.equals(primaryArtist, ignoreCase = true)) {
                    add("$cleanTitle $fullArtist")
                }
            }.distinct()

            for (q in searchQueries) {
                val searched = runCatching {
                    searchJioSaavnCatalog(q, page = 1, limit = 15)
                }.getOrDefault(emptyList())

                val verifiedMatches = searched.mapNotNull { candidate ->
                    val candScore = scoreAndVerifyLyricsCandidate(
                        targetCleanTitle = cleanTitle,
                        primaryArtistTokens = primaryArtistTokens,
                        allTargetArtistTokens = allTargetArtistTokens,
                        candidateTitle = candidate.title,
                        candidateArtist = candidate.artist,
                        candidateAlbum = candidate.albumName,
                        targetDurationSec = durationSec,
                        candidateDurationSec = candidate.durationMs / 1000L,
                        hasSynced = false
                    ) ?: return@mapNotNull null
                    candidate.id.removePrefix("saavn_").trim() to candScore
                }.sortedByDescending { it.second }

                verifiedMatches.take(4).forEach { (id, _) ->
                    if (id.isNotBlank()) candidateSaavnIds.add(id)
                }
                if (candidateSaavnIds.size >= 3) break
            }
        }

        for (saavnId in candidateSaavnIds.distinct().take(4)) {
            val endpoints = listOf(
                "https://www.jiosaavn.com/api.php?__call=lyrics.getLyrics&ctx=web6dot0&api_version=4&_format=json&_marker=0&lyrics_id=$saavnId",
                "https://saavn.sumit.co/api/songs/$saavnId/lyrics",
                "https://saavn.dev/api/songs/$saavnId/lyrics",
                "https://jiosaavn-api-privatecvc2.vercel.app/lyrics?id=$saavnId"
            )
            for (endpoint in endpoints) {
                val lyricsData = runCatching {
                    val text = httpGet(endpoint)
                    val root = jsonParser.parseToJsonElement(text).objOrNull() ?: return@runCatching null
                    val rawLyrics = root["lyrics"].strOrNull()
                        ?: root["data"].objOrNull()?.get("lyrics").strOrNull()
                        ?: root["lyrics_snippet"].strOrNull()
                        ?: return@runCatching null
                    val lines = parsePlainLyricsLines(rawLyrics)
                    if (lines.size >= 4) {
                        LyricsData(
                            lines = lines,
                            isSynced = false,
                            languageLabel = detectLyricsScriptLabel(lines)
                        )
                    } else null
                }.getOrNull()
                if (lyricsData != null) return lyricsData
            }
        }
        return null
    }

    /**
     * Queries NetEase Cloud Music LRC catalog with strict title + artist verification.
     */
    private suspend fun fetchNetEaseVerifiedLyrics(
        rawTitle: String,
        cleanTitle: String,
        primaryArtist: String,
        fullArtist: String,
        durationSec: Long
    ): LyricsData? {
        if (cleanTitle.isBlank()) return null
        val primaryArtistTokens = lyricsArtistTokens(primaryArtist)
        val allTargetArtistTokens = buildSet {
            addAll(primaryArtistTokens)
            extractAllArtistCandidatesForLyrics(rawTitle, fullArtist.ifBlank { primaryArtist })
                .forEach { addAll(lyricsArtistTokens(it)) }
        }
        if (allTargetArtistTokens.isEmpty()) return null

        return runCatching {
            val query = "$cleanTitle $primaryArtist".trim()
            val searchUrl = "https://music.163.com/api/search/get/web?s=${urlEncode(query)}&type=1&offset=0&total=true&limit=10"
            val searchJson = httpGet(
                url = searchUrl,
                headers = mapOf(
                    "Referer" to "https://music.163.com",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
            )
            val songsArr = jsonParser.parseToJsonElement(searchJson).objOrNull()
                ?.get("result").objOrNull()
                ?.get("songs").arrOrNull()
                ?: return@runCatching null

            val verifiedSongIds = songsArr.mapNotNull { elem ->
                val obj = elem.objOrNull() ?: return@mapNotNull null
                val id = obj["id"].longValOrNull() ?: return@mapNotNull null
                val name = obj["name"].strOrNull() ?: return@mapNotNull null
                val artistsStr = obj["artists"].arrOrNull()
                    ?.mapNotNull { it.objOrNull()?.get("name").strOrNull() }
                    ?.joinToString(", ")
                    .orEmpty()
                val albumStr = obj["album"].objOrNull()?.get("name").strOrNull().orEmpty()
                val durSec = (obj["duration"].longValOrNull() ?: 0L) / 1000L

                val score = scoreAndVerifyLyricsCandidate(
                    targetCleanTitle = cleanTitle,
                    primaryArtistTokens = primaryArtistTokens,
                    allTargetArtistTokens = allTargetArtistTokens,
                    candidateTitle = name,
                    candidateArtist = artistsStr,
                    candidateAlbum = albumStr,
                    targetDurationSec = durationSec,
                    candidateDurationSec = durSec.takeIf { it > 0L },
                    hasSynced = true
                ) ?: return@mapNotNull null

                id to score
            }.sortedByDescending { it.second }

            for ((netEaseId, _) in verifiedSongIds.take(2)) {
                val lyricUrl = "https://music.163.com/api/song/lyric?os=pc&id=$netEaseId&lv=-1&kv=-1&tv=-1"
                val lyricJson = httpGet(
                    url = lyricUrl,
                    headers = mapOf("Referer" to "https://music.163.com")
                )
                val lyricRoot = jsonParser.parseToJsonElement(lyricJson).objOrNull() ?: continue
                val lrcRaw = lyricRoot["lrc"].objOrNull()?.get("lyric").strOrNull()
                if (!lrcRaw.isNullOrBlank()) {
                    val syncedLines = parseLrcString(lrcRaw)
                    if (syncedLines.size >= 4) {
                        return@runCatching LyricsData(
                            lines = syncedLines,
                            isSynced = true,
                            languageLabel = detectLyricsScriptLabel(syncedLines)
                        )
                    }
                    val plainLines = parsePlainLyricsLines(lrcRaw)
                    if (plainLines.size >= 4) {
                        return@runCatching LyricsData(
                            lines = plainLines,
                            isSynced = false,
                            languageLabel = detectLyricsScriptLabel(plainLines)
                        )
                    }
                }
            }
            null
        }.getOrNull()
    }

    /**
     * Resolves official YouTube Music / YouTube caption tracks (`captionTracks` in `youtubei/v1/player`)
     * for a strictly verified title + artist match so regional/Indian & indie songs with official
     * video captions get timestamp-synced lyrics matching that exact artist.
     */
    private suspend fun fetchYouTubeVerifiedCaptionsLyrics(
        songId: String,
        rawTitle: String,
        cleanTitle: String,
        primaryArtist: String,
        fullArtist: String,
        durationSec: Long
    ): LyricsData? {
        if (cleanTitle.isBlank()) return null
        val primaryArtistTokens = lyricsArtistTokens(primaryArtist)
        val allTargetArtistTokens = buildSet {
            addAll(primaryArtistTokens)
            extractAllArtistCandidatesForLyrics(rawTitle, fullArtist.ifBlank { primaryArtist })
                .forEach { addAll(lyricsArtistTokens(it)) }
        }

        val candidateVideoIds = mutableListOf<String>()
        val isDirectYtId = songId.isNotBlank() &&
            !songId.startsWith("saavn_") &&
            !songId.startsWith("deezer_") &&
            !songId.startsWith("itunes_") &&
            !songId.startsWith("spotify_")
        if (isDirectYtId) {
            candidateVideoIds.add(songId)
        }

        val ytSearchResults = runCatching {
            searchInnerTubeWebRemix("$cleanTitle $primaryArtist".trim()) +
                searchYouTubeVideoCatalog("$cleanTitle $primaryArtist".trim())
        }.getOrDefault(emptyList())

        val verifiedYt = ytSearchResults.mapNotNull { item ->
            val score = scoreAndVerifyLyricsCandidate(
                targetCleanTitle = cleanTitle,
                primaryArtistTokens = primaryArtistTokens,
                allTargetArtistTokens = allTargetArtistTokens,
                candidateTitle = item.title,
                candidateArtist = item.artist,
                candidateAlbum = item.albumName,
                targetDurationSec = durationSec,
                candidateDurationSec = item.durationMs / 1000L,
                hasSynced = true
            ) ?: return@mapNotNull null
            item.id to score
        }.sortedByDescending { it.second }

        verifiedYt.take(2).forEach { (vid, _) -> candidateVideoIds.add(vid) }

        for (videoId in candidateVideoIds.distinct().take(2)) {
            val captionLyrics = runCatching {
                val payload = buildJsonObject {
                    put("videoId", videoId)
                    put("racyCheckOk", true)
                    put("contentCheckOk", true)
                    putJsonObject("context") {
                        putJsonObject("client") {
                            put("clientName", "WEB")
                            put("clientVersion", "2.20241118.01.00")
                            put("hl", "en")
                            put("gl", "IN")
                        }
                    }
                }
                val playerText = httpPostJson(
                    url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                    jsonBody = payload.toString(),
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    )
                )
                val root = jsonParser.parseToJsonElement(playerText).objOrNull() ?: return@runCatching null
                val videoDetails = root["videoDetails"].objOrNull()
                val ytTitle = videoDetails?.get("title").strOrNull().orEmpty()
                val ytAuthor = videoDetails?.get("author").strOrNull()
                    ?.replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
                    .orEmpty()

                // Double-check videoDetails title + author so we never use captions from an unrelated video
                if (ytTitle.isNotBlank() && allTargetArtistTokens.isNotEmpty()) {
                    val detailScore = scoreAndVerifyLyricsCandidate(
                        targetCleanTitle = cleanTitle,
                        primaryArtistTokens = primaryArtistTokens,
                        allTargetArtistTokens = allTargetArtistTokens,
                        candidateTitle = ytTitle,
                        candidateArtist = ytAuthor,
                        targetDurationSec = durationSec,
                        candidateDurationSec = videoDetails?.get("lengthSeconds").strOrNull()?.toLongOrNull()
                    )
                    if (detailScore == null) return@runCatching null
                }

                val captionTracks = root["captions"].objOrNull()
                    ?.get("playerCaptionsTracklistRenderer").objOrNull()
                    ?.get("captionTracks").arrOrNull()
                    ?.mapNotNull { it.objOrNull() }
                    .orEmpty()
                if (captionTracks.isEmpty()) return@runCatching null

                // Prefer manual/artist-uploaded lyric caption tracks over ASR tracks
                val chosenTrack = captionTracks.firstOrNull {
                    it["kind"].strOrNull() != "asr"
                } ?: captionTracks.first()

                val baseUrl = chosenTrack["baseUrl"].strOrNull() ?: return@runCatching null
                val json3Url = if (baseUrl.contains("fmt=")) {
                    baseUrl.replace(Regex("fmt=[^&]+"), "fmt=json3")
                } else {
                    "$baseUrl&fmt=json3"
                }

                val captionBody = httpGet(json3Url)
                val captionRoot = jsonParser.parseToJsonElement(captionBody).objOrNull() ?: return@runCatching null
                val events = captionRoot["events"].arrOrNull() ?: return@runCatching null

                val parsedLines = mutableListOf<LyricLine>()
                for (evElem in events) {
                    val ev = evElem.objOrNull() ?: continue
                    val startMs = ev["tStartMs"].longValOrNull() ?: continue
                    val segs = ev["segs"].arrOrNull() ?: continue
                    val rawLine = segs.mapNotNull { it.objOrNull()?.get("utf8").strOrNull() }
                        .joinToString("")
                        .replace("\n", " ")
                        .replace("♪", "")
                        .replace("♫", "")
                        .trim()
                    if (rawLine.length < 2) continue
                    if (rawLine.startsWith("[") && rawLine.endsWith("]")) continue
                    if (rawLine.contains("http://", ignoreCase = true) ||
                        rawLine.contains("https://", ignoreCase = true) ||
                        rawLine.contains("subscribe", ignoreCase = true)
                    ) continue

                    val cleanLine = decodeHtmlEntities(rawLine)
                    if (parsedLines.lastOrNull()?.text.equals(cleanLine, ignoreCase = true)) continue
                    parsedLines.add(LyricLine(startTimeMs = startMs, text = cleanLine))
                }

                if (parsedLines.size >= 6) {
                    LyricsData(
                        lines = parsedLines,
                        isSynced = true,
                        languageLabel = detectLyricsScriptLabel(parsedLines)
                    )
                } else null
            }.getOrNull()

            if (captionLyrics != null) return captionLyrics
        }
        return null
    }

    private suspend fun fetchLyricsOvh(cleanTitle: String, primaryArtist: String): LyricsData? {
        if (cleanTitle.isBlank() || primaryArtist.isBlank()) return null
        return runCatching {
            val url = "https://api.lyrics.ovh/v1/${urlEncode(primaryArtist)}/${urlEncode(cleanTitle)}"
            val text = httpGet(url)
            val root = jsonParser.parseToJsonElement(text).objOrNull() ?: return@runCatching null
            val rawLyrics = root["lyrics"].strOrNull() ?: return@runCatching null
            val lines = parsePlainLyricsLines(rawLyrics)
            if (lines.size >= 2) {
                LyricsData(
                    lines = lines,
                    isSynced = false,
                    languageLabel = detectLyricsScriptLabel(lines)
                )
            } else null
        }.getOrNull()
    }

    /**
     * Multi-stage real lyrics resolver with strict Title + Artist verification and Default Language Priority:
     * Default Priority: 1. Romanized (Hinglish) -> 2. English -> 3. Original
     */
    suspend fun getSyncedLyrics(
        trackTitle: String,
        artist: String,
        durationSec: Long,
        songId: String = ""
    ): Result<LyricsData> = withContext(Dispatchers.IO) {
        runCatching {
            val primaryArtist = extractPrimaryArtistForLyrics(artist)
            val cleanTitle = cleanTrackTitleForLyrics(trackTitle, artist)
            val primaryArtistTokens = lyricsArtistTokens(primaryArtist)
            val allTargetArtistTokens = buildSet {
                addAll(primaryArtistTokens)
                extractAllArtistCandidatesForLyrics(trackTitle, artist.ifBlank { primaryArtist })
                    .forEach { addAll(lyricsArtistTokens(it)) }
            }

            val durationBucket = if (durationSec > 0L) durationSec / 8L else 0L
            val cacheKey = "v3|${canonicalSongTitle(cleanTitle)}|${allTargetArtistTokens.sorted().joinToString("_")}|$durationBucket"

            lyricsCache[cacheKey]?.let { cached ->
                if (cached.lines.isNotEmpty()) return@runCatching cached
            }

            // Stage 1: LRCLIB Exact endpoint with cleaned title, primary artist & duration (plus strict artist+title check)
            val exactResult = runCatching {
                val url = buildString {
                    append("https://lrclib.net/api/get?track_name=")
                    append(urlEncode(cleanTitle))
                    append("&artist_name=")
                    append(urlEncode(primaryArtist))
                    if (durationSec > 0) {
                        append("&duration=")
                        append(durationSec)
                    }
                }
                val responseText = httpGet(url)
                val obj = jsonParser.parseToJsonElement(responseText).objOrNull() ?: return@runCatching null
                val objTitle = obj["trackName"].strOrNull() ?: obj["name"].strOrNull() ?: cleanTitle
                val objArtist = obj["artistName"].strOrNull().orEmpty()
                val objAlbum = obj["albumName"].strOrNull().orEmpty()
                val objDurSec = obj["duration"].longValOrNull()
                    ?: obj["duration"].strOrNull()?.toDoubleOrNull()?.toLong()

                val verifiedScore = scoreAndVerifyLyricsCandidate(
                    targetCleanTitle = cleanTitle,
                    primaryArtistTokens = primaryArtistTokens,
                    allTargetArtistTokens = allTargetArtistTokens,
                    candidateTitle = objTitle,
                    candidateArtist = objArtist,
                    candidateAlbum = objAlbum,
                    targetDurationSec = durationSec,
                    candidateDurationSec = objDurSec,
                    hasSynced = !obj["syncedLyrics"].strOrNull().isNullOrBlank()
                )
                if (verifiedScore == null) return@runCatching null

                parseLrclibObjectToLyricsData(obj)?.let { data ->
                    data.copy(languageLabel = detectLyricsScriptLabel(data.lines, objTitle))
                }
            }.getOrNull()

            // Stage 2: LRCLIB Search (strictly verified against song title + artist & duration proximity)
            val (searchResult, searchVariants) = searchLrclibCatalogWithVariants(
                rawTitle = trackTitle,
                cleanTitle = cleanTitle,
                primaryArtist = primaryArtist,
                fullArtist = artist,
                durationSec = durationSec
            )

            val primaryLrclib = when {
                exactResult != null && exactResult.isSynced -> exactResult
                searchResult != null && searchResult.isSynced -> searchResult
                exactResult != null -> exactResult
                searchResult != null -> searchResult
                else -> null
            }

            if (primaryLrclib != null) {
                val extraVariants = buildList {
                    if (exactResult != null && exactResult !== primaryLrclib) {
                        add(
                            LyricsVariant(
                                languageLabel = exactResult.languageLabel,
                                lines = exactResult.lines,
                                isSynced = exactResult.isSynced
                            )
                        )
                    }
                    if (searchResult != null && searchResult !== primaryLrclib) {
                        add(
                            LyricsVariant(
                                languageLabel = searchResult.languageLabel,
                                lines = searchResult.lines,
                                isSynced = searchResult.isSynced
                            )
                        )
                    }
                    addAll(searchVariants)
                }

                val prioritized = prioritizeLyricsVariants(primaryLrclib, extraVariants)
                lyricsCache[cacheKey] = prioritized
                return@runCatching prioritized
            }

            // Stage 3: Parallel verified lookup across NetEase LRC, Official JioSaavn Lyrics, YouTube Captions, and Lyrics.ovh
            val fallbackLyrics = coroutineScope {
                val netEaseDef = async {
                    fetchNetEaseVerifiedLyrics(trackTitle, cleanTitle, primaryArtist, artist, durationSec)
                }
                val saavnDef = async {
                    fetchJioSaavnLyrics(songId, trackTitle, cleanTitle, primaryArtist, artist, durationSec)
                }
                val ytCaptionsDef = async {
                    fetchYouTubeVerifiedCaptionsLyrics(songId, trackTitle, cleanTitle, primaryArtist, artist, durationSec)
                }
                val ovhDef = async {
                    fetchLyricsOvh(cleanTitle, primaryArtist)
                }

                val netEase = netEaseDef.await()
                val saavn = saavnDef.await()
                val ytCaptions = ytCaptionsDef.await()
                val ovh = ovhDef.await()

                val allCandidates = listOfNotNull(netEase, ytCaptions, saavn, ovh)
                    .filter { it.lines.isNotEmpty() }

                if (allCandidates.isEmpty()) {
                    null
                } else {
                    val bestBase = allCandidates.firstOrNull { it.isSynced } ?: allCandidates.first()
                    val otherVariants = allCandidates
                        .filter { it !== bestBase }
                        .map {
                            LyricsVariant(
                                languageLabel = it.languageLabel,
                                lines = it.lines,
                                isSynced = it.isSynced
                            )
                        }
                    prioritizeLyricsVariants(bestBase, otherVariants)
                }
            }

            if (fallbackLyrics != null && fallbackLyrics.lines.isNotEmpty()) {
                lyricsCache[cacheKey] = fallbackLyrics
                return@runCatching fallbackLyrics
            }

            LyricsData(lines = emptyList(), isSynced = false)
        }
    }

    fun parseLrcString(lrcContent: String): List<LyricLine> {
        val tagRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
        val lines = mutableListOf<LyricLine>()

        for (rawLine in lrcContent.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            val matches = tagRegex.findAll(trimmed).toList()
            if (matches.isEmpty()) continue

            val lastTagEnd = matches.last().range.last + 1
            val lyricText = decodeHtmlEntities(trimmed.substring(lastTagEnd).trim()).ifEmpty { "♪" }

            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionStr = match.groupValues[3]
                val millis = when (fractionStr.length) {
                    1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
                    3 -> fractionStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val timestampMs = (minutes * 60_000L) + (seconds * 1_000L) + millis
                lines.add(LyricLine(startTimeMs = timestampMs, text = lyricText))
            }
        }
        return lines.sortedBy { it.startTimeMs }
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun parseDurationMs(durationText: String): Long {
        val parts = durationText.trim().split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> 210_000L
        }
    }
}
