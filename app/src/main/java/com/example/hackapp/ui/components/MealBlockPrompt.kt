package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma "MealBlockPrompt" (53:1267), PlanningStopSelectionScreen: a reserved 90-min Meal Block
 * waiting for a restaurant or bar. [start] and [end] are formatted times ("19:00").
 */
@Composable
fun MealBlockPrompt(
    start: String,
    end: String,
    onChooseMeal: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.selection_meal_block_title, start, end),
    body: String = stringResource(R.string.selection_meal_block_body),
    actionLabel: String = stringResource(R.string.selection_choose_meal),
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(HackappCorners.Large))
            .background(scheme.tertiaryContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onTertiaryContainer)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onTertiaryContainer)
        HackappSmallButton(text = actionLabel, onClick = onChooseMeal)
    }
}

@Preview(name = "MealBlockPrompt", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun MealBlockPromptPreview() = HackappTheme {
    MealBlockPrompt(start = "19:00", end = "20:30", onChooseMeal = {})
}
