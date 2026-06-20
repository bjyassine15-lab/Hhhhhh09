package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF00E5FF),            // Cyber Cyan primary
    onPrimary = Color(0xFF121212),          // High-contrast deep primary text
    primaryContainer = Color(0xFF1E1E1E),   // Night Gray card_background
    onPrimaryContainer = Color(0xFFE0E0E0), // Comfortable white text
    inversePrimary = Color(0xFF121212),
    secondary = Color(0xFFB0B0B0),
    onSecondary = Color(0xFF121212),
    secondaryContainer = Color(0xFF2D2D2D), // Accent panels
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFF03DAC6),
    onTertiary = Color(0xFF121212),
    tertiaryContainer = Color(0xFF1E1E1E),
    onTertiaryContainer = Color(0xFFE0E0E0),
    background = Color(0xFF121212),         // Charcoal Black background
    onBackground = Color(0xFFE0E0E0),       // Comfortable white text
    surface = Color(0xFF1E1E1E),            // Night Gray card surface
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2D2D2D),     // Accent panels
    onSurfaceVariant = Color(0xFFB0B0B0),
    surfaceTint = Color(0xFF121212)         // Prevent default purple surfaceTint
  )

private val LightColorScheme =
  darkColorScheme(                          // Force Dark palette even in Light configuration to lock the theme
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF121212),
    primaryContainer = Color(0xFF1E1E1E),
    onPrimaryContainer = Color(0xFFE0E0E0),
    inversePrimary = Color(0xFF121212),
    secondary = Color(0xFFB0B0B0),
    onSecondary = Color(0xFF121212),
    secondaryContainer = Color(0xFF2D2D2D),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFF03DAC6),
    onTertiary = Color(0xFF121212),
    tertiaryContainer = Color(0xFF1E1E1E),
    onTertiaryContainer = Color(0xFFE0E0E0),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFB0B0B0),
    surfaceTint = Color(0xFF121212)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled by default to respect custom harmonious developer rules
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
