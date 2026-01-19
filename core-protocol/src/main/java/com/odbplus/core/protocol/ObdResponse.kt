package com.odbplus.core.protocol

/**
 * Represents a parsed OBD-II response.
 */
sealed class ObdResponse {
    /** The raw hex string received from the adapter. */
    abstract val rawResponse: String

    /**
     * A successfully parsed OBD response with a meaningful value.
     */
    data class Success(
        override val rawResponse: String,
        val pid: ObdPid,
        val value: Double,
        val formattedValue: String
    ) : ObdResponse() {
        override fun toString(): String = "$formattedValue (${pid.description})"
    }

    /**
     * The adapter returned "NO DATA" - PID not supported or no response from ECU.
     */
    data class NoData(
        override val rawResponse: String,
        val requestedPid: ObdPid? = null
    ) : ObdResponse()

    /**
     * The adapter returned an error response.
     */
    data class Error(
        override val rawResponse: String,
        val message: String
    ) : ObdResponse()

    /**
     * Unable to parse the response.
     */
    data class ParseError(
        override val rawResponse: String,
        val reason: String
    ) : ObdResponse()
}

/**
 * Represents a batch of OBD responses from multiple PIDs.
 */
data class ObdSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val responses: Map<ObdPid, ObdResponse>
) {
    /**
     * Get a specific PID value, or null if not available.
     */
    fun getValue(pid: ObdPid): Double? {
        return (responses[pid] as? ObdResponse.Success)?.value
    }

    /**
     * Get all successful responses.
     */
    fun successfulResponses(): List<ObdResponse.Success> {
        return responses.values.filterIsInstance<ObdResponse.Success>()
    }
}

/**
 * Diagnostic Trouble Code (DTC) representation.
 */
data class DiagnosticTroubleCode(
    val code: String,
    val system: DtcSystem,
    val description: String? = null
) {
    enum class DtcSystem(val prefix: Char, val displayName: String) {
        POWERTRAIN('P', "Powertrain"),
        CHASSIS('C', "Chassis"),
        BODY('B', "Body"),
        NETWORK('U', "Network")
    }

    companion object {
        /**
         * Parse a raw DTC byte pair into a formatted code.
         * First nibble determines the system and type.
         */
        fun fromBytes(byte1: Int, byte2: Int): DiagnosticTroubleCode {
            val firstNibble = (byte1 shr 4) and 0x0F
            val system = when (firstNibble shr 2) {
                0 -> DtcSystem.POWERTRAIN
                1 -> DtcSystem.CHASSIS
                2 -> DtcSystem.BODY
                3 -> DtcSystem.NETWORK
                else -> DtcSystem.POWERTRAIN
            }

            val secondChar = (firstNibble and 0x03).toString()
            val thirdChar = (byte1 and 0x0F).toString(16).uppercase()
            val fourthChar = ((byte2 shr 4) and 0x0F).toString(16).uppercase()
            val fifthChar = (byte2 and 0x0F).toString(16).uppercase()

            val code = "${system.prefix}$secondChar$thirdChar$fourthChar$fifthChar"
            return DiagnosticTroubleCode(code, system)
        }
    }
}
