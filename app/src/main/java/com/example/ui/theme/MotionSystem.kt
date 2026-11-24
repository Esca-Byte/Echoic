package com.example.ui.theme

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Centralized accessibility & performance-aware motion configuration.
 * Automatically detects Android's system "Remove animations" / Animator Duration Scale = 0 setting.
 */
@Immutable
data class MotionConfig(
    val reduceMotion: Boolean = false
)

val LocalMotionConfig = staticCompositionLocalOf { MotionConfig(reduceMotion = false) }

@Composable
fun rememberMotionConfig(): MotionConfig {
    val context = LocalContext.current
    val view = LocalView.current
    val reduceMotion = remember(context, view.isInEditMode) {
        if (view.isInEditMode) {
            false
        } else {
            runCatching {
                val scale = Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f
                )
                scale == 0f
            }.getOrDefault(false)
        }
    }
    return remember(reduceMotion) { MotionConfig(reduceMotion = reduceMotion) }
}

/**
 * Unified Timing (#14) & Slow-In / Slow-Out (#9) Tokens with balanced, fluid springs.
 * Every spec automatically degrades gracefully when Reduce Motion is enabled.
 */
object MotionTokens {
    const val MicroMs = 110
    const val StandardMs = 240
    const val HeroMs = 340

    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val EmphasizedStandard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    fun <T> fluidSpring(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        )

    fun <T> snappySpring(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMedium
        )

    fun <T> tactilePopSpring(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else spring(
            dampingRatio = 0.62f,
            stiffness = Spring.StiffnessMedium
        )

    fun <T> gentleSpring(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )

    fun <T> microTween(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else tween(durationMillis = MicroMs, easing = EmphasizedStandard)

    fun <T> standardTween(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) tween(durationMillis = 90) else tween(durationMillis = StandardMs, easing = EmphasizedDecelerate)

    fun <T> heroTween(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) tween(durationMillis = 110) else tween(durationMillis = HeroMs, easing = EmphasizedDecelerate)
}

/**
 * Principle #5: Rubber Banding — asymptotic resistance past a physical boundary.
 * Smoothly damps excess displacement using `limit + maxOverflow * (1 - 1 / (1 + k * excess))`
 * so over-drags feel weighted and elastic instead of stopping against a rigid wall.
 */
fun rubberBandDrag(
    rawOffset: Float,
    softLimit: Float,
    maxOverflow: Float = softLimit * 0.45f,
    stiffnessFactor: Float = 0.012f
): Float {
    val magnitude = abs(rawOffset)
    if (magnitude <= softLimit || softLimit <= 0f) return rawOffset
    val excess = magnitude - softLimit
    val dampedExcess = maxOverflow * (1f - (1f / (1f + excess * stiffnessFactor)))
    return sign(rawOffset) * (softLimit + dampedExcess)
}

/**
 * Principle #4 (Squash and Stretch) + #6 (Doherty Threshold):
 * GPU-only press deformation inside `graphicsLayer {}` — zero relayout and zero recomposition.
 * Subtly widens X while compressing Y on touch-down, then springs smoothly back on release.
 */
fun Modifier.tactileSquashPress(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScaleX: Float = 1.025f,
    pressedScaleY: Float = 0.935f
): Modifier = composed {
    val reduceMotion = LocalMotionConfig.current.reduceMotion
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleXAnim by animateFloatAsState(
        targetValue = if (isPressed && enabled && !reduceMotion) pressedScaleX else 1f,
        animationSpec = MotionTokens.snappySpring(reduceMotion),
        label = "squashPressX"
    )
    val scaleYAnim by animateFloatAsState(
        targetValue = if (isPressed && enabled && !reduceMotion) pressedScaleY else 1f,
        animationSpec = MotionTokens.snappySpring(reduceMotion),
        label = "squashPressY"
    )

    this.graphicsLayer {
        scaleX = scaleXAnim
        scaleY = scaleYAnim
    }
}

/**
 * Principle #3 (Staggering) + #9 (Slow In, Slow Out) + Zero-Lag Budget:
 * Cascades ONLY the first 6 visible items (`index <= maxAnimatedIndex`) once on initial mount
 * using pure GPU `graphicsLayer` translationY + alpha. Recycled/scrolled items beyond the cap
 * incur ZERO delay and ZERO animation work.
 */
fun Modifier.staggeredMountReveal(
    index: Int,
    maxAnimatedIndex: Int = 5,
    stepDelayMs: Int = 28,
    offsetDp: Float = 14f
): Modifier = composed {
    val reduceMotion = LocalMotionConfig.current.reduceMotion
    val shouldAnimate = !reduceMotion && index in 0..maxAnimatedIndex
    if (!shouldAnimate) {
        return@composed this
    }

    val density = LocalDensity.current
    val initialOffsetPx = remember(density, offsetDp) { offsetDp * density.density }
    var hasRevealed by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (!hasRevealed) {
            hasRevealed = true
            if (index > 0) {
                delay((index * stepDelayMs).toLong())
            }
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = MotionTokens.StandardMs,
                    easing = MotionTokens.EmphasizedDecelerate
                )
            )
        } else {
            progress.snapTo(1f)
        }
    }

    this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * initialOffsetPx
    }
}

