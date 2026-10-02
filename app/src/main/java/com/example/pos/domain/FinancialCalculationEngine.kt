package com.example.pos.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Centralized Financial Calculation Engine for the POS & Invoice Manager.
 *
 * PRECISE DECIMAL / SCALED-VALUE STRATEGY:
 * 1. ZERO FLOATING POINT: Float and Double are strictly forbidden for financial calculations to prevent binary floating-point errors.
 * 2. MONETARY REPRESENTATION: All monetary amounts (subtotals, rates, line totals, discounts) are stored internally
 *    as scaled 64-bit signed integers (Long) representing Paisa (1 BDT = 100 Paisa).
 * 3. FRACTIONAL QUANTITY SUPPORT: Quantities (e.g., 10.5 L Octane, 25.75 L LPG Gas) are parsed and evaluated via
 *    [BigDecimal] with explicit scale control, avoiding any binary approximation.
 * 4. LINE TOTAL FORMULA: Line total (in Paisa) = (BigDecimal(unitPricePaisa) * quantity).setScale(0, RoundingMode.HALF_UP).longValueExact()
 * 5. PRD-MANDATED AUTO-ROUNDING RULE:
 *    - Decimal .01 to .50 (1 to 50 Paisa)   -> Rounds Down to lower whole integer (e.g., 50.50 BDT -> 50 BDT).
 *    - Decimal .51 to .99 (51 to 99 Paisa)  -> Rounds Up to next whole integer (e.g., 50.51 BDT -> 51 BDT).
 *    - Decimal .00 (0 Paisa)                -> Exact whole integer (e.g., 50.00 BDT -> 50 BDT).
 * 6. MATHEMATICAL CONSISTENCY: The displayed BDT amount is guaranteed to match the stored scaled value identically.
 */
object FinancialCalculationEngine {

    private const val PAISA_PER_BDT = 100L

    private val englishSymbols = DecimalFormatSymbols(Locale.US)
    private val decimal2Format = DecimalFormat("0.00", englishSymbols)

