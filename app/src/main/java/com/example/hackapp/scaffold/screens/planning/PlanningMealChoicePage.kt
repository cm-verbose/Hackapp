package com.example.hackapp.scaffold.screens.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.itinerary.LocalItineraryRepository
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.LocalProfileRepository
import com.example.hackapp.scaffold.screens.budgetBracketLabel
import com.example.hackapp.ui.components.ChoiceOption
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.HackappOutlineButton
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappTimeField
import com.example.hackapp.ui.components.InfoCallout
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.components.MultiChoiceChips
import com.example.hackapp.ui.components.RadioOptionRow
import com.example.hackapp.ui.components.SingleChoiceChips
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val MEAL_STEP = 4

/** Times in controls: `18:30`, in both languages. */
private val ControlTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** Keeps the Meal start field narrow, so the Meal name beside it has room to wrap. */
private val TimeFieldWidth = 140.dp

/** The Itinerary's window, shown above the Meal chips. Null while unknown. */
data class ItineraryHours(val start: LocalTime, val end: LocalTime)

/** Connects the Meal choice page (Planning step 4) to its ViewModel and to navigation. */
@Composable
fun PlanningMealChoiceScreen(
    itineraryId: String,
    onBack: () -> Unit,
    onContinue: (String) -> Unit,
    onChangeDateTime: (String) -> Unit,
) {
    val auth = LocalAuthService.current
    val itineraries = LocalItineraryRepository.current
    val viewModel: PlanningMealChoiceViewModel = viewModel(
        factory = PlanningMealChoiceViewModel.factory(
            itineraryId = itineraryId,
            auth = auth,
            itineraries = itineraries,
            profiles = LocalProfileRepository.current,
            zone = LocalCityPack.current.timeZone,
        ),
    )
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.continueToId) {
        state.continueToId?.let { id ->
            viewModel.onNavigated()
            onContinue(id)
        }
    }
    LaunchedEffect(state.changeDateTimeId) {
        state.changeDateTimeId?.let { id ->
            viewModel.onNavigated()
            onChangeDateTime(id)
        }
    }
    PlanningMealChoicePage(
        state = state,
        hours = state.windowStart?.let { start -> state.windowEnd?.let { end -> ItineraryHours(start, end) } },
        onBack = onBack,
        onRetry = viewModel::retry,
        onPlanMealsChange = viewModel::setPlanMeals,
        onToggleMeal = viewModel::toggleMeal,
        onStartChange = viewModel::setStart,
        onBudgetChange = viewModel::setBudget,
        onChangeDateTime = viewModel::changeDateAndTime,
        onContinue = viewModel::continueToStops,
    )
}

