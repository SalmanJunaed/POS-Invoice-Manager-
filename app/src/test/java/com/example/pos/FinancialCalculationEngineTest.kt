package com.example.pos

import com.example.pos.domain.FinancialCalculationEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class FinancialCalculationEngineTest {

    @Test
    fun testPrdSampleInvoiceCalculations() {
        // Octane: 10.5 L @ 130.00 BDT = 1365.00 BDT
        val octaneRatePaisa = FinancialCalculationEngine.parseBdtToPaisa("130.00")
        val octaneQty = FinancialCalculationEngine.parseQuantity("10.5")
        val octaneTotalPaisa = FinancialCalculationEngine.calculateLineTotal(octaneRatePaisa, octaneQty)
        assertEquals(136500L, octaneTotalPaisa)
        assertEquals("1365.00", FinancialCalculationEngine.formatPaisaToBdt(octaneTotalPaisa))

        // Engine Oil: 1.0 Pcs @ 500.00 BDT = 500.00 BDT
        val oilRatePaisa = FinancialCalculationEngine.parseBdtToPaisa("500.00")
        val oilQty = FinancialCalculationEngine.parseQuantity("1.0")
        val oilTotalPaisa = FinancialCalculationEngine.calculateLineTotal(oilRatePaisa, oilQty)
        assertEquals(50000L, oilTotalPaisa)
        assertEquals("500.00", FinancialCalculationEngine.formatPaisaToBdt(oilTotalPaisa))

        // LPG Gas: 25.75 L @ 95.00 BDT = 2446.25 BDT
        val lpgRatePaisa = FinancialCalculationEngine.parseBdtToPaisa("95.00")
        val lpgQty = FinancialCalculationEngine.parseQuantity("25.75")
        val lpgTotalPaisa = FinancialCalculationEngine.calculateLineTotal(lpgRatePaisa, lpgQty)
        assertEquals(244625L, lpgTotalPaisa)
        assertEquals("2446.25", FinancialCalculationEngine.formatPaisaToBdt(lpgTotalPaisa))

        // Subtotal: 1365.00 + 500.00 + 2446.25 = 4311.25 BDT (431125 Paisa)
        val rawTotalPaisa = FinancialCalculationEngine.calculateRawTotal(
            listOf(octaneTotalPaisa, oilTotalPaisa, lpgTotalPaisa)
        )
        assertEquals(431125L, rawTotalPaisa)
        assertEquals("4311.25", FinancialCalculationEngine.formatPaisaToBdt(rawTotalPaisa))

        // PRD Rounding Rule: .01 to .50 rounds down -> 4311.00 BDT
        val roundedBdt = FinancialCalculationEngine.calculateRoundedTotalBdt(rawTotalPaisa)
        assertEquals(4311L, roundedBdt)
        assertEquals("4311.00", FinancialCalculationEngine.formatWholeBdtToDisplay(roundedBdt))

        // Rounding adjustment: -0.25 BDT (-25 Paisa)
        val roundingDiff = FinancialCalculationEngine.calculateRoundingDifferencePaisa(rawTotalPaisa, roundedBdt)
        assertEquals(-25L, roundingDiff)
    }

    @Test
    fun testRoundingBoundaryRules() {
        // .00 remains unchanged
        assertEquals(50L, FinancialCalculationEngine.calculateRoundedTotalBdt(5000L)) // 50.00 -> 50

        // .01 - .50 rounds down
        assertEquals(50L, FinancialCalculationEngine.calculateRoundedTotalBdt(5001L)) // 50.01 -> 50
        assertEquals(50L, FinancialCalculationEngine.calculateRoundedTotalBdt(5050L)) // 50.50 -> 50

        // .51 - .99 rounds up
        assertEquals(51L, FinancialCalculationEngine.calculateRoundedTotalBdt(5051L)) // 50.51 -> 51
        assertEquals(51L, FinancialCalculationEngine.calculateRoundedTotalBdt(5099L)) // 50.99 -> 51
    }

    @Test
    fun testFractionalQuantityFormatting() {
        val qty1 = FinancialCalculationEngine.parseQuantity("10.50")
        assertEquals("10.5", FinancialCalculationEngine.formatQuantity(qty1))

        val qty2 = FinancialCalculationEngine.parseQuantity("25.750")
        assertEquals("25.75", FinancialCalculationEngine.formatQuantity(qty2))

        val qty3 = FinancialCalculationEngine.parseQuantity("5.000")
        assertEquals("5", FinancialCalculationEngine.formatQuantity(qty3))
    }
}
