package com.example.pos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyHeaderDao {
    @Query("SELECT * FROM company_header WHERE id = 1 LIMIT 1")
    fun getCompanyHeaderFlow(): Flow<CompanyHeaderEntity?>

    @Query("SELECT * FROM company_header WHERE id = 1 LIMIT 1")
    suspend fun getCompanyHeader(): CompanyHeaderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(header: CompanyHeaderEntity)
}

@Dao
interface OperatorDao {
    @Query("SELECT * FROM operators WHERE is_active = 1 LIMIT 1")
    fun getActiveOperatorFlow(): Flow<OperatorEntity?>

    @Query("SELECT * FROM operators WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveOperator(): OperatorEntity?

    @Query("SELECT * FROM operators WHERE operator_phone = :phone LIMIT 1")
    suspend fun getOperatorByPhone(phone: String): OperatorEntity?

    @Query("SELECT * FROM operators WHERE user_id = :userId LIMIT 1")
    suspend fun getOperatorById(userId: String): OperatorEntity?

    @Query("SELECT * FROM operators ORDER BY created_at DESC")
    fun getAllOperators(): Flow<List<OperatorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperator(operator: OperatorEntity)

    @Update
    suspend fun updateOperator(operator: OperatorEntity)

    @Query("UPDATE operators SET is_active = 0")
    suspend fun clearAllActiveOperators()

    @Query("UPDATE operators SET is_active = 1, remember_me = :rememberMe, updated_at = :now WHERE user_id = :userId")
    suspend fun setActiveOperator(userId: String, rememberMe: Boolean, now: Long = System.currentTimeMillis())
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE is_active = 1 ORDER BY sort_order ASC, item_name ASC")
    fun getAllActiveProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY sort_order ASC, item_name ASC")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE item_id = :itemId LIMIT 1")
    suspend fun getProductById(itemId: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE item_id = :itemId")
    suspend fun deleteProductById(itemId: String)

    @Query("UPDATE products SET sort_order = :newOrder, updated_at = :now WHERE item_id = :itemId")
    suspend fun updateSortOrder(itemId: String, newOrder: Int, now: Long = System.currentTimeMillis())

    @androidx.room.Transaction
    suspend fun updateMultipleSortOrders(orderMap: List<Pair<String, Int>>) {
        val now = System.currentTimeMillis()
        orderMap.forEach { (id, order) ->
            updateSortOrder(id, order, now)
        }
    }

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

    @Query("SELECT COUNT(*) FROM products")
    fun getProductCountFlow(): Flow<Int>
}

data class AggregatedSalesResult(
    val totalOrders: Int,
    val totalRawPaisa: Long,
    val totalRoundedBdt: Long
)

data class SalesPeriodAggregate(
    val periodKey: String,
    val transactionCount: Int,
    val totalRawPaisa: Long,
    val totalRoundedBdt: Long
)

data class ItemSalesSummary(
    val itemName: String,
    val unit: String,
    val orderCount: Int,
    val totalRevenuePaisa: Long
)

@Dao
interface InvoiceDao {

    /**
     * Database-side paginated queries to handle up to 200,000 records smoothly without loading into memory.
     */
    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        ORDER BY date_time DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getInvoicesPagedFlow(
        query: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        ORDER BY date_time DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getInvoicesPagedDateDescFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        ORDER BY date_time ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getInvoicesPagedDateAscFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        ORDER BY rounded_total_bdt DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getInvoicesPagedAmountDescFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        ORDER BY rounded_total_bdt ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getInvoicesPagedAmountAscFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        ORDER BY order_id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getInvoicesPagedOrderIdDescFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        ORDER BY order_id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getInvoicesPagedOrderIdAscFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        """
    )
    fun getInvoiceCountFlow(query: String, startDate: Long, endDate: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        """
    )
    fun getInvoiceCountFilteredFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<Int>

    /**
     * Database-side aggregation for high-speed reporting over hundreds of thousands of rows.
     */
    @Query(
        """
        SELECT 
            COUNT(*) AS totalOrders,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        """
    )
    fun getAggregatedSalesFlow(
        query: String,
        startDate: Long,
        endDate: Long
    ): Flow<AggregatedSalesResult>

    @Query(
        """
        SELECT 
            COUNT(*) AS totalOrders,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        """
    )
    fun getAggregatedSalesFilteredFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<AggregatedSalesResult>

    @Query(
        """
        SELECT 
            l.item_name AS itemName,
            l.unit AS unit,
            COUNT(DISTINCT l.order_id) AS orderCount,
            COALESCE(SUM(l.line_total_paisa), 0) AS totalRevenuePaisa
        FROM invoice_lines l
        INNER JOIN invoices i ON l.order_id = i.order_id
        WHERE i.date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR i.order_id LIKE '%' || :query || '%' OR i.customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR i.order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR i.customer_info LIKE '%' || :customerFilter || '%')
        GROUP BY l.item_name
        ORDER BY totalRevenuePaisa DESC
        LIMIT :limit
        """
    )
    fun getTopSellingItemsFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int = 5
    ): Flow<List<ItemSalesSummary>>

