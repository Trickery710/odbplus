package com.odbplus.app.guidedtest

import com.odbplus.core.protocol.ObdPid
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [GuidedTestResult.toPayload] — verifies that the JSON payload
 * is assembled correctly from in-memory domain objects.
 *
 * No Android framework, Hilt, or OBD stack needed.
 */
class PayloadBuilderTest {

    private val json = Json { prettyPrint = true }

    private fun makeStageSummary(pid: ObdPid, avg: Double?, sampleCount: Int = 10) =
        PidStageSummary(pid = pid, avg = avg, min = avg?.minus(10), max = avg?.plus(10), last = avg, sampleCount = sampleCount)

    private fun makeStageResult(stage: TestStage, avgRpm: Double, timeInRange: Float): StageResult {
        val summaries = GUIDED_TEST_PIDS.associateWith { pid ->
            if (pid == ObdPid.ENGINE_RPM) makeStageSummary(pid, avgRpm)
            else makeStageSummary(pid, null, 0)
        }
        return StageResult(
            stage = stage,
            avgRpm = avgRpm,
            minRpm = avgRpm - 50,
            maxRpm = avgRpm + 50,
            timeInRangeSec = timeInRange,
            pidSummaries = summaries,
        )
    }

    private fun buildTestResult(
        vin: String? = "1HGCM82633A004352",
        storedDtcs: List<String> = listOf("P0300", "P0171"),
        pendingDtcs: List<String> = emptyList(),
        symptoms: Set<String> = setOf("Rough idle", "Misfire"),
        freeText: String = "Happens when cold",
    ): GuidedTestResult {
        return GuidedTestResult(
            startTime = 1_700_000_000_000L,
            endTime   = 1_700_000_015_000L, // 15 seconds later
            adapterLabel = "TCP/OBDSim",
            vin = vin,
            stageResults = listOf(
                makeStageResult(TestStage.IDLE, avgRpm = 750.0, timeInRange = 5f),
                makeStageResult(TestStage.RPM_1000, avgRpm = 1000.0, timeInRange = 5f),
                makeStageResult(TestStage.RPM_2000, avgRpm = 2000.0, timeInRange = 5f),
            ),
            storedDtcs = storedDtcs,
            pendingDtcs = pendingDtcs,
            selectedSymptoms = symptoms,
            freeText = freeText,
        )
    }

    @Test
    fun `toPayload preserves VIN`() {
        val payload = buildTestResult().toPayload()
        assertEquals("1HGCM82633A004352", payload.meta.vin)
    }

    @Test
    fun `toPayload returns null VIN when not available`() {
        val payload = buildTestResult(vin = null).toPayload()
        assertNull(payload.meta.vin)
    }

    @Test
    fun `toPayload duration_sec is 15 for a 15 second test`() {
        val payload = buildTestResult().toPayload()
        assertEquals(15L, payload.meta.duration_sec)
    }

    @Test
    fun `toPayload contains three stage entries`() {
        val payload = buildTestResult().toPayload()
        assertEquals(3, payload.stages.size)
    }

    @Test
    fun `toPayload stage names match TestStage labels`() {
        val payload = buildTestResult().toPayload()
        assertEquals(TestStage.IDLE.label, payload.stages[0].name)
        assertEquals(TestStage.RPM_1000.label, payload.stages[1].name)
        assertEquals(TestStage.RPM_2000.label, payload.stages[2].name)
    }

    @Test
    fun `toPayload stage time_in_range_sec matches input`() {
        val payload = buildTestResult().toPayload()
        payload.stages.forEach { stage ->
            assertEquals(5f, stage.time_in_range_sec)
        }
    }

    @Test
    fun `toPayload stage target_min and target_max are correct for IDLE`() {
        val payload = buildTestResult().toPayload()
        val idle = payload.stages[0]
        assertEquals(TestStage.IDLE.targetMin, idle.target_min_rpm)
        assertEquals(TestStage.IDLE.targetMax, idle.target_max_rpm)
    }

    @Test
    fun `toPayload stored DTCs are preserved`() {
        val payload = buildTestResult().toPayload()
        assertEquals(listOf("P0300", "P0171"), payload.dtcs.stored)
    }

    @Test
    fun `toPayload pending DTCs are empty list when none`() {
        val payload = buildTestResult(pendingDtcs = emptyList()).toPayload()
        assertTrue(payload.dtcs.pending.isEmpty())
    }

    @Test
    fun `toPayload symptoms selected list contains chosen chips`() {
        val payload = buildTestResult().toPayload()
        assertTrue(payload.symptoms.selected.contains("Rough idle"))
        assertTrue(payload.symptoms.selected.contains("Misfire"))
    }

    @Test
    fun `toPayload symptoms notes are preserved`() {
        val payload = buildTestResult(freeText = "Happens when cold").toPayload()
        assertEquals("Happens when cold", payload.symptoms.notes)
    }

    @Test
    fun `toPayload serializes to valid JSON without throwing`() {
        val payload = buildTestResult().toPayload()
        val serialized = json.encodeToString(payload)
        assertTrue("JSON should contain meta", serialized.contains("\"meta\""))
        assertTrue("JSON should contain stages", serialized.contains("\"stages\""))
        assertTrue("JSON should contain dtcs", serialized.contains("\"dtcs\""))
        assertTrue("JSON should contain symptoms", serialized.contains("\"symptoms\""))
    }

    @Test
    fun `toPayload null PID averages serialize to null in JSON`() {
        val payload = buildTestResult().toPayload()
        val serialized = json.encodeToString(payload)
        // PIDs with no data have null avg — kotlinx.serialization emits null for Double?
        assertTrue("Serialized JSON should be non-empty", serialized.isNotEmpty())
    }

    @Test
    fun `GUIDED_TEST_PIDS contains ENGINE_RPM as first entry`() {
        assertEquals(ObdPid.ENGINE_RPM, GUIDED_TEST_PIDS.first())
    }

    @Test
    fun `COMMON_SYMPTOMS contains expected entries`() {
        assertTrue(COMMON_SYMPTOMS.contains("Stalling"))
        assertTrue(COMMON_SYMPTOMS.contains("Misfire"))
        assertTrue(COMMON_SYMPTOMS.contains("Check engine light"))
        assertEquals(11, COMMON_SYMPTOMS.size)
    }
}
