package com.example.hackapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.credentials.CredentialManager
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.itinerary.LocalItineraryRepository
import com.example.hackapp.language.AppLanguageStore
import com.example.hackapp.passport.LocalKeepsakeStore
import com.example.hackapp.passport.LocalStampPhotoStore
import com.example.hackapp.passport.LocalStampRepository
import com.example.hackapp.profile.LocalProfileRepository
import com.example.hackapp.scaffold.AppScaffold
import com.example.hackapp.services.LocalCityServices
import com.example.hackapp.ui.theme.HackappTheme
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
                    AppScaffold()
                }
            }
        }
    }
}
