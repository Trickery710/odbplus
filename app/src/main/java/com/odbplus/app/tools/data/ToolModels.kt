package com.odbplus.app.tools.data

import kotlinx.serialization.Serializable

@Serializable
data class Tool(
    val id: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val priceRange: String? = null,
    val isAiRecommended: Boolean = false,
    val aiRecommendationReason: String? = null
)

@Serializable
enum class ToolCategory(val displayName: String) {
    ODB_ADAPTERS("OBD Adapters"),
    MULTIMETERS("Multimeters"),
    TESTING_TOOLS("Testing Tools"),
    AI_RECOMMENDED("AI Recommended")
}

@Serializable
data class ToolRetailer(
    val name: String,
    val url: String,
    val isLocal: Boolean = false
)

object ToolRetailers {
    // Local stores
    val AUTOZONE = ToolRetailer("AutoZone", "https://www.autozone.com", isLocal = true)
    val OREILLY = ToolRetailer("O'Reilly Auto Parts", "https://www.oreillyauto.com", isLocal = true)
    val ADVANCE_AUTO = ToolRetailer("Advance Auto Parts", "https://www.advanceautoparts.com", isLocal = true)
    val HARBOR_FREIGHT = ToolRetailer("Harbor Freight", "https://www.harborfreight.com", isLocal = true)
    val HOME_DEPOT = ToolRetailer("Home Depot", "https://www.homedepot.com", isLocal = true)
    val NAPA = ToolRetailer("NAPA Auto Parts", "https://www.napaonline.com", isLocal = true)

    // Online stores
    val AMAZON = ToolRetailer("Amazon", "https://www.amazon.com", isLocal = false)
    val EBAY = ToolRetailer("eBay", "https://www.ebay.com", isLocal = false)
    val WALMART = ToolRetailer("Walmart", "https://www.walmart.com", isLocal = false)
    val ROCKAUTO = ToolRetailer("RockAuto", "https://www.rockauto.com", isLocal = false)

    fun localStores(): List<ToolRetailer> = listOf(AUTOZONE, OREILLY, ADVANCE_AUTO, HARBOR_FREIGHT, HOME_DEPOT, NAPA)
    fun onlineStores(): List<ToolRetailer> = listOf(AMAZON, EBAY, WALMART, ROCKAUTO)
    fun all(): List<ToolRetailer> = localStores() + onlineStores()
}

fun ToolRetailer.searchUrl(query: String): String {
    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
    return when (name) {
        "AutoZone" -> "https://www.autozone.com/searchresult?searchText=$encodedQuery"
        "O'Reilly Auto Parts" -> "https://www.oreillyauto.com/shop/b/search?q=$encodedQuery"
        "Advance Auto Parts" -> "https://shop.advanceautoparts.com/web/SearchResults?searchTerm=$encodedQuery"
        "Harbor Freight" -> "https://www.harborfreight.com/catalogsearch/result?q=$encodedQuery"
        "Home Depot" -> "https://www.homedepot.com/s/$encodedQuery"
        "NAPA Auto Parts" -> "https://www.napaonline.com/en/search?q=$encodedQuery"
        "Amazon" -> "https://www.amazon.com/s?k=$encodedQuery"
        "eBay" -> "https://www.ebay.com/sch/i.html?_nkw=$encodedQuery"
        "Walmart" -> "https://www.walmart.com/search?q=$encodedQuery"
        "RockAuto" -> "https://www.rockauto.com/en/partsearch/?partnum=$encodedQuery"
        else -> "$url/search?q=$encodedQuery"
    }
}
