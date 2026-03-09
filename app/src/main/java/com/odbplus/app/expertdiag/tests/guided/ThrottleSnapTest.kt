package com.odbplus.app.expertdiag.tests.guided

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import com.odbplus.app.diagnostic.DiagnosticTest
import com.odbplus.app.diagnostic.model.DiagnosticFinding
import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.app.diagnostic.model.DiagnosticSample
import com.odbplus.app.diagnostic.model.DiagnosticStatus
import com.odbplus.app.diagnostic.model.DiagTestStep
import com.odbplus.app.diagnostic.model.avgForStep
import com.odbplus.app.diagnostic.model.maxForStep
import com.odbplus.app.diagnostic.model.valuesForStep
import com.odbplus.app.diagnostic.tests.confidenceScore
import com.odbplus.core.protocol.ObdPid

/**
 * Guided test: rapid throttle blip to evaluate sensor transient response.
 *
 * Steps:
 *  1. Idle baseline (5 s)
 *  2. Snap throttle to ~50% and hold (8 s)
 *  3. Return to idle (5 s)
 *
 * Evaluates: TPS-to-RPM tracking, MAF transient, O2 step response,
 * fuel trim enrichment during transient.
 */
class ThrottleSnapTest : DiagnosticTest {

    override val id = "ThrottleSnapTest"
    override val name = "Throttle Snap Test"
    override val description =
        "Rapid throttle input tests transient sensor response: TPS tracking, MAF step, O2 voltage swing under load."
    override val icon = Icons.Filled.FlashOn

    private val PIDS = listOf(
        ObdPid.ENGINE_RPM,
        ObdPid.THROTTLE_POSITION,
        ObdPid.MAF_FLOW_RATE,
        ObdPid.O2_SENSOR_B1S1_VOLTAGE,
        ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
        ObdPid.INTAKE_MANIFOLD_PRESSURE,
    )

    override fun getRequiredPids() = PIDS

    override fun getSteps() = listOf(
        DiagTestStep(1, "Idle Baseline",
            "Engine idling. Do not press accelerator. Let readings settle for the full duration.",
            8, targetRpm = null, monitoredPids = PIDS),
        DiagTestStep(2, "Snap Throttle",
            "Quickly snap the throttle to approximately 50% open and hold it steady. Do not rev to redline.",
            10, targetRpm = 2500, monitoredPids = PIDS),
        DiagTestStep(3, "Return to Idle",
            "Release throttle smoothly and allow engine to return to idle.",
            8, targetRpm = null, monitoredPids = PIDS),
    )

    override fun analyze(samples: List<DiagnosticSample>): DiagnosticResult {
        val findings = mutableListOf<DiagnosticFinding>()
        val issues = mutableListOf<String>()
        val causes = mutableListOf<String>()
        val checks = mutableListOf<String>()

        val idleTps  = samples.avgForStep(0, ObdPid.THROTTLE_POSITION)
        val snapTps  = samples.maxForStep(1, ObdPid.THROTTLE_POSITION)
        val idleRpm  = samples.avgForStep(0, ObdPid.ENGINE_RPM)
        val snapRpm  = samples.maxForStep(1, ObdPid.ENGINE_RPM)
        val idleMaf  = samples.avgForStep(0, ObdPid.MAF_FLOW_RATE)
        val snapMaf  = samples.maxForStep(1, ObdPid.MAF_FLOW_RATE)
        val snapO2   = samples.valuesForStep(1, ObdPid.O2_SENSOR_B1S1_VOLTAGE)
        val snapStft = samples.avgForStep(1, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1)

        // TPS response
        if (idleTps != null && snapTps != null) {
            val delta = snapTps - idleTps
            if (delta > 15.0) {
                findings += DiagnosticFinding("TPS Responds Correctly",
                    "TPS increased from ${"%.1f".format(idleTps)}% to ${"%.1f".format(snapTps)}% — sensor tracking throttle input.",
                    DiagnosticStatus.PASS)
            } else {
                findings += DiagnosticFinding("TPS Response Minimal",
                    "TPS only changed ${"%.1f".format(delta)}% during snap. Check throttle linkage and TPS calibration.",
                    DiagnosticStatus.WARNING)
                issues += "TPS not responding proportionally to throttle input"
                causes += "TPS misalignment or binding"; causes += "Throttle body carbon buildup"
                checks += "Clean throttle body"; checks += "Verify TPS voltage sweep"
            }
        }

        // MAF transient
        if (idleMaf != null && snapMaf != null) {
            val ratio = snapMaf / idleMaf.coerceAtLeast(0.1)
            if (ratio > 3.0) {
                findings += DiagnosticFinding("MAF Transient Response Normal",
                    "MAF jumped from ${"%.2f".format(idleMaf)} to ${"%.2f".format(snapMaf)} g/s under throttle snap.",
                    DiagnosticStatus.PASS)
            } else {
                findings += DiagnosticFinding("MAF Transient Flat",
                    "MAF only ${"%.2f".format(idleMaf)}→${"%.2f".format(snapMaf)} g/s. Insufficient response to throttle input.",
                    DiagnosticStatus.WARNING)
                issues += "MAF not responding to transient load"
                causes += "Dirty MAF sensor"; causes += "Intake air leak bypassing MAF"
                checks += "Clean MAF element"; checks += "Inspect intake boots for cracks"
            }
        }

        // O2 response (should go rich during acceleration)
        if (snapO2.isNotEmpty()) {
            val maxO2 = snapO2.max()
            if (maxO2 > 0.8) {
                findings += DiagnosticFinding("O2 B1S1 Rich Response Under Snap",
                    "O2 peaked at ${"%.3f".format(maxO2)}V during acceleration — correct enrichment response.",
                    DiagnosticStatus.PASS)
            } else {
                findings += DiagnosticFinding("O2 B1S1 No Rich Spike Under Snap",
                    "O2 max only ${"%.3f".format(maxO2)}V during acceleration. Expected >0.8V enrichment.",
                    DiagnosticStatus.WARNING)
                issues += "Fuel enrichment not occurring during acceleration"
                causes += "Injector flow limited"; causes += "Fuel pressure dropping under load"
                checks += "Check fuel pressure under acceleration"; checks += "Evaluate injector flow balance"
            }
        }

        // Fuel trim correction
        if (snapStft != null && snapStft < -5.0) {
            findings += DiagnosticFinding("Fuel Trim Cuts During Snap",
                "STFT B1=${"%.1f".format(snapStft)}% during throttle snap — ECU is cutting fuel correctly.",
                DiagnosticStatus.PASS)
        }

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
            recommendedChecks = checks.ifEmpty { listOf("Transient sensor response normal.") },
        )
    }
}
