package com.example.data.repository

import com.example.data.local.FavoriteSongEntity
import com.example.data.local.ImportedPlaylistEntity
import com.example.data.local.MusicDao
import com.example.data.local.OfflineSongEntity
import com.example.data.local.RecentlyPlayedSongEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.model.AlbumDetailData
import com.example.data.model.AlbumReleaseItem
import com.example.data.model.ArtistProfileData
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
import com.example.data.model.TimeOfDayShelfData
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

    private val resolvedStreamCache = ConcurrentHashMap<String, String>()
    private val songMetadataCache = ConcurrentHashMap<String, SongItem>()
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
     * Returns true only when [url] is a non-blank, full-length audio stream (not a 30s preview clip).
     */
    fun isFullLengthStreamUrl(url: String): Boolean {
        return url.isNotBlank() && !isPreviewClipUrl(url)
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
            val targetSuffix = if (prefer320) "_320.mp4" else "_160.mp4"
            rawUrl
                .replace("_96_p.mp4", targetSuffix)
                .replace("_96.mp4", targetSuffix)
                .replace("_160.mp4", targetSuffix)
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
        withContext(Dispatchers.IO) {
            val reqBuilder = Request.Builder()
                .url(url)
                .get()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept", "application/json, text/plain, */*")
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }

            okHttpClient.newCall(reqBuilder.build()).awaitResponse().use { response ->
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
    ): String = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val reqBuilder = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(mediaType))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }

        okHttpClient.newCall(reqBuilder.build()).awaitResponse().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            response.body?.string().orEmpty()
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
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
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

        combinedFinal.also { finalQueue ->
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
     */
    suspend fun fetchSearchAutocompleteSuggestions(prefix: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = prefix.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val suggestions = LinkedHashSet<String>()

        // 1. YouTube Music / Google Music fast suggest endpoint
        runCatching {
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=${urlEncode(trimmed)}"
            val text = httpGet(url)
            val rootArr = jsonParser.parseToJsonElement(text).arrOrNull()
            val itemsArr = rootArr?.getOrNull(1)?.arrOrNull()
            itemsArr?.forEach { elem ->
                val raw = (elem as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                val cleaned = raw
                    .replace(Regex("\\b(lyrics|official video|video song|full video|mp3|download|status)\\b", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (cleaned.length in 2..42 && !cleaned.equals(trimmed, ignoreCase = true)) {
                    suggestions.add(cleaned)
                }
            }
        }

        // 2. Supplement with cached song/artist matches if needed
        if (suggestions.size < 5) {
            val lower = trimmed.lowercase()
            songMetadataCache.values.forEach { song ->
                if (song.title.lowercase().startsWith(lower)) {
                    suggestions.add(song.title)
                }
                val primary = extractPrimaryArtistForLyrics(song.artist)
                if (primary.lowercase().startsWith(lower)) {
                    suggestions.add(primary)
                }
            }
        }

        suggestions.take(7).toList()
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
        OnboardingGenreOption("bollywood_hits", "Bollywood Hits", "Hindi Film & Romance", "Bollywood Hot Hits Hindi"),
        OnboardingGenreOption("punjabi_pop", "Punjabi Pop", "Bhangra & Urban Desi", "Diljit Dosanjh Karan Aujla Punjabi Hits"),
        OnboardingGenreOption("desi_hiphop", "Desi Hip-Hop", "Indian Rap & Bars", "Divine KRSNA Seedhe Maut Desi Hip Hop"),
        OnboardingGenreOption("indian_indie", "Indian Indie", "Acoustic & Unplugged", "Anuv Jain Prateek Kuhad Indian Indie"),
        OnboardingGenreOption("tamil_telugu", "Tamil & Telugu", "Kollywood & Tollywood", "Anirudh Ravichander Tamil Telugu Hits"),
        OnboardingGenreOption("sufi_soul", "Sufi & Soul", "Ghazal, Folk & Sufi", "Rahat Fateh Ali Khan Sufi Hits"),
        OnboardingGenreOption("global_pop", "Global Pop", "Charting Pop Anthems", "Today Top Hits Pop Global"),
        OnboardingGenreOption("hiphop_rap", "Hip-Hop & Rap", "Global Trap & Rap", "RapCaviar Drake Kendrick Lamar"),
        OnboardingGenreOption("rnb_soul", "R&B & Soul", "Late Night & Smooth", "The Weeknd SZA R&B Hits"),
        OnboardingGenreOption("lofi_chill", "Lo-Fi & Chill", "Study, Focus & Vibes", "Lofi Beats Chill Study"),
        OnboardingGenreOption("electronic_edm", "Electronic & EDM", "House, Synth & Dance", "Electronic Dance Hits Calvin Harris"),
        OnboardingGenreOption("rock_alt", "Rock & Alt", "Indie Rock & Anthems", "Coldplay Imagine Dragons Rock Hits")
    )

    fun getOnboardingArtists(): List<OnboardingArtistOption> = listOf(
        OnboardingArtistOption("Arijit Singh", "India • Bollywood"),
        OnboardingArtistOption("Diljit Dosanjh", "India • Punjabi"),
        OnboardingArtistOption("Anuv Jain", "India • Indie"),
        OnboardingArtistOption("AP Dhillon", "India • Punjabi"),
        OnboardingArtistOption("Pritam", "India • Bollywood"),
        OnboardingArtistOption("Shreya Ghoshal", "India • Playback"),
        OnboardingArtistOption("Divine", "India • Desi Rap"),
        OnboardingArtistOption("A.R. Rahman", "India • Classical & Pop"),
        OnboardingArtistOption("The Weeknd", "Global • R&B / Pop"),
        OnboardingArtistOption("Taylor Swift", "Global • Pop"),
        OnboardingArtistOption("Drake", "Global • Hip-Hop"),
        OnboardingArtistOption("Billie Eilish", "Global • Alt Pop"),
        OnboardingArtistOption("Dua Lipa", "Global • Dance Pop"),
        OnboardingArtistOption("Kendrick Lamar", "Global • Rap"),
        OnboardingArtistOption("Coldplay", "Global • Alt Rock"),
        OnboardingArtistOption("Bruno Mars", "Global • Pop & Funk")
    )

    suspend fun hydrateArtistPortraitUrls(
        artists: List<OnboardingArtistOption>
    ): List<OnboardingArtistOption> = withContext(Dispatchers.IO) {
        coroutineScope {
            artists.map { artist ->
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

    /**
     * Live search across Official JioSaavn (full-length 320kbps MP4/AAC), YouTube Music InnerTube, Apple iTunes, and Deezer.
     * Ranks results by query relevance and prioritizes full-length playable streams.
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

        val combinedResults = coroutineScope {
            val saavnDeferred = async {
                runCatching { searchJioSaavnCatalog(trimmed) }.getOrDefault(emptyList())
            }
            val ytDeferred = async {
                runCatching { searchInnerTubeWebRemix(trimmed) }.getOrDefault(emptyList())
            }
            val itunesDeferred = async {
                runCatching { searchITunesCatalog(trimmed) }.getOrDefault(emptyList())
            }
            val deezerDeferred = async {
                runCatching { searchDeezerCatalog(trimmed) }.getOrDefault(emptyList())
            }

            val saavnSongs = saavnDeferred.await()
            val ytSongs = ytDeferred.await()
            val itunesSongs = itunesDeferred.await()
            val deezerSongs = deezerDeferred.await()

            // Attach verified full-length JioSaavn 320kbps streams to InnerTube / iTunes / Deezer results where matched
            val enrichWithFullSaavn = { song: SongItem ->
                val matched = findBestMatchingTrack(song.title, song.artist, saavnSongs)
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

            val enrichedYt = ytSongs.map(enrichWithFullSaavn)
            val enrichedItunes = itunesSongs.map(enrichWithFullSaavn)
            val enrichedDeezer = deezerSongs.map(enrichWithFullSaavn)

            val allCandidates = (saavnSongs + enrichedYt + enrichedItunes + enrichedDeezer)
                .distinctBy { "${normalizeToken(it.title)}|${normalizeToken(it.artist)}" }

            val scored = allCandidates.map { song -> song to scoreSongRelevance(song, trimmed) }
            val positiveMatches = scored.filter { (_, score) -> score > 0 }
            (if (positiveMatches.isNotEmpty()) positiveMatches else scored)
                .sortedByDescending { (_, score) -> score }
                .map { (song, _) -> song }
        }

        combinedResults.forEach { songMetadataCache[it.id] = it }
        emit(combinedResults)
    }.flowOn(Dispatchers.IO)

    internal fun scoreSongRelevance(song: SongItem, rawQuery: String): Int {
        val normQuery = normalizeToken(rawQuery)
        if (normQuery.isEmpty()) return 1

        val normTitle = normalizeToken(song.title)
        val normArtist = normalizeToken(song.artist)
        val normCombined = "$normTitle $normArtist"
        val queryTokens = normQuery.split(" ").filter { it.isNotEmpty() }

        var score = 0
        if (normTitle == normQuery) score += 120
        else if (normTitle.startsWith(normQuery)) score += 85
        else if (normTitle.contains(normQuery)) score += 65

        if (normArtist == normQuery) score += 90
        else if (normArtist.contains(normQuery)) score += 55

        val matchedTokens = queryTokens.count { token -> normCombined.contains(token) }
        if (queryTokens.isNotEmpty() && matchedTokens == queryTokens.size) {
            score += 70
        } else {
            score += matchedTokens * 20
        }

        if (score == 0) return 0

        if (isFullLengthStreamUrl(song.streamUrl)) {
            score += 45
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

    private fun findBestMatchingTrack(
        targetTitle: String,
        targetArtist: String,
        candidates: List<SongItem>
    ): SongItem? {
        if (candidates.isEmpty()) return null
        val normTitle = canonicalSongTitle(targetTitle).ifBlank { normalizeToken(targetTitle) }
        val normArtist = normalizeToken(targetArtist)
        val hasKnownArtist = normArtist.isNotEmpty() &&
            normArtist != "unknown artist" &&
            normArtist != "spotify artist"

        candidates.firstOrNull { c ->
            val cTitle = canonicalSongTitle(c.title).ifBlank { normalizeToken(c.title) }
            val cArtist = normalizeToken(c.artist)
            val titleMatches = cTitle.isNotEmpty() &&
                (cTitle == normTitle || cTitle.contains(normTitle) || normTitle.contains(cTitle))
            val artistMatches = !hasKnownArtist ||
                cArtist.contains(normArtist) ||
                normArtist.contains(cArtist) ||
                normArtist.split(" ").any { token -> token.length > 2 && cArtist.contains(token) }
            titleMatches && artistMatches
        }?.let { return it }

        if (!hasKnownArtist) {
            candidates.firstOrNull { c ->
                val cTitle = canonicalSongTitle(c.title).ifBlank { normalizeToken(c.title) }
                cTitle.isNotEmpty() && (cTitle == normTitle || cTitle.contains(normTitle) || normTitle.contains(cTitle))
            }?.let { return it }
        }

        return null
    }

    private suspend fun searchInnerTubeWebRemix(query: String): List<SongItem> {
        val payload = buildJsonObject {
            put("query", query)
            put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA==")
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
            parseMusicResponsiveListItem(item)
        }.distinctBy { it.id }
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

        val title = titleRuns?.getOrNull(0).objOrNull()?.get("text").strOrNull()
            ?: return null

        val subtitleRuns = flexColumns.getOrNull(1).objOrNull()
            ?.get("musicResponsiveListItemFlexColumnRenderer").objOrNull()
            ?.get("text").objOrNull()
            ?.get("runs").arrOrNull()

        val textTokens = subtitleRuns?.mapNotNull { run ->
            run.objOrNull()?.get("text").strOrNull()
        }?.filter { it != " • " && it != "Song" && it != "Video" } ?: emptyList()

        val artist = textTokens.firstOrNull() ?: "Unknown Artist"
        val albumName = if (textTokens.size >= 2 && !textTokens[1].contains(":")) {
            textTokens[1]
        } else {
            "Single"
        }
        val durationToken = textTokens.lastOrNull { it.contains(":") } ?: "3:30"
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
            title = title,
            artist = artist,
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
     * full-length 320kbps `SongItem`.
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

        val primaryArtistsFromMoreInfo = moreInfo?.get("artistMap").objOrNull()
            ?.get("primary_artists").arrOrNull()
            ?.mapNotNull { a -> a.objOrNull()?.get("name").strOrNull() }
            ?.joinToString(", ")

        val primaryArtistsFromObj = obj["artists"].objOrNull()
            ?.get("primary").arrOrNull()
            ?.mapNotNull { a -> a.objOrNull()?.get("name").strOrNull() }
            ?.joinToString(", ")

        val artist = decodeHtmlEntities(
            primaryArtistsFromMoreInfo?.takeIf { it.isNotBlank() }
                ?: primaryArtistsFromObj?.takeIf { it.isNotBlank() }
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

        // 1. Check direct downloadUrl array (wrapper format)
        val downloadUrls = obj["downloadUrl"].arrOrNull()
        val directDownloadUrl = downloadUrls
            ?.mapNotNull { it.objOrNull() }
            ?.lastOrNull()
            ?.let { dl -> dl["url"].strOrNull() ?: dl["link"].strOrNull() }
            ?.replace("http://", "https://")

        // 2. Or decrypt official JioSaavn `encrypted_media_url` on-device (DES-ECB -> 320kbps full song)
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
        val endpoints = listOf(
            "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=$safeLimit&p=$safePage&q=$encoded",
            "https://saavn.sumit.co/api/search/songs?query=$encoded&page=$safePage&limit=$safeLimit",
            "https://saavn.dev/api/search/songs?query=$encoded&page=$safePage&limit=$safeLimit",
            "https://jiosaavn-api-privatecvc2.vercel.app/search/songs?query=$encoded&limit=$safeLimit"
        )

        for (endpoint in endpoints) {
            val list = runCatching {
                val text = httpGet(endpoint)
                val root = jsonParser.parseToJsonElement(text).objOrNull() ?: return@runCatching emptyList()
                val results = root["results"].arrOrNull()
                    ?: root["data"].objOrNull()?.get("results").arrOrNull()
                    ?: return@runCatching emptyList()

                results.mapNotNull { elem ->
                    parseJioSaavnSongObject(elem.objOrNull())
                }
            }.getOrDefault(emptyList())

            if (list.isNotEmpty()) return list
        }
        return emptyList()
    }

    private suspend fun fetchJioSaavnSongById(saavnId: String): SongItem? {
        val cleanId = saavnId.removePrefix("saavn_").trim()
        if (cleanId.isEmpty()) return null
        val url = "https://www.jiosaavn.com/api.php?__call=song.getDetails&_format=json&_marker=0&api_version=4&pids=$cleanId"
        return runCatching {
            val text = httpGet(url)
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

    private suspend fun searchITunesCatalog(query: String): List<SongItem> {
        val encoded = urlEncode(query)
        val responseText = httpGet(
            "https://itunes.apple.com/search?term=$encoded&media=music&entity=song&limit=25"
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
     * Strictly resolves the exact song's FULL-LENGTH playable audio stream
     * (Never returns 30-second iTunes or Deezer preview clips).
     */
    suspend fun getStreamUrl(
        videoId: String,
        trackTitle: String = "",
        artistName: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Check in-memory cache for a verified full-length stream
            resolvedStreamCache[videoId]?.takeIf { isFullLengthStreamUrl(it) }?.let {
                return@runCatching it
            }

            val cachedSong = songMetadataCache[videoId]
            if (cachedSong != null && isFullLengthStreamUrl(cachedSong.streamUrl)) {
                resolvedStreamCache[videoId] = cachedSong.streamUrl
                return@runCatching cachedSong.streamUrl
            }

            // 2. If this is a JioSaavn song ID, fetch its official details directly
            if (videoId.startsWith("saavn_")) {
                val directSaavn = fetchJioSaavnSongById(videoId)
                if (directSaavn != null && isFullLengthStreamUrl(directSaavn.streamUrl)) {
                    resolvedStreamCache[videoId] = directSaavn.streamUrl
                    return@runCatching directSaavn.streamUrl
                }
            }

            val effectiveTitle = trackTitle.ifBlank { cachedSong?.title.orEmpty() }
            val effectiveArtist = artistName.ifBlank { cachedSong?.artist.orEmpty() }

            val lookupQuery = buildString {
                if (effectiveTitle.isNotBlank()) {
                    append(effectiveTitle)
                    if (effectiveArtist.isNotBlank() && !effectiveArtist.equals("Unknown Artist", ignoreCase = true)) {
                        append(" ")
                        append(effectiveArtist.split(",").first().trim())
                    }
                } else {
                    append(fetchYouTubeOEmbedTitle(videoId).ifBlank { videoId })
                }
            }.trim()

            // 3. Search Official JioSaavn (320kbps Full-Length Stream) + YouTube Music InnerTube in parallel
            if (lookupQuery.isNotBlank()) {
                val resolvedFullUrl = coroutineScope {
                    val saavnPrimaryDef = async {
                        runCatching { searchJioSaavnCatalog(lookupQuery) }.getOrDefault(emptyList())
                    }
                    val saavnTitleOnlyDef = async {
                        if (effectiveTitle.isNotBlank() && effectiveTitle != lookupQuery) {
                            runCatching { searchJioSaavnCatalog(effectiveTitle) }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }
                    }
                    val ytSearchDef = async {
                        runCatching { searchInnerTubeWebRemix(lookupQuery) }.getOrDefault(emptyList())
                    }

                    val saavnCombined = (saavnPrimaryDef.await() + saavnTitleOnlyDef.await())
                        .filter { isFullLengthStreamUrl(it.streamUrl) }

                    // A. Try strict title + artist match on JioSaavn 320kbps catalog
                    val matchedSaavn = if (effectiveTitle.isNotBlank()) {
                        findBestMatchingTrack(effectiveTitle, effectiveArtist, saavnCombined)
                    } else {
                        saavnCombined.firstOrNull()
                    }

                    if (matchedSaavn != null && isFullLengthStreamUrl(matchedSaavn.streamUrl)) {
                        return@coroutineScope matchedSaavn.streamUrl
                    }

                    // B. Otherwise extract full-length audio stream from YouTube Music InnerTube
                    val isSyntheticId = videoId.startsWith("itunes_") ||
                        videoId.startsWith("saavn_") ||
                        videoId.startsWith("deezer_") ||
                        videoId.startsWith("spotify_")

                    val ytCandidates = buildList {
                        if (!isSyntheticId) {
                            add(videoId)
                        }
                        val ytResults = ytSearchDef.await()
                        val bestYt = if (effectiveTitle.isNotBlank()) {
                            findBestMatchingTrack(effectiveTitle, effectiveArtist, ytResults)
                                ?: ytResults.firstOrNull()
                        } else {
                            ytResults.firstOrNull()
                        }
                        if (bestYt != null) add(bestYt.id)
                        ytResults.take(2).forEach { add(it.id) }
                    }.distinct()

                    for (ytId in ytCandidates) {
                        val directYtStream = tryInnerTubeDirectClients(ytId)
                            ?: tryPipedStreamInstances(ytId)
                        if (!directYtStream.isNullOrBlank()) {
                            return@coroutineScope directYtStream
                        }
                    }

                    // C. Last-resort JioSaavn candidate if InnerTube was unreachable
                    saavnCombined.firstOrNull()?.streamUrl
                }

                if (!resolvedFullUrl.isNullOrBlank()) {
                    resolvedStreamCache[videoId] = resolvedFullUrl
                    return@runCatching resolvedFullUrl
                }
            }

            // 4. Direct YouTube videoId fallback if lookupQuery was empty
            if (
                !videoId.startsWith("itunes_") &&
                !videoId.startsWith("saavn_") &&
                !videoId.startsWith("deezer_") &&
                !videoId.startsWith("spotify_")
            ) {
                val innerTubeUrl = tryInnerTubeDirectClients(videoId)
                    ?: tryPipedStreamInstances(videoId)
                if (!innerTubeUrl.isNullOrBlank()) {
                    resolvedStreamCache[videoId] = innerTubeUrl
                    return@runCatching innerTubeUrl
                }
            }

            throw IllegalStateException("Full-length stream unavailable for $effectiveTitle")
        }
    }

    suspend fun getAlternateStreamUrl(song: SongItem, failedUrl: String): String? = withContext(Dispatchers.IO) {
        val query = "${song.title} ${song.artist}".trim()
        val saavn = runCatching { searchJioSaavnCatalog(query) }.getOrDefault(emptyList())
            .filter { isFullLengthStreamUrl(it.streamUrl) && it.streamUrl != failedUrl }
        val chosenSaavn = findBestMatchingTrack(song.title, song.artist, saavn)?.streamUrl
            ?: saavn.firstOrNull()?.streamUrl
        if (!chosenSaavn.isNullOrBlank()) {
            resolvedStreamCache[song.id] = chosenSaavn
            return@withContext chosenSaavn
        }

        val ytResults = runCatching { searchInnerTubeWebRemix(query) }.getOrDefault(emptyList())
        for (yt in ytResults.take(2)) {
            val ytUrl = tryInnerTubeDirectClients(yt.id) ?: tryPipedStreamInstances(yt.id)
            if (!ytUrl.isNullOrBlank() && ytUrl != failedUrl) {
                resolvedStreamCache[song.id] = ytUrl
                return@withContext ytUrl
            }
        }
        null
    }

    private suspend fun tryInnerTubeDirectClients(videoId: String): String? {
        data class InnerTubeClientSpec(
            val clientName: String,
            val clientVersion: String,
            val clientIdHeader: String,
            val userAgent: String,
            val deviceMake: String,
            val deviceModel: String,
            val osName: String,
            val osVersion: String,
            val androidSdkVersion: Int? = null
        )

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
                clientName = "ANDROID_TESTSUITE",
                clientVersion = "1.9",
                clientIdHeader = "30",
                userAgent = "com.google.android.youtube/1.9 (Linux; U; Android 14; Pixel 8 Pro) gzip",
                deviceMake = "Google",
                deviceModel = "Pixel 8 Pro",
                osName = "Android",
                osVersion = "14",
                androidSdkVersion = 34
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
            )
        )

        for (spec in clients) {
            val extracted = runCatching {
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
                            put("gl", "US")
                        }
                    }
                }

                val responseText = httpPostJson(
                    url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                    jsonBody = payload.toString(),
                    headers = mapOf(
                        "User-Agent" to spec.userAgent,
                        "X-YouTube-Client-Name" to spec.clientIdHeader,
                        "X-YouTube-Client-Version" to spec.clientVersion
                    )
                )

                val root = jsonParser.parseToJsonElement(responseText).objOrNull() ?: return@runCatching null
                val adaptiveFormats = root["streamingData"].objOrNull()
                    ?.get("adaptiveFormats").arrOrNull()

                adaptiveFormats
                    ?.mapNotNull { it.objOrNull() }
                    ?.filter { format ->
                        val mime = format["mimeType"].strOrNull() ?: ""
                        val url = format["url"].strOrNull()
                        val approxMs = format["approxDurationMs"].strOrNull()?.toLongOrNull() ?: 180_000L
                        mime.startsWith("audio/") && !url.isNullOrBlank() && approxMs > 45_000L
                    }
                    ?.sortedWith(
                        compareByDescending<JsonObject> { format ->
                            val itag = format["itag"].intValOrNull() ?: 0
                            when (itag) {
                                140 -> 2 // M4A AAC (universal hardware decoder support)
                                251 -> 1 // WebM Opus
                                else -> 0
                            }
                        }.thenByDescending { format ->
                            format["bitrate"].intValOrNull() ?: 0
                        }
                    )
                    ?.firstOrNull()
                    ?.get("url").strOrNull()
            }.getOrNull()

            if (!extracted.isNullOrBlank()) {
                return extracted
            }
        }
        return null
    }

    private suspend fun tryPipedStreamInstances(videoId: String): String? {
        val instances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.private.coffee"
        )
        for (base in instances) {
            val url = runCatching {
                val text = httpGet("$base/streams/$videoId")
                val root = jsonParser.parseToJsonElement(text).objOrNull() ?: return@runCatching null
                val audioStreams = root["audioStreams"].arrOrNull()
                audioStreams
                    ?.mapNotNull { it.objOrNull() }
                    ?.filter { !it["url"].strOrNull().isNullOrBlank() }
                    ?.maxByOrNull { it["bitrate"].intValOrNull() ?: 0 }
                    ?.get("url").strOrNull()
            }.getOrNull()
            if (!url.isNullOrBlank()) return url
        }
        return null
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

    internal fun cleanTrackTitleForLyrics(rawTitle: String): String {
        val decoded = decodeHtmlEntities(rawTitle)
        val stripped = decoded
            .replace(Regex("\\((?:from|official|lyric|audio|video|feat|ft|prod|remaster|version|live).*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[(?:from|official|lyric|audio|video|feat|ft|prod|remaster|version|live).*?]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(.*?\\)|\\[.*?]"), "")
            .replace(Regex("\\s[-–|]\\s*(?:from|official|lyric|audio|video|remaster|original|soundtrack|motion picture).*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(?:feat|ft|featuring)\\.?\\s+.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return stripped.ifBlank { decoded.trim() }
    }

    internal fun extractPrimaryArtistForLyrics(rawArtist: String): String {
        val decoded = decodeHtmlEntities(rawArtist)
        val first = decoded
            .split(Regex("[,&/]|\\b(?:feat|ft|featuring|with)\\b", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
        return first ?: decoded.trim()
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
            .filter { it.isNotEmpty() && !it.startsWith("Paroles de la chanson", ignoreCase = true) }
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

    private fun detectLyricsScriptLabel(lines: List<LyricLine>, candidateTitle: String = ""): String {
        val sample = lines.take(24).joinToString(" ") { it.text }
        var devanagari = 0
        var gurmukhi = 0
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
            lowerTitle.contains("english translation") || lowerTitle.contains("(english") -> "English"
            lowerTitle.contains("romanized") || lowerTitle.contains("romanised") -> "Romanized"
            devanagari > 12 -> "Hindi (Devanagari)"
            gurmukhi > 12 -> "Punjabi"
            tamil > 12 -> "Tamil"
            telugu > 12 -> "Telugu"
            hangul > 10 -> "Korean"
            japanese > 8 -> "Japanese / CJK"
            arabic > 10 -> "Arabic / Urdu"
            latin > 12 -> "Latin / English"
            else -> "Original"
        }
    }

    private suspend fun searchLrclibCatalogWithVariants(
        cleanTitle: String,
        primaryArtist: String,
        fullArtist: String,
        durationSec: Long
    ): Pair<LyricsData?, List<LyricsVariant>> {
        val queries = buildList {
            if (cleanTitle.isNotBlank() && primaryArtist.isNotBlank()) {
                add("$cleanTitle $primaryArtist")
            }
            if (cleanTitle.isNotBlank()) {
                add(cleanTitle)
            }
        }.distinct()

        val targetCanonTitle = canonicalSongTitle(cleanTitle)
        val targetArtistWords = artistWordSet(fullArtist.ifBlank { primaryArtist })
        val collectedParsed = mutableListOf<Pair<Int, LyricsData>>()

        for (q in queries) {
            runCatching {
                val url = "https://lrclib.net/api/search?q=${urlEncode(q)}"
                val text = httpGet(url)
                val arr = jsonParser.parseToJsonElement(text).arrOrNull() ?: return@runCatching
                val candidates = arr.mapNotNull { it.objOrNull() }
                if (candidates.isEmpty()) return@runCatching

                val scored = candidates.mapNotNull { item ->
                    val itemTitle = item["trackName"].strOrNull() ?: item["name"].strOrNull() ?: return@mapNotNull null
                    val itemArtist = item["artistName"].strOrNull().orEmpty()
                    val itemCanonTitle = canonicalSongTitle(itemTitle)
                    val titleMatches = itemCanonTitle == targetCanonTitle ||
                        itemCanonTitle.contains(targetCanonTitle) ||
                        targetCanonTitle.contains(itemCanonTitle)
                    if (!titleMatches) return@mapNotNull null

                    val itemArtistWords = artistWordSet(itemArtist)
                    val artistOverlap = targetArtistWords.any { it in itemArtistWords }
                    val hasSynced = !item["syncedLyrics"].strOrNull().isNullOrBlank()
                    val hasPlain = !item["plainLyrics"].strOrNull().isNullOrBlank()
                    if (!hasSynced && !hasPlain) return@mapNotNull null

                    var score = 0
                    if (itemCanonTitle == targetCanonTitle) score += 100 else score += 50
                    if (artistOverlap) score += 80
                    if (hasSynced) score += 220

                    // Weight duration proximity heavily so radio edits / slowed versions don't desync
                    val itemDurationSec = item["duration"].longValOrNull()
                        ?: item["duration"].strOrNull()?.toDoubleOrNull()?.toLong()
                    if (durationSec > 0L && itemDurationSec != null && itemDurationSec > 0L) {
                        val diffSec = kotlin.math.abs(itemDurationSec - durationSec)
                        score += when {
                            diffSec <= 2L -> 160
                            diffSec <= 5L -> 110
                            diffSec <= 10L -> 50
                            diffSec <= 18L -> 0
                            else -> -150
                        }
                    }

                    Triple(item, itemTitle, score)
                }.sortedByDescending { it.third }

                for ((bestObj, itemTitle, score) in scored.take(6)) {
                    parseLrclibObjectToLyricsData(bestObj)?.let { parsed ->
                        val label = detectLyricsScriptLabel(parsed.lines, itemTitle)
                        collectedParsed.add(score to parsed.copy(languageLabel = label))
                    }
                }
            }
            if (collectedParsed.isNotEmpty()) break
        }

        if (collectedParsed.isEmpty()) return null to emptyList()
        val sortedByBestScore = collectedParsed.sortedByDescending { it.first }
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
        cleanTitle: String,
        primaryArtist: String
    ): LyricsData? {
        val candidateSaavnIds = mutableListOf<String>()
        if (songId.startsWith("saavn_")) {
            candidateSaavnIds.add(songId.removePrefix("saavn_").trim())
        }
        if (candidateSaavnIds.isEmpty() && cleanTitle.isNotBlank()) {
            val searched = runCatching {
                searchJioSaavnCatalog("$cleanTitle $primaryArtist".trim())
            }.getOrDefault(emptyList())
            searched.firstOrNull()?.id?.removePrefix("saavn_")?.takeIf { it.isNotBlank() }?.let {
                candidateSaavnIds.add(it)
            }
        }

        for (saavnId in candidateSaavnIds.distinct()) {
            val endpoints = listOf(
                "https://www.jiosaavn.com/api.php?__call=lyrics.getLyrics&ctx=web6dot0&api_version=4&_format=json&_marker=0&lyrics_id=$saavnId",
                "https://saavn.sumit.co/api/songs/$saavnId/lyrics",
                "https://saavn.dev/api/songs/$saavnId/lyrics"
            )
            for (endpoint in endpoints) {
                val lyricsData = runCatching {
                    val text = httpGet(endpoint)
                    val root = jsonParser.parseToJsonElement(text).objOrNull() ?: return@runCatching null
                    val rawLyrics = root["lyrics"].strOrNull()
                        ?: root["data"].objOrNull()?.get("lyrics").strOrNull()
                        ?: return@runCatching null
                    val lines = parsePlainLyricsLines(rawLyrics)
                    if (lines.size >= 2) {
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
     * Multi-stage real lyrics resolver:
     * 1. Cleans movie/soundtrack tags from title and extracts primary artist
     * 2. Queries LRCLIB Exact (`/api/get`) with track duration
     * 3. Queries LRCLIB Fuzzy Search (`/api/search?q=...`) scored by exact duration match & multi-language variants
     * 4. Queries Official JioSaavn Lyrics API & Lyrics.ovh for full scrollable plain lyrics
     */
    suspend fun getSyncedLyrics(
        trackTitle: String,
        artist: String,
        durationSec: Long,
        songId: String = ""
    ): Result<LyricsData> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanTitle = cleanTrackTitleForLyrics(trackTitle)
            val primaryArtist = extractPrimaryArtistForLyrics(artist)
            val durationBucket = if (durationSec > 0L) durationSec / 8L else 0L
            val cacheKey = "${canonicalSongTitle(cleanTitle)}|${primaryArtist.lowercase()}|$durationBucket"

            lyricsCache[cacheKey]?.let { return@runCatching it }

            // Stage 1: LRCLIB Exact endpoint with cleaned title, primary artist & duration
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
                val obj = jsonParser.parseToJsonElement(responseText).objOrNull()
                parseLrclibObjectToLyricsData(obj)?.let { data ->
                    data.copy(languageLabel = detectLyricsScriptLabel(data.lines, cleanTitle))
                }
            }.getOrNull()

            // Stage 2: LRCLIB Search (scored with duration proximity & collects genuine variants)
            val (searchResult, searchVariants) = searchLrclibCatalogWithVariants(
                cleanTitle = cleanTitle,
                primaryArtist = primaryArtist,
                fullArtist = artist,
                durationSec = durationSec
            )

            val primary = when {
                exactResult != null && exactResult.isSynced -> exactResult
                searchResult != null && searchResult.isSynced -> searchResult
                exactResult != null -> exactResult
                searchResult != null -> searchResult
                else -> null
            }

            if (primary != null) {
                val allVariants = buildList {
                    if (searchResult != null && !searchResult.languageLabel.equals(primary.languageLabel, ignoreCase = true)) {
                        add(
                            LyricsVariant(
                                languageLabel = searchResult.languageLabel,
                                lines = searchResult.lines,
                                isSynced = searchResult.isSynced
                            )
                        )
                    }
                    addAll(searchVariants.filter { !it.languageLabel.equals(primary.languageLabel, ignoreCase = true) })
                }.distinctBy { it.languageLabel.lowercase() }

                val enriched = primary.copy(alternateVariants = allVariants)
                lyricsCache[cacheKey] = enriched
                return@runCatching enriched
            }

            // Stage 3: Official JioSaavn Lyrics API + Lyrics.ovh in parallel
            val fallbackPlainLyrics = coroutineScope {
                val saavnDef = async { fetchJioSaavnLyrics(songId, cleanTitle, primaryArtist) }
                val ovhDef = async { fetchLyricsOvh(cleanTitle, primaryArtist) }
                saavnDef.await() ?: ovhDef.await()
            }

            if (fallbackPlainLyrics != null) {
                lyricsCache[cacheKey] = fallbackPlainLyrics
                return@runCatching fallbackPlainLyrics
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
