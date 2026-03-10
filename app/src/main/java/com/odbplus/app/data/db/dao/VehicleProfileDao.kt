package com.odbplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.odbplus.app.data.db.entity.VehicleProfileEntity

@Dao
interface VehicleProfileDao {

    @Query("SELECT * FROM vehicle_profiles WHERE fingerprintHash = :hash LIMIT 1")
    suspend fun getByFingerprint(hash: String): VehicleProfileEntity?

    @Query("SELECT * FROM vehicle_profiles WHERE vin = :vin ORDER BY lastSeen DESC LIMIT 1")
    suspend fun getByVin(vin: String): VehicleProfileEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(profile: VehicleProfileEntity): Long

    @Update
    suspend fun update(profile: VehicleProfileEntity)

    @Query("""
        UPDATE vehicle_profiles
        SET lastSeen = :ts, cacheConfidence = :confidence, validationSource = :source
        WHERE id = :id
    """)
    suspend fun updateLastSeen(id: Long, ts: Long, confidence: Float, source: String)

    @Query("""
        UPDATE vehicle_profiles
        SET failedValidationCount = failedValidationCount + 1,
            cacheConfidence = MAX(0.0, cacheConfidence - 0.2)
        WHERE id = :id
    """)
    suspend fun incrementFailedValidation(id: Long)

    @Query("DELETE FROM vehicle_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
