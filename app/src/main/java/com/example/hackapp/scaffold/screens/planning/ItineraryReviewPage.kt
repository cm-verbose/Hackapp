package com.example.hackapp.scaffold.screens.planning

import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.IconButton
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.DistrictMapData
import com.example.hackapp.citypack.GeoPoint
import com.example.hackapp.citypack.loadDistrictMap
import com.example.hackapp.citypack.rememberDistrictMap
import com.example.hackapp.citypack.resolveStayOrigin
import com.example.hackapp.data.repository.EventQuery
import com.example.hackapp.itinerary.BudgetPrices
import com.example.hackapp.itinerary.BudgetSummary
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.LocalItineraryRepository
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.itinerary.OutingKind
import com.example.hackapp.itinerary.PlanningChangePreview
import com.example.hackapp.itinerary.PlanningContext
import com.example.hackapp.itinerary.RemovedStop
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.StopRemovalReason
import com.example.hackapp.itinerary.activeMealBlocks
import com.example.hackapp.itinerary.mealBudget
import com.example.hackapp.profile.AgeRange
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.LocalProfileRepository
import com.example.hackapp.scaffold.screens.budgetBracketLabel
import com.example.hackapp.scaffold.screens.itinerary.appLocale
import com.example.hackapp.scaffold.screens.itinerary.itineraryTitle
import com.example.hackapp.scaffold.screens.itinerary.stopTypesFor
import com.example.hackapp.scaffold.screens.underway.UnderwayExactAlarmRationale
import com.example.hackapp.scaffold.screens.underway.currentUnderwayCoordinator
import com.example.hackapp.scaffold.screens.underway.rememberUnderwayStartPermissions
import com.example.hackapp.scaffold.screens.underway.reviewStarter
import com.example.hackapp.services.LocalCityServices
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.HackappOutlineButton
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappTextActionButton
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.components.RunningTotalCard
import com.example.hackapp.ui.components.StopRow
import com.example.hackapp.ui.components.formatCad
import com.example.hackapp.ui.components.map.ItineraryRouteMap
import com.example.hackapp.ui.components.map.PreviewDistrictMap
import com.example.hackapp.ui.components.map.RouteStop
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.ui.theme.StopType
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val REVIEW_STEP = 5

/** Times in Stop rows: `18:30`, in both languages. */
private val StopTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** One line of the budget card. A null [bracket] has no budget set. */
data class ReviewBudgetRow(@param:StringRes val label: Int, val bracket: BudgetBracket?)

