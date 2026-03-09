package com.odbplus.app.expertdiag.model

/** Status of a single automatic diagnostic test run. */
enum class AutoTestStatus { RUNNING, PASS, WARN, FAIL, SKIPPED, ERROR }

/**
 * Result produced by one [AutomaticTest] run.
 */
data class AutoTestResult(
    val testId: String,
    val testName: String,
    val status: AutoTestStatus,
    val summary: String,
    val details: String,
    val pidReadings: Map<String, Double?> = emptyMap(),
    val confidenceScore: Int = 0,       // 0–100
)
