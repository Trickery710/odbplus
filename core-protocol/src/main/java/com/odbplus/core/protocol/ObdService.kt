package com.odbplus.core.protocol

import com.odbplus.core.protocol.adapter.DeviceProfile
import com.odbplus.core.protocol.adapter.ProtocolSessionState
import com.odbplus.core.protocol.session.AdapterSession
import com.odbplus.core.protocol.signalset.VehicleCommand
import com.odbplus.core.protocol.signalset.VehicleSignal
import com.odbplus.core.protocol.signalset.VehicleSignalResult
import com.odbplus.core.protocol.signalset.VehicleSignalSet
import com.odbplus.core.protocol.signalset.VehicleSignalSetRepository
import com.odbplus.core.protocol.signalset.extractFrom
import com.odbplus.core.transport.ConnectionState
import com.odbplus.core.transport.TransportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifecycle state of the Mode 01 PID support bitmap discovery run.
 *
 * Discovery runs once per connection, after [ProtocolSessionState.SESSION_ACTIVE] is reached.
 * Results are cached so the ECU is never re-queried for support bitmaps during polling.
 */
enum class PidDiscoveryState { IDLE, DISCOVERING, COMPLETE, FAILED }

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
    private val adapterSession: AdapterSession,
    private val signalSetRepository: VehicleSignalSetRepository
) {
    val connectionState: StateFlow<ConnectionState> = transport.connectionState

    /** Live adapter profile (null until fingerprinting completes). */
    val deviceProfile: StateFlow<DeviceProfile?> = adapterSession.deviceProfile

    /** Full UOAPL session state (richer than ConnectionState). */
    val sessionState: StateFlow<ProtocolSessionState> = adapterSession.state

    // ── PID Discovery ─────────────────────────────────────────────────────────

    /**
     * Mode 01 PIDs confirmed supported by the connected ECU, keyed by hex code (e.g. "0C", "0D").
     *
     * Null until [runPidDiscovery] completes for the current connection.
     * Reset to null on [onTransportDisconnected].
     *
     * Caching discovery results is critical — re-querying support bitmaps wastes ECU
     * bus time and can interfere with KWP2000 slow-init recovery windows.
     */
    private val _supportedPids = MutableStateFlow<Set<String>?>(null)
    val supportedPids: StateFlow<Set<String>?> = _supportedPids.asStateFlow()

    private val _discoveryState = MutableStateFlow(PidDiscoveryState.IDLE)
    val discoveryState: StateFlow<PidDiscoveryState> = _discoveryState.asStateFlow()

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

    /**
     * Called before the transport disconnects.
     * Tears down the UOAPL session cleanly — cancels keepalive / reconnect jobs
     * and resets state to DISCONNECTED so the next [onTransportReady] starts fresh.
     * Also clears the discovery cache so the next connection re-runs discovery.
     */
    suspend fun onTransportDisconnected() {
        adapterSession.disconnect()
        _supportedPids.value = null
        _discoveryState.value = PidDiscoveryState.IDLE
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

    // ── PID Discovery ─────────────────────────────────────────────────────────

    /**
     * Run Mode 01 PID support bitmap discovery, querying 0100 through 01A0.
     *
     * Each 32-PID range is probed in order. Discovery stops early when a range's
     * continuation bit (bit 0, i.e. PID xx20) is not set — this matches the
     * ISO 15765-4 / KWP2000 / ISO 9141-2 standard bitmap walk.
     *
     * Results are stored in [supportedPids] and [discoveryState] is updated.
     * Must be called after the session reaches [ProtocolSessionState.SESSION_ACTIVE].
     *
     * Unsupported PIDs must never be polled — each NO DATA response on a KWP2000
     * bus burns a full timeout window (~300 ms) and risks ECU error recovery.
     */
    suspend fun runPidDiscovery(timeoutMs: Long = 3000L) {
        if (!adapterSession.state.value.canSendCommands) return
        _discoveryState.value = PidDiscoveryState.DISCOVERING
        try {
            val discovered = buildSupportedPidSet(timeoutMs)
            _supportedPids.value = discovered
            _discoveryState.value = PidDiscoveryState.COMPLETE
            Timber.i("PID discovery complete: ${discovered.size} supported PIDs")
        } catch (e: Exception) {
            Timber.w(e, "PID discovery failed")
            _discoveryState.value = PidDiscoveryState.FAILED
        }
    }

    /**
     * Walk all six Mode 01 support bitmap ranges and decode the resulting PID set.
     *
     * The "support bitmap" PIDs (00, 20, 40, 60, 80, A0, C0) encode range availability,
     * not sensor values — they are excluded from the returned set so callers never
     * attempt to poll them as live data.
     */
    private suspend fun buildSupportedPidSet(timeoutMs: Long): Set<String> {
        val supported = mutableSetOf<String>()
        // Each pair: (Mode 01 command to send, first real PID encoded in that bitmap).
        val ranges = listOf(
            "0100" to 0x01,
            "0120" to 0x21,
            "0140" to 0x41,
            "0160" to 0x61,
            "0180" to 0x81,
            "01A0" to 0xA1,
        )
        for ((command, startPid) in ranges) {
            // Ranges after the first require the previous range's continuation bit
            // (PID startPid-1, e.g. "20" for range 0120) to be present in supported.
            val continuationPid = String.format("%02X", startPid - 1)
            if (startPid != 0x01 && continuationPid !in supported) break

            val bitmap = queryPidBitmap(command, timeoutMs)
            if (bitmap == 0L) break
            supported.addAll(decodeSupportBitmap(bitmap, startPid))
        }
        // Exclude the range-support PIDs themselves — they are bitmask metadata, not data PIDs.
        return supported - setOf("00", "20", "40", "60", "80", "A0", "C0")
    }

    /**
     * Send a Mode 01 support bitmap command and extract the 4-byte bitmask.
     *
     * Searches for the "41 XX" positive response tag anywhere in the cleaned response
     * string rather than assuming a fixed byte offset. This correctly handles both
     * CAN responses (no ISO headers) and KWP2000/ISO 9141-2 responses (3-byte ISO
     * header prefix, e.g. "84 F1 10 41 00 BE 3F A8 13").
     *
     * Returns 0L on NO DATA, parse failure, or when the session is not active.
     */
    private suspend fun queryPidBitmap(command: String, timeoutMs: Long): Long {
        val pidCode = command.drop(2).uppercase() // "0100" → "00", "0120" → "20"
        val raw = sendCommand(command, timeoutMs)
        val cleaned = raw.replace("\\s+".toRegex(), "").replace(">", "").uppercase()
        val marker = "41$pidCode"
        val idx = cleaned.indexOf(marker)
        if (idx < 0) return 0L
        val bitmapStart = idx + 4 // skip "41" (2 chars) + pidCode (2 chars)
        if (cleaned.length < bitmapStart + 8) return 0L
        return try {
            cleaned.substring(bitmapStart, bitmapStart + 8).toLong(16)
        } catch (_: NumberFormatException) {
            0L
        }
    }

    /**
     * Skip the bitmap discovery phase by directly loading a pre-validated supported-PID set
     * from the Room cache. Sets [supportedPids] and [discoveryState] as if discovery completed.
     *
     * Call this when [ResolveSupportedPidsUseCase] returns a CacheHit or ValidatedCache.
     */
    fun preloadSupportedPids(pids: Set<String>) {
        _supportedPids.value = pids
        _discoveryState.value = PidDiscoveryState.COMPLETE
        Timber.i("ObdService: preloaded ${pids.size} supported PIDs from cache")
    }

    // ── PID support bitmap (legacy) ───────────────────────────────────────────

    /**
     * Check which PIDs are supported by the vehicle.
     * @deprecated Prefer [runPidDiscovery] which caches results and covers all six ranges.
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

    // ── Mode 22 (UDS Read DID) ────────────────────────────────────────────────

    /**
     * Load the [VehicleSignalSet] for [vehicleKey] (e.g. "Toyota-Camry").
     * Uses memory/disk/asset/network fallback chain inside the repository.
     */
    suspend fun loadVehicleSignalSet(vehicleKey: String): VehicleSignalSet? =
        signalSetRepository.load(vehicleKey)

    /**
     * Send [command] to the ECU (targeting [VehicleCommand.hdr]) and decode
     * all of its signals from the response payload.
     *
     * Returns an empty list on NO DATA or a parse failure.
     */
    suspend fun queryCommand(
        command: VehicleCommand,
        timeoutMs: Long = 3_000L
    ): List<VehicleSignalResult> {
        val rawResponse = sendCommand(
            command  = command.commandString,
            timeoutMs = timeoutMs,
            canHeader = command.hdr.ifEmpty { null }
        )
        val payload = extractMode22Payload(rawResponse, command) ?: return emptyList()
        return command.signals.map { it.extractFrom(payload) }
    }

    /**
     * Convenience wrapper: query a single [signal] that belongs to [command].
     * Makes the same round-trip as [queryCommand] — prefer [queryCommand] when
     * you need multiple signals from the same frame.
     */
    suspend fun querySignal(
        signal: VehicleSignal,
        command: VehicleCommand,
        timeoutMs: Long = 3_000L
    ): VehicleSignalResult {
        val rawResponse = sendCommand(
            command   = command.commandString,
            timeoutMs = timeoutMs,
            canHeader = command.hdr.ifEmpty { null }
        )
        val payload = extractMode22Payload(rawResponse, command)
            ?: return VehicleSignalResult(signal = signal, rawValue = null)
        return signal.extractFrom(payload)
    }

    /**
     * Strip the Mode 22 positive response header (`62 DID_HI DID_LO`) from
     * [rawResponse] and return the remaining data bytes.
     *
     * The ELM/adapter may return multiple lines (multi-ECU or multi-frame).
     * We search for the first line whose hex stream contains the expected prefix.
     *
     * Returns null when no matching response line is found.
     */
    private fun extractMode22Payload(rawResponse: String, command: VehicleCommand): ByteArray? {
        // Positive UDS response service byte = request service | 0x40  (0x22 → 0x62)
        val positiveService = (command.service or 0x40).toString(16).padStart(2, '0').uppercase()
        val didParts = command.did.uppercase().chunked(2)
        // We need at least 2 hex chars (1 byte) for the DID; pad if the DID is a single byte
        val didHi = didParts.getOrNull(0) ?: return null
        val didLo = didParts.getOrNull(1) ?: "00"
        val expectedPrefix = "$positiveService$didHi$didLo"

        for (line in rawResponse.lines()) {
            val cleaned = line.uppercase().replace("\\s+".toRegex(), "").replace(">", "")
            if (cleaned.isEmpty() || cleaned == "NODATA" || cleaned == "ERROR") continue

            // Find the header prefix anywhere in the cleaned line
            val idx = cleaned.indexOf(expectedPrefix)
            if (idx < 0) continue

            // Everything after the 3-byte response header is payload
            val payloadHex = cleaned.substring(idx + expectedPrefix.length)
            if (payloadHex.length % 2 != 0) continue
            return payloadHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        return null
    }

    // ── Internal command routing ──────────────────────────────────────────────

    /**
     * Route a raw command through [AdapterSession].
     * Returns an empty string when the session is not yet active (e.g. during
     * UOAPL initialisation) so callers receive a clean NoData / Error rather than
     * racing with the AT-command init sequence via the transport directly.
     */
    private suspend fun sendCommand(
        command: String,
        timeoutMs: Long,
        canHeader: String? = null
    ): String {
        if (!adapterSession.state.value.canSendCommands) return ""
        return adapterSession.sendCommand(command, timeoutMs, canHeader)
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
