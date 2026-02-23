package com.odbplus.core.protocol.adapter

import com.odbplus.core.transport.ObdTransport
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Identifies, classifies, and probes an OBD-II adapter after transport connection.
 *
 * Detection algorithm:
 * 1. Reset (ATZ) and suppress echo/linefeeds
 * 2. Read identity strings (ATI, AT@1, ATDP)
 * 3. Classify chip family from response keywords
 * 4. Apply clone-detection heuristics
 * 5. Probe optional AT commands (ATAL, ATCAF1, ATSH)
 * 6. Test live protocol stability with 0100
 * 7. Cross-reference against [KnownDeviceRegistry]
 * 8. Build and return a [DeviceProfile]
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

        // ── Step 2: Read identity ─────────────────────────────────────────────
        val atiResponse  = sendRaw("ATI",  CMD_TIMEOUT_MS)   // e.g. "ELM327 v2.1"
        val at1Response  = sendRaw("AT@1", CMD_TIMEOUT_MS)   // e.g. "OBDLink MX+"
        val atdpResponse = sendRaw("ATDP", CMD_TIMEOUT_MS)   // e.g. "AUTO"

        Timber.d("ATZ  → $atzResponse")
        Timber.d("ATI  → $atiResponse")
        Timber.d("AT@1 → $at1Response")
        Timber.d("ATDP → $atdpResponse")

        // ── Step 3: Classify family ───────────────────────────────────────────
        val combinedId = "$atzResponse $atiResponse $at1Response".uppercase()
        val detectedFamily = classifyFamily(combinedId)
        val firmwareVersion = extractFirmwareVersion(atiResponse)

        // ── Step 4: Clone detection ───────────────────────────────────────────
        val isClone = detectedFamily == AdapterFamily.ELM327 && detectClone(firmwareVersion, at1Response)
        val resolvedFamily = if (isClone) AdapterFamily.ELM_CLONE else detectedFamily

        Timber.d("Family: $resolvedFamily  FW: $firmwareVersion  Clone: $isClone")

        // ── Step 5: Probe AT capabilities ─────────────────────────────────────
        val atAlOk    = probeAtAl()
        val atCafOk   = probeAtCaf()
        val headersOk = probeCustomHeaders()

        // ── Step 6: Protocol stability test ───────────────────────────────────
        val protocolLive = probeProtocolStability()

        Timber.d("ATAL=$atAlOk  ATCAF=$atCafOk  Headers=$headersOk  Proto=$protocolLive")

        // ── Step 7: Known-device registry lookup ──────────────────────────────
        val registryHit = KnownDeviceRegistry.lookup(at1Response)
            ?: KnownDeviceRegistry.lookup(atiResponse)

        if (registryHit != null) {
            Timber.d("Registry hit: ${registryHit.deviceName}")
            return registryHit.copy(
                firmwareVersion = firmwareVersion.ifEmpty { registryHit.firmwareVersion },
                transport = transportLabel
            )
        }

        // ── Step 8: Build capability profile from probed results ──────────────
        val capabilities = buildCapabilities(
            family = resolvedFamily,
            supportsAtAl = atAlOk,
            supportsAtCaf = atCafOk,
            supportsHeaders = headersOk
        )

        return DeviceProfile(
            deviceName = resolveDeviceName(atiResponse, at1Response),
            chipFamily = resolvedFamily,
            firmwareVersion = firmwareVersion,
            transport = transportLabel,
            capabilities = capabilities
        )
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
            transport.drainChannel()
            transport.writeLine(command)
            transport.readUntilPrompt(timeoutMs)
        } catch (e: Exception) {
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
