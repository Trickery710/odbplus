package com.odbplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.odbplus.app.data.db.entity.SensorLogEntity

@Dao
interface SensorLogDao {
    @Insert
    suspend fun insert(log: SensorLogEntity)

    @Insert
    suspend fun insertAll(logs: List<SensorLogEntity>)

    @Query("SELECT * FROM sensor_logs WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLogsForSession(sessionId: String, limit: Int = 500): List<SensorLogEntity>

    @Query("SELECT * FROM sensor_logs WHERE sessionId = :sessionId AND pid = :pid ORDER BY timestamp ASC")
    suspend fun getLogsForPid(sessionId: String, pid: String): List<SensorLogEntity>

    @Query("DELETE FROM sensor_logs WHERE sessionId IN (SELECT sessionId FROM vehicle_sessions WHERE vin = :vin) AND timestamp < :cutoff")
    suspend fun pruneOldLogs(vin: String, cutoff: Long)
}
