package com.example.pos.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "company_header",
    indices = [
        Index(value = ["branch_number"])
    ]
)
data class CompanyHeaderEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "company_name") val companyName: String,
    @ColumnInfo(name = "branch_name") val branchName: String,
    @ColumnInfo(name = "branch_number") val branchNumber: String,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "logo_uri") val logoUri: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "operators",
    indices = [
        Index(value = ["operator_phone"], unique = true),
        Index(value = ["is_active"])
    ]
)
data class OperatorEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "operator_name") val operatorName: String,
    @ColumnInfo(name = "operator_phone") val operatorPhone: String,
    @ColumnInfo(name = "operator_address") val operatorAddress: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false,
    @ColumnInfo(name = "remember_me") val rememberMe: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["item_name"]),
        Index(value = ["name_bn"]),
        Index(value = ["name_en"]),
        Index(value = ["item_type"]),
        Index(value = ["sort_order"]),
        Index(value = ["is_active"])
    ]
)
data class ProductEntity(
    @PrimaryKey
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "item_name") val itemName: String,
    @ColumnInfo(name = "name_bn", defaultValue = "") val nameBn: String = "",
    @ColumnInfo(name = "name_en", defaultValue = "") val nameEn: String = "",
    @ColumnInfo(name = "item_type") val itemType: String, // "Liquid", "Product", "Service"
    @ColumnInfo(name = "unit") val unit: String, // "Liter", "Quantity", "Pcs", "Kg", etc.
    @ColumnInfo(name = "default_unit_price_paisa") val defaultUnitPricePaisa: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Returns the localized display name according to the active language ("bn" or "en").
     * Falls back seamlessly to the opposite localized name or legacy itemName if blank.
     */
    fun getDisplayName(lang: String): String {
        return if (lang.equals("bn", ignoreCase = true)) {
            when {
                nameBn.isNotBlank() -> nameBn
                itemName.isNotBlank() -> itemName
                else -> nameEn
            }
        } else {
            when {
                nameEn.isNotBlank() -> nameEn
                itemName.isNotBlank() -> itemName
                else -> nameBn
            }
        }
    }
}

/**
 * Invoices entity optimized for high-volume transactions (up to 200,000 records).
 *
 * Transaction Identity Design:
 * - global_transaction_id: Universally unique, immutable internal identifier (UUID) for multi-branch sync.
 * - order_id: Human-readable display invoice number (e.g., "INV-10293") shown on receipts.
 * - branch_code: Identity of originating branch for distributed sync and audit.
 * - Indexed by date_time, order_id, global_transaction_id, operator_id, and customer_info for sub-50ms database-side query execution.
 */
@Entity(
    tableName = "invoices",
    indices = [
        Index(value = ["date_time"]),
        Index(value = ["global_transaction_id"], unique = true),
        Index(value = ["operator_id"]),
        Index(value = ["customer_info"]),
        Index(value = ["rounded_total_bdt"]),
        Index(value = ["raw_total_paisa"])
    ]
)
data class InvoiceEntity(
    @PrimaryKey
    @ColumnInfo(name = "order_id") val orderId: String, // Human-readable e.g. "INV-10293"
    @ColumnInfo(name = "global_transaction_id") val globalTransactionId: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "branch_code") val branchCode: String = "01",
    @ColumnInfo(name = "date_time") val dateTime: Long, // timestamp
    @ColumnInfo(name = "operator_id") val operatorId: String,
    @ColumnInfo(name = "operator_name") val operatorName: String,
    @ColumnInfo(name = "customer_info") val customerInfo: String,
    @ColumnInfo(name = "raw_total_paisa") val rawTotalPaisa: Long,
    @ColumnInfo(name = "rounded_total_bdt") val roundedTotalBdt: Long,
    @ColumnInfo(name = "footer_note") val footerNote: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Historical invoice items preserve the exact item name, unit, fractional quantity,
 * unit price (paisa), and line total (paisa) at the time of sale.
 *
 * Modifying or deleting products from the Product catalog NEVER alters historical invoice lines.
 */
