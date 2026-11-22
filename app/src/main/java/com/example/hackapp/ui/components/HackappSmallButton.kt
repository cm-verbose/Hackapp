package com.example.hackapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.ui.theme.HackappTheme

/*
 * The M3 Design Kit "Button · Round · Small" instances used inside the Qindong cards:
 * filled (Button), tonal (Button - tonal) and text (Button - text). label/large, 40 dp high.
 */

private val SmallButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
private val SmallButtonMinHeight = 40.dp

/** Filled button (primary). */
@Composable
fun HackappSmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = SmallButtonMinHeight),
        contentPadding = SmallButtonPadding,
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Tonal button (secondary container). */
@Composable
fun HackappSmallTonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = SmallButtonMinHeight),
        contentPadding = SmallButtonPadding,
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Text button (primary label, no container). */
@Composable
fun HackappSmallTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = modifier.heightIn(min = SmallButtonMinHeight),
        contentPadding = SmallButtonPadding,
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Preview(name = "Button · Round · Small", showBackground = true)
@Composable
private fun HackappSmallButtonPreview() = HackappTheme {
    HackappSmallButton(text = "Planifier un itinéraire", onClick = {})
}

@Preview(name = "Button - tonal · Round · Small", showBackground = true)
@Composable
private fun HackappSmallTonalButtonPreview() = HackappTheme {
    HackappSmallTonalButton(text = "Ajouter en 3e", onClick = {})
}

@Preview(name = "Button - text · Round · Small", showBackground = true)
@Composable
private fun HackappSmallTextButtonPreview() = HackappTheme {
    HackappSmallTextButton(text = "Choisir un autre", onClick = {})
}

@Preview(name = "Buttons · all", showBackground = true)
@Composable
private fun AllSmallButtonsPreview() = HackappTheme {
    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HackappSmallButton("Filled", {})
        HackappSmallTonalButton("Tonal", {})
        HackappSmallTextButton("Text", {})
    }
}
