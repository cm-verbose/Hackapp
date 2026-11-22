package com.example.hackapp.scaffold.screens.planning

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.citypack.GeoPoint
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.citypack.rememberDistrictMap
import com.example.hackapp.data.Event
import com.example.hackapp.data.model.MealSlot
import com.example.hackapp.data.model.Place
import com.example.hackapp.domain.FlowViewModel
import com.example.hackapp.domain.ItineraryKind
import com.example.hackapp.domain.ItineraryPlanner
import com.example.hackapp.domain.MealBlock
import com.example.hackapp.domain.PlanInput
import com.example.hackapp.domain.SelectionView
import com.example.hackapp.domain.Stop
import com.example.hackapp.domain.StopPrice
import com.example.hackapp.domain.endLocal
import com.example.hackapp.domain.eventStopType
import com.example.hackapp.domain.startLocal
import com.example.hackapp.domain.stopPrice
import com.example.hackapp.ui.components.BottomActions
import com.example.hackapp.ui.components.ChoiceOption
import com.example.hackapp.ui.components.DataAttribution
import com.example.hackapp.ui.components.DistrictPreviewSheet
import com.example.hackapp.ui.components.EmptyState
import com.example.hackapp.ui.components.EventOptionCard
import com.example.hackapp.ui.components.HackappDateField
import com.example.hackapp.ui.components.HackappOutlineButton
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappSegmentedButton
import com.example.hackapp.ui.components.HackappTimeField
import com.example.hackapp.ui.components.InfoCallout
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.components.MealBlockPrompt
import com.example.hackapp.ui.components.MealOptionCard
import com.example.hackapp.ui.components.PlannedDepartureSheet
import com.example.hackapp.ui.components.PriceLabel
import com.example.hackapp.ui.components.RadioOptionRow
import com.example.hackapp.ui.components.SingleChoiceChips
import com.example.hackapp.ui.components.StopRow
import com.example.hackapp.ui.components.StopTypeHeader
import com.example.hackapp.ui.components.TimeBlockHeader
import com.example.hackapp.ui.components.map.DistrictMap
import com.example.hackapp.ui.components.map.DistrictState
import com.example.hackapp.ui.components.map.ItineraryRouteMap
import com.example.hackapp.ui.components.map.RouteStop
import com.example.hackapp.ui.motion.entrance
import com.example.hackapp.ui.theme.HackappTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

const val PLANNING_STEPS = 5
private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun budgetText(code: String) = stringResource(
    when (code) {
        "UNDER_20" -> R.string.budget_under_20
        "UNDER_50" -> R.string.budget_under_50
        "UNDER_100" -> R.string.budget_under_100
        else -> R.string.budget_100_plus
    },
)

private fun StopPrice.label(): PriceLabel = when {
    free -> PriceLabel.Free
    text != null -> PriceLabel.Amount(text)
    else -> PriceLabel.Unknown
}

