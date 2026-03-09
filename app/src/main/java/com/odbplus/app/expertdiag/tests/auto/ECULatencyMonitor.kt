package com.odbplus.app.expertdiag.tests.auto

import com.odbplus.app.expertdiag.engine.AutomaticTest
import com.odbplus.app.expertdiag.engine.AUTO_INTER_PID_DELAY_MS
import com.odbplus.app.expertdiag.model.AutoTestResult
import com.odbplus.app.expertdiag.model.AutoTestStatus
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdResponse
import com.odbplus.core.protocol.ObdService
import kotlinx.coroutines.CancellationException

/**
 * Automatic test: measure round-trip latency for a simple PID query.
 *
 * Issues 10 consecutive RPM queries and measures the mean and max response time.
 * This baseline latency is used by the engine to adapt poll rates.
 *
 * Thresholds:
 *   < 80 ms  — excellent (CAN 11-bit)
 *   < 200 ms — good (KWP2000 fast)
 *   < 500 ms — fair (ISO 9141 or congested bus)
 *   ≥ 500 ms — slow — reduce polling rate automatically
 */
class ECULatencyMonitor : AutomaticTest {

    override val id = "ECULatencyMonitor"
    override val name = "ECU Latency Monitor"
    override val description = "Measures round-trip OBD response latency. Used to calibrate adaptive polling rate."

    override fun getRequiredPids() = listOf(ObdPid.ENGINE_RPM)

    override suspend fun run(obdService: ObdService): AutoTestResult {
        val latencies = mutableListOf<Long>()

        repeat(10) {
            val t0 = System.currentTimeMillis()
            try {
                obdService.query(ObdPid.ENGINE_RPM)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            latencies += System.currentTimeMillis() - t0
            kotlinx.coroutines.delay(AUTO_INTER_PID_DELAY_MS)
        }

        if (latencies.isEmpty()) {
            return AutoTestResult(
                testId = id, testName = name,
                status = AutoTestStatus.ERROR,
                summary = "Could not measure ECU latency",
                details = "All latency probes failed — ECU may not be responding.",
            )
        }

        val mean = latencies.average()
        val max = latencies.max()
        val readings = mapOf("Mean_ms" to mean, "Max_ms" to max.toDouble())

        val (status, summary, details) = when {
            mean < 80 -> Triple(
                AutoTestStatus.PASS,
                "ECU latency excellent — mean ${"%.0f".format(mean)} ms",
                "Mean latency ${"%.0f".format(mean)} ms / max $max ms. CAN bus responding quickly. Full polling rate enabled."
            )
            mean < 200 -> Triple(
                AutoTestStatus.PASS,
                "ECU latency good — mean ${"%.0f".format(mean)} ms",
                "Mean latency ${"%.0f".format(mean)} ms / max $max ms. Typical for KWP2000 or slower CAN. " +
                        "Standard polling rate of 500 ms is appropriate."
            )
            mean < 500 -> Triple(
                AutoTestStatus.WARN,
                "ECU latency high — mean ${"%.0f".format(mean)} ms",
                "Mean latency ${"%.0f".format(mean)} ms / max $max ms. Slower protocol (ISO 9141) or congested bus. " +
                        "Polling rate automatically reduced to avoid overwhelming ECU."
            )
            else -> Triple(
                AutoTestStatus.FAIL,
                "ECU latency critical — mean ${"%.0f".format(mean)} ms",
                "Mean latency ${"%.0f".format(mean)} ms / max $max ms. Bus may be congested or ECU overloaded. " +
                        "Only high-priority PIDs queried. Consider reducing active PIDs."
            )
        }

        return AutoTestResult(
            testId = id, testName = name,
            status = status, summary = summary, details = details,
            pidReadings = readings,
            confidenceScore = 95,
        )
    }
}
