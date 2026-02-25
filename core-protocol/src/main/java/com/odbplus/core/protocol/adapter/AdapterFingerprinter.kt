package com.odbplus.core.protocol.adapter

import com.odbplus.core.protocol.diagnostic.DiagnosticLogger
import com.odbplus.core.transport.ObdTransport
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Identifies, classifies, and probes an OBD-II adapter after transport connection.
 *
 * Detection algorithm:
 * 1. Reset (ATZ) and suppress echo/linefeeds
 * 2. STN/OBDLink fast-path: probe STI — if response starts with "STN", skip to [buildStnProfile]
 * 3. Read ELM identity strings (ATI, AT@1, ATDP)
 * 4. Classify chip family from response keywords
 * 5. Apply clone-detection heuristics
 * 6. Probe optional AT commands (ATAL, ATCAF1, ATSH)
 * 7. Test live protocol stability with 0100
 * 8. Cross-reference against [KnownDeviceRegistry]
 * 9. Build and return a [DeviceProfile]
 */
class AdapterFingerprinter(private val transport: ObdTransport) {

    /** Run the full fingerprinting sequence and return a resolved [DeviceProfile]. */
    suspend fun fingerprint(transportLabel: String = "unknown"): DeviceProfile {
        Timber.d("UOAPL fingerprint starting")

        // ── Step 1: Reset + suppress noise ───────────────────────────────────
        val atzResponse = sendRaw("ATZ", RESET_TIMEOUT_MS)
        delay(RESET_SETTLE_MS)

        sendRaw("ATE0", CMD_TIMEOUT_MS)  // Echo off
        sendRaw("ATL0", CMD_TIMEOUT_MS)  // Linefeeds off
        sendRaw("ATS0", CMD_TIMEOUT_MS)  // Spaces off (optional, some clones ignore)

        // ── Step 2: STN / OBDLink fast-path probe ────────────────────────────
        // STN chips respond to STI with "STN<device_id> vX.Y.Z" (e.g. "STN2232 v5.10.3").
        // Non-STN devices return "?". This probe MUST come before ELM identification
        // because STN/OBDLink chips advertise "ELM327 v1.4b" in ATZ/ATI, making them
        // indistinguishable from genuine ELM327s without this ST-specific command.
        val stiResponse = sendRaw("STI", CMD_TIMEOUT_MS)
        Timber.d("STI → $stiResponse")

        if (stiResponse.uppercase().startsWith("STN")) {
            DiagnosticLogger.i("Fingerprint", "STI fast-path → STN/OBDLink detected: $stiResponse")
            return buildStnProfile(stiResponse, transportLabel)
        }

        // ── Step 3: Read ELM identity ─────────────────────────────────────────
        val atiResponse  = sendRaw("ATI",  CMD_TIMEOUT_MS)   // e.g. "ELM327 v2.1"
        val at1Response  = sendRaw("AT@1", CMD_TIMEOUT_MS)   // e.g. "OBDLink MX+"
        val atdpResponse = sendRaw("ATDP", CMD_TIMEOUT_MS)   // e.g. "AUTO"

        Timber.d("ATZ  → $atzResponse")
        Timber.d("ATI  → $atiResponse")
        Timber.d("AT@1 → $at1Response")
        Timber.d("ATDP → $atdpResponse")

        // ── Step 4: Classify family ───────────────────────────────────────────
        val combinedId = "$atzResponse $atiResponse $at1Response".uppercase()
        val detectedFamily = classifyFamily(combinedId)
        val firmwareVersion = extractFirmwareVersion(atiResponse)

        // ── Step 5: Clone detection ───────────────────────────────────────────
        val isClone = detectedFamily == AdapterFamily.ELM327 && detectClone(firmwareVersion, at1Response)
        val resolvedFamily = if (isClone) AdapterFamily.ELM_CLONE else detectedFamily

        Timber.d("Family: $resolvedFamily  FW: $firmwareVersion  Clone: $isClone")

        // ── Step 6: Probe AT capabilities ─────────────────────────────────────
        val atAlOk    = probeAtAl()
        val atCafOk   = probeAtCaf()
        val headersOk = probeCustomHeaders()

        // ── Step 7: Protocol stability test ───────────────────────────────────
        val protocolLive = probeProtocolStability()

        Timber.d("ATAL=$atAlOk  ATCAF=$atCafOk  Headers=$headersOk  Proto=$protocolLive")

        // ── Step 8: Known-device registry lookup ──────────────────────────────
        val registryHit = KnownDeviceRegistry.lookup(at1Response)
            ?: KnownDeviceRegistry.lookup(atiResponse)

        if (registryHit != null) {
            Timber.d("Registry hit: ${registryHit.deviceName}")
            return registryHit.copy(
                firmwareVersion = firmwareVersion.ifEmpty { registryHit.firmwareVersion },
                transport = transportLabel
            )
        }

        // ── Step 9: Build capability profile from probed results ──────────────
        val capabilities = buildCapabilities(
            family = resolvedFamily,
            supportsAtAl = atAlOk,
            supportsAtCaf = atCafOk,
            supportsHeaders = headersOk
        )

        val profile = DeviceProfile(
            deviceName = resolveDeviceName(atiResponse, at1Response),
            chipFamily = resolvedFamily,
            firmwareVersion = firmwareVersion,
            transport = transportLabel,
            capabilities = capabilities
        )
        DiagnosticLogger.i("Fingerprint", "ELM path → device=${profile.deviceName}  family=${profile.chipFamily}  fw=${profile.firmwareVersion}  clone=$isClone")
        return profile
    }

