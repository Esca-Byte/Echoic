package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import android.content.ClipData
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.model.EqualizerState
import com.example.data.model.LyricCardTheme
import com.example.data.model.LyricsData
import com.example.data.model.LyricsVariant
import com.example.data.model.PlayerRepeatMode
import com.example.data.model.SleepTimerState
import com.example.data.model.SongItem
import com.example.ui.theme.AlabasterWhite
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DimSlate
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.PhotoForegroundColor
import com.example.ui.theme.PhotoMutedColor
import com.example.ui.theme.PhotoScrimColor
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.ThemeOnPrimary
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TWO_PI_FLOAT = (2.0 * Math.PI).toFloat()

/**
 * Reusable Soft Minimal Tonal Circular Icon Button with Clean Outlined / Minimal Iconography
 */
@Composable
fun SoftTonalCircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    isActive: Boolean = false,
    activeTint: Color = Color.Unspecified,
    inactiveTint: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
    enabled: Boolean = true
) {
    val resolvedActiveTint = activeTint.takeOrElse { ElectricMint }
    val resolvedInactiveTint = inactiveTint.takeOrElse { AlabasterWhite }
    val baseContainer = containerColor.takeOrElse { CarbonSurface }

    val resolvedContainer = when {
        !enabled -> CarbonSurface.copy(alpha = 0.55f)
        isActive -> resolvedActiveTint.copy(alpha = 0.16f)
        baseContainer == GlassSurface -> CarbonSurface
        else -> baseContainer
    }
    val resolvedBorderColor = when {
        !enabled -> SubtleBorder.copy(alpha = 0.35f)
        isActive -> resolvedActiveTint.copy(alpha = 0.72f)
        baseContainer == ElectricMint -> ElectricMint
        else -> SubtleBorder
    }
    val resolvedTint = when {
        !enabled -> DimSlate
        isActive -> resolvedActiveTint
        else -> resolvedInactiveTint
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .clip(CircleShape)
            .background(resolvedContainer)
            .border(width = 0.85.dp, color = resolvedBorderColor, shape = CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = resolvedTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Color-Coded Platform Badge Pill:
 * - Spotify: Emerald Green (#1DB954)
 * - YouTube Music: Coral Red (#FF3B5C)
 * - JioSaavn: Cyber Cyan (#2BC5B4)
 * - Deezer: Electric Violet (#A855F7)
 * - Custom: Electric Mint (#00E676)
 */
@Composable
fun PlatformBadgePill(
    platform: String,
    modifier: Modifier = Modifier,
    darkBackgroundOverlay: Boolean = false
) {
    val cleanLabel = platform.trim().ifBlank { "JioSaavn" }.uppercase()
    val themeMintColor = ElectricMint
    val accentColor = remember(cleanLabel, themeMintColor) {
        when {
            cleanLabel.contains("SPOTIFY") -> Color(0xFF1DB954)
            cleanLabel.contains("YOUTUBE") || cleanLabel.contains("YT") -> Color(0xFFFF3B5C)
            cleanLabel.contains("SAAVN") -> Color(0xFF2BC5B4)
            cleanLabel.contains("DEEZER") -> Color(0xFFA855F7)
            cleanLabel.contains("CUSTOM") -> themeMintColor
            else -> themeMintColor
        }
    }

    val containerBg = if (darkBackgroundOverlay) {
        PhotoScrimColor.copy(alpha = 0.78f)
    } else {
        accentColor.copy(alpha = 0.15f)
    }

    Surface(
        color = containerBg,
        shape = RoundedCornerShape(50),
        modifier = modifier.border(
            width = 0.6.dp,
            color = accentColor.copy(alpha = 0.55f),
            shape = RoundedCornerShape(50)
        )
    ) {
        Text(
            text = cleanLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            ),
            color = accentColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

/**
 * Spring-Physics Animated Like / Favorite Heart Button with Radial Shockwave Ring & Sparkle Burst.
 * Only runs animations when the favorite state actively changes (zero idle CPU/GPU cost during scroll).
 */
@Composable
fun AnimatedFavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Favorite",
    size: Dp = 38.dp,
    iconSize: Dp = 18.dp,
    activeTint: Color = Color.Unspecified,
    inactiveTint: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified
) {
    val resolvedActiveTintArg = activeTint.takeOrElse { SunsetCoral }
    val resolvedInactiveTintArg = inactiveTint.takeOrElse { MutedSlate }
    val resolvedContainerArg = containerColor.takeOrElse { CarbonElevated }
    val burstAccentColor = ElectricMint
    val haptic = LocalHapticFeedback.current
    val heartScale = remember { Animatable(1f) }
    val heartRotation = remember { Animatable(0f) }
    val burstProgress = remember { Animatable(0f) }

    var previousFavorite by remember { mutableStateOf(isFavorite) }

    LaunchedEffect(isFavorite) {
        if (isFavorite != previousFavorite) {
            previousFavorite = isFavorite
            if (isFavorite) {
                launch {
                    burstProgress.snapTo(0.01f)
                    burstProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                    )
                    burstProgress.snapTo(0f)
                }
                launch {
                    heartRotation.snapTo(0f)
                    heartRotation.animateTo(-14f, tween(70))
                    heartRotation.animateTo(11f, tween(95))
                    heartRotation.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                heartScale.snapTo(0.70f)
                heartScale.animateTo(
                    targetValue = 1.34f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                heartScale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            } else {
                burstProgress.snapTo(0f)
                heartRotation.snapTo(0f)
                heartScale.snapTo(0.76f)
                heartScale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }

    val resolvedContainer by animateColorAsState(
        targetValue = if (isFavorite) resolvedActiveTintArg.copy(alpha = 0.18f) else resolvedContainerArg,
        animationSpec = tween(durationMillis = 180),
        label = "favContainerColor"
    )
    val resolvedBorder by animateColorAsState(
        targetValue = if (isFavorite) resolvedActiveTintArg.copy(alpha = 0.65f) else SubtleBorder.copy(alpha = 0.65f),
        animationSpec = tween(durationMillis = 180),
        label = "favBorderColor"
    )
    val resolvedTint by animateColorAsState(
        targetValue = if (isFavorite) resolvedActiveTintArg else resolvedInactiveTintArg,
        animationSpec = tween(durationMillis = 180),
        label = "favIconTint"
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .drawBehind {
                val p = burstProgress.value
                if (p > 0.01f && p < 0.99f) {
                    val fade = (1f - p).coerceIn(0f, 1f)
                    val baseRadius = this.size.minDimension * 0.48f
                    val ringRadius = baseRadius * (0.75f + p * 0.72f)
                    drawCircle(
                        color = resolvedActiveTintArg.copy(alpha = 0.55f * fade),
                        radius = ringRadius,
                        style = Stroke(width = 2.dp.toPx() * fade)
                    )

                    val particleCount = 6
                    val orbitRadius = baseRadius * (0.65f + p * 0.88f)
                    val dotRadius = 2.2.dp.toPx() * fade
                    for (i in 0 until particleCount) {
                        val angle = (i * 60f - 15f) * (Math.PI.toFloat() / 180f)
                        val px = center.x + cos(angle) * orbitRadius
                        val py = center.y + sin(angle) * orbitRadius
                        val dotColor = if (i % 2 == 0) resolvedActiveTintArg else burstAccentColor
                        drawCircle(
                            color = dotColor.copy(alpha = 0.85f * fade),
                            radius = dotRadius,
                            center = Offset(px, py)
                        )
                    }
                }
            }
            .clip(CircleShape)
            .background(resolvedContainer)
            .border(width = 0.75.dp, color = resolvedBorder, shape = CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = contentDescription,
            tint = resolvedTint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    scaleX = heartScale.value
                    scaleY = heartScale.value
                    rotationZ = heartRotation.value
                }
        )
    }
}

/**
 * Scroll-Optimized Artwork Image that caches its ImageRequest with exact target pixel dimensions
 * so LazyColumn / LazyRow scrolling never allocates requests or decodes oversized bitmaps on the UI thread.
 */
@Composable
fun OptimizedArtworkImage(
    artworkUrl: String,
    contentDescription: String?,
    targetSizeDp: Dp,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetSizePx = remember(targetSizeDp, density) {
        with(density) { (targetSizeDp * 1.6f).roundToPx().coerceIn(96, 720) }
    }
    val imageRequest = remember(context, artworkUrl, targetSizePx) {
        ImageRequest.Builder(context)
            .data(artworkUrl)
            .size(targetSizePx)
            .precision(Precision.INEXACT)
            .memoryCacheKey("${artworkUrl}_$targetSizePx")
            .diskCacheKey(artworkUrl)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}

/**
 * Samples the loaded album cover bitmap via Coil to compute a vibrant, high-contrast
 * primary + secondary ambient glow pair, animated smoothly across track transitions.
 */
@Composable
fun rememberDynamicArtworkGlow(artworkUrl: String): Pair<Color, Color> {
    val context = LocalContext.current
    val studioPalette = LocalStudioPalette.current
    val themePrimary = studioPalette.primary
    val themeSecondary = studioPalette.secondary
    val themeTertiary = studioPalette.tertiary
    val themeCoral = studioPalette.accentCoral
    val useDynamicTint = studioPalette.dynamicArtworkTint

    var targetPrimary by remember(artworkUrl, themePrimary, useDynamicTint) { mutableStateOf(themePrimary) }
    var targetSecondary by remember(artworkUrl, themeSecondary, useDynamicTint) { mutableStateOf(themeSecondary) }

    LaunchedEffect(artworkUrl, useDynamicTint, themePrimary, themeTertiary) {
        if (!useDynamicTint) {
            targetPrimary = themePrimary
            targetSecondary = themeTertiary
            return@LaunchedEffect
        }
        if (artworkUrl.isBlank()) {
            targetPrimary = themePrimary
            targetSecondary = themeSecondary
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(64, 64)
                    .precision(Precision.INEXACT)
                    .allowHardware(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                val result = context.imageLoader.execute(request)
                val bitmap: Bitmap? = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                    var sumR = 0L
                    var sumG = 0L
                    var sumB = 0L
                    var count = 0L
                    val stepX = (bitmap.width / 8).coerceAtLeast(1)
                    val stepY = (bitmap.height / 8).coerceAtLeast(1)
                    val hsv = FloatArray(3)

                    for (x in 0 until bitmap.width step stepX) {
                        for (y in 0 until bitmap.height step stepY) {
                            val pixel = bitmap.getPixel(x, y)
                            val r = android.graphics.Color.red(pixel)
                            val g = android.graphics.Color.green(pixel)
                            val b = android.graphics.Color.blue(pixel)
                            android.graphics.Color.RGBToHSV(r, g, b, hsv)
                            // Favor saturated, non-black, non-white pixels for studio ambient glow
                            if (hsv[1] > 0.18f && hsv[2] in 0.18f..0.95f) {
                                val weight = if (hsv[1] > 0.45f) 3L else 1L
                                sumR += r * weight
                                sumG += g * weight
                                sumB += b * weight
                                count += weight
                            }
                        }
                    }

                    if (count > 0L) {
                        val avgR = (sumR / count).toInt().coerceIn(0, 255)
                        val avgG = (sumG / count).toInt().coerceIn(0, 255)
                        val avgB = (sumB / count).toInt().coerceIn(0, 255)
                        android.graphics.Color.RGBToHSV(avgR, avgG, avgB, hsv)
                        hsv[1] = hsv[1].coerceIn(0.58f, 0.92f)
                        hsv[2] = hsv[2].coerceIn(0.72f, 0.95f)
                        val primaryInt = android.graphics.Color.HSVToColor(hsv)

                        val secHsv = floatArrayOf((hsv[0] + 42f) % 360f, (hsv[1] * 0.9f).coerceIn(0.5f, 0.88f), 0.82f)
                        val secondaryInt = android.graphics.Color.HSVToColor(secHsv)
                        Pair(Color(primaryInt), Color(secondaryInt))
                    } else {
                        null
                    }
                } else {
                    null
                }
            }.getOrNull()
        }

        if (extracted != null) {
            targetPrimary = extracted.first
            targetSecondary = extracted.second
        } else {
            val palette = listOf(
                Pair(themePrimary, themeSecondary),
                Pair(themeTertiary, themeSecondary),
                Pair(themeCoral, themeSecondary),
                Pair(themeSecondary, themeTertiary)
            )
            val chosen = palette[abs(artworkUrl.hashCode()) % palette.size]
            targetPrimary = chosen.first
            targetSecondary = chosen.second
        }
    }

    val animatedPrimary by animateColorAsState(
        targetValue = targetPrimary,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "artworkAmbientPrimary"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = targetSecondary,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "artworkAmbientSecondary"
    )

    return Pair(animatedPrimary, animatedSecondary)
}

@Composable
fun MiniPlayer(
    currentTrack: SongItem,
    isPlaying: Boolean,
    progressProvider: () -> Long,
    durationProvider: () -> Long,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onSkipPrevious: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "miniOffsetX"
    )
    val animatedOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "miniOffsetY"
    )

    val (ambientPrimary, ambientSecondary) = rememberDynamicArtworkGlow(currentTrack.artworkUrl)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .offset { IntOffset(animatedOffsetX.roundToInt(), animatedOffsetY.roundToInt()) }
            .shadow(16.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(CarbonSurface)
            .border(
                width = 0.85.dp,
                color = SubtleBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .pointerInput(currentTrack.id) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            dragOffsetX < -105f -> onSkipNext()
                            dragOffsetX > 105f -> onSkipPrevious()
                            dragOffsetY > 110f -> onDismiss()
                        }
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(-220f, 220f)
                        dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(-30f, 180f)
                    }
                )
            }
            .clickable(onClick = onExpand)
            .testTag("mini_player_container")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center) {
                    OptimizedArtworkImage(
                        artworkUrl = currentTrack.artworkUrl,
                        contentDescription = currentTrack.title,
                        targetSizeDp = 46.dp,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CarbonElevated)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MiniWaveformIndicator(isPlaying = isPlaying)
                        Text(
                            text = currentTrack.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = AlabasterWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentTrack.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                AnimatedFavoriteButton(
                    isFavorite = currentTrack.isFavorite,
                    onClick = onToggleFavorite,
                    size = 38.dp,
                    iconSize = 18.dp,
                    containerColor = CarbonElevated,
                    modifier = Modifier.testTag("mini_player_favorite_button")
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Solid Mint Primary Play/Pause Button
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ElectricMint)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("mini_player_play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedPlayPauseIcon(
                        isPlaying = isPlaying,
                        tint = ThemeOnPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Soft Minimal Tonal Circular Next Button
                SoftTonalCircleIconButton(
                    icon = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    onClick = onSkipNext,
                    size = 40.dp,
                    iconSize = 20.dp,
                    containerColor = CarbonElevated,
                    modifier = Modifier.testTag("mini_player_next_button")
                )
            }

            // Sleek Rounded Pill Progress Track along the bottom of MiniPlayer
            val progressTrackBg = SubtleBorder.copy(alpha = 0.55f)
            val progressGradientColors = listOf(ElectricMint, CyberCyan)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 6.dp)
                    .height(3.dp)
            ) {
                val total = durationProvider().coerceAtLeast(1L).toFloat()
                val current = progressProvider().coerceIn(0L, total.toLong()).toFloat()
                val fraction = (current / total).coerceIn(0f, 1f)
                val radius = CornerRadius(size.height / 2f, size.height / 2f)

                drawRoundRect(
                    color = progressTrackBg,
                    size = size,
                    cornerRadius = radius
                )
                if (fraction > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(progressGradientColors),
                        size = Size(width = (size.width * fraction).coerceAtLeast(size.height), height = size.height),
                        cornerRadius = radius
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(140)))
                .togetherWith(scaleOut(tween(110)) + fadeOut(tween(110)))
        },
        label = "playPauseMorph",
        modifier = modifier
    ) { playing ->
        Icon(
            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            tint = tint
        )
    }
}

/**
 * 100% Seamlessly Looping 4-Bar Mini Waveform Indicator.
 * Only runs infinite transition when `isPlaying == true` so paused states consume zero frame budget.
 */
@Composable
fun MiniWaveformIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val phase = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "miniSeamlessWave")
        val animatedPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = TWO_PI_FLOAT,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "miniWavePhase"
        )
        animatedPhase
    } else {
        0.8f
    }

    val activeLevel by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "miniWaveActiveLevel"
    )

    val waveBarColor = ElectricMint
    Canvas(
        modifier = modifier
            .width(15.dp)
            .height(14.dp)
    ) {
        val barCount = 4
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)

        for (idx in 0 until barCount) {
            val h1 = sin(1f * phase + idx * 1.35f)
            val h2 = cos(2f * phase - idx * 0.85f)
            val normalizedWave = ((h1 * 0.6f + h2 * 0.4f) * 0.5f + 0.5f).coerceIn(0f, 1f)
            val ratio = 0.24f + normalizedWave * 0.76f * activeLevel
            val h = (size.height * ratio).coerceAtLeast(3.dp.toPx())
            val x = idx * (barWidth + gap)
            val y = (size.height - h) / 2f

            drawRoundRect(
                color = waveBarColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

enum class ExpandedPlayerTab {
    CONTROLS,
    QUEUE,
    LYRICS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedPlayerSheet(
    currentTrack: SongItem,
    isPlaying: Boolean,
    progressProvider: () -> Long,
    durationProvider: () -> Long,
    queue: List<SongItem>,
    repeatMode: PlayerRepeatMode,
    isShuffled: Boolean,
    currentLyrics: LyricsData?,
    currentLyricIndex: Int,
    isLyricsLoading: Boolean,
    sleepTimerState: SleepTimerState,
    playbackSpeed: Float,
    playbackPitch: Float,
    equalizerState: EqualizerState,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onPlaySongFromQueue: (SongItem) -> Unit,
    onReorderQueue: (Int, Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onStartSleepTimer: (Int, Boolean) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetPlaybackPitch: (Float) -> Unit,
    onSetEqualizerEnabled: (Boolean) -> Unit,
    onSetEqualizerBand: (Short, Short) -> Unit,
    onSetBassBoost: (Int) -> Unit,
    onApplyEqPreset: (String) -> Unit,
    isTrackDownloaded: Boolean = false,
    isTrackDownloading: Boolean = false,
    onToggleDownloadTrack: () -> Unit = {},
    onOpenAddToPlaylist: () -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String, String, String) -> Unit = { _, _, _ -> },
    onPlayNextInQueue: (SongItem) -> Unit = {},
    onClearUpcomingQueue: () -> Unit = {},
    isKaraokeModeEnabled: Boolean = false,
    onToggleKaraokeMode: () -> Unit = {},
    onStartSongRadio: () -> Unit = {},
    onSaveQueueAsPlaylist: (String) -> Unit = {},
    autoQueuedIds: Set<String> = emptySet(),
    isRefillingAutoQueue: Boolean = false
) {
    BackHandler(onBack = onCollapse)

    var selectedTab by remember { mutableStateOf(ExpandedPlayerTab.CONTROLS) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    val (ambientPrimary, ambientSecondary) = rememberDynamicArtworkGlow(currentTrack.artworkUrl)

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val sheetDragOffsetY = remember { Animatable(0f) }
    val collapseThresholdPx = with(density) { 110.dp.toPx() }
    var didTriggerCollapseHaptic by remember { mutableStateOf(false) }

    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "heroArtworkSpring"
    )

    val frostedFill = CarbonSurface
    val frostedBorder = SubtleBorder

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = sheetDragOffsetY.value
            }
            .background(PitchBlack)
            .testTag("expanded_player_sheet")
    ) {
        // Layer 1: Full-Bleed Blurred Album Cover Art Backdrop + Atmospheric Color Scrim
        Box(modifier = Modifier.fillMaxSize()) {
            OptimizedArtworkImage(
                artworkUrl = currentTrack.artworkUrl,
                contentDescription = null,
                targetSizeDp = 360.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.32f
                        scaleY = 1.32f
                    }
                    .blur(56.dp)
            )

            val studioPalette = LocalStudioPalette.current
            val backdropBase = studioPalette.background
            val scrimMidAlpha = if (studioPalette.isLight) 0.80f else 0.72f
            val scrimOverlayAlpha = if (studioPalette.isLight) 0.56f else 0.38f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Deep atmospheric tint matched to active light/dark studio surface
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    ambientPrimary.copy(alpha = if (studioPalette.isLight) 0.22f else 0.36f),
                                    ambientSecondary.copy(alpha = if (studioPalette.isLight) 0.16f else 0.28f),
                                    backdropBase.copy(alpha = scrimMidAlpha)
                                )
                            )
                        )
                        drawRect(color = backdropBase.copy(alpha = scrimOverlayAlpha))
                    }
            )
        }

        // Layer 2: Foreground Content (Classic Flow with Blurred Backdrop & Frosted Glass Controls)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Bar with Matte Circular Buttons & Capsule Switcher (Swipe down to collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { didTriggerCollapseHaptic = false },
                            onVerticalDrag = { change, dragAmount ->
                                if (sheetDragOffsetY.value > 0f || dragAmount > 0f) {
                                    change.consume()
                                    val nextY = (sheetDragOffsetY.value + dragAmount).coerceAtLeast(0f)
                                    coroutineScope.launch { sheetDragOffsetY.snapTo(nextY) }
                                    if (!didTriggerCollapseHaptic && nextY >= collapseThresholdPx) {
                                        didTriggerCollapseHaptic = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (sheetDragOffsetY.value >= collapseThresholdPx) {
                                    onCollapse()
                                } else {
                                    coroutineScope.launch {
                                        sheetDragOffsetY.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    sheetDragOffsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FrostedGlassCircleIconButton(
                    icon = Icons.Outlined.ExpandMore,
                    contentDescription = "Collapse",
                    onClick = onCollapse,
                    size = 44.dp,
                    iconSize = 23.dp,
                    containerColor = frostedFill,
                    borderColor = frostedBorder,
                    modifier = Modifier.testTag("collapse_player_button")
                )

                // Frosted Glass Mode Switcher Capsule: Controls | Queue | Lyrics
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(frostedFill)
                        .border(0.8.dp, frostedBorder, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpandedPlayerTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) ElectricMint.copy(alpha = 0.16f) else Color.Transparent
                                )
                                .border(
                                    width = if (isSelected) 1.2.dp else 0.dp,
                                    color = if (isSelected) ElectricMint else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedTab = tab }
                                .testTag("player_tab_${tab.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (tab) {
                                    ExpandedPlayerTab.CONTROLS -> Icons.Outlined.Equalizer
                                    ExpandedPlayerTab.QUEUE -> Icons.AutoMirrored.Outlined.QueueMusic
                                    ExpandedPlayerTab.LYRICS -> Icons.Outlined.Lyrics
                                },
                                contentDescription = tab.name,
                                tint = if (isSelected) ElectricMint else AlabasterWhite.copy(alpha = 0.9f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                FrostedGlassCircleIconButton(
                    icon = Icons.Outlined.Bedtime,
                    contentDescription = "Sleep Timer",
                    onClick = { showSleepTimerSheet = true },
                    size = 44.dp,
                    iconSize = 20.dp,
                    isActive = sleepTimerState.isActive,
                    activeTint = ElectricMint,
                    inactiveTint = AlabasterWhite.copy(alpha = 0.9f),
                    containerColor = frostedFill,
                    borderColor = frostedBorder,
                    modifier = Modifier.testTag("open_sleep_timer_button")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (selectedTab == ExpandedPlayerTab.CONTROLS) {
                            Modifier.pointerInput(selectedTab) {
                                detectVerticalDragGestures(
                                    onDragStart = { didTriggerCollapseHaptic = false },
                                    onVerticalDrag = { change, dragAmount ->
                                        if (sheetDragOffsetY.value > 0f || dragAmount > 0f) {
                                            change.consume()
                                            val nextY = (sheetDragOffsetY.value + dragAmount).coerceAtLeast(0f)
                                            coroutineScope.launch { sheetDragOffsetY.snapTo(nextY) }
                                            if (!didTriggerCollapseHaptic && nextY >= collapseThresholdPx) {
                                                didTriggerCollapseHaptic = true
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (sheetDragOffsetY.value >= collapseThresholdPx) {
                                            onCollapse()
                                        } else {
                                            coroutineScope.launch {
                                                sheetDragOffsetY.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            sheetDragOffsetY.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                when (selectedTab) {
                    ExpandedPlayerTab.CONTROLS -> {
                        PlayerControlsTabContent(
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            artworkScale = artworkScale,
                            currentLyrics = currentLyrics,
                            currentLyricIndex = currentLyricIndex,
                            isLyricsLoading = isLyricsLoading,
                            isDownloaded = isTrackDownloaded,
                            isDownloading = isTrackDownloading,
                            frostedFill = frostedFill,
                            frostedBorder = frostedBorder,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            onOpenLyricsTab = { selectedTab = ExpandedPlayerTab.LYRICS },
                            onToggleFavorite = { onToggleFavorite(currentTrack) },
                            onToggleDownload = onToggleDownloadTrack,
                            onAddToPlaylist = onOpenAddToPlaylist,
                            onOpenArtist = { onOpenArtist(currentTrack.artist) }
                        )
                    }
                    ExpandedPlayerTab.QUEUE -> {
                        UpNextQueueTabContent(
                            queue = queue,
                            currentTrackId = currentTrack.id,
                            autoQueuedIds = autoQueuedIds,
                            isRefillingAutoQueue = isRefillingAutoQueue,
                            onPlaySong = onPlaySongFromQueue,
                            onReorderQueue = onReorderQueue,
                            onRemoveFromQueue = onRemoveFromQueue,
                            onPlayNextInQueue = onPlayNextInQueue,
                            onClearUpcomingQueue = onClearUpcomingQueue,
                            onStartSongRadio = onStartSongRadio,
                            onSaveQueueAsPlaylist = onSaveQueueAsPlaylist
                        )
                    }
                    ExpandedPlayerTab.LYRICS -> {
                        SyncedLyricsTabContent(
                            currentTrack = currentTrack,
                            ambientPrimary = ambientPrimary,
                            ambientSecondary = ambientSecondary,
                            lyricsData = currentLyrics,
                            currentLyricIndex = currentLyricIndex,
                            isLoading = isLyricsLoading,
                            isKaraokeModeEnabled = isKaraokeModeEnabled,
                            onToggleKaraokeMode = onToggleKaraokeMode,
                            onSeekToLine = onSeekTo
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Bottom Waveform Scrubber, Monospace Timestamps & Transport Row
            PlayerTransportSection(
                trackId = currentTrack.id,
                progressProvider = progressProvider,
                durationProvider = durationProvider,
                isPlaying = isPlaying,
                isShuffled = isShuffled,
                repeatMode = repeatMode,
                frostedFill = frostedFill,
                frostedBorder = frostedBorder,
                onSeekTo = onSeekTo,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat
            )
        }
    }

    if (showSleepTimerSheet) {
        SleepTimerBottomSheet(
            sleepTimerState = sleepTimerState,
            onStartTimer = { mins, stopAfterCurrent ->
                onStartSleepTimer(mins, stopAfterCurrent)
                showSleepTimerSheet = false
            },
            onCancelTimer = {
                onCancelSleepTimer()
                showSleepTimerSheet = false
            },
            onDismiss = { showSleepTimerSheet = false }
        )
    }
}

/**
 * Frosted Glass Circular Icon Button matching the reference player design
 */
@Composable
private fun FrostedGlassCircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    isActive: Boolean = false,
    activeTint: Color = Color.Unspecified,
    inactiveTint: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified
) {
    val resolvedActiveTint = activeTint.takeOrElse { ElectricMint }
    val resolvedInactiveTint = inactiveTint.takeOrElse { AlabasterWhite }
    val resolvedContainer = containerColor.takeOrElse { CarbonSurface }
    val resolvedBorder = borderColor.takeOrElse { SubtleBorder }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isActive) resolvedActiveTint.copy(alpha = 0.16f) else resolvedContainer)
            .border(
                width = 0.85.dp,
                color = if (isActive) resolvedActiveTint.copy(alpha = 0.75f) else resolvedBorder,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) resolvedActiveTint else resolvedInactiveTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun PlayerControlsTabContent(
    currentTrack: SongItem,
    isPlaying: Boolean,
    artworkScale: Float,
    currentLyrics: LyricsData?,
    currentLyricIndex: Int,
    isLyricsLoading: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    frostedFill: Color,
    frostedBorder: Color,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onOpenLyricsTab: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenArtist: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val artworkOffsetX = remember(currentTrack.id) { Animatable(0f) }
    val skipThresholdPx = with(LocalDensity.current) { 76.dp.toPx() }
    var didTriggerSkipHaptic by remember(currentTrack.id) { mutableStateOf(false) }
    val hasLyricsSlot = isLyricsLoading || (currentLyrics != null && currentLyrics.lines.isNotEmpty())

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val reservedBelowArt = if (hasLyricsSlot) 166.dp else 96.dp
        val maxArtSide = (maxHeight - reservedBelowArt).coerceAtLeast(160.dp)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // 1. Rounded Square Album Art with Soft Drop Shadow & Swipe Left/Right to Skip
            Box(
                modifier = Modifier
                    .widthIn(max = maxArtSide)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(2.dp)
                    .pointerInput(currentTrack.id) {
                        detectHorizontalDragGestures(
                            onDragStart = { didTriggerSkipHaptic = false },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val nextX = (artworkOffsetX.value + dragAmount)
                                    .coerceIn(-skipThresholdPx * 1.85f, skipThresholdPx * 1.85f)
                                coroutineScope.launch { artworkOffsetX.snapTo(nextX) }
                                if (!didTriggerSkipHaptic && abs(nextX) >= skipThresholdPx) {
                                    didTriggerSkipHaptic = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                val finalX = artworkOffsetX.value
                                if (finalX <= -skipThresholdPx) {
                                    onSkipNext()
                                } else if (finalX >= skipThresholdPx) {
                                    onSkipPrevious()
                                }
                                coroutineScope.launch {
                                    artworkOffsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    artworkOffsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        )
                    }
                    .graphicsLayer {
                        scaleX = artworkScale
                        scaleY = artworkScale
                        translationX = artworkOffsetX.value
                        rotationZ = (artworkOffsetX.value / skipThresholdPx).coerceIn(-1.5f, 1.5f) * 3.5f
                        alpha = (1f - (abs(artworkOffsetX.value) / (skipThresholdPx * 3f))).coerceIn(0.65f, 1f)
                    }
                    .shadow(32.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(CarbonSurface)
                    .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                    .testTag("player_album_artwork")
            ) {
                OptimizedArtworkImage(
                    artworkUrl = currentTrack.artworkUrl,
                    contentDescription = currentTrack.title,
                    targetSizeDp = 320.dp,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 1.5. Compact Live Lyrics Preview Pill (Tap to open full Lyrics tab)
            if (hasLyricsSlot) {
                Spacer(modifier = Modifier.height(12.dp))
                LiveLyricsPreviewPill(
                    lyricsData = currentLyrics,
                    currentLyricIndex = currentLyricIndex,
                    isLoading = isLyricsLoading,
                    isPlaying = isPlaying,
                    onClick = onOpenLyricsTab
                )
            }

            // 2. Song Title, Artist Link & 3 Circular Action Buttons Centered Vertically Between Artwork/Lyrics & Progress Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 14.dp)
                    ) {
                        Text(
                            text = currentTrack.title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 23.sp
                            ),
                            color = AlabasterWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onOpenArtist)
                                .padding(vertical = 2.dp)
                                .testTag("player_sheet_open_artist_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = "Open Artist Hub",
                                tint = SunsetCoral,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = currentTrack.artist,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = AlabasterWhite.copy(alpha = 0.88f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FrostedGlassCircleIconButton(
                            icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                            contentDescription = "Add to Playlist",
                            onClick = onAddToPlaylist,
                            size = 44.dp,
                            iconSize = 21.dp,
                            containerColor = frostedFill,
                            borderColor = frostedBorder,
                            modifier = Modifier.testTag("expanded_add_to_playlist_button")
                        )

                        if (isDownloading) {
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(frostedFill)
                                    .border(0.85.dp, ElectricMint.copy(alpha = 0.65f), CircleShape)
                                    .clickable(onClick = onToggleDownload)
                                    .testTag("expanded_download_cancel_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = ElectricMint,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Cancel download",
                                    tint = AlabasterWhite,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        } else {
                            FrostedGlassCircleIconButton(
                                icon = if (isDownloaded) Icons.Outlined.DownloadDone else Icons.Outlined.Download,
                                contentDescription = if (isDownloaded) "Downloaded for Offline" else "Download for Offline",
                                onClick = onToggleDownload,
                                size = 44.dp,
                                iconSize = 20.dp,
                                isActive = isDownloaded,
                                activeTint = CyberCyan,
                                inactiveTint = AlabasterWhite,
                                containerColor = frostedFill,
                                borderColor = frostedBorder,
                                modifier = Modifier.testTag("expanded_download_button")
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (currentTrack.isFavorite) SunsetCoral.copy(alpha = 0.22f) else frostedFill
                                )
                                .border(
                                    width = 0.85.dp,
                                    color = if (currentTrack.isFavorite) SunsetCoral.copy(alpha = 0.7f) else frostedBorder,
                                    shape = CircleShape
                                )
                                .clickable(onClick = onToggleFavorite)
                                .testTag("expanded_favorite_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (currentTrack.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (currentTrack.isFavorite) SunsetCoral else AlabasterWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveLyricsPreviewPill(
    lyricsData: LyricsData?,
    currentLyricIndex: Int,
    isLoading: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeLineText = remember(lyricsData, currentLyricIndex) {
        when {
            lyricsData == null || lyricsData.lines.isEmpty() -> ""
            lyricsData.isSynced && currentLyricIndex in lyricsData.lines.indices ->
                lyricsData.lines[currentLyricIndex].text.trim()
            lyricsData.isSynced ->
                lyricsData.lines.firstOrNull { it.text.isNotBlank() }?.text?.trim().orEmpty()
            else ->
                lyricsData.lines.firstOrNull { it.text.isNotBlank() }?.text?.trim().orEmpty()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CarbonSurface.copy(alpha = 0.84f))
            .border(
                width = 0.8.dp,
                color = if (lyricsData?.isSynced == true && currentLyricIndex >= 0) {
                    ElectricMint.copy(alpha = 0.48f)
                } else {
                    SubtleBorder
                },
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("live_lyrics_preview_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (lyricsData?.isSynced == true && currentLyricIndex >= 0) {
            MiniWaveformIndicator(isPlaying = isPlaying)
        } else {
            Icon(
                imageVector = Icons.Outlined.Lyrics,
                contentDescription = "Live Lyrics Preview",
                tint = if (lyricsData != null) ElectricMint else MutedSlate,
                modifier = Modifier.size(16.dp)
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (isLoading && lyricsData == null) {
                Text(
                    text = "Finding synced lyrics...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                AnimatedContent(
                    targetState = activeLineText.ifBlank { "Tap to open lyrics" },
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically { it / 3 })
                            .togetherWith(fadeOut(tween(150)) + slideOutVertically { -it / 3 })
                    },
                    label = "liveLyricsPreviewLine"
                ) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            lineHeight = 18.sp
                        ),
                        color = if (lyricsData?.isSynced == true && currentLyricIndex >= 0) {
                            AlabasterWhite
                        } else {
                            MutedSlate
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = if (lyricsData?.isSynced == true) "SYNCED" else "LYRICS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ElectricMint
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Open Full Lyrics",
                tint = ElectricMint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}



/**
 * Interactive Waveform Progress Scrubber with 100% Seamless Infinite Harmonic Loop.
 *
 * - Fixes the loop discontinuity by using ONLY exact integer harmonic multipliers
 *   (1f * phase, 2f * phase, 3f * phase) as `phase` traverses [0, 2π].
 *   Because sin(k * 2π + φ) == sin(φ) and its derivative matches at 0 and 2π for integer k,
 *   the waveform loops forever with zero jump or seam.
 * - Combines the live audio waveform with the interactive seek progress bar:
 *   bars to the left of the playback playhead fill with ElectricMint -> CyberCyan gradient,
 *   while unplayed bars to the right remain in soft tonal slate.
 * - Supports both tap-to-seek and smooth horizontal drag scrubbing.
 */
@Composable
fun InteractiveWaveformProgressBar(
    trackId: String,
    progressProvider: () -> Long,
    durationProvider: () -> Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    // Seamless [0, 2π] phase animation with exact integer harmonics
    val infiniteTransition = rememberInfiniteTransition(label = "seamlessWaveformScrubber")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI_FLOAT,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "seamlessPhase"
    )

    val dynamicAmplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "dynamicWaveAmplitude"
    )

    // Deterministic per-track acoustic profile (44 bars)
    val barCount = 44
    val baseProfile = remember(trackId) {
        val seed = abs(trackId.hashCode()).coerceAtLeast(7)
        FloatArray(barCount) { idx ->
            val u = idx.toFloat() / (barCount - 1).toFloat()
            val arch = sin(u * Math.PI).toFloat().coerceIn(0.25f, 1.0f)
            val pseudo1 = sin(idx * 1.73f + (seed % 19) * 0.41f)
            val pseudo2 = cos(idx * 0.91f - (seed % 13) * 0.63f)
            val variation = ((pseudo1 * 0.55f + pseudo2 * 0.45f) * 0.5f + 0.5f)
            (0.26f + (0.28f + variation * 0.46f) * arch).coerceIn(0.24f, 0.92f)
        }
    }

    val waveAccent = ElectricMint
    val waveUnplayed = AlabasterWhite
    val playheadColor = AlabasterWhite
    val playedBrush = remember(waveAccent) {
        Brush.verticalGradient(
            colors = listOf(waveAccent, waveAccent.copy(alpha = 0.85f))
        )
    }
    val unplayedBrush = remember(waveUnplayed) {
        Brush.verticalGradient(
            colors = listOf(
                waveUnplayed.copy(alpha = 0.34f),
                waveUnplayed.copy(alpha = 0.22f)
            )
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CarbonSurface.copy(alpha = 0.72f))
                .border(
                    width = 0.85.dp,
                    color = if (isScrubbing) ElectricMint.copy(alpha = 0.65f) else SubtleBorder,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 7.dp)
                .pointerInput(trackId) {
                    detectTapGestures { offset ->
                        val widthPx = size.width.coerceAtLeast(1)
                        val tappedFraction = (offset.x / widthPx.toFloat()).coerceIn(0f, 1f)
                        val safeDur = durationProvider().coerceAtLeast(1L)
                        onSeekTo((tappedFraction * safeDur.toFloat()).toLong())
                    }
                }
                .pointerInput(trackId) {
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            val widthPx = size.width.coerceAtLeast(1)
                            isScrubbing = true
                            scrubFraction = (startOffset.x / widthPx.toFloat()).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val widthPx = size.width.coerceAtLeast(1)
                            scrubFraction = (change.position.x / widthPx.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val safeDur = durationProvider().coerceAtLeast(1L)
                            onSeekTo((scrubFraction * safeDur.toFloat()).toLong())
                            isScrubbing = false
                        },
                        onDragCancel = {
                            isScrubbing = false
                        }
                    )
                }
                .testTag("player_seek_slider")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val safeDur = durationProvider().coerceAtLeast(1L)
                val liveFraction = (progressProvider().coerceIn(0L, safeDur).toFloat() / safeDur.toFloat())
                    .coerceIn(0f, 1f)
                val displayFraction = if (isScrubbing) scrubFraction else liveFraction

                val totalBars = barCount
                val spacingRatio = 0.42f
                val totalSpacing = size.width * spacingRatio
                val barWidth = ((size.width - totalSpacing) / totalBars).coerceAtLeast(2.5.dp.toPx())
                val gap = totalSpacing / (totalBars - 1).coerceAtLeast(1)
                val playheadX = size.width * displayFraction

                for (i in 0 until totalBars) {
                    val h1 = sin(1f * phase + i * 0.44f)
                    val h2 = cos(2f * phase - i * 0.71f)
                    val h3 = sin(3f * phase + i * 1.13f)
                    val harmonicWave = (h1 * 0.48f + h2 * 0.34f + h3 * 0.18f)

                    val baseHeight = baseProfile[i]
                    val animatedHeight = (baseHeight + harmonicWave * 0.24f * dynamicAmplitude)
                        .coerceIn(0.18f, 0.96f)

                    val barHeight = (size.height * animatedHeight).coerceAtLeast(5.dp.toPx())
                    val x = i * (barWidth + gap)
                    val y = (size.height - barHeight) / 2f
                    val barCenterX = x + barWidth / 2f
                    val isBarPlayed = barCenterX <= playheadX

                    drawRoundRect(
                        brush = if (isBarPlayed) playedBrush else unplayedBrush,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }

                // Crisp Vertical Playhead Bar
                val pillWidth = if (isScrubbing) 4.dp.toPx() else 3.dp.toPx()
                val clampedX = playheadX.coerceIn(pillWidth / 2f, size.width - pillWidth / 2f)
                drawRoundRect(
                    color = playheadColor,
                    topLeft = Offset(clampedX - pillWidth / 2f, 0f),
                    size = Size(pillWidth, size.height),
                    cornerRadius = CornerRadius(pillWidth / 2f, pillWidth / 2f)
                )
            }
        }

        WaveformTimestampsRow(
            progressProvider = progressProvider,
            durationProvider = durationProvider,
            isScrubbing = isScrubbing,
            scrubFraction = scrubFraction
        )
    }
}

@Composable
private fun WaveformTimestampsRow(
    progressProvider: () -> Long,
    durationProvider: () -> Long,
    isScrubbing: Boolean,
    scrubFraction: Float
) {
    val safeDuration = durationProvider().coerceAtLeast(1L)
    val displayTimeMs = if (isScrubbing) {
        (scrubFraction * safeDuration.toFloat()).toLong()
    } else {
        progressProvider().coerceIn(0L, safeDuration)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = if (isScrubbing) ElectricMint.copy(alpha = 0.16f) else Color.Transparent,
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = formatDurationTimestamp(displayTimeMs),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (isScrubbing) ElectricMint else AlabasterWhite.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Text(
            text = formatDurationTimestamp(safeDuration),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily
            ),
            color = MutedSlate,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpNextQueueTabContent(
    queue: List<SongItem>,
    currentTrackId: String,
    autoQueuedIds: Set<String> = emptySet(),
    isRefillingAutoQueue: Boolean = false,
    onPlaySong: (SongItem) -> Unit,
    onReorderQueue: (Int, Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onPlayNextInQueue: (SongItem) -> Unit = {},
    onClearUpcomingQueue: () -> Unit = {},
    onStartSongRadio: () -> Unit = {},
    onSaveQueueAsPlaylist: (String) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    var draggedItemKey by remember { mutableStateOf<String?>(null) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }
    var showSaveQueueDialog by remember { mutableStateOf(false) }
    val latestQueue by rememberUpdatedState(queue)
    val latestOnReorder by rememberUpdatedState(onReorderQueue)
    val latestOnRemove by rememberUpdatedState(onRemoveFromQueue)

    val currentIdx = remember(queue, currentTrackId) {
        queue.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0)
    }
    val firstAutoQueueIndex = remember(queue, autoQueuedIds, currentIdx) {
        (currentIdx + 1 until queue.size).firstOrNull { queue[it].id in autoQueuedIds } ?: -1
    }
    val explicitItems = remember(queue, firstAutoQueueIndex) {
        if (firstAutoQueueIndex in queue.indices) queue.subList(0, firstAutoQueueIndex) else queue
    }
    val autoQueuedItems = remember(queue, firstAutoQueueIndex) {
        if (firstAutoQueueIndex in queue.indices) queue.subList(firstAutoQueueIndex, queue.size) else emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("up_next_queue_list")
    ) {
        // Queue Action Bar: Track Count + Song Radio + Save Queue + Clear (Autoplay always on)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${queue.size} ${if (queue.size == 1) "track" else "tracks"}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MutedSlate
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(start = 8.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                Surface(
                    color = CyberCyan.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(0.75.dp, CyberCyan.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .clickable { onStartSongRadio() }
                        .testTag("queue_start_radio_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Radio,
                            contentDescription = "Start Song Radio",
                            tint = CyberCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Radio",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = CyberCyan
                        )
                    }
                }

                if (queue.isNotEmpty()) {
                    Surface(
                        color = ElectricMint,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showSaveQueueDialog = true }
                            .testTag("queue_save_playlist_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                                contentDescription = "Save queue as playlist",
                                tint = ThemeOnPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Save Queue",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = ThemeOnPrimary
                            )
                        }
                    }
                }

                if (queue.size > 1) {
                    Surface(
                        color = CarbonSurface,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(0.75.dp, SubtleBorder, RoundedCornerShape(50))
                            .clickable { onClearUpcomingQueue() }
                            .testTag("queue_clear_upcoming_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ClearAll,
                                contentDescription = "Clear upcoming",
                                tint = MutedSlate,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MutedSlate
                            )
                        }
                    }
                }
            }
        }

        val renderQueueRow: @Composable (SongItem, Boolean) -> Unit = { song, isAutoGenerated ->
            val isCurrent = song.id == currentTrackId
            val isDragged = draggedItemKey == song.id
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (draggedItemKey == null && value == SwipeToDismissBoxValue.EndToStart) {
                        val removeIdx = latestQueue.indexOfFirst { it.id == song.id }
                        if (removeIdx >= 0) {
                            latestOnRemove(removeIdx)
                        }
                        true
                    } else {
                        false
                    }
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragged) 2f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragged) dragDeltaY else 0f
                        scaleX = if (isDragged) 1.02f else 1f
                        scaleY = if (isDragged) 1.02f else 1f
                    }
            ) {
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    gesturesEnabled = draggedItemKey == null,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SunsetCoral.copy(alpha = 0.22f))
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove from queue",
                                tint = SunsetCoral
                            )
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                when {
                                    isDragged -> CarbonElevated
                                    isCurrent -> CarbonElevated
                                    else -> CarbonSurface
                                }
                            )
                            .border(
                                width = if (isDragged || isCurrent) 1.dp else 0.7.dp,
                                color = when {
                                    isDragged -> ElectricMint
                                    isCurrent -> ElectricMint.copy(alpha = 0.65f)
                                    isAutoGenerated -> SubtleBorder.copy(alpha = 0.7f)
                                    else -> SubtleBorder
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(enabled = draggedItemKey == null) { onPlaySong(song) }
                            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                            .testTag("queue_row_${song.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CarbonElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            OptimizedArtworkImage(
                                artworkUrl = song.artworkUrl,
                                contentDescription = song.title,
                                targetSizeDp = 44.dp,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(PhotoScrimColor.copy(alpha = 0.52f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    MiniWaveformIndicator(isPlaying = true)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp)
                        ) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = if (isCurrent) ElectricMint else AlabasterWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedSlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Spotify-style 6-dot drag handle on right (key-based neighbor lookup handles divider safely)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .pointerInput(song.id) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            draggedItemKey = song.id
                                            dragDeltaY = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragEnd = {
                                            draggedItemKey = null
                                            dragDeltaY = 0f
                                        },
                                        onDragCancel = {
                                            draggedItemKey = null
                                            dragDeltaY = 0f
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            dragDeltaY += dragAmount
                                            val activeList = latestQueue
                                            val fromIdx = activeList.indexOfFirst { it.id == song.id }
                                            if (fromIdx == -1) return@detectVerticalDragGestures

                                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                                            val draggedInfo = visibleItems.firstOrNull { it.key == song.id }
                                                ?: return@detectVerticalDragGestures
                                            val draggedCenterY = draggedInfo.offset + draggedInfo.size / 2f + dragDeltaY

                                            if (dragDeltaY > 0f && fromIdx < activeList.lastIndex) {
                                                val targetNext = activeList[fromIdx + 1]
                                                val nextInfo = visibleItems.firstOrNull { it.key == targetNext.id }
                                                if (nextInfo != null) {
                                                    val nextMidY = nextInfo.offset + nextInfo.size / 2f
                                                    if (draggedCenterY > nextMidY) {
                                                        latestOnReorder(fromIdx, fromIdx + 1)
                                                        dragDeltaY -= (nextInfo.offset - draggedInfo.offset).toFloat()
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            } else if (dragDeltaY < 0f && fromIdx > 0) {
                                                val targetPrev = activeList[fromIdx - 1]
                                                val prevInfo = visibleItems.firstOrNull { it.key == targetPrev.id }
                                                if (prevInfo != null) {
                                                    val prevMidY = prevInfo.offset + prevInfo.size / 2f
                                                    if (draggedCenterY < prevMidY) {
                                                        latestOnReorder(fromIdx, fromIdx - 1)
                                                        dragDeltaY -= (prevInfo.offset - draggedInfo.offset).toFloat()
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                                .testTag("queue_drag_handle_${song.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            SpotifySixDotDragHandle(
                                tint = if (isDragged) ElectricMint else MutedSlate
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            userScrollEnabled = draggedItemKey == null,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(
                items = explicitItems,
                key = { _, item -> item.id }
            ) { _, song ->
                renderQueueRow(song, false)
            }

            if (autoQueuedItems.isNotEmpty() || isRefillingAutoQueue) {
                item(key = "auto_queued_section_divider") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp, start = 4.dp, end = 4.dp)
                            .testTag("queue_auto_queued_divider"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            thickness = 0.75.dp,
                            color = ElectricMint.copy(alpha = 0.35f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(CarbonElevated)
                                .border(0.75.dp, ElectricMint.copy(alpha = 0.45f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AllInclusive,
                                contentDescription = null,
                                tint = ElectricMint,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "AUTO-QUEUED • EVOLVING MIX",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = ElectricMint
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            thickness = 0.75.dp,
                            color = ElectricMint.copy(alpha = 0.35f)
                        )
                    }
                }
            }

            itemsIndexed(
                items = autoQueuedItems,
                key = { _, item -> item.id }
            ) { _, song ->
                renderQueueRow(song, true)
            }

            if (isRefillingAutoQueue) {
                item(key = "auto_queue_loading_footer") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .testTag("queue_refilling_indicator"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = ElectricMint,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generating more endless tracks...",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = MutedSlate
                        )
                    }
                }
            }
        }
    }

    if (showSaveQueueDialog) {
        val defaultTitle = remember(queue) {
            val anchor = queue.firstOrNull()?.title?.take(22)?.trim().orEmpty()
            if (anchor.isNotBlank()) "$anchor Queue Mix" else "Echoic Queue Mix"
        }
        var playlistTitle by remember(defaultTitle) { mutableStateOf(defaultTitle) }

        AlertDialog(
            onDismissRequest = { showSaveQueueDialog = false },
            containerColor = CarbonSurface,
            titleContentColor = AlabasterWhite,
            textContentColor = MutedSlate,
            title = {
                Text(
                    text = "Save Queue as Playlist",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Saves all ${queue.size} tracks in your current queue to Library.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = playlistTitle,
                        onValueChange = { playlistTitle = it },
                        singleLine = true,
                        placeholder = { Text("Playlist title...") },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricMint,
                            unfocusedBorderColor = SubtleBorder,
                            focusedTextColor = AlabasterWhite,
                            unfocusedTextColor = AlabasterWhite,
                            cursorColor = ElectricMint
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_queue_title_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveQueueAsPlaylist(playlistTitle.trim().ifBlank { defaultTitle })
                        showSaveQueueDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricMint,
                        contentColor = ThemeOnPrimary
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("confirm_save_queue_button")
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveQueueDialog = false }) {
                    Text("Cancel", color = MutedSlate)
                }
            }
        )
    }
}

@Composable
private fun SpotifySixDotDragHandle(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.size(width = 14.dp, height = 20.dp)
    ) {
        val dotRadius = 1.65.dp.toPx()
        val leftX = size.width * 0.28f
        val rightX = size.width * 0.72f
        val ys = listOf(size.height * 0.2f, size.height * 0.5f, size.height * 0.8f)
        ys.forEach { y ->
            drawCircle(color = tint, radius = dotRadius, center = Offset(leftX, y))
            drawCircle(color = tint, radius = dotRadius, center = Offset(rightX, y))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncedLyricsTabContent(
    currentTrack: SongItem,
    ambientPrimary: Color,
    ambientSecondary: Color,
    lyricsData: LyricsData?,
    currentLyricIndex: Int,
    isLoading: Boolean,
    isKaraokeModeEnabled: Boolean = false,
    onToggleKaraokeMode: () -> Unit = {},
    onSeekToLine: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showShareCardSheet by remember { mutableStateOf(false) }
    var isUserScrollingLyrics by remember(currentTrack.id) { mutableStateOf(false) }

    LaunchedEffect(listState, currentTrack.id) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                isUserScrollingLyrics = true
            }
        }
    }

    val availableVariants = remember(lyricsData) {
        if (lyricsData == null) {
            emptyList()
        } else {
            listOf(
                LyricsVariant(
                    languageLabel = lyricsData.languageLabel.ifBlank { "Original" },
                    lines = lyricsData.lines,
                    isSynced = lyricsData.isSynced
                )
            ) + lyricsData.alternateVariants
        }
    }
    var selectedVariantIndex by remember(lyricsData) { mutableIntStateOf(0) }
    val activeVariant = availableVariants.getOrNull(selectedVariantIndex) ?: availableVariants.firstOrNull()
    val activeLines = activeVariant?.lines ?: lyricsData?.lines.orEmpty()
    val activeIsSynced = activeVariant?.isSynced ?: (lyricsData?.isSynced == true)

    val effectiveCurrentIndex = remember(
        lyricsData,
        currentLyricIndex,
        selectedVariantIndex,
        activeLines,
        activeIsSynced
    ) {
        if (selectedVariantIndex == 0 || !activeIsSynced) {
            currentLyricIndex
        } else {
            val anchorTimeMs = lyricsData?.lines?.getOrNull(currentLyricIndex)?.startTimeMs ?: 0L
            activeLines.indexOfLast { it.startTimeMs <= anchorTimeMs }
        }
    }

    LaunchedEffect(effectiveCurrentIndex, activeLines, activeIsSynced, isUserScrollingLyrics) {
        if (activeIsSynced && !isUserScrollingLyrics && activeLines.isNotEmpty()) {
            if (effectiveCurrentIndex in activeLines.indices) {
                val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                val centerOffset = if (viewportHeight > 0) -(viewportHeight / 3) else -180
                runCatching {
                    listState.animateScrollToItem(
                        index = effectiveCurrentIndex,
                        scrollOffset = centerOffset
                    )
                }
            } else if (effectiveCurrentIndex < 0) {
                runCatching { listState.animateScrollToItem(0) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(PitchBlack.copy(alpha = 0.45f))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ambientPrimary.copy(alpha = 0.22f),
                        ambientSecondary.copy(alpha = 0.12f),
                        PitchBlack.copy(alpha = 0.55f)
                    )
                )
            )
            .border(0.85.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("synced_lyrics_container")
    ) {
        if (isLoading && lyricsData == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    tint = ElectricMint,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "Finding lyrics...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlate
                )
            }
        } else if (lyricsData == null || lyricsData.lines.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    tint = MutedSlate.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "No lyrics available for this track",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSlate
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = if (lyricsData.isSynced) "LIVE SYNCED" else "FULL LYRICS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp
                            ),
                            color = if (lyricsData.isSynced) ElectricMint else MutedSlate,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (lyricsData.isSynced) {
                            Text(
                                text = "Tap line to jump",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp
                                ),
                                color = MutedSlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        // Sing-Along Karaoke Vocal Ducking Toggle Pill
                        Surface(
                            color = if (isKaraokeModeEnabled) ElectricMint.copy(alpha = 0.16f) else CarbonElevated,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(
                                    width = 0.75.dp,
                                    color = if (isKaraokeModeEnabled) ElectricMint else SubtleBorder,
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable { onToggleKaraokeMode() }
                                .testTag("lyrics_karaoke_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Mic,
                                    contentDescription = "Karaoke Vocal Ducking",
                                    tint = if (isKaraokeModeEnabled) ElectricMint else AlabasterWhite,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isKaraokeModeEnabled) "KARAOKE ON" else "KARAOKE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = if (isKaraokeModeEnabled) ElectricMint else AlabasterWhite,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Conditional Multi-Language Lyrics Switcher — ONLY rendered when song actually has >1 language variant
                        if (lyricsData.alternateVariants.isNotEmpty() && availableVariants.size > 1) {
                            val activeLabel = activeVariant?.languageLabel?.uppercase() ?: "ORIGINAL"
                            Surface(
                                color = if (selectedVariantIndex > 0) CyberCyan.copy(alpha = 0.16f) else CarbonElevated,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(
                                        width = 0.75.dp,
                                        color = if (selectedVariantIndex > 0) CyberCyan else SubtleBorder,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable {
                                        selectedVariantIndex = (selectedVariantIndex + 1) % availableVariants.size
                                    }
                                    .testTag("lyrics_language_switcher")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Translate,
                                        contentDescription = "Switch lyrics language",
                                        tint = if (selectedVariantIndex > 0) CyberCyan else AlabasterWhite,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = activeLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        color = if (selectedVariantIndex > 0) CyberCyan else AlabasterWhite,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // Solid Mint Share Lyric Card Pill Button
                        Surface(
                            color = ElectricMint,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { showShareCardSheet = true }
                                .testTag("lyrics_share_card_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "Share Lyric Card",
                                    tint = ThemeOnPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Share Card",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    ),
                                    color = ThemeOnPrimary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(
                            items = activeLines,
                            key = { index, line -> "${index}_${line.startTimeMs}" }
                        ) { index, line ->
                            val displayedLine = line.text
                            if (activeIsSynced) {
                                val isActive = index == effectiveCurrentIndex
                                val isPassed = effectiveCurrentIndex >= 0 && index < effectiveCurrentIndex

                                val lineColor by animateColorAsState(
                                    targetValue = when {
                                        isActive -> ElectricMint
                                        isPassed -> AlabasterWhite.copy(alpha = 0.62f)
                                        else -> AlabasterWhite.copy(alpha = 0.42f)
                                    },
                                    animationSpec = tween(180),
                                    label = "lyricColor"
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isActive) ElectricMint.copy(alpha = 0.14f) else Color.Transparent
                                        )
                                        .border(
                                            width = if (isActive) 0.85.dp else 0.dp,
                                            color = if (isActive) ElectricMint.copy(alpha = 0.55f) else Color.Transparent,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            isUserScrollingLyrics = false
                                            onSeekToLine(line.startTimeMs)
                                        }
                                        .padding(
                                            horizontal = if (isActive) 12.dp else 4.dp,
                                            vertical = if (isActive) 10.dp else 6.dp
                                        )
                                ) {
                                    Text(
                                        text = displayedLine,
                                        style = if (isActive) {
                                            MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                lineHeight = 28.sp
                                            )
                                        } else {
                                            MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Medium,
                                                lineHeight = 24.sp
                                            )
                                        },
                                        color = lineColor,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            } else {
                                // Full scrollable plain-text lyrics reader
                                Text(
                                    text = displayedLine,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 25.sp
                                    ),
                                    color = AlabasterWhite.copy(alpha = 0.92f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Floating Follow Lyric / Re-sync Pill when user manually scrolls away
                    if (activeIsSynced && isUserScrollingLyrics && effectiveCurrentIndex in activeLines.indices) {
                        Surface(
                            color = CarbonElevated.copy(alpha = 0.94f),
                            shape = RoundedCornerShape(50),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, ElectricMint, RoundedCornerShape(50))
                                .clickable {
                                    isUserScrollingLyrics = false
                                    coroutineScope.launch {
                                        val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                                        val centerOffset = if (viewportHeight > 0) -(viewportHeight / 3) else -180
                                        runCatching {
                                            listState.animateScrollToItem(
                                                index = effectiveCurrentIndex,
                                                scrollOffset = centerOffset
                                            )
                                        }
                                    }
                                }
                                .testTag("lyrics_resync_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MyLocation,
                                    contentDescription = "Re-sync Lyrics",
                                    tint = ElectricMint,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Follow Lyric",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = ElectricMint
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showShareCardSheet && activeLines.isNotEmpty()) {
        val totalLines = activeLines.size
        var selectedTheme by remember { mutableStateOf(LyricCardTheme.ALBUM_GLOW) }
        var lineCount by remember { mutableIntStateOf(3) }
        var lineOffset by remember(effectiveCurrentIndex) {
            mutableIntStateOf(effectiveCurrentIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0)))
        }
        var isRenderingShareImage by remember { mutableStateOf(false) }

        val snippetLines = remember(activeLines, lineOffset, lineCount) {
            val safeStart = lineOffset.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
            activeLines
                .drop(safeStart)
                .take(lineCount)
                .ifEmpty { activeLines.take(lineCount) }
                .map { it.text }
        }

        val themeMint = ElectricMint
        val themeCoral = SunsetCoral
        val themeViolet = NeonViolet
        val themeCyan = CyberCyan
        val (themeStartColor, themeMidColor, themeAccentColor) = remember(
            selectedTheme,
            ambientPrimary,
            ambientSecondary,
            themeMint,
            themeCoral,
            themeViolet,
            themeCyan
        ) {
            when (selectedTheme) {
                LyricCardTheme.ALBUM_GLOW -> Triple(
                    ambientPrimary.copy(alpha = 0.42f),
                    ambientSecondary.copy(alpha = 0.25f),
                    ambientPrimary
                )
                LyricCardTheme.ELECTRIC_MINT -> Triple(
                    Color(0xFF0A3625),
                    Color(0xFF091B24),
                    themeMint
                )
                LyricCardTheme.SUNSET_CORAL -> Triple(
                    Color(0xFF3F1321),
                    Color(0xFF1F1028),
                    themeCoral
                )
                LyricCardTheme.CYBER_VIOLET -> Triple(
                    Color(0xFF281246),
                    Color(0xFF0D1E38),
                    themeViolet
                )
                LyricCardTheme.MIDNIGHT_OLED -> Triple(
                    Color(0xFF141820),
                    Color(0xFF090B0E),
                    themeCyan
                )
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showShareCardSheet = false },
            containerColor = CarbonSurface,
            contentColor = AlabasterWhite,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
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
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = themeAccentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Share Lyric Story Card",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = AlabasterWhite
                        )
                    }
                    Text(
                        text = "Auto-Synced",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = themeAccentColor
                    )
                }

                // 1. Stylized Gradient Lyric Story Card Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(PhotoScrimColor)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    themeStartColor,
                                    themeMidColor,
                                    PhotoScrimColor
                                )
                            )
                        )
                        .border(1.2.dp, themeAccentColor.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                        .padding(22.dp)
                        .testTag("lyric_share_card_preview")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OptimizedArtworkImage(
                                artworkUrl = currentTrack.artworkUrl,
                                contentDescription = currentTrack.title,
                                targetSizeDp = 54.dp,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(0.8.dp, themeAccentColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentTrack.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = PhotoForegroundColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentTrack.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PhotoForegroundColor.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            PlatformBadgePill(platform = "ECHOIC")
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            snippetLines.forEachIndexed { idx, lineText ->
                                Text(
                                    text = "“$lineText”",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (idx == 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        lineHeight = 24.sp
                                    ),
                                    color = if (idx == 0) themeAccentColor else PhotoForegroundColor.copy(alpha = 0.94f)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentTrack.albumName.ifBlank { "Echoic Studio Sessions" },
                                style = MaterialTheme.typography.labelSmall,
                                color = PhotoMutedColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "♪ ECHOIC LYRICS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                color = themeAccentColor.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                // 2. Background Theme Selector Pills
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "CARD THEME",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MutedSlate
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LyricCardTheme.entries.forEach { themeOption ->
                            val isSelected = selectedTheme == themeOption
                            Surface(
                                color = if (isSelected) themeAccentColor.copy(alpha = 0.18f) else CarbonElevated,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(
                                        width = if (isSelected) 1.dp else 0.6.dp,
                                        color = if (isSelected) themeAccentColor else SubtleBorder,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable { selectedTheme = themeOption }
                                    .testTag("lyric_card_theme_${themeOption.name.lowercase()}")
                            ) {
                                Text(
                                    text = themeOption.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) themeAccentColor else AlabasterWhite,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Active Lines Nudger & Line Count Selector (2 / 3 / 4 Lines)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = CarbonElevated,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(0.6.dp, SubtleBorder, RoundedCornerShape(50))
                                .clickable(enabled = lineOffset > 0) {
                                    lineOffset = (lineOffset - 1).coerceAtLeast(0)
                                }
                                .testTag("lyric_card_prev_line_button")
                        ) {
                            Text(
                                text = "◀ Prev Line",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (lineOffset > 0) AlabasterWhite else DimSlate,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            color = CarbonElevated,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(0.6.dp, SubtleBorder, RoundedCornerShape(50))
                                .clickable(enabled = lineOffset < totalLines - 1) {
                                    lineOffset = (lineOffset + 1).coerceAtMost((totalLines - 1).coerceAtLeast(0))
                                }
                                .testTag("lyric_card_next_line_button")
                        ) {
                            Text(
                                text = "Next Line ▶",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (lineOffset < totalLines - 1) AlabasterWhite else DimSlate,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(2, 3, 4).forEach { countOption ->
                            val isSelectedCount = lineCount == countOption
                            Surface(
                                color = if (isSelectedCount) themeAccentColor.copy(alpha = 0.2f) else CarbonElevated,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(
                                        width = 0.75.dp,
                                        color = if (isSelectedCount) themeAccentColor else SubtleBorder,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable { lineCount = countOption }
                                    .testTag("lyric_card_count_$countOption")
                            ) {
                                Text(
                                    text = "${countOption}L",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isSelectedCount) themeAccentColor else AlabasterWhite,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // 4. Share Actions: Share PNG Story Image + Share Text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (!isRenderingShareImage) {
                                isRenderingShareImage = true
                                coroutineScope.launch {
                                    shareRenderedLyricCardPng(
                                        context = context,
                                        song = currentTrack,
                                        lines = snippetLines,
                                        startColor = themeStartColor,
                                        midColor = themeMidColor,
                                        accentColor = themeAccentColor
                                    )
                                    isRenderingShareImage = false
                                    showShareCardSheet = false
                                }
                            }
                        },
                        enabled = !isRenderingShareImage,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = themeAccentColor,
                            contentColor = PhotoScrimColor
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_share_lyric_card_button")
                    ) {
                        if (isRenderingShareImage) {
                            CircularProgressIndicator(
                                color = PhotoScrimColor,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Rendering Card...",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Share Story Card",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            val shareBody = buildString {
                                snippetLines.forEach { appendLine("“$it”") }
                                appendLine()
                                append("🎵 ${currentTrack.title} — ${currentTrack.artist} • via Echoic")
                            }
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "${currentTrack.title} Lyrics")
                                putExtra(Intent.EXTRA_TEXT, shareBody)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(sendIntent, "Share Lyrics Text"))
                            }
                            showShareCardSheet = false
                        },
                        modifier = Modifier.testTag("share_lyric_text_only_button")
                    ) {
                        Text(
                            text = "Text Only",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = AlabasterWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

private suspend fun shareRenderedLyricCardPng(
    context: Context,
    song: SongItem,
    lines: List<String>,
    startColor: Color,
    midColor: Color,
    accentColor: Color
) {
    val pngUri = withContext(Dispatchers.IO) {
        runCatching {
            val width = 1080
            val height = 1350
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = AndroidCanvas(bitmap)

                val startArgb = android.graphics.Color.argb(
                    255,
                    (startColor.red * 255).toInt().coerceIn(0, 255),
                    (startColor.green * 255).toInt().coerceIn(0, 255),
                    (startColor.blue * 255).toInt().coerceIn(0, 255)
                )
                val midArgb = android.graphics.Color.argb(
                    255,
                    (midColor.red * 255).toInt().coerceIn(0, 255),
                    (midColor.green * 255).toInt().coerceIn(0, 255),
                    (midColor.blue * 255).toInt().coerceIn(0, 255)
                )
                val pitchArgb = android.graphics.Color.rgb(8, 10, 14)
                val accentArgb = android.graphics.Color.argb(
                    255,
                    (accentColor.red * 255).toInt().coerceIn(0, 255),
                    (accentColor.green * 255).toInt().coerceIn(0, 255),
                    (accentColor.blue * 255).toInt().coerceIn(0, 255)
                )

                // 1. Deep Atmospheric Gradient Backdrop
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f,
                        0f,
                        width.toFloat(),
                        height.toFloat(),
                        intArrayOf(startArgb, midArgb, pitchArgb),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                // 2. Inner Rounded Story Card Container
                val cardRect = RectF(72f, 120f, width - 72f, height - 120f)
                val cardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(175, 14, 18, 24)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(cardRect, 64f, 64f, cardFillPaint)

                val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accentArgb
                    alpha = 180
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                }
                canvas.drawRoundRect(cardRect, 64f, 64f, cardBorderPaint)

                // 3. Load Album Cover Art if available
                val artBitmap = if (song.artworkUrl.isNotBlank()) {
                    runCatching {
                        val req = ImageRequest.Builder(context)
                            .data(song.artworkUrl)
                            .size(220, 220)
                            .allowHardware(false)
                            .build()
                        val res = context.imageLoader.execute(req)
                        val drawable = (res as? SuccessResult)?.drawable
                        when (drawable) {
                            is BitmapDrawable -> drawable.bitmap
                            null -> null
                            else -> {
                                val w = drawable.intrinsicWidth.coerceAtLeast(180)
                                val h = drawable.intrinsicHeight.coerceAtLeast(180)
                                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { rendered ->
                                    val artCanvas = AndroidCanvas(rendered)
                                    drawable.setBounds(0, 0, w, h)
                                    drawable.draw(artCanvas)
                                }
                            }
                        }
                    }.getOrNull()
                } else {
                    null
                }

                val headerLeft = 136f
                val headerTop = 192f
                val artSize = 148f
                val destRect = RectF(headerLeft, headerTop, headerLeft + artSize, headerTop + artSize)
                if (artBitmap != null) {
                    val clipPath = Path().apply {
                        addRoundRect(destRect, 28f, 28f, Path.Direction.CW)
                    }
                    canvas.save()
                    canvas.clipPath(clipPath)
                    canvas.drawBitmap(artBitmap, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    canvas.restore()
                } else {
                    val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = accentArgb
                        alpha = 65
                    }
                    canvas.drawRoundRect(destRect, 28f, 28f, placeholderPaint)
                }

                // 4. Song Title & Artist Header
                val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 46f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(200, 210, 218, 226)
                    textSize = 34f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
                val textStartX = headerLeft + artSize + 36f
                val safeTitle = if (song.title.length > 24) song.title.take(22) + "…" else song.title
                val safeArtist = if (song.artist.length > 30) song.artist.take(28) + "…" else song.artist
                canvas.drawText(safeTitle, textStartX, headerTop + 62f, titlePaint)
                canvas.drawText(safeArtist, textStartX, headerTop + 114f, artistPaint)

                // 5. Quoted Lyric Lines
                var currentY = headerTop + artSize + 100f
                val maxTextWidth = (width - 272).coerceAtLeast(400)
                lines.forEachIndexed { index, rawLine ->
                    val lineText = "“${rawLine.trim()}”"
                    val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (index == 0) accentArgb else android.graphics.Color.WHITE
                        textSize = if (index == 0) 54f else 48f
                        typeface = Typeface.create(
                            Typeface.DEFAULT,
                            if (index == 0) Typeface.BOLD else Typeface.NORMAL
                        )
                    }
                    val staticLayout = StaticLayout.Builder
                        .obtain(lineText, 0, lineText.length, lyricPaint, maxTextWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(8f, 1.08f)
                        .setIncludePad(false)
                        .build()

                    if (currentY + staticLayout.height < height - 250f) {
                        canvas.save()
                        canvas.translate(headerLeft, currentY)
                        staticLayout.draw(canvas)
                        canvas.restore()
                        currentY += staticLayout.height + 42f
                    }
                }

                // 6. Footer Branding
                val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accentArgb
                    textSize = 30f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                canvas.drawText("♪ ECHOIC STUDIO LYRICS", headerLeft, height - 176f, footerPaint)

                val cardsDir = File(context.cacheDir, "lyric_cards").apply { mkdirs() }
                // Prune older cached lyric cards so storage stays tidy
                val cutoff = System.currentTimeMillis() - 3_600_000L
                cardsDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoff) {
                        file.delete()
                    }
                }

                val outFile = File(cardsDir, "echoic_lyric_card_${System.currentTimeMillis()}.png")
                FileOutputStream(outFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.flush()
                }
                if (!outFile.exists() || outFile.length() == 0L) {
                    return@runCatching null
                }
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
        val shareClip = ClipData.newUri(context.contentResolver, "Lyric Story Card", pngUri)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, pngUri)
            putExtra(Intent.EXTRA_SUBJECT, "${song.title} — ${song.artist}")
            clipData = shareClip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(sendIntent, "Share Lyric Story Card").apply {
            clipData = shareClip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(chooserIntent)
        }.onFailure {
            Toast.makeText(context, "No compatible app found to share image", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "Could not render lyric card image", Toast.LENGTH_SHORT).show()
    }
}

private fun formatPhoneticLyricLine(raw: String): String {
    val cleaned = raw
        .replace(Regex("\\[(?:Verse|Chorus|Bridge|Intro|Outro|Hook).*?]", RegexOption.IGNORE_CASE), "")
        .trim()
    return cleaned.ifBlank { raw }
}

@Composable
private fun PlayerTransportSection(
    trackId: String,
    progressProvider: () -> Long,
    durationProvider: () -> Long,
    isPlaying: Boolean,
    isShuffled: Boolean,
    repeatMode: PlayerRepeatMode,
    frostedFill: Color,
    frostedBorder: Color,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Unified Interactive Pill-Enclosed Waveform Scrubber
        InteractiveWaveformProgressBar(
            trackId = trackId,
            progressProvider = progressProvider,
            durationProvider = durationProvider,
            isPlaying = isPlaying,
            onSeekTo = onSeekTo
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Frosted Glass Circular Transport Controls + Glowing Green Play Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FrostedGlassCircleIconButton(
                icon = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                onClick = onToggleShuffle,
                size = 48.dp,
                iconSize = 21.dp,
                isActive = isShuffled,
                activeTint = ElectricMint,
                inactiveTint = AlabasterWhite.copy(alpha = 0.85f),
                containerColor = frostedFill,
                borderColor = frostedBorder,
                modifier = Modifier.testTag("player_shuffle_button")
            )

            FrostedGlassCircleIconButton(
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                onClick = onSkipPrevious,
                size = 58.dp,
                iconSize = 26.dp,
                containerColor = frostedFill,
                borderColor = frostedBorder,
                inactiveTint = AlabasterWhite,
                modifier = Modifier.testTag("player_previous_button")
            )

            val playBtnSize by animateDpAsState(
                targetValue = if (isPlaying) 70.dp else 68.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "playBtnSize"
            )

            // Centerpiece Glowing Play/Pause Button with Soft Radial Halo
            val haloColor = ElectricMint
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    haloColor.copy(alpha = 0.42f),
                                    haloColor.copy(alpha = 0.14f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.minDimension * 0.56f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(playBtnSize)
                        .shadow(16.dp, CircleShape, spotColor = ElectricMint)
                        .clip(CircleShape)
                        .background(ElectricMint)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("player_play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedPlayPauseIcon(
                        isPlaying = isPlaying,
                        tint = ThemeOnPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            FrostedGlassCircleIconButton(
                icon = Icons.Default.SkipNext,
                contentDescription = "Next",
                onClick = onSkipNext,
                size = 58.dp,
                iconSize = 26.dp,
                containerColor = frostedFill,
                borderColor = frostedBorder,
                inactiveTint = AlabasterWhite,
                modifier = Modifier.testTag("player_next_button")
            )

            FrostedGlassCircleIconButton(
                icon = if (repeatMode == PlayerRepeatMode.ONE) {
                    Icons.Default.RepeatOne
                } else {
                    Icons.Default.Repeat
                },
                contentDescription = "Repeat",
                onClick = onCycleRepeat,
                size = 48.dp,
                iconSize = 21.dp,
                isActive = repeatMode != PlayerRepeatMode.OFF,
                activeTint = ElectricMint,
                inactiveTint = AlabasterWhite.copy(alpha = 0.85f),
                containerColor = frostedFill,
                borderColor = frostedBorder,
                modifier = Modifier.testTag("player_repeat_button")
            )
        }
    }
}

private data class SleepBubbleOption(
    val minutes: Int,
    val stopAfterCurrent: Boolean,
    val orbLabel: String,
    val title: String,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerBottomSheet(
    sleepTimerState: SleepTimerState,
    onStartTimer: (minutes: Int, stopAfterCurrent: Boolean) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bubbleOptions = remember {
        listOf(
            SleepBubbleOption(
                minutes = 5,
                stopAfterCurrent = false,
                orbLabel = "5",
                title = "5 minutes",
                testTag = "sleep_preset_5m"
            ),
            SleepBubbleOption(
                minutes = 10,
                stopAfterCurrent = false,
                orbLabel = "10",
                title = "10 minutes",
                testTag = "sleep_preset_10m"
            ),
            SleepBubbleOption(
                minutes = 15,
                stopAfterCurrent = false,
                orbLabel = "15",
                title = "15 minutes",
                testTag = "sleep_preset_15m"
            ),
            SleepBubbleOption(
                minutes = 30,
                stopAfterCurrent = false,
                orbLabel = "30",
                title = "30 minutes",
                testTag = "sleep_preset_30m"
            ),
            SleepBubbleOption(
                minutes = 45,
                stopAfterCurrent = false,
                orbLabel = "45",
                title = "45 minutes",
                testTag = "sleep_preset_45m"
            ),
            SleepBubbleOption(
                minutes = 60,
                stopAfterCurrent = false,
                orbLabel = "1h",
                title = "1 hour",
                testTag = "sleep_preset_60m"
            ),
            SleepBubbleOption(
                minutes = 0,
                stopAfterCurrent = true,
                orbLabel = "♪",
                title = "End of track",
                testTag = "sleep_preset_end_of_song"
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CarbonSurface,
        contentColor = AlabasterWhite,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Drag Handle Pill
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SubtleBorder)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Clean Centered Title
            Text(
                text = "Sleep timer",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = AlabasterWhite
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Vertical Minimal Bubble Selector List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bubbleOptions.forEach { option ->
                    val isSelected = if (option.stopAfterCurrent) {
                        sleepTimerState.isActive && sleepTimerState.stopAfterCurrentTrack
                    } else {
                        sleepTimerState.isActive &&
                            !sleepTimerState.stopAfterCurrentTrack &&
                            (sleepTimerState.totalDurationSeconds / 60L).toInt() == option.minutes
                    }

                    val containerBg by animateColorAsState(
                        targetValue = if (isSelected) ElectricMint.copy(alpha = 0.14f) else CarbonElevated,
                        animationSpec = tween(180),
                        label = "sleepBubbleBg_${option.orbLabel}"
                    )

                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) ElectricMint.copy(alpha = 0.7f) else SubtleBorder.copy(alpha = 0.55f),
                        animationSpec = tween(180),
                        label = "sleepBubbleBorder_${option.orbLabel}"
                    )

                    Surface(
                        color = containerBg,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .border(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable {
                                onStartTimer(option.minutes, option.stopAfterCurrent)
                            }
                            .testTag(option.testTag)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) ElectricMint
                                            else CarbonSurface
                                        )
                                        .border(
                                            width = 0.75.dp,
                                            color = if (isSelected) ElectricMint else SubtleBorder,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option.orbLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isSelected) ThemeOnPrimary else MutedSlate
                                    )
                                }

                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) ElectricMint else AlabasterWhite
                                )
                            }

                            // Minimal Selection Dot Bubble
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = 1.25.dp,
                                        color = if (isSelected) ElectricMint else SubtleBorder,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(ElectricMint)
                                    )
                                }
                            }
                        }
                    }
                }

                if (sleepTimerState.isActive) {
                    Surface(
                        color = SunsetCoral.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .border(0.75.dp, SunsetCoral.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .clickable(onClick = onCancelTimer)
                            .testTag("cancel_sleep_timer_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Turn off timer",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SunsetCoral
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

fun formatDurationTimestamp(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
