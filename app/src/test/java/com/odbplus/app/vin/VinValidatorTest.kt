package com.odbplus.app.vin

import com.odbplus.app.vin.domain.VinValidationStatus
import com.odbplus.app.vin.validation.VinValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VinValidatorTest {

    // ── Normalization ─────────────────────────────────────────────────────────

    @Test
    fun `normalize trims whitespace and uppercases`() {
        assertEquals("1HGBH41JXMN109186", VinValidator.normalize("  1hgbh41jxmn109186  "))
    }

    @Test
    fun `normalize removes dashes and spaces`() {
        assertEquals("1HGBH41JXMN109186", VinValidator.normalize("1HGB-H41J-XMN1-0918-6"))
    }

    // ── Length validation ─────────────────────────────────────────────────────

    @Test
    fun `valid 17-char VIN passes length check`() {
        val result = VinValidator.validate("1HGBH41JXMN109186")
        assertTrue(result.isLengthValid)
    }

    @Test
    fun `16-char VIN fails length check`() {
        val result = VinValidator.validate("1HGBH41JXMN10918")
        assertFalse(result.isLengthValid)
        assertEquals(VinValidationStatus.INVALID, result.validationStatus)
    }

    @Test
    fun `18-char VIN fails length check`() {
        val result = VinValidator.validate("1HGBH41JXMN10918600")
        assertFalse(result.isLengthValid)
    }

    // ── Illegal character rejection ────────────────────────────────────────────

    @Test
    fun `VIN with I is rejected`() {
        val result = VinValidator.validate("1HGBH41JXMN10918I")
        assertFalse(result.hasOnlyAllowedChars)
        assertTrue(result.validationMessages.any { it.contains("I") })
    }

    @Test
    fun `VIN with O is rejected`() {
        val result = VinValidator.validate("1OGBH41JXMN109186")
        assertFalse(result.hasOnlyAllowedChars)
    }

    @Test
    fun `VIN with Q is rejected`() {
        val result = VinValidator.validate("QHGBH41JXMN109186")
        assertFalse(result.hasOnlyAllowedChars)
    }

    @Test
    fun `VIN with non-alphanumeric is rejected`() {
        val result = VinValidator.validate("1HGBH41JXMN10918!")
        assertFalse(result.hasOnlyAllowedChars)
    }

    // ── Check digit validation ────────────────────────────────────────────────

    @Test
    fun `known valid VIN passes check digit`() {
        // 1HGBH41JXMN109186 is a well-known valid test VIN
        val result = VinValidator.validate("1HGBH41JXMN109186")
        assertTrue(result.isCheckDigitValid)
        assertEquals(VinValidationStatus.VALID, result.validationStatus)
    }

    @Test
    fun `VIN with wrong check digit is SUSPECT not INVALID`() {
        // Modify check digit (position 9) from 'X' to 'Y'
        val result = VinValidator.validate("1HGBH41JYMN109186")
        assertTrue(result.isLengthValid)
        assertTrue(result.hasOnlyAllowedChars)
        assertFalse(result.isCheckDigitValid)
        assertEquals(VinValidationStatus.SUSPECT, result.validationStatus)
    }

    @Test
    fun `check digit X is computed correctly`() {
        // For 1HGBH41JXMN109186 the check digit at index 8 should be X
        val result = VinValidator.validate("1HGBH41JXMN109186")
        assertEquals('X', VinValidator.computeCheckDigit("1HGBH41JXMN109186"))
    }

    // ── Model year parsing ────────────────────────────────────────────────────

    @Test
    fun `model year A parses to 2010 in current era`() {
        // 'A' at position 10 (index 9): second cycle = 2010, within currentYear+1
        val year = VinValidator.parseModelYear('A', currentYear = 2026)
        assertEquals(2010, year)
    }

    @Test
    fun `model year T parses to 2026`() {
        val year = VinValidator.parseModelYear('T', currentYear = 2026)
        assertEquals(2026, year)
    }

    @Test
    fun `model year 9 (digit) parses to 2009`() {
        // '9' in second cycle = 2039 (future from 2026+1=2027), so falls back to 2009
        val year = VinValidator.parseModelYear('9', currentYear = 2026)
        assertEquals(2009, year)
    }

    @Test
    fun `illegal model year character returns null`() {
        // 'I' is not a valid model year code
        val year = VinValidator.parseModelYear('I', currentYear = 2026)
        assertNull(year)
    }

    // ── WMI extraction ────────────────────────────────────────────────────────

    @Test
    fun `WMI is first 3 characters`() {
        val result = VinValidator.validate("1HGBH41JXMN109186")
        assertEquals("1HG", result.wmi)
    }

    @Test
    fun `plant code is character at index 10`() {
        val result = VinValidator.validate("1HGBH41JXMN109186")
        // Index 10 = 'M'... but wait let's check: 1HGBH41JXMN109186
        // 0=1, 1=H, 2=G, 3=B, 4=H, 5=4, 6=1, 7=J, 8=X(check), 9=M(year), 10=N(plant)
        assertEquals('N', result.plantCode)
    }

    // ── Status transitions ────────────────────────────────────────────────────

    @Test
    fun `completely invalid VIN returns INVALID status`() {
        val result = VinValidator.validate("TOOSHORT")
        assertEquals(VinValidationStatus.INVALID, result.validationStatus)
        assertFalse(result.isValid)
    }

    @Test
    fun `messages list is empty for valid VIN`() {
        val result = VinValidator.validate("1HGBH41JXMN109186")
        assertTrue(result.validationMessages.isEmpty())
    }

    @Test
    fun `messages list is non-empty for invalid VIN`() {
        val result = VinValidator.validate("TOOSHORT")
        assertTrue(result.validationMessages.isNotEmpty())
    }
}
