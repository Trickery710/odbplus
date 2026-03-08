package com.odbplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.odbplus.app.data.db.entity.TestResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TestResultDao {
    @Insert
    suspend fun insert(result: TestResultEntity)

    @Query("SELECT * FROM test_results WHERE vin = :vin ORDER BY timestamp DESC")
    fun getResultsForVin(vin: String): Flow<List<TestResultEntity>>
}
