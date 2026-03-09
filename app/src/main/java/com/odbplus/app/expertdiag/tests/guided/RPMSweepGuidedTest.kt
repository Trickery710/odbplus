package com.odbplus.app.expertdiag.tests.guided

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
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
 * Guided test: full RPM sweep from idle → 1500 → 2500 → 3500 RPM.
 *
 * Validates MAF scaling, fuel trims, O2 switching, and MAP pressure response
 * across the usable RPM range.
 */
class RPMSweepGuidedTest : DiagnosticTest {

    override val id = "RPMSweepTest"
    override val name = "RPM Sweep Test"
    override val description =
        "Sweeps engine from idle to 3500 RPM. Validates MAF, fuel trims, O2, MAP, and timing across the range."
    override val icon = Icons.Filled.Speed

    private val PIDS = listOf(
        ObdPid.ENGINE_RPM,
        ObdPid.MAF_FLOW_RATE,
        ObdPid.INTAKE_MANIFOLD_PRESSURE,
        ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
        ObdPid.LONG_TERM_FUEL_TRIM_BANK1,
        ObdPid.O2_SENSOR_B1S1_VOLTAGE,
        ObdPid.THROTTLE_POSITION,
        ObdPid.ENGINE_LOAD,
    )

    override fun getRequiredPids() = PIDS

    override fun getSteps() = listOf(
        DiagTestStep(1, "Idle Baseline",
            "Allow engine to idle. Do not touch the accelerator. Wait for readings to stabilise.",
            12, targetRpm = 750, monitoredPids = PIDS),
        DiagTestStep(2, "1500 RPM",
            "Slowly increase throttle and hold engine at 1500 RPM.",
            10, targetRpm = 1500, monitoredPids = PIDS),
        DiagTestStep(3, "2500 RPM",
            "Increase throttle and hold at 2500 RPM for the full duration.",
            10, targetRpm = 2500, monitoredPids = PIDS),
        DiagTestStep(4, "3500 RPM",
            "Increase throttle and hold at 3500 RPM.",
            10, targetRpm = 3500, monitoredPids = PIDS),
        DiagTestStep(5, "Return to Idle",
            "Slowly release throttle and allow engine to return to idle.",
            10, targetRpm = 750, monitoredPids = PIDS),
    )

    override fun analyze(samples: List<DiagnosticSample>): DiagnosticResult {
        val findings = mutableListOf<DiagnosticFinding>()
        val issues = mutableListOf<String>()
        val causes = mutableListOf<String>()
        val checks = mutableListOf<String>()

        val idleMaf = samples.avgForStep(0, ObdPid.MAF_FLOW_RATE)
        val midMaf  = samples.avgForStep(2, ObdPid.MAF_FLOW_RATE)
        val idleStft = samples.avgForStep(0, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1)
        val highStft = samples.avgForStep(2, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1)
        val idleO2   = samples.valuesForStep(0, ObdPid.O2_SENSOR_B1S1_VOLTAGE)
        val idleMap  = samples.avgForStep(0, ObdPid.INTAKE_MANIFOLD_PRESSURE)
        val highMap  = samples.avgForStep(3, ObdPid.INTAKE_MANIFOLD_PRESSURE)

        // MAF scaling
        if (idleMaf != null && midMaf != null) {
            if (midMaf > idleMaf * 1.3) {
                findings += DiagnosticFinding("MAF Scales with RPM",
                    "MAF: ${"%.2f".format(idleMaf)} g/s idle → ${"%.2f".format(midMaf)} g/s at 2500 RPM.", DiagnosticStatus.PASS)
            } else {
                findings += DiagnosticFinding("MAF Response Flat",
                    "MAF only ${"%.2f".format(idleMaf)}→${"%.2f".format(midMaf)} g/s. Expected ≥30% increase.", DiagnosticStatus.WARNING)
                issues += "MAF does not scale with RPM"
                causes += "Dirty MAF sensing element"
                checks += "Clean MAF with MAF-safe spray"
            }
        }

        // Fuel trims
        if (idleStft != null && highStft != null) {
            val vacuumLeak = idleStft > 8.0 && highStft < idleStft * 0.65
            val persistentLean = idleStft > 8.0 && highStft > 8.0
            when {
                vacuumLeak -> {
                    findings += DiagnosticFinding("Vacuum Leak Pattern",
                        "STFT: ${"%.1f".format(idleStft)}% idle → ${"%.1f".format(highStft)}% at 2500 RPM. Classic leak signature.",
                        DiagnosticStatus.FAIL)
                    issues += "Intake vacuum leak suspected"
                    causes += "Intake manifold gasket"; causes += "Cracked vacuum hose"
                    checks += "Smoke test intake system"
                }
                persistentLean -> {
                    findings += DiagnosticFinding("Persistent Lean Condition",
                        "STFT positive at all RPMs: idle=${"%.1f".format(idleStft)}%, 2500=${"%.1f".format(highStft)}%.",
                        DiagnosticStatus.WARNING)
                    issues += "Lean across RPM range"
                    causes += "Weak fuel pump"; causes += "Clogged fuel filter"
                    checks += "Measure fuel rail pressure"
                }
                else -> findings += DiagnosticFinding("Fuel Trims Normal",
                    "STFT idle=${"%.1f".format(idleStft)}%, 2500=${"%.1f".format(highStft)}%.", DiagnosticStatus.PASS)
            }
        }

        // O2 switching
        if (idleO2.size >= 4) {
            val swing = idleO2.max() - idleO2.min()
            if (swing > 0.6) findings += DiagnosticFinding("O2 B1S1 Switching Normally",
                "Swing ${"%.3f".format(swing)}V at idle — healthy closed-loop control.", DiagnosticStatus.PASS)
            else {
                findings += DiagnosticFinding("O2 B1S1 Slow/Flat",
                    "Swing only ${"%.3f".format(swing)}V — expected >0.6V.", DiagnosticStatus.WARNING)
                issues += "O2 sensor response poor"
                causes += "Aging O2 sensor"
                checks += "Check O2 waveform with oscilloscope"
            }
        }

        // MAP response
        if (idleMap != null && highMap != null && highMap > idleMap) {
            findings += DiagnosticFinding("MAP Responds to Load",
                "MAP: ${"%.0f".format(idleMap)} kPa idle → ${"%.0f".format(highMap)} kPa at 3500 RPM.", DiagnosticStatus.PASS)
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
            recommendedChecks = checks.ifEmpty { listOf("All sensors responding normally across RPM sweep.") },
        )
    }
}
