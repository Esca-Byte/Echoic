package com.example.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.model.ListeningStatsData
import com.example.data.model.ReplayCardTheme
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
import com.example.ui.theme.ThemeOnPrimary
import com.example.ui.theme.tactileSquashPress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Immutable
private data class ReplayPalette(
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val accent: Color,
    val secondaryAccent: Color
)

private fun ReplayCardTheme.toPalette(): ReplayPalette = when (this) {
    ReplayCardTheme.OBSIDIAN_MINT -> ReplayPalette(
        bgTop = Color(0xFF0A2A22),
        bgMid = Color(0xFF0B1416),
        bgBottom = Color(0xFF06080A),
        accent = Color(0xFF2BF0A0),
        secondaryAccent = Color(0xFF39D9FF)
    )
    ReplayCardTheme.SUNSET_VINYL -> ReplayPalette(
        bgTop = Color(0xFF3A1622),
        bgMid = Color(0xFF1A0F18),
        bgBottom = Color(0xFF0B070B),
        accent = Color(0xFFFF6B7D),
        secondaryAccent = Color(0xFFFFB86C)
    )
    ReplayCardTheme.CYBER_VIOLET -> ReplayPalette(
        bgTop = Color(0xFF281448),
        bgMid = Color(0xFF130D24),
        bgBottom = Color(0xFF080612),
        accent = Color(0xFFC084FC),
        secondaryAccent = Color(0xFF38BDF8)
    )
    ReplayCardTheme.CHAMPAGNE_GOLD -> ReplayPalette(
        bgTop = Color(0xFF332512),
        bgMid = Color(0xFF1A140D),
        bgBottom = Color(0xFF0B0907),
        accent = Color(0xFFF6C667),
        secondaryAccent = Color(0xFFE8A87C)
    )
    ReplayCardTheme.MIDNIGHT_OLED -> ReplayPalette(
        bgTop = Color(0xFF121826),
        bgMid = Color(0xFF090B10),
        bgBottom = Color(0xFF000000),
        accent = Color(0xFF60A5FA),
        secondaryAccent = Color(0xFF34D399)
    )
}

