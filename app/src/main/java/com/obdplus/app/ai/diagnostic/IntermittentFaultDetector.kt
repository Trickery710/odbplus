package com.obdplus.app.ai.diagnostic

import com.obdplus.app.ai.VehicleContextProvider
import com.obdplus.app.live.PidDisplayState
import com.obdplus.core.protocol.ObdPid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntermittentFaultDetector @Inject constructor(
    private val vehicleContextProvider: VehicleContextProvider
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Per-PID history: code → list of last 60 values
    private val pidHistory = mutableMapOf<String, ArrayDeque<Double?>>()
    // Per-PID sample counter (samples with no data)
    private val noDataCount = mutableMapOf<String, Int>()
    // Per-PID dropout and spike counters
    private val dropoutCount = mutableMapOf<String, Int>()
    private val spikeCount = mutableMapOf<String, Int>()

    private val _faultPatterns = MutableStateFlow<List<String>>(emptyList())
    val faultPatterns: StateFlow<List<String>> = _faultPatterns.asStateFlow()

    init {
        scope.launch {
            vehicleContextProvider.context.collect { ctx ->
                val pidMap = ctx.livePidValues
                processSnapshot(pidMap)
            }
        }
    }

    private fun processSnapshot(pidMap: Map<ObdPid, PidDisplayState>) {
        val patterns = _faultPatterns.value.toMutableList()
        val rpm = pidMap.entries.find { it.key.code == "0C" }?.value?.value ?: 0.0

        // Update history for each observed PID
        pidMap.keys.forEach { pid ->
            val code = pid.code
            val history = pidHistory.getOrPut(code) { ArrayDeque(60) }
            if (history.size >= 60) history.removeFirst()

            val currentValue = pidMap[pid]?.value
            history.addLast(currentValue)

            // Reset no-data counter if we have data
            if (currentValue != null) {
                noDataCount[code] = 0
            } else {
                noDataCount[code] = (noDataCount[code] ?: 0) + 1
            }

            if (history.size < 6) return@forEach

            val recent5: List<Double> = history.takeLast(6).dropLast(1).mapNotNull { it }
            val current = currentValue ?: return@forEach

            // Dropout detection: current is 0, previous 5 were above threshold
            if (recent5.size >= 5 && current == 0.0) {
                val avgPrev = recent5.average()
                if (avgPrev > 1.0) {
                    dropoutCount[code] = (dropoutCount[code] ?: 0) + 1
                    if ((dropoutCount[code] ?: 0) >= 3) {
                        val tag = "${code.lowercase()}_intermittent_dropout"
                        if (tag !in patterns) patterns += tag
                    }
                }
            } else if (current > 0.0) {
                // Reset dropout counter when value returns
                dropoutCount[code] = 0
            }

            // Voltage spike detection: current > 4.8V, previous 5 < 4.0V
            if (recent5.size >= 5 && current > 4.8) {
                val prevMax = recent5.max()
                if (prevMax < 4.0) {
                    spikeCount[code] = (spikeCount[code] ?: 0) + 1
                    val tag = "${code.lowercase()}_voltage_spike"
                    if (tag !in patterns) patterns += tag
                }
            }

            // Communication loss: no new data for > 10 consecutive samples while RPM > 0
            val noData = noDataCount[code] ?: 0
            if (noData > 10 && rpm > 0) {
                val tag = "${code.lowercase()}_comm_loss"
                if (tag !in patterns) patterns += tag
            }
        }

        _faultPatterns.value = patterns.distinct()
    }
}
