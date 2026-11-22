package com.example.hackapp.scaffold.screens.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.citypack.rememberDistrictMap
import com.example.hackapp.data.Cost
import com.example.hackapp.data.CostStatus
import com.example.hackapp.data.Event
import com.example.hackapp.data.Location
import com.example.hackapp.data.Schedule
import com.example.hackapp.itinerary.BudgetSummary
import com.example.hackapp.itinerary.DepartureSuggestion
import com.example.hackapp.itinerary.EventCard
import com.example.hackapp.itinerary.EventHourGroup
import com.example.hackapp.itinerary.EventTypeGroup
import com.example.hackapp.itinerary.InMemoryTicketPriceRepository
import com.example.hackapp.itinerary.LocalItineraryRepository
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.itinerary.PriceRank
import com.example.hackapp.itinerary.ReachableShowtime
import com.example.hackapp.itinerary.ShowtimeKey
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.TicketPriceRepository
import com.example.hackapp.itinerary.defaultBlockId
import com.example.hackapp.itinerary.publishedEnd
import com.example.hackapp.profile.LocalProfileRepository
import com.example.hackapp.scaffold.screens.budgetBracketLabel
import com.example.hackapp.scaffold.screens.itinerary.eventStopType
import com.example.hackapp.services.LocalCityServices
import com.example.hackapp.ui.components.ChoiceOption
import com.example.hackapp.ui.components.DataAttribution
import com.example.hackapp.ui.components.EmptyState
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.EventDetailsSheet
import com.example.hackapp.ui.components.EventOptionCard
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappSmallButton
import com.example.hackapp.ui.components.HackappSmallTextButton
import com.example.hackapp.ui.components.HackappSmallTonalButton
import com.example.hackapp.ui.components.InfoCallout
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.components.MealBlockPrompt
import com.example.hackapp.ui.components.MealPickerSheet
import com.example.hackapp.ui.components.PlannedDepartureSheet
import com.example.hackapp.ui.components.PriceLabel
import com.example.hackapp.ui.components.StopTypeTag
import com.example.hackapp.ui.components.formatCad
import com.example.hackapp.ui.components.text
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val SELECTION_STEP = 5
private const val DETAIL_SEPARATOR = " · "

/** Times in controls: `18:30`, in both languages. */
private val SelectionTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The app has no shared ticket price store on this branch yet, so reports live for the process.
 * Replace it with the application's repository when the Firestore work is merged.
 */
private val SessionTicketPrices: TicketPriceRepository = InMemoryTicketPriceRepository()

