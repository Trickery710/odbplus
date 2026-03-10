package com.odbplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Caches individual PID support status for a given vehicle profile.
 *
 * Only Mode 01 PIDs are stored here; Mode 09 (vehicle info) is handled
 * separately via the VehicleEntity fields.
 */
@Entity(
    tableName = "supported_pids",
    foreignKeys = [
        ForeignKey(
            entity = VehicleProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleProfileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("vehicleProfileId"),
        Index(value = ["vehicleProfileId", "mode", "pid"], unique = true)
    ]
)
data class SupportedPidEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleProfileId: Long,
    val mode: Int = 1,
    /** Hex code without mode prefix, e.g. "0C" for RPM. */
    val pid: String,
    val supported: Boolean,
    /** How this entry was populated: "bitmap_discovery" | "direct_probe" | "cached" | "inferred" */
    val source: String = "bitmap_discovery",
    val firstSeen: Long = System.currentTimeMillis(),
    val lastConfirmed: Long = System.currentTimeMillis()
)
