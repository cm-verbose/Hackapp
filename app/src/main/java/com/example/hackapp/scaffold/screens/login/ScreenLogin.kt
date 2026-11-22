package com.example.hackapp.scaffold.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.auth.AuthError
import com.example.hackapp.auth.AuthResult
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.ui.components.HackappIcon
import com.example.hackapp.ui.components.HackappIconView
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappTextActionButton
import com.example.hackapp.ui.components.HackappTextField
import com.example.hackapp.ui.theme.HackappTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object ScreenLoginDestination

/**
 * Figma "WelcomeScreen" (28:3). The frame uses the dark colour scheme on the Hero background,
 * whatever the system theme is. Actions stay pinned so Sign in is visible on short screens.
 */
@Composable
fun ScreenLogin(
    onSignedIn: (isNewUser: Boolean) -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val alreadySignedIn = remember { FirebaseAuth.getInstance().currentUser != null }
    LaunchedEffect(alreadySignedIn) {
        if (alreadySignedIn) onSignedIn(false)
    }
    if (alreadySignedIn) return

    HackappTheme(darkTheme = true) {
        WelcomeContent(onSignedIn, onRegister, onForgotPassword)
    }
}

@Composable
private fun WelcomeContent(
    onSignedIn: (isNewUser: Boolean) -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val authService = LocalAuthService.current
    val scope = rememberCoroutineScope()
    val colors = HackappTheme.appColors
    val scheme = MaterialTheme.colorScheme
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<AuthError?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.hero)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .background(scheme.primary, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = scheme.onPrimary)
                HackappIconView(HackappIcon.Star, size = 22.dp, tint = scheme.onPrimary)
            }
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.displaySmall, color = colors.onHero)
            Text(stringResource(R.string.welcome_tagline), style = MaterialTheme.typography.bodyLarge, color = colors.onHeroCard)

            GoogleLoginButton(
                onSignedIn = onSignedIn,
                onError = { error = it },
                enabled = !busy,
                contentColor = scheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.label_or),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
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
                imeAction = ImeAction.Done,
                enabled = !busy,
            )
            HackappTextActionButton(stringResource(R.string.auth_forgot_password), onForgotPassword, enabled = !busy)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AuthErrorMessage(error)
            HackappPrimaryButton(
                text = stringResource(R.string.auth_sign_in),
                enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        when (val result = authService.signIn(email, password)) {
                            AuthResult.Success -> onSignedIn(false)
                            is AuthResult.Failure -> error = result.error
                        }
                        busy = false
                    }
                },
            )
            HackappTextActionButton(stringResource(R.string.auth_create_account), onRegister, enabled = !busy)
        }
    }
}