@Entity(
    tableName = "invoice_lines",
    indices = [
        Index(value = ["order_id"]),
        Index(value = ["item_id"])
    ],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["order_id"],
            childColumns = ["order_id"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class InvoiceLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "order_id") val orderId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "item_name") val itemName: String, // Preserved snapshot at time of sale
    @ColumnInfo(name = "item_type") val itemType: String,
    @ColumnInfo(name = "unit") val unit: String,
    @ColumnInfo(name = "quantity_str") val quantityStr: String, // Fractional representation e.g. "10.5", "25.75"
    @ColumnInfo(name = "unit_price_paisa") val unitPricePaisa: Long, // Preserved unit price at sale
    @ColumnInfo(name = "line_total_paisa") val lineTotalPaisa: Long, // Preserved line total at sale
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "language") val language: String = "bn", // "bn" or "en"
    @ColumnInfo(name = "theme_mode") val themeMode: String = "LIGHT", // "LIGHT", "DARK", "SYSTEM"
    @ColumnInfo(name = "paper_size") val paperSize: String = "58mm", // "58mm", "80mm"
    @ColumnInfo(name = "auto_cutter_enabled") val autoCutterEnabled: Boolean = true,
    @ColumnInfo(name = "bluetooth_printer_address") val bluetoothPrinterAddress: String? = null,
    @ColumnInfo(name = "bluetooth_printer_name") val bluetoothPrinterName: String? = null,
    @ColumnInfo(name = "footer_note_line1") val footerNoteLine1: String = "*** নিখুঁত মানের নিশ্চয়তা ***",
    @ColumnInfo(name = "footer_note_line2") val footerNoteLine2: String = "ধন্যবাদ আবার আসবেন",
    @ColumnInfo(name = "next_order_sequence") val nextOrderSequence: Long = 10294L,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payment_records",
    indices = [
        Index(value = ["order_id"]),
        Index(value = ["payment_method"]),
        Index(value = ["timestamp"])
    ],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["order_id"],
            childColumns = ["order_id"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class PaymentRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "order_id") val orderId: String,
    @ColumnInfo(name = "payment_method") val paymentMethod: String = "CASH",
    @ColumnInfo(name = "amount_paid_paisa") val amountPaidPaisa: Long,
    @ColumnInfo(name = "transaction_ref") val transactionRef: String? = null,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

data class SalesSummaryDto(
    val totalOrders: Int,
    val totalRawPaisa: Long,
    val totalRoundedBdt: Long
)

/**
 * Reusable Customer entity supporting alphanumeric customer_info values
 * (Numbers, English letters, Bangla text, and mixed alphanumeric values like
 * "01700000000", "ABC-123", "CUSTOMER-001", "ঢাকা মেট্রো গ-১১-২২৩৩").
 *
 * Stores created_at and updated_at for audit and timestamp tracking.
 */
@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["customer_info"], unique = true),
        Index(value = ["customer_name"]),
        Index(value = ["phone_number"]),
        Index(value = ["updated_at"])
    ]
)
data class CustomerEntity(
    @PrimaryKey
    @ColumnInfo(name = "customer_info") val customerInfo: String, // Alphanumeric primary identifier e.g. "01700000000", "ABC-123", "CUSTOMER-001"
    @ColumnInfo(name = "customer_name") val customerName: String = "", // Optional display name (Bangla or English)
    @ColumnInfo(name = "phone_number") val phoneNumber: String = "", // Optional secondary contact number
    @ColumnInfo(name = "address") val address: String = "", // Optional customer location/address
    @ColumnInfo(name = "notes") val notes: String = "", // Optional notes (e.g. vehicle type, account notes)
    @ColumnInfo(name = "total_orders") val totalOrders: Int = 0, // Order count summary
    @ColumnInfo(name = "total_spent_paisa") val totalSpentPaisa: Long = 0L, // Historical spent paisa
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
