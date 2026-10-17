package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
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
    primary = FarmGreenPrimary,
    onPrimary = FarmGreenOnPrimary,
    primaryContainer = FarmGreenContainer,
    onPrimaryContainer = FarmGreenOnContainer,
    secondary = FarmSecondary,
    tertiary = FarmTertiary,
    background = FarmBackgroundLight,
    surface = FarmSurfaceLight,
    onBackground = FarmOnSurfaceLight,
    onSurface = FarmOnSurfaceLight
)

@Composable
fun FarmManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val typography = createFarmTypography(fontScale)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
