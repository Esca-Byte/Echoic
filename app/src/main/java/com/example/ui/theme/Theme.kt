package com.example.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.data.model.AppThemeState

@Composable
fun SomifyTheme(
    themeState: AppThemeState = AppThemeState(),
    content: @Composable () -> Unit
) {
    val targetPalette = resolveStudioPalette(
        preset = themeState.selectedTheme,
        amoledPureBlack = themeState.amoledPureBlack,
        dynamicArtworkTint = themeState.dynamicArtworkTint
    )

    val motionConfig = rememberMotionConfig()
    val spec = MotionTokens.standardTween<Color>(motionConfig.reduceMotion)
    val bg by animateColorAsState(targetPalette.background, spec, label = "themeBg")
    val surface by animateColorAsState(targetPalette.surface, spec, label = "themeSurface")
    val elevated by animateColorAsState(targetPalette.surfaceElevated, spec, label = "themeElevated")
    val glass by animateColorAsState(targetPalette.glassSurface, spec, label = "themeGlass")
    val border by animateColorAsState(targetPalette.subtleBorder, spec, label = "themeBorder")
    val primary by animateColorAsState(targetPalette.primary, spec, label = "themePrimary")
    val secondary by animateColorAsState(targetPalette.secondary, spec, label = "themeSecondary")
    val tertiary by animateColorAsState(targetPalette.tertiary, spec, label = "themeTertiary")
    val onPrimary by animateColorAsState(targetPalette.onPrimary, spec, label = "themeOnPrimary")
    val coral by animateColorAsState(targetPalette.accentCoral, spec, label = "themeCoral")
    val amber by animateColorAsState(targetPalette.accentAmber, spec, label = "themeAmber")
    val textPrimary by animateColorAsState(targetPalette.textPrimary, spec, label = "themeTextPrimary")
    val textSecondary by animateColorAsState(targetPalette.textSecondary, spec, label = "themeTextSecondary")
    val textDim by animateColorAsState(targetPalette.textDim, spec, label = "themeTextDim")

    val animatedPalette = targetPalette.copy(
        background = bg,
        surface = surface,
        surfaceElevated = elevated,
        glassSurface = glass,
        subtleBorder = border,
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        onPrimary = onPrimary,
        accentCoral = coral,
        accentAmber = amber,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textDim = textDim
    )

    val colorScheme = if (animatedPalette.isLight) {
        lightColorScheme(
            primary = animatedPalette.primary,
            onPrimary = animatedPalette.onPrimary,
            primaryContainer = animatedPalette.primary.copy(alpha = 0.16f),
            onPrimaryContainer = animatedPalette.primary,
            secondary = animatedPalette.secondary,
            onSecondary = Color.White,
            secondaryContainer = animatedPalette.secondary.copy(alpha = 0.14f),
            onSecondaryContainer = animatedPalette.secondary,
            tertiary = animatedPalette.tertiary,
            onTertiary = Color.White,
            background = animatedPalette.background,
            onBackground = animatedPalette.textPrimary,
            surface = animatedPalette.surface,
            onSurface = animatedPalette.textPrimary,
            surfaceVariant = animatedPalette.surfaceElevated,
            onSurfaceVariant = animatedPalette.textSecondary,
            outline = animatedPalette.subtleBorder,
            error = animatedPalette.accentCoral,
            onError = Color.White
        )
    } else {
        darkColorScheme(
            primary = animatedPalette.primary,
            onPrimary = animatedPalette.onPrimary,
            primaryContainer = animatedPalette.primary.copy(alpha = 0.18f),
            onPrimaryContainer = animatedPalette.primary,
            secondary = animatedPalette.secondary,
            onSecondary = animatedPalette.textPrimary,
            secondaryContainer = animatedPalette.secondary.copy(alpha = 0.22f),
            onSecondaryContainer = animatedPalette.textPrimary,
            tertiary = animatedPalette.tertiary,
            onTertiary = animatedPalette.onPrimary,
            background = animatedPalette.background,
            onBackground = animatedPalette.textPrimary,
            surface = animatedPalette.surface,
            onSurface = animatedPalette.textPrimary,
            surfaceVariant = animatedPalette.surfaceElevated,
            onSurfaceVariant = animatedPalette.textSecondary,
            outline = animatedPalette.subtleBorder,
            error = animatedPalette.accentCoral,
            onError = animatedPalette.onPrimary
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val isLightBars = animatedPalette.isLight
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = isLightBars
                    isAppearanceLightNavigationBars = isLightBars
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalStudioPalette provides animatedPalette,
        LocalMotionConfig provides motionConfig
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SomifyTheme(content = content)
}
