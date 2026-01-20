package com.odbplus.app.ai.data

import com.odbplus.app.live.LogSession
import com.odbplus.app.live.PidDisplayState
import com.odbplus.core.protocol.DiagnosticTroubleCode
import com.odbplus.core.protocol.ObdPid

/**
 * Aggregates vehicle data for AI context injection.
 */
data class VehicleContext(
    val isConnected: Boolean = false,
    val vehicleInfo: VehicleInfo? = null,
    val storedDtcs: List<DiagnosticTroubleCode> = emptyList(),
    val pendingDtcs: List<DiagnosticTroubleCode> = emptyList(),
    val livePidValues: Map<ObdPid, PidDisplayState> = emptyMap(),
    val recentSessions: List<LogSession> = emptyList(),
    val isFetchingVehicleInfo: Boolean = false
) {
    /**
     * Format vehicle context for AI system prompt injection.
     */
    fun formatForAi(): String {
        val sb = StringBuilder()

        // Connection status
        sb.appendLine("## Current Vehicle Status")
        sb.appendLine("Connection: ${if (isConnected) "CONNECTED" else "DISCONNECTED"}")
        sb.appendLine()

        // Vehicle info
        if (vehicleInfo != null && vehicleInfo.vin.isNotEmpty()) {
            sb.appendLine(vehicleInfo.formatForAi())
            sb.appendLine()
        }

        // DTCs section
        if (storedDtcs.isNotEmpty() || pendingDtcs.isNotEmpty()) {
            sb.appendLine("## Diagnostic Trouble Codes")

            if (storedDtcs.isNotEmpty()) {
                sb.appendLine("### Stored Codes (${storedDtcs.size})")
                storedDtcs.forEach { dtc ->
                    sb.appendLine("- ${dtc.code} (${dtc.system.displayName})")
                }
                sb.appendLine()
            }

            if (pendingDtcs.isNotEmpty()) {
                sb.appendLine("### Pending Codes (${pendingDtcs.size})")
                pendingDtcs.forEach { dtc ->
                    sb.appendLine("- ${dtc.code} (${dtc.system.displayName})")
                }
                sb.appendLine()
            }
        } else {
            sb.appendLine("## Diagnostic Trouble Codes")
            sb.appendLine("No codes currently stored or pending.")
            sb.appendLine()
        }

        // Live PID values
        val validPidValues = livePidValues.filter { it.value.value != null }
        if (validPidValues.isNotEmpty()) {
            sb.appendLine("## Live Sensor Data")
            validPidValues.forEach { (pid, state) ->
                sb.appendLine("- ${pid.description}: ${state.formattedValue}")
            }
            sb.appendLine()
        }

        // Recent log sessions summary
        if (recentSessions.isNotEmpty()) {
            sb.appendLine("## Recent Data Logging Sessions")
            recentSessions.take(3).forEach { session ->
                val durationSec = session.duration / 1000
                val pidList = session.selectedPids.joinToString(", ") { it.description }
                sb.appendLine("- Session (${durationSec}s, ${session.dataPointCount} points): $pidList")
            }
            sb.appendLine()
        }

        return sb.toString().trim()
    }

    /**
     * Check if there's any vehicle data available.
     */
    fun hasData(): Boolean {
        return (vehicleInfo != null && vehicleInfo.vin.isNotEmpty()) ||
                storedDtcs.isNotEmpty() ||
                pendingDtcs.isNotEmpty() ||
                livePidValues.any { it.value.value != null } ||
                recentSessions.isNotEmpty()
    }
}
