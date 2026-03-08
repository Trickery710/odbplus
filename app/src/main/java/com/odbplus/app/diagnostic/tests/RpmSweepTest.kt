package com.odbplus.app.diagnostic.tests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import com.odbplus.app.diagnostic.DiagnosticTest
import com.odbplus.app.diagnostic.model.DiagnosticFinding
import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.app.diagnostic.model.DiagnosticSample
import com.odbplus.app.diagnostic.model.DiagnosticStatus
import com.odbplus.app.diagnostic.model.DiagTestStep
import com.odbplus.app.diagnostic.model.avgForStep
import com.odbplus.app.diagnostic.model.maxForStep
import com.odbplus.app.diagnostic.model.valuesForStep
import com.odbplus.core.protocol.ObdPid

/**
 * Sweeps the engine from idle → 1500 → 2500 → 3500 RPM and back.
 *
 * Evaluates MAF scaling, fuel trim bounds, O2 switching, and MAP response
 * across the full RPM range.
 */
class RpmSweepTest : DiagnosticTest {

    override val id = "rpm_sweep"
    override val name = "RPM Sweep Test"
    override val description =
        "Verify sensor response across RPM ranges by sweeping from idle to 3500 RPM. Checks MAF scaling, fuel trims, O2 switching, and MAP response."
    override val icon = Icons.Filled.Speed

    private val PIDS = listOf(
        ObdPid.ENGINE_RPM,
        ObdPid.MAF_FLOW_RATE,
        ObdPid.INTAKE_MANIFOLD_PRESSURE,
        ObdPid.THROTTLE_POSITION,
        ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
        ObdPid.LONG_TERM_FUEL_TRIM_BANK1,
        ObdPid.O2_SENSOR_B1S1_VOLTAGE,
        ObdPid.O2_SENSOR_B2S1_VOLTAGE,
        ObdPid.TIMING_ADVANCE,
        ObdPid.ENGINE_LOAD,
    )

    override fun getRequiredPids() = PIDS

    override fun getSteps() = listOf(
        DiagTestStep(
            stepNumber = 1,
            name = "Idle",
            instruction = "Start engine and allow it to idle. Do not press the accelerator.",
            durationSeconds = 10,
            targetRpm = 750,
            monitoredPids = PIDS,
        ),
        DiagTestStep(
            stepNumber = 2,
            name = "1500 RPM",
            instruction = "Slowly increase engine speed and hold at 1500 RPM.",
            durationSeconds = 10,
            targetRpm = 1500,
            monitoredPids = PIDS,
        ),
        DiagTestStep(
            stepNumber = 3,
            name = "2500 RPM",
            instruction = "Increase engine speed and hold at 2500 RPM.",
            durationSeconds = 10,
            targetRpm = 2500,
            monitoredPids = PIDS,
        ),
        DiagTestStep(
            stepNumber = 4,
            name = "3500 RPM",
            instruction = "Increase engine speed and hold at 3500 RPM.",
            durationSeconds = 10,
            targetRpm = 3500,
            monitoredPids = PIDS,
        ),
        DiagTestStep(
            stepNumber = 5,
            name = "Return to Idle",
            instruction = "Release throttle and allow engine to return to idle.",
            durationSeconds = 10,
            targetRpm = 750,
            monitoredPids = PIDS,
        ),
    )

