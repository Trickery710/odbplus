package com.odbplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the raw JSON response from an online VIN decoder provider.
 * Kept separately from [VehicleIdentityEntity] so future field extractions can be
 * done without re-querying the network.
 */
@Entity(tableName = "vehicle_vin_raw_decode")
data class VehicleVinRawDecodeEntity(
    @PrimaryKey val vin: String,
    /** e.g. "NHTSA_VPIC" */
    val providerName: String,
    /** API version string if available, e.g. "1.0" */
    val providerVersion: String?,
    /** Full raw JSON response body from the provider. */
    val rawJson: String,
    val fetchedAt: Long = System.currentTimeMillis(),
    val httpStatus: Int,
    val wasSuccessful: Boolean
)
