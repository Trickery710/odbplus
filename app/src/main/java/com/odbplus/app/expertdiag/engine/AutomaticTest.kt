package com.odbplus.app.expertdiag.engine

import com.odbplus.app.expertdiag.model.AutoTestResult
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdResponse
import com.odbplus.core.protocol.ObdService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Contract for a fully-automatic diagnostic test that runs without any user interaction.
 *
 * Implementations collect PID samples for [sampleDurationMs] milliseconds and analyse
 * the data, returning an [AutoTestResult] immediately.
 *
 * Every [AutomaticTest] must have a stable [id] that matches the test name string used
 * in the knowledge-base CSV `automatic_tests` column.
 */
interface AutomaticTest {
    val id: String
    val name: String
    val description: String

    fun getRequiredPids(): List<ObdPid>

    /** Run the test. Must be called from a coroutine. */
    suspend fun run(obdService: ObdService): AutoTestResult
}

// ── Shared polling helper ─────────────────────────────────────────────────────

internal const val AUTO_INTER_PID_DELAY_MS = 25L
internal const val AUTO_SAMPLE_INTERVAL_MS = 400L
internal const val AUTO_MAX_PIDS = 8      // stay well under global 20-PID limit per auto test

/**
 * Collect [cycleCount] polling cycles for the given [pids].
 * Returns a flat list of `Pair(pid, value)` readings.
 */
internal suspend fun collectSamples(
    obdService: ObdService,
    pids: List<ObdPid>,
    cycleCount: Int = 8,
): List<Pair<ObdPid, Double?>> {
    val results = mutableListOf<Pair<ObdPid, Double?>>()
    val capped = pids.take(AUTO_MAX_PIDS)

    repeat(cycleCount) {
        if (!coroutineContext.isActive) return results
        for ((i, pid) in capped.withIndex()) {
            if (!coroutineContext.isActive) return results
            val value = try {
                when (val r = obdService.query(pid)) {
                    is ObdResponse.Success -> r.value
                    else -> null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
            results += pid to value
            if (i < capped.lastIndex) delay(AUTO_INTER_PID_DELAY_MS)
        }
        delay(AUTO_SAMPLE_INTERVAL_MS)
    }
    return results
}

/** Average of non-null values for a given PID across collected samples. */
internal fun List<Pair<ObdPid, Double?>>.avgFor(pid: ObdPid): Double? =
    filter { it.first == pid }.mapNotNull { it.second }.average().takeIf { !it.isNaN() }

internal fun List<Pair<ObdPid, Double?>>.maxFor(pid: ObdPid): Double? =
    filter { it.first == pid }.mapNotNull { it.second }.maxOrNull()

internal fun List<Pair<ObdPid, Double?>>.minFor(pid: ObdPid): Double? =
    filter { it.first == pid }.mapNotNull { it.second }.minOrNull()

internal fun List<Pair<ObdPid, Double?>>.rangeFor(pid: ObdPid): Double? {
    val max = maxFor(pid) ?: return null
    val min = minFor(pid) ?: return null
    return max - min
}

internal fun List<Pair<ObdPid, Double?>>.valuesFor(pid: ObdPid): List<Double> =
    filter { it.first == pid }.mapNotNull { it.second }

/** Simple confidence estimate based on sample count for a PID. */
internal fun coverageConfidence(
    samples: List<Pair<ObdPid, Double?>>,
    requiredPids: List<ObdPid>,
): Int {
    val covered = requiredPids.count { pid -> samples.any { it.first == pid && it.second != null } }
    val ratio = covered.toFloat() / requiredPids.size.coerceAtLeast(1)
    return (ratio * 80 + 10).toInt().coerceIn(0, 95)
}
