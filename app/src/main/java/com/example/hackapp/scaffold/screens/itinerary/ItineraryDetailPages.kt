package com.example.hackapp.scaffold.screens.itinerary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.DistrictMapData
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.citypack.rememberDistrictMap
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.LocalItineraryRepository
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.StopStatus
import com.example.hackapp.scaffold.screens.onboarding.LoadState
import com.example.hackapp.services.LocalCityServices
import com.example.hackapp.ui.components.DataAttribution
import com.example.hackapp.ui.components.EmptyState
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.HackappOutlineButton
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.InfoCallout
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.components.Stamp
import com.example.hackapp.ui.components.StopRow
import com.example.hackapp.ui.components.map.ItineraryRouteMap
import com.example.hackapp.ui.components.map.RouteStop
import com.example.hackapp.ui.components.stampInitials
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.ui.theme.StopType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val WindowTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun rememberDetailViewModel(itineraryId: String, phase: ItineraryPhase): ItineraryDetailViewModel {
    val context = LocalContext.current.applicationContext
    val cityPack = LocalCityPack.current
    val auth = LocalAuthService.current
    val repository = LocalItineraryRepository.current
    val events = LocalCityServices.current.events
    return viewModel(
        factory = ItineraryDetailViewModel.factory(
            itineraryId = itineraryId,
            expectedPhase = phase,
            uid = auth.currentUser?.uid,
            itineraries = repository,
            events = events,
            zone = cityPack.timeZone,
            loadDistrictNames = { loadDistrictNames(context, cityPack) },
        ),
    )
}

@Composable
fun ScreenPastItinerary(itineraryId: String, onBack: () -> Unit) {
    val viewModel = rememberDetailViewModel(itineraryId, ItineraryPhase.Completed)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val map by rememberDistrictMap()
    PastItineraryPage(state = state, map = map, onBack = onBack, onRetry = viewModel::load)
}

@Composable
fun ScreenArchivedItinerary(itineraryId: String, onBack: () -> Unit, onChooseNewDate: () -> Unit) {
    val viewModel = rememberDetailViewModel(itineraryId, ItineraryPhase.Archived)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val map by rememberDistrictMap()
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    ArchivedItineraryPage(
        state = state,
        map = map,
        onBack = onBack,
        onRetry = viewModel::load,
        onChooseNewDate = onChooseNewDate,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete,
    )
}

/** Figma PastItinerarySummaryScreen (130:1643). Read-only: no edit or restart action. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PastItineraryPage(
    state: ItineraryDetailUiState,
    map: DistrictMapData?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val detail = (state.detail as? LoadState.Ready)?.value
    HackappPage(title = detailTitle(detail), onBack = onBack) {
        DetailBody(state.detail, onRetry) { loaded ->
            DateLine(loaded)
            RouteMap(map, loaded.routeStops)
            loaded.stops.forEach { StatusStopRow(it, showStatus = true) }
            SectionLabel(stringResource(R.string.past_stamps_earned))
            if (loaded.stamps.isEmpty()) {
                Text(
                    stringResource(R.string.past_stamps_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val date = itineraryShortDate(loaded.date)
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    loaded.stamps.forEach { stamp ->
                        Stamp(
                            initials = stampInitials(stamp.name),
                            stampName = stamp.name,
                            date = date,
                            district = stamp.districtName,
                        )
                    }
                }
            }
            DataAttribution()
        }
    }
}

/** Figma ArchivedItineraryScreen (130:1760). */
@Composable
fun ArchivedItineraryPage(
    state: ItineraryDetailUiState,
    map: DistrictMapData?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onChooseNewDate: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    val detail = (state.detail as? LoadState.Ready)?.value
    HackappPage(
        title = detailTitle(detail),
        onBack = onBack,
        bottomActions = if (detail == null) null else {
            {
                HackappPrimaryButton(stringResource(R.string.archived_choose_new_date), onChooseNewDate)
                HackappOutlineButton(stringResource(R.string.action_delete), onRequestDelete)
            }
        },
    ) {
        DetailBody(state.detail, onRetry) { loaded ->
            DateLine(loaded)
            InfoCallout(stringResource(R.string.itineraries_archived_note))
            if (state.deleteFailed) InfoCallout(stringResource(R.string.itinerary_delete_error))
            RouteMap(map, loaded.routeStops)
            loaded.stops.forEach { StatusStopRow(it, showStatus = false) }
            DataAttribution()
        }
    }
    if (state.confirmingDelete) DeleteItineraryDialog(onConfirm = onConfirmDelete, onDismiss = onDismissDelete)
}

