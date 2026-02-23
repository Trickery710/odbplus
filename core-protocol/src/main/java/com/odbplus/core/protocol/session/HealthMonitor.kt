package com.odbplus.core.protocol.session

import com.odbplus.core.protocol.adapter.DeviceProfile
import timber.log.Timber

/**
 * Tracks adapter health over time and triggers safe-mode / recovery.
 *
 * ## Health Score
 * Score starts at 100 and is decremented by events:
 * | Event                      | Penalty |
 * |----------------------------|---------|
 * | Timeout                    | -10     |
 * | Corrupt frame              | -8      |
 * | Unexpected echo            | -4      |
 * | Protocol switch failure    | -15     |
 *
 * Each successful command recovers +3 points.
 *
 * ## Safe Mode
 * When health < 40 the session enters safe mode:
 * - Poll rate capped at 4 PIDs/sec
 * - Inter-command delay increased to 100 ms
 * - Retries doubled
 * - Large payload requests suppressed
 *
 * When health < 10 a full state reset is triggered.
 */
class HealthMonitor(private var profile: DeviceProfile) {

    /** Current health score (0–100). */
    val healthScore: Int get() = profile.healthScore

    /** True when the health score is below [DeviceProfile.SAFE_MODE_THRESHOLD]. */
    val isInSafeMode: Boolean get() = healthScore < DeviceProfile.SAFE_MODE_THRESHOLD

    /** True when health is critically low (< 10) — full session reset required. */
    val requiresReset: Boolean get() = healthScore < CRITICAL_THRESHOLD

    private var successStreak = 0

    // ── Event handlers ────────────────────────────────────────────────────────

    fun onTimeout() = applyPenalty(DeviceProfile.PENALTY_TIMEOUT, "timeout") {
        successStreak = 0
    }

    fun onCorruptFrame() = applyPenalty(DeviceProfile.PENALTY_CORRUPT_FRAME, "corrupt frame") {
        successStreak = 0
    }

    fun onUnexpectedEcho() = applyPenalty(DeviceProfile.PENALTY_UNEXPECTED_ECHO, "unexpected echo") {
        successStreak = 0
    }

    fun onProtocolSwitchFailure() = applyPenalty(
        DeviceProfile.PENALTY_PROTOCOL_SWITCH_FAILURE, "protocol switch failure"
    ) {
        successStreak = 0
    }

    fun onCommandSuccess() {
        successStreak++
        val gain = when {
            successStreak >= 5 -> DeviceProfile.RECOVERY_PER_SUCCESS * 2
            else               -> DeviceProfile.RECOVERY_PER_SUCCESS
        }
        val old = profile.healthScore
        profile = profile.copy(healthScore = minOf(old + gain, DeviceProfile.MAX_HEALTH))
        Timber.d("Health +$gain → ${profile.healthScore} (streak=$successStreak)")
    }

    /**
     * Inject an updated profile reference (e.g. after a driver re-initialises it).
     */
    fun updateProfile(newProfile: DeviceProfile) {
        profile = newProfile
    }

    /**
     * Current profile with live health score.
     */
    fun currentProfile(): DeviceProfile = profile

    /**
     * Effective max poll rate, accounting for safe mode.
     */
    fun effectiveMaxPollRateHz(): Int =
        if (isInSafeMode) SAFE_MODE_MAX_POLL_HZ
        else profile.capabilities.maxPollRateHz

    /**
     * Effective inter-command delay in ms, accounting for safe mode.
     */
    fun effectiveDelayMs(): Long =
        if (isInSafeMode) maxOf(profile.capabilities.recommendedDelayMs, SAFE_MODE_DELAY_MS)
        else profile.capabilities.recommendedDelayMs

    // ── Private ───────────────────────────────────────────────────────────────

    private inline fun applyPenalty(penalty: Int, reason: String, extra: () -> Unit) {
        extra()
        val old = profile.healthScore
        val newScore = maxOf(0, old - penalty)
        profile = profile.copy(healthScore = newScore)
        val level = when {
            newScore < CRITICAL_THRESHOLD      -> "CRITICAL"
            newScore < DeviceProfile.SAFE_MODE_THRESHOLD -> "SAFE MODE"
            else                               -> "OK"
        }
        Timber.w("Health -$penalty ($reason): $old → $newScore [$level]")
    }

    companion object {
        const val CRITICAL_THRESHOLD    = 10
        const val SAFE_MODE_MAX_POLL_HZ = 4
        const val SAFE_MODE_DELAY_MS    = 100L
    }
}
