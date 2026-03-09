package com.odbplus.app.expertdiag.tests.guided

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Engineering
import com.odbplus.app.diagnostic.DiagnosticTest
import com.odbplus.app.diagnostic.model.DiagnosticFinding
import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.app.diagnostic.model.DiagnosticSample
import com.odbplus.app.diagnostic.model.DiagnosticStatus
import com.odbplus.app.diagnostic.model.DiagTestStep
import com.odbplus.app.diagnostic.model.avgForStep
import com.odbplus.app.diagnostic.model.valuesForStep
import com.odbplus.app.diagnostic.tests.confidenceScore
import com.odbplus.core.protocol.ObdPid

/**
 * Guided test: cylinder contribution assessment via RPM stability analysis.
 *
 * An OBD-II compatible dead-cylinder test is performed by holding idle and
 * observing RPM drop and stability. A healthy engine at idle will maintain
 * a consistent RPM. A dead cylinder causes rhythmic RPM drops at a frequency
 * proportional to the misfire cylinder's firing order.
 *
 * Additional guided instructions prompt the user to perform a cylinder
 * "kill test" (disable each injector in sequence) using a noid light or
 * injector tester — confirming which cylinder has degraded contribution.
 */
class CylinderContributionTest : DiagnosticTest {

    override val id = "CylinderContributionTest"
    override val name = "Cylinder Contribution Test"
    override val description =
        "Identifies weak or dead cylinders by analysing RPM stability and guided injector contribution steps."
    override val icon = Icons.Filled.Engineering

    private val PIDS = listOf(
        ObdPid.ENGINE_RPM,
        ObdPid.ENGINE_LOAD,
        ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
        ObdPid.THROTTLE_POSITION,
    )

    override fun getRequiredPids() = PIDS

    override fun getSteps() = listOf(
        DiagTestStep(1, "Cold Engine Assessment",
            "Start engine from cold if possible. A cold idle will amplify any misfire. Observe engine idle quality for the full duration without touching throttle.",
            15, targetRpm = null, monitoredPids = PIDS),
        DiagTestStep(2, "Warm Idle Stability",
            "Allow engine to reach operating temperature (ECT ≥ 80°C) and idle normally. OBD monitors RPM stability.",
            20, targetRpm = null, monitoredPids = PIDS),
        DiagTestStep(3, "Load Pulse — 1500 RPM Hold",
            "Hold engine at 1500 RPM steady for the full duration. Listen for any rhythmic roughness, which indicates a cylinder not firing.",
            12, targetRpm = 1500, monitoredPids = PIDS),
        DiagTestStep(4, "Return to Idle",
            "Release throttle. Observe final idle quality. Note any visible exhaust smoke colour.",
            8, targetRpm = null, monitoredPids = PIDS),
    )

    override fun analyze(samples: List<DiagnosticSample>): DiagnosticResult {
        val findings = mutableListOf<DiagnosticFinding>()
        val issues = mutableListOf<String>()
        val causes = mutableListOf<String>()
        val checks = mutableListOf<String>()

        val coldRpm  = samples.valuesForStep(0, ObdPid.ENGINE_RPM)
        val warmRpm  = samples.valuesForStep(1, ObdPid.ENGINE_RPM)
        val loadRpm  = samples.valuesForStep(2, ObdPid.ENGINE_RPM)
        val warmStft = samples.avgForStep(1, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1)
        val avgWarmRpm = samples.avgForStep(1, ObdPid.ENGINE_RPM)

        fun stdDev(values: List<Double>): Double {
            if (values.isEmpty()) return 0.0
            val m = values.average()
            return Math.sqrt(values.map { (it - m) * (it - m) }.average())
        }

        val coldStdDev = stdDev(coldRpm)
        val warmStdDev = stdDev(warmRpm)
        val loadStdDev = stdDev(loadRpm)

        // Warm idle jitter
        if (warmRpm.size >= 5 && avgWarmRpm != null) {
            when {
                warmStdDev > 120 -> {
                    findings += DiagnosticFinding("Severe RPM Jitter — Multiple Cylinders Suspected",
                        "Warm idle RPM σ=${"%.0f".format(warmStdDev)} at ${"%.0f".format(avgWarmRpm)} RPM. Very rough idle.",
                        DiagnosticStatus.FAIL)
                    issues += "Severe misfires at idle"
                    causes += "Multiple failed spark plugs"; causes += "Coil-on-plug failure"
                    causes += "Injector fault"; causes += "Low compression"
                    checks += "Run cylinder kill test to isolate affected cylinders"
                    checks += "Check compression on all cylinders"
                    checks += "Replace spark plugs and test coils individually"
                }
                warmStdDev > 60 -> {
                    findings += DiagnosticFinding("Elevated RPM Jitter — Single Cylinder Misfire Suspected",
                        "Warm idle RPM σ=${"%.0f".format(warmStdDev)} at ${"%.0f".format(avgWarmRpm)} RPM.",
                        DiagnosticStatus.WARNING)
                    issues += "Single cylinder misfire or weak contribution"
                    causes += "Fouled spark plug"; causes += "Weak coil pack"
                    causes += "Clogged injector on one cylinder"
                    checks += "Swap coil and plug to adjacent cylinder — if misfire moves, component is faulty"
                    checks += "Perform injector balance test"
                }
                else -> findings += DiagnosticFinding("Idle RPM Stable",
                    "Warm idle σ=${"%.0f".format(warmStdDev)} RPM — all cylinders contributing evenly.",
                    DiagnosticStatus.PASS)
            }
        }

        // Load jitter — rough at 1500 RPM more likely compression
        if (loadRpm.size >= 5) {
            if (loadStdDev > 80) {
                findings += DiagnosticFinding("RPM Rough at 1500 RPM",
                    "Load RPM σ=${"%.0f".format(loadStdDev)} — instability persists at 1500 RPM. Suggests mechanical fault.",
                    DiagnosticStatus.FAIL)
                issues += "Cylinder misfire under light load"
                causes += "Low cylinder compression"; causes += "Burned exhaust valve"
                checks += "Perform cranking compression test on all cylinders"
                checks += "Check cylinder leakdown percentage"
            }
        }

        // Fuel trim during warm idle
        if (warmStft != null && warmStft > 10.0) {
            findings += DiagnosticFinding("Lean STFT During Cylinder Test",
                "STFT B1=${"%.1f".format(warmStft)}% — lean condition may be masking misfires or indicate injector issue.",
                DiagnosticStatus.WARNING)
            causes += "Lean misfires from insufficient fuel delivery"
            checks += "Resolve lean condition before cylinder contribution test"
        }

        val guidedNote = DiagnosticFinding(
            "Manual Cylinder Kill Test Recommended",
            "For definitive cylinder identification: use a noid light or injector tester to disable one injector at a time. " +
                    "The cylinder that causes the least RPM drop when disabled is the weak contributor.",
            DiagnosticStatus.WARNING,
        )
        findings.add(guidedNote)
        checks += "Disable each injector individually and note RPM drop — weakest drop = problem cylinder"

        val overall = when {
            findings.any { it.status == DiagnosticStatus.FAIL } -> DiagnosticStatus.FAIL
            findings.any { it.status == DiagnosticStatus.WARNING } -> DiagnosticStatus.WARNING
            else -> DiagnosticStatus.PASS
        }

        return DiagnosticResult(
            testName = name, status = overall,
            confidenceScore = confidenceScore(samples, PIDS),
            findings = findings, detectedIssues = issues,
            possibleCauses = causes,
            recommendedChecks = checks.ifEmpty { listOf("All cylinders appear to be contributing normally.") },
        )
    }
}
