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
    primary = Teal80,
    secondary = TealGrey80,
    tertiary = Mint80,
    background = Color(0xFF121212),         // Deep gray background to avoid eye strain as requested
    surface = Color(0xFF1E1E1E),            // Contrast surface for cards
    onBackground = Color(0xFFE1E3E2),       // Light gray readable text
    onSurface = Color(0xFFE1E3E2),
    surfaceVariant = Color(0xFF2D3332),     // Accent panels
    onSurfaceVariant = Color(0xFFBCC9C8)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Teal40,
    secondary = TealGrey40,
    tertiary = Mint40,
    background = Color(0xFFF9FBFB),        // Soft off-white clean background
    surface = Color(0xFFFFFFFF),           // Pure white cards
    onBackground = Color(0xFF191C1C),      // High-contrast dark text
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFE0F2F1),    // Light teal variant for active components
    onSurfaceVariant = Color(0xFF004D40)
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
