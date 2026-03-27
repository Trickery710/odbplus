package com.obdplus.app.ai.diagnostic

/**
 * Pure stateless scoring engine that produces ranked root-cause probabilities
 * from hypothesis tokens, active codes, sensor flags, and learned weights.
 */
object ProbabilityScoringEngine {

    private val BASE_SCORES: Map<String, Float> = mapOf(
        "pcm_fault"             to 0.70f,
        "tac_comm_fault"        to 0.75f,
        "tac_system_fault"      to 0.65f,
        "vref_fault"            to 0.80f,
        "vref_circuit_fault"    to 0.85f,
        "maf_circuit_fault"     to 0.60f,
        "tps_circuit_fault"     to 0.60f,
        "map_sensor_fault"      to 0.55f,
        "fuel_trim_lean"        to 0.50f,
        "fuel_trim_rich"        to 0.50f,
        "fuel_trim_concern"     to 0.45f,
        "misfire_fault"         to 0.60f,
        "catalyst_fault"        to 0.50f,
        "o2_sensor_concern"     to 0.55f,
        "throttle_system_fault" to 0.65f,
        "crank_sensor_fault"    to 0.70f,
        "pcm_power_fault"       to 0.75f,
        "pcm_tac_network_fault" to 0.80f,
        "shared_vref_a_fault"   to 0.85f
    )

    fun score(
        hypothesisTokens: List<String>,
        dtcCodes: List<String>,
        plausibilityFlags: List<String>,
        electricalFlags: List<String>,
        vinMemory: DiagnosticMemoryRepository.VinMemory?,
        weightAdjustments: Map<String, Float>
    ): List<Pair<String, Float>> {
        if (hypothesisTokens.isEmpty()) return emptyList()

        val scored = hypothesisTokens.map { token ->
            var s = BASE_SCORES[token] ?: 0.40f

            // Recurring fault booster
            if (vinMemory?.recurringFaultTokens?.contains(token) == true) {
                s *= 1.3f
            }

            // Apply adaptive learning weight adjustment
            val adjustment = weightAdjustments[token] ?: 0f
            s *= (1f + adjustment)

            // Reduce confidence when there are no DTC codes
            if (dtcCodes.isEmpty()) {
                s *= 0.8f
            }

            // Cap to [0, 1]
            Pair(token, s.coerceIn(0f, 1f))
        }

        return scored.sortedByDescending { it.second }
    }
}
