package com.odbplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.odbplus.app.data.db.entity.VehicleVinCachePolicyEntity

@Dao
interface VinCachePolicyDao {

    @Upsert
    suspend fun upsert(entity: VehicleVinCachePolicyEntity)

    @Query("SELECT * FROM vehicle_vin_cache_policy WHERE vin = :vin LIMIT 1")
    suspend fun getByVin(vin: String): VehicleVinCachePolicyEntity?

    @Query("UPDATE vehicle_vin_cache_policy SET isStale = 1, shouldRefresh = 1 WHERE vin = :vin")
    suspend fun markStale(vin: String)

    @Query("DELETE FROM vehicle_vin_cache_policy WHERE vin = :vin")
    suspend fun deleteByVin(vin: String)

    @Query("SELECT vin FROM vehicle_vin_cache_policy WHERE cacheExpiresAt < :nowMs")
    suspend fun getExpiredVins(nowMs: Long = System.currentTimeMillis()): List<String>
}
