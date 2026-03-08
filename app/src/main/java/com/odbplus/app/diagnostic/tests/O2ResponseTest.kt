package com.odbplus.app.diagnostic.tests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import com.odbplus.app.diagnostic.DiagnosticTest
import com.odbplus.app.diagnostic.model.DiagnosticFinding
import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.app.diagnostic.model.DiagnosticSample
import com.odbplus.app.diagnostic.model.DiagnosticStatus
import com.odbplus.app.diagnostic.model.DiagTestStep
import com.odbplus.app.diagnostic.model.valuesForStep
import com.odbplus.core.protocol.ObdPid

/**
 * Snap-throttle test that evaluates O2 sensor switching speed.
 *
 * Three quick wide-open throttle snaps provoke a large lambda excursion.
 * A healthy upstream O2 sensor should swing rapidly between 0.1V and 0.9V;
 * a slow, stuck, or lazy sensor will show a limited voltage range or
 * few crossings of the 0.45V midpoint.
 */
class O2ResponseTest : DiagnosticTest {

    override val id = "o2_response"
    override val name = "O2 Sensor Response"
    override val description =
        "Verify oxygen sensor switching speed via snap-throttle test. Detects slow, stuck, or lazy upstream O2 sensors."
    override val icon = Icons.Filled.Sync

    private val PIDS = listOf(
        ObdPid.O2_SENSOR_B1S1_VOLTAGE,
        ObdPid.O2_SENSOR_B2S1_VOLTAGE,
        ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
        ObdPid.SHORT_TERM_FUEL_TRIM_BANK2,
        ObdPid.ENGINE_RPM,
        ObdPid.THROTTLE_POSITION,
    )

    override fun getRequiredPids() = PIDS

    override fun getSteps() = listOf(
        DiagTestStep(
            stepNumber = 1,
            name = "Idle Warmup",
            instruction = "Allow engine to idle. O2 sensors need operating temperature (~300°C). Watch for steady idle before proceeding.",
            durationSeconds = 10,
            targetRpm = null,
            monitoredPids = PIDS,
        ),
        DiagTestStep(
            stepNumber = 2,
            name = "Snap Throttle × 3",
            instruction = "Quickly snap the throttle fully open, then immediately release. Repeat 3 times with a brief pause between each snap.",
            durationSeconds = 15,
            targetRpm = null,
            monitoredPids = PIDS,
        ),
        DiagTestStep(
            stepNumber = 3,
            name = "Idle Recovery",
            instruction = "Release throttle fully and allow engine to stabilise at idle.",
            durationSeconds = 10,
            targetRpm = null,
            monitoredPids = PIDS,
        ),
    )

