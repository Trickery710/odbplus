package com.odbplus.app.vin.domain

/**
 * Structured result of local VIN normalization and validation.
 * Returned before any network call is made.
 */
data class VinValidationResult(
    val normalizedVin: String,
    val isLengthValid: Boolean,
    val hasOnlyAllowedChars: Boolean,
    val isCheckDigitValid: Boolean,
    /** Model year derived from position 10 (index 9) of the VIN, if parseable. */
    val modelYearCandidate: Int?,
    /** World Manufacturer Identifier: first 3 characters. */
    val wmi: String?,
    /** Plant code: character at position 11 (index 10). */
    val plantCode: Char?,
    val validationStatus: VinValidationStatus,
    val validationMessages: List<String>
) {
    val isValid: Boolean get() = validationStatus == VinValidationStatus.VALID
    val isSuspect: Boolean get() = validationStatus == VinValidationStatus.SUSPECT
}

enum class VinValidationStatus {
    /** Passes all local checks including check digit. */
    VALID,
    /** Format and character set valid, but check digit is wrong. May still decode successfully. */
    SUSPECT,
    /** Fails basic format or character set requirements. Cannot be sent to a decoder. */
    INVALID
}
