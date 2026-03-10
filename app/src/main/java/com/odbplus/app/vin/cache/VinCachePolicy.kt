package com.odbplus.app.vin.cache

import com.odbplus.app.data.db.entity.VehicleVinCachePolicyEntity
import com.odbplus.app.vin.domain.VerificationStatus

/**
 * Encapsulates cache freshness rules for VIN decode results.
 *
 * Rules (default values):
 * - Verified decode: valid for [VERIFIED_TTL_MS] (30 days)
 * - Partial decode: valid for [PARTIAL_TTL_MS] (7 days)
 * - Failed attempts: exponential backoff, capped at [MAX_BACKOFF_MS] (24 hours)
 * - Manual refresh: bypasses cache but enforces [RATE_LIMIT_MS] between manual requests
 */
object VinCachePolicy {

    val VERIFIED_TTL_MS: Long = 30L * 24 * 60 * 60 * 1000   // 30 days
    val PARTIAL_TTL_MS: Long  =  7L * 24 * 60 * 60 * 1000   //  7 days
    val MAX_BACKOFF_MS: Long  = 24L * 60 * 60 * 1000         // 24 hours
    val RATE_LIMIT_MS: Long   =  5L * 60 * 1000              //  5 minutes
    private const val BACKOFF_BASE_MS = 60_000L               //  1 minute base

    data class CacheDecision(
        val shouldUseCached: Boolean,
        val shouldRefreshInBackground: Boolean,
        val reason: String
    )

    /**
     * Evaluate whether the cached record should be used or a fresh decode queued.
     *
     * @param entity       The cache policy record from Room. Null = VIN not yet seen.
     * @param status       Verification status of the cached decode, if available.
     * @param forceRefresh True if the user explicitly requested a refresh.
     * @param nowMs        Current time in milliseconds (injectable for testing).
     */
    fun evaluate(
        entity: VehicleVinCachePolicyEntity?,
        status: VerificationStatus?,
        forceRefresh: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): CacheDecision {
        if (entity == null) {
            return CacheDecision(
                shouldUseCached = false,
                shouldRefreshInBackground = true,
                reason = "No cache record — first decode"
            )
        }

        // Respect rate limit even for forced refreshes
        val lastAttemptAge = nowMs - entity.lastAttemptAt
        if (forceRefresh && lastAttemptAge < RATE_LIMIT_MS) {
            val remainingSec = (RATE_LIMIT_MS - lastAttemptAge) / 1000
            return CacheDecision(
                shouldUseCached = true,
                shouldRefreshInBackground = false,
                reason = "Rate limited — try again in ${remainingSec}s"
            )
        }

        if (forceRefresh) {
            return CacheDecision(
                shouldUseCached = false,
                shouldRefreshInBackground = true,
                reason = "Manual refresh requested"
            )
        }

        // If last attempt was a failure, apply exponential backoff
        if (entity.lastSuccessAt == null || entity.lastSuccessAt < entity.lastFailureAt ?: 0L) {
            val backoff = computeBackoff(entity.retryCount)
            val nextRetry = (entity.lastAttemptAt) + backoff
            if (nowMs < nextRetry) {
                return CacheDecision(
                    shouldUseCached = entity.lastSuccessAt != null,
                    shouldRefreshInBackground = false,
                    reason = "Backoff active (retry #${entity.retryCount}) — next attempt in ${(nextRetry - nowMs) / 1000}s"
                )
            }
        }

        // Determine TTL based on verification status
        val ttl = when (status) {
            VerificationStatus.VERIFIED, VerificationStatus.MOSTLY_VERIFIED -> VERIFIED_TTL_MS
            else -> PARTIAL_TTL_MS
        }

        val lastSuccess = entity.lastSuccessAt ?: 0L
        val isExpired = (nowMs - lastSuccess) > ttl
        val isStale = entity.isStale || isExpired

        return when {
            !isStale -> CacheDecision(
                shouldUseCached = true,
                shouldRefreshInBackground = false,
                reason = "Cache fresh (${ttl / 86_400_000}d TTL)"
            )
            isStale && entity.lastSuccessAt != null -> CacheDecision(
                shouldUseCached = true,
                shouldRefreshInBackground = true,
                reason = "Stale cache — returning cached result, refreshing in background"
            )
            else -> CacheDecision(
                shouldUseCached = false,
                shouldRefreshInBackground = true,
                reason = "Cache expired and no successful decode on record"
            )
        }
    }

    /**
     * Compute exponential backoff delay for failed attempts.
     * Formula: base * 2^retryCount, capped at [MAX_BACKOFF_MS].
     */
    fun computeBackoff(retryCount: Int): Long {
        // Cap exponent at 20 (60_000 * 2^20 ≈ 63GB ms >> MAX_BACKOFF_MS) so the min() always
        // clamps correctly without Long overflow risk (2^20 = 1_048_576, well within Long range).
        val exp = minOf(retryCount, 20)
        return minOf(BACKOFF_BASE_MS * (1L shl exp), MAX_BACKOFF_MS)
    }

    /**
     * Build an updated cache policy entity to persist after a decode attempt.
     */
    fun buildUpdatedEntity(
        vin: String,
        existing: VehicleVinCachePolicyEntity?,
        succeeded: Boolean,
        ttlMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): VehicleVinCachePolicyEntity {
        val retryCount = if (!succeeded) (existing?.retryCount ?: 0) + 1 else 0
        return VehicleVinCachePolicyEntity(
            vin = vin,
            lastAttemptAt = nowMs,
            lastSuccessAt = if (succeeded) nowMs else existing?.lastSuccessAt,
            lastFailureAt = if (!succeeded) nowMs else existing?.lastFailureAt,
            retryCount = retryCount,
            cacheExpiresAt = if (succeeded) nowMs + ttlMs else existing?.cacheExpiresAt ?: 0L,
            isStale = !succeeded,
            shouldRefresh = !succeeded
        )
    }
}
