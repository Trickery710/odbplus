package com.odbplus.app.ai

import com.odbplus.app.ai.data.VehicleInfo
import com.odbplus.app.data.db.dao.VehicleDao
import com.odbplus.app.data.db.entity.VehicleEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleInfoRepository @Inject constructor(
    private val vehicleDao: VehicleDao
) {
    private val _currentVehicle = MutableStateFlow<VehicleInfo?>(null)
    val currentVehicle: StateFlow<VehicleInfo?> = _currentVehicle.asStateFlow()

    /** No-op — Room initializes lazily. Kept for API compatibility. */
    suspend fun initialize() = Unit

    suspend fun isFirstTimeVehicle(vin: String): Boolean =
        vehicleDao.getByVin(vin) == null

    suspend fun getVehicle(vin: String): VehicleInfo? =
        vehicleDao.getByVin(vin)?.toDomain()

    suspend fun saveVehicle(info: VehicleInfo) {
        vehicleDao.upsert(info.toEntity())
        _currentVehicle.value = info
    }

    fun setCurrentVehicle(info: VehicleInfo?) {
        _currentVehicle.value = info
    }

    fun clearCurrentVehicle() {
        _currentVehicle.value = null
    }

    suspend fun getAllVehicles(): List<VehicleInfo> =
        vehicleDao.getRecent(50).map { it.toDomain() }

    suspend fun deleteVehicle(vin: String) {
        vehicleDao.getByVin(vin)?.let { vehicleDao.delete(it) }
        if (_currentVehicle.value?.vin == vin) _currentVehicle.value = null
    }
}

fun VehicleEntity.toDomain(): VehicleInfo = VehicleInfo(
    vin = vin,
    calibrationId = calibrationId,
    calibrationVerificationNumber = calibrationVerificationNumber,
    ecuName = ecuName,
    firstSeenTimestamp = firstSeenTimestamp,
    lastSeenTimestamp = lastSeenTimestamp
)

fun VehicleInfo.toEntity(): VehicleEntity = VehicleEntity(
    vin = vin,
    calibrationId = calibrationId,
    calibrationVerificationNumber = calibrationVerificationNumber,
    ecuName = ecuName,
    firstSeenTimestamp = firstSeenTimestamp,
    lastSeenTimestamp = lastSeenTimestamp
)
