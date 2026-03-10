package com.odbplus.app.vin

import com.odbplus.app.vin.domain.DecodedVin
import com.odbplus.app.vin.domain.DecodeSource
import com.odbplus.app.vin.domain.VerificationStatus
import com.odbplus.app.vin.domain.VinVerificationFlag
import com.odbplus.app.vin.validation.VinValidator
import com.odbplus.app.vin.verification.VinVerificationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VinVerificationEngineTest {

    private val validVin = "1HGBH41JXMN109186"

    private fun makeDecoded(
        vin: String = validVin,
        make: String? = "HONDA",
        model: String? = "Civic",
        modelYear: Int? = 2021,
        manufacturer: String? = "Honda Manufacturing of Alabama, LLC"
    ) = DecodedVin(
        vin = vin,
        make = make,
        model = model,
        modelYear = modelYear,
        trim = null,
        series = null,
        manufacturer = manufacturer,
        vehicleType = "PASSENGER CAR",
        bodyClass = "Sedan/Saloon",
        engineModel = null,
        engineCylinders = 4,
        displacementL = 2.0,
        fuelTypePrimary = "Gasoline",
        fuelTypeSecondary = null,
        driveType = "FWD",
        transmissionStyle = "Automatic",
        plantCountry = "USA",
        plantCompany = null,
        plantCity = "Marysville",
        plantState = "Ohio",
        gvwrClass = null,
        brakeSystemType = null,
        airBagLocations = null,
        source = DecodeSource.NHTSA,
        decodeTimestamp = System.currentTimeMillis(),
        confidence = 0f,
        verificationStatus = VerificationStatus.PARTIAL
    )

    // ── Local-only verification ───────────────────────────────────────────────

    @Test
    fun `local valid VIN produces reasonable confidence`() {
        val validation = VinValidator.validate(validVin)
        val result = VinVerificationEngine.verifyLocalOnly(validation)
        assertTrue(result.confidence > 0f)
        assertTrue(result.has(VinVerificationFlag.LENGTH_VALID))
        assertTrue(result.has(VinVerificationFlag.ALLOWED_CHARS_VALID))
        assertTrue(result.has(VinVerificationFlag.CHECK_DIGIT_VALID))
    }

    @Test
    fun `local-only suspect VIN gets lower confidence than valid`() {
        val validResult = VinVerificationEngine.verifyLocalOnly(VinValidator.validate(validVin))
        // Tamper check digit
        val suspectResult = VinVerificationEngine.verifyLocalOnly(VinValidator.validate("1HGBH41JYMN109186"))
        assertTrue(suspectResult.confidence < validResult.confidence)
    }

    // ── Remote verification ───────────────────────────────────────────────────

    @Test
    fun `full valid decode produces high confidence`() {
        val validation = VinValidator.validate(validVin)
        val decoded = makeDecoded(vin = validVin, modelYear = 2021)
        val result = VinVerificationEngine.verifyWithRemote(validation, decoded)
        assertTrue(result.has(VinVerificationFlag.ONLINE_DECODE_SUCCESS))
        assertTrue(result.has(VinVerificationFlag.REMOTE_VIN_MATCH))
        assertTrue(result.has(VinVerificationFlag.REMOTE_CORE_FIELDS_PRESENT))
        assertTrue(result.confidence >= 0.70f)
    }

    @Test
    fun `VIN mismatch from provider adds error and lowers confidence`() {
        val validation = VinValidator.validate(validVin)
        val decoded = makeDecoded(vin = "DIFFERENT12345678")
        val result = VinVerificationEngine.verifyWithRemote(validation, decoded)
        assertFalse(result.has(VinVerificationFlag.REMOTE_VIN_MATCH))
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `model year mismatch generates a warning`() {
        val validation = VinValidator.validate("1HGBH41JXMN109186") // year code M=2021
        val decoded = makeDecoded(modelYear = 1995) // deliberate mismatch
        val result = VinVerificationEngine.verifyWithRemote(validation, decoded)
        assertFalse(result.has(VinVerificationFlag.REMOTE_MODEL_YEAR_MATCH))
        assertTrue(result.warnings.any { it.contains("mismatch", ignoreCase = true) })
    }

    @Test
    fun `WMI known and make matches sets REMOTE_WMI_MATCH`() {
        // WMI "1HG" is Honda
        val validation = VinValidator.validate(validVin)
        val decoded = makeDecoded(make = "HONDA", manufacturer = "Honda")
        val result = VinVerificationEngine.verifyWithRemote(validation, decoded)
        assertTrue(result.has(VinVerificationFlag.WMI_KNOWN))
        assertTrue(result.has(VinVerificationFlag.REMOTE_WMI_MATCH))
    }

    @Test
    fun `WMI known but make mismatches does not set REMOTE_WMI_MATCH`() {
        val validation = VinValidator.validate(validVin)
        val decoded = makeDecoded(make = "TOYOTA", manufacturer = "Toyota")
        val result = VinVerificationEngine.verifyWithRemote(validation, decoded)
        assertTrue(result.has(VinVerificationFlag.WMI_KNOWN))
        assertFalse(result.has(VinVerificationFlag.REMOTE_WMI_MATCH))
        assertTrue(result.warnings.any { it.contains("WMI") })
    }

    @Test
    fun `missing core fields does not set REMOTE_CORE_FIELDS_PRESENT`() {
        val validation = VinValidator.validate(validVin)
        val decoded = makeDecoded(make = null, model = null, modelYear = null)
        val result = VinVerificationEngine.verifyWithRemote(validation, decoded)
        assertFalse(result.has(VinVerificationFlag.REMOTE_CORE_FIELDS_PRESENT))
    }

    @Test
    fun `suspect check digit with remote success penalises confidence`() {
        // Check digit is wrong but format passes
        val validation = VinValidator.validate("1HGBH41JYMN109186")
        val decoded = makeDecoded(vin = "1HGBH41JYMN109186")
        val result = VinVerificationEngine.verifyWithRemote(validation, decoded)
        // Should have a suspect flag warning
        assertTrue(result.warnings.any { it.contains("SUSPECT", ignoreCase = true) || it.contains("check digit", ignoreCase = true) })
    }

    @Test
    fun `verification status maps from confidence correctly`() {
        assertEquals(VerificationStatus.VERIFIED,       VerificationStatus.from(0.95f))
        assertEquals(VerificationStatus.MOSTLY_VERIFIED, VerificationStatus.from(0.80f))
        assertEquals(VerificationStatus.PARTIAL,        VerificationStatus.from(0.55f))
        assertEquals(VerificationStatus.SUSPECT,        VerificationStatus.from(0.30f))
        assertEquals(VerificationStatus.FAILED,         VerificationStatus.from(0.10f))
    }
}
