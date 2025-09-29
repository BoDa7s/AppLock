package com.example.adamapplock.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

val LightColors = lightColorScheme(
    primary        = Color(0xFF4F7DFF), // blue
    onPrimary      = Color.White,
    secondary      = Color(0xFF6B7C93),
    onSecondary    = Color.White,
    background     = Color(0xFFF7F7FA), // very light gray
    onBackground   = Color(0xFF101114), // near-black text
    surface        = Color.White,       // cards, app bars, text fields
    onSurface      = Color(0xFF1A1B1F),
)

val DarkColors = darkColorScheme(
    primary        = Color(0xFF8FB1FF), // lighter blue for contrast
    onPrimary      = Color(0xFF0A0D14),
    secondary      = Color(0xFF8A98AC),
    onSecondary    = Color(0xFF0A0D14),
    background     = Color(0xFF0E1014), // near-black
    onBackground   = Color(0xFFE7E9EE), // light text
    surface        = Color(0xFF141820), // panels, sheets
    onSurface      = Color(0xFFE7E9EE),
)


val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)