package com.odbplus.app.live

import com.odbplus.core.protocol.ObdPid
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// Derived metric result types
// ─────────────────────────────────────────────────────────────────────────────

enum class DerivedMetricId {
    MPG_FROM_MAF,
    MPG_FROM_FUEL_RATE,
    L_PER_100KM,
    FUEL_TRIM_TOTAL_B1,
    FUEL_TRIM_TOTAL_B2,
    VOLTAGE_HEALTH,
    INTAKE_TEMP_DELTA,
}

data class DerivedMetric(
    val id: DerivedMetricId,
    val label: String,
    val value: Double?,
    val formattedValue: String,
    val unit: String,
    val category: PidCategory,
    val status: SensorStatus = SensorStatus.NORMAL,
    val note: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Calculator — all formulas centralised here
// ─────────────────────────────────────────────────────────────────────────────

object DerivedMetricCalculator {

    /**
     * Compute all applicable derived metrics from a snapshot of PID values.
     * Only returns metrics for which sufficient input data is present.
     */
    fun calculate(pidValues: Map<ObdPid, Double?>): List<DerivedMetric> {
        val results = mutableListOf<DerivedMetric>()

        mpgFromMaf(pidValues)?.let { results += it }
        mpgFromFuelRate(pidValues)?.let { results += it }
        l100km(results)?.let { results += it }
        fuelTrimTotalB1(pidValues)?.let { results += it }
        fuelTrimTotalB2(pidValues)?.let { results += it }
        voltageHealth(pidValues)?.let { results += it }
        intakeTempDelta(pidValues)?.let { results += it }

        return results
    }

    // ── MPG from MAF ─────────────────────────────────────────────────────────
    // Formula: fuel_gal_hr = (MAF_gps * 3600) / (14.7 * 453.592 * 6.17)
    //          MPG = speed_mph / fuel_gal_hr
    private fun mpgFromMaf(pids: Map<ObdPid, Double?>): DerivedMetric? {
        val maf = pids[ObdPid.MAF_FLOW_RATE] ?: return null
        val speedKmh = pids[ObdPid.VEHICLE_SPEED] ?: return null
        if (maf <= 0.0) return DerivedMetric(
            DerivedMetricId.MPG_FROM_MAF, "MPG (MAF)",
            null, "—", "mpg", PidCategory.FUEL_ECONOMY, note = "idle"
        )
        val speedMph = speedKmh * 0.621371
        if (speedMph < 1.0) return DerivedMetric(
            DerivedMetricId.MPG_FROM_MAF, "MPG (MAF)",
            null, "idle", "mpg", PidCategory.FUEL_ECONOMY, note = "idle"
        )
        val fuelGalPerHr = (maf * 3600.0) / (14.7 * 453.592 * 6.17)
        val mpg = speedMph / fuelGalPerHr
        val clamped = mpg.coerceIn(0.0, 200.0)
        val status = when {
            clamped >= 35.0 -> SensorStatus.NORMAL
            clamped >= 20.0 -> SensorStatus.WARNING
            else -> SensorStatus.CRITICAL
        }
        return DerivedMetric(
            DerivedMetricId.MPG_FROM_MAF, "MPG (MAF)",
            clamped, "%.1f".format(clamped), "mpg",
            PidCategory.FUEL_ECONOMY, status
        )
    }

    // ── MPG from Fuel Rate PID 0x5E ──────────────────────────────────────────
    private fun mpgFromFuelRate(pids: Map<ObdPid, Double?>): DerivedMetric? {
        val fuelRateLph = pids[ObdPid.ENGINE_FUEL_RATE] ?: return null
        val speedKmh = pids[ObdPid.VEHICLE_SPEED] ?: return null
        if (fuelRateLph <= 0.0 || speedKmh < 1.0) return null
        val fuelRateGph = fuelRateLph * 0.264172
        val speedMph = speedKmh * 0.621371
        val mpg = speedMph / fuelRateGph
        val clamped = mpg.coerceIn(0.0, 200.0)
        val status = when {
            clamped >= 35.0 -> SensorStatus.NORMAL
            clamped >= 20.0 -> SensorStatus.WARNING
            else -> SensorStatus.CRITICAL
        }
        return DerivedMetric(
            DerivedMetricId.MPG_FROM_FUEL_RATE, "MPG (rate)",
            clamped, "%.1f".format(clamped), "mpg",
            PidCategory.FUEL_ECONOMY, status
        )
    }

    // ── L/100km from existing MPG results ────────────────────────────────────
    private fun l100km(existing: List<DerivedMetric>): DerivedMetric? {
        val mpgMetric = existing.firstOrNull {
            it.id == DerivedMetricId.MPG_FROM_MAF || it.id == DerivedMetricId.MPG_FROM_FUEL_RATE
        } ?: return null
        val mpg = mpgMetric.value ?: return null
        if (mpg <= 0.0) return null
        val l100 = 235.215 / mpg
        val status = when {
            l100 <= 7.0 -> SensorStatus.NORMAL
            l100 <= 12.0 -> SensorStatus.WARNING
            else -> SensorStatus.CRITICAL
        }
        return DerivedMetric(
            DerivedMetricId.L_PER_100KM, "L/100km",
            l100, "%.1f".format(l100), "L/100km",
            PidCategory.FUEL_ECONOMY, status
        )
    }

    // ── Fuel Trim Total (STFT + LTFT) Bank 1 ─────────────────────────────────
    private fun fuelTrimTotalB1(pids: Map<ObdPid, Double?>): DerivedMetric? {
        val stft = pids[ObdPid.SHORT_TERM_FUEL_TRIM_BANK1] ?: return null
        val ltft = pids[ObdPid.LONG_TERM_FUEL_TRIM_BANK1] ?: return null
        val total = stft + ltft
        val absTotal = abs(total)
        val status = when {
            absTotal <= 10.0 -> SensorStatus.NORMAL
            absTotal <= 25.0 -> SensorStatus.WARNING
            else -> SensorStatus.CRITICAL
        }
        return DerivedMetric(
            DerivedMetricId.FUEL_TRIM_TOTAL_B1, "Trim Total B1",
            total, "%+.1f%%".format(total), "%",
            PidCategory.AIR_FUEL, status
        )
    }

    // ── Fuel Trim Total Bank 2 ────────────────────────────────────────────────
    private fun fuelTrimTotalB2(pids: Map<ObdPid, Double?>): DerivedMetric? {
        val stft = pids[ObdPid.SHORT_TERM_FUEL_TRIM_BANK2] ?: return null
        val ltft = pids[ObdPid.LONG_TERM_FUEL_TRIM_BANK2] ?: return null
        val total = stft + ltft
        val absTotal = abs(total)
        val status = when {
            absTotal <= 10.0 -> SensorStatus.NORMAL
            absTotal <= 25.0 -> SensorStatus.WARNING
            else -> SensorStatus.CRITICAL
        }
        return DerivedMetric(
            DerivedMetricId.FUEL_TRIM_TOTAL_B2, "Trim Total B2",
            total, "%+.1f%%".format(total), "%",
            PidCategory.AIR_FUEL, status
        )
    }

    // ── Voltage Health ────────────────────────────────────────────────────────
    private fun voltageHealth(pids: Map<ObdPid, Double?>): DerivedMetric? {
        val volts = pids[ObdPid.CONTROL_MODULE_VOLTAGE] ?: return null
        val (label, status) = when {
            volts < 11.0 -> "Very Low" to SensorStatus.CRITICAL
            volts < 12.2 -> "Low" to SensorStatus.WARNING
            volts in 13.5..14.8 -> "Charging OK" to SensorStatus.NORMAL
            volts > 15.0 -> "Overcharge" to SensorStatus.CRITICAL
            volts > 14.8 -> "High" to SensorStatus.WARNING
            else -> "OK" to SensorStatus.NORMAL
        }
        return DerivedMetric(
            DerivedMetricId.VOLTAGE_HEALTH, "Voltage Health",
            volts, "%.2fV · $label".format(volts), "V",
            PidCategory.ELECTRICAL, status
        )
    }

    // ── Intake Temp Delta (IAT vs Coolant) ────────────────────────────────────
    private fun intakeTempDelta(pids: Map<ObdPid, Double?>): DerivedMetric? {
        val iat = pids[ObdPid.INTAKE_AIR_TEMP] ?: return null
        val coolant = pids[ObdPid.ENGINE_COOLANT_TEMP] ?: return null
        val delta = iat - coolant
        val status = when {
            abs(delta) <= 15.0 -> SensorStatus.NORMAL
            abs(delta) <= 30.0 -> SensorStatus.WARNING
            else -> SensorStatus.CRITICAL
        }
        return DerivedMetric(
            DerivedMetricId.INTAKE_TEMP_DELTA, "IAT–Coolant Delta",
            delta, "%+.0f°C".format(delta), "°C",
            PidCategory.TEMPERATURES, status,
            note = "IAT vs Coolant"
        )
    }
}
