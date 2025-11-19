package com.odbplus.core.transport.di

import com.odbplus.core.transport.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TransportModule {

    // --- FIX: Bind the implementation to the interface ---
    @Binds
    @Singleton
    abstract fun bindTransportRepository(impl: TransportRepositoryImpl): TransportRepository

    companion object {
        @Provides
        @Singleton
        @Named("tcp")
        fun provideTcpTransport(transport: TcpTransport): ObdTransport = transport

        @Provides
        @Singleton
        @Named("bt")
        fun provideBluetoothTransport(transport: BluetoothTransport): ObdTransport = transport
    }
}
