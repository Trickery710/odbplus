package com.odbplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val vin: String,
    val calibrationId: String? = null,
    val calibrationVerificationNumber: String? = null,
    val ecuName: String? = null,
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)
