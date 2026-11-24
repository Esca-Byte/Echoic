package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SongItem
import com.example.data.model.TrackSortOrder
import com.example.ui.components.AnimatedFavoriteButton
import com.example.ui.components.MiniWaveformIndicator
import com.example.ui.components.OptimizedArtworkImage
import com.example.ui.components.SoftTonalCircleIconButton
import com.example.ui.theme.AlabasterWhite
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PhotoScrimColor
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.tactileSquashPress

/**
 * Shared modular track row, sort chips, and track sorting helpers used across
 * Home, Search, Library, Downloads, Artist Hub, and Playlist Detail screens.
 */
@Composable
internal fun TrackSortOrderChipsRow(
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
            val chipInteraction = remember { MutableInteractionSource() }
            Surface(
                color = if (isSelected) ElectricMint.copy(alpha = 0.16f) else CarbonSurface,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .tactileSquashPress(chipInteraction, pressedScaleX = 0.96f, pressedScaleY = 0.94f)
                    .clip(RoundedCornerShape(50))
                    .border(
                        width = if (isSelected) 0.85.dp else 0.6.dp,
                        color = if (isSelected) ElectricMint else SubtleBorder.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(50)
                    )
                    .clickable(
                        interactionSource = chipInteraction,
                        indication = null
                    ) { onSelectSortOrder(order) }
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

internal fun sortTracksByOrder(
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

@Composable
internal fun SongTrackRowCard(
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
    onOpenArtistClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rowInteraction = remember { MutableInteractionSource() }
    val rowModifier = if (isContainerless) {
        modifier
            .fillMaxWidth()
            .tactileSquashPress(rowInteraction, pressedScaleX = 1.01f, pressedScaleY = 0.97f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrentTrack) ElectricMint.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(
                interactionSource = rowInteraction,
                indication = null,
                onClick = onPlayClick
            )
            .padding(horizontal = 4.dp, vertical = 7.dp)
    } else {
        modifier
            .fillMaxWidth()
            .tactileSquashPress(rowInteraction, pressedScaleX = 1.01f, pressedScaleY = 0.97f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCurrentTrack) CarbonElevated else CarbonSurface)
            .border(
                width = if (isCurrentTrack) 0.75.dp else 0.5.dp,
                color = if (isCurrentTrack) ElectricMint.copy(alpha = 0.5f) else SubtleBorder.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = rowInteraction,
                indication = null,
                onClick = onPlayClick
            )
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
