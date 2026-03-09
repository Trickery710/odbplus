package com.odbplus.app.expertdiag.engine

import com.odbplus.app.diagnostic.DiagnosticTest
import com.odbplus.app.expertdiag.tests.guided.CylinderContributionTest
import com.odbplus.app.expertdiag.tests.guided.EVAPLeakTest
import com.odbplus.app.expertdiag.tests.guided.FuelPressureTest
import com.odbplus.app.expertdiag.tests.guided.RPMSweepGuidedTest
import com.odbplus.app.expertdiag.tests.guided.ThrottleSnapTest

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
