package com.example.hackapp.language

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/** Languages the app ships. French is the default resource set, so anything else falls back to it. */
enum class AppLanguage(val tag: String) {
    French("fr"),
    English("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }

        /** The language the resources resolve to for [locale]. */
        fun resolvedFor(locale: Locale): AppLanguage =
            if (locale.language == English.tag) English else French
    }
}

/**
 * The per-app language choice, on every supported Android version.
 *
 * MainActivity wraps its base context with [wrap], so no AppCompat dependency or theme change is
 * needed. The value is read synchronously because `attachBaseContext` cannot suspend. With no
 * saved choice the app follows the device language.
 */
object AppLanguageStore {
    private const val PREFERENCES = "app_language"
    private const val KEY_TAG = "tag"

    fun saved(context: Context): AppLanguage? = AppLanguage.fromTag(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_TAG, null),
    )

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putString(KEY_TAG, language.tag) }
    }

    fun wrap(base: Context): Context {
        val language = saved(base) ?: return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag(language.tag)))
        return base.createConfigurationContext(configuration)
    }
}
