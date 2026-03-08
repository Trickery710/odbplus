package com.odbplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicle_sessions")
data class VehicleSessionEntity(
    @PrimaryKey val sessionId: String,
    val vin: String,
    val timestampStart: Long,
    val timestampEnd: Long? = null,
    val odometer: Int? = null,
    val notes: String? = null
)
