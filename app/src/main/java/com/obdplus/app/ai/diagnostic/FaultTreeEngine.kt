package com.obdplus.app.ai.diagnostic

/**
 * Pure stateless fault tree reasoner.
 * Maps DTC codes and sensor flags to diagnostic hypothesis tokens.
 */
object FaultTreeEngine {

    fun reason(
        dtcCodes: List<String>,
        plausibilityFlags: List<String>,
        electricalFlags: List<String>
    ): List<String> {
        val hypotheses = mutableListOf<String>()
        val dtcSet = dtcCodes.map { it.uppercase() }.toSet()

        // ── DTC-driven rules ────────────────────────────────────────────────

        if ("P0606" in dtcSet) hypotheses += "pcm_fault"
        if ("P1518" in dtcSet) hypotheses += "tac_comm_fault"
        if ("P1515" in dtcSet || "P1516" in dtcSet) hypotheses += "tac_system_fault"
        if ("P0641" in dtcSet || "P0651" in dtcSet) {
            hypotheses += "vref_fault"
        }
        if ("P0101" in dtcSet || "P0102" in dtcSet || "P0103" in dtcSet) {
            hypotheses += "maf_circuit_fault"
        }
        if ("P0121" in dtcSet || "P0122" in dtcSet || "P0123" in dtcSet) {
            hypotheses += "tps_circuit_fault"
        }
        if ("P0107" in dtcSet || "P0108" in dtcSet) {
            hypotheses += "map_sensor_fault"
        }
        if ("P0171" in dtcSet) hypotheses += "fuel_trim_lean"
        if ("P0172" in dtcSet) hypotheses += "fuel_trim_rich"

        // Misfire codes P0300–P0312
        val misfireCodes = (300..312).map { "P0${it}" }
        if (dtcSet.any { it in misfireCodes }) {
            hypotheses += "misfire_fault"
        }

        if ("P0420" in dtcSet) hypotheses += "catalyst_fault"

        // ── Plausibility flag-driven rules ───────────────────────────────────

        if ("app_mismatch" in plausibilityFlags) hypotheses += "throttle_system_fault"
        if ("fuel_trim_lean" in plausibilityFlags) hypotheses += "fuel_trim_concern"
        if ("fuel_trim_rich" in plausibilityFlags) hypotheses += "fuel_trim_concern"
        if ("no_rpm_signal" in plausibilityFlags) hypotheses += "crank_sensor_fault"
        if ("o2_not_switching" in plausibilityFlags) hypotheses += "o2_sensor_concern"

        // ── Electrical flag-driven rules ────────────────────────────────────

        if ("vref_fault" in electricalFlags) hypotheses += "vref_circuit_fault"

        // ── Compound rules ──────────────────────────────────────────────────

        if ("P0606" in dtcSet && "P1518" in dtcSet) {
            hypotheses += "pcm_tac_network_fault"
        }

        if ("vref_circuit_fault" in hypotheses && "tps_circuit_fault" in hypotheses) {
            hypotheses += "shared_vref_a_fault"
        }

        if ("pcm_fault" in hypotheses && "vref_fault" in electricalFlags) {
            hypotheses += "pcm_power_fault"
        }

        return hypotheses.distinct()
    }
}