/** Connects the Itinerary review (Planning step 5) to its ViewModel, data and navigation. */
@Composable
fun ItineraryReviewScreen(
    itineraryId: String,
    onBack: () -> Unit,
    onStarted: (String) -> Unit,
    onBackToSelection: (ReviewNavigation.BackToSelection) -> Unit,
    onChangeDateTime: (String) -> Unit,
    onShare: (String) -> Unit = {},
) {
    val services = LocalCityServices.current
    val auth = LocalAuthService.current
    val profiles = LocalProfileRepository.current
    val zone = services.cityPack.timeZone
    val coordinator = currentUnderwayCoordinator()
    val appContext = LocalContext.current.applicationContext
    // A missing or unreadable Profile only leaves the fallback budgets unset, as on Meal choice.
    val loadProfileContext: suspend () -> PlanningContext = {
        val profile = try {
            auth.currentUser?.uid?.let { profiles.getProfile(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        profile?.let { PlanningContext.from(it, coordinator.clock.planningNow(), zone) }
            ?: PlanningContext(coordinator.clock.planningNow(), zone, AgeRange.From25To34)
    }
    val viewModel: ItineraryReviewViewModel = viewModel(
        factory = ItineraryReviewViewModel.factory(
            itineraryId = itineraryId,
            auth = auth,
            itineraries = LocalItineraryRepository.current,
            estimator = services.legs,
            zone = zone,
            loadContext = loadProfileContext,
            loadPrices = { itinerary ->
                val eventsById = itinerary.stops.filter { it.kind == StopKind.Event }
                    .mapNotNull { services.events.getEvent(it.sourceId) }
                    .associateBy { it.id }
                val mealPrices = itinerary.stops.filter { it.kind == StopKind.Meal }
                    .mapNotNull { it.sourceId.toLongOrNull() }
                    .associateWith { services.places.getPlace(it)?.price }
                BudgetPrices(eventsById = eventsById, mealPrices = mealPrices)
            },
            loadEvents = { itinerary ->
                services.events.search(
                    EventQuery(
                        // No lower bound, as in Selection: reachableShowtimes keeps only drop-ins still running.
                        startsAtOrAfter = null,
                        startsBefore = itinerary.windowEnd(zone).toOffsetDateTime(),
                    ),
                )
            },
            starter = coordinator.reviewStarter(
                originFor = { uid ->
                    resolveStayOrigin(
                        loadDistrictId = { profiles.getProfile(uid)?.districtOfStayId },
                        loadMap = { loadDistrictMap(appContext, services.cityPack) },
                    )
                },
                originDistrictFor = { uid ->
                    try {
                        profiles.getProfile(uid)?.districtOfStayId
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                },
            ),
            demoMode = { coordinator.clock.isSimulated },
            clock = coordinator.clock::now,
            planningClock = coordinator.clock::planningNow,
        ),
    )
    val permissions = rememberUnderwayStartPermissions()
    // Started, and waiting for an answer on exact alarms. Leaving earlier would hide the question.
    var startedId by rememberSaveable { mutableStateOf<String?>(null) }
    UnderwayExactAlarmRationale(
        permissions,
        onClosed = {
            startedId?.let(onStarted)
            startedId = null
        },
    )
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.navigation) {
        when (val target = state.navigation) {
            null -> Unit
            is ReviewNavigation.Started -> {
                viewModel.onNavigated()
                if (target.askExactAlarm && permissions.offerExactAlarm()) {
                    startedId = target.itineraryId
                } else {
                    onStarted(target.itineraryId)
                }
            }
            is ReviewNavigation.BackToSelection -> {
                viewModel.onNavigated()
                onBackToSelection(target)
            }
        }
    }
    val map by rememberDistrictMap(services.cityPack)
    val stops = remember(state.stops) { state.stops.map { it.stop } }
    val stopTypes by produceState(emptyMap<String, StopType>(), stops) {
        value = services.events.stopTypesFor(stops)
    }
    val context by produceState<PlanningContext?>(null, auth.currentUser?.uid) {
        value = loadProfileContext()
    }
    // Start depends on the clock, so a page left open past its window must update.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    ItineraryReviewPage(
        state = state,
        map = map,
        stopTypes = stopTypes,
        budgetRows = remember(state.itinerary, context) {
            state.itinerary?.let { reviewBudgetRows(it, context) }.orEmpty()
        },
        onBack = onBack,
        onRetry = viewModel::retry,
        onSelectStop = viewModel::selectStop,
        onRemove = viewModel::remove,
        onStart = {
            // Never waits for the answer: Start works with notifications off.
            permissions.requestNotifications()
            viewModel.start()
        },
        onChangeDateTime = { onChangeDateTime(itineraryId) },
        onShare = { onShare(itineraryId) },
        onConfirmDialog = viewModel::confirmDialog,
        onDismissDialog = viewModel::dismissDialog,
        onReschedule = viewModel::reschedule,
        onReorganize = viewModel::reorganize,
        onPickShowtime = viewModel::pickShowtime,
        onCancelReschedule = viewModel::cancelReschedule,
    )
}

/** The Meals planned, in time order, then the Event budget. */
internal fun reviewBudgetRows(itinerary: Itinerary, context: PlanningContext?): List<ReviewBudgetRow> {
    val meals = itinerary.activeMealBlocks.sortedBy { it.start }.map { block ->
        val bracket = block.budget
            ?: context?.let { itinerary.mealBudget(block.kind, it) }
            ?: itinerary.mealBudgets?.forKind(block.kind)
        ReviewBudgetRow(
            label = when (block.kind) {
                MealKind.Lunch -> R.string.budget_lunch
                MealKind.Dinner -> R.string.budget_dinner
                MealKind.LateBite -> R.string.budget_late_night
            },
            bracket = bracket,
        )
    }
    return meals + ReviewBudgetRow(R.string.budget_events, itinerary.eventBudget ?: context?.defaultEventBudget)
}

/** Figma "ItineraryReviewScreen" (29:853, 141:2692, 141:2822). Holds no rules. */
@Composable
fun ItineraryReviewPage(
    state: ItineraryReviewUiState,
    map: DistrictMapData?,
    stopTypes: Map<String, StopType>,
    budgetRows: List<ReviewBudgetRow>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectStop: (String?) -> Unit,
    onRemove: (String) -> Unit,
    onStart: () -> Unit,
    onChangeDateTime: () -> Unit,
    onConfirmDialog: () -> Unit,
    onDismissDialog: () -> Unit,
    onReschedule: () -> Unit,
    onReorganize: () -> Unit,
    onPickShowtime: (String) -> Unit,
    onCancelReschedule: () -> Unit,
    onShare: (() -> Unit)? = null,
) {
    val ready = !state.loading && !state.loadFailed
    val districtNames = remember(map) { map?.districts?.associate { it.id to it.name }.orEmpty() }
    HackappPage(
        title = stringResource(R.string.review_title),
        onBack = onBack,
        step = REVIEW_STEP,
        totalSteps = PLANNING_STEPS,
        actions = {
            if (ready && onShare != null) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                }
            }
        },
        bottomActions = if (ready) {
            { ReviewActions(state, onStart, onChangeDateTime) }
        } else {
            null
        },
    ) {
        when {
            state.loading -> LoadingState()
            state.loadFailed -> ErrorState(onRetry = onRetry, title = stringResource(R.string.review_load_failed))
            else -> {
                if (map != null && state.stops.isNotEmpty()) {
                    val routeStops = state.stops.map { row ->
                        RouteStop(
                            order = row.number,
                            districtId = row.stop.districtId,
                            type = stopTypes[row.stop.id] ?: row.stop.fallbackType(),
                            location = GeoPoint(row.stop.latitude, row.stop.longitude),
                            name = row.stop.name,
                        )
                    }
                    val byNumber = state.stops.associateBy { it.number }
                    ItineraryRouteMap(
                        map = map,
                        stops = routeStops,
                        highlightedOrder = state.stops.firstOrNull { it.highlighted }?.number,
                        onStopClick = { marker -> byNumber[marker.order]?.let { onSelectStop(it.stop.id) } },
                    )
                }
                Column {
                    state.stops.forEach { row ->
                        StopRow(
                            time = row.stop.start.format(StopTimeFormat),
                            stopName = row.stop.name,
                            details = stopDetails(row, districtNames[row.stop.districtId]),
                            highlighted = row.highlighted,
                            onClick = { onSelectStop(row.stop.id) },
                            onRemove = if (state.itinerary?.phase == ItineraryPhase.Planning) {
                                { onRemove(row.stop.id) }
                            } else {
                                null
                            },
                            removeEnabled = !state.busy,
                        )
                    }
                }
                ReviewBudgetCard(budgetRows)
                // Over budget moves to the bottom actions, so it stays visible above Start.
                state.budget?.takeUnless { it.isOverBudget }?.let { ReviewTotal(it) }
            }
        }
    }
    when (val dialog = state.dialog) {
        null -> Unit
        is ReviewDialog.EndCurrent -> StartWarningDialog(
            currentName = itineraryTitle(
                dialog.current.date,
                (dialog.current.districtIds.firstOrNull() ?: dialog.current.stops.firstOrNull()?.districtId)
                    ?.let(districtNames::get),
            ),
            busy = state.busy,
            onConfirm = onConfirmDialog,
            onCancel = onDismissDialog,
        )
        is ReviewDialog.RemoveAffects -> UnreachableStopsDialog(
            removedName = dialog.removed.name,
            affected = dialog.affected,
            kept = state.stops.map { it.stop }.filter { stop ->
                stop.id != dialog.removed.id && dialog.affected.none { it.id == stop.id }
            },
            canReschedule = dialog.canReschedule,
            busy = state.busy,
            saveFailed = state.actionFailed,
            onReschedule = onReschedule,
            onReorganize = onReorganize,
            onCancel = onDismissDialog,
        )
    }
    state.reschedule?.let { step ->
        ReschedulePickerDialog(
            step = step,
            busy = state.busy,
            saveFailed = state.actionFailed,
            onPick = onPickShowtime,
            onReorganize = onReorganize,
            onCancel = onCancelReschedule,
        )
    }
}

