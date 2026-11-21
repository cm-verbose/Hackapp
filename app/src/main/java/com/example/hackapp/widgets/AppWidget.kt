package com.example.hackapp.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import com.example.hackapp.MainActivity

class AppWidget: GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetRoot()
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme {
                WidgetRoot()
            }
        }
    }

    @Composable
    private fun WidgetRoot() {
        Column(
            modifier = GlanceModifier
                .background(GlanceTheme.colors.widgetBackground)
                .padding(16.dp)
                .fillMaxSize()
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Text("Hello from widget !")
        }
    }
}
