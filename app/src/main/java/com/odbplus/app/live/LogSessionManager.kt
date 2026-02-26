package com.odbplus.app.live

import com.odbplus.app.ai.data.VehicleInfo
import com.odbplus.core.protocol.ObdPid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LogSessionManager(private val repository: LogSessionRepository) {

    val sessions: StateFlow<List<LogSession>> = repository.sessions

    private val _isLogging = MutableStateFlow(false)
    val isLogging: StateFlow<Boolean> = _isLogging.asStateFlow()

    private val _currentSession = MutableStateFlow<LogSession?>(null)
    val currentSession: StateFlow<LogSession?> = _currentSession.asStateFlow()

    fun start(selectedPids: List<ObdPid>, vehicleInfo: VehicleInfo? = null) {
        if (selectedPids.isEmpty()) return
        _currentSession.value = LogSession(selectedPids = selectedPids, vehicleInfo = vehicleInfo)
        _isLogging.value = true
    }

    fun stop(scope: CoroutineScope) {
        val session = _currentSession.value ?: return
        val finalSession = session.copy(endTime = System.currentTimeMillis())
        _currentSession.value = null
        _isLogging.value = false
        scope.launch { repository.saveSession(finalSession) }
    }

    fun addPoint(pidValues: Map<ObdPid, Double?>) {
        if (!_isLogging.value) return
        _currentSession.update { session ->
            session?.copy(dataPoints = session.dataPoints + LoggedDataPoint(
                timestamp = System.currentTimeMillis(),
                pidValues = pidValues
            ))
        }
    }

    fun delete(session: LogSession, scope: CoroutineScope) {
        scope.launch { repository.deleteSession(session.id) }
    }

    fun clearAll(scope: CoroutineScope) {
        scope.launch { repository.clearAllSessions() }
    }
}
