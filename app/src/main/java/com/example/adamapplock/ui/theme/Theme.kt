package com.example.adamapplock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat


enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun AdamAppLockTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
    }

    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme =
        if (supportsDynamic) {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) DarkScheme else LightScheme
        }

    // Optional: make system bars readable (light/dark icons)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? androidx.activity.ComponentActivity)?.window
        window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            val controller = WindowCompat.getInsetsController(it, it.decorView)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        //shapes = Shapes,
        content = content
    )
}

/* Fallback palettes for pre-Android 12 devices (customize if you want) */
private val LightScheme = lightColorScheme()
private val DarkScheme  = darkColorScheme()

