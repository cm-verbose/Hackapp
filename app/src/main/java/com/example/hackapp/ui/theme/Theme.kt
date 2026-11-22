package com.example.hackapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
val LocalStopTypeColors = staticCompositionLocalOf { stopTypeColorsFor(StopTypePaletteMode.Analogue) }

/**
 * App theme (Figma "City Pack → Compose": HackappTheme = MaterialTheme + LocalAppColors +
 * LocalStopTypeColors). Brand colours always win: Android 12+ dynamic colour is not used.
 */
@Composable
fun HackappTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    stopTypePalette: StopTypePaletteMode = StopTypePaletteMode.Analogue,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppColors provides if (darkTheme) DarkAppColors else LightAppColors,
        LocalStopTypeColors provides stopTypeColorsFor(stopTypePalette),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}

/** Shortcuts for the theme values Material 3 does not hold. */
object HackappTheme {
    val appColors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val stopTypeColors: StopTypeColors
        @Composable @ReadOnlyComposable get() = LocalStopTypeColors.current
}

/** Kept so existing call sites compile; use [HackappTheme]. */
@Deprecated("Use HackappTheme", ReplaceWith("HackappTheme(darkTheme = darkTheme, content = content)"))
@Composable
fun AndroidTemplateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) = HackappTheme(darkTheme = darkTheme, content = content)