/** Connects Event and Meal selection (Planning step 5) to its ViewModel, sheets and navigation. */
@Composable
fun PlanningStopSelectionScreen(
    itineraryId: String,
    onLeave: (String) -> Unit,
    onReview: (String) -> Unit,
) {
    val services = LocalCityServices.current
    val viewModel: PlanningStopSelectionViewModel = viewModel(
        factory = PlanningStopSelectionViewModel.factory(
            itineraryId = itineraryId,
            auth = LocalAuthService.current,
            itineraries = LocalItineraryRepository.current,
            profiles = LocalProfileRepository.current,
            events = services.events,
            places = services.places,
            tickets = SessionTicketPrices,
            estimator = services.legs,
            zone = LocalCityPack.current.timeZone,
        ),
    )
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.reviewId) {
        state.reviewId?.let { id ->
            viewModel.onNavigated()
            onReview(id)
        }
    }
    LaunchedEffect(state.leaveId) {
        state.leaveId?.let { id ->
            viewModel.onNavigated()
            onLeave(id)
        }
    }
    val map by rememberDistrictMap()
    val districtName = state.districtId?.let { id -> map?.districts?.firstOrNull { it.id == id }?.name }

    // The showtime highlighted on each card. It only preselects the time in Event details.
    var chosen by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    fun choose(sameCard: List<String>, eventId: String?) {
        chosen = ArrayList(chosen - sameCard.toSet() + listOfNotNull(eventId))
    }
    fun open(card: EventCard, eventId: String?) {
        viewModel.openEvent(card)
        eventId?.let(viewModel::selectShowtime)
    }

    PlanningStopSelectionPage(
        state = state,
        districtName = districtName,
        chosenShowtimes = chosen.toSet(),
        onBack = viewModel::back,
        onRetry = viewModel::retry,
        onRetrySave = viewModel::retrySave,
        onOpenEvent = { card -> open(card, card.showtimes.firstOrNull { it.event.id in chosen }?.event?.id) },
        onShowtimeChange = { card, eventId ->
            choose(card.showtimes.map { it.event.id }, eventId)
            if (eventId != null) open(card, eventId)
        },
        onChooseMeal = viewModel::openMealPicker,
        onSkipMeal = viewModel::skipMeal,
        onNextDistrict = viewModel::nextDistrict,
        onFinish = viewModel::finish,
    )

    when (val sheet = state.sheet) {
        null -> Unit
        is SelectionSheet.EventDetails -> {
            val details by sheet.holder.state.collectAsState()
            EventDetailsSheet(
                state = details,
                onSelectShowtime = { eventId ->
                    viewModel.selectShowtime(eventId)
                    choose(details.showtimes.map { it.eventId }, eventId)
                },
                onTicketPriceChange = viewModel::setTicketPriceInput,
                onRetryLoad = viewModel::retryEventDetails,
                // Planning needs an account, so a lost session only closes the sheet.
                onSignIn = viewModel::dismissSheet,
                onAddToItinerary = viewModel::addToItinerary,
                onAddWithoutPrice = viewModel::addWithoutPrice,
                onDismiss = viewModel::dismissSheet,
            )
        }
        is SelectionSheet.PlannedDeparture -> {
            val departure by sheet.holder.state.collectAsState()
            PlannedDepartureSheet(
                state = departure,
                venueName = sheet.event.location.venueName,
                onTimeChange = viewModel::setDeparture,
                onConfirm = viewModel::confirmDeparture,
                onDismiss = viewModel::dismissSheet,
            )
        }
        is SelectionSheet.MealPicker -> {
            val picker by sheet.holder.state.collectAsState()
            MealPickerSheet(
                state = picker,
                onSelectTab = viewModel::selectMealTab,
                onChoose = viewModel::chooseMeal,
                onSkip = viewModel::skipMeal,
                onDismiss = viewModel::dismissSheet,
                budgetLabel = sheet.block.budget?.let { budgetBracketLabel(it) },
            )
        }
    }
}

/** Figma "PlanningStopSelectionScreen" (29:754, 141:3868, 141:3910, 141:3952, 141:3994). Holds no rules. */
@Composable
fun PlanningStopSelectionPage(
    state: PlanningStopSelectionUiState,
    districtName: String?,
    chosenShowtimes: Set<String>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetrySave: () -> Unit,
    onOpenEvent: (EventCard) -> Unit,
    onShowtimeChange: (EventCard, String?) -> Unit,
    onChooseMeal: () -> Unit,
    onSkipMeal: () -> Unit,
    onNextDistrict: () -> Unit,
    onFinish: () -> Unit,
) {
    val ready = !state.loading && !state.loadFailed
    HackappPage(
        title = districtName ?: stringResource(R.string.planning_stops_title),
        onBack = onBack,
        step = SELECTION_STEP,
        totalSteps = PLANNING_STEPS,
        stepLabel = if (ready) {
            stringResource(
                R.string.selection_progress,
                SELECTION_STEP,
                PLANNING_STEPS,
                state.districtNumber,
                state.districtIds.size,
            )
        } else {
            null
        },
        bottomActions = if (ready) {
            {
                if (state.saveFailed) SaveError(onRetrySave)
                state.budget?.let { RunningTotal(it, state.stops) }
                SelectionActions(state, onNextDistrict, onFinish)
            }
        } else {
            null
        },
    ) {
        when {
            state.loading -> LoadingState()
            state.loadFailed -> ErrorState(onRetry = onRetry)
            else -> {
                val prompt = state.mealPrompt
                val atMeal = prompt?.reached == true
                SlotHeader(
                    start = if (atMeal) prompt.start else state.gapStart,
                    end = if (atMeal) prompt.end else state.gapEnd,
                    showFrom = !atMeal,
                )
                if (atMeal) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MealBlockPrompt(onChooseMeal = onChooseMeal, modifier = Modifier.fillMaxWidth())
                        HackappSmallTextButton(stringResource(R.string.action_skip_meal), onSkipMeal)
                    }
                }
                EventGroups(state.groups, chosenShowtimes, onOpenEvent, onShowtimeChange)
                when {
                    atMeal -> InfoCallout(stringResource(R.string.selection_fixed_hidden))
                    state.groups.isEmpty() -> EmptyGap()
                }
                if (prompt != null && !atMeal) MealPromptRow(prompt, onChooseMeal, onSkipMeal)
                if (state.groups.isNotEmpty()) DataAttribution()
            }
        }
    }
}

