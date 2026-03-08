package com.odbplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.odbplus.app.data.db.entity.DtcLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DtcLogDao {
    @Insert
    suspend fun insert(dtc: DtcLogEntity)

    @Query("SELECT * FROM dtc_logs WHERE vin = :vin ORDER BY timestamp DESC")
    fun getDtcsForVin(vin: String): Flow<List<DtcLogEntity>>

    @Query("SELECT COUNT(*) FROM dtc_logs WHERE vin = :vin AND dtcCode = :code AND timestamp > :since")
    suspend fun countRecent(vin: String, code: String, since: Long): Int
}
