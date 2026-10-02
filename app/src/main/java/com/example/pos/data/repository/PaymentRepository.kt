package com.example.pos.data.repository

import com.example.pos.core.error.PosResult
import com.example.pos.data.db.PaymentDao
import com.example.pos.data.db.PaymentMethodSummary
import com.example.pos.data.db.PaymentRecordEntity
import kotlinx.coroutines.flow.Flow

interface PaymentRepository {
    fun getPaymentsForOrder(orderId: String): Flow<List<PaymentRecordEntity>>
    fun getPaymentMethodBreakdown(): Flow<List<PaymentMethodSummary>>
    suspend fun recordPayment(orderId: String, method: String, amountPaidPaisa: Long, ref: String?): PosResult<Unit>
}

class PaymentRepositoryImpl(private val paymentDao: PaymentDao) : PaymentRepository {

    override fun getPaymentsForOrder(orderId: String): Flow<List<PaymentRecordEntity>> =
        paymentDao.getPaymentsForOrder(orderId)

    override fun getPaymentMethodBreakdown(): Flow<List<PaymentMethodSummary>> =
        paymentDao.getPaymentMethodBreakdown()

    override suspend fun recordPayment(
        orderId: String,
        method: String,
        amountPaidPaisa: Long,
        ref: String?
    ): PosResult<Unit> {
        val record = PaymentRecordEntity(
            orderId = orderId,
            paymentMethod = method,
            amountPaidPaisa = amountPaidPaisa,
            transactionRef = ref
        )
        paymentDao.insertPayment(record)
        return PosResult.Success(Unit)
    }
}
