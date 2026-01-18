package com.awi.lock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private enum class OemStyle { PIXEL, SAMSUNG, XIAOMI, OTHER }

@Composable
fun AWILockTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val oemStyle = remember {
        detectOemStyle(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND
        )
    }

    val colorScheme = remember(darkTheme, supportsDynamic, oemStyle, context) {
        if (supportsDynamic) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            fallbackColorSchemeFor(oemStyle, darkTheme)
        }
    }

    val shapes = remember(oemStyle) { shapesFor(oemStyle) }

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
        shapes = shapes,
        content = content
    )
}

private fun detectOemStyle(manufacturer: String, brand: String): OemStyle {
    val lowerManufacturer = manufacturer.lowercase(Locale.getDefault())
    val lowerBrand = brand.lowercase(Locale.getDefault())
    return when {
        lowerManufacturer.contains("samsung") || lowerBrand.contains("samsung") -> OemStyle.SAMSUNG
        lowerManufacturer.contains("xiaomi") || lowerBrand.contains("xiaomi") ||
            lowerBrand.contains("redmi") || lowerBrand.contains("poco") -> OemStyle.XIAOMI
        lowerManufacturer.contains("google") || lowerBrand.contains("google") || lowerBrand.contains("pixel") -> OemStyle.PIXEL
        else -> OemStyle.OTHER
    }
}

private fun fallbackColorSchemeFor(oemStyle: OemStyle, darkTheme: Boolean): ColorScheme = when (oemStyle) {
    OemStyle.SAMSUNG -> if (darkTheme) OneUiDarkColors else OneUiLightColors
    OemStyle.XIAOMI -> if (darkTheme) MiuiDarkColors else MiuiLightColors
    OemStyle.PIXEL, OemStyle.OTHER -> if (darkTheme) MaterialFallbackDarkColors else MaterialFallbackLightColors
}

private fun shapesFor(oemStyle: OemStyle): Shapes = when (oemStyle) {
    OemStyle.SAMSUNG -> Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp)
    )

    OemStyle.XIAOMI -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(26.dp)
    )

    OemStyle.PIXEL -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(26.dp),
        extraLarge = RoundedCornerShape(32.dp)
    )

    OemStyle.OTHER -> Shapes()
}
