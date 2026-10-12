package ru.shturman.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Светлой темы нет: приложение всегда тёмное, меняются только цвета выбранной темы.
@Composable
fun ShturmanTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = ShColors.Accent,
        onPrimary = ShColors.Background,
        background = ShColors.Background,
        onBackground = ShColors.TextPrimary,
        surface = ShColors.Panel,
        onSurface = ShColors.TextPrimary
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = ShTypography,
        content = content
    )
}
