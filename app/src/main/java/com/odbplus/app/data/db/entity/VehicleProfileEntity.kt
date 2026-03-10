package com.odbplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores the vehicle identity fingerprint used to look up cached supported-PID sets.
 *
 * Cache confidence:
 *   1.0 = VIN + firmware match (fully trusted)
 *   0.7 = VIN match, firmware mismatch (revalidate)
 *   0.5 = no VIN, fingerprint only (medium trust)
 *   0.0 = no match (full discovery required)
 */
@Entity(
    tableName = "vehicle_profiles",
    indices = [
        Index("vin"),
        Index(value = ["fingerprintHash"], unique = true)
    ]
)
data class VehicleProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vin: String? = null,
    val protocol: String? = null,
    val ecuFirmwareId: String? = null,
    val calibrationId: String? = null,
    val cvn: String? = null,
    /** SHA-256 of (vin + calibrationId + cvn + ecuFirmwareId), null-safe. */
    val fingerprintHash: String,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val lastValidated: Long? = null,
    /** How the last cache load was validated: "discovery", "validated_cache", "cache_hit" */
    val validationSource: String = "discovery",
    /** 0.0–1.0 trust level of the cached PID set. */
    val cacheConfidence: Float = 1.0f,
    val failedValidationCount: Int = 0
)
