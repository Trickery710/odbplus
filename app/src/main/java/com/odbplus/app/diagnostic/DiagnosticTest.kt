package com.odbplus.app.diagnostic

import androidx.compose.ui.graphics.vector.ImageVector
import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.app.diagnostic.model.DiagnosticSample
import com.odbplus.app.diagnostic.model.DiagTestStep
import com.odbplus.core.protocol.ObdPid

/**
 * Contract for a guided diagnostic test.
 *
 * Each implementation defines:
 * - What [ObdPid]s to monitor
 * - The guided [DiagTestStep]s to execute
 * - How to [analyze] collected samples into a [DiagnosticResult]
 *
 * The test execution lifecycle is managed by [DiagnosticOrchestrator]; tests
 * themselves are stateless — they only declare steps and analyze samples.
 */
interface DiagnosticTest {
    val id: String
    val name: String
    val description: String
    val icon: ImageVector

    fun getSteps(): List<DiagTestStep>
    fun getRequiredPids(): List<ObdPid>
    fun analyze(samples: List<DiagnosticSample>): DiagnosticResult
}
