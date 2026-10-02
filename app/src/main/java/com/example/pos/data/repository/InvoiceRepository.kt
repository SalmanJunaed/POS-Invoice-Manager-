package com.example.pos.data.repository

import com.example.pos.core.error.PosResult
import com.example.pos.data.db.AppSettingsDao
import com.example.pos.data.db.InvoiceDao
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity

interface InvoiceRepository {
    suspend fun getNextOrderId(): String
    suspend fun saveOrder(invoice: InvoiceEntity, lines: List<InvoiceLineEntity>): PosResult<Unit>
    suspend fun getInvoice(orderId: String): InvoiceEntity?
    suspend fun getInvoiceLines(orderId: String): List<InvoiceLineEntity>
}

class InvoiceRepositoryImpl(
    private val invoiceDao: InvoiceDao,
    private val appSettingsDao: AppSettingsDao
) : InvoiceRepository {

    override suspend fun getNextOrderId(): String {
        val settings = appSettingsDao.getSettings()
        val seq = settings?.nextOrderSequence ?: 10294L
        return "INV-$seq"
    }

    override suspend fun saveOrder(invoice: InvoiceEntity, lines: List<InvoiceLineEntity>): PosResult<Unit> {
        invoiceDao.saveFullOrder(invoice, lines)
        appSettingsDao.incrementOrderSequence()
        return PosResult.Success(Unit)
    }

    override suspend fun getInvoice(orderId: String): InvoiceEntity? = invoiceDao.getInvoiceById(orderId)

    override suspend fun getInvoiceLines(orderId: String): List<InvoiceLineEntity> =
        invoiceDao.getLinesForOrder(orderId)
}
