package com.example.hackapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma "Icons/…" components. Every one is a Material Symbols glyph (filled), so they map
 * one-to-one to Material Icons Extended instead of shipping copies of the SVGs.
 */
enum class HackappIcon(val vector: ImageVector) {
    // Components page, group "Qindong"
    LocalCafe(Icons.Filled.LocalCafe),
    LocalPizza(Icons.Filled.LocalPizza),
    SentimentSatisfied(Icons.Filled.SentimentSatisfied),
    DirectionsRun(Icons.AutoMirrored.Filled.DirectionsRun),
    Palette(Icons.Filled.Palette),
    Star(Icons.Filled.Star),
    LocalActivity(Icons.Filled.LocalActivity),
    People(Icons.Filled.People),
    Eco(Icons.Filled.Eco),

    // Used by Qindong components; the components themselves live in "First Eric Group"
    MusicNote(Icons.Filled.MusicNote),
    Restaurant(Icons.Filled.Restaurant),
    LocalBar(Icons.Filled.LocalBar),
    Movie(Icons.Filled.Movie),
    ConfirmationNumber(Icons.Filled.ConfirmationNumber),
}

/** Decorative icon (24 dp in Figma). Pass a [contentDescription] when it carries meaning. */
@Composable
fun HackappIconView(
    icon: HackappIcon,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = icon.vector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

// ---------------------------------------------------------------- previews (one per Figma icon)

@Composable
private fun IconPreview(icon: HackappIcon) = HackappTheme {
    HackappIconView(icon, Modifier.padding(4.dp))
}

@Preview(name = "Icons/LocalCafe", showBackground = true)
@Composable private fun LocalCafePreview() = IconPreview(HackappIcon.LocalCafe)

@Preview(name = "Icons/LocalPizza", showBackground = true)
@Composable private fun LocalPizzaPreview() = IconPreview(HackappIcon.LocalPizza)

@Preview(name = "Icons/SentimentSatisfied", showBackground = true)
@Composable private fun SentimentSatisfiedPreview() = IconPreview(HackappIcon.SentimentSatisfied)

@Preview(name = "Icons/DirectionsRun", showBackground = true)
@Composable private fun DirectionsRunPreview() = IconPreview(HackappIcon.DirectionsRun)

@Preview(name = "Icons/Palette", showBackground = true)
@Composable private fun PalettePreview() = IconPreview(HackappIcon.Palette)

@Preview(name = "Icons/Star", showBackground = true)
@Composable private fun StarPreview() = IconPreview(HackappIcon.Star)

@Preview(name = "Icons/LocalActivity", showBackground = true)
@Composable private fun LocalActivityPreview() = IconPreview(HackappIcon.LocalActivity)

@Preview(name = "Icons/People", showBackground = true)
@Composable private fun PeoplePreview() = IconPreview(HackappIcon.People)

@Preview(name = "Icons/Eco", showBackground = true)
@Composable private fun EcoPreview() = IconPreview(HackappIcon.Eco)

@Preview(name = "Icons · all", showBackground = true)
@Composable
private fun AllIconsPreview() = HackappTheme {
    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        HackappIcon.entries.forEach { HackappIconView(it) }
    }
}
