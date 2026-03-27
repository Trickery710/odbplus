package com.obdplus.app.ai.diagnostic

data class DiagnosticPipelineResult(
    val symptomTokens: String = "",
    val plausibilityFlags: List<String> = emptyList(),
    val electricalFlags: List<String> = emptyList(),
    val hypothesisTokens: List<String> = emptyList(),
    val knowledgeTokens: List<String> = emptyList(),
    val guidedTestIds: List<String> = emptyList(),
    val rootCauseProbabilities: List<Pair<String, Float>> = emptyList(),
    val telemetryAnomalies: List<String> = emptyList()
) {
    /** All flags combined for X: section */
    val allFlags: List<String> get() = (plausibilityFlags + electricalFlags + telemetryAnomalies).distinct()
}
