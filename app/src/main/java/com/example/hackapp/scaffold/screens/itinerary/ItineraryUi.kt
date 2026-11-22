package com.example.hackapp.scaffold.screens.itinerary

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.hackapp.R
import com.example.hackapp.ui.components.CardMediaPlaceholder
import com.example.hackapp.ui.components.HorizontalInfoCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun appLocale(): Locale = LocalConfiguration.current.locales[0]

private fun String.capitalized(locale: Locale) = replaceFirstChar { it.titlecase(locale) }

/** "Samedi à Verdun", or the weekday alone when the District is not known yet. */
@Composable
fun itineraryTitle(date: LocalDate, districtName: String?): String {
    val locale = appLocale()
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).capitalized(locale)
    return if (districtName == null) weekday else stringResource(R.string.itinerary_title_in, weekday, districtName)
}

@Composable
fun itineraryShortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(stringResource(R.string.itinerary_date_pattern), appLocale()))

@Composable
fun itineraryFullDate(date: LocalDate): String {
    val locale = appLocale()
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Figma "Horizontal card" as used by HomeScreen and ItinerariesScreen. */
@Composable
fun ItineraryCard(
    card: ItineraryCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = { CardMediaPlaceholder() },
) {
    val parts = buildList {
        add(itineraryShortDate(card.date))
        add(pluralStringResource(R.plurals.stops_count, card.stopCount, card.stopCount))
        card.stampCount?.let { add(pluralStringResource(R.plurals.stamps_count, it, it)) }
    }
    HorizontalInfoCard(
        title = itineraryTitle(card.date, card.districtName),
        subtitle = parts.joinToString("\u00a0· "),
        modifier = modifier,
        onClick = onClick,
        trailing = trailing,
    )
}

@Composable
fun DeleteItineraryDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.itinerary_delete_title)) },
        text = { Text(stringResource(R.string.itinerary_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
