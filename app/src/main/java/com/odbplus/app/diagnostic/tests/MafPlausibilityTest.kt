package com.odbplus.app.diagnostic.tests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import com.odbplus.app.diagnostic.DiagnosticTest
import com.odbplus.app.diagnostic.model.DiagnosticFinding
import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.app.diagnostic.model.DiagnosticSample
import com.odbplus.app.diagnostic.model.DiagnosticStatus
import com.odbplus.app.diagnostic.model.DiagTestStep
import com.odbplus.app.diagnostic.model.avgForStep
import com.odbplus.core.protocol.ObdPid

/**
 * Verifies MAF sensor airflow readings match expected values at multiple RPM points.
 *
 * Expected approximate ranges (naturally aspirated, varies by displacement):
 *  - Idle (~750 RPM): 2–7 g/s
 *  - 2000 RPM:        9–18 g/s
 *  - 3000 RPM:        15–28 g/s
 *
 * Also checks that MAF scales proportionally (dirty sensor = flat line).
 */
class MafPlausibilityTest : DiagnosticTest {

    override val id = "maf_plausibility"
    override val name = "MAF Plausibility Test"
    override val description =
        "Verify MAF sensor airflow readings match expected values at idle, 2000 RPM, and 3000 RPM, and scale proportionally with engine speed."
    override val icon = Icons.Filled.CheckCircle

    private val PIDS = listOf(
        ObdPid.MAF_FLOW_RATE,
        ObdPid.ENGINE_RPM,
        ObdPid.THROTTLE_POSITION,
        ObdPid.ENGINE_LOAD,
        ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
    )

    override fun getRequiredPids() = PIDS

