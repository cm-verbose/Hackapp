package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme

/** Figma "App bar". [onBack] null hides the back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HackappTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            titleContentColor = contentColor,
            navigationIconContentColor = contentColor,
        ),
    )
}

/** Figma's progress bar under the app bar: a filled segment and the remaining track. */
@Composable
fun StepProgress(current: Int, total: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val done = current.coerceIn(0, total)
    val description = stringResource(R.string.onboarding_step, done, total)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (done > 0) {
            Box(
                Modifier
                    .weight(done.toFloat())
                    .height(4.dp)
                    .background(scheme.primary, CircleShape),
            )
        }
        if (done < total) {
            Box(
                Modifier
                    .weight((total - done).toFloat())
                    .height(4.dp)
                    .background(scheme.secondaryContainer, CircleShape),
            )
        }
    }
}

/** Figma "BottomActions": the actions pinned to the bottom of a screen. */
@Composable
fun BottomActions(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * Standard page: app bar, optional step progress, scrolling content and pinned bottom actions.
 * Pages with a bottom navigation bar pass no [bottomActions] and let the host add the bar.
 */
@Composable
fun HackappPage(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    step: Int? = null,
    totalSteps: Int? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    bottomActions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(containerColor)
            .imePadding(),
    ) {
        HackappTopBar(title, onBack = onBack, containerColor = containerColor, contentColor = contentColor)
        if (step != null && totalSteps != null) StepProgress(step, totalSteps)
        val scrollState = rememberScrollState()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        if (bottomActions != null) {
            if (scrollState.canScrollForward) HorizontalDivider()
            BottomActions(content = bottomActions)
        }
    }
}

@Preview(name = "HackappPage", showBackground = true, widthDp = 412, heightDp = 500)
@Composable
private fun HackappPagePreview() = HackappTheme {
    HackappPage(
        title = stringResource(R.string.profile_title),
        onBack = {},
        step = 2,
        totalSteps = 3,
        bottomActions = { HackappPrimaryButton(stringResource(R.string.action_continue), {}) },
    ) {
        Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineSmall)
    }
}
