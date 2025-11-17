package com.odbplus.app.di

import com.odbplus.core.transport.ObdTransport
import com.odbplus.core.transport.TcpTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TransportModule {

    @Provides @Singleton
    fun provideExternalScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides @Singleton
    fun provideObdTransport(scope: CoroutineScope): ObdTransport =
        TcpTransport(host = "127.0.0.1", port = 35000, externalScope = scope)
}
