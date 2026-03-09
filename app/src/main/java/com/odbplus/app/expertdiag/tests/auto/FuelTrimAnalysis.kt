package com.odbplus.app.expertdiag.tests.auto

import com.odbplus.app.expertdiag.engine.AutomaticTest
import com.odbplus.app.expertdiag.engine.AUTO_INTER_PID_DELAY_MS
import com.odbplus.app.expertdiag.engine.AUTO_SAMPLE_INTERVAL_MS
import com.odbplus.app.expertdiag.engine.avgFor
import com.odbplus.app.expertdiag.engine.collectSamples
import com.odbplus.app.expertdiag.engine.coverageConfidence
import com.odbplus.app.expertdiag.model.AutoTestResult
import com.odbplus.app.expertdiag.model.AutoTestStatus
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdService

/**
 * Automatic test: analyse short- and long-term fuel trims to detect
 * vacuum leaks, fuel delivery faults, or rich running conditions.
 *
 * Runs entirely from live PID data — no user interaction required.
 */
class FuelTrimAnalysis : AutomaticTest {

    override val id = "FuelTrimAnalysis"
    override val name = "Fuel Trim Analysis"
    override val description = "Evaluates STFT/LTFT Bank 1 and 2 at idle to classify lean/rich/vacuum-leak conditions."

    private val PIDS = listOf(
        ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
        ObdPid.LONG_TERM_FUEL_TRIM_BANK1,
        ObdPid.SHORT_TERM_FUEL_TRIM_BANK2,
        ObdPid.LONG_TERM_FUEL_TRIM_BANK2,
        ObdPid.ENGINE_RPM,
    )

    override fun getRequiredPids() = PIDS

    override suspend fun run(obdService: ObdService): AutoTestResult {
        val samples = collectSamples(obdService, PIDS, cycleCount = 10)

        val stft1 = samples.avgFor(ObdPid.SHORT_TERM_FUEL_TRIM_BANK1)
        val ltft1 = samples.avgFor(ObdPid.LONG_TERM_FUEL_TRIM_BANK1)
        val stft2 = samples.avgFor(ObdPid.SHORT_TERM_FUEL_TRIM_BANK2)
        val combined1 = listOfNotNull(stft1, ltft1).sumOf { it }.takeIf { listOfNotNull(stft1, ltft1).isNotEmpty() }

        val readings = mapOf(
            "STFT_B1" to stft1,
            "LTFT_B1" to ltft1,
            "STFT_B2" to stft2,
            "Combined_B1" to combined1,
        )

        if (stft1 == null) {
            return AutoTestResult(
                testId = id, testName = name,
                status = AutoTestStatus.SKIPPED,
                summary = "No fuel trim data — PID not supported",
                details = "STFT Bank 1 returned no data. ECU may not support this PID.",
                pidReadings = readings,
            )
        }

        val leanAtIdle = stft1 > 10.0
        val richAtIdle = stft1 < -10.0
        val ltftElevated = ltft1 != null && Math.abs(ltft1) > 8.0
        val bankDivergence = stft2 != null && Math.abs(stft1 - stft2) > 6.0

        val (status, summary, details) = when {
            leanAtIdle && ltftElevated -> Triple(
                AutoTestStatus.FAIL,
                "Persistent lean condition — vacuum leak or fuel delivery fault",
                "STFT B1 ${"%.1f".format(stft1)}% AND LTFT B1 ${"%.1f".format(ltft1)}%. " +
                        "Both positive trims indicate the ECU is compensating for a genuine lean condition. " +
                        "Likely vacuum leak or fuel pressure/volume fault."
            )
            leanAtIdle -> Triple(
                AutoTestStatus.WARN,
                "Short-term lean correction — possible transient vacuum leak",
                "STFT B1 ${"%.1f".format(stft1)}%. Active lean correction at idle. " +
                        "LTFT ${ltft1?.let { "${"%.1f".format(it)}%" } ?: "N/A"} — if LTFT remains low the leak may be intermittent."
            )
            richAtIdle -> Triple(
                AutoTestStatus.WARN,
                "Rich running — injector leak or high fuel pressure suspected",
                "STFT B1 ${"%.1f".format(stft1)}%. ECU pulling fuel back. " +
                        "Common causes: leaking injector, failed fuel pressure regulator, stuck-open purge valve."
            )
            bankDivergence -> Triple(
                AutoTestStatus.WARN,
                "Bank divergence — B1 and B2 trims differ by >${"%.1f".format(Math.abs(stft1 - stft2!!))}%",
                "STFT B1=${"%.1f".format(stft1)}%, B2=${"%.1f".format(stft2)}%. " +
                        "Asymmetric trims suggest a bank-specific fault: O2 sensor, injector, or intake leak on one bank only."
            )
            else -> Triple(
                AutoTestStatus.PASS,
                "Fuel trims normal — STFT B1 ${"%.1f".format(stft1)}%",
                "STFT B1=${"%.1f".format(stft1)}%, LTFT B1=${ltft1?.let { "${"%.1f".format(it)}%" } ?: "N/A"}. " +
                        "Both banks within ±10% acceptable window. Fuel metering is stable."
            )
        }

        return AutoTestResult(
            testId = id, testName = name,
            status = status, summary = summary, details = details,
            pidReadings = readings,
            confidenceScore = coverageConfidence(samples, PIDS),
        )
    }
}
