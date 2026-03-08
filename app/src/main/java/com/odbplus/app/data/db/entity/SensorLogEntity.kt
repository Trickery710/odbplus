package com.odbplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sensor_logs",
    indices = [Index("sessionId"), Index("pid")],
    foreignKeys = [ForeignKey(
        entity = VehicleSessionEntity::class,
        parentColumns = ["sessionId"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class SensorLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val pid: String,
    val value: Double,
    val timestamp: Long
)
