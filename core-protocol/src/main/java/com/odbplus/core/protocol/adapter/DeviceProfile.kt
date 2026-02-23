package com.odbplus.core.protocol.adapter

/**
 * Full runtime profile for a connected OBD-II adapter.
 *
 * Created by [AdapterFingerprinter] after connection, persisted for the
 * lifetime of the session, and mutated by [com.odbplus.core.protocol.session.HealthMonitor]
 * as health events occur.
 */
data class DeviceProfile(
    val deviceName: String,
    val chipFamily: AdapterFamily,
    val firmwareVersion: String,
    val transport: String,
    val capabilities: DeviceCapabilities,

    /**
     * Current health score (0–100).
     * Decremented on timeouts, corrupt frames, and unexpected echoes.
     * When it falls below [SAFE_MODE_THRESHOLD] the session enters safe-mode.
     */
    var healthScore: Int = 100
) {
    val isClone: Boolean
        get() = chipFamily == AdapterFamily.ELM_CLONE

    val isHighPerformance: Boolean
        get() = chipFamily == AdapterFamily.STN || chipFamily == AdapterFamily.OBDLINK

    val isInSafeMode: Boolean
        get() = healthScore < SAFE_MODE_THRESHOLD

    /** Serialize to the canonical Device Profile JSON schema from the spec. */
    fun toJsonString(): String = buildString {
        appendLine("{")
        appendLine("  \"device_name\": \"$deviceName\",")
        appendLine("  \"chip_family\": \"${chipFamily.name}\",")
        appendLine("  \"firmware_version\": \"$firmwareVersion\",")
        appendLine("  \"transport\": \"$transport\",")
        appendLine("  \"capabilities\": {")
        appendLine("    \"long_can\": ${capabilities.supportsLongCanFrames},")
        appendLine("    \"sw_can\": ${capabilities.supportsSwCan},")
        appendLine("    \"j1939\": ${capabilities.supportsJ1939},")
        appendLine("    \"binary_mode\": ${capabilities.supportsBinaryMode}")
        appendLine("  },")
        appendLine("  \"buffer_size\": ${capabilities.bufferSize},")
        appendLine("  \"max_poll_rate\": ${capabilities.maxPollRateHz},")
        appendLine("  \"recommended_delay_ms\": ${capabilities.recommendedDelayMs},")
        append("  \"health_score\": $healthScore")
        appendLine()
        append("}")
    }

    companion object {
        const val MAX_HEALTH = 100
        const val SAFE_MODE_THRESHOLD = 40

        /** Penalty values applied by HealthMonitor for specific failure types. */
        const val PENALTY_TIMEOUT = 10
        const val PENALTY_CORRUPT_FRAME = 8
        const val PENALTY_UNEXPECTED_ECHO = 4
        const val PENALTY_PROTOCOL_SWITCH_FAILURE = 15
        const val RECOVERY_PER_SUCCESS = 3

        fun unknown(transport: String = "unknown") = DeviceProfile(
            deviceName = "Unknown OBD Adapter",
            chipFamily = AdapterFamily.UNKNOWN,
            firmwareVersion = "unknown",
            transport = transport,
            capabilities = DeviceCapabilities(
                requiresDelayBetweenCmds = true,
                bufferSize = 128,
                maxPollRateHz = 6,
                recommendedDelayMs = 35L
            )
        )
    }
}
