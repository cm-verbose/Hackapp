package com.example.hackapp.scaffold.screens.underway

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.example.hackapp.R
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.passport.PassportStamp
import com.example.hackapp.passport.StopStampResult
import com.example.hackapp.scaffold.screens.itinerary.appLocale
import com.example.hackapp.services.LegEstimator
import com.example.hackapp.services.LegMode
import com.example.hackapp.ui.components.StripStop
import com.example.hackapp.ui.components.TransportChoice
import com.example.hackapp.ui.theme.StopType
import com.example.hackapp.underway.EndOfDay
import com.example.hackapp.underway.StopOutcome
import com.example.hackapp.underway.UnderwayView
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.example.hackapp.underway.TransportChoice as EngineTransport

private val ValueTime = DateTimeFormatter.ofPattern("HH:mm")

@Immutable
data class UnderwayStopUi(
    val id: String,
    val name: String,
    val type: StopType,
    val isMeal: Boolean,
    val time: String,
    val districtName: String?,
)

/** The Leg hero's times: travel and the arrival for the chosen departure. */
@Immutable
data class LegTimesUi(val travelMinutes: Int, val arriveTime: String)

@Immutable
data class LegUi(
    val stop: UnderwayStopUi,
    val stopIndex: Int,
    val stopCount: Int,
    /** The first Leg has no previous Stop and uses the plain layout. */
    val hero: LegTimesUi?,
    val leaveBy: String?,
    val strip: List<StripStop>,
    val currentIndex: Int?,
    /** Leave By has passed: show Départ, Maintenant. */
    val leaveNow: Boolean = false,
    /** The person chose to leave now instead of at [leaveBy]. */
    val departsNow: Boolean = false,
)

@Immutable
data class ArrivalUi(val stop: UnderwayStopUi, val expectedArrival: String?)

@Immutable
data class AtStopUi(
    val stop: UnderwayStopUi,
    val isLastStop: Boolean,
    val leaveBy: String?,
    val nextStopName: String?,
    val nextTransitMinutes: Int?,
    val lateStop: UnderwayStopUi?,
    /** Leave By has passed: show Départ, Maintenant. */
    val leaveNow: Boolean = false,
)

enum class DayStampUi { New, AlreadyInPassport, None, Unavailable }

@Immutable
data class DayResultUi(
    val stopId: String,
    val name: String,
    val outcome: StopOutcome,
    val stamp: DayStampUi,
)

@Immutable
data class NewStampUi(val key: String, val name: String, val date: LocalDate, val districtName: String)

@Immutable
data class EndOfDayUi(val results: List<DayResultUi>, val newStamps: List<NewStampUi>)

/** What the Underway view does not carry: the Itinerary's Stops and their display names. */
class UnderwayStopsInfo(
    val stops: List<Stop>,
    val stopTypes: Map<String, StopType>,
    val districtNames: Map<String, String>,
    val zone: ZoneId,
) {
    fun time(value: OffsetDateTime): String = value.atZoneSameInstant(zone).format(ValueTime)

    fun ui(stop: Stop) = UnderwayStopUi(
        id = stop.id,
        name = stop.name,
        type = stopTypes[stop.id] ?: if (stop.kind == StopKind.Meal) StopType.Meal else StopType.Festival,
        isMeal = stop.kind == StopKind.Meal,
        time = time(stop.start),
        districtName = districtNames[stop.districtId],
    )
}

private val Stop.point get() = LatLng(latitude, longitude)

private fun UnderwayView.isPastLeaveBy(now: Instant) = leaveBy?.toInstant()?.isAfter(now) == false

internal fun EngineTransport.toChoice() = when (this) {
    EngineTransport.Transit, EngineTransport.Walk -> TransportChoice.Transit
    EngineTransport.Uber -> TransportChoice.RideHail
}

internal fun TransportChoice.toEngine() = when (this) {
    TransportChoice.Transit -> EngineTransport.Transit
    TransportChoice.RideHail -> EngineTransport.Uber
}

