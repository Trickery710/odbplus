package com.odbplus.app.vin.domain

/**
 * Domain model for a fully (or partially) decoded VIN.
 * Fields may be null if the provider did not return them.
 */
data class DecodedVin(
    val vin: String,
    val make: String?,
    val model: String?,
    val modelYear: Int?,
    val trim: String?,
    val series: String?,
    val manufacturer: String?,
    val vehicleType: String?,
    val bodyClass: String?,
    val engineModel: String?,
    val engineCylinders: Int?,
    val displacementL: Double?,
    val fuelTypePrimary: String?,
    val fuelTypeSecondary: String?,
    val driveType: String?,
    val transmissionStyle: String?,
    val plantCountry: String?,
    val plantCompany: String?,
    val plantCity: String?,
    val plantState: String?,
    val gvwrClass: String?,
    val brakeSystemType: String?,
    val airBagLocations: String?,
    val source: DecodeSource,
    val decodeTimestamp: Long,
    val confidence: Float,
    val verificationStatus: VerificationStatus,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList()
) {
    /** Returns a compact display string like "2018 Honda Civic Sport". */
    fun displayLabel(): String = buildString {
        modelYear?.let { append("$it ") }
        make?.let { append("$it ") }
        model?.let { append("$it ") }
        trim?.let { append(it) }
    }.trim().ifBlank { vin }

    /** True if core identification fields are present. */
    val hasCoreFields: Boolean
        get() = make != null && model != null && modelYear != null
}

enum class DecodeSource {
    /** Fetched live from NHTSA or another online provider. */
    NHTSA,
    /** Returned from the local Room cache without a network call. */
    CACHE,
    /** Only local validation was run; no online decode available. */
    LOCAL_ONLY
}

enum class VerificationStatus {
    /** Confidence ≥ 0.90 — all key checks passed. */
    VERIFIED,
    /** Confidence 0.70–0.89 — most checks passed. */
    MOSTLY_VERIFIED,
    /** Confidence 0.40–0.69 — some fields confirmed, proceed with caution. */
    PARTIAL,
    /** Confidence 0.20–0.39 — contradictions detected or data very sparse. */
    SUSPECT,
    /** Confidence < 0.20 — decoding failed or validation contradicts remote data. */
    FAILED;

    companion object {
        fun from(confidence: Float): VerificationStatus = when {
            confidence >= 0.90f -> VERIFIED
            confidence >= 0.70f -> MOSTLY_VERIFIED
            confidence >= 0.40f -> PARTIAL
            confidence >= 0.20f -> SUSPECT
            else -> FAILED
        }
    }
}
