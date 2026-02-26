package com.odbplus.app.live

import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdResponse
import com.odbplus.core.protocol.ObdService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PollingManager(private val obdService: ObdService) {

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    private val _pollIntervalMs = MutableStateFlow(500L)
    val pollIntervalMs: StateFlow<Long> = _pollIntervalMs.asStateFlow()

    private val _pidValues = MutableStateFlow<Map<ObdPid, PidDisplayState>>(emptyMap())
    val pidValues: StateFlow<Map<ObdPid, PidDisplayState>> = _pidValues.asStateFlow()

    /** PIDs confirmed unsupported by the connected vehicle (returned NoData). Reset on each start(). */
    private val _unsupportedPids = MutableStateFlow<Set<ObdPid>>(emptySet())
    val unsupportedPids: StateFlow<Set<ObdPid>> = _unsupportedPids.asStateFlow()

    private var pollingJob: Job? = null

    /** Callback invoked after each poll cycle with the collected pid→value map. */
    var onPollCycle: ((Map<ObdPid, Double?>) -> Unit)? = null

    /** Pause between individual PID commands within one poll cycle. */
    private val INTER_PID_DELAY_MS = 25L

    fun setInterval(ms: Long) {
        _pollIntervalMs.value = ms.coerceIn(100L, 5000L)
    }

    fun start(pids: List<ObdPid>, scope: CoroutineScope) {
        if (pids.isEmpty()) return
        pollingJob?.cancel()
        _isPolling.value = true
        _unsupportedPids.value = emptySet()

        pollingJob = scope.launch {
            while (isActive && _isPolling.value) {
                pollOnce(pids)
                delay(_pollIntervalMs.value)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        _isPolling.value = false
    }

    fun querySingle(pid: ObdPid, scope: CoroutineScope) {
        scope.launch {
            updateLoading(pid, true)
            val response = obdService.query(pid)
            applyResponse(pid, emptyList(), response)
        }
    }

    private suspend fun pollOnce(pids: List<ObdPid>) {
        // Skip PIDs confirmed unsupported on this vehicle so we don't waste bus time.
        val activePids = pids.filterNot { it in _unsupportedPids.value }
        val valuesForCycle = mutableMapOf<ObdPid, Double?>()
        for ((index, pid) in activePids.withIndex()) {
            if (!_isPolling.value) break
            updateLoading(pid, true)
            val response = obdService.query(pid)
            applyResponse(pid, pids, response)
            valuesForCycle[pid] = (response as? ObdResponse.Success)?.value
            // Small breathing room between commands so the adapter isn't overwhelmed.
            if (index < activePids.lastIndex) delay(INTER_PID_DELAY_MS)
        }
        if (valuesForCycle.isNotEmpty()) onPollCycle?.invoke(valuesForCycle)
    }

    private fun updateLoading(pid: ObdPid, loading: Boolean) {
        _pidValues.update { current ->
            current.toMutableMap().also {
                it[pid] = (it[pid] ?: PidDisplayState(pid)).copy(isLoading = loading)
            }
        }
    }

    private fun applyResponse(pid: ObdPid, selectedPids: List<ObdPid>, response: ObdResponse) {
        if (response is ObdResponse.NoData) {
            _unsupportedPids.update { it + pid }
        }
        val pidState = when (response) {
            is ObdResponse.Success -> PidDisplayState(
                pid = pid, isSelected = pid in selectedPids,
                isLoading = false, value = response.value,
                formattedValue = response.formattedValue, error = null
            )
            is ObdResponse.NoData -> PidDisplayState(
                pid = pid, isSelected = pid in selectedPids,
                isLoading = false, value = null,
                formattedValue = "N/A", error = "Not supported"
            )
            is ObdResponse.Error -> PidDisplayState(
                pid = pid, isSelected = pid in selectedPids,
                isLoading = false, value = null,
                formattedValue = "--", error = response.message
            )
            is ObdResponse.ParseError -> PidDisplayState(
                pid = pid, isSelected = pid in selectedPids,
                isLoading = false, value = null,
                formattedValue = "--", error = response.reason
            )
        }
        _pidValues.update { it.toMutableMap().also { m -> m[pid] = pidState } }
    }
}
