package com.odbplus.app.tools

import com.odbplus.app.tools.data.Tool
import com.odbplus.app.tools.data.ToolCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolsRepository @Inject constructor() {

    private val _aiRecommendedTools = MutableStateFlow<List<Tool>>(emptyList())
    val aiRecommendedTools: Flow<List<Tool>> = _aiRecommendedTools

    fun getOdbAdapters(): List<Tool> = listOf(
        Tool(
            id = "elm327_wifi",
            name = "ELM327 WiFi OBD2 Scanner",
            description = "Wireless OBD2 adapter compatible with iOS and Android. Good for basic diagnostics.",
            category = ToolCategory.ODB_ADAPTERS,
            priceRange = "$15-$30"
        ),
        Tool(
            id = "elm327_bluetooth",
            name = "ELM327 Bluetooth OBD2 Scanner",
            description = "Bluetooth OBD2 adapter for Android devices. Budget-friendly option.",
            category = ToolCategory.ODB_ADAPTERS,
            priceRange = "$10-$25"
        ),
        Tool(
            id = "obdlink_mx",
            name = "OBDLink MX+",
            description = "Professional-grade Bluetooth OBD2 adapter with fast response times and wide vehicle coverage.",
            category = ToolCategory.ODB_ADAPTERS,
            priceRange = "$100-$120"
        ),
        Tool(
            id = "bluedriver_pro",
            name = "BlueDriver Pro",
            description = "Professional OBD2 scanner with enhanced diagnostics and repair reports.",
            category = ToolCategory.ODB_ADAPTERS,
            priceRange = "$100-$130"
        ),
        Tool(
            id = "veepeak_obdcheck",
            name = "Veepeak OBDCheck BLE+",
            description = "Compact Bluetooth 4.0 OBD2 adapter with low power consumption.",
            category = ToolCategory.ODB_ADAPTERS,
            priceRange = "$20-$35"
        )
    )

    fun getMultimeters(): List<Tool> = listOf(
        Tool(
            id = "fluke_117",
            name = "Fluke 117 Electricians Multimeter",
            description = "Professional automotive multimeter with non-contact voltage detection and True-RMS.",
            category = ToolCategory.MULTIMETERS,
            priceRange = "$150-$200"
        ),
        Tool(
            id = "innova_3340",
            name = "Innova 3340 Automotive Multimeter",
            description = "10 MegOhm digital multimeter designed specifically for automotive diagnostics.",
            category = ToolCategory.MULTIMETERS,
            priceRange = "$50-$70"
        ),
        Tool(
            id = "klein_mm400",
            name = "Klein Tools MM400",
            description = "Auto-ranging digital multimeter with temperature measurement capability.",
            category = ToolCategory.MULTIMETERS,
            priceRange = "$40-$60"
        ),
        Tool(
            id = "astroai_dm6000ar",
            name = "AstroAI Digital Multimeter",
            description = "Budget-friendly multimeter with auto-ranging and backlit display.",
            category = ToolCategory.MULTIMETERS,
            priceRange = "$15-$25"
        )
    )

    fun getTestingTools(): List<Tool> = listOf(
        Tool(
            id = "power_probe_iii",
            name = "Power Probe III Circuit Tester",
            description = "All-in-one circuit tester with powered test light, voltage reading, and short finder.",
            category = ToolCategory.TESTING_TOOLS,
            priceRange = "$100-$150"
        ),
        Tool(
            id = "compression_tester",
            name = "Compression Tester Kit",
            description = "Engine compression testing gauge set for diagnosing cylinder problems.",
            category = ToolCategory.TESTING_TOOLS,
            priceRange = "$25-$50"
        ),
        Tool(
            id = "fuel_pressure_gauge",
            name = "Fuel Pressure Gauge Kit",
            description = "Fuel injection pressure testing kit for diagnosing fuel delivery issues.",
            category = ToolCategory.TESTING_TOOLS,
            priceRange = "$30-$60"
        ),
        Tool(
            id = "vacuum_pump_tester",
            name = "Vacuum Pump Tester",
            description = "Hand-held vacuum pump for testing vacuum-operated components and bleeding brakes.",
            category = ToolCategory.TESTING_TOOLS,
            priceRange = "$30-$50"
        ),
        Tool(
            id = "battery_load_tester",
            name = "Battery Load Tester",
            description = "Tests battery condition and charging system performance under load.",
            category = ToolCategory.TESTING_TOOLS,
            priceRange = "$30-$60"
        ),
        Tool(
            id = "timing_light",
            name = "Timing Light",
            description = "Inductive timing light for checking and adjusting ignition timing.",
            category = ToolCategory.TESTING_TOOLS,
            priceRange = "$25-$50"
        )
    )

    fun updateAiRecommendations(tools: List<Tool>) {
        _aiRecommendedTools.value = tools
    }

    fun addAiRecommendation(tool: Tool) {
        _aiRecommendedTools.value = _aiRecommendedTools.value + tool
    }

    fun clearAiRecommendations() {
        _aiRecommendedTools.value = emptyList()
    }
}
