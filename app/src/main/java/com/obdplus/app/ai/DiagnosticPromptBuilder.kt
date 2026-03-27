package com.obdplus.app.ai

import android.content.Context
import com.obdplus.app.ai.data.VehicleContext
import com.obdplus.app.ai.diagnostic.DiagnosticPipelineResult
import com.obdplus.app.live.PidDisplayState
import com.obdplus.app.live.SensorStatus
import com.obdplus.app.vin.domain.DecodedVin
import com.obdplus.core.protocol.ObdPid

private const val TEMPLATE_PATH = "prompts/first-message.txt"

// ── PID short names for compressed output ─────────────────────────────────────
// Keys are the 2-char uppercase hex PID codes used by ObdPid.code
private val PID_SHORT_NAMES: Map<String, String> = mapOf(
    "04" to "load",   "05" to "ect",    "06" to "stft1",  "07" to "ltft1",
    "08" to "stft2",  "09" to "ltft2",  "0A" to "fp",     "0B" to "map",
    "0C" to "rpm",    "0D" to "spd",    "0E" to "ign",    "0F" to "iat",
    "10" to "maf",    "11" to "tps",    "14" to "o2b1s1", "15" to "o2b1s2",
    "1B" to "o2b2s1", "1C" to "o2b2s2", "2F" to "fuel",  "42" to "vref",
    "44" to "er",     "49" to "app1",   "4A" to "app2",   "5E" to "frate"
)

// ── PIDs always included regardless of filter ─────────────────────────────────
private val ESSENTIAL_PIDS: Set<String> = setOf("0C", "05", "10", "11", "0B", "06", "07")

// ── PIDs relevant to each symptom token ──────────────────────────────────────
private val SYMPTOM_PIDS: Map<String, List<String>> = mapOf(
    "S1" to listOf("0C", "10", "11", "06", "07", "0A"),         // stall / dies
    "S2" to listOf("0C", "0B", "10", "11", "42"),               // crank / no-start
    "S3" to listOf("0C", "11", "49", "4A", "10"),               // no throttle
    "S4" to listOf("0C", "06", "07", "10", "0B", "14", "15"),   // rough idle
    "S5" to listOf("05", "0C", "04"),                           // overheat
    "S6" to listOf("0C", "06", "07", "10", "04"),               // misfire
    "S7" to listOf("0C", "10", "11", "0B"),                     // hesitation
    "S8" to listOf("0C", "04", "10", "0B"),                     // no power
    "S9" to listOf("06", "07", "10", "05", "0C")                // check engine
)

// ── DTCs whose prefix implies which sensors to include ────────────────────────
private val DTC_PREFIX_PIDS: List<Pair<String, List<String>>> = listOf(
    "P00" to listOf("10", "06", "07", "0B", "11"),
    "P01" to listOf("10", "06", "07", "14", "15"),
    "P02" to listOf("14", "15", "06", "07"),
    "P03" to listOf("0C", "06", "07", "04"),
    "P04" to listOf("06", "07", "10", "44"),
    "P06" to listOf("0C", "11", "0B", "42"),
    "P15" to listOf("11", "49", "4A", "0C")
)

/**
 * Builds the first diagnostic context message from
 * `assets/prompts/first-message.txt`.
 *
 * Key behaviours:
 * - Symptom text is tokenized (stall → S1, etc.)
 * - Only relevant / abnormal PIDs are included (signal-rich, token-efficient)
 * - All data sections use compressed single-line format
 * - Empty sections are stripped so the AI receives a minimal-noise prompt
 */
object DiagnosticPromptBuilder {

