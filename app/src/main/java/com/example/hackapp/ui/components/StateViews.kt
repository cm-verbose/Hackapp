package com.example.hackapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme

@Composable
private fun StateContainer(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String = stringResource(R.string.state_loading)) {
    StateContainer(modifier) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = message })
    }
}

@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.state_empty_title),
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateContainer(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        body?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) HackappSmallTonalButton(actionLabel, onAction)
    }
}

@Composable
fun ErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.state_error_title),
    body: String? = null,
) {
    StateContainer(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        body?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        HackappSmallButton(stringResource(R.string.action_retry), onRetry)
    }
}

@Preview(name = "State views", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun StateViewsPreview() = HackappTheme {
    Column {
        LoadingState()
        EmptyState()
        ErrorState(onRetry = {})
    }
}
