package com.odbplus.app.data.db

import android.content.Context
import androidx.room.Room
import com.odbplus.app.data.db.dao.DtcLogDao
import com.odbplus.app.data.db.dao.EcuModuleDao
import com.odbplus.app.data.db.dao.FreezeFrameDao
import com.odbplus.app.data.db.dao.SensorLogDao
import com.odbplus.app.data.db.dao.TestResultDao
import com.odbplus.app.data.db.dao.VehicleDao
import com.odbplus.app.data.db.dao.VehicleSessionDao
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
        Room.databaseBuilder(ctx, OdbDatabase::class.java, "odb_database").build()

    @Provides
    @Singleton
    fun provideVehicleDao(db: OdbDatabase): VehicleDao = db.vehicleDao()

    @Provides
    @Singleton
    fun provideSessionDao(db: OdbDatabase): VehicleSessionDao = db.sessionDao()

    @Provides
    @Singleton
    fun provideSensorLogDao(db: OdbDatabase): SensorLogDao = db.sensorLogDao()

    @Provides
    @Singleton
    fun provideDtcLogDao(db: OdbDatabase): DtcLogDao = db.dtcLogDao()

    @Provides
    @Singleton
    fun provideFreezeFrameDao(db: OdbDatabase): FreezeFrameDao = db.freezeFrameDao()

    @Provides
    @Singleton
    fun provideEcuModuleDao(db: OdbDatabase): EcuModuleDao = db.ecuModuleDao()

    @Provides
    @Singleton
    fun provideTestResultDao(db: OdbDatabase): TestResultDao = db.testResultDao()
}
