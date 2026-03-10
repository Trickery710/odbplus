package com.odbplus.app.vin

import com.odbplus.app.data.db.entity.VehicleVinCachePolicyEntity
import com.odbplus.app.vin.cache.VinCachePolicy
import com.odbplus.app.vin.domain.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VinCachePolicyTest {

    private val testVin = "1HGBH41JXMN109186"
    private val now = System.currentTimeMillis()

    private fun freshEntity(
        lastSuccessAt: Long? = now - 1_000,
        retryCount: Int = 0,
        isStale: Boolean = false
    ) = VehicleVinCachePolicyEntity(
        vin = testVin,
        lastAttemptAt = now - 1_000,
        lastSuccessAt = lastSuccessAt,
        lastFailureAt = null,
        retryCount = retryCount,
        cacheExpiresAt = now + VinCachePolicy.VERIFIED_TTL_MS,
        isStale = isStale,
        shouldRefresh = false
    )

    // ── No cache record ───────────────────────────────────────────────────────

    @Test
    fun `null entity triggers decode`() {
        val decision = VinCachePolicy.evaluate(null, null, nowMs = now)
        assertFalse(decision.shouldUseCached)
        assertTrue(decision.shouldRefreshInBackground)
    }

    // ── Cache hit ─────────────────────────────────────────────────────────────

    @Test
    fun `fresh verified cache is used without refresh`() {
        val entity = freshEntity()
        val decision = VinCachePolicy.evaluate(entity, VerificationStatus.VERIFIED, nowMs = now)
        assertTrue(decision.shouldUseCached)
        assertFalse(decision.shouldRefreshInBackground)
    }

    @Test
    fun `fresh partial cache is used without refresh`() {
        val entity = freshEntity()
        val decision = VinCachePolicy.evaluate(entity, VerificationStatus.PARTIAL, nowMs = now)
        assertTrue(decision.shouldUseCached)
        assertFalse(decision.shouldRefreshInBackground)
    }

    // ── Stale cache ───────────────────────────────────────────────────────────

    @Test
    fun `stale cache returns cached but triggers background refresh`() {
        val entity = freshEntity(
            lastSuccessAt = now - VinCachePolicy.VERIFIED_TTL_MS - 1,
            isStale = true
        ).copy(cacheExpiresAt = now - 1)
        val decision = VinCachePolicy.evaluate(entity, VerificationStatus.VERIFIED, nowMs = now)
        assertTrue(decision.shouldUseCached)
        assertTrue(decision.shouldRefreshInBackground)
    }

    @Test
    fun `expired cache with no success triggers fresh decode`() {
        val entity = VehicleVinCachePolicyEntity(
            vin = testVin,
            lastAttemptAt = now - VinCachePolicy.VERIFIED_TTL_MS * 2,
            lastSuccessAt = null,
            lastFailureAt = now - VinCachePolicy.VERIFIED_TTL_MS * 2,
            retryCount = 1,
            cacheExpiresAt = now - 1000,
            isStale = true,
            shouldRefresh = true
        )
        val decision = VinCachePolicy.evaluate(entity, null, nowMs = now)
        assertFalse(decision.shouldUseCached)
        assertTrue(decision.shouldRefreshInBackground)
    }

    // ── Backoff ───────────────────────────────────────────────────────────────

    @Test
    fun `backoff is applied after failures`() {
        val entity = VehicleVinCachePolicyEntity(
            vin = testVin,
            lastAttemptAt = now - 30_000, // 30s ago
            lastSuccessAt = null,
            lastFailureAt = now - 30_000,
            retryCount = 2,
            cacheExpiresAt = 0L,
            isStale = true,
            shouldRefresh = true
        )
        // Backoff for retry 2 = 60s * 2^2 = 240s. Only 30s elapsed → still in backoff
        val decision = VinCachePolicy.evaluate(entity, null, nowMs = now)
        assertFalse(decision.shouldRefreshInBackground)
    }

    @Test
    fun `backoff expires and allows retry`() {
        val entity = VehicleVinCachePolicyEntity(
            vin = testVin,
            lastAttemptAt = now - VinCachePolicy.MAX_BACKOFF_MS - 1,
            lastSuccessAt = null,
            lastFailureAt = now - VinCachePolicy.MAX_BACKOFF_MS - 1,
            retryCount = 20, // Very high retry count → capped backoff
            cacheExpiresAt = 0L,
            isStale = true,
            shouldRefresh = true
        )
        val decision = VinCachePolicy.evaluate(entity, null, nowMs = now)
        assertTrue(decision.shouldRefreshInBackground)
    }

    @Test
    fun `backoff is capped at MAX_BACKOFF_MS`() {
        // Exponent cap prevents overflow
        val backoff = VinCachePolicy.computeBackoff(100)
        assertEquals(VinCachePolicy.MAX_BACKOFF_MS, backoff)
    }

    // ── Force refresh ─────────────────────────────────────────────────────────

    @Test
    fun `force refresh bypasses stale check`() {
        // lastAttemptAt must be outside the rate-limit window (> RATE_LIMIT_MS ago)
        val entity = freshEntity().copy(lastAttemptAt = now - VinCachePolicy.RATE_LIMIT_MS - 1_000)
        val decision = VinCachePolicy.evaluate(entity, VerificationStatus.VERIFIED,
            forceRefresh = true, nowMs = now)
        assertFalse(decision.shouldUseCached)
        assertTrue(decision.shouldRefreshInBackground)
    }

    @Test
    fun `force refresh is rate-limited`() {
        // lastAttemptAt = 10s ago → within RATE_LIMIT_MS (5min)
        val entity = freshEntity().copy(lastAttemptAt = now - 10_000)
        val decision = VinCachePolicy.evaluate(entity, VerificationStatus.VERIFIED,
            forceRefresh = true, nowMs = now)
        // Rate limited — still uses cache
        assertTrue(decision.shouldUseCached)
        assertFalse(decision.shouldRefreshInBackground)
        assertTrue(decision.reason.contains("Rate limited", ignoreCase = true))
    }

    // ── Build updated entity ──────────────────────────────────────────────────

    @Test
    fun `successful decode resets retry count`() {
        val updated = VinCachePolicy.buildUpdatedEntity(
            vin = testVin,
            existing = freshEntity(retryCount = 5),
            succeeded = true,
            ttlMs = VinCachePolicy.VERIFIED_TTL_MS,
            nowMs = now
        )
        assertEquals(0, updated.retryCount)
        assertFalse(updated.isStale)
    }

    @Test
    fun `failed decode increments retry count`() {
        val updated = VinCachePolicy.buildUpdatedEntity(
            vin = testVin,
            existing = freshEntity(retryCount = 2),
            succeeded = false,
            ttlMs = 0L,
            nowMs = now
        )
        assertEquals(3, updated.retryCount)
        assertTrue(updated.isStale)
    }
}