/**
 * Interactive Shareable "Echoic Replay" Listening Cards ModalBottomSheet.
 * Features 4 swipeable animated summary cards, 5 studio visual themes,
 * high-resolution 1080x1920 PNG Story Card export via FileProvider, and Text Recap share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoicReplayBottomSheet(
    stats: ListeningStatsData,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalMotionConfig.current.reduceMotion

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    var selectedTheme by remember { mutableStateOf(ReplayCardTheme.OBSIDIAN_MINT) }
    var isRenderingPng by remember { mutableStateOf(false) }

    val palette = selectedTheme.toPalette()
    val animatedAccent by animateColorAsState(
        targetValue = palette.accent,
        animationSpec = MotionTokens.standardTween(reduceMotion),
        label = "replay_accent"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CarbonSurface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("echoic_replay_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(animatedAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = animatedAccent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Echoic Replay",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = AlabasterWhite
                        )
                        Text(
                            text = "Swipe cards · Pick a theme · Share as Story PNG",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedSlate
                        )
                    }
                }

                SoftTonalCircleIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = "Close Echoic Replay",
                    onClick = onDismiss,
                    size = 38.dp,
                    iconSize = 18.dp,
                    containerColor = CarbonElevated,
                    modifier = Modifier.testTag("echoic_replay_close_button")
                )
            }

            // 2. Swipeable 4-Card HorizontalPager Preview
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 18.dp),
                pageSpacing = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("echoic_replay_pager")
            ) { page ->
                ReplayStoryCardPreview(
                    pageIndex = page,
                    stats = stats,
                    palette = palette,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.74f)
                )
            }

            // 3. Animated Page Indicator Dots + Card Label
            val pageLabels = remember {
                listOf("Overview", "#1 Track", "Top Artists", "Sound Aura")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pageLabels.forEachIndexed { index, label ->
                    val active = pagerState.currentPage == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (active) 22.dp else 7.dp,
                        animationSpec = MotionTokens.snappySpring(reduceMotion),
                        label = "replay_dot_$index"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(7.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(if (active) animatedAccent else SubtleBorder)
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                            .testTag("replay_page_dot_$index")
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = pageLabels[pagerState.currentPage],
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = animatedAccent
                )
            }

            // 4. Visual Theme Picker Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("replay_theme_picker_row")
            ) {
                items(
                    items = ReplayCardTheme.entries,
                    key = { it.name }
                ) { theme ->
                    val isSelected = selectedTheme == theme
                    val themePalette = theme.toPalette()
                    val interaction = remember { MutableInteractionSource() }
                    Surface(
                        color = if (isSelected) themePalette.accent.copy(alpha = 0.18f) else CarbonElevated,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            width = if (isSelected) 1.dp else 0.7.dp,
                            color = if (isSelected) themePalette.accent else SubtleBorder.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .tactileSquashPress(interaction, pressedScaleX = 0.96f, pressedScaleY = 0.94f)
                            .clip(RoundedCornerShape(50))
                            .clickable(
                                interactionSource = interaction,
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedTheme = theme
                            }
                            .testTag("replay_theme_${theme.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(themePalette.accent)
                            )
                            Text(
                                text = theme.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) themePalette.accent else AlabasterWhite
                            )
                        }
                    }
                }
            }

            // 5. Primary PNG Share Action + Secondary Text Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (!isRenderingPng) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isRenderingPng = true
                            scope.launch {
                                shareReplayCardPng(
                                    context = context,
                                    pageIndex = pagerState.currentPage,
                                    stats = stats,
                                    theme = selectedTheme
                                )
                                isRenderingPng = false
                            }
                        }
                    },
                    enabled = !isRenderingPng,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = animatedAccent,
                        contentColor = ThemeOnPrimary
                    ),
                    modifier = Modifier
                        .weight(1.35f)
                        .height(48.dp)
                        .testTag("replay_share_png_button")
                ) {
                    if (isRenderingPng) {
                        CircularProgressIndicator(
                            color = ThemeOnPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Rendering…",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "Share Story PNG",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        shareReplayTextSummary(context, stats)
                    },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier
                        .weight(0.85f)
                        .height(48.dp)
                        .testTag("replay_share_text_button")
                ) {
                    Text(
                        text = "Share Text",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = AlabasterWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplayStoryCardPreview(
    pageIndex: Int,
    stats: ListeningStatsData,
    palette: ReplayPalette,
    modifier: Modifier = Modifier
) {
    val bgBrush = remember(palette) {
        Brush.verticalGradient(
            colors = listOf(palette.bgTop, palette.bgMid, palette.bgBottom)
        )
    }

    Surface(
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.42f)),
        shadowElevation = 12.dp,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Brand Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "♪ ECHOIC REPLAY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        ),
                        color = palette.accent
                    )
                    Surface(
                        color = palette.accent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = stats.listenerPersona.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            color = palette.accent,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                // Dynamic Card Center Body
                when (pageIndex) {
                    0 -> ReplayOverviewCardBody(stats = stats, palette = palette)
                    1 -> ReplayTopTrackCardBody(stats = stats, palette = palette)
                    2 -> ReplayTopArtistsCardBody(stats = stats, palette = palette)
                    else -> ReplaySoundAuraCardBody(stats = stats, palette = palette)
                }

                // Bottom Footer Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stats.totalPlays} plays · ${stats.uniqueTracksCount} tracks",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = Color.White.copy(alpha = 0.65f)
                    )
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplayOverviewCardBody(
    stats: ListeningStatsData,
    palette: ReplayPalette
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "MINUTES LISTENED",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    letterSpacing = 1.sp
                ),
                color = Color.White.copy(alpha = 0.65f)
            )
            Text(
                text = "${stats.totalMinutes}",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 56.sp,
                    lineHeight = 58.sp
                ),
                color = palette.accent
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReplayMiniStatTile(
                label = "TOTAL PLAYS",
                value = "${stats.totalPlays}",
                accent = palette.secondaryAccent,
                modifier = Modifier.weight(1f)
            )
            ReplayMiniStatTile(
                label = "ARTISTS",
                value = "${stats.uniqueArtistsCount}",
                accent = palette.accent,
                modifier = Modifier.weight(1f)
            )
        }

        Surface(
            color = Color.White.copy(alpha = 0.06f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (stats.topArtistArtwork.isNotBlank()) {
                        OptimizedArtworkImage(
                            artworkUrl = stats.topArtistArtwork,
                            contentDescription = stats.topArtistName,
                            targetSizeDp = 36.dp,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(palette.accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Person, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TOP ARTIST",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontFamily = JetBrainsMonoFontFamily),
                            color = palette.secondaryAccent
                        )
                        Text(
                            text = stats.topArtistName.ifBlank { "Start Listening" },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val art = stats.topSong?.artworkUrl.orEmpty()
                    if (art.isNotBlank()) {
                        OptimizedArtworkImage(
                            artworkUrl = art,
                            contentDescription = stats.topSong?.title,
                            targetSizeDp = 36.dp,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(palette.accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TOP TRACK",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontFamily = JetBrainsMonoFontFamily),
                            color = palette.accent
                        )
                        Text(
                            text = stats.topSong?.title ?: "Play songs to unlock",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplayTopTrackCardBody(
    stats: ListeningStatsData,
    palette: ReplayPalette
) {
    val topSong = stats.topSong
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "#1 MOST PLAYED TRACK",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = palette.secondaryAccent
        )

        Box(
            modifier = Modifier
                .size(164.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.5.dp, palette.accent.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            if (!topSong?.artworkUrl.isNullOrBlank()) {
                OptimizedArtworkImage(
                    artworkUrl = topSong!!.artworkUrl,
                    contentDescription = topSong.title,
                    targetSizeDp = 164.dp,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = topSong?.title ?: "No Top Track Yet",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = topSong?.artist ?: "Play your favorite tracks to build your Replay",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (stats.topSongPlayCount > 0) {
            Surface(
                color = palette.accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(0.75.dp, palette.accent.copy(alpha = 0.5f))
            ) {
                Text(
                    text = if (stats.topSongPlayCount == 1) "Played 1 time on repeat" else "Played ${stats.topSongPlayCount} times",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = palette.accent,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ReplayTopArtistsCardBody(
    stats: ListeningStatsData,
    palette: ReplayPalette
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "TOP ARTISTS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = palette.secondaryAccent
        )

        val artists = stats.topArtists
        if (artists.isEmpty()) {
            Text(
                text = "Listen to a few songs and your top artists podium will appear right here.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                artists.take(5).forEachIndexed { idx, artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (idx == 0) palette.accent.copy(alpha = 0.14f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "#${idx + 1}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = if (idx == 0) palette.accent else Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.width(26.dp)
                        )
                        if (artist.artworkUrl.isNotBlank()) {
                            OptimizedArtworkImage(
                                artworkUrl = artist.artworkUrl,
                                contentDescription = artist.name,
                                targetSizeDp = 34.dp,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Person, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (idx == 0) FontWeight.ExtraBold else FontWeight.SemiBold
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = artist.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplaySoundAuraCardBody(
    stats: ListeningStatsData,
    palette: ReplayPalette
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "YOUR SOUND AURA",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    letterSpacing = 1.sp
                ),
                color = palette.secondaryAccent
            )
            Text(
                text = stats.topGenre.ifBlank { "Eclectic Studio" },
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = palette.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val songs = stats.topSongs
        if (songs.isEmpty()) {
            Text(
                text = "Your top 5 tracks will light up here as you listen.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                songs.take(5).forEachIndexed { idx, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${idx + 1}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = if (idx == 0) palette.accent else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.width(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.62f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplayMiniStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.1f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 9.sp
                ),
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = accent
            )
        }
    }
}

private fun shareReplayTextSummary(context: Context, stats: ListeningStatsData) {
    val lines = buildString {
        appendLine("♪ My Echoic Replay")
        appendLine("• Listening Time: ${stats.totalMinutes} min (${stats.totalPlays} plays)")
        appendLine("• Sound Aura: ${stats.topGenre} · ${stats.listenerPersona}")
        if (stats.topArtistName.isNotBlank() && stats.topArtistName != "Explore Music") {
            appendLine("• Top Artist: ${stats.topArtistName}")
        }
        stats.topSong?.let { top ->
            appendLine("• #1 Track: ${top.title} — ${top.artist}")
        }
        if (stats.topSongs.size > 1) {
            appendLine()
            appendLine("Top Tracks:")
            stats.topSongs.take(5).forEachIndexed { idx, song ->
                appendLine("${idx + 1}. ${song.title} — ${song.artist}")
            }
        }
    }.trim()

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "My Echoic Replay")
        putExtra(Intent.EXTRA_TEXT, lines)
    }
    runCatching {
        context.startActivity(Intent.createChooser(sendIntent, "Share Echoic Replay"))
    }.onFailure {
        Toast.makeText(context, "No compatible app found to share", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Renders a crisp 1080x1920 vertical Story PNG of the active Echoic Replay card
 * and launches Android's native share sheet via FileProvider.
 */
