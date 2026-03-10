package com.odbplus.app.live

import com.odbplus.core.protocol.ObdPid

// ─────────────────────────────────────────────────────────────────────────────
// Enumerations
// ─────────────────────────────────────────────────────────────────────────────

enum class PidCategory(val displayName: String, val icon: String) {
    ESSENTIALS("Essentials", "⚡"),
    FUEL_ECONOMY("Fuel Economy", "⛽"),
    AIR_FUEL("Air / Fuel", "💨"),
    MISFIRE_COMBUSTION("Combustion", "🔥"),
    TEMPERATURES("Temperatures", "🌡"),
    ELECTRICAL("Electrical", "🔋"),
    EMISSIONS("Emissions", "🌿"),
    SENSORS("Sensors", "📡"),
    DIAGNOSTIC("Diagnostic", "🔧"),
    CUSTOM("Custom", "⭐")
}

enum class SensorStatus { NORMAL, WARNING, CRITICAL }

enum class PollingPriority { FAST, NORMAL, SLOW }

enum class LiveDisplayMode(val label: String) {
    NUMERIC("List"),
    GAUGE("Gauge"),
    GRAPH("Graph"),
    TILES("Tiles")
}

enum class SortOrder(val label: String) {
    CATEGORY("Category"),
    ALPHABETICAL("A–Z"),
    VALUE("Value"),
    SEVERITY("Severity"),
    MOST_ACTIVE("Most Active"),
    FAVORITES("Favorites"),
    SUPPORTED_ONLY("Supported Only")
}

// ─────────────────────────────────────────────────────────────────────────────
// PidDefinition
// ─────────────────────────────────────────────────────────────────────────────

