package com.odbplus.app.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.odbplus.app.ai.data.AiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_settings")

/**
 * Repository for storing AI-related settings, including API keys for multiple providers.
 */
@Singleton
class AiSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val selectedProviderKey = stringPreferencesKey("selected_provider")
    private val claudeApiKeyKey = stringPreferencesKey("claude_api_key")
    private val geminiApiKeyKey = stringPreferencesKey("gemini_api_key")
    private val groqApiKeyKey = stringPreferencesKey("groq_api_key")

    // Google Auth keys
    private val googleIdTokenKey = stringPreferencesKey("google_id_token")
    private val googleEmailKey = stringPreferencesKey("google_email")
    private val googleNameKey = stringPreferencesKey("google_name")
    private val useGoogleAuthForGeminiKey = stringPreferencesKey("use_google_auth_gemini")

    /**
     * Flow of the currently selected provider.
     */
    val selectedProvider: Flow<AiProvider> = context.aiSettingsDataStore.data
        .map { preferences ->
            val providerName = preferences[selectedProviderKey]
            providerName?.let {
                try {
                    AiProvider.valueOf(it)
                } catch (e: Exception) {
                    AiProvider.GEMINI // Default to free option
                }
            } ?: AiProvider.GEMINI
        }

    /**
     * Flow of whether the current provider has an API key configured.
     */
    val hasApiKey: Flow<Boolean> = context.aiSettingsDataStore.data
        .map { preferences ->
            val provider = preferences[selectedProviderKey]?.let {
                try { AiProvider.valueOf(it) } catch (e: Exception) { AiProvider.GEMINI }
            } ?: AiProvider.GEMINI

            val key = getApiKeyForProvider(preferences, provider)
            !key.isNullOrBlank()
        }

    /**
     * Flow of the current provider's API key (or null if not set).
     */
    val apiKey: Flow<String?> = context.aiSettingsDataStore.data
        .map { preferences ->
            val provider = preferences[selectedProviderKey]?.let {
                try { AiProvider.valueOf(it) } catch (e: Exception) { AiProvider.GEMINI }
            } ?: AiProvider.GEMINI

            getApiKeyForProvider(preferences, provider)
        }

    private fun getApiKeyForProvider(preferences: Preferences, provider: AiProvider): String? {
        val key = when (provider) {
            AiProvider.CLAUDE -> preferences[claudeApiKeyKey]
            AiProvider.GEMINI -> preferences[geminiApiKeyKey]
            AiProvider.GROQ -> preferences[groqApiKeyKey]
        }
        return key?.takeIf { it.isNotBlank() }
    }

    /**
     * Get API key for a specific provider.
     */
    fun getApiKeyForProvider(provider: AiProvider): Flow<String?> = context.aiSettingsDataStore.data
        .map { preferences -> getApiKeyForProvider(preferences, provider) }

    /**
     * Check if a specific provider has an API key configured.
     */
    fun hasApiKeyForProvider(provider: AiProvider): Flow<Boolean> = context.aiSettingsDataStore.data
        .map { preferences -> !getApiKeyForProvider(preferences, provider).isNullOrBlank() }

    /**
     * Save the selected provider.
     */
    suspend fun saveSelectedProvider(provider: AiProvider) {
        context.aiSettingsDataStore.edit { preferences ->
            preferences[selectedProviderKey] = provider.name
        }
    }

    /**
     * Save API key for a specific provider.
     */
    suspend fun saveApiKey(provider: AiProvider, key: String) {
        context.aiSettingsDataStore.edit { preferences ->
            when (provider) {
                AiProvider.CLAUDE -> preferences[claudeApiKeyKey] = key.trim()
                AiProvider.GEMINI -> preferences[geminiApiKeyKey] = key.trim()
                AiProvider.GROQ -> preferences[groqApiKeyKey] = key.trim()
            }
        }
    }

    /**
     * Clear API key for a specific provider.
     */
    suspend fun clearApiKey(provider: AiProvider) {
        context.aiSettingsDataStore.edit { preferences ->
            when (provider) {
                AiProvider.CLAUDE -> preferences.remove(claudeApiKeyKey)
                AiProvider.GEMINI -> preferences.remove(geminiApiKeyKey)
                AiProvider.GROQ -> preferences.remove(groqApiKeyKey)
            }
        }
    }

    /**
     * Validate API key format for a provider.
     */
    fun isValidApiKeyFormat(provider: AiProvider, key: String): Boolean {
        return provider.isValidKeyFormat(key)
    }

    // ==================== Google Auth Methods ====================

    /**
     * Flow of whether Google Auth is being used for Gemini.
     */
    val useGoogleAuthForGemini: Flow<Boolean> = context.aiSettingsDataStore.data
        .map { preferences ->
            preferences[useGoogleAuthForGeminiKey] == "true"
        }

    /**
     * Flow of Google ID token.
     */
    val googleIdToken: Flow<String?> = context.aiSettingsDataStore.data
        .map { preferences ->
            preferences[googleIdTokenKey]?.takeIf { it.isNotBlank() }
        }

    /**
     * Flow of Google user email.
     */
    val googleEmail: Flow<String?> = context.aiSettingsDataStore.data
        .map { preferences ->
            preferences[googleEmailKey]?.takeIf { it.isNotBlank() }
        }

    /**
     * Flow of Google user name.
     */
    val googleName: Flow<String?> = context.aiSettingsDataStore.data
        .map { preferences ->
            preferences[googleNameKey]?.takeIf { it.isNotBlank() }
        }

    /**
     * Save Google auth state.
     */
    suspend fun saveGoogleAuth(idToken: String, email: String?, name: String?) {
        context.aiSettingsDataStore.edit { preferences ->
            preferences[googleIdTokenKey] = idToken
            preferences[useGoogleAuthForGeminiKey] = "true"
            email?.let { preferences[googleEmailKey] = it }
            name?.let { preferences[googleNameKey] = it }
        }
    }

    /**
     * Clear Google auth state.
     */
    suspend fun clearGoogleAuth() {
        context.aiSettingsDataStore.edit { preferences ->
            preferences.remove(googleIdTokenKey)
            preferences.remove(googleEmailKey)
            preferences.remove(googleNameKey)
            preferences[useGoogleAuthForGeminiKey] = "false"
        }
    }

    /**
     * Get saved Google auth data for restoring state.
     */
    suspend fun getSavedGoogleAuth(): Triple<String?, String?, String?> {
        val preferences = context.aiSettingsDataStore.data.first()
        return Triple(
            preferences[googleIdTokenKey],
            preferences[googleEmailKey],
            preferences[googleNameKey]
        )
    }
}
