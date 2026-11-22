package com.example.hackapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.ui.theme.StopType

/** Display-ready price of a Stop option. */
sealed interface PriceLabel {
    data object Free : PriceLabel
    data object Unknown : PriceLabel
    data class Amount(val text: String) : PriceLabel
}

@Composable
fun PriceLabel.text(): String = when (this) {
    PriceLabel.Free -> stringResource(R.string.price_free)
    PriceLabel.Unknown -> stringResource(R.string.price_unknown)
    is PriceLabel.Amount -> text
}

/** One-hour block heading on the selection screen, e.g. "18:00 – 19:00". */
@Composable
fun TimeBlockHeader(start: String, end: String, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.selection_time_block, start, end),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/** Event type heading inside a time block. */
@Composable
fun StopTypeHeader(type: StopType, modifier: Modifier = Modifier) {
    Text(
        stopTypeLabel(type),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

/**
 * Figma "EventCard" (141:4047), "EventCard_Showtimes" (141:4050) and "EventCard_DropIn" (141:4082).
 * [details] is the ready line under the title. [dropInLabel] marks a long drop-in Event with a
 * dashed card and a tag, so it reads differently from a fixed showtime. With more than one
 * [showtimes], the chips show the times; the selected one uses colour only and can be cleared.
 */
@Composable
fun EventOptionCard(
    title: String,
    details: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dropInLabel: String? = null,
    showtimes: List<ChoiceOption<String>> = emptyList(),
    selectedShowtime: String? = null,
    onShowtimeChange: (String?) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(HackappCorners.Medium)
    val startTimes = stringResource(R.string.selection_start_times)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            dropInLabel?.let { DropInTag(it) }
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(details, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            if (showtimes.size > 1) {
                SingleChoiceChips(
                    options = showtimes,
                    selected = selectedShowtime,
                    onSelectedChange = onShowtimeChange,
                    allowDeselect = true,
                    modifier = Modifier.semantics { contentDescription = startTimes },
                )
            }
        }
    }
    if (dropInLabel != null) {
        val dash = scheme.outline
        Card(
            onClick = onClick,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
            modifier = modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        color = dash,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(HackappCorners.Medium.toPx()),
                        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                    )
                },
            content = body,
        )
    } else {
        OutlinedCard(
            onClick = onClick,
            shape = shape,
            colors = CardDefaults.outlinedCardColors(containerColor = HackappTheme.appColors.card),
            border = BorderStroke(1.dp, scheme.outlineVariant),
            modifier = modifier.fillMaxWidth(),
            content = body,
        )
    }
}

/** Figma "DropInTag" (141:4083). The text wraps inside the pill at large font scales. */
@Composable
private fun DropInTag(label: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .background(scheme.primaryContainer, RoundedCornerShape(HackappCorners.Medium))
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.AllInclusive, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer)
    }
}

/** A restaurant or bar to choose for a Meal Block. */
@Composable
fun MealOptionCard(
    name: String,
    details: String,
    price: PriceLabel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    HorizontalInfoCard(
        title = name,
        subtitle = "$details\u00a0· ${price.text()}",
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    )
}

@Preview(name = "Stop options", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun StopOptionCardsPreview() = HackappTheme {
    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeBlockHeader("18:00", "19:00")
        StopTypeHeader(StopType.Music)
        EventOptionCard("Jazz sur le toit", "17:30 · Salle Rosemont · 20 $", {})
        EventOptionCard(
            title = "Concert de poche",
            details = "Église du Plateau · prix inconnu",
            onClick = {},
            showtimes = listOf("17:15", "17:45", "18:15").map { ChoiceOption(it, it) },
            selectedShowtime = "17:45",
        )
        EventOptionCard(
            title = "Exposition « Lumières du fleuve »",
            details = "Galerie du Mile End · gratuit",
            onClick = {},
            dropInLabel = "Sans heure fixe · 10:00 – 21:00",
        )
        MealOptionCard("Bistro du coin", "Bistro · 300 m", PriceLabel.Unknown, {})
    }
}
