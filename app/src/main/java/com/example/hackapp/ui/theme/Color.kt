package com.example.hackapp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

//Hackapp-specific roles (Figma: M3 › Extended/*). Read with AppTheme.colors.
@Immutable
data class AppColors(
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
val hero: Color,
val onHero: Color,
val heroCard: Color,
val onHeroCard: Color,
val canvas: Color,
val card: Color,
)

// Stop types (glossary: a Stop is an Event or a Meal; bars are Meals). Figma: StopType
enum class StopType { Music, Festival, Theatre, Comedy, Exhibition, Sport, Cinema, Family, Meal, Bar }

@Immutable
data class StopTypeColor(val container: Color, val content: Color)

@Immutable
class StopTypeColors(private val colors: Map<StopType, StopTypeColor>) {
operator fun get(type: StopType): StopTypeColor = colors.getValue(type)
}