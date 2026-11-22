package com.example.hackapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/* Visual check of the tokens against Figma "Sésame × M3 — Foundations". */

@Composable
private fun Swatch(name: String, color: Color, on: Color) {
    Box(
        Modifier
            .size(width = 120.dp, height = 56.dp)
            .background(color, RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) { Text(name, style = MaterialTheme.typography.labelSmall, color = on) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchemeSwatches() {
    val s = MaterialTheme.colorScheme
    val a = HackappTheme.appColors
    FlowRow(Modifier.background(s.surfaceContainerLowest).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Swatch("Primary", s.primary, s.onPrimary)
        Swatch("Primary Container", s.primaryContainer, s.onPrimaryContainer)
        Swatch("Secondary", s.secondary, s.onSecondary)
        Swatch("Secondary Container", s.secondaryContainer, s.onSecondaryContainer)
        Swatch("Tertiary", s.tertiary, s.onTertiary)
        Swatch("Tertiary Container", s.tertiaryContainer, s.onTertiaryContainer)
        Swatch("Error", s.error, s.onError)
        Swatch("Error Container", s.errorContainer, s.onErrorContainer)
        Swatch("Surface", s.surface, s.onSurface)
        Swatch("Surface Container High", s.surfaceContainerHigh, s.onSurface)
        Swatch("Inverse Surface", s.inverseSurface, s.inverseOnSurface)
        Swatch("Outline", s.outline, s.inverseOnSurface)
        Swatch("Success", a.success, a.onSuccess)
        Swatch("Warning", a.warning, a.onWarning)
        Swatch("Info", a.info, a.onInfo)
        Swatch("Hero", a.hero, a.onHero)
        Swatch("Hero Card", a.heroCard, a.onHeroCard)
        Swatch("Canvas", a.canvas, s.onSurface)
    }
}

@Preview(name = "Colour roles · Light", widthDp = 800)
@Composable
private fun LightSchemePreview() = HackappTheme(darkTheme = false) { SchemeSwatches() }

@Preview(name = "Colour roles · Dark", widthDp = 800)
@Composable
private fun DarkSchemePreview() = HackappTheme(darkTheme = true) { SchemeSwatches() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopTypeRow(mode: StopTypePaletteMode) = HackappTheme(stopTypePalette = mode) {
    val c = HackappTheme.stopTypeColors
    Column(Modifier.background(Color.White).padding(8.dp)) {
        Text(mode.name, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StopType.entries.forEach {
                Text(
                    it.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = c.onStopType,
                    modifier = Modifier.background(c[it], CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Preview(name = "Stop Type Palette", widthDp = 800)
@Composable
private fun StopTypePalettePreview() = Column {
    StopTypePaletteMode.entries.forEach { StopTypeRow(it) }
}

@Preview(name = "Typography — M3 type scale", widthDp = 800, showBackground = true)
@Composable
private fun TypographyPreview() = HackappTheme {
    val t = MaterialTheme.typography
    val sample = "Planifiez votre sortie à Montréal"
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            "display/small" to t.displaySmall, "headline/large" to t.headlineLarge,
            "headline/medium" to t.headlineMedium, "headline/small" to t.headlineSmall,
            "title/large" to t.titleLarge, "title/medium" to t.titleMedium, "title/small" to t.titleSmall,
            "body/large" to t.bodyLarge, "body/medium" to t.bodyMedium, "body/small" to t.bodySmall,
            "label/large" to t.labelLarge, "label/medium" to t.labelMedium, "label/small" to t.labelSmall,
        ).forEach { (name, style) -> Text("$name · $sample", style = style) }
    }
}
