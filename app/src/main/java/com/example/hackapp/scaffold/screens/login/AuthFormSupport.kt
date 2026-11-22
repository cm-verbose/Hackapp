package com.example.hackapp.scaffold.screens.login

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.hackapp.R
import com.example.hackapp.auth.AuthError

/** Announced to screen readers when it appears. */
@Composable
internal fun AuthErrorMessage(error: AuthError?, modifier: Modifier = Modifier) {
    if (error == null) return
    val message = stringResource(
        when (error) {
            AuthError.InvalidCredentials -> R.string.auth_error_invalid_credentials
            AuthError.InvalidEmail -> R.string.auth_error_invalid_email
            AuthError.EmailInUse -> R.string.auth_error_email_in_use
            AuthError.WeakPassword -> R.string.auth_error_weak_password
            AuthError.Network -> R.string.auth_error_network
            AuthError.ProviderDisabled -> R.string.auth_error_provider_disabled
            AuthError.GoogleFailed -> R.string.auth_error_google
            AuthError.Unknown -> R.string.auth_error_unknown
        },
    )
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
