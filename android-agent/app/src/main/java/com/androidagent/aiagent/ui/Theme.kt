package com.androidagent.aiagent.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Ultra-minimalist monochrome color system inspired by TBH Helper AI.
 * Pure black background, subtle cards, clean hierarchy.
 */
object AppColors {
    // Core surfaces
    val Background = Color(0xFF0a0a0a)
    val Surface = Color(0xFF1a1a1a)
    val SurfaceBorder = Color(0xFF2a2a2a)

    // Text hierarchy
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF888888)
    val TextMuted = Color(0xFF555555)

    // Accent colors
    val AccentBlue = Color(0xFF3b82f6)       // Thinking / active
    val SuccessGreen = Color(0xFF22c55e)      // Tool success
    val ErrorRed = Color(0xFFef4444)          // Tool fail / errors
    val WarningAmber = Color(0xFFf59e0b)      // Warnings
    val ObservationPurple = Color(0xFF6366f1) // Observations

    // Semantic aliases (kept for backward compat in non-UI code)
    val Primary = AccentBlue
    val Secondary = TextSecondary
    val DarkBackground = Background
    val SurfaceVariant = SurfaceBorder
    val PrimaryVariant = Color(0xFF2563eb)
    val OnPrimary = Color.White
    val Success = SuccessGreen
    val Error = ErrorRed
    val Warning = WarningAmber
    val TextMutedCompat = TextMuted
}

private val TaskFlowColorScheme = darkColorScheme(
    primary = AppColors.AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AppColors.PrimaryVariant,
    secondary = AppColors.TextSecondary,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceBorder,
    onSurfaceVariant = AppColors.TextSecondary,
    error = AppColors.ErrorRed,
    onError = Color.White,
    onErrorContainer = AppColors.ErrorRed,
    outline = AppColors.SurfaceBorder,
    outlineVariant = AppColors.SurfaceBorder,
)

@Composable
fun AndroidAgentTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AppColors.Background.toArgb()
            window.navigationBarColor = AppColors.Background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = TaskFlowColorScheme,
        content = content
    )
}