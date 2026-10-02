package com.example.pos.data.repository

import com.example.pos.data.db.AggregatedSalesResult
import com.example.pos.data.db.InvoiceDao
import com.example.pos.data.db.ItemSalesSummary
import kotlinx.coroutines.flow.Flow

interface ReportRepository {
    fun getSalesReport(startDate: Long, endDate: Long): Flow<AggregatedSalesResult>
    fun getTopSellingItems(startDate: Long, endDate: Long, limit: Int = 5): Flow<List<ItemSalesSummary>>
    fun getSalesByDay(startDate: Long, endDate: Long): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>>
    fun getSalesByMonth(startDate: Long, endDate: Long): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>>
    fun getSalesByYear(startDate: Long, endDate: Long): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>>
    suspend fun getTotalTransactionVolume(): Int
}

class ReportRepositoryImpl(private val invoiceDao: InvoiceDao) : ReportRepository {

    override fun getSalesReport(startDate: Long, endDate: Long): Flow<AggregatedSalesResult> =
        invoiceDao.getAggregatedSalesFlow("", startDate, endDate)

    override fun getTopSellingItems(startDate: Long, endDate: Long, limit: Int): Flow<List<ItemSalesSummary>> =
        invoiceDao.getTopSellingItemsFlow("", "", "", startDate, endDate, limit)

    override fun getSalesByDay(startDate: Long, endDate: Long): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>> =
        invoiceDao.getSalesByDayFlow("", "", "", startDate, endDate)

    override fun getSalesByMonth(startDate: Long, endDate: Long): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>> =
        invoiceDao.getSalesByMonthFlow("", "", "", startDate, endDate)

    override fun getSalesByYear(startDate: Long, endDate: Long): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>> =
        invoiceDao.getSalesByYearFlow("", "", "", startDate, endDate)

    override suspend fun getTotalTransactionVolume(): Int = invoiceDao.getTotalInvoiceCount()
}
