package com.example.hackapp.scaffold.screens.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.itinerary.LocalItineraryRepository
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.LocalProfileRepository
import com.example.hackapp.services.LocalCityServices
import com.example.hackapp.scaffold.screens.BudgetFields
import com.example.hackapp.scaffold.screens.budgetBracketLabel
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.HackappOutlineButton
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme

private const val BUDGET_STEP = 2

@Composable
private fun planningBudgetViewModel(itineraryId: String): PlanningBudgetViewModel = viewModel(
    factory = PlanningBudgetViewModel.factory(
        itineraryId = itineraryId,
        auth = LocalAuthService.current,
        itineraries = LocalItineraryRepository.current,
        profiles = LocalProfileRepository.current,
        reconciler = LocalCityServices.current.stopFitReconciler(),
    ),
)

/** Figma "PlanningBudgetChoiceScreen": keep the current budget or change it for this outing. */
@Composable
fun PlanningBudgetChoiceScreen(
    itineraryId: String,
    onBack: () -> Unit,
    onKept: (String) -> Unit,
    onChange: () -> Unit,
) {
    val viewModel = planningBudgetViewModel(itineraryId)
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.continueToId) {
        state.continueToId?.let { id ->
            viewModel.onNavigated()
            onKept(id)
        }
    }
    StopChangeWarningDialog(state.changeWarning, viewModel::confirmChange, viewModel::cancelChange)
    PlanningBudgetChoicePage(state, onBack, viewModel::retry, viewModel::keep, onChange)
}

@Composable
fun PlanningBudgetChoicePage(
    state: PlanningBudgetUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onKeep: () -> Unit,
    onChange: () -> Unit,
) {
    val ready = !state.loading && !state.loadFailed
    HackappPage(
        title = stringResource(R.string.planning_budget_title),
        onBack = onBack,
        step = BUDGET_STEP,
        totalSteps = PLANNING_STEPS,
        bottomActions = {
            SaveError(state.saveFailed)
            HackappPrimaryButton(
                text = stringResource(R.string.planning_budget_keep),
                onClick = onKeep,
                enabled = ready && !state.saving,
            )
            HackappOutlineButton(
                text = stringResource(R.string.planning_budget_change),
                onClick = onChange,
                enabled = ready && !state.saving,
            )
        },
    ) {
        when {
            state.loading -> LoadingState()
            state.loadFailed -> ErrorState(onRetry = onRetry)
            else -> {
                CurrentBudgetCard(state.selection)
            }
        }
    }
}

@Composable
private fun CurrentBudgetCard(selection: BudgetSelection) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(HackappCorners.Medium),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.budget_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BudgetValueRow(stringResource(R.string.budget_lunch), bracketText(selection.lunch))
            BudgetValueRow(stringResource(R.string.budget_dinner), bracketText(selection.dinner))
            BudgetValueRow(stringResource(R.string.budget_events), bracketText(selection.events))
            BudgetValueRow(
                stringResource(R.string.budget_late_night),
                if (selection.lateNightEnabled) bracketText(selection.lateNight) else stringResource(R.string.budget_off),
            )
        }
    }
}

@Composable
private fun bracketText(bracket: BudgetBracket?): String =
    bracket?.let { budgetBracketLabel(it) } ?: stringResource(R.string.budget_not_set)

@Composable
private fun BudgetValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Figma "PlanningBudgetEditScreen": budgets for this Itinerary only. */
@Composable
fun PlanningBudgetEditScreen(
    itineraryId: String,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val viewModel = planningBudgetViewModel(itineraryId)
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.continueToId) {
        state.continueToId?.let { id ->
            viewModel.onNavigated()
            onSaved(id)
        }
    }
    StopChangeWarningDialog(state.changeWarning, viewModel::confirmChange, viewModel::cancelChange)
    PlanningBudgetEditPage(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onLunchChange = viewModel::setLunch,
        onDinnerChange = viewModel::setDinner,
        onEventsChange = viewModel::setEvents,
        onLateNightEnabledChange = viewModel::setLateNightEnabled,
        onLateNightChange = viewModel::setLateNight,
        onContinue = viewModel::saveEdits,
    )
}

@Composable
fun PlanningBudgetEditPage(
    state: PlanningBudgetUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLunchChange: (BudgetBracket?) -> Unit,
    onDinnerChange: (BudgetBracket?) -> Unit,
    onEventsChange: (BudgetBracket?) -> Unit,
    onLateNightEnabledChange: (Boolean) -> Unit,
    onLateNightChange: (BudgetBracket?) -> Unit,
    onContinue: () -> Unit,
) {
    val selection = state.selection
    HackappPage(
        title = stringResource(R.string.planning_budget_title),
        onBack = onBack,
        step = BUDGET_STEP,
        totalSteps = PLANNING_STEPS,
        bottomActions = {
            SaveError(state.saveFailed)
            HackappPrimaryButton(
                text = stringResource(R.string.action_continue),
                onClick = onContinue,
                enabled = !state.loading && !state.loadFailed && !state.saving && selection.isComplete,
            )
        },
    ) {
        when {
            state.loading -> LoadingState()
            state.loadFailed -> ErrorState(onRetry = onRetry)
            else -> {
                Text(
                    stringResource(R.string.planning_budget_edit_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BudgetFields(
                    lunch = selection.lunch,
                    dinner = selection.dinner,
                    events = selection.events,
                    lateNightEnabled = selection.lateNightEnabled,
                    lateNight = selection.lateNight,
                    onLunchChange = onLunchChange,
                    onDinnerChange = onDinnerChange,
                    onEventsChange = onEventsChange,
                    onLateNightEnabledChange = onLateNightEnabledChange,
                    onLateNightChange = onLateNightChange,
                )
            }
        }
    }
}

@Composable
private fun SaveError(visible: Boolean) {
    if (visible) {
        Text(
            stringResource(R.string.planning_save_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private val PreviewBudgetState = PlanningBudgetUiState(
    loading = false,
    selection = BudgetSelection(BudgetBracket.Under20, BudgetBracket.Under50, BudgetBracket.Under50),
)

@Preview(name = "Budget choice", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun PlanningBudgetChoicePreview() = HackappTheme {
    PlanningBudgetChoicePage(PreviewBudgetState, {}, {}, {}, {})
}

@Preview(name = "Budget edit", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun PlanningBudgetEditPreview() = HackappTheme {
    PlanningBudgetEditPage(PreviewBudgetState, {}, {}, {}, {}, {}, {}, {}, {})
}
