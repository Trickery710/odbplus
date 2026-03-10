package com.odbplus.app.vin.network

import com.odbplus.app.vin.domain.DecodedVin

/**
 * Abstract interface for online VIN decoder providers.
 *
 * To add a new provider (e.g. AutoCheck, DataOne):
 *  1. Create a new implementation of this interface.
 *  2. Register it in [VinModule] alongside or as a replacement for [NhtsaVinDecoderService].
 *  3. Inject it into [VinDecoderRepository] via the qualifier or a list.
 */
interface VinDecoderService {

    /** Human-readable provider name, e.g. "NHTSA_VPIC". Used for logging and DB records. */
    val providerName: String

    /**
     * Decode a normalized 17-character VIN.
     *
     * @param vin  Normalized (uppercase, no spaces) VIN.
     * @return [NetworkDecodeResult] — never throws; errors are wrapped in the sealed class.
     */
    suspend fun decode(vin: String): NetworkDecodeResult
}

/**
 * Result returned by [VinDecoderService.decode].
 */
sealed class NetworkDecodeResult {
    data class Success(
        val decoded: DecodedVin,
        val rawJson: String,
        val httpStatus: Int
    ) : NetworkDecodeResult()

    data class PartialData(
        val decoded: DecodedVin,
        val rawJson: String,
        val httpStatus: Int,
        val missingFields: List<String>
    ) : NetworkDecodeResult()

    data class HttpError(
        val httpStatus: Int,
        val message: String
    ) : NetworkDecodeResult()

    data class NetworkError(
        val cause: Throwable,
        val message: String = cause.message ?: "Unknown network error"
    ) : NetworkDecodeResult()

    data class ParseError(
        val rawBody: String?,
        val cause: Throwable
    ) : NetworkDecodeResult()
}
