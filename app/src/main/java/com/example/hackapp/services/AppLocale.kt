package com.example.hackapp.services

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language (French or English), kept in SharedPreferences so it can be read before the UI
 * exists. No value means "follow the system". Works on every supported API without AppCompat.
 */
object AppLocale {
    private const val PREFS = "settings"
    private const val KEY = "language"

    fun saved(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    /** Wraps [base] so resources use the saved language. Call from `attachBaseContext`. */
    fun wrap(base: Context): Context {
        val tag = saved(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    /** Saves the language and recreates the screen so every string is reloaded. */
    fun set(activity: Activity, tag: String) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
        activity.recreate()
    }
}
