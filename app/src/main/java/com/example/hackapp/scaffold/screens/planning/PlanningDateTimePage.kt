package com.example.hackapp.scaffold.screens.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.EventCoverage
import com.example.hackapp.itinerary.LocalItineraryRepository
import com.example.hackapp.itinerary.OutingKind
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.LocalProfileRepository
import com.example.hackapp.services.LocalCityServices
import com.example.hackapp.ui.components.ChoiceOption
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.HackappDateField
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappPickerField
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappSegmentedButton
import com.example.hackapp.ui.components.HackappTimeField
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.theme.HackappTheme
import java.time.LocalDate
import java.time.LocalTime

/** Connects the Date and time page to its ViewModel and to navigation. */
@Composable
fun PlanningDateTimeScreen(
    itineraryId: String?,
    onBack: () -> Unit,
    onContinue: (String) -> Unit,
) {
    val services = LocalCityServices.current
    val viewModel: PlanningDateTimeViewModel = viewModel(
        factory = PlanningDateTimeViewModel.factory(
            routeItineraryId = itineraryId,
            auth = LocalAuthService.current,
            itineraries = LocalItineraryRepository.current,
            profiles = LocalProfileRepository.current,
            events = services.events,
            cityPack = services.cityPack,
        ),
    )
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.savedItineraryId) {
        state.savedItineraryId?.let { id ->
            viewModel.onNavigated()
            onContinue(id)
        }
    }
    PlanningDateTimePage(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onDateChange = viewModel::setDate,
        onOutingKindChange = viewModel::setOutingKind,
        onStartChange = viewModel::setStart,
        onEndChange = viewModel::setEnd,
        onBudgetChange = viewModel::setEventBudget,
        onBudgetReset = viewModel::resetEventBudget,
        onContinue = viewModel::save,
    )
}

@Composable
fun PlanningDateTimePage(
    state: PlanningDateTimeUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onOutingKindChange: (OutingKind) -> Unit,
    onStartChange: (LocalTime) -> Unit,
    onEndChange: (LocalTime) -> Unit,
    onBudgetChange: (BudgetBracket) -> Unit,
    onBudgetReset: () -> Unit,
    onContinue: () -> Unit,
) {
    HackappPage(
        title = stringResource(R.string.planning_new_itinerary),
        onBack = onBack,
        step = 1,
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
                Text(stringResource(R.string.planning_when_title), style = MaterialTheme.typography.headlineSmall)
                HackappDateField(
                    value = state.date,
                    onValueChange = onDateChange,
                    label = stringResource(R.string.planning_date),
                    firstDate = state.coverage.firstDate,
                    lastDate = state.coverage.lastDate,
                )
                HackappSegmentedButton(
                    options = listOf(
                        ChoiceOption(OutingKind.Day, stringResource(R.string.planning_day_out)),
                        ChoiceOption(OutingKind.Night, stringResource(R.string.planning_night_out)),
                    ),
                    selected = state.outingKind,
                    onSelectedChange = onOutingKindChange,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HackappTimeField(state.start, onStartChange, stringResource(R.string.planning_start), Modifier.weight(1f))
                    HackappTimeField(state.end, onEndChange, stringResource(R.string.planning_end), Modifier.weight(1f))
                }
                state.windowError?.let { error ->
                    Text(
                        stringResource(
                            when (error) {
                                TimeWindowError.TooShort -> R.string.planning_error_min_duration
                                TimeWindowError.EndsTooLate -> R.string.planning_error_latest_end
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                EventBudgetField(state.eventBudget, state.profileBudget, onBudgetChange, onBudgetReset)
                Text(
                    stringResource(R.string.planning_time_window_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EventBudgetField(
    budget: BudgetBracket?,
    profileBudget: BudgetBracket?,
    onChange: (BudgetBracket) -> Unit,
    onReset: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = budget?.let { budgetLabel(it) }.orEmpty()
    val value = if (budget != null && budget == profileBudget) {
        stringResource(R.string.planning_budget_from_profile, label)
    } else {
        label
    }
    Box(Modifier.fillMaxWidth()) {
        HackappPickerField(value, stringResource(R.string.planning_budget_override), onClick = { open = true })
        if (profileBudget != null && budget != profileBudget) {
            IconButton(onClick = onReset, modifier = Modifier.align(Alignment.CenterEnd).padding(top = 4.dp)) {
                Icon(Icons.Outlined.Cancel, contentDescription = stringResource(R.string.planning_budget_reset))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BudgetBracket.entries.forEach { bracket ->
                DropdownMenuItem(
                    text = { Text(budgetLabel(bracket)) },
                    onClick = {
                        onChange(bracket)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun budgetLabel(bracket: BudgetBracket): String = stringResource(
    when (bracket) {
        BudgetBracket.Under20 -> R.string.budget_under_20
        BudgetBracket.Under50 -> R.string.budget_under_50
        BudgetBracket.Under100 -> R.string.budget_under_100
        BudgetBracket.From100 -> R.string.budget_100_plus
    },
)

private val PreviewCoverage = EventCoverage(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 12, 6))

@Composable
private fun PreviewPage(state: PlanningDateTimeUiState) = HackappTheme {
    PlanningDateTimePage(state, {}, {}, {}, {}, {}, {}, {}, {}, {})
}

@Preview(name = "Date and time", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun PlanningDateTimePreview() = PreviewPage(
    PlanningDateTimeUiState(
        loading = false,
        coverage = PreviewCoverage,
        date = LocalDate.of(2026, 11, 28),
        profileBudget = BudgetBracket.Under50,
        eventBudget = BudgetBracket.Under50,
    ),
)

@Preview(name = "Date and time · invalid window", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun PlanningDateTimeInvalidPreview() = PreviewPage(
    PlanningDateTimeUiState(
        loading = false,
        coverage = PreviewCoverage,
        date = LocalDate.of(2026, 11, 28),
        end = LocalTime.of(5, 0),
        profileBudget = BudgetBracket.Under50,
        eventBudget = BudgetBracket.Under100,
    ),
)

@Preview(name = "Date and time · loading", showBackground = true, widthDp = 412, heightDp = 500)
@Composable
private fun PlanningDateTimeLoadingPreview() = PreviewPage(PlanningDateTimeUiState(coverage = PreviewCoverage))

@Preview(name = "Date and time · error", showBackground = true, widthDp = 412, heightDp = 500)
@Composable
private fun PlanningDateTimeErrorPreview() = PreviewPage(
    PlanningDateTimeUiState(loading = false, loadFailed = true, coverage = PreviewCoverage),
)
