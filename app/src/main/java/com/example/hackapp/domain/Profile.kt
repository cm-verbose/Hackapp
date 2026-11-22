package com.example.hackapp.domain

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.hackapp.ui.theme.StopType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class VisitorType { VISITOR, RESIDENT }

/** Age bands of the onboarding. Events whose minimum age is above [min] are hidden (an 18-24 person may be 18). */
enum class AgeRange(val min: Int, val max: Int) { A18_24(18, 24), A25_34(25, 34), A35_44(35, 44), A45_54(45, 54), A55_PLUS(55, 120) }

enum class Audience { ADULTS_ONLY, FAMILY_FRIENDLY, NO_ALCOHOL }

/** What the person told the app in onboarding. Every field is optional until onboarding ends. */
data class UserProfile(
    val onboarded: Boolean = false,
    val visitorType: VisitorType = VisitorType.VISITOR,
    val name: String = "",
    val email: String = "",
    val ageRange: AgeRange? = null,
    /** District id (`area.code`) of the borough the person stays in. */
    val districtOfStay: String? = null,
    /** `price_bracket.code` such as UNDER_50; null = no budget. */
    val budgetCode: String? = null,
    val audience: Audience = Audience.ADULTS_ONLY,
    /** Interests only rank results, they never hide them. */
    val interestTypes: Set<StopType> = emptySet(),
    val cuisineGroups: Set<String> = emptySet(),
)

private val Context.profileDataStore by preferencesDataStore(name = "profile")

/** [UserProfile] kept on the device (no account sync yet). */
class ProfileStore(context: Context) {
    private val store = context.applicationContext.profileDataStore

    val profile: Flow<UserProfile> = store.data.map { it.toProfile() }

    suspend fun update(change: (UserProfile) -> UserProfile) {
        store.edit { prefs -> prefs.write(change(prefs.toProfile())) }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun Preferences.toProfile() = UserProfile(
        onboarded = this[ONBOARDED] ?: false,
        visitorType = enumOr(this[VISITOR], VisitorType.VISITOR),
        name = this[NAME].orEmpty(),
        email = this[EMAIL].orEmpty(),
        ageRange = this[AGE]?.let { v -> AgeRange.entries.firstOrNull { it.name == v } },
        districtOfStay = this[DISTRICT],
        budgetCode = this[BUDGET],
        audience = enumOr(this[AUDIENCE], Audience.ADULTS_ONLY),
        interestTypes = this[INTERESTS].orEmpty().mapNotNull { v -> StopType.entries.firstOrNull { it.name == v } }.toSet(),
        cuisineGroups = this[CUISINES].orEmpty(),
    )

    private fun androidx.datastore.preferences.core.MutablePreferences.write(p: UserProfile) {
        this[ONBOARDED] = p.onboarded
        this[VISITOR] = p.visitorType.name
        this[NAME] = p.name
        this[EMAIL] = p.email
        p.ageRange?.let { this[AGE] = it.name } ?: remove(AGE)
        p.districtOfStay?.let { this[DISTRICT] = it } ?: remove(DISTRICT)
        p.budgetCode?.let { this[BUDGET] = it } ?: remove(BUDGET)
        this[AUDIENCE] = p.audience.name
        this[INTERESTS] = p.interestTypes.map { it.name }.toSet()
        this[CUISINES] = p.cuisineGroups
    }

    private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == value } ?: default

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val VISITOR = stringPreferencesKey("visitor")
        val NAME = stringPreferencesKey("name")
        val EMAIL = stringPreferencesKey("email")
        val AGE = stringPreferencesKey("age")
        val DISTRICT = stringPreferencesKey("district")
        val BUDGET = stringPreferencesKey("budget")
        val AUDIENCE = stringPreferencesKey("audience")
        val INTERESTS = stringSetPreferencesKey("interests")
        val CUISINES = stringSetPreferencesKey("cuisines")
    }
}
