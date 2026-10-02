package com.example.pos.modules.invoice

import com.example.pos.core.error.PosResult
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.repository.InvoiceRepository
import com.example.pos.domain.FinancialCalculationEngine
import java.math.BigDecimal

/**
 * Modular foundation for POS / Invoicing (Module 5).
 * Enforces use of [FinancialCalculationEngine] for all financial calculations.
 */
interface InvoiceFeatureFacade {
    suspend fun getNextInvoiceNumber(): String
    fun calculateLineItem(unitPricePaisa: Long, quantity: BigDecimal): Long
    fun calculateInvoiceTotals(lineTotalsPaisa: List<Long>): Pair<Long, Long> // rawPaisa, roundedBdt
    suspend fun commitInvoice(invoice: InvoiceEntity, lines: List<InvoiceLineEntity>): PosResult<Unit>
}

class InvoiceFeatureFacadeImpl(private val invoiceRepository: InvoiceRepository) : InvoiceFeatureFacade {

    override suspend fun getNextInvoiceNumber(): String = invoiceRepository.getNextOrderId()

    override fun calculateLineItem(unitPricePaisa: Long, quantity: BigDecimal): Long =
        FinancialCalculationEngine.calculateLineTotal(unitPricePaisa, quantity)

    override fun calculateInvoiceTotals(lineTotalsPaisa: List<Long>): Pair<Long, Long> {
        val rawPaisa = FinancialCalculationEngine.calculateRawTotal(lineTotalsPaisa)
        val roundedBdt = FinancialCalculationEngine.calculateRoundedTotalBdt(rawPaisa)
        return Pair(rawPaisa, roundedBdt)
    }

    override suspend fun commitInvoice(invoice: InvoiceEntity, lines: List<InvoiceLineEntity>): PosResult<Unit> =
        invoiceRepository.saveOrder(invoice, lines)
}
