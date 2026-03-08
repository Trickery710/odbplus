package com.odbplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.odbplus.app.data.db.entity.FreezeFrameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FreezeFrameDao {
    @Insert
    suspend fun insert(frame: FreezeFrameEntity)

    @Query("SELECT * FROM freeze_frames WHERE vin = :vin ORDER BY timestamp DESC")
    fun getFramesForVin(vin: String): Flow<List<FreezeFrameEntity>>
}
