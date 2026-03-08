package com.odbplus.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.odbplus.app.data.db.dao.DtcLogDao
import com.odbplus.app.data.db.dao.EcuModuleDao
import com.odbplus.app.data.db.dao.FreezeFrameDao
import com.odbplus.app.data.db.dao.SensorLogDao
import com.odbplus.app.data.db.dao.TestResultDao
import com.odbplus.app.data.db.dao.VehicleDao
import com.odbplus.app.data.db.dao.VehicleSessionDao
import com.odbplus.app.data.db.entity.DtcLogEntity
import com.odbplus.app.data.db.entity.EcuModuleEntity
import com.odbplus.app.data.db.entity.FreezeFrameEntity
import com.odbplus.app.data.db.entity.SensorLogEntity
import com.odbplus.app.data.db.entity.TestResultEntity
import com.odbplus.app.data.db.entity.VehicleEntity
import com.odbplus.app.data.db.entity.VehicleSessionEntity

@Database(
    entities = [
        VehicleEntity::class,
        VehicleSessionEntity::class,
        SensorLogEntity::class,
        DtcLogEntity::class,
        FreezeFrameEntity::class,
        EcuModuleEntity::class,
        TestResultEntity::class
    ],
    version = 1,
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
}
