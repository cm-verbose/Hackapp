package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.hackapp.ui.theme.HackappPalette
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma "Stamp" (30:737): a Passport stamp. One per Event or restaurant/bar ever visited
 * (chains count once). Lemon disc with a dashed ink border and the place's initials,
 * then name, date and District.
 *
 * Colours are the brand accents (Accent/Lemon, Brand/Ink) and do not change in dark mode, as in Figma.
 */
@Composable
fun Stamp(
    initials: String,
    stampName: String,
    date: String,
    district: String,
    modifier: Modifier = Modifier,
    discColor: Color = HackappPalette.AccentLemon,
    inkColor: Color = HackappPalette.Ink,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.width(104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(discColor, CircleShape)
                .dashedCircleBorder(2.dp, inkColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, style = MaterialTheme.typography.titleMedium, color = inkColor, maxLines = 1)
        }
        val center = Modifier.fillMaxWidth()
        Text(stampName, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = center)
        Text(date, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = center)
        Text(district, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = center)
    }
}

/** Stamp initials from a place name: first letters of the first two words ("Jazz sur le toit" -> "JS"). */
fun stampInitials(name: String): String =
    name.split(' ', '-', '’', '\'')
        .filter { it.isNotBlank() && it.first().isLetterOrDigit() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

private fun Modifier.dashedCircleBorder(width: Dp, color: Color): Modifier = drawBehind {
    val stroke = width.toPx()
    drawCircle(
        color = color,
        radius = (size.minDimension - stroke) / 2f,
        center = Offset(size.width / 2f, size.height / 2f),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

@Preview(name = "Stamp", showBackground = true)
@Composable
private fun StampPreview() = HackappTheme {
    Stamp(initials = "JZ", stampName = "Jazz sur le toit", date = "28 nov. 2026", district = "Plateau", modifier = Modifier.padding(8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Stamp · passport grid", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun StampGridPreview() = HackappTheme {
    FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("Jazz sur le toit" to "Plateau", "Bistro du coin" to "Ville-Marie", "Marché Jean-Talon" to "Rosemont").forEach { (n, d) ->
            Stamp(initials = stampInitials(n), stampName = n, date = "28 nov. 2026", district = d)
        }
    }
}
