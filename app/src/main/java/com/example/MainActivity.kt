package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PosViewModel
import com.example.ui.viewmodel.PosViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved theme preference
        val sharedPrefs = getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE)
        val savedMode = sharedPrefs.getInt("night_mode_state", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedMode)

        enableEdgeToEdge()

        val viewModel: PosViewModel by viewModels {
            PosViewModelFactory(application)
        }

        setContent {
            val sharedPrefsState = remember { getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE) }
            var currentMode by remember {
                mutableStateOf(sharedPrefsState.getInt("night_mode_state", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
            }

            val isDarkThemeNow = when (currentMode) {
                AppCompatDelegate.MODE_NIGHT_YES -> true
                AppCompatDelegate.MODE_NIGHT_NO -> false
                else -> isSystemInDarkTheme()
            }

            Crossfade(
                targetState = isDarkThemeNow,
                animationSpec = tween(durationMillis = 800),
                label = "ThemeColorFade"
            ) { targetDarkTheme ->
                MyApplicationTheme(darkTheme = targetDarkTheme, dynamicColor = false) {
                    MainScreen(
                        viewModel = viewModel,
                        onThemeToggle = {
                            val nextMode = if (isDarkThemeNow) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
                            sharedPrefsState.edit().putInt("night_mode_state", nextMode).apply()
                            AppCompatDelegate.setDefaultNightMode(nextMode)
                            currentMode = nextMode
                        }
                    )
                }
            }
        }
    }
}
