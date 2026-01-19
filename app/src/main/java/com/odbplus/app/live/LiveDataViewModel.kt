package com.odbplus.app.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdResponse
import com.odbplus.core.protocol.ObdService
import com.odbplus.core.transport.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Represents a single PID's current state in the live data view.
 */
data class PidDisplayState(
    val pid: ObdPid,
    val isSelected: Boolean = false,
    val isLoading: Boolean = false,
    val value: Double? = null,
    val formattedValue: String = "--",
    val error: String? = null
)

/**
 * A single logged data point.
 */
data class LoggedDataPoint(
    val timestamp: Long,
    val pidValues: Map<ObdPid, Double?>
)

/**
 * A complete log session.
 */
data class LogSession(
    val id: String = System.currentTimeMillis().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val selectedPids: List<ObdPid> = emptyList(),
    val dataPoints: List<LoggedDataPoint> = emptyList()
) {
    val duration: Long get() = (endTime ?: System.currentTimeMillis()) - startTime
    val dataPointCount: Int get() = dataPoints.size
}

/**
 * UI state for the live data screen.
 */
data class LiveDataUiState(
    val isConnected: Boolean = false,
    val isPolling: Boolean = false,
    val pollIntervalMs: Long = 500L,
    val availablePids: List<PidDisplayState> = ObdPid.entries.map { PidDisplayState(it) },
    val selectedPids: List<ObdPid> = emptyList(),
    val pidValues: Map<ObdPid, PidDisplayState> = emptyMap(),
    // Logging state
    val isLogging: Boolean = false,
    val currentLogSession: LogSession? = null,
    val savedSessions: List<LogSession> = emptyList(),
    // Replay state
    val isReplaying: Boolean = false,
    val replaySession: LogSession? = null,
    val replayIndex: Int = 0,
    val replaySpeed: Float = 1.0f
)

