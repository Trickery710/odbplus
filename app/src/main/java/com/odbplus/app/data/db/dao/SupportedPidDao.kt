package com.odbplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.odbplus.app.data.db.entity.SupportedPidEntity

@Dao
interface SupportedPidDao {

    @Query("SELECT * FROM supported_pids WHERE vehicleProfileId = :profileId AND supported = 1")
    suspend fun getSupportedPids(profileId: Long): List<SupportedPidEntity>

    @Query("SELECT * FROM supported_pids WHERE vehicleProfileId = :profileId")
    suspend fun getAllPids(profileId: Long): List<SupportedPidEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pids: List<SupportedPidEntity>)

    @Query("DELETE FROM supported_pids WHERE vehicleProfileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)

    @Query("SELECT COUNT(*) FROM supported_pids WHERE vehicleProfileId = :profileId AND supported = 1")
    suspend fun countSupported(profileId: Long): Int

    @Query("""
        UPDATE supported_pids
        SET lastConfirmed = :ts
        WHERE vehicleProfileId = :profileId AND pid IN (:pids)
    """)
    suspend fun updateLastConfirmed(profileId: Long, pids: List<String>, ts: Long)
}
