package com.example.hackapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
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
 * An Event to choose. [endTime] null shows "fin non publiée", because the person then sets a
 * Planned Departure.
 */
@Composable
fun EventOptionCard(
    title: String,
    startTime: String,
    endTime: String?,
    price: PriceLabel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val time = endTime?.let { stringResource(R.string.selection_time_block, startTime, it) } ?: startTime
    val parts = buildList {
        add(time)
        add(price.text())
    }
    HorizontalInfoCard(
        title = title,
        subtitle = parts.joinToString(" · "),
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    )
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
        subtitle = "$details · ${price.text()}",
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
        EventOptionCard("Jazz sur le toit", "18:30", null, PriceLabel.Amount("20 $"), {})
        EventOptionCard("Vernissage, galerie du Mile End", "18:00", "21:00", PriceLabel.Free, {}, selected = true)
        MealOptionCard("Bistro du coin", "Bistro · 300 m", PriceLabel.Unknown, {})
    }
}
