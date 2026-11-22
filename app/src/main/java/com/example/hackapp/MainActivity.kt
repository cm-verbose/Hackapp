package com.example.hackapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.credentials.CredentialManager
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.itinerary.LocalItineraryRepository
import com.example.hackapp.language.AppLanguageStore
import com.example.hackapp.passport.LocalKeepsakeStore
import com.example.hackapp.passport.LocalStampPhotoStore
import com.example.hackapp.passport.LocalStampRepository
import com.example.hackapp.profile.LocalProfileRepository
import com.example.hackapp.scaffold.AppScaffold
import com.example.hackapp.scaffold.screens.splash.CitySplashScreen
import com.example.hackapp.services.LocalCityServices
import com.example.hackapp.ui.theme.HackappTheme
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        val app = application as HackappApplication
        val cityServices = app.cityServices
        setContent {
            CompositionLocalProvider(
                LocalCityPack provides cityServices.cityPack,
                LocalCityServices provides cityServices,
                LocalProfileRepository provides app.profileRepository,
                LocalItineraryRepository provides app.itineraryRepository,
                LocalStampRepository provides app.stampRepository,
                LocalStampPhotoStore provides app.stampPhotos,
                LocalKeepsakeStore provides app.keepsakes,
            ) {
                HackappTheme {
                    // The City Pack splash stays briefly on app open (not after a rotation or a
                    // restored process), while the start screen composes underneath.
                    var showSplash by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                    LaunchedEffect(Unit) {
                        delay(SPLASH_MILLIS)
                        showSplash = false
                    }
                    Box {
                        AppScaffold()
                        AnimatedVisibility(visible = showSplash, enter = EnterTransition.None, exit = fadeOut(tween(300))) {
                            CitySplashScreen()
                        }
                    }
                }
            }
        }
    }
}

private const val SPLASH_MILLIS = 1_200L
