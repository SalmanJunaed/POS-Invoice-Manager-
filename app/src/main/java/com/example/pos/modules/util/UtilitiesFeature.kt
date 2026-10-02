package com.example.pos.modules.util

import com.example.pos.core.util.CurrencyFormatter
import com.example.pos.core.util.DateTimeUtils
import com.example.pos.core.util.IdGenerator

/**
 * Modular foundation for Utilities (Module 18).
 * Consolidates identifier generation, timestamps, and currency formatting.
 */
object UtilitiesFeature {
    val idGenerator = IdGenerator
    val dateTimeUtils = DateTimeUtils
    val currencyFormatter = CurrencyFormatter
}
