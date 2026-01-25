package com.odbplus.app.parts.data

import kotlinx.serialization.Serializable

/**
 * Represents a recommended part from the AI diagnostics.
 */
@Serializable
data class RecommendedPart(
    val id: String,
    val name: String,
    val partNumber: String? = null,
    val description: String,
    val category: PartCategory,
    val estimatedPrice: String? = null,
    val priority: PartPriority = PartPriority.MEDIUM,
    val relatedDtc: String? = null,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Categories of automotive parts.
 */
@Serializable
enum class PartCategory(val displayName: String) {
    ENGINE("Engine"),
    ELECTRICAL("Electrical"),
    FUEL_SYSTEM("Fuel System"),
    EXHAUST("Exhaust"),
    IGNITION("Ignition"),
    SENSORS("Sensors"),
    FILTERS("Filters"),
    BRAKES("Brakes"),
    SUSPENSION("Suspension"),
    COOLING("Cooling"),
    TRANSMISSION("Transmission"),
    OTHER("Other")
}

/**
 * Priority level for part replacement.
 */
@Serializable
enum class PartPriority(val displayName: String, val colorHex: Long) {
    CRITICAL("Critical", 0xFFD32F2F),  // Red
    HIGH("High", 0xFFF57C00),           // Orange
    MEDIUM("Medium", 0xFFFFA000),       // Amber
    LOW("Low", 0xFF388E3C)              // Green
}

/**
 * Retailer information for part sourcing.
 */
@Serializable
data class PartRetailer(
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val isLocal: Boolean = false
)

/**
 * Link to purchase a part from a specific retailer.
 */
@Serializable
data class PartLink(
    val retailer: PartRetailer,
    val url: String,
    val price: String? = null,
    val inStock: Boolean? = null
)

/**
 * Common automotive retailers.
 */
object CommonRetailers {
    val AUTOZONE = PartRetailer(
        name = "AutoZone",
        url = "https://www.autozone.com",
        isLocal = true
    )

    val OREILLY = PartRetailer(
        name = "O'Reilly Auto Parts",
        url = "https://www.oreillyauto.com",
        isLocal = true
    )

    val ADVANCE_AUTO = PartRetailer(
        name = "Advance Auto Parts",
        url = "https://www.advanceautoparts.com",
        isLocal = true
    )

    val ROCKAUTO = PartRetailer(
        name = "RockAuto",
        url = "https://www.rockauto.com",
        isLocal = false
    )

    val AMAZON = PartRetailer(
        name = "Amazon",
        url = "https://www.amazon.com",
        isLocal = false
    )

    val NAPA = PartRetailer(
        name = "NAPA Auto Parts",
        url = "https://www.napaonline.com",
        isLocal = true
    )

    fun all(): List<PartRetailer> = listOf(
        AUTOZONE, OREILLY, ADVANCE_AUTO, ROCKAUTO, AMAZON, NAPA
    )

    fun local(): List<PartRetailer> = all().filter { it.isLocal }

    fun online(): List<PartRetailer> = all().filter { !it.isLocal }
}

/**
 * Extension to generate search URLs for retailers.
 */
fun PartRetailer.searchUrl(partName: String, partNumber: String? = null): String {
    val query = partNumber ?: partName
    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

    return when (name) {
        "AutoZone" -> "https://www.autozone.com/searchresult?searchText=$encodedQuery"
        "O'Reilly Auto Parts" -> "https://www.oreillyauto.com/shop/b/search?q=$encodedQuery"
        "Advance Auto Parts" -> "https://shop.advanceautoparts.com/web/SearchResults?searchTerm=$encodedQuery"
        "RockAuto" -> "https://www.rockauto.com/en/partsearch/?partnum=$encodedQuery"
        "Amazon" -> "https://www.amazon.com/s?k=$encodedQuery+automotive"
        "NAPA Auto Parts" -> "https://www.napaonline.com/en/search?q=$encodedQuery"
        else -> "$url/search?q=$encodedQuery"
    }
}
