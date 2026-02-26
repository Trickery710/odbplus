package com.odbplus.app.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.ai.VehicleContextProvider
import com.odbplus.app.ai.data.VehicleInfo
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdService
import com.odbplus.core.transport.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val dataPoints: List<LoggedDataPoint> = emptyList(),
    val vehicleInfo: VehicleInfo? = null
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
    val availablePids: List<PidDisplayState> = emptyList(),
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
    val replaySpeed: Float = 1.0f,
    // PIDs confirmed unsupported by the connected vehicle (NoData response).
    // Hidden from the live grid and greyed out in the selector.
    val unsupportedPids: Set<ObdPid> = emptySet()
)

@HiltViewModel
class LiveDataViewModel @Inject constructor(
    private val obdService: ObdService,
    private val repository: LogSessionRepository,
    private val vehicleContextProvider: VehicleContextProvider,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveDataUiState())
    val uiState: StateFlow<LiveDataUiState> = _uiState.asStateFlow()

    private val polling = PollingManager(obdService).also { mgr ->
        mgr.onPollCycle = { pidValues ->
            logSession.addPoint(pidValues)
            repository.updatePidValues(_uiState.value.pidValues)
        }
    }
    private val logSession = LogSessionManager(repository)
    private val replay = ReplayManager().also { mgr ->
        mgr.onFrame = { pidValues ->
            val selectedPids = _uiState.value.selectedPids
            val updatedValues = _uiState.value.pidValues.toMutableMap()
            for ((pid, value) in pidValues) {
                updatedValues[pid] = PidDisplayState(
                    pid = pid, isSelected = pid in selectedPids,
                    isLoading = false, value = value,
                    formattedValue = if (value != null) formatReplayValue(value, pid) else "N/A",
                    error = if (value == null) "No data" else null
                )
            }
            _uiState.update { it.copy(pidValues = updatedValues) }
        }
    }

    init {
        viewModelScope.launch {
            try {
                repository.initialize()
                val savedPids = repository.selectedPids.value
                // Build PID list off the main thread — enum class-loading + 100 allocations
                // are CPU work that must not run on Main.
                val allPids = withContext(Dispatchers.Default) {
                    ObdPid.entries.map { PidDisplayState(it, isSelected = it in savedPids) }
                }
                _uiState.update { it.copy(selectedPids = savedPids, availablePids = allPids) }
                val savedValues = repository.currentPidValues.value
                if (savedValues.isNotEmpty()) {
                    _uiState.update { it.copy(pidValues = savedValues) }
                }
            } catch (_: Exception) {}
        }

        viewModelScope.launch {
            repository.sessions.collect { sessions ->
                _uiState.update { it.copy(savedSessions = sessions) }
            }
        }

        viewModelScope.launch {
            obdService.connectionState.collect { state ->
                val connected = state == ConnectionState.CONNECTED
                _uiState.update { it.copy(isConnected = connected) }
                if (!connected && polling.isPolling.value) polling.stop()
            }
        }

        // Mirror sub-manager state into the unified UiState
        viewModelScope.launch { polling.isPolling.collect { v -> _uiState.update { it.copy(isPolling = v) } } }
        viewModelScope.launch { polling.pollIntervalMs.collect { v -> _uiState.update { it.copy(pollIntervalMs = v) } } }
        viewModelScope.launch { polling.pidValues.collect { v -> _uiState.update { it.copy(pidValues = v) } } }
        viewModelScope.launch { polling.unsupportedPids.collect { v -> _uiState.update { it.copy(unsupportedPids = v) } } }
        viewModelScope.launch { logSession.isLogging.collect { v -> _uiState.update { it.copy(isLogging = v) } } }
        viewModelScope.launch { logSession.currentSession.collect { v -> _uiState.update { it.copy(currentLogSession = v) } } }
        viewModelScope.launch { replay.isReplaying.collect { v -> _uiState.update { it.copy(isReplaying = v) } } }
        viewModelScope.launch { replay.replaySession.collect { v -> _uiState.update { it.copy(replaySession = v) } } }
        viewModelScope.launch { replay.replayIndex.collect { v -> _uiState.update { it.copy(replayIndex = v) } } }
        viewModelScope.launch { replay.replaySpeed.collect { v -> _uiState.update { it.copy(replaySpeed = v) } } }
    }

    // ==================== PID Selection ====================

    fun togglePidSelection(pid: ObdPid) {
        _uiState.update { state ->
            val updated = state.selectedPids.toMutableList().also {
                if (pid in it) it.remove(pid) else it.add(pid)
            }
            state.copy(
                selectedPids = updated,
                availablePids = state.availablePids.map { it.copy(isSelected = it.pid in updated) }
            )
        }
    }

    fun selectPids(pids: List<ObdPid>) {
        _uiState.update { state ->
            state.copy(
                selectedPids = pids,
                availablePids = state.availablePids.map { it.copy(isSelected = it.pid in pids) }
            )
        }
        viewModelScope.launch { repository.saveSelectedPids(pids) }
    }

    fun selectPreset(preset: PidPreset) = selectPids(preset.pids)

    fun clearSelection() = selectPids(emptyList())

    // ==================== Polling ====================

    fun setPollInterval(intervalMs: Long) = polling.setInterval(intervalMs)

    fun startPolling() {
        if (_uiState.value.isReplaying) return
        polling.start(_uiState.value.selectedPids, viewModelScope)
    }

    fun stopPolling() {
        polling.stop()
        if (logSession.isLogging.value) logSession.stop(viewModelScope)
    }

    fun togglePolling() { if (polling.isPolling.value) stopPolling() else startPolling() }

    fun querySinglePid(pid: ObdPid) {
        if (!_uiState.value.isConnected || _uiState.value.isReplaying) return
        polling.querySingle(pid, viewModelScope)
    }

    // ==================== Logging ====================

    fun startLogging() {
        if (_uiState.value.isReplaying) return
        val vehicleInfo = vehicleContextProvider.current().vehicleInfo
        logSession.start(_uiState.value.selectedPids, vehicleInfo)
        if (!polling.isPolling.value) startPolling()
    }

    fun stopLogging() = logSession.stop(viewModelScope)

    fun toggleLogging() { if (logSession.isLogging.value) stopLogging() else startLogging() }

    fun deleteSession(session: LogSession) = logSession.delete(session, viewModelScope)

    fun clearAllSessions() = logSession.clearAll(viewModelScope)

    // ==================== Replay ====================

    fun startReplay(session: LogSession) {
        if (session.dataPoints.isEmpty()) return
        polling.stop()
        selectPids(session.selectedPids)
        replay.start(session, viewModelScope)
    }

    fun stopReplay() = replay.stop()

    fun setReplaySpeed(speed: Float) = replay.setSpeed(speed)

    // ==================== Helpers ====================

    private fun formatReplayValue(value: Double, pid: ObdPid): String {
        val numericPart = when (pid) {
            ObdPid.ENGINE_RPM, ObdPid.VEHICLE_SPEED,
            ObdPid.ENGINE_COOLANT_TEMP, ObdPid.INTAKE_AIR_TEMP,
            ObdPid.AMBIENT_AIR_TEMP, ObdPid.ENGINE_OIL_TEMP -> value.toInt().toString()
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
        polling.stop()
        replay.stop()
    }
}

