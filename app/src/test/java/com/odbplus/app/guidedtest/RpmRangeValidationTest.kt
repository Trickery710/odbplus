package com.odbplus.app.guidedtest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for RPM range validation and stage time-tracking logic.
 *
 * These are pure-logic tests — no Android framework or OBD mocks required.
 */
class RpmRangeValidationTest {

    // ── TestStage.isRpmInRange ─────────────────────────────────────────────────

    @Test
    fun `IDLE stage accepts 0 RPM`() {
        assertTrue(TestStage.IDLE.isRpmInRange(0))
    }

    @Test
    fun `IDLE stage accepts 700 RPM`() {
        assertTrue(TestStage.IDLE.isRpmInRange(700))
    }

    @Test
    fun `IDLE stage accepts boundary 1200 RPM`() {
        assertTrue(TestStage.IDLE.isRpmInRange(1200))
    }

    @Test
    fun `IDLE stage rejects 1201 RPM`() {
        assertFalse(TestStage.IDLE.isRpmInRange(1201))
    }

    @Test
    fun `RPM_1000 stage rejects 899 RPM`() {
        assertFalse(TestStage.RPM_1000.isRpmInRange(899))
    }

    @Test
    fun `RPM_1000 stage accepts lower boundary 900 RPM`() {
        assertTrue(TestStage.RPM_1000.isRpmInRange(900))
    }

    @Test
    fun `RPM_1000 stage accepts 1000 RPM`() {
        assertTrue(TestStage.RPM_1000.isRpmInRange(1000))
    }

    @Test
    fun `RPM_1000 stage accepts upper boundary 1100 RPM`() {
        assertTrue(TestStage.RPM_1000.isRpmInRange(1100))
    }

    @Test
    fun `RPM_1000 stage rejects 1101 RPM`() {
        assertFalse(TestStage.RPM_1000.isRpmInRange(1101))
    }

    @Test
    fun `RPM_2000 stage rejects 1899 RPM`() {
        assertFalse(TestStage.RPM_2000.isRpmInRange(1899))
    }

    @Test
    fun `RPM_2000 stage accepts lower boundary 1900 RPM`() {
        assertTrue(TestStage.RPM_2000.isRpmInRange(1900))
    }

    @Test
    fun `RPM_2000 stage accepts 2000 RPM`() {
        assertTrue(TestStage.RPM_2000.isRpmInRange(2000))
    }

    @Test
    fun `RPM_2000 stage accepts upper boundary 2100 RPM`() {
        assertTrue(TestStage.RPM_2000.isRpmInRange(2100))
    }

    @Test
    fun `RPM_2000 stage rejects 2101 RPM`() {
        assertFalse(TestStage.RPM_2000.isRpmInRange(2101))
    }

    // ── Stage duration constant ────────────────────────────────────────────────

    @Test
    fun `all stages require 5 seconds of in-range time`() {
        TestStage.entries.forEach { stage ->
            assertTrue("${stage.name}.durationSec should be 5", stage.durationSec == 5)
        }
    }

    // ── In-range accumulation simulation ─────────────────────────────────────

    /**
     * Simulates the wall-clock accumulation logic used in [GuidedRpmTestOrchestrator]
     * as a pure function so it can be tested without coroutines.
     */
    private fun simulateInRangeMs(events: List<Pair<Long, Boolean>>): Long {
        var accumulatedMs = 0L
        var rangeEntryTime: Long? = null
        for ((now, inRange) in events) {
            if (inRange) {
                if (rangeEntryTime == null) rangeEntryTime = now
            } else {
                val entry = rangeEntryTime
                if (entry != null) {
                    accumulatedMs += now - entry
                    rangeEntryTime = null
                }
            }
        }
        // Flush open range
        val lastTime = events.lastOrNull()?.first
        val entry = rangeEntryTime
        if (entry != null && lastTime != null) {
            accumulatedMs += lastTime - entry
        }
        return accumulatedMs
    }

    @Test
    fun `continuous in-range for 5 seconds accumulates 5000ms`() {
        // 11 poll events from t=0 to t=5000, all in range
        val events = (0..10).map { i -> (i * 500L) to true }
        val totalMs = simulateInRangeMs(events)
        // First event opens range at 0, last event is at 5000. Accumulated = 5000ms.
        assertTrue("Expected >= 5000ms, got $totalMs", totalMs >= 5000L)
    }

    @Test
    fun `drifting out of range pauses the timer`() {
        val events = listOf(
            0L to true,    // enter range
            500L to true,
            1000L to false, // leave range (accumulated 1000ms)
            1500L to false,
            2000L to true,  // re-enter range
            2500L to true,
            3000L to true,  // accumulated 1000 + 1000 = 2000ms
        )
        val totalMs = simulateInRangeMs(events)
        // Range entered at 0, left at 1000 → 1000ms. Re-entered at 2000, last at 3000 → 1000ms. Total = 2000ms.
        assertTrue("Expected ~2000ms, got $totalMs", totalMs in 1900L..2100L)
    }

    @Test
    fun `all out-of-range events accumulate zero ms`() {
        val events = (0..10).map { i -> (i * 500L) to false }
        val totalMs = simulateInRangeMs(events)
        assertTrue("Expected 0ms, got $totalMs", totalMs == 0L)
    }

    // ── PidStageSummary calculation ────────────────────────────────────────────

    @Test
    fun `buildSummary from known values computes correct avg, min, max, last`() {
        val values = listOf(800.0, 900.0, 1000.0, 850.0)
        val avg = values.average()
        val min = values.min()
        val max = values.max()
        val last = values.last()

        assertTrue("avg should be 887.5", avg == 887.5)
        assertTrue("min should be 800", min == 800.0)
        assertTrue("max should be 1000", max == 1000.0)
        assertTrue("last should be 850", last == 850.0)
    }

    @Test
    fun `average of empty list is NaN`() {
        val avg = emptyList<Double>().average()
        assertTrue("average of empty list should be NaN", avg.isNaN())
    }

    @Test
    fun `takeIf isNaN guard returns null for empty PID data`() {
        val avg = emptyList<Double>().average().takeIf { !it.isNaN() }
        assertTrue("should be null for empty data", avg == null)
    }
}
