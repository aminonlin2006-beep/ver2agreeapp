package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection

private val DarkColorScheme = darkColorScheme(
    primary = FarmGreenPrimaryDark,
    onPrimary = FarmGreenOnPrimaryDark,
    primaryContainer = FarmGreenContainerDark,
    onPrimaryContainer = FarmGreenOnContainerDark,
    secondary = FarmSecondaryDark,
    tertiary = FarmTertiaryDark,
    background = FarmBackgroundDark,
    surface = FarmSurfaceDark,
    onBackground = FarmOnSurfaceDark,
    onSurface = FarmOnSurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32), // Deep Green
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8E6C9), // Soft Green
    onPrimaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFF689F38), // Light Green
    tertiary = Color(0xFF8D6E63), // Earth Brown
    background = Color(0xFFF9F9F8), // Soft Off-White
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF333333), // Darker Grey for text
    onSurface = Color(0xFF333333)
)

@Composable
fun FarmManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val typography = createFarmTypography(fontScale)

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalTextStyle provides TextStyle(fontFamily = VazirmatnFontFamily)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
