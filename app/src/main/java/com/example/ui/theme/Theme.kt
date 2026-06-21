package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

private val LightColorScheme =
  lightColorScheme(
    primary = CyberPrimary,
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF006064),
    inversePrimary = CyberSecondary,
    secondary = CyberSecondary,
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFF0F4F8),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = CyberSuccess,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8F5E9),
    onTertiaryContainer = Color(0xFF1B5E20),
    background = Color(0xFFF4F6F9), // Soft misty white/blue gray
    onBackground = Color(0xFF12151C), // Deep slate dark text
    surface = Color(0xFFFFFFFF), // Pristine light white card
    onSurface = Color(0xFF12151C),
    surfaceVariant = Color(0xFFECEFF1),
    onSurfaceVariant = Color(0xFF37474F),
    surfaceTint = Color(0xFFF4F6F9),
    error = CyberDanger,
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFCFD8DC)
  )

@Composable
fun animateColorScheme(targetColorScheme: ColorScheme): ColorScheme {
    val duration = 400
    val primary by animateColorAsState(targetColorScheme.primary, tween(duration), label = "primary")
    val onPrimary by animateColorAsState(targetColorScheme.onPrimary, tween(duration), label = "onPrimary")
    val primaryContainer by animateColorAsState(targetColorScheme.primaryContainer, tween(duration), label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(targetColorScheme.onPrimaryContainer, tween(duration), label = "onPrimaryContainer")
    val inversePrimary by animateColorAsState(targetColorScheme.inversePrimary, tween(duration), label = "inversePrimary")
    val secondary by animateColorAsState(targetColorScheme.secondary, tween(duration), label = "secondary")
    val onSecondary by animateColorAsState(targetColorScheme.onSecondary, tween(duration), label = "onSecondary")
    val secondaryContainer by animateColorAsState(targetColorScheme.secondaryContainer, tween(duration), label = "secondaryContainer")
    val onSecondaryContainer by animateColorAsState(targetColorScheme.onSecondaryContainer, tween(duration), label = "onSecondaryContainer")
    val tertiary by animateColorAsState(targetColorScheme.tertiary, tween(duration), label = "tertiary")
    val onTertiary by animateColorAsState(targetColorScheme.onTertiary, tween(duration), label = "onTertiary")
    val tertiaryContainer by animateColorAsState(targetColorScheme.tertiaryContainer, tween(duration), label = "tertiaryContainer")
    val onTertiaryContainer by animateColorAsState(targetColorScheme.onTertiaryContainer, tween(duration), label = "onTertiaryContainer")
    val background by animateColorAsState(targetColorScheme.background, tween(duration), label = "background")
    val onBackground by animateColorAsState(targetColorScheme.onBackground, tween(duration), label = "onBackground")
    val surface by animateColorAsState(targetColorScheme.surface, tween(duration), label = "surface")
    val onSurface by animateColorAsState(targetColorScheme.onSurface, tween(duration), label = "onSurface")
    val surfaceVariant by animateColorAsState(targetColorScheme.surfaceVariant, tween(duration), label = "surfaceVariant")
    val onSurfaceVariant by animateColorAsState(targetColorScheme.onSurfaceVariant, tween(duration), label = "onSurfaceVariant")
    val surfaceTint by animateColorAsState(targetColorScheme.surfaceTint, tween(duration), label = "surfaceTint")
    val error by animateColorAsState(targetColorScheme.error, tween(duration), label = "error")
    val onError by animateColorAsState(targetColorScheme.onError, tween(duration), label = "onError")
    val outline by animateColorAsState(targetColorScheme.outline, tween(duration), label = "outline")

    return targetColorScheme.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        error = error,
        onError = onError,
        outline = outline
    )
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled by default to respect custom harmonious developer rules
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val baseColorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  val colorScheme = animateColorScheme(baseColorScheme)

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
