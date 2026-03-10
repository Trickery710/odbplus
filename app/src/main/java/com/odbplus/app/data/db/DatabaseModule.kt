package com.odbplus.app.data.db

import android.content.Context
import androidx.room.Room
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): OdbDatabase =
        Room.databaseBuilder(ctx, OdbDatabase::class.java, "odb_database")
            .addMigrations(OdbDatabase.MIGRATION_1_2, OdbDatabase.MIGRATION_2_3)
            .build()

    @Provides @Singleton
    fun provideVehicleDao(db: OdbDatabase): VehicleDao = db.vehicleDao()

    @Provides @Singleton
    fun provideSessionDao(db: OdbDatabase): VehicleSessionDao = db.sessionDao()

    @Provides @Singleton
    fun provideSensorLogDao(db: OdbDatabase): SensorLogDao = db.sensorLogDao()

    @Provides @Singleton
    fun provideDtcLogDao(db: OdbDatabase): DtcLogDao = db.dtcLogDao()

    @Provides @Singleton
    fun provideFreezeFrameDao(db: OdbDatabase): FreezeFrameDao = db.freezeFrameDao()

    @Provides @Singleton
    fun provideEcuModuleDao(db: OdbDatabase): EcuModuleDao = db.ecuModuleDao()

    @Provides @Singleton
    fun provideTestResultDao(db: OdbDatabase): TestResultDao = db.testResultDao()

    @Provides @Singleton
    fun provideVehicleProfileDao(db: OdbDatabase): VehicleProfileDao = db.vehicleProfileDao()

    @Provides @Singleton
    fun provideSupportedPidDao(db: OdbDatabase): SupportedPidDao = db.supportedPidDao()

    // VIN decode subsystem (v3)
    @Provides @Singleton
    fun provideVehicleIdentityDao(db: OdbDatabase): VehicleIdentityDao = db.vehicleIdentityDao()

    @Provides @Singleton
    fun provideVinValidationDao(db: OdbDatabase): VinValidationDao = db.vinValidationDao()

    @Provides @Singleton
    fun provideVinRawDecodeDao(db: OdbDatabase): VinRawDecodeDao = db.vinRawDecodeDao()

    @Provides @Singleton
    fun provideVinCachePolicyDao(db: OdbDatabase): VinCachePolicyDao = db.vinCachePolicyDao()
}
