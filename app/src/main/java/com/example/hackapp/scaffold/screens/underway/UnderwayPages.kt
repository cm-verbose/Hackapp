package com.example.hackapp.scaffold.screens.underway

import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.scaffold.screens.itinerary.itineraryShortDate
import com.example.hackapp.ui.components.ArrivalStatusCard
import com.example.hackapp.ui.components.DataAttribution
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.HackappOutlineButton
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappTextActionButton
import com.example.hackapp.ui.components.ItineraryStrip
import com.example.hackapp.ui.components.LateArrivalWarning
import com.example.hackapp.ui.components.LeaveByCard
import com.example.hackapp.ui.components.LegHeader
import com.example.hackapp.ui.components.LegTimes
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.components.NextStopCard
import com.example.hackapp.ui.components.Stamp
import com.example.hackapp.ui.components.StopTypeTag
import com.example.hackapp.ui.components.TransportChoice
import com.example.hackapp.ui.components.TransportChoiceCard
import com.example.hackapp.ui.components.stampInitials
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.underway.StopOutcome

/** The City Pack's travel names, resolved by the Screen. */
class TransportLabels(
    val transitAppName: String,
    val rideHailName: String,
    val fareLabel: String,
    val fareAgency: String,
)

@Composable
private fun skipLabel(stop: UnderwayStopUi) =
    stringResource(if (stop.isMeal) R.string.action_skip_meal else R.string.action_skip_stop)

/** Figma Leg: `30:751`, `159:2539` (with Leave By) and `38:1132` (hero, after the first Stop). */
@Composable
fun UnderwayLegPage(
    title: String,
    ui: LegUi,
    transport: TransportChoice,
    labels: TransportLabels,
    handoffError: String?,
    chrome: UnderwayChrome,
    onTransportChange: (TransportChoice) -> Unit,
    onOpenTransport: () -> Unit,
    onOpenDirections: () -> Unit,
    onSkip: () -> Unit,
    onArrived: () -> Unit,
    walking: Boolean = false,
    onOpenWalking: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val hero = ui.hero
    val actionColor = if (hero != null) HackappTheme.appColors.onHeroCard else MaterialTheme.colorScheme.primary
    val transportRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(handoffError) {
        if (handoffError != null) {
            // The error makes the pinned actions taller: wait for that layout, then show the alternative.
            withFrameNanos { }
            transportRequester.bringIntoView()
        }
    }
    UnderwayActivePage(
        title = title,
        chrome = chrome,
        modifier = modifier,
        hero = hero != null,
        bottomActions = {
            val appName = if (transport == TransportChoice.Transit) labels.transitAppName else labels.rideHailName
            handoffError?.let { HandoffError(it, hero != null) }
            if (walking) {
                // Inside a District the default is a walk: Google Maps opens walking directions.
                HackappPrimaryButton(stringResource(R.string.leg_google_maps_directions), onOpenWalking)
            } else {
                HackappPrimaryButton(stringResource(R.string.leg_open_transit_app, appName), onOpenTransport)
            }
            // With no travel app there is no Arrival page, and this is the only way to earn the Stamp.
            if (handoffError != null) {
                HackappOutlineButton(stringResource(R.string.action_arrived), onArrived, contentColor = actionColor)
            }
            if (!walking && transport == TransportChoice.Transit) {
                HackappTextActionButton(
                    stringResource(R.string.leg_google_maps_directions),
                    onOpenDirections,
                    contentColor = actionColor,
                )
            }
            HackappTextActionButton(skipLabel(ui.stop), onSkip, contentColor = actionColor)
        },
    ) {
        if (hero != null) {
            LegHeader(
                stopName = ui.stop.name,
                stopDetails = ui.stop.districtName?.let { stringResource(R.string.leg_time_district, ui.stop.time, it) }
                    ?: ui.stop.time,
                stopTypeTag = { StopTypeTag(ui.stop.type) },
                legTimes = {
                    LegTimes(
                        departValue = hero.departTime ?: stringResource(R.string.leg_now),
                        travelValue = stringResource(R.string.leg_walk_minutes, hero.travelMinutes),
                        arriveValue = hero.arriveTime,
                    )
                },
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(HackappCorners.Large))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { ItineraryStrip(ui.strip, currentIndex = ui.currentIndex) }
        } else {
            NextStopCard(
                stopName = ui.stop.name,
                stopIndex = ui.stopIndex,
                stopCount = ui.stopCount,
                stopDetails = stringResource(R.string.leg_start_from_location, ui.stop.time),
                modifier = Modifier.fillMaxWidth(),
            )
            ui.leaveBy?.let { LegLeaveBy(it, ui.leaveNow) }
        }
        TransportChoiceCard(
            selected = transport.takeUnless { walking },
            onSelect = onTransportChange,
            rideHailName = labels.rideHailName,
            fareLabel = labels.fareLabel,
            fareAgency = labels.fareAgency,
            modifier = Modifier.bringIntoViewRequester(transportRequester),
        )
    }
}

@Composable
private fun LegLeaveBy(time: String, leaveNow: Boolean) {
    val color = MaterialTheme.colorScheme.onPrimaryContainer
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HackappCorners.ExtraLarge))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(if (leaveNow) R.string.leg_depart else R.string.at_stop_leave_by),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
        Text(
            if (leaveNow) stringResource(R.string.leg_now) else time,
            style = if (leaveNow) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
            color = color,
        )
    }
}