    /**
     * Aggregated sales grouped by calendar day (YYYY-MM-DD) for graph analytics.
     * Evaluated entirely database-side with zero heavy in-memory dataset mapping.
     */
    @Query(
        """
        SELECT 
            strftime('%Y-%m-%d', date_time / 1000, 'unixepoch', 'localtime') AS periodKey,
            COUNT(*) AS transactionCount,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        GROUP BY periodKey
        ORDER BY periodKey ASC
        """
    )
    fun getSalesByDayFlow(
        query: String = "",
        orderIdFilter: String = "",
        customerFilter: String = "",
        startDate: Long = 0L,
        endDate: Long = Long.MAX_VALUE
    ): Flow<List<SalesPeriodAggregate>>

    /**
     * Aggregated sales grouped by month (YYYY-MM) for monthly graph analytics.
     */
    @Query(
        """
        SELECT 
            strftime('%Y-%m', date_time / 1000, 'unixepoch', 'localtime') AS periodKey,
            COUNT(*) AS transactionCount,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        GROUP BY periodKey
        ORDER BY periodKey ASC
        """
    )
    fun getSalesByMonthFlow(
        query: String = "",
        orderIdFilter: String = "",
        customerFilter: String = "",
        startDate: Long = 0L,
        endDate: Long = Long.MAX_VALUE
    ): Flow<List<SalesPeriodAggregate>>

    /**
     * Aggregated sales grouped by year (YYYY) for yearly graph analytics.
     */
    @Query(
        """
        SELECT 
            strftime('%Y', date_time / 1000, 'unixepoch', 'localtime') AS periodKey,
            COUNT(*) AS transactionCount,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        GROUP BY periodKey
        ORDER BY periodKey ASC
        """
    )
    fun getSalesByYearFlow(
        query: String = "",
        orderIdFilter: String = "",
        customerFilter: String = "",
        startDate: Long = 0L,
        endDate: Long = Long.MAX_VALUE
    ): Flow<List<SalesPeriodAggregate>>

    @Query(
        """
        SELECT 
            strftime('%Y-%m-%d', date_time / 1000, 'unixepoch', 'localtime') AS periodKey,
            COUNT(*) AS transactionCount,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        GROUP BY periodKey
        ORDER BY periodKey ASC
        """
    )
    suspend fun getSalesByDayDirect(startDate: Long, endDate: Long): List<SalesPeriodAggregate>

    @Query(
        """
        SELECT 
            strftime('%Y-%m', date_time / 1000, 'unixepoch', 'localtime') AS periodKey,
            COUNT(*) AS transactionCount,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        GROUP BY periodKey
        ORDER BY periodKey ASC
        """
    )
    suspend fun getSalesByMonthDirect(startDate: Long, endDate: Long): List<SalesPeriodAggregate>

    @Query(
        """
        SELECT 
            strftime('%Y', date_time / 1000, 'unixepoch', 'localtime') AS periodKey,
            COUNT(*) AS transactionCount,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        GROUP BY periodKey
        ORDER BY periodKey ASC
        """
    )
    suspend fun getSalesByYearDirect(startDate: Long, endDate: Long): List<SalesPeriodAggregate>

    @Query("SELECT * FROM invoices WHERE order_id = :orderId LIMIT 1")
    suspend fun getInvoiceById(orderId: String): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE global_transaction_id = :globalId LIMIT 1")
    suspend fun getInvoiceByGlobalId(globalId: String): InvoiceEntity?

    @Query("SELECT * FROM invoice_lines WHERE order_id = :orderId ORDER BY sort_order ASC, id ASC")
    fun getLinesForOrderFlow(orderId: String): Flow<List<InvoiceLineEntity>>

    @Query("SELECT * FROM invoice_lines WHERE order_id = :orderId ORDER BY sort_order ASC, id ASC")
    suspend fun getLinesForOrder(orderId: String): List<InvoiceLineEntity>

