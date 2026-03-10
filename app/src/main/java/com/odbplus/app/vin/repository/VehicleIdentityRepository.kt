package com.odbplus.app.vin.repository

import com.odbplus.app.data.db.dao.VehicleIdentityDao
import com.odbplus.app.data.db.entity.VehicleIdentityEntity
import com.odbplus.app.vin.domain.DecodedVin
import com.odbplus.app.vin.domain.DecodeSource
import com.odbplus.app.vin.domain.VerificationStatus
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for [VehicleIdentityEntity].
 * Provides access to decoded VIN information stored in Room.
 */
@Singleton
class VehicleIdentityRepository @Inject constructor(
    private val dao: VehicleIdentityDao
) {

    fun observeByVin(vin: String): Flow<VehicleIdentityEntity?> = dao.observeByVin(vin)

    suspend fun getByVin(vin: String): VehicleIdentityEntity? = dao.getByVin(vin)

    suspend fun exists(vin: String): Boolean = dao.existsCount(vin) > 0

    suspend fun save(decoded: DecodedVin) {
        Timber.d("VIN identity: saving $decoded")
        dao.upsert(decoded.toEntity())
    }

    suspend fun getVinsWithLowConfidence(minConfidence: Float = 0.70f): List<String> =
        dao.getVinsWithLowConfidence(minConfidence)

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun DecodedVin.toEntity(): VehicleIdentityEntity = VehicleIdentityEntity(
        vin                = vin,
        normalizedVin      = vin,
        make               = make,
        model              = model,
        modelYear          = modelYear,
        trim               = trim,
        series             = series,
        manufacturer       = manufacturer,
        vehicleType        = vehicleType,
        bodyClass          = bodyClass,
        engineModel        = engineModel,
        engineCylinders    = engineCylinders,
        displacementL      = displacementL,
        fuelTypePrimary    = fuelTypePrimary,
        fuelTypeSecondary  = fuelTypeSecondary,
        driveType          = driveType,
        transmissionStyle  = transmissionStyle,
        plantCountry       = plantCountry,
        plantCompany       = plantCompany,
        plantCity          = plantCity,
        plantState         = plantState,
        gvwrClass          = gvwrClass,
        brakeSystemType    = brakeSystemType,
        airBagLocations    = airBagLocations,
        updatedAt          = System.currentTimeMillis(),
        source             = source.name,
        confidenceScore    = confidence,
        verificationStatus = verificationStatus.name
    )

    fun VehicleIdentityEntity.toDomain(): DecodedVin = DecodedVin(
        vin                = vin,
        make               = make,
        model              = model,
        modelYear          = modelYear,
        trim               = trim,
        series             = series,
        manufacturer       = manufacturer,
        vehicleType        = vehicleType,
        bodyClass          = bodyClass,
        engineModel        = engineModel,
        engineCylinders    = engineCylinders,
        displacementL      = displacementL,
        fuelTypePrimary    = fuelTypePrimary,
        fuelTypeSecondary  = fuelTypeSecondary,
        driveType          = driveType,
        transmissionStyle  = transmissionStyle,
        plantCountry       = plantCountry,
        plantCompany       = plantCompany,
        plantCity          = plantCity,
        plantState         = plantState,
        gvwrClass          = gvwrClass,
        brakeSystemType    = brakeSystemType,
        airBagLocations    = airBagLocations,
        source             = runCatching { DecodeSource.valueOf(source) }.getOrDefault(DecodeSource.CACHE),
        decodeTimestamp    = updatedAt,
        confidence         = confidenceScore,
        verificationStatus = runCatching { VerificationStatus.valueOf(verificationStatus) }.getOrDefault(VerificationStatus.PARTIAL)
    )
}
