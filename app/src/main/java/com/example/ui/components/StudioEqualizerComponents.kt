package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BluetoothAudio
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppThemeState
import com.example.data.model.AudioOutputRoute
import com.example.data.model.EqualizerBand
import com.example.data.model.EqualizerState
import com.example.data.model.SleepTimerState
import com.example.data.model.StudioThemePreset
import com.example.ui.theme.AlabasterWhite
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DimSlate
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.ThemeOnPrimary
import com.example.ui.theme.resolveStudioPalette
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Redesigned Pro Studio Equalizer & DSP Console Tab:
 * - Master Studio Header with LED status & one-tap Flat/1.0x Reset
 * - Acoustic Preset Ribbon (Studio Warmth, Bass Boost, Desi Bass, Vocal Clarity, Treble Sparkle, Late Night, Flat)
 * - Live Cubic Bezier EQ Frequency Response Curve Graph
 * - 5-Band Tactile Vertical Studio Faders with drag & tap support
 * - Interactive Rotary Dials for Sub-Bass Boost, Playback Speed, and Pitch Shift
 * - Studio Smart Sleep Timer Console
 */
@Composable
fun StudioEqualizerConsole(
    equalizerState: EqualizerState,
    playbackSpeed: Float,
    playbackPitch: Float,
    sleepTimerState: SleepTimerState,
    innerPadding: PaddingValues,
    onSetEqEnabled: (Boolean) -> Unit,
    onSetBandLevel: (Short, Short) -> Unit,
    onSetBassBoost: (Int) -> Unit,
    onApplyPreset: (String) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onStartSleepTimer: (Int, Boolean) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onSetCrossfadeSeconds: (Int) -> Unit = {},
    onSetGaplessPlayback: (Boolean) -> Unit = {},
    onSetAutoQueueRadio: (Boolean) -> Unit = {},
    onSetAutoDeviceProfilesEnabled: (Boolean) -> Unit = {},
    onSetOutputRoutePreset: (AudioOutputRoute, String) -> Unit = { _, _ -> },
    themeState: AppThemeState = AppThemeState(),
    onSelectThemePreset: (StudioThemePreset) -> Unit = {},
    onSetAmoledPureBlack: (Boolean) -> Unit = {},
    onSetDynamicArtworkTint: (Boolean) -> Unit = {},
    selectedTasteGenres: List<String> = emptyList(),
    onOpenTasteEditor: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val eqPresets = remember {
        listOf(
            "Studio Warmth",
            "Bass Boost",
            "Desi Bass",
            "Vocal Clarity",
            "Treble Sparkle",
            "Late Night",
            "Flat"
        )
    }
    val sleepPresets = remember { listOf(15, 30, 45, 60) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ElectricMint.copy(alpha = 0.07f),
                        CyberCyan.copy(alpha = 0.04f),
                        PitchBlack
                    ),
                    startY = 0f,
                    endY = 640f
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp)
                .testTag("settings_eq_lazy_column"),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 0. Appearance & Premium Studio Themes Card
            item(key = "studio_appearance_themes") {
                AppearanceAndThemesCard(
                    themeState = themeState,
                    onSelectThemePreset = onSelectThemePreset,
                    onSetAmoledPureBlack = onSetAmoledPureBlack,
                    onSetDynamicArtworkTint = onSetDynamicArtworkTint
                )
            }

            // 1. Sleek Equalizer Header Card with Master Power Switch
            item(key = "studio_eq_header") {
                Surface(
                    color = CarbonSurface,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(0.75.dp, SubtleBorder.copy(alpha = 0.75f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (equalizerState.isEnabled) ElectricMint.copy(alpha = 0.16f)
                                        else CarbonElevated
                                    )
                                    .border(
                                        width = 0.75.dp,
                                        color = if (equalizerState.isEnabled) ElectricMint.copy(alpha = 0.55f)
                                        else SubtleBorder,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = null,
                                    tint = if (equalizerState.isEnabled) ElectricMint else MutedSlate,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Equalizer",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = AlabasterWhite
                                )
                                Text(
                                    text = if (equalizerState.isEnabled) "Custom sound profile enabled" else "Sound profile disabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (equalizerState.isEnabled) ElectricMint.copy(alpha = 0.85f) else MutedSlate
                                )
                            }
                        }

                        Switch(
                            checked = equalizerState.isEnabled,
                            onCheckedChange = onSetEqEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ThemeOnPrimary,
                                checkedTrackColor = ElectricMint,
                                uncheckedThumbColor = MutedSlate,
                                uncheckedTrackColor = CarbonElevated,
                                uncheckedBorderColor = SubtleBorder
                            ),
                            modifier = Modifier.testTag("studio_eq_master_switch")
                        )
                    }
                }
            }

            // 2. Acoustic Preset Ribbon Card
            item(key = "studio_eq_presets_ribbon") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(CarbonSurface)
                        .border(0.75.dp, SubtleBorder.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
                        .padding(vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SOUND PRESETS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp
                            ),
                            color = MutedSlate
                        )
                        Text(
                            text = equalizerState.activePreset.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = ElectricMint
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        eqPresets.forEach { preset ->
                            val isSelected = equalizerState.activePreset.equals(preset, ignoreCase = true)
                            Surface(
                                color = if (isSelected) ElectricMint.copy(alpha = 0.16f) else CarbonElevated,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(
                                        width = if (isSelected) 1.dp else 0.5.dp,
                                        color = if (isSelected) ElectricMint else SubtleBorder.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable(enabled = equalizerState.isEnabled) {
                                        onApplyPreset(preset)
                                    }
                                    .testTag("eq_preset_chip_${preset.lowercase().replace(" ", "_")}")
                            ) {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) ElectricMint else AlabasterWhite.copy(alpha = 0.88f),
                                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Live EQ Frequency Response Curve Graph + 5-Band Vertical Studio Faders Console
            item(key = "studio_eq_curve_and_vertical_faders") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(CarbonSurface)
                        .border(0.75.dp, SubtleBorder.copy(alpha = 0.75f), RoundedCornerShape(24.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GraphicEq,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "SOUND CURVE & FREQUENCY BANDS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = AlabasterWhite
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "-15dB .. +15dB",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = MutedSlate
                            )

                            val hasNonFlatGain = equalizerState.bands.any { it.currentLevelMb.toInt() != 0 }
                            if (equalizerState.isEnabled && hasNonFlatGain) {
                                Text(
                                    text = "Reset",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = ElectricMint,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(ElectricMint.copy(alpha = 0.14f))
                                        .border(0.5.dp, ElectricMint.copy(alpha = 0.45f), RoundedCornerShape(50))
                                        .clickable {
                                            onApplyPreset("Flat")
                                            onSetBassBoost(0)
                                            onSetSpeed(1.0f)
                                            onSetPitch(1.0f)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("studio_eq_reset_flat_button")
                                )
                            }
                        }
                    }

                    // Live Spline Frequency Response Graph
                    StudioEqCurveGraph(
                        bands = equalizerState.bands,
                        isEnabled = equalizerState.isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(136.dp)
                    )

                    // 5-Band Vertical Studio Faders Row
                    StudioVerticalFaderBank(
                        bands = equalizerState.bands,
                        isEnabled = equalizerState.isEnabled,
                        onSetBandLevel = onSetBandLevel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 4. Playback Transitions & Endless Auto-Queue Radio Console
            item(key = "studio_playback_transitions_card") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(CarbonSurface)
                        .border(0.75.dp, SubtleBorder.copy(alpha = 0.75f), RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = ElectricMint,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "CROSSFADE & PLAYBACK",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = AlabasterWhite
                            )
                        }

                        Surface(
                            color = if (equalizerState.crossfadeSeconds > 0) ElectricMint.copy(alpha = 0.14f) else CarbonElevated,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.border(
                                width = 0.5.dp,
                                color = if (equalizerState.crossfadeSeconds > 0) ElectricMint.copy(alpha = 0.45f) else SubtleBorder,
                                shape = RoundedCornerShape(50)
                            )
                        ) {
                            Text(
                                text = if (equalizerState.crossfadeSeconds == 0) "OFF" else "${equalizerState.crossfadeSeconds}s CROSSFADE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = if (equalizerState.crossfadeSeconds > 0) ElectricMint else MutedSlate,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Track Crossfade Transition",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = AlabasterWhite
                            )
                            Text(
                                text = "0s .. 12s",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MutedSlate
                            )
                        }
                        Slider(
                            value = equalizerState.crossfadeSeconds.toFloat(),
                            onValueChange = { onSetCrossfadeSeconds(it.roundToInt()) },
                            valueRange = 0f..12f,
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricMint,
                                activeTrackColor = ElectricMint,
                                inactiveTrackColor = CarbonElevated
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("crossfade_duration_slider")
                        )
                    }

                    // Gapless Playback Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Gapless Playback",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = AlabasterWhite
                            )
                            Text(
                                text = "Eliminates silence between consecutive album & playlist tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedSlate
                            )
                        }
                        Switch(
                            checked = equalizerState.gaplessPlayback,
                            onCheckedChange = onSetGaplessPlayback,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ThemeOnPrimary,
                                checkedTrackColor = ElectricMint,
                                uncheckedThumbColor = MutedSlate,
                                uncheckedTrackColor = CarbonElevated,
                                uncheckedBorderColor = SubtleBorder
                            ),
                            modifier = Modifier.testTag("gapless_playback_switch")
                        )
                    }
                }
            }

            // 5. Audio Output Device Auto-Profiles (Speaker / Wired Headphones / Bluetooth)
            item(key = "studio_output_device_profiles_card") {
                val routeRows = listOf(
                    Triple(AudioOutputRoute.SPEAKER, Icons.Outlined.Speaker, equalizerState.speakerPreset),
                    Triple(AudioOutputRoute.WIRED_HEADPHONES, Icons.Outlined.Headphones, equalizerState.wiredPreset),
                    Triple(AudioOutputRoute.BLUETOOTH, Icons.Outlined.BluetoothAudio, equalizerState.bluetoothPreset)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(CarbonSurface)
                        .border(0.75.dp, SubtleBorder.copy(alpha = 0.75f), RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = when (equalizerState.activeOutputRoute) {
                                        AudioOutputRoute.BLUETOOTH -> Icons.Outlined.BluetoothAudio
                                        AudioOutputRoute.WIRED_HEADPHONES -> Icons.Outlined.Headphones
                                        AudioOutputRoute.SPEAKER -> Icons.Outlined.Speaker
                                    },
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "OUTPUT DEVICE AUTO-PROFILES",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = AlabasterWhite
                                )
                            }
                            Text(
                                text = "Active: ${equalizerState.activeOutputRoute.label} • Auto-switches EQ on plug/pair",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberCyan.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Switch(
                            checked = equalizerState.autoDeviceProfilesEnabled,
                            onCheckedChange = onSetAutoDeviceProfilesEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ThemeOnPrimary,
                                checkedTrackColor = ElectricMint,
                                uncheckedThumbColor = MutedSlate,
                                uncheckedTrackColor = CarbonElevated,
                                uncheckedBorderColor = SubtleBorder
                            ),
                            modifier = Modifier.testTag("auto_device_profiles_switch")
                        )
                    }

                    routeRows.forEach { (route, routeIcon, selectedPreset) ->
                        val isActiveRoute = equalizerState.activeOutputRoute == route
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isActiveRoute) CyberCyan.copy(alpha = 0.09f) else CarbonElevated.copy(alpha = 0.65f)
                                )
                                .border(
                                    width = if (isActiveRoute) 0.9.dp else 0.5.dp,
                                    color = if (isActiveRoute) CyberCyan.copy(alpha = 0.55f) else SubtleBorder.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    val currentIdx = eqPresets.indexOfFirst { it.equals(selectedPreset, ignoreCase = true) }
                                    val nextPreset = eqPresets[(currentIdx + 1) % eqPresets.size]
                                    onSetOutputRoutePreset(route, nextPreset)
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                .testTag("device_profile_row_${route.key}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = routeIcon,
                                    contentDescription = route.label,
                                    tint = if (isActiveRoute) CyberCyan else MutedSlate,
                                    modifier = Modifier.size(19.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        text = route.label,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = if (isActiveRoute) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = AlabasterWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isActiveRoute) "Connected now • Tap to cycle preset" else "Tap to cycle EQ preset",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isActiveRoute) CyberCyan else MutedSlate,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                color = if (isActiveRoute) ElectricMint.copy(alpha = 0.16f) else CarbonElevated,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.border(
                                    width = 0.6.dp,
                                    color = if (isActiveRoute) ElectricMint.copy(alpha = 0.55f) else SubtleBorder,
                                    shape = RoundedCornerShape(50)
                                )
                            ) {
                                Text(
                                    text = selectedPreset,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isActiveRoute) ElectricMint else AlabasterWhite,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 6. Personalize Music Taste Card (Re-open 2-step Genre & Artist Editor anytime)
            item(key = "studio_music_taste_profile_card") {
                Surface(
                    color = CarbonSurface,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(0.75.dp, SubtleBorder.copy(alpha = 0.75f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = onOpenTasteEditor)
                        .testTag("settings_edit_taste_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ElectricMint.copy(alpha = 0.14f))
                                    .border(0.75.dp, ElectricMint.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = ElectricMint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Music Taste & Artists",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = AlabasterWhite
                                )
                                Text(
                                    text = if (selectedTasteGenres.isNotEmpty()) {
                                        selectedTasteGenres.take(4).joinToString(" • ")
                                    } else {
                                        "Pick genres & favorite artists to tune Home & Mixes"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedSlate,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Surface(
                            color = ElectricMint,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("settings_edit_taste_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Edit",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = ThemeOnPrimary
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = ThemeOnPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Live Cubic Bezier EQ Frequency Response Curve Graph
 */
@Composable
private fun StudioEqCurveGraph(
    bands: List<EqualizerBand>,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedFractions = bands.map { band ->
        val range = (band.maxLevelMb - band.minLevelMb).coerceAtLeast(1)
        val norm = ((band.currentLevelMb - band.minLevelMb).toFloat() / range.toFloat()).coerceIn(0f, 1f)
        val state by animateFloatAsState(
            targetValue = if (isEnabled) norm else 0.5f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "eqCurveNode_${band.index}"
        )
        state
    }

    val curveBorder = SubtleBorder
    val curvePrimary = ElectricMint
    val curveCyan = CyberCyan
    val curveViolet = NeonViolet
    val curveMuted = MutedSlate
    val curveDim = DimSlate
    val curveText = AlabasterWhite

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PitchBlack.copy(alpha = 0.78f))
            .border(0.75.dp, curveBorder.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerY = h * 0.5f

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

            // Reference Horizontal Grid Lines (+15dB, +7.5dB, 0dB, -7.5dB, -15dB)
            val gridRatios = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
            gridRatios.forEachIndexed { idx, ratio ->
                val y = h * ratio
                val isZeroLine = idx == 2
                drawLine(
                    color = if (isZeroLine) curveBorder.copy(alpha = 0.9f) else curveBorder.copy(alpha = 0.38f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (isZeroLine) 1.2.dp.toPx() else 0.75.dp.toPx(),
                    pathEffect = if (isZeroLine) null else dashEffect
                )
            }

            if (animatedFractions.isEmpty()) return@Canvas

            val count = animatedFractions.size
            val points = List(count) { i ->
                val x = if (count == 1) w / 2f else {
                    val horizontalPad = w * 0.08f
                    horizontalPad + (w - horizontalPad * 2f) * (i.toFloat() / (count - 1).toFloat())
                }
                // fraction 1.0 -> top (0.10 * h), fraction 0.0 -> bottom (0.90 * h)
                val y = h * (0.90f - animatedFractions[i] * 0.80f)
                Offset(x, y)
            }

            // Build Smooth Cubic Bezier Spline Path
            val curvePath = Path().apply {
                moveTo(0f, centerY)
                val first = points.first()
                cubicTo(
                    first.x * 0.35f,
                    centerY,
                    first.x * 0.65f,
                    first.y,
                    first.x,
                    first.y
                )
                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val midX = (p0.x + p1.x) / 2f
                    cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }
                val last = points.last()
                cubicTo(
                    last.x + (w - last.x) * 0.35f,
                    last.y,
                    last.x + (w - last.x) * 0.65f,
                    centerY,
                    w,
                    centerY
                )
            }

            // Area Fill Path under the Spline Curve
            val fillPath = Path().apply {
                addPath(curvePath)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }

            val primaryStrokeBrush = Brush.horizontalGradient(
                colors = if (isEnabled) {
                    listOf(curvePrimary, curveCyan, curveViolet)
                } else {
                    listOf(curveMuted.copy(alpha = 0.5f), curveDim.copy(alpha = 0.5f))
                }
            )

            val areaFillBrush = Brush.verticalGradient(
                colors = if (isEnabled) {
                    listOf(
                        curvePrimary.copy(alpha = 0.28f),
                        curveCyan.copy(alpha = 0.10f),
                        Color.Transparent
                    )
                } else {
                    listOf(
                        curveMuted.copy(alpha = 0.10f),
                        Color.Transparent
                    )
                }
            )

            drawPath(path = fillPath, brush = areaFillBrush)

            drawPath(
                path = curvePath,
                brush = primaryStrokeBrush,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw Glowing Control Nodes at each Frequency Band
            points.forEach { pt ->
                // Vertical guide line to node
                drawLine(
                    color = curveBorder.copy(alpha = 0.45f),
                    start = Offset(pt.x, 0f),
                    end = Offset(pt.x, h),
                    strokeWidth = 0.75.dp.toPx()
                )
                if (isEnabled) {
                    drawCircle(
                        color = curvePrimary.copy(alpha = 0.26f),
                        radius = 7.dp.toPx(),
                        center = pt
                    )
                }
                drawCircle(
                    color = if (isEnabled) curveText else curveMuted,
                    radius = 3.5.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}

/**
 * 5-Band Vertical Studio Faders Bank
 */
@Composable
private fun StudioVerticalFaderBank(
    bands: List<EqualizerBand>,
    isEnabled: Boolean,
    onSetBandLevel: (Short, Short) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        bands.forEach { band ->
            StudioVerticalFaderChannel(
                band = band,
                isEnabled = isEnabled,
                onLevelChanged = { newMb -> onSetBandLevel(band.index, newMb) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StudioVerticalFaderChannel(
    band: EqualizerBand,
    isEnabled: Boolean,
    onLevelChanged: (Short) -> Unit,
    modifier: Modifier = Modifier
) {
    val minMb = band.minLevelMb.toInt()
    val maxMb = band.maxLevelMb.toInt()
    val rangeMb = (maxMb - minMb).coerceAtLeast(1)
    val currentFraction = ((band.currentLevelMb.toInt() - minMb).toFloat() / rangeMb.toFloat())
        .coerceIn(0f, 1f)

    val animatedFraction by animateFloatAsState(
        targetValue = if (isEnabled) currentFraction else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "verticalFader_${band.index}"
    )

    val currentOnLevelChanged by rememberUpdatedState(onLevelChanged)
    val dbValue = band.currentLevelMb / 100f

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Live Monospace dB Readout Pill
        Surface(
            color = CarbonElevated,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.border(
                width = 0.5.dp,
                color = if (isEnabled && band.currentLevelMb.toInt() != 0) {
                    ElectricMint.copy(alpha = 0.45f)
                } else {
                    SubtleBorder.copy(alpha = 0.6f)
                },
                shape = RoundedCornerShape(8.dp)
            )
        ) {
            Text(
                text = String.format(Locale.US, "%+.1fdB", dbValue),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                ),
                color = if (!isEnabled) DimSlate
                else if (band.currentLevelMb > 0) ElectricMint
                else if (band.currentLevelMb < 0) CyberCyan
                else AlabasterWhite.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        // Tactile Vertical Fader Track Canvas with Drag & Tap Gestures
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(178.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PitchBlack.copy(alpha = 0.68f))
                .border(0.75.dp, SubtleBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .pointerInput(isEnabled, minMb, maxMb) {
                    if (!isEnabled) return@pointerInput
                    detectTapGestures { offset ->
                        val h = size.height.coerceAtLeast(1).toFloat()
                        val pad = 16.dp.toPx()
                        val usableH = (h - pad * 2f).coerceAtLeast(1f)
                        val normY = ((offset.y - pad) / usableH).coerceIn(0f, 1f)
                        val newFraction = 1f - normY
                        val newMb = (minMb + newFraction * rangeMb).roundToInt()
                            .coerceIn(minMb, maxMb)
                            .toShort()
                        currentOnLevelChanged(newMb)
                    }
                }
                .pointerInput(isEnabled, minMb, maxMb) {
                    if (!isEnabled) return@pointerInput
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val h = size.height.coerceAtLeast(1).toFloat()
                        val pad = 16.dp.toPx()
                        val usableH = (h - pad * 2f).coerceAtLeast(1f)
                        val normY = ((change.position.y - pad) / usableH).coerceIn(0f, 1f)
                        val newFraction = 1f - normY
                        val newMb = (minMb + newFraction * rangeMb).roundToInt()
                            .coerceIn(minMb, maxMb)
                            .toShort()
                        currentOnLevelChanged(newMb)
                    }
                }
                .testTag("studio_vertical_fader_${band.index}")
        ) {
            val faderTextPrimary = AlabasterWhite
            val faderBorder = SubtleBorder
            val faderElevated = CarbonElevated
            val faderPrimary = ElectricMint
            val faderCyan = CyberCyan
            val faderViolet = NeonViolet
            val faderMuted = MutedSlate

            Canvas(modifier = Modifier.fillMaxSize()) {
                val pad = 16.dp.toPx()
                val usableH = (size.height - pad * 2f).coerceAtLeast(1f)
                val centerX = size.width / 2f
                val centerY = pad + usableH * 0.5f
                val thumbY = pad + usableH * (1f - animatedFraction)

                // Studio Fader Tick Marks on Left & Right
                val tickSteps = 10
                for (t in 0..tickSteps) {
                    val ty = pad + usableH * (t.toFloat() / tickSteps.toFloat())
                    val isCenter = t == tickSteps / 2
                    val tickLen = if (isCenter) 7.dp.toPx() else 4.dp.toPx()
                    val tickColor = if (isCenter) faderTextPrimary.copy(alpha = 0.55f)
                    else faderBorder.copy(alpha = 0.65f)

                    drawLine(
                        color = tickColor,
                        start = Offset(6.dp.toPx(), ty),
                        end = Offset(6.dp.toPx() + tickLen, ty),
                        strokeWidth = if (isCenter) 1.5.dp.toPx() else 1.dp.toPx()
                    )
                    drawLine(
                        color = tickColor,
                        start = Offset(size.width - 6.dp.toPx() - tickLen, ty),
                        end = Offset(size.width - 6.dp.toPx(), ty),
                        strokeWidth = if (isCenter) 1.5.dp.toPx() else 1.dp.toPx()
                    )
                }

                // Center Track Groove
                val grooveWidth = 6.dp.toPx()
                drawRoundRect(
                    color = faderElevated,
                    topLeft = Offset(centerX - grooveWidth / 2f, pad),
                    size = Size(grooveWidth, usableH),
                    cornerRadius = CornerRadius(grooveWidth / 2f, grooveWidth / 2f)
                )

                // Bipolar Active Fill Bar (From 0dB center line to Thumb)
                if (isEnabled && abs(thumbY - centerY) > 1.dp.toPx()) {
                    val topY = minOf(centerY, thumbY)
                    val barH = abs(thumbY - centerY)
                    val activeBrush = if (thumbY <= centerY) {
                        Brush.verticalGradient(listOf(faderPrimary, faderCyan))
                    } else {
                        Brush.verticalGradient(listOf(faderCyan, faderViolet))
                    }
                    drawRoundRect(
                        brush = activeBrush,
                        topLeft = Offset(centerX - grooveWidth / 2f, topY),
                        size = Size(grooveWidth, barH),
                        cornerRadius = CornerRadius(grooveWidth / 2f, grooveWidth / 2f)
                    )
                }

                // Studio Fader Cap Thumb
                val capWidth = 28.dp.toPx()
                val capHeight = 16.dp.toPx()
                val capTopLeft = Offset(centerX - capWidth / 2f, thumbY - capHeight / 2f)

                drawRoundRect(
                    color = faderElevated,
                    topLeft = capTopLeft,
                    size = Size(capWidth, capHeight),
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                )
                drawRoundRect(
                    color = if (isEnabled) faderPrimary else faderBorder,
                    topLeft = capTopLeft,
                    size = Size(capWidth, capHeight),
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                    style = Stroke(width = 1.2.dp.toPx())
                )
                // Center indicator slit on fader cap
                drawLine(
                    color = if (isEnabled) faderPrimary else faderMuted,
                    start = Offset(centerX - 7.dp.toPx(), thumbY),
                    end = Offset(centerX + 7.dp.toPx(), thumbY),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Frequency Band Label at Bottom (60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz)
        Text(
            text = band.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = if (isEnabled) AlabasterWhite else MutedSlate,
            maxLines = 1
        )
    }
}

@Composable
private fun AppearanceAndThemesCard(
    themeState: AppThemeState,
    onSelectThemePreset: (StudioThemePreset) -> Unit,
    onSetAmoledPureBlack: (Boolean) -> Unit,
    onSetDynamicArtworkTint: (Boolean) -> Unit
) {
    Surface(
        color = CarbonSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(0.75.dp, SubtleBorder.copy(alpha = 0.75f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("appearance_themes_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ElectricMint.copy(alpha = 0.16f))
                            .border(0.75.dp, ElectricMint.copy(alpha = 0.55f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = ElectricMint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Appearance & Themes",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = AlabasterWhite
                        )
                        Text(
                            text = "Studio atmosphere & surface palette",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSlate
                        )
                    }
                }

                Surface(
                    color = ElectricMint.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(0.6.dp, ElectricMint.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = themeState.selectedTheme.displayName.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        ),
                        color = ElectricMint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Interactive Horizontal Theme Preview Cards Carousel
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("theme_carousel")
            ) {
                items(
                    items = StudioThemePreset.entries,
                    key = { it.name }
                ) { preset ->
                    StudioThemePreviewCard(
                        preset = preset,
                        isSelected = themeState.selectedTheme == preset,
                        amoledPureBlack = themeState.amoledPureBlack,
                        onClick = { onSelectThemePreset(preset) }
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.6.dp,
                color = SubtleBorder.copy(alpha = 0.65f)
            )

            // True OLED Pure Black Switch (for dark themes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "True OLED Pure Black",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (themeState.selectedTheme.isLight) MutedSlate else AlabasterWhite
                    )
                    Text(
                        text = if (themeState.selectedTheme.isLight) {
                            "Unavailable while Paper Daylight is active"
                        } else {
                            "Drops base canvas to #000000 for AMOLED depth & battery"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlate
                    )
                }
                Switch(
                    checked = themeState.amoledPureBlack && !themeState.selectedTheme.isLight,
                    onCheckedChange = onSetAmoledPureBlack,
                    enabled = !themeState.selectedTheme.isLight,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ThemeOnPrimary,
                        checkedTrackColor = ElectricMint,
                        uncheckedThumbColor = MutedSlate,
                        uncheckedTrackColor = CarbonElevated,
                        uncheckedBorderColor = SubtleBorder
                    ),
                    modifier = Modifier.testTag("theme_amoled_switch")
                )
            }

            // Dynamic Artwork Tint Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Dynamic Artwork Tint",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = AlabasterWhite
                    )
                    Text(
                        text = "Blend cover-art glow into the player & screen backdrops",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlate
                    )
                }
                Switch(
                    checked = themeState.dynamicArtworkTint,
                    onCheckedChange = onSetDynamicArtworkTint,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ThemeOnPrimary,
                        checkedTrackColor = ElectricMint,
                        uncheckedThumbColor = MutedSlate,
                        uncheckedTrackColor = CarbonElevated,
                        uncheckedBorderColor = SubtleBorder
                    ),
                    modifier = Modifier.testTag("theme_dynamic_artwork_switch")
                )
            }
        }
    }
}

@Composable
private fun StudioThemePreviewCard(
    preset: StudioThemePreset,
    isSelected: Boolean,
    amoledPureBlack: Boolean,
    onClick: () -> Unit
) {
    val previewPalette = remember(preset, amoledPureBlack) {
        resolveStudioPalette(
            preset = preset,
            amoledPureBlack = amoledPureBlack,
            dynamicArtworkTint = false
        )
    }

    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(previewPalette.background)
            .border(
                width = if (isSelected) 1.75.dp else 0.75.dp,
                color = if (isSelected) previewPalette.primary else previewPalette.subtleBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
            .testTag("theme_preview_card_${preset.name.lowercase()}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Miniature UI Preview Window
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            previewPalette.surface,
                            previewPalette.surfaceElevated
                        )
                    )
                )
                .border(0.6.dp, previewPalette.subtleBorder, RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Palette Swatch Dots
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(previewPalette.primary)
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(previewPalette.tertiary)
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(previewPalette.secondary)
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(previewPalette.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected theme",
                                tint = previewPalette.onPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }

                // Miniature Studio Waveform Bars + Mock Mini Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val barHeights = listOf(7.dp, 14.dp, 10.dp, 16.dp, 9.dp)
                        barHeights.forEachIndexed { idx, h ->
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(h)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (idx % 2 == 0) previewPalette.primary else previewPalette.tertiary)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(previewPalette.textPrimary.copy(alpha = 0.22f))
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = preset.displayName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = previewPalette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = preset.vibeSubtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = previewPalette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
