package com.example.hackapp.scaffold.screens.underway

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.domain.FlowViewModel
import com.example.hackapp.domain.StopStatus
import com.example.hackapp.domain.UnderwayProgress
import com.example.hackapp.domain.durationMinutes
import com.example.hackapp.services.HandoffDestination
import com.example.hackapp.services.LegMode
import com.example.hackapp.ui.components.ArrivalStatusCard
import com.example.hackapp.ui.components.EmptyState
import com.example.hackapp.ui.components.HackappOutlineButton
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappTextActionButton
import com.example.hackapp.ui.components.HeroHeader
import com.example.hackapp.ui.components.InfoCallout
import com.example.hackapp.ui.components.LateArrivalWarning
import com.example.hackapp.ui.components.LeaveByCard
import com.example.hackapp.ui.components.LegHeader
import com.example.hackapp.ui.components.LegTimes
import com.example.hackapp.ui.components.NextStopCard
import com.example.hackapp.ui.components.Stamp
import com.example.hackapp.ui.components.StopTypeTag
import com.example.hackapp.ui.components.TransportChoice
import com.example.hackapp.ui.components.TransportChoiceCard
import com.example.hackapp.ui.components.stampInitials
import com.example.hackapp.ui.motion.entrance
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Where the Underway pages are in the flow, from the persisted progress. */
fun UnderwayProgress.hasNext() = index + 1 < itinerary.stops.size

@Composable
private fun NoItinerary(onBack: () -> Unit) {
    HackappPage(title = stringResource(R.string.leg_next_stop_label), onBack = onBack) {
        EmptyState(title = stringResource(R.string.state_empty_title))
    }
}

/** Last known position, if the person allowed location. */
@SuppressLint("MissingPermission")
private fun lastKnown(context: Context): LatLng? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .firstNotNullOfOrNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        ?.let { LatLng(it.latitude, it.longitude) }
}

/** 1/4: how to get to the next Stop; the first leg starts from the current position when allowed. */
@Composable
fun ScreenUnderwayLeg(vm: FlowViewModel, onBack: () -> Unit, onArrive: () -> Unit, onEnd: () -> Unit) {
    val progress by vm.underway.collectAsState()
    val p = progress ?: return NoItinerary(onBack)
    val context = LocalContext.current
    val services = vm.services
    val stop = p.current
    LaunchedEffect(p.finished) { if (p.finished) onEnd() }
    if (stop == null) return

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) lastKnown(context)?.let { vm.setStartFrom(it.lat, it.lng) }
    }
    LaunchedEffect(p.index == 0 && p.fromLat == null) {
        if (p.index == 0 && p.fromLat == null) askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    val from: LatLng? = if (p.index == 0) p.fromLat?.let { lat -> p.fromLng?.let { LatLng(lat, it) } }
    else p.itinerary.stops[p.index - 1].location
    val mode = if (p.useRideHail) LegMode.RIDE_HAIL else LegMode.TRANSIT
    val estimate = remember(from, stop.id, mode) { from?.let { f -> stop.location?.let { services.legs.estimate(f, it, mode) } } }
    val depart = if (p.index == 0) LocalTime.now(services.cityPack.timeZone) else p.itinerary.stops[p.index - 1].departure.toLocalTime()
    val destination = stop.location?.let { HandoffDestination(stop.title, it) }
    val language = context.resources.configuration.locales[0].language

    HackappPage(
        title = stringResource(R.string.leg_next_stop_label),
        onBack = onBack,
        bottomActions = {
            HackappPrimaryButton(stringResource(R.string.action_continue), onArrive)
            destination?.let { d ->
                if (p.useRideHail) {
                    HackappOutlineButton("${services.rideHail.appName}", { services.rideHail.requestRide(d) })
                } else {
                    HackappOutlineButton(stringResource(R.string.leg_open_transit_app, services.transit.appName), { services.transit.openTransitApp(d) })
                    HackappTextActionButton(stringResource(R.string.leg_google_maps_directions), { services.transit.openDirections(d) })
                }
            }
        },
    ) {
        LegHeader(
            stopName = stop.title,
            stopDetails = stop.details.orEmpty(),
            stopTypeTag = { StopTypeTag(stop.type) },
            legTimes = {
                LegTimes(
                    departValue = if (p.index == 0) stringResource(R.string.leg_now) else depart.format(HHMM),
                    travelValue = estimate?.let { "${it.totalMinutes} min" } ?: "–",
                    arriveValue = estimate?.let { depart.plusMinutes(it.totalMinutes.toLong()).format(HHMM) } ?: stop.start.format(HHMM),
                )
            },
            modifier = Modifier.entrance(0),
        )
        Text(stringResource(R.string.leg_how_to_get_there), style = MaterialTheme.typography.titleMedium)
        TransportChoiceCard(
            selected = if (p.useRideHail) TransportChoice.RideHail else TransportChoice.Transit,
            onSelect = { vm.setRideHail(it == TransportChoice.RideHail) },
            rideHailName = services.rideHail.appName,
            fareLabel = services.fares.fareForLeg().label.get(language).orEmpty(),
            modifier = Modifier.entrance(1),
        )
        if (p.index == 0 && p.fromLat != null) {
            Text(stringResource(R.string.leg_start_from_location, depart.format(HHMM)), style = MaterialTheme.typography.bodySmall)
        }
        NextStopCard(stop.title, p.index + 1, p.itinerary.stops.size, stop.details.orEmpty(), Modifier.entrance(2))
    }
}