@Composable
private fun HandoffError(message: String, onHero: Boolean) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = if (onHero) HackappTheme.appColors.onHeroCard else MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Figma Arrival: `30:808`, with Changer de transport from `159:2624`. */
@Composable
fun UnderwayArrivalPage(
    ui: ArrivalUi,
    chrome: UnderwayChrome,
    onArrived: () -> Unit,
    onChangeTransport: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnderwayActivePage(
        title = ui.stop.name,
        chrome = chrome,
        modifier = modifier,
        bottomActions = {
            HackappPrimaryButton(stringResource(R.string.action_arrived), onArrived)
            HackappTextActionButton(skipLabel(ui.stop), onSkip)
        },
    ) {
        val time = ui.expectedArrival ?: ui.stop.time
        ArrivalStatusCard(
            expectedArrivalTime = time,
            modifier = Modifier.fillMaxWidth(),
            expectedArrival = stringResource(
                if (ui.expectedArrival != null) R.string.arrival_expected else R.string.leg_start_from_location,
                time,
            ),
        )
        HackappTextActionButton(
            stringResource(R.string.arrival_change_transport),
            onChangeTransport,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** Figma At the Stop: `30:852`, last Stop `159:2674`, late `159:2712`. */
@Composable
fun UnderwayAtStopPage(
    ui: AtStopUi,
    chrome: UnderwayChrome,
    onLeaving: () -> Unit,
    onFinish: () -> Unit,
    onGoAnyway: (String) -> Unit,
    onSkipNext: (UnderwayStopUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    UnderwayActivePage(
        title = ui.stop.name,
        chrome = chrome,
        modifier = modifier,
        bottomActions = {
            if (ui.isLastStop) {
                HackappPrimaryButton(stringResource(R.string.end_of_day_finish), onFinish)
            } else {
                HackappPrimaryButton(stringResource(R.string.action_leaving), onLeaving)
            }
        },
    ) {
        if (ui.isLastStop) {
            Text(
                stringResource(R.string.underway_last_stop),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ui.leaveBy != null && ui.nextStopName != null && ui.nextTransitMinutes != null) {
            LeaveByCard(
                leaveByTime = if (ui.leaveNow) stringResource(R.string.leg_now) else ui.leaveBy,
                nextStopName = ui.nextStopName,
                transitMinutes = ui.nextTransitMinutes,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(if (ui.leaveNow) R.string.leg_depart else R.string.at_stop_leave_by),
                valueStyle = if (ui.leaveNow) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.displayMedium
                },
            )
        }
        ui.lateStop?.let { late ->
            LateArrivalWarning(
                stopName = late.name,
                onGoAnyway = { onGoAnyway(late.id) },
                onSkipStop = { onSkipNext(late) },
                skipStopLabel = skipLabel(late),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Figma Results `160:2756`. The day is over, so it has no overflow menu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnderwayEndOfDayPage(
    ui: EndOfDayUi,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HackappPage(
        title = stringResource(R.string.end_of_day_title),
        modifier = modifier,
        onBack = onDone,
        bottomActions = { HackappPrimaryButton(stringResource(R.string.end_of_day_done), onDone) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.results.forEach { DayResultRow(it) }
        }
        if (ui.newStamps.isNotEmpty()) {
            Text(
                stringResource(R.string.end_of_day_new_stamps_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ui.newStamps.forEach { stamp ->
                    Stamp(
                        initials = stampInitials(stamp.name),
                        stampName = stamp.name,
                        date = itineraryShortDate(stamp.date),
                        district = stamp.districtName,
                        modifier = Modifier.semantics(mergeDescendants = true) {},
                    )
                }
            }
        }
        DataAttribution()
    }
}

@Composable
private fun DayResultRow(result: DayResultUi) {
    val scheme = MaterialTheme.colorScheme
    val visited = result.outcome != StopOutcome.Skipped
    val status = stringResource(if (visited) R.string.end_of_day_status_visited else R.string.stop_status_skipped)
    val stamp = when {
        result.outcome == StopOutcome.TooBrief -> stringResource(R.string.end_of_day_too_brief)
        result.outcome == StopOutcome.Skipped -> stringResource(R.string.end_of_day_no_stamp)
        result.stamp == DayStampUi.New -> stringResource(R.string.end_of_day_stamp_new)
        result.stamp == DayStampUi.AlreadyInPassport -> stringResource(R.string.end_of_day_already_in_passport)
        result.stamp == DayStampUi.None -> stringResource(R.string.end_of_day_no_stamp)
        else -> stringResource(R.string.end_of_day_stamp_pending)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HackappCorners.Medium))
            .background(scheme.surfaceContainer)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(if (visited) scheme.tertiary else scheme.outline, CircleShape),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(result.name, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Text(
                listOfNotNull(status, stamp).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun UnderwayLoadingPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    HackappPage(title = "", modifier = modifier, onBack = onBack) { LoadingState() }
}

@Composable
fun UnderwayErrorPage(onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    HackappPage(title = "", modifier = modifier, onBack = onBack) { ErrorState(onRetry = onRetry) }
}

/** An error that retrying cannot fix: the only action goes Home. */
@Composable
fun UnderwayExitErrorPage(onExit: () -> Unit, modifier: Modifier = Modifier) {
    HackappPage(title = "", modifier = modifier, onBack = onExit) {
        ErrorState(onRetry = onExit, actionLabel = stringResource(R.string.end_of_day_done))
    }
}
