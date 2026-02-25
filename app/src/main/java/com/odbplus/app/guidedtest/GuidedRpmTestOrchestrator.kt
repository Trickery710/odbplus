package com.odbplus.app.guidedtest

import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdResponse
import com.odbplus.core.protocol.ObdService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/** Return value from a single completed stage run. */
data class StageRunResult(
    val stage: TestStage,
    val samples: List<PidSample>,
    /** Wall-clock seconds where RPM was within the stage's target range. */
    val finalTimeInRangeSec: Float,
)

/**
 * Drives the three-stage guided RPM test sequence.
 *
 * Each stage accumulates **wall-clock time** in which the RPM reading falls
 * within the target range. The stage only completes after [TestStage.durationSec]
 * seconds of continuous (or cumulative when the user drifts out) in-range time.
 *
 * Cancel the calling coroutine at any point to abort the test.
 */
class GuidedRpmTestOrchestrator(private val obdService: ObdService) {

    companion object {
        /** Pause between full PID poll cycles. */
        private const val POLL_INTERVAL_MS = 500L

        /** Pause between individual PID commands within one poll cycle. */
        private const val INTER_PID_DELAY_MS = 25L

        /**
         * Settling pause injected between stages (before the first poll cycle of the
         * next stage). Gives the adapter a moment to breathe while the driver revs up,
         * preventing a burst of back-to-back commands from triggering ERROR_RECOVERY.
         */
        private const val STAGE_SETTLE_MS = 500L

        /** Exposed so callers can compute per-sample time weight if needed. */
        const val POLL_INTERVAL_SEC: Float = 0.5f
    }

    private val _liveRpm = MutableStateFlow<Int?>(null)

    /** Current RPM reading, updated after every poll cycle. Observe for live UI display. */
    val liveRpm: StateFlow<Int?> = _liveRpm.asStateFlow()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run all three stages in order and return the collected samples with timing.
     *
     * @param onStageStart   Called once at the beginning of each stage.
     * @param onProgress     Called after every poll cycle with current progress
     *                       (timeInRangeSec capped at [TestStage.durationSec]).
     * @param skipValidation Returns `true` when the user has asked to skip RPM
     *                       validation for the current stage. Checked each cycle.
     * @return List of [StageRunResult] — stage, samples, and final in-range seconds.
     */
    suspend fun runAllStages(
        onStageStart: (TestStage) -> Unit,
        onProgress: (stage: TestStage, timeInRangeSec: Float, inRange: Boolean) -> Unit,
        skipValidation: () -> Boolean,
    ): List<StageRunResult> {
        val results = mutableListOf<StageRunResult>()
        for ((index, stage) in TestStage.entries.withIndex()) {
            // Brief settle between stages so the adapter isn't immediately hit with
            // a new burst of commands the moment the previous stage breaks out of its loop.
            if (index > 0) delay(STAGE_SETTLE_MS)
            onStageStart(stage)
            results.add(runStage(stage, onProgress, skipValidation))
        }
        return results
    }

    /** Attempt VIN read via Mode 09. Returns null on any non-cancellation error. */
    suspend fun readVin(): String? = try { obdService.readVin() } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

    /** Read stored (Mode 03) and pending (Mode 07) DTCs. Returns (stored, pending). */
    suspend fun readDtcs(): Pair<List<String>, List<String>> {
        val stored = try { obdService.readStoredDtcs().map { it.code } } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
        val pending = try { obdService.readPendingDtcs().map { it.code } } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
        return stored to pending
    }

    // ── Stage execution ───────────────────────────────────────────────────────

    private suspend fun runStage(
        stage: TestStage,
        onProgress: (stage: TestStage, timeInRangeSec: Float, inRange: Boolean) -> Unit,
        skipValidation: () -> Boolean,
    ): StageRunResult {
        val samples = mutableListOf<PidSample>()
        var accumulatedInRangeMs = 0L
        var rangeEntryTime: Long? = null
        val requiredMs = stage.durationSec * 1000L

        while (coroutineContext.isActive) {
            val cycleValues = pollPids()
            val now = System.currentTimeMillis()

            // Record all samples for this cycle.
            cycleValues.forEach { (pid, value) ->
                samples.add(PidSample(pid = pid, value = value, timestamp = now, stage = stage))
            }

            val rpm = cycleValues[ObdPid.ENGINE_RPM]?.toInt()
            _liveRpm.value = rpm

            // Determine if we're currently in range.
            val inRange = skipValidation() || (rpm != null && stage.isRpmInRange(rpm))

            // Accumulate wall-clock time while in range; stop accumulating when out.
            if (inRange) {
                if (rangeEntryTime == null) rangeEntryTime = now
            } else {
                val entry = rangeEntryTime
                if (entry != null) {
                    accumulatedInRangeMs += now - entry
                    rangeEntryTime = null
                }
            }

            val totalInRangeMs = accumulatedInRangeMs + (rangeEntryTime?.let { now - it } ?: 0L)
            val timeInRangeSec = (totalInRangeMs / 1000f).coerceAtMost(stage.durationSec.toFloat())

            onProgress(stage, timeInRangeSec, inRange)

            if (totalInRangeMs >= requiredMs) break
            delay(POLL_INTERVAL_MS)
        }

        // Flush any open range entry before returning
        val finalInRangeMs = accumulatedInRangeMs +
                (rangeEntryTime?.let { System.currentTimeMillis() - it } ?: 0L)
        return StageRunResult(
            stage = stage,
            samples = samples,
            finalTimeInRangeSec = (finalInRangeMs / 1000f).coerceAtMost(stage.durationSec.toFloat()),
        )
    }

    // ── PID polling ───────────────────────────────────────────────────────────

    private suspend fun pollPids(): Map<ObdPid, Double?> {
        val result = mutableMapOf<ObdPid, Double?>()
        for ((index, pid) in GUIDED_TEST_PIDS.withIndex()) {
            if (!coroutineContext.isActive) break
            result[pid] = try {
                when (val response = obdService.query(pid)) {
                    is ObdResponse.Success -> response.value
                    else -> null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
            // Small breathing room between commands so the adapter isn't overwhelmed.
            if (index < GUIDED_TEST_PIDS.lastIndex && coroutineContext.isActive) {
                delay(INTER_PID_DELAY_MS)
            }
        }
        return result
    }
}
