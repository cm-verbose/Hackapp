package com.example.hackapp.auth

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.util.Base64
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/** Handles authentification using Google. */
class AuthHandler(context: Context, val activity: Activity) {
    private val credentialManager = CredentialManager.create(context)
    private val webClientId: String =
        "1009330576567-gai095q1he8i1dqdd71nkvsdm42k2236.apps.googleusercontent.com"
    private val auth = FirebaseAuth.getInstance()

    /** [onSignIn] receives whether this sign-in created a new account. */
    suspend fun signIn(onSignIn: (isNewUser: Boolean) -> Unit, onError: (AuthError) -> Unit) {
        val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(generateSecureRandomNonce())
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val mutableContext = MutableContextWrapper(activity)

        try {
            val result = credentialManager.getCredential(
                request = request,
                context = mutableContext
            )
            handleSignIn(result, onSignIn, onError)
        } catch (_: GetCredentialCancellationException) {
            Log.d(AUTH_TAG, "Sign in cancelled")
        } catch (err: GetCredentialException) {
            Log.e(AUTH_TAG, "Sign in Error: ${err.message}")
            onError(AuthError.GoogleFailed)
        }
    }

    private suspend fun handleSignIn(
        result: GetCredentialResponse,
        onSignIn: (isNewUser: Boolean) -> Unit,
        onError: (AuthError) -> Unit,
    ) {
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            Log.e(AUTH_TAG, "Unexpected type of credential")
            onError(AuthError.GoogleFailed)
            return
        }
        try {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential =
                GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            if (authResult.user != null) {
                onSignIn(authResult.additionalUserInfo?.isNewUser == true)
            } else {
                Log.e(AUTH_TAG, "No user provided")
                onError(AuthError.GoogleFailed)
            }
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(AUTH_TAG, "Received an invalid google id token response", e)
            onError(AuthError.GoogleFailed)
        } catch (e: FirebaseException) {
            Log.e(AUTH_TAG, "Firebase rejected the Google sign-in", e)
            onError(e.toAuthError().takeUnless { it == AuthError.Unknown } ?: AuthError.GoogleFailed)
        }
    }

    fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(
            randomBytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }
}
