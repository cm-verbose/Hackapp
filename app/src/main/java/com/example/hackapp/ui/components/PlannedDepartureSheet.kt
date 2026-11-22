package com.example.hackapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.scaffold.screens.itinerary.eventStopType
import com.example.hackapp.scaffold.screens.planning.DepartureError
import com.example.hackapp.scaffold.screens.planning.PlannedDepartureUiState
import com.example.hackapp.ui.theme.HackappTheme
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val ClockFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Figma "PlannedDepartureSheet" (141:2614): asks when the person will leave an Event that
 * publishes no end time. Open it only when `!state.hasPublishedEnd`. [venueName] is the Event's
 * venue for the subtitle; the Event type label stands in when it is unknown.
 * [onTimeChange] feeds `PlannedDepartureHolder.setTime`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedDepartureSheet(
    state: PlannedDepartureUiState,
    venueName: String?,
    onTimeChange: (LocalTime) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PlannedDepartureContent(
            state = state,
            venueName = venueName,
            onTimeChange = onTimeChange,
            onConfirm = onConfirm,
            onCancel = onDismiss,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

/** Sheet body without the ModalBottomSheet, for previews. */
@Composable
fun PlannedDepartureContent(
    state: PlannedDepartureUiState,
    venueName: String?,
    onTimeChange: (LocalTime) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shown = state.rejectedTime ?: state.departure.toLocalTime()

    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.planned_departure_title), style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
            Text(
                stringResource(
                    R.string.planned_departure_subtitle,
                    state.eventTitle,
                    venueName ?: stopTypeLabel(eventStopType(state.eventType)),
                    state.start.format(ClockFormat),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }

        Text(
            stringResource(R.string.planned_departure_no_end, formatDurationMinutes(state.typicalStayMinutes)),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
        )

        HackappTimeWheels(
            hour = shown.hour,
            minute = shown.minute,
            onHourChange = { onTimeChange(LocalTime.of(it, shown.minute)) },
            onMinuteChange = { onTimeChange(LocalTime.of(shown.hour, it)) },
            modifier = Modifier.padding(vertical = 4.dp),
        )

        state.error?.let { error ->
            Text(
                when (error) {
                    DepartureError.OutsideWindow -> stringResource(
                        R.string.planned_departure_error_outside_window,
                        formatSentenceTime(state.latestDeparture.toLocalTime()),
                    )
                    DepartureError.NotAfterStart -> stringResource(R.string.planned_departure_error_not_after_start)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            HackappIconView(HackappIcon.Schedule, tint = scheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.planned_departure_duration, formatDurationMinutes(state.stayMinutes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                if (state.crossesMidnight) {
                    Text(
                        stringResource(R.string.planned_departure_next_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        HackappPrimaryButton(
            text = stringResource(R.string.action_confirm),
            onClick = onConfirm,
            enabled = state.canConfirm,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        HackappSmallTextButton(
            text = stringResource(R.string.action_cancel),
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

// Previews

private val PreviewZone: ZoneId = ZoneId.of("America/Toronto")

private fun previewState(
    stayMinutes: Int = 120,
    error: DepartureError? = null,
    rejectedTime: LocalTime? = null,
    start: ZonedDateTime = ZonedDateTime.of(2026, 11, 28, 14, 0, 0, 0, PreviewZone),
    windowEnd: ZonedDateTime = ZonedDateTime.of(2026, 11, 28, 23, 30, 0, 0, PreviewZone),
): PlannedDepartureUiState {
    val departure = start.plusMinutes(stayMinutes.toLong())
    return PlannedDepartureUiState(
        eventId = "monet",
        eventTitle = "Exposition Monet",
        eventType = "exhibition",
        start = start,
        latestDeparture = windowEnd,
        typicalStayMinutes = 120,
        departure = departure,
        stayMinutes = stayMinutes,
        hasPublishedEnd = false,
        isTypicalStay = stayMinutes == 120,
        crossesMidnight = departure.toLocalDate() != start.toLocalDate(),
        rejectedTime = rejectedTime,
        error = error,
    )
}

@Composable
private fun PlannedDeparturePreview(state: PlannedDepartureUiState, venueName: String? = "Musée des beaux-arts") = HackappTheme {
    PlannedDepartureContent(
        state = state,
        venueName = venueName,
        onTimeChange = {},
        onConfirm = {},
        onCancel = {},
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Preview(name = "PlannedDeparture · typical stay", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun PlannedDepartureTypicalPreview() = PlannedDeparturePreview(previewState())

@Preview(name = "PlannedDeparture · changed, no venue", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun PlannedDepartureChangedPreview() = PlannedDeparturePreview(previewState(stayMinutes = 90), venueName = null)

@Preview(name = "PlannedDeparture · outside window", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun PlannedDepartureOutsideWindowPreview() =
    PlannedDeparturePreview(previewState(error = DepartureError.OutsideWindow, rejectedTime = LocalTime.of(23, 45)))

@Preview(name = "PlannedDeparture · after midnight", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun PlannedDepartureAfterMidnightPreview() = PlannedDeparturePreview(
    previewState(
        stayMinutes = 150,
        start = ZonedDateTime.of(2026, 11, 28, 23, 0, 0, 0, PreviewZone),
        windowEnd = ZonedDateTime.of(2026, 11, 29, 3, 0, 0, 0, PreviewZone),
    ),
)

@Preview(name = "PlannedDeparture · not after start", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun PlannedDepartureNotAfterStartPreview() =
    PlannedDeparturePreview(previewState(error = DepartureError.NotAfterStart))

@Preview(name = "PlannedDeparture · 360 dp, 1.3x", showBackground = true, widthDp = 360, fontScale = 1.3f)
@Composable
private fun PlannedDepartureLargeTextPreview() = PlannedDeparturePreview(previewState())
