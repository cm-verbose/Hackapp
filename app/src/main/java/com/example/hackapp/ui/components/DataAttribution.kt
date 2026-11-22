package com.example.hackapp.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme

/** Figma "DataAttribution" (53:1485): required data-source credit under Event lists and maps. */
@Composable
fun DataAttribution(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.data_attribution),
) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview(name = "DataAttribution", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun DataAttributionPreview() = HackappTheme {
    DataAttribution()
}