/** The time slot being filled. It is announced when adding a Stop moves it. */
@Composable
private fun SlotHeader(start: ZonedDateTime?, end: ZonedDateTime?, showFrom: Boolean) {
    if (start == null || end == null) return
    Column(
        Modifier.semantics(mergeDescendants = true) {
            heading()
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.selection_slot_title, start.timeText(), end.timeText()),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (showFrom) {
            Text(
                stringResource(R.string.selection_slot_from, start.timeText()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Fixed showtimes by hour and type, then the drop-in Events under "Ongoing". */
@Composable
private fun EventGroups(
    groups: List<EventHourGroup>,
    chosenShowtimes: Set<String>,
    onOpenEvent: (EventCard) -> Unit,
    onShowtimeChange: (EventCard, String?) -> Unit,
) {
    fixedGroups(groups).forEach { hour ->
        hour.types.forEach { typeGroup ->
            key(hour.hourStart, typeGroup.type) {
                TypeGroup(hour.hourStart.timeText(), typeGroup, chosenShowtimes, onOpenEvent, onShowtimeChange)
            }
        }
    }
    ongoingGroups(groups).forEach { typeGroup ->
        key(typeGroup.type) {
            TypeGroup(stringResource(R.string.selection_ongoing), typeGroup, chosenShowtimes, onOpenEvent, onShowtimeChange)
        }
    }
}

private fun fixedGroups(groups: List<EventHourGroup>): List<EventHourGroup> = groups.mapNotNull { hour ->
    val types = hour.types.mapNotNull { group ->
        group.cards.filterNot { it.isDropIn }.takeIf { it.isNotEmpty() }?.let { group.copy(cards = it) }
    }
    if (types.isEmpty()) null else hour.copy(types = types)
}

private fun ongoingGroups(groups: List<EventHourGroup>): List<EventTypeGroup> = groups
    .flatMap { hour -> hour.types }
    .flatMap { group -> group.cards.filter { it.isDropIn }.map { group.type to it } }
    .groupBy({ it.first }, { it.second })
    .map { (type, cards) -> EventTypeGroup(type, cards) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeGroup(
    label: String,
    group: EventTypeGroup,
    chosenShowtimes: Set<String>,
    onOpenEvent: (EventCard) -> Unit,
    onShowtimeChange: (EventCard, String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            StopTypeTag(eventStopType(group.type))
        }
        group.cards.forEach { card ->
            key(card.key.value) {
                EventOptionCard(
                    title = card.title,
                    details = cardDetails(card),
                    onClick = { onOpenEvent(card) },
                    dropInLabel = if (card.isDropIn) dropInLabel(card) else null,
                    showtimes = card.showtimes.map { ChoiceOption(it.event.id, it.start.timeText()) },
                    selectedShowtime = card.showtimes.firstOrNull { it.event.id in chosenShowtimes }?.event?.id,
                    onShowtimeChange = { onShowtimeChange(card, it) },
                )
            }
        }
    }
}

/** "17:30 · Venue · 20 $ · end time not published". Chips or the drop-in tag carry the time otherwise. */
@Composable
private fun cardDetails(card: EventCard): String {
    val single = card.showtimes.singleOrNull()?.takeUnless { card.isDropIn }
    val price = when (val cad = card.priceCad) {
        null -> PriceLabel.Unknown
        0.0 -> PriceLabel.Free
        else -> PriceLabel.Amount(formatCad(cad))
    }
    val parts = buildList {
        single?.let { add(it.start.timeText()) }
        card.venueName?.takeIf { it.isNotBlank() }?.let { add(it) }
        add(price.text())
        if (single != null && single.event.publishedEnd() == null) add(stringResource(R.string.selection_end_unknown))
    }
    return parts.joinToString(DETAIL_SEPARATOR)
}

@Composable
private fun dropInLabel(card: EventCard): String {
    val first = card.showtimes.first()
    val start = first.start.timeText()
    val range = first.event.publishedEnd()
        ?.atZoneSameInstant(first.start.zone)
        ?.let { stringResource(R.string.selection_time_block, start, it.timeText()) }
        ?: start
    return stringResource(R.string.selection_no_fixed_time, range)
}

/** Figma empty gap (141:3952). The next actions stay visible in the footer. */
@Composable
private fun EmptyGap() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.EventBusy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 32.dp)
                .size(48.dp),
        )
        EmptyState(
            title = stringResource(R.string.selection_empty_title),
            body = stringResource(R.string.selection_empty_body),
        )
    }
}

/** A Meal Block still ahead while Events remain before it: a quieter version of the Meal prompt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealPromptRow(prompt: MealPrompt, onChooseMeal: () -> Unit, onSkipMeal: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow, RoundedCornerShape(HackappCorners.Medium))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                stringResource(R.string.selection_meal_block_title),
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
            )
            Text(
                stringResource(R.string.selection_time_block, prompt.start.timeText(), prompt.end.timeText()),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HackappSmallTonalButton(stringResource(R.string.selection_choose_meal), onChooseMeal)
            HackappSmallTextButton(stringResource(R.string.action_skip_meal), onSkipMeal)
        }
    }
}

/** Figma "RunningTotal" (141:4091). Over budget warns only, so the actions below stay available. */
@Composable
private fun RunningTotal(budget: BudgetSummary, stops: List<Stop>) {
    val scheme = MaterialTheme.colorScheme
    val over = budget.isOverBudget
    val container = if (over) scheme.errorContainer else scheme.surfaceContainerHigh
    val strong = if (over) scheme.onErrorContainer else scheme.onSurface
    val quiet = if (over) scheme.onErrorContainer else scheme.onSurfaceVariant
    val limit = budget.totalBudgetCad
    Row(
        Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(HackappCorners.Medium))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (over) Icons.Filled.Warning else Icons.Filled.Payments,
            contentDescription = null,
            tint = strong,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                if (over && limit != null) {
                    stringResource(R.string.running_total_over, formatCad(budget.runningTotalCad - limit))
                } else {
                    stringResource(R.string.running_total_label)
                },
                style = MaterialTheme.typography.labelMedium,
                color = quiet,
            )
            Text(totalAmount(budget), style = MaterialTheme.typography.titleMedium, color = strong)
            val detail = if (over) stringResource(R.string.running_total_over_hint) else totalBreakdown(budget, stops)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = quiet)
        }
        limit?.let {
            Text(
                stringResource(R.string.running_total_budget, formatCad(it)),
                style = MaterialTheme.typography.labelMedium,
                color = quiet,
            )
        }
    }
}

