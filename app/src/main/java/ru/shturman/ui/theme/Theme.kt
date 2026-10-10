package ru.shturman.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Светлой темы нет — приложение всегда тёмное.
private val DarkColors = darkColorScheme(
    primary = ShColors.Accent,
    onPrimary = ShColors.Background,
    background = ShColors.Background,
    onBackground = ShColors.TextPrimary,
    surface = ShColors.Panel,
    onSurface = ShColors.TextPrimary
)

@Composable
fun ShturmanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = ShTypography,
        content = content
    )
}
