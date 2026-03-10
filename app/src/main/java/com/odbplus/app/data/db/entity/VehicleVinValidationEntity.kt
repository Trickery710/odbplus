package com.odbplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists the result of local VIN validation for a given VIN.
 * Updated every time a VIN passes through the validator.
 */
@Entity(tableName = "vehicle_vin_validation")
data class VehicleVinValidationEntity(
    @PrimaryKey val vin: String,
    val isLengthValid: Boolean,
    val hasOnlyAllowedChars: Boolean,
    val isCheckDigitValid: Boolean,
    val parsedModelYear: Int?,
    val wmi: String?,
    val plantCode: String?,
    /** "VALID" | "SUSPECT" | "INVALID" */
    val validationStatus: String,
    /** Newline-separated list of validation messages, for diagnostics. */
    val validationSummary: String,
    val checkedAt: Long = System.currentTimeMillis()
)
