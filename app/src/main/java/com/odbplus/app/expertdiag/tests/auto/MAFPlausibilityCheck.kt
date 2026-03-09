package com.odbplus.app.expertdiag.tests.auto

import com.odbplus.app.expertdiag.engine.AutomaticTest
import com.odbplus.app.expertdiag.engine.avgFor
import com.odbplus.app.expertdiag.engine.collectSamples
import com.odbplus.app.expertdiag.engine.coverageConfidence
import com.odbplus.app.expertdiag.model.AutoTestResult
import com.odbplus.app.expertdiag.model.AutoTestStatus
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdService

/**
 * Automatic test: verify MAF sensor output is plausible for the engine's
 * current RPM and load.
 *
 * Rule of thumb: at idle (~750 RPM) a typical 4-cylinder engine flows
 * 2–8 g/s. At 2000 RPM expect 8–20 g/s. Values well outside these bands
 * indicate a contaminated or failed MAF, or an air metering problem.
 */
class MAFPlausibilityCheck : AutomaticTest {

    override val id = "MAF_Plausibility"
    override val name = "MAF Plausibility Check"
    override val description = "Validates MAF sensor reading against RPM and engine load at idle."

    private val PIDS = listOf(
        ObdPid.MAF_FLOW_RATE,
        ObdPid.ENGINE_RPM,
        ObdPid.ENGINE_LOAD,
        ObdPid.INTAKE_MANIFOLD_PRESSURE,
    )

    override fun getRequiredPids() = PIDS

    override suspend fun run(obdService: ObdService): AutoTestResult {
        val samples = collectSamples(obdService, PIDS, cycleCount = 8)

        val maf = samples.avgFor(ObdPid.MAF_FLOW_RATE)
        val rpm = samples.avgFor(ObdPid.ENGINE_RPM)
        val load = samples.avgFor(ObdPid.ENGINE_LOAD)

        val readings = mapOf("MAF_g/s" to maf, "RPM" to rpm, "Load_%" to load)

        if (maf == null || rpm == null) {
            return AutoTestResult(
                testId = id, testName = name,
                status = AutoTestStatus.SKIPPED,
                summary = "MAF or RPM data unavailable — PID not supported",
                details = "Cannot evaluate MAF plausibility without both MAF flow and RPM.",
                pidReadings = readings,
            )
        }

        // Expected idle MAF: roughly RPM/400 g/s (heuristic for typical 4-cyl 2L engine)
        val expectedMin = (rpm / 600.0).coerceAtLeast(1.5)
        val expectedMax = (rpm / 150.0).coerceAtMost(50.0)

        val (status, summary, details) = when {
            maf < expectedMin * 0.6 -> Triple(
                AutoTestStatus.FAIL,
                "MAF reading critically low — ${"%.2f".format(maf)} g/s at ${"%.0f".format(rpm)} RPM",
                "Expected ~${"%.1f".format(expectedMin)}–${"%.1f".format(expectedMax)} g/s. " +
                        "Reading of ${"%.2f".format(maf)} g/s is below minimum threshold. " +
                        "Dirty/contaminated MAF element, wiring short to ground, or severe intake restriction."
            )
            maf < expectedMin -> Triple(
                AutoTestStatus.WARN,
                "MAF reading low — ${"%.2f".format(maf)} g/s (expected ≥${"%.1f".format(expectedMin)} g/s)",
                "MAF output is below expected range for ${"%.0f".format(rpm)} RPM. " +
                        "Clean MAF sensor with MAF-safe spray. Check for intake air restrictions."
            )
            maf > expectedMax * 1.4 -> Triple(
                AutoTestStatus.WARN,
                "MAF reading high — ${"%.2f".format(maf)} g/s at ${"%.0f".format(rpm)} RPM",
                "Reading above expected range (${"%.1f".format(expectedMax)} g/s max). " +
                        "Possible air-metering calibration drift or false air entering after the MAF sensor."
            )
            else -> Triple(
                AutoTestStatus.PASS,
                "MAF plausible — ${"%.2f".format(maf)} g/s at ${"%.0f".format(rpm)} RPM",
                "MAF flow ${"%.2f".format(maf)} g/s is within the expected band of " +
                        "${"%.1f".format(expectedMin)}–${"%.1f".format(expectedMax)} g/s for ${"%.0f".format(rpm)} RPM."
            )
        }

        return AutoTestResult(
            testId = id, testName = name,
            status = status, summary = summary, details = details,
            pidReadings = readings,
            confidenceScore = coverageConfidence(samples, PIDS),
        )
    }
}
