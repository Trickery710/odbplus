package com.odbplus.app.expertdiag.engine

import com.odbplus.app.expertdiag.model.AutoTestResult
import com.odbplus.app.expertdiag.model.AutoTestStatus
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdService
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes [AutomaticTest]s against a live [ObdService].
 *
 * Enforces the global 20-PID polling limit by capping simultaneous PID
 * usage across all tests in a batch. If ECU latency is high (detected by
 * [ECULatencyMonitor]), the per-test sample count is automatically reduced.
 */
@Singleton
class AutomaticTestRunner @Inject constructor(
    private val obdService: ObdService,
) {
    companion object {
        private const val MAX_GLOBAL_PIDS = 20
    }

    /**
     * Run a list of auto tests in sequence, returning a result for each.
     *
     * Tests are run one at a time (not in parallel) to respect ECU PID limits.
     * If the ECU is too slow (latency > 400 ms), tests requiring many PIDs are
     * automatically capped.
     */
    suspend fun runAll(testIds: List<String>): List<AutoTestResult> {
        val tests = AutoTestRegistry.resolve(testIds)
        val results = mutableListOf<AutoTestResult>()

        // Run latency monitor first to adapt sample counts
        val latencyTest = AutoTestRegistry.lookup("ECULatencyMonitor")
        var slowEcu = false
        if (latencyTest != null && !testIds.contains("ECULatencyMonitor")) {
            try {
                val latencyResult = latencyTest.run(obdService)
                slowEcu = latencyResult.status == AutoTestStatus.FAIL ||
                          latencyResult.status == AutoTestStatus.WARN
            } catch (_: Exception) { }
        }

        val totalPids = tests.flatMap { it.getRequiredPids() }.toSet().size
        val pidOverBudget = totalPids > MAX_GLOBAL_PIDS

        for (test in tests) {
            if (pidOverBudget) {
                val testPids = test.getRequiredPids().size
                if (testPids > MAX_GLOBAL_PIDS / 2) {
                    Timber.w("AutoRunner: skipping ${test.id} — would exceed 20-PID limit")
                    results += AutoTestResult(
                        testId = test.id, testName = test.name,
                        status = AutoTestStatus.SKIPPED,
                        summary = "Skipped — total PIDs across all tests exceeds ECU limit",
                        details = "Too many concurrent PID requests. Reduce active tests.",
                    )
                    continue
                }
            }

            results += try {
                test.run(obdService)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "AutoRunner: ${test.id} threw exception")
                AutoTestResult(
                    testId = test.id, testName = test.name,
                    status = AutoTestStatus.ERROR,
                    summary = "Test error: ${e.message ?: "unknown"}",
                    details = e.stackTraceToString().take(300),
                )
            }
        }

        return results
    }

    /** Run a single test by ID. Returns null if the test is not registered. */
    suspend fun runSingle(testId: String): AutoTestResult? {
        val test = AutoTestRegistry.lookup(testId) ?: return null
        return try {
            test.run(obdService)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AutoTestResult(
                testId = test.id, testName = test.name,
                status = AutoTestStatus.ERROR,
                summary = "Test error: ${e.message ?: "unknown"}",
                details = e.stackTraceToString().take(300),
            )
        }
    }
}
