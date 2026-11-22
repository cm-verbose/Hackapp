package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.data.CostStatus
import com.example.hackapp.data.Event
import com.example.hackapp.itinerary.MAX_TICKET_PRICE_CAD
import com.example.hackapp.itinerary.MIN_TICKET_PRICE_CAD
import com.example.hackapp.itinerary.ReachableShowtime
import com.example.hackapp.itinerary.ShowtimeKey
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.TicketPriceRepository
import com.example.hackapp.itinerary.TicketPriceSummary
import com.example.hackapp.itinerary.eventStop
import com.example.hackapp.itinerary.isValidTicketPrice
import com.example.hackapp.itinerary.publishedEnd
import com.example.hackapp.itinerary.showtimeKey
import com.example.hackapp.itinerary.startTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.ZoneId
import java.time.ZonedDateTime

/** What the price line shows. Reports never carry who submitted them. */
sealed interface EventPrice {
    data object Free : EventPrice

    /** Label it as the median of user-reported prices. */
    data class UserMedian(val medianCad: Double, val reportCount: Int) : EventPrice

    /** A price the source published, used only when there are no user reports. */
    data class Published(val minCad: Double?, val maxCad: Double?) : EventPrice

    data object Unknown : EventPrice
}

data class EventAudience(val description: String?, val minAge: Int?, val maxAge: Int?)

data class ShowtimeOptionUi(val eventId: String, val start: ZonedDateTime, val selected: Boolean)

enum class TicketPriceInputError {
    /** Not a plain amount such as `12`, `12.5` or `12,50`. */
    NotANumber,

    /** Outside $0 to $500 CAD. */
    OutOfRange,
}

sealed interface TicketPriceStatus {
    /** Loading the shared summary and the person's own report. */
    data object Loading : TicketPriceStatus
    data object Idle : TicketPriceStatus
    data object Saving : TicketPriceStatus
    data object Saved : TicketPriceStatus

    /** Could not read prices. Offer Retry through `load()`. The Event can still be added. */
    data object LoadFailed : TicketPriceStatus

    /** The save failed or the store rejected the value. The input stays so the person can retry. */
    data object SaveFailed : TicketPriceStatus
}

data class TicketPriceFieldState(
    /** Reports need a signed-in person. When false, hide the field or show a sign-in prompt. */
    val signedIn: Boolean,
    val input: String = "",
    val inputError: TicketPriceInputError? = null,
    /** This person's saved report, loaded from the repository. */
    val ownReportCad: Double? = null,
    val status: TicketPriceStatus = TicketPriceStatus.Loading,
    val minCad: Double = MIN_TICKET_PRICE_CAD,
    val maxCad: Double = MAX_TICKET_PRICE_CAD,
) {
    /** The parsed input, or null when blank or invalid. */
    val parsedCad: Double? get() = if (inputError == null) parseTicketPrice(input).amount else null

    /** True while loading or saving. */
    val isBusy: Boolean get() = status == TicketPriceStatus.Loading || status == TicketPriceStatus.Saving

    /** Blank input with an existing report clears it. */
    val clearsReport: Boolean get() = inputError == null && input.isBlank() && ownReportCad != null

    /** Enables the save button: a new valid amount, or clearing the existing report. */
    val canSave: Boolean
        get() = signedIn && inputError == null && !isBusy &&
            (clearsReport || (parsedCad != null && parsedCad != ownReportCad))
}

data class EventDetailsUiState(
    val eventId: String,
    val title: String,
    val type: String,
    val start: ZonedDateTime,
    val showtimes: List<ShowtimeOptionUi>,
    /** More than one showtime: show the time chooser. */
    val hasRepeatedShowtimes: Boolean,
    /** Null when the source published no end time. */
    val publishedEnd: ZonedDateTime?,
    val endTimeUnpublished: Boolean,
    val venueName: String?,
    val address: String?,
    val price: EventPrice,
    val audience: EventAudience?,
    val description: String?,
    val ticketUrl: String?,
    val ticketPrice: TicketPriceFieldState,
    /** False when the Event cannot become a Stop, such as no coordinates, or while prices load or save. */
    val canAdd: Boolean,
)

sealed interface AddToItineraryAction {
    /** The Event has a published end, so [stop] is ready for `Itinerary.withStop`. */
    data class Add(val stop: Stop) : AddToItineraryAction

    /**
     * No published end: open the Planned Departure sheet for [event], then build the Stop with
     * `eventStop(event, departure, ticketPriceCad)`.
     */
    data class ChooseDeparture(val event: Event, val ticketPriceCad: Double?) : AddToItineraryAction
}

internal data class ParsedTicketPrice(val amount: Double?, val error: TicketPriceInputError?)

private val AmountPattern = Regex("^[0-9]{1,6}([.,][0-9]{1,2})?\$")

/**
 * Blank is valid and means no report. A `$` before or after the amount and spaces are ignored, so
 * `$25`, `25 $` and `12,50 $` work. A leading minus is out of range.
 */
internal fun parseTicketPrice(input: String): ParsedTicketPrice {
    var text = input.trim().removePrefix("$").trim().removeSuffix("$").trim()
    if (text.isEmpty()) return ParsedTicketPrice(null, null)
    val negative = text.startsWith("-")
    if (negative) text = text.removePrefix("-").trim()
    if (!AmountPattern.matches(text)) return ParsedTicketPrice(null, TicketPriceInputError.NotANumber)
    if (negative) return ParsedTicketPrice(null, TicketPriceInputError.OutOfRange)
    val amount = text.replace(',', '.').toDouble()
    return if (isValidTicketPrice(amount)) ParsedTicketPrice(amount, null)
    else ParsedTicketPrice(null, TicketPriceInputError.OutOfRange)
}

