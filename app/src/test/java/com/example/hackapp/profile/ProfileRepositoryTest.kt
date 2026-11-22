package com.example.hackapp.profile

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.hackapp.auth.AuthResult
import com.example.hackapp.auth.AuthService
import com.example.hackapp.auth.AuthUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val profile = Profile(
        visitorType = VisitorType.Visitor,
        name = "Camille",
        email = "camille@exemple.ca",
        ageRange = AgeRange.From25To34,
        districtOfStayId = "district-a",
        lunchBudget = BudgetBracket.Under20,
        dinnerBudget = BudgetBracket.Under50,
        eventBudget = BudgetBracket.From100,
        lateNightBudget = BudgetBracket.Under20,
        audience = setOf(AudiencePreference.FamilyFriendly, AudiencePreference.NoAlcohol),
        interests = setOf(Interest.Music, Interest.Comedy),
        cuisineGroups = setOf("group-a"),
    )

    @After
    fun closeStore() = scope.cancel()

    private fun repository() = DataStoreProfileRepository(
        PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("profiles.preferences_pb") },
    )

    @Test
    fun `profile survives encoding`() {
        assertEquals(profile, ProfileCodec.decode(ProfileCodec.encode(profile)))
    }

    @Test
    fun `late night off stays null`() {
        val off = profile.copy(lateNightBudget = null)
        assertNull(ProfileCodec.decode(ProfileCodec.encode(off))?.lateNightBudget)
    }

    @Test
    fun `unreadable profile counts as missing`() {
        assertNull(ProfileCodec.decode("not json"))
        assertNull(ProfileCodec.decode("""{"name":"missing required fields"}"""))
    }

    @Test
    fun `profiles are kept per account`() = runBlocking {
        val repository = repository()
        repository.saveProfile("uid-a", profile)
        assertEquals(profile, repository.getProfile("uid-a"))
        assertNull(repository.getProfile("uid-b"))
    }

    @Test
    fun `signed out goes to welcome`() = runBlocking {
        assertEquals(SessionDestination.Welcome, resolveSessionDestination(FakeAuth(null), repository()))
    }

    @Test
    fun `existing account without profile goes to onboarding`() = runBlocking {
        val auth = FakeAuth(AuthUser("uid-a", null, null))
        assertEquals(SessionDestination.Onboarding, resolveSessionDestination(auth, repository()))
    }

    @Test
    fun `account with profile goes home`() = runBlocking {
        val repository = repository()
        repository.saveProfile("uid-a", profile)
        val auth = FakeAuth(AuthUser("uid-a", null, null))
        assertEquals(SessionDestination.Home, resolveSessionDestination(auth, repository))
    }

    private class FakeAuth(override val currentUser: AuthUser?) : AuthService {
        override suspend fun signIn(email: String, password: String) = AuthResult.Success
        override suspend fun register(email: String, password: String) = AuthResult.Success
        override suspend fun sendPasswordReset(email: String) = AuthResult.Success
    }
}
