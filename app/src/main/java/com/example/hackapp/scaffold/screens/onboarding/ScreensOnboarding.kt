package com.example.hackapp.scaffold.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.citypack.rememberDistrictMap
import com.example.hackapp.data.model.CuisineGroup
import com.example.hackapp.data.model.PriceBracket
import com.example.hackapp.domain.AgeRange
import com.example.hackapp.domain.Audience
import com.example.hackapp.domain.FlowViewModel
import com.example.hackapp.domain.UserProfile
import com.example.hackapp.domain.VisitorType
import com.example.hackapp.ui.components.ChoiceOption
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappTextActionButton
import com.example.hackapp.ui.components.HackappTextField
import com.example.hackapp.ui.components.HorizontalInfoCard
import com.example.hackapp.ui.components.InfoCallout
import com.example.hackapp.ui.components.MultiChoiceChips
import com.example.hackapp.ui.components.SingleChoiceChips
import com.example.hackapp.ui.components.stopTypeLabel
import com.example.hackapp.ui.motion.entrance
import com.example.hackapp.ui.theme.StopType
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

const val ONBOARDING_STEPS = 4

/** The profile as stored, waiting for DataStore on the first frame. */
@Composable
private fun rememberProfile(vm: FlowViewModel): UserProfile? = vm.profile.collectAsState().value

@Composable
private fun budgetLabel(code: String): String = stringResource(
    when (code) {
        "UNDER_20" -> R.string.budget_under_20
        "UNDER_50" -> R.string.budget_under_50
        "UNDER_100" -> R.string.budget_under_100
        else -> R.string.budget_100_plus
    },
)

@Composable
private fun brackets(vm: FlowViewModel): List<PriceBracket> {
    val list by androidx.compose.runtime.produceState(emptyList<PriceBracket>()) { value = vm.brackets() }
    return list
}

/** Step 1: Visitor or Resident. Resident is shown but not available yet. */
@Composable
fun ScreenOnboardingVisitorType(vm: FlowViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    HackappPage(
        title = stringResource(R.string.onboarding_visitor_type_title),
        onBack = onBack,
        step = 1,
        totalSteps = ONBOARDING_STEPS,
        bottomActions = {
            HackappPrimaryButton(stringResource(R.string.action_continue), {
                scope.launch { vm.profileStore.update { it.copy(visitorType = VisitorType.VISITOR) }; onNext() }
            })
        },
    ) {
        Text(stringResource(R.string.onboarding_visitor_type_subtitle), style = MaterialTheme.typography.bodyLarge)
        HorizontalInfoCard(
            title = stringResource(R.string.visitor_title),
            subtitle = stringResource(R.string.visitor_description),
            selected = true,
            onClick = {},
            modifier = Modifier.entrance(0),
        )
        HorizontalInfoCard(
            title = stringResource(R.string.resident_title),
            subtitle = stringResource(R.string.label_coming_soon),
            enabled = false,
            modifier = Modifier.entrance(1),
        )
    }
}

