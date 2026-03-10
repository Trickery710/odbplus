package com.odbplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.odbplus.app.data.db.entity.VehicleIdentityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleIdentityDao {

    @Upsert
    suspend fun upsert(entity: VehicleIdentityEntity)

    @Query("SELECT * FROM vehicle_identity WHERE vin = :vin LIMIT 1")
    suspend fun getByVin(vin: String): VehicleIdentityEntity?

    @Query("SELECT * FROM vehicle_identity WHERE vin = :vin LIMIT 1")
    fun observeByVin(vin: String): Flow<VehicleIdentityEntity?>

    @Query("SELECT * FROM vehicle_identity ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<VehicleIdentityEntity>>

    @Query("SELECT * FROM vehicle_identity ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 10): List<VehicleIdentityEntity>

    @Query("DELETE FROM vehicle_identity WHERE vin = :vin")
    suspend fun deleteByVin(vin: String)

    @Query("SELECT COUNT(*) FROM vehicle_identity WHERE vin = :vin")
    suspend fun existsCount(vin: String): Int

    /** Return VINs that have not been decoded or have low confidence scores. */
    @Query(
        "SELECT vin FROM vehicle_identity WHERE confidenceScore < :minConfidence ORDER BY updatedAt ASC"
    )
    suspend fun getVinsWithLowConfidence(minConfidence: Float = 0.70f): List<String>
}
