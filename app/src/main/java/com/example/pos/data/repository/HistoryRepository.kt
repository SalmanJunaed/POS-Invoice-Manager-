package com.example.pos.data.repository

import com.example.pos.data.db.AggregatedSalesResult
import com.example.pos.data.db.InvoiceDao
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.ItemSalesSummary
import kotlinx.coroutines.flow.Flow

enum class HistorySortOption {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC,
    ORDER_ID_DESC,
    ORDER_ID_ASC
}

interface HistoryRepository {
    fun getInvoicesPaged(
        query: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    fun getInvoicesPaged(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        sortOption: HistorySortOption,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    fun getInvoiceCount(query: String, startDate: Long, endDate: Long): Flow<Int>

    fun getInvoiceCount(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<Int>

    fun getAggregatedSales(
        query: String,
        startDate: Long,
        endDate: Long
    ): Flow<AggregatedSalesResult>

    fun getAggregatedSales(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<AggregatedSalesResult>

    fun getTopSellingItems(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int = 5
    ): Flow<List<ItemSalesSummary>>

    fun getSalesByDay(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>>

    fun getSalesByMonth(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>>

    fun getSalesByYear(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>>

    fun getLinesForOrderFlow(orderId: String): Flow<List<InvoiceLineEntity>>

    suspend fun getLinesForOrders(orderIds: List<String>): List<InvoiceLineEntity>

    suspend fun getInvoicesForExport(startDate: Long, endDate: Long, limit: Int = 200000): List<InvoiceEntity>

    suspend fun getMatchingOrderIds(query: String, limit: Int = 8): List<String>

    suspend fun updateInvoiceCustomerAndNote(orderId: String, customerInfo: String, footerNote: String): Int

    suspend fun deleteOrder(orderId: String)

    fun getTotalInvoiceCountFlow(): Flow<Int>

    fun getTotalInvoiceLinesCountFlow(): Flow<Int>
}

class HistoryRepositoryImpl(private val invoiceDao: InvoiceDao) : HistoryRepository {

    override fun getInvoicesPaged(
        query: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>> = invoiceDao.getInvoicesPagedFlow(query, startDate, endDate, limit, offset)

    override fun getInvoicesPaged(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        sortOption: HistorySortOption,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>> {
        val q = query.trim()
        val o = orderIdFilter.trim()
        val c = customerFilter.trim()
        return when (sortOption) {
            HistorySortOption.DATE_DESC -> invoiceDao.getInvoicesPagedDateDescFlow(q, o, c, startDate, endDate, limit, offset)
            HistorySortOption.DATE_ASC -> invoiceDao.getInvoicesPagedDateAscFlow(q, o, c, startDate, endDate, limit, offset)
            HistorySortOption.AMOUNT_DESC -> invoiceDao.getInvoicesPagedAmountDescFlow(q, o, c, startDate, endDate, limit, offset)
            HistorySortOption.AMOUNT_ASC -> invoiceDao.getInvoicesPagedAmountAscFlow(q, o, c, startDate, endDate, limit, offset)
            HistorySortOption.ORDER_ID_DESC -> invoiceDao.getInvoicesPagedOrderIdDescFlow(q, o, c, startDate, endDate, limit, offset)
            HistorySortOption.ORDER_ID_ASC -> invoiceDao.getInvoicesPagedOrderIdAscFlow(q, o, c, startDate, endDate, limit, offset)
        }
    }

    override fun getInvoiceCount(query: String, startDate: Long, endDate: Long): Flow<Int> =
        invoiceDao.getInvoiceCountFlow(query, startDate, endDate)

    override fun getInvoiceCount(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<Int> = invoiceDao.getInvoiceCountFilteredFlow(query.trim(), orderIdFilter.trim(), customerFilter.trim(), startDate, endDate)

    override fun getAggregatedSales(
        query: String,
        startDate: Long,
        endDate: Long
    ): Flow<AggregatedSalesResult> = invoiceDao.getAggregatedSalesFlow(query, startDate, endDate)

    override fun getAggregatedSales(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<AggregatedSalesResult> =
        invoiceDao.getAggregatedSalesFilteredFlow(query.trim(), orderIdFilter.trim(), customerFilter.trim(), startDate, endDate)

    override fun getTopSellingItems(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int
    ): Flow<List<ItemSalesSummary>> =
        invoiceDao.getTopSellingItemsFlow(query.trim(), orderIdFilter.trim(), customerFilter.trim(), startDate, endDate, limit)

    override fun getSalesByDay(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>> =
        invoiceDao.getSalesByDayFlow(query.trim(), orderIdFilter.trim(), customerFilter.trim(), startDate, endDate)

    override fun getSalesByMonth(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>> =
        invoiceDao.getSalesByMonthFlow(query.trim(), orderIdFilter.trim(), customerFilter.trim(), startDate, endDate)

    override fun getSalesByYear(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>> =
        invoiceDao.getSalesByYearFlow(query.trim(), orderIdFilter.trim(), customerFilter.trim(), startDate, endDate)

    override fun getLinesForOrderFlow(orderId: String): Flow<List<InvoiceLineEntity>> =
        invoiceDao.getLinesForOrderFlow(orderId)

    override suspend fun getLinesForOrders(orderIds: List<String>): List<InvoiceLineEntity> =
        if (orderIds.isEmpty()) emptyList() else invoiceDao.getLinesForOrders(orderIds)

    override suspend fun getInvoicesForExport(startDate: Long, endDate: Long, limit: Int): List<InvoiceEntity> =
        invoiceDao.getInvoicesForExport(startDate, endDate, limit)

    override suspend fun getMatchingOrderIds(query: String, limit: Int): List<String> =
        invoiceDao.getMatchingOrderIds(query, limit)

    override suspend fun updateInvoiceCustomerAndNote(orderId: String, customerInfo: String, footerNote: String): Int =
        invoiceDao.updateInvoiceCustomerAndNote(orderId, customerInfo, footerNote)

    override suspend fun deleteOrder(orderId: String) {
        invoiceDao.deleteFullOrder(orderId)
    }

    override fun getTotalInvoiceCountFlow(): Flow<Int> =
        invoiceDao.getTotalInvoiceCountFlow()

    override fun getTotalInvoiceLinesCountFlow(): Flow<Int> =
        invoiceDao.getTotalInvoiceLinesCountFlow()
}
