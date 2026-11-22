package com.example.hackapp

import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.collection.intSetOf
import androidx.credentials.CredentialManager
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.example.hackapp.scaffold.AppScaffold
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.widgets.AppWidgetReceiver
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContent {
            HackappTheme {
                AppScaffold()
            }
        }
        this.instantiateWidgets()
    }

    /** Instantiates app widgets. */
    private fun instantiateWidgets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            lifecycleScope.launch {
                val manager = GlanceAppWidgetManager(this@MainActivity)
                manager.setWidgetPreviews(
                    AppWidgetReceiver::class,
                    intSetOf(WIDGET_CATEGORY_HOME_SCREEN)
                )
            }
        }
    }
}