@Composable
private fun totalAmount(budget: BudgetSummary): String {
    val known = budget.runningTotalCad
    return when {
        !budget.hasUnknownPrices -> formatCad(known)
        known == 0.0 -> stringResource(R.string.running_total_unknown_only)
        else -> stringResource(R.string.running_total_amount, formatCad(known))
    }
}

/**
 * "Events 20 $ · Lunch 50 $". The summary has one Meal amount, so it is named only when a single
 * Meal Stop explains it. With several, the line keeps the Events amount alone.
 */
@Composable
private fun totalBreakdown(budget: BudgetSummary, stops: List<Stop>): String {
    val events = stringResource(R.string.running_total_events, formatCad(budget.eventsCad))
    if (budget.mealsCad <= 0.0) return events
    val meal = stops.filter { it.kind == StopKind.Meal }.singleOrNull() ?: return events
    val kind = MealKind.entries.firstOrNull { it.defaultBlockId() == meal.mealBlockId } ?: return events
    val amount = formatCad(budget.mealsCad)
    val mealPart = stringResource(
        when (kind) {
            MealKind.Lunch -> R.string.running_total_lunch
            MealKind.Dinner -> R.string.running_total_dinner
            MealKind.LateBite -> R.string.running_total_late_bite
        },
        amount,
    )
    return events + DETAIL_SEPARATOR + mealPart
}

/** A failed autosave, announced politely, with the way to save again. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SaveError(onRetrySave: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.planning_save_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        HackappSmallTextButton(stringResource(R.string.action_retry), onRetrySave)
    }
}

/** Next borough with Finish here, or Finish alone on the last District. The row wraps when tight. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectionActions(
    state: PlanningStopSelectionUiState,
    onNextDistrict: () -> Unit,
    onFinish: () -> Unit,
) {
    if (!state.hasNextDistrict) {
        HackappPrimaryButton(
            text = stringResource(R.string.action_finish),
            onClick = onFinish,
            enabled = state.canFinish,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        val finishHere = stringResource(R.string.selection_finish_here)
        // Figma shows the disabled action as a filled grey pill (141:3952).
        if (state.canFinish) {
            HackappSmallTextButton(finishHere, onFinish)
        } else {
            HackappSmallButton(finishHere, onFinish, enabled = false)
        }
        HackappSmallButton(stringResource(R.string.selection_next_district), onNextDistrict)
    }
}

private fun ZonedDateTime.timeText(): String = format(SelectionTimeFormat)

/* Previews */

