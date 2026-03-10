package com.odbplus.app.vin.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the NHTSA vPIC VIN decode API.
 *
 * Endpoint: GET https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVin/{VIN}?format=json
 *
 * The API returns a flat list of variable/value pairs rather than a typed object.
 * [NhtsaDecodeResponse.results] is parsed into a Map<String, String?> by [NhtsaMapper].
 */
@Serializable
data class NhtsaDecodeResponse(
    @SerialName("Count") val count: Int = 0,
    @SerialName("Message") val message: String = "",
    @SerialName("SearchCriteria") val searchCriteria: String = "",
    @SerialName("Results") val results: List<NhtsaResultItem> = emptyList()
)

@Serializable
data class NhtsaResultItem(
    @SerialName("Variable") val variable: String = "",
    @SerialName("Value") val value: String? = null,
    @SerialName("ValueId") val valueId: String? = null,
    @SerialName("VariableId") val variableId: Int = 0
)