/**
 * Preset PID groups for quick selection.
 */
enum class PidPreset(val displayName: String, val pids: List<ObdPid>) {
    ENGINE_BASICS(
        "Engine Basics",
        listOf(ObdPid.ENGINE_RPM, ObdPid.ENGINE_LOAD, ObdPid.ENGINE_COOLANT_TEMP, ObdPid.THROTTLE_POSITION)
    ),
    DRIVING(
        "Driving",
        listOf(ObdPid.ENGINE_RPM, ObdPid.VEHICLE_SPEED, ObdPid.THROTTLE_POSITION, ObdPid.ENGINE_LOAD)
    ),
    FUEL_ECONOMY(
        "Fuel Economy",
        listOf(ObdPid.MAF_FLOW_RATE, ObdPid.VEHICLE_SPEED, ObdPid.ENGINE_FUEL_RATE, ObdPid.FUEL_TANK_LEVEL)
    ),
    TEMPERATURES(
        "Temperatures",
        listOf(ObdPid.ENGINE_COOLANT_TEMP, ObdPid.INTAKE_AIR_TEMP, ObdPid.AMBIENT_AIR_TEMP, ObdPid.ENGINE_OIL_TEMP)
    ),
    FULL_DASHBOARD(
        "Full Dashboard",
        listOf(
            ObdPid.ENGINE_RPM, ObdPid.VEHICLE_SPEED, ObdPid.ENGINE_COOLANT_TEMP,
            ObdPid.THROTTLE_POSITION, ObdPid.ENGINE_LOAD, ObdPid.FUEL_TANK_LEVEL
        )
    )
}
