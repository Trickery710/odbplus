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
}
