package com.example.pos.modules.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Modular foundation for Theme (Module 15).
 * Contains M3 High-Contrast palettes tailored for fuel service stations.
 */
object ThemeFeature {
    val LightColorScheme: ColorScheme = lightColorScheme(
        primary = Color(0xFF0D5C3A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC8E6C9),
        onPrimaryContainer = Color(0xFF00210B),
        secondary = Color(0xFF006C50),
        onSecondary = Color.White,
        tertiary = Color(0xFFBA1A1A),
        background = Color(0xFFF8FAF7),
        onBackground = Color(0xFF191C1A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF191C1A),
        surfaceVariant = Color(0xFFE1E5DF),
        onSurfaceVariant = Color(0xFF414942),
        outline = Color(0xFF717971)
    )

    val DarkColorScheme: ColorScheme = darkColorScheme(
        primary = Color(0xFF72DA9B),
        onPrimary = Color(0xFF00391E),
        primaryContainer = Color(0xFF00522E),
        onPrimaryContainer = Color(0xFF8FF7B5),
        secondary = Color(0xFF53DBC5),
        onSecondary = Color(0xFF003828),
        background = Color(0xFF111412),
        onBackground = Color(0xFFE1E3DF),
        surface = Color(0xFF191C1A),
        onSurface = Color(0xFFE1E3DF),
        surfaceVariant = Color(0xFF414942),
        onSurfaceVariant = Color(0xFFC1C9BF),
        outline = Color(0xFF8B938B)
    )
}
