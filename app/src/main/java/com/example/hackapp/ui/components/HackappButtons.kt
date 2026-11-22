package com.example.hackapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme

/* Figma "Button · Round · Medium": the 56 dp pills used in each screen's BottomActions. */

private val PagePadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
private val PageButtonMinHeight = 56.dp

@Composable
fun HackappPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        contentPadding = PagePadding,
        modifier = modifier.heightIn(min = PageButtonMinHeight),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun HackappOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PagePadding,
        modifier = modifier.heightIn(min = PageButtonMinHeight),
    ) { Text(text, style = MaterialTheme.typography.titleSmall) }
}

@Composable
fun HackappTextActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
        modifier = modifier.heightIn(min = 48.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Preview(name = "Page buttons", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun HackappButtonsPreview() = HackappTheme {
    Column(
        Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HackappPrimaryButton(stringResource(R.string.action_continue), {})
        HackappOutlineButton(stringResource(R.string.auth_continue_with_google), {})
        HackappTextActionButton(stringResource(R.string.auth_forgot_password), {})
    }
}
