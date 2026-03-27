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
import kotlin.math.abs

@Singleton
class TelemetryAnalyzer @Inject constructor(
    private val vehicleContextProvider: VehicleContextProvider
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val PID_SHORT_NAMES = mapOf(
        "0C" to "rpm", "10" to "maf", "11" to "tps", "0B" to "map",
        "05" to "ect", "06" to "stft", "07" to "ltft", "49" to "app1",
        "4A" to "app2", "42" to "vref", "0F" to "iat", "14" to "o2"
    )

    // Circular buffer of the last 30 snapshots
    private val snapshots = ArrayDeque<Map<ObdPid, PidDisplayState>>(30)

    private val _detectedAnomalies = MutableStateFlow<List<String>>(emptyList())
    val detectedAnomalies: StateFlow<List<String>> = _detectedAnomalies.asStateFlow()

    init {
        scope.launch {
            vehicleContextProvider.context.collect { ctx ->
                val pidMap = ctx.livePidValues.filter { it.value.value != null }
                if (pidMap.isNotEmpty()) {
                    if (snapshots.size >= 30) snapshots.removeFirst()
                    snapshots.addLast(pidMap)
                    analyzeSnapshot(pidMap)
                }
            }
        }
    }

    private fun analyzeSnapshot(pidMap: Map<ObdPid, PidDisplayState>) {
        val anomalies = _detectedAnomalies.value.toMutableList()

        fun pidValue(snap: Map<ObdPid, PidDisplayState>, code: String): Double? =
            snap.entries.find { it.key.code.equals(code, ignoreCase = true) }?.value?.value

        // Need at least 3 snapshots for temporal analysis
        if (snapshots.size >= 3) {
            val recent = snapshots.takeLast(3)

            // RPM dropout: > 400, then 0, then > 400
            val rpm0 = pidValue(recent[0], "0C")
            val rpm1 = pidValue(recent[1], "0C")
            val rpm2 = pidValue(recent[2], "0C")
            if (rpm0 != null && rpm1 != null && rpm2 != null) {
                if (rpm0 > 400 && rpm1 == 0.0 && rpm2 > 400) {
                    if ("intermittent_rpm_dropout" !in anomalies) {
                        anomalies += "intermittent_rpm_dropout"
                    }
                }
            }
        }

        // MAF spike: consecutive snapshot delta > 20 g/s
        if (snapshots.size >= 2) {
            val prev = snapshots[snapshots.size - 2]
            val mafPrev = prev.entries.find { it.key.code == "10" }?.value?.value
            val mafCurr = pidMap.entries.find { it.key.code == "10" }?.value?.value
            if (mafPrev != null && mafCurr != null && abs(mafCurr - mafPrev) > 20.0) {
                if ("maf_spike" !in anomalies) anomalies += "maf_spike"
            }

            // PID dropout: any PID that was non-zero is now zero
            pidMap.entries.forEach { (pid, state) ->
                val prevVal = prev.entries.find { it.key.code == pid.code }?.value?.value
                val currVal = state.value
                if (prevVal != null && currVal != null && prevVal > 0.0 && currVal == 0.0) {
                    val shortName = PID_SHORT_NAMES[pid.code] ?: pid.code.lowercase()
                    val tag = "${shortName}_dropout"
                    if (tag !in anomalies) anomalies += tag
                }
            }
        }

        // Keep only 5 most recent unique anomalies
        val trimmed = anomalies.distinct().takeLast(5)
        _detectedAnomalies.value = trimmed
    }
}
