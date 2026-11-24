package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ListeningStatsData
import com.example.data.model.SongItem
import com.example.ui.theme.AlabasterWhite
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.tactileSquashPress

/**
 * Modular Editorial Listening Snapshot card displayed in Library,
 * featuring an interactive "Echoic Replay" pill trigger for shareable story cards.
 */
@Composable
fun ListeningSnapshotCard(
    listeningStats: ListeningStatsData,
    onOpenArtist: (String) -> Unit,
    onPlayTopSong: (SongItem) -> Unit,
    onOpenEchoicReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val replayInteraction = remember { MutableInteractionSource() }
    val playsLabel = if (listeningStats.totalPlays == 1) "1 play" else "${listeningStats.totalPlays} plays"

    Column(
        modifier = modifier
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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

            Surface(
                color = ElectricMint.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(0.8.dp, ElectricMint.copy(alpha = 0.55f)),
                modifier = Modifier
                    .tactileSquashPress(replayInteraction, pressedScaleX = 0.96f, pressedScaleY = 0.93f)
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = replayInteraction,
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenEchoicReplay()
                    }
                    .testTag("open_echoic_replay_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = ElectricMint,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.share_replay_cta),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = ElectricMint,
                        maxLines = 1
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Top Artist inline tile
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        enabled = listeningStats.topArtistName.isNotBlank() &&
                            listeningStats.topArtistName != "Explore Music"
                    ) {
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
                        .background(CarbonElevated),
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
                        text = listeningStats.topArtistName.ifBlank { "Explore Music" },
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

            // Top Track inline tile
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = listeningStats.topSong != null) {
                        listeningStats.topSong?.let(onPlayTopSong)
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
                        .background(CarbonElevated),
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