    /**
     * Converts a BDT decimal string (e.g., "130", "130.50", "95.25") into scaled Paisa (Long).
     * Never uses Double.parseDouble().
     */
    fun parseBdtToPaisa(bdtText: String): Long {
        val clean = bdtText.trim()
        if (clean.isEmpty()) return 0L
        return try {
            val bd = BigDecimal(clean)
            val paisaBd = bd.multiply(BigDecimal.valueOf(PAISA_PER_BDT))
                .setScale(0, RoundingMode.HALF_UP)
            paisaBd.longValueExact()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Parses a quantity string (e.g., "10.5", "25.75", "1") into a safe [BigDecimal].
     */
    fun parseQuantity(qtyText: String): BigDecimal {
        val clean = qtyText.trim()
        if (clean.isEmpty()) return BigDecimal.ZERO
        return try {
            BigDecimal(clean).stripTrailingZeros()
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
    }

    /**
     * Formats a BigDecimal quantity for display (e.g., "10.5", "25.75", "1").
     */
    fun formatQuantity(qty: BigDecimal): String {
        return if (qty.scale() <= 0) {
            qty.toPlainString()
        } else {
            qty.stripTrailingZeros().toPlainString()
        }
    }

    /**
     * Calculates the line total in Paisa for a given unit price (in Paisa) and fractional quantity.
     * E.g., Rate = 130.00 BDT (13000 Paisa), Qty = 10.5 L -> 1365.00 BDT (136500 Paisa).
     */
    fun calculateLineTotal(unitPricePaisa: Long, quantity: BigDecimal): Long {
        if (unitPricePaisa <= 0L || quantity <= BigDecimal.ZERO) return 0L
        val unitPriceBd = BigDecimal.valueOf(unitPricePaisa)
        val lineTotalBd = unitPriceBd.multiply(quantity)
            .setScale(0, RoundingMode.HALF_UP)
        return lineTotalBd.longValueExact()
    }

    /**
     * Calculates the raw total in Paisa by summing line totals.
     */
    fun calculateRawTotal(lineTotalsPaisa: List<Long>): Long {
        var sum = 0L
        for (total in lineTotalsPaisa) {
            sum += total
        }
        return sum
    }

    /**
     * Applies the PRD-mandated Auto-Rounding Rule:
     * - Decimal .01 to .50 (1 to 50 Paisa)   -> Rounds Down to lower whole integer (e.g., 50.50 -> 50 BDT).
     * - Decimal .51 to .99 (51 to 99 Paisa)  -> Rounds Up to next whole integer (e.g., 50.51 -> 51 BDT).
     * - Decimal .00 (0 Paisa)                -> Retains exact whole integer.
     *
     * Returns the rounded amount in whole BDT.
     */
    fun calculateRoundedTotalBdt(rawTotalPaisa: Long): Long {
        if (rawTotalPaisa <= 0L) return 0L

        val wholeBdt = rawTotalPaisa / PAISA_PER_BDT
        val paisaRemainder = rawTotalPaisa % PAISA_PER_BDT

        return when {
            paisaRemainder == 0L -> wholeBdt
            paisaRemainder in 1L..50L -> wholeBdt // Rounds down
            else -> wholeBdt + 1L // 51..99 rounds up
        }
    }

    /**
     * Alias for calculateRoundedTotalBdt for clear caller intent.
     */
    fun roundPaisaToWholeBdt(rawTotalPaisa: Long): Long = calculateRoundedTotalBdt(rawTotalPaisa)

    /**
     * Calculates the rounding difference in Paisa: (roundedTotalBdt * 100) - rawTotalPaisa.
     * Positive indicates round-up addition; negative indicates round-down reduction.
     */
    fun calculateRoundingDifferencePaisa(rawTotalPaisa: Long, roundedTotalBdt: Long): Long {
        val roundedPaisa = roundedTotalBdt * PAISA_PER_BDT
        return roundedPaisa - rawTotalPaisa
    }

    /**
     * Formats scaled Paisa to 2-decimal BDT string (e.g., 431125 Paisa -> "4311.25").
     */
    fun formatPaisaToBdt(paisa: Long): String {
        val isNegative = paisa < 0
        val absPaisa = kotlin.math.abs(paisa)
        val whole = absPaisa / PAISA_PER_BDT
        val rem = absPaisa % PAISA_PER_BDT
        val formatted = "$whole.${rem.toString().padStart(2, '0')}"
        return if (isNegative) "-$formatted" else formatted
    }

    /**
     * Formats scaled Paisa to an editable BDT string for input fields without trailing zeroes.
     * E.g., 13000 Paisa (130 BDT) -> "130"
     *       13050 Paisa (130.50 BDT) -> "130.5"
     */
    fun formatPaisaToBdtInput(paisa: Long): String {
        val isNegative = paisa < 0
        val absPaisa = kotlin.math.abs(paisa)
        val whole = absPaisa / PAISA_PER_BDT
        val rem = absPaisa % PAISA_PER_BDT
        val formatted = if (rem == 0L) {
            whole.toString()
        } else {
            val remStr = rem.toString().padStart(2, '0').trimEnd('0')
            "$whole.$remStr"
        }
        return if (isNegative) "-$formatted" else formatted
    }

    /**
     * Formats whole BDT to standard 2-decimal display (e.g., 4311 -> "4311.00").
     */
    fun formatWholeBdtToDisplay(wholeBdt: Long): String {
        return "$wholeBdt.00"
    }

    /**
     * Converts ASCII digits to Bengali digits (০১২৩৪৫৬৭৮৯) if needed.
     */
    fun toBengaliNumerals(numberString: String): String {
        val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val sb = java.lang.StringBuilder(numberString.length)
        for (ch in numberString) {
            if (ch in '0'..'9') {
                sb.append(bnDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}
