package com.odbplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks decode attempt history and cache freshness state for each VIN.
 * Used by [VinCachePolicy] to decide whether a fresh network call is needed.
 */
@Entity(tableName = "vehicle_vin_cache_policy")
data class VehicleVinCachePolicyEntity(
    @PrimaryKey val vin: String,
    val lastAttemptAt: Long,
    val lastSuccessAt: Long?,
    val lastFailureAt: Long?,
    val retryCount: Int,
    val cacheExpiresAt: Long,
    val isStale: Boolean,
    val shouldRefresh: Boolean
)
