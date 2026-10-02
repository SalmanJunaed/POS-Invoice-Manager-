package com.example.pos

import com.example.pos.domain.CustomerValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerValidatorTest {

    @Test
    fun testNumericCustomerPhone() {
        val result = CustomerValidator.validate("01700000000")
        assertTrue(result.isValid)
        assertEquals("01700000000", result.sanitizedValue)
        assertNull(result.errorMessage)
    }

    @Test
    fun testAlphanumericVehiclePlate() {
        val result = CustomerValidator.validate("ABC-123")
        assertTrue(result.isValid)
        assertEquals("ABC-123", result.sanitizedValue)
        assertNull(result.errorMessage)
    }

    @Test
    fun testAlphanumericCustomerCode() {
        val result = CustomerValidator.validate("CUSTOMER-001")
        assertTrue(result.isValid)
        assertEquals("CUSTOMER-001", result.sanitizedValue)
        assertNull(result.errorMessage)
    }

    @Test
    fun testBanglaTextCustomer() {
        val result = CustomerValidator.validate("করিম আহমেদ")
        assertTrue(result.isValid)
        assertEquals("করিম আহমেদ", result.sanitizedValue)
        assertNull(result.errorMessage)
    }

    @Test
    fun testMixedAlphanumericBanglaPlate() {
        val result = CustomerValidator.validate("ঢাকা মেট্রো গ-১১-২২৩৩")
        assertTrue(result.isValid)
        assertEquals("ঢাকা মেট্রো গ-১১-২২৩৩", result.sanitizedValue)
        assertNull(result.errorMessage)
    }

    @Test
    fun testBlankOrNullInput() {
        val blankResult = CustomerValidator.validate("   ")
        assertTrue(blankResult.isValid)
        assertEquals("", blankResult.sanitizedValue)

        val nullResult = CustomerValidator.validate(null)
        assertTrue(nullResult.isValid)
        assertEquals("", nullResult.sanitizedValue)
    }

    @Test
    fun testResolveForInvoiceFallback() {
        val resolvedDefault = CustomerValidator.resolveForInvoice("")
        assertEquals("01700000000", resolvedDefault)

        val resolvedCustom = CustomerValidator.resolveForInvoice("CUSTOMER-001")
        assertEquals("CUSTOMER-001", resolvedCustom)
    }
}
