package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataSaverOn
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AudioQualitySettings
import com.example.data.model.StreamingQualityMode
import com.example.ui.theme.AlabasterWhite
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMotionConfig
import com.example.ui.theme.MotionTokens
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.tactileSquashPress

/**
 * Compact Audio Quality selector card for Settings with 3 clean options:
 * Auto, High Quality, and Data Saver.
 */
@Composable
fun AudioQualityAndDataSaverCard(
    settings: AudioQualitySettings,
    onSelectQualityMode: (StreamingQualityMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val reduceMotion = LocalMotionConfig.current.reduceMotion

    val selectedMode = remember(settings) {
        when {
            settings.dataSaverEnabled || settings.wifiQuality == StreamingQualityMode.DATA_SAVER ->
                StreamingQualityMode.DATA_SAVER
            settings.wifiQuality == StreamingQualityMode.HIGH &&
                settings.cellularQuality == StreamingQualityMode.HIGH ->
                StreamingQualityMode.HIGH
            else -> StreamingQualityMode.AUTO
        }
    }

    val isDataSaver = selectedMode == StreamingQualityMode.DATA_SAVER
    val accent by animateColorAsState(
        targetValue = if (isDataSaver) CyberCyan else ElectricMint,
        animationSpec = MotionTokens.standardTween(reduceMotion),
        label = "compact_quality_accent"
    )

    val badgeLabel = when (selectedMode) {
        StreamingQualityMode.AUTO -> "Adaptive"
        StreamingQualityMode.HIGH -> "320 kbps"
        StreamingQualityMode.DATA_SAVER -> "96 kbps"
        StreamingQualityMode.BALANCED -> "160 kbps"
    }

    val options = remember {
        listOf(
            StreamingQualityMode.AUTO to "Auto",
            StreamingQualityMode.HIGH to "High Quality",
            StreamingQualityMode.DATA_SAVER to "Data Saver"
        )
    }

    Surface(
        color = CarbonSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.75.dp, SubtleBorder.copy(alpha = 0.75f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_audio_quality_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.14f))
                            .border(0.75.dp, accent.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDataSaver) Icons.Outlined.DataSaverOn else Icons.Outlined.HighQuality,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "Audio Quality",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = AlabasterWhite
                    )
                }

                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(0.7.dp, accent.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = accent,
                        modifier = Modifier
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                            .testTag("audio_quality_active_badge")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { (mode, label) ->
                    val isSelected = selectedMode == mode
                    val optionAccent = if (mode == StreamingQualityMode.DATA_SAVER) CyberCyan else ElectricMint
                    val interaction = remember { MutableInteractionSource() }
                    Surface(
                        color = if (isSelected) optionAccent.copy(alpha = 0.16f) else CarbonElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = if (isSelected) 1.dp else 0.7.dp,
                            color = if (isSelected) optionAccent else SubtleBorder.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .tactileSquashPress(interaction, pressedScaleX = 0.96f, pressedScaleY = 0.94f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = interaction,
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectQualityMode(mode)
                            }
                            .testTag("audio_quality_option_${mode.name.lowercase()}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                ),
                                color = if (isSelected) optionAccent else AlabasterWhite.copy(alpha = 0.88f),
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
}
