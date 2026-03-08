package com.odbplus.app.diagnostic

import com.odbplus.app.diagnostic.tests.FuelTrimTest
import com.odbplus.app.diagnostic.tests.MafPlausibilityTest
import com.odbplus.app.diagnostic.tests.MapLoadTest
import com.odbplus.app.diagnostic.tests.O2ResponseTest
import com.odbplus.app.diagnostic.tests.RpmSweepTest

/**
 * Central registry of all available guided diagnostic tests.
 *
 * Tests are listed in a logical diagnostic order: broad sensor sweep first,
 * then increasingly targeted follow-up investigations.
 */
object DiagnosticTestRegistry {

    val tests: List<DiagnosticTest> = listOf(
        RpmSweepTest(),
        FuelTrimTest(),
        O2ResponseTest(),
        MapLoadTest(),
        MafPlausibilityTest(),
    )

    fun getTest(id: String): DiagnosticTest? = tests.find { it.id == id }
}