@HiltViewModel
class LiveDataViewModel @Inject constructor(
    private val obdService: ObdService,
    private val repository: LogSessionRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveDataUiState())
    val uiState: StateFlow<LiveDataUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var replayJob: Job? = null

    init {
        // Initialize repository and restore state
        viewModelScope.launch {
            repository.initialize()

            // Restore selected PIDs from repository
            repository.selectedPids.collect { savedPids ->
                if (savedPids.isNotEmpty() && _uiState.value.selectedPids.isEmpty()) {
                    val updatedAvailable = _uiState.value.availablePids.map { pidState ->
                        pidState.copy(isSelected = pidState.pid in savedPids)
                    }
                    _uiState.update {
                        it.copy(
                            selectedPids = savedPids,
                            availablePids = updatedAvailable
                        )
                    }
                }
            }
        }

        // Restore PID values from repository
        viewModelScope.launch {
            repository.currentPidValues.collect { savedValues ->
                if (savedValues.isNotEmpty()) {
                    _uiState.update { it.copy(pidValues = savedValues) }
                }
            }
        }

        // Load saved sessions from repository
        viewModelScope.launch {
            repository.sessions.collect { sessions ->
                _uiState.update { it.copy(savedSessions = sessions) }
            }
        }

        // Monitor connection state
        viewModelScope.launch {
            obdService.connectionState.collect { state ->
                val isConnected = state == ConnectionState.CONNECTED
                _uiState.update { it.copy(isConnected = isConnected) }

                if (!isConnected && _uiState.value.isPolling) {
                    stopPolling()
                }
            }
        }
    }

    // ==================== PID Selection ====================

    fun togglePidSelection(pid: ObdPid) {
        _uiState.update { state ->
            val currentSelected = state.selectedPids.toMutableList()
            if (pid in currentSelected) {
                currentSelected.remove(pid)
            } else {
                currentSelected.add(pid)
            }

            val updatedAvailable = state.availablePids.map { pidState ->
                pidState.copy(isSelected = pidState.pid in currentSelected)
            }

            state.copy(
                selectedPids = currentSelected,
                availablePids = updatedAvailable
            )
        }
    }

    fun selectPids(pids: List<ObdPid>) {
        _uiState.update { state ->
            val updatedAvailable = state.availablePids.map { pidState ->
                pidState.copy(isSelected = pidState.pid in pids)
            }
            state.copy(
                selectedPids = pids,
                availablePids = updatedAvailable
            )
        }
        // Persist selected PIDs
        viewModelScope.launch {
            repository.saveSelectedPids(pids)
        }
    }

    fun selectPreset(preset: PidPreset) {
        selectPids(preset.pids)
    }

    fun clearSelection() {
        selectPids(emptyList())
    }

    // ==================== Polling ====================

    fun setPollInterval(intervalMs: Long) {
        _uiState.update { it.copy(pollIntervalMs = intervalMs.coerceIn(100L, 5000L)) }
    }

    fun startPolling() {
        if (_uiState.value.selectedPids.isEmpty()) return
        if (!_uiState.value.isConnected) return
        if (_uiState.value.isReplaying) return

        pollingJob?.cancel()
        _uiState.update { it.copy(isPolling = true) }

        pollingJob = viewModelScope.launch {
            while (isActive && _uiState.value.isPolling) {
                pollSelectedPids()
                delay(_uiState.value.pollIntervalMs)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _uiState.update { it.copy(isPolling = false) }

        // Stop logging if it was active
        if (_uiState.value.isLogging) {
            stopLogging()
        }
    }

    fun togglePolling() {
        if (_uiState.value.isPolling) {
            stopPolling()
        } else {
            startPolling()
        }
    }

    fun querySinglePid(pid: ObdPid) {
        if (!_uiState.value.isConnected) return
        if (_uiState.value.isReplaying) return

        viewModelScope.launch {
            updatePidLoading(pid, true)
            val response = obdService.query(pid)
            updatePidValue(pid, response)
        }
    }

    private suspend fun pollSelectedPids() {
        val selectedPids = _uiState.value.selectedPids
        val pidValuesForLog = mutableMapOf<ObdPid, Double?>()

        for (pid in selectedPids) {
            if (!_uiState.value.isPolling) break

            updatePidLoading(pid, true)
            val response = obdService.query(pid)
            updatePidValue(pid, response)

            // Collect values for logging
            if (_uiState.value.isLogging) {
                pidValuesForLog[pid] = (response as? ObdResponse.Success)?.value
            }
        }

        // Add data point to log if logging
        if (_uiState.value.isLogging && pidValuesForLog.isNotEmpty()) {
            addLogDataPoint(pidValuesForLog)
        }
    }

    private fun updatePidLoading(pid: ObdPid, isLoading: Boolean) {
        _uiState.update { state ->
            val currentPidState = state.pidValues[pid] ?: PidDisplayState(pid)
            val updatedValues = state.pidValues.toMutableMap()
            updatedValues[pid] = currentPidState.copy(isLoading = isLoading)
            state.copy(pidValues = updatedValues)
        }
    }

    private fun updatePidValue(pid: ObdPid, response: ObdResponse) {
        _uiState.update { state ->
            val pidState = when (response) {
                is ObdResponse.Success -> PidDisplayState(
                    pid = pid,
                    isSelected = pid in state.selectedPids,
                    isLoading = false,
                    value = response.value,
                    formattedValue = response.formattedValue,
                    error = null
                )
                is ObdResponse.NoData -> PidDisplayState(
                    pid = pid,
                    isSelected = pid in state.selectedPids,
                    isLoading = false,
                    value = null,
                    formattedValue = "N/A",
                    error = "Not supported"
                )
                is ObdResponse.Error -> PidDisplayState(
                    pid = pid,
                    isSelected = pid in state.selectedPids,
                    isLoading = false,
                    value = null,
                    formattedValue = "--",
                    error = response.message
                )
                is ObdResponse.ParseError -> PidDisplayState(
                    pid = pid,
                    isSelected = pid in state.selectedPids,
                    isLoading = false,
                    value = null,
                    formattedValue = "--",
                    error = response.reason
                )
            }

            val updatedValues = state.pidValues.toMutableMap()
            updatedValues[pid] = pidState

            // Persist PID values to repository for tab switching
            repository.updatePidValues(updatedValues)

            state.copy(pidValues = updatedValues)
        }
    }

    // ==================== Logging ====================

    fun startLogging() {
        if (_uiState.value.selectedPids.isEmpty()) return
        if (_uiState.value.isReplaying) return

        val session = LogSession(
            selectedPids = _uiState.value.selectedPids
        )

        _uiState.update {
            it.copy(
                isLogging = true,
                currentLogSession = session
            )
        }

        // Start polling if not already polling
        if (!_uiState.value.isPolling) {
            startPolling()
        }
    }

    fun stopLogging() {
        val currentSession = _uiState.value.currentLogSession ?: return

        val finalSession = currentSession.copy(endTime = System.currentTimeMillis())

        _uiState.update { state ->
            state.copy(
                isLogging = false,
                currentLogSession = null
            )
        }

        // Persist session to repository
        viewModelScope.launch {
            repository.saveSession(finalSession)
        }
    }

    fun toggleLogging() {
        if (_uiState.value.isLogging) {
            stopLogging()
        } else {
            startLogging()
        }
    }

    private fun addLogDataPoint(pidValues: Map<ObdPid, Double?>) {
        _uiState.update { state ->
            val currentSession = state.currentLogSession ?: return@update state
            val dataPoint = LoggedDataPoint(
                timestamp = System.currentTimeMillis(),
                pidValues = pidValues
            )
            val updatedSession = currentSession.copy(
                dataPoints = currentSession.dataPoints + dataPoint
            )
            state.copy(currentLogSession = updatedSession)
        }
    }

    fun deleteSession(session: LogSession) {
        viewModelScope.launch {
            repository.deleteSession(session.id)
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            repository.clearAllSessions()
        }
    }

    // ==================== Replay ====================

    fun startReplay(session: LogSession) {
        if (session.dataPoints.isEmpty()) return

        // Stop polling if active
        stopPolling()

        // Set the selected PIDs to match the session
        selectPids(session.selectedPids)

        _uiState.update {
            it.copy(
                isReplaying = true,
                replaySession = session,
                replayIndex = 0
            )
        }

        replayJob = viewModelScope.launch {
            val dataPoints = session.dataPoints
            var index = 0

            while (isActive && _uiState.value.isReplaying && index < dataPoints.size) {
                val dataPoint = dataPoints[index]
                applyReplayDataPoint(dataPoint)

                _uiState.update { it.copy(replayIndex = index) }

                // Calculate delay to next point
                if (index + 1 < dataPoints.size) {
                    val currentTime = dataPoint.timestamp
                    val nextTime = dataPoints[index + 1].timestamp
                    val delayMs = ((nextTime - currentTime) / _uiState.value.replaySpeed).toLong()
                    delay(delayMs.coerceAtLeast(50L))
                }

                index++
            }

            // Replay finished
            if (_uiState.value.isReplaying) {
                _uiState.update { it.copy(isReplaying = false, replaySession = null, replayIndex = 0) }
            }
        }
    }

    fun stopReplay() {
        replayJob?.cancel()
        replayJob = null
        _uiState.update {
            it.copy(
                isReplaying = false,
                replaySession = null,
                replayIndex = 0
            )
        }
    }

    fun setReplaySpeed(speed: Float) {
        _uiState.update { it.copy(replaySpeed = speed.coerceIn(0.25f, 4.0f)) }
    }

    private fun applyReplayDataPoint(dataPoint: LoggedDataPoint) {
        _uiState.update { state ->
            val updatedPidValues = state.pidValues.toMutableMap()

            for ((pid, value) in dataPoint.pidValues) {
                val formatted = if (value != null) {
                    formatReplayValue(value, pid)
                } else {
                    "N/A"
                }

                updatedPidValues[pid] = PidDisplayState(
                    pid = pid,
                    isSelected = pid in state.selectedPids,
                    isLoading = false,
                    value = value,
                    formattedValue = formatted,
                    error = if (value == null) "No data" else null
                )
            }

            state.copy(pidValues = updatedPidValues)
        }
    }

    private fun formatReplayValue(value: Double, pid: ObdPid): String {
        val numericPart = when (pid) {
            ObdPid.ENGINE_RPM,
            ObdPid.VEHICLE_SPEED,
            ObdPid.ENGINE_COOLANT_TEMP,
            ObdPid.INTAKE_AIR_TEMP,
            ObdPid.AMBIENT_AIR_TEMP,
            ObdPid.ENGINE_OIL_TEMP -> value.toInt().toString()
            else -> if (value == value.toLong().toDouble()) {
                value.toLong().toString()
            } else {
                String.format("%.1f", value)
            }
        }
        return "$numericPart ${pid.unit}"
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
        stopReplay()
    }
}

/**
 * Preset PID groups for quick selection.
 */
enum class PidPreset(val displayName: String, val pids: List<ObdPid>) {
    ENGINE_BASICS(
        "Engine Basics",
        listOf(
            ObdPid.ENGINE_RPM,
            ObdPid.ENGINE_LOAD,
            ObdPid.ENGINE_COOLANT_TEMP,
            ObdPid.THROTTLE_POSITION
        )
    ),
    DRIVING(
        "Driving",
        listOf(
            ObdPid.ENGINE_RPM,
            ObdPid.VEHICLE_SPEED,
            ObdPid.THROTTLE_POSITION,
            ObdPid.ENGINE_LOAD
        )
    ),
    FUEL_ECONOMY(
        "Fuel Economy",
        listOf(
            ObdPid.MAF_FLOW_RATE,
            ObdPid.VEHICLE_SPEED,
            ObdPid.ENGINE_FUEL_RATE,
            ObdPid.FUEL_TANK_LEVEL
        )
    ),
    TEMPERATURES(
        "Temperatures",
        listOf(
            ObdPid.ENGINE_COOLANT_TEMP,
            ObdPid.INTAKE_AIR_TEMP,
            ObdPid.AMBIENT_AIR_TEMP,
            ObdPid.ENGINE_OIL_TEMP
        )
    ),
    FULL_DASHBOARD(
        "Full Dashboard",
        listOf(
            ObdPid.ENGINE_RPM,
            ObdPid.VEHICLE_SPEED,
            ObdPid.ENGINE_COOLANT_TEMP,
            ObdPid.THROTTLE_POSITION,
            ObdPid.ENGINE_LOAD,
            ObdPid.FUEL_TANK_LEVEL
        )
    )
}
