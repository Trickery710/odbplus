package com.odbplus.app.diagnostic.tests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import com.odbplus.app.diagnostic.DiagnosticTest
import com.odbplus.app.diagnostic.model.DiagnosticFinding
import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.app.diagnostic.model.DiagnosticSample
import com.odbplus.app.diagnostic.model.DiagnosticStatus
import com.odbplus.app.diagnostic.model.DiagTestStep
import com.odbplus.app.diagnostic.model.avgForStep
import com.odbplus.core.protocol.ObdPid

/**
 * Captures short- and long-term fuel trims at idle and 2500 RPM to detect
 * vacuum leaks, fuel delivery faults, and rich running conditions.
 */
class FuelTrimTest : DiagnosticTest {

    override val id = "fuel_trim"
    override val name = "Fuel Trim Diagnostic"
    override val description =
        "Detect vacuum leaks or fuel delivery issues by comparing short and long-term fuel trims at idle vs 2500 RPM."
    override val icon = Icons.Filled.Warning

    private val PIDS = listOf(
        ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
        ObdPid.LONG_TERM_FUEL_TRIM_BANK1,
        ObdPid.SHORT_TERM_FUEL_TRIM_BANK2,
        ObdPid.LONG_TERM_FUEL_TRIM_BANK2,
        ObdPid.O2_SENSOR_B1S1_VOLTAGE,
        ObdPid.O2_SENSOR_B2S1_VOLTAGE,
        ObdPid.MAF_FLOW_RATE,
        ObdPid.ENGINE_RPM,
    )

    override fun getRequiredPids() = PIDS

