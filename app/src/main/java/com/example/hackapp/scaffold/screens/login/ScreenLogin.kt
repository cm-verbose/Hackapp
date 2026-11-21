package com.example.hackapp.scaffold.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.serialization.Serializable

@Serializable
object ScreenLoginDestination

/** Login screen with Google authentification. */
@Composable
fun ScreenLogin(onNavigateNext: () -> Unit) {
    val isLoggedOut by rememberSaveable {
        mutableStateOf(FirebaseAuth.getInstance().currentUser == null)
    }

    LaunchedEffect(isLoggedOut) {
        if (!isLoggedOut) {
            onNavigateNext()
        }
    }

    Column {
        if (!isLoggedOut) return@ScreenLogin
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.app_name),
                fontSize = MaterialTheme.typography.displayMedium.fontSize
            )
            Spacer(Modifier.size(20.dp))
            GoogleLoginButton(onNavigateNext)
        }
    }
}