/** 2/4: travelling. The person confirms arrival; there is no GPS detection. */
@Composable
fun ScreenUnderwayArrival(vm: FlowViewModel, onBack: () -> Unit, onAtStop: () -> Unit, onSkipped: (finished: Boolean) -> Unit) {
    val progress by vm.underway.collectAsState()
    val p = progress ?: return NoItinerary(onBack)
    val stop = p.current ?: return NoItinerary(onBack)
    HackappPage(
        title = stringResource(R.string.arrival_en_route),
        onBack = onBack,
        bottomActions = {
            HackappPrimaryButton(stringResource(R.string.action_arrived), { vm.arrived(); onAtStop() })
            HackappTextActionButton(stringResource(R.string.action_skip_stop), {
                vm.finishStop(StopStatus.SKIPPED)
                onSkipped(!p.hasNext())
            })
        },
    ) {
        NextStopCard(stop.title, p.index + 1, p.itinerary.stops.size, stop.details.orEmpty(), Modifier.entrance(0))
        ArrivalStatusCard(expectedArrivalTime = stop.start.format(HHMM), modifier = Modifier.entrance(1))
        InfoCallout(stringResource(R.string.arrival_reminder_note))
    }
}

/** 3/4: at the Stop. "Leaving" confirms it; a late next Stop gets a warning, never a re-plan. */
@Composable
fun ScreenUnderwayAtStop(vm: FlowViewModel, onBack: () -> Unit, onLeft: (finished: Boolean) -> Unit) {
    val progress by vm.underway.collectAsState()
    val p = progress ?: return NoItinerary(onBack)
    val stop = p.current ?: return NoItinerary(onBack)
    val next = p.itinerary.stops.getOrNull(p.index + 1)
    val zone = vm.services.cityPack.timeZone
    val walk = remember(stop.id) {
        val to = next?.location
        val from = stop.location
        if (from != null && to != null) vm.services.legs.estimate(from, to, LegMode.WALK).travelMinutes else 0
    }
    val leaveBy = stop.departure
    // Late = the next Stop cannot be reached on time if the person left right now.
    val late = next != null && LocalDateTime.now(zone).plusMinutes(walk.toLong()).isAfter(next.start)

    fun leave(skipNext: Boolean) {
        if (skipNext) vm.leaveAndSkipNext() else vm.finishStop(StopStatus.CONFIRMED)
        onLeft(!p.hasNext() || skipNext && p.index + 2 >= p.itinerary.stops.size)
    }

    HackappPage(
        title = stop.title,
        onBack = onBack,
        bottomActions = {
            HackappPrimaryButton(stringResource(R.string.action_leaving), { leave(false) })
        },
    ) {
        NextStopCard(stop.title, p.index + 1, p.itinerary.stops.size, stop.details.orEmpty(), Modifier.entrance(0))
        if (next != null) {
            LeaveByCard(leaveBy.format(HHMM), next.title, walk, Modifier.entrance(1))
            InfoCallout(stringResource(R.string.at_stop_alert_note))
            if (late) LateArrivalWarning(next.title, onGoAnyway = { leave(false) }, onSkipStop = { leave(true) })
        }
    }
}

/** 4/4: summary and the Stamps earned (confirmed Stops only). */
@Composable
fun ScreenUnderwayEndOfDay(vm: FlowViewModel, onDone: () -> Unit) {
    val progress by vm.underway.collectAsState()
    val p = progress
    val language = LocalContext.current.resources.configuration.locales[0].language
    HackappPage(
        title = stringResource(R.string.end_of_day_title),
        bottomActions = {
            HackappPrimaryButton(stringResource(R.string.end_of_day_finish), { vm.endDay(onDone) })
        },
    ) {
        if (p == null) {
            EmptyState(title = stringResource(R.string.end_of_day_no_stamp))
            return@HackappPage
        }
        val confirmed = p.statuses.count { it == StopStatus.CONFIRMED }
        val skipped = p.statuses.count { it == StopStatus.SKIPPED }
        val night = (p.itinerary.stops.lastOrNull()?.start?.hour ?: 0).let { it >= 18 || it < 4 }
        HeroHeader(
            title = stringResource(if (night) R.string.end_of_day_hero_night else R.string.end_of_day_hero_day),
            subtitle = pluralStringResource(R.plurals.stops_confirmed, confirmed, confirmed) + " · " +
                pluralStringResource(R.plurals.stops_skipped, skipped, skipped),
            modifier = Modifier.entrance(0),
        )
        val stamps = vm.newStamps(p)
        Text(stringResource(R.string.end_of_day_new_stamps_section), style = MaterialTheme.typography.titleMedium)
        if (stamps.isEmpty()) {
            Text(stringResource(R.string.end_of_day_no_stamp), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(pluralStringResource(R.plurals.new_stamps_in_passport, stamps.size, stamps.size), style = MaterialTheme.typography.bodyMedium)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                stamps.forEachIndexed { i, s ->
                    Stamp(stampInitials(s.name), s.name, s.date, s.district, Modifier.entrance(i + 1, stepMillis = 90))
                }
            }
        }
        InfoCallout(stringResource(R.string.end_of_day_confirmed_only))
    }
}
