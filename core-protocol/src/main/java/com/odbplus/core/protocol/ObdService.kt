package com.odbplus.core.protocol

import com.odbplus.core.transport.ConnectionState
import com.odbplus.core.transport.TransportRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level service for OBD-II communication.
 *
 * Provides a clean API for:
 * - Querying single PIDs
 * - Querying multiple PIDs at once
 * - Reading DTCs
 * - Clearing DTCs
 * - Reading vehicle info (VIN, etc.)
 */
@Singleton
class ObdService @Inject constructor(
    private val transport: TransportRepository,
    private val parser: ObdParser
) {
    val connectionState: StateFlow<ConnectionState> = transport.connectionState

    /**
     * Query a single PID and return the parsed response.
     */
    suspend fun query(pid: ObdPid, timeoutMs: Long = 3000L): ObdResponse {
        val rawResponse = sendCommand(pid.command, timeoutMs)
        return parser.parse(rawResponse, pid)
    }

    /**
     * Query multiple PIDs and return a snapshot of all responses.
     */
    suspend fun queryMultiple(
        pids: List<ObdPid>,
        timeoutMs: Long = 3000L
    ): ObdSnapshot {
        val responses = mutableMapOf<ObdPid, ObdResponse>()

        for (pid in pids) {
            responses[pid] = query(pid, timeoutMs)
        }

        return ObdSnapshot(responses = responses)
    }

    /**
     * Query commonly used live data PIDs.
     */
    suspend fun queryLiveData(timeoutMs: Long = 3000L): ObdSnapshot {
        return queryMultiple(
            listOf(
                ObdPid.ENGINE_RPM,
                ObdPid.VEHICLE_SPEED,
                ObdPid.ENGINE_COOLANT_TEMP,
                ObdPid.THROTTLE_POSITION,
                ObdPid.ENGINE_LOAD
            ),
            timeoutMs
        )
    }

    /**
     * Read stored Diagnostic Trouble Codes (Mode 03).
     */
    suspend fun readStoredDtcs(timeoutMs: Long = 5000L): List<DiagnosticTroubleCode> {
        val rawResponse = sendCommand("03", timeoutMs)
        return parser.parseDtcs(rawResponse, isPending = false)
    }

    /**
     * Read pending Diagnostic Trouble Codes (Mode 07).
     */
    suspend fun readPendingDtcs(timeoutMs: Long = 5000L): List<DiagnosticTroubleCode> {
        val rawResponse = sendCommand("07", timeoutMs)
        return parser.parseDtcs(rawResponse, isPending = true)
    }

    /**
     * Clear all DTCs and reset monitors (Mode 04).
     * Warning: This clears all stored codes and may reset emission monitors.
     */
    suspend fun clearDtcs(timeoutMs: Long = 5000L): Boolean {
        val rawResponse = sendCommand("04", timeoutMs)
        return rawResponse.contains("44") || rawResponse.contains("OK")
    }

    /**
     * Read the Vehicle Identification Number (Mode 09, PID 02).
     */
    suspend fun readVin(timeoutMs: Long = 5000L): String? {
        val rawResponse = sendCommand("0902", timeoutMs)
        return parser.parseVin(rawResponse)
    }

    /**
     * Check which PIDs are supported by the vehicle.
     * Returns a set of supported PID codes.
     */
    suspend fun getSupportedPids(timeoutMs: Long = 3000L): Set<String> {
        val supportedPids = mutableSetOf<String>()

        // Query PID 00 (PIDs 01-20 supported)
        val pids0100 = querySupportBitmap("0100", timeoutMs)
        supportedPids.addAll(decodeSupportBitmap(pids0100, 0x01))

        // If PID 20 is supported, query PID 20 (PIDs 21-40)
        if ("20" in supportedPids) {
            val pids0120 = querySupportBitmap("0120", timeoutMs)
            supportedPids.addAll(decodeSupportBitmap(pids0120, 0x21))
        }

        // If PID 40 is supported, query PID 40 (PIDs 41-60)
        if ("40" in supportedPids) {
            val pids0140 = querySupportBitmap("0140", timeoutMs)
            supportedPids.addAll(decodeSupportBitmap(pids0140, 0x41))
        }

        return supportedPids
    }

    /**
     * Get a value directly, or null if not available.
     * Convenience method for quick queries.
     */
    suspend fun getValue(pid: ObdPid, timeoutMs: Long = 3000L): Double? {
        return (query(pid, timeoutMs) as? ObdResponse.Success)?.value
    }

    /**
     * Get RPM value directly.
     */
    suspend fun getRpm(timeoutMs: Long = 3000L): Double? = getValue(ObdPid.ENGINE_RPM, timeoutMs)

    /**
     * Get vehicle speed directly (km/h).
     */
    suspend fun getSpeed(timeoutMs: Long = 3000L): Double? = getValue(ObdPid.VEHICLE_SPEED, timeoutMs)

    /**
     * Get coolant temperature directly (°C).
     */
    suspend fun getCoolantTemp(timeoutMs: Long = 3000L): Double? =
        getValue(ObdPid.ENGINE_COOLANT_TEMP, timeoutMs)

    private suspend fun sendCommand(command: String, timeoutMs: Long): String {
        // Use a simple approach - capture log output after sending command
        // This works with the existing TransportRepository implementation
        val logLinesBefore = transport.logLines.value.size

        transport.sendAndAwait(command, timeoutMs)

        // Get new log lines that appeared after the command
        val currentLines = transport.logLines.value
        val newLines = currentLines.drop(logLinesBefore)

        // Find response lines (those starting with "<<")
        val responseLines = newLines
            .filter { it.startsWith("<< ") }
            .map { it.removePrefix("<< ").trim() }
            .filter { it.isNotEmpty() }

        // Extract the PID code from the command (e.g., "010C" -> "0C")
        val pidCode = if (command.length >= 4 && command.startsWith("01")) {
            command.substring(2).uppercase()
        } else null

        // If we have a specific PID, try to find matching responses first
        if (pidCode != null) {
            val matchingResponses = responseLines.filter { line ->
                val cleaned = line.uppercase().replace(" ", "")
                cleaned.startsWith("41$pidCode")
            }
            if (matchingResponses.isNotEmpty()) {
                return matchingResponses.joinToString("\n")
            }
        }

        // Fall back to returning all non-empty responses
        return responseLines.joinToString("\n")
    }

    private suspend fun querySupportBitmap(command: String, timeoutMs: Long): Long {
        val response = sendCommand(command, timeoutMs)
        val cleaned = response
            .replace("\\s+".toRegex(), "")
            .replace(">", "")
            .uppercase()

        // Response format: 41 00 XX XX XX XX
        if (cleaned.length < 12) return 0L

        return try {
            // Skip "41XX" prefix (4 chars) and parse remaining 8 hex chars
            cleaned.substring(4, 12).toLong(16)
        } catch (e: NumberFormatException) {
            0L
        }
    }

    private fun decodeSupportBitmap(bitmap: Long, startPid: Int): Set<String> {
        val supported = mutableSetOf<String>()
        for (bit in 0 until 32) {
            if ((bitmap shr (31 - bit)) and 1L == 1L) {
                val pid = startPid + bit
                supported.add(String.format("%02X", pid))
            }
        }
        return supported
    }
}
