package com.sentinelvault.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SentinelDarkColors = darkColorScheme(
    primary = SecurityBlue,
    onPrimary = DeepBlack,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = PureWhite,
    secondary = SecurityIndigo,
    onSecondary = PureWhite,
    tertiary = SignalAmber,
    onTertiary = DeepBlack,
    background = DeepBlack,
    onBackground = PureWhite,
    surface = SurfaceDark,
    onSurface = PureWhite,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = FogBlue,
    outline = SurfaceOutline,
    error = AlertNeon,
    onError = PureWhite
)

@Composable
fun SentinelTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Android 15+ enforces edge-to-edge: opt out of automatic decor fitting and let
            // composables pad themselves through Modifier.systemBarsPadding(). The
            // statusBarColor / navigationBarColor setters are no-ops from targetSdk = 35
            // and removed in a future release.
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = SentinelDarkColors,
        typography = SentinelTypography,
        content = content
    )
}
