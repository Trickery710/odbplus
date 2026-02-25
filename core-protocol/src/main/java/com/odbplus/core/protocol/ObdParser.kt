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
     * Handles multi-line responses where the adapter returns multiple PID responses
     * in a single batch. Searches for the response matching the requested PID.
     *
     * @param rawResponse The raw hex response from the adapter (e.g., "41 0C 1A F8")
     * @param requestedPid The PID that was requested (for validation)
     * @return Parsed OBD response
     */
    fun parse(rawResponse: String, requestedPid: ObdPid): ObdResponse {
        // Split into individual response lines first
        val responseLines = rawResponse
            .split("\n", "\r")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // If we have multiple lines, try to find the one matching our requested PID
        if (responseLines.size > 1) {
            val matchingResponse = findMatchingResponse(responseLines, requestedPid)
            if (matchingResponse != null) {
                return parseDataResponse(rawResponse, matchingResponse, requestedPid)
            }
        }

        val cleaned = cleanResponse(rawResponse)

        // If the entire response is a negative response (7F xx yy) with no valid data,
        // treat it as NoData. Common on multi-ECU buses where a secondary module
        // responds with "subFunctionNotSupported" (12) for unsupported PIDs.
        if (isOnlyNegativeResponse(cleaned)) {
            return ObdResponse.NoData(rawResponse, requestedPid)
        }

        // Try to extract matching response from merged multi-response data FIRST
        // This handles cases where "NO DATA" appears alongside valid responses.
        // The extracted response is truncated to exactly the expected byte count,
        // discarding any trailing 7F xx xx bytes from secondary ECUs.
        val extractedResponse = extractMatchingPidResponse(cleaned, requestedPid)
        if (extractedResponse != null) {
            return parseDataResponse(rawResponse, extractedResponse, requestedPid)
        }

        // Check for error responses only if we couldn't find a valid response
        when {
            cleaned.isEmpty() -> return ObdResponse.NoData(rawResponse, requestedPid)
            cleaned == "NO DATA" -> return ObdResponse.NoData(rawResponse, requestedPid)
            // Only treat as NO DATA if there's no valid 41 response mixed in
            cleaned.contains("NO DATA") && !cleaned.contains("41") ->
                return ObdResponse.NoData(rawResponse, requestedPid)
            cleaned == "?" -> return ObdResponse.Error(rawResponse, "Unknown command")
            cleaned.startsWith("ERROR") -> return ObdResponse.Error(rawResponse, cleaned)
            cleaned == "UNABLE TO CONNECT" -> return ObdResponse.Error(rawResponse, "Unable to connect to vehicle")
            cleaned == "BUS INIT" -> return ObdResponse.Error(rawResponse, "Bus initialization failed")
            cleaned.startsWith("STOPPED") -> return ObdResponse.Error(rawResponse, "Command stopped")
        }

        return parseDataResponse(rawResponse, cleaned, requestedPid)
    }

    /**
     * Find a response line that matches the requested PID.
     */
    private fun findMatchingResponse(responseLines: List<String>, requestedPid: ObdPid): String? {
        val expectedPrefix = "41 ${requestedPid.code}".uppercase()
        val expectedPrefixNoSpace = "41${requestedPid.code}".uppercase()

        for (line in responseLines) {
            val cleaned = cleanResponse(line)
            if (cleaned.uppercase().startsWith(expectedPrefix) ||
                cleaned.uppercase().replace(" ", "").startsWith(expectedPrefixNoSpace)) {
                return cleaned
            }
        }
        return null
    }

    /**
     * Extract a specific PID response from a merged multi-response string.
     *
     * Handles cases like "41 0C 1A F8 41 0D 3C" where multiple responses are concatenated,
     * and also strips trailing negative-response bytes ("7F 01 12") that secondary ECUs
     * append after the primary ECU's valid positive response.
     *
     * The returned string contains EXACTLY `2 + expectedBytes` hex tokens so the caller
     * always gets a clean, unambiguous response with no trailing garbage.
     */
    private fun extractMatchingPidResponse(merged: String, requestedPid: ObdPid): String? {
        val pidCode = requestedPid.code.uppercase()
        // Match "41 <PID>" followed by at least one hex byte
        val pattern = "41\\s*$pidCode(?:\\s+[0-9A-F]{2})+".toRegex(RegexOption.IGNORE_CASE)

        val match = pattern.find(merged) ?: return null
        val allBytes = hexStringToBytes(match.value)

        // Need at least: service (1) + pid (1) + data (expectedBytes)
        val needed = 2 + requestedPid.expectedBytes
        if (allBytes.size < needed) return null

        // Truncate to exactly the expected size — drops any trailing 7F xx xx bytes
        val exactBytes = allBytes.take(needed)
        return exactBytes.joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }
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
     * Handles multi-ECU responses where two or more ECUs each emit a complete
     * `43`/`47` frame concatenated into a single string, e.g.:
     *   `43 01 71 00 00 00 00 43 00 00 00 00 00 00`
     * The second `43` is a new response header, not a DTC byte. Each segment is
     * parsed independently and duplicate DTCs are de-duplicated.
     *
     * @param rawResponse The raw hex response
     * @param isPending True for Mode 07 (pending DTCs), false for Mode 03 (stored DTCs)
     * @return De-duplicated list of parsed DTCs
     */
    fun parseDtcs(rawResponse: String, isPending: Boolean = false): List<DiagnosticTroubleCode> {
        val cleaned = cleanResponse(rawResponse)
        if (cleaned.isEmpty() || cleaned == "NO DATA") return emptyList()

        val headerByte = if (isPending) 0x47 else 0x43
        val headerHex  = String.format("%02X", headerByte)

        // Split the cleaned string into segments at each occurrence of the header token.
        // "43 01 71 00 43 00 00" → ["43 01 71 00", "43 00 00"]
        val segments = cleaned.split(Regex("(?<![0-9A-Fa-f])$headerHex(?![0-9A-Fa-f])"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val dtcs = linkedSetOf<DiagnosticTroubleCode>() // use Set to de-duplicate
        for (segment in segments) {
            val bytes = hexStringToBytes(segment)
            if (bytes.isEmpty()) continue

            // DTCs come in pairs of bytes
            var i = 0
            while (i + 1 < bytes.size) {
                val b1 = bytes[i].toInt()     and 0xFF
                val b2 = bytes[i + 1].toInt() and 0xFF
                i += 2
                if (b1 == 0 && b2 == 0) continue  // empty slot
                dtcs.add(DiagnosticTroubleCode.fromBytes(b1, b2))
            }
        }

        return dtcs.toList()
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

    /**
     * Parse a Mode 09 response as ASCII string (e.g., Calibration ID, ECU Name).
     */
    fun parseMode09String(rawResponse: String, expectedPid: Int): String? {
        val cleaned = cleanResponse(rawResponse)
        if (cleaned.isEmpty() || cleaned == "NO DATA") return null

        val bytes = hexStringToBytes(cleaned)

        // Response starts with 49 XX (Mode 09 response)
        if (bytes.size < 3 || bytes[0] != 0x49.toByte()) return null

        // Verify PID
        if ((bytes[1].toInt() and 0xFF) != expectedPid) return null

        // Skip header bytes (49 XX) and message count byte
        val dataBytes = bytes.drop(3)
        if (dataBytes.isEmpty()) return null

        // Convert to ASCII, filtering printable characters
        return dataBytes
            .map { (it.toInt() and 0xFF).toChar() }
            .filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".-_" }
            .joinToString("")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    /**
     * Parse a Mode 09 response as hex string (e.g., CVN).
     */
    fun parseMode09Hex(rawResponse: String, expectedPid: Int): String? {
        val cleaned = cleanResponse(rawResponse)
        if (cleaned.isEmpty() || cleaned == "NO DATA") return null

        val bytes = hexStringToBytes(cleaned)

        // Response starts with 49 XX (Mode 09 response)
        if (bytes.size < 3 || bytes[0] != 0x49.toByte()) return null

        // Verify PID
        if ((bytes[1].toInt() and 0xFF) != expectedPid) return null

        // Skip header bytes (49 XX) and message count byte
        val dataBytes = bytes.drop(3)
        if (dataBytes.isEmpty()) return null

        // Return as hex string
        return dataBytes
            .joinToString("") { String.format("%02X", it.toInt() and 0xFF) }
            .takeIf { it.isNotEmpty() }
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

    /**
     * Returns true when every hex token in [cleaned] is part of a negative-response
     * frame (`7F xx yy`), meaning no positive `41` response is present.
     *
     * A negative response has the form:
     *   7F — negative-response service ID
     *   xx — echoed service byte
     *   yy — NRC (e.g. 12 = subFunctionNotSupported, 22 = conditionsNotCorrect)
     *
     * Secondary ECUs on a multi-ECU CAN bus commonly return `7F 01 12` for PIDs
     * they don't support, appearing after or instead of the primary ECU's response.
     */
    private fun isOnlyNegativeResponse(cleaned: String): Boolean {
        if (cleaned.isEmpty() || cleaned.contains("41")) return false
        val tokens = cleaned.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        // Must be a multiple of 3 (each NRC frame is 3 bytes: 7F xx yy)
        if (tokens.size % 3 != 0) return false
        return tokens.chunked(3).all { (a, _, _) -> a.equals("7F", ignoreCase = true) }
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
