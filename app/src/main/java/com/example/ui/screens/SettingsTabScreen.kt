package com.example.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.example.data.model.AppThemeState
import com.example.data.model.AudioQualitySettings
import com.example.data.model.EqualizerState
import com.example.data.model.SleepTimerState
import com.example.data.model.StreamingQualityMode
import com.example.data.model.StudioThemePreset
import com.example.ui.components.StudioEqualizerConsole

/**
 * Modular Settings & EQ Tab Screen wrapper bridging appearance themes,
 * audio quality / data saver controls, equalizer console, and sleep timer.
 */
@Composable
internal fun SettingsAndEqTabScreen(
    equalizerState: EqualizerState,
    themeState: AppThemeState,
    audioQualitySettings: AudioQualitySettings,
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
    onSelectThemePreset: (StudioThemePreset) -> Unit,
    onSetAmoledPureBlack: (Boolean) -> Unit,
    onSetDynamicArtworkTint: (Boolean) -> Unit,
    onSelectStreamingQualityMode: (StreamingQualityMode) -> Unit,
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
        themeState = themeState,
        onSelectThemePreset = onSelectThemePreset,
        onSetAmoledPureBlack = onSetAmoledPureBlack,
        onSetDynamicArtworkTint = onSetDynamicArtworkTint,
        audioQualitySettings = audioQualitySettings,
        onSelectStreamingQualityMode = onSelectStreamingQualityMode,
        selectedTasteGenres = selectedTasteGenres,
        onOpenTasteEditor = onOpenTasteEditor,
        onStartSleepTimer = onStartSleepTimer,
        onCancelSleepTimer = onCancelSleepTimer
    )
}
