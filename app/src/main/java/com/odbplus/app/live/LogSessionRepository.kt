package com.odbplus.app.live

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.odbplus.app.ai.data.VehicleInfo
import com.odbplus.core.protocol.ObdPid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "log_sessions")

/**
 * Serializable version of LogSession for persistence.
 */
@Serializable
data class SerializableLogSession(
    val id: String,
    val startTime: Long,
    val endTime: Long?,
    val selectedPidCodes: List<String>,
    val dataPoints: List<SerializableDataPoint>,
    val vehicleInfo: VehicleInfo? = null
)

@Serializable
data class SerializableDataPoint(
    val timestamp: Long,
    val pidValues: Map<String, Double?>
)

/**
 * Repository for persisting and retrieving log sessions.
 */
@Singleton
class LogSessionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _sessions = MutableStateFlow<List<LogSession>>(emptyList())
    val sessions: StateFlow<List<LogSession>> = _sessions.asStateFlow()

    // Keys for selected PIDs persistence
    private val selectedPidsKey = stringSetPreferencesKey("selected_pids")
    private val sessionsKey = stringPreferencesKey("sessions_json")

    // In-memory cache of current live data state
    private val _currentPidValues = MutableStateFlow<Map<ObdPid, PidDisplayState>>(emptyMap())
    val currentPidValues: StateFlow<Map<ObdPid, PidDisplayState>> = _currentPidValues.asStateFlow()

    private val _selectedPids = MutableStateFlow<List<ObdPid>>(emptyList())
    val selectedPids: StateFlow<List<ObdPid>> = _selectedPids.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            // Single read — both sessions and selected PIDs live in the same DataStore file.
            val prefs = context.dataStore.data.first()

            val sessionsJson = prefs[sessionsKey]
            if (sessionsJson != null) {
                runCatching { json.decodeFromString<List<SerializableLogSession>>(sessionsJson) }
                    .onSuccess { serialized -> _sessions.value = serialized.map { it.toLogSession() } }
                    .onFailure { _sessions.value = emptyList() }
            }

            val pidCodes = prefs[selectedPidsKey]
            if (pidCodes != null) {
                _selectedPids.value = pidCodes.mapNotNull { ObdPid.fromCode(it) }
            }
        } catch (e: Exception) {
            _sessions.value = emptyList()
            _selectedPids.value = emptyList()
        }
    }

    suspend fun saveSession(session: LogSession) = withContext(Dispatchers.IO) {
        val currentSessions = _sessions.value.toMutableList()
        // Keep only last 20 sessions
        currentSessions.add(session)
        val trimmed = currentSessions.takeLast(20)
        _sessions.value = trimmed
        persistSessions(trimmed)
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        val updated = _sessions.value.filter { it.id != sessionId }
        _sessions.value = updated
        persistSessions(updated)
    }

    suspend fun clearAllSessions() = withContext(Dispatchers.IO) {
        _sessions.value = emptyList()
        persistSessions(emptyList())
    }

    private suspend fun persistSessions(sessions: List<LogSession>) {
        try {
            val serialized = sessions.map { it.toSerializable() }
            context.dataStore.edit { prefs ->
                prefs[sessionsKey] = json.encodeToString(serialized)
            }
        } catch (e: Exception) {
            // Ignore persistence errors
        }
    }

    suspend fun saveSelectedPids(pids: List<ObdPid>) = withContext(Dispatchers.IO) {
        _selectedPids.value = pids
        try {
            context.dataStore.edit { prefs ->
                prefs[selectedPidsKey] = pids.map { it.code }.toSet()
            }
        } catch (e: Exception) {
            // Ignore persistence errors
        }
    }

    fun updatePidValues(values: Map<ObdPid, PidDisplayState>) {
        _currentPidValues.value = values
    }

    fun updateSelectedPids(pids: List<ObdPid>) {
        _selectedPids.value = pids
    }

    private fun SerializableLogSession.toLogSession(): LogSession {
        return LogSession(
            id = id,
            startTime = startTime,
            endTime = endTime,
            selectedPids = selectedPidCodes.mapNotNull { ObdPid.fromCode(it) },
            dataPoints = dataPoints.map { dp ->
                LoggedDataPoint(
                    timestamp = dp.timestamp,
                    pidValues = dp.pidValues
                        .mapNotNull { (code, value) ->
                            ObdPid.fromCode(code)?.let { pid -> pid to value }
                        }
                        .toMap()
                )
            },
            vehicleInfo = vehicleInfo
        )
    }

    private fun LogSession.toSerializable(): SerializableLogSession {
        return SerializableLogSession(
            id = id,
            startTime = startTime,
            endTime = endTime,
            selectedPidCodes = selectedPids.map { it.code },
            dataPoints = dataPoints.map { dp ->
                SerializableDataPoint(
                    timestamp = dp.timestamp,
                    pidValues = dp.pidValues.mapKeys { (pid, _) -> pid.code }
                )
            },
            vehicleInfo = vehicleInfo
        )
    }
}
