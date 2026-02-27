package com.odbplus.core.protocol.session

import com.odbplus.core.protocol.adapter.AdapterFingerprinter
import com.odbplus.core.protocol.adapter.DeviceProfile
import com.odbplus.core.protocol.adapter.ProtocolSessionState
import com.odbplus.core.protocol.diagnostic.DiagnosticLogger
import com.odbplus.core.protocol.driver.AdapterDriver
import com.odbplus.core.protocol.driver.DriverFactory
import com.odbplus.core.protocol.isotp.IsoTpAssembler
import com.odbplus.core.transport.ObdTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Main UOAPL session orchestrator.
 *
 * Manages the full adapter lifecycle:
 * 1. Post-transport fingerprinting
 * 2. Driver instantiation
 * 3. Protocol negotiation with fallback
 * 4. SESSION_ACTIVE command routing
 * 5. Health monitoring and self-healing
 * 6. Reconnect / error recovery
 *
 * ## State machine
 * ```
 * DISCONNECTED
 *   → TRANSPORT_CONNECTED  (after transport.connect)
 *   → DEVICE_IDENTIFIED    (after fingerprint)
 *   → PROTOCOL_DETECTED    (after 0100 succeeds)
 *   → SESSION_ACTIVE       (ready for PID queries)
 *   ↔ STREAMING            (high-rate CAN stream)
 *   → ERROR_RECOVERY       (soft-reset + re-init)
 *   → RECONNECTING         (transport dropped)
 * ```
 */
