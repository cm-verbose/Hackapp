package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.itinerary.BudgetSummary
import com.example.hackapp.itinerary.MealAmount
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme

/** Keeps the dot on the line of the amount before it. */
private const val TOTAL_SEPARATOR = "\u00A0· "

/**
 * Figma "RunningTotal" (141:4091). Over budget warns only, so the actions around it stay available.
 * [showBreakdown] adds the line per Events and Meal. [announceChanges] reads updates to TalkBack,
 * for pages where the total changes while the person works.
 */
@Composable
fun RunningTotalCard(
    budget: BudgetSummary,
    modifier: Modifier = Modifier,
    showBreakdown: Boolean = true,
    announceChanges: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val over = budget.isOverBudget
    val strong = if (over) scheme.onErrorContainer else scheme.onSurface
    val quiet = if (over) scheme.onErrorContainer else scheme.onSurfaceVariant
    val limit = budget.totalBudgetCad
    Row(
        modifier
            .fillMaxWidth()
            .background(
                if (over) scheme.errorContainer else scheme.surfaceContainerHigh,
                RoundedCornerShape(HackappCorners.Medium),
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                if (announceChanges) liveRegion = LiveRegionMode.Polite
            },
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (over && limit != null) {
                        stringResource(R.string.running_total_over, formatCad(budget.runningTotalCad - limit))
                    } else {
                        stringResource(R.string.running_total_label)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = quiet,
                    modifier = Modifier.weight(1f),
                )
                limit?.let {
                    Text(
                        stringResource(R.string.running_total_budget, formatCad(it)),
                        style = MaterialTheme.typography.labelMedium,
                        color = quiet,
                    )
                }
            }
            Text(totalAmount(budget), style = MaterialTheme.typography.titleMedium, color = strong)
            if (showBreakdown) {
                val detail = if (over) stringResource(R.string.running_total_over_hint) else totalBreakdown(budget)
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = quiet)
            }
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
 * "Events 20 $ · Lunch 50 $ · Dinner 30 $". A Meal with an unknown price is left out, because the
 * total already says "+ unknown price". So are Events when none has a known price.
 */
@Composable
private fun totalBreakdown(budget: BudgetSummary): String {
    val events = stringResource(R.string.running_total_events, formatCad(budget.eventsCad))
        .takeUnless { budget.eventsCad == 0.0 && budget.hasUnknownPrices }
    val meals = budget.meals.mapNotNull { meal ->
        val kind = meal.kind ?: return@mapNotNull null
        val price = meal.priceCad ?: return@mapNotNull null
        stringResource(
            when (kind) {
                MealKind.Lunch -> R.string.running_total_lunch
                MealKind.Dinner -> R.string.running_total_dinner
                MealKind.LateBite -> R.string.running_total_late_bite
            },
            formatCad(price),
        )
    }
    return (listOfNotNull(events) + meals).joinToString(TOTAL_SEPARATOR)
}

@Preview(name = "RunningTotalCard", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun RunningTotalCardPreview() = HackappTheme {
    val within = BudgetSummary(
        eventBudgetCad = 100,
        mealBudgetCad = 70,
        totalBudgetCad = 170,
        eventsCad = 60.0,
        mealsCad = 30.0,
        unknownPriceCount = 0,
        isOverBudget = false,
        meals = listOf(MealAmount("m", "meal-dinner", MealKind.Dinner, 30.0, 50)),
    )
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RunningTotalCard(within)
        RunningTotalCard(within, showBreakdown = false, announceChanges = false)
        RunningTotalCard(within.copy(eventsCad = 0.0, mealsCad = 0.0, unknownPriceCount = 2, meals = emptyList()))
        RunningTotalCard(within.copy(eventsCad = 170.0, unknownPriceCount = 1, isOverBudget = true))
    }
}
