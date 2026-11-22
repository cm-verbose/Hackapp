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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
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
        StampDisc(initials, Modifier.size(StampDiscSize), discColor, inkColor)
        val center = Modifier.fillMaxWidth()
        Text(stampName, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = center)
        Text(date, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = center)
        Text(district, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = center)
    }
}

/** Diameter of the Stamp disc. */
val StampDiscSize = 72.dp

/** The Stamp's filled [shape] with its dashed ink border and initials. Fills [modifier]'s size. */
@Composable
fun StampDisc(
    initials: String,
    modifier: Modifier = Modifier,
    discColor: Color = HackappPalette.AccentLemon,
    inkColor: Color = HackappPalette.Ink,
    shape: Shape = CircleShape,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    initialsScale: () -> Float = { 1f },
) {
    Box(
        modifier
            .background(discColor, shape)
            .dashedBorder(2.dp, inkColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            style = textStyle,
            color = inkColor,
            maxLines = 1,
            modifier = Modifier
                .clearAndSetSemantics {}
                .graphicsLayer {
                    val scale = initialsScale()
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

/**
 * Stamp initials from a name: the first letters of its first two words ("Jazz sur le toit" -> "JS").
 * An apostrophe separates words only after a one-letter elision ("L'Avenue" -> "LA"), so a possessive
 * stays one word ("Schwartz's" -> "S"). Leading punctuation is ignored, and the result is never blank.
 */
fun stampInitials(name: String): String {
    val words = name.split(Regex("[\\s\\-–—]+"))
        .map { word -> word.dropWhile { !it.isLetterOrDigit() } }
        .flatMap { word ->
            if (word.length > 2 && word[0].isLetter() && word[1] in "'’") {
                listOf(word.substring(0, 1), word.substring(2).dropWhile { !it.isLetterOrDigit() })
            } else {
                listOf(word)
            }
        }
        .filter { it.isNotEmpty() }
    if (words.isNotEmpty()) return words.take(2).joinToString("") { it.first().uppercase() }
    val text = name.trim()
    return if (text.isEmpty()) "?" else text.substring(0, text.offsetByCodePoints(0, 1))
}

private fun Modifier.dashedBorder(width: Dp, color: Color, shape: Shape): Modifier = drawWithCache {
    val stroke = width.toPx()
    val outline = shape.createOutline(Size(size.width - stroke, size.height - stroke), layoutDirection, this)
    val style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
    onDrawBehind { translate(stroke / 2f, stroke / 2f) { drawOutline(outline, color, style = style) } }
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
