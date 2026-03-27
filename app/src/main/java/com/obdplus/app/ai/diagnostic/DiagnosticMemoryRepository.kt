package com.obdplus.app.ai.diagnostic

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.diagnosticMemoryDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "diagnostic_memory")

@Singleton
class DiagnosticMemoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @Serializable
    data class VinMemory(
        val vin: String,
        val knownDtcs: List<String> = emptyList(),
        val confirmedRepairs: List<String> = emptyList(),
        val recurringFaultTokens: List<String> = emptyList(),
        val anomalyHistory: List<String> = emptyList(),
        val lastSeenTimestamp: Long = 0L
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val memoryKey = stringPreferencesKey("vin_memories")

    suspend fun getMemory(vin: String): VinMemory? {
        val prefs = context.diagnosticMemoryDataStore.data
        var result: VinMemory? = null
        prefs.collect { preferences ->
            val raw = preferences[memoryKey] ?: return@collect
            val map = runCatching {
                json.decodeFromString<Map<String, VinMemory>>(raw)
            }.getOrElse { emptyMap() }
            result = map[vin]
        }
        return result
    }

    suspend fun recordDiagnosticSession(
        vin: String,
        dtcCodes: List<String>,
        faultTokens: List<String>,
        anomalies: List<String>
    ) {
        if (vin.isBlank()) return
        context.diagnosticMemoryDataStore.edit { prefs ->
            val raw = prefs[memoryKey]
            val map = raw?.let {
                runCatching { json.decodeFromString<Map<String, VinMemory>>(it) }.getOrElse { emptyMap() }
            }?.toMutableMap() ?: mutableMapOf()

            val existing = map[vin] ?: VinMemory(vin = vin)

            // Merge DTCs
            val mergedDtcs = (existing.knownDtcs + dtcCodes).distinct()

            // Track recurring fault tokens: tokens seen in a previous session
            val newRecurring = (existing.recurringFaultTokens +
                faultTokens.filter { it in existing.knownDtcs || it in existing.recurringFaultTokens }
            ).distinct()

            // Merge anomaly history (keep last 20)
            val mergedAnomalies = (existing.anomalyHistory + anomalies).distinct().takeLast(20)

            val updated = existing.copy(
                knownDtcs = mergedDtcs,
                recurringFaultTokens = newRecurring,
                anomalyHistory = mergedAnomalies,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            map[vin] = updated
            prefs[memoryKey] = json.encodeToString(map as Map<String, VinMemory>)
        }
    }

    suspend fun recordConfirmedRepair(vin: String, repairDescription: String) {
        if (vin.isBlank() || repairDescription.isBlank()) return
        context.diagnosticMemoryDataStore.edit { prefs ->
            val raw = prefs[memoryKey]
            val map = raw?.let {
                runCatching { json.decodeFromString<Map<String, VinMemory>>(it) }.getOrElse { emptyMap() }
            }?.toMutableMap() ?: mutableMapOf()

            val existing = map[vin] ?: VinMemory(vin = vin)
            val updated = existing.copy(
                confirmedRepairs = (existing.confirmedRepairs + repairDescription).distinct()
            )
            map[vin] = updated
            prefs[memoryKey] = json.encodeToString(map as Map<String, VinMemory>)
        }
    }
}
