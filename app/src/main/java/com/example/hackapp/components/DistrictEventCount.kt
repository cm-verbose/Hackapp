package com.example.hackapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma "DistrictEventCount" (29:345): circle counting the matching Events in a District on the map.
 * Primary fill, onPrimary label/large text, 32 dp (Figma).
 *
 * Originally Eric's; now maintained by the business team. The signature (`result`, `badgeColor`,
 * `badgeSize`) is kept so `ui/components/map/DistrictMap.kt` keeps compiling.
 * Pass `outline` as [badgeColor] for a District with no matching Event.
 */
@Composable
fun DistrictEventCount(
    result: Int = 0,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    badgeSize: Dp = 32.dp,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    // The text is a fraction of the badge in dp, not sp, so font scaling never pushes "99+" out of the disc.
    val fontSize = with(LocalDensity.current) { (badgeSize * 0.44f).toSp() }
    Box(
        modifier
            .size(badgeSize)
            .background(badgeColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (result > 99) "99+" else result.toString(),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize, lineHeight = fontSize, letterSpacing = 0.sp),
            color = textColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Preview(name = "DistrictEventCount", showBackground = true, backgroundColor = 0xFFF0F0F1)
@Composable
private fun DistrictEventCountPreview() = HackappTheme {
    DistrictEventCount(result = 8, modifier = Modifier.padding(8.dp))
}

@Preview(name = "DistrictEventCount · states", showBackground = true, backgroundColor = 0xFFF0F0F1)
@Composable
private fun DistrictEventCountStatesPreview() = HackappTheme {
    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DistrictEventCount(result = 8)
        DistrictEventCount(result = 22)
        DistrictEventCount(result = 140)
        DistrictEventCount(result = 140, badgeSize = 24.dp)
        DistrictEventCount(result = 0, badgeColor = MaterialTheme.colorScheme.outline)
    }
}
