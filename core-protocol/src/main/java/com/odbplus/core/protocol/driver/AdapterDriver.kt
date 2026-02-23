package com.odbplus.core.protocol.driver

import com.odbplus.core.protocol.adapter.DeviceProfile
import com.odbplus.core.transport.ObdTransport

/**
 * Per-family adapter driver interface.
 *
 * Each concrete driver encapsulates the command timing, retry logic, and
 * adapter-specific initialization for one hardware family.  Drivers are
 * instantiated by [DriverFactory] after fingerprinting is complete.
 */
interface AdapterDriver {

    /** Resolved profile for the connected device. */
    val profile: DeviceProfile

    /**
     * Run adapter-specific initialization after the device has been identified.
     *
     * Examples:
     * - ELM/Clone: apply delay guards, disable long-frame support if unstable
     * - STN: enable J1939, set high baud, reduce inter-command delay
     * - OBDLink: verify firmware >= 4.x, configure sleep/wake
     * - ESP32: detect binary vs ASCII mode, negotiate frame format
     */
    suspend fun initialize(transport: ObdTransport)

    /**
     * Send a raw command (AT or OBD hex) and return the response text.
     *
     * The driver is responsible for:
     * - Inserting the correct inter-command delay
     * - Retrying up to [maxRetries] on timeout or corrupt response
     * - Notifying the health monitor on failures
     *
     * @param transport The active low-level transport
     * @param command   Raw command string (e.g. "010C", "ATZ")
     * @param timeoutMs Per-attempt timeout
     * @return The adapter's response, or empty string on total failure
     */
    suspend fun sendCommand(
        transport: ObdTransport,
        command: String,
        timeoutMs: Long = 3_000L
    ): String

    /**
     * Called when a command fails after all retries.
     * Allows the driver to update internal state (e.g. disable a feature).
     */
    suspend fun onCommandFailure(transport: ObdTransport, command: String, reason: String)

    /**
     * Decrement the device health score.
     * [penalty] is taken from [DeviceProfile] penalty constants.
     */
    fun onHealthDecrement(penalty: Int, reason: String)

    /** Milliseconds to wait between consecutive commands. */
    fun interCommandDelayMs(): Long = profile.capabilities.recommendedDelayMs
}
