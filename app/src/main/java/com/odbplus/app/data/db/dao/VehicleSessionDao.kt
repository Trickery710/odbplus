package com.odbplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.odbplus.app.data.db.entity.VehicleSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleSessionDao {
    @Insert
    suspend fun insert(session: VehicleSessionEntity)

    @Query("UPDATE vehicle_sessions SET timestampEnd = :endTime WHERE sessionId = :id")
    suspend fun closeSession(id: String, endTime: Long)

    @Query("SELECT * FROM vehicle_sessions WHERE vin = :vin ORDER BY timestampStart DESC")
    fun getSessionsForVin(vin: String): Flow<List<VehicleSessionEntity>>

    @Query("SELECT * FROM vehicle_sessions WHERE vin = :vin ORDER BY timestampStart DESC LIMIT :limit")
    suspend fun getRecentSessions(vin: String, limit: Int = 10): List<VehicleSessionEntity>

    @Query("SELECT * FROM vehicle_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getBySessionId(sessionId: String): VehicleSessionEntity?
}
