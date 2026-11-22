package com.example.hackapp.scaffold.screens.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackapp.HackappApplication
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
import com.example.hackapp.scaffold.screens.underway.DemoControlsViewModel
import com.example.hackapp.scaffold.screens.underway.ExitDemoDialog
import com.example.hackapp.services.LocalCityServices
import com.example.hackapp.ui.components.ChoiceOption
import com.example.hackapp.ui.components.DataAttribution
import com.example.hackapp.ui.components.EmptyState
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.HackappOutlineButton
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappSegmentedButton
import com.example.hackapp.ui.components.HackappSmallTextButton
import com.example.hackapp.ui.components.HorizontalInfoCard
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.components.stopTypeLabel
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.underway.DemoEnableResult
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
    val uid = auth.currentUser?.uid
    val demo: DemoControlsViewModel? = uid?.let {
        viewModel(
            key = "demo-$it",
            factory = DemoControlsViewModel.factory(it, (context as HackappApplication).demoController),
        )
    }
    var confirmDemoExit by rememberSaveable { mutableStateOf(false) }
    val demoUi = demo?.let {
        val status by it.state.collectAsStateWithLifecycle()
        val lastResult by it.lastResult.collectAsStateWithLifecycle()
        val exitFailed by it.exitFailed.collectAsStateWithLifecycle()
        val actionFailed by it.actionFailed.collectAsStateWithLifecycle()
        DemoSettingsUi(
            enabled = status.enabled,
            canAdvance = status.canAdvance,
            blocked = !status.enabled && lastResult is DemoEnableResult.RealItineraryUnderway,
            failed = exitFailed || actionFailed,
        )
    }
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
        demo = demoUi,
        onDemoChange = { on ->
            when {
                on -> demo?.enable()
                // Only a run in progress has something to lose, so only then is leaving confirmed.
                demoUi?.canAdvance == true -> confirmDemoExit = true
                else -> demo?.exit()
            }
        },
        onDemoRetry = { demo?.retry() },
    )
    if (confirmDemoExit) {
        ExitDemoDialog(
            onConfirm = {
                confirmDemoExit = false
                demo?.exit()
            },
            onDismiss = { confirmDemoExit = false },
        )
    }
}

/** The Mode démo row. [blocked]: a real Itinerary is Underway. [failed]: the last action failed. */
@Immutable
data class DemoSettingsUi(
    val enabled: Boolean,
    val canAdvance: Boolean = false,
    val blocked: Boolean = false,
    val failed: Boolean = false,
)

/** Figma SettingsScreen (30:1076). */
@Composable
fun SettingsPage(
    state: SettingsUiState,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    demo: DemoSettingsUi? = null,
    onDemoChange: (Boolean) -> Unit = {},
    onDemoRetry: () -> Unit = {},
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
        demo?.let { DemoModeCard(it, onDemoChange, onDemoRetry) }
        DataAttribution()
        DataAttribution(text = stringResource(R.string.data_licences))
    }
}

/** Figma Settings (`186:2798`). The whole row is the switch. */
@Composable
private fun DemoModeCard(ui: DemoSettingsUi, onChange: (Boolean) -> Unit, onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(HackappCorners.Medium)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, scheme.outlineVariant, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = ui.enabled, role = Role.Switch, onValueChange = onChange)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.demo_mode_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
                Text(
                    stringResource(R.string.demo_mode_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Switch(checked = ui.enabled, onCheckedChange = null)
        }
        if (ui.blocked) {
            Text(
                stringResource(R.string.demo_blocked_real_underway),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (ui.failed) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.demo_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                HackappSmallTextButton(stringResource(R.string.action_retry), onRetry)
            }
        }
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

@Composable
private fun DemoCardPreview(ui: DemoSettingsUi, dark: Boolean = false) = HackappTheme(darkTheme = dark) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        DemoModeCard(ui, {}, {})
    }
}

@Preview(name = "Mode démo · off", widthDp = 360)
@Composable
private fun DemoOffPreview() = DemoCardPreview(DemoSettingsUi(enabled = false))

@Preview(name = "Mode démo · on", widthDp = 360)
@Composable
private fun DemoOnPreview() = DemoCardPreview(DemoSettingsUi(enabled = true))

@Preview(name = "Mode démo · on, dark", widthDp = 360)
@Composable
private fun DemoOnDarkPreview() = DemoCardPreview(DemoSettingsUi(enabled = true), dark = true)

@Preview(name = "Mode démo · real Itinerary Underway, 1.3x", widthDp = 360, fontScale = 1.3f)
@Composable
private fun DemoBlockedPreview() = DemoCardPreview(DemoSettingsUi(enabled = false, blocked = true))

@Preview(name = "Mode démo · action failed", widthDp = 360)
@Composable
private fun DemoFailedPreview() = DemoCardPreview(DemoSettingsUi(enabled = true, failed = true))
