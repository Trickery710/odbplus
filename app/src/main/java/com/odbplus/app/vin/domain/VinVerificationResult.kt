package com.odbplus.app.vin.domain

/**
 * Result of the verification pipeline.
 * Combines local validation signals with remote decode signals to produce
 * a structured confidence score and a set of verification flags.
 */
data class VinVerificationResult(
    val vin: String,
    val flags: Set<VinVerificationFlag>,
    val confidence: Float,
    val status: VerificationStatus,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList()
) {
    fun has(flag: VinVerificationFlag): Boolean = flag in flags
}

/**
 * Individual signals that contribute to overall VIN confidence.
 * Each flag represents a discrete check that passed.
 */
enum class VinVerificationFlag {
    // ── Local format checks ──────────────────────────────────────────────────
    FORMAT_VALID,
    LENGTH_VALID,
    ALLOWED_CHARS_VALID,
    CHECK_DIGIT_VALID,
    WMI_PRESENT,
    /** WMI is in the local known-manufacturer table. */
    WMI_KNOWN,
    MODEL_YEAR_PARSED_LOCAL,

    // ── Remote decode signals ─────────────────────────────────────────────────
    ONLINE_DECODE_SUCCESS,
    /** VIN echoed back by the provider matches our normalized VIN. */
    REMOTE_VIN_MATCH,
    /** WMI prefix of remote make matches our local WMI. */
    REMOTE_WMI_MATCH,
    /** Remote model year matches locally parsed model year. */
    REMOTE_MODEL_YEAR_MATCH,
    /** Provider returned a non-blank manufacturer name. */
    REMOTE_MANUFACTURER_PRESENT,
    /** Make + Model + Year are all non-blank in the remote response. */
    REMOTE_CORE_FIELDS_PRESENT,

    // ── Cache signals ─────────────────────────────────────────────────────────
    CACHE_HIT,
    CACHE_STALE,

    // ── Overall status flags (exactly one will be set) ────────────────────────
    VERIFIED,
    PARTIALLY_VERIFIED,
    SUSPECT,
    FAILED
}
