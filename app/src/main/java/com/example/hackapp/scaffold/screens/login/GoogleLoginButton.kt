package com.example.hackapp.scaffold.screens.login

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.auth.AuthHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Google OAuth Login button for logging in a user. */
@Composable
fun GoogleLoginButton(onNavigateNext: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = CoroutineScope(Dispatchers.Main)
    val authHandler = AuthHandler(context, activity!!)
    val signInText = stringResource(R.string.text_sign_in_with_google)

    Button(onClick = {
        scope.launch {
            authHandler.signIn(onSignIn = { user ->
                onNavigateNext()
            })
        }
    }) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val resource = painterResource(R.drawable.ic_google_logo)
            Icon(painter = resource, contentDescription = signInText)
            Text(
                signInText,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize
            )
        }
    }
}
