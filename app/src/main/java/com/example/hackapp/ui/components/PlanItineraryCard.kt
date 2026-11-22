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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import com.example.hackapp.ui.motion.floatingOrbs
import com.example.hackapp.ui.motion.shimmerSweep
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma "PlanItineraryCard" (53:1200), HomeScreen: call to start planning.
 * [cityName] comes from the active City Pack (never hard-code the city).
 */
@Composable
fun PlanItineraryCard(
    cityName: String,
    onPlan: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.home_plan_title),
    body: String = stringResource(R.string.home_plan_body, cityName),
    actionLabel: String = stringResource(R.string.home_plan_action),
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(HackappCorners.ExtraLarge))
            .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer.copy(alpha = 0.55f).compositeOver(scheme.primaryContainer))))
            .floatingOrbs(scheme.primary.copy(alpha = 0.30f), scheme.tertiary.copy(alpha = 0.25f))
            .shimmerSweep()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = scheme.onPrimaryContainer)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = scheme.onPrimaryContainer)
        HackappSmallButton(text = actionLabel, onClick = onPlan)
    }
}

@Preview(name = "PlanItineraryCard", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun PlanItineraryCardPreview() = HackappTheme {
    PlanItineraryCard(cityName = "Montréal", onPlan = {})
}