private fun Stop.fallbackType() = if (kind == StopKind.Meal) StopType.Meal else StopType.Festival

@Composable
private fun stopDetails(row: ReviewStop, districtName: String?): String {
    val stop = row.stop
    // A Meal keeps its block's end, so it only shows a time when the next Stop needs an earlier one.
    val departure = if (stop.kind == StopKind.Event || row.leaveBy.isBefore(stop.plannedDeparture)) {
        stringResource(R.string.review_leave_by, row.leaveBy.format(StopTimeFormat))
    } else {
        null
    }
    // The time comes first, so a long District name is what gets cut.
    return listOfNotNull(departure, districtName).joinToString(" · ")
}

@Composable
private fun ColumnScope.ReviewActions(
    state: ItineraryReviewUiState,
    onStart: () -> Unit,
    onChangeDateTime: () -> Unit,
) {
    val start = state.start
    // Inside a dialog or the picker, the error is shown there instead.
    if (state.actionFailed && state.dialog == null && state.reschedule == null) {
        Text(
            stringResource(R.string.planning_save_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    state.budget?.takeIf { it.isOverBudget }?.let { ReviewTotal(it) }
    if (start is ReviewStartState.NotPlanning) return
    val blockedReason = when (start) {
        is ReviewStartState.NotToday -> stringResource(R.string.review_start_only_on, reviewDate(start.date, appLocale()))
        is ReviewStartState.WindowEnded -> stringResource(R.string.review_start_window_ended)
        ReviewStartState.NoEvents -> stringResource(R.string.review_start_no_events)
        else -> null
    }
    HackappPrimaryButton(
        text = stringResource(R.string.review_start),
        onClick = onStart,
        enabled = start is ReviewStartState.Ready && !state.busy,
    )
    if (blockedReason != null) {
        Text(
            blockedReason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (start is ReviewStartState.WindowEnded) {
        HackappOutlineButton(text = stringResource(R.string.review_change_date), onClick = onChangeDateTime)
    }
}

/** `sam. 28 nov. 2026` in French, `Sat, Nov 28, 2026` in English. */
internal fun reviewDate(date: LocalDate, locale: Locale): String {
    val pattern = if (locale.language == "fr") "EEE d MMM yyyy" else "EEE, MMM d, yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

@Composable
private fun ReviewBudgetCard(rows: List<ReviewBudgetRow>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(HackappCorners.Medium),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.planning_budget_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            rows.forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(row.label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        row.bracket?.let { budgetBracketLabel(it) } ?: stringResource(R.string.budget_not_set),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** Review's total is read once, so it has no breakdown and no live announcement. */
@Composable
private fun ReviewTotal(budget: BudgetSummary) =
    RunningTotalCard(budget, showBreakdown = false, announceChanges = false)

/** A failed save inside a dialog. The dialog's actions stay available to try again. */
@Composable
private fun DialogSaveError() {
    Text(
        stringResource(R.string.planning_save_error),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * The frame shared by the Stop warnings: title, scrolling body and actions that wrap, so nothing
 * clips at 360 dp with large text. Previews draw the card without a dialog window.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StopWarningDialog(
    title: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    stackActions: Boolean = false,
    actions: @Composable () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    val card = @Composable {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.semantics { paneTitle = title },
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = body,
                )
                if (stackActions) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) { actions() }
                } else {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) { actions() }
                }
            }
        }
    }
    if (LocalInspectionMode.current) {
        card()
    } else {
        BasicAlertDialog(onDismissRequest = { if (!busy) onDismiss() }) { card() }
    }
}

@Composable
private fun WarningBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@StringRes
private fun StopRemovalReason.textRes(): Int = when (this) {
    StopRemovalReason.RemovedByPerson -> R.string.stop_edit_reason_removed_by_person
    StopRemovalReason.DateChanged -> R.string.stop_edit_reason_date_changed
    StopRemovalReason.OutsideWindow -> R.string.stop_edit_reason_outside_window
    StopRemovalReason.DistrictRemoved -> R.string.stop_edit_reason_district_removed
    StopRemovalReason.MealBlockRemoved -> R.string.stop_edit_reason_meal_block_removed
    StopRemovalReason.MealClosed -> R.string.stop_edit_reason_meal_closed
    StopRemovalReason.NoLongerReachable -> R.string.stop_edit_reason_no_longer_reachable
    StopRemovalReason.MealOverBudget -> R.string.stop_edit_reason_meal_over_budget
}

/**
 * A list of Stops, as time and name, under an optional [label]. [emphasized] marks those that
 * would be lost, and [reasons] by Stop id says why.
 */
@Composable
private fun WarningStopList(
    label: String?,
    stops: List<Stop>,
    emphasized: Boolean,
    reasons: Map<String, StopRemovalReason> = emptyMap(),
) {
    if (stops.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        label?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = if (emphasized) scheme.error else scheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
        }
        stops.forEach { stop ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (emphasized) scheme.errorContainer else Color.Transparent,
                        RoundedCornerShape(HackappCorners.Small),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val color = if (emphasized) scheme.onErrorContainer else scheme.onSurface
                Text(stop.start.format(StopTimeFormat), style = MaterialTheme.typography.labelLarge, color = color)
                Column(Modifier.weight(1f)) {
                    Text(stop.name, style = MaterialTheme.typography.bodyMedium, color = color)
                    reasons[stop.id]?.let { reason ->
                        Text(stringResource(reason.textRes()), style = MaterialTheme.typography.bodySmall, color = color)
                    }
                }
            }
        }
    }
}

/** A single affected Stop needs no list label. */
@Composable
private fun affectedLabel(stops: List<Stop>): String? =
    if (stops.size > 1) stringResource(R.string.review_unreachable_affected) else null

/** Figma "StartItineraryWarningDialog" (141:2958). */
@Composable
internal fun StartWarningDialog(currentName: String, busy: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    StopWarningDialog(
        title = stringResource(R.string.review_start_warning_title),
        busy = busy,
        onDismiss = onCancel,
        actions = {
            HackappTextActionButton(stringResource(R.string.action_cancel), onCancel, enabled = !busy)
            HackappTextActionButton(stringResource(R.string.review_start_warning_confirm), onConfirm, enabled = !busy)
        },
    ) {
        WarningBody(stringResource(R.string.review_start_warning_body, currentName))
    }
}

/**
 * Figma "StopEditWarningDialog" (141:2993), shared by Date and time, Budget and District pick.
 * Shows nothing when [preview] is null.
 */
@Composable
internal fun StopChangeWarningDialog(
    preview: PlanningChangePreview?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    if (preview == null) return
    StopWarningDialog(
        title = stringResource(R.string.stop_edit_title),
        busy = false,
        onDismiss = onCancel,
        actions = {
            HackappTextActionButton(stringResource(R.string.action_cancel), onCancel)
            HackappTextActionButton(stringResource(R.string.stop_edit_confirm), onConfirm)
        },
    ) {
        WarningBody(stringResource(R.string.stop_edit_body))
        WarningStopList(
            label = stringResource(R.string.stop_edit_removed),
            stops = preview.removed.map { it.stop },
            emphasized = true,
            reasons = preview.removed.associate { it.stop.id to it.reason },
        )
        WarningStopList(stringResource(R.string.stop_edit_kept), preview.kept, emphasized = false)
    }
}

/** Figma "RemoveStopWarningDialog" (141:3031). Stops only leave through one of its actions. */
@Composable
internal fun UnreachableStopsDialog(
    removedName: String,
    affected: List<Stop>,
    kept: List<Stop>,
    canReschedule: Boolean,
    busy: Boolean,
    saveFailed: Boolean = false,
    onReschedule: () -> Unit,
    onReorganize: () -> Unit,
    onCancel: () -> Unit,
) {
    StopWarningDialog(
        title = stringResource(R.string.review_unreachable_title),
        busy = busy,
        onDismiss = onCancel,
        stackActions = true,
        actions = {
            HackappTextActionButton(stringResource(R.string.action_cancel), onCancel, enabled = !busy)
            HackappTextActionButton(
                stringResource(R.string.action_reschedule),
                onReschedule,
                enabled = canReschedule && !busy,
            )
            HackappTextActionButton(
                stringResource(R.string.action_reorganize),
                onReorganize,
                enabled = !busy,
                contentColor = MaterialTheme.colorScheme.error,
            )
        },
    ) {
        WarningBody(stringResource(R.string.review_unreachable_body, removedName))
        WarningStopList(affectedLabel(affected), affected, emphasized = true)
        if (!canReschedule) WarningBody(stringResource(R.string.review_reschedule_unavailable))
        WarningStopList(stringResource(R.string.stop_edit_kept), kept, emphasized = false)
        if (saveFailed) DialogSaveError()
    }
}

/** Picks another showtime for one affected Stop at a time. After a failed save the same pick can be retried. */
@Composable
private fun ReschedulePickerDialog(
    step: RescheduleStep,
    busy: Boolean,
    saveFailed: Boolean = false,
    onPick: (String) -> Unit,
    onReorganize: () -> Unit,
    onCancel: () -> Unit,
) {
    val loading = stringResource(R.string.state_loading)
    val canReorganize = step.options.isEmpty() || saveFailed
    // Réorganiser removes every affected Stop, so all of them are listed whenever it is offered.
    val listed = if (canReorganize) step.affected.ifEmpty { listOf(step.stop) } else listOf(step.stop)
    StopWarningDialog(
        title = stringResource(R.string.review_reschedule_title),
        busy = busy,
        onDismiss = onCancel,
        actions = {
            HackappTextActionButton(stringResource(R.string.action_cancel), onCancel, enabled = !busy)
            if (canReorganize) {
                HackappTextActionButton(
                    stringResource(R.string.action_reorganize),
                    onReorganize,
                    enabled = !busy,
                    contentColor = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        if (step.total > 1) {
            Text(
                stringResource(R.string.review_reschedule_progress, step.index + 1, step.total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WarningStopList(affectedLabel(listed), listed, emphasized = true)
        if (saveFailed) DialogSaveError()
        if (busy) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = loading },
            )
        }
        if (step.options.isEmpty()) {
            WarningBody(stringResource(R.string.review_reschedule_empty))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                step.options.forEach { option ->
                    HackappOutlineButton(
                        text = listOfNotNull(
                            option.start.format(StopTimeFormat),
                            if (option.hasPublishedEnd) {
                                null
                            } else {
                                stringResource(R.string.review_planned_departure, option.plannedDeparture.format(StopTimeFormat))
                            },
                        ).joinToString(" · "),
                        onClick = { onPick(option.eventId) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun previewTime(hour: Int, minute: Int = 0): OffsetDateTime =
    OffsetDateTime.of(2026, 11, 28, hour, minute, 0, 0, ZoneOffset.ofHours(-5))

private fun previewStop(id: String, name: String, kind: StopKind, district: String, hour: Int, minute: Int = 0) = Stop(
    id = id,
    kind = kind,
    sourceId = id,
    name = name,
    districtId = district,
    latitude = 45.52,
    longitude = -73.58,
    start = previewTime(hour, minute),
    plannedDeparture = previewTime(hour, minute).plusHours(1),
)

// Lazy, so preview data never runs when the page's file is loaded in the app.
private val PreviewStops by lazy {
    listOf(
        previewStop("1", "Jazz sur le toit", StopKind.Event, "a", 18, 30),
        previewStop("2", "Bistro du coin", StopKind.Meal, "a", 19, 30),
        previewStop("3", "Spectacle d’humour", StopKind.Event, "b", 21),
        previewStop("4", "Bar à vin", StopKind.Meal, "b", 23, 30),
    )
}

private val PreviewTypes by lazy {
    mapOf(
        "1" to StopType.Music,
        "2" to StopType.Meal,
        "3" to StopType.Comedy,
        "4" to StopType.Bar,
    )
}

private val PreviewBudget by lazy {
    listOf(
        ReviewBudgetRow(R.string.budget_lunch, BudgetBracket.Under50),
        ReviewBudgetRow(R.string.budget_late_night, BudgetBracket.Under20),
        ReviewBudgetRow(R.string.budget_events, BudgetBracket.Under100),
    )
}

private fun previewState(
    highlighted: String? = null,
    start: ReviewStartState = ReviewStartState.Ready(),
) = ItineraryReviewUiState(
    loading = false,
    itinerary = Itinerary(
        id = "preview",
        ownerUid = "preview",
        cityId = "mtl",
        date = LocalDate.of(2026, 11, 28),
        outingKind = OutingKind.Night,
        startTime = LocalTime.of(18, 0),
        endTime = LocalTime.of(2, 0),
        stops = PreviewStops,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    ),
    stops = PreviewStops.mapIndexed { index, stop ->
        ReviewStop(stop, index + 1, stop.id == highlighted, stop.plannedDeparture)
    },
    highlightedStopId = highlighted,
    start = start,
)

@Composable
private fun PreviewPage(state: ItineraryReviewUiState) = HackappTheme {
    ItineraryReviewPage(
        state = state,
        map = PreviewDistrictMap,
        stopTypes = PreviewTypes,
        budgetRows = PreviewBudget,
        onBack = {},
        onRetry = {},
        onSelectStop = {},
        onRemove = {},
        onStart = {},
        onChangeDateTime = {},
        onConfirmDialog = {},
        onDismissDialog = {},
        onReschedule = {},
        onReorganize = {},
        onPickShowtime = {},
        onCancelReschedule = {},
    )
}

@Preview(name = "Review", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ReviewPreview() = PreviewPage(previewState())

@Preview(name = "Review · marker selected", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ReviewMarkerSelectedPreview() = PreviewPage(previewState(highlighted = "2"))

@Preview(name = "Review · not today", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ReviewNotTodayPreview() =
    PreviewPage(previewState(start = ReviewStartState.NotToday(LocalDate.of(2026, 11, 28))))

@Preview(name = "Review · window ended", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ReviewWindowEndedPreview() =
    PreviewPage(previewState(start = ReviewStartState.WindowEnded(LocalDate.of(2026, 11, 28))))

@Preview(name = "Review · over budget, unknown price", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun ReviewOverBudgetPreview() = PreviewPage(
    previewState().copy(
        budget = BudgetSummary(
            eventBudgetCad = 50,
            mealBudgetCad = 20,
            totalBudgetCad = 70,
            eventsCad = 60.0,
            mealsCad = 30.0,
            unknownPriceCount = 1,
            isOverBudget = true,
        ),
    ),
)

@Preview(name = "Running total", showBackground = true, widthDp = 380)
@Composable
private fun ReviewRunningTotalPreview() = HackappTheme {
    val within = BudgetSummary(
        eventBudgetCad = 100,
        mealBudgetCad = 70,
        totalBudgetCad = 170,
        eventsCad = 60.0,
        mealsCad = 30.0,
        unknownPriceCount = 0,
        isOverBudget = false,
    )
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReviewTotal(within)
        ReviewTotal(within.copy(unknownPriceCount = 1))
        ReviewTotal(within.copy(eventsCad = 170.0, unknownPriceCount = 1, isOverBudget = true))
    }
}

@Preview(name = "Review · 360 dp, 1.3x", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun ReviewLargeTextPreview() =
    PreviewPage(previewState(highlighted = "1", start = ReviewStartState.NotToday(LocalDate.of(2026, 11, 28))))

@Preview(name = "Review · loading", showBackground = true, widthDp = 412, heightDp = 400)
@Composable
private fun ReviewLoadingPreview() = PreviewPage(ItineraryReviewUiState())

@Preview(name = "Review · load error", showBackground = true, widthDp = 412, heightDp = 400)
@Composable
private fun ReviewErrorPreview() = PreviewPage(ItineraryReviewUiState(loading = false, loadFailed = true))

@Preview(name = "Start warning", showBackground = true, widthDp = 328)
@Composable
private fun StartWarningPreview() = HackappTheme {
    StartWarningDialog("Jeudi à Rosemont", busy = false, onConfirm = {}, onCancel = {})
}

@Preview(name = "Stop edit warning", showBackground = true, widthDp = 328)
@Composable
private fun StopEditWarningPreview() = HackappTheme {
    val itinerary = previewState().itinerary!!
    StopChangeWarningDialog(
        preview = PlanningChangePreview(
            previous = itinerary,
            proposed = itinerary,
            kept = PreviewStops.take(2),
            removed = PreviewStops.drop(2).map {
                RemovedStop(it, StopRemovalReason.OutsideWindow)
            },
        ),
        onConfirm = {},
        onCancel = {},
    )
}

@Preview(name = "Unreachable Stops", showBackground = true, widthDp = 328)
@Composable
private fun UnreachablePreview() = HackappTheme {
    UnreachableStopsDialog(
        removedName = "Bistro du coin",
        affected = listOf(PreviewStops[2]),
        kept = listOf(PreviewStops[0], PreviewStops[3]),
        canReschedule = true,
        busy = false,
        onReschedule = {},
        onReorganize = {},
        onCancel = {},
    )
}

@Preview(name = "Unreachable Stops · 360 dp, 1.3x", showBackground = true, widthDp = 312, fontScale = 1.3f)
@Composable
private fun UnreachableLargeTextPreview() = HackappTheme {
    UnreachableStopsDialog(
        removedName = "Bistro du coin",
        affected = listOf(PreviewStops[2]),
        kept = listOf(PreviewStops[0], PreviewStops[3]),
        canReschedule = false,
        busy = false,
        onReschedule = {},
        onReorganize = {},
        onCancel = {},
    )
}

@Preview(name = "Reschedule picker", showBackground = true, widthDp = 328)
@Composable
private fun ReschedulePreview() = HackappTheme {
    ReschedulePickerDialog(
        step = RescheduleStep(
            stop = PreviewStops[2],
            options = listOf(RescheduleOption("e", previewTime(21, 30), previewTime(23), hasPublishedEnd = true)),
            index = 0,
            total = 2,
        ),
        busy = false,
        onPick = {},
        onReorganize = {},
        onCancel = {},
    )
}

@Preview(name = "Reschedule picker · save failed", showBackground = true, widthDp = 328)
@Composable
private fun RescheduleFailedPreview() = HackappTheme {
    ReschedulePickerDialog(
        step = RescheduleStep(
            stop = PreviewStops[3],
            options = listOf(RescheduleOption("e", previewTime(23, 45), previewTime(23, 55), hasPublishedEnd = true)),
            index = 1,
            total = 2,
            affected = PreviewStops.drop(2),
        ),
        busy = false,
        saveFailed = true,
        onPick = {},
        onReorganize = {},
        onCancel = {},
    )
}

@Preview(name = "Reschedule picker · empty", showBackground = true, widthDp = 328)
@Composable
private fun RescheduleEmptyPreview() = HackappTheme {
    ReschedulePickerDialog(
        step = RescheduleStep(PreviewStops[3], emptyList(), index = 1, total = 2, affected = PreviewStops.drop(2)),
        busy = false,
        onPick = {},
        onReorganize = {},
        onCancel = {},
    )
}