    override fun analyze(samples: List<DiagnosticSample>): DiagnosticResult {
        val findings = mutableListOf<DiagnosticFinding>()
        val issues = mutableListOf<String>()
        val causes = mutableListOf<String>()
        val checks = mutableListOf<String>()

        // Step indices: 0=idle, 1=1500, 2=2500, 3=3500, 4=return
        val idleMaf = samples.avgForStep(0, ObdPid.MAF_FLOW_RATE)
        val midMaf = samples.avgForStep(2, ObdPid.MAF_FLOW_RATE)

        // ── MAF scaling ───────────────────────────────────────────────────────
        if (idleMaf != null && midMaf != null) {
            if (midMaf > idleMaf * 1.3) {
                findings += DiagnosticFinding(
                    title = "MAF Scales with RPM",
                    detail = "MAF increases from ${"%.1f".format(idleMaf)} g/s at idle to ${"%.1f".format(midMaf)} g/s at 2500 RPM — proportional response.",
                    status = DiagnosticStatus.PASS,
                )
            } else {
                findings += DiagnosticFinding(
                    title = "MAF Response Flat",
                    detail = "MAF only changed ${"%.1f".format(idleMaf)} → ${"%.1f".format(midMaf)} g/s from idle to 2500 RPM. Expected at least 30% increase.",
                    status = DiagnosticStatus.WARNING,
                )
                issues += "MAF reading does not scale with RPM"
                causes += "Dirty or contaminated MAF sensing element"
                causes += "MAF sensor wiring fault"
                checks += "Clean MAF sensor with MAF-safe spray cleaner"
                checks += "Check MAF sensor connector and harness"
            }
        }

        // ── Fuel trims ────────────────────────────────────────────────────────
        val idleStft1 = samples.avgForStep(0, ObdPid.SHORT_TERM_FUEL_TRIM_BANK1)
        val idleLtft1 = samples.avgForStep(0, ObdPid.LONG_TERM_FUEL_TRIM_BANK1)

        if (idleStft1 != null) {
            if (Math.abs(idleStft1) > 10.0) {
                val direction = if (idleStft1 > 0) "lean (positive)" else "rich (negative)"
                findings += DiagnosticFinding(
                    title = "Fuel Trim Out of Range",
                    detail = "STFT B1 at idle: ${"%.1f".format(idleStft1)}% — $direction condition. ±10% is the normal band.",
                    status = DiagnosticStatus.WARNING,
                )
                issues += "Fuel trim beyond ±10% at idle"
                if (idleStft1 > 0) {
                    causes += "Vacuum leak causing lean condition"
                    checks += "Smoke-test intake system for vacuum leaks"
                } else {
                    causes += "Rich running — possible injector leak or high fuel pressure"
                    checks += "Check fuel injectors and fuel pressure regulator"
                }
            } else {
                findings += DiagnosticFinding(
                    title = "Fuel Trims Normal",
                    detail = "STFT B1 at idle: ${"%.1f".format(idleStft1)}%${idleLtft1?.let { ", LTFT: ${"%.1f".format(it)}%" } ?: ""} — within ±10% band.",
                    status = DiagnosticStatus.PASS,
                )
            }
        }

        // ── O2 switching ──────────────────────────────────────────────────────
        val idleO2B1 = samples.valuesForStep(0, ObdPid.O2_SENSOR_B1S1_VOLTAGE)
        if (idleO2B1.size >= 4) {
            val range = idleO2B1.max() - idleO2B1.min()
            if (range > 0.6) {
                findings += DiagnosticFinding(
                    title = "O2 B1S1 Switching Normally",
                    detail = "Sensor voltage swings ${"%.2f".format(idleO2B1.min())}V–${"%.2f".format(idleO2B1.max())}V — good switching activity.",
                    status = DiagnosticStatus.PASS,
                )
            } else {
                findings += DiagnosticFinding(
                    title = "O2 B1S1 Weak Response",
                    detail = "Voltage range only ${"%.2f".format(range)}V (min ${"%.2f".format(idleO2B1.min())}V / max ${"%.2f".format(idleO2B1.max())}V). Expected >0.6V swing.",
                    status = DiagnosticStatus.WARNING,
                )
                issues += "O2 sensor B1S1 shows weak voltage switching"
                causes += "Aging or contaminated O2 sensor"
                checks += "Check O2 sensor waveform with oscilloscope"
            }
        }

        // ── MAP response ──────────────────────────────────────────────────────
        val idleMap = samples.avgForStep(0, ObdPid.INTAKE_MANIFOLD_PRESSURE)
        val highMap = samples.avgForStep(3, ObdPid.INTAKE_MANIFOLD_PRESSURE)
        if (idleMap != null && highMap != null && highMap > idleMap) {
            findings += DiagnosticFinding(
                title = "MAP Responds to Load",
                detail = "MAP rises from ${"%.0f".format(idleMap)} kPa at idle to ${"%.0f".format(highMap)} kPa at 3500 RPM — sensor tracking load changes.",
                status = DiagnosticStatus.PASS,
            )
        }

        // ── Ignition timing ───────────────────────────────────────────────────
        val idleTiming = samples.avgForStep(0, ObdPid.TIMING_ADVANCE)
        val highTiming = samples.avgForStep(3, ObdPid.TIMING_ADVANCE)
        if (idleTiming != null && highTiming != null) {
            if (highTiming > idleTiming) {
                findings += DiagnosticFinding(
                    title = "Timing Advance Normal",
                    detail = "Timing advances from ${"%.1f".format(idleTiming)}° at idle to ${"%.1f".format(highTiming)}° at 3500 RPM — ECU advancing as expected.",
                    status = DiagnosticStatus.PASS,
                )
            } else if (idleTiming - highTiming > 5.0) {
                findings += DiagnosticFinding(
                    title = "Timing Retarded at High RPM",
                    detail = "Timing at 3500 RPM (${"%.1f".format(highTiming)}°) is lower than idle (${"%.1f".format(idleTiming)}°). ECU may be retarding due to knock.",
                    status = DiagnosticStatus.WARNING,
                )
                issues += "Timing being retarded at high RPM"
                causes += "Engine knock detected by ECU"
                causes += "Poor quality fuel (low octane)"
                checks += "Check for carbon buildup"
                checks += "Use higher octane fuel"
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
            recommendedChecks = checks.ifEmpty { listOf("All sensors responding normally across RPM sweep") },
        )
    }
}

/** Estimate confidence 50–90 based on sample count and PID coverage. */
internal fun confidenceScore(samples: List<DiagnosticSample>, requiredPids: List<ObdPid>): Int {
    val pidsCovered = requiredPids.count { pid -> samples.any { it.pid == pid && it.value != null } }
    val coverage = pidsCovered.toFloat() / requiredPids.size.coerceAtLeast(1)
    val volumeBonus = (samples.size / 10).coerceAtMost(20)
    return (50 + (coverage * 20).toInt() + volumeBonus).coerceIn(50, 90)
}
