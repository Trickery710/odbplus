package com.odbplus.app.vin.di

import com.odbplus.app.vin.network.NhtsaVinDecoderService
import com.odbplus.app.vin.network.VinDecoderService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt module for the VIN decoding subsystem.
 *
 * The active decoder provider is [NhtsaVinDecoderService].
 *
 * To add a second provider:
 *  1. Implement [VinDecoderService] in a new class (e.g. DataOneVinDecoderService).
 *  2. Add a @Named binding here.
 *  3. Inject both into [VinDecoderRepository] and implement a selection strategy.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VinModule {

    @Binds
    @Singleton
    abstract fun bindVinDecoderService(impl: NhtsaVinDecoderService): VinDecoderService

    companion object {

        /**
         * Dedicated [HttpClient] for VIN decode requests.
         * Isolated from any other Ktor client to prevent timeout or config bleed.
         *
         * To add a second VIN provider, either reuse this client (if compatible timeouts)
         * or create a second @Named client.
         */
        @Provides
        @Singleton
        @VinHttpClient
        fun provideVinHttpClient(): HttpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                })
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis  = 15_000
            }
        }

        /**
         * Convenience binding — injects the [VinHttpClient] into [NhtsaVinDecoderService].
         * If [NhtsaVinDecoderService] were to accept the @VinHttpClient qualifier directly,
         * this shim would not be needed. Since Hilt @Inject constructors can't carry qualifiers
         * on parameters, we provide it here.
         */
        @Provides
        @Singleton
        fun provideNhtsaVinDecoderService(
            @VinHttpClient client: HttpClient
        ): NhtsaVinDecoderService = NhtsaVinDecoderService(client)
    }
}

/** Qualifier for the VIN-decode-specific [HttpClient]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VinHttpClient
