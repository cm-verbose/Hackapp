package com.example.hackapp.scaffold.screens.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.data.model.CuisineGroup
import com.example.hackapp.data.model.LocalizedText
import com.example.hackapp.profile.AgeRange
import com.example.hackapp.profile.AudiencePreference
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.Interest
import com.example.hackapp.profile.VisitorType
import com.example.hackapp.scaffold.screens.BudgetFields
import com.example.hackapp.ui.components.ChoiceOption
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.HackappIcon
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappPickerField
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappTextActionButton
import com.example.hackapp.ui.components.HackappTextField
import com.example.hackapp.ui.components.HorizontalInfoCard
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.components.MultiChoiceChips
import com.example.hackapp.ui.components.SingleChoiceChips
import com.example.hackapp.ui.components.stopTypeLabel
import com.example.hackapp.ui.theme.HackappTheme

internal const val ONBOARDING_STEPS = 4

@Composable
fun OnboardingVisitorTypePage(state: OnboardingUiState, onContinue: () -> Unit, onSignOut: () -> Unit) {
    HackappPage(
        title = stringResource(R.string.onboarding_step, 1, ONBOARDING_STEPS),
        step = 1,
        totalSteps = ONBOARDING_STEPS,
        bottomActions = {
            HackappPrimaryButton(stringResource(R.string.action_continue), onContinue)
            HackappTextActionButton(stringResource(R.string.settings_sign_out), onSignOut)
        },
    ) {
        PageHeading(stringResource(R.string.onboarding_visitor_type_title))
        BodyText(stringResource(R.string.onboarding_visitor_type_subtitle))
        HorizontalInfoCard(
            title = stringResource(R.string.visitor_title),
            subtitle = stringResource(R.string.visitor_description),
            selected = state.draft.visitorType == VisitorType.Visitor,
            onClick = {},
            trailing = null,
        )
        HorizontalInfoCard(
            title = stringResource(R.string.resident_title),
            subtitle = stringResource(R.string.label_coming_soon),
            enabled = false,
            onClick = {},
            trailing = null,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingProfilePage(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onAgeRangeChange: (AgeRange?) -> Unit,
    onDistrictChange: (String) -> Unit,
    onRetryDistricts: () -> Unit,
    onContinue: () -> Unit,
) {
    val draft = state.draft
    var pickingDistrict by rememberSaveable { mutableStateOf(false) }
    val districtLabel = stringResource(R.string.profile_district_of_stay)

    HackappPage(
        title = stringResource(R.string.onboarding_step, 2, ONBOARDING_STEPS),
        onBack = onBack,
        step = 2,
        totalSteps = ONBOARDING_STEPS,
        bottomActions = {
            HackappPrimaryButton(stringResource(R.string.action_continue), onContinue, enabled = draft.isProfileStepValid)
        },
    ) {
        PageHeading(stringResource(R.string.profile_title))
        BodyText(stringResource(R.string.profile_prefill_note))
        HackappTextField(draft.name, onNameChange, stringResource(R.string.profile_name))
        HackappTextField(
            value = draft.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.auth_email),
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            errorText = if (draft.email.isNotBlank() && !draft.isEmailValid) {
                stringResource(R.string.auth_error_invalid_email)
            } else {
                null
            },
        )
        SectionTitle(stringResource(R.string.profile_age_range))
        SingleChoiceChips(ageRangeOptions(), draft.ageRange, onAgeRangeChange, allowDeselect = true)
        when (val districts = state.districts) {
            LoadState.Loading -> LoadingState()
            LoadState.Failed -> ErrorState(onRetry = onRetryDistricts, body = districtLabel)
            is LoadState.Ready -> HackappPickerField(
                value = districts.value.firstOrNull { it.id == draft.districtOfStayId }?.name.orEmpty(),
                label = districtLabel,
                onClick = { pickingDistrict = true },
            )
        }
    }

    val options = (state.districts as? LoadState.Ready)?.value
    if (pickingDistrict && options != null) {
        ModalBottomSheet(onDismissRequest = { pickingDistrict = false }) {
            Text(
                districtLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
            LazyColumn {
                items(options, key = { it.id }) { option ->
                    ListItem(
                        headlineContent = { Text(option.name) },
                        modifier = Modifier.clickable {
                            onDistrictChange(option.id)
                            pickingDistrict = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingBudgetAudiencePage(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onLunchChange: (BudgetBracket?) -> Unit,
    onDinnerChange: (BudgetBracket?) -> Unit,
    onEventsChange: (BudgetBracket?) -> Unit,
    onLateNightEnabledChange: (Boolean) -> Unit,
    onLateNightChange: (BudgetBracket?) -> Unit,
    onAudienceChange: (Set<AudiencePreference>) -> Unit,
    onContinue: () -> Unit,
) {
    val draft = state.draft
    HackappPage(
        title = stringResource(R.string.onboarding_step, 3, ONBOARDING_STEPS),
        onBack = onBack,
        step = 3,
        totalSteps = ONBOARDING_STEPS,
        bottomActions = {
            HackappPrimaryButton(stringResource(R.string.action_continue), onContinue, enabled = draft.isBudgetStepValid)
        },
    ) {
        PageHeading(stringResource(R.string.budget_audience_title))
        BodyText(stringResource(R.string.budget_subtitle))
        BudgetFields(
            lunch = draft.lunchBudget,
            dinner = draft.dinnerBudget,
            events = draft.eventBudget,
            lateNightEnabled = draft.lateNightEnabled,
            lateNight = draft.lateNightBudget,
            onLunchChange = onLunchChange,
            onDinnerChange = onDinnerChange,
            onEventsChange = onEventsChange,
            onLateNightEnabledChange = onLateNightEnabledChange,
            onLateNightChange = onLateNightChange,
        )
        SectionTitle(stringResource(R.string.audience_title))
        MultiChoiceChips(audienceOptions(), draft.audience, onAudienceChange)
        BodyText(stringResource(R.string.audience_bars_hidden_note))
    }
}

@Composable
fun OnboardingInterestsPage(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onInterestsChange: (Set<Interest>) -> Unit,
    onCuisinesChange: (Set<String>) -> Unit,
    onRetryCuisines: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    val draft = state.draft
    HackappPage(
        title = stringResource(R.string.onboarding_step, 4, ONBOARDING_STEPS),
        onBack = onBack,
        step = 4,
        totalSteps = ONBOARDING_STEPS,
        bottomActions = {
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.onboarding_save_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            HackappPrimaryButton(stringResource(R.string.action_finish), onFinish, enabled = !state.saving)
            HackappTextActionButton(stringResource(R.string.action_skip), onSkip, enabled = !state.saving)
        },
    ) {
        PageHeading(stringResource(R.string.interests_title))
        BodyText(stringResource(R.string.interests_subtitle))
        SectionTitle(stringResource(R.string.interests_event_types))
        MultiChoiceChips(
            options = Interest.entries.map { ChoiceOption(it, stopTypeLabel(it.stopType)) },
            selected = draft.interests,
            onSelectedChange = onInterestsChange,
        )
        SectionTitle(stringResource(R.string.interests_cuisines))
        when (val cuisines = state.cuisines) {
            LoadState.Loading -> LoadingState()
            LoadState.Failed -> ErrorState(onRetry = onRetryCuisines)
            is LoadState.Ready -> {
                val language = cuisineLanguage()
                MultiChoiceChips(
                    options = visibleCuisineGroups(cuisines.value, draft.audience).map {
                        ChoiceOption(
                            value = it.code,
                            label = it.label.get(language) ?: it.code,
                            icon = HackappIcon.forCuisineGroup(it.icon).vector,
                        )
                    },
                    selected = draft.cuisineGroups,
                    onSelectedChange = onCuisinesChange,
                )
            }
        }
    }
}

/** French is the default resource locale, so any language other than English reads French. */
@Composable
private fun cuisineLanguage(): String =
    if (LocalConfiguration.current.locales[0].language == "en") "en" else "fr"

@Composable
private fun ageRangeOptions() = listOf(
    ChoiceOption(AgeRange.From18To24, stringResource(R.string.age_range_18_24)),
    ChoiceOption(AgeRange.From25To34, stringResource(R.string.age_range_25_34)),
    ChoiceOption(AgeRange.From35To44, stringResource(R.string.age_range_35_44)),
    ChoiceOption(AgeRange.From45To54, stringResource(R.string.age_range_45_54)),
    ChoiceOption(AgeRange.From55, stringResource(R.string.age_range_55_plus)),
)

@Composable
private fun audienceOptions() = listOf(
    ChoiceOption(AudiencePreference.AdultsOnly, stringResource(R.string.audience_adults_only)),
    ChoiceOption(AudiencePreference.FamilyFriendly, stringResource(R.string.audience_family_friendly)),
    ChoiceOption(AudiencePreference.NoAlcohol, stringResource(R.string.audience_no_alcohol)),
)

@Composable
private fun PageHeading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun BodyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val PreviewState = OnboardingUiState(
    draft = OnboardingDraft(
        name = "Camille Tremblay",
        email = "camille@exemple.ca",
        ageRange = AgeRange.From25To34,
        districtOfStayId = "district-a",
        lunchBudget = BudgetBracket.Under20,
        dinnerBudget = BudgetBracket.Under50,
        eventBudget = BudgetBracket.Under50,
        lateNightEnabled = true,
        lateNightBudget = BudgetBracket.Under20,
        audience = setOf(AudiencePreference.FamilyFriendly, AudiencePreference.NoAlcohol),
        interests = setOf(Interest.Music),
        cuisineGroups = setOf("group-a"),
    ),
    districts = LoadState.Ready(listOf(DistrictOption("district-a", "District A"))),
    cuisines = LoadState.Ready(
        listOf(CuisineGroup("group-a", "restaurant_menu", LocalizedText("Groupe A", "Group A"), emptySet(), null)),
    ),
)

@Preview(name = "Onboarding 1 Visitor type", showBackground = true, widthDp = 412, heightDp = 720)
@Composable
private fun OnboardingVisitorTypePreview() = HackappTheme {
    OnboardingVisitorTypePage(PreviewState, onContinue = {}, onSignOut = {})
}

@Preview(name = "Onboarding 2 Profile", showBackground = true, widthDp = 412, heightDp = 720)
@Composable
private fun OnboardingProfilePreview() = HackappTheme {
    OnboardingProfilePage(PreviewState, {}, {}, {}, {}, {}, {}, {})
}

@Preview(name = "Onboarding 3 Budget and Audience", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun OnboardingBudgetAudiencePreview() = HackappTheme {
    OnboardingBudgetAudiencePage(PreviewState, {}, {}, {}, {}, {}, {}, {}, {})
}

@Preview(name = "Onboarding 4 Interests", showBackground = true, widthDp = 412, heightDp = 720)
@Composable
private fun OnboardingInterestsPreview() = HackappTheme {
    OnboardingInterestsPage(PreviewState, {}, {}, {}, {}, {}, {})
}
