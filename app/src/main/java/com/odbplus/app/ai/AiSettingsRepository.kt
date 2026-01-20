package com.odbplus.app.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_settings")

/**
 * Repository for storing AI-related settings, including the Claude API key.
 */
@Singleton
class AiSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val apiKeyKey = stringPreferencesKey("claude_api_key")

    /**
     * Flow of the current API key status.
     */
    val hasApiKey: Flow<Boolean> = context.aiSettingsDataStore.data
        .map { preferences ->
            val key = preferences[apiKeyKey]
            !key.isNullOrBlank()
        }

    /**
     * Flow of the current API key (or null if not set).
     */
    val apiKey: Flow<String?> = context.aiSettingsDataStore.data
        .map { preferences ->
            preferences[apiKeyKey]?.takeIf { it.isNotBlank() }
        }

    /**
     * Save the Claude API key.
     */
    suspend fun saveApiKey(key: String) {
        context.aiSettingsDataStore.edit { preferences ->
            preferences[apiKeyKey] = key.trim()
        }
    }

    /**
     * Clear the stored API key.
     */
    suspend fun clearApiKey() {
        context.aiSettingsDataStore.edit { preferences ->
            preferences.remove(apiKeyKey)
        }
    }

    /**
     * Validate API key format (basic check).
     */
    fun isValidApiKeyFormat(key: String): Boolean {
        val trimmed = key.trim()
        return trimmed.startsWith("sk-ant-") && trimmed.length > 20
    }
}
