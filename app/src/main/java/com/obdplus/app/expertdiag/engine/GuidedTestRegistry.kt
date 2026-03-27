package com.obdplus.app.expertdiag.engine

import com.obdplus.app.diagnostic.DiagnosticTest
import com.obdplus.app.expertdiag.tests.guided.CylinderContributionTest
import com.obdplus.app.expertdiag.tests.guided.EVAPLeakTest
import com.obdplus.app.expertdiag.tests.guided.FuelPressureTest
import com.obdplus.app.expertdiag.tests.guided.RPMSweepGuidedTest
import com.obdplus.app.expertdiag.tests.guided.ThrottleSnapTest

/**
 * Central registry of all guided [DiagnosticTest] implementations.
 *
 * Test [DiagnosticTest.id] values must match the `guided_tests` column values
 * in the knowledge base CSV.
 */
object GuidedTestRegistry {

    private val all: List<DiagnosticTest> = listOf(
        RPMSweepGuidedTest(),
        ThrottleSnapTest(),
        FuelPressureTest(),
        EVAPLeakTest(),
        CylinderContributionTest(),
    )

    private val byId = all.associateBy { it.id }

    fun lookup(id: String): DiagnosticTest? = byId[id.trim()]

    fun resolve(ids: List<String>): List<DiagnosticTest> =
        ids.mapNotNull { lookup(it) }
}
