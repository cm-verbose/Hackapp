package com.example.hackapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun LocalDate.toPickerMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toPickerDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Date field limited to [firstDate]..[lastDate], the range covered by the event data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HackappDateField(
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit,
    label: String,
    firstDate: LocalDate,
    lastDate: LocalDate,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale) }
    HackappPickerField(value.format(format), label, onClick = { open = true }, modifier = modifier)

    if (open) {
        val selectable = remember(firstDate, lastDate) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis.toPickerDate().let { it >= firstDate && it <= lastDate }

                override fun isSelectableYear(year: Int): Boolean = year in firstDate.year..lastDate.year
            }
        }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.toPickerMillis(),
            yearRange = firstDate.year..lastDate.year,
            selectableDates = selectable,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { onValueChange(it.toPickerDate()) }
                        open = false
                    },
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = state) }
    }
}

/** 24-hour time field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HackappTimeField(
    value: LocalTime,
    onValueChange: (LocalTime) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    HackappPickerField(value.format(TimeFormat), label, onClick = { open = true }, modifier = modifier)

    if (open) {
        val state = rememberTimePickerState(value.hour, value.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(LocalTime.of(state.hour, state.minute))
                    open = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Asks when a person will leave an Event that publishes no end time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedDepartureSheet(
    eventTitle: String,
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialTime.hour, initialTime.minute, is24Hour = true)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.planned_departure_title), style = MaterialTheme.typography.titleLarge)
            Text(eventTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TimePicker(state = state)
            HackappPrimaryButton(
                text = stringResource(R.string.action_confirm),
                onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) },
            )
        }
    }
}

@Preview(name = "Date and time fields", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun DateTimeFieldsPreview() = HackappTheme {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HackappDateField(
            value = LocalDate.of(2026, 11, 28),
            onValueChange = {},
            label = stringResource(R.string.planning_date),
            firstDate = LocalDate.of(2026, 11, 21),
            lastDate = LocalDate.of(2026, 12, 6),
        )
        HackappTimeField(LocalTime.of(18, 0), {}, stringResource(R.string.planning_start))
    }
}
