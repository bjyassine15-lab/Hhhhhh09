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
    primary = CyberPrimary,
    onPrimary = Color(0xFF000000),
    primaryContainer = CyberCard3,
    onPrimaryContainer = CyberTextPrimary,
    inversePrimary = CyberSecondary,
    secondary = CyberSecondary,
    onSecondary = Color(0xFF000000),
    secondaryContainer = CyberBackgroundSecondary,
    onSecondaryContainer = CyberTextSecondary,
    tertiary = CyberSuccess,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = CyberCard1,
    onTertiaryContainer = CyberTextSecondary,
    background = CyberBackground,
    onBackground = CyberTextPrimary,
    surface = CyberCard1,
    onSurface = CyberTextPrimary,
    surfaceVariant = CyberCard2,
    onSurfaceVariant = CyberTextSecondary,
    surfaceTint = CyberBackground,
    error = CyberDanger,
    onError = Color(0xFFFFFFFF),
    outline = CyberTextHint
  )

private val LightColorScheme = DarkColorScheme

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