    override fun getSteps() = listOf(
        DiagTestStep(
            stepNumber = 1,
            name = "Idle",
            instruction = "Allow engine to idle. MAF should read approximately 2–7 g/s on a typical N/A engine.",
            durationSeconds = 10,
            targetRpm = null,
            monitoredPids = PIDS,
        ),
        DiagTestStep(
            stepNumber = 2,
            name = "2000 RPM",
            instruction = "Increase engine speed to 2000 RPM and hold steady. Expected MAF: 9–18 g/s.",
            durationSeconds = 10,
            targetRpm = 2000,
            monitoredPids = PIDS,
        ),
        DiagTestStep(
            stepNumber = 3,
            name = "3000 RPM",
            instruction = "Increase engine speed to 3000 RPM and hold steady.",
            durationSeconds = 10,
            targetRpm = 3000,
            monitoredPids = PIDS,
        ),
        DiagTestStep(
            stepNumber = 4,
            name = "Return to Idle",
            instruction = "Release throttle and allow engine to return to idle.",
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

        val idleMaf = samples.avgForStep(0, ObdPid.MAF_FLOW_RATE)
        val maf2000 = samples.avgForStep(1, ObdPid.MAF_FLOW_RATE)
        val maf3000 = samples.avgForStep(2, ObdPid.MAF_FLOW_RATE)

        // ── Idle MAF: expect 2–7 g/s ──────────────────────────────────────────
        if (idleMaf != null) {
            when {
                idleMaf < 1.5 -> {
                    findings += DiagnosticFinding(
                        title = "MAF Low at Idle",
                        detail = "MAF reads ${"%.1f".format(idleMaf)} g/s at idle — below expected 2–7 g/s. Sensor output may be restricted.",
                        status = DiagnosticStatus.WARNING,
                    )
                    issues += "MAF sensor under-reading at idle"
                    causes += "Dirty or contaminated MAF sensor hot-wire element"
                    causes += "Restricted air filter reducing airflow to sensor"
                    causes += "Damaged MAF sensor"
                    checks += "Clean MAF sensor with approved MAF spray cleaner"
                    checks += "Replace air filter if overdue"
                    checks += "Verify intake duct is fully sealed from MAF to throttle"
                }

                idleMaf > 10.0 -> {
                    findings += DiagnosticFinding(
                        title = "MAF Elevated at Idle",
                        detail = "MAF reads ${"%.1f".format(idleMaf)} g/s at idle — above expected 2–7 g/s. Possible unmetered air bypass or sensor offset.",
                        status = DiagnosticStatus.WARNING,
                    )
                    issues += "MAF reading unusually high at idle"
                    causes += "Air leak downstream of MAF sensor (unmetered air)"
                    causes += "MAF sensor output signal offset or drifted high"
                    checks += "Check intake duct between MAF and throttle body for cracks, loose clamps"
                    checks += "Verify MAF sensor output voltage at idle vs spec"
                }

                else -> {
                    findings += DiagnosticFinding(
                        title = "MAF Normal at Idle",
                        detail = "MAF reads ${"%.1f".format(idleMaf)} g/s — within expected 2–7 g/s range.",
                        status = DiagnosticStatus.PASS,
                    )
                }
            }
        }

        // ── 2000 RPM MAF: expect 9–18 g/s ────────────────────────────────────
        if (maf2000 != null) {
            when {
                maf2000 < 6.0 -> {
                    findings += DiagnosticFinding(
                        title = "MAF Low at 2000 RPM",
                        detail = "MAF reads ${"%.1f".format(maf2000)} g/s at 2000 RPM — below expected 9–18 g/s.",
                        status = DiagnosticStatus.WARNING,
                    )
                    issues += "MAF under-reading at 2000 RPM — not scaling correctly"
                    causes += "MAF sensor contamination affecting mid-range output"
                    checks += "Clean MAF sensor"
                    checks += "Compare calculated MAF (from load × displacement) to actual"
                }

                maf2000 in 6.0..22.0 -> {
                    findings += DiagnosticFinding(
                        title = "MAF Normal at 2000 RPM",
                        detail = "MAF reads ${"%.1f".format(maf2000)} g/s at 2000 RPM — within expected range.",
                        status = DiagnosticStatus.PASS,
                    )
                }

                else -> {
                    findings += DiagnosticFinding(
                        title = "MAF Elevated at 2000 RPM",
                        detail = "MAF reads ${"%.1f".format(maf2000)} g/s at 2000 RPM — above typical 9–18 g/s. May indicate large engine displacement or air leak.",
                        status = DiagnosticStatus.PASS,
                    )
                }
            }
        }

        // ── Linearity / scaling check ─────────────────────────────────────────
        if (idleMaf != null && maf3000 != null && idleMaf > 0.5) {
            val scalingRatio = maf3000 / idleMaf

            when {
                scalingRatio >= 2.5 -> {
                    findings += DiagnosticFinding(
                        title = "MAF Scales Linearly with RPM",
                        detail = "MAF increases ${"%.1f".format(scalingRatio)}× from idle to 3000 RPM (${"%.1f".format(idleMaf)} → ${"%.1f".format(maf3000)} g/s) — proportional scaling confirmed.",
                        status = DiagnosticStatus.PASS,
                    )
                }

                scalingRatio < 1.5 -> {
                    findings += DiagnosticFinding(
                        title = "MAF Not Scaling with RPM",
                        detail = "MAF only changed ${"%.1f".format(idleMaf)} → ${"%.1f".format(maf3000)} g/s from idle to 3000 RPM (${"%.1f".format(scalingRatio)}× ratio). Expected at least 2.5× scaling.",
                        status = DiagnosticStatus.WARNING,
                    )
                    issues += "MAF not scaling proportionally with engine speed"
                    causes += "MAF sensor element contaminated — output saturating at low values"
                    causes += "Air intake restriction upstream of MAF"
                    checks += "Clean MAF sensor thoroughly"
                    checks += "Inspect air filter and intake tract for restriction"
                    checks += "Verify MAF sensor signal voltage at high RPM vs ECU specification"
                }

                else -> {
                    findings += DiagnosticFinding(
                        title = "MAF Scaling Marginal",
                        detail = "MAF scaling ratio ${"%.1f".format(scalingRatio)}× — acceptable but toward the lower end. Monitor for deterioration.",
                        status = DiagnosticStatus.PASS,
                    )
                }
            }
        }

        // ── STFT correlation ──────────────────────────────────────────────────
        val idleStft1 = samples.avgForStep(0, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1)
        if (idleMaf != null && idleStft1 != null) {
            val mafLowAndLeanTrim = idleMaf < 3.0 && idleStft1 > 8.0
            if (mafLowAndLeanTrim) {
                findings += DiagnosticFinding(
                    title = "Low MAF + Positive Trim — Likely Dirty Sensor",
                    detail = "MAF under-reading (${"%.1f".format(idleMaf)} g/s) combined with positive STFT (${"%.1f".format(idleStft1)}%) suggests ECU compensating for under-reported airflow.",
                    status = DiagnosticStatus.WARNING,
                )
                issues += "MAF under-reading correlated with positive fuel trim"
                causes += "Dirty MAF sensor causing systematic under-reading of airflow"
                checks += "Clean MAF sensor and clear adaptive fuel trim (disconnect battery briefly)"
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
            recommendedChecks = checks.ifEmpty { listOf("MAF sensor readings plausible across the RPM sweep") },
        )
    }
}
