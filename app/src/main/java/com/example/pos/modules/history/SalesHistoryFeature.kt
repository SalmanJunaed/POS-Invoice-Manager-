package com.example.pos.modules.history

import com.example.pos.data.db.AggregatedSalesResult
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow

/**
 * Modular foundation for Sales History & Order Management (Module 6).
 */
interface SalesHistoryFacade {
    fun getInvoicesPaged(query: String, start: Long, end: Long, limit: Int, offset: Int): Flow<List<InvoiceEntity>>
    fun getSalesSummary(query: String, start: Long, end: Long): Flow<AggregatedSalesResult>
    fun getLinesForOrder(orderId: String): Flow<List<InvoiceLineEntity>>
    suspend fun deleteOrder(orderId: String)
}

class SalesHistoryFacadeImpl(private val historyRepository: HistoryRepository) : SalesHistoryFacade {
    override fun getInvoicesPaged(query: String, start: Long, end: Long, limit: Int, offset: Int) =
        historyRepository.getInvoicesPaged(query, start, end, limit, offset)

    override fun getSalesSummary(query: String, start: Long, end: Long) =
        historyRepository.getAggregatedSales(query, start, end)

    override fun getLinesForOrder(orderId: String) =
        historyRepository.getLinesForOrderFlow(orderId)

    override suspend fun deleteOrder(orderId: String) =
        historyRepository.deleteOrder(orderId)
}
