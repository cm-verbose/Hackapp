package com.example.hackapp.scaffold.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.example.hackapp.R
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.ui.components.ChoiceOption
import com.example.hackapp.ui.components.SingleChoiceChips

@Composable
fun budgetBracketLabel(bracket: BudgetBracket): String = stringResource(
    when (bracket) {
        BudgetBracket.Under20 -> R.string.budget_under_20
        BudgetBracket.Under50 -> R.string.budget_under_50
        BudgetBracket.Under100 -> R.string.budget_under_100
        BudgetBracket.From100 -> R.string.budget_100_plus
    },
)

/** The Lunch, Dinner, Events and optional Late-night budgets. Selected brackets toggle off. */
@Composable
fun ColumnScope.BudgetFields(
    lunch: BudgetBracket?,
    dinner: BudgetBracket?,
    events: BudgetBracket?,
    lateNightEnabled: Boolean,
    lateNight: BudgetBracket?,
    onLunchChange: (BudgetBracket?) -> Unit,
    onDinnerChange: (BudgetBracket?) -> Unit,
    onEventsChange: (BudgetBracket?) -> Unit,
    onLateNightEnabledChange: (Boolean) -> Unit,
    onLateNightChange: (BudgetBracket?) -> Unit,
) {
    val brackets = BudgetBracket.entries.map { ChoiceOption(it, budgetBracketLabel(it)) }
    BudgetTitle(stringResource(R.string.budget_lunch))
    SingleChoiceChips(brackets, lunch, onLunchChange, allowDeselect = true)
    BudgetTitle(stringResource(R.string.budget_dinner))
    SingleChoiceChips(brackets, dinner, onDinnerChange, allowDeselect = true)
    BudgetTitle(stringResource(R.string.budget_events))
    SingleChoiceChips(brackets, events, onEventsChange, allowDeselect = true)
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(lateNightEnabled, role = Role.Switch, onValueChange = onLateNightEnabledChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BudgetTitle(stringResource(R.string.budget_late_night))
        }
        Switch(checked = lateNightEnabled, onCheckedChange = null)
    }
    if (lateNightEnabled) SingleChoiceChips(brackets, lateNight, onLateNightChange, allowDeselect = true)
}

@Composable
private fun BudgetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}
