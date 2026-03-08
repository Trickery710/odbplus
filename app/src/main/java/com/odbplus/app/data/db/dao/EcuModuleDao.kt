package com.odbplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.odbplus.app.data.db.entity.EcuModuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EcuModuleDao {
    @Upsert
    suspend fun upsert(module: EcuModuleEntity)

    @Query("SELECT * FROM ecu_modules WHERE vin = :vin")
    fun getModulesForVin(vin: String): Flow<List<EcuModuleEntity>>
}
