package com.pedro.cinelive.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeoColors.Yellow,
    onPrimary = NeoColors.Navy,
    primaryContainer = NeoColors.SurfaceFocused,
    onPrimaryContainer = NeoColors.TextPrimary,
    secondary = NeoColors.Yellow,
    onSecondary = NeoColors.Navy,
    background = NeoColors.Bg,
    onBackground = NeoColors.TextPrimary,
    surface = NeoColors.Surface,
    onSurface = NeoColors.TextPrimary,
    error = NeoColors.Red,
    onError = NeoColors.TextPrimary
)

@Composable
fun CineLiveTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
