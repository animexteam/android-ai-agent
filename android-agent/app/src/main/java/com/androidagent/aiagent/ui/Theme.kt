package com.androidagent.aiagent.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * v4.3: Ultra-minimalist monochrome design inspired by TBH Helper AI.
 * Clean, high-contrast dark theme — blacks, whites, grays only.
 */
object AppColors {
    // Backgrounds — true dark monochrome
    val DarkBackground = Color(0xFF0A0A0A)
    val Surface = Color(0xFF141414)
    val SurfaceVariant = Color(0xFF1C1C1C)
    val SurfaceHover = Color(0xFF242424)
    val CardBackground = Color(0xFF161616)
    val Line = Color(0xFF242424)
    val LineVariant = Color(0xFF333333)

    // Accent — monochrome white
    val Primary = Color(0xFFF2F2F2)
    val PrimaryVariant = Color(0xFFD0D0D0)
    val PrimaryDim = Color(0xFFA0A0A0)
    val OnPrimary = Color(0xFF0A0A0A)

    // Secondary accent
    val Secondary = Color(0xFFA0A0A0)
    val Accent = Color(0xFFF2F2F2)

    // Status
    val Success = Color(0xFFE8E8E8)
    val Error = Color(0xFFFF6B6B)
    val Warning = Color(0xFFC9C9C9)
    val Info = Color(0xFFA0A0A0)

    // Text — monochrome
    val TextPrimary = Color(0xFFF2F2F2)
    val TextSecondary = Color(0xFFA0A0A0)
    val TextMuted = Color(0xFF6A6A6A)

    // Chat
    val UserBubble = Color(0xFF1A1A1A)
    val AgentBubble = Color(0xFF141414)
    val AgentBubbleBorder = Color(0xFF242424)

    // Scrim
    val Scrim = Color(0xB3000000)
}

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.OnPrimary,
    primaryContainer = AppColors.SurfaceVariant,
    secondary = AppColors.Secondary,
    background = AppColors.DarkBackground,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceVariant,
    onSurfaceVariant = AppColors.TextSecondary,
    error = AppColors.Error,
    onError = Color.White,
    onErrorContainer = AppColors.Error,
    outline = AppColors.TextMuted,
    outlineVariant = AppColors.SurfaceVariant,
)

@Composable
fun AndroidAgentTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AppColors.DarkBackground.toArgb()
            window.navigationBarColor = Color(0xFF0A0A0A).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