/**
 * State holder for the Event details sheet. [showtimes] are the card's showtimes. Call [load]
 * once when the sheet opens. [uid] is null when nobody is signed in.
 */
class EventDetailsHolder(
    private val showtimes: List<ReachableShowtime>,
    initialEventId: String? = null,
    private val zone: ZoneId,
    private val uid: String?,
    private val repository: TicketPriceRepository,
) {
    init {
        require(showtimes.isNotEmpty()) { "An Event card has at least one showtime" }
    }

    private val key: ShowtimeKey = showtimes.first().event.showtimeKey()
    private var selected: ReachableShowtime =
        showtimes.firstOrNull { it.event.id == initialEventId } ?: showtimes.first()
    private var summary: TicketPriceSummary? = null
    private var field = TicketPriceFieldState(signedIn = uid != null)

    private val _state = MutableStateFlow(build())
    val state: StateFlow<EventDetailsUiState> = _state.asStateFlow()

    /** Loads the shared median and this person's own report. Call again to retry. */
    suspend fun load() {
        field = field.copy(status = TicketPriceStatus.Loading)
        publish()
        try {
            summary = repository.getSummary(key)
            val own = uid?.let { repository.getOwnReport(it, key) }
            field = field.copy(
                ownReportCad = own,
                input = if (field.input.isBlank() && own != null) formatAmount(own) else field.input,
                status = TicketPriceStatus.Idle,
            )
        } catch (e: CancellationException) {
            field = field.copy(status = TicketPriceStatus.LoadFailed)
            publish()
            throw e
        } catch (_: Exception) {
            field = field.copy(status = TicketPriceStatus.LoadFailed)
        }
        publish()
    }

    fun selectShowtime(eventId: String) {
        selected = showtimes.firstOrNull { it.event.id == eventId } ?: return
        publish()
    }

    fun setTicketPriceInput(input: String) {
        field = field.copy(
            input = input,
            inputError = parseTicketPrice(input).error,
            status = if (field.status == TicketPriceStatus.Saved || field.status == TicketPriceStatus.SaveFailed) {
                TicketPriceStatus.Idle
            } else {
                field.status
            },
        )
        publish()
    }

    /** True when the report was stored or cleared. Replaces this person's earlier report for all showtimes. */
    suspend fun saveTicketPrice(): Boolean {
        val id = uid ?: return false
        val current = field.takeIf { it.canSave } ?: return false
        val amount = current.parsedCad
        field = field.copy(status = TicketPriceStatus.Saving)
        publish()
        val done = try {
            if (amount == null) {
                repository.deleteReport(id, key)
                true
            } else {
                repository.saveReport(id, key, amount)
            }
        } catch (e: CancellationException) {
            field = field.copy(status = TicketPriceStatus.Idle)
            publish()
            throw e
        } catch (_: Exception) {
            false
        }
        if (done) {
            field = field.copy(ownReportCad = amount, status = if (amount == null) TicketPriceStatus.Idle else TicketPriceStatus.Saved)
            summary = try {
                repository.getSummary(key)
            } catch (e: CancellationException) {
                publish()
                throw e
            } catch (_: Exception) {
                summary
            }
        } else {
            field = field.copy(status = TicketPriceStatus.SaveFailed)
        }
        publish()
        return done
    }

    /**
     * Saves a pending valid ticket price first, so it travels with the Stop. Null while prices
     * load or save, when the input is invalid, the save failed, or the Event cannot become a Stop.
     */
    suspend fun addToItinerary(): AddToItineraryAction? {
        val current = _state.value
        if (!current.canAdd || field.isBusy || field.inputError != null) return null
        if (field.canSave && !saveTicketPrice()) return null
        val event = selected.event
        val ticketPrice = field.ownReportCad
        if (event.publishedEnd() == null) return AddToItineraryAction.ChooseDeparture(event, ticketPrice)
        val stop = eventStop(event, selected.suggestedDeparture.plannedDeparture, ticketPrice) ?: return null
        return AddToItineraryAction.Add(stop)
    }

    private fun publish() {
        _state.value = build()
    }

    private fun build(): EventDetailsUiState {
        val event = selected.event
        val end = event.publishedEnd()
        val start = event.startTime()?.atZoneSameInstant(zone) ?: selected.start
        return EventDetailsUiState(
            eventId = event.id,
            title = event.title,
            type = event.type,
            start = start,
            showtimes = showtimes.map { ShowtimeOptionUi(it.event.id, it.start, it.event.id == event.id) },
            hasRepeatedShowtimes = showtimes.size > 1,
            publishedEnd = end?.atZoneSameInstant(zone),
            endTimeUnpublished = end == null,
            venueName = event.location.venueName,
            address = event.location.address,
            price = priceOf(event),
            audience = event.targetAudience?.let { EventAudience(it.description, it.minAge, it.maxAge) },
            description = event.description,
            ticketUrl = event.url,
            ticketPrice = field,
            canAdd = !field.isBusy && eventStop(event, selected.suggestedDeparture.plannedDeparture) != null,
        )
    }

    private fun priceOf(event: Event): EventPrice {
        val cost = event.cost
        val median = summary
        return when {
            cost.status == CostStatus.FREE -> EventPrice.Free
            median != null -> EventPrice.UserMedian(median.medianCad, median.reportCount)
            cost.status == CostStatus.PAID && (cost.minPrice != null || cost.maxPrice != null) ->
                EventPrice.Published(cost.minPrice, cost.maxPrice)
            else -> EventPrice.Unknown
        }
    }

    private fun formatAmount(amount: Double): String =
        if (amount % 1.0 == 0.0) amount.toInt().toString() else String.format(java.util.Locale.ROOT, "%.2f", amount)
}
