package com.odbplus.core.protocol

/**
 * OBD-II Parameter IDs (PIDs) for Mode 01 (live data).
 *
 * Each PID defines:
 * - The hex code to send
 * - Number of expected data bytes
 * - How to parse the response into a meaningful value
 *
 * Reference: SAE J1979 / ISO 15031-5
 */
enum class ObdPid(
    val code: String,
    val description: String,
    val unit: String,
    val expectedBytes: Int,
    private val parser: (ByteArray) -> Double
) {
    // ========== PIDs Supported Bitmasks ==========

    PIDS_SUPPORTED_01_20(
        code = "00",
        description = "PIDs Supported [01-20]",
        unit = "bitmask",
        expectedBytes = 4,
        parser = ::fourByteBitmask
    ),

    PIDS_SUPPORTED_21_40(
        code = "20",
        description = "PIDs Supported [21-40]",
        unit = "bitmask",
        expectedBytes = 4,
        parser = ::fourByteBitmask
    ),

    PIDS_SUPPORTED_41_60(
        code = "40",
        description = "PIDs Supported [41-60]",
        unit = "bitmask",
        expectedBytes = 4,
        parser = ::fourByteBitmask
    ),

    PIDS_SUPPORTED_61_80(
        code = "60",
        description = "PIDs Supported [61-80]",
        unit = "bitmask",
        expectedBytes = 4,
        parser = ::fourByteBitmask
    ),

    PIDS_SUPPORTED_81_A0(
        code = "80",
        description = "PIDs Supported [81-A0]",
        unit = "bitmask",
        expectedBytes = 4,
        parser = ::fourByteBitmask
    ),

    PIDS_SUPPORTED_A1_C0(
        code = "A0",
        description = "PIDs Supported [A1-C0]",
        unit = "bitmask",
        expectedBytes = 4,
        parser = ::fourByteBitmask
    ),

    PIDS_SUPPORTED_C1_E0(
        code = "C0",
        description = "PIDs Supported [C1-E0]",
        unit = "bitmask",
        expectedBytes = 4,
        parser = ::fourByteBitmask
    ),

    // ========== Monitor Status ==========

    MONITOR_STATUS(
        code = "01",
        description = "Monitor Status Since DTCs Cleared",
        unit = "bitmask",
        expectedBytes = 4,
        parser = ::fourByteBitmask
    ),

    FREEZE_DTC(
        code = "02",
        description = "Freeze DTC",
        unit = "code",
        expectedBytes = 2,
        parser = ::twoByteUInt
    ),

    // ========== Fuel System ==========

    FUEL_SYSTEM_STATUS(
        code = "03",
        description = "Fuel System Status",
        unit = "bitmask",
        expectedBytes = 2,
        parser = ::twoByteUInt
    ),

    ENGINE_LOAD(
        code = "04",
        description = "Calculated Engine Load",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    ENGINE_COOLANT_TEMP(
        code = "05",
        description = "Engine Coolant Temperature",
        unit = "°C",
        expectedBytes = 1,
        parser = ::singleByteOffset40
    ),

    SHORT_TERM_FUEL_TRIM_BANK1(
        code = "06",
        description = "Short Term Fuel Trim - Bank 1",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleByteSigned128Percent
    ),

    LONG_TERM_FUEL_TRIM_BANK1(
        code = "07",
        description = "Long Term Fuel Trim - Bank 1",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleByteSigned128Percent
    ),

    SHORT_TERM_FUEL_TRIM_BANK2(
        code = "08",
        description = "Short Term Fuel Trim - Bank 2",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleByteSigned128Percent
    ),

    LONG_TERM_FUEL_TRIM_BANK2(
        code = "09",
        description = "Long Term Fuel Trim - Bank 2",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleByteSigned128Percent
    ),

    FUEL_PRESSURE(
        code = "0A",
        description = "Fuel Pressure",
        unit = "kPa",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() * 3.0 }
    ),

    // ========== Intake / Engine ==========

    INTAKE_MANIFOLD_PRESSURE(
        code = "0B",
        description = "Intake Manifold Absolute Pressure",
        unit = "kPa",
        expectedBytes = 1,
        parser = ::singleByte
    ),

    ENGINE_RPM(
        code = "0C",
        description = "Engine RPM",
        unit = "rpm",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) / 4.0 }
    ),

    VEHICLE_SPEED(
        code = "0D",
        description = "Vehicle Speed",
        unit = "km/h",
        expectedBytes = 1,
        parser = ::singleByte
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
        parser = ::singleByteOffset40
    ),

    MAF_FLOW_RATE(
        code = "10",
        description = "MAF Air Flow Rate",
        unit = "g/s",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) / 100.0 }
    ),

    THROTTLE_POSITION(
        code = "11",
        description = "Throttle Position",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    // ========== Secondary Air / O2 Sensors Present ==========

    COMMANDED_SECONDARY_AIR_STATUS(
        code = "12",
        description = "Commanded Secondary Air Status",
        unit = "status",
        expectedBytes = 1,
        parser = ::singleByte
    ),

    O2_SENSORS_PRESENT_2_BANKS(
        code = "13",
        description = "Oxygen Sensors Present (2 Banks)",
        unit = "bitmask",
        expectedBytes = 1,
        parser = ::singleByte
    ),

    // ========== Oxygen Sensors (Bank 1 & 2, Voltage) ==========

    O2_SENSOR_B1S1_VOLTAGE(
        code = "14",
        description = "O2 Sensor Bank 1, Sensor 1 - Voltage",
        unit = "V",
        expectedBytes = 2,
        parser = ::o2Voltage
    ),

    O2_SENSOR_B1S1_FUEL_TRIM(
        code = "14",
        description = "O2 Sensor Bank 1, Sensor 1 - Short Term Fuel Trim",
        unit = "%",
        expectedBytes = 2,
        parser = { bytes -> (bytes[1].toUByte().toDouble() - 128.0) * 100.0 / 128.0 }
    ),

    O2_SENSOR_B1S2_VOLTAGE(
        code = "15",
        description = "O2 Sensor Bank 1, Sensor 2 - Voltage",
        unit = "V",
        expectedBytes = 2,
        parser = ::o2Voltage
    ),

    O2_SENSOR_B1S3_VOLTAGE(
        code = "16",
        description = "O2 Sensor Bank 1, Sensor 3 - Voltage",
        unit = "V",
        expectedBytes = 2,
        parser = ::o2Voltage
    ),

    O2_SENSOR_B1S4_VOLTAGE(
        code = "17",
        description = "O2 Sensor Bank 1, Sensor 4 - Voltage",
        unit = "V",
        expectedBytes = 2,
        parser = ::o2Voltage
    ),

    O2_SENSOR_B2S1_VOLTAGE(
        code = "18",
        description = "O2 Sensor Bank 2, Sensor 1 - Voltage",
        unit = "V",
        expectedBytes = 2,
        parser = ::o2Voltage
    ),

    O2_SENSOR_B2S2_VOLTAGE(
        code = "19",
        description = "O2 Sensor Bank 2, Sensor 2 - Voltage",
        unit = "V",
        expectedBytes = 2,
        parser = ::o2Voltage
    ),

    O2_SENSOR_B2S3_VOLTAGE(
        code = "1A",
        description = "O2 Sensor Bank 2, Sensor 3 - Voltage",
        unit = "V",
        expectedBytes = 2,
        parser = ::o2Voltage
    ),

    O2_SENSOR_B2S4_VOLTAGE(
        code = "1B",
        description = "O2 Sensor Bank 2, Sensor 4 - Voltage",
        unit = "V",
        expectedBytes = 2,
        parser = ::o2Voltage
    ),

    // ========== OBD Standards / Aux Input ==========

    OBD_STANDARDS(
        code = "1C",
        description = "OBD Standards This Vehicle Conforms To",
        unit = "type",
        expectedBytes = 1,
        parser = ::singleByte
    ),

    O2_SENSORS_PRESENT_4_BANKS(
        code = "1D",
        description = "Oxygen Sensors Present (4 Banks)",
        unit = "bitmask",
        expectedBytes = 1,
        parser = ::singleByte
    ),

    AUXILIARY_INPUT_STATUS(
        code = "1E",
        description = "Auxiliary Input Status",
        unit = "status",
        expectedBytes = 1,
        parser = ::singleByte
    ),

    RUNTIME_SINCE_START(
        code = "1F",
        description = "Run Time Since Engine Start",
        unit = "sec",
        expectedBytes = 2,
        parser = ::twoByteUInt
    ),

    // ========== Distance / Fuel Rail ==========

    DISTANCE_WITH_MIL_ON(
        code = "21",
        description = "Distance Traveled with MIL On",
        unit = "km",
        expectedBytes = 2,
        parser = ::twoByteUInt
    ),

    FUEL_RAIL_PRESSURE_VAC(
        code = "22",
        description = "Fuel Rail Pressure (Relative to Vacuum)",
        unit = "kPa",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) * 0.079 }
    ),

    FUEL_RAIL_GAUGE_PRESSURE(
        code = "23",
        description = "Fuel Rail Gauge Pressure (Diesel/GDI)",
        unit = "kPa",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) * 10.0 }
    ),

    // ========== Oxygen Sensors (Lambda / Voltage) ==========

    O2_SENSOR_B1S1_LAMBDA(
        code = "24",
        description = "O2 Sensor Bank 1, Sensor 1 - Lambda",
        unit = "ratio",
        expectedBytes = 4,
        parser = ::lambdaRatio
    ),

    O2_SENSOR_B1S2_LAMBDA(
        code = "25",
        description = "O2 Sensor Bank 1, Sensor 2 - Lambda",
        unit = "ratio",
        expectedBytes = 4,
        parser = ::lambdaRatio
    ),

    O2_SENSOR_B2S1_LAMBDA(
        code = "26",
        description = "O2 Sensor Bank 2, Sensor 1 - Lambda",
        unit = "ratio",
        expectedBytes = 4,
        parser = ::lambdaRatio
    ),

    O2_SENSOR_B2S2_LAMBDA(
        code = "27",
        description = "O2 Sensor Bank 2, Sensor 2 - Lambda",
        unit = "ratio",
        expectedBytes = 4,
        parser = ::lambdaRatio
    ),

    O2_SENSOR_B1S1_LAMBDA_VOLTAGE(
        code = "24",
        description = "O2 Sensor Bank 1, Sensor 1 - Voltage (Wide Band)",
        unit = "V",
        expectedBytes = 4,
        parser = { bytes -> (bytes[2].toUByte().toInt() * 256 + bytes[3].toUByte().toInt()) * 8.0 / 65536.0 }
    ),

    O2_SENSOR_B3S1_LAMBDA(
        code = "28",
        description = "O2 Sensor Bank 3, Sensor 1 - Lambda",
        unit = "ratio",
        expectedBytes = 4,
        parser = ::lambdaRatio
    ),

    O2_SENSOR_B3S2_LAMBDA(
        code = "29",
        description = "O2 Sensor Bank 3, Sensor 2 - Lambda",
        unit = "ratio",
        expectedBytes = 4,
        parser = ::lambdaRatio
    ),

    O2_SENSOR_B4S1_LAMBDA(
        code = "2A",
        description = "O2 Sensor Bank 4, Sensor 1 - Lambda",
        unit = "ratio",
        expectedBytes = 4,
        parser = ::lambdaRatio
    ),

    O2_SENSOR_B4S2_LAMBDA(
        code = "2B",
        description = "O2 Sensor Bank 4, Sensor 2 - Lambda",
        unit = "ratio",
        expectedBytes = 4,
        parser = ::lambdaRatio
    ),

    // ========== EGR / Evaporative System ==========

    COMMANDED_EGR(
        code = "2C",
        description = "Commanded EGR",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    EGR_ERROR(
        code = "2D",
        description = "EGR Error",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleByteSigned128Percent
    ),

    COMMANDED_EVAP_PURGE(
        code = "2E",
        description = "Commanded Evaporative Purge",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    FUEL_TANK_LEVEL(
        code = "2F",
        description = "Fuel Tank Level Input",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    // ========== Warmups / Distance / Pressure ==========

    WARMUPS_SINCE_CODES_CLEARED(
        code = "30",
        description = "Warm-Ups Since Codes Cleared",
        unit = "count",
        expectedBytes = 1,
        parser = ::singleByte
    ),

    DISTANCE_SINCE_CODES_CLEARED(
        code = "31",
        description = "Distance Traveled Since Codes Cleared",
        unit = "km",
        expectedBytes = 2,
        parser = ::twoByteUInt
    ),

    EVAP_SYSTEM_VAPOR_PRESSURE(
        code = "32",
        description = "Evap. System Vapor Pressure",
        unit = "Pa",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toByte().toInt() * 256 + bytes[1].toUByte().toInt()) / 4.0 }
    ),

    BAROMETRIC_PRESSURE(
        code = "33",
        description = "Barometric Pressure",
        unit = "kPa",
        expectedBytes = 1,
        parser = ::singleByte
    ),

    // ========== Oxygen Sensors (Lambda / Current) ==========

    O2_SENSOR_B1S1_CURRENT(
        code = "34",
        description = "O2 Sensor Bank 1, Sensor 1 - Current",
        unit = "mA",
        expectedBytes = 4,
        parser = ::wideRangeCurrent
    ),

    O2_SENSOR_B1S2_CURRENT(
        code = "35",
        description = "O2 Sensor Bank 1, Sensor 2 - Current",
        unit = "mA",
        expectedBytes = 4,
        parser = ::wideRangeCurrent
    ),

    O2_SENSOR_B2S1_CURRENT(
        code = "36",
        description = "O2 Sensor Bank 2, Sensor 1 - Current",
        unit = "mA",
        expectedBytes = 4,
        parser = ::wideRangeCurrent
    ),

    O2_SENSOR_B2S2_CURRENT(
        code = "37",
        description = "O2 Sensor Bank 2, Sensor 2 - Current",
        unit = "mA",
        expectedBytes = 4,
        parser = ::wideRangeCurrent
    ),

    O2_SENSOR_B3S1_CURRENT(
        code = "38",
        description = "O2 Sensor Bank 3, Sensor 1 - Current",
        unit = "mA",
        expectedBytes = 4,
        parser = ::wideRangeCurrent
    ),

    O2_SENSOR_B3S2_CURRENT(
        code = "39",
        description = "O2 Sensor Bank 3, Sensor 2 - Current",
        unit = "mA",
        expectedBytes = 4,
        parser = ::wideRangeCurrent
    ),

    O2_SENSOR_B4S1_CURRENT(
        code = "3A",
        description = "O2 Sensor Bank 4, Sensor 1 - Current",
        unit = "mA",
        expectedBytes = 4,
        parser = ::wideRangeCurrent
    ),

    O2_SENSOR_B4S2_CURRENT(
        code = "3B",
        description = "O2 Sensor Bank 4, Sensor 2 - Current",
        unit = "mA",
        expectedBytes = 4,
        parser = ::wideRangeCurrent
    ),

    // ========== Catalyst Temperature ==========

    CATALYST_TEMP_BANK1_SENSOR1(
        code = "3C",
        description = "Catalyst Temperature: Bank 1, Sensor 1",
        unit = "°C",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) / 10.0 - 40.0 }
    ),

    CATALYST_TEMP_BANK2_SENSOR1(
        code = "3D",
        description = "Catalyst Temperature: Bank 2, Sensor 1",
        unit = "°C",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) / 10.0 - 40.0 }
    ),

    CATALYST_TEMP_BANK1_SENSOR2(
        code = "3E",
        description = "Catalyst Temperature: Bank 1, Sensor 2",
        unit = "°C",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) / 10.0 - 40.0 }
    ),

    CATALYST_TEMP_BANK2_SENSOR2(
        code = "3F",
        description = "Catalyst Temperature: Bank 2, Sensor 2",
        unit = "°C",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) / 10.0 - 40.0 }
    ),

    // ========== Monitor Status / Voltage / Load ==========

    MONITOR_STATUS_DRIVE_CYCLE(
        code = "41",
        description = "Monitor Status This Drive Cycle",
        unit = "bitmask",
        expectedBytes = 4,
        parser = ::fourByteBitmask
    ),

    CONTROL_MODULE_VOLTAGE(
        code = "42",
        description = "Control Module Voltage",
        unit = "V",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) / 1000.0 }
    ),

    ABSOLUTE_LOAD(
        code = "43",
        description = "Absolute Load Value",
        unit = "%",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) * 100.0 / 255.0 }
    ),

    COMMANDED_AIR_FUEL_RATIO(
        code = "44",
        description = "Commanded Air-Fuel Equivalence Ratio",
        unit = "ratio",
        expectedBytes = 2,
        parser = ::lambdaRatio
    ),

    // ========== Throttle Positions ==========

    RELATIVE_THROTTLE_POSITION(
        code = "45",
        description = "Relative Throttle Position",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    AMBIENT_AIR_TEMP(
        code = "46",
        description = "Ambient Air Temperature",
        unit = "°C",
        expectedBytes = 1,
        parser = ::singleByteOffset40
    ),

    ABSOLUTE_THROTTLE_POSITION_B(
        code = "47",
        description = "Absolute Throttle Position B",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    ABSOLUTE_THROTTLE_POSITION_C(
        code = "48",
        description = "Absolute Throttle Position C",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    // ========== Accelerator Pedal Positions ==========

    ACCELERATOR_PEDAL_POSITION_D(
        code = "49",
        description = "Accelerator Pedal Position D",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    ACCELERATOR_PEDAL_POSITION_E(
        code = "4A",
        description = "Accelerator Pedal Position E",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    ACCELERATOR_PEDAL_POSITION_F(
        code = "4B",
        description = "Accelerator Pedal Position F",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    COMMANDED_THROTTLE_ACTUATOR(
        code = "4C",
        description = "Commanded Throttle Actuator",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    // ========== Time PIDs ==========

    TIME_WITH_MIL_ON(
        code = "4D",
        description = "Time Run with MIL On",
        unit = "min",
        expectedBytes = 2,
        parser = ::twoByteUInt
    ),

    TIME_SINCE_CODES_CLEARED(
        code = "4E",
        description = "Time Since Trouble Codes Cleared",
        unit = "min",
        expectedBytes = 2,
        parser = ::twoByteUInt
    ),

    // ========== Maximum Values ==========

    MAX_VALUES_FUEL_AIR_O2_VOLTAGE(
        code = "4F",
        description = "Max Values: Fuel-Air Ratio, O2 Voltage, O2 Current, Intake Pressure",
        unit = "various",
        expectedBytes = 4,
        parser = ::singleByte
    ),

    MAX_MAF_FLOW_RATE(
        code = "50",
        description = "Maximum Air Flow Rate from MAF",
        unit = "g/s",
        expectedBytes = 4,
        parser = { bytes -> bytes[0].toUByte().toDouble() * 10.0 }
    ),

    // ========== Fuel Type / Ethanol ==========

    FUEL_TYPE(
        code = "51",
        description = "Fuel Type",
        unit = "type",
        expectedBytes = 1,
        parser = ::singleByte
    ),

    ETHANOL_FUEL_PERCENT(
        code = "52",
        description = "Ethanol Fuel Percentage",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    ABSOLUTE_EVAP_SYSTEM_VAPOR_PRESSURE(
        code = "53",
        description = "Absolute Evap System Vapor Pressure",
        unit = "kPa",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) / 200.0 }
    ),

    EVAP_SYSTEM_VAPOR_PRESSURE_2(
        code = "54",
        description = "Evap System Vapor Pressure",
        unit = "Pa",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) - 32767.0 }
    ),

    // ========== Secondary O2 Sensor Trims ==========

    SHORT_TERM_O2_TRIM_BANK1_BANK3(
        code = "55",
        description = "Short Term Secondary O2 Sensor Trim - Bank 1 & 3",
        unit = "%",
        expectedBytes = 2,
        parser = ::singleByteSigned128Percent
    ),

    LONG_TERM_O2_TRIM_BANK1_BANK3(
        code = "56",
        description = "Long Term Secondary O2 Sensor Trim - Bank 1 & 3",
        unit = "%",
        expectedBytes = 2,
        parser = ::singleByteSigned128Percent
    ),

    SHORT_TERM_O2_TRIM_BANK2_BANK4(
        code = "57",
        description = "Short Term Secondary O2 Sensor Trim - Bank 2 & 4",
        unit = "%",
        expectedBytes = 2,
        parser = ::singleByteSigned128Percent
    ),

    LONG_TERM_O2_TRIM_BANK2_BANK4(
        code = "58",
        description = "Long Term Secondary O2 Sensor Trim - Bank 2 & 4",
        unit = "%",
        expectedBytes = 2,
        parser = ::singleByteSigned128Percent
    ),

    // ========== Fuel Rail / Pedal / Battery ==========

    FUEL_RAIL_ABSOLUTE_PRESSURE(
        code = "59",
        description = "Fuel Rail Absolute Pressure",
        unit = "kPa",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) * 10.0 }
    ),

    RELATIVE_ACCELERATOR_PEDAL_POSITION(
        code = "5A",
        description = "Relative Accelerator Pedal Position",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    HYBRID_BATTERY_PACK_LIFE(
        code = "5B",
        description = "Hybrid Battery Pack Remaining Life",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    ENGINE_OIL_TEMP(
        code = "5C",
        description = "Engine Oil Temperature",
        unit = "°C",
        expectedBytes = 1,
        parser = ::singleByteOffset40
    ),

    FUEL_INJECTION_TIMING(
        code = "5D",
        description = "Fuel Injection Timing",
        unit = "°",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) / 128.0 - 210.0 }
    ),

    ENGINE_FUEL_RATE(
        code = "5E",
        description = "Engine Fuel Rate",
        unit = "L/h",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) / 20.0 }
    ),

    EMISSION_REQUIREMENTS(
        code = "5F",
        description = "Emission Requirements to Which Vehicle is Designed",
        unit = "type",
        expectedBytes = 1,
        parser = ::singleByte
    ),

    // ========== Engine Torque ==========

    DEMANDED_ENGINE_TORQUE(
        code = "61",
        description = "Driver's Demand Engine Percent Torque",
        unit = "%",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() - 125.0 }
    ),

    ACTUAL_ENGINE_TORQUE(
        code = "62",
        description = "Actual Engine Percent Torque",
        unit = "%",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() - 125.0 }
    ),

    ENGINE_REFERENCE_TORQUE(
        code = "63",
        description = "Engine Reference Torque",
        unit = "Nm",
        expectedBytes = 2,
        parser = ::twoByteUInt
    ),

    ENGINE_PERCENT_TORQUE_DATA(
        code = "64",
        description = "Engine Percent Torque Data",
        unit = "%",
        expectedBytes = 5,
        parser = { bytes -> bytes[0].toUByte().toDouble() - 125.0 }
    ),

    // ========== Auxiliary I/O ==========

    AUXILIARY_IO_SUPPORTED(
        code = "65",
        description = "Auxiliary Input/Output Supported",
        unit = "bitmask",
        expectedBytes = 2,
        parser = ::twoByteUInt
    ),

    // ========== MAF Sensor ==========

    MAF_SENSOR(
        code = "66",
        description = "MAF Sensor",
        unit = "g/s",
        expectedBytes = 5,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) / 32.0 }
    ),

    // ========== Engine Coolant Temperature (Extended) ==========

    ENGINE_COOLANT_TEMP_SENSOR(
        code = "67",
        description = "Engine Coolant Temperature Sensor",
        unit = "°C",
        expectedBytes = 3,
        parser = { bytes -> bytes[1].toUByte().toDouble() - 40.0 }
    ),

    // ========== Intake Air Temperature (Extended) ==========

    INTAKE_AIR_TEMP_SENSOR(
        code = "68",
        description = "Intake Air Temperature Sensor",
        unit = "°C",
        expectedBytes = 7,
        parser = { bytes -> bytes[1].toUByte().toDouble() - 40.0 }
    ),

    // ========== Commanded EGR / Intake Valve ==========

    COMMANDED_EGR_AND_EGR_ERROR(
        code = "69",
        description = "Commanded EGR and EGR Error",
        unit = "%",
        expectedBytes = 7,
        parser = { bytes -> bytes[1].toUByte().toDouble() * 100.0 / 255.0 }
    ),

    COMMANDED_DIESEL_INTAKE_AIR_FLOW(
        code = "6A",
        description = "Commanded Diesel Intake Air Flow Control and Position",
        unit = "%",
        expectedBytes = 5,
        parser = { bytes -> bytes[1].toUByte().toDouble() * 100.0 / 255.0 }
    ),

    EXHAUST_GAS_RECIRCULATION_TEMP(
        code = "6B",
        description = "Exhaust Gas Recirculation Temperature",
        unit = "°C",
        expectedBytes = 5,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) / 10.0 - 40.0 }
    ),

    COMMANDED_THROTTLE_ACTUATOR_2(
        code = "6C",
        description = "Commanded Throttle Actuator Control and Position",
        unit = "%",
        expectedBytes = 5,
        parser = { bytes -> bytes[1].toUByte().toDouble() * 100.0 / 255.0 }
    ),

    // ========== Fuel Pressure Control ==========

    FUEL_PRESSURE_CONTROL(
        code = "6D",
        description = "Fuel Pressure Control System",
        unit = "kPa",
        expectedBytes = 6,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) * 10.0 }
    ),

    INJECTION_PRESSURE_CONTROL(
        code = "6E",
        description = "Injection Pressure Control System",
        unit = "kPa",
        expectedBytes = 5,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) * 10.0 }
    ),

    // ========== Turbocharger ==========

    TURBOCHARGER_COMPRESSOR_INLET_PRESSURE(
        code = "6F",
        description = "Turbocharger Compressor Inlet Pressure",
        unit = "kPa",
        expectedBytes = 3,
        parser = { bytes -> bytes[1].toUByte().toDouble() }
    ),

    BOOST_PRESSURE_CONTROL(
        code = "70",
        description = "Boost Pressure Control",
        unit = "kPa",
        expectedBytes = 9,
        parser = { bytes -> bytes[1].toUByte().toDouble() * 0.03125 }
    ),

    VARIABLE_GEOMETRY_TURBO_CONTROL(
        code = "71",
        description = "Variable Geometry Turbo Control",
        unit = "%",
        expectedBytes = 5,
        parser = { bytes -> bytes[1].toUByte().toDouble() * 100.0 / 255.0 }
    ),

    WASTEGATE_CONTROL(
        code = "72",
        description = "Wastegate Control",
        unit = "%",
        expectedBytes = 5,
        parser = { bytes -> bytes[1].toUByte().toDouble() * 100.0 / 255.0 }
    ),

    EXHAUST_PRESSURE(
        code = "73",
        description = "Exhaust Pressure",
        unit = "kPa",
        expectedBytes = 5,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) * 0.01 }
    ),

    TURBOCHARGER_RPM(
        code = "74",
        description = "Turbocharger RPM",
        unit = "rpm",
        expectedBytes = 5,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()).toDouble() }
    ),

    // ========== Turbocharger Temperature ==========

    TURBOCHARGER_TEMP_A(
        code = "75",
        description = "Turbocharger Temperature A",
        unit = "°C",
        expectedBytes = 7,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) / 10.0 - 40.0 }
    ),

    TURBOCHARGER_TEMP_B(
        code = "76",
        description = "Turbocharger Temperature B",
        unit = "°C",
        expectedBytes = 7,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) / 10.0 - 40.0 }
    ),

    // ========== Charge Air Cooler ==========

    CHARGE_AIR_COOLER_TEMP(
        code = "77",
        description = "Charge Air Cooler Temperature",
        unit = "°C",
        expectedBytes = 5,
        parser = { bytes -> bytes[1].toUByte().toDouble() - 40.0 }
    ),

    // ========== Exhaust Gas Temperature ==========

    EXHAUST_GAS_TEMP_BANK1(
        code = "78",
        description = "Exhaust Gas Temperature Bank 1",
        unit = "°C",
        expectedBytes = 9,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) / 10.0 - 40.0 }
    ),

    EXHAUST_GAS_TEMP_BANK2(
        code = "79",
        description = "Exhaust Gas Temperature Bank 2",
        unit = "°C",
        expectedBytes = 9,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) / 10.0 - 40.0 }
    ),

    // ========== Diesel Particulate Filter ==========

    DPF_DIFFERENTIAL_PRESSURE(
        code = "7A",
        description = "Diesel Particulate Filter Differential Pressure",
        unit = "kPa",
        expectedBytes = 5,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) * 0.01 }
    ),

    DPF_INLET_OUTLET_TEMP(
        code = "7B",
        description = "Diesel Particulate Filter",
        unit = "°C",
        expectedBytes = 5,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) / 10.0 - 40.0 }
    ),

    DPF_TEMP(
        code = "7C",
        description = "Diesel Particulate Filter Temperature",
        unit = "°C",
        expectedBytes = 9,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) / 10.0 - 40.0 }
    ),

    // ========== NOx Sensor ==========

    NOX_NTE_CONTROL_AREA_STATUS(
        code = "7D",
        description = "NOx NTE Control Area Status",
        unit = "status",
        expectedBytes = 1,
        parser = ::singleByte
    ),

    PM_NTE_CONTROL_AREA_STATUS(
        code = "7E",
        description = "PM NTE Control Area Status",
        unit = "status",
        expectedBytes = 1,
        parser = ::singleByte
    ),

    ENGINE_RUN_TIME(
        code = "7F",
        description = "Engine Run Time",
        unit = "sec",
        expectedBytes = 13,
        parser = { bytes ->
            ((bytes[1].toUByte().toLong() shl 24) or (bytes[2].toUByte().toLong() shl 16) or
             (bytes[3].toUByte().toLong() shl 8) or bytes[4].toUByte().toLong()).toDouble()
        }
    ),

    // ========== Hybrid/EV PIDs (81-8E) ==========

    ENGINE_RUN_TIME_AECD_1(
        code = "81",
        description = "Engine Run Time for AECD #1",
        unit = "sec",
        expectedBytes = 21,
        parser = { bytes ->
            ((bytes[1].toUByte().toLong() shl 24) or (bytes[2].toUByte().toLong() shl 16) or
             (bytes[3].toUByte().toLong() shl 8) or bytes[4].toUByte().toLong()).toDouble()
        }
    ),

    ENGINE_RUN_TIME_AECD_2(
        code = "82",
        description = "Engine Run Time for AECD #2",
        unit = "sec",
        expectedBytes = 21,
        parser = { bytes ->
            ((bytes[1].toUByte().toLong() shl 24) or (bytes[2].toUByte().toLong() shl 16) or
             (bytes[3].toUByte().toLong() shl 8) or bytes[4].toUByte().toLong()).toDouble()
        }
    ),

    NOX_SENSOR(
        code = "83",
        description = "NOx Sensor",
        unit = "ppm",
        expectedBytes = 5,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) * 0.1 }
    ),

    MANIFOLD_SURFACE_TEMP(
        code = "84",
        description = "Manifold Surface Temperature",
        unit = "°C",
        expectedBytes = 1,
        parser = ::singleByteOffset40
    ),

    NOX_REAGENT_SYSTEM(
        code = "85",
        description = "NOx Reagent System",
        unit = "%",
        expectedBytes = 10,
        parser = { bytes -> bytes[1].toUByte().toDouble() * 100.0 / 255.0 }
    ),

    PM_SENSOR(
        code = "86",
        description = "Particulate Matter Sensor",
        unit = "mg/m³",
        expectedBytes = 5,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) * 0.0125 }
    ),

    INTAKE_MANIFOLD_ABSOLUTE_PRESSURE(
        code = "87",
        description = "Intake Manifold Absolute Pressure (Extended)",
        unit = "kPa",
        expectedBytes = 5,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) * 0.03125 }
    ),

    // ========== SCR System ==========

    SCR_INDUCE_SYSTEM(
        code = "88",
        description = "SCR Induce System",
        unit = "status",
        expectedBytes = 13,
        parser = { bytes -> bytes[1].toUByte().toDouble() }
    ),

    RUN_TIME_AECD_11_15(
        code = "89",
        description = "Run Time for AECD #11-#15",
        unit = "sec",
        expectedBytes = 41,
        parser = { bytes ->
            ((bytes[1].toUByte().toLong() shl 24) or (bytes[2].toUByte().toLong() shl 16) or
             (bytes[3].toUByte().toLong() shl 8) or bytes[4].toUByte().toLong()).toDouble()
        }
    ),

    RUN_TIME_AECD_16_20(
        code = "8A",
        description = "Run Time for AECD #16-#20",
        unit = "sec",
        expectedBytes = 41,
        parser = { bytes ->
            ((bytes[1].toUByte().toLong() shl 24) or (bytes[2].toUByte().toLong() shl 16) or
             (bytes[3].toUByte().toLong() shl 8) or bytes[4].toUByte().toLong()).toDouble()
        }
    ),

    // ========== Diesel Aftertreatment ==========

    DIESEL_AFTERTREATMENT_STATUS(
        code = "8B",
        description = "Diesel Aftertreatment Status",
        unit = "status",
        expectedBytes = 7,
        parser = { bytes -> bytes[1].toUByte().toDouble() }
    ),

    O2_SENSOR_WIDE_RANGE(
        code = "8C",
        description = "O2 Sensor (Wide Range)",
        unit = "ratio",
        expectedBytes = 17,
        parser = { bytes -> (bytes[2].toUByte().toInt() * 256 + bytes[3].toUByte().toInt()) / 32768.0 }
    ),

    THROTTLE_POSITION_G(
        code = "8D",
        description = "Throttle Position G",
        unit = "%",
        expectedBytes = 1,
        parser = ::singleBytePercent
    ),

    ENGINE_FRICTION_TORQUE(
        code = "8E",
        description = "Engine Friction Percent Torque",
        unit = "%",
        expectedBytes = 1,
        parser = { bytes -> bytes[0].toUByte().toDouble() - 125.0 }
    ),

    // ========== Additional PIDs (90-9F) ==========

    PM_SENSOR_BANK1_BANK2(
        code = "8F",
        description = "PM Sensor Output Bank 1 & 2",
        unit = "mA",
        expectedBytes = 5,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) * 0.01 }
    ),

    WWH_OBD_VEHICLE_INFO(
        code = "90",
        description = "WWH-OBD Vehicle OBD System Information",
        unit = "status",
        expectedBytes = 3,
        parser = { bytes -> bytes[1].toUByte().toDouble() }
    ),

    WWH_OBD_VEHICLE_INFO_2(
        code = "91",
        description = "WWH-OBD Vehicle OBD System Information 2",
        unit = "status",
        expectedBytes = 5,
        parser = { bytes -> bytes[1].toUByte().toDouble() }
    ),

    FUEL_SYSTEM_CONTROL(
        code = "92",
        description = "Fuel System Control",
        unit = "status",
        expectedBytes = 2,
        parser = ::singleByte
    ),

    WWH_OBD_COUNTERS_SUPPORT(
        code = "93",
        description = "WWH-OBD Vehicle OBD Counters Support",
        unit = "bitmask",
        expectedBytes = 3,
        parser = { bytes -> bytes[1].toUByte().toDouble() }
    ),

    NOX_WARNING_INDUCEMENT_SYSTEM(
        code = "94",
        description = "NOx Warning and Inducement System",
        unit = "status",
        expectedBytes = 12,
        parser = { bytes -> bytes[1].toUByte().toDouble() }
    ),

    EXHAUST_GAS_TEMP_SENSOR(
        code = "98",
        description = "Exhaust Gas Temperature Sensor",
        unit = "°C",
        expectedBytes = 9,
        parser = { bytes -> (bytes[1].toUByte().toInt() * 256 + bytes[2].toUByte().toInt()) / 10.0 - 40.0 }
    ),

    HYBRID_EV_VEHICLE_SYSTEM_DATA(
        code = "9A",
        description = "Hybrid/EV Vehicle System Data",
        unit = "status",
        expectedBytes = 6,
        parser = { bytes -> bytes[1].toUByte().toDouble() }
    ),

    DIESEL_EXHAUST_FLUID_SENSOR_DATA(
        code = "9B",
        description = "Diesel Exhaust Fluid Sensor Data",
        unit = "%",
        expectedBytes = 4,
        parser = { bytes -> bytes[1].toUByte().toDouble() * 100.0 / 255.0 }
    ),

    O2_SENSOR_DATA(
        code = "9C",
        description = "O2 Sensor Data",
        unit = "V",
        expectedBytes = 17,
        parser = { bytes -> (bytes[2].toUByte().toInt() * 256 + bytes[3].toUByte().toInt()) * 8.0 / 65536.0 }
    ),

    ENGINE_FUEL_RATE_MULTI(
        code = "9D",
        description = "Engine Fuel Rate Multi",
        unit = "g/s",
        expectedBytes = 4,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) * 0.05 }
    ),

    ENGINE_EXHAUST_FLOW_RATE(
        code = "9E",
        description = "Engine Exhaust Flow Rate",
        unit = "kg/h",
        expectedBytes = 2,
        parser = { bytes -> (bytes[0].toUByte().toInt() * 256 + bytes[1].toUByte().toInt()) * 0.2 }
    ),

    FUEL_SYSTEM_PERCENTAGE_USE(
        code = "9F",
        description = "Fuel System Percentage Use",
        unit = "%",
        expectedBytes = 9,
        parser = { bytes -> bytes[1].toUByte().toDouble() * 100.0 / 255.0 }
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

// ── Parser helpers ────────────────────────────────────────────────────────────
// Referenced by ::function in enum constructors above.

private fun singleByte(bytes: ByteArray) = bytes[0].toUByte().toDouble()

private fun singleByteOffset40(bytes: ByteArray) = bytes[0].toUByte().toDouble() - 40.0

private fun singleBytePercent(bytes: ByteArray) = bytes[0].toUByte().toDouble() * 100.0 / 255.0

private fun singleByteSigned128Percent(bytes: ByteArray) =
    (bytes[0].toUByte().toDouble() - 128.0) * 100.0 / 128.0

private fun twoByteUInt(bytes: ByteArray) =
    ((bytes[0].toUByte().toInt() shl 8) or bytes[1].toUByte().toInt()).toDouble()

private fun fourByteBitmask(bytes: ByteArray) =
    ((bytes[0].toUByte().toLong() shl 24) or (bytes[1].toUByte().toLong() shl 16) or
     (bytes[2].toUByte().toLong() shl 8) or bytes[3].toUByte().toLong()).toDouble()

/** bytes[0] / 200 — used for narrow-band O2 sensor voltage readings. */
private fun o2Voltage(bytes: ByteArray) = bytes[0].toUByte().toDouble() / 200.0

/** (2 / 65536) × (bytes[0..1] as uint16) — used for lambda-ratio and equivalence-ratio readings. */
private fun lambdaRatio(bytes: ByteArray) =
    ((bytes[0].toUByte().toInt() shl 8) or bytes[1].toUByte().toInt()) / 32768.0

/** bytes[2..3] as uint16, scaled to mA — used for wide-range O2 sensor current readings. */
private fun wideRangeCurrent(bytes: ByteArray) =
    ((bytes[2].toUByte().toInt() shl 8) or bytes[3].toUByte().toInt()) / 256.0 - 128.0
