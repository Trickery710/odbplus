package com.odbplus.core.protocol

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for OBD-II responses from an ELM327-compatible adapter.
 *
 * Handles:
 * - Mode 01 (live data) responses
 * - Mode 03 (stored DTCs) responses
 * - Mode 07 (pending DTCs) responses
 * - Error responses (NO DATA, ?, ERROR, etc.)
 */
@Singleton
class ObdParser @Inject constructor() {

    /**
     * Parse a raw response string for a known PID request.
     *
     * @param rawResponse The raw hex response from the adapter (e.g., "41 0C 1A F8")
     * @param requestedPid The PID that was requested (for validation)
     * @return Parsed OBD response
     */
    fun parse(rawResponse: String, requestedPid: ObdPid): ObdResponse {
        val cleaned = cleanResponse(rawResponse)

        // Check for error responses
        when {
            cleaned.isEmpty() -> return ObdResponse.NoData(rawResponse, requestedPid)
            cleaned == "NO DATA" -> return ObdResponse.NoData(rawResponse, requestedPid)
            cleaned == "?" -> return ObdResponse.Error(rawResponse, "Unknown command")
            cleaned.startsWith("ERROR") -> return ObdResponse.Error(rawResponse, cleaned)
            cleaned == "UNABLE TO CONNECT" -> return ObdResponse.Error(rawResponse, "Unable to connect to vehicle")
            cleaned == "BUS INIT" -> return ObdResponse.Error(rawResponse, "Bus initialization failed")
            cleaned.startsWith("STOPPED") -> return ObdResponse.Error(rawResponse, "Command stopped")
        }

        return parseDataResponse(rawResponse, cleaned, requestedPid)
    }

    /**
     * Parse a raw response without knowing the requested PID.
     * Attempts to identify the PID from the response itself.
     */
    fun parseAuto(rawResponse: String): ObdResponse {
        val cleaned = cleanResponse(rawResponse)

        when {
            cleaned.isEmpty() -> return ObdResponse.NoData(rawResponse)
            cleaned == "NO DATA" -> return ObdResponse.NoData(rawResponse)
            cleaned == "?" -> return ObdResponse.Error(rawResponse, "Unknown command")
            cleaned.startsWith("ERROR") -> return ObdResponse.Error(rawResponse, cleaned)
        }

        val bytes = hexStringToBytes(cleaned)
        if (bytes.size < 2) {
            return ObdResponse.ParseError(rawResponse, "Response too short")
        }

        // Mode 01 response starts with 41
        if (bytes[0] != 0x41.toByte()) {
            return ObdResponse.ParseError(rawResponse, "Not a Mode 01 response")
        }

        val pidCode = String.format("%02X", bytes[1])
        val pid = ObdPid.fromCode(pidCode)
            ?: return ObdResponse.ParseError(rawResponse, "Unknown PID: $pidCode")

        val dataBytes = bytes.drop(2).toByteArray()
        return parseValue(rawResponse, pid, dataBytes)
    }

    /**
     * Parse DTCs from Mode 03 (stored) or Mode 07 (pending) response.
     *
     * @param rawResponse The raw hex response
     * @param isPending True for Mode 07 (pending DTCs), false for Mode 03 (stored DTCs)
     * @return List of parsed DTCs
     */
    fun parseDtcs(rawResponse: String, isPending: Boolean = false): List<DiagnosticTroubleCode> {
        val cleaned = cleanResponse(rawResponse)

        if (cleaned.isEmpty() || cleaned == "NO DATA") {
            return emptyList()
        }

        val bytes = hexStringToBytes(cleaned)
        if (bytes.isEmpty()) return emptyList()

        // Response should start with 43 (Mode 03) or 47 (Mode 07)
        val expectedHeader = if (isPending) 0x47.toByte() else 0x43.toByte()
        if (bytes[0] != expectedHeader) {
            return emptyList()
        }

        val dtcBytes = bytes.drop(1)
        val dtcs = mutableListOf<DiagnosticTroubleCode>()

        // DTCs come in pairs of bytes
        for (i in dtcBytes.indices step 2) {
            if (i + 1 >= dtcBytes.size) break

            val b1 = dtcBytes[i].toInt() and 0xFF
            val b2 = dtcBytes[i + 1].toInt() and 0xFF

            // Skip empty DTC slots (00 00)
            if (b1 == 0 && b2 == 0) continue

            dtcs.add(DiagnosticTroubleCode.fromBytes(b1, b2))
        }

        return dtcs
    }

