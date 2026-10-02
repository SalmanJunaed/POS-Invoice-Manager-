package com.example.pos.data.repository

import android.content.Context
import com.example.pos.data.db.AggregatedSalesResult
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.CustomerEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.OperatorEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.excel.ExcelExchangeEngine
import com.example.pos.excel.ImportConflict
import com.example.pos.excel.ImportSummary
import com.example.pos.excel.ParseXlsxResult
import com.example.pos.excel.ParsedTransaction
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class PosRepository(
    context: Context,
    private val db: PosDatabase = PosDatabase.getInstance(context)
) {
    private val appContext = context.applicationContext
    private val sessionManager = com.example.pos.core.security.SecureSessionManager.getInstance(appContext)

    private val companyDao = db.companyHeaderDao()
    private val operatorDao = db.operatorDao()
    private val productDao = db.productDao()
    private val invoiceDao = db.invoiceDao()
    private val settingsDao = db.appSettingsDao()
    private val customerDao = db.customerDao()

    val auth: AuthRepository by lazy { AuthRepositoryImpl(operatorDao) }
    val company: CompanyRepository by lazy { CompanyRepositoryImpl(companyDao) }
    val products: ProductRepository by lazy { ProductRepositoryImpl(productDao) }
    val invoices: InvoiceRepository by lazy { InvoiceRepositoryImpl(invoiceDao, settingsDao) }
    val history: HistoryRepository by lazy { HistoryRepositoryImpl(invoiceDao) }
    val payments: PaymentRepository by lazy { PaymentRepositoryImpl(db.paymentDao()) }
    val reports: ReportRepository by lazy { ReportRepositoryImpl(invoiceDao) }
    val settings: SettingsRepository by lazy { SettingsRepositoryImpl(settingsDao) }

    val companyHeaderFlow: Flow<CompanyHeaderEntity?> = companyDao.getCompanyHeaderFlow()
    val activeOperatorFlow: Flow<OperatorEntity?> = operatorDao.getActiveOperatorFlow()
    val allActiveProductsFlow: Flow<List<ProductEntity>> = productDao.getAllActiveProductsFlow()
    val allProductsFlow: Flow<List<ProductEntity>> = productDao.getAllProductsFlow()
    val appSettingsFlow: Flow<AppSettingsEntity?> = settingsDao.getSettingsFlow()

    suspend fun getActiveOperator(): OperatorEntity? = withContext(Dispatchers.IO) {
        operatorDao.getActiveOperator()
    }

    suspend fun checkAndRestoreSession(): OperatorEntity? = withContext(Dispatchers.IO) {
        // First check if an operator is marked active in the database
        val activeInDb = operatorDao.getActiveOperator()
        if (activeInDb != null) {
            if (activeInDb.rememberMe) {
                sessionManager.saveLoginSession(activeInDb.userId, activeInDb.operatorPhone, true)
                return@withContext activeInDb
            } else {
                // If rememberMe was false, session should not persist across fresh app launches
                operatorDao.clearAllActiveOperators()
                sessionManager.clearSession(clearRememberedPhone = false)
                return@withContext null
            }
        }

        // If not active in DB, check secure local session storage
        if (sessionManager.isRememberMeEnabled()) {
            val rememberedUserId = sessionManager.getActiveUserId()
            if (!rememberedUserId.isNullOrBlank()) {
                val op = operatorDao.getOperatorById(rememberedUserId)
                if (op != null) {
                    operatorDao.clearAllActiveOperators()
                    operatorDao.setActiveOperator(op.userId, true)
                    return@withContext op.copy(isActive = true, rememberMe = true)
                }
            }
        }
        null
    }

    fun getRememberedPhone(): String {
        return sessionManager.getSavedPhone()
    }

    fun isRememberMeSaved(): Boolean {
        return sessionManager.isRememberMeEnabled()
    }

    suspend fun getCompanyHeader(): CompanyHeaderEntity? = withContext(Dispatchers.IO) {
        companyDao.getCompanyHeader()
    }

    suspend fun updateCompanyHeader(header: CompanyHeaderEntity) = withContext(Dispatchers.IO) {
        companyDao.insertOrUpdate(header.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun login(phone: String, passwordRaw: String, rememberMe: Boolean): Result<OperatorEntity> =
        withContext(Dispatchers.IO) {
            val cleanPhone = phone.trim()
            val op = operatorDao.getOperatorByPhone(cleanPhone)
                ?: return@withContext Result.failure(Exception("Operator not found with phone: $phone"))

            val isValid = com.example.pos.core.security.PasswordSecurity.verifyPassword(passwordRaw, op.passwordHash)
            if (!isValid) {
                return@withContext Result.failure(Exception("Incorrect password"))
            }

            // If stored password was legacy hash, upgrade securely in background to salted PBKDF2
            if (!op.passwordHash.startsWith("pbkdf2_sha256$")) {
                val newSecureHash = com.example.pos.core.security.PasswordSecurity.hashPassword(passwordRaw)
                operatorDao.updateOperator(op.copy(passwordHash = newSecureHash, updatedAt = System.currentTimeMillis()))
            }

            // Only one active operator on device at a time
            operatorDao.clearAllActiveOperators()
            operatorDao.setActiveOperator(op.userId, rememberMe)

            // Save encrypted session secrets
            sessionManager.saveLoginSession(op.userId, cleanPhone, rememberMe)

            Result.success(op.copy(isActive = true, rememberMe = rememberMe))
        }

    suspend fun registerOperator(
        name: String,
        phone: String,
        address: String,
        passwordRaw: String
    ): Result<OperatorEntity> = withContext(Dispatchers.IO) {
        val cleanPhone = phone.trim()
        val existing = operatorDao.getOperatorByPhone(cleanPhone)
        if (existing != null) {
            return@withContext Result.failure(Exception("Operator with phone $cleanPhone already exists"))
        }
        val secureHash = com.example.pos.core.security.PasswordSecurity.hashPassword(passwordRaw)
        val newOp = OperatorEntity(
            userId = "OP-" + UUID.randomUUID().toString().take(8).uppercase(),
            operatorName = name.trim(),
            operatorPhone = cleanPhone,
            operatorAddress = address.trim(),
            passwordHash = secureHash,
            isActive = true,
            rememberMe = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        // Ensure only one operator is active on device
        operatorDao.clearAllActiveOperators()
        operatorDao.insertOperator(newOp)

        // Save session in secure local storage
        sessionManager.saveLoginSession(newOp.userId, cleanPhone, true)

        Result.success(newOp)
    }

    suspend fun resetPassword(phone: String, newPasswordRaw: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val cleanPhone = phone.trim()
            val op = operatorDao.getOperatorByPhone(cleanPhone)
                ?: return@withContext Result.failure(Exception("No operator registered with phone $phone"))
            val secureHash = com.example.pos.core.security.PasswordSecurity.hashPassword(newPasswordRaw)
            val updated = op.copy(
                passwordHash = secureHash,
                updatedAt = System.currentTimeMillis()
            )
            operatorDao.updateOperator(updated)
            Result.success(Unit)
        }

    suspend fun logout() = withContext(Dispatchers.IO) {
        operatorDao.clearAllActiveOperators()
        // Clear active session token while keeping remembered phone if remember-me was set
        val keepRememberedPhone = sessionManager.isRememberMeEnabled()
        sessionManager.clearSession(clearRememberedPhone = !keepRememberedPhone)
    }

    suspend fun updateOperatorProfile(name: String, phone: String, address: String) =
        withContext(Dispatchers.IO) {
            val current = operatorDao.getActiveOperator() ?: return@withContext
            val updated = current.copy(
                operatorName = name.trim(),
                operatorPhone = phone.trim(),
                operatorAddress = address.trim(),
                updatedAt = System.currentTimeMillis()
            )
            operatorDao.updateOperator(updated)
        }

    // Product CRUD
    suspend fun saveProduct(product: ProductEntity) = withContext(Dispatchers.IO) {
        productDao.insertProduct(product.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProduct(itemId: String) = withContext(Dispatchers.IO) {
        productDao.deleteProductById(itemId)
    }

    suspend fun updateProductSortOrder(itemId: String, newOrder: Int) = withContext(Dispatchers.IO) {
        productDao.updateSortOrder(itemId, newOrder)
    }

    suspend fun updateProductReorder(orderedItemIds: List<String>) = withContext(Dispatchers.IO) {
        val pairs = orderedItemIds.mapIndexed { index, id -> Pair(id, index + 1) }
        productDao.updateMultipleSortOrders(pairs)
    }

    // Invoice & Transaction operations
    fun getInvoicesPaged(
        query: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>> {
        return invoiceDao.getInvoicesPagedFlow(query.trim(), startDate, endDate, limit, offset)
    }

    fun getInvoicesPaged(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        sortOption: HistorySortOption,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>> {
        return history.getInvoicesPaged(query, orderIdFilter, customerFilter, startDate, endDate, sortOption, limit, offset)
    }

    fun getInvoiceCount(query: String, startDate: Long, endDate: Long): Flow<Int> {
        return invoiceDao.getInvoiceCountFlow(query.trim(), startDate, endDate)
    }

    fun getInvoiceCount(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<Int> {
        return history.getInvoiceCount(query, orderIdFilter, customerFilter, startDate, endDate)
    }

    fun getAggregatedSales(query: String, startDate: Long, endDate: Long): Flow<AggregatedSalesResult> {
        return invoiceDao.getAggregatedSalesFlow(query.trim(), startDate, endDate)
    }

    fun getAggregatedSales(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<AggregatedSalesResult> {
        return history.getAggregatedSales(query, orderIdFilter, customerFilter, startDate, endDate)
    }

    fun getTopSellingItems(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int = 5
    ): Flow<List<com.example.pos.data.db.ItemSalesSummary>> {
        return history.getTopSellingItems(query, orderIdFilter, customerFilter, startDate, endDate, limit)
    }

    fun getSalesByDay(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>> {
        return history.getSalesByDay(query, orderIdFilter, customerFilter, startDate, endDate)
    }

    fun getSalesByMonth(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>> {
        return history.getSalesByMonth(query, orderIdFilter, customerFilter, startDate, endDate)
    }

    fun getSalesByYear(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>> {
        return history.getSalesByYear(query, orderIdFilter, customerFilter, startDate, endDate)
    }

    fun getLinesForOrder(orderId: String): Flow<List<InvoiceLineEntity>> {
        return invoiceDao.getLinesForOrderFlow(orderId)
    }

    suspend fun getLinesForOrderDirect(orderId: String): List<InvoiceLineEntity> =
        withContext(Dispatchers.IO) {
            invoiceDao.getLinesForOrder(orderId)
        }

    suspend fun getLinesForOrders(orderIds: List<String>): List<InvoiceLineEntity> =
        withContext(Dispatchers.IO) {
            history.getLinesForOrders(orderIds)
        }

    suspend fun getInvoicesForExport(startDate: Long, endDate: Long): List<InvoiceEntity> =
        withContext(Dispatchers.IO) {
            invoiceDao.getInvoicesForExport(startDate, endDate)
        }

    suspend fun getMatchingOrderIds(query: String, limit: Int = 8): List<String> =
        withContext(Dispatchers.IO) {
            history.getMatchingOrderIds(query, limit)
        }

    suspend fun updateInvoiceCustomerAndNote(orderId: String, customerInfo: String, footerNote: String): Int =
        withContext(Dispatchers.IO) {
            history.updateInvoiceCustomerAndNote(orderId, customerInfo, footerNote)
        }

    suspend fun saveOrder(invoice: InvoiceEntity, lines: List<InvoiceLineEntity>) =
        withContext(Dispatchers.IO) {
            invoiceDao.saveFullOrder(invoice, lines)
            settingsDao.incrementOrderSequence()
        }

    suspend fun getNextOrderId(): String = withContext(Dispatchers.IO) {
        val settings = settingsDao.getSettings()
        val seq = settings?.nextOrderSequence ?: 10294L
        "INV-$seq"
    }

    suspend fun importInvoices(invoices: List<InvoiceEntity>, lines: List<InvoiceLineEntity>) =
        withContext(Dispatchers.IO) {
            for (inv in invoices) {
                val matchingLines = lines.filter { it.orderId == inv.orderId }
                invoiceDao.saveFullOrder(inv, matchingLines)
            }
        }

    suspend fun importAndMergeTransactions(parsed: ParseXlsxResult): ImportSummary = withContext(Dispatchers.IO) {
        var importedCount = 0
        var duplicateSkippedCount = 0
        var conflictCount = 0
        var failedCount = 0
        val invalidCount = parsed.invalidRows
        val conflicts = mutableListOf<ImportConflict>()
        val validationErrors = parsed.validationErrors.toMutableList()

        val toInsert = mutableListOf<ParsedTransaction>()
        val processedGlobalIds = mutableSetOf<String>()
        val processedOrderIds = mutableSetOf<String>()

        for (candidate in parsed.transactions) {
            val inv = candidate.invoice

            // 1. Check duplicate within file
            if (processedGlobalIds.contains(inv.globalTransactionId) || processedOrderIds.contains(inv.orderId)) {
                duplicateSkippedCount++
                continue
            }

            // 2. Query local database by Global ID first, then by Order ID
            val existing = (if (inv.globalTransactionId.isNotBlank()) invoiceDao.getInvoiceByGlobalId(inv.globalTransactionId) else null)
                ?: invoiceDao.getInvoiceById(inv.orderId)

            if (existing == null) {
                // Brand new transaction!
                toInsert.add(candidate)
                processedGlobalIds.add(inv.globalTransactionId)
                processedOrderIds.add(inv.orderId)
            } else {
                // Existing record found: check if identical vs conflict
                val isIdentical = (existing.rawTotalPaisa == inv.rawTotalPaisa) &&
                        (existing.roundedTotalBdt == inv.roundedTotalBdt) &&
                        (existing.customerInfo.trim() == inv.customerInfo.trim()) &&
                        (existing.operatorName.trim() == inv.operatorName.trim() || inv.operatorName.isBlank())

                if (isIdentical) {
                    // Duplicate record: preserve local record unchanged and skip insert
                    duplicateSkippedCount++
                    processedGlobalIds.add(inv.globalTransactionId)
                    processedOrderIds.add(inv.orderId)
                } else {
                    // CONFLICT DETECTED: Data differs. Do NOT overwrite local record!
                    conflictCount++
                    val reason = when {
                        existing.rawTotalPaisa != inv.rawTotalPaisa ->
                            "Amount mismatch: Local ৳${FinancialCalculationEngine.formatPaisaToBdt(existing.rawTotalPaisa)} vs Imported ৳${FinancialCalculationEngine.formatPaisaToBdt(inv.rawTotalPaisa)}"
                        existing.customerInfo.trim() != inv.customerInfo.trim() ->
                            "Customer mismatch: Local '${existing.customerInfo}' vs Imported '${inv.customerInfo}'"
                        else ->
                            "Transaction data mismatch for Order ID ${existing.orderId}"
                    }
                    conflicts.add(
                        ImportConflict(
                            globalTransactionId = inv.globalTransactionId,
                            orderId = inv.orderId,
                            localSummary = "${existing.orderId} | ৳${FinancialCalculationEngine.formatPaisaToBdt(existing.rawTotalPaisa)} | ${existing.customerInfo}",
                            importedSummary = "${inv.orderId} | ৳${FinancialCalculationEngine.formatPaisaToBdt(inv.rawTotalPaisa)} | ${inv.customerInfo}",
                            reason = reason
                        )
                    )
                    processedGlobalIds.add(inv.globalTransactionId)
                    processedOrderIds.add(inv.orderId)
                }
            }
        }

        // 3. Perform atomic Room database transaction for all valid new records
        if (toInsert.isNotEmpty()) {
            try {
                db.withTransaction {
                    for (item in toInsert) {
                        invoiceDao.insertInvoice(item.invoice)
                        invoiceDao.deleteLinesForOrder(item.invoice.orderId)
                        if (item.lines.isNotEmpty()) {
                            invoiceDao.insertInvoiceLines(item.lines)
                        }
                        // CRM update
                        val custInfo = item.invoice.customerInfo.trim()
                        if (custInfo.isNotBlank() && custInfo != "External Customer") {
                            val existingCust = customerDao.getCustomerByInfo(custInfo)
                            val now = System.currentTimeMillis()
                            if (existingCust != null) {
                                customerDao.recordCustomerOrder(custInfo, item.invoice.rawTotalPaisa, now)
                            } else {
                                customerDao.insertOrUpdateCustomer(
                                    CustomerEntity(
                                        customerInfo = custInfo,
                                        phoneNumber = if (custInfo.all { it.isDigit() || it in "+- " }) custInfo else "",
                                        totalOrders = 1,
                                        totalSpentPaisa = item.invoice.rawTotalPaisa,
                                        createdAt = item.invoice.createdAt,
                                        updatedAt = now
                                    )
                                )
                            }
                        }
                    }
                }
                importedCount = toInsert.size
            } catch (e: Exception) {
                failedCount = toInsert.size
                validationErrors.add("Database transaction error: ${e.message}")
            }
        }

        ImportSummary(
            totalRows = parsed.totalRowsParsed,
            importedCount = importedCount,
            duplicateSkippedCount = duplicateSkippedCount,
            invalidCount = invalidCount,
            conflictCount = conflictCount,
            failedCount = failedCount,
            conflicts = conflicts,
            validationErrors = validationErrors
        )
    }

    suspend fun importSalesFromUri(context: Context, uri: android.net.Uri): Result<ImportSummary> = withContext(Dispatchers.IO) {
        val parseResult = ExcelExchangeEngine.parseXlsxFromUri(context, uri)
        if (parseResult.isFailure) {
            return@withContext Result.failure(parseResult.exceptionOrNull() ?: Exception("Import failed."))
        }
        val summary = importAndMergeTransactions(parseResult.getOrThrow())
        Result.success(summary)
    }

    suspend fun importSalesFromStream(stream: java.io.InputStream): Result<ImportSummary> = withContext(Dispatchers.IO) {
        val parseResult = ExcelExchangeEngine.parseXlsx(stream)
        if (parseResult.isFailure) {
            return@withContext Result.failure(parseResult.exceptionOrNull() ?: Exception("Import failed."))
        }
        val summary = importAndMergeTransactions(parseResult.getOrThrow())
        Result.success(summary)
    }

    // Settings
    suspend fun updateLanguage(lang: String) = withContext(Dispatchers.IO) {
        settingsDao.updateLanguage(lang)
    }

    suspend fun updateThemeMode(themeMode: String) = withContext(Dispatchers.IO) {
        settingsDao.updateThemeMode(themeMode)
    }

    suspend fun updatePaperSize(paperSize: String) = withContext(Dispatchers.IO) {
        settingsDao.updatePaperSize(paperSize)
    }

    suspend fun updateAutoCutter(enabled: Boolean) = withContext(Dispatchers.IO) {
        settingsDao.updateAutoCutter(enabled)
    }

    suspend fun updateBluetoothPrinter(address: String?, name: String?) = withContext(Dispatchers.IO) {
        settingsDao.updateBluetoothPrinter(address, name)
    }

    suspend fun updateFooterNotes(line1: String, line2: String) = withContext(Dispatchers.IO) {
        settingsDao.updateFooterNotes(line1, line2)
    }

    // Customer Operations
    val allCustomersFlow: Flow<List<CustomerEntity>> = customerDao.getAllCustomersFlow()

    // Database Capacity & Statistics Monitoring
    val totalInvoiceCountFlow: Flow<Int> = invoiceDao.getTotalInvoiceCountFlow()
    val totalInvoiceLinesCountFlow: Flow<Int> = invoiceDao.getTotalInvoiceLinesCountFlow()
    val totalProductCountFlow: Flow<Int> = productDao.getProductCountFlow()
    val totalCustomerCountFlow: Flow<Int> = customerDao.getCustomerCountFlow()

    fun getDatabaseSizeBytes(context: Context): Long {
        return try {
            val dbFile = context.getDatabasePath("pos_invoice_master.db")
            val walFile = context.getDatabasePath("pos_invoice_master.db-wal")
            val shmFile = context.getDatabasePath("pos_invoice_master.db-shm")
            var size = 0L
            if (dbFile.exists()) size += dbFile.length()
            if (walFile.exists()) size += walFile.length()
            if (shmFile.exists()) size += shmFile.length()
            size
        } catch (e: Exception) {
            0L
        }
    }

    fun searchCustomersFlow(query: String, limit: Int = 10): Flow<List<CustomerEntity>> {
        return customerDao.searchCustomersFlow(query.trim(), limit)
    }

    suspend fun searchCustomersDirect(query: String, limit: Int = 10): List<CustomerEntity> =
        withContext(Dispatchers.IO) {
            customerDao.searchCustomers(query.trim(), limit)
        }

    suspend fun getCustomerByInfo(customerInfo: String): CustomerEntity? =
        withContext(Dispatchers.IO) {
            customerDao.getCustomerByInfo(customerInfo.trim())
        }

    suspend fun saveCustomer(customer: CustomerEntity) = withContext(Dispatchers.IO) {
        val existing = customerDao.getCustomerByInfo(customer.customerInfo.trim())
        val toSave = customer.copy(
            customerInfo = customer.customerInfo.trim(),
            createdAt = existing?.createdAt ?: customer.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        customerDao.insertOrUpdateCustomer(toSave)
    }

    suspend fun recordCustomerOrder(customerInfo: String, totalPaisa: Long) = withContext(Dispatchers.IO) {
        val trimmed = customerInfo.trim()
        if (trimmed.isNotBlank()) {
            val existing = customerDao.getCustomerByInfo(trimmed)
            val now = System.currentTimeMillis()
            if (existing != null) {
                customerDao.recordCustomerOrder(trimmed, totalPaisa, now)
            } else {
                customerDao.insertOrUpdateCustomer(
                    CustomerEntity(
                        customerInfo = trimmed,
                        phoneNumber = if (trimmed.all { it.isDigit() || it in "+- " }) trimmed else "",
                        totalOrders = 1,
                        totalSpentPaisa = totalPaisa,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
    }
}