@Composable
private fun detailTitle(detail: ItineraryDetail?): String =
    detail?.let { itineraryTitle(it.date, it.districtName) } ?: stringResource(R.string.nav_itineraries)

@Composable
private fun ColumnScope.DetailBody(
    detail: LoadState<ItineraryDetail?>,
    onRetry: () -> Unit,
    content: @Composable ColumnScope.(ItineraryDetail) -> Unit,
) {
    when (detail) {
        LoadState.Loading -> LoadingState()
        LoadState.Failed -> ErrorState(onRetry = onRetry)
        is LoadState.Ready -> {
            val loaded = detail.value
            if (loaded == null) EmptyState(title = stringResource(R.string.itinerary_not_found)) else content(loaded)
        }
    }
}

@Composable
private fun DateLine(detail: ItineraryDetail) {
    SectionLabel(
        stringResource(
            R.string.itinerary_date_window,
            itineraryFullDate(detail.date),
            detail.startTime.format(WindowTimeFormat),
            detail.endTime.format(WindowTimeFormat),
        ),
    )
}

@Composable
private fun RouteMap(map: DistrictMapData?, stops: List<RouteStop>) {
    if (map != null && stops.isNotEmpty()) ItineraryRouteMap(map = map, stops = stops)
}

@Composable
private fun StatusStopRow(stop: DetailStop, showStatus: Boolean) {
    val kind = stringResource(if (stop.kind == StopKind.Meal) R.string.stop_kind_meal else R.string.stop_kind_event)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StopRow(
            time = stop.time,
            stopName = stop.name,
            details = "$kind\u00a0· ${stop.districtName}",
            modifier = Modifier.weight(1f),
        )
        if (showStatus) {
            when (stop.status) {
                StopStatus.Confirmed -> StatusLabel(
                    stringResource(R.string.stop_status_confirmed),
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
                StopStatus.Skipped -> StatusLabel(
                    stringResource(R.string.stop_status_skipped),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StopStatus.Planned -> Unit
            }
        }
    }
}

@Composable
private fun StatusLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = color)
}

private val PreviewDetail = ItineraryDetail(
    date = LocalDate.of(2026, 11, 28),
    startTime = LocalTime.of(18, 30),
    endTime = LocalTime.of(23, 59),
    districtName = "Le Plateau",
    stops = listOf(
        DetailStop("1", "18:30", "Jazz sur le toit", StopKind.Event, "Le Plateau", StopStatus.Confirmed),
        DetailStop("2", "19:30", "Bistro du coin", StopKind.Meal, "Le Plateau", StopStatus.Confirmed),
        DetailStop("3", "21:00", "Spectacle d’humour", StopKind.Event, "Ville-Marie", StopStatus.Skipped),
    ),
    routeStops = listOf(RouteStop(1, "a", StopType.Music), RouteStop(2, "a", StopType.Meal)),
    stamps = listOf(
        DetailStamp("1", "Jazz sur le toit", "Le Plateau"),
        DetailStamp("2", "Bistro du coin", "Le Plateau"),
    ),
)

@Preview(name = "Past Itinerary", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun PastItineraryPreview() = HackappTheme {
    PastItineraryPage(ItineraryDetailUiState(detail = LoadState.Ready(PreviewDetail)), null, {}, {})
}

@Preview(name = "Archived Itinerary", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ArchivedItineraryPreview() = HackappTheme {
    ArchivedItineraryPage(
        state = ItineraryDetailUiState(detail = LoadState.Ready(PreviewDetail)),
        map = null,
        onBack = {},
        onRetry = {},
        onChooseNewDate = {},
        onRequestDelete = {},
        onConfirmDelete = {},
        onDismissDelete = {},
    )
}

@Preview(name = "Itinerary not found", showBackground = true, widthDp = 412, heightDp = 400)
@Composable
private fun ItineraryNotFoundPreview() = HackappTheme {
    PastItineraryPage(ItineraryDetailUiState(detail = LoadState.Ready(null)), null, {}, {})
}
