package com.odbplus.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.odbplus.app.data.db.dao.DtcLogDao
import com.odbplus.app.data.db.dao.EcuModuleDao
import com.odbplus.app.data.db.dao.FreezeFrameDao
import com.odbplus.app.data.db.dao.SensorLogDao
import com.odbplus.app.data.db.dao.SupportedPidDao
import com.odbplus.app.data.db.dao.TestResultDao
import com.odbplus.app.data.db.dao.VehicleDao
import com.odbplus.app.data.db.dao.VehicleIdentityDao
import com.odbplus.app.data.db.dao.VehicleProfileDao
import com.odbplus.app.data.db.dao.VehicleSessionDao
import com.odbplus.app.data.db.dao.VinCachePolicyDao
import com.odbplus.app.data.db.dao.VinRawDecodeDao
import com.odbplus.app.data.db.dao.VinValidationDao
import com.odbplus.app.data.db.entity.DtcLogEntity
import com.odbplus.app.data.db.entity.EcuModuleEntity
import com.odbplus.app.data.db.entity.FreezeFrameEntity
import com.odbplus.app.data.db.entity.SensorLogEntity
import com.odbplus.app.data.db.entity.SupportedPidEntity
import com.odbplus.app.data.db.entity.TestResultEntity
import com.odbplus.app.data.db.entity.VehicleEntity
import com.odbplus.app.data.db.entity.VehicleIdentityEntity
import com.odbplus.app.data.db.entity.VehicleProfileEntity
import com.odbplus.app.data.db.entity.VehicleSessionEntity
import com.odbplus.app.data.db.entity.VehicleVinCachePolicyEntity
import com.odbplus.app.data.db.entity.VehicleVinRawDecodeEntity
import com.odbplus.app.data.db.entity.VehicleVinValidationEntity

@Database(
    entities = [
        VehicleEntity::class,
        VehicleSessionEntity::class,
        SensorLogEntity::class,
        DtcLogEntity::class,
        FreezeFrameEntity::class,
        EcuModuleEntity::class,
        TestResultEntity::class,
        VehicleProfileEntity::class,
        SupportedPidEntity::class,
        // VIN decode subsystem (v3)
        VehicleIdentityEntity::class,
        VehicleVinValidationEntity::class,
        VehicleVinRawDecodeEntity::class,
        VehicleVinCachePolicyEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class OdbDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun sessionDao(): VehicleSessionDao
    abstract fun sensorLogDao(): SensorLogDao
    abstract fun dtcLogDao(): DtcLogDao
    abstract fun freezeFrameDao(): FreezeFrameDao
    abstract fun ecuModuleDao(): EcuModuleDao
    abstract fun testResultDao(): TestResultDao
    abstract fun vehicleProfileDao(): VehicleProfileDao
    abstract fun supportedPidDao(): SupportedPidDao
    // VIN decode subsystem (v3)
    abstract fun vehicleIdentityDao(): VehicleIdentityDao
    abstract fun vinValidationDao(): VinValidationDao
    abstract fun vinRawDecodeDao(): VinRawDecodeDao
    abstract fun vinCachePolicyDao(): VinCachePolicyDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vehicle_profiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        vin TEXT,
                        protocol TEXT,
                        ecuFirmwareId TEXT,
                        calibrationId TEXT,
                        cvn TEXT,
                        fingerprintHash TEXT NOT NULL,
                        firstSeen INTEGER NOT NULL,
                        lastSeen INTEGER NOT NULL,
                        lastValidated INTEGER,
                        validationSource TEXT NOT NULL DEFAULT 'discovery',
                        cacheConfidence REAL NOT NULL DEFAULT 1.0,
                        failedValidationCount INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_profile_vin ON vehicle_profiles(vin)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_profile_hash ON vehicle_profiles(fingerprintHash)"
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS supported_pids (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        vehicleProfileId INTEGER NOT NULL,
                        mode INTEGER NOT NULL DEFAULT 1,
                        pid TEXT NOT NULL,
                        supported INTEGER NOT NULL DEFAULT 1,
                        source TEXT NOT NULL DEFAULT 'bitmap_discovery',
                        firstSeen INTEGER NOT NULL,
                        lastConfirmed INTEGER NOT NULL,
                        FOREIGN KEY(vehicleProfileId) REFERENCES vehicle_profiles(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_spid_profile ON supported_pids(vehicleProfileId)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_spid_unique ON supported_pids(vehicleProfileId, mode, pid)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // vehicle_identity: stores remotely decoded VIN data
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vehicle_identity (
                        vin TEXT PRIMARY KEY NOT NULL,
                        normalizedVin TEXT NOT NULL,
                        make TEXT,
                        model TEXT,
                        modelYear INTEGER,
                        trim TEXT,
                        series TEXT,
                        manufacturer TEXT,
                        vehicleType TEXT,
                        bodyClass TEXT,
                        engineModel TEXT,
                        engineCylinders INTEGER,
                        displacementL REAL,
                        fuelTypePrimary TEXT,
                        fuelTypeSecondary TEXT,
                        driveType TEXT,
                        transmissionStyle TEXT,
                        plantCountry TEXT,
                        plantCompany TEXT,
                        plantCity TEXT,
                        plantState TEXT,
                        gvwrClass TEXT,
                        brakeSystemType TEXT,
                        airBagLocations TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        confidenceScore REAL NOT NULL,
                        verificationStatus TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_identity_make ON vehicle_identity(make)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_identity_year ON vehicle_identity(modelYear)")

                // vehicle_vin_validation: local validation result per VIN
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vehicle_vin_validation (
                        vin TEXT PRIMARY KEY NOT NULL,
                        isLengthValid INTEGER NOT NULL,
                        hasOnlyAllowedChars INTEGER NOT NULL,
                        isCheckDigitValid INTEGER NOT NULL,
                        parsedModelYear INTEGER,
                        wmi TEXT,
                        plantCode TEXT,
                        validationStatus TEXT NOT NULL,
                        validationSummary TEXT NOT NULL,
                        checkedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // vehicle_vin_raw_decode: raw JSON from provider
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vehicle_vin_raw_decode (
                        vin TEXT PRIMARY KEY NOT NULL,
                        providerName TEXT NOT NULL,
                        providerVersion TEXT,
                        rawJson TEXT NOT NULL,
                        fetchedAt INTEGER NOT NULL,
                        httpStatus INTEGER NOT NULL,
                        wasSuccessful INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // vehicle_vin_cache_policy: cache freshness tracking
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vehicle_vin_cache_policy (
                        vin TEXT PRIMARY KEY NOT NULL,
                        lastAttemptAt INTEGER NOT NULL,
                        lastSuccessAt INTEGER,
                        lastFailureAt INTEGER,
                        retryCount INTEGER NOT NULL,
                        cacheExpiresAt INTEGER NOT NULL,
                        isStale INTEGER NOT NULL,
                        shouldRefresh INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
