package com.odbplus.core.protocol.adapter

/**
 * Seed registry of known OBD-II adapter profiles.
 *
 * Used by [AdapterFingerprinter] to short-circuit capability probing when
 * a device's identity string matches a known entry.  Entries can be updated
 * at runtime via [register] to support remote capability database updates.
 */
object KnownDeviceRegistry {

    private val registry: MutableMap<String, DeviceProfile> = mutableMapOf(

        // ── OBDLink / STN family ───────────────────────────────────────────────

        "obdlink mx+" to DeviceProfile(
            deviceName = "OBDLink MX+",
            chipFamily = AdapterFamily.OBDLINK,
            firmwareVersion = "5.x",
            transport = "BLE-Secure",
            capabilities = DeviceCapabilities(
                supportsLongCanFrames = true,
                supportsSwCan = true,
                supportsJ1939 = true,
                supportsAutoProtocol = true,
                supportsAtAl = true,
                supportsAtCaf = true,
                supportsCustomHeaders = true,
                supportsHighSpeedPolling = true,
                bufferSize = 2048,
                maxPollRateHz = 30,
                recommendedDelayMs = 0L,
                requiresKeepalive = false
            )
        ),

        "obdlink mx" to DeviceProfile(
            deviceName = "OBDLink MX",
            chipFamily = AdapterFamily.OBDLINK,
            firmwareVersion = "4.x",
            transport = "Bluetooth",
            capabilities = DeviceCapabilities(
                supportsLongCanFrames = true,
                supportsJ1939 = true,
                supportsAutoProtocol = true,
                supportsAtAl = true,
                supportsAtCaf = true,
                supportsCustomHeaders = true,
                supportsHighSpeedPolling = true,
                bufferSize = 1024,
                maxPollRateHz = 25,
                recommendedDelayMs = 0L
            )
        ),

        "obdlink lx" to DeviceProfile(
            deviceName = "OBDLink LX",
            chipFamily = AdapterFamily.OBDLINK,
            firmwareVersion = "4.x",
            transport = "Bluetooth",
            capabilities = DeviceCapabilities(
                supportsLongCanFrames = true,
                supportsSwCan = true,
                supportsJ1939 = true,
                supportsAutoProtocol = true,
                supportsAtAl = true,
                supportsAtCaf = true,
                supportsCustomHeaders = true,
                supportsHighSpeedPolling = true,
                bufferSize = 1024,
                maxPollRateHz = 25,
                recommendedDelayMs = 0L
            )
        ),

        "obdlink sx" to DeviceProfile(
            deviceName = "OBDLink SX",
            chipFamily = AdapterFamily.OBDLINK,
            firmwareVersion = "4.x",
            transport = "USB",
            capabilities = DeviceCapabilities(
                supportsLongCanFrames = true,
                supportsJ1939 = true,
                supportsAutoProtocol = true,
                supportsAtAl = true,
                supportsAtCaf = true,
                supportsCustomHeaders = true,
                supportsHighSpeedPolling = true,
                bufferSize = 1024,
                maxPollRateHz = 25,
                recommendedDelayMs = 0L
            )
        ),

        "obdlink cx" to DeviceProfile(
            deviceName = "OBDLink CX",
            chipFamily = AdapterFamily.OBDLINK,
            firmwareVersion = "5.x",
            transport = "BLE",
            capabilities = DeviceCapabilities(
                supportsLongCanFrames = true,
                supportsJ1939 = true,
                supportsAutoProtocol = true,
                supportsAtAl = true,
                supportsAtCaf = true,
                supportsCustomHeaders = true,
                supportsHighSpeedPolling = true,
                bufferSize = 1024,
                maxPollRateHz = 25,
                recommendedDelayMs = 0L,
                requiresKeepalive = false
            )
        ),

        // ── Common ELM327 clones ──────────────────────────────────────────────

        "veepeak" to DeviceProfile(
            deviceName = "Veepeak Mini Bluetooth",
            chipFamily = AdapterFamily.ELM_CLONE,
            firmwareVersion = "v2.1",
            transport = "Bluetooth",
            capabilities = DeviceCapabilities(
                supportsLongCanFrames = false,
                supportsAutoProtocol = true,
                supportsAtCaf = false,
                supportsAtAl = false,
                requiresDelayBetweenCmds = true,
                bufferSize = 128,
                maxPollRateHz = 8,
                recommendedDelayMs = 30L,
                requiresKeepalive = true,
                keepaliveIntervalMs = 10_000L
            )
        ),

        "bafx" to DeviceProfile(
            deviceName = "BAFX Bluetooth",
            chipFamily = AdapterFamily.ELM_CLONE,
            firmwareVersion = "v1.5",
            transport = "Bluetooth",
            capabilities = DeviceCapabilities(
                supportsLongCanFrames = false,
                supportsAutoProtocol = true,
                supportsAtCaf = false,
                supportsAtAl = false,
                requiresDelayBetweenCmds = true,
                bufferSize = 128,
                maxPollRateHz = 8,
                recommendedDelayMs = 30L,
                requiresKeepalive = true
            )
        ),

        "konnwei" to DeviceProfile(
            deviceName = "KONNWEI KW902",
            chipFamily = AdapterFamily.ELM_CLONE,
            firmwareVersion = "v1.5",
            transport = "Bluetooth",
            capabilities = DeviceCapabilities(
                supportsLongCanFrames = false,
                supportsAutoProtocol = true,
                supportsAtCaf = false,
                supportsAtAl = false,
                requiresDelayBetweenCmds = true,
                bufferSize = 64,
                maxPollRateHz = 6,
                recommendedDelayMs = 35L,
                requiresKeepalive = true
            )
        ),

        // Generic blue dongle — most common clone
        "generic elm327" to DeviceProfile(
            deviceName = "Generic ELM327 Bluetooth",
            chipFamily = AdapterFamily.ELM_CLONE,
            firmwareVersion = "v2.1",
            transport = "Bluetooth",
            capabilities = DeviceCapabilities(
                supportsLongCanFrames = false,
                supportsAutoProtocol = true,
                supportsAtCaf = false,
                supportsAtAl = false,
                requiresDelayBetweenCmds = true,
                bufferSize = 128,
                maxPollRateHz = 8,
                recommendedDelayMs = 30L,
                requiresKeepalive = true
            )
        )
    )

    /**
     * Look up a profile by matching [nameHint] (case-insensitive substring match).
     * Returns the first match found, or null if no entry matches.
     */
    fun lookup(nameHint: String): DeviceProfile? {
        val lower = nameHint.lowercase()
        return registry.entries.firstOrNull { (key, _) -> lower.contains(key) }?.value
    }

    /**
     * Register or update a device profile at runtime.
     * Useful for remote capability database updates.
     *
     * @param key Lowercase lookup key (e.g. "obdlink mx+")
     * @param profile The profile to store
     */
    fun register(key: String, profile: DeviceProfile) {
        registry[key.lowercase()] = profile
    }

    /** All profiles in the registry. */
    fun all(): List<DeviceProfile> = registry.values.toList()
}
