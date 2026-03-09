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
 * Automatic test: verify MAP sensor reading is plausible at idle.
 *
 * At idle a naturally-aspirated engine should show ~25–45 kPa (high manifold vacuum).
 * Near atmospheric (~95–105 kPa) at idle strongly suggests a vacuum leak or
 * throttle body wide open, or a failed MAP sensor.
 *
 * For turbocharged engines with positive boost the thresholds differ —
 * this test is most accurate on N/A engines but still useful for gross outliers.
 */
class MAPSensorPlausibility : AutomaticTest {

    override val id = "MAP_Plausibility"
    override val name = "MAP Sensor Plausibility"
    override val description = "Checks MAP pressure at idle. Normal N/A idle: 25–45 kPa. High MAP at idle suggests vacuum leak or failed sensor."

    private val PIDS = listOf(
        ObdPid.INTAKE_MANIFOLD_PRESSURE,
        ObdPid.ENGINE_RPM,
        ObdPid.THROTTLE_POSITION,
        ObdPid.ENGINE_LOAD,
    )

    override fun getRequiredPids() = PIDS

    override suspend fun run(obdService: ObdService): AutoTestResult {
        val samples = collectSamples(obdService, PIDS, cycleCount = 8)

        val map = samples.avgFor(ObdPid.INTAKE_MANIFOLD_PRESSURE)
        val rpm = samples.avgFor(ObdPid.ENGINE_RPM)
        val tps = samples.avgFor(ObdPid.THROTTLE_POSITION)

        val readings = mapOf("MAP_kPa" to map, "RPM" to rpm, "TPS_%" to tps)

        if (map == null || rpm == null) {
            return AutoTestResult(
                testId = id, testName = name,
                status = AutoTestStatus.SKIPPED,
                summary = "MAP or RPM not available — PID not supported",
                details = "Cannot evaluate MAP plausibility without both MAP and RPM data.",
                pidReadings = readings,
            )
        }

        val isIdle = rpm < 1200
        val (status, summary, details) = when {
            isIdle && map > 80.0 -> Triple(
                AutoTestStatus.FAIL,
                "MAP critically high at idle — ${"%.0f".format(map)} kPa (expected 25–45 kPa)",
                "MAP=${"%.0f".format(map)} kPa at RPM=${"%.0f".format(rpm)}. This is near atmospheric, " +
                        "indicating near-zero manifold vacuum. Causes: major vacuum leak, open throttle, " +
                        "stuck-open EGR valve, or shorted MAP sensor signal wire."
            )
            isIdle && map > 55.0 -> Triple(
                AutoTestStatus.WARN,
                "MAP elevated at idle — ${"%.0f".format(map)} kPa (expected 25–45 kPa)",
                "MAP=${"%.0f".format(map)} kPa is higher than expected for idle. " +
                        "Suggests reduced manifold vacuum from vacuum leak, clogged PCV, or idle speed too high."
            )
            isIdle && map < 15.0 -> Triple(
                AutoTestStatus.WARN,
                "MAP unusually low at idle — ${"%.0f".format(map)} kPa",
                "MAP below 15 kPa at idle is possible on cam-aggressive engines but may indicate " +
                        "MAP sensor bias, open-circuit wiring, or short to ground."
            )
            else -> Triple(
                AutoTestStatus.PASS,
                "MAP plausible — ${"%.0f".format(map)} kPa at ${"%.0f".format(rpm)} RPM",
                "MAP reading ${"%.0f".format(map)} kPa is within the expected range for this engine speed. " +
                        "Manifold vacuum appears normal."
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