    /**
     * Build a [DeviceProfile] for a confirmed STN / OBDLink chip.
     *
     * Called when [STI] returns a response beginning with "STN" (e.g. "STN2232 v5.10.3").
     *
     * Additional ST commands probed here:
     * - **STDI** — hardware device name: `<name> rX.Y.Z` (e.g. "OBDLink MX+ r3.2.1")
     * - **STMFR** — manufacturer string: "OBD Solutions LLC" for OBDLink-branded products
     */
    private suspend fun buildStnProfile(stiResponse: String, transportLabel: String): DeviceProfile {
        // STDI gives the human-readable hardware name: "OBDLink MX+ r3.2.1"
        val stdiResponse = sendRaw("STDI", CMD_TIMEOUT_MS)
        // STMFR distinguishes OBDLink-branded product from a standalone STN IC
        val stmfrResponse = sendRaw("STMFR", CMD_TIMEOUT_MS)

        Timber.d("STDI → $stdiResponse")
        Timber.d("STMFR → $stmfrResponse")

        val firmwareVersion = extractFirmwareVersion(stiResponse)

        // Prefer STDI hardware name (e.g. "OBDLink MX+"); fall back to STN model ID
        val deviceName = when {
            stdiResponse.length > 3 && !stdiResponse.contains("?") ->
                stdiResponse.substringBefore(" r").trim()
            else ->
                stiResponse.substringBefore(" v").trim()   // e.g. "STN2232"
        }

        val family = if (stmfrResponse.contains("OBD Solutions", ignoreCase = true)) {
            AdapterFamily.OBDLINK
        } else {
            AdapterFamily.STN
        }

        Timber.d("STN identified — device=$deviceName  fw=$firmwareVersion  family=$family")

        // Registry check (e.g. OBDLink MX+ may have a curated profile)
        val registryHit = KnownDeviceRegistry.lookup(stdiResponse)
            ?: KnownDeviceRegistry.lookup(stiResponse)
        if (registryHit != null) {
            Timber.d("Registry hit: ${registryHit.deviceName}")
            return registryHit.copy(
                firmwareVersion = firmwareVersion.ifEmpty { registryHit.firmwareVersion },
                transport = transportLabel
            )
        }

        val atAlOk    = probeAtAl()
        val atCafOk   = probeAtCaf()
        val headersOk = probeCustomHeaders()

        val capabilities = buildCapabilities(
            family = family,
            supportsAtAl = atAlOk,
            supportsAtCaf = atCafOk,
            supportsHeaders = headersOk
        )

        val profile = DeviceProfile(
            deviceName = deviceName,
            chipFamily = family,
            firmwareVersion = firmwareVersion,
            transport = transportLabel,
            capabilities = capabilities
        )
        DiagnosticLogger.i("Fingerprint", "STN path → device=${profile.deviceName}  family=${profile.chipFamily}  fw=${profile.firmwareVersion}")
        return profile
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun classifyFamily(combinedId: String): AdapterFamily = when {
        combinedId.contains("OBDLINK") -> AdapterFamily.OBDLINK
        combinedId.contains("STN")     -> AdapterFamily.STN
        combinedId.contains("ELM327")  -> AdapterFamily.ELM327
        combinedId.contains("ESP")     -> AdapterFamily.ESP32
        else                           -> AdapterFamily.UNKNOWN
    }

    /**
     * Heuristics for clone detection on ELM327-class adapters.
     *
     * - v1.5 does not exist in official firmware → always clone
     * - v2.1 with a blank or OEM-looking AT@1 → likely clone
     * - v2.1 genuine chips report "OBDII to RS232 Interpreter" from AT@1
     */
    private fun detectClone(version: String, at1Response: String): Boolean {
        val v = version.lowercase().replace("v", "").trim()

        if (v == "1.5") return true

        if (v == "2.1") {
            val at1 = at1Response.uppercase().trim()
            val genuineAt1Phrases = listOf("OBDII TO RS232", "ELM ELECTRONICS")
            if (at1.isEmpty() || genuineAt1Phrases.none { at1.contains(it) }) {
                // Blank or OEM-brand AT@1 on a "v2.1" chip → clone
                val oemBrands = listOf("OBDIISCAN", "SCANTOOL", "ICAR", "VGATE", "VEEPEAK", "BAFX")
                if (oemBrands.any { at1.contains(it) } || at1.isEmpty()) return true
            }
        }
        return false
    }

    private fun extractFirmwareVersion(atiResponse: String): String {
        val regex = "v?(\\d+\\.\\d+[a-zA-Z]?)".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(atiResponse)?.value?.lowercase() ?: ""
    }

    private fun resolveDeviceName(atiResponse: String, at1Response: String): String = when {
        at1Response.length > 3 -> at1Response.trim()
        atiResponse.length > 3 -> atiResponse.trim()
        else -> "Unknown OBD Adapter"
    }

    private suspend fun probeAtAl(): Boolean {
        val r = sendRaw("ATAL", CMD_TIMEOUT_MS)
        return r.contains("OK") && !r.contains("?")
    }

    private suspend fun probeAtCaf(): Boolean {
        val r = sendRaw("ATCAF1", CMD_TIMEOUT_MS)
        val ok = r.contains("OK") && !r.contains("?")
        if (ok) sendRaw("ATCAF0", 500L)   // Restore default — don't leave it on
        return ok
    }

    private suspend fun probeCustomHeaders(): Boolean {
        val r = sendRaw("ATSH 7DF", CMD_TIMEOUT_MS)
        return r.contains("OK") && !r.contains("?")
    }

    /**
     * Send 0100 (Mode 01, PID 00 — supported PIDs).
     * A valid 41 00 response or even NO DATA confirms the protocol layer works.
     */
    private suspend fun probeProtocolStability(): Boolean {
        val r = sendRaw("0100", PROTO_PROBE_TIMEOUT_MS)
        return r.contains("41") || r.contains("NO DATA")
    }

    private fun buildCapabilities(
        family: AdapterFamily,
        supportsAtAl: Boolean,
        supportsAtCaf: Boolean,
        supportsHeaders: Boolean
    ): DeviceCapabilities = when (family) {

        AdapterFamily.OBDLINK, AdapterFamily.STN -> DeviceCapabilities(
            supportsLongCanFrames   = supportsAtAl,
            supportsSwCan           = true,
            supportsJ1939           = true,
            supportsAutoProtocol    = true,
            supportsAtAl            = supportsAtAl,
            supportsAtCaf           = supportsAtCaf,
            supportsCustomHeaders   = supportsHeaders,
            supportsHighSpeedPolling = true,
            requiresDelayBetweenCmds = false,
            bufferSize              = 1024,
            maxPollRateHz           = 25,
            recommendedDelayMs      = 5L
        )

        AdapterFamily.ELM327 -> DeviceCapabilities(
            supportsLongCanFrames   = supportsAtAl,
            supportsAutoProtocol    = true,
            supportsAtAl            = supportsAtAl,
            supportsAtCaf           = supportsAtCaf,
            supportsCustomHeaders   = supportsHeaders,
            requiresDelayBetweenCmds = false,
            bufferSize              = 256,
            maxPollRateHz           = 12,
            recommendedDelayMs      = 10L
        )

        AdapterFamily.ELM_CLONE -> DeviceCapabilities(
            supportsLongCanFrames   = false,
            supportsAutoProtocol    = true,
            supportsAtAl            = false,
            supportsAtCaf           = false,
            supportsCustomHeaders   = supportsHeaders,
            requiresDelayBetweenCmds = true,
            bufferSize              = 128,
            maxPollRateHz           = 8,
            recommendedDelayMs      = 30L,
            requiresKeepalive       = true
        )

        AdapterFamily.ESP32 -> DeviceCapabilities(
            supportsLongCanFrames   = true,
            supportsBinaryMode      = true,
            supportsAutoProtocol    = false,
            bufferSize              = 512,
            maxPollRateHz           = 50,
            recommendedDelayMs      = 2L
        )

        AdapterFamily.UNKNOWN -> DeviceCapabilities(
            requiresDelayBetweenCmds = true,
            bufferSize              = 128,
            maxPollRateHz           = 6,
            recommendedDelayMs      = 35L,
            requiresKeepalive       = true
        )
    }

    private suspend fun sendRaw(command: String, timeoutMs: Long): String =
        try {
            transport.sendCommand(command, timeoutMs)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w("Fingerprint '$command' failed: ${e.message}")
            ""
        }

    companion object {
        private const val RESET_TIMEOUT_MS   = 3_000L
        private const val RESET_SETTLE_MS    = 300L
        private const val CMD_TIMEOUT_MS     = 1_000L
        private const val PROTO_PROBE_TIMEOUT_MS = 2_000L
    }
}
