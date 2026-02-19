package com.odbplus.app.ai

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents the current Google authentication state.
 */
data class GoogleAuthState(
    val isSignedIn: Boolean = false,
    val userEmail: String? = null,
    val userName: String? = null,
    val userPhotoUrl: String? = null,
    val idToken: String? = null
)

/**
 * Result of a sign-in attempt.
 */
sealed class GoogleSignInResult {
    data class Success(val state: GoogleAuthState) : GoogleSignInResult()
    data class Error(val message: String) : GoogleSignInResult()
    object Cancelled : GoogleSignInResult()
}

/**
 * Manages Google Sign-In using Credential Manager API.
 *
 * Setup required:
 * 1. Create a project in Google Cloud Console
 * 2. Configure OAuth consent screen
 * 3. Create OAuth 2.0 Client ID (Android type)
 * 4. Add your app's SHA-1 fingerprint
 * 5. Replace WEB_CLIENT_ID with your Web Client ID (not Android Client ID)
 */
@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // TODO: Replace with your Web Client ID from Google Cloud Console
        // Go to: https://console.cloud.google.com/apis/credentials
        // Create OAuth 2.0 Client ID -> Web application
        // Copy the Client ID here
        const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
    }

    private val credentialManager = CredentialManager.create(context)

    private val _authState = MutableStateFlow(GoogleAuthState())
    val authState: StateFlow<GoogleAuthState> = _authState.asStateFlow()

    /**
     * Check if Google Sign-In is properly configured.
     */
    fun isConfigured(): Boolean {
        return WEB_CLIENT_ID != "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
    }

    /**
     * Initiate Google Sign-In flow.
     * Must be called from an Activity context.
     */
    suspend fun signIn(activityContext: Context): GoogleSignInResult {
        if (!isConfigured()) {
            return GoogleSignInResult.Error(
                "Google Sign-In not configured. Please set up OAuth credentials in Google Cloud Console."
            )
        }

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )

            handleSignInResult(result)
        } catch (e: GetCredentialCancellationException) {
            Timber.d("Google Sign-In cancelled by user")
            GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            Timber.w(e, "No Google credentials available")
            GoogleSignInResult.Error("No Google account found. Please add a Google account to your device.")
        } catch (e: GetCredentialException) {
            Timber.e(e, "Google Sign-In failed")
            GoogleSignInResult.Error("Sign-in failed: ${e.message}")
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during Google Sign-In")
            GoogleSignInResult.Error("Unexpected error: ${e.message}")
        }
    }

    private fun handleSignInResult(result: GetCredentialResponse): GoogleSignInResult {
        val credential = result.credential

        return when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

                        val newState = GoogleAuthState(
                            isSignedIn = true,
                            userEmail = googleIdTokenCredential.id,
                            userName = googleIdTokenCredential.displayName,
                            userPhotoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                            idToken = googleIdTokenCredential.idToken
                        )

                        _authState.value = newState
                        Timber.d("Google Sign-In successful: ${newState.userEmail}")

                        GoogleSignInResult.Success(newState)
                    } catch (e: GoogleIdTokenParsingException) {
                        Timber.e(e, "Failed to parse Google ID token")
                        GoogleSignInResult.Error("Failed to parse credentials")
                    }
                } else {
                    Timber.w("Unexpected credential type: ${credential.type}")
                    GoogleSignInResult.Error("Unexpected credential type")
                }
            }
            else -> {
                Timber.w("Unexpected credential class: ${credential.javaClass.name}")
                GoogleSignInResult.Error("Unexpected credential type")
            }
        }
    }

    /**
     * Sign out and clear credentials.
     */
    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            _authState.value = GoogleAuthState()
            Timber.d("Google Sign-Out successful")
        } catch (e: Exception) {
            Timber.e(e, "Error during sign out")
        }
    }

    /**
     * Restore auth state from saved token.
     */
    fun restoreAuthState(idToken: String?, email: String?, name: String?) {
        if (!idToken.isNullOrBlank()) {
            _authState.value = GoogleAuthState(
                isSignedIn = true,
                idToken = idToken,
                userEmail = email,
                userName = name
            )
        }
    }
}
