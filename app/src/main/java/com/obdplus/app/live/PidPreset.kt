package com.obdplus.app.live

import com.obdplus.core.protocol.ObdPid

enum class PidPreset(val displayName: String, val pids: List<ObdPid>) {
    ENGINE_BASICS(
        "Engine Basics",
        listOf(ObdPid.ENGINE_RPM, ObdPid.ENGINE_LOAD, ObdPid.ENGINE_COOLANT_TEMP, ObdPid.THROTTLE_POSITION)
    ),
    DRIVING(
        "Driving",
        listOf(ObdPid.ENGINE_RPM, ObdPid.VEHICLE_SPEED, ObdPid.THROTTLE_POSITION, ObdPid.ENGINE_LOAD)
    ),
    FUEL_ECONOMY(
        "Fuel Economy",
        listOf(ObdPid.MAF_FLOW_RATE, ObdPid.VEHICLE_SPEED, ObdPid.ENGINE_FUEL_RATE, ObdPid.FUEL_TANK_LEVEL)
    ),
    TEMPERATURES(
        "Temperatures",
        listOf(ObdPid.ENGINE_COOLANT_TEMP, ObdPid.INTAKE_AIR_TEMP, ObdPid.AMBIENT_AIR_TEMP, ObdPid.ENGINE_OIL_TEMP)
    ),
    FULL_DASHBOARD(
        "Full Dashboard",
        listOf(
            ObdPid.ENGINE_RPM, ObdPid.VEHICLE_SPEED, ObdPid.ENGINE_COOLANT_TEMP,
            ObdPid.THROTTLE_POSITION, ObdPid.ENGINE_LOAD, ObdPid.FUEL_TANK_LEVEL
        )
    ),
    AIR_FUEL(
        "Air / Fuel",
        listOf(
            ObdPid.MAF_FLOW_RATE, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
            ObdPid.LONG_TERM_FUEL_TRIM_BANK1, ObdPid.INTAKE_MANIFOLD_PRESSURE,
            ObdPid.O2_SENSOR_B1S1_VOLTAGE
        )
    ),
    MISFIRE_DIAGNOSIS(
        "Misfire",
        listOf(
            ObdPid.ENGINE_RPM, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
            ObdPid.LONG_TERM_FUEL_TRIM_BANK1, ObdPid.MAF_FLOW_RATE,
            ObdPid.TIMING_ADVANCE, ObdPid.ENGINE_LOAD
        )
    )
}
