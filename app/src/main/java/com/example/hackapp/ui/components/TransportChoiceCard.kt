package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.hackapp.citypack.MontrealCityPack
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme
import java.util.Locale

enum class TransportChoice { Transit, RideHail }

/**
 * Figma "TransportChoiceCard" (38:1186). [rideHailName], [fareLabel] and [fareAgency] come from the City Pack.
 * The fare row shows only for public transit.
 */
@Composable
fun TransportChoiceCard(
    selected: TransportChoice?,
    onSelect: (TransportChoice) -> Unit,
    rideHailName: String,
    fareLabel: String,
    fareAgency: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HackappCorners.Large))
            .background(scheme.surfaceContainerLowest)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.leg_how_to_get_there),
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
        )
        HackappSegmentedButton<TransportChoice?>(
            options = listOf(
                ChoiceOption<TransportChoice?>(TransportChoice.Transit, stringResource(R.string.leg_mode_transit)),
                ChoiceOption<TransportChoice?>(TransportChoice.RideHail, rideHailName),
            ),
            selected = selected,
            onSelectedChange = { choice -> choice?.let(onSelect) },
        )
        if (selected == TransportChoice.Transit) {
            TransitFareRow(
                stringResource(R.string.leg_fare_required, fareLabel, fareAgency),
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "TransportChoiceCard", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun TransportChoiceCardPreview() = HackappTheme {
    TransportChoiceCard(
        selected = TransportChoice.Transit,
        onSelect = {},
        rideHailName = MontrealCityPack.rideHail.displayName,
        fareLabel = MontrealCityPack.fareTable.singleRide.get(Locale.getDefault().language).orEmpty(),
        fareAgency = MontrealCityPack.fareTable.agencyName,
    )
}