    override fun getSteps() = listOf(
        DiagTestStep(
            stepNumber = 1,
            name = "Idle Baseline",
            instruction = "Allow engine to idle normally. Do not press the accelerator. Wait for fuel trims to stabilize.",
            durationSeconds = 15,
            targetRpm = null,
            monitoredPids = PIDS,
        ),
        DiagTestStep(
            stepNumber = 2,
            name = "2500 RPM",
            instruction = "Increase engine speed and hold at 2500 RPM. Maintain steady throttle.",
            durationSeconds = 10,
            targetRpm = 2500,
            monitoredPids = PIDS,
        ),
        DiagTestStep(
            stepNumber = 3,
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

        val idleStft1 = samples.avgForStep(0, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1)
        val idleLtft1 = samples.avgForStep(0, ObdPid.LONG_TERM_FUEL_TRIM_BANK1)
        val highStft1 = samples.avgForStep(1, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1)
        val highLtft1 = samples.avgForStep(1, ObdPid.LONG_TERM_FUEL_TRIM_BANK1)
        val idleStft2 = samples.avgForStep(0, ObdPid.SHORT_TERM_FUEL_TRIM_BANK2)

        // Combined trim (STFT + LTFT) gives the full correction picture.
        val idleCombined1 = listOfNotNull(idleStft1, idleLtft1).sumOf { it }
            .takeIf { listOfNotNull(idleStft1, idleLtft1).isNotEmpty() }
        val highCombined1 = listOfNotNull(highStft1, highLtft1).sumOf { it }
            .takeIf { listOfNotNull(highStft1, highLtft1).isNotEmpty() }

        // ── Pattern detection ─────────────────────────────────────────────────

        val vacuumLeak = idleStft1 != null && highStft1 != null &&
                idleStft1 > 8.0 && highStft1 < idleStft1 * 0.6

        val fuelDelivery = idleStft1 != null && highStft1 != null &&
                idleStft1 > 8.0 && highStft1 > 8.0

        val richRunning = idleStft1 != null && idleStft1 < -8.0

        when {
            vacuumLeak -> {
                findings += DiagnosticFinding(
                    title = "Vacuum Leak Pattern Detected",
                    detail = "STFT B1 high at idle (${"%.1f".format(idleStft1)}%) but normalises at 2500 RPM (${"%.1f".format(highStft1)}%). Classic intake vacuum leak signature.",
                    status = DiagnosticStatus.FAIL,
                )
                issues += "Intake vacuum leak suspected"
                causes += "Intake manifold gasket leak"
                causes += "Cracked or disconnected vacuum hose"
                causes += "Throttle body gasket leak"
                causes += "PCV hose disconnected"
                checks += "Perform smoke test on entire intake system"
                checks += "Inspect all vacuum hoses visually and by touch"
                checks += "Check intake manifold and throttle body gaskets"
            }

            fuelDelivery -> {
                findings += DiagnosticFinding(
                    title = "Lean Fuel Delivery Across RPM Range",
                    detail = "STFT B1 consistently positive: idle=${"%.1f".format(idleStft1)}%, 2500 RPM=${"%.1f".format(highStft1)}%. Lean condition persists regardless of RPM.",
                    status = DiagnosticStatus.WARNING,
                )
                issues += "Lean fuel supply across all RPM ranges"
                causes += "Weak or failing fuel pump"
                causes += "Clogged fuel filter"
                causes += "Partially blocked fuel injectors"
                checks += "Measure fuel pressure at the rail"
                checks += "Replace fuel filter"
                checks += "Perform fuel injector flow test"
            }

            richRunning -> {
                findings += DiagnosticFinding(
                    title = "Rich Running Condition",
                    detail = "STFT B1 at idle: ${"%.1f".format(idleStft1)}% — negative trim indicates ECU pulling fuel out.",
                    status = DiagnosticStatus.WARNING,
                )
                issues += "Rich running condition detected"
                causes += "Leaking fuel injector(s)"
                causes += "Faulty coolant temperature sensor causing cold-run enrichment"
                causes += "High fuel pressure from failed regulator"
                causes += "Leaking fuel pressure regulator vacuum diaphragm"
                checks += "Check fuel injector balance (injector leakdown test)"
                checks += "Verify coolant temperature sensor reading matches actual temp"
                checks += "Measure fuel rail pressure"
            }

            else -> {
                val idleDesc = idleStft1?.let { "${"%.1f".format(it)}%" } ?: "N/A"
                val highDesc = highStft1?.let { "${"%.1f".format(it)}%" } ?: "N/A"
                findings += DiagnosticFinding(
                    title = "Bank 1 Fuel Trims Normal",
                    detail = "STFT B1: idle=$idleDesc, 2500 RPM=$highDesc — both within ±10% acceptable band.",
                    status = DiagnosticStatus.PASS,
                )
            }
        }

        // ── Bank 2 check ──────────────────────────────────────────────────────
        if (idleStft2 != null) {
            if (Math.abs(idleStft2) > 10.0) {
                findings += DiagnosticFinding(
                    title = "Bank 2 Fuel Trim Elevated",
                    detail = "STFT B2 at idle: ${"%.1f".format(idleStft2)}% — beyond ±10%. Banks are diverging.",
                    status = DiagnosticStatus.WARNING,
                )
                issues += "Bank 2 fuel trim out of normal range"
                checks += "Compare B2 O2 sensor activity vs B1 to identify which bank is affected"
            } else {
                findings += DiagnosticFinding(
                    title = "Bank 2 Fuel Trim Normal",
                    detail = "STFT B2 at idle: ${"%.1f".format(idleStft2)}% — within ±10%.",
                    status = DiagnosticStatus.PASS,
                )
            }
        }

        // ── LTFT summary ──────────────────────────────────────────────────────
        if (idleLtft1 != null && Math.abs(idleLtft1) > 8.0) {
            findings += DiagnosticFinding(
                title = "Long-Term Fuel Trim Elevated",
                detail = "LTFT B1: ${"%.1f".format(idleLtft1)}% — ECU has accumulated a large correction. Indicates a persistent lean/rich condition.",
                status = DiagnosticStatus.WARNING,
            )
            issues += "LTFT shows persistent long-running fueling imbalance"
            checks += "Address root cause and allow ECU to relearn trims"
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
            recommendedChecks = checks.ifEmpty { listOf("Fuel system operating normally — no corrective action needed") },
        )
    }
}
