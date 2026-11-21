package com.example.androidtemplate

import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.collection.intSetOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.example.androidtemplate.scaffold.AppScaffold
import com.example.androidtemplate.ui.theme.AndroidTemplateTheme
import com.example.androidtemplate.widgets.AppWidgetReceiver
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidTemplateTheme {
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
