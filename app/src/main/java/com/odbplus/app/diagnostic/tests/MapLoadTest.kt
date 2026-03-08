package com.odbplus.app.diagnostic.tests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import com.odbplus.app.diagnostic.DiagnosticTest
import com.odbplus.app.diagnostic.model.DiagnosticFinding
import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.app.diagnostic.model.DiagnosticSample
import com.odbplus.app.diagnostic.model.DiagnosticStatus
import com.odbplus.app.diagnostic.model.DiagTestStep
import com.odbplus.app.diagnostic.model.avgForStep
import com.odbplus.app.diagnostic.model.maxForStep
import com.odbplus.core.protocol.ObdPid

/**
 * Tests MAP sensor accuracy across three conditions: idle, steady 2500 RPM,
 * and snap-throttle (WOT spike).
 *
 * Expected ranges:
 *  - Idle:   25–35 kPa (naturally aspirated)
 *  - Cruise: 40–60 kPa at 2500 RPM
 *  - WOT:    spike toward 90–100 kPa
 */
class MapLoadTest : DiagnosticTest {

    override val id = "map_load"
    override val name = "MAP Sensor Load Test"
    override val description =
        "Verify MAP sensor response to engine load changes. Checks readings at idle, cruise, and wide-open throttle snap."
    override val icon = Icons.Filled.Tune

    private val PIDS = listOf(
        ObdPid.INTAKE_MANIFOLD_PRESSURE,
        ObdPid.ENGINE_RPM,
        ObdPid.THROTTLE_POSITION,
        ObdPid.ENGINE_LOAD,
        ObdPid.MAF_FLOW_RATE,
    )

    override fun getRequiredPids() = PIDS

