package com.example.hackapp.scaffold.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.hackapp.R
import com.example.hackapp.ui.components.EmptyState
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappPrimaryButton

/** Temporary body for a route whose page is not built yet. Keeps the app's flow navigable. */
@Composable
fun RoutePlaceholder(
    title: String,
    onBack: (() -> Unit)?,
    step: Int? = null,
    totalSteps: Int? = null,
    onNext: (() -> Unit)? = null,
) {
    HackappPage(
        title = title,
        onBack = onBack,
        step = step,
        totalSteps = totalSteps,
        bottomActions = onNext?.let { next ->
            { HackappPrimaryButton(stringResource(R.string.action_continue), next) }
        },
    ) {
        EmptyState(title = title, body = stringResource(R.string.label_coming_soon))
    }
}
