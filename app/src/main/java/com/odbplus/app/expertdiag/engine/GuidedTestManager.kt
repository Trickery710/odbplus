package com.odbplus.app.expertdiag.engine

import com.odbplus.app.diagnostic.DiagnosticOrchestrator
import com.odbplus.app.diagnostic.DiagnosticTest
import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.core.protocol.ObdService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class GuidedTestSession(
    val testId: String,
    val testName: String,
    val currentStepIndex: Int,
    val currentStepName: String,
    val currentInstruction: String,
    val remainingSeconds: Int,
    val totalSteps: Int,
    val liveValues: Map<String, String> = emptyMap(),
)

/**
 * Manages the lifecycle of one guided [DiagnosticTest] at a time.
 *
 * The currently running test is exposed via [activeSession] StateFlow.
 * Results are accumulated in [completedResults].
 *
 * Integrates with the existing [DiagnosticOrchestrator] so all existing
 * tests work without modification.
 */
@Singleton
class GuidedTestManager @Inject constructor(
    private val obdService: ObdService,
) {
    private val orchestrator = DiagnosticOrchestrator(obdService)

    private val _activeSession = MutableStateFlow<GuidedTestSession?>(null)
    val activeSession: StateFlow<GuidedTestSession?> = _activeSession.asStateFlow()

    private val _completedResults = MutableStateFlow<List<DiagnosticResult>>(emptyList())
    val completedResults: StateFlow<List<DiagnosticResult>> = _completedResults.asStateFlow()

    private var currentJob: Job? = null

    /**
     * Start a guided test. Any currently running test is cancelled first.
     *
     * @param testId  ID from [GuidedTestRegistry]
     * @param scope   Coroutine scope (caller-controlled lifecycle)
     * @param onResult Callback when the test finishes and analysis is complete
     */
    fun startTest(
        testId: String,
        scope: CoroutineScope,
        onResult: (DiagnosticResult) -> Unit = {},
    ) {
        val test: DiagnosticTest = GuidedTestRegistry.lookup(testId) ?: run {
            Timber.w("GuidedTestManager: unknown test '$testId'")
            return
        }
        cancelCurrentTest()
        val steps = test.getSteps()

        currentJob = scope.launch {
            try {
                val samples = orchestrator.runTest(
                    test = test,
                    onStepStart = { index ->
                        val step = steps.getOrNull(index) ?: return@runTest
                        _activeSession.value = GuidedTestSession(
                            testId = testId,
                            testName = test.name,
                            currentStepIndex = index,
                            currentStepName = step.name,
                            currentInstruction = step.instruction,
                            remainingSeconds = step.durationSeconds,
                            totalSteps = steps.size,
                        )
                    },
                    onProgress = { index, remaining, live ->
                        val step = steps.getOrNull(index) ?: return@runTest
                        _activeSession.value = GuidedTestSession(
                            testId = testId,
                            testName = test.name,
                            currentStepIndex = index,
                            currentStepName = step.name,
                            currentInstruction = step.instruction,
                            remainingSeconds = remaining,
                            totalSteps = steps.size,
                            liveValues = live.entries.associate { (pid, v) ->
                                pid.description to (v?.let { "${"%.2f".format(it)} ${pid.unit}" } ?: "--")
                            }
                        )
                    },
                )
                val result = test.analyze(samples)
                _completedResults.value = _completedResults.value + result
                onResult(result)
            } catch (e: CancellationException) {
                // normal cancellation
            } catch (e: Exception) {
                Timber.e(e, "GuidedTestManager: test '$testId' threw exception")
            } finally {
                _activeSession.value = null
            }
        }
    }

    fun cancelCurrentTest() {
        currentJob?.cancel()
        currentJob = null
        _activeSession.value = null
    }

    fun clearResults() {
        _completedResults.value = emptyList()
    }
}
