package com.example.hackapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Figma: "Typography — M3 type scale". Two families, both Figtree in Figma
 * (a stand-in for TT Commons):
 *   Brand → display, headline, title/large
 *   Plain → everything else
 *
 * TODO(font): Figtree is not bundled yet, so both families fall back to the system sans-serif.
 * To enable it: download Figtree (Regular, Medium, Bold) from Google Fonts into res/font/ as
 * figtree_regular.ttf, figtree_medium.ttf, figtree_bold.ttf and replace FontFamily.Default below with
 *   FontFamily(Font(R.font.figtree_regular, FontWeight.Normal),
 *              Font(R.font.figtree_medium, FontWeight.Medium),
 *              Font(R.font.figtree_bold, FontWeight.Bold))
 */
object HackappFonts {
    val Brand: FontFamily = FontFamily.Default
    val Plain: FontFamily = FontFamily.Default
}

private fun style(family: FontFamily, weight: FontWeight, size: Int, lineHeight: Int, tracking: Double) =
    TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
    )

private val B = HackappFonts.Brand
private val P = HackappFonts.Plain

val Typography = Typography(
    displayLarge = style(B, FontWeight.Bold, 57, 60, -1.5),
    displayMedium = style(B, FontWeight.Bold, 45, 48, -1.0),
    displaySmall = style(B, FontWeight.Bold, 36, 44, -0.75),
    headlineLarge = style(B, FontWeight.Bold, 32, 40, -0.5),
    headlineMedium = style(B, FontWeight.Bold, 28, 36, -0.5),
    headlineSmall = style(B, FontWeight.Bold, 24, 32, -0.25),
    titleLarge = style(B, FontWeight.Medium, 22, 28, 0.0),
    titleMedium = style(P, FontWeight.Medium, 16, 24, 0.15),
    titleSmall = style(P, FontWeight.Medium, 14, 20, 0.1),
    bodyLarge = style(P, FontWeight.Normal, 16, 24, 0.5),
    bodyMedium = style(P, FontWeight.Normal, 14, 20, 0.25),
    bodySmall = style(P, FontWeight.Normal, 12, 16, 0.4),
    labelLarge = style(P, FontWeight.Medium, 14, 20, 0.1),
    labelMedium = style(P, FontWeight.Medium, 12, 16, 0.5),
    labelSmall = style(P, FontWeight.Medium, 11, 16, 0.5),
)
