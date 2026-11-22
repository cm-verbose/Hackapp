package com.example.hackapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Figma "Shape — corner radius" (Corner/…). */
object HackappCorners {
    val None = 0.dp
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 16.dp
    val LargeIncreased = 20.dp
    val Large = 24.dp
    val ExtraLarge = 32.dp
    val ExtraLargeIncreased = 32.dp
    val ExtraExtraLarge = 48.dp
    /** Corner/Full (999 in Figma): use [androidx.compose.foundation.shape.CircleShape]. */
}

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(HackappCorners.ExtraSmall),
    small = RoundedCornerShape(HackappCorners.Small),
    medium = RoundedCornerShape(HackappCorners.Medium),
    large = RoundedCornerShape(HackappCorners.Large),
    extraLarge = RoundedCornerShape(HackappCorners.ExtraLarge),
)
