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

    private var pollingJob: Job? = null

    /** Callback invoked after each poll cycle with the collected pid→value map. */
    var onPollCycle: ((Map<ObdPid, Double?>) -> Unit)? = null

    /** Pause between individual PID commands within one poll cycle. */
    private val INTER_PID_DELAY_MS = 25L

    /**
     * Maximum number of PIDs queried per poll cycle.
     *
     * Limiting batch size prevents ECU overload from simultaneous burst requests.
     * When more than MAX_BATCH_SIZE PIDs are selected, the scheduler rotates
     * through them across successive cycles so all PIDs are sampled fairly.
     */
    private val MAX_BATCH_SIZE = 15

    /** Rotation offset for fair scheduling when PID count exceeds MAX_BATCH_SIZE. */
    private var batchOffset = 0

    fun setInterval(ms: Long) {
        _pollIntervalMs.value = ms.coerceIn(100L, 5000L)
    }

    /**
     * Start polling the given [pids].
     *
     * The [pids] list must contain only PIDs confirmed supported by the vehicle ECU
     * (i.e. filtered by PID discovery results). Polling unsupported PIDs wastes ECU
     * bus time and can cause timeout cascades on KWP2000 / ISO 9141-2 buses.
     */
    fun start(pids: List<ObdPid>, scope: CoroutineScope) {
        if (pids.isEmpty()) return
        pollingJob?.cancel()
        batchOffset = 0
        _isPolling.value = true

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
        // Determine the batch for this cycle, rotating through all PIDs when the
        // selected count exceeds MAX_BATCH_SIZE. This ensures all PIDs are sampled
        // across cycles rather than always skipping the tail of the list.
        val batch: List<ObdPid> = if (pids.size <= MAX_BATCH_SIZE) {
            pids
        } else {
            val offset = batchOffset % pids.size
            val rotated = pids.drop(offset) + pids.take(offset)
            batchOffset = (batchOffset + MAX_BATCH_SIZE) % pids.size
            rotated.take(MAX_BATCH_SIZE)
        }

        val valuesForCycle = mutableMapOf<ObdPid, Double?>()
        for ((index, pid) in batch.withIndex()) {
            if (!_isPolling.value) break
            updateLoading(pid, true)
            val response = obdService.query(pid)
            applyResponse(pid, pids, response)
            valuesForCycle[pid] = (response as? ObdResponse.Success)?.value
            // Small breathing room between commands so the adapter isn't overwhelmed.
            if (index < batch.lastIndex) delay(INTER_PID_DELAY_MS)
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
        val pidState = when (response) {
            is ObdResponse.Success -> PidDisplayState(
                pid = pid, isSelected = pid in selectedPids,
                isLoading = false, value = response.value,
                formattedValue = response.formattedValue, error = null
            )
            // NoData on a supported PID is a transient ECU condition — show "--" and
            // retry next cycle. Do NOT permanently mark as unsupported; discovery already
            // filtered to confirmed PIDs so this should be rare.
            is ObdResponse.NoData -> PidDisplayState(
                pid = pid, isSelected = pid in selectedPids,
                isLoading = false, value = null,
                formattedValue = "--", error = null
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
