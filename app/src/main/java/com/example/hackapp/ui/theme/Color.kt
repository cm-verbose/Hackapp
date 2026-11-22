package com.example.hackapp.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * Colour tokens from Figma "ETS-HackApp" → page "design system" → "Sésame × M3 — Foundations".
 * Names mirror the Figma variables (Schemes/…, Extended/…, StopType/…, Brand/…, Accent/…).
 */

// ---------------------------------------------------------------- M3 schemes (Schemes/…)

internal val LightColors = lightColorScheme(
    primary = Color(0xFFE4002B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF930018),
    inversePrimary = Color(0xFFFFB4AA),
    secondary = Color(0xFF89182C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF5D002C),
    onSecondaryContainer = Color(0xFFE77488),
    tertiary = Color(0xFF097D6C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFADF0E0),
    onTertiaryContainer = Color(0xFF005145),
    error = Color(0xFFB82100),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDBD0),
    onErrorContainer = Color(0xFF8C1900),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF211A17),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF211A17),
    surfaceVariant = Color(0xFFEAE1DD),
    onSurfaceVariant = Color(0xFF54433C),
    surfaceTint = Color(0xFFE4002B),
    inverseSurface = Color(0xFF362F2C),
    inverseOnSurface = Color(0xFFF8EFEB),
    outline = Color(0xFF86736C),
    outlineVariant = Color(0xFFD7C2BA),
    surfaceBright = Color(0xFFFFF8F5),
    surfaceDim = Color(0xFFE2D8D4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF2EE),
    surfaceContainer = Color(0xFFF6ECE8),
    surfaceContainerHigh = Color(0xFFF0E6E2),
    surfaceContainerHighest = Color(0xFFEAE1DD),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4AA),
    onPrimary = Color(0xFF68000D),
    primaryContainer = Color(0xFF930018),
    onPrimaryContainer = Color(0xFFFFDAD5),
    inversePrimary = Color(0xFFC00022),
    secondary = Color(0xFFFFB3B3),
    onSecondary = Color(0xFF68001A),
    secondaryContainer = Color(0xFF5D002C),
    onSecondaryContainer = Color(0xFFE77488),
    tertiary = Color(0xFF4DDCC2),
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005145),
    onTertiaryContainer = Color(0xFFADF0E0),
    error = Color(0xFFFFB59E),
    onError = Color(0xFF5F1500),
    errorContainer = Color(0xFF8C1900),
    onErrorContainer = Color(0xFFFFDBD0),
    background = Color(0xFF19120E),
    onBackground = Color(0xFFEAE1DD),
    surface = Color(0xFF19120E),
    onSurface = Color(0xFFEAE1DD),
    surfaceVariant = Color(0xFF54433C),
    // Not in the Figma dark scheme; M3 convention (light outlineVariant).
    onSurfaceVariant = Color(0xFFD7C2BA),
    surfaceTint = Color(0xFFFFB4AA),
    inverseSurface = Color(0xFFEAE1DD),
    inverseOnSurface = Color(0xFF362F2C),
    outline = Color(0xFFA08D85),
    outlineVariant = Color(0xFF54433C),
    surfaceBright = Color(0xFF3F3835),
    surfaceDim = Color(0xFF19120E),
    surfaceContainerLowest = Color(0xFF150C07),
    surfaceContainerLow = Color(0xFF211A17),
    surfaceContainer = Color(0xFF251E1B),
    surfaceContainerHigh = Color(0xFF2F2826),
    surfaceContainerHighest = Color(0xFF3A3330),
)

// ---------------------------------------------------------------- Extended/… (Hackapp roles + status)

/** Roles that Material 3 has no slot for. Read them through [HackappTheme.appColors]. */
@Immutable
data class AppColors(
    /** Underway & Welcome background, Leg hero. */
    val hero: Color,
    val onHero: Color,
    /** Dark inset on hero. */
    val heroCard: Color,
    val onHeroCard: Color,
    /** Light-grey screen background. */
    val canvas: Color,
    /** White card on hero/canvas (mode-independent). */
    val card: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

internal val LightAppColors = AppColors(
    hero = Color(0xFF5D002C),
    onHero = Color(0xFFE77488),
    heroCard = Color(0xFF181A1B),
    onHeroCard = Color(0xFFE9E4DE),
    canvas = Color(0xFFF0F0F1),
    card = Color(0xFFFFFFFF),
    success = Color(0xFF006D41),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFBCEECF),
    onSuccessContainer = Color(0xFF005230),
    warning = Color(0xFF7F5700),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFDDEB6),
    onWarningContainer = Color(0xFF604100),
    info = Color(0xFF0062A0),
    onInfo = Color(0xFFFFFFFF),
    infoContainer = Color(0xFFD3E4FF),
    onInfoContainer = Color(0xFF00497A),
)

