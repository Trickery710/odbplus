package com.odbplus.app.vin.validation

import com.odbplus.app.vin.domain.VinValidationResult
import com.odbplus.app.vin.domain.VinValidationStatus
import java.util.Calendar

/**
 * Stateless VIN normalization and local validation utility.
 *
 * VIN structure (ISO 3779):
 *   [WMI 1-3][VDS 4-9][VIS 10-17]
 *   Position 9  (index 8) = check digit
 *   Position 10 (index 9) = model year
 *   Position 11 (index 10) = plant code
 *
 * Illegal characters: I, O, Q (to avoid confusion with 1, 0, 0).
 */
object VinValidator {

    private val ALLOWED_CHAR_REGEX = Regex("^[A-HJ-NPR-Z0-9]{17}$")

    /** Transliteration table for check-digit calculation (ISO 3779 Annex B). */
    private val TRANSLITERATION: Map<Char, Int> = buildMap {
        put('A', 1); put('B', 2); put('C', 3); put('D', 4); put('E', 5)
        put('F', 6); put('G', 7); put('H', 8)
        // I = 9 is ILLEGAL in VINs, J follows
        put('J', 1); put('K', 2); put('L', 3); put('M', 4); put('N', 5)
        // O = illegal, P follows
        put('P', 7)
        // Q = illegal, R follows
        put('R', 9); put('S', 2); put('T', 3); put('U', 4); put('V', 5)
        put('W', 6); put('X', 7); put('Y', 8); put('Z', 9)
        for (d in '0'..'9') put(d, d - '0')
    }

    /** Positional weights for check-digit calculation. */
    private val WEIGHTS = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)

    /**
     * First model-year cycle: 1980–2009.
     * Letters A–Y (no I, O, Q) and digits 1–9.
     */
    private val MODEL_YEAR_CYCLE_1: Map<Char, Int> = buildMap {
        val letters = "ABCDEFGHJKLMNPRSTUVWXY"
        val years1 = (1980..2000).toList() // A=1980, B=1981 … Y=2000 (22 letters for Y)
        // Actually: A=1980 to Y=2000 (22 codes), then 1=2001 to 9=2009 (9 codes) = 31 total
        // Letters in order: A B C D E F G H J K L M N P R S T V W X Y (21 non-I/O/Q letters)
        val orderedLetters = "ABCDEFGHJKLMNPRSTVWXY"
        orderedLetters.forEachIndexed { i, c -> put(c, 1980 + i) }
        // 1980+20 = Y=2000, then digits
        for (d in 1..9) put('0' + d, 2001 + d - 1)
    }

    /**
     * Second model-year cycle: 2010–2039.
     * Same character set as cycle 1, shifted by 30 years.
     */
    private val MODEL_YEAR_CYCLE_2: Map<Char, Int> = MODEL_YEAR_CYCLE_1.mapValues { it.value + 30 }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Normalize a raw VIN string:
     * - Trim whitespace
     * - Uppercase
     * - Remove common separators (spaces, dashes)
     */
    fun normalize(raw: String): String =
        raw.trim().uppercase().replace(Regex("[\\s\\-]"), "")

    /**
     * Run full local validation on a raw VIN string.
     * Always returns a result; never throws.
     */
    fun validate(rawVin: String): VinValidationResult {
        val normalized = normalize(rawVin)
        val messages = mutableListOf<String>()

        // 1. Length check
        val isLengthValid = normalized.length == 17
        if (!isLengthValid) {
            messages += "VIN must be exactly 17 characters (got ${normalized.length})"
        }

        // 2. Character set check — scan for illegal chars even if wrong length
        val illegalChars = normalized.filter { it in "IOQ" }
        val nonAlphanumericChars = normalized.filter { !it.isLetterOrDigit() }
        val hasOnlyAllowedChars = if (isLengthValid) {
            ALLOWED_CHAR_REGEX.matches(normalized).also { ok ->
                if (!ok) {
                    if (illegalChars.isNotEmpty())
                        messages += "Illegal VIN characters (I/O/Q not allowed): $illegalChars"
                    if (nonAlphanumericChars.isNotEmpty())
                        messages += "Non-alphanumeric characters found: $nonAlphanumericChars"
                }
            }
        } else {
            // Still check chars for a useful message even if length is wrong
            if (illegalChars.isNotEmpty())
                messages += "Illegal VIN characters (I/O/Q not allowed): $illegalChars"
            illegalChars.isEmpty() && nonAlphanumericChars.isEmpty()
        }

        // 3. Check digit (index 8, position 9) — only meaningful if format is valid
        val isCheckDigitValid = if (isLengthValid && hasOnlyAllowedChars) {
            val computed = computeCheckDigit(normalized)
            val actual = normalized[8]
            (computed == actual).also { ok ->
                if (!ok) messages += "Check digit mismatch: expected '$computed', got '$actual'"
            }
        } else {
            false
        }

        // 4. Parse model year from position 10 (index 9)
        val modelYearCandidate = if (normalized.length >= 10) {
            parseModelYear(normalized[9])
        } else null

        // 5. Extract WMI and plant code
        val wmi = if (normalized.length >= 3) normalized.substring(0, 3) else null
        val plantCode = if (normalized.length >= 11) normalized[10] else null

        val status = when {
            isLengthValid && hasOnlyAllowedChars && isCheckDigitValid -> VinValidationStatus.VALID
            isLengthValid && hasOnlyAllowedChars -> VinValidationStatus.SUSPECT
            else -> VinValidationStatus.INVALID
        }

        return VinValidationResult(
            normalizedVin = normalized,
            isLengthValid = isLengthValid,
            hasOnlyAllowedChars = hasOnlyAllowedChars,
            isCheckDigitValid = isCheckDigitValid,
            modelYearCandidate = modelYearCandidate,
            wmi = wmi,
            plantCode = plantCode,
            validationStatus = status,
            validationMessages = messages
        )
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Compute the ISO 3779 check digit for a 17-character VIN.
     * Returns '?' if any character has no transliteration (shouldn't happen after validation).
     */
    internal fun computeCheckDigit(vin: String): Char {
        var sum = 0
        for (i in 0..16) {
            val value = TRANSLITERATION[vin[i]] ?: return '?'
            sum += value * WEIGHTS[i]
        }
        val remainder = sum % 11
        return if (remainder == 10) 'X' else ('0' + remainder)
    }

    /**
     * Parse the model year from VIN position 10 (index 9).
     *
     * Because the 30-year cycle repeats, a given character could map to two different
     * years (e.g., 'A' = 1980 or 2010). We select the cycle that gives a year closest
     * to the current date without exceeding [currentYear] + 1, falling back to the
     * first cycle if the second cycle year is too far in the future.
     *
     * Returns null if the character is not a valid year code.
     */
    internal fun parseModelYear(code: Char, currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)): Int? {
        val y1 = MODEL_YEAR_CYCLE_1[code]
        val y2 = MODEL_YEAR_CYCLE_2[code]
        val maxAllowed = currentYear + 1 // Next model year is typically valid
        return when {
            y2 != null && y2 <= maxAllowed -> y2   // Prefer second cycle if it fits
            y1 != null && y1 <= maxAllowed -> y1   // Fall back to first cycle
            y1 != null -> y1                        // Both future; use first cycle as best guess
            else -> null
        }
    }
}