/**
 * Principle #6 (Doherty Threshold):
 * Ultra-lightweight GPU shimmer sweep for skeleton placeholders and resolving states.
 * Halts automatically when inactive or when Reduce Motion is enabled.
 */
fun Modifier.shimmerSweep(
    active: Boolean,
    baseColor: Color,
    highlightColor: Color
): Modifier = composed {
    val reduceMotion = LocalMotionConfig.current.reduceMotion
    if (!active || reduceMotion) {
        return@composed this
    }

    val transition = rememberInfiniteTransition(label = "dohertyShimmer")
    val progress by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerSweepProgress"
    )

    this.drawWithCache {
        val width = size.width.coerceAtLeast(1f)
        val band = width * 0.55f
        onDrawWithContent {
            drawContent()
            val startX = width * progress - band
            val brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    highlightColor.copy(alpha = 0.22f),
                    baseColor.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                start = Offset(startX, 0f),
                end = Offset(startX + band, size.height)
            )
            drawRect(brush = brush)
        }
    }
}

/**
 * Principle #19 (Peak-End Rule) + #2 (Origin Awareness) + #16 (Secondary Action):
 * Celebratory, non-intrusive milestone confirmation pill for rewarding moments
 * (liking a track, saving a playlist/queue, completing downloads, finishing onboarding).
 */
@Composable
fun PeakEndMilestoneBanner(
    message: String?,
    modifier: Modifier = Modifier
) {
    val reduceMotion = LocalMotionConfig.current.reduceMotion
    val haptic = LocalHapticFeedback.current
    val pulseProgress = remember { Animatable(0f) }
    val checkPopScale = remember { Animatable(1f) }

    val isCelebratory = remember(message) {
        val lower = message?.lowercase().orEmpty()
        lower.contains("saved") ||
            lower.contains("added") ||
            lower.contains("liked") ||
            lower.contains("downloaded") ||
            lower.contains("created") ||
            lower.contains("taste") ||
            lower.contains("synced") ||
            lower.contains("playlist") ||
            lower.contains("queue") ||
            lower.contains("radio")
    }

    LaunchedEffect(message) {
        if (message != null && isCelebratory) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (!reduceMotion) {
                launch {
                    pulseProgress.snapTo(0.01f)
                    pulseProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
                    )
                    pulseProgress.snapTo(0f)
                }
                launch {
                    checkPopScale.snapTo(0.62f)
                    checkPopScale.animateTo(
                        targetValue = 1.18f,
                        animationSpec = MotionTokens.tactilePopSpring(false)
                    )
                    checkPopScale.animateTo(
                        targetValue = 1.0f,
                        animationSpec = MotionTokens.snappySpring(false)
                    )
                }
            } else {
                pulseProgress.snapTo(0f)
                checkPopScale.snapTo(1f)
            }
        }
    }

    val accentColor = ElectricMint
    val pillBg = CarbonElevated

    AnimatedVisibility(
        visible = message != null,
        enter = if (reduceMotion) {
            fadeIn(tween(110))
        } else {
            fadeIn(tween(180, easing = MotionTokens.EmphasizedDecelerate)) +
                slideInVertically(
                    animationSpec = MotionTokens.fluidSpring(false),
                    initialOffsetY = { it / 2 }
                ) +
                scaleIn(
                    initialScale = 0.92f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                    animationSpec = MotionTokens.fluidSpring(false)
                )
        },
        exit = if (reduceMotion) {
            fadeOut(tween(90))
        } else {
            fadeOut(tween(140)) +
                slideOutVertically(
                    animationSpec = tween(160, easing = MotionTokens.EmphasizedAccelerate),
                    targetOffsetY = { it / 2 }
                ) +
                scaleOut(targetScale = 0.94f, animationSpec = tween(140))
        },
        modifier = modifier
    ) {
        Surface(
            color = pillBg,
            shape = RoundedCornerShape(50),
            shadowElevation = 10.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 4.dp)
                .drawBehind {
                    val p = pulseProgress.value
                    if (p > 0.01f && p < 0.99f) {
                        val fade = (1f - p).coerceIn(0f, 1f)
                        val expand = 8.dp.toPx() * p
                        drawRoundRect(
                            color = accentColor.copy(alpha = 0.42f * fade),
                            topLeft = Offset(-expand, -expand * 0.55f),
                            size = size.copy(
                                width = size.width + expand * 2f,
                                height = size.height + expand * 1.1f
                            ),
                            cornerRadius = CornerRadius(size.height, size.height),
                            style = Stroke(width = 1.6.dp.toPx() * fade)
                        )
                    }
                }
                .border(
                    width = 0.85.dp,
                    color = accentColor.copy(alpha = if (isCelebratory) 0.68f else 0.45f),
                    shape = RoundedCornerShape(50)
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (isCelebratory) {
                    Icon(
                        imageVector = if (message?.lowercase()?.contains("taste") == true) {
                            Icons.Outlined.AutoAwesome
                        } else {
                            Icons.Filled.CheckCircle
                        },
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                scaleX = checkPopScale.value
                                scaleY = checkPopScale.value
                            }
                    )
                }
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = accentColor
                )
            }
        }
    }
}
