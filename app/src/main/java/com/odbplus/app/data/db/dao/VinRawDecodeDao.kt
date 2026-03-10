package com.odbplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.odbplus.app.data.db.entity.VehicleVinRawDecodeEntity

@Dao
interface VinRawDecodeDao {

    @Upsert
    suspend fun upsert(entity: VehicleVinRawDecodeEntity)

    @Query("SELECT * FROM vehicle_vin_raw_decode WHERE vin = :vin LIMIT 1")
    suspend fun getByVin(vin: String): VehicleVinRawDecodeEntity?

    @Query("SELECT rawJson FROM vehicle_vin_raw_decode WHERE vin = :vin LIMIT 1")
    suspend fun getRawJsonByVin(vin: String): String?

    @Query("DELETE FROM vehicle_vin_raw_decode WHERE vin = :vin")
    suspend fun deleteByVin(vin: String)
}
