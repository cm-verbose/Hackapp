package com.example.hackapp.scaffold.screens.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.language.AppLanguage
import com.example.hackapp.language.AppLanguageStore
import com.example.hackapp.profile.AgeRange
import com.example.hackapp.profile.AudiencePreference
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.Interest
import com.example.hackapp.profile.LocalProfileRepository
import com.example.hackapp.profile.Profile
import com.example.hackapp.profile.VisitorType
import com.example.hackapp.scaffold.screens.itinerary.loadDistrictNames
import com.example.hackapp.scaffold.screens.onboarding.LoadState
import com.example.hackapp.scaffold.screens.onboarding.stopType
import com.example.hackapp.scaffold.screens.planning.rememberSignOut
import com.example.hackapp.services.LocalCityServices
import com.example.hackapp.ui.components.ChoiceOption
import com.example.hackapp.ui.components.DataAttribution
import com.example.hackapp.ui.components.EmptyState
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.HackappOutlineButton
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappSegmentedButton
import com.example.hackapp.ui.components.HorizontalInfoCard
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.components.stopTypeLabel
import com.example.hackapp.ui.theme.HackappTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScreenSettings(onBack: () -> Unit, onSignedOut: () -> Unit) {
    val activity = LocalActivity.current
    val context = LocalContext.current.applicationContext
    val cityPack = LocalCityPack.current
    val cityServices = LocalCityServices.current
    val auth = LocalAuthService.current
    val profiles = LocalProfileRepository.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            uid = auth.currentUser?.uid,
            profiles = profiles,
            loadDistrictNames = { loadDistrictNames(context, cityPack) },
            loadCuisines = { withContext(Dispatchers.IO) { cityServices.places.cuisineGroups() } },
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = AppLanguage.resolvedFor(LocalConfiguration.current.locales[0])
    SettingsPage(
        state = state,
        language = language,
        onLanguageChange = { selected ->
            if (selected != language) {
                AppLanguageStore.save(context, selected)
                activity?.recreate()
            }
        },
        onBack = onBack,
        onRetry = viewModel::load,
        onSignOut = rememberSignOut(onSignedOut),
    )
}

/** Figma SettingsScreen (30:1076). */
@Composable
fun SettingsPage(
    state: SettingsUiState,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    HackappPage(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        bottomActions = { HackappOutlineButton(stringResource(R.string.settings_sign_out), onSignOut) },
    ) {
        SettingsSection(stringResource(R.string.settings_language))
        HackappSegmentedButton(
            options = listOf(
                ChoiceOption(AppLanguage.French, stringResource(R.string.language_french)),
                ChoiceOption(AppLanguage.English, stringResource(R.string.language_english)),
            ),
            selected = language,
            onSelectedChange = onLanguageChange,
        )
        SettingsSection(stringResource(R.string.settings_profile))
        when (val profile = state.profile) {
            LoadState.Loading -> LoadingState()
            LoadState.Failed -> ErrorState(onRetry = onRetry)
            is LoadState.Ready -> profile.value?.let { ProfileCards(it, language) } ?: EmptyState()
        }
        DataAttribution()
        DataAttribution(text = stringResource(R.string.data_licences))
    }
}

@Composable
private fun SettingsSection(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun ProfileCards(summary: SettingsProfile, language: AppLanguage) {
    val profile = summary.profile
    HorizontalInfoCard(
        title = profile.name,
        subtitle = listOfNotNull(
            profile.email,
            ageRangeLabel(profile.ageRange),
            summary.districtName,
        ).joinToString("\u00a0· "),
        avatarText = profile.name.take(1).uppercase(),
        subtitleMaxLines = Int.MAX_VALUE,
        trailing = null,
    )
    HorizontalInfoCard(
        title = stringResource(R.string.budget_audience_title),
        subtitle = (
            listOf(stringResource(R.string.settings_events_budget, budgetLabel(profile.eventBudget))) +
                profile.audience.map { audienceLabel(it) }
            )
            .joinToString("\u00a0· "),
        leadingIcon = Icons.Filled.Payments,
        subtitleMaxLines = Int.MAX_VALUE,
        trailing = null,
    )
    val preferences = profile.interests.map { stopTypeLabel(it.stopType) } +
        summary.cuisines.mapNotNull { it.label.get(language.tag) }
    HorizontalInfoCard(
        title = stringResource(R.string.interests_title),
        subtitle = preferences.joinToString(", ").ifEmpty { stringResource(R.string.settings_no_preferences) },
        leadingIcon = Icons.Filled.Interests,
        subtitleMaxLines = Int.MAX_VALUE,
        trailing = null,
    )
}

@Composable
private fun ageRangeLabel(range: AgeRange): String = stringResource(
    when (range) {
        AgeRange.From18To24 -> R.string.age_range_18_24
        AgeRange.From25To34 -> R.string.age_range_25_34
        AgeRange.From35To44 -> R.string.age_range_35_44
        AgeRange.From45To54 -> R.string.age_range_45_54
        AgeRange.From55 -> R.string.age_range_55_plus
    },
)

@Composable
private fun budgetLabel(bracket: BudgetBracket): String = stringResource(
    when (bracket) {
        BudgetBracket.Under20 -> R.string.budget_under_20
        BudgetBracket.Under50 -> R.string.budget_under_50
        BudgetBracket.Under100 -> R.string.budget_under_100
        BudgetBracket.From100 -> R.string.budget_100_plus
    },
)

@Composable
private fun audienceLabel(audience: AudiencePreference): String = stringResource(
    when (audience) {
        AudiencePreference.AdultsOnly -> R.string.audience_adults_only
        AudiencePreference.FamilyFriendly -> R.string.audience_family_friendly
        AudiencePreference.NoAlcohol -> R.string.audience_no_alcohol
    },
)

@Preview(name = "Settings", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun SettingsPreview() = HackappTheme {
    SettingsPage(
        state = SettingsUiState(
            LoadState.Ready(
                SettingsProfile(
                    profile = Profile(
                        visitorType = VisitorType.Visitor,
                        name = "Nikola",
                        email = "nikola@example.com",
                        ageRange = AgeRange.From25To34,
                        districtOfStayId = "ville-marie",
                        lunchBudget = BudgetBracket.Under20,
                        dinnerBudget = BudgetBracket.Under50,
                        eventBudget = BudgetBracket.Under50,
                        audience = setOf(AudiencePreference.FamilyFriendly),
                        interests = setOf(Interest.Music, Interest.Festival),
                    ),
                    districtName = "Ville-Marie",
                    cuisines = emptyList(),
                ),
            ),
        ),
        language = AppLanguage.French,
        onLanguageChange = {},
        onBack = {},
        onRetry = {},
        onSignOut = {},
    )
}

@Preview(name = "Settings · error", showBackground = true, widthDp = 412, heightDp = 600)
@Composable
private fun SettingsErrorPreview() = HackappTheme {
    SettingsPage(SettingsUiState(LoadState.Failed), AppLanguage.English, {}, {}, {}, {})
}
