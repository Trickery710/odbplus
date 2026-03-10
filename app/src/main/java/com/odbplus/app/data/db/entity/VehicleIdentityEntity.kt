package com.odbplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores remotely decoded VIN identity data for a vehicle.
 * One record per unique VIN. Updated on each successful online decode.
 */
@Entity(
    tableName = "vehicle_identity",
    indices = [
        Index("make"),
        Index("modelYear")
    ]
)
data class VehicleIdentityEntity(
    @PrimaryKey val vin: String,
    val normalizedVin: String,
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
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** "NHTSA" | "CACHE" | "LOCAL_ONLY" */
    val source: String,
    /** 0.0–1.0 */
    val confidenceScore: Float,
    /** "VERIFIED" | "MOSTLY_VERIFIED" | "PARTIAL" | "SUSPECT" | "FAILED" */
    val verificationStatus: String
)
