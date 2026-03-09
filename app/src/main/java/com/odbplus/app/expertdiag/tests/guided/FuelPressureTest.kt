package com.odbplus.app.expertdiag.tests.guided

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import com.odbplus.app.diagnostic.DiagnosticTest
import com.odbplus.app.diagnostic.model.DiagnosticFinding
import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.app.diagnostic.model.DiagnosticSample
import com.odbplus.app.diagnostic.model.DiagnosticStatus
import com.odbplus.app.diagnostic.model.DiagTestStep
import com.odbplus.app.diagnostic.model.avgForStep
import com.odbplus.app.diagnostic.model.maxForStep
import com.odbplus.app.diagnostic.model.minForStep
import com.odbplus.app.diagnostic.tests.confidenceScore
import com.odbplus.core.protocol.ObdPid

/**
 * Guided test: correlate fuel system behaviour across idle, high-RPM, and
 * deceleration fuel-cut conditions.
 *
 * Many vehicles do not expose fuel rail pressure via OBD-II Mode 01, so this
 * test infers fuel delivery health from fuel trims and O2 response under load.
 */
class FuelPressureTest : DiagnosticTest {

    override val id = "FuelPressureTest"
    override val name = "Fuel Pressure Test"
    override val description =
        "Infers fuel delivery health from trim, O2, and MAP correlation across idle, high load, and decel fuel-cut."
    override val icon = Icons.Filled.LocalGasStation

    private val PIDS = listOf(
        ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
        ObdPid.LONG_TERM_FUEL_TRIM_BANK1,
        ObdPid.O2_SENSOR_B1S1_VOLTAGE,
        ObdPid.ENGINE_RPM,
        ObdPid.ENGINE_LOAD,
        ObdPid.INTAKE_MANIFOLD_PRESSURE,
        ObdPid.THROTTLE_POSITION,
    )

    override fun getRequiredPids() = PIDS

    override fun getSteps() = listOf(
        DiagTestStep(1, "Idle — Fuel Trim Baseline",
            "Allow engine to idle. Do not press accelerator. Let fuel trims stabilise.",
            15, targetRpm = null, monitoredPids = PIDS),
        DiagTestStep(2, "High Load — 3000 RPM Steady",
            "Hold engine at 3000 RPM with steady throttle for the full duration.",
            12, targetRpm = 3000, monitoredPids = PIDS),
        DiagTestStep(3, "Deceleration Fuel Cut",
            "Snap to 3000 RPM, then fully release throttle and let engine decelerate to idle naturally.",
            10, targetRpm = null, monitoredPids = PIDS),
        DiagTestStep(4, "Return to Idle",
            "Allow engine to settle at idle.",
            8, targetRpm = null, monitoredPids = PIDS),
    )

    override fun analyze(samples: List<DiagnosticSample>): DiagnosticResult {
        val findings = mutableListOf<DiagnosticFinding>()
        val issues = mutableListOf<String>()
        val causes = mutableListOf<String>()
        val checks = mutableListOf<String>()

        val idleStft   = samples.avgForStep(0, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1)
        val idleLtft   = samples.avgForStep(0, ObdPid.LONG_TERM_FUEL_TRIM_BANK1)
        val highStft   = samples.avgForStep(1, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1)
        val highO2Max  = samples.maxForStep(1, ObdPid.O2_SENSOR_B1S1_VOLTAGE)
        val decelO2Min = samples.minForStep(2, ObdPid.O2_SENSOR_B1S1_VOLTAGE)
        val highLoad   = samples.avgForStep(1, ObdPid.ENGINE_LOAD)

        // Trim at idle
        if (idleStft != null) {
            when {
                idleStft > 10.0 -> {
                    findings += DiagnosticFinding("Lean at Idle",
                        "STFT B1=${"%.1f".format(idleStft)}% at idle — ECU adding fuel to compensate.",
                        DiagnosticStatus.WARNING)
                    issues += "Lean condition at idle"
                    if (idleLtft != null && idleLtft > 8.0) {
                        causes += "Weak fuel pump (persistent across drive cycles)"
                        causes += "Clogged fuel filter"
                    } else {
                        causes += "Vacuum leak (LTFT not yet adapted)"
                    }
                    checks += "Measure fuel rail pressure with engine running"
                    checks += "Smoke test intake system"
                }
                idleStft < -10.0 -> {
                    findings += DiagnosticFinding("Rich at Idle",
                        "STFT B1=${"%.1f".format(idleStft)}% — ECU pulling fuel back.",
                        DiagnosticStatus.WARNING)
                    issues += "Rich condition at idle"
                    causes += "Leaking fuel injector(s)"; causes += "Stuck-open EVAP purge valve"
                    causes += "High fuel pressure — failed regulator"
                    checks += "Check injector balance"; checks += "Verify EVAP purge command"
                }
                else -> findings += DiagnosticFinding("Idle Fuel Trim Normal",
                    "STFT B1=${"%.1f".format(idleStft)}% — within ±10% band at idle.",
                    DiagnosticStatus.PASS)
            }
        }

        // Trim vs load
        if (idleStft != null && highStft != null) {
            val worsensUnderLoad = highStft > idleStft + 5.0
            val improvesUnderLoad = highStft < idleStft - 5.0 && idleStft > 5.0
            when {
                worsensUnderLoad -> {
                    findings += DiagnosticFinding("Lean Condition Worsens Under Load",
                        "STFT: idle=${"%.1f".format(idleStft)}% → 3000RPM=${"%.1f".format(highStft)}%. " +
                                "Lean worsens as demand increases.",
                        DiagnosticStatus.FAIL)
                    issues += "Fuel delivery inadequate under load"
                    causes += "Fuel pump unable to maintain pressure under high demand"
                    causes += "Fuel pressure regulator diaphragm leak"
                    checks += "Perform fuel pressure drop test under load"
                }
                improvesUnderLoad -> {
                    findings += DiagnosticFinding("Vacuum Leak Pattern — Trim Improves Under Load",
                        "STFT: idle=${"%.1f".format(idleStft)}% → 3000RPM=${"%.1f".format(highStft)}%. Classic vacuum leak.",
                        DiagnosticStatus.FAIL)
                    issues += "Intake vacuum leak"
                    causes += "Intake manifold gasket"; causes += "Vacuum hose disconnected"
                    checks += "Perform smoke test on intake system"
                }
                else -> findings += DiagnosticFinding("Fuel Trim Consistent Across Load",
                    "STFT idle=${"%.1f".format(idleStft)}%, 3000RPM=${"%.1f".format(highStft)}% — no load-dependent shift.",
                    DiagnosticStatus.PASS)
            }
        }

        // Decel fuel cut — O2 should drop near zero
        if (decelO2Min != null) {
            if (decelO2Min < 0.1) {
                findings += DiagnosticFinding("Decel Fuel Cut Confirmed",
                    "O2 dropped to ${"%.3f".format(decelO2Min)}V during deceleration — correct fuel cut behaviour.",
                    DiagnosticStatus.PASS)
            } else {
                findings += DiagnosticFinding("Decel Fuel Cut Not Detected",
                    "O2 stayed at ${"%.3f".format(decelO2Min)}V during decel. Expected near 0V.",
                    DiagnosticStatus.WARNING)
                issues += "Possible EVAP or injector leakdown during decel"
                checks += "Check EVAP solenoid command during decel"
            }
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
            recommendedChecks = checks.ifEmpty { listOf("Fuel system operating normally across all load conditions.") },
        )
    }
}
