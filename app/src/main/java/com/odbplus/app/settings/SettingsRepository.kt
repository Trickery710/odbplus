package com.odbplus.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val DEFAULT_POLL_INTERVAL_MS = longPreferencesKey("default_poll_interval_ms")
    private val AUTO_ACQUIRE_VIN        = booleanPreferencesKey("auto_acquire_vin")
    private val PROFESSIONAL_LEVEL      = stringPreferencesKey("professional_level")
    private val OWNED_TOOLS             = stringPreferencesKey("owned_tools")

    val defaultPollIntervalMs: Flow<Long> =
        context.settingsDataStore.data.map { it[DEFAULT_POLL_INTERVAL_MS] ?: 500L }

    val autoAcquireVin: Flow<Boolean> =
        context.settingsDataStore.data.map { it[AUTO_ACQUIRE_VIN] ?: true }

    val professionalLevel: Flow<ProfessionalLevel> =
        context.settingsDataStore.data.map { prefs ->
            prefs[PROFESSIONAL_LEVEL]
                ?.let { runCatching { ProfessionalLevel.valueOf(it) }.getOrNull() }
                ?: ProfessionalLevel.BEGINNER
        }

    /** Returns the set of [OwnedTool.id] strings the user has marked as owned. */
    val ownedToolIds: Flow<Set<String>> =
        context.settingsDataStore.data.map { prefs ->
            prefs[OWNED_TOOLS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
        }

    suspend fun setDefaultPollInterval(ms: Long) {
        context.settingsDataStore.edit { it[DEFAULT_POLL_INTERVAL_MS] = ms }
    }

    suspend fun setAutoAcquireVin(enabled: Boolean) {
        context.settingsDataStore.edit { it[AUTO_ACQUIRE_VIN] = enabled }
    }

    suspend fun setProfessionalLevel(level: ProfessionalLevel) {
        context.settingsDataStore.edit { it[PROFESSIONAL_LEVEL] = level.name }
    }

    suspend fun setOwnedToolIds(ids: Set<String>) {
        context.settingsDataStore.edit { it[OWNED_TOOLS] = ids.joinToString(",") }
    }
}
