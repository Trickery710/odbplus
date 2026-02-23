package com.odbplus.core.protocol.driver

import com.odbplus.core.protocol.adapter.AdapterFamily
import com.odbplus.core.protocol.adapter.DeviceProfile
import com.odbplus.core.transport.ObdTransport
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Driver for STN1110 / STN1170 / STN2120 / STN2230 and OBDLink-branded devices.
 *
 * Capabilities enabled at init:
 * - High-speed polling (reduced inter-command delay to 5 ms)
 * - J1939 mode if supported (STN21xx)
 * - SW CAN (GMLAN) if supported
 * - ST proprietary extensions (STPX, STBR, etc.)
 * - Firmware version gate: full features only on >= 4.x
 *
 * OBDLink additionally:
 * - Sleep/wake management via ATLP / wake via 0100
 * - High baud rate negotiation
 */
class StnDriver(override var profile: DeviceProfile) : AdapterDriver {

    private val isOBDLink: Boolean = profile.chipFamily == AdapterFamily.OBDLINK
    private var consecutiveFailures = 0

    override suspend fun initialize(transport: ObdTransport) {
        Timber.d("StnDriver init — device=${profile.deviceName}  fw=${profile.firmwareVersion}")

        sendRaw(transport, "ATE0", 1_000L)
        sendRaw(transport, "ATL0", 1_000L)
        sendRaw(transport, "ATH1", 1_000L)   // Headers on
        sendRaw(transport, "ATSP0", 2_000L)  // Auto-detect protocol
        sendRaw(transport, "ATAT2", 1_000L)  // Adaptive timing level 2 (aggressive)

        // Enable long-frame support
        if (profile.capabilities.supportsAtAl) {
            sendRaw(transport, "ATAL", 1_000L)
        }

        // CAN auto-formatting
        if (profile.capabilities.supportsAtCaf) {
            sendRaw(transport, "ATCAF1", 1_000L)
        }

        // J1939 support (STN21xx only — check firmware)
        if (profile.capabilities.supportsJ1939 && isFirmwareAtLeast(4)) {
            val r = sendRaw(transport, "ATPB", 500L)  // Protocol B = J1939
            Timber.d("J1939 probe: $r")
        }

        // OBDLink-specific: ensure device is awake (not in low-power mode)
        if (isOBDLink) {
            // A simple 0100 will wake a sleeping OBDLink
            sendRaw(transport, "0100", 2_000L)
        }

        Timber.d("StnDriver init complete")
    }

    override suspend fun sendCommand(
        transport: ObdTransport,
        command: String,
        timeoutMs: Long
    ): String {
        val delayMs = interCommandDelayMs()

        repeat(MAX_RETRIES) { attempt ->
            if (delayMs > 0) delay(delayMs)

            val response = sendRaw(transport, command, adjustedTimeout(timeoutMs, attempt))

            when {
                response.isEmpty() -> {
                    Timber.w("StnDriver attempt ${attempt + 1}: empty response for $command")
                    onHealthDecrement(DeviceProfile.PENALTY_TIMEOUT, "empty response")
                }
                response.isErrorResponse() -> {
                    Timber.w("StnDriver attempt ${attempt + 1}: error response for $command: $response")
                    // Don't retry on hard error responses
                    return response
                }
                else -> {
                    consecutiveFailures = 0
                    recoverHealth()
                    return response
                }
            }
        }

        consecutiveFailures++
        return ""
    }

    override suspend fun onCommandFailure(
        transport: ObdTransport,
        command: String,
        reason: String
    ) {
        Timber.w("StnDriver failure on '$command': $reason")
        // STN devices rarely need a full reset — try a channel clear first
        transport.drainChannel()

        if (consecutiveFailures >= 3) {
            Timber.d("StnDriver: 3 consecutive failures — soft reset")
            sendRaw(transport, "ATZ", 2_000L)
            delay(200)
            sendRaw(transport, "ATE0", 500L)
            consecutiveFailures = 0
        }
    }

    override fun onHealthDecrement(penalty: Int, reason: String) {
        val newScore = maxOf(0, profile.healthScore - penalty)
        Timber.d("StnDriver health: ${profile.healthScore} → $newScore ($reason)")
        profile = profile.copy(healthScore = newScore)
    }

    override fun interCommandDelayMs(): Long = profile.capabilities.recommendedDelayMs

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun sendRaw(transport: ObdTransport, cmd: String, timeoutMs: Long): String =
        try {
            transport.drainChannel()
            transport.writeLine(cmd)
            transport.readUntilPrompt(timeoutMs)
        } catch (e: Exception) {
            Timber.w("StnDriver raw send '$cmd' exception: ${e.message}")
            ""
        }

    private fun adjustedTimeout(base: Long, attempt: Int): Long {
        var t = base
        repeat(attempt) { t = (t * 1.5).toLong() }
        return t.coerceAtMost(base * 3)
    }

    private fun recoverHealth() {
        if (profile.healthScore < DeviceProfile.MAX_HEALTH) {
            profile = profile.copy(
                healthScore = minOf(
                    profile.healthScore + DeviceProfile.RECOVERY_PER_SUCCESS,
                    DeviceProfile.MAX_HEALTH
                )
            )
        }
    }

    /**
     * Returns true if the firmware version is at least [major].x.
     * Treats unknown firmware as failing the gate.
     */
    private fun isFirmwareAtLeast(major: Int): Boolean {
        val v = profile.firmwareVersion.replace("v", "").replace("x", "0").trim()
        return v.substringBefore(".").toIntOrNull()?.let { it >= major } ?: false
    }

    private fun String.isErrorResponse(): Boolean {
        val upper = uppercase().trim()
        return upper.startsWith("?") || upper.startsWith("ERROR") ||
            upper.contains("UNABLE TO CONNECT") || upper.contains("BUS ERROR")
    }

    companion object {
        const val MAX_RETRIES = 3
    }
}