    fun build(
        context: Context,
        vehicleCtx: VehicleContext,
        symptoms: String,
        decodedVin: DecodedVin? = null,
        pipelineResult: DiagnosticPipelineResult? = null
    ): String {
        val template = context.assets.open(TEMPLATE_PATH).bufferedReader().readText()

        // ── Vehicle identity ───────────────────────────────────────────────────
        val vehicleInfo = vehicleCtx.vehicleInfo
        val vinDecoded  = vehicleInfo?.decodeVin() ?: emptyMap()

        val year   = decodedVin?.modelYear?.toString() ?: vinDecoded["Model Year"] ?: ""
        val make   = decodedVin?.make ?: vinDecoded["Manufacturer"] ?: ""
        val model  = decodedVin?.model ?: ""
        val engine = buildEngineString(decodedVin)

        // V: field — comma-separated, empty parts skipped
        val vehicleInfo_ = listOf(year, make, model, engine)
            .filter { it.isNotBlank() }
            .joinToString(",")

        // ── Symptoms ──────────────────────────────────────────────────────────
        val symptomTokens = SymptomTokenizer.tokenize(symptoms)

        // ── DTCs ──────────────────────────────────────────────────────────────
        val allDtcs = vehicleCtx.storedDtcs + vehicleCtx.pendingDtcs
        val dtcLine = allDtcs.joinToString(",") { it.code }

        // ── PID data ──────────────────────────────────────────────────────────
        val allPids = vehicleCtx.livePidValues.filter { it.value.value != null }

        val relevantCodes = buildRelevantPidCodes(vehicleCtx, symptomTokens, allDtcs.map { it.code })
        val filteredPids  = allPids.filter { (pid, _) -> pid.code in relevantCodes }

        // F: freeze-frame — rpm ect tps spd load (values only, space-separated)
        val freezeFrame = listOf("0C", "05", "11", "0D", "04")
            .mapNotNull { code -> allPids.entries.find { it.key.code == code }?.value?.value }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ") { formatValue(it) }
            ?: ""

        // P: active sensor snapshot
        val pidData = compressPids(filteredPids)

        // T: auto-test results
        val testResults = compressTestResults(vehicleCtx.autoTestResults)

        // N: network module status
        val networkStatus = vehicleCtx.networkModules
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ") { "${it.lowercase()}.on" }
            ?: ""

        // ── Pipeline result ──────────────────────────────────────────────────────
        val hypothesisLine = pipelineResult?.hypothesisTokens?.takeIf { it.isNotEmpty() }
            ?.joinToString(" ") ?: ""
        val knowledgeLine = pipelineResult?.knowledgeTokens?.takeIf { it.isNotEmpty() }
            ?.joinToString(" ") ?: ""
        val guidedTestsLine = pipelineResult?.guidedTestIds?.take(5)?.joinToString(" ") ?: ""

        // Merge pipeline flags into the flags string
        val allFlags = buildFlags(allPids).let { base ->
            val pipelineFlags = pipelineResult?.allFlags ?: emptyList()
            (base.split(" ").filter { it.isNotBlank() } + pipelineFlags).distinct().joinToString(" ")
        }

        // ── Fill template ──────────────────────────────────────────────────────
        var result = template
            .replace("{vehicle_info}",  vehicleInfo_)
            .replace("{symptoms}",      symptomTokens)
            .replace("{dtc_codes}",     dtcLine)
            .replace("{freeze_frame}",  freezeFrame)
            .replace("{pid_data}",      pidData)
            .replace("{test_results}",  testResults)
            .replace("{network_status}", networkStatus)
            .replace("{flags}",         allFlags)
            .replace("{hypothesis}",    hypothesisLine)
            .replace("{knowledge}",     knowledgeLine)
            .replace("{guided_tests}",  guidedTestsLine)

        result = removeEmptySections(result)
        result = collapseBlankLines(result)

        return result.trim()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Determines the set of PID codes to include in the prompt.
     *
     * Includes:
     *  1. Essential PIDs (RPM, ECT, MAF, TPS, MAP, fuel-trim banks)
     *  2. PIDs flagged WARNING or CRITICAL by the live-data engine
     *  3. PIDs correlated to the symptom tokens
     *  4. PIDs correlated to active DTC prefixes
     */
    private fun buildRelevantPidCodes(
        ctx: VehicleContext,
        symptomTokens: String,
        dtcCodes: List<String>
    ): Set<String> {
        val codes = mutableSetOf<String>()
        codes.addAll(ESSENTIAL_PIDS)

        // Symptom-linked PIDs
        symptomTokens.split(",").forEach { token ->
            SYMPTOM_PIDS[token.trim()]?.let { codes.addAll(it) }
        }

        // DTC prefix-linked PIDs
        dtcCodes.forEach { dtc ->
            DTC_PREFIX_PIDS.forEach { (prefix, pids) ->
                if (dtc.startsWith(prefix, ignoreCase = true)) codes.addAll(pids)
            }
        }

        // Abnormal-state PIDs always included
        ctx.livePidValues.filter { it.value.value != null }.forEach { (pid, state) ->
            if (state.status == SensorStatus.WARNING || state.status == SensorStatus.CRITICAL) {
                codes.add(pid.code)
            }
        }

        return codes
    }

    /**
     * Produces the compressed P: line, e.g.:
     *   `rpm0 ect188 app1.66 app2.33 map100 maf0 vref.02`
     */
    private fun compressPids(pidMap: Map<ObdPid, PidDisplayState>): String {
        if (pidMap.isEmpty()) return ""
        return pidMap.values.joinToString(" ") { state ->
            val name  = PID_SHORT_NAMES[state.pid.code]
                ?: state.pid.description.lowercase().replace(" ", "").take(8)
            val value = state.value?.let { formatValue(it) } ?: "?"
            "$name$value"
        }
    }

    /**
     * Produces the compressed T: line, e.g.:
     *   `pcm_pwr.ok pcm_gnd.ok tac_comm.ok`
     */
    private fun compressTestResults(results: List<Pair<String, String>>): String {
        if (results.isEmpty()) return ""
        return results.joinToString(" ") { (name, status) ->
            "${name.lowercase().replace(" ", "_")}.${status.lowercase()}"
        }
    }

    /**
     * Formats a double for the compressed output:
     * - Integers printed without decimal: `0`, `188`, `100`
     * - Fractions rounded to 2 dp, trailing zeros removed: `1.66`, `.02`
     */
    private fun formatValue(value: Double): String {
        if (value == kotlin.math.floor(value) && !value.isInfinite()) {
            return value.toLong().toString()
        }
        val s = "%.2f".format(value).trimEnd('0').trimEnd('.')
        // If result starts with "0." strip the leading zero → ".02"
        return if (s.startsWith("0.")) s.substring(1) else s
    }

    private fun buildEngineString(decoded: DecodedVin?): String {
        if (decoded == null) return ""
        return listOfNotNull(
            decoded.displacementL?.let { "${it}L" },
            decoded.engineCylinders?.let { "V$it" }.takeIf { decoded.engineCylinders != null },
            decoded.engineModel
        ).joinToString(" ")
    }

    /**
     * Generates space-separated flag tokens for the X: line.
     *
     * Flags are derived from:
     *  - PIDs in WARNING → `<name>_warning`
     *  - PIDs in CRITICAL → `<name>_critical`
     *  - Hard-coded threshold checks for key sensors
     */
    private fun buildFlags(pidMap: Map<ObdPid, PidDisplayState>): String {
        val flags = mutableListOf<String>()

        pidMap.values.filter { it.status == SensorStatus.CRITICAL }.forEach { state ->
            flags += "${PID_SHORT_NAMES[state.pid.code] ?: state.pid.description.lowercase().replace(" ", "_")}_critical"
        }
        pidMap.values.filter { it.status == SensorStatus.WARNING }.forEach { state ->
            flags += "${PID_SHORT_NAMES[state.pid.code] ?: state.pid.description.lowercase().replace(" ", "_")}_warning"
        }

        // Threshold-derived flags
        pidMap.entries.find { it.key.code == "0C" }?.value?.value?.let { rpm ->
            if (rpm == 0.0) flags += "no_rpm_signal"
        }
        pidMap.entries.find { it.key.code == "05" }?.value?.value?.let { ect ->
            if (ect > 220) flags += "coolant_temp_high"
        }
        pidMap.entries.find { it.key.code == "10" }?.value?.value?.let { maf ->
            if (maf == 0.0) flags += "maf_zero"
        }
        pidMap.entries.find { it.key.code == "0B" }?.value?.value?.let { map ->
            if (map > 100) flags += "map_high"
        }
        pidMap.entries.find { it.key.code == "42" }?.value?.value?.let { vref ->
            if (vref < 4.5) flags += "vref_low"
        }

        // APP correlation check (APP2 should track ~half of APP1)
        val app1 = (pidMap.entries.find { it.key.code == "49" }
            ?: pidMap.entries.find { it.key.code == "13" })?.value?.value
        val app2 = (pidMap.entries.find { it.key.code == "4A" }
            ?: pidMap.entries.find { it.key.code == "14" })?.value?.value
        if (app1 != null && app2 != null && Math.abs(app1 - app2 * 2) > 0.5) {
            flags += "app_mismatch"
        }

        return flags.distinct().joinToString(" ")
    }

    /**
     * Removes lines where a section prefix (V:, S:, D:, …) has no trailing value.
     * This strips empty sections cleanly without leaving bare colons.
     */
    private fun removeEmptySections(text: String): String {
        val sectionPrefixes = listOf("V:", "S:", "D:", "F:", "P:", "T:", "N:", "X:", "H:", "K:", "G:")
        return text.lines().filter { line ->
            val trimmed = line.trim()
            val emptySection = sectionPrefixes.any { prefix ->
                trimmed == prefix || (trimmed.startsWith(prefix) && trimmed.substringAfter(prefix).isBlank())
            }
            !emptySection
        }.joinToString("\n")
    }

    private fun collapseBlankLines(text: String): String =
        text.replace(Regex("\n{3,}"), "\n\n")
}
