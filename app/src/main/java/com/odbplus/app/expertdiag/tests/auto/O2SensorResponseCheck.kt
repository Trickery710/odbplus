package com.odbplus.app.expertdiag.tests.auto

import com.odbplus.app.expertdiag.engine.AutomaticTest
import com.odbplus.app.expertdiag.engine.avgFor
import com.odbplus.app.expertdiag.engine.collectSamples
import com.odbplus.app.expertdiag.engine.coverageConfidence
import com.odbplus.app.expertdiag.engine.rangeFor
import com.odbplus.app.expertdiag.engine.valuesFor
import com.odbplus.app.expertdiag.model.AutoTestResult
import com.odbplus.app.expertdiag.model.AutoTestStatus
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdService

/**
 * Automatic test: verify upstream O2 sensor switching activity at idle.
 *
 * A healthy narrowband O2 sensor at operating temperature should switch
 * between ~0.1V (lean) and ~0.9V (rich) at least 4–8 times per minute.
 * We count crossings of the 0.45V midpoint over the sampling window.
 */
class O2SensorResponseCheck : AutomaticTest {

    override val id = "O2_ResponseTest"
    override val name = "O2 Sensor Response Check"
    override val description = "Measures O2 B1S1 switching voltage and rate. A healthy sensor swings >0.6V and crosses 0.45V midpoint regularly."

    private val PIDS = listOf(
        ObdPid.O2_SENSOR_B1S1_VOLTAGE,
        ObdPid.O2_SENSOR_B2S1_VOLTAGE,
        ObdPid.ENGINE_RPM,
        ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
    )

    override fun getRequiredPids() = PIDS

    override suspend fun run(obdService: ObdService): AutoTestResult {
        // Collect more cycles to get enough crossings for a rate count
        val samples = collectSamples(obdService, PIDS, cycleCount = 12)

        val o2b1 = samples.valuesFor(ObdPid.O2_SENSOR_B1S1_VOLTAGE)
        val avgVolt = samples.avgFor(ObdPid.O2_SENSOR_B1S1_VOLTAGE)
        val swingRange = samples.rangeFor(ObdPid.O2_SENSOR_B1S1_VOLTAGE)
        val rpm = samples.avgFor(ObdPid.ENGINE_RPM)

        val readings = mapOf(
            "O2_B1S1_avg" to avgVolt,
            "O2_B1S1_swing" to swingRange,
            "RPM" to rpm,
        )

        if (avgVolt == null || o2b1.size < 4) {
            return AutoTestResult(
                testId = id, testName = name,
                status = AutoTestStatus.SKIPPED,
                summary = "O2 B1S1 data unavailable — sensor may not be warmed up",
                details = "No voltage data returned. Ensure engine is at operating temperature (ECT ≥ 75°C) before running.",
                pidReadings = readings,
            )
        }

        // Count midpoint crossings
        val mid = 0.45
        var crossings = 0
        for (i in 1 until o2b1.size) {
            if ((o2b1[i - 1] < mid) != (o2b1[i] < mid)) crossings++
        }

        val swing = swingRange ?: 0.0
        val sensorFrozenLow = avgVolt < 0.2 && swing < 0.1
        val sensorFrozenHigh = avgVolt > 0.7 && swing < 0.1
        val sensorSlow = swing > 0.4 && crossings < 2
        val sensorHealthy = swing > 0.6 && crossings >= 2

        val (status, summary, details) = when {
            sensorFrozenLow -> Triple(
                AutoTestStatus.FAIL,
                "O2 B1S1 frozen LOW — avg ${"%.3f".format(avgVolt)}V, swing ${"%.3f".format(swing)}V",
                "Sensor reading flat near 0V. ECU may interpret this as permanent lean. " +
                        "Possible open circuit, cold sensor, or failed sensor."
            )
            sensorFrozenHigh -> Triple(
                AutoTestStatus.FAIL,
                "O2 B1S1 frozen HIGH — avg ${"%.3f".format(avgVolt)}V, swing ${"%.3f".format(swing)}V",
                "Sensor stuck near 0.8–0.9V indicating rich bias. " +
                        "Possible: contaminated sensor, short to positive, or fuel system rich."
            )
            sensorSlow -> Triple(
                AutoTestStatus.WARN,
                "O2 B1S1 slow response — only $crossings crossings, swing ${"%.3f".format(swing)}V",
                "Sensor is switching but response time is sluggish (expected ≥4 midpoint crossings in this window). " +
                        "Aging, carbon-contaminated, or thermally compromised O2 sensor."
            )
            sensorHealthy -> Triple(
                AutoTestStatus.PASS,
                "O2 B1S1 switching normally — $crossings crossings, swing ${"%.3f".format(swing)}V",
                "Voltage swings from ~${"%.3f".format(o2b1.min())}V to ~${"%.3f".format(o2b1.max())}V. " +
                        "Sensor is active and ECU closed-loop control appears functional."
            )
            else -> Triple(
                AutoTestStatus.WARN,
                "O2 B1S1 marginal — swing ${"%.3f".format(swing)}V, $crossings crossings",
                "Sensor shows some activity but less than expected for a healthy sensor. " +
                        "Monitor under full operating conditions — may be warming up."
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