private fun TransportChoice.legMode() = when (this) {
    TransportChoice.Transit -> LegMode.TRANSIT
    TransportChoice.RideHail -> LegMode.RIDE_HAIL
}

internal fun legUi(
    view: UnderwayView,
    info: UnderwayStopsInfo,
    now: Instant,
    estimator: LegEstimator,
    transport: TransportChoice,
    walking: Boolean = false,
): LegUi? {
    val stop = view.currentStop ?: return null
    val index = info.stops.indexOfFirst { it.id == stop.id }
    val hero = view.legFrom?.let { from ->
        val minutes = estimator.estimate(from.point, stop.point, if (walking) LegMode.WALK else transport.legMode()).travelMinutes
        val leaveAt = view.leaveBy?.toInstant()
        val departs = view.departAt?.toInstant() ?: leaveAt?.takeIf { it.isAfter(now) } ?: now
        LegTimesUi(
            travelMinutes = minutes,
            arriveTime = info.time(departs.plusSeconds(minutes * 60L).atZone(info.zone).toOffsetDateTime()),
        )
    }
    return LegUi(
        stop = info.ui(stop),
        stopIndex = index + 1,
        stopCount = info.stops.size,
        hero = hero,
        leaveBy = view.leaveBy?.let(info::time),
        strip = info.stops.map { StripStop(info.time(it.start), info.ui(it).type) },
        currentIndex = index.takeIf { it >= 0 },
        leaveNow = view.isPastLeaveBy(now),
        departsNow = view.departAt != null,
    )
}

internal fun arrivalUi(view: UnderwayView, info: UnderwayStopsInfo): ArrivalUi? {
    val stop = view.currentStop ?: return null
    return ArrivalUi(info.ui(stop), view.expectedArrival?.let(info::time))
}

internal fun atStopUi(view: UnderwayView, info: UnderwayStopsInfo, now: Instant, estimator: LegEstimator): AtStopUi? {
    val stop = view.currentStop ?: return null
    val next = view.nextStop
    return AtStopUi(
        stop = info.ui(stop),
        isLastStop = view.isLastStop,
        leaveBy = view.leaveBy?.takeIf { next != null }?.let(info::time),
        nextStopName = next?.name,
        nextTransitMinutes = next?.let { estimator.estimate(stop.point, it.point, LegMode.TRANSIT).travelMinutes },
        lateStop = view.lateWarningStop?.let(info::ui),
        leaveNow = view.isPastLeaveBy(now),
    )
}

internal fun endOfDayUi(day: EndOfDay, districtNames: Map<String, String>): EndOfDayUi {
    val newStamps = day.results.mapNotNull { (it.stamp as? StopStampResult.NewStamp)?.stamp }
    return EndOfDayUi(
        results = day.results.map { result ->
            DayResultUi(
                stopId = result.stop.id,
                name = result.stop.name,
                outcome = result.outcome,
                stamp = when (result.stamp) {
                    is StopStampResult.NewStamp -> DayStampUi.New
                    is StopStampResult.AlreadyInPassport -> DayStampUi.AlreadyInPassport
                    StopStampResult.NoStamp -> DayStampUi.None
                    null -> DayStampUi.Unavailable
                },
            )
        },
        newStamps = newStamps.map { it.toUi(districtNames) },
    )
}

private fun PassportStamp.toUi(districtNames: Map<String, String>) =
    NewStampUi(key, name, date, districtNames[districtId].orEmpty())

/** The simulated date and time on the demo control: `sam. 28 nov. · 18:42`. */
@Composable
internal fun demoDateTime(at: Instant, zone: ZoneId): String {
    val local = at.atZone(zone)
    val datePattern = "EEE ${stringResource(R.string.itinerary_date_pattern)}"
    val date = local.format(DateTimeFormatter.ofPattern(datePattern, appLocale()))
    return stringResource(R.string.demo_date_time, date, local.format(ValueTime))
}
