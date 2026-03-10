package com.odbplus.app.settings

/**
 * Tools a user can mark as owned.
 * The [id] is persisted to DataStore — do NOT rename existing entries.
 * Add new tools at the end to keep stored IDs stable.
 */
enum class OwnedTool(
    val id: String,
    val displayName: String,
    val category: ToolCategory
) {
    MULTIMETER(
        id = "multimeter",
        displayName = "Multimeter",
        category = ToolCategory.ELECTRICAL
    ),
    OSCILLOSCOPE(
        id = "oscilloscope",
        displayName = "Oscilloscope / Lab Scope",
        category = ToolCategory.ELECTRICAL
    ),
    BATTERY_TESTER(
        id = "battery_tester",
        displayName = "Battery Load Tester",
        category = ToolCategory.ELECTRICAL
    ),
    COMPRESSION_TESTER(
        id = "compression_tester",
        displayName = "Compression Tester",
        category = ToolCategory.ENGINE
    ),
    CYLINDER_LEAK_DOWN(
        id = "cylinder_leak_down",
        displayName = "Leak-Down Tester",
        category = ToolCategory.ENGINE
    ),
    VACUUM_PUMP(
        id = "vacuum_pump",
        displayName = "Vacuum Pump / Gauge",
        category = ToolCategory.ENGINE
    ),
    FUEL_PRESSURE_GAUGE(
        id = "fuel_pressure_gauge",
        displayName = "Fuel Pressure Gauge",
        category = ToolCategory.FUEL
    ),
    INJECTOR_TESTER(
        id = "injector_tester",
        displayName = "Injector Tester / Pulse Tool",
        category = ToolCategory.FUEL
    ),
    SMOKE_MACHINE(
        id = "smoke_machine",
        displayName = "Smoke Machine / EVAP Tester",
        category = ToolCategory.FUEL
    ),
    TIMING_LIGHT(
        id = "timing_light",
        displayName = "Timing Light",
        category = ToolCategory.ENGINE
    ),
    COOLING_SYSTEM_TESTER(
        id = "cooling_system_tester",
        displayName = "Cooling System Pressure Tester",
        category = ToolCategory.COOLING
    ),
    INFRARED_THERMOMETER(
        id = "infrared_thermometer",
        displayName = "Infrared Thermometer",
        category = ToolCategory.COOLING
    ),
    BORESCOPE(
        id = "borescope",
        displayName = "Borescope / Inspection Camera",
        category = ToolCategory.ENGINE
    ),
    ADVANCED_SCAN_TOOL(
        id = "advanced_scan_tool",
        displayName = "Advanced Scan Tool / Bidirectional",
        category = ToolCategory.DIAGNOSTIC
    );

    enum class ToolCategory(val displayName: String) {
        ELECTRICAL("Electrical"),
        ENGINE("Engine"),
        FUEL("Fuel System"),
        COOLING("Cooling"),
        DIAGNOSTIC("Diagnostic")
    }
}
