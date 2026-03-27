package com.obdplus.app.ai.diagnostic

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.adaptiveLearningDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "adaptive_learning")

@Singleton
class AdaptiveLearningRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val weightsKey = stringPreferencesKey("outcome_weights")

    private fun makeKey(faultToken: String, rootCause: String) = "$faultToken|$rootCause"

    suspend fun getWeightAdjustment(faultToken: String, rootCause: String): Float {
        var result = 0f
        context.adaptiveLearningDataStore.data.collect { prefs ->
            val raw = prefs[weightsKey] ?: return@collect
            val map = runCatching {
                json.decodeFromString<Map<String, Float>>(raw)
            }.getOrElse { emptyMap() }
            result = map[makeKey(faultToken, rootCause)] ?: 0f
        }
        return result
    }

    suspend fun recordConfirmedOutcome(
        faultToken: String,
        confirmedRootCause: String,
        wasCorrect: Boolean
    ) {
        context.adaptiveLearningDataStore.edit { prefs ->
            val raw = prefs[weightsKey]
            val map = raw?.let {
                runCatching { json.decodeFromString<Map<String, Float>>(it) }.getOrElse { emptyMap() }
            }?.toMutableMap() ?: mutableMapOf()

            val key = makeKey(faultToken, confirmedRootCause)
            val current = map[key] ?: 0f
            val updated = if (wasCorrect) {
                (current + 0.1f).coerceAtMost(2.0f)
            } else {
                (current - 0.05f).coerceAtLeast(0.1f)
            }
            map[key] = updated
            prefs[weightsKey] = json.encodeToString(map as Map<String, Float>)
        }
    }

    suspend fun getTopConfirmedCauses(faultToken: String): List<Pair<String, Float>> {
        var result: List<Pair<String, Float>> = emptyList()
        context.adaptiveLearningDataStore.data.collect { prefs ->
            val raw = prefs[weightsKey] ?: return@collect
            val map = runCatching {
                json.decodeFromString<Map<String, Float>>(raw)
            }.getOrElse { emptyMap() }

            result = map.entries
                .filter { it.key.startsWith("$faultToken|") }
                .map { Pair(it.key.substringAfter("|"), it.value) }
                .sortedByDescending { it.second }
        }
        return result
    }
}