/** 1/5: date, day or night out, times and an optional budget for this Itinerary. */
@Composable
fun ScreenPlanningDateTime(vm: FlowViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val profile by vm.profile.collectAsState()
    val brackets by produceState(emptyList<com.example.hackapp.data.model.PriceBracket>()) { value = vm.brackets() }
    val profileBudget = profile?.budgetCode
    HackappPage(
        title = stringResource(R.string.planning_new_itinerary),
        onBack = onBack,
        step = 1,
        totalSteps = PLANNING_STEPS,
        bottomActions = { HackappPrimaryButton(stringResource(R.string.action_continue), onNext) },
    ) {
        Text(stringResource(R.string.planning_when_title), style = MaterialTheme.typography.headlineSmall)
        HackappDateField(
            value = vm.window.date,
            onValueChange = vm::setDate,
            label = stringResource(R.string.planning_date),
            firstDate = vm.firstPlanDate,
            lastDate = FlowViewModel.LAST_DATE,
            modifier = Modifier.entrance(0),
        )
        HackappSegmentedButton(
            listOf(
                ChoiceOption(ItineraryKind.DAY_OUT, stringResource(R.string.planning_day_out)),
                ChoiceOption(ItineraryKind.NIGHT_OUT, stringResource(R.string.planning_night_out)),
            ),
            vm.kind, vm::pickKind, Modifier.fillMaxWidth().entrance(1),
        )
        HackappTimeField(vm.window.start, vm::setStart, stringResource(R.string.planning_start), Modifier.entrance(2))
        HackappTimeField(vm.window.end, vm::setEnd, stringResource(R.string.planning_end), Modifier.entrance(3))
        InfoCallout(stringResource(R.string.planning_time_window_note))
        Text(stringResource(R.string.planning_budget_override), style = MaterialTheme.typography.titleMedium)
        SingleChoiceChips(
            brackets.map { ChoiceOption(it.code, budgetText(it.code)) },
            vm.budgetOverride ?: profileBudget,
            { vm.pickBudget(it) },
            allowDeselect = true,
        )
        profileBudget?.let {
            if (vm.budgetOverride == null) Text(stringResource(R.string.planning_budget_from_profile, budgetText(it)), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 2/5: pick 1 to 3 districts on the map; each shows how many events match the window and budget. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScreenPlanningDistrictPick(vm: FlowViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val mapState by rememberDistrictMap()
    val map = mapState
    var preview by remember { mutableStateOf<String?>(null) }
    var counts by remember { mutableStateOf<Map<String, Int>?>(null) }
    val boroughs = map?.districts?.filter { it.groupId == null }?.map { it.id }.orEmpty()
    LaunchedEffect(map != null, vm.window, vm.budgetOverride) {
        map ?: return@LaunchedEffect
        counts = vm.planner.countsByDistrict(vm.planInput(), boroughs)
    }
    val names = remember(map) { map?.districts?.associate { it.id to it.name }.orEmpty() }
    var previewTypes by remember { mutableStateOf<List<String>>(emptyList()) }

    HackappPage(
        title = stringResource(R.string.district_pick_title),
        onBack = onBack,
        step = 2,
        totalSteps = PLANNING_STEPS,
        bottomActions = {
            HackappPrimaryButton(stringResource(R.string.action_continue), onNext, enabled = vm.districts.isNotEmpty())
        },
    ) {
        Text(stringResource(R.string.district_pick_instructions), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.districts.forEachIndexed { i, id ->
                FilterChip(
                    selected = true,
                    onClick = { vm.toggleDistrict(id) },
                    label = { Text(stringResource(R.string.district_order_chip, i + 1, names[id] ?: id)) },
                )
            }
        }
        if (map == null || counts == null) {
            LoadingState()
        } else {
            val c = counts.orEmpty()
            if (boroughs.none { (c[it] ?: 0) > 0 }) {
                EmptyState(
                    title = stringResource(R.string.planning_no_events_title),
                    body = stringResource(R.string.planning_no_events_body),
                    actionLabel = stringResource(R.string.planning_change_time),
                    onAction = onBack,
                )
            }
            DistrictMap(
                map = map,
                stateOf = { id ->
                    when {
                        id in vm.districts -> DistrictState.Selected
                        id == preview -> DistrictState.Previewed
                        id !in boroughs || (c[id] ?: 0) == 0 -> DistrictState.Unavailable
                        else -> DistrictState.Available
                    }
                },
                eventCountOf = { id -> if (id in boroughs) c[id] ?: 0 else null },
                onDistrictClick = { id ->
                    if (id in vm.districts) vm.toggleDistrict(id) else preview = id
                },
            )
        }
        DataAttribution()
    }

    val shown = preview
    if (shown != null) {
        LaunchedEffect(shown) {
            previewTypes = vm.planner.typesInDistrict(vm.planInput(), shown).entries.sortedByDescending { it.value }.map { it.key.name }
        }
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { preview = null }, sheetState = sheet) {
            DistrictPreviewSheet(
                districtName = names[shown] ?: shown,
                description = pluralStringResource(R.plurals.stops_count, counts?.get(shown) ?: 0, counts?.get(shown) ?: 0),
                tags = previewTypes,
                position = vm.districts.size + 1,
                onAdd = { vm.toggleDistrict(shown); preview = null },
                onChooseAnother = { preview = null },
            )
        }
    }
}

/** 3/5: let the app plan meals (and when), or skip. */
@Composable
fun ScreenPlanningMealChoice(vm: FlowViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val options = remember(vm.window) { ItineraryPlanner.mealTimes(vm.window, vm.now()) }
    // A meal chosen earlier that no longer fits (window changed, or it has started) is replaced.
    LaunchedEffect(options) {
        val chosen = vm.meal
        if (chosen != null && chosen !in options) vm.chooseMeal(options.firstOrNull())
    }
    HackappPage(
        title = stringResource(R.string.meal_title),
        onBack = onBack,
        step = 3,
        totalSteps = PLANNING_STEPS,
        bottomActions = { HackappPrimaryButton(stringResource(R.string.action_continue), onNext) },
    ) {
        Text(stringResource(R.string.meal_question), style = MaterialTheme.typography.headlineSmall)
        RadioOptionRow(stringResource(R.string.meal_plan_yes), vm.wantsMeal, { vm.pickWantsMeal(true); if (vm.meal == null) vm.chooseMeal(options.firstOrNull()) })
        RadioOptionRow(stringResource(R.string.meal_plan_no), !vm.wantsMeal, { vm.pickWantsMeal(false) })
        if (vm.wantsMeal) {
            Text(stringResource(R.string.meal_when), style = MaterialTheme.typography.titleMedium)
            options.forEach { block ->
                val t = block.mealStart.toLocalTime().format(HHMM)
                val label = when (block.slot) {
                    MealSlot.LUNCH -> stringResource(R.string.meal_lunch_at, t)
                    MealSlot.DINNER -> stringResource(R.string.meal_dinner_at, t)
                    else -> stringResource(R.string.meal_late_bite_at, t)
                }
                RadioOptionRow(label, vm.meal == block, { vm.chooseMeal(block) })
            }
            InfoCallout(stringResource(R.string.meal_block_note))
        }
    }
}

/** 4/5: choose stops district by district, only from what is reachable after the previous one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenPlanningStopSelection(vm: FlowViewModel, onBack: () -> Unit, onReview: () -> Unit) {
    val mapState by rememberDistrictMap()
    val names = remember(mapState) { mapState?.districts?.associate { it.id to it.name }.orEmpty() }
    val district = vm.districts.getOrNull(vm.districtIndex)
    val previous = vm.stops.lastOrNull()
    var view by remember { mutableStateOf<SelectionView?>(null) }
    var events by remember { mutableStateOf<Map<String, Event>>(emptyMap()) }
    var input by remember { mutableStateOf<PlanInput?>(null) }
    var pendingEvent by remember { mutableStateOf<Event?>(null) }
    var mealPicker by remember { mutableStateOf(false) }
    val zone = LocalCityPack.current.timeZone

    LaunchedEffect(district, vm.stops.size, vm.meal, vm.mealChosen) {
        district ?: return@LaunchedEffect
        val i = vm.planInput()
        input = i
        val v = vm.planner.selection(i, district, previous, vm.meal, vm.mealChosen, vm.stops.map { it.id }.toSet())
        view = v
        events = v.blocks.flatMap { b -> b.groups.flatMap { it.second } }.associateBy { it.id }
    }

    val hasEvent = vm.stops.any { !it.isMeal }
    val isLast = vm.districtIndex >= vm.districts.lastIndex

    HackappPage(
        title = stringResource(R.string.stop_kind_event),
        onBack = onBack,
        step = 4,
        totalSteps = PLANNING_STEPS,
        bottomActions = {
            if (!isLast) HackappOutlineButton(stringResource(R.string.selection_next_district), { vm.nextDistrict() })
            HackappPrimaryButton(stringResource(R.string.selection_finish_here), onReview, enabled = hasEvent)
        },
    ) {
        if (district == null) {
            EmptyState(title = stringResource(R.string.state_empty_title))
            return@HackappPage
        }
        Text(
            stringResource(
                R.string.selection_district_progress, vm.districtIndex + 1, vm.districts.size,
                names[district] ?: district, vm.districts.getOrNull(vm.districtIndex + 1)?.let { names[it] ?: it } ?: "–",
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        if (vm.stops.isNotEmpty()) {
            Text(pluralStringResource(R.plurals.stops_count, vm.stops.size, vm.stops.size), style = MaterialTheme.typography.labelLarge)
        }
        val v = view
        if (v == null) {
            LoadingState()
        } else {
            val prompt = v.mealPrompt
            var promptShown = false
            v.blocks.forEach { block ->
                if (prompt != null && !promptShown && block.start >= prompt.blockStart) {
                    MealBlockPrompt(prompt.blockStart.format(HHMM), prompt.blockEnd.format(HHMM), { mealPicker = true })
                    promptShown = true
                }
                TimeBlockHeader(block.start.format(HHMM), block.end.format(HHMM))
                block.groups.forEach { (type, list) ->
                    StopTypeHeader(type)
                    list.forEach { e ->
                        val start = e.startLocal(zone)
                        EventOptionCard(
                            title = e.title,
                            startTime = start?.format(HHMM).orEmpty(),
                            endTime = e.endLocal(zone)?.format(HHMM),
                            price = e.stopPrice().label(),
                            onClick = {
                                val stop = vm.planner.eventStop(e, null)
                                if (stop != null) vm.addStop(stop) else pendingEvent = e
                            },
                        )
                    }
                }
            }
            if (prompt != null && !promptShown) {
                MealBlockPrompt(prompt.blockStart.format(HHMM), prompt.blockEnd.format(HHMM), { mealPicker = true })
            }
            if (v.blocks.isEmpty() && prompt == null) {
                EmptyState(
                    title = stringResource(R.string.selection_no_events_title, names[district] ?: district),
                    body = stringResource(if (isLast) R.string.selection_no_events_body_last else R.string.selection_no_events_body_next),
                )
            }
        }
        DataAttribution()
    }

    pendingEvent?.let { e ->
        PlannedDepartureSheet(
            eventTitle = e.title,
            initialTime = (e.startLocal(zone)?.plusHours(2)?.toLocalTime()) ?: LocalTime.NOON,
            onConfirm = { t -> vm.planner.eventStop(e, t)?.let(vm::addStop); pendingEvent = null },
            onDismiss = { pendingEvent = null },
        )
    }

    val meal = vm.meal
    if (mealPicker && meal != null && district != null && input != null) {
        MealSheet(vm, input!!, district, previous, meal, onPicked = { vm.addStop(it); mealPicker = false }, onDismiss = { mealPicker = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealSheet(
    vm: FlowViewModel,
    input: PlanInput,
    district: String,
    previous: Stop?,
    block: MealBlock,
    onPicked: (Stop) -> Unit,
    onDismiss: () -> Unit,
) {
    val places by produceState<List<Place>?>(null, block, district) {
        value = vm.planner.mealOptions(input, district, previous?.location, block, input.profile.cuisineGroups)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.selection_choose_meal), style = MaterialTheme.typography.titleLarge)
            val list = places
            when {
                list == null -> LoadingState()
                list.isEmpty() -> EmptyState(title = stringResource(R.string.state_empty_title))
                else -> Column(
                    Modifier.padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    list.take(12).forEach { p ->
                        MealOptionCard(
                            name = p.name,
                            details = listOfNotNull(p.cuisines.firstOrNull(), p.address).joinToString(" · "),
                            price = p.price?.label()?.let { PriceLabel.Amount(it) } ?: PriceLabel.Unknown,
                            onClick = { onPicked(vm.planner.mealStop(p, block, district)) },
                        )
                    }
                }
            }
        }
    }
}

/** 5/5: the route and the stops; removing one goes back to choosing from that gap. */
@Composable
fun ScreenItineraryReview(vm: FlowViewModel, onBack: () -> Unit, onRemoved: () -> Unit, onStarted: () -> Unit) {
    val mapState by rememberDistrictMap()
    val map = mapState
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.start { onStarted() }
    }
    HackappPage(
        title = stringResource(R.string.review_title),
        onBack = onBack,
        step = 5,
        totalSteps = PLANNING_STEPS,
        bottomActions = {
            Text(stringResource(R.string.review_notification_note), style = MaterialTheme.typography.bodySmall)
            HackappPrimaryButton(stringResource(R.string.review_start), {
                if (Build.VERSION.SDK_INT >= 33 && !vm.notifier.hasPermission()) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    vm.start { onStarted() }
                }
            }, enabled = vm.stops.isNotEmpty() && !vm.saving)
        },
    ) {
        if (map != null && vm.stops.isNotEmpty()) {
            ItineraryRouteMap(
                map = map,
                stops = vm.stops.mapIndexed { i, s ->
                    RouteStop(i + 1, s.districtId, s.type, s.location?.let { GeoPoint(it.lat, it.lng) })
                },
            )
        }
        vm.stops.forEachIndexed { i, s ->
            StopRow(
                time = s.start.format(HHMM),
                stopName = s.title,
                details = stringResource(R.string.review_planned_departure, s.departure.format(HHMM)),
                modifier = Modifier.entrance(i, stepMillis = 50),
                dotColor = HackappTheme.stopTypeColors[s.type],
                onRemove = { vm.removeStop(i); onRemoved() },
            )
        }
        InfoCallout(stringResource(R.string.review_remove_note))
        DataAttribution()
    }
}