    override fun analyze(samples: List<DiagnosticSample>): DiagnosticResult {
        val findings = mutableListOf<DiagnosticFinding>()
        val issues = mutableListOf<String>()
        val causes = mutableListOf<String>()
        val checks = mutableListOf<String>()

        val snapO2B1 = samples.valuesForStep(1, ObdPid.O2_SENSOR_B1S1_VOLTAGE)
        val snapO2B2 = samples.valuesForStep(1, ObdPid.O2_SENSOR_B2S1_VOLTAGE)
        val snapStft1 = samples.valuesForStep(1, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1)

        // ── Bank 1 upstream O2 ────────────────────────────────────────────────
        if (snapO2B1.isNotEmpty()) {
            val b1Range = snapO2B1.max() - snapO2B1.min()
            val b1Switches = countMidpointCrossings(snapO2B1)

            when {
                b1Range < 0.25 -> {
                    val stuckVoltage = snapO2B1.average()
                    findings += DiagnosticFinding(
                        title = "O2 B1S1 Stuck — No Response",
                        detail = "Voltage fixed near ${"%.2f".format(stuckVoltage)}V during snap throttle (range only ${"%.2f".format(b1Range)}V). Sensor not responding to throttle input.",
                        status = DiagnosticStatus.FAIL,
                    )
                    issues += "O2 sensor Bank 1 Sensor 1 not responding"
                    causes += "O2 sensor past end of service life"
                    causes += "Sensor contaminated (oil ash, coolant leak)"
                    causes += "Open circuit or bad ground in O2 sensor wiring"
                    checks += "Replace upstream Bank 1 O2 sensor"
                    checks += "Check for oil consumption or coolant leak into combustion"
                    checks += "Inspect O2 sensor wiring harness and connector"
                }

                b1Switches < 2 || b1Range < 0.5 -> {
                    findings += DiagnosticFinding(
                        title = "O2 B1S1 Slow Response",
                        detail = "Only $b1Switches midpoint crossings with ${"%.2f".format(b1Range)}V range during throttle snap. Expected ≥3 crossings and >0.5V swing.",
                        status = DiagnosticStatus.WARNING,
                    )
                    issues += "O2 sensor Bank 1 responding slowly to throttle input"
                    causes += "Aging upstream O2 sensor with degraded response time"
                    causes += "Sensor partially contaminated"
                    checks += "Capture O2 sensor waveform with oscilloscope"
                    checks += "Consider replacing if sensor is over 100,000 km or 6+ years old"
                }

                else -> {
                    findings += DiagnosticFinding(
                        title = "O2 B1S1 Response Normal",
                        detail = "Bank 1 sensor switched $b1Switches times, voltage swing ${"%.2f".format(snapO2B1.min())}V–${"%.2f".format(snapO2B1.max())}V — active and responding.",
                        status = DiagnosticStatus.PASS,
                    )
                }
            }
        } else {
            findings += DiagnosticFinding(
                title = "O2 B1S1 No Data",
                detail = "No O2 sensor data was returned. Sensor may not be supported or may be faulty.",
                status = DiagnosticStatus.WARNING,
            )
        }

        // ── Bank 2 upstream O2 ────────────────────────────────────────────────
        if (snapO2B2.isNotEmpty()) {
            val b2Range = snapO2B2.max() - snapO2B2.min()
            val b2Switches = countMidpointCrossings(snapO2B2)

            when {
                b2Range < 0.25 -> {
                    findings += DiagnosticFinding(
                        title = "O2 B2S1 Stuck — No Response",
                        detail = "Bank 2 voltage range only ${"%.2f".format(b2Range)}V — sensor not reacting to throttle snap.",
                        status = DiagnosticStatus.FAIL,
                    )
                    issues += "O2 sensor Bank 2 Sensor 1 not responding"
                    checks += "Replace upstream Bank 2 O2 sensor"
                }

                b2Switches < 2 -> {
                    findings += DiagnosticFinding(
                        title = "O2 B2S1 Slow Response",
                        detail = "Bank 2 only $b2Switches midpoint crossings. Expected ≥3.",
                        status = DiagnosticStatus.WARNING,
                    )
                    checks += "Compare B2 response to B1 — if both slow, check for exhaust restriction"
                }

                else -> {
                    findings += DiagnosticFinding(
                        title = "O2 B2S1 Response Normal",
                        detail = "Bank 2 switched $b2Switches times across ${"%.2f".format(b2Range)}V range — normal.",
                        status = DiagnosticStatus.PASS,
                    )
                }
            }
        }

        // ── STFT response check ───────────────────────────────────────────────
        if (snapStft1.isNotEmpty()) {
            val stftRange = snapStft1.max() - snapStft1.min()
            if (stftRange > 12.0) {
                findings += DiagnosticFinding(
                    title = "Fuel System Responded to Snap",
                    detail = "STFT B1 varied ${"%.1f".format(stftRange)}% during snap throttle — closed-loop fueling actively reacting.",
                    status = DiagnosticStatus.PASS,
                )
            }
        }

        val overallStatus = when {
            findings.any { it.status == DiagnosticStatus.FAIL } -> DiagnosticStatus.FAIL
            findings.any { it.status == DiagnosticStatus.WARNING } -> DiagnosticStatus.WARNING
            else -> DiagnosticStatus.PASS
        }

        return DiagnosticResult(
            testName = name,
            status = overallStatus,
            confidenceScore = confidenceScore(samples, PIDS),
            findings = findings,
            detectedIssues = issues,
            possibleCauses = causes,
            recommendedChecks = checks.ifEmpty { listOf("O2 sensors operating normally") },
        )
    }

    /** Count transitions crossing the 0.45V O2 sensor midpoint. */
    private fun countMidpointCrossings(voltages: List<Double>): Int {
        if (voltages.size < 2) return 0
        var count = 0
        var wasLow = voltages.first() < 0.45
        for (v in voltages.drop(1)) {
            val isLow = v < 0.45
            if (isLow != wasLow) { count++; wasLow = isLow }
        }
        return count
    }
}
