package com.odbplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.odbplus.app.data.db.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Upsert
    suspend fun upsert(vehicle: VehicleEntity)

    @Query("SELECT * FROM vehicles WHERE vin = :vin LIMIT 1")
    suspend fun getByVin(vin: String): VehicleEntity?

    @Query("SELECT * FROM vehicles ORDER BY lastSeenTimestamp DESC")
    fun getAllFlow(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY lastSeenTimestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 10): List<VehicleEntity>

    @Delete
    suspend fun delete(vehicle: VehicleEntity)
}
