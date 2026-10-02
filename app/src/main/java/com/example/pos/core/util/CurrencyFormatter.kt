package com.example.pos.core.util

import com.example.pos.domain.FinancialCalculationEngine

/**
 * Currency formatting utility bridging business math with UI presentation.
 */
object CurrencyFormatter {

    /**
     * Formats Paisa into standard display with BDT currency symbol (e.g., "৳ 4,311.00").
     */
    fun formatBdtWithSymbol(paisa: Long, language: String = "en"): String {
        val bdtString = FinancialCalculationEngine.formatPaisaToBdt(paisa)
        val symbol = "৳"
        val displayed = if (language == "bn") {
            FinancialCalculationEngine.toBengaliNumerals(bdtString)
        } else {
            bdtString
        }
        return "$symbol $displayed"
    }

    /**
     * Formats whole BDT with symbol.
     */
    fun formatWholeBdtWithSymbol(wholeBdt: Long, language: String = "en"): String {
        val bdtString = FinancialCalculationEngine.formatWholeBdtToDisplay(wholeBdt)
        val symbol = "৳"
        val displayed = if (language == "bn") {
            FinancialCalculationEngine.toBengaliNumerals(bdtString)
        } else {
            bdtString
        }
        return "$symbol $displayed"
    }
}
