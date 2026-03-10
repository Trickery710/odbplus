package com.odbplus.app.vin.repository

import com.odbplus.app.data.db.dao.VinCachePolicyDao
import com.odbplus.app.data.db.dao.VinRawDecodeDao
import com.odbplus.app.data.db.dao.VinValidationDao
import com.odbplus.app.data.db.entity.VehicleVinCachePolicyEntity
import com.odbplus.app.data.db.entity.VehicleVinRawDecodeEntity
import com.odbplus.app.data.db.entity.VehicleVinValidationEntity
import com.odbplus.app.vin.cache.VinCachePolicy
import com.odbplus.app.vin.domain.DecodedVin
import com.odbplus.app.vin.domain.DecodeSource
import com.odbplus.app.vin.domain.VinDecodeOutcome
import com.odbplus.app.vin.domain.VinValidationResult
import com.odbplus.app.vin.domain.VinValidationStatus
import com.odbplus.app.vin.domain.VerificationStatus
import com.odbplus.app.vin.network.NetworkDecodeResult
import com.odbplus.app.vin.network.VinDecoderService
import com.odbplus.app.vin.validation.VinValidator
import com.odbplus.app.vin.verification.VinVerificationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the full VIN decode pipeline:
 *  1. Normalize + local validation
 *  2. Persist validation result
 *  3. Cache policy check
 *  4. If cached and fresh → return [VinDecodeOutcome.Cached]
 *  5. If network needed → call [VinDecoderService]
 *  6. Persist raw JSON + identity record
 *  7. Run verification + return typed outcome
 *
 * All DB and network work runs on [Dispatchers.IO].
 * This class never throws — all errors are wrapped in [VinDecodeOutcome].
 */
