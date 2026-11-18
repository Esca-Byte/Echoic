package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SomifyDarkColorScheme = darkColorScheme(
    primary = ElectricMint,
    onPrimary = PitchBlack,
    primaryContainer = Color(0xFF0E3B22),
    onPrimaryContainer = ElectricMint,
    secondary = NeonViolet,
    onSecondary = AlabasterWhite,
    secondaryContainer = Color(0xFF2D1646),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = CyberCyan,
    onTertiary = PitchBlack,
    background = PitchBlack,
    onBackground = AlabasterWhite,
    surface = CarbonSurface,
    onSurface = AlabasterWhite,
    surfaceVariant = CarbonElevated,
    onSurfaceVariant = MutedSlate,
    outline = SubtleBorder,
    error = SunsetCoral,
    onError = PitchBlack
)

@Composable
fun SomifyTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = SomifyDarkColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SomifyTheme(content = content)
}
