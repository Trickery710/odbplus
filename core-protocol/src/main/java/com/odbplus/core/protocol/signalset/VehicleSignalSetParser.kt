package com.odbplus.core.protocol.signalset

import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Parses OBDb signalset v3 JSON into [VehicleSignalSet].
 *
 * JSON shape (abbreviated):
 * ```json
 * {
 *   "commands": [
 *     {
 *       "hdr": "7E0",
 *       "rax": "7E8",
 *       "cmd": { "22": "1002" },
 *       "freq": 1.0,
 *       "filter": { "from": 2018, "to": 2023 },
 *       "signals": [
 *         {
 *           "id": "ENGINE_COOLANT_TEMP",
 *           "name": "Engine Coolant Temperature",
 *           "fmt": { "bix": 0, "len": 8, "mul": 1, "div": 1, "add": -40, "unit": "°C" },
 *           "hidden": false
 *         }
 *       ]
 *     }
 *   ]
 * }
 * ```
 */
object VehicleSignalSetParser {

    /**
     * Parse [json] and return a [VehicleSignalSet], or null on any top-level failure.
     *
     * Individual malformed commands / signals are skipped with a warning rather than
     * aborting the entire parse.
     */
    fun parse(vehicleKey: String, json: String): VehicleSignalSet? {
        return try {
            val root     = JSONObject(json)
            val cmdArray = root.optJSONArray("commands") ?: JSONArray()

            val commands = mutableListOf<VehicleCommand>()
            for (i in 0 until cmdArray.length()) {
                val cmdObj = cmdArray.optJSONObject(i) ?: continue
                parseCommand(cmdObj)?.let { commands += it }
            }

            VehicleSignalSet(vehicleKey = vehicleKey, commands = commands)
        } catch (e: Exception) {
            Timber.e(e, "VehicleSignalSetParser: failed to parse '$vehicleKey'")
            null
        }
    }

    // ── Command ───────────────────────────────────────────────────────────────

    private fun parseCommand(obj: JSONObject): VehicleCommand? {
        return try {
            val hdr = obj.optString("hdr", "7DF")
            val rax = obj.optString("rax", "")

            // "cmd" is an object whose single key is the hex service code
            // and whose value is the DID string.  e.g. { "22": "1002" }
            val cmdObj  = obj.optJSONObject("cmd") ?: return null
            val svcHex  = cmdObj.keys().asSequence().firstOrNull() ?: return null
            val service = svcHex.toInt(16)
            val did     = cmdObj.optString(svcHex)

            val freq = obj.optDouble("freq", 0.0)

            val filter   = obj.optJSONObject("filter")
            val yearFrom = filter?.optInt("from")?.takeIf { filter.has("from") }
            val yearTo   = filter?.optInt("to")?.takeIf   { filter.has("to") }

            val signalArray = obj.optJSONArray("signals") ?: JSONArray()
            val signals     = mutableListOf<VehicleSignal>()
            for (i in 0 until signalArray.length()) {
                val sigObj = signalArray.optJSONObject(i) ?: continue
                // Skip hidden signals
                if (sigObj.optBoolean("hidden", false)) continue
                parseSignal(sigObj)?.let { signals += it }
            }

            VehicleCommand(
                hdr      = hdr,
                rax      = rax,
                service  = service,
                did      = did,
                freq     = freq,
                yearFrom = yearFrom,
                yearTo   = yearTo,
                signals  = signals
            )
        } catch (e: Exception) {
            Timber.w(e, "VehicleSignalSetParser: skipping malformed command")
            null
        }
    }

    // ── Signal ────────────────────────────────────────────────────────────────

    private fun parseSignal(obj: JSONObject): VehicleSignal? {
        return try {
            val id   = obj.optString("id").ifEmpty { return null }
            val name = obj.optString("name", id)
            val desc = obj.optString("description", "")

            val fmtObj = obj.optJSONObject("fmt") ?: return null
            val fmt    = parseFormat(fmtObj) ?: return null

            val metric = obj.optString("suggestedMetric").ifEmpty { null }

            VehicleSignal(
                id              = id,
                name            = name,
                description     = desc,
                fmt             = fmt,
                suggestedMetric = metric
            )
        } catch (e: Exception) {
            Timber.w(e, "VehicleSignalSetParser: skipping malformed signal")
            null
        }
    }

    // ── Format ────────────────────────────────────────────────────────────────

    private fun parseFormat(obj: JSONObject): SignalFormat? {
        return try {
            val bix = obj.optInt("bix", -1).takeIf { it >= 0 } ?: return null
            val len = obj.optInt("len", -1).takeIf { it > 0  } ?: return null

            val mul = obj.optDouble("mul", 1.0).let { if (it.isNaN()) 1.0 else it }
            val div = obj.optDouble("div", 1.0).let { if (it.isNaN() || it == 0.0) 1.0 else it }
            val add = obj.optDouble("add", 0.0).let { if (it.isNaN()) 0.0 else it }

            val signed  = obj.optBoolean("sign", false)
            val unit    = obj.optString("unit", "")

            val min = obj.optDouble("min", Double.NaN).takeIf { !it.isNaN() }
            val max = obj.optDouble("max", Double.NaN).takeIf { !it.isNaN() }

            val nullMin = obj.optDouble("nullMin", Double.NaN).takeIf { !it.isNaN() }
            val nullMax = obj.optDouble("nullMax", Double.NaN).takeIf { !it.isNaN() }

            val enumMap: Map<Int, String>? = obj.optJSONObject("map")?.let { mapObj ->
                buildMap {
                    for (key in mapObj.keys()) {
                        val intKey = key.toIntOrNull() ?: continue
                        put(intKey, mapObj.optString(key))
                    }
                }.takeIf { it.isNotEmpty() }
            }

            SignalFormat(
                bix     = bix,
                len     = len,
                mul     = mul,
                div     = div,
                add     = add,
                signed  = signed,
                unit    = unit,
                min     = min,
                max     = max,
                nullMin = nullMin,
                nullMax = nullMax,
                enumMap = enumMap
            )
        } catch (e: Exception) {
            Timber.w(e, "VehicleSignalSetParser: skipping malformed format")
            null
        }
    }
}
