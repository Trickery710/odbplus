package com.odbplus.app.vin.verification

import com.odbplus.app.vin.domain.DecodedVin
import com.odbplus.app.vin.domain.VerificationStatus
import com.odbplus.app.vin.domain.VinValidationResult
import com.odbplus.app.vin.domain.VinValidationStatus
import com.odbplus.app.vin.domain.VinVerificationFlag
import com.odbplus.app.vin.domain.VinVerificationFlag.*
import com.odbplus.app.vin.domain.VinVerificationResult

/**
 * Combines local validation signals and remote decode signals into a confidence score
 * and a structured set of [VinVerificationFlag]s.
 *
 * All scoring is additive; each flag carries a weight defined in [FLAG_WEIGHTS].
 * The final score is clamped to [0.0, 1.0].
 */
object VinVerificationEngine {

    /**
     * Weight contributed to confidence when a flag is present.
     * Weights sum to 1.0 if all positive flags are set.
     */
    private val FLAG_WEIGHTS: Map<VinVerificationFlag, Float> = mapOf(
        LENGTH_VALID            to 0.05f,
        ALLOWED_CHARS_VALID     to 0.05f,
        CHECK_DIGIT_VALID       to 0.10f,
        WMI_PRESENT             to 0.03f,
        WMI_KNOWN               to 0.04f,
        MODEL_YEAR_PARSED_LOCAL to 0.03f,
        ONLINE_DECODE_SUCCESS   to 0.15f,
        REMOTE_VIN_MATCH        to 0.15f,
        REMOTE_WMI_MATCH        to 0.08f,
        REMOTE_MODEL_YEAR_MATCH to 0.08f,
        REMOTE_MANUFACTURER_PRESENT to 0.08f,
        REMOTE_CORE_FIELDS_PRESENT  to 0.16f,
        // FORMAT_VALID is a composite — not counted separately
    )