// Figma only defines dark values for the status roles. Hero, hero card and card are the same in
// both modes; the dark canvas uses the dark surface (assumption, not in Figma).
internal val DarkAppColors = LightAppColors.copy(
    canvas = Color(0xFF19120E),
    success = Color(0xFF7BD9A3),
    onSuccess = Color(0xFF00391F),
    successContainer = Color(0xFF005230),
    onSuccessContainer = Color(0xFFBCEECF),
    warning = Color(0xFFEFBE76),
    onWarning = Color(0xFF422C00),
    warningContainer = Color(0xFF604100),
    onWarningContainer = Color(0xFFFDDEB6),
    info = Color(0xFFA2C9FF),
    onInfo = Color(0xFF003256),
    infoContainer = Color(0xFF00497A),
    onInfoContainer = Color(0xFFD3E4FF),
)

// ---------------------------------------------------------------- StopType/… (Stop Type Palette)

/** Glossary: Meal and Bar are Meal Stops; the rest are Event types. */
enum class StopType { Music, Festival, Theatre, Comedy, Exhibition, Sport, Cinema, Family, Meal, Bar }

/** Figma variable collection "Stop Type Palette" and its three modes. */
enum class StopTypePaletteMode { Analogue, Triadique, ComplementaireDivisee }

@Immutable
data class StopTypeColors(
    private val containers: Map<StopType, Color>,
    /** StopType/On <Type>: the same ink for every type and mode. */
    val onStopType: Color = Color(0xFF151515),
) {
    operator fun get(type: StopType): Color = containers.getValue(type)
}

private fun palette(vararg c: Long) =
    StopTypeColors(StopType.entries.zip(c.map { Color(it) }).toMap())

internal fun stopTypeColorsFor(mode: StopTypePaletteMode): StopTypeColors = when (mode) {
    StopTypePaletteMode.Analogue -> palette(
        0xFFD2C4FF, 0xFFFDACEF, 0xFFFFA8C1, 0xFFFFC7C0, 0xFFFFBE90,
        0xFFA4B5FF, 0xFFFFD0E7, 0xFFFFA584, 0xFFFFBABF, 0xFFEFCAFF,
    )
    StopTypePaletteMode.Triadique -> palette(
        0xFF6ED2FF, 0xFFC5EA80, 0xFFFFB1C6, 0xFFFFE082, 0xFF61DDED,
        0xFF86DFA2, 0xFF8EBEFF, 0xFFDCDC7A, 0xFFFFBCB3, 0xFFF7A7EA,
    )
    StopTypePaletteMode.ComplementaireDivisee -> palette(
        0xFF90DDA7, 0xFF61DDED, 0xFFFFB1C6, 0xFF85EFDD, 0xFF35D2F5,
        0xFF75EDC1, 0xFF29CAC9, 0xFFB1ECA0, 0xFFFFC3BB, 0xFFF7B0EB,
    )
}

// ---------------------------------------------------------------- Brand/… and Accent/…

/** Brand colours (Sésame / montreal.ca). Accents are categorical only, never text on surface. */
object HackappPalette {
    val SesameRed = Color(0xFFE4002B)
    val SesameRedAccessible = Color(0xFFD60028)
    val Burgundy = Color(0xFF89182C)
    val SoftPink = Color(0xFFFFB5DA)
    val WarmSand = Color(0xFFF0EBE9)
    val Ink = Color(0xFF151515)
    val Text = Color(0xFF333333)
    val MontrealTeal = Color(0xFF097D6C)
    val MontrealBlue = Color(0xFF0079C4)

    val AccentYellow = Color(0xFFFFC83B)
    val AccentGreen = Color(0xFF85EAB9)
    val AccentPink = Color(0xFFFFB9DA)
    val AccentBlue = Color(0xFF89D7F8)
    val AccentLemon = Color(0xFFFDF05E)
    val AccentPorto = Color(0xFFAB1F5F)
    val AccentPear = Color(0xFFCADE74)
    val AccentBahama = Color(0xFF005C80)
    val AccentSalmon = Color(0xFFFFAD84)
    val AccentMagenta = Color(0xFFD1008F)
    val AccentLavender = Color(0xFFAFA6F5)
    val AccentViolet = Color(0xFF64007F)
}
