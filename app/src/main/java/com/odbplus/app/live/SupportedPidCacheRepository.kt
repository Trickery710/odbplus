package com.odbplus.app.live

import com.odbplus.app.data.db.dao.SupportedPidDao
import com.odbplus.app.data.db.dao.VehicleProfileDao
import com.odbplus.app.data.db.entity.SupportedPidEntity
import com.odbplus.app.data.db.entity.VehicleProfileEntity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Minimum confidence to trust the cache without rediscovery. */
private const val TRUST_THRESHOLD = 0.7f

/** Stale after 30 days without validation. */
private const val STALE_MS = 30L * 24 * 60 * 60 * 1000

data class CacheResolveResult(
    val profileId: Long,
    val supportedPidCodes: Set<String>,
    val confidence: Float,
    val source: String          // "cache_hit" | "stale_cache" | "no_cache"
)

/**
 * Manages the vehicle-profile / supported-PID Room cache.
 *
 * This repository is the single authority for:
 *  - looking up a profile by fingerprint
 *  - loading the cached supported-PID set
 *  - persisting a fresh discovery result
 *  - downgrading confidence on failed validations
 */
@Singleton
class SupportedPidCacheRepository @Inject constructor(
    private val profileDao: VehicleProfileDao,
    private val pidDao: SupportedPidDao
) {

    /**
     * Attempt to load a trusted cached PID set for the given fingerprint.
     *
     * Returns a [CacheResolveResult] describing what was found.
     * Never throws — any DB error returns a "no_cache" result.
     */
    suspend fun resolve(
        fingerprint: VehicleProfileFingerprintBuilder.Fingerprint,
        vin: String?,
        calibrationId: String?,
        cvn: String?,
        ecuFirmwareId: String?,
        protocol: String?
    ): CacheResolveResult {
        return try {
            resolveInternal(fingerprint, vin, calibrationId, cvn, ecuFirmwareId, protocol)
        } catch (e: Exception) {
            Timber.w(e, "SupportedPidCache: DB error during resolve")
            CacheResolveResult(-1L, emptySet(), 0f, "no_cache")
        }
    }

    private suspend fun resolveInternal(
        fingerprint: VehicleProfileFingerprintBuilder.Fingerprint,
        vin: String?,
        calibrationId: String?,
        cvn: String?,
        ecuFirmwareId: String?,
        protocol: String?
    ): CacheResolveResult {
        if (fingerprint.confidence == 0f) {
            return CacheResolveResult(-1L, emptySet(), 0f, "no_cache")
        }

        val existing = profileDao.getByFingerprint(fingerprint.hash)

        if (existing == null) {
            // No profile for this fingerprint — insert placeholder, then need discovery.
            val newId = insertProfile(fingerprint, vin, calibrationId, cvn, ecuFirmwareId, protocol)
            return CacheResolveResult(newId, emptySet(), fingerprint.confidence, "no_cache")
        }

        // Profile found — check if it's stale.
        val now = System.currentTimeMillis()
        val isStale = (now - (existing.lastValidated ?: existing.lastSeen)) > STALE_MS
        val supported = pidDao.getSupportedPids(existing.id)

        if (supported.isEmpty()) {
            profileDao.updateLastSeen(existing.id, now, existing.cacheConfidence, "no_cache")
            return CacheResolveResult(existing.id, emptySet(), existing.cacheConfidence, "no_cache")
        }

        val pidCodes = supported.map { it.pid }.toSet()
        val source = if (isStale) "stale_cache" else "cache_hit"
        profileDao.updateLastSeen(existing.id, now, existing.cacheConfidence, source)

        Timber.d("SupportedPidCache: $source — ${pidCodes.size} PIDs, confidence=${existing.cacheConfidence}")
        return CacheResolveResult(existing.id, pidCodes, existing.cacheConfidence, source)
    }

    /**
     * Persist the result of a fresh PID discovery run.
     * Replaces any existing PID entries for this profile.
     */
    suspend fun saveDiscovery(
        profileId: Long,
        supportedCodes: Set<String>,
        source: String = "bitmap_discovery"
    ) {
        if (profileId < 0) return
        try {
            val now = System.currentTimeMillis()
            pidDao.deleteForProfile(profileId)
            val entities = supportedCodes.map { code ->
                SupportedPidEntity(
                    vehicleProfileId = profileId,
                    pid = code,
                    supported = true,
                    source = source,
                    firstSeen = now,
                    lastConfirmed = now
                )
            }
            pidDao.insertAll(entities)
            profileDao.updateLastSeen(profileId, now, 1.0f, "discovery")
            Timber.d("SupportedPidCache: saved ${entities.size} supported PIDs for profile $profileId")
        } catch (e: Exception) {
            Timber.w(e, "SupportedPidCache: failed to save discovery result")
        }
    }

    /**
     * Record that a subset of cached PIDs were successfully probed during
     * lightweight validation, reinforcing the cache confidence.
     */
    suspend fun confirmValidation(profileId: Long, confirmedCodes: List<String>) {
        if (profileId < 0 || confirmedCodes.isEmpty()) return
        try {
            val now = System.currentTimeMillis()
            pidDao.updateLastConfirmed(profileId, confirmedCodes, now)
            profileDao.updateLastSeen(profileId, now, 1.0f, "validated_cache")
        } catch (e: Exception) {
            Timber.w(e, "SupportedPidCache: confirmValidation failed")
        }
    }

    /** Called when a validation probe finds unexpected failures. */
    suspend fun recordValidationFailure(profileId: Long) {
        if (profileId < 0) return
        try {
            profileDao.incrementFailedValidation(profileId)
        } catch (e: Exception) {
            Timber.w(e, "SupportedPidCache: recordValidationFailure failed")
        }
    }

    private suspend fun insertProfile(
        fingerprint: VehicleProfileFingerprintBuilder.Fingerprint,
        vin: String?,
        calibrationId: String?,
        cvn: String?,
        ecuFirmwareId: String?,
        protocol: String?
    ): Long {
        val now = System.currentTimeMillis()
        val entity = VehicleProfileEntity(
            vin = vin,
            protocol = protocol,
            ecuFirmwareId = ecuFirmwareId,
            calibrationId = calibrationId,
            cvn = cvn,
            fingerprintHash = fingerprint.hash,
            firstSeen = now,
            lastSeen = now,
            validationSource = "discovery",
            cacheConfidence = fingerprint.confidence
        )
        return profileDao.insert(entity)
    }
}
