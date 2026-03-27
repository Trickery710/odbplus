package com.obdplus.app.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vehicleHistoryDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "vehicle_history")

/**
 * Persists the last [MAX_HISTORY] VINs encountered across sessions.
 *
 * The most-recently-seen VIN is always at the end of the list.
 * Duplicates are removed on insertion so each VIN appears at most once.
 */
@Singleton
class VehicleHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val historyKey = stringPreferencesKey("vin_history")

    val vinHistory: Flow<List<String>> = context.vehicleHistoryDataStore.data.map { prefs ->
        runCatching {
            val raw = prefs[historyKey] ?: return@map emptyList()
            json.decodeFromString<List<String>>(raw)
        }.getOrElse { emptyList() }
    }

    /**
     * Records [vin] in the history.
     *
     * If the VIN is already present it is moved to the end (most recent).
     * Entries beyond [MAX_HISTORY] are dropped from the front.
     */
    suspend fun recordVin(vin: String) {
        if (vin.isBlank()) return
        context.vehicleHistoryDataStore.edit { prefs ->
            val current = runCatching {
                prefs[historyKey]?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()
            }.getOrElse { emptyList() }

            val updated = (current.filterNot { it == vin } + vin).takeLast(MAX_HISTORY)
            prefs[historyKey] = json.encodeToString(updated)
        }
    }

    companion object {
        const val MAX_HISTORY = 10
    }
}
