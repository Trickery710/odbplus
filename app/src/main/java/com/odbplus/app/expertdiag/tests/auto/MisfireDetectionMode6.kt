package com.odbplus.app.expertdiag.tests.auto

import com.odbplus.app.expertdiag.engine.AutomaticTest
import com.odbplus.app.expertdiag.engine.avgFor
import com.odbplus.app.expertdiag.engine.collectSamples
import com.odbplus.app.expertdiag.engine.coverageConfidence
import com.odbplus.app.expertdiag.engine.valuesFor
import com.odbplus.app.expertdiag.model.AutoTestResult
import com.odbplus.app.expertdiag.model.AutoTestStatus
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdService

/**
 * Automatic test: detect misfires by analysing RPM stability and engine load
 * consistency.
 *
 * Mode 06 misfire counters are not universally supported over ELM327 at the
 * OBD-II abstraction layer, so this test uses an RPM jitter analysis as a proxy:
 * high cycle-to-cycle RPM variance at idle is strongly correlated with misfires.
 *
 * A standard deviation > 50 RPM at idle is flagged as a warning.
 * > 100 RPM standard deviation is a misfire indicator.
 */
class MisfireDetectionMode6 : AutomaticTest {

    override val id = "Mode6Misfire"
    override val name = "Misfire Detection (RPM Stability)"
    override val description = "Analyses RPM jitter at idle as a misfire proxy. High standard deviation indicates cylinder misfires."

    private val PIDS = listOf(
        ObdPid.ENGINE_RPM,
        ObdPid.ENGINE_LOAD,
        ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
    )

    override fun getRequiredPids() = PIDS

    override suspend fun run(obdService: ObdService): AutoTestResult {
        val samples = collectSamples(obdService, PIDS, cycleCount = 15)

        val rpmValues = samples.valuesFor(ObdPid.ENGINE_RPM)
        val avgRpm = samples.avgFor(ObdPid.ENGINE_RPM)
        val avgLoad = samples.avgFor(ObdPid.ENGINE_LOAD)

        val readings = mapOf("RPM_avg" to avgRpm, "Load_%" to avgLoad)

        if (rpmValues.size < 5 || avgRpm == null) {
            return AutoTestResult(
                testId = id, testName = name,
                status = AutoTestStatus.SKIPPED,
                summary = "Insufficient RPM data for jitter analysis",
                details = "Need at least 5 RPM readings. Engine may not be running.",
                pidReadings = readings,
            )
        }

        val mean = rpmValues.average()
        val stdDev = Math.sqrt(rpmValues.map { (it - mean) * (it - mean) }.average())
        val isIdle = mean < 1200

        val extReadings = readings + mapOf("RPM_stdDev" to stdDev)

        val (status, summary, details) = when {
            !isIdle -> Triple(
                AutoTestStatus.SKIPPED,
                "RPM too high for idle misfire analysis — ${"%.0f".format(mean)} RPM",
                "This test evaluates idle RPM stability. Engine is not at idle. Result not applicable."
            )
            stdDev > 100 -> Triple(
                AutoTestStatus.FAIL,
                "RPM jitter severe — σ=${"%.0f".format(stdDev)} RPM at ${"%.0f".format(mean)} RPM idle",
                "Standard deviation of ${"%.0f".format(stdDev)} RPM indicates significant cylinder misfires or " +
                        "combustion irregularity at idle. Check spark plugs, ignition coils, injectors, and compression."
            )
            stdDev > 50 -> Triple(
                AutoTestStatus.WARN,
                "RPM jitter elevated — σ=${"%.0f".format(stdDev)} RPM at ${"%.0f".format(mean)} RPM idle",
                "Standard deviation of ${"%.0f".format(stdDev)} RPM suggests intermittent misfire or rough idle. " +
                        "Common causes: worn spark plugs, marginal coil, partially clogged injector."
            )
            else -> Triple(
                AutoTestStatus.PASS,
                "RPM stable — σ=${"%.0f".format(stdDev)} RPM at ${"%.0f".format(mean)} RPM idle",
                "Idle RPM standard deviation ${"%.0f".format(stdDev)} is within normal range. " +
                        "No RPM-based misfire evidence detected."
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