private val PreviewZone: ZoneId = ZoneId.of("America/Toronto")

private fun previewTime(hour: Int, minute: Int = 0): ZonedDateTime =
    ZonedDateTime.of(2026, 12, 5, hour, minute, 0, 0, PreviewZone)

private fun previewShowtime(id: String, title: String, start: ZonedDateTime, end: ZonedDateTime?) = ReachableShowtime(
    event = Event(
        id = id,
        title = title,
        type = "music",
        location = Location(city = "Montréal"),
        schedule = Schedule(
            startDate = start.toOffsetDateTime().toString(),
            endDate = end?.toOffsetDateTime()?.toString(),
            timezone = PreviewZone.id,
        ),
        cost = Cost(CostStatus.UNKNOWN),
    ),
    start = start,
    suggestedDeparture = DepartureSuggestion(end ?: start.plusMinutes(90), end != null),
    leg = null,
)

private fun previewCard(
    title: String,
    venue: String,
    type: String,
    price: Double?,
    starts: List<ZonedDateTime>,
    end: ZonedDateTime? = null,
    dropIn: Boolean = false,
) = EventCard(
    key = ShowtimeKey(title),
    title = title,
    venueName = venue,
    type = type,
    showtimes = starts.mapIndexed { index, start -> previewShowtime("$title-$index", title, start, end) },
    matchesInterest = false,
    priceRank = if (price == null) PriceRank.Unknown else PriceRank.KnownWithinBudget,
    priceCad = price,
    travelMinutes = null,
    isDropIn = dropIn,
)

private val PreviewDropIn = previewCard(
    "Exposition « Lumières du fleuve »",
    "Galerie du Mile End",
    "exhibition",
    0.0,
    listOf(previewTime(10)),
    end = previewTime(21),
    dropIn = true,
)

private fun previewHour(hour: Int, type: String, vararg cards: EventCard) =
    EventHourGroup(previewTime(hour), listOf(EventTypeGroup(type, cards.toList())))

private val PreviewPocketConcert = previewCard(
    "Concert de poche",
    "Église du Plateau",
    "music",
    null,
    listOf(previewTime(17, 15), previewTime(17, 45), previewTime(18, 15)),
    end = previewTime(19),
)

private val PreviewComedy = previewCard(
    "Soirée d’humour",
    "Bar Le Zinc",
    "comedy",
    15.0,
    listOf(previewTime(18, 30)),
    end = previewTime(20),
)

private val PreviewGroups = listOf(
    previewHour(
        17,
        "music",
        previewCard("Jazz sur le toit", "Salle Rosemont", "music", 20.0, listOf(previewTime(17, 30))),
        PreviewPocketConcert,
    ),
    previewHour(18, "comedy", PreviewComedy),
    EventHourGroup(previewTime(10), listOf(EventTypeGroup("exhibition", listOf(PreviewDropIn)))),
)

private fun previewStop(kind: StopKind, blockId: String? = null): Stop {
    val start: OffsetDateTime = previewTime(12).toOffsetDateTime()
    return Stop(
        id = "$kind-$blockId",
        kind = kind,
        sourceId = "1",
        name = "Stop",
        districtId = "plateau-mont-royal",
        latitude = 45.52,
        longitude = -73.58,
        start = start,
        plannedDeparture = start.plusHours(1),
        mealBlockId = blockId,
    )
}

private val PreviewStops = listOf(previewStop(StopKind.Event), previewStop(StopKind.Meal, MealKind.Lunch.defaultBlockId()))

private fun previewBudget(events: Double, meals: Double, unknown: Int = 0, over: Boolean = false) = BudgetSummary(
    eventBudgetCad = 50,
    mealBudgetCad = 50,
    totalBudgetCad = 100,
    eventsCad = events,
    mealsCad = meals,
    unknownPriceCount = unknown,
    isOverBudget = over,
)

private val PreviewState = PlanningStopSelectionUiState(
    loading = false,
    districtIds = listOf("plateau-mont-royal", "ville-marie"),
    districtId = "plateau-mont-royal",
    districtNumber = 1,
    hasNextDistrict = true,
    canFinish = true,
    gapStart = previewTime(17),
    gapEnd = previewTime(19),
    groups = PreviewGroups,
    stops = PreviewStops,
    budget = previewBudget(20.0, 50.0, unknown = 1),
)

