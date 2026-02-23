package com.odbplus.core.protocol

import com.odbplus.core.protocol.adapter.DeviceProfile
import com.odbplus.core.protocol.adapter.ProtocolSessionState
import com.odbplus.core.protocol.session.AdapterSession
import com.odbplus.core.transport.ConnectionState
import com.odbplus.core.transport.TransportRepository
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
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
 *
 * Internally routes all commands through [AdapterSession] which handles
 * device fingerprinting, driver selection, health monitoring, and
 * protocol fallback automatically.
 */
@Singleton
class ObdService @Inject constructor(
    private val transport: TransportRepository,
    private val parser: ObdParser,
    private val adapterSession: AdapterSession
) {
    val connectionState: StateFlow<ConnectionState> = transport.connectionState

    /** Live adapter profile (null until fingerprinting completes). */
    val deviceProfile: StateFlow<DeviceProfile?> = adapterSession.deviceProfile

    /** Full UOAPL session state (richer than ConnectionState). */
    val sessionState: StateFlow<ProtocolSessionState> = adapterSession.state

    /**
     * Called after the transport layer has established a raw connection.
     * Hands off to [AdapterSession] for fingerprinting and full initialization.
     *
     * Should be called from the connect flow (e.g. in a ViewModel after
     * [TransportRepository.connect] succeeds).
     */
    suspend fun onTransportReady(transportLabel: String = "unknown") {
        val rawTransport = transport.getActiveTransport() ?: run {
            Timber.w("ObdService.onTransportReady: no active transport")
            return
        }
        adapterSession.onTransportConnected(rawTransport, transportLabel)
    }

    // ── Query API ─────────────────────────────────────────────────────────────

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

    // ── DTCs ──────────────────────────────────────────────────────────────────

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
     */
    suspend fun clearDtcs(timeoutMs: Long = 5000L): Boolean {
        val rawResponse = sendCommand("04", timeoutMs)
        return rawResponse.contains("44") || rawResponse.contains("OK")
    }

    // ── Vehicle Info ──────────────────────────────────────────────────────────

    /**
     * Read the Vehicle Identification Number (Mode 09, PID 02).
     */
    suspend fun readVin(timeoutMs: Long = 5000L): String? {
        val rawResponse = sendCommand("0902", timeoutMs)
        return parser.parseVin(rawResponse)
    }

    /**
     * Read Calibration ID (Mode 09, PID 04).
     */
    suspend fun readCalibrationId(timeoutMs: Long = 5000L): String? {
        val rawResponse = sendCommand("0904", timeoutMs)
        return parser.parseMode09String(rawResponse, 0x04)
    }

    /**
     * Read Calibration Verification Number (Mode 09, PID 06).
     */
    suspend fun readCalibrationVerificationNumber(timeoutMs: Long = 5000L): String? {
        val rawResponse = sendCommand("0906", timeoutMs)
        return parser.parseMode09Hex(rawResponse, 0x06)
    }

    /**
     * Read ECU Name (Mode 09, PID 0A).
     */
    suspend fun readEcuName(timeoutMs: Long = 5000L): String? {
        val rawResponse = sendCommand("090A", timeoutMs)
        return parser.parseMode09String(rawResponse, 0x0A)
    }

    /**
     * Read all available vehicle information.
     */
    suspend fun readAllVehicleInfo(timeoutMs: Long = 5000L): Map<String, String> {
        val info = mutableMapOf<String, String>()
        readVin(timeoutMs)?.let { info["VIN"] = it }
        readCalibrationId(timeoutMs)?.let { info["Calibration ID"] = it }
        readCalibrationVerificationNumber(timeoutMs)?.let { info["CVN"] = it }
        readEcuName(timeoutMs)?.let { info["ECU Name"] = it }
        return info
    }

    // ── PID support bitmap ────────────────────────────────────────────────────

    /**
     * Check which PIDs are supported by the vehicle.
     */
    suspend fun getSupportedPids(timeoutMs: Long = 3000L): Set<String> {
        val supportedPids = mutableSetOf<String>()
        val pids0100 = querySupportBitmap("0100", timeoutMs)
        supportedPids.addAll(decodeSupportBitmap(pids0100, 0x01))
        if ("20" in supportedPids) {
            val pids0120 = querySupportBitmap("0120", timeoutMs)
            supportedPids.addAll(decodeSupportBitmap(pids0120, 0x21))
        }
        if ("40" in supportedPids) {
            val pids0140 = querySupportBitmap("0140", timeoutMs)
            supportedPids.addAll(decodeSupportBitmap(pids0140, 0x41))
        }
        return supportedPids
    }

    // ── Convenience getters ───────────────────────────────────────────────────

    suspend fun getValue(pid: ObdPid, timeoutMs: Long = 3000L): Double? =
        (query(pid, timeoutMs) as? ObdResponse.Success)?.value

    suspend fun getRpm(timeoutMs: Long = 3000L): Double? = getValue(ObdPid.ENGINE_RPM, timeoutMs)
    suspend fun getSpeed(timeoutMs: Long = 3000L): Double? = getValue(ObdPid.VEHICLE_SPEED, timeoutMs)
    suspend fun getCoolantTemp(timeoutMs: Long = 3000L): Double? =
        getValue(ObdPid.ENGINE_COOLANT_TEMP, timeoutMs)

    // ── Internal command routing ──────────────────────────────────────────────

    /**
     * Route a raw command through [AdapterSession] when active, otherwise
     * fall back to [TransportRepository] for compatibility.
     */
    private suspend fun sendCommand(command: String, timeoutMs: Long): String {
        // Primary path: UOAPL AdapterSession
        if (adapterSession.state.value.canSendCommands) {
            return adapterSession.sendCommand(command, timeoutMs)
        }

        // Fallback path: legacy log-based extraction (pre-UOAPL)
        val logLinesBefore = transport.logLines.value.size
        transport.sendAndAwait(command, timeoutMs)
        val currentLines = transport.logLines.value
        val newLines = currentLines.drop(logLinesBefore)
        val responseLines = newLines
            .filter { it.startsWith("<< ") }
            .map { it.removePrefix("<< ").trim() }
            .filter { it.isNotEmpty() }

        val pidCode = if (command.length >= 4 && command.startsWith("01")) {
            command.substring(2).uppercase()
        } else null

        if (pidCode != null) {
            val matchingResponses = responseLines.filter { line ->
                val cleaned = line.uppercase().replace(" ", "")
                cleaned.startsWith("41$pidCode")
            }
            if (matchingResponses.isNotEmpty()) {
                return matchingResponses.joinToString("\n")
            }
        }
        return responseLines.joinToString("\n")
    }

    private suspend fun querySupportBitmap(command: String, timeoutMs: Long): Long {
        val response = sendCommand(command, timeoutMs)
        val cleaned = response
            .replace("\\s+".toRegex(), "")
            .replace(">", "")
            .uppercase()
        if (cleaned.length < 12) return 0L
        return try {
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
