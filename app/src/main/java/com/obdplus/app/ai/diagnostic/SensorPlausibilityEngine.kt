package com.obdplus.app.ai.diagnostic

import com.obdplus.app.live.PidDisplayState
import com.obdplus.core.protocol.ObdPid
import kotlin.math.abs

/**
 * Pure stateless engine that inspects a live PID snapshot and returns
 * a list of plausibility fault tokens.
 */
object SensorPlausibilityEngine {

    fun analyze(pidMap: Map<ObdPid, PidDisplayState>): List<String> {
        val flags = mutableListOf<String>()

        fun pidValue(code: String): Double? =
            pidMap.entries.find { it.key.code.equals(code, ignoreCase = true) }?.value?.value

        val rpm  = pidValue("0C")
        val tps  = pidValue("11")
        val maf  = pidValue("10")
        val map  = pidValue("0B")
        val ect  = pidValue("05")
        val o2   = pidValue("14")
        val stft = pidValue("06")
        val ltft = pidValue("07")
        val vref = pidValue("42")

        // APP1/APP2 correlation — APP1 should be ~twice APP2
        val app1 = (pidMap.entries.find { it.key.code == "49" }
            ?: pidMap.entries.find { it.key.code == "13" })?.value?.value
        val app2 = (pidMap.entries.find { it.key.code == "4A" }
            ?: pidMap.entries.find { it.key.code == "14" })?.value?.value
        if (app1 != null && app2 != null && abs(app1 - app2 * 2) > 0.5) {
            flags += "app_mismatch"
        }

        // TPS vs RPM: TPS should not be near zero when engine is running above idle
        if (rpm != null && tps != null && rpm > 1000 && tps < 0.3) {
            flags += "tps_invalid"
        }

        // MAF vs RPM: MAF must not be zero when engine is running
        if (rpm != null && maf != null && rpm > 800 && maf == 0.0) {
            flags += "maf_zero"
        }

        // MAP at idle: MAP should show vacuum (low kPa) when engine is running
        if (rpm != null && map != null && rpm > 800 && map > 95.0) {
            flags += "map_high_at_idle"
        }

        // ECT sanity limits
        if (ect != null && (ect > 250.0 || ect < -20.0)) {
            flags += "ect_invalid"
        }

        // O2 not switching at warm idle
        if (o2 != null && ect != null && ect > 170.0 && o2 > 0.85) {
            flags += "o2_not_switching"
        }

        // Fuel trim combined check
        if (stft != null && ltft != null) {
            val combined = stft + ltft
            if (combined > 25.0)  flags += "fuel_trim_lean"
            if (combined < -25.0) flags += "fuel_trim_rich"
        }

        // vref check
        if (vref != null && vref < 4.5) {
            flags += "vref_low"
        }

        return flags.distinct()
    }
}