    override fun getSteps() = listOf(
        DiagTestStep(
            stepNumber = 1,
            name = "Idle",
            instruction = "Allow engine to idle. MAP should read 25–35 kPa on a naturally aspirated engine.",
            durationSeconds = 10,
            targetRpm = null,
            monitoredPids = PIDS,
        ),
        DiagTestStep(
            stepNumber = 2,
            name = "2500 RPM Cruise",
            instruction = "Hold engine speed at 2500 RPM with steady throttle. MAP should read 40–60 kPa.",
            durationSeconds = 10,
            targetRpm = 2500,
            monitoredPids = PIDS,
        ),
        DiagTestStep(
            stepNumber = 3,
            name = "Snap Throttle × 2",
            instruction = "Snap throttle fully open twice briefly. MAP should spike toward 90–100 kPa at WOT.",
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

        val idleMap = samples.avgForStep(0, ObdPid.INTAKE_MANIFOLD_PRESSURE)
        val cruiseMap = samples.avgForStep(1, ObdPid.INTAKE_MANIFOLD_PRESSURE)
        val snapMapMax = samples.maxForStep(2, ObdPid.INTAKE_MANIFOLD_PRESSURE)

        // ── Idle MAP: expect 25–35 kPa (N/A); higher on boosted engines ──────
        if (idleMap != null) {
            when {
                idleMap < 20.0 -> {
                    findings += DiagnosticFinding(
                        title = "MAP Too Low at Idle",
                        detail = "MAP reads ${"%.0f".format(idleMap)} kPa at idle — below expected 25–35 kPa. Unusually high vacuum or sensor offset.",
                        status = DiagnosticStatus.WARNING,
                    )
                    issues += "MAP reads below normal range at idle"
                    causes += "MAP sensor calibration offset"
                    causes += "Blocked or kinked sensor vacuum port/hose"
                    causes += "Very high intake restriction (clogged air filter)"
                    checks += "Compare reading to a calibrated vacuum gauge"
                    checks += "Inspect MAP sensor vacuum port for blockage"
                }

                idleMap in 20.0..40.0 -> {
                    findings += DiagnosticFinding(
                        title = "MAP Normal at Idle",
                        detail = "MAP reads ${"%.0f".format(idleMap)} kPa at idle — within expected 25–35 kPa range.",
                        status = DiagnosticStatus.PASS,
                    )
                }

                else -> {
                    findings += DiagnosticFinding(
                        title = "MAP Elevated at Idle",
                        detail = "MAP reads ${"%.0f".format(idleMap)} kPa at idle — above expected 25–35 kPa. Low vacuum may indicate vacuum leak or cam timing.",
                        status = DiagnosticStatus.WARNING,
                    )
                    issues += "MAP reads high at idle — engine vacuum lower than normal"
                    causes += "Intake vacuum leak reducing manifold vacuum"
                    causes += "Retarded camshaft timing"
                    causes += "Worn piston rings or valves reducing compression vacuum"
                    checks += "Smoke-test intake system"
                    checks += "Perform compression test if accompanied by other symptoms"
                }
            }
        }

        // ── Cruise MAP: expect 40–60 kPa at 2500 RPM ─────────────────────────
        if (cruiseMap != null) {
            when (cruiseMap) {
                in 35.0..65.0 -> {
                    findings += DiagnosticFinding(
                        title = "MAP Normal at 2500 RPM",
                        detail = "MAP reads ${"%.0f".format(cruiseMap)} kPa at cruise — within expected 40–60 kPa.",
                        status = DiagnosticStatus.PASS,
                    )
                }

                else -> {
                    findings += DiagnosticFinding(
                        title = "MAP Out of Range at Cruise",
                        detail = "MAP reads ${"%.0f".format(cruiseMap)} kPa at 2500 RPM — expected 40–60 kPa.",
                        status = DiagnosticStatus.WARNING,
                    )
                    issues += "MAP reading abnormal at cruise RPM"
                    checks += "Verify MAP sensor 5V reference and ground circuit"
                    checks += "Compare with known-good MAP sensor signal"
                }
            }
        }

        // ── WOT spike: expect approach toward 90–100 kPa (atmospheric) ───────
        if (snapMapMax != null) {
            when {
                snapMapMax >= 80.0 -> {
                    findings += DiagnosticFinding(
                        title = "MAP WOT Response Good",
                        detail = "MAP spiked to ${"%.0f".format(snapMapMax)} kPa at snap throttle — sensor tracking WOT load correctly.",
                        status = DiagnosticStatus.PASS,
                    )
                }

                snapMapMax >= 60.0 -> {
                    findings += DiagnosticFinding(
                        title = "MAP WOT Spike Moderate",
                        detail = "MAP reached ${"%.0f".format(snapMapMax)} kPa at WOT snap — acceptable but expected closer to 90–100 kPa at full throttle.",
                        status = DiagnosticStatus.PASS,
                    )
                }

                else -> {
                    findings += DiagnosticFinding(
                        title = "MAP WOT Spike Limited",
                        detail = "MAP only reached ${"%.0f".format(snapMapMax)} kPa during throttle snap — expected spike toward 90–100 kPa.",
                        status = DiagnosticStatus.WARNING,
                    )
                    issues += "MAP sensor WOT response appears limited"
                    causes += "MAP sensor vacuum port or hose blocked/restricted"
                    causes += "Slow sensor response from dampened port"
                    checks += "Inspect MAP sensor vacuum hose for kinks, cracks, or partial blockage"
                    checks += "Test MAP sensor signal directly with a vacuum pump"
                }
            }
        }

        // ── Consistency check: MAP and load should correlate ──────────────────
        val cruiseLoad = samples.avgForStep(1, ObdPid.ENGINE_LOAD)
        if (cruiseMap != null && cruiseLoad != null) {
            val mismatch = cruiseMap < 35.0 && cruiseLoad > 50.0
            if (mismatch) {
                findings += DiagnosticFinding(
                    title = "MAP / Engine Load Mismatch",
                    detail = "MAP reads ${"%.0f".format(cruiseMap)} kPa but calculated engine load is ${"%.0f".format(cruiseLoad)}%. MAP may be under-reading.",
                    status = DiagnosticStatus.WARNING,
                )
                issues += "MAP sensor reading inconsistent with calculated engine load"
                causes += "MAP sensor signal offset or failing"
                checks += "Cross-check MAP sensor output voltage vs expected table"
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
            recommendedChecks = checks.ifEmpty { listOf("MAP sensor operating correctly across load range") },
        )
    }
}
