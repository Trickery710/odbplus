package com.odbplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "freeze_frames", indices = [Index("vin")])
data class FreezeFrameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vin: String,
    val dtcCode: String,
    val rpm: Double? = null,
    val speed: Double? = null,
    val coolantTemp: Double? = null,
    val fuelTrimShortBank1: Double? = null,
    val throttle: Double? = null,
    val engineLoad: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)
