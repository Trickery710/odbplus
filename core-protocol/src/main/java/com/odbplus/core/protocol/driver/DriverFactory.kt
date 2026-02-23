package com.odbplus.core.protocol.driver

import com.odbplus.core.protocol.adapter.AdapterFamily
import com.odbplus.core.protocol.adapter.DeviceProfile

/**
 * Creates the correct [AdapterDriver] implementation for a resolved [DeviceProfile].
 */
object DriverFactory {

    /**
     * Instantiate and return the appropriate driver.
     *
     * - ELM327 genuine → [ElmDriver] (normal timing)
     * - ELM_CLONE      → [ElmDriver] (clone-conservative timing)
     * - STN / OBDLink  → [StnDriver]
     * - ESP32          → [Esp32Driver]
     * - UNKNOWN        → [ElmDriver] with maximum-conservative defaults
     */
    fun create(profile: DeviceProfile): AdapterDriver = when (profile.chipFamily) {
        AdapterFamily.ELM327,
        AdapterFamily.ELM_CLONE,
        AdapterFamily.UNKNOWN  -> ElmDriver(profile)

        AdapterFamily.STN,
        AdapterFamily.OBDLINK  -> StnDriver(profile)

        AdapterFamily.ESP32    -> Esp32Driver(profile)
    }
}
