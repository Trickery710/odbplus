package com.odbplus.app.guidedtest

import com.odbplus.core.protocol.ObdPid
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Test stages ───────────────────────────────────────────────────────────────

/**
 * The three sequential RPM stages in the guided test.
 * Each stage requires [durationSec] seconds of continuous RPM within [targetMin]..[targetMax].
 */
enum class TestStage(
    val label: String,
    val instruction: String,
    val targetMin: Int,
    val targetMax: Int,
    val targetRpm: Int,
) {
    IDLE(
        label = "Idle",
        instruction = "Let engine idle naturally — do not press the accelerator.",
        targetMin = 0,
        targetMax = 1200,
        targetRpm = 750,
    ),
    RPM_1000(
        label = "1000 RPM",
        instruction = "Slowly press the accelerator to hold 1000 RPM.",
        targetMin = 900,
        targetMax = 1100,
        targetRpm = 1000,
    ),
    RPM_2000(
        label = "2000 RPM",
        instruction = "Press further to hold 2000 RPM.",
        targetMin = 1900,
        targetMax = 2100,
        targetRpm = 2000,
    );

    val durationSec: Int get() = 5
    fun isRpmInRange(rpm: Int): Boolean = rpm in targetMin..targetMax
}

// ── Internal domain models ─────────────────────────────────────────────────────

/** One PID reading captured during a test stage. */
data class PidSample(
    val pid: ObdPid,
    val value: Double?,
    val timestamp: Long,
    val stage: TestStage,
)

/** Min/avg/max/last statistics for a PID within one stage. */
data class PidStageSummary(
    val pid: ObdPid,
    val avg: Double?,
    val min: Double?,
    val max: Double?,
    val last: Double?,
    val sampleCount: Int,
)

/** Aggregated result for one completed test stage. */
data class StageResult(
    val stage: TestStage,
    val avgRpm: Double?,
    val minRpm: Double?,
    val maxRpm: Double?,
    val timeInRangeSec: Float,
    val pidSummaries: Map<ObdPid, PidStageSummary>,
)

/** Complete result produced at the end of a guided test run. */
data class GuidedTestResult(
    val startTime: Long,
    val endTime: Long,
    val adapterLabel: String,
    val vin: String?,
    val stageResults: List<StageResult>,
    val storedDtcs: List<String>,
    val pendingDtcs: List<String>,
    val selectedSymptoms: Set<String> = emptySet(),
    val freeText: String = "",
) {
    val durationSec: Long get() = (endTime - startTime) / 1000
}

// ── Serializable payload classes (for JSON output) ────────────────────────────

@Serializable
data class GuidedTestPayload(
    val meta: PayloadMeta,
    val stages: List<StagePayload>,
    val dtcs: DtcPayload,
    val symptoms: SymptomsPayload,
)

@Serializable
data class PayloadMeta(
    val start: String,
    val end: String,
    val duration_sec: Long,
    val adapter: String,
    val vin: String? = null,
)

@Serializable
data class StagePayload(
    val name: String,
    val target_min_rpm: Int,
    val target_max_rpm: Int,
    val avg_rpm: Double? = null,
    val min_rpm: Double? = null,
    val max_rpm: Double? = null,
    val time_in_range_sec: Float,
    val pids: Map<String, PidStatsPayload>,
)

@Serializable
data class PidStatsPayload(
    val avg: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
    val last: Double? = null,
    val samples: Int,
)

@Serializable
data class DtcPayload(
    val stored: List<String>,
    val pending: List<String>,
)

@Serializable
data class SymptomsPayload(
    val selected: List<String>,
    val notes: String,
)

// ── Payload builder ───────────────────────────────────────────────────────────

fun GuidedTestResult.toPayload(): GuidedTestPayload {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    return GuidedTestPayload(
        meta = PayloadMeta(
            start = fmt.format(Date(startTime)),
            end = fmt.format(Date(endTime)),
            duration_sec = durationSec,
            adapter = adapterLabel,
            vin = vin,
        ),
        stages = stageResults.map { sr ->
            StagePayload(
                name = sr.stage.label,
                target_min_rpm = sr.stage.targetMin,
                target_max_rpm = sr.stage.targetMax,
                avg_rpm = sr.avgRpm,
                min_rpm = sr.minRpm,
                max_rpm = sr.maxRpm,
                time_in_range_sec = sr.timeInRangeSec,
                pids = sr.pidSummaries.entries.associate { (pid, stats) ->
                    pid.description to PidStatsPayload(
                        avg = stats.avg,
                        min = stats.min,
                        max = stats.max,
                        last = stats.last,
                        samples = stats.sampleCount,
                    )
                },
            )
        },
        dtcs = DtcPayload(stored = storedDtcs, pending = pendingDtcs),
        symptoms = SymptomsPayload(
            selected = selectedSymptoms.toList(),
            notes = freeText,
        ),
    )
}

// ── Constants ─────────────────────────────────────────────────────────────────

/** PIDs polled during every stage of the guided test. */
val GUIDED_TEST_PIDS: List<ObdPid> = listOf(
    ObdPid.ENGINE_RPM,
    ObdPid.ENGINE_LOAD,
    ObdPid.ENGINE_COOLANT_TEMP,
    ObdPid.INTAKE_AIR_TEMP,
    ObdPid.INTAKE_MANIFOLD_PRESSURE,
    ObdPid.MAF_FLOW_RATE,
    ObdPid.THROTTLE_POSITION,
    ObdPid.SHORT_TERM_FUEL_TRIM_BANK1,
    ObdPid.LONG_TERM_FUEL_TRIM_BANK1,
    ObdPid.SHORT_TERM_FUEL_TRIM_BANK2,
    ObdPid.LONG_TERM_FUEL_TRIM_BANK2,
    ObdPid.VEHICLE_SPEED,
)

/** Quick-select symptom chips shown in the symptoms input step. */
val COMMON_SYMPTOMS: List<String> = listOf(
    "Stalling",
    "Rough idle",
    "Misfire",
    "Hesitation",
    "Poor acceleration",
    "Hard start",
    "Surging",
    "Smoke",
    "Fuel smell",
    "Overheating",
    "Check engine light",
)
