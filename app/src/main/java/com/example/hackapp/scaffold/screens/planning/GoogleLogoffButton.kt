package com.example.hackapp.scaffold.screens.planning

import android.util.Log
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import com.example.hackapp.HackappApplication
import com.example.hackapp.R
import com.example.hackapp.auth.AUTH_TAG
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/** Signs the user out of Firebase and clears the saved Google credential choice. */
@Composable
fun rememberSignOut(onSignedOut: () -> Unit): () -> Unit {
    val application = LocalContext.current.applicationContext as HackappApplication
    val latestOnSignedOut by rememberUpdatedState(onSignedOut)
    return remember(application) {
        {
            val uid = application.currentUid()
            FirebaseAuth.getInstance().signOut()
            // Alarms and notifications of the account must not outlive its session.
            if (uid != null) {
                application.applicationScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                    try {
                        application.underwayCoordinator.onSignOut(uid)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(AUTH_TAG, "Could not cancel Underway alerts", e)
                    }
                }
            }
            // Play Services can be slow, and this must outlive the screen we are leaving.
            application.applicationScope.launch {
                try {
                    CredentialManager.create(application).clearCredentialState(ClearCredentialStateRequest())
                } catch (e: ClearCredentialException) {
                    Log.w(AUTH_TAG, "Could not clear the saved credential", e)
                }
            }
            latestOnSignedOut()
        }
    }
}

@Composable
fun GoogleLogoffButton(onLogOff: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = rememberSignOut(onLogOff), modifier = modifier) {
        Text(stringResource(R.string.settings_sign_out))
    }
}
