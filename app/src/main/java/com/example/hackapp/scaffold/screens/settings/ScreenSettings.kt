package com.example.hackapp.scaffold.screens.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.hackapp.R
import com.example.hackapp.scaffold.screens.planning.GoogleLogoffButton
import com.example.hackapp.services.AppLocale
import com.example.hackapp.ui.components.ChoiceOption
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappSegmentedButton
import com.example.hackapp.ui.components.HorizontalInfoCard
import com.example.hackapp.ui.motion.entrance

/** Language (French / English), profile and sign out. */
@Composable
fun ScreenSettings(onBack: () -> Unit, onProfile: () -> Unit, onLogOff: () -> Unit) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val current = AppLocale.saved(context) ?: context.resources.configuration.locales[0].language
    val selected = if (current.startsWith("en")) "en" else "fr"

    HackappPage(title = stringResource(R.string.settings_title), onBack = onBack) {
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        HackappSegmentedButton(
            options = listOf(
                ChoiceOption("fr", stringResource(R.string.language_french)),
                ChoiceOption("en", stringResource(R.string.language_english)),
            ),
            selected = selected,
            onSelectedChange = { tag -> if (tag != selected) activity?.let { AppLocale.set(it, tag) } },
            modifier = Modifier.fillMaxWidth().entrance(0),
        )
        HorizontalInfoCard(
            title = stringResource(R.string.settings_profile),
            subtitle = stringResource(R.string.settings_profile_description),
            onClick = onProfile,
            modifier = Modifier.entrance(1),
            trailing = null,
        )
        GoogleLogoffButton(onLogOff = onLogOff, modifier = Modifier.align(Alignment.CenterHorizontally).entrance(2))
    }
}
