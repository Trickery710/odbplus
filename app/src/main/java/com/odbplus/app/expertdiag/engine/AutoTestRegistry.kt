package com.odbplus.app.expertdiag.engine

import com.odbplus.app.expertdiag.tests.auto.CatalystEfficiencyCheck
import com.odbplus.app.expertdiag.tests.auto.ECULatencyMonitor
import com.odbplus.app.expertdiag.tests.auto.FuelTrimAnalysis
import com.odbplus.app.expertdiag.tests.auto.MAFPlausibilityCheck
import com.odbplus.app.expertdiag.tests.auto.MAPSensorPlausibility
import com.odbplus.app.expertdiag.tests.auto.MisfireDetectionMode6
import com.odbplus.app.expertdiag.tests.auto.O2SensorResponseCheck

/**
 * Central registry of all [AutomaticTest] implementations.
 *
 * Test [AutomaticTest.id] values must match the `automatic_tests` column values
 * in the knowledge base CSV (semicolon-separated list per row).
 */
object AutoTestRegistry {

    private val all: List<AutomaticTest> = listOf(
        FuelTrimAnalysis(),
        MAFPlausibilityCheck(),
        O2SensorResponseCheck(),
        MAPSensorPlausibility(),
        ECULatencyMonitor(),
        MisfireDetectionMode6(),
        CatalystEfficiencyCheck(),
    )

    private val byId = all.associateBy { it.id }

    fun lookup(id: String): AutomaticTest? = byId[id]

    /** Return all tests whose IDs appear in [ids]. Preserves declaration order. */
    fun resolve(ids: List<String>): List<AutomaticTest> =
        ids.mapNotNull { lookup(it.trim()) }
}
