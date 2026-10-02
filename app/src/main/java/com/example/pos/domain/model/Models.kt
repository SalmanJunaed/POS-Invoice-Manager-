package com.example.pos.domain.model

import java.math.BigDecimal

/**
 * Pure domain models decoupled from Room annotations and UI dependencies.
 */

data class CompanyProfile(
    val id: Int = 1,
    val companyName: String,
    val branchName: String,
    val branchNumber: String,
    val address: String,
    val phoneNumber: String,
    val logoUri: String? = null
)

data class OperatorProfile(
    val userId: String,
    val name: String,
    val phone: String,
    val address: String,
    val isActive: Boolean = true
)

enum class ItemCategory {
    LIQUID,
    PRODUCT,
    SERVICE
}

data class ProductItem(
    val id: String,
    val name: String,
    val type: ItemCategory,
    val unit: String,
    val unitPricePaisa: Long,
    val sortOrder: Int = 0,
    val isActive: Boolean = true
)

data class InvoiceLine(
    val id: Long = 0L,
    val orderId: String,
    val itemId: String,
    val itemName: String,
    val itemType: String,
    val unit: String,
    val quantity: BigDecimal,
    val unitPricePaisa: Long,
    val lineTotalPaisa: Long,
    val sortOrder: Int = 0
)

data class Invoice(
    val globalId: String,
    val orderId: String, // Human-readable e.g. "INV-10294"
    val dateTime: Long,
    val operatorId: String,
    val operatorName: String,
    val customerInfo: String,
    val rawTotalPaisa: Long,
    val roundedTotalBdt: Long,
    val footerNote: String = "",
    val items: List<InvoiceLine> = emptyList(),
    val paymentMethod: PaymentMethod = PaymentMethod.CASH
)

enum class PaymentMethod {
    CASH,
    CARD,
    MFS_BKASH,
    MFS_NAGAD,
    CREDIT
}

data class PaymentRecord(
    val id: Long = 0L,
    val orderId: String,
    val paymentMethod: PaymentMethod,
    val amountPaidPaisa: Long,
    val transactionRef: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class SalesKPIs(
    val totalOrders: Int,
    val totalSalesPaisa: Long,
    val totalCollectedBdt: Long
)

data class ChartDataPoint(
    val label: String,
    val value: Long,
    val secondaryValue: Long? = null
)

data class XlsxSyncResult(
    val importedCount: Int,
    val skippedDuplicates: Int,
    val conflictCount: Int,
    val message: String
)