    @Query("SELECT * FROM invoice_lines WHERE order_id IN (:orderIds) ORDER BY sort_order ASC, id ASC")
    suspend fun getLinesForOrders(orderIds: List<String>): List<InvoiceLineEntity>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate 
        ORDER BY date_time DESC 
        LIMIT :limit
        """
    )
    suspend fun getInvoicesForExport(startDate: Long, endDate: Long, limit: Int = 200000): List<InvoiceEntity>

    @Query(
        """
        SELECT order_id FROM invoices 
        WHERE order_id LIKE '%' || :query || '%' 
        ORDER BY date_time DESC 
        LIMIT :limit
        """
    )
    suspend fun getMatchingOrderIds(query: String, limit: Int = 8): List<String>

    @Query("UPDATE invoices SET customer_info = :customerInfo, footer_note = :footerNote, updated_at = :updatedAt WHERE order_id = :orderId")
    suspend fun updateInvoiceCustomerAndNote(
        orderId: String,
        customerInfo: String,
        footerNote: String,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoices(invoices: List<InvoiceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceLines(lines: List<InvoiceLineEntity>)

    @Query("DELETE FROM invoice_lines WHERE order_id = :orderId")
    suspend fun deleteLinesForOrder(orderId: String)

    @Query("DELETE FROM invoices WHERE order_id = :orderId")
    suspend fun deleteInvoice(orderId: String)

    @Transaction
    suspend fun saveFullOrder(invoice: InvoiceEntity, lines: List<InvoiceLineEntity>) {
        insertInvoice(invoice)
        deleteLinesForOrder(invoice.orderId)
        insertInvoiceLines(lines)
    }

    @Transaction
    suspend fun deleteFullOrder(orderId: String) {
        deleteLinesForOrder(orderId)
        deleteInvoice(orderId)
    }

    @Query("SELECT COUNT(*) FROM invoices")
    suspend fun getTotalInvoiceCount(): Int

    @Query("SELECT COUNT(*) FROM invoices")
    fun getTotalInvoiceCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM invoice_lines")
    fun getTotalInvoiceLinesCountFlow(): Flow<Int>
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: AppSettingsEntity)

    @Query("UPDATE app_settings SET language = :lang WHERE id = 1")
    suspend fun updateLanguage(lang: String)

    @Query("UPDATE app_settings SET theme_mode = :themeMode WHERE id = 1")
    suspend fun updateThemeMode(themeMode: String)

    @Query("UPDATE app_settings SET paper_size = :paperSize WHERE id = 1")
    suspend fun updatePaperSize(paperSize: String)

    @Query("UPDATE app_settings SET auto_cutter_enabled = :enabled WHERE id = 1")
    suspend fun updateAutoCutter(enabled: Boolean)

    @Query("UPDATE app_settings SET bluetooth_printer_address = :address, bluetooth_printer_name = :name WHERE id = 1")
    suspend fun updateBluetoothPrinter(address: String?, name: String?)

    @Query("UPDATE app_settings SET footer_note_line1 = :line1, footer_note_line2 = :line2 WHERE id = 1")
    suspend fun updateFooterNotes(line1: String, line2: String)

    @Query("UPDATE app_settings SET next_order_sequence = next_order_sequence + 1 WHERE id = 1")
    suspend fun incrementOrderSequence()
}

data class DailySalesSummary(
    val dateString: String,
    val orderCount: Int,
    val totalBdt: Long
)

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payment_records WHERE order_id = :orderId")
    fun getPaymentsForOrder(orderId: String): Flow<List<PaymentRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentRecordEntity)

    @Query("SELECT payment_method, COUNT(*) as count, SUM(amount_paid_paisa) as totalPaisa FROM payment_records GROUP BY payment_method")
    fun getPaymentMethodBreakdown(): Flow<List<PaymentMethodSummary>>
}

data class PaymentMethodSummary(
    val payment_method: String,
    val count: Int,
    val totalPaisa: Long
)

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customers ORDER BY updated_at DESC")
    fun getAllCustomersFlow(): Flow<List<CustomerEntity>>

    @Query(
        """
        SELECT * FROM customers 
        WHERE customer_info LIKE '%' || :query || '%' 
           OR customer_name LIKE '%' || :query || '%' 
           OR phone_number LIKE '%' || :query || '%'
        ORDER BY updated_at DESC 
        LIMIT :limit
        """
    )
    fun searchCustomersFlow(query: String, limit: Int = 20): Flow<List<CustomerEntity>>

    @Query(
        """
        SELECT * FROM customers 
        WHERE customer_info LIKE '%' || :query || '%' 
           OR customer_name LIKE '%' || :query || '%' 
           OR phone_number LIKE '%' || :query || '%'
        ORDER BY updated_at DESC 
        LIMIT :limit
        """
    )
    suspend fun searchCustomers(query: String, limit: Int = 20): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE customer_info = :customerInfo LIMIT 1")
    suspend fun getCustomerByInfo(customerInfo: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE customer_info = :customerInfo")
    suspend fun deleteCustomer(customerInfo: String)

    @Query(
        """
        UPDATE customers 
        SET total_orders = total_orders + 1,
            total_spent_paisa = total_spent_paisa + :orderTotalPaisa,
            updated_at = :now
        WHERE customer_info = :customerInfo
        """
    )
    suspend fun recordCustomerOrder(customerInfo: String, orderTotalPaisa: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun getCustomerCount(): Int

    @Query("SELECT COUNT(*) FROM customers")
    fun getCustomerCountFlow(): Flow<Int>
}

