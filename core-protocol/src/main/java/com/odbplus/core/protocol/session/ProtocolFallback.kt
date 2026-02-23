package com.odbplus.core.protocol.session

import com.odbplus.core.protocol.adapter.DeviceProfile
import timber.log.Timber

/**
 * OBD-II protocol fallback sequencer.
 *
 * Attempts protocols in priority order, tracking which have been tried.
 * Designed to work with any ELM/STN adapter that accepts ATSP commands.
 *
 * ## Fallback order (per spec)
 * 1. CAN 11-bit 500k  (ISO 15765-4 — most modern vehicles)
 * 2. CAN 29-bit 500k
 * 3. CAN 11/29-bit 250k
 * 4. ISO 9141-2       (older European/Asian vehicles)
 * 5. KWP2000 (ISO 14230-4)
 * 6. J1850 PWM        (Ford)
 * 7. J1850 VPW        (GM)
 *
 * ## Timeout strategy
 * - Initial timeout: 1000 ms
 * - Retry timeout: 1500 ms
 * - Backoff multiplier: 1.5×
 * - Max retries per protocol: 3
 */
class ProtocolFallback(private val profile: DeviceProfile) {

    data class ProtocolEntry(
        val name: String,
        val atspCommand: String,
        val initialTimeoutMs: Long = 1_000L
    )

    private val protocols: List<ProtocolEntry> = buildList {
        add(ProtocolEntry("CAN 11-bit 500k",   "ATSP6", 1_000L))
        add(ProtocolEntry("CAN 29-bit 500k",   "ATSP7", 1_000L))
        add(ProtocolEntry("CAN 11-bit 250k",   "ATSP8", 1_500L))
        add(ProtocolEntry("CAN 29-bit 250k",   "ATSP9", 1_500L))
        add(ProtocolEntry("ISO 9141-2",        "ATSP3", 2_000L))
        add(ProtocolEntry("KWP2000 (fast)",    "ATSP5", 2_000L))
        add(ProtocolEntry("KWP2000 (5-baud)",  "ATSP4", 3_000L))
        add(ProtocolEntry("J1850 PWM",         "ATSP1", 2_000L))
        add(ProtocolEntry("J1850 VPW",         "ATSP2", 2_000L))
    }

    private var currentIndex = 0
    private val failedProtocols = mutableSetOf<String>()

    /** True when all protocols have been exhausted. */
    val isExhausted: Boolean get() = currentIndex >= protocols.size

    /** The protocol entry to try next, or null if exhausted. */
    fun nextProtocol(): ProtocolEntry? {
        while (currentIndex < protocols.size) {
            val candidate = protocols[currentIndex]
            if (candidate.name !in failedProtocols) {
                return candidate
            }
            currentIndex++
        }
        return null
    }

    /** Mark the current protocol as failed and advance to the next one. */
    fun markCurrentFailed() {
        if (currentIndex < protocols.size) {
            val p = protocols[currentIndex]
            Timber.w("Protocol fallback: '$${p.name}' failed — trying next")
            failedProtocols.add(p.name)
            currentIndex++
        }
    }

    /** Mark the current protocol as succeeded. */
    fun markCurrentSucceeded() {
        if (currentIndex < protocols.size) {
            Timber.d("Protocol fallback: '${protocols[currentIndex].name}' succeeded")
        }
    }

    /** Reset and start over (e.g. after a device reset). */
    fun reset() {
        currentIndex = 0
        failedProtocols.clear()
    }

    /**
     * Compute the per-retry timeout with backoff.
     *
     * @param baseTimeoutMs  The protocol's initial timeout
     * @param attempt        0-based retry attempt index
     */
    fun retryTimeout(baseTimeoutMs: Long, attempt: Int): Long {
        var t = baseTimeoutMs
        repeat(attempt) { t = (t * BACKOFF_MULTIPLIER).toLong() }
        return t.coerceAtMost(baseTimeoutMs * MAX_TIMEOUT_MULTIPLIER.toLong())
    }

    companion object {
        const val MAX_RETRIES_PER_PROTOCOL = 3
        const val BACKOFF_MULTIPLIER = 1.5
        const val MAX_TIMEOUT_MULTIPLIER = 4
    }
}