    // ── Well-known WMI prefixes for manufacturer cross-check ─────────────────
    // Extend this map as needed; it does not need to be exhaustive.
    private val WMI_MAKE_MAP: Map<String, List<String>> = mapOf(
        "1HG" to listOf("honda"),
        "1G1" to listOf("chevrolet"),
        "1G6" to listOf("cadillac"),
        "1FA" to listOf("ford"),
        "1FT" to listOf("ford"),
        "1FM" to listOf("ford"),
        "1C4" to listOf("chrysler", "dodge", "ram", "jeep"),
        "1C6" to listOf("chrysler", "dodge", "ram", "jeep"),
        "1D7" to listOf("dodge", "ram"),
        "2HG" to listOf("honda"),
        "2T1" to listOf("toyota"),
        "3FA" to listOf("ford"),
        "3VW" to listOf("volkswagen"),
        "4T1" to listOf("toyota"),
        "4T4" to listOf("toyota"),
        "5YJ" to listOf("tesla"),
        "JN1" to listOf("nissan"),
        "JHM" to listOf("honda"),
        "JT2" to listOf("toyota"),
        "JTD" to listOf("toyota"),
        "KNA" to listOf("kia"),
        "KNB" to listOf("kia"),
        "KMH" to listOf("hyundai"),
        "SAL" to listOf("land rover"),
        "SCA" to listOf("rolls-royce"),
        "WBA" to listOf("bmw"),
        "WBY" to listOf("bmw"),
        "WDB" to listOf("mercedes-benz", "mercedes"),
        "WDD" to listOf("mercedes-benz", "mercedes"),
        "WP0" to listOf("porsche"),
        "WAU" to listOf("audi"),
        "VF1" to listOf("renault"),
        "YV1" to listOf("volvo"),
        "ZFF" to listOf("ferrari"),
        "ZAR" to listOf("alfa romeo"),
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run the verification pipeline after local-only validation (no network data).
     * Used to produce a quick preliminary result before the online decode completes.
     */
    fun verifyLocalOnly(validation: VinValidationResult): VinVerificationResult {
        val flags = mutableSetOf<VinVerificationFlag>()
        val warnings = mutableListOf<String>()

        if (validation.isLengthValid) flags += LENGTH_VALID
        if (validation.hasOnlyAllowedChars) flags += ALLOWED_CHARS_VALID
        if (validation.isLengthValid && validation.hasOnlyAllowedChars) flags += FORMAT_VALID
        if (validation.isCheckDigitValid) flags += CHECK_DIGIT_VALID
        if (validation.wmi != null) flags += WMI_PRESENT
        if (validation.modelYearCandidate != null) flags += MODEL_YEAR_PARSED_LOCAL

        if (!validation.isCheckDigitValid && validation.validationStatus == VinValidationStatus.SUSPECT) {
            warnings += "Check digit does not match; VIN may be incorrect or a test VIN"
        }

        val confidence = computeConfidence(flags)
        val status = VerificationStatus.from(confidence)
        flags += statusFlag(status)

        return VinVerificationResult(
            vin = validation.normalizedVin,
            flags = flags,
            confidence = confidence,
            status = status,
            warnings = warnings
        )
    }

    /**
     * Run the full verification pipeline after an online decode completes.
     *
     * @param validation  Result of local VIN validation.
     * @param decoded     Domain model returned by the remote decoder.
     * @param cacheHit    True if the decoded result came from cache (not a live network call).
     * @param cacheStale  True if the cache record is past its freshness window.
     */
    fun verifyWithRemote(
        validation: VinValidationResult,
        decoded: DecodedVin,
        cacheHit: Boolean = false,
        cacheStale: Boolean = false
    ): VinVerificationResult {
        val flags = mutableSetOf<VinVerificationFlag>()
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // ── Local format flags ─────────────────────────────────────────────────
        if (validation.isLengthValid) flags += LENGTH_VALID
        if (validation.hasOnlyAllowedChars) flags += ALLOWED_CHARS_VALID
        if (validation.isLengthValid && validation.hasOnlyAllowedChars) flags += FORMAT_VALID
        if (validation.isCheckDigitValid) flags += CHECK_DIGIT_VALID
        else if (validation.hasOnlyAllowedChars) {
            warnings += "Local check digit invalid — trusting remote decode with caution"
        }
        if (validation.wmi != null) flags += WMI_PRESENT
        if (validation.modelYearCandidate != null) flags += MODEL_YEAR_PARSED_LOCAL

        // ── Remote decode flags ────────────────────────────────────────────────
        flags += ONLINE_DECODE_SUCCESS

        // VIN echo match
        if (decoded.vin.equals(validation.normalizedVin, ignoreCase = true)) {
            flags += REMOTE_VIN_MATCH
        } else {
            errors += "Remote VIN '${decoded.vin}' does not match submitted VIN '${validation.normalizedVin}'"
        }

        // Core fields present
        if (decoded.hasCoreFields) {
            flags += REMOTE_CORE_FIELDS_PRESENT
        } else {
            warnings += "Remote decode is missing core fields (make/model/year)"
        }

        // Manufacturer present
        if (!decoded.manufacturer.isNullOrBlank()) {
            flags += REMOTE_MANUFACTURER_PRESENT
        }

        // Model year cross-check
        val localYear = validation.modelYearCandidate
        val remoteYear = decoded.modelYear
        if (localYear != null && remoteYear != null) {
            if (localYear == remoteYear) {
                flags += REMOTE_MODEL_YEAR_MATCH
            } else {
                warnings += "Model year mismatch: local parsed $localYear, remote returned $remoteYear"
            }
        } else if (remoteYear != null) {
            // No local year to compare — just accept remote
            flags += REMOTE_MODEL_YEAR_MATCH
        }

        // WMI / make cross-check
        val wmi = validation.wmi
        if (wmi != null) {
            val knownMakes = WMI_MAKE_MAP[wmi]
            if (knownMakes != null) {
                flags += WMI_KNOWN
                val remoteMake = decoded.make?.lowercase()?.trim() ?: ""
                val remoteManuf = decoded.manufacturer?.lowercase()?.trim() ?: ""
                val matched = knownMakes.any { known ->
                    remoteMake.contains(known) || remoteManuf.contains(known)
                }
                if (matched) {
                    flags += REMOTE_WMI_MATCH
                } else if (remoteMake.isNotBlank()) {
                    warnings += "WMI '$wmi' expected one of ${knownMakes}, remote returned make='${decoded.make}'"
                }
            }
            // If WMI is not in our local table, skip the match check (don't penalize)
        }

        // Cache signals
        if (cacheHit) flags += CACHE_HIT
        if (cacheStale) flags += CACHE_STALE

        // ── Suspect condition: check digit failed but remote succeeded ─────────
        val isSuspectCheckDigit = !validation.isCheckDigitValid && ONLINE_DECODE_SUCCESS in flags
        if (isSuspectCheckDigit) {
            warnings += "VIN passed remote decode despite invalid check digit — flagged SUSPECT"
        }

        val confidence = computeConfidence(flags, penaliseSuspectCheckDigit = isSuspectCheckDigit)
        val status = VerificationStatus.from(confidence)
        flags += statusFlag(status)

        return VinVerificationResult(
            vin = validation.normalizedVin,
            flags = flags,
            confidence = confidence,
            status = status,
            warnings = warnings,
            errors = errors
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun computeConfidence(
        flags: Set<VinVerificationFlag>,
        penaliseSuspectCheckDigit: Boolean = false
    ): Float {
        var score = FLAG_WEIGHTS.entries.sumOf { (flag, weight) ->
            if (flag in flags) weight.toDouble() else 0.0
        }.toFloat()

        // Apply penalty when remote succeeded despite bad check digit
        if (penaliseSuspectCheckDigit) score *= 0.75f

        return score.coerceIn(0f, 1f)
    }

    private fun statusFlag(status: VerificationStatus): VinVerificationFlag = when (status) {
        VerificationStatus.VERIFIED -> VERIFIED
        VerificationStatus.MOSTLY_VERIFIED -> PARTIALLY_VERIFIED
        VerificationStatus.PARTIAL -> PARTIALLY_VERIFIED
        VerificationStatus.SUSPECT -> SUSPECT
        VerificationStatus.FAILED -> FAILED
    }
}
