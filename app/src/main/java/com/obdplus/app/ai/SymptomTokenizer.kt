package com.obdplus.app.ai

/**
 * Converts user-typed symptom text into compact diagnostic tokens (S1–S9+).
 *
 * Each token maps to a known failure pattern. When a symptom phrase cannot be
 * matched, the raw text is preserved so the AI still has full context.
 */
object SymptomTokenizer {

    // Ordered by specificity — check longer / more specific phrases first
    private val tokenMap: List<Pair<String, String>> = listOf(
        "crank no start"   to "S2",
        "cranks no start"  to "S2",
        "crank but"        to "S2",
        "won't start"      to "S2",
        "wont start"       to "S2",
        "hard start"       to "S2",
        "hard restart"     to "S2",
        "no start"         to "S2",
        "rough idle"       to "S4",
        "idle rough"       to "S4",
        "rough at idle"    to "S4",
        "no throttle"      to "S3",
        "throttle response" to "S3",
        "no response"      to "S3",
        "loss of power"    to "S8",
        "no power"         to "S8",
        "low power"        to "S8",
        "check engine"     to "S9",
        "cel on"           to "S9",
        "mil on"           to "S9",
        "overheating"      to "S5",
        "overheat"         to "S5",
        "running hot"      to "S5",
        "misfire"          to "S6",
        "misfiring"        to "S6",
        "hesitation"       to "S7",
        "hesitates"        to "S7",
        "stumble"          to "S7",
        "stalling"         to "S1",
        "stalls"           to "S1",
        "stall"            to "S1",
        "dies"             to "S1",
        "died"             to "S1",
        "cuts out"         to "S1",
        "throttle"         to "S3",
        "idle"             to "S4",
        "crank"            to "S2"
    )

    /**
     * Parses a multi-symptom string (comma/semicolon/newline separated) and
     * returns a compact token list: e.g. "stall, rough idle" → "S1,S4".
     *
     * Symptoms with no matching token are kept as raw text. Duplicate tokens
     * are collapsed.
     */
    fun tokenize(symptoms: String): String {
        val parts = symptoms
            .split(Regex("[,;\n]"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val tokens = mutableListOf<String>()
        val raw = mutableListOf<String>()

        for (symptom in parts) {
            val lower = symptom.lowercase()
            val token = tokenMap.firstOrNull { (key, _) -> lower.contains(key) }?.second
            when {
                token != null && token !in tokens -> tokens += token
                token == null -> raw += symptom
            }
        }

        return (tokens + raw).joinToString(",")
    }
}
