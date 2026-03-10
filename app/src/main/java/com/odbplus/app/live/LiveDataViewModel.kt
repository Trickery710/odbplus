package com.odbplus.app.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.ai.VehicleContextProvider
import com.odbplus.app.ai.data.VehicleInfo
import com.odbplus.app.session.SensorLoggingService
import com.odbplus.app.session.VehicleSessionManager
import com.odbplus.app.settings.SettingsRepository
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdService
import com.odbplus.core.protocol.PidDiscoveryState
import com.odbplus.core.transport.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// UI data classes
// ─────────────────────────────────────────────────────────────────────────────

data class PidDisplayState(
    val pid: ObdPid,
    val definition: PidDefinition? = null,
    val isSelected: Boolean = false,
    val isLoading: Boolean = false,
    val value: Double? = null,
    val formattedValue: String = "--",
    val error: String? = null,
    val status: SensorStatus = SensorStatus.NORMAL,
    val isFavorite: Boolean = false,
    val changeRate: Double = 0.0     // units/s, for "most active" sorting
) {
    val category: PidCategory get() = definition?.category ?: PidCategory.SENSORS
}

data class LoggedDataPoint(
    val timestamp: Long,
    val pidValues: Map<ObdPid, Double?>
)

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

data class ChartPoint(val timestamp: Long, val value: Double)

private const val CHART_MAX_POINTS = 120