@Composable
private fun PreviewPage(
    state: PlanningStopSelectionUiState,
    districtName: String? = "Le Plateau-Mont-Royal",
    chosen: Set<String> = setOf("Concert de poche-1"),
) = HackappTheme {
    PlanningStopSelectionPage(state, districtName, chosen, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {})
}

@Preview(name = "Selection · Events (29:754)", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun SelectionEventsPreview() = PreviewPage(PreviewState)

@Preview(name = "Selection · 360 dp, 1.3x", showBackground = true, widthDp = 360, heightDp = 917, fontScale = 1.3f)
@Composable
private fun SelectionLargeTextPreview() = PreviewPage(PreviewState)

@Preview(name = "Selection · Meal Block (141:3868)", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun SelectionMealBlockPreview() = PreviewPage(
    PreviewState.copy(
        gapStart = previewTime(19),
        gapEnd = previewTime(20, 30),
        groups = listOf(EventHourGroup(previewTime(10), listOf(EventTypeGroup("exhibition", listOf(PreviewDropIn))))),
        mealPrompt = MealPrompt("meal-dinner", MealKind.Dinner, previewTime(19), previewTime(20, 30), reached = true),
    ),
)

@Preview(name = "Selection · Meal ahead", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun SelectionMealAheadPreview() = PreviewPage(
    PreviewState.copy(
        mealPrompt = MealPrompt("meal-dinner", MealKind.Dinner, previewTime(19), previewTime(20, 30), reached = false),
    ),
)

@Preview(name = "Selection · over budget (141:3910)", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun SelectionOverBudgetPreview() = PreviewPage(
    PreviewState.copy(
        groups = listOf(
            previewHour(
                17,
                "music",
                previewCard("Gala de jazz", "Théâtre Outremont", "music", 120.0, listOf(previewTime(17, 30)), previewTime(19)),
                PreviewPocketConcert,
            ),
            previewHour(18, "comedy", PreviewComedy),
        ),
        budget = previewBudget(120.0, 50.0, unknown = 1, over = true),
    ),
    chosen = setOf("Concert de poche-0"),
)

@Preview(name = "Selection · over budget, 360 dp, 1.3x", showBackground = true, widthDp = 360, heightDp = 917, fontScale = 1.3f)
@Composable
private fun SelectionOverBudgetLargeTextPreview() = PreviewPage(
    PreviewState.copy(budget = previewBudget(120.0, 50.0, unknown = 1, over = true)),
)

@Preview(name = "Selection · empty gap (141:3952)", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun SelectionEmptyPreview() = PreviewPage(
    PreviewState.copy(
        canFinish = false,
        gapStart = previewTime(18),
        groups = emptyList(),
        stops = listOf(previewStop(StopKind.Meal, MealKind.Lunch.defaultBlockId())),
        budget = previewBudget(0.0, 50.0),
    ),
)

@Preview(name = "Selection · last borough (141:3994)", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun SelectionLastBoroughPreview() = PreviewPage(
    PreviewState.copy(
        districtId = "ville-marie",
        districtNumber = 2,
        hasNextDistrict = false,
        gapStart = previewTime(21),
        gapEnd = previewTime(23, 30),
        groups = listOf(
            previewHour(
                21,
                "theatre",
                previewCard(
                    "Pièce « Le dernier métro »",
                    "Théâtre du Quartier",
                    "theatre",
                    35.0,
                    listOf(previewTime(21), previewTime(21, 30)),
                    previewTime(23),
                ),
            ),
            previewHour(22, "music", previewCard("DJ set au sous-sol", "Bar Datcha", "music", 0.0, listOf(previewTime(22, 15)))),
        ),
        budget = previewBudget(35.0, 50.0, unknown = 1),
    ),
    districtName = "Ville-Marie",
    chosen = setOf("Pièce « Le dernier métro »-0"),
)

@Preview(name = "Selection · save error", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun SelectionSaveErrorPreview() = PreviewPage(PreviewState.copy(saveFailed = true))

@Preview(name = "Selection · loading", showBackground = true, widthDp = 412, heightDp = 500)
@Composable
private fun SelectionLoadingPreview() = PreviewPage(PlanningStopSelectionUiState(), districtName = null)

@Preview(name = "Selection · load error", showBackground = true, widthDp = 412, heightDp = 500)
@Composable
private fun SelectionErrorPreview() = PreviewPage(
    PlanningStopSelectionUiState(loading = false, loadFailed = true),
    districtName = null,
)
