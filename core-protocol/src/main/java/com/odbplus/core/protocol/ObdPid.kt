package com.odbplus.core.protocol

/**
 * OBD-II Parameter IDs (PIDs) for Mode 01 (live data).
 *
 * Each PID defines:
 * - The hex code to send
 * - Number of expected data bytes
 * - How to parse the response into a meaningful value
 */
enum class ObdPid(
    val code: String,
    val description: String,
    val unit: String,
    val expectedBytes: Int,
    private val parser: (ByteArray) -> Double
) {
    // Engine
    ENGINE_LOAD(
        code = "04",
        description = "Calculated Engine Load",
        unit = "%",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() * 100.0 / 255.0 }
    ),

    ENGINE_COOLANT_TEMP(
        code = "05",
        description = "Engine Coolant Temperature",
        unit = "°C",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() - 40.0 }
    ),

    SHORT_TERM_FUEL_TRIM_BANK1(
        code = "06",
        description = "Short Term Fuel Trim - Bank 1",
        unit = "%",
        expectedBytes = 1,
        parser = { bytes -> (bytes[0].toUByte().toDouble() - 128.0) * 100.0 / 128.0 }
    ),

    LONG_TERM_FUEL_TRIM_BANK1(
        code = "07",
        description = "Long Term Fuel Trim - Bank 1",
        unit = "%",
        expectedBytes = 1,
        parser = { bytes -> (bytes[0].toUByte().toDouble() - 128.0) * 100.0 / 128.0 }
    ),

    FUEL_PRESSURE(
        code = "0A",
        description = "Fuel Pressure",
        unit = "kPa",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() * 3.0 }
    ),

    INTAKE_MANIFOLD_PRESSURE(
        code = "0B",
        description = "Intake Manifold Absolute Pressure",
        unit = "kPa",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() }
    ),

    ENGINE_RPM(
        code = "0C",
        description = "Engine RPM",
        unit = "rpm",
        expectedBytes = 2,
        parser = { bytes ->
            val a = bytes[0].toUByte().toInt()
            val b = bytes[1].toUByte().toInt()
            (256 * a + b) / 4.0
        }
    ),

    VEHICLE_SPEED(
        code = "0D",
        description = "Vehicle Speed",
        unit = "km/h",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() }
    ),

    TIMING_ADVANCE(
        code = "0E",
        description = "Timing Advance",
        unit = "°",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() / 2.0 - 64.0 }
    ),

    INTAKE_AIR_TEMP(
        code = "0F",
        description = "Intake Air Temperature",
        unit = "°C",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() - 40.0 }
    ),

    MAF_FLOW_RATE(
        code = "10",
        description = "MAF Air Flow Rate",
        unit = "g/s",
        expectedBytes = 2,
        parser = { bytes ->
            val a = bytes[0].toUByte().toInt()
            val b = bytes[1].toUByte().toInt()
            (256 * a + b) / 100.0
        }
    ),

    THROTTLE_POSITION(
        code = "11",
        description = "Throttle Position",
        unit = "%",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() * 100.0 / 255.0 }
    ),

    RUNTIME_SINCE_START(
        code = "1F",
        description = "Run Time Since Engine Start",
        unit = "sec",
        expectedBytes = 2,
        parser = { bytes ->
            val a = bytes[0].toUByte().toInt()
            val b = bytes[1].toUByte().toInt()
            (256 * a + b).toDouble()
        }
    ),

    FUEL_TANK_LEVEL(
        code = "2F",
        description = "Fuel Tank Level Input",
        unit = "%",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() * 100.0 / 255.0 }
    ),

    BAROMETRIC_PRESSURE(
        code = "33",
        description = "Barometric Pressure",
        unit = "kPa",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() }
    ),

    CATALYST_TEMP_BANK1_SENSOR1(
        code = "3C",
        description = "Catalyst Temperature: Bank 1, Sensor 1",
        unit = "°C",
        expectedBytes = 2,
        parser = { bytes ->
            val a = bytes[0].toUByte().toInt()
            val b = bytes[1].toUByte().toInt()
            (256 * a + b) / 10.0 - 40.0
        }
    ),

    CONTROL_MODULE_VOLTAGE(
        code = "42",
        description = "Control Module Voltage",
        unit = "V",
        expectedBytes = 2,
        parser = { bytes ->
            val a = bytes[0].toUByte().toInt()
            val b = bytes[1].toUByte().toInt()
            (256 * a + b) / 1000.0
        }
    ),

    ABSOLUTE_LOAD(
        code = "43",
        description = "Absolute Load Value",
        unit = "%",
        expectedBytes = 2,
        parser = { bytes ->
            val a = bytes[0].toUByte().toInt()
            val b = bytes[1].toUByte().toInt()
            (256 * a + b) * 100.0 / 255.0
        }
    ),

    AMBIENT_AIR_TEMP(
        code = "46",
        description = "Ambient Air Temperature",
        unit = "°C",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() - 40.0 }
    ),

    ENGINE_OIL_TEMP(
        code = "5C",
        description = "Engine Oil Temperature",
        unit = "°C",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() - 40.0 }
    ),

    ENGINE_FUEL_RATE(
        code = "5E",
        description = "Engine Fuel Rate",
        unit = "L/h",
        expectedBytes = 2,
        parser = { bytes ->
            val a = bytes[0].toUByte().toInt()
            val b = bytes[1].toUByte().toInt()
            (256 * a + b) / 20.0
        }
    );

    /**
     * The full command to send (Mode 01 + PID code).
     */
    val command: String get() = "01$code"

    /**
     * Parse raw data bytes into a meaningful value.
     */
    fun parse(data: ByteArray): Double {
        require(data.size >= expectedBytes) {
            "Expected $expectedBytes bytes for $name, got ${data.size}"
        }
        return parser(data)
    }

    companion object {
        private val byCode = entries.associateBy { it.code.uppercase() }

        /**
         * Find a PID by its hex code (e.g., "0C" for RPM).
         */
        fun fromCode(code: String): ObdPid? = byCode[code.uppercase()]
    }
}
