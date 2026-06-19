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
    primary = FinDarkPrimary,
    secondary = FinDarkSecondary,
    tertiary = FinDarkTertiary,
    background = FinDarkBackground,         // Modern deep slate blue-gray
    surface = FinDarkSurface,               // Cool metal gray cards
    onBackground = Color(0xFFF8FAFC),       // Clean radiant off-white text
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = FinDarkSurfaceVariant,  // Premium dark accent panels
    onSurfaceVariant = Color(0xFFCBD5E1)      // Readable secondary text
  )

private val LightColorScheme =
  lightColorScheme(
    primary = FinLightPrimary,
    secondary = FinLightSecondary,
    tertiary = FinLightTertiary,
    background = FinLightBackground,        // Clean modern light gray-blue
    surface = FinLightSurface,               // Polished white cards
    onBackground = Color(0xFF0F172A),       // Sharp professional dark text
    onSurface = Color(0xFF0F172A),
    surfaceVariant = FinLightSurfaceVariant, // Light accent gray
    onSurfaceVariant = Color(0xFF475569)     // Slate-cool secondary text
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
