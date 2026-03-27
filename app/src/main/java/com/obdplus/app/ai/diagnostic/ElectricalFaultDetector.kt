package com.obdplus.app.ai.diagnostic

import com.obdplus.app.live.PidDisplayState
import com.obdplus.core.protocol.ObdPid

/**
 * Pure stateless engine that detects electrical fault patterns
 * (shared reference faults, ground faults, wiring shorts) from a PID snapshot.
 */
object ElectricalFaultDetector {

    // PIDs on the 5V_REF_A circuit: TPS=11, APP1=49 (or 13), MAP=0B
    private val VREF_A_PIDS = setOf("11", "49", "13", "0B")

    // PIDs on the 5V_REF_B circuit: APP2=4A (or 14), MAF=10, IAT=0F
    private val VREF_B_PIDS = setOf("4A", "14", "10", "0F")

    fun analyze(pidMap: Map<ObdPid, PidDisplayState>): List<String> {
        val faults = mutableListOf<String>()

        fun pidValue(code: String): Double? =
            pidMap.entries.find { it.key.code.equals(code, ignoreCase = true) }?.value?.value

        val vref = pidValue("42")
        val rpm  = pidValue("0C")
        val maf  = pidValue("10")
        val tps  = pidValue("11")

        // Count how many sensors on each vref circuit are near-zero
        val vrefAValues = VREF_A_PIDS.mapNotNull { code -> pidValue(code) }
        val vrefBValues = VREF_B_PIDS.mapNotNull { code -> pidValue(code) }

        val vrefANearZeroCount = vrefAValues.count { it < 0.3 }
        val vrefBNearZeroCount = vrefBValues.count { it < 0.3 }

        val vrefANearFullCount = vrefAValues.count { it > 4.7 }
        val vrefBNearFullCount = vrefBValues.count { it > 4.7 }

        // 2+ sensors on same circuit near-zero → shared ground fault
        if (vrefANearZeroCount >= 2 || vrefBNearZeroCount >= 2) {
            faults += "sensor_ground_fault"
        }

        // 2+ sensors on same circuit near 5V while vref is LOW → short to vref
        val vrefIsLow = vref != null && vref < 4.5
        if (vrefIsLow && (vrefANearFullCount >= 2 || vrefBNearFullCount >= 2)) {
            faults += "signal_short_to_vref"
        }

        // Direct vref fault
        if (vrefIsLow) {
            faults += "vref_fault"
        }

        // MAF and TPS both at zero while RPM > 0 → shared ground fault
        if (rpm != null && rpm > 0.0 && maf != null && maf == 0.0 && tps != null && tps == 0.0) {
            faults += "shared_ground_fault"
        }

        if (faults.isNotEmpty()) {
            faults += "electrical_fault_detected"
        }

        return faults.distinct()
    }
}
