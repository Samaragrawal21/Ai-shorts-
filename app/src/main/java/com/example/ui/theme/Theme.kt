package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
  darkColorScheme(
    primary = HighlightBlue,
    onPrimary = DarkBackground,
    primaryContainer = AccentTag,
    onPrimaryContainer = HighlightBlue,
    secondary = AccentPurple,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = PrimaryText,
    surface = DarkCardSurface,
    onSurface = PrimaryText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = SecondaryBody,
    outline = AccentTagBorder,
    error = ErrorRed,
  )

@Composable
fun AIShortsTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = DarkColorScheme,
    typography = Typography,
    content = content
  )
}

