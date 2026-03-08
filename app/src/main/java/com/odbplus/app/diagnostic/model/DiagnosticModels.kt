package com.odbplus.app.diagnostic.model

import com.odbplus.core.protocol.ObdPid

// ── Step model ────────────────────────────────────────────────────────────────

/**
 * A single guided step within a diagnostic test.
 *
 * Each step runs for exactly [durationSeconds], collecting PID samples throughout.
 * [targetRpm] is displayed as a guide to the user but does not gate completion.
 */
data class DiagTestStep(
    val stepNumber: Int,
    val name: String,
    val instruction: String,
    val durationSeconds: Int,
    val targetRpm: Int? = null,
    val monitoredPids: List<ObdPid>,
)

// ── Sample model ──────────────────────────────────────────────────────────────

/** One PID reading captured at a point in time during a specific test step. */
data class DiagnosticSample(
    val timestamp: Long,
    val stepIndex: Int,
    val pid: ObdPid,
    val value: Double?,
)

// ── Result models ─────────────────────────────────────────────────────────────

enum class DiagnosticStatus { PASS, WARNING, FAIL }

/** One evaluated finding within a diagnostic result. */
data class DiagnosticFinding(
    val title: String,
    val detail: String,
    val status: DiagnosticStatus,
)

/**
 * Structured output produced by [DiagnosticTest.analyze].
 *
 * @param confidenceScore  0–100. Higher when more samples were collected and
 *                         more PIDs returned valid data.
 */
data class DiagnosticResult(
    val testName: String,
    val status: DiagnosticStatus,
    val confidenceScore: Int,
    val findings: List<DiagnosticFinding>,
    val detectedIssues: List<String>,
    val possibleCauses: List<String>,
    val recommendedChecks: List<String>,
)

// ── Analysis helpers ──────────────────────────────────────────────────────────

fun List<DiagnosticSample>.valuesForStep(stepIndex: Int, pid: ObdPid): List<Double> =
    filter { it.stepIndex == stepIndex && it.pid == pid }.mapNotNull { it.value }

fun List<DiagnosticSample>.avgForStep(stepIndex: Int, pid: ObdPid): Double? =
    valuesForStep(stepIndex, pid).average().takeIf { !it.isNaN() }

fun List<DiagnosticSample>.maxForStep(stepIndex: Int, pid: ObdPid): Double? =
    valuesForStep(stepIndex, pid).maxOrNull()

fun List<DiagnosticSample>.minForStep(stepIndex: Int, pid: ObdPid): Double? =
    valuesForStep(stepIndex, pid).minOrNull()
