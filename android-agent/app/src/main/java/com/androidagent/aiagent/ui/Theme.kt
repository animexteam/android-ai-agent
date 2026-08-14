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
 * v4.1: Refined color palette inspired by Gemini/ChatGPT dark themes.
 * Clean, modern, high-contrast dark theme.
 */
object AppColors {
    // Backgrounds
    val DarkBackground = Color(0xFF0A0A0F)
    val Surface = Color(0xFF141419)
    val SurfaceVariant = Color(0xFF1E1E26)
    val SurfaceHover = Color(0xFF26262F)
    val CardBackground = Color(0xFF1A1A22)

    // Primary — vibrant blue (Gemini-inspired)
    val Primary = Color(0xFF4A9EFF)
    val PrimaryVariant = Color(0xFF1A6FEB)
    val PrimaryDim = Color(0xFF2A5DB0)
    val OnPrimary = Color.White

    // Accent
    val Secondary = Color(0xFF7C83FF)
    val Accent = Color(0xFF00C9A7)

    // Status
    val Success = Color(0xFF34D399)
    val Error = Color(0xFFEF4444)
    val Warning = Color(0xFFFBBF24)
    val Info = Color(0xFF60A5FA)

    // Text
    val TextPrimary = Color(0xFFF0F0F5)
    val TextSecondary = Color(0xFF9898A6)
    val TextMuted = Color(0xFF5A5A6E)

    // Chat specific
    val UserBubble = Color(0xFF2A4A7F)
    val AgentBubble = Color(0xFF1E1E26)
    val AgentBubbleBorder = Color(0xFF2A2A36)
}

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.OnPrimary,
    primaryContainer = AppColors.PrimaryVariant,
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
            window.navigationBarColor = Color(0xFF0A0A0F).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
