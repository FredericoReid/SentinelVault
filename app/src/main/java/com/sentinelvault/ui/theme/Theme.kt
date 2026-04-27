package com.sentinelvault.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SentinelDarkColors = darkColorScheme(
    primary = SecurityBlue,
    onPrimary = PureWhite,
    background = DeepBlack,
    onBackground = PureWhite,
    surface = SurfaceDark,
    onSurface = PureWhite,
    error = AlertNeon,
    onError = PureWhite
)

@Composable
fun SentinelTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DeepBlack.toArgb()
            window.navigationBarColor = DeepBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = SentinelDarkColors,
        typography = SentinelTypography,
        content = content
    )
}
