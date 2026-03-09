package com.odbplus.app.expertdiag.model

/**
 * One row from the OBD diagnostic knowledge base CSV.
 *
 * Parsed at app startup from assets/obd_diagnostic_knowledgebase.csv.
 */
data class KnowledgeBaseEntry(
    val number: Int,
    val dtc: String,                      // e.g. "P0100"
    val description: String,
    val severity: DtcSeverity,
    val commonCauses: List<String>,
    val automaticTests: List<String>,      // test IDs matching AutomaticTestRegistry
    val guidedTests: List<String>,         // test IDs matching GuidedTestRegistry
    val monitoredPids: List<String>,       // raw PID name strings from CSV
)

enum class DtcSeverity(val label: String, val ordinal2: Int) {
    LOW("Low", 0),
    MEDIUM("Medium", 1),
    HIGH("High", 2),
    CRITICAL("Critical", 3);

    companion object {
        fun from(text: String): DtcSeverity = when (text.trim().lowercase()) {
            "low" -> LOW
            "high" -> HIGH
            "critical" -> CRITICAL
            else -> MEDIUM
        }
    }
}
