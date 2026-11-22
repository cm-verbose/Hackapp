package com.example.hackapp.scaffold.screens.login

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.auth.AuthError
import com.example.hackapp.auth.AuthHandler
import kotlinx.coroutines.launch

/** Google login button. [onSignedIn] receives whether this created a new account. */
@Composable
fun GoogleLoginButton(
    onSignedIn: (isNewUser: Boolean) -> Unit,
    onError: (AuthError) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val authHandler = remember(context, activity) { activity?.let { AuthHandler(context, it) } }

    OutlinedButton(
        onClick = { scope.launch { authHandler?.signIn(onSignedIn, onError) } },
        enabled = enabled && authHandler != null,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_google_logo),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp),
            )
            Text(stringResource(R.string.auth_continue_with_google), style = MaterialTheme.typography.titleMedium)
        }
    }
}
