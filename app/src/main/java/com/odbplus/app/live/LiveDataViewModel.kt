package com.odbplus.app.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.ai.VehicleContextProvider
import com.odbplus.app.ai.data.VehicleInfo
import com.odbplus.app.session.SensorLoggingService
import com.odbplus.app.session.VehicleSessionManager
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdService
import com.odbplus.core.protocol.PidDiscoveryState
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

/** A single timestamped value for chart rendering. */
data class ChartPoint(val timestamp: Long, val value: Double)

private const val CHART_MAX_POINTS = 120

/**
 * UI state for the live data screen.
 */
data class LiveDataUiState(
    val isConnected: Boolean = false,
    val isPolling: Boolean = false,
    val pollIntervalMs: Long = 500L,
    val chartData: Map<ObdPid, List<ChartPoint>> = emptyMap(),
    val showChart: Boolean = false,
    /**
     * PIDs available for selection on the current vehicle.
     *
     * When disconnected: the full master PID list (allows offline browsing).
     * When connected and discovery complete: only PIDs confirmed supported by the
     * vehicle's ECU. Unsupported PIDs are never included — they must never be
     * polled, as doing so wastes ECU bus time and risks timeout cascades.
     */
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
    /** Current state of the PID discovery run. COMPLETE means availablePids is filtered. */
    val pidDiscoveryState: PidDiscoveryState = PidDiscoveryState.IDLE
)

@HiltViewModel
class LiveDataViewModel @Inject constructor(
    private val obdService: ObdService,
    private val repository: LogSessionRepository,
    private val vehicleContextProvider: VehicleContextProvider,
    private val savedStateHandle: SavedStateHandle,
    private val sensorLoggingService: SensorLoggingService,
    private val sessionManager: VehicleSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveDataUiState())
    val uiState: StateFlow<LiveDataUiState> = _uiState.asStateFlow()

    private val polling = PollingManager(obdService).also { mgr ->
        mgr.onPollCycle = { pidValues ->
            logSession.addPoint(pidValues)
            repository.updatePidValues(_uiState.value.pidValues)
            // Record each non-null PID value to the sensor log DB and update chart data
            val now = System.currentTimeMillis()
            val chartUpdates = _uiState.value.chartData.toMutableMap()
            for ((pid, v) in pidValues) {
                val value = v ?: continue
                sensorLoggingService.record(pid.name, value)
                val existing = chartUpdates[pid] ?: emptyList()
                chartUpdates[pid] = (existing + ChartPoint(now, value))
                    .takeLast(CHART_MAX_POINTS)
            }
            _uiState.update { it.copy(chartData = chartUpdates) }
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
        // Load persisted state first, then observe discovery results. Sequenced in
        // a single coroutine so the saved PIDs are available when the first
        // supportedPids emission is processed.
        viewModelScope.launch {
            try {
                repository.initialize()
                val savedPids = repository.selectedPids.value
                _uiState.update { it.copy(selectedPids = savedPids) }
                val savedValues = repository.currentPidValues.value
                if (savedValues.isNotEmpty()) {
                    _uiState.update { it.copy(pidValues = savedValues) }
                }
            } catch (_: Exception) {}

            // Observe PID discovery results and rebuild availablePids reactively.
            //
            // supportedPids == null  → not connected; show full master list so users
            //                          can browse and plan their PID selection offline.
            // supportedPids != null  → discovery complete; show only PIDs confirmed
            //                          supported by this vehicle's ECU.
            //
            // Caching discovery results avoids re-querying the ECU on every poll start.
            // Only supported PIDs are ever added to availablePids — unsupported PIDs
            // must never appear in the UI, and must never be polled.
            obdService.supportedPids.collect { supported ->
                val current = _uiState.value
                if (supported == null) {
                    // Disconnected: show full master list for offline browsing.
                    val allPids = withContext(Dispatchers.Default) {
                        ObdPid.entries.map { pid ->
                            PidDisplayState(pid, isSelected = pid in current.selectedPids)
                        }
                    }
                    _uiState.update { it.copy(availablePids = allPids) }
                } else {
                    // Discovery complete: only include PIDs the ECU confirmed as supported.
                    val availablePids = withContext(Dispatchers.Default) {
                        ObdPid.entries
                            .filter { pid -> supported.contains(pid.code) }
                            .map { pid ->
                                PidDisplayState(pid, isSelected = pid in current.selectedPids)
                            }
                    }
                    // On reconnect: restore only selected PIDs still supported by this ECU.
                    val validSelected = current.selectedPids.filter { supported.contains(it.code) }
                    _uiState.update { it.copy(availablePids = availablePids, selectedPids = validSelected) }
                }
            }
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
        viewModelScope.launch { obdService.discoveryState.collect { v -> _uiState.update { it.copy(pidDiscoveryState = v) } } }
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

    fun selectPreset(preset: PidPreset) {
        val supported = obdService.supportedPids.value
        // When connected, filter the preset to only include PIDs that the vehicle's
        // ECU confirmed as supported during discovery. Presets contain hardcoded PIDs
        // that may not exist on all vehicles.
        val filteredPids = if (supported != null) {
            preset.pids.filter { supported.contains(it.code) }
        } else {
            preset.pids
        }
        selectPids(filteredPids)
    }

    fun clearSelection() = selectPids(emptyList())

    // ==================== Polling ====================

    fun setPollInterval(intervalMs: Long) = polling.setInterval(intervalMs)

    fun startPolling() {
        if (_uiState.value.isReplaying) return
        val state = _uiState.value
        // Only poll PIDs that appear in availablePids (i.e. confirmed supported by discovery).
        // Sending Mode 01 queries for unsupported PIDs wastes ECU bus time and can cause
        // timeout cascades on KWP2000 / ISO 9141-2 buses where each NO DATA response
        // burns a full timeout window (~300 ms).
        val availablePidSet = state.availablePids.map { it.pid }.toHashSet()
        val pollablePids = state.selectedPids.filter { it in availablePidSet }
        if (pollablePids.isNotEmpty()) {
            polling.start(pollablePids, viewModelScope)
            sessionManager.activeSessionId.value?.let { sid ->
                sensorLoggingService.startLogging(sid)
            }
        }
    }

    fun stopPolling() {
        polling.stop()
        sensorLoggingService.stopLogging()
        if (logSession.isLogging.value) logSession.stop(viewModelScope)
    }

    fun togglePolling() { if (polling.isPolling.value) stopPolling() else startPolling() }

    fun toggleChart() { _uiState.update { it.copy(showChart = !it.showChart) } }

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
