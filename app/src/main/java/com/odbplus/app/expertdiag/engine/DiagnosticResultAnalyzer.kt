package com.odbplus.app.expertdiag.engine

import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.app.diagnostic.model.DiagnosticStatus
import com.odbplus.app.expertdiag.model.AutoTestResult
import com.odbplus.app.expertdiag.model.AutoTestStatus
import com.odbplus.app.expertdiag.model.KnowledgeBaseEntry
import com.odbplus.app.expertdiag.model.RootCauseProbability
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates root-cause probability scores for a DTC by cross-referencing:
 *  - The knowledge base [KnowledgeBaseEntry.commonCauses]
 *  - [AutoTestResult] outcomes (FAIL/WARN confirm a cause; PASS reduces it)
 *  - [DiagnosticResult] guided test findings
 *
 * Probabilities are relative weights, normalised to sum to 1.0.
 */
@Singleton
class DiagnosticResultAnalyzer @Inject constructor() {

    /**
     * Compute root-cause probabilities for a single DTC.
     *
     * Returns causes sorted descending by probability.
     */
    fun analyze(
        entry: KnowledgeBaseEntry,
        autoResults: List<AutoTestResult>,
        guidedResults: List<DiagnosticResult>,
    ): List<RootCauseProbability> {
        if (entry.commonCauses.isEmpty()) return emptyList()

        // Start with equal base weights — adjusted by evidence
        val weights = entry.commonCauses.associateWith { 1.0f }.toMutableMap()
        val evidence = entry.commonCauses.associateWith { mutableListOf<String>() }.toMutableMap()

        // ── Auto test evidence ─────────────────────────────────────────────
        for (result in autoResults) {
            when (result.status) {
                AutoTestStatus.FAIL -> {
                    // FAIL strongly implicates certain causes by keyword matching
                    for (cause in entry.commonCauses) {
                        val match = causeMatchesTest(cause, result.testId, result.summary)
                        when {
                            match > 0 -> {
                                weights[cause] = (weights[cause] ?: 1f) + match * 2.0f
                                evidence[cause]?.add("${result.testName}: FAIL — ${result.summary}")
                            }
                            match < 0 -> {
                                weights[cause] = (weights[cause] ?: 1f) * 0.4f
                                evidence[cause]?.add("${result.testName}: PASS — contradicts this cause")
                            }
                        }
                    }
                }
                AutoTestStatus.WARN -> {
                    for (cause in entry.commonCauses) {
                        val match = causeMatchesTest(cause, result.testId, result.summary)
                        if (match > 0) {
                            weights[cause] = (weights[cause] ?: 1f) + match * 1.0f
                            evidence[cause]?.add("${result.testName}: WARN — ${result.summary}")
                        }
                    }
                }
                AutoTestStatus.PASS -> {
                    for (cause in entry.commonCauses) {
                        val match = causeMatchesTest(cause, result.testId, result.summary)
                        if (match > 0) {
                            // A passing test for that domain slightly reduces the related cause
                            weights[cause] = (weights[cause] ?: 1f) * 0.7f
                        }
                    }
                }
                else -> Unit
            }
        }

        // ── Guided test evidence ───────────────────────────────────────────
        for (result in guidedResults) {
            for (finding in result.findings) {
                if (finding.status == DiagnosticStatus.FAIL || finding.status == DiagnosticStatus.WARNING) {
                    for (cause in entry.commonCauses) {
                        if (textOverlap(cause, finding.title) > 0 || textOverlap(cause, finding.detail) > 0) {
                            weights[cause] = (weights[cause] ?: 1f) + 1.5f
                            evidence[cause]?.add("Guided: ${finding.title}")
                        }
                    }
                }
            }
            for (detectedIssue in result.detectedIssues) {
                for (cause in entry.commonCauses) {
                    if (textOverlap(cause, detectedIssue) > 0) {
                        weights[cause] = (weights[cause] ?: 1f) + 1.0f
                    }
                }
            }
        }

        // Normalise to 0.0–1.0
        val total = weights.values.sum().coerceAtLeast(0.01f)
        return entry.commonCauses
            .map { cause ->
                RootCauseProbability(
                    cause = cause,
                    probability = (weights[cause] ?: 1f) / total,
                    supportingEvidence = evidence[cause]?.toList() ?: emptyList(),
                )
            }
            .sortedByDescending { it.probability }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns a positive score if the cause is implicated by the test result,
     * negative if contradicted, 0 if unrelated.
     */
    private fun causeMatchesTest(cause: String, testId: String, summary: String): Float {
        val causeL = cause.lowercase()
        val testL = testId.lowercase()
        val summaryL = summary.lowercase()

        // Domain-specific keyword mapping
        val domainMatches = mapOf(
            "maf" to listOf("maf", "mass air", "air flow"),
            "vacuum" to listOf("vacuum", "intake leak", "manifold"),
            "fuel" to listOf("fuel trim", "lean", "rich", "stft", "ltft"),
            "o2" to listOf("o2", "oxygen", "lambda"),
            "map" to listOf("map", "manifold pressure"),
            "misfire" to listOf("misfire", "rpm jitter", "cylinder"),
            "catalyst" to listOf("catalyst", "catalytic", "downstream"),
            "injector" to listOf("injector", "fuel injector"),
            "wiring" to listOf("wiring", "connector", "circuit"),
        )

        var score = 0f
        for ((domain, keywords) in domainMatches) {
            val causeHasDomain = keywords.any { causeL.contains(it) }
            val testOrSummaryHasDomain = keywords.any { testL.contains(it) || summaryL.contains(it) }
            if (causeHasDomain && testOrSummaryHasDomain) score += 1.0f
        }

        return score
    }

    /** Count common words between two strings (simple overlap score). */
    private fun textOverlap(a: String, b: String): Int {
        val wordsA = a.lowercase().split(" ", "-", "_").filter { it.length > 3 }.toSet()
        val wordsB = b.lowercase().split(" ", "-", "_", ":", ".").filter { it.length > 3 }.toSet()
        return (wordsA intersect wordsB).size
    }
}
