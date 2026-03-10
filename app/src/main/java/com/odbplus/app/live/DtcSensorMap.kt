package com.odbplus.app.live

import com.odbplus.core.protocol.ObdPid

/**
 * Maps DTC codes to the PIDs most relevant for diagnosing that fault.
 *
 * When the user is in "DTC Focus" mode with an active trouble code, only
 * these PIDs are shown so the display stays focused on what matters.
 */
object DtcSensorMap {

    private val map: Map<String, List<ObdPid>> = mapOf(
        // MAF / Air flow
        "P0100" to listOf(ObdPid.MAF_FLOW_RATE, ObdPid.INTAKE_MANIFOLD_PRESSURE, ObdPid.ENGINE_RPM, ObdPid.THROTTLE_POSITION, ObdPid.INTAKE_AIR_TEMP, ObdPid.ENGINE_LOAD),
        "P0101" to listOf(ObdPid.MAF_FLOW_RATE, ObdPid.INTAKE_MANIFOLD_PRESSURE, ObdPid.ENGINE_RPM, ObdPid.THROTTLE_POSITION, ObdPid.INTAKE_AIR_TEMP, ObdPid.ENGINE_LOAD, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.LONG_TERM_FUEL_TRIM_BANK1),
        "P0102" to listOf(ObdPid.MAF_FLOW_RATE, ObdPid.INTAKE_MANIFOLD_PRESSURE, ObdPid.CONTROL_MODULE_VOLTAGE, ObdPid.ENGINE_RPM),
        "P0103" to listOf(ObdPid.MAF_FLOW_RATE, ObdPid.INTAKE_MANIFOLD_PRESSURE, ObdPid.CONTROL_MODULE_VOLTAGE, ObdPid.ENGINE_RPM),

        // Throttle position
        "P0120" to listOf(ObdPid.THROTTLE_POSITION, ObdPid.ABSOLUTE_THROTTLE_POSITION_B, ObdPid.ACCELERATOR_PEDAL_POSITION_D, ObdPid.ENGINE_RPM, ObdPid.ENGINE_LOAD),
        "P0121" to listOf(ObdPid.THROTTLE_POSITION, ObdPid.ABSOLUTE_THROTTLE_POSITION_B, ObdPid.ENGINE_LOAD, ObdPid.ENGINE_RPM),
        "P0220" to listOf(ObdPid.THROTTLE_POSITION, ObdPid.ABSOLUTE_THROTTLE_POSITION_B, ObdPid.ACCELERATOR_PEDAL_POSITION_E, ObdPid.ENGINE_RPM, ObdPid.ENGINE_LOAD),

        // Coolant temperature
        "P0117" to listOf(ObdPid.ENGINE_COOLANT_TEMP, ObdPid.CONTROL_MODULE_VOLTAGE, ObdPid.INTAKE_AIR_TEMP),
        "P0118" to listOf(ObdPid.ENGINE_COOLANT_TEMP, ObdPid.CONTROL_MODULE_VOLTAGE, ObdPid.INTAKE_AIR_TEMP),

        // O2 sensors
        "P0130" to listOf(ObdPid.O2_SENSOR_B1S1_VOLTAGE, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.LONG_TERM_FUEL_TRIM_BANK1),
        "P0131" to listOf(ObdPid.O2_SENSOR_B1S1_VOLTAGE, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.LONG_TERM_FUEL_TRIM_BANK1, ObdPid.FUEL_PRESSURE),
        "P0132" to listOf(ObdPid.O2_SENSOR_B1S1_VOLTAGE, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.LONG_TERM_FUEL_TRIM_BANK1),
        "P0133" to listOf(ObdPid.O2_SENSOR_B1S1_VOLTAGE, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.ENGINE_RPM, ObdPid.ENGINE_LOAD),
        "P0137" to listOf(ObdPid.O2_SENSOR_B1S2_VOLTAGE, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.LONG_TERM_FUEL_TRIM_BANK1),
        "P0138" to listOf(ObdPid.O2_SENSOR_B1S2_VOLTAGE, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1),
        "P013A" to listOf(ObdPid.O2_SENSOR_B1S1_VOLTAGE, ObdPid.O2_SENSOR_B1S2_VOLTAGE, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.LONG_TERM_FUEL_TRIM_BANK1),
        "P013E" to listOf(ObdPid.O2_SENSOR_B1S1_VOLTAGE, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.LONG_TERM_FUEL_TRIM_BANK1, ObdPid.MAF_FLOW_RATE),
        "P0150" to listOf(ObdPid.O2_SENSOR_B2S1_VOLTAGE, ObdPid.SHORT_TERM_FUEL_TRIM_BANK2, ObdPid.LONG_TERM_FUEL_TRIM_BANK2),
        "P0151" to listOf(ObdPid.O2_SENSOR_B2S1_VOLTAGE, ObdPid.SHORT_TERM_FUEL_TRIM_BANK2, ObdPid.LONG_TERM_FUEL_TRIM_BANK2),
        "P0157" to listOf(ObdPid.O2_SENSOR_B2S2_VOLTAGE, ObdPid.SHORT_TERM_FUEL_TRIM_BANK2),

        // Fuel system lean/rich
        "P0171" to listOf(ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.LONG_TERM_FUEL_TRIM_BANK1, ObdPid.MAF_FLOW_RATE, ObdPid.O2_SENSOR_B1S1_VOLTAGE, ObdPid.FUEL_PRESSURE, ObdPid.INTAKE_MANIFOLD_PRESSURE),
        "P0172" to listOf(ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.LONG_TERM_FUEL_TRIM_BANK1, ObdPid.MAF_FLOW_RATE, ObdPid.O2_SENSOR_B1S1_VOLTAGE, ObdPid.FUEL_PRESSURE),
        "P0174" to listOf(ObdPid.SHORT_TERM_FUEL_TRIM_BANK2, ObdPid.LONG_TERM_FUEL_TRIM_BANK2, ObdPid.MAF_FLOW_RATE, ObdPid.O2_SENSOR_B2S1_VOLTAGE),
        "P0175" to listOf(ObdPid.SHORT_TERM_FUEL_TRIM_BANK2, ObdPid.LONG_TERM_FUEL_TRIM_BANK2, ObdPid.MAF_FLOW_RATE, ObdPid.O2_SENSOR_B2S1_VOLTAGE),

        // Misfire
        "P0300" to listOf(ObdPid.ENGINE_RPM, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.LONG_TERM_FUEL_TRIM_BANK1, ObdPid.MAF_FLOW_RATE, ObdPid.INTAKE_MANIFOLD_PRESSURE, ObdPid.TIMING_ADVANCE, ObdPid.ENGINE_COOLANT_TEMP),
        "P0301" to listOf(ObdPid.ENGINE_RPM, ObdPid.ENGINE_LOAD, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.TIMING_ADVANCE),
        "P0302" to listOf(ObdPid.ENGINE_RPM, ObdPid.ENGINE_LOAD, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.TIMING_ADVANCE),
        "P0303" to listOf(ObdPid.ENGINE_RPM, ObdPid.ENGINE_LOAD, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.TIMING_ADVANCE),
        "P0304" to listOf(ObdPid.ENGINE_RPM, ObdPid.ENGINE_LOAD, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.TIMING_ADVANCE),

        // Catalyst efficiency
        "P0420" to listOf(ObdPid.O2_SENSOR_B1S1_VOLTAGE, ObdPid.O2_SENSOR_B1S2_VOLTAGE, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.LONG_TERM_FUEL_TRIM_BANK1, ObdPid.ENGINE_LOAD, ObdPid.ENGINE_COOLANT_TEMP, ObdPid.CATALYST_TEMP_BANK1_SENSOR1),
        "P0430" to listOf(ObdPid.O2_SENSOR_B2S1_VOLTAGE, ObdPid.O2_SENSOR_B2S2_VOLTAGE, ObdPid.SHORT_TERM_FUEL_TRIM_BANK2, ObdPid.LONG_TERM_FUEL_TRIM_BANK2, ObdPid.ENGINE_LOAD),

        // Fuel pressure
        "P0087" to listOf(ObdPid.FUEL_PRESSURE, ObdPid.ENGINE_LOAD, ObdPid.ENGINE_RPM, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1),
        "P0088" to listOf(ObdPid.FUEL_PRESSURE, ObdPid.ENGINE_RPM, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1),

        // EVAP
        "P0442" to listOf(ObdPid.EVAP_SYSTEM_VAPOR_PRESSURE, ObdPid.COMMANDED_EVAP_PURGE, ObdPid.FUEL_TANK_LEVEL),
        "P0455" to listOf(ObdPid.EVAP_SYSTEM_VAPOR_PRESSURE, ObdPid.COMMANDED_EVAP_PURGE, ObdPid.FUEL_TANK_LEVEL),
        "P0456" to listOf(ObdPid.EVAP_SYSTEM_VAPOR_PRESSURE, ObdPid.COMMANDED_EVAP_PURGE, ObdPid.FUEL_TANK_LEVEL),

        // Idle
        "P0506" to listOf(ObdPid.ENGINE_RPM, ObdPid.THROTTLE_POSITION, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.LONG_TERM_FUEL_TRIM_BANK1, ObdPid.ENGINE_COOLANT_TEMP),
        "P0507" to listOf(ObdPid.ENGINE_RPM, ObdPid.THROTTLE_POSITION, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, ObdPid.LONG_TERM_FUEL_TRIM_BANK1, ObdPid.ENGINE_COOLANT_TEMP),

        // Throttle actuator / pedal
        "P1518" to listOf(ObdPid.THROTTLE_POSITION, ObdPid.ABSOLUTE_THROTTLE_POSITION_B, ObdPid.ACCELERATOR_PEDAL_POSITION_D, ObdPid.ACCELERATOR_PEDAL_POSITION_E, ObdPid.CONTROL_MODULE_VOLTAGE, ObdPid.ENGINE_RPM),
    )

    /**
     * Returns the ordered list of PIDs relevant to the given [dtcCode].
     * Returns empty list if the DTC is not mapped.
     */
    fun getPidsForDtc(dtcCode: String): List<ObdPid> =
        map[dtcCode.uppercase()] ?: emptyList()

    /**
     * Returns all PIDs relevant to any of the given DTC codes, de-duplicated,
     * ordered by how many DTCs they appear in (most-relevant first).
     */
    fun getPidsForDtcs(dtcCodes: List<String>): List<ObdPid> {
        val freq = mutableMapOf<ObdPid, Int>()
        for (code in dtcCodes) {
            for (pid in getPidsForDtc(code)) {
                freq[pid] = (freq[pid] ?: 0) + 1
            }
        }
        return freq.entries.sortedByDescending { it.value }.map { it.key }
    }

    /** All DTC codes that have a PID mapping defined. */
    val knownDtcs: Set<String> get() = map.keys
}