class AdapterSession(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(ProtocolSessionState.DISCONNECTED)
    val state: StateFlow<ProtocolSessionState> = _state.asStateFlow()

    private val _deviceProfile = MutableStateFlow<DeviceProfile?>(null)
    val deviceProfile: StateFlow<DeviceProfile?> = _deviceProfile.asStateFlow()

    private var driver: AdapterDriver? = null
    private var transport: ObdTransport? = null
    private var healthMonitor: HealthMonitor? = null
    private var protocolFallback: ProtocolFallback? = null
    private val isoTpAssembler = IsoTpAssembler()

    private var consecutiveCommandFailures = 0
    private var keepaliveJob: Job? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Called by the transport layer after a raw connection is established.
     *
     * This is where UOAPL takes over: fingerprinting, driver setup, protocol detection.
     */
    suspend fun onTransportConnected(
        transport: ObdTransport,
        transportLabel: String = "unknown"
    ) {
        this.transport = transport
        transition(ProtocolSessionState.TRANSPORT_CONNECTED)

        try {
            // ── Step 1: Fingerprint ──────────────────────────────────────────
            val fingerprinter = AdapterFingerprinter(transport)
            val profile = fingerprinter.fingerprint(transportLabel)
            _deviceProfile.value = profile

            Timber.i("UOAPL: Identified — ${profile.deviceName}  (${profile.chipFamily})")
            DiagnosticLogger.i("Fingerprint", "device=${profile.deviceName}  family=${profile.chipFamily}  fw=${profile.firmwareVersion}  transport=${profile.transport}")
            transition(ProtocolSessionState.DEVICE_IDENTIFIED)

            // ── Step 2: Instantiate driver ────────────────────────────────────
            driver = DriverFactory.create(profile)
            healthMonitor = HealthMonitor(profile)
            protocolFallback = ProtocolFallback(profile)

            driver!!.initialize(transport)

            // ── Step 3: Protocol detection ────────────────────────────────────
            val protocolOk = negotiateProtocol(transport, driver!!, protocolFallback!!)
            if (protocolOk) {
                transition(ProtocolSessionState.PROTOCOL_DETECTED)
                transition(ProtocolSessionState.SESSION_ACTIVE)
                Timber.i("UOAPL: SESSION_ACTIVE")

                // Start keepalive if required
                if (profile.capabilities.requiresKeepalive) {
                    startKeepalive(transport)
                }
            } else {
                Timber.w("UOAPL: Protocol detection failed — entering ERROR_RECOVERY")
                enterErrorRecovery(transport)
            }

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "UOAPL: onTransportConnected failed")
            transition(ProtocolSessionState.ERROR_RECOVERY)
        }
    }

    /**
     * Send a command through the active driver.
     *
     * Handles:
     * - State gate (rejects commands unless SESSION_ACTIVE or STREAMING)
     * - Health-aware timeout scaling
     * - Consecutive failure tracking → ERROR_RECOVERY
     * - ISO-TP assembly if the adapter can't handle it internally
     * - Optional [canHeader]: when non-null, issues `AT SH <header>` before the
     *   command and restores the broadcast header (`AT SH 7DF`) in a finally block.
     */
    suspend fun sendCommand(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        canHeader: String? = null
    ): String {
        val currentState = _state.value
        if (!currentState.canSendCommands) {
            Timber.w("UOAPL: sendCommand rejected — state=$currentState")
            return ""
        }

        val d = driver ?: return ""
        val t = transport ?: return ""
        val hm = healthMonitor ?: return ""

        val effectiveTimeout = if (hm.isInSafeMode) timeoutMs * 2 else timeoutMs

        if (canHeader != null) {
            d.sendCommand(t, "AT SH $canHeader", 1_000L)
        }

        val tSend = System.currentTimeMillis()
        DiagnosticLogger.d("Tx", "t=$tSend  cmd=$command  timeout=${effectiveTimeout}ms  hdr=${canHeader ?: "default"}")

        val rawResponse: String
        try {
            rawResponse = d.sendCommand(t, command, effectiveTimeout)
        } catch (e: Exception) {
            if (canHeader != null) d.sendCommand(t, "AT SH 7DF", 1_000L)
            throw e
        }
        if (canHeader != null) {
            d.sendCommand(t, "AT SH 7DF", 1_000L)
        }

        val tRecv = System.currentTimeMillis()
        DiagnosticLogger.d("Rx", "t=$tRecv  cmd=$command  latency=${tRecv - tSend}ms  len=${rawResponse.length}  preview=${rawResponse.take(64).replace('\n', '|')}")

        // Attempt ISO-TP software reassembly for adapters that emit raw CAN frames
        // (e.g. ESP32 with ATCAF0, dumb clones that don't handle multi-frame internally).
        // If the response doesn't look like ISO-TP frames the assembler returns null
        // and we fall back to the raw string.
        val response = tryIsoTpReassembly(rawResponse) ?: rawResponse

        when {
            response.isEmpty() -> {
                consecutiveCommandFailures++
                hm.onTimeout()
                Timber.w("UOAPL: empty response #$consecutiveCommandFailures for '$command'")
                DiagnosticLogger.w("Session", "empty response #$consecutiveCommandFailures cmd=$command  latency=${tRecv - tSend}ms")
                if (consecutiveCommandFailures >= MAX_CONSECUTIVE_FAILURES) {
                    DiagnosticLogger.e("Session", "MAX_FAILURES reached — entering ERROR_RECOVERY")
                    enterErrorRecovery(t)
                }
            }
            response.isCorrupt() -> {
                consecutiveCommandFailures++
                hm.onCorruptFrame()
                DiagnosticLogger.w("Session", "corrupt frame cmd=$command resp=${response.take(32)}")
            }
            else -> {
                consecutiveCommandFailures = 0
                hm.onCommandSuccess()
            }
        }

        _deviceProfile.value = hm.currentProfile()
        return response
    }

    /**
     * Try to reassemble a raw multi-frame ISO-TP response using [isoTpAssembler].
     *
     * Returns a hex-string representation of the assembled payload when the
     * response contains ISO-TP framing markers (First Frame / Consecutive Frame
     * PCI nibbles).  Returns null when the response looks like a normal
     * single-line ELM-formatted reply so the caller falls back to the raw string.
     *
     * The assembler is NOT used when the ELM/STN adapter handles reassembly
     * internally (the default — `ATCAF1`).  It IS useful for ESP32 adapters
     * in raw CAN mode and for clones that emit bare CAN frames.
     */
    private fun tryIsoTpReassembly(raw: String): String? {
        val lines = raw.lines().filter { it.isNotBlank() }
        // A quick heuristic: if no line starts with a hex byte whose upper nibble
        // is 1 or 2 (First Frame / Consecutive Frame), skip assembly entirely.
        val hasMultiFrameMarkers = lines.any { line ->
            val firstToken = line.trim().uppercase().split("\\s+".toRegex()).firstOrNull() ?: return@any false
            // With headers, first token is the CAN address; skip to the PCI token
            val tokens = line.trim().uppercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            tokens.getOrNull(if (tokens.size >= 4) 3 else 0)
                ?.let { t -> t.length == 2 && (t[0] == '1' || t[0] == '2') }
                ?: false
        }
        if (!hasMultiFrameMarkers) return null

        isoTpAssembler.reset()
        var result: com.odbplus.core.protocol.isotp.IsoTpAssembler.AssemblyResult? = null
        for (line in lines) {
            result = isoTpAssembler.feed(line) ?: continue
            if (result.isComplete) break
        }

        val assembled = result?.takeIf { it.isComplete } ?: return null
        DiagnosticLogger.d("IsoTP", "reassembled ${assembled.received}/${assembled.totalExpected} bytes from ${lines.size} frames")
        return assembled.payload.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    }

    /** Gracefully disconnect and release all resources. */
    suspend fun disconnect() {
        Timber.d("UOAPL: disconnect")

        // cancelAndJoin (not just cancel) ensures the keepalive coroutine has
        // fully stopped before we null out `transport`.  Without join(), a keepalive
        // that is mid-command will continue to execute against a null transport,
        // causing a NullPointerException or writing to an already-closed stream.
        keepaliveJob?.cancelAndJoin()
        keepaliveJob = null

        driver = null
        transport = null
        healthMonitor = null
        protocolFallback = null
        consecutiveCommandFailures = 0
        isoTpAssembler.reset()
        _deviceProfile.value = null
        transition(ProtocolSessionState.DISCONNECTED)
    }

    /** Return the current effective health score. */
    fun healthScore(): Int = healthMonitor?.healthScore ?: 0

    /** True when the session is in health-reduced safe mode. */
    fun isInSafeMode(): Boolean = healthMonitor?.isInSafeMode ?: false

    // ── Protocol negotiation ──────────────────────────────────────────────────

    private suspend fun negotiateProtocol(
        transport: ObdTransport,
        driver: AdapterDriver,
        fallback: ProtocolFallback
    ): Boolean {
        // Wrap the entire negotiation in a hard ceiling.
        //
        // Without this, the fallback loop (9 protocols × 3 retries × up to 3 s each)
        // can block for up to 81 seconds when the adapter can't connect.  The user
        // experiences a permanent freeze with no indication of progress.
        val result = withTimeoutOrNull(NEGOTIATION_TIMEOUT_MS) {
            negotiateProtocolInner(transport, driver, fallback)
        }
        if (result == null) {
            Timber.w("UOAPL: protocol negotiation timed out after ${NEGOTIATION_TIMEOUT_MS}ms")
            DiagnosticLogger.w("Session", "negotiation timed out after ${NEGOTIATION_TIMEOUT_MS}ms")
        }
        return result == true
    }

    private suspend fun negotiateProtocolInner(
        transport: ObdTransport,
        driver: AdapterDriver,
        fallback: ProtocolFallback
    ): Boolean {
        // First try ATSP0 (auto)
        if (driver.profile.capabilities.supportsAutoProtocol) {
            Timber.d("UOAPL: trying ATSP0 (auto)")
            val r = driver.sendCommand(transport, "ATSP0", 2_000L)
            if (r.contains("OK") || r.isEmpty()) {
                // Validate with 0100
                val response = driver.sendCommand(transport, "0100", 2_500L)
                if (response.contains("41") || response.contains("NO DATA")) {
                    Timber.d("UOAPL: ATSP0 + 0100 succeeded")
                    fallback.markCurrentSucceeded()
                    return true
                }
            }
        }

        // Manual fallback loop
        while (!fallback.isExhausted) {
            val proto = fallback.nextProtocol() ?: break
            Timber.d("UOAPL: trying protocol ${proto.name} (${proto.atspCommand})")

            var success = false
            repeat(ProtocolFallback.MAX_RETRIES_PER_PROTOCOL) { attempt ->
                if (success) return@repeat
                val timeout = fallback.retryTimeout(proto.initialTimeoutMs, attempt)

                val atspR = driver.sendCommand(transport, proto.atspCommand, 1_000L)
                if (atspR.contains("OK") || atspR.isEmpty()) {
                    val probeR = driver.sendCommand(transport, "0100", timeout)
                    if (probeR.contains("41") || probeR.contains("NO DATA")) {
                        Timber.i("UOAPL: Protocol '${proto.name}' succeeded")
                        fallback.markCurrentSucceeded()
                        success = true
                    }
                }
            }

            if (success) return true
            healthMonitor?.onProtocolSwitchFailure()
            fallback.markCurrentFailed()
        }

        return false
    }

    // ── Error recovery ────────────────────────────────────────────────────────

    private suspend fun enterErrorRecovery(transport: ObdTransport) {
        transition(ProtocolSessionState.ERROR_RECOVERY)
        consecutiveCommandFailures = 0

        Timber.w("UOAPL: entering ERROR_RECOVERY — attempting soft reset")
        DiagnosticLogger.w("Recovery", "ERROR_RECOVERY started  health=${healthMonitor?.healthScore}")

        try {
            transport.drainChannel()
            val d = driver
            if (d != null) {
                d.onCommandFailure(transport, "ATZ", "error recovery")
                // Re-initialize after reset
                d.initialize(transport)
            }

            // Re-run protocol negotiation
            val pf = protocolFallback?.also { it.reset() } ?: return
            val ok = negotiateProtocol(transport, d ?: return, pf)

            if (ok) {
                transition(ProtocolSessionState.SESSION_ACTIVE)
                Timber.i("UOAPL: ERROR_RECOVERY succeeded — back to SESSION_ACTIVE")
                DiagnosticLogger.i("Recovery", "ERROR_RECOVERY succeeded")
            } else {
                Timber.e("UOAPL: ERROR_RECOVERY failed — session in ERROR")
                DiagnosticLogger.e("Recovery", "ERROR_RECOVERY failed — entering RECONNECTING")
                transition(ProtocolSessionState.RECONNECTING)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "UOAPL: ERROR_RECOVERY exception")
            transition(ProtocolSessionState.RECONNECTING)
        }
    }

    // ── Keepalive ─────────────────────────────────────────────────────────────

    private fun startKeepalive(transport: ObdTransport) {
        val intervalMs = driver?.profile?.capabilities?.keepaliveIntervalMs ?: 15_000L
        Timber.d("UOAPL: starting keepalive every ${intervalMs}ms")

        keepaliveJob = scope.launch {
            while (isActive && _state.value.canSendCommands) {
                delay(intervalMs)
                if (_state.value.canSendCommands) {
                    try {
                        transport.sendCommand("AT", 1_000L)  // Lightweight ping (mutex-safe)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Timber.w("UOAPL: keepalive failed: ${e.message}")
                    }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun transition(newState: ProtocolSessionState) {
        val old = _state.value
        _state.value = newState
        Timber.d("UOAPL: $old → $newState")
        DiagnosticLogger.i("Session", "State: $old → $newState")
    }

    private fun String.isCorrupt(): Boolean {
        val upper = uppercase().trim()
        return upper.contains("BUFFER FULL") ||
            upper.contains("DATA ERROR") ||
            upper.contains("<DATA ERROR")
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 3_000L

        // ElmDriver retries each command up to MAX_RETRIES (3) times with exponential
        // timeout backoff (1.0×, 1.5×, 2.25× of base timeout).  At a 3-second base:
        //   attempt 1 = 3 s, attempt 2 = 4.5 s, attempt 3 = 6.75 s → ~14 s per PID.
        // MAX_CONSECUTIVE_FAILURES = 5 → at most ~70 s of silence before recovery,
        // long enough to survive engine-load hiccups but short enough to avoid a
        // multi-minute freeze visible to the user.
        private const val MAX_CONSECUTIVE_FAILURES = 5

        // Hard ceiling on the entire protocol-negotiation pass.
        //
        // Without this, the ProtocolFallback loop (9 protocols × 3 retries × up to
        // 3 s each) can stall for up to 81 seconds on an adapter that cannot
        // connect to the vehicle.  30 seconds is long enough to try all CAN speeds
        // plus one or two legacy protocols before giving up cleanly.
        private const val NEGOTIATION_TIMEOUT_MS = 30_000L
    }
}
