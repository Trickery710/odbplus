package com.odbplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "dtc_logs", indices = [Index("vin")])
data class DtcLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vin: String,
    val dtcCode: String,
    val description: String? = null,
    val module: String? = null,
    val sessionId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