/** Figma "PlanningMealChoiceScreen" (29:687, 141:3340, 141:3485). Holds no rules. */
@Composable
fun PlanningMealChoicePage(
    state: PlanningMealChoiceUiState,
    hours: ItineraryHours?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlanMealsChange: (Boolean) -> Unit,
    onToggleMeal: (MealKind) -> Unit,
    onStartChange: (MealKind, LocalTime) -> Unit,
    onBudgetChange: (MealKind, BudgetBracket?) -> Unit,
    onChangeDateTime: () -> Unit,
    onContinue: () -> Unit,
) {
    HackappPage(
        title = stringResource(R.string.meal_title),
        onBack = onBack,
        step = MEAL_STEP,
        totalSteps = PLANNING_STEPS,
        bottomActions = {
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.planning_save_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            HackappPrimaryButton(
                text = stringResource(R.string.action_continue),
                onClick = onContinue,
                enabled = state.canContinue,
            )
        },
    ) {
        when {
            state.loading -> LoadingState()
            state.loadFailed -> ErrorState(onRetry = onRetry)
            else -> {
                Column(Modifier.selectableGroup()) {
                    RadioOptionRow(
                        label = stringResource(R.string.meal_plan_yes),
                        selected = state.planMeals,
                        onClick = { onPlanMealsChange(true) },
                    )
                    RadioOptionRow(
                        label = stringResource(R.string.meal_plan_no),
                        selected = !state.planMeals,
                        onClick = { onPlanMealsChange(false) },
                    )
                }
                when {
                    !state.planMeals -> InfoCallout(stringResource(R.string.meal_none_reserved))
                    state.noMealFits -> {
                        hours?.let { ItineraryRange(it) }
                        InfoCallout(stringResource(R.string.meal_none_fit))
                        HackappOutlineButton(
                            text = stringResource(R.string.meal_change_time),
                            onClick = onChangeDateTime,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    else -> {
                        hours?.let { ItineraryRange(it) }
                        MealChips(state.options, onToggleMeal)
                        state.options.filter { it.selected }.forEach { option ->
                            MealCard(option, onStartChange, onBudgetChange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItineraryRange(hours: ItineraryHours) {
    Text(
        stringResource(R.string.meal_itinerary_range, hours.start.controlText(), hours.end.controlText()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One chip per offered Meal, named with its start time. Chips toggle off. */
@Composable
private fun MealChips(options: List<MealChoiceOption>, onToggleMeal: (MealKind) -> Unit) {
    val selected = options.filter { it.selected }.map { it.kind }.toSet()
    MultiChoiceChips(
        options = options.map { ChoiceOption(it.kind, mealChipLabel(it.kind, it.start)) },
        selected = selected,
        onSelectedChange = { next -> ((next - selected) + (selected - next)).forEach(onToggleMeal) },
    )
}

/** Figma "MealDetails": start time, reserved range and the budget for one selected Meal. */
@Composable
private fun MealCard(
    option: MealChoiceOption,
    onStartChange: (MealKind, LocalTime) -> Unit,
    onBudgetChange: (MealKind, BudgetBracket?) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val range = "${option.start.controlText()} – ${option.endTime.controlText()}"
    Surface(
        color = scheme.surfaceContainerLow,
        shape = RoundedCornerShape(HackappCorners.Medium),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(mealLabel(option.kind), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                    Text(
                        stringResource(R.string.meal_block_note, option.kind.blockMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    // The reserved range turns the error colour when the block leaves the window or
                    // overlaps another Meal, and the sentence below carries the error for TalkBack.
                    Text(
                        range,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (option.error != null) scheme.error else scheme.onSurfaceVariant,
                    )
                    option.error?.let { timeError ->
                        val message = mealTimeErrorText(timeError)
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.error,
                            modifier = Modifier.semantics { error(message) },
                        )
                    }
                }
                HackappTimeField(
                    value = option.start,
                    onValueChange = { onStartChange(option.kind, it) },
                    label = stringResource(R.string.planning_start),
                    modifier = Modifier.width(TimeFieldWidth),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.meal_budget_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // With no bracket chosen, the inherited budget shows as quiet text, not as a chip.
                if (option.budget == null && option.effectiveBudget != null) {
                    Text(
                        budgetBracketLabel(option.effectiveBudget),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            SingleChoiceChips(
                options = BudgetBracket.entries.map { ChoiceOption(it, budgetBracketLabel(it)) },
                selected = option.budget,
                onSelectedChange = { onBudgetChange(option.kind, it) },
                allowDeselect = true,
            )
        }
    }
}

@Composable
private fun mealLabel(kind: MealKind): String = stringResource(
    when (kind) {
        MealKind.Lunch -> R.string.meal_lunch
        MealKind.Dinner -> R.string.meal_dinner
        MealKind.LateBite -> R.string.meal_late_bite
    },
)

@Composable
private fun mealTimeErrorText(error: MealTimeError): String = stringResource(
    when (error) {
        MealTimeError.OutsideWindow -> R.string.meal_error_outside_window
        MealTimeError.Overlaps -> R.string.meal_error_overlaps
    },
)

@Composable
private fun mealChipLabel(kind: MealKind, start: LocalTime): String = stringResource(
    when (kind) {
        MealKind.Lunch -> R.string.meal_lunch_at
        MealKind.Dinner -> R.string.meal_dinner_at
        MealKind.LateBite -> R.string.meal_late_bite_at
    },
    start.controlText(),
)

private fun LocalTime.controlText(): String = format(ControlTimeFormat)

/* Previews */

private fun previewOption(
    kind: MealKind,
    selected: Boolean,
    start: LocalTime = when (kind) {
        MealKind.Lunch -> LocalTime.of(12, 0)
        MealKind.Dinner -> LocalTime.of(18, 30)
        MealKind.LateBite -> LocalTime.of(22, 0)
    },
    budget: BudgetBracket? = null,
    effectiveBudget: BudgetBracket? = budget,
    error: MealTimeError? = null,
) = MealChoiceOption(
    kind = kind,
    selected = selected,
    start = start,
    endTime = start.plusMinutes(kind.blockMinutes.toLong()),
    budget = budget,
    effectiveBudget = effectiveBudget,
    error = error,
)

private val PreviewHours = ItineraryHours(LocalTime.of(11, 0), LocalTime.of(23, 30))

private val PreviewPlanState = PlanningMealChoiceUiState(
    loading = false,
    options = listOf(
        previewOption(MealKind.Lunch, selected = true, effectiveBudget = BudgetBracket.Under50),
        previewOption(MealKind.Dinner, selected = false, effectiveBudget = BudgetBracket.Under50),
        previewOption(MealKind.LateBite, selected = true, budget = BudgetBracket.Under20),
    ),
)

@Composable
private fun PreviewPage(state: PlanningMealChoiceUiState, hours: ItineraryHours? = PreviewHours) = HackappTheme {
    PlanningMealChoicePage(state, hours, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {})
}

@Preview(name = "Meal choice", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun PlanningMealChoicePreview() = PreviewPage(PreviewPlanState)

@Preview(name = "Meal choice · 360 dp, 1.3x", showBackground = true, widthDp = 360, heightDp = 917, fontScale = 1.3f)
@Composable
private fun PlanningMealChoiceLargeTextPreview() = PreviewPage(PreviewPlanState)

@Preview(name = "Meal choice · user handles food", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun PlanningMealChoiceUserHandlesPreview() = PreviewPage(PreviewPlanState.copy(planMeals = false))

@Preview(name = "Meal choice · no Meal fits", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun PlanningMealChoiceNoMealFitsPreview() = PreviewPage(
    PlanningMealChoiceUiState(loading = false, noMealFits = true),
    hours = ItineraryHours(LocalTime.of(14, 0), LocalTime.of(17, 0)),
)

@Preview(name = "Meal choice · time error", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun PlanningMealChoiceTimeErrorPreview() = PreviewPage(
    PlanningMealChoiceUiState(
        loading = false,
        options = listOf(
            previewOption(MealKind.Lunch, selected = true, start = LocalTime.of(18, 0), error = MealTimeError.Overlaps),
            previewOption(MealKind.Dinner, selected = true, error = MealTimeError.Overlaps),
            previewOption(MealKind.LateBite, selected = true, start = LocalTime.of(23, 0), error = MealTimeError.OutsideWindow),
        ),
    ),
)

@Preview(name = "Meal choice · loading", showBackground = true, widthDp = 412, heightDp = 500)
@Composable
private fun PlanningMealChoiceLoadingPreview() = PreviewPage(PlanningMealChoiceUiState(), hours = null)

@Preview(name = "Meal choice · load error", showBackground = true, widthDp = 412, heightDp = 500)
@Composable
private fun PlanningMealChoiceErrorPreview() = PreviewPage(
    PlanningMealChoiceUiState(loading = false, loadFailed = true),
    hours = null,
)