data class LiveDataUiState(
    val isConnected: Boolean = false,
    val isPolling: Boolean = false,
    val pollIntervalMs: Long = 500L,
    val chartData: Map<ObdPid, List<ChartPoint>> = emptyMap(),
    val showChart: Boolean = false,
    /**
     * PIDs available for selection on the current vehicle.
     * When disconnected: full master list for offline browsing.
     * When connected + discovery complete: only ECU-confirmed supported PIDs.
     */
    val availablePids: List<PidDisplayState> = emptyList(),
    val selectedPids: List<ObdPid> = emptyList(),
    val pidValues: Map<ObdPid, PidDisplayState> = emptyMap(),
    // Logging
    val isLogging: Boolean = false,
    val currentLogSession: LogSession? = null,
    val savedSessions: List<LogSession> = emptyList(),
    // Replay
    val isReplaying: Boolean = false,
    val replaySession: LogSession? = null,
    val replayIndex: Int = 0,
    val replaySpeed: Float = 1.0f,
    val pidDiscoveryState: PidDiscoveryState = PidDiscoveryState.IDLE,
    // Smart display
    val displayMode: LiveDisplayMode = LiveDisplayMode.NUMERIC,
    val sortOrder: SortOrder = SortOrder.CATEGORY,
    val activeCategory: PidCategory? = null,
    val activeDtcFilter: List<String> = emptyList(),
    val derivedMetrics: List<DerivedMetric> = emptyList(),
    val favoritePidCodes: Set<String> = emptySet(),
    /** Source of the current supported-PID set: "cache_hit", "validated_cache", "discovery", "disconnected" */
    val supportSource: String = "disconnected"
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class LiveDataViewModel @Inject constructor(
    private val obdService: ObdService,
    private val repository: LogSessionRepository,
    private val vehicleContextProvider: VehicleContextProvider,
    private val savedStateHandle: SavedStateHandle,
    private val sensorLoggingService: SensorLoggingService,
    private val sessionManager: VehicleSessionManager,
    private val settingsRepository: SettingsRepository,
    private val resolveSupportedPids: ResolveSupportedPidsUseCase,
    private val pidCacheRepository: SupportedPidCacheRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveDataUiState())
    val uiState: StateFlow<LiveDataUiState> = _uiState.asStateFlow()

    /** Profile ID resolved during the last connect flow, for saving discovery results. */
    private var resolvedProfileId: Long = -1L

    private val polling = PollingManager(obdService).also { mgr ->
        mgr.onPollCycle = { pidValues ->
            logSession.addPoint(pidValues)
            repository.updatePidValues(_uiState.value.pidValues)

            val now = System.currentTimeMillis()
            val chartUpdates = _uiState.value.chartData.toMutableMap()
            val favorites = _uiState.value.favoritePidCodes

            for ((pid, v) in pidValues) {
                val value = v ?: continue
                sensorLoggingService.record(pid.name, value)
                val existing = chartUpdates[pid] ?: emptyList()
                chartUpdates[pid] = (existing + ChartPoint(now, value)).takeLast(CHART_MAX_POINTS)
            }

            // Recalculate derived metrics from the full current PID value map.
            val allValues = buildCurrentValueMap(pidValues)
            val derived = DerivedMetricCalculator.calculate(allValues)

            _uiState.update { state ->
                val updatedValues = state.pidValues.toMutableMap()
                for ((pid, v) in pidValues) {
                    val current = updatedValues[pid]
                    if (current != null) {
                        val def = PidRegistry.get(pid)
                        val status = if (v != null) def?.statusFor(v) ?: SensorStatus.NORMAL else SensorStatus.NORMAL
                        updatedValues[pid] = current.copy(
                            value = v,
                            formattedValue = if (v != null) "%.1f ${pid.unit}".format(v) else "--",
                            status = status,
                            isFavorite = pid.code in favorites
                        )
                    }
                }
                state.copy(
                    chartData = chartUpdates,
                    pidValues = updatedValues,
                    derivedMetrics = derived
                )
            }
        }
    }
    private val logSession = LogSessionManager(repository)
    private val replay = ReplayManager().also { mgr ->
        mgr.onFrame = { pidValues ->
            val selectedPids = _uiState.value.selectedPids
            val updatedValues = _uiState.value.pidValues.toMutableMap()
            for ((pid, value) in pidValues) {
                updatedValues[pid] = PidDisplayState(
                    pid = pid,
                    definition = PidRegistry.get(pid),
                    isSelected = pid in selectedPids,
                    isLoading = false,
                    value = value,
                    formattedValue = if (value != null) "%.1f ${pid.unit}".format(value) else "N/A",
                    error = if (value == null) "No data" else null
                )
            }
            _uiState.update { it.copy(pidValues = updatedValues) }
        }
    }

    init {
        viewModelScope.launch {
            settingsRepository.defaultPollIntervalMs.collect { ms ->
                polling.setInterval(ms)
            }
        }

        viewModelScope.launch {
            try {
                repository.initialize()
                val savedPids = repository.selectedPids.value
                _uiState.update { it.copy(selectedPids = savedPids) }
                val savedValues = repository.currentPidValues.value
                if (savedValues.isNotEmpty()) _uiState.update { it.copy(pidValues = savedValues) }
            } catch (_: Exception) {}

            obdService.supportedPids.collect { supported ->
                val current = _uiState.value
                if (supported == null) {
                    val allPids = withContext(Dispatchers.Default) {
                        ObdPid.entries.map { pid ->
                            PidDisplayState(
                                pid, PidRegistry.get(pid),
                                isSelected = pid in current.selectedPids
                            )
                        }
                    }
                    _uiState.update { it.copy(availablePids = allPids, supportSource = "disconnected") }
                } else {
                    val availablePids = withContext(Dispatchers.Default) {
                        ObdPid.entries
                            .filter { pid -> supported.contains(pid.code) }
                            .map { pid ->
                                PidDisplayState(
                                    pid, PidRegistry.get(pid),
                                    isSelected = pid in current.selectedPids,
                                    isFavorite = pid.code in current.favoritePidCodes
                                )
                            }
                    }
                    val validSelected = current.selectedPids.filter { supported.contains(it.code) }
                    _uiState.update {
                        it.copy(
                            availablePids = availablePids,
                            selectedPids = validSelected
                        )
                    }
                }
            }
        }

        viewModelScope.launch { repository.sessions.collect { s -> _uiState.update { it.copy(savedSessions = s) } } }
        viewModelScope.launch {
            obdService.connectionState.collect { state ->
                val connected = state == ConnectionState.CONNECTED
                _uiState.update { it.copy(isConnected = connected) }
                if (!connected && polling.isPolling.value) polling.stop()
            }
        }
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

    // ─────────────────────────────────────────────────────────────────────────
    // Connect-time cache resolution
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Run on every successful transport connect, before PID polling begins.
     *
     * Attempts to load the supported-PID set from the Room cache.
     * Falls back to full bitmap discovery if the cache is empty, stale, or fails validation.
     * After discovery, saves the result back to the cache.
     */
    fun resolveAndDiscoverPids() {
        viewModelScope.launch {
            val outcome = resolveSupportedPids.execute()
            when (outcome) {
                is ResolutionOutcome.CacheHit -> {
                    resolvedProfileId = outcome.profileId
                    obdService.preloadSupportedPids(outcome.pidCodes)
                    _uiState.update { it.copy(supportSource = "cache_hit") }
                }
                is ResolutionOutcome.ValidatedCache -> {
                    resolvedProfileId = outcome.profileId
                    obdService.preloadSupportedPids(outcome.pidCodes)
                    _uiState.update { it.copy(supportSource = "validated_cache") }
                }
                is ResolutionOutcome.NeedsDiscovery -> {
                    resolvedProfileId = outcome.profileId
                    obdService.runPidDiscovery()
                    _uiState.update { it.copy(supportSource = "discovery") }
                    // Save the fresh discovery result for next time.
                    obdService.supportedPids.value?.let { codes ->
                        if (codes.isNotEmpty()) {
                            pidCacheRepository.saveDiscovery(resolvedProfileId, codes)
                        }
                    }
                }
            }
        }
    }

    /** Force a full rescan, ignoring any cached data. */
    fun rescanSupportedPids() {
        viewModelScope.launch {
            obdService.runPidDiscovery()
            _uiState.update { it.copy(supportSource = "discovery") }
            obdService.supportedPids.value?.let { codes ->
                if (codes.isNotEmpty() && resolvedProfileId >= 0) {
                    pidCacheRepository.saveDiscovery(resolvedProfileId, codes, "bitmap_discovery")
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PID Selection
    // ─────────────────────────────────────────────────────────────────────────

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
        val filteredPids = if (supported != null) {
            preset.pids.filter { supported.contains(it.code) }
        } else {
            preset.pids
        }
        selectPids(filteredPids)
    }

    fun clearSelection() = selectPids(emptyList())

    fun toggleFavorite(pid: ObdPid) {
        _uiState.update { state ->
            val updated = state.favoritePidCodes.toMutableSet().also {
                if (pid.code in it) it.remove(pid.code) else it.add(pid.code)
            }
            state.copy(
                favoritePidCodes = updated,
                availablePids = state.availablePids.map {
                    it.copy(isFavorite = it.pid.code in updated)
                },
                pidValues = state.pidValues.mapValues { (p, s) ->
                    s.copy(isFavorite = p.code in updated)
                }
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Display / Filtering
    // ─────────────────────────────────────────────────────────────────────────

    fun setDisplayMode(mode: LiveDisplayMode) = _uiState.update { it.copy(displayMode = mode) }

    fun setSortOrder(order: SortOrder) = _uiState.update { it.copy(sortOrder = order) }

    fun setActiveCategory(category: PidCategory?) = _uiState.update { it.copy(activeCategory = category) }

    fun setDtcFilter(dtcCodes: List<String>) {
        _uiState.update { it.copy(activeDtcFilter = dtcCodes) }
    }

    fun clearDtcFilter() = _uiState.update { it.copy(activeDtcFilter = emptyList()) }

    /**
     * Returns the current pid display list filtered + sorted according to active state.
     * Expensive — call from a derived StateFlow or remember in the UI.
     */
    fun sortedFilteredPids(state: LiveDataUiState): List<PidDisplayState> {
        var pids = state.availablePids

        // DTC filter takes priority
        if (state.activeDtcFilter.isNotEmpty()) {
            val dtcPidCodes = DtcSensorMap.getPidsForDtcs(state.activeDtcFilter).map { it.code }.toSet()
            pids = pids.filter { it.pid.code in dtcPidCodes }
        }

        // Category filter
        state.activeCategory?.let { cat ->
            pids = pids.filter { it.category == cat }
        }

        return when (state.sortOrder) {
            SortOrder.ALPHABETICAL -> pids.sortedBy { it.definition?.name ?: it.pid.description }
            SortOrder.CATEGORY -> pids.sortedWith(compareBy({ it.category.ordinal }, { it.definition?.name }))
            SortOrder.VALUE -> pids.sortedByDescending { it.value ?: Double.MIN_VALUE }
            SortOrder.SEVERITY -> pids.sortedByDescending { it.status.ordinal }
            SortOrder.MOST_ACTIVE -> pids.sortedByDescending { it.changeRate }
            SortOrder.FAVORITES -> pids.sortedWith(compareByDescending<PidDisplayState> { it.isFavorite }.thenBy { it.definition?.name })
            SortOrder.SUPPORTED_ONLY -> pids // already filtered to supported only when connected
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Polling
    // ─────────────────────────────────────────────────────────────────────────

    fun setPollInterval(intervalMs: Long) {
        polling.setInterval(intervalMs)
        viewModelScope.launch { settingsRepository.setDefaultPollInterval(intervalMs) }
    }

    fun startPolling() {
        if (_uiState.value.isReplaying) return
        val state = _uiState.value
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

    // ─────────────────────────────────────────────────────────────────────────
    // Logging
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // Replay
    // ─────────────────────────────────────────────────────────────────────────

    fun startReplay(session: LogSession) {
        if (session.dataPoints.isEmpty()) return
        polling.stop()
        selectPids(session.selectedPids)
        replay.start(session, viewModelScope)
    }

    fun stopReplay() = replay.stop()

    fun setReplaySpeed(speed: Float) = replay.setSpeed(speed)

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildCurrentValueMap(freshValues: Map<ObdPid, Double?>): Map<ObdPid, Double?> {
        val result = _uiState.value.pidValues
            .mapValues { (_, state) -> state.value }
            .toMutableMap()
        result.putAll(freshValues)
        return result
    }

    override fun onCleared() {
        super.onCleared()
        polling.stop()
        replay.stop()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preset PID groups
// ─────────────────────────────────────────────────────────────────────────────

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
    ),
    AIR_FUEL(
        "Air / Fuel",
        listOf(
            ObdPid.MAF_FLOW_RATE, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
            ObdPid.LONG_TERM_FUEL_TRIM_BANK1, ObdPid.INTAKE_MANIFOLD_PRESSURE,
            ObdPid.O2_SENSOR_B1S1_VOLTAGE
        )
    ),
    MISFIRE_DIAGNOSIS(
        "Misfire",
        listOf(
            ObdPid.ENGINE_RPM, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
            ObdPid.LONG_TERM_FUEL_TRIM_BANK1, ObdPid.MAF_FLOW_RATE,
            ObdPid.TIMING_ADVANCE, ObdPid.ENGINE_LOAD
        )
    )
}
