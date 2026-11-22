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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch

/** Figma "RegisterScreen" (28:63). */
@Composable
fun ScreenRegister(
    onBack: () -> Unit,
    onSignedIn: (isNewUser: Boolean) -> Unit,
) {
    val authService = LocalAuthService.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<AuthError?>(null) }
    var busy by remember { mutableStateOf(false) }
    val mismatch = confirm.isNotEmpty() && confirm != password

    HackappPage(
        title = stringResource(R.string.auth_create_account),
        onBack = onBack,
        bottomActions = {
            HackappPrimaryButton(
                text = stringResource(R.string.register_submit),
                enabled = !busy && email.isNotBlank() && password.isNotEmpty() && confirm == password,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        when (val result = authService.register(email, password)) {
                            AuthResult.Success -> onSignedIn(true)
                            is AuthResult.Failure -> error = result.error
                        }
                        busy = false
                    }
                },
            )
        },
    ) {
        Text(
            stringResource(R.string.register_one_account_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HackappTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = stringResource(R.string.auth_email),
            keyboardType = KeyboardType.Email,
            enabled = !busy,
        )
        HackappTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = stringResource(R.string.auth_password),
            isPassword = true,
            enabled = !busy,
        )
        HackappTextField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            label = stringResource(R.string.auth_password_confirm),
            isPassword = true,
            imeAction = ImeAction.Done,
            enabled = !busy,
            errorText = if (mismatch) stringResource(R.string.auth_error_password_mismatch) else null,
        )
        AuthErrorMessage(error)
        GoogleLoginButton(
            onSignedIn = onSignedIn,
            onError = { error = it },
            enabled = !busy,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
