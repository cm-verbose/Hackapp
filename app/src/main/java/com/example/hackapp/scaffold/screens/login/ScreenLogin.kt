package com.example.hackapp.scaffold.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.serialization.Serializable

@Serializable
object ScreenLoginDestination

/** Login screen with Google authentification. */
@Composable
fun ScreenLogin(onNavigateNext: () -> Unit) {
    val typography = MaterialTheme.typography
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
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(
                space = 16.dp,
                alignment = Alignment.CenterVertically
            ),
        ) {
            Text(
                stringResource(R.string.app_name),
                fontSize = typography.displayMedium.fontSize
            )
            val subTextSize = typography.displaySmall.fontSize
            Text(
                "Votre sortie, sur\nmesure.",
                fontSize = subTextSize,
                fontWeight = FontWeight.W600,
                style = TextStyle(lineHeight = subTextSize * 1.15)
            )
            Text("Planifiez votre sortie, suivez-la arrêt par arrêt, et collectionnez vos timbres.")
            GoogleLoginButton(onNavigateNext)
        }
    }
}
