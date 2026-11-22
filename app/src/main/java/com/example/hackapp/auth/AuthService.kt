package com.example.hackapp.auth

import androidx.compose.runtime.staticCompositionLocalOf
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

enum class AuthError {
    InvalidCredentials,
    InvalidEmail,
    EmailInUse,
    WeakPassword,
    Network,
    ProviderDisabled,
    GoogleFailed,
    Unknown,
}

sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}

/** The signed-in account. [name] and [email] are null when the provider does not supply them. */
data class AuthUser(val uid: String, val name: String?, val email: String?)

/** Email account operations. Screens depend on this interface, not on Firebase. */
interface AuthService {
    val currentUser: AuthUser?
    suspend fun signIn(email: String, password: String): AuthResult
    suspend fun register(email: String, password: String): AuthResult
    suspend fun sendPasswordReset(email: String): AuthResult
}

class FirebaseAuthService : AuthService {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    override val currentUser: AuthUser?
        get() = auth.currentUser?.let { AuthUser(it.uid, it.displayName, it.email) }

    override suspend fun signIn(email: String, password: String) = run {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    override suspend fun register(email: String, password: String) = run {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    /** An unknown address reports success, so this form cannot reveal who has an account. */
    override suspend fun sendPasswordReset(email: String): AuthResult = try {
        auth.sendPasswordResetEmail(email.trim()).await()
        AuthResult.Success
    } catch (_: FirebaseAuthInvalidUserException) {
        AuthResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Failure(e.toAuthError())
    }

    private suspend fun run(operation: suspend () -> Unit): AuthResult = try {
        operation()
        AuthResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Failure(e.toAuthError())
    }
}

internal fun Exception.toAuthError(): AuthError = when (this) {
    is FirebaseNetworkException -> AuthError.Network
    is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword
    is FirebaseAuthUserCollisionException -> AuthError.EmailInUse
    is FirebaseAuthInvalidUserException -> AuthError.InvalidCredentials
    is FirebaseAuthInvalidCredentialsException ->
        if (errorCode == "ERROR_INVALID_EMAIL") AuthError.InvalidEmail else AuthError.InvalidCredentials
    is FirebaseAuthException ->
        if (errorCode == "ERROR_OPERATION_NOT_ALLOWED") AuthError.ProviderDisabled else AuthError.Unknown
    else -> AuthError.Unknown
}

val LocalAuthService = staticCompositionLocalOf<AuthService> { FirebaseAuthService() }