/** Step 2: name, email (prefilled from Google), age range and the borough of stay. */
@Composable
fun ScreenOnboardingProfile(vm: FlowViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val profile = rememberProfile(vm)
    val scope = rememberCoroutineScope()
    val user = remember { FirebaseAuth.getInstance().currentUser }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var age by remember { mutableStateOf<AgeRange?>(null) }
    var district by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(profile != null) {
        profile ?: return@LaunchedEffect
        name = profile.name.ifBlank { user?.displayName.orEmpty() }
        email = profile.email.ifBlank { user?.email.orEmpty() }
        age = profile.ageRange
        district = profile.districtOfStay
    }
    val map by rememberDistrictMap()
    val boroughs = map?.districts?.filter { it.groupId == null }.orEmpty()
    val ageOptions = AgeRange.entries.map { ChoiceOption(it, stringResource(ageLabel(it))) }

    HackappPage(
        title = stringResource(R.string.profile_title),
        onBack = onBack,
        step = 2,
        totalSteps = ONBOARDING_STEPS,
        bottomActions = {
            HackappPrimaryButton(stringResource(R.string.action_continue), {
                scope.launch {
                    vm.profileStore.update { it.copy(name = name.trim(), email = email.trim(), ageRange = age, districtOfStay = district) }
                    onNext()
                }
            })
        },
    ) {
        InfoCallout(stringResource(R.string.profile_prefill_note))
        HackappTextField(name, { name = it }, stringResource(R.string.profile_name), Modifier.entrance(0))
        HackappTextField(email, { email = it }, stringResource(R.string.auth_email), Modifier.entrance(1), keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
        Text(stringResource(R.string.profile_age_range), style = MaterialTheme.typography.titleMedium)
        SingleChoiceChips(ageOptions, age, { age = it }, Modifier.entrance(2))
        Text(stringResource(R.string.profile_district_of_stay), style = MaterialTheme.typography.titleMedium)
        SingleChoiceChips(boroughs.map { ChoiceOption(it.id, it.name) }, district, { district = it }, Modifier.entrance(3), allowDeselect = true)
    }
}

private fun ageLabel(a: AgeRange) = when (a) {
    AgeRange.A18_24 -> R.string.age_range_18_24
    AgeRange.A25_34 -> R.string.age_range_25_34
    AgeRange.A35_44 -> R.string.age_range_35_44
    AgeRange.A45_54 -> R.string.age_range_45_54
    AgeRange.A55_PLUS -> R.string.age_range_55_plus
}

/** Step 3: budget per person and the kind of audience. */
@Composable
fun ScreenOnboardingBudgetAudience(vm: FlowViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val profile = rememberProfile(vm)
    val scope = rememberCoroutineScope()
    val all = brackets(vm)
    var budget by remember { mutableStateOf<String?>(null) }
    var audience by remember { mutableStateOf(Audience.ADULTS_ONLY) }
    LaunchedEffect(profile != null) {
        profile ?: return@LaunchedEffect
        budget = profile.budgetCode
        audience = profile.audience
    }
    val audiences = listOf(
        ChoiceOption(Audience.ADULTS_ONLY, stringResource(R.string.audience_adults_only)),
        ChoiceOption(Audience.FAMILY_FRIENDLY, stringResource(R.string.audience_family_friendly)),
        ChoiceOption(Audience.NO_ALCOHOL, stringResource(R.string.audience_no_alcohol)),
    )
    HackappPage(
        title = stringResource(R.string.budget_audience_title),
        onBack = onBack,
        step = 3,
        totalSteps = ONBOARDING_STEPS,
        bottomActions = {
            HackappPrimaryButton(stringResource(R.string.action_continue), {
                scope.launch { vm.profileStore.update { it.copy(budgetCode = budget, audience = audience) }; onNext() }
            })
        },
    ) {
        Text(stringResource(R.string.budget_subtitle), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceChips(
            all.map { ChoiceOption(it.code, budgetLabel(it.code)) }, budget, { budget = it },
            Modifier.fillMaxWidth().entrance(0), allowDeselect = true,
        )
        Text(stringResource(R.string.audience_title), style = MaterialTheme.typography.titleMedium)
        SingleChoiceChips(audiences, audience, { audience = it ?: Audience.ADULTS_ONLY }, Modifier.entrance(1))
        if (audience != Audience.ADULTS_ONLY) InfoCallout(stringResource(R.string.audience_bars_hidden_note))
    }
}

/** Step 4: optional interests. They rank results and never hide them. Ends onboarding. */
@Composable
fun ScreenOnboardingInterests(vm: FlowViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val profile = rememberProfile(vm)
    val scope = rememberCoroutineScope()
    val language = LocalConfiguration.current.locales[0].language
    val groups by androidx.compose.runtime.produceState(emptyList<CuisineGroup>()) { value = vm.services.places.cuisineGroups() }
    var types by remember { mutableStateOf(emptySet<StopType>()) }
    var cuisines by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(profile != null) {
        profile ?: return@LaunchedEffect
        types = profile.interestTypes
        cuisines = profile.cuisineGroups
    }
    val typeOptions = StopType.entries.filter { it != StopType.Meal && it != StopType.Bar }
        .map { ChoiceOption(it, stopTypeLabel(it)) }

    fun finish() = scope.launch {
        vm.profileStore.update { it.copy(interestTypes = types, cuisineGroups = cuisines, onboarded = true) }
        onDone()
    }

    HackappPage(
        title = stringResource(R.string.interests_title),
        onBack = onBack,
        step = 4,
        totalSteps = ONBOARDING_STEPS,
        bottomActions = {
            HackappPrimaryButton(stringResource(R.string.action_finish), { finish() })
            HackappTextActionButton(stringResource(R.string.action_skip), { scope.launch {
                vm.profileStore.update { it.copy(onboarded = true) }
                onDone()
            } })
        },
    ) {
        Text(stringResource(R.string.interests_subtitle), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.interests_event_types), style = MaterialTheme.typography.titleMedium)
        MultiChoiceChips(typeOptions, types, { types = it }, Modifier.entrance(0))
        Text(stringResource(R.string.interests_cuisines), style = MaterialTheme.typography.titleMedium)
        MultiChoiceChips(
            groups.map { ChoiceOption(it.code, "${it.emoji} ${it.label.get(language) ?: it.code}") },
            cuisines, { cuisines = it }, Modifier.entrance(1),
        )
    }
}
