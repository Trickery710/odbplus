package com.odbplus.app.expertdiag.tests.auto

import com.odbplus.app.expertdiag.engine.AutomaticTest
import com.odbplus.app.expertdiag.engine.avgFor
import com.odbplus.app.expertdiag.engine.collectSamples
import com.odbplus.app.expertdiag.engine.coverageConfidence
import com.odbplus.app.expertdiag.engine.rangeFor
import com.odbplus.app.expertdiag.model.AutoTestResult
import com.odbplus.app.expertdiag.model.AutoTestStatus
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdService

/**
 * Automatic test: compare upstream and downstream O2 sensor switching activity
 * to estimate catalytic converter efficiency.
 *
 * A healthy catalyst dampens the downstream (post-cat) O2 sensor oscillation
 * relative to the upstream sensor. If the downstream sensor switches as fast
 * as the upstream, the catalyst is depleted.
 *
 * Switching ratio = downstream_range / upstream_range
 * < 0.4 → catalyst healthy (downstream is damped)
 * 0.4–0.7 → marginal
 * > 0.7 → catalyst efficiency low
 */
class CatalystEfficiencyCheck : AutomaticTest {

    override val id = "CatalystMonitor"
    override val name = "Catalyst Efficiency Check"
    override val description = "Compares upstream vs downstream O2 switching amplitude to estimate catalytic converter health."

    private val PIDS = listOf(
        ObdPid.O2_SENSOR_B1S1_VOLTAGE,   // upstream
        ObdPid.O2_SENSOR_B1S2_VOLTAGE,   // downstream
        ObdPid.ENGINE_RPM,
        ObdPid.ENGINE_COOLANT_TEMP,
    )

    override fun getRequiredPids() = PIDS

    override suspend fun run(obdService: ObdService): AutoTestResult {
        val samples = collectSamples(obdService, PIDS, cycleCount = 14)

        val upstreamRange = samples.rangeFor(ObdPid.O2_SENSOR_B1S1_VOLTAGE)
        val downstreamRange = samples.rangeFor(ObdPid.O2_SENSOR_B1S2_VOLTAGE)
        val avgEct = samples.avgFor(ObdPid.ENGINE_COOLANT_TEMP)
        val avgRpm = samples.avgFor(ObdPid.ENGINE_RPM)

        val readings = mapOf(
            "O2_upstream_swing" to upstreamRange,
            "O2_downstream_swing" to downstreamRange,
            "ECT_°C" to avgEct,
            "RPM" to avgRpm,
        )

        if (upstreamRange == null || downstreamRange == null) {
            return AutoTestResult(
                testId = id, testName = name,
                status = AutoTestStatus.SKIPPED,
                summary = "O2 sensor data incomplete — need both upstream & downstream",
                details = "This vehicle may only have one O2 sensor bank, or sensors are not yet warmed up.",
                pidReadings = readings,
            )
        }

        if (avgEct != null && avgEct < 70.0) {
            return AutoTestResult(
                testId = id, testName = name,
                status = AutoTestStatus.SKIPPED,
                summary = "Engine not at operating temperature — ECT ${"%.0f".format(avgEct)}°C",
                details = "Catalyst test requires operating temperature (≥70°C). Results would be inaccurate.",
                pidReadings = readings,
            )
        }

        if (upstreamRange < 0.3) {
            return AutoTestResult(
                testId = id, testName = name,
                status = AutoTestStatus.WARN,
                summary = "Upstream O2 not switching — cannot evaluate catalyst",
                details = "Upstream sensor swing ${"%.3f".format(upstreamRange)}V is too low. " +
                        "The upstream sensor must be active (open-loop closed-loop) to evaluate downstream damping.",
                pidReadings = readings,
            )
        }

        val ratio = downstreamRange / upstreamRange.coerceAtLeast(0.01)
        val extReadings = readings + mapOf("Switch_ratio" to ratio)

        val (status, summary, details) = when {
            ratio < 0.40 -> Triple(
                AutoTestStatus.PASS,
                "Catalyst healthy — downstream damping ratio ${"%.2f".format(ratio)}",
                "Downstream O2 swings ${"%.3f".format(downstreamRange)}V vs upstream ${"%.3f".format(upstreamRange)}V. " +
                        "Ratio of ${"%.2f".format(ratio)} indicates the catalyst is absorbing oxygen storage events normally."
            )
            ratio < 0.70 -> Triple(
                AutoTestStatus.WARN,
                "Catalyst marginal — downstream ratio ${"%.2f".format(ratio)}",
                "Downstream switching is elevated relative to upstream. Catalyst may be partially depleted. " +
                        "Run guided O2 comparison test and check for exhaust leaks before the downstream sensor."
            )
            else -> Triple(
                AutoTestStatus.FAIL,
                "Catalyst efficiency low — downstream ratio ${"%.2f".format(ratio)}",
                "Downstream O2 swings ${"%.3f".format(downstreamRange)}V (upstream ${"%.3f".format(upstreamRange)}V). " +
                        "Ratio ${"%.2f".format(ratio)} → catalyst not damping post-cat oscillation. " +
                        "Catalytic converter likely depleted or poisoned. Consider replacement."
            )
        }

        return AutoTestResult(
            testId = id, testName = name,
            status = status, summary = summary, details = details,
            pidReadings = extReadings,
            confidenceScore = coverageConfidence(samples, PIDS),
        )
    }
}
