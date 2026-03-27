package com.obdplus.core.protocol.di

import com.obdplus.core.protocol.session.AdapterSession
import com.obdplus.core.transport.di.AppScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProtocolModule {

    @Provides
    @Singleton
    fun provideAdapterSession(
        @AppScope scope: CoroutineScope
    ): AdapterSession = AdapterSession(scope)
}
