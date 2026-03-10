package com.odbplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.odbplus.app.data.db.entity.VehicleVinValidationEntity

@Dao
interface VinValidationDao {

    @Upsert
    suspend fun upsert(entity: VehicleVinValidationEntity)

    @Query("SELECT * FROM vehicle_vin_validation WHERE vin = :vin LIMIT 1")
    suspend fun getByVin(vin: String): VehicleVinValidationEntity?

    @Query("DELETE FROM vehicle_vin_validation WHERE vin = :vin")
    suspend fun deleteByVin(vin: String)
}
