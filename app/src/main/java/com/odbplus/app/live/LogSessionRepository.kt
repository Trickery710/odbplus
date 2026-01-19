package com.odbplus.app.live

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.odbplus.core.protocol.ObdPid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    val dataPoints: List<SerializableDataPoint>
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

    suspend fun initialize() {
        // Load saved sessions
        loadSessions()
        // Load selected PIDs
        loadSelectedPids()
    }

    private suspend fun loadSessions() {
        try {
            val prefs = context.dataStore.data.first()
            val sessionsJson = prefs[sessionsKey] ?: return
            val serialized = json.decodeFromString<List<SerializableLogSession>>(sessionsJson)
            _sessions.value = serialized.map { it.toLogSession() }
        } catch (e: Exception) {
            // Ignore errors on load, start fresh
            _sessions.value = emptyList()
        }
    }

    private suspend fun loadSelectedPids() {
        try {
            val prefs = context.dataStore.data.first()
            val pidCodes = prefs[selectedPidsKey] ?: return
            _selectedPids.value = pidCodes.mapNotNull { ObdPid.fromCode(it) }
        } catch (e: Exception) {
            _selectedPids.value = emptyList()
        }
    }

    suspend fun saveSession(session: LogSession) {
        val currentSessions = _sessions.value.toMutableList()
        // Keep only last 20 sessions
        currentSessions.add(session)
        val trimmed = currentSessions.takeLast(20)
        _sessions.value = trimmed
        persistSessions(trimmed)
    }

    suspend fun deleteSession(sessionId: String) {
        val updated = _sessions.value.filter { it.id != sessionId }
        _sessions.value = updated
        persistSessions(updated)
    }

    suspend fun clearAllSessions() {
        _sessions.value = emptyList()
        persistSessions(emptyList())
    }

    private suspend fun persistSessions(sessions: List<LogSession>) {
        val serialized = sessions.map { it.toSerializable() }
        context.dataStore.edit { prefs ->
            prefs[sessionsKey] = json.encodeToString(serialized)
        }
    }

    suspend fun saveSelectedPids(pids: List<ObdPid>) {
        _selectedPids.value = pids
        context.dataStore.edit { prefs ->
            prefs[selectedPidsKey] = pids.map { it.code }.toSet()
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
                    pidValues = dp.pidValues.mapKeys { (code, _) ->
                        ObdPid.fromCode(code) ?: ObdPid.ENGINE_RPM
                    }.filterKeys { it != ObdPid.ENGINE_RPM || dp.pidValues.containsKey("0C") }
                )
            }
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
            }
        )
    }
}
