package com.example.hackapp.scaffold.screens.planning

import android.util.Log
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun GoogleLogoffButton(onLogOff: () -> Unit, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as HackappApplication
    OutlinedButton(
        modifier = modifier,
        onClick = {
            FirebaseAuth.getInstance().signOut()
            // Play Services can be slow, and this must outlive the screen we are leaving.
            application.applicationScope.launch {
                try {
                    CredentialManager.create(application).clearCredentialState(ClearCredentialStateRequest())
                } catch (e: ClearCredentialException) {
                    Log.w(AUTH_TAG, "Could not clear the saved credential", e)
                }
            }
            onLogOff()
        },
    ) {
        Text(stringResource(R.string.settings_sign_out))
    }
}
