package com.example.hackapp.underway

import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.passport.StopStampResult
import com.example.hackapp.services.LegEstimator
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.time.ZoneId

/* Constant names are stored in saved state: renaming one is a data migration. */

@Serializable
enum class UnderwayPhase { Leg, Arrival, AtStop, Ended }

@Serializable
enum class TransportChoice { Transit, Uber, Walk }

@Serializable
enum class EndReason { Finished, EndedEarly, Automatic, Replaced }

@Serializable
enum class StopOutcome { Visited, Skipped, TooBrief }

/** What happened at one Stop, in epoch milliseconds. */
@Serializable
data class StopRecord(
    val arrivedAtMillis: Long? = null,
    val leftAtMillis: Long? = null,
    val skippedAtMillis: Long? = null,
    /** The first user action after arriving. Ends the stay when the person never taps leaving. */
    val nextActionAtMillis: Long? = null,
)

@Serializable
data class UnderwayState(
    val itineraryId: String,
    val ownerUid: String,
    /** Stops in visit order, fixed at Start. */
    val stopOrder: List<String>,
    val phase: UnderwayPhase,
    /** The Stop being travelled to or attended. The last one visited once Ended. */
    val currentStopId: String?,
    val transport: TransportChoice = TransportChoice.Transit,
    val startedAtMillis: Long,
    val originLat: Double? = null,
    val originLng: Double? = null,
    val records: Map<String, StopRecord> = emptyMap(),
    val firedAlerts: Set<String> = emptySet(),
    val dismissedLateWarnings: Set<String> = emptySet(),
    val bannerHandled: Boolean = false,
    val endReason: EndReason? = null,
    val endedAtMillis: Long? = null,
    val outcomes: Map<String, StopOutcome> = emptyMap(),
    val stampsAwarded: Boolean = false,
    /** When transit or Uber was opened for the current Leg. Null on the Leg and in older saved state. */
    val handoffAtMillis: Long? = null,
    /** Started in demo mode: reads simulated time and never schedules a real alarm. */
    val demoRun: Boolean = false,
    /** The District the first Leg starts from (the Profile's District of stay), when known. */
    val originDistrictId: String? = null,
    /** True once the person picked a way to travel for this Leg: the default mode no longer applies. */
    val legTransportChosen: Boolean = false,
    /** The person chose to leave now for this Leg, at this moment. Null means the planned Leave By. */
    val legDepartAtMillis: Long? = null,
)

/** Everything the engine needs besides the state. The Itinerary is read-only here. */
class UnderwayContext(
    val itinerary: Itinerary,
    val estimator: LegEstimator,
    val zone: ZoneId,
) {
    private val byId = itinerary.stops.associateBy { it.id }
    fun stop(id: String): Stop? = byId[id]
}

sealed interface UnderwayEvent {
    data class OpenTransport(val choice: TransportChoice, val stopId: String? = null) : UnderwayEvent
    data object ReturnToLeg : UnderwayEvent
    /** On the Leg: leave now ([now] true) or at the planned Leave By. */
    data class ChooseDeparture(val now: Boolean, val stopId: String? = null) : UnderwayEvent
    data class ConfirmArrival(val stopId: String? = null) : UnderwayEvent
    data class Leaving(val stopId: String? = null) : UnderwayEvent
    /** On Leg and Arrival the current Stop. At the Stop, the next Stop. */
    data class SkipStop(val stopId: String? = null, val fromNotification: Boolean = false) : UnderwayEvent
    data class GoAnyway(val stopId: String) : UnderwayEvent
    data class EndItinerary(val confirmed: Boolean = false) : UnderwayEvent
    data object NotificationBannerHandled : UnderwayEvent
    /** Time passed: fire due alerts and the automatic end. Not a user action. */
    data object Tick : UnderwayEvent
    /** Starting another Itinerary ends this one. */
    data object EndForReplacement : UnderwayEvent
}

enum class Rejection { NeedsConfirmation, StaleStop, WrongPhase, Ended }

enum class AlertKind { ArrivalReminder, LeaveBy, LateWarning, AutoEnd }

/** A notification the engine wants shown, and the time it is due. [id] is `<kind>:<stopId>`. */
data class PlannedAlert(
    val id: String,
    val kind: AlertKind,
    val at: java.time.Instant,
    val highPriority: Boolean,
    /** The Stop the alert is about. For a late warning, the Stop that may be missed. */
    val stopId: String,
    val stopName: String,
    val stopStart: OffsetDateTime,
    val leaveBy: OffsetDateTime? = null,
    val nextStopName: String? = null,
    /** The Stop that Skip this Stop skips from the notification. */
    val skipStopId: String? = null,
    /** The Stop that Skip skips is a Meal: the action reads "Skip this Meal". */
    val isMeal: Boolean = false,
)

sealed interface UnderwayEffect {
    data class Notify(val alert: PlannedAlert) : UnderwayEffect
    /** An action was taken: earlier notifications are out of date. */
    data object ClearNotifications : UnderwayEffect
    data object DayEnded : UnderwayEffect
}

data class Transition(
    val state: UnderwayState,
    val effects: List<UnderwayEffect> = emptyList(),
    val rejection: Rejection? = null,
)

/** What pages draw. Derived from the state, the Itinerary and the time. */
data class UnderwayView(
    val itineraryId: String,
    val phase: UnderwayPhase,
    val currentStop: Stop?,
    val legFrom: Stop?,
    val expectedArrival: OffsetDateTime?,
    val leaveBy: OffsetDateTime?,
    /** On the Leg: when the person chose to leave now. Null means they leave at [leaveBy]. */
    val departAt: OffsetDateTime? = null,
    val nextStop: Stop?,
    val isLastStop: Boolean,
    val transport: TransportChoice,
    /** The Leg is walking: the default inside a District, or a chosen walk. Never true on a Leg with no start point. */
    val walking: Boolean,
    val remainingStops: Int,
    val lateWarningStop: Stop?,
    val endNeedsConfirmation: Boolean,
    val showNotificationBanner: Boolean,
    val endedReason: EndReason?,
)

data class StopDayResult(
    val stop: Stop,
    val outcome: StopOutcome,
    /** Null when the Passport was unavailable. */
    val stamp: StopStampResult?,
    val arrivedLateMinutes: Long?,
)

data class EndOfDay(
    val itineraryId: String,
    val reason: EndReason,
    val results: List<StopDayResult>,
    val stampsAvailable: Boolean,
)
