package com.obdplus.app.ai

import android.content.Context
import com.obdplus.app.ai.data.VehicleContext
import com.obdplus.app.live.PidDisplayState
import com.obdplus.app.live.SensorStatus
import com.obdplus.app.vin.domain.DecodedVin
import com.obdplus.core.protocol.ObdPid

private const val TEMPLATE_PATH = "prompts/first-message.txt"

/**
 * Builds the first diagnostic context message from the template file at
 * assets/prompts/first-message.txt.
 *
 * Template placeholders are replaced with live vehicle data; sections with
 * no available data are omitted so the AI receives a compact, signal-rich prompt.
 */
object DiagnosticPromptBuilder {

    /**
     * @param context      Android application context (for assets)
     * @param vehicleCtx   Current vehicle context (VIN, DTCs, live PIDs, etc.)
     * @param symptoms     Raw symptom text typed by the user
     * @param decodedVin   Optional NHTSA-decoded VIN data (make, model, engine, …)
     * @return             Filled-in template string, ready to send to the AI
     */
    fun build(
        context: Context,
        vehicleCtx: VehicleContext,
        symptoms: String,
        decodedVin: DecodedVin? = null
    ): String {
        val template = context.assets.open(TEMPLATE_PATH).bufferedReader().readText()

        val vehicleInfo = vehicleCtx.vehicleInfo
        val vinDecoded = vehicleInfo?.decodeVin() ?: emptyMap()

        // ── Vehicle fields ────────────────────────────────────────────────────
        val vin   = vehicleInfo?.vin?.takeIf { it.isNotBlank() } ?: ""
        val year  = decodedVin?.modelYear?.toString() ?: vinDecoded["Model Year"] ?: ""
        val make  = decodedVin?.make ?: vinDecoded["Manufacturer"] ?: ""
        val model = decodedVin?.model ?: ""
        val engine = buildEngineString(decodedVin)
        val trans  = decodedVin?.transmissionStyle ?: ""
        val fuel   = decodedVin?.fuelTypePrimary ?: ""

        // ── Symptoms ──────────────────────────────────────────────────────────
        val symptomList = symptoms
            .split(Regex("[,;\n]"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n") { "- $it" }

        // ── DTCs ──────────────────────────────────────────────────────────────
        val allDtcs = vehicleCtx.storedDtcs + vehicleCtx.pendingDtcs
        val dtcList = if (allDtcs.isNotEmpty()) {
            allDtcs.joinToString("\n") { dtc ->
                "${dtc.code}${dtc.description?.let { " – $it" } ?: ""}"
            }
        } else {
            "None"
        }

        // ── Live PIDs ─────────────────────────────────────────────────────────
        val pidMap = vehicleCtx.livePidValues.filter { it.value.value != null }

        fun pidValue(code: String): String =
            pidMap.entries.find { it.key.code == code }?.value?.formattedValue ?: ""

        val rpm   = pidValue("0C")
        val tps   = pidValue("11")
        val ect   = pidValue("05")
        val maf   = pidValue("10")
        val load  = pidValue("04")
        val speed = pidValue("0D")

        val pidList = pidMap.entries.joinToString("\n") { (pid, state) ->
            "${pid.description}=${state.formattedValue}|${pid.unit}"
        }

        // ── Flags ─────────────────────────────────────────────────────────────
        val flags = buildFlags(pidMap)

        // ── Replace all placeholders ──────────────────────────────────────────
        var result = template
            .replace("{vin}",               vin)
            .replace("{year}",              year)
            .replace("{make}",              make)
            .replace("{model}",             model)
            .replace("{engine}",            engine)
            .replace("{mileage}",           "")
            .replace("{trans}",             trans)
            .replace("{fuel}",              fuel)
            .replace("{short_symptom_list}", symptomList)
            .replace("{dtc_list}",          dtcList)
            .replace("{rpm}",               rpm)
            .replace("{tps}",               tps)
            .replace("{ect}",               ect)
            .replace("{maf}",               maf)
            .replace("{load}",              load)
            .replace("{speed}",             speed)
            .replace("{pid_list}",          pidList.ifBlank { "No live data" })
            .replace("{pid_summary}",       "")
            .replace("{test_results}",      "")
            .replace("{flags}",             flags.ifBlank { "None" })
            .replace("{notes}",             "")

        // ── Post-process ──────────────────────────────────────────────────────
        result = removeExampleBlocks(result)
        result = removeEmptyValueLines(result)
        result = collapseBlankLines(result)

        return result.trim()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildEngineString(decoded: DecodedVin?): String {
        if (decoded == null) return ""
        val parts = listOfNotNull(
            decoded.displacementL?.let { "${it}L" },
            decoded.engineCylinders?.let { "V$it" }.takeIf { decoded.engineCylinders != null },
            decoded.engineModel
        )
        return parts.joinToString(" ")
    }

    private fun buildFlags(pidMap: Map<ObdPid, PidDisplayState>): String {
        val flags = mutableListOf<String>()

        // Flag sensors in non-normal states
        pidMap.values.filter { it.status == SensorStatus.CRITICAL }.forEach { state ->
            flags += "${state.pid.description.uppercase().replace(" ", "_")}_CRITICAL"
        }
        pidMap.values.filter { it.status == SensorStatus.WARNING }.forEach { state ->
            flags += "${state.pid.description.uppercase().replace(" ", "_")}_WARNING"
        }

        // Additional derived flags
        pidMap.entries.find { it.key.code == "0C" }?.value?.value?.let { rpm ->
            if (rpm == 0.0) flags += "RPM_AT_ZERO"
        }
        pidMap.entries.find { it.key.code == "05" }?.value?.value?.let { ect ->
            if (ect > 220) flags += "COOLANT_TEMP_HIGH"
        }
        pidMap.entries.find { it.key.code == "10" }?.value?.value?.let { maf ->
            if (maf == 0.0) flags += "MAF_ZERO"
        }

        return flags.distinct().joinToString("\n")
    }

    /**
     * Removes "Example:" header and the lines that follow it until the next
     * section separator (---) or end of string.
     */
    private fun removeExampleBlocks(text: String): String {
        // Match "Example:" and everything after it until the next "---" or end
        return text.replace(
            Regex("(?m)^Example:.*?(?=^---|\\z)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)),
            ""
        )
    }

    /**
     * Removes lines where a label ends with a colon and no value follows
     * (i.e. the placeholder was replaced with "").
     * e.g. "Mileage: " or "Transmission: " → dropped.
     */
    private fun removeEmptyValueLines(text: String): String {
        return text.lines().filter { line ->
            val trimmed = line.trim()
            // Keep non-label lines and lines that still have content after ":"
            if (!trimmed.contains(":")) return@filter true
            val afterColon = trimmed.substringAfter(":").trim()
            afterColon.isNotBlank() || trimmed.startsWith("---") || trimmed.startsWith("#")
        }.joinToString("\n")
    }

    /** Collapses three or more consecutive blank lines into two. */
    private fun collapseBlankLines(text: String): String =
        text.replace(Regex("\n{3,}"), "\n\n")
}