    /**
     * Parse the VIN (Vehicle Identification Number) from Mode 09 PID 02 response.
     */
    fun parseVin(rawResponse: String): String? {
        val cleaned = cleanResponse(rawResponse)
        if (cleaned.isEmpty() || cleaned == "NO DATA") return null

        val bytes = hexStringToBytes(cleaned)

        // Response starts with 49 02 (Mode 09, PID 02)
        if (bytes.size < 3 || bytes[0] != 0x49.toByte() || bytes[1] != 0x02.toByte()) {
            return null
        }

        // Skip header bytes and message count byte
        val vinBytes = bytes.drop(3)
        return vinBytes.map { it.toInt().toChar() }
            .joinToString("")
            .filter { it.isLetterOrDigit() }
            .take(17) // VIN is 17 characters
    }

    private fun parseDataResponse(
        rawResponse: String,
        cleaned: String,
        requestedPid: ObdPid
    ): ObdResponse {
        val bytes = hexStringToBytes(cleaned)

        if (bytes.size < 2) {
            return ObdResponse.ParseError(rawResponse, "Response too short: ${bytes.size} bytes")
        }

        // Validate response header (41 = Mode 01 response)
        if (bytes[0] != 0x41.toByte()) {
            return ObdResponse.ParseError(
                rawResponse,
                "Invalid mode byte: expected 41, got ${String.format("%02X", bytes[0])}"
            )
        }

        // Validate PID matches
        val responsePid = String.format("%02X", bytes[1])
        if (!responsePid.equals(requestedPid.code, ignoreCase = true)) {
            return ObdResponse.ParseError(
                rawResponse,
                "PID mismatch: expected ${requestedPid.code}, got $responsePid"
            )
        }

        // Extract data bytes (skip mode + pid bytes)
        val dataBytes = bytes.drop(2).toByteArray()
        return parseValue(rawResponse, requestedPid, dataBytes)
    }

    private fun parseValue(rawResponse: String, pid: ObdPid, dataBytes: ByteArray): ObdResponse {
        if (dataBytes.size < pid.expectedBytes) {
            return ObdResponse.ParseError(
                rawResponse,
                "Insufficient data: expected ${pid.expectedBytes} bytes, got ${dataBytes.size}"
            )
        }

        return try {
            val value = pid.parse(dataBytes)
            val formatted = formatValue(value, pid)
            ObdResponse.Success(rawResponse, pid, value, formatted)
        } catch (e: Exception) {
            ObdResponse.ParseError(rawResponse, "Parse error: ${e.message}")
        }
    }

    private fun formatValue(value: Double, pid: ObdPid): String {
        val formatted = when {
            value == value.toLong().toDouble() -> value.toLong().toString()
            else -> String.format("%.2f", value)
        }
        return "$formatted ${pid.unit}"
    }

    private fun cleanResponse(response: String): String {
        return response
            .uppercase()
            .replace("\r", "")
            .replace("\n", " ")
            .replace(">", "")
            .replace("SEARCHING...", "")
            .replace("BUS INIT: ...", "")
            .trim()
            .split("\\s+".toRegex())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    private fun hexStringToBytes(hexString: String): ByteArray {
        val parts = hexString.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        return parts.mapNotNull { part ->
            try {
                part.toInt(16).toByte()
            } catch (e: NumberFormatException) {
                null
            }
        }.toByteArray()
    }
}
