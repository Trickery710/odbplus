package com.obdplus.app.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdplus.app.ai.VehicleContextProvider
import com.obdplus.app.session.SensorLoggingService
import com.obdplus.app.session.VehicleSessionManager
import com.obdplus.app.settings.SettingsRepository
import com.obdplus.core.protocol.ObdPid
import com.obdplus.core.protocol.ObdService
import com.obdplus.core.transport.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val CHART_MAX_POINTS = 120

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

    /**
     * Accumulates all PID values seen across poll cycles on the main thread.
     * Avoids copying the full pidValues map on every cycle just to compute derived metrics.
     */
    private val lastKnownValues = mutableMapOf<ObdPid, Double?>()

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

            // Accumulate values and recalculate derived metrics without copying the full pidValues map.
            lastKnownValues.putAll(pidValues)
            val derived = DerivedMetricCalculator.calculate(lastKnownValues)

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
                // Use a HashSet for O(1) membership checks across 200+ PID iterations.
                val selectedSet = current.selectedPids.toHashSet()
                if (supported == null) {
                    val allPids = withContext(Dispatchers.Default) {
                        ObdPid.entries.map { pid ->
                            PidDisplayState(
                                pid, PidRegistry.get(pid),
                                isSelected = pid in selectedSet
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
                                    isSelected = pid in selectedSet,
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
                if (!connected) {
                    if (polling.isPolling.value) polling.stop()
                    lastKnownValues.clear()
                }
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
            val outcome = try {
                resolveSupportedPids.execute()
            } catch (e: Exception) {
                Timber.w(e, "resolveAndDiscoverPids: execute() failed, falling back to full discovery")
                ResolutionOutcome.NeedsDiscovery(-1L)
            }
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
            try {
                obdService.runPidDiscovery()
                _uiState.update { it.copy(supportSource = "discovery") }
                obdService.supportedPids.value?.let { codes ->
                    if (codes.isNotEmpty() && resolvedProfileId >= 0) {
                        pidCacheRepository.saveDiscovery(resolvedProfileId, codes, "bitmap_discovery")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "rescanSupportedPids failed")
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

    override fun onCleared() {
        super.onCleared()
        polling.stop()
        replay.stop()
    }
}

