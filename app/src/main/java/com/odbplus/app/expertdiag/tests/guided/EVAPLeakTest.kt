package com.odbplus.app.expertdiag.tests.guided

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
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
 * Guided test: EVAP system leak detection via fuel trim and O2 analysis
 * at warm idle and during EVAP purge activation.
 *
 * A confirmed EVAP leak manifests as sudden lean spike (STFT jumps positive)
 * when the purge valve opens, followed by rich correction.
 * A stuck-open purge causes continuous rich condition at idle.
 */
class EVAPLeakTest : DiagnosticTest {

    override val id = "EVAPLeakTest"
    override val name = "EVAP Leak Test"
    override val description =
        "Detects EVAP evaporative emission system leaks by monitoring fuel trim and O2 response during purge conditions."
    override val icon = Icons.Filled.Air

    private val PIDS = listOf(
        ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
        ObdPid.LONG_TERM_FUEL_TRIM_BANK1,
        ObdPid.O2_SENSOR_B1S1_VOLTAGE,
        ObdPid.ENGINE_RPM,
        ObdPid.ENGINE_COOLANT_TEMP,
        ObdPid.ENGINE_LOAD,
    )

    override fun getRequiredPids() = PIDS

    override fun getSteps() = listOf(
        DiagTestStep(1, "Warm-Up Confirm",
            "Ensure engine is at full operating temperature (ECT ≥ 80°C). Keep engine at idle.",
            10, targetRpm = null, monitoredPids = PIDS),
        DiagTestStep(2, "Steady Idle Observation",
            "Allow engine to idle for the full duration. The ECU will activate EVAP purge automatically when conditions are met.",
            20, targetRpm = null, monitoredPids = PIDS),
        DiagTestStep(3, "2000 RPM Hold — Purge Activation",
            "Hold engine at 2000 RPM for the full duration. This loads the purge schedule and typically triggers purge valve.",
            15, targetRpm = 2000, monitoredPids = PIDS),
        DiagTestStep(4, "Return to Idle",
            "Release throttle and allow engine to settle at idle.",
            8, targetRpm = null, monitoredPids = PIDS),
    )

    override fun analyze(samples: List<DiagnosticSample>): DiagnosticResult {
        val findings = mutableListOf<DiagnosticFinding>()
        val issues = mutableListOf<String>()
        val causes = mutableListOf<String>()
        val checks = mutableListOf<String>()

        val ect = samples.avgForStep(0, ObdPid.ENGINE_COOLANT_TEMP)
        val idleStft = samples.avgForStep(1, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1)
        val ltft = samples.avgForStep(1, ObdPid.LONG_TERM_FUEL_TRIM_BANK1)
        val purgeStft = samples.avgForStep(2, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1)
        val idleO2values = samples.valuesForStep(1, ObdPid.O2_SENSOR_B1S1_VOLTAGE)
        val purgeO2values = samples.valuesForStep(2, ObdPid.O2_SENSOR_B1S1_VOLTAGE)

        // Temperature check
        if (ect != null && ect < 75.0) {
            findings += DiagnosticFinding("Engine Not at Operating Temperature",
                "ECT=${"%.0f".format(ect)}°C — EVAP system may not be active. Rerun after full warm-up.",
                DiagnosticStatus.WARNING)
            issues += "EVAP system may be inactive below operating temp"
        }

        // LTFT — persistent enrichment suggests stuck-open purge
        if (ltft != null) {
            if (ltft < -8.0) {
                findings += DiagnosticFinding("LTFT Rich — Possible Stuck-Open Purge Valve",
                    "LTFT B1=${"%.1f".format(ltft)}% — ECU has learned to reduce fuel significantly. " +
                            "Stuck-open purge valve continuously adds fuel vapour to intake.",
                    DiagnosticStatus.FAIL)
                issues += "Possible stuck-open EVAP purge valve"
                causes += "Stuck-open EVAP purge solenoid"; causes += "Saturated charcoal canister"
                checks += "Command EVAP purge off and monitor fuel trim change"
                checks += "Inspect purge solenoid and canister"
            } else if (ltft > 8.0) {
                findings += DiagnosticFinding("LTFT Lean — EVAP Canister May Be Sealed or Empty",
                    "LTFT B1=${"%.1f".format(ltft)}% — lean LTFT with EVAP codes may indicate no vapour load.",
                    DiagnosticStatus.WARNING)
                issues += "EVAP canister may be empty or disconnected"
            }
        }

        // Purge vs idle trim shift
        if (idleStft != null && purgeStft != null) {
            val shift = purgeStft - idleStft
            when {
                shift > 6.0 -> {
                    findings += DiagnosticFinding("Large Lean Shift During Purge — Small EVAP Leak",
                        "STFT went from ${"%.1f".format(idleStft)}% to ${"%.1f".format(purgeStft)}% (+${"%.1f".format(shift)}%) during purge hold. " +
                                "Lean spike suggests vapour is not reaching intake — possible canister vent leak.",
                        DiagnosticStatus.WARNING)
                    issues += "EVAP system may have a vent-side leak"
                    causes += "EVAP canister vent valve stuck open"; causes += "Fuel cap not sealing"
                    checks += "Inspect EVAP canister vent valve"; checks += "Check fuel cap seal"
                }
                shift < -6.0 -> {
                    findings += DiagnosticFinding("Large Rich Shift During Purge — Heavy Vapour Load",
                        "STFT dropped from ${"%.1f".format(idleStft)}% to ${"%.1f".format(purgeStft)}% (${"%.1f".format(shift)}%) during purge. " +
                                "Heavy vapour pulse may indicate saturated canister.",
                        DiagnosticStatus.WARNING)
                    issues += "Possible saturated EVAP canister"
                    causes += "Overfilled fuel tank"; causes += "Failed EVAP canister (saturated)"
                    checks += "Inspect canister for liquid fuel contamination"
                }
                else -> findings += DiagnosticFinding("Fuel Trim Stable During Purge",
                    "STFT shift of ${"%.1f".format(shift)}% during purge is within ±6% — normal EVAP contribution.",
                    DiagnosticStatus.PASS)
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
            recommendedChecks = checks.ifEmpty { listOf("EVAP system behaviour within normal parameters.") },
        )
    }
}
