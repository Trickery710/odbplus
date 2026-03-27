package com.obdplus.app.ai.diagnostic

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuidedTestEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class TestStep(
        val stepNumber: Int,
        val instruction: String,
        val expectedResult: String,
        val toolRequired: String
    )

    data class GuidedTest(
        val testId: String,
        val title: String,
        val steps: List<TestStep>
    )

    // Lazy-loaded test library
    private val testById: Map<String, GuidedTest> by lazy { loadTestLibrary() }
    private val tokenToTestIds: Map<String, List<String>> by lazy { buildTokenIndex() }

    // ── Public API ────────────────────────────────────────────────────────────

    fun getTestsForFaults(faultTokens: List<String>): List<GuidedTest> {
        val ids = getTestIds(faultTokens)
        return ids.mapNotNull { testById[it] }
    }

    fun getTestIds(faultTokens: List<String>): List<String> {
        val ids = mutableListOf<String>()
        faultTokens.forEach { token ->
            tokenToTestIds[token]?.let { ids.addAll(it) }
        }
        return ids.distinct()
    }

    // ── CSV Loader ────────────────────────────────────────────────────────────

    private fun loadTestLibrary(): Map<String, GuidedTest> {
        // Intermediate: testId → (title, faultTokens, list of steps)
        data class RawRow(
            val testId: String,
            val faultTokens: List<String>,
            val title: String,
            val stepNumber: Int,
            val instruction: String,
            val expectedResult: String,
            val toolRequired: String
        )

        val rawRows = mutableListOf<RawRow>()

        try {
            context.assets.open("data/test_library.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = line.split(",", limit = 7)
                    if (cols.size < 7) return@forEach
                    rawRows += RawRow(
                        testId         = cols[0].trim(),
                        faultTokens    = cols[1].trim().split("|").map { it.trim() }.filter { it.isNotEmpty() },
                        title          = cols[2].trim(),
                        stepNumber     = cols[3].trim().toIntOrNull() ?: 1,
                        instruction    = cols[4].trim(),
                        expectedResult = cols[5].trim(),
                        toolRequired   = cols[6].trim()
                    )
                }
            }
        } catch (_: Exception) { }

        // Group by testId
        val grouped = rawRows.groupBy { it.testId }
        return grouped.mapValues { (testId, rows) ->
            val title = rows.first().title
            val steps = rows
                .sortedBy { it.stepNumber }
                .map { TestStep(it.stepNumber, it.instruction, it.expectedResult, it.toolRequired) }
            GuidedTest(testId = testId, title = title, steps = steps)
        }
    }

    private fun buildTokenIndex(): Map<String, List<String>> {
        // Re-parse just to get faultTokens per testId
        data class TokenEntry(val testId: String, val tokens: List<String>)

        val entries = mutableListOf<TokenEntry>()
        val seenTestIds = mutableSetOf<String>()

        try {
            context.assets.open("data/test_library.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = line.split(",", limit = 7)
                    if (cols.size < 2) return@forEach
                    val testId = cols[0].trim()
                    if (testId in seenTestIds) return@forEach
                    seenTestIds += testId
                    val tokens = cols[1].trim().split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    entries += TokenEntry(testId, tokens)
                }
            }
        } catch (_: Exception) { }

        val index = mutableMapOf<String, MutableList<String>>()
        entries.forEach { entry ->
            entry.tokens.forEach { token ->
                index.getOrPut(token) { mutableListOf() } += entry.testId
            }
        }
        return index
    }
}
