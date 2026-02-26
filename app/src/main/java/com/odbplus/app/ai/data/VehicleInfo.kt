package com.odbplus.app.ai.data

import kotlinx.serialization.Serializable

/**
 * Comprehensive vehicle information retrieved from OBD-II Mode 09.
 */
@Serializable
data class VehicleInfo(
    val vin: String,
    val calibrationId: String? = null,
    val calibrationVerificationNumber: String? = null,
    val ecuName: String? = null,
    val sparkIgnitionMonitoringSupported: String? = null,
    val compressionIgnitionMonitoringSupported: String? = null,
    val inUsePerformanceTracking: String? = null,
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Format vehicle info for AI context.
     */
    fun formatForAi(): String {
        val sb = StringBuilder()
        sb.appendLine("## Vehicle Information")
        sb.appendLine("- VIN: $vin")

        // Decode VIN info
        val vinInfo = decodeVin()
        if (vinInfo.isNotEmpty()) {
            vinInfo.forEach { (key, value) ->
                sb.appendLine("- $key: $value")
            }
        }

        calibrationId?.let { sb.appendLine("- Calibration ID: $it") }
        calibrationVerificationNumber?.let { sb.appendLine("- CVN: $it") }
        ecuName?.let { sb.appendLine("- ECU: $it") }

        return sb.toString()
    }

    /**
     * Short display name for UI: "Year Manufacturer" or VIN if undecodeable.
     */
    val displayName: String get() {
        if (vin.length != 17) return vin.ifBlank { "Unknown Vehicle" }
        val decoded = decodeVin()
        val year = decoded["Model Year"] ?: ""
        val manufacturer = decoded["Manufacturer"] ?: ""
        return listOf(year, manufacturer).filter { it.isNotBlank() }.joinToString(" ").ifBlank { vin }
    }

    /**
     * Basic VIN decoding for common manufacturers.
     */
    internal fun decodeVin(): Map<String, String> {
        if (vin.length != 17) return emptyMap()

        val info = mutableMapOf<String, String>()

        // World Manufacturer Identifier (first 3 characters)
        val wmi = vin.substring(0, 3)
        val manufacturer = when {
            wmi.startsWith("1G") || wmi.startsWith("2G") || wmi.startsWith("3G") -> "General Motors"
            wmi.startsWith("1F") || wmi.startsWith("2F") || wmi.startsWith("3F") -> "Ford"
            wmi.startsWith("1C") || wmi.startsWith("2C") || wmi.startsWith("3C") -> "Chrysler"
            wmi.startsWith("1H") || wmi.startsWith("2H") -> "Honda"
            wmi.startsWith("1N") || wmi.startsWith("5N") -> "Nissan"
            wmi.startsWith("JT") -> "Toyota"
            wmi.startsWith("JH") -> "Honda (Japan)"
            wmi.startsWith("JN") -> "Nissan (Japan)"
            wmi.startsWith("JM") -> "Mazda"
            wmi.startsWith("KM") || wmi.startsWith("KN") -> "Hyundai/Kia"
            wmi.startsWith("WA") || wmi.startsWith("WV") || wmi.startsWith("WF") -> "Volkswagen/Audi"
            wmi.startsWith("WB") -> "BMW"
            wmi.startsWith("WD") || wmi.startsWith("WDB") -> "Mercedes-Benz"
            wmi.startsWith("ZF") || wmi.startsWith("ZA") -> "Fiat/Alfa Romeo"
            wmi.startsWith("5Y") || wmi.startsWith("4T") -> "Toyota (USA)"
            wmi.startsWith("5T") -> "Toyota Trucks"
            wmi.startsWith("19") || wmi.startsWith("2T") -> "Toyota/Lexus"
            else -> null
        }
        manufacturer?.let { info["Manufacturer"] = it }

        // Model year (10th character)
        val yearChar = vin[9]
        val year = when (yearChar) {
            'A' -> "2010"
            'B' -> "2011"
            'C' -> "2012"
            'D' -> "2013"
            'E' -> "2014"
            'F' -> "2015"
            'G' -> "2016"
            'H' -> "2017"
            'J' -> "2018"
            'K' -> "2019"
            'L' -> "2020"
            'M' -> "2021"
            'N' -> "2022"
            'P' -> "2023"
            'R' -> "2024"
            'S' -> "2025"
            'T' -> "2026"
            '1' -> "2001"
            '2' -> "2002"
            '3' -> "2003"
            '4' -> "2004"
            '5' -> "2005"
            '6' -> "2006"
            '7' -> "2007"
            '8' -> "2008"
            '9' -> "2009"
            else -> null
        }
        year?.let { info["Model Year"] = it }

        // Assembly plant (11th character)
        info["Plant Code"] = vin[10].toString()

        // Serial number (last 6 digits)
        info["Serial Number"] = vin.substring(11)

        return info
    }

    companion object {
        fun empty() = VehicleInfo(vin = "")
    }
}

/**
 * Storage container for multiple vehicles indexed by VIN.
 */
@Serializable
data class VehicleDatabase(
    val vehicles: Map<String, VehicleInfo> = emptyMap(),
    val lastActiveVin: String? = null
) {
    fun getVehicle(vin: String): VehicleInfo? = vehicles[vin]

    fun addOrUpdateVehicle(info: VehicleInfo): VehicleDatabase {
        val existing = vehicles[info.vin]
        val updated = if (existing != null) {
            info.copy(
                firstSeenTimestamp = existing.firstSeenTimestamp,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        } else {
            info
        }
        return copy(
            vehicles = vehicles + (info.vin to updated),
            lastActiveVin = info.vin
        )
    }

    fun isFirstTimeVehicle(vin: String): Boolean = !vehicles.containsKey(vin)
}
