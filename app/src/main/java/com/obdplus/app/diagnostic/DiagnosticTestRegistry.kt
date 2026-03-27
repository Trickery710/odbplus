package com.obdplus.app.diagnostic

import com.obdplus.app.diagnostic.tests.FuelTrimTest
import com.obdplus.app.diagnostic.tests.MafPlausibilityTest
import com.obdplus.app.diagnostic.tests.MapLoadTest
import com.obdplus.app.diagnostic.tests.O2ResponseTest
import com.obdplus.app.diagnostic.tests.RpmSweepTest

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