@Singleton
class VinDecoderRepository @Inject constructor(
    private val decoderService: VinDecoderService,
    private val identityRepository: VehicleIdentityRepository,
    private val validationDao: VinValidationDao,
    private val rawDecodeDao: VinRawDecodeDao,
    private val cachePolicyDao: VinCachePolicyDao
) {

    /**
     * Decode a VIN through the full pipeline.
     *
     * @param rawVin      Raw VIN string as received (may have leading/trailing whitespace).
     * @param forceRefresh True to bypass cache (subject to rate limiting).
     */
    suspend fun decode(rawVin: String, forceRefresh: Boolean = false): VinDecodeOutcome =
        withContext(Dispatchers.IO) {
            // 1. Normalize and validate locally
            val validation = VinValidator.validate(rawVin)
            persistValidation(validation)

            if (validation.validationStatus == VinValidationStatus.INVALID) {
                Timber.w("VIN decode: invalid VIN '${rawVin}' — ${validation.validationMessages}")
                return@withContext VinDecodeOutcome.ValidationFailed(
                    vin = validation.normalizedVin,
                    validation = validation
                )
            }

            val vin = validation.normalizedVin

            // 2. Check cache policy
            val cachePolicy = cachePolicyDao.getByVin(vin)
            val cachedIdentity = identityRepository.getByVin(vin)
            val cachedStatus = cachedIdentity?.let {
                runCatching { VerificationStatus.valueOf(it.verificationStatus) }.getOrNull()
            }

            val decision = VinCachePolicy.evaluate(
                entity = cachePolicy,
                status = cachedStatus,
                forceRefresh = forceRefresh
            )

            Timber.d("VIN decode: cache decision for $vin — ${decision.reason}")

            if (decision.shouldUseCached && cachedIdentity != null && !decision.shouldRefreshInBackground) {
                // Fresh cache hit — return immediately
                val decoded = with(identityRepository) { cachedIdentity.toDomain() }
                    .copy(source = DecodeSource.CACHE)
                return@withContext VinDecodeOutcome.Cached(decoded)
            }

            // 3. Network decode
            val networkResult = decoderService.decode(vin)

            return@withContext handleNetworkResult(
                vin = vin,
                validation = validation,
                networkResult = networkResult,
                cachedIdentity = cachedIdentity
            )
        }

    // ── Private pipeline steps ────────────────────────────────────────────────

    private suspend fun handleNetworkResult(
        vin: String,
        validation: VinValidationResult,
        networkResult: NetworkDecodeResult,
        cachedIdentity: com.odbplus.app.data.db.entity.VehicleIdentityEntity?
    ): VinDecodeOutcome {
        return when (networkResult) {
            is NetworkDecodeResult.Success -> {
                val verification = VinVerificationEngine.verifyWithRemote(
                    validation = validation,
                    decoded = networkResult.decoded
                )
                val finalDecoded = networkResult.decoded.copy(
                    confidence = verification.confidence,
                    verificationStatus = verification.status,
                    warnings = verification.warnings,
                    errors = verification.errors
                )

                persistSuccess(vin, finalDecoded, networkResult.rawJson, networkResult.httpStatus, verification.status)

                if (verification.status == VerificationStatus.SUSPECT || verification.status == VerificationStatus.FAILED) {
                    VinDecodeOutcome.VerificationFailed(vin, verification)
                } else {
                    VinDecodeOutcome.Success(finalDecoded, verification)
                }
            }

            is NetworkDecodeResult.PartialData -> {
                val verification = VinVerificationEngine.verifyWithRemote(
                    validation = validation,
                    decoded = networkResult.decoded
                )
                val finalDecoded = networkResult.decoded.copy(
                    confidence = verification.confidence,
                    verificationStatus = verification.status,
                    warnings = verification.warnings + listOf("Missing fields: ${networkResult.missingFields.joinToString()}")
                )

                persistSuccess(vin, finalDecoded, networkResult.rawJson, networkResult.httpStatus, verification.status)

                VinDecodeOutcome.PartialSuccess(
                    decoded = finalDecoded,
                    warnings = networkResult.missingFields.map { "Missing field: $it" } + verification.warnings
                )
            }

            is NetworkDecodeResult.HttpError -> {
                Timber.w("VIN decode: HTTP error ${networkResult.httpStatus} for $vin")
                persistFailure(vin, networkResult.httpStatus, "HTTP ${networkResult.httpStatus}: ${networkResult.message}")
                // Return cached if we have it
                if (cachedIdentity != null) {
                    val cached = with(identityRepository) { cachedIdentity.toDomain() }
                        .copy(source = DecodeSource.CACHE)
                    VinDecodeOutcome.Cached(cached)
                } else {
                    VinDecodeOutcome.NetworkFailed(vin, networkResult.message)
                }
            }

            is NetworkDecodeResult.NetworkError -> {
                Timber.e(networkResult.cause, "VIN decode: network error for $vin")
                persistFailure(vin, 0, networkResult.message)
                if (cachedIdentity != null) {
                    val cached = with(identityRepository) { cachedIdentity.toDomain() }
                        .copy(source = DecodeSource.CACHE)
                    VinDecodeOutcome.Cached(cached)
                } else {
                    VinDecodeOutcome.NetworkFailed(vin, networkResult.message)
                }
            }

            is NetworkDecodeResult.ParseError -> {
                Timber.e(networkResult.cause, "VIN decode: parse error for $vin")
                persistFailure(vin, 0, "Parse error: ${networkResult.cause.message}")
                VinDecodeOutcome.NetworkFailed(vin, "Response parse failed: ${networkResult.cause.message}")
            }
        }
    }

    private suspend fun persistValidation(validation: VinValidationResult) {
        runCatching {
            validationDao.upsert(
                VehicleVinValidationEntity(
                    vin               = validation.normalizedVin,
                    isLengthValid     = validation.isLengthValid,
                    hasOnlyAllowedChars = validation.hasOnlyAllowedChars,
                    isCheckDigitValid = validation.isCheckDigitValid,
                    parsedModelYear   = validation.modelYearCandidate,
                    wmi               = validation.wmi,
                    plantCode         = validation.plantCode?.toString(),
                    validationStatus  = validation.validationStatus.name,
                    validationSummary = validation.validationMessages.joinToString("\n"),
                    checkedAt         = System.currentTimeMillis()
                )
            )
        }.onFailure { Timber.e(it, "VIN decode: failed to persist validation for ${validation.normalizedVin}") }
    }

    private suspend fun persistSuccess(
        vin: String,
        decoded: DecodedVin,
        rawJson: String,
        httpStatus: Int,
        status: VerificationStatus
    ) {
        runCatching {
            identityRepository.save(decoded)
            rawDecodeDao.upsert(
                VehicleVinRawDecodeEntity(
                    vin             = vin,
                    providerName    = decoderService.providerName,
                    providerVersion = null,
                    rawJson         = rawJson,
                    fetchedAt       = System.currentTimeMillis(),
                    httpStatus      = httpStatus,
                    wasSuccessful   = true
                )
            )
            val ttl = when (status) {
                VerificationStatus.VERIFIED, VerificationStatus.MOSTLY_VERIFIED -> VinCachePolicy.VERIFIED_TTL_MS
                else -> VinCachePolicy.PARTIAL_TTL_MS
            }
            val existingPolicy = cachePolicyDao.getByVin(vin)
            cachePolicyDao.upsert(VinCachePolicy.buildUpdatedEntity(vin, existingPolicy, succeeded = true, ttlMs = ttl))
        }.onFailure { Timber.e(it, "VIN decode: failed to persist success for $vin") }
    }

    private suspend fun persistFailure(vin: String, httpStatus: Int, reason: String) {
        runCatching {
            rawDecodeDao.upsert(
                VehicleVinRawDecodeEntity(
                    vin             = vin,
                    providerName    = decoderService.providerName,
                    providerVersion = null,
                    rawJson         = "",
                    fetchedAt       = System.currentTimeMillis(),
                    httpStatus      = httpStatus,
                    wasSuccessful   = false
                )
            )
            val existingPolicy = cachePolicyDao.getByVin(vin)
            cachePolicyDao.upsert(
                VinCachePolicy.buildUpdatedEntity(vin, existingPolicy, succeeded = false, ttlMs = 0L)
            )
        }.onFailure { Timber.e(it, "VIN decode: failed to persist failure for $vin — $reason") }
    }
}