private suspend fun shareReplayCardPng(
    context: Context,
    pageIndex: Int,
    stats: ListeningStatsData,
    theme: ReplayCardTheme
) {
    val pngUri = withContext(Dispatchers.IO) {
        runCatching {
            val width = 1080
            val height = 1920
            val palette = theme.toPalette()
            val accentArgb = palette.accent.toArgb()
            val secondaryArgb = palette.secondaryAccent.toArgb()

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = AndroidCanvas(bitmap)

                // 1. Background vertical gradient + ambient radial glow
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(palette.bgTop.toArgb(), palette.bgMid.toArgb(), palette.bgBottom.toArgb()),
                        floatArrayOf(0f, 0.52f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        width * 0.5f, height * 0.28f, width * 0.72f,
                        intArrayOf(
                            android.graphics.Color.argb(
                                88,
                                android.graphics.Color.red(accentArgb),
                                android.graphics.Color.green(accentArgb),
                                android.graphics.Color.blue(accentArgb)
                            ),
                            android.graphics.Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)

                // 2. Framed outer card border
                val cardRect = RectF(72f, 110f, width - 72f, height - 110f)
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3.5f
                    color = accentArgb
                    alpha = 110
                }
                canvas.drawRoundRect(cardRect, 64f, 64f, borderPaint)

                // Helper to load real Coil artwork bitmap safely
                suspend fun loadArt(url: String?): Bitmap? {
                    if (url.isNullOrBlank()) return null
                    return runCatching {
                        val loader = ImageLoader(context)
                        val req = ImageRequest.Builder(context)
                            .data(url)
                            .allowHardware(false)
                            .size(512)
                            .build()
                        val result = loader.execute(req)
                        if (result is SuccessResult) {
                            when (val drawable = result.drawable) {
                                is BitmapDrawable -> drawable.bitmap
                                else -> {
                                    val w = drawable.intrinsicWidth.coerceAtLeast(256)
                                    val h = drawable.intrinsicHeight.coerceAtLeast(256)
                                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { rendered ->
                                        val c = AndroidCanvas(rendered)
                                        drawable.setBounds(0, 0, w, h)
                                        drawable.draw(c)
                                    }
                                }
                            }
                        } else null
                    }.getOrNull()
                }

                // 3. Top header branding + persona badge
                val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accentArgb
                    textSize = 36f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                val personaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = secondaryArgb
                    textSize = 30f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                canvas.drawText("♪ ECHOIC REPLAY", 136f, 224f, brandPaint)
                canvas.drawText(stats.listenerPersona.uppercase(), 136f, 274f, personaPaint)

                // Shared text paints
                val sectionLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = secondaryArgb
                    textSize = 32f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                val heroNumPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accentArgb
                    textSize = 188f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 54f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(190, 225, 232, 240)
                    textSize = 36f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
                val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(24, 255, 255, 255)
                }

                when (pageIndex) {
                    0 -> {
                        // Overview Card
                        canvas.drawText("MINUTES LISTENED", 136f, 440f, sectionLabelPaint)
                        canvas.drawText("${stats.totalMinutes}", 132f, 628f, heroNumPaint)

                        val statBox1 = RectF(136f, 700f, 520f, 880f)
                        val statBox2 = RectF(560f, 700f, 944f, 880f)
                        canvas.drawRoundRect(statBox1, 34f, 34f, glassPaint)
                        canvas.drawRoundRect(statBox2, 34f, 34f, glassPaint)

                        canvas.drawText("TOTAL PLAYS", 174f, 766f, subtitlePaint)
                        canvas.drawText("${stats.totalPlays}", 174f, 844f, titlePaint.apply { color = secondaryArgb })
                        canvas.drawText("ARTISTS", 598f, 766f, subtitlePaint)
                        canvas.drawText("${stats.uniqueArtistsCount}", 598f, 844f, titlePaint.apply { color = accentArgb })
                        titlePaint.color = android.graphics.Color.WHITE

                        val summaryRect = RectF(136f, 940f, 944f, 1460f)
                        canvas.drawRoundRect(summaryRect, 42f, 42f, glassPaint)

                        val artistArt = loadArt(stats.topArtistArtwork)
                        val topSongArt = loadArt(stats.topSong?.artworkUrl)

                        // Top Artist Row
                        val aRect = RectF(186f, 1004f, 326f, 1144f)
                        if (artistArt != null) {
                            val p = Path().apply { addOval(aRect, Path.Direction.CW) }
                            canvas.save(); canvas.clipPath(p)
                            canvas.drawBitmap(artistArt, null, aRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                            canvas.restore()
                        }
                        canvas.drawText("TOP ARTIST", 364f, 1054f, sectionLabelPaint)
                        val safeArtist = stats.topArtistName.ifBlank { "Explore Music" }.take(22)
                        canvas.drawText(safeArtist, 364f, 1122f, titlePaint)

                        // Top Song Row
                        val sRect = RectF(186f, 1230f, 326f, 1370f)
                        if (topSongArt != null) {
                            val p = Path().apply { addRoundRect(sRect, 26f, 26f, Path.Direction.CW) }
                            canvas.save(); canvas.clipPath(p)
                            canvas.drawBitmap(topSongArt, null, sRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                            canvas.restore()
                        }
                        canvas.drawText("TOP TRACK", 364f, 1280f, brandPaint.apply { textSize = 32f })
                        val safeSong = (stats.topSong?.title ?: "No plays yet").take(22)
                        canvas.drawText(safeSong, 364f, 1348f, titlePaint)
                    }

                    1 -> {
                        // #1 Track Spotlight
                        canvas.drawText("#1 MOST PLAYED TRACK", 136f, 420f, sectionLabelPaint)
                        val art = loadArt(stats.topSong?.artworkUrl)
                        val artRect = RectF(220f, 490f, 860f, 1130f)
                        if (art != null) {
                            val clipPath = Path().apply { addRoundRect(artRect, 54f, 54f, Path.Direction.CW) }
                            canvas.save(); canvas.clipPath(clipPath)
                            canvas.drawBitmap(art, null, artRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                            canvas.restore()
                        } else {
                            canvas.drawRoundRect(artRect, 54f, 54f, glassPaint)
                        }
                        canvas.drawRoundRect(artRect, 54f, 54f, borderPaint)

                        val trackTitle = stats.topSong?.title ?: "Start Listening"
                        val layout = StaticLayout.Builder
                            .obtain(trackTitle, 0, trackTitle.length, titlePaint.apply { textSize = 64f }, 800)
                            .setAlignment(Layout.Alignment.ALIGN_CENTER)
                            .setMaxLines(2)
                            .build()
                        canvas.save()
                        canvas.translate(140f, 1210f)
                        layout.draw(canvas)
                        canvas.restore()

                        val artistText = stats.topSong?.artist.orEmpty().take(34)
                        val artistLayout = StaticLayout.Builder
                            .obtain(artistText, 0, artistText.length, subtitlePaint.apply { textSize = 42f }, 800)
                            .setAlignment(Layout.Alignment.ALIGN_CENTER)
                            .setMaxLines(1)
                            .build()
                        canvas.save()
                        canvas.translate(140f, 1370f)
                        artistLayout.draw(canvas)
                        canvas.restore()

                        if (stats.topSongPlayCount > 0) {
                            val countText = "PLAYED ${stats.topSongPlayCount} TIMES"
                            val countLayout = StaticLayout.Builder
                                .obtain(countText, 0, countText.length, brandPaint.apply { textSize = 36f }, 800)
                                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                                .build()
                            canvas.save()
                            canvas.translate(140f, 1480f)
                            countLayout.draw(canvas)
                            canvas.restore()
                        }
                    }

                    2 -> {
                        // Top Artists Podium
                        canvas.drawText("TOP ARTISTS", 136f, 430f, sectionLabelPaint)
                        var y = 500f
                        stats.topArtists.take(5).forEachIndexed { idx, artist ->
                            val rowRect = RectF(136f, y, 944f, y + 190f)
                            canvas.drawRoundRect(rowRect, 34f, 34f, glassPaint)
                            val art = loadArt(artist.artworkUrl)
                            val avatarRect = RectF(264f, y + 35f, 384f, y + 155f)
                            if (art != null) {
                                val p = Path().apply { addOval(avatarRect, Path.Direction.CW) }
                                canvas.save(); canvas.clipPath(p)
                                canvas.drawBitmap(art, null, avatarRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                                canvas.restore()
                            }
                            val rankPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = if (idx == 0) accentArgb else android.graphics.Color.argb(180, 255, 255, 255)
                                textSize = 44f
                                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                            }
                            canvas.drawText("#${idx + 1}", 170f, y + 112f, rankPaint)
                            canvas.drawText(artist.name.take(20), 420f, y + 96f, titlePaint.apply { textSize = 44f })
                            canvas.drawText(artist.subtitle, 420f, y + 144f, subtitlePaint.apply { textSize = 30f })
                            y += 220f
                        }
                    }

                    else -> {
                        // Sound Aura & Top 5 Tracks
                        canvas.drawText("YOUR SOUND AURA", 136f, 420f, sectionLabelPaint)
                        canvas.drawText(
                            stats.topGenre.ifBlank { "Eclectic Studio" }.take(18),
                            136f,
                            532f,
                            titlePaint.apply { textSize = 82f; color = accentArgb }
                        )
                        titlePaint.color = android.graphics.Color.WHITE
                        var y = 620f
                        stats.topSongs.take(5).forEachIndexed { idx, song ->
                            val rowRect = RectF(136f, y, 944f, y + 170f)
                            canvas.drawRoundRect(rowRect, 30f, 30f, glassPaint)
                            canvas.drawText(
                                "${idx + 1}",
                                178f,
                                y + 104f,
                                brandPaint.apply { textSize = 44f }
                            )
                            canvas.drawText(song.title.take(24), 258f, y + 84f, titlePaint.apply { textSize = 42f })
                            canvas.drawText(song.artist.take(30), 258f, y + 132f, subtitlePaint.apply { textSize = 30f })
                            y += 196f
                        }
                    }
                }

                // 4. Footer Branding
                val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accentArgb
                    textSize = 30f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                canvas.drawText(
                    "ECHOIC · ${stats.totalPlays} PLAYS · ${stats.totalMinutes} MIN",
                    136f,
                    height - 172f,
                    footerPaint
                )

                val cardsDir = File(context.cacheDir, "replay_cards").apply { mkdirs() }
                val cutoff = System.currentTimeMillis() - 3_600_000L
                cardsDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoff) file.delete()
                }
                val outFile = File(cardsDir, "echoic_replay_${System.currentTimeMillis()}.png")
                FileOutputStream(outFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.flush()
                }
                if (!outFile.exists() || outFile.length() == 0L) return@runCatching null
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outFile
                )
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
    }

    if (pngUri != null) {
        val shareClip = ClipData.newUri(context.contentResolver, "Echoic Replay Card", pngUri)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, pngUri)
            putExtra(Intent.EXTRA_SUBJECT, "My Echoic Replay")
            clipData = shareClip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(sendIntent, "Share Echoic Replay Card").apply {
            clipData = shareClip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(chooserIntent)
        }.onFailure {
            Toast.makeText(context, "No compatible app found to share image", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "Could not render Echoic Replay card", Toast.LENGTH_SHORT).show()
    }
}
