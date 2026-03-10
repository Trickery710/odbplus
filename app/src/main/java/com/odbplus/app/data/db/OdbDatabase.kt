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
import com.odbplus.app.data.db.dao.VehicleProfileDao
import com.odbplus.app.data.db.dao.VehicleSessionDao
import com.odbplus.app.data.db.entity.DtcLogEntity
import com.odbplus.app.data.db.entity.EcuModuleEntity
import com.odbplus.app.data.db.entity.FreezeFrameEntity
import com.odbplus.app.data.db.entity.SensorLogEntity
import com.odbplus.app.data.db.entity.SupportedPidEntity
import com.odbplus.app.data.db.entity.TestResultEntity
import com.odbplus.app.data.db.entity.VehicleEntity
import com.odbplus.app.data.db.entity.VehicleProfileEntity
import com.odbplus.app.data.db.entity.VehicleSessionEntity

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
        SupportedPidEntity::class
    ],
    version = 2,
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
    }
}
