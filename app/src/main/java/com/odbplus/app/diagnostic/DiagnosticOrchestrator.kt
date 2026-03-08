package com.odbplus.app.diagnostic

import com.odbplus.app.diagnostic.model.DiagnosticSample
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

/**
 * Drives the step-by-step execution of any [DiagnosticTest].
 *
 * Runs each [DiagTestStep] for its full [DiagTestStep.durationSeconds], polling
 * all required PIDs every [POLL_INTERVAL_MS] and collecting [DiagnosticSample]s.
 * Cancel the calling coroutine at any time to abort the test.
 *
 * Follows the same ECU-friendly polling strategy as [GuidedRpmTestOrchestrator]:
 * 25 ms between individual PID commands, max 12 PIDs per cycle.
 */
class DiagnosticOrchestrator(private val obdService: ObdService) {

    companion object {
        private const val POLL_INTERVAL_MS = 500L
        private const val INTER_PID_DELAY_MS = 25L

        /**
         * Maximum PIDs queried per cycle. Keeps each cycle under ~300 ms on
         * ISO/KWP buses, leaving headroom before the next 500 ms tick.
         */
        private const val MAX_PIDS_PER_CYCLE = 12

        /** Brief settling pause between steps — mirrors GuidedRpmTestOrchestrator. */
        private const val STEP_SETTLE_MS = 500L
    }

    private val _liveValues = MutableStateFlow<Map<ObdPid, Double?>>(emptyMap())

    /** Most recent PID values from the last poll cycle. Observe for live UI display. */
    val liveValues: StateFlow<Map<ObdPid, Double?>> = _liveValues.asStateFlow()

    /**
     * Execute all steps in [test] in order and return the full sample list.
     *
     * @param onStepStart  Called once at the beginning of each step with its index.
     * @param onProgress   Called after every poll cycle with the remaining countdown
     *                     (seconds) and latest PID values.
     */
    suspend fun runTest(
        test: DiagnosticTest,
        onStepStart: (stepIndex: Int) -> Unit,
        onProgress: (stepIndex: Int, remainingSeconds: Int, liveValues: Map<ObdPid, Double?>) -> Unit,
    ): List<DiagnosticSample> {
        val allSamples = mutableListOf<DiagnosticSample>()
        val steps = test.getSteps()
        // Cap to MAX_PIDS_PER_CYCLE to avoid ECU overload on slow buses.
        val pids = test.getRequiredPids().take(MAX_PIDS_PER_CYCLE)

        for ((stepIndex, step) in steps.withIndex()) {
            if (!coroutineContext.isActive) break
            if (stepIndex > 0) delay(STEP_SETTLE_MS)

            onStepStart(stepIndex)

            val stepEndMs = System.currentTimeMillis() + step.durationSeconds * 1000L

            while (coroutineContext.isActive) {
                val cycleValues = pollPids(pids)
                val now = System.currentTimeMillis()

                _liveValues.value = cycleValues

                // Stamp each reading with this step's index.
                cycleValues.forEach { (pid, value) ->
                    allSamples.add(
                        DiagnosticSample(
                            timestamp = now,
                            stepIndex = stepIndex,
                            pid = pid,
                            value = value,
                        )
                    )
                }

                val remainingSeconds = ((stepEndMs - now) / 1000L).coerceAtLeast(0L).toInt()
                onProgress(stepIndex, remainingSeconds, cycleValues)

                if (now >= stepEndMs) break
                delay(POLL_INTERVAL_MS)
            }
        }

        return allSamples
    }

    // ── PID polling ───────────────────────────────────────────────────────────

    private suspend fun pollPids(pids: List<ObdPid>): Map<ObdPid, Double?> {
        val result = mutableMapOf<ObdPid, Double?>()
        for ((index, pid) in pids.withIndex()) {
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
            if (index < pids.lastIndex && coroutineContext.isActive) {
                delay(INTER_PID_DELAY_MS)
            }
        }
        return result
    }
}
