package com.odbplus.app.vin.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import timber.log.Timber
import javax.inject.Inject

/**
 * NHTSA vPIC VIN decode implementation of [VinDecoderService].
 *
 * API docs: https://vpic.nhtsa.dot.gov/api/
 * Endpoint: GET /api/vehicles/DecodeVin/{VIN}?format=json
 *
 * The API is free, requires no API key, and has generous rate limits.
 * Response fields are a flat list of Variable/Value pairs.
 *
 * To add a second provider: implement [VinDecoderService], inject it in [VinModule],
 * and let [VinDecoderRepository] select between providers via strategy or priority.
 */
class NhtsaVinDecoderService @Inject constructor(
    private val httpClient: HttpClient
) : VinDecoderService {

    override val providerName: String = "NHTSA_VPIC"

    private val baseUrl = "https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVin"

    override suspend fun decode(vin: String): NetworkDecodeResult {
        return try {
            Timber.d("VIN decode: requesting NHTSA for $vin")
            val response = httpClient.get("$baseUrl/$vin") {
                parameter("format", "json")
            }

            val status = response.status.value

            if (!response.status.isSuccess()) {
                Timber.w("VIN decode: NHTSA returned HTTP $status for $vin")
                return NetworkDecodeResult.HttpError(
                    httpStatus = status,
                    message = "NHTSA returned HTTP $status"
                )
            }

            val rawJson = response.bodyAsText()
            val dto: NhtsaDecodeResponse = try {
                response.body()
            } catch (e: Exception) {
                Timber.e(e, "VIN decode: JSON parse failed for $vin")
                return NetworkDecodeResult.ParseError(rawJson, e)
            }

            if (dto.results.isEmpty()) {
                Timber.w("VIN decode: NHTSA returned empty results for $vin")
                return NetworkDecodeResult.HttpError(status, "Provider returned empty results")
            }

            val decoded = NhtsaMapper.map(vin, dto)
            val missing = NhtsaMapper.missingCoreFields(decoded)

            Timber.d("VIN decode: NHTSA success for $vin — ${decoded.make} ${decoded.model} ${decoded.modelYear}")

            if (missing.isEmpty()) {
                NetworkDecodeResult.Success(
                    decoded = decoded,
                    rawJson = rawJson,
                    httpStatus = status
                )
            } else {
                Timber.w("VIN decode: NHTSA partial data for $vin — missing: $missing")
                NetworkDecodeResult.PartialData(
                    decoded = decoded,
                    rawJson = rawJson,
                    httpStatus = status,
                    missingFields = missing
                )
            }

        } catch (e: Exception) {
            Timber.e(e, "VIN decode: network error for $vin")
            NetworkDecodeResult.NetworkError(cause = e)
        }
    }
}
