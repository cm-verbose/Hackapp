package com.example.hackapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme

/**
 * When the person leaves for the next Stop, as a split pill: the planned time or Now. The chosen
 * one is the start the arrival estimate uses. Once the planned time has passed, only Now is left.
 *
 * [color] is the text colour of the card it sits on; [background] is that card's colour, used for
 * the text of the selected segment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartureChoice(
    plannedTime: String,
    departsNow: Boolean,
    onChoose: (now: Boolean) -> Unit,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier,
    plannedAvailable: Boolean = true,
    label: String = stringResource(R.string.leg_depart),
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        HackappSegmentedButton(
            options = listOf(
                ChoiceOption(false, plannedTime, enabled = plannedAvailable),
                ChoiceOption(true, stringResource(R.string.leg_now)),
            ),
            selected = departsNow || !plannedAvailable,
            onSelectedChange = onChoose,
            maxLines = 1,
            colors = SegmentedButtonDefaults.colors(
                activeContainerColor = color,
                activeContentColor = background,
                activeBorderColor = color,
                inactiveContainerColor = Color.Transparent,
                inactiveContentColor = color,
                inactiveBorderColor = color,
                disabledInactiveContainerColor = Color.Transparent,
                disabledInactiveContentColor = color.copy(alpha = 0.38f),
                disabledInactiveBorderColor = color.copy(alpha = 0.38f),
            ),
        )
    }
}

@Preview(name = "DepartureChoice", showBackground = true, backgroundColor = PreviewDefaults.HERO_CARD, widthDp = 364)
@Composable
private fun DepartureChoicePreview() = HackappTheme {
    val colors = HackappTheme.appColors
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DepartureChoice("18:05", departsNow = false, onChoose = {}, color = colors.onHeroCard, background = colors.heroCard)
        DepartureChoice("18:05", departsNow = true, onChoose = {}, color = colors.onHeroCard, background = colors.heroCard)
        DepartureChoice(
            "17:40",
            departsNow = true,
            onChoose = {},
            color = colors.onHeroCard,
            background = colors.heroCard,
            plannedAvailable = false,
        )
    }
}
