package com.odbplus.core.protocol.driver

import com.odbplus.core.protocol.adapter.DeviceProfile
import com.odbplus.core.transport.ObdTransport
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Driver for ESP32-based CAN/OBD adapters.
 *
 * These devices may operate in several modes:
 * - **ASCII ELM emulation** — behaves like a standard ELM327
 * - **Binary framed stream** — fixed-width CAN frames with optional CRC
 * - **REST / WebSocket** — HTTP or WS interface (not handled here; handled by transport)
 *
 * The driver auto-detects which mode is active during [initialize] and
 * switches its parser accordingly.
 */
class Esp32Driver(override var profile: DeviceProfile) : AdapterDriver {

    enum class Esp32Mode { ASCII_ELM, BINARY_STREAM, UNKNOWN }

    var activeMode: Esp32Mode = Esp32Mode.UNKNOWN
        private set

    private var consecutiveFailures = 0

    override suspend fun initialize(transport: ObdTransport) {
        Timber.d("Esp32Driver init — device=${profile.deviceName}")

        // Try ASCII ELM emulation first
        val r = sendRaw(transport, "ATI", 1_500L)

        activeMode = when {
            r.contains("ELM327", ignoreCase = true) -> {
                Timber.d("Esp32Driver: ASCII ELM emulation mode")
                sendRaw(transport, "ATE0", 500L)
                sendRaw(transport, "ATL0", 500L)
                sendRaw(transport, "ATSP0", 2_000L)
                Esp32Mode.ASCII_ELM
            }
            r.isEmpty() || r.all { it.code < 32 || it.code > 126 } -> {
                Timber.d("Esp32Driver: Binary stream mode detected")
                // Binary mode: send wake frame and detect stream format
                detectBinaryProtocol(transport)
                Esp32Mode.BINARY_STREAM
            }
            else -> {
                Timber.w("Esp32Driver: Unknown response format — $r")
                Esp32Mode.UNKNOWN
            }
        }
    }

    override suspend fun sendCommand(
        transport: ObdTransport,
        command: String,
        timeoutMs: Long
    ): String {
        val delayMs = interCommandDelayMs()
        if (delayMs > 0) delay(delayMs)

        return when (activeMode) {
            Esp32Mode.ASCII_ELM    -> sendAscii(transport, command, timeoutMs)
            Esp32Mode.BINARY_STREAM -> sendBinary(transport, command, timeoutMs)
            Esp32Mode.UNKNOWN      -> sendAscii(transport, command, timeoutMs)
        }
    }

    override suspend fun onCommandFailure(
        transport: ObdTransport,
        command: String,
        reason: String
    ) {
        Timber.w("Esp32Driver failure on '$command': $reason")
        consecutiveFailures++
        if (consecutiveFailures >= 3) {
            // Re-probe mode — ESP32 firmware may have reset
            Timber.d("Esp32Driver: re-detecting mode after 3 failures")
            initialize(transport)
            consecutiveFailures = 0
        }
    }

    override fun onHealthDecrement(penalty: Int, reason: String) {
        val newScore = maxOf(0, profile.healthScore - penalty)
        Timber.d("Esp32Driver health: ${profile.healthScore} → $newScore ($reason)")
        profile = profile.copy(healthScore = newScore)
    }

    override fun interCommandDelayMs(): Long = profile.capabilities.recommendedDelayMs

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun detectBinaryProtocol(transport: ObdTransport) {
        // Send a known query in binary format and measure response shape.
        // Binary frames for common ESP32 CAN gateways typically have:
        //   [0xAA] [len] [canId_hi] [canId_lo] [data...] [CRC]
        // We send a raw 0x7DF broadcast frame for service 01 PID 00.
        val probeFrame = byteArrayOf(0xAA.toByte(), 0x08, 0x07, 0xDF.toByte(),
            0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        try {
            transport.writeLine(probeFrame.decodeToString())
            delay(300)
        } catch (e: Exception) {
            Timber.w("Esp32Driver binary probe failed: ${e.message}")
        }
    }

    private suspend fun sendAscii(
        transport: ObdTransport,
        command: String,
        timeoutMs: Long
    ): String =
        try {
            transport.drainChannel()
            transport.writeLine(command)
            val response = transport.readUntilPrompt(timeoutMs)
            if (response.isNotEmpty()) {
                consecutiveFailures = 0
            }
            response
        } catch (e: Exception) {
            Timber.w("Esp32Driver ASCII send '$command' exception: ${e.message}")
            ""
        }

    /**
     * Send a command as a binary CAN frame and return the decoded hex response.
     * The exact framing depends on the ESP32 firmware; this is a best-effort
     * implementation for the most common DIY gateway format.
     */
    private suspend fun sendBinary(
        transport: ObdTransport,
        command: String,
        timeoutMs: Long
    ): String {
        Timber.d("Esp32Driver: binary send '$command'")
        // Fallback to ASCII for now; binary parsing is firmware-specific
        return sendAscii(transport, command, timeoutMs)
    }

    private suspend fun sendRaw(transport: ObdTransport, cmd: String, timeoutMs: Long): String =
        try {
            transport.drainChannel()
            transport.writeLine(cmd)
            transport.readUntilPrompt(timeoutMs)
        } catch (e: Exception) {
            ""
        }
}