data class PidDefinition(
    val pid: ObdPid,
    val name: String,
    val label: String,
    val category: PidCategory,
    val minValue: Double,
    val maxValue: Double,
    val warningMin: Double? = null,
    val warningMax: Double? = null,
    val criticalMin: Double? = null,
    val criticalMax: Double? = null,
    val graphable: Boolean = true,
    val gaugeEligible: Boolean = false,
    val pollingPriority: PollingPriority = PollingPriority.NORMAL,
    /** DTC codes that make this PID particularly relevant for diagnosis. */
    val dtcTags: Set<String> = emptySet()
) {
    fun statusFor(value: Double): SensorStatus {
        if (criticalMin != null && value < criticalMin) return SensorStatus.CRITICAL
        if (criticalMax != null && value > criticalMax) return SensorStatus.CRITICAL
        if (warningMin != null && value < warningMin) return SensorStatus.WARNING
        if (warningMax != null && value > warningMax) return SensorStatus.WARNING
        return SensorStatus.NORMAL
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PidRegistry — metadata for every ObdPid we care about rendering
// ─────────────────────────────────────────────────────────────────────────────

object PidRegistry {

    private val definitions: Map<ObdPid, PidDefinition> = buildMap {
        fun reg(
            pid: ObdPid,
            name: String,
            label: String,
            category: PidCategory,
            min: Double, max: Double,
            warnMin: Double? = null, warnMax: Double? = null,
            critMin: Double? = null, critMax: Double? = null,
            graphable: Boolean = true,
            gauge: Boolean = false,
            priority: PollingPriority = PollingPriority.NORMAL,
            dtcs: Set<String> = emptySet()
        ) {
            put(
                pid, PidDefinition(
                    pid, name, label, category, min, max,
                    warnMin, warnMax, critMin, critMax,
                    graphable, gauge, priority, dtcs
                )
            )
        }

        // ── Essentials / Engine ──────────────────────────────────────────────
        reg(
            ObdPid.ENGINE_RPM, "Engine RPM", "RPM", PidCategory.ESSENTIALS,
            min = 0.0, max = 8000.0,
            warnMax = 6000.0, critMax = 7000.0,
            gauge = true, priority = PollingPriority.FAST,
            dtcs = setOf("P0300", "P0301", "P0302", "P0303", "P0304", "P0506", "P0507")
        )
        reg(
            ObdPid.VEHICLE_SPEED, "Vehicle Speed", "Speed", PidCategory.ESSENTIALS,
            min = 0.0, max = 250.0,
            gauge = true, priority = PollingPriority.FAST
        )
        reg(
            ObdPid.ENGINE_LOAD, "Engine Load", "Load", PidCategory.ESSENTIALS,
            min = 0.0, max = 100.0,
            warnMax = 85.0, critMax = 95.0,
            gauge = true, priority = PollingPriority.FAST,
            dtcs = setOf("P0300", "P0506", "P0507", "P1518")
        )
        reg(
            ObdPid.THROTTLE_POSITION, "Throttle Position", "TPS", PidCategory.ESSENTIALS,
            min = 0.0, max = 100.0,
            gauge = true, priority = PollingPriority.FAST,
            dtcs = setOf("P0120", "P0220", "P0506", "P1518")
        )
        reg(
            ObdPid.TIMING_ADVANCE, "Timing Advance", "Timing", PidCategory.ESSENTIALS,
            min = -64.0, max = 64.0,
            warnMin = -10.0, critMin = -20.0,
            priority = PollingPriority.FAST,
            dtcs = setOf("P0300")
        )

        // ── Fuel Economy ─────────────────────────────────────────────────────
        reg(
            ObdPid.MAF_FLOW_RATE, "MAF Air Flow Rate", "MAF", PidCategory.FUEL_ECONOMY,
            min = 0.0, max = 655.35,
            priority = PollingPriority.FAST,
            dtcs = setOf("P0101", "P0102", "P0103", "P0300")
        )
        reg(
            ObdPid.ENGINE_FUEL_RATE, "Fuel Rate", "Fuel Rate", PidCategory.FUEL_ECONOMY,
            min = 0.0, max = 655.35,
            priority = PollingPriority.NORMAL
        )
        reg(
            ObdPid.FUEL_TANK_LEVEL, "Fuel Tank Level", "Fuel Level", PidCategory.FUEL_ECONOMY,
            min = 0.0, max = 100.0,
            warnMin = 15.0, critMin = 5.0,
            priority = PollingPriority.SLOW
        )

        // ── Air / Fuel ────────────────────────────────────────────────────────
        reg(
            ObdPid.SHORT_TERM_FUEL_TRIM_BANK1, "STFT Bank 1", "STFT B1",
            PidCategory.AIR_FUEL, min = -100.0, max = 99.2,
            warnMin = -10.0, warnMax = 10.0,
            critMin = -25.0, critMax = 25.0,
            priority = PollingPriority.FAST,
            dtcs = setOf("P0171", "P0172", "P0101", "P013A", "P013E")
        )
        reg(
            ObdPid.LONG_TERM_FUEL_TRIM_BANK1, "LTFT Bank 1", "LTFT B1",
            PidCategory.AIR_FUEL, min = -100.0, max = 99.2,
            warnMin = -10.0, warnMax = 10.0,
            critMin = -25.0, critMax = 25.0,
            priority = PollingPriority.FAST,
            dtcs = setOf("P0171", "P0172", "P0101", "P013A")
        )
        reg(
            ObdPid.SHORT_TERM_FUEL_TRIM_BANK2, "STFT Bank 2", "STFT B2",
            PidCategory.AIR_FUEL, min = -100.0, max = 99.2,
            warnMin = -10.0, warnMax = 10.0,
            critMin = -25.0, critMax = 25.0,
            priority = PollingPriority.FAST,
            dtcs = setOf("P0174", "P0175")
        )
        reg(
            ObdPid.LONG_TERM_FUEL_TRIM_BANK2, "LTFT Bank 2", "LTFT B2",
            PidCategory.AIR_FUEL, min = -100.0, max = 99.2,
            warnMin = -10.0, warnMax = 10.0,
            critMin = -25.0, critMax = 25.0,
            priority = PollingPriority.FAST,
            dtcs = setOf("P0174", "P0175")
        )
        reg(
            ObdPid.INTAKE_MANIFOLD_PRESSURE, "MAP Sensor", "MAP",
            PidCategory.AIR_FUEL, min = 0.0, max = 255.0,
            priority = PollingPriority.FAST,
            dtcs = setOf("P0101", "P0300")
        )
        reg(
            ObdPid.FUEL_PRESSURE, "Fuel Pressure", "Fuel Press",
            PidCategory.AIR_FUEL, min = 0.0, max = 765.0,
            warnMin = 250.0, critMin = 150.0,
            priority = PollingPriority.NORMAL,
            dtcs = setOf("P0087", "P0088")
        )

        // ── O2 Sensors ────────────────────────────────────────────────────────
        reg(
            ObdPid.O2_SENSOR_B1S1_VOLTAGE, "O2 B1S1 Voltage", "O2 B1S1",
            PidCategory.EMISSIONS, min = 0.0, max = 1.275,
            priority = PollingPriority.FAST,
            dtcs = setOf("P0131", "P0132", "P0133", "P0420")
        )
        reg(
            ObdPid.O2_SENSOR_B1S2_VOLTAGE, "O2 B1S2 (Downstream)", "O2 B1S2",
            PidCategory.EMISSIONS, min = 0.0, max = 1.275,
            priority = PollingPriority.NORMAL,
            dtcs = setOf("P0137", "P0138", "P0420")
        )
        reg(
            ObdPid.O2_SENSOR_B2S1_VOLTAGE, "O2 B2S1 Voltage", "O2 B2S1",
            PidCategory.EMISSIONS, min = 0.0, max = 1.275,
            priority = PollingPriority.FAST,
            dtcs = setOf("P0151", "P0152", "P0430")
        )
        reg(
            ObdPid.O2_SENSOR_B2S2_VOLTAGE, "O2 B2S2 (Downstream)", "O2 B2S2",
            PidCategory.EMISSIONS, min = 0.0, max = 1.275,
            priority = PollingPriority.NORMAL,
            dtcs = setOf("P0157", "P0158", "P0430")
        )

        // ── Temperatures ──────────────────────────────────────────────────────
        reg(
            ObdPid.ENGINE_COOLANT_TEMP, "Coolant Temp", "Coolant",
            PidCategory.TEMPERATURES, min = -40.0, max = 215.0,
            warnMax = 100.0, critMax = 115.0,
            warnMin = 60.0, critMin = 40.0,
            gauge = true, priority = PollingPriority.SLOW,
            dtcs = setOf("P0117", "P0118", "P0506", "P0507")
        )
        reg(
            ObdPid.INTAKE_AIR_TEMP, "Intake Air Temp", "IAT",
            PidCategory.TEMPERATURES, min = -40.0, max = 215.0,
            warnMax = 50.0, critMax = 65.0,
            priority = PollingPriority.SLOW,
            dtcs = setOf("P0101", "P0113", "P0114")
        )
        reg(
            ObdPid.AMBIENT_AIR_TEMP, "Ambient Air Temp", "Ambient",
            PidCategory.TEMPERATURES, min = -40.0, max = 85.0,
            priority = PollingPriority.SLOW
        )
        reg(
            ObdPid.ENGINE_OIL_TEMP, "Engine Oil Temp", "Oil Temp",
            PidCategory.TEMPERATURES, min = -40.0, max = 215.0,
            warnMax = 130.0, critMax = 150.0,
            priority = PollingPriority.SLOW
        )
        reg(
            ObdPid.CATALYST_TEMP_BANK1_SENSOR1, "Catalyst Temp B1S1", "Cat Temp B1",
            PidCategory.TEMPERATURES, min = -40.0, max = 6513.5,
            warnMax = 800.0, critMax = 900.0,
            priority = PollingPriority.SLOW,
            dtcs = setOf("P0420")
        )

        // ── Electrical ────────────────────────────────────────────────────────
        reg(
            ObdPid.CONTROL_MODULE_VOLTAGE, "Battery Voltage", "Voltage",
            PidCategory.ELECTRICAL, min = 0.0, max = 65.535,
            warnMin = 12.2, critMin = 11.0,
            warnMax = 15.0, critMax = 16.0,
            gauge = true, priority = PollingPriority.SLOW,
            dtcs = setOf("P0117", "P0118", "P1518")
        )

        // ── Emissions ─────────────────────────────────────────────────────────
        reg(
            ObdPid.COMMANDED_EGR, "Commanded EGR", "EGR Cmd",
            PidCategory.EMISSIONS, min = 0.0, max = 100.0,
            priority = PollingPriority.NORMAL
        )
        reg(
            ObdPid.EGR_ERROR, "EGR Error", "EGR Err",
            PidCategory.EMISSIONS, min = -100.0, max = 99.2,
            warnMin = -20.0, warnMax = 20.0,
            priority = PollingPriority.NORMAL
        )
        reg(
            ObdPid.EVAP_SYSTEM_VAPOR_PRESSURE, "EVAP Vapor Pressure", "EVAP Press",
            PidCategory.EMISSIONS, min = -8192.0, max = 8191.75,
            priority = PollingPriority.SLOW,
            dtcs = setOf("P0442", "P0455", "P0456")
        )
        reg(
            ObdPid.COMMANDED_EVAP_PURGE, "EVAP Purge", "EVAP Purge",
            PidCategory.EMISSIONS, min = 0.0, max = 100.0,
            priority = PollingPriority.SLOW,
            dtcs = setOf("P0442", "P0455")
        )

        // ── Sensors / Misc ────────────────────────────────────────────────────
        reg(
            ObdPid.BAROMETRIC_PRESSURE, "Barometric Pressure", "Baro",
            PidCategory.SENSORS, min = 0.0, max = 255.0,
            priority = PollingPriority.SLOW
        )
        reg(
            ObdPid.ABSOLUTE_THROTTLE_POSITION_B, "Absolute TPS-B", "TPS-B",
            PidCategory.SENSORS, min = 0.0, max = 100.0,
            priority = PollingPriority.FAST,
            dtcs = setOf("P0220", "P1518")
        )
        reg(
            ObdPid.ACCELERATOR_PEDAL_POSITION_D, "Pedal Position D", "APP-D",
            PidCategory.SENSORS, min = 0.0, max = 100.0,
            priority = PollingPriority.FAST,
            dtcs = setOf("P0120", "P1518")
        )
        reg(
            ObdPid.ACCELERATOR_PEDAL_POSITION_E, "Pedal Position E", "APP-E",
            PidCategory.SENSORS, min = 0.0, max = 100.0,
            priority = PollingPriority.FAST,
            dtcs = setOf("P0220", "P1518")
        )
        reg(
            ObdPid.RUNTIME_SINCE_START, "Engine Run Time", "Run Time",
            PidCategory.DIAGNOSTIC, min = 0.0, max = 65535.0,
            graphable = false, priority = PollingPriority.SLOW
        )
        reg(
            ObdPid.DISTANCE_SINCE_CODES_CLEARED, "Distance Since Clear", "Dist",
            PidCategory.DIAGNOSTIC, min = 0.0, max = 65535.0,
            graphable = false, priority = PollingPriority.SLOW
        )
    }

    fun get(pid: ObdPid): PidDefinition? = definitions[pid]

    fun getOrDefault(pid: ObdPid): PidDefinition = definitions[pid] ?: PidDefinition(
        pid = pid,
        name = pid.description,
        label = pid.code,
        category = PidCategory.SENSORS,
        minValue = 0.0,
        maxValue = Double.MAX_VALUE
    )

    /** PIDs that have full metadata defined. */
    val allDefined: Collection<PidDefinition> get() = definitions.values

    /** Fast polling PIDs for priority-based scheduler. */
    val fastPids: List<ObdPid> get() =
        definitions.values.filter { it.pollingPriority == PollingPriority.FAST }.map { it.pid }

    /** Slow polling PIDs. */
    val slowPids: List<ObdPid> get() =
        definitions.values.filter { it.pollingPriority == PollingPriority.SLOW }.map { it.pid }
}
