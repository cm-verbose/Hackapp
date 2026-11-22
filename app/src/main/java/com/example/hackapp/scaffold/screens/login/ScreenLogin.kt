package com.example.hackapp.scaffold.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.auth.AuthError
import com.example.hackapp.auth.AuthResult
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappTextActionButton
import com.example.hackapp.ui.components.HackappTextField
import com.example.hackapp.ui.theme.HackappTheme
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object ScreenLoginDestination

/**
 * Figma City Packs "WelcomeScreen · Montréal" (66:400): "Plan" + the City Pack logo, then the
 * sign-in form. The frame uses the dark colour scheme on the Hero background, whatever the system
 * theme is. Actions stay pinned so Sign in is visible on short screens.
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
                .padding(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlanCityTitle(Modifier.fillMaxWidth())

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GoogleLoginButton(
                    onSignedIn = onSignedIn,
                    onError = { error = it },
                    enabled = !busy,
                    contentColor = scheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
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
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
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

/**
 * Figma "Title · Plan + logo" (190:1172): the word "Plan" and the City Pack logo, centred
 * vertically on each other. As in Figma, "Plan" is measured by its cap height (the text box is
 * trimmed to cap height and baseline), so its capitals, not its line box, are centred on the logo.
 * Read as the app name by screen readers.
 */
@Composable
private fun PlanCityTitle(modifier: Modifier = Modifier) {
    val appName = stringResource(R.string.app_name)
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = appName
            heading()
        },
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MaterialTheme.typography.displayMedium
        Text(
            stringResource(R.string.brand_plan),
            style = style,
            color = HackappTheme.appColors.onHero,
            modifier = Modifier.trimToCapHeight(style.fontSize),
        )
        Image(
            painter = painterResource(LocalCityPack.current.logo),
            contentDescription = null,
            // The Figma scale of the logo (cropped vector mark, no padding).
            modifier = Modifier.size(width = 81.8.dp, height = 53.2.dp),
        )
    }
}

/** Figtree's cap height (700 of 1000 units, OS/2 table of res/font/figtree.ttf). */
private const val FIGTREE_CAP_HEIGHT_EM = 0.70f

/**
 * Lays the text out in a box from its cap height down to its baseline, like Figma's
 * "Vertical trim: Cap height to baseline". One line of text only.
 */
private fun Modifier.trimToCapHeight(fontSize: TextUnit): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val baseline = placeable[FirstBaseline]
    val capHeight = (fontSize.toPx() * FIGTREE_CAP_HEIGHT_EM).roundToInt()
    layout(placeable.width, capHeight) {
        placeable.placeRelative(0, capHeight - baseline)
    }
}
