package com.odbplus.app.live

import java.security.MessageDigest

/**
 * Builds a stable SHA-256 fingerprint from vehicle identity fields.
 *
 * Priority / trust rules (higher = more trusted cache):
 *  1. VIN + calibrationId + CVN   → confidence 1.0  (full match)
 *  2. VIN + calibrationId         → confidence 0.85
 *  3. VIN only                    → confidence 0.7  (revalidate on connect)
 *  4. calibrationId + CVN (no VIN)→ confidence 0.5  (medium)
 *  5. calibrationId only          → confidence 0.4
 *  6. nothing useful              → confidence 0.0  (force discovery)
 */
object VehicleProfileFingerprintBuilder {

    data class Fingerprint(
        val hash: String,
        val confidence: Float,
        val missingFields: List<String>
    )

    fun build(
        vin: String?,
        calibrationId: String?,
        cvn: String?,
        ecuFirmwareId: String? = null
    ): Fingerprint {
        val missing = mutableListOf<String>()
        if (vin.isNullOrBlank()) missing += "VIN"
        if (calibrationId.isNullOrBlank()) missing += "CalID"
        if (cvn.isNullOrBlank()) missing += "CVN"

        val confidence: Float = when {
            !vin.isNullOrBlank() && !calibrationId.isNullOrBlank() && !cvn.isNullOrBlank() -> 1.0f
            !vin.isNullOrBlank() && !calibrationId.isNullOrBlank() -> 0.85f
            !vin.isNullOrBlank() -> 0.7f
            !calibrationId.isNullOrBlank() && !cvn.isNullOrBlank() -> 0.5f
            !calibrationId.isNullOrBlank() -> 0.4f
            else -> 0.0f
        }

        val input = listOfNotNull(
            vin?.trim()?.uppercase(),
            calibrationId?.trim()?.uppercase(),
            cvn?.trim()?.uppercase(),
            ecuFirmwareId?.trim()?.uppercase()
        ).joinToString("|")

        val hash = if (input.isEmpty()) {
            "empty_${System.currentTimeMillis()}"
        } else {
            sha256(input)
        }

        return Fingerprint(hash, confidence, missing)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
