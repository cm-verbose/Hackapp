package com.example.hackapp.profile

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Stores one Profile per account. Screens depend on this interface, so Firestore can replace it. */
interface ProfileRepository {
    /** Returns null when this account has no usable Profile. Throws if storage cannot be read. */
    suspend fun getProfile(uid: String): Profile?
    suspend fun saveProfile(uid: String, profile: Profile)
}

internal object ProfileCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(profile: Profile): String = json.encodeToString(Profile.serializer(), profile)

    /** An unreadable Profile counts as missing, so the person can go through onboarding again. */
    fun decode(text: String): Profile? = try {
        json.decodeFromString(Profile.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

class DataStoreProfileRepository(private val store: DataStore<Preferences>) : ProfileRepository {
    override suspend fun getProfile(uid: String): Profile? =
        store.data.first()[keyFor(uid)]?.let(ProfileCodec::decode)

    override suspend fun saveProfile(uid: String, profile: Profile) {
        store.edit { it[keyFor(uid)] = ProfileCodec.encode(profile) }
    }

    private fun keyFor(uid: String) = stringPreferencesKey("profile_$uid")
}

private val Context.profileDataStore by preferencesDataStore(name = "profiles")

fun createProfileRepository(context: Context): ProfileRepository =
    DataStoreProfileRepository(context.applicationContext.profileDataStore)

val LocalProfileRepository = staticCompositionLocalOf<ProfileRepository> {
    error("ProfileRepository not provided")
}
