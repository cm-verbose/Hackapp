package com.example.hackapp.scaffold.screens.login

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.hackapp.R
import com.example.hackapp.auth.AuthError
import com.example.hackapp.auth.AuthResult
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappTextField
import com.example.hackapp.ui.components.InfoCallout
import kotlinx.coroutines.launch

/** Password reset. Figma has its strings but no frame, so it follows RegisterScreen's layout. */
@Composable
fun ScreenPasswordReset(onBack: () -> Unit) {
    val authService = LocalAuthService.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<AuthError?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf(false) }

    HackappPage(
        title = stringResource(R.string.password_reset_title),
        onBack = onBack,
        bottomActions = {
            HackappPrimaryButton(
                text = stringResource(R.string.password_reset_send),
                enabled = !busy && email.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    sent = false
                    scope.launch {
                        when (val result = authService.sendPasswordReset(email)) {
                            AuthResult.Success -> sent = true
                            is AuthResult.Failure -> error = result.error
                        }
                        busy = false
                    }
                },
            )
        },
    ) {
        Text(
            stringResource(R.string.password_reset_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HackappTextField(
            value = email,
            onValueChange = { email = it; error = null; sent = false },
            label = stringResource(R.string.auth_email),
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            enabled = !busy,
        )
        AuthErrorMessage(error)
        if (sent) InfoCallout(stringResource(R.string.password_reset_sent))
    }
}
