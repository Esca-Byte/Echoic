package com.example.data.local

import android.content.Context
import com.example.data.model.EqualizerState
import com.example.data.model.PlayerRepeatMode
import com.example.data.model.SongItem
import com.example.data.model.defaultEqBands
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PersistedPlaybackSession(
    val currentTrack: SongItem? = null,
    val queue: List<SongItem> = emptyList(),
    val progressMs: Long = 0L,
    val repeatModeName: String = PlayerRepeatMode.OFF.name,
    val isShuffled: Boolean = false,
    val autoQueuedIds: List<String> = emptyList(),
    val savedAtMs: Long = 0L
)

@Serializable
data class PersistedAudioSettings(
    val isEqEnabled: Boolean = false,
    val bandLevelsMb: List<Int> = listOf(300, 150, 0, 200, 350),
    val bassBoostStrength: Int = 250,
    val activePreset: String = "Studio Warmth",
    val crossfadeSeconds: Int = 4,
    val gaplessPlayback: Boolean = true,
    val autoDeviceProfilesEnabled: Boolean = true,
    val speakerPreset: String = "Treble Sparkle",
    val wiredPreset: String = "Studio Warmth",
    val bluetoothPreset: String = "Bass Boost"
)

object PlaybackSessionStore {

    private const val SESSION_PREFS = "echoic_playback_session_prefs"
    private const val KEY_SESSION_JSON = "persisted_session_json"

    private const val AUDIO_PREFS = "echoic_audio_console_prefs"
    private const val KEY_AUDIO_SETTINGS_JSON = "persisted_audio_settings_json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun saveSession(
        context: Context,
        currentTrack: SongItem?,
        queue: List<SongItem>,
        progressMs: Long,
        repeatMode: PlayerRepeatMode,
        isShuffled: Boolean,
        autoQueuedIds: Set<String>
    ) {
        if (currentTrack == null) {
            clearSession(context)
            return
        }
        val cappedQueue = queue.take(80)
        val payload = PersistedPlaybackSession(
            currentTrack = currentTrack,
            queue = cappedQueue,
            progressMs = progressMs.coerceAtLeast(0L),
            repeatModeName = repeatMode.name,
            isShuffled = isShuffled,
            autoQueuedIds = autoQueuedIds.take(60),
            savedAtMs = System.currentTimeMillis()
        )
        runCatching {
            val encoded = json.encodeToString(payload)
            context.applicationContext
                .getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SESSION_JSON, encoded)
                .apply()
        }
    }

    fun loadSession(context: Context): PersistedPlaybackSession? {
        val raw = context.applicationContext
            .getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SESSION_JSON, null)
            ?: return null
        return runCatching {
            json.decodeFromString<PersistedPlaybackSession>(raw)
        }.getOrNull()?.takeIf { it.currentTrack != null }
    }

    fun clearSession(context: Context) {
        context.applicationContext
            .getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SESSION_JSON)
            .apply()
    }

    fun saveAudioSettings(context: Context, eqState: EqualizerState) {
        val payload = PersistedAudioSettings(
            isEqEnabled = eqState.isEnabled,
            bandLevelsMb = eqState.bands.map { it.currentLevelMb.toInt() },
            bassBoostStrength = eqState.bassBoostStrength,
            activePreset = eqState.activePreset,
            crossfadeSeconds = eqState.crossfadeSeconds,
            gaplessPlayback = eqState.gaplessPlayback,
            autoDeviceProfilesEnabled = eqState.autoDeviceProfilesEnabled,
            speakerPreset = eqState.speakerPreset,
            wiredPreset = eqState.wiredPreset,
            bluetoothPreset = eqState.bluetoothPreset
        )
        runCatching {
            val encoded = json.encodeToString(payload)
            context.applicationContext
                .getSharedPreferences(AUDIO_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_AUDIO_SETTINGS_JSON, encoded)
                .apply()
        }
    }

    fun loadAudioSettings(context: Context): EqualizerState {
        val raw = context.applicationContext
            .getSharedPreferences(AUDIO_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_AUDIO_SETTINGS_JSON, null)
            ?: return EqualizerState()
        val parsed = runCatching {
            json.decodeFromString<PersistedAudioSettings>(raw)
        }.getOrNull() ?: return EqualizerState()

        val bands = defaultEqBands().mapIndexed { idx, band ->
            val level = parsed.bandLevelsMb.getOrElse(idx) { band.currentLevelMb.toInt() }
                .coerceIn(band.minLevelMb.toInt(), band.maxLevelMb.toInt())
                .toShort()
            band.copy(currentLevelMb = level)
        }
        return EqualizerState(
            isEnabled = parsed.isEqEnabled,
            bands = bands,
            bassBoostStrength = parsed.bassBoostStrength.coerceIn(0, 1000),
            activePreset = parsed.activePreset.ifBlank { "Studio Warmth" },
            crossfadeSeconds = parsed.crossfadeSeconds.coerceIn(0, 12),
            gaplessPlayback = parsed.gaplessPlayback,
            autoQueueRadio = true,
            autoDeviceProfilesEnabled = parsed.autoDeviceProfilesEnabled,
            speakerPreset = parsed.speakerPreset.ifBlank { "Treble Sparkle" },
            wiredPreset = parsed.wiredPreset.ifBlank { "Studio Warmth" },
            bluetoothPreset = parsed.bluetoothPreset.ifBlank { "Bass Boost" }
        )
    }
}
