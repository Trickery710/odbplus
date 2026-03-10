package com.odbplus.app.live

import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdResponse
import com.odbplus.core.protocol.ObdService
import timber.log.Timber
import javax.inject.Inject

/** PIDs used during lightweight cache validation. All fast-path, reliable on KWP2000. */
private val VALIDATION_PIDS = listOf(
    ObdPid.ENGINE_RPM,
    ObdPid.VEHICLE_SPEED,
    ObdPid.ENGINE_COOLANT_TEMP,
    ObdPid.ENGINE_LOAD
)

/** Minimum fraction of validation probes that must succeed to accept the cache. */
private const val VALIDATION_PASS_THRESHOLD = 0.5

sealed interface ResolutionOutcome {
    /** Trusted cached PIDs loaded — skip bitmap discovery. */
    data class CacheHit(val pidCodes: Set<String>, val profileId: Long) : ResolutionOutcome
    /** Cache found but stale or low confidence — validation ran. */
    data class ValidatedCache(val pidCodes: Set<String>, val profileId: Long) : ResolutionOutcome
    /** Cache missing, stale validation failed, or confidence too low — run full discovery. */
    data class NeedsDiscovery(val profileId: Long) : ResolutionOutcome
}

/**
 * Orchestrates the connect-flow PID resolution strategy:
 *
 * 1. Read VIN + calibration identity from ECU
 * 2. Build fingerprint → look up Room cache
 * 3a. Trusted cache (confidence ≥ 0.85, not stale) → [ResolutionOutcome.CacheHit]
 * 3b. Stale/medium cache → run lightweight validation probes
 *      - Pass → [ResolutionOutcome.ValidatedCache]
 *      - Fail → [ResolutionOutcome.NeedsDiscovery]
 * 3c. No cache / low confidence → [ResolutionOutcome.NeedsDiscovery]
 *
 * The caller is responsible for calling [ObdService.runPidDiscovery] when
 * [NeedsDiscovery] is returned, then calling
 * [SupportedPidCacheRepository.saveDiscovery] with the result.
 */
class ResolveSupportedPidsUseCase @Inject constructor(
    private val obdService: ObdService,
    private val cache: SupportedPidCacheRepository
) {

    suspend fun execute(): ResolutionOutcome {
        // Step 1: read vehicle identity
        val vin = tryRead { obdService.readVin() }
        val calId = tryRead { obdService.readCalibrationId() }
        val cvn = tryRead { obdService.readCalibrationVerificationNumber() }
        val ecuName = tryRead { obdService.readEcuName() }
        val protocol = obdService.sessionState.value.name

        Timber.d("ResolveSupportedPids: VIN=$vin calId=$calId cvn=$cvn ecu=$ecuName")

        // Step 2: build fingerprint
        val fingerprint = VehicleProfileFingerprintBuilder.build(vin, calId, cvn, ecuName)
        Timber.d("ResolveSupportedPids: fingerprint hash=${fingerprint.hash} confidence=${fingerprint.confidence}")

        // Step 3: resolve from cache
        val cacheResult = cache.resolve(fingerprint, vin, calId, cvn, ecuName, protocol)

        return when {
            // No cache data — full discovery needed.
            cacheResult.source == "no_cache" || cacheResult.supportedPidCodes.isEmpty() -> {
                Timber.d("ResolveSupportedPids: no_cache → NeedsDiscovery")
                ResolutionOutcome.NeedsDiscovery(cacheResult.profileId)
            }

            // Fully trusted cache — use immediately.
            cacheResult.source == "cache_hit" && cacheResult.confidence >= 0.85f -> {
                Timber.d("ResolveSupportedPids: cache_hit (confidence=${cacheResult.confidence}) → CacheHit (${cacheResult.supportedPidCodes.size} PIDs)")
                ResolutionOutcome.CacheHit(cacheResult.supportedPidCodes, cacheResult.profileId)
            }

            // Stale or medium-confidence cache — validate a subset of key PIDs.
            else -> {
                Timber.d("ResolveSupportedPids: ${cacheResult.source} — running validation probes")
                validateCache(cacheResult)
            }
        }
    }

    private suspend fun validateCache(cacheResult: CacheResolveResult): ResolutionOutcome {
        val cachedCodes = cacheResult.supportedPidCodes
        // Only probe PIDs that are actually in the cached supported set.
        val probePids = VALIDATION_PIDS.filter { it.code in cachedCodes }
        if (probePids.isEmpty()) {
            Timber.d("ResolveSupportedPids: no validation PIDs in cache → NeedsDiscovery")
            return ResolutionOutcome.NeedsDiscovery(cacheResult.profileId)
        }

        var successes = 0
        val confirmed = mutableListOf<String>()
        for (pid in probePids) {
            val response = try { obdService.query(pid, timeoutMs = 2000L) } catch (e: Exception) { null }
            if (response is ObdResponse.Success) {
                successes++
                confirmed += pid.code
            }
        }

        val passRate = successes.toDouble() / probePids.size
        Timber.d("ResolveSupportedPids: validation pass=$passRate ($successes/${probePids.size})")

        return if (passRate >= VALIDATION_PASS_THRESHOLD) {
            cache.confirmValidation(cacheResult.profileId, confirmed)
            ResolutionOutcome.ValidatedCache(cachedCodes, cacheResult.profileId)
        } else {
            cache.recordValidationFailure(cacheResult.profileId)
            ResolutionOutcome.NeedsDiscovery(cacheResult.profileId)
        }
    }

    private suspend fun <T> tryRead(block: suspend () -> T?): T? = try {
        block()
    } catch (e: Exception) {
        Timber.w(e, "ResolveSupportedPids: identity read failed")
        null
    }
}
