package com.odbplus.core.protocol.driver

import com.odbplus.core.protocol.adapter.AdapterFamily
import com.odbplus.core.protocol.adapter.DeviceCapabilities
import com.odbplus.core.protocol.adapter.DeviceProfile
import com.odbplus.core.transport.ObdTransport
import kotlin.math.pow
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Driver for genuine ELM327 and ELM327 clones.
 *
 * Clone-specific mitigations applied at init:
 * - 30 ms inter-command delay enforced (configurable)
 * - ATAL disabled if unstable (clones crash on long CAN frames)
 * - Poll rate capped at 8 PIDs/sec for clones
 * - Manual flow-control (no ATCAF) for ISO-TP on clones
 */
class ElmDriver(override var profile: DeviceProfile) : AdapterDriver {

    private val isClone: Boolean = profile.chipFamily == AdapterFamily.ELM_CLONE
    private var consecutiveFailures = 0

    override suspend fun initialize(transport: ObdTransport) {
        Timber.d("ElmDriver init — clone=$isClone  fw=${profile.firmwareVersion}")

        sendRaw(transport, "ATE0", 1_000L)   // Echo off
        sendRaw(transport, "ATL0", 1_000L)   // Linefeeds off
        sendRaw(transport, "ATH1", 1_000L)   // Headers on (needed for ISO-TP framing)
        sendRaw(transport, "ATSP0", 2_000L)  // Auto-detect protocol

        if (profile.capabilities.supportsAtAl && !isClone) {
            val r = sendRaw(transport, "ATAL", 1_000L)
            if (!r.contains("OK")) {
                Timber.w("ATAL rejected — disabling long-frame support")
                profile = profile.copy(
                    capabilities = profile.capabilities.copy(supportsLongCanFrames = false)
                )
            }
        }

        // Reduce timing multiplier for genuine chips; clones keep longer timeouts
        if (!isClone) {
            sendRaw(transport, "ATAT1", 1_000L)   // Adaptive timing level 1
        }

        Timber.d("ElmDriver init complete")
    }

    override suspend fun sendCommand(
        transport: ObdTransport,
        command: String,
        timeoutMs: Long
    ): String {
        val delayMs = if (isClone) maxOf(interCommandDelayMs(), 30L) else interCommandDelayMs()

        repeat(MAX_RETRIES) { attempt ->
            if (delayMs > 0) delay(delayMs)

            val response = sendRaw(transport, command, adjustedTimeout(timeoutMs, attempt))

            when {
                response.isEmpty() -> {
                    Timber.w("ElmDriver attempt ${attempt + 1}: empty response for $command")
                    onHealthDecrement(DeviceProfile.PENALTY_TIMEOUT, "empty response")
                }
                response.isCorrupt() -> {
                    Timber.w("ElmDriver attempt ${attempt + 1}: corrupt frame for $command")
                    onHealthDecrement(DeviceProfile.PENALTY_CORRUPT_FRAME, "corrupt frame")
                }
                else -> {
                    consecutiveFailures = 0
                    // Recover health slightly on success
                    if (profile.healthScore < DeviceProfile.MAX_HEALTH) {
                        profile = profile.copy(
                            healthScore = minOf(
                                profile.healthScore + DeviceProfile.RECOVERY_PER_SUCCESS,
                                DeviceProfile.MAX_HEALTH
                            )
                        )
                    }
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
        Timber.w("ElmDriver failure on '$command': $reason")
        // On clone overflow, try a soft reset
        if (isClone && consecutiveFailures >= 2) {
            Timber.d("ElmDriver: clone instability — issuing soft reset")
            sendRaw(transport, "ATZ", 2_000L)
            delay(300)
            sendRaw(transport, "ATE0", 1_000L)
            sendRaw(transport, "ATL0", 1_000L)
            consecutiveFailures = 0
        }
    }

    override fun onHealthDecrement(penalty: Int, reason: String) {
        val newScore = maxOf(0, profile.healthScore - penalty)
        Timber.d("ElmDriver health: ${profile.healthScore} → $newScore ($reason)")
        profile = profile.copy(healthScore = newScore)
    }

    override fun interCommandDelayMs(): Long =
        profile.capabilities.recommendedDelayMs

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun sendRaw(transport: ObdTransport, cmd: String, timeoutMs: Long): String =
        try {
            transport.drainChannel()
            transport.writeLine(cmd)
            transport.readUntilPrompt(timeoutMs)
        } catch (e: Exception) {
            Timber.w("ElmDriver raw send '$cmd' exception: ${e.message}")
            ""
        }

    /** Backoff: each retry adds 50 % to the timeout, up to 3× original. */
    private fun adjustedTimeout(base: Long, attempt: Int): Long =
        (base * TIMEOUT_BACKOFF_MULTIPLIER.pow(attempt)).toLong().coerceAtMost(base * 3)

    private fun String.isCorrupt(): Boolean {
        val upper = uppercase().trim()
        return upper.contains("BUFFER FULL") ||
            upper.contains("DATA ERROR") ||
            upper.contains("<DATA ERROR") ||
            (upper.isNotEmpty() && upper.all { !it.isLetterOrDigit() && it != ' ' && it != '\n' })
    }

    companion object {
        const val MAX_RETRIES = 3
        const val TIMEOUT_BACKOFF_MULTIPLIER = 1.5
    }
}
