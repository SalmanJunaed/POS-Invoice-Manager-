package com.example.pos.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pos.core.error.PosResult
import com.example.pos.core.util.CompanyLogoManager
import com.example.pos.data.Localization
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.CustomerEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.OperatorEntity
import com.example.pos.data.db.ProductEntity
import com.example.pos.data.db.ItemSalesSummary
import com.example.pos.data.repository.HistorySortOption
import com.example.pos.data.repository.PosRepository
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.excel.ExcelExchangeEngine
import com.example.pos.excel.ImportSummary
import com.example.pos.modules.pdf.PdfInvoiceEngine
import com.example.pos.printer.BluetoothDeviceItem
import com.example.pos.printer.BluetoothPrinterManager
import com.example.pos.printer.EscPosPrinterEngine
import com.example.pos.printer.PrinterConnectionState
import com.example.pos.printer.ThermalReceiptFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.util.Calendar
import java.util.UUID

data class DraftInvoiceItem(
    val tempId: String = UUID.randomUUID().toString(),
    val itemId: String,
    val itemName: String,
    val itemType: String,
    val unit: String,
    val quantityStr: String,
    val unitPriceBdtStr: String,
    val unitPricePaisa: Long,
    val lineTotalPaisa: Long
)

enum class DateFilterMode {
    ALL, TODAY, YESTERDAY, WEEK, MONTH, SIX_MONTHS, YEAR, CUSTOM
}

enum class AnalyticsGrouping {
    DAY, MONTH, YEAR
}

enum class AnalyticsMetric {
    ROUNDED_SALES, RAW_SALES, TRANSACTION_COUNT
}

class PosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PosRepository(application)

    // Language & Theme State
    private val _language = MutableStateFlow("bn")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _themeMode = MutableStateFlow("LIGHT")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // Session State
    val activeOperator: StateFlow<OperatorEntity?> = repository.activeOperatorFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val companyHeader: StateFlow<CompanyHeaderEntity?> = repository.companyHeaderFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val allProducts: StateFlow<List<ProductEntity>> = repository.allProductsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allActiveProducts: StateFlow<List<ProductEntity>> = repository.allActiveProductsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val appSettings = repository.appSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Active Invoice Draft
    private val _draftOrderId = MutableStateFlow("INV-10294")
    val draftOrderId: StateFlow<String> = _draftOrderId.asStateFlow()

    private val _customerInfo = MutableStateFlow("")
    val customerInfo: StateFlow<String> = _customerInfo.asStateFlow()

    private val _customerInfoError = MutableStateFlow<String?>(null)
    val customerInfoError: StateFlow<String?> = _customerInfoError.asStateFlow()

    // Customer search suggestions for fast autocomplete
    private val _customerSuggestions = MutableStateFlow<List<CustomerEntity>>(emptyList())
    val customerSuggestions: StateFlow<List<CustomerEntity>> = _customerSuggestions.asStateFlow()

    // All registered customers list
    val allCustomers: StateFlow<List<CustomerEntity>> = repository.allCustomersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _draftItems = MutableStateFlow<List<DraftInvoiceItem>>(emptyList())
    val draftItems: StateFlow<List<DraftInvoiceItem>> = _draftItems.asStateFlow()

    // Real-time calculation state
    val rawSubtotalPaisa: StateFlow<Long> = _draftItems.combine(_customerInfo) { items, _ ->
        FinancialCalculationEngine.calculateRawTotal(items.map { it.lineTotalPaisa })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val roundedTotalBdt: StateFlow<Long> = rawSubtotalPaisa.combine(_draftItems) { raw, _ ->
        FinancialCalculationEngine.calculateRoundedTotalBdt(raw)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val roundingDifferencePaisa: StateFlow<Long> = rawSubtotalPaisa.combine(roundedTotalBdt) { raw, rounded ->
        FinancialCalculationEngine.calculateRoundingDifferencePaisa(raw, rounded)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val _draftPaidAmountBdt = MutableStateFlow<String>("")
    val draftPaidAmountBdt: StateFlow<String> = _draftPaidAmountBdt.asStateFlow()

    fun setDraftPaidAmount(amount: String) {
        _draftPaidAmountBdt.value = amount
    }

    val isDraftFullPaid: StateFlow<Boolean> = combine(
        roundedTotalBdt,
        _draftPaidAmountBdt
    ) { roundedTotal, paidText ->
        if (roundedTotal <= 0L) return@combine false
        val totalPaisa = roundedTotal * 100L
        val paidPaisa = if (paidText.isBlank()) totalPaisa else FinancialCalculationEngine.parseBdtToPaisa(paidText)
        val duePaisa = maxOf(0L, totalPaisa - paidPaisa)
        paidPaisa >= totalPaisa && duePaisa <= 0L
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val effectiveDraftPaidBdt: StateFlow<Long> = combine(
        roundedTotalBdt,
        _draftPaidAmountBdt
    ) { roundedTotal, paidText ->
        if (paidText.isBlank()) roundedTotal else FinancialCalculationEngine.parseBdtToPaisa(paidText) / 100L
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val draftDueBdt: StateFlow<Long> = combine(
        roundedTotalBdt,
        effectiveDraftPaidBdt
    ) { roundedTotal, paidBdt ->
        maxOf(0L, roundedTotal - paidBdt)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Sales History & Reports Filters
    private val _historyQuery = MutableStateFlow("")
    val historyQuery: StateFlow<String> = _historyQuery.asStateFlow()

    private val _historyOrderIdFilter = MutableStateFlow("")
    val historyOrderIdFilter: StateFlow<String> = _historyOrderIdFilter.asStateFlow()

    private val _historyCustomerFilter = MutableStateFlow("")
    val historyCustomerFilter: StateFlow<String> = _historyCustomerFilter.asStateFlow()

    private val _dateFilter = MutableStateFlow(DateFilterMode.ALL)
    val dateFilter: StateFlow<DateFilterMode> = _dateFilter.asStateFlow()

    private val _customStartDate = MutableStateFlow<Long?>(null)
    val customStartDate: StateFlow<Long?> = _customStartDate.asStateFlow()

    private val _customEndDate = MutableStateFlow<Long?>(null)
    val customEndDate: StateFlow<Long?> = _customEndDate.asStateFlow()

    private val _historySortOption = MutableStateFlow(HistorySortOption.DATE_DESC)
    val historySortOption: StateFlow<HistorySortOption> = _historySortOption.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _pageSize = MutableStateFlow(25)
    val pageSize: StateFlow<Int> = _pageSize.asStateFlow()

    private val _totalFilteredCount = MutableStateFlow(0)
    val totalFilteredCount: StateFlow<Int> = _totalFilteredCount.asStateFlow()

    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory.asStateFlow()

    private val _orderLinesCache = MutableStateFlow<Map<String, List<InvoiceLineEntity>>>(emptyMap())
    val orderLinesCache: StateFlow<Map<String, List<InvoiceLineEntity>>> = _orderLinesCache.asStateFlow()

    private val _expandedOrderId = MutableStateFlow<String?>(null)
    val expandedOrderId: StateFlow<String?> = _expandedOrderId.asStateFlow()

    private val _expandedLines = MutableStateFlow<List<InvoiceLineEntity>>(emptyList())
    val expandedLines: StateFlow<List<InvoiceLineEntity>> = _expandedLines.asStateFlow()

    // Invoices list for history (paged slice)
    private val _historyInvoices = MutableStateFlow<List<InvoiceEntity>>(emptyList())
    val historyInvoices: StateFlow<List<InvoiceEntity>> = _historyInvoices.asStateFlow()

    // Top selling products database aggregation for report
    private val _topSellingItems = MutableStateFlow<List<ItemSalesSummary>>(emptyList())
    val topSellingItems: StateFlow<List<ItemSalesSummary>> = _topSellingItems.asStateFlow()

    // Aggregations
    private val _aggregatedOrders = MutableStateFlow(0)
    val aggregatedOrders: StateFlow<Int> = _aggregatedOrders.asStateFlow()

    private val _aggregatedRawPaisa = MutableStateFlow(0L)
    val aggregatedRawPaisa: StateFlow<Long> = _aggregatedRawPaisa.asStateFlow()

    private val _aggregatedRoundedBdt = MutableStateFlow(0L)
    val aggregatedRoundedBdt: StateFlow<Long> = _aggregatedRoundedBdt.asStateFlow()

    // Multi-Branch Sync & Import/Export State
    private val _importSummary = MutableStateFlow<ImportSummary?>(null)
    val importSummary: StateFlow<ImportSummary?> = _importSummary.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportDateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val exportDateRange: StateFlow<Pair<Long, Long>?> = _exportDateRange.asStateFlow()

    fun setExportDateRange(range: Pair<Long, Long>?) {
        _exportDateRange.value = range
    }

    private val _orderIdSuggestions = MutableStateFlow<List<String>>(emptyList())
    val orderIdSuggestions: StateFlow<List<String>> = _orderIdSuggestions.asStateFlow()

    fun clearImportSummary() {
        _importSummary.value = null
    }

    // Analytics Graph State
    private val _analyticsGrouping = MutableStateFlow(AnalyticsGrouping.DAY)
    val analyticsGrouping: StateFlow<AnalyticsGrouping> = _analyticsGrouping.asStateFlow()

    private val _analyticsMetric = MutableStateFlow(AnalyticsMetric.ROUNDED_SALES)
    val analyticsMetric: StateFlow<AnalyticsMetric> = _analyticsMetric.asStateFlow()

    private val _analyticsDataPoints = MutableStateFlow<List<com.example.pos.data.db.SalesPeriodAggregate>>(emptyList())
    val analyticsDataPoints: StateFlow<List<com.example.pos.data.db.SalesPeriodAggregate>> = _analyticsDataPoints.asStateFlow()

    val chartDataPoints: StateFlow<List<com.example.pos.domain.model.ChartDataPoint>> = combine(
        _analyticsDataPoints,
        _analyticsMetric,
        language
    ) { aggregates, metric, lang ->
        aggregates.map { agg ->
            val label = com.example.pos.core.util.DateTimeUtils.formatPeriodKeyForDisplay(agg.periodKey, lang)
            val value = when (metric) {
                AnalyticsMetric.ROUNDED_SALES -> agg.totalRoundedBdt
                AnalyticsMetric.RAW_SALES -> agg.totalRawPaisa / 100L
                AnalyticsMetric.TRANSACTION_COUNT -> agg.transactionCount.toLong()
            }
            com.example.pos.domain.model.ChartDataPoint(label = label, value = value)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Detailed Receipt Popup Dialog State
    private val _isReceiptPopupOpen = MutableStateFlow(false)
    val isReceiptPopupOpen: StateFlow<Boolean> = _isReceiptPopupOpen.asStateFlow()

    private val _selectedInvoiceForReceipt = MutableStateFlow<InvoiceEntity?>(null)
    val selectedInvoiceForReceipt: StateFlow<InvoiceEntity?> = _selectedInvoiceForReceipt.asStateFlow()

    private val _receiptPopupLines = MutableStateFlow<List<InvoiceLineEntity>>(emptyList())
    val receiptPopupLines: StateFlow<List<InvoiceLineEntity>> = _receiptPopupLines.asStateFlow()

    private val _receiptPreviewOpen = MutableStateFlow(false)
    val receiptPreviewOpen: StateFlow<Boolean> = _receiptPreviewOpen.asStateFlow()

    private val _receiptPreviewText = MutableStateFlow("")
    val receiptPreviewText: StateFlow<String> = _receiptPreviewText.asStateFlow()

    // UI Snackbars / Alerts
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Transaction checkout in-flight guard
    private val _isProcessingOrder = MutableStateFlow(false)
    val isProcessingOrder: StateFlow<Boolean> = _isProcessingOrder.asStateFlow()

    // Printer hardware states
    private val _pairedPrinters = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val pairedPrinters: StateFlow<List<Pair<String, String>>> = _pairedPrinters.asStateFlow()

    val printerConnectionState: StateFlow<PrinterConnectionState> = BluetoothPrinterManager.connectionState
    val pairedPrinterDevices: StateFlow<List<BluetoothDeviceItem>> = BluetoothPrinterManager.pairedDevices
    val discoveredPrinterDevices: StateFlow<List<BluetoothDeviceItem>> = BluetoothPrinterManager.discoveredDevices
    val isBluetoothScanning: StateFlow<Boolean> = BluetoothPrinterManager.isScanning
    val isBluetoothEnabled: Boolean get() = BluetoothPrinterManager.isBluetoothEnabled
    val isBluetoothAvailable: Boolean get() = BluetoothPrinterManager.isBluetoothAvailable

    // Database Capacity & Storage Monitoring
    val supportedCapacity = 200_000

    val totalInvoiceCount: StateFlow<Int> = repository.totalInvoiceCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalInvoiceLinesCount: StateFlow<Int> = repository.totalInvoiceLinesCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalProductCount: StateFlow<Int> = repository.totalProductCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCustomerCount: StateFlow<Int> = repository.totalCustomerCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _databaseSizeBytes = MutableStateFlow(0L)
    val databaseSizeBytes: StateFlow<Long> = _databaseSizeBytes.asStateFlow()

    private val _isDeletingInvoice = MutableStateFlow(false)
    val isDeletingInvoice: StateFlow<Boolean> = _isDeletingInvoice.asStateFlow()

    // Session initialization state
    private val _isSessionChecking = MutableStateFlow(true)
    val isSessionChecking: StateFlow<Boolean> = _isSessionChecking.asStateFlow()

    init {
        // Check and restore active session based on remember-me and secure storage
        viewModelScope.launch {
            repository.checkAndRestoreSession()
            _isSessionChecking.value = false
        }

        // Observe settings changes for language and theme
        viewModelScope.launch {
            repository.appSettingsFlow.collect { settings ->
                if (settings != null) {
                    _language.value = settings.language
                    _themeMode.value = settings.themeMode
                }
            }
        }
        refreshNextOrderId()
        refreshHistory()
        refreshPairedPrinters()
        refreshDatabaseSize()
    }

    fun getRememberedPhone(): String {
        return repository.getRememberedPhone()
    }

    fun isRememberMeSaved(): Boolean {
        return repository.isRememberMeSaved()
    }

    fun refreshDatabaseSize() {
        viewModelScope.launch(Dispatchers.IO) {
            _databaseSizeBytes.value = repository.getDatabaseSizeBytes(getApplication())
        }
    }

    fun refreshPairedPrinters() {
        BluetoothPrinterManager.refreshPairedDevices(getApplication())
        _pairedPrinters.value = EscPosPrinterEngine.getPairedDevices()
    }

    fun startBluetoothScanning() {
        BluetoothPrinterManager.startDiscovery(getApplication())
    }

    fun stopBluetoothScanning() {
        BluetoothPrinterManager.stopDiscovery(getApplication())
    }

    fun connectPrinter(address: String, name: String? = null) {
        viewModelScope.launch {
            val res = BluetoothPrinterManager.connect(address, name)
            if (res.isSuccess) {
                val isBn = language.value == "bn"
                _toastMessage.value = if (isBn) "প্রিন্টার সংযুক্ত: ${name ?: address}" else "Printer connected: ${name ?: address}"
            } else {
                _toastMessage.value = res.exceptionOrNull()?.message ?: "Failed to connect"
            }
        }
    }

    fun disconnectPrinter() {
        viewModelScope.launch {
            BluetoothPrinterManager.disconnect()
            val isBn = language.value == "bn"
            _toastMessage.value = if (isBn) "প্রিন্টার সংযোগ বিচ্ছিন্ন করা হয়েছে" else "Printer disconnected"
        }
    }

    fun refreshNextOrderId() {
        viewModelScope.launch {
            _draftOrderId.value = repository.getNextOrderId()
        }
    }

    fun setCustomerInfo(info: String) {
        _customerInfo.value = info
        val validation = com.example.pos.domain.CustomerValidator.validate(info)
        _customerInfoError.value = validation.errorMessage

        // Query customer suggestions asynchronously
        val trimmed = info.trim()
        if (trimmed.length >= 2) {
            viewModelScope.launch {
                _customerSuggestions.value = repository.searchCustomersDirect(trimmed, 6)
            }
        } else {
            _customerSuggestions.value = emptyList()
        }
    }

    fun selectCustomerSuggestion(customer: CustomerEntity) {
        _customerInfo.value = customer.customerInfo
        _customerInfoError.value = null
        _customerSuggestions.value = emptyList()
    }

    fun clearCustomerSuggestions() {
        _customerSuggestions.value = emptyList()
    }

    fun saveCustomerProfile(
        customerInfo: String,
        name: String,
        phone: String,
        address: String,
        notes: String,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val validation = com.example.pos.domain.CustomerValidator.validate(customerInfo)
        if (!validation.isValid || validation.sanitizedValue.isBlank()) {
            onComplete(false, validation.errorMessage ?: "Invalid customer info")
            return
        }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val existing = repository.getCustomerByInfo(validation.sanitizedValue)
                val customer = CustomerEntity(
                    customerInfo = validation.sanitizedValue,
                    customerName = name.trim(),
                    phoneNumber = phone.trim(),
                    address = address.trim(),
                    notes = notes.trim(),
                    totalOrders = existing?.totalOrders ?: 0,
                    totalSpentPaisa = existing?.totalSpentPaisa ?: 0L,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
                repository.saveCustomer(customer)
                _toastMessage.value = "Customer saved successfully"
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.message)
            }
        }
    }

    fun addItemFromCatalog(product: ProductEntity) {
        val qtyStr = "1"
        val qty = FinancialCalculationEngine.parseQuantity(qtyStr)
        val lineTotal = FinancialCalculationEngine.calculateLineTotal(product.defaultUnitPricePaisa, qty)
        val priceBdtStr = FinancialCalculationEngine.formatPaisaToBdtInput(product.defaultUnitPricePaisa)

        // Select the appropriate display name based on active app language
        val snapshotItemName = product.getDisplayName(_language.value)

        val newItem = DraftInvoiceItem(
            itemId = product.itemId,
            itemName = snapshotItemName,
            itemType = product.itemType,
            unit = product.unit,
            quantityStr = qtyStr,
            unitPriceBdtStr = priceBdtStr,
            unitPricePaisa = product.defaultUnitPricePaisa,
            lineTotalPaisa = lineTotal
        )
        _draftItems.value = _draftItems.value + newItem
    }

    fun addCustomItem(name: String, priceBdtText: String, qtyText: String, unitText: String, itemType: String = "Product") {
        val cleanName = name.trim().ifEmpty { "Custom Service" }
        val pricePaisa = FinancialCalculationEngine.parseBdtToPaisa(priceBdtText)
        val cleanQty = qtyText.trim().ifEmpty { "1" }
        val qty = FinancialCalculationEngine.parseQuantity(cleanQty)
        val cleanUnit = unitText.trim().ifEmpty { "Unit" }
        val cleanType = itemType.trim().ifEmpty { "Product" }
        val lineTotal = FinancialCalculationEngine.calculateLineTotal(pricePaisa, qty)

        val newItem = DraftInvoiceItem(
            itemId = "custom-" + UUID.randomUUID().toString().take(6),
            itemName = cleanName,
            itemType = cleanType,
            unit = cleanUnit,
            quantityStr = cleanQty,
            unitPriceBdtStr = FinancialCalculationEngine.formatPaisaToBdtInput(pricePaisa),
            unitPricePaisa = pricePaisa,
            lineTotalPaisa = lineTotal
        )
        _draftItems.value = _draftItems.value + newItem
    }

    fun updateItemQuantity(tempId: String, newQtyText: String) {
        val currentList = _draftItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.tempId == tempId }
        if (index != -1) {
            val item = currentList[index]
            val qty = FinancialCalculationEngine.parseQuantity(newQtyText)
            val newTotal = FinancialCalculationEngine.calculateLineTotal(item.unitPricePaisa, qty)
            currentList[index] = item.copy(
                quantityStr = newQtyText,
                lineTotalPaisa = newTotal
            )
            _draftItems.value = currentList
        }
    }

    fun updateItemUnitPrice(tempId: String, newPriceBdtText: String) {
        val currentList = _draftItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.tempId == tempId }
        if (index != -1) {
            val item = currentList[index]
            val newPricePaisa = FinancialCalculationEngine.parseBdtToPaisa(newPriceBdtText)
            val qty = FinancialCalculationEngine.parseQuantity(item.quantityStr)
            val newTotal = FinancialCalculationEngine.calculateLineTotal(newPricePaisa, qty)
            currentList[index] = item.copy(
                unitPriceBdtStr = newPriceBdtText,
                unitPricePaisa = newPricePaisa,
                lineTotalPaisa = newTotal
            )
            _draftItems.value = currentList
        }
    }

    fun removeItem(tempId: String) {
        _draftItems.value = _draftItems.value.filterNot { it.tempId == tempId }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        val current = _draftItems.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val moved = current.removeAt(fromIndex)
            current.add(toIndex, moved)
            _draftItems.value = current
        }
    }

    fun clearDraft() {
        _draftItems.value = emptyList()
        _customerInfo.value = ""
        _draftPaidAmountBdt.value = ""
        refreshNextOrderId()
    }

    fun generateCurrentReceiptText(): String {
        val header = companyHeader.value
        val settings = appSettings.value
        val lines = _draftItems.value.mapIndexed { idx, item ->
            InvoiceLineEntity(
                orderId = _draftOrderId.value,
                itemId = item.itemId,
                itemName = item.itemName,
                itemType = item.itemType,
                unit = item.unit,
                quantityStr = item.quantityStr,
                unitPricePaisa = item.unitPricePaisa,
                lineTotalPaisa = item.lineTotalPaisa,
                sortOrder = idx + 1
            )
        }
        val rawTotal = FinancialCalculationEngine.calculateRawTotal(lines.map { it.lineTotalPaisa })
        val roundedTotal = FinancialCalculationEngine.calculateRoundedTotalBdt(rawTotal)

        val invoice = InvoiceEntity(
            orderId = _draftOrderId.value,
            dateTime = System.currentTimeMillis(),
            operatorId = activeOperator.value?.userId ?: "OP-101",
            operatorName = activeOperator.value?.operatorName ?: "Operator",
            customerInfo = _customerInfo.value.ifBlank { "N/A" },
            rawTotalPaisa = rawTotal,
            roundedTotalBdt = roundedTotal,
            footerNote = "${settings?.footerNoteLine1}\n${settings?.footerNoteLine2}"
        )
        return EscPosPrinterEngine.generateReceiptText(
            header = header,
            invoice = invoice,
            lines = lines,
            paperSize = settings?.paperSize ?: "58mm",
            footerLine1 = settings?.footerNoteLine1 ?: "",
            footerLine2 = settings?.footerNoteLine2 ?: "",
            isFullPaid = isDraftFullPaid.value
        )
    }

    fun openReceiptPreview() {
        if (_draftItems.value.isEmpty()) {
            _toastMessage.value = Localization.get(Localization.Key.EMPTY_INVOICE_PROMPT, _language.value)
            return
        }
        _receiptPreviewText.value = generateCurrentReceiptText()
        _receiptPreviewOpen.value = true
    }

    fun closeReceiptPreview() {
        _receiptPreviewOpen.value = false
    }

    fun checkoutAndSave(printDirectly: Boolean = true) {
        if (_draftItems.value.isEmpty()) {
            _toastMessage.value = Localization.get(Localization.Key.EMPTY_INVOICE_PROMPT, _language.value)
            return
        }

        if (_isProcessingOrder.value) {
            return
        }
        _isProcessingOrder.value = true

        viewModelScope.launch {
            try {
                val orderId = _draftOrderId.value
                val now = System.currentTimeMillis()
                val operator = activeOperator.value
                val settings = appSettings.value

                val header = companyHeader.value
                val branchCode = header?.branchNumber?.ifBlank { "01" } ?: "01"
                val globalId = com.example.pos.core.util.IdGenerator.generateGlobalTransactionId()

                val lines = _draftItems.value.mapIndexed { idx, it ->
                    InvoiceLineEntity(
                        orderId = orderId,
                        itemId = it.itemId,
                        itemName = it.itemName,
                        itemType = it.itemType,
                        unit = it.unit,
                        quantityStr = it.quantityStr,
                        unitPricePaisa = it.unitPricePaisa,
                        lineTotalPaisa = it.lineTotalPaisa,
                        sortOrder = idx + 1
                    )
                }
                val rawTotal = FinancialCalculationEngine.calculateRawTotal(lines.map { it.lineTotalPaisa })
                val roundedTotal = FinancialCalculationEngine.calculateRoundedTotalBdt(rawTotal)

                val invoice = InvoiceEntity(
                    orderId = orderId,
                    globalTransactionId = globalId,
                    branchCode = branchCode,
                    dateTime = now,
                    operatorId = operator?.userId ?: "OP-101",
                    operatorName = operator?.operatorName ?: "Operator",
                    customerInfo = _customerInfo.value.ifBlank { "01700000000" },
                    rawTotalPaisa = rawTotal,
                    roundedTotalBdt = roundedTotal,
                    footerNote = "${settings?.footerNoteLine1}\n${settings?.footerNoteLine2}",
                    createdAt = now,
                    updatedAt = now
                )

                repository.saveOrder(invoice, lines)
                val customerToRecord = _customerInfo.value.trim().ifBlank { "01700000000" }
                repository.recordCustomerOrder(customerToRecord, rawSubtotalPaisa.value)

                val isPaid = isDraftFullPaid.value
                val paperSize = settings?.paperSize ?: "58mm"
                val autoCutter = settings?.autoCutterEnabled ?: true
                val receiptText = ThermalReceiptFormatter.generateReceiptText(
                    header = companyHeader.value,
                    invoice = invoice,
                    lines = lines,
                    paperSize = paperSize,
                    footerLine1 = settings?.footerNoteLine1 ?: "",
                    footerLine2 = settings?.footerNoteLine2 ?: "",
                    isFullPaid = isPaid
                )

                if (printDirectly && settings?.bluetoothPrinterAddress != null) {
                    val logoBitmap = EscPosPrinterEngine.loadLogoBitmapForThermal(
                        getApplication(),
                        companyHeader.value?.logoUri,
                        paperSize
                    )
                    val payload = ThermalReceiptFormatter.buildEscPosReceipt(
                        header = companyHeader.value,
                        invoice = invoice,
                        lines = lines,
                        paperSize = paperSize,
                        autoCutter = autoCutter,
                        footerLine1 = settings?.footerNoteLine1 ?: "",
                        footerLine2 = settings?.footerNoteLine2 ?: "",
                        logoBitmap = logoBitmap,
                        isFullPaid = isPaid
                    )
                    val printResult = BluetoothPrinterManager.print(
                        deviceAddress = settings.bluetoothPrinterAddress,
                        deviceName = settings.bluetoothPrinterName,
                        payload = payload
                    )
                    if (printResult.isFailure) {
                        val isBn = _language.value == "bn"
                        _toastMessage.value = if (isBn) "সংরক্ষিত হয়েছে! প্রিন্টার ত্রুটি: ${printResult.exceptionOrNull()?.message}" else "Saved to DB! Printer error: ${printResult.exceptionOrNull()?.message}"
                    } else {
                        _toastMessage.value = Localization.get(Localization.Key.SUCCESS_SAVED, _language.value)
                    }
                } else {
                    _toastMessage.value = Localization.get(Localization.Key.SUCCESS_SAVED, _language.value)
                }

                _receiptPreviewText.value = receiptText
                _receiptPreviewOpen.value = true

                // Clear draft and generate new order ID
                _draftItems.value = emptyList()
                _customerInfo.value = ""
                _draftPaidAmountBdt.value = ""
                refreshNextOrderId()
                refreshHistory()
            } catch (e: Exception) {
                _toastMessage.value = "Transaction failed: ${e.message}"
            } finally {
                _isProcessingOrder.value = false
            }
        }
    }

    private var historyInvoicesJob: Job? = null
    private var historyCountJob: Job? = null
    private var historyAggJob: Job? = null
    private var historyTopItemsJob: Job? = null
    private var historyAnalyticsJob: Job? = null

    // Sales History & Reports
    fun setHistoryQuery(query: String) {
        _historyQuery.value = query
        _currentPage.value = 0
        refreshHistory()
        updateOrderIdSuggestions(query)
    }

    fun setOrderIdFilter(orderId: String) {
        _historyOrderIdFilter.value = orderId
        _currentPage.value = 0
        refreshHistory()
        updateOrderIdSuggestions(orderId)
    }

    fun updateOrderIdSuggestions(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _orderIdSuggestions.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val matches = repository.getMatchingOrderIds(trimmed, limit = 8)
                _orderIdSuggestions.value = matches
            } catch (e: Exception) {
                _orderIdSuggestions.value = emptyList()
            }
        }
    }

    fun updateInvoiceDetails(orderId: String, customerInfo: String, footerNote: String) {
        viewModelScope.launch {
            try {
                val rows = repository.updateInvoiceCustomerAndNote(
                    orderId = orderId,
                    customerInfo = customerInfo.trim(),
                    footerNote = footerNote.trim()
                )
                if (rows > 0) {
                    val isBn = language.value == "bn"
                    _toastMessage.value = if (isBn) "ভাউচারের বিবরণ সফলভাবে আপডেট করা হয়েছে।" else "Voucher details updated successfully."
                    refreshHistory()
                }
            } catch (e: Exception) {
                _toastMessage.value = "Update failed: ${e.message}"
            }
        }
    }

    fun setCustomerFilter(customer: String) {
        _historyCustomerFilter.value = customer
        _currentPage.value = 0
        refreshHistory()
    }

    fun setDateFilter(filter: DateFilterMode) {
        _dateFilter.value = filter
        _currentPage.value = 0
        refreshHistory()
    }

    fun setCustomDateRange(startDate: Long, endDate: Long) {
        _customStartDate.value = startDate
        _customEndDate.value = endDate
        _dateFilter.value = DateFilterMode.CUSTOM
        _currentPage.value = 0
        refreshHistory()
    }

    fun setHistorySortOption(sort: HistorySortOption) {
        _historySortOption.value = sort
        _currentPage.value = 0
        refreshHistory()
    }

    fun clearAllFilters() {
        _historyQuery.value = ""
        _historyOrderIdFilter.value = ""
        _historyCustomerFilter.value = ""
        _orderIdSuggestions.value = emptyList()
        _dateFilter.value = DateFilterMode.ALL
        _customStartDate.value = null
        _customEndDate.value = null
        _historySortOption.value = HistorySortOption.DATE_DESC
        _currentPage.value = 0
        refreshHistory()
    }

    fun setPageSize(size: Int) {
        if (size > 0 && size != _pageSize.value) {
            _pageSize.value = size
            _currentPage.value = 0
            refreshHistory()
        }
    }

    fun nextPage() {
        val total = _totalFilteredCount.value
        val nextOffset = (_currentPage.value + 1) * _pageSize.value
        if (nextOffset < total) {
            _currentPage.value = _currentPage.value + 1
            fetchInvoicesPage()
        }
    }

    fun prevPage() {
        if (_currentPage.value > 0) {
            _currentPage.value = _currentPage.value - 1
            fetchInvoicesPage()
        }
    }

    fun goToPage(page: Int) {
        val total = _totalFilteredCount.value
        val maxPage = if (total == 0) 0 else (total - 1) / _pageSize.value
        val target = page.coerceIn(0, maxPage)
        if (target != _currentPage.value) {
            _currentPage.value = target
            fetchInvoicesPage()
        }
    }

    private fun fetchInvoicesPage() {
        val (startDate, endDate) = calculateDateRange(_dateFilter.value, _customStartDate.value, _customEndDate.value)
        val query = _historyQuery.value
        val orderId = _historyOrderIdFilter.value
        val customer = _historyCustomerFilter.value
        val sort = _historySortOption.value
        val size = _pageSize.value
        val offset = _currentPage.value * size

        _isLoadingHistory.value = true
        historyInvoicesJob?.cancel()
        historyInvoicesJob = viewModelScope.launch {
            repository.getInvoicesPaged(query, orderId, customer, startDate, endDate, sort, size, offset)
                .collect { list ->
                    _historyInvoices.value = list
                    _isLoadingHistory.value = false
                    if (list.isNotEmpty()) {
                        val ids = list.map { it.orderId }
                        val lines = repository.getLinesForOrders(ids)
                        val grouped = lines.groupBy { it.orderId }
                        _orderLinesCache.value = _orderLinesCache.value + grouped
                    }
                }
        }
    }

    fun refreshHistory() {
        val (startDate, endDate) = calculateDateRange(_dateFilter.value, _customStartDate.value, _customEndDate.value)
        val query = _historyQuery.value
        val orderId = _historyOrderIdFilter.value
        val customer = _historyCustomerFilter.value
        val sort = _historySortOption.value
        val size = _pageSize.value
        val offset = _currentPage.value * size

        _isLoadingHistory.value = true

        historyInvoicesJob?.cancel()
        historyInvoicesJob = viewModelScope.launch {
            repository.getInvoicesPaged(query, orderId, customer, startDate, endDate, sort, size, offset)
                .collect { list ->
                    _historyInvoices.value = list
                    _isLoadingHistory.value = false
                    if (list.isNotEmpty()) {
                        val ids = list.map { it.orderId }
                        val lines = repository.getLinesForOrders(ids)
                        val grouped = lines.groupBy { it.orderId }
                        _orderLinesCache.value = _orderLinesCache.value + grouped
                    }
                }
        }

        historyCountJob?.cancel()
        historyCountJob = viewModelScope.launch {
            repository.getInvoiceCount(query, orderId, customer, startDate, endDate).collect { count ->
                _totalFilteredCount.value = count
            }
        }

        historyAggJob?.cancel()
        historyAggJob = viewModelScope.launch {
            repository.getAggregatedSales(query, orderId, customer, startDate, endDate).collect { agg ->
                _aggregatedOrders.value = agg.totalOrders
                _aggregatedRawPaisa.value = agg.totalRawPaisa
                _aggregatedRoundedBdt.value = agg.totalRoundedBdt
            }
        }

        historyTopItemsJob?.cancel()
        historyTopItemsJob = viewModelScope.launch {
            repository.getTopSellingItems(query, orderId, customer, startDate, endDate, 5).collect { topList ->
                _topSellingItems.value = topList
            }
        }

        historyAnalyticsJob?.cancel()
        historyAnalyticsJob = viewModelScope.launch {
            val flow = when (_analyticsGrouping.value) {
                AnalyticsGrouping.DAY -> repository.getSalesByDay(query, orderId, customer, startDate, endDate)
                AnalyticsGrouping.MONTH -> repository.getSalesByMonth(query, orderId, customer, startDate, endDate)
                AnalyticsGrouping.YEAR -> repository.getSalesByYear(query, orderId, customer, startDate, endDate)
            }
            flow.collect { aggregates ->
                _analyticsDataPoints.value = aggregates
            }
        }
    }

    fun setAnalyticsGrouping(grouping: AnalyticsGrouping) {
        _analyticsGrouping.value = grouping
        refreshHistory()
    }

    fun setAnalyticsMetric(metric: AnalyticsMetric) {
        _analyticsMetric.value = metric
    }

    private fun calculateDateRange(
        filter: DateFilterMode,
        customStart: Long? = null,
        customEnd: Long? = null
    ): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis

        return when (filter) {
            DateFilterMode.ALL -> Pair(0L, now + 86400000L)
            DateFilterMode.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, start + 86400000L - 1)
            }
            DateFilterMode.YESTERDAY -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, start + 86400000L - 1)
            }
            DateFilterMode.WEEK -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now + 86400000L)
            }
            DateFilterMode.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now + 86400000L)
            }
            DateFilterMode.SIX_MONTHS -> {
                cal.add(Calendar.MONTH, -6)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now + 86400000L)
            }
            DateFilterMode.YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now + 86400000L)
            }
            DateFilterMode.CUSTOM -> {
                val start = customStart ?: 0L
                val end = customEnd ?: (now + 86400000L)
                Pair(start, end)
            }
        }
    }

    fun toggleOrderExpansion(orderId: String) {
        if (_expandedOrderId.value == orderId) {
            _expandedOrderId.value = null
            _expandedLines.value = emptyList()
        } else {
            _expandedOrderId.value = orderId
            val cached = _orderLinesCache.value[orderId]
            if (cached != null && cached.isNotEmpty()) {
                _expandedLines.value = cached
            } else {
                viewModelScope.launch {
                    val lines = repository.getLinesForOrderDirect(orderId)
                    _expandedLines.value = lines
                    _orderLinesCache.value = _orderLinesCache.value + (orderId to lines)
                }
            }
        }
    }

    fun openReceiptPopup(invoice: InvoiceEntity) {
        viewModelScope.launch {
            val lines = repository.getLinesForOrderDirect(invoice.orderId)
            val header = companyHeader.value
            val settings = appSettings.value
            val paperSize = settings?.paperSize ?: "58mm"
            val text = EscPosPrinterEngine.generateReceiptText(
                header = header,
                invoice = invoice,
                lines = lines,
                paperSize = paperSize,
                footerLine1 = settings?.footerNoteLine1 ?: "",
                footerLine2 = settings?.footerNoteLine2 ?: ""
            )
            _selectedInvoiceForReceipt.value = invoice
            _receiptPopupLines.value = lines
            _receiptPreviewText.value = text
            _isReceiptPopupOpen.value = true
        }
    }

    fun closeReceiptPopup() {
        _isReceiptPopupOpen.value = false
        _selectedInvoiceForReceipt.value = null
        _receiptPopupLines.value = emptyList()
    }

    fun reprintInvoice(invoice: InvoiceEntity) {
        viewModelScope.launch {
            val settings = appSettings.value
            val address = settings?.bluetoothPrinterAddress
            val lines = repository.getLinesForOrderDirect(invoice.orderId)
            val header = companyHeader.value
            val paperSize = settings?.paperSize ?: "58mm"
            val autoCutter = settings?.autoCutterEnabled ?: true
            val isBn = language.value == "bn"

            val validation = ThermalReceiptFormatter.validateInvoice(lines)
            if (validation.isFailure) {
                _toastMessage.value = if (isBn) "ভাউচারে কোনো পণ্য নেই" else "Cannot print voucher with no items"
                return@launch
            }

            val text = ThermalReceiptFormatter.generateReceiptText(
                header = header,
                invoice = invoice,
                lines = lines,
                paperSize = paperSize,
                footerLine1 = settings?.footerNoteLine1 ?: "",
                footerLine2 = settings?.footerNoteLine2 ?: ""
            )

            if (address != null) {
                val logoBitmap = EscPosPrinterEngine.loadLogoBitmapForThermal(
                    getApplication(),
                    header?.logoUri,
                    paperSize
                )
                val payload = ThermalReceiptFormatter.buildEscPosReceipt(
                    header = header,
                    invoice = invoice,
                    lines = lines,
                    paperSize = paperSize,
                    autoCutter = autoCutter,
                    footerLine1 = settings?.footerNoteLine1 ?: "",
                    footerLine2 = settings?.footerNoteLine2 ?: "",
                    logoBitmap = logoBitmap
                )
                val res = BluetoothPrinterManager.print(address, settings?.bluetoothPrinterName, payload)
                if (res.isSuccess) {
                    _toastMessage.value = if (isBn) "ভাউচার #${invoice.orderId} প্রিন্ট সফল!" else "Receipt printed for #${invoice.orderId}!"
                } else {
                    _toastMessage.value = if (isBn) "প্রিন্টার ত্রুটি: ${res.exceptionOrNull()?.message}" else "Printer error: ${res.exceptionOrNull()?.message}"
                }
            } else {
                _receiptPreviewText.value = text
                _selectedInvoiceForReceipt.value = invoice
                _receiptPopupLines.value = lines
                _isReceiptPopupOpen.value = true
                _toastMessage.value = if (isBn) "কোনো ব্লুটুথ প্রিন্টার যুক্ত নেই। প্রিভিউ দেখানো হচ্ছে।" else "No Bluetooth printer configured. Showing preview."
            }
        }
    }

    fun deleteInvoice(orderId: String, onSuccess: (() -> Unit)? = null) {
        if (_isDeletingInvoice.value) return
        _isDeletingInvoice.value = true
        viewModelScope.launch {
            try {
                repository.history.deleteOrder(orderId)
                if (_selectedInvoiceForReceipt.value?.orderId == orderId) {
                    _isReceiptPopupOpen.value = false
                    _selectedInvoiceForReceipt.value = null
                }
                val isBn = language.value == "bn"
                _toastMessage.value = if (isBn) "ভাউচার #$orderId সফলভাবে মুছে ফেলা হয়েছে।" else "Voucher #$orderId deleted successfully."
                refreshHistory()
                refreshDatabaseSize()
                onSuccess?.invoke()
            } catch (e: Exception) {
                val isBn = language.value == "bn"
                _toastMessage.value = if (isBn) "ভাউচার মুছতে সমস্যা হয়েছে: ${e.message}" else "Failed to delete voucher: ${e.message}"
            } finally {
                _isDeletingInvoice.value = false
            }
        }
    }

    // PDF Invoice Generation, Sharing & Saving
    fun generateAndShareInvoicePdf(context: Context, invoice: InvoiceEntity, lines: List<InvoiceLineEntity>? = null) {
        viewModelScope.launch {
            try {
                val itemLines = if (!lines.isNullOrEmpty()) lines else repository.getLinesForOrderDirect(invoice.orderId)
                val result = PdfInvoiceEngine.generateInvoicePdf(
                    context = context,
                    company = companyHeader.value,
                    invoice = invoice,
                    lines = itemLines
                )
                when (result) {
                    is PosResult.Success -> {
                        val shareIntent = PdfInvoiceEngine.getSharePdfIntent(context, result.data)
                        context.startActivity(Intent.createChooser(shareIntent, "Share Invoice PDF"))
                    }
                    is PosResult.Error -> {
                        _toastMessage.value = "PDF error: ${result.error.message}"
                    }
                }
            } catch (e: Exception) {
                _toastMessage.value = "Failed to generate PDF: ${e.message}"
            }
        }
    }

    fun saveInvoicePdfToUri(context: Context, uri: Uri, invoice: InvoiceEntity, lines: List<InvoiceLineEntity>? = null) {
        viewModelScope.launch {
            try {
                val itemLines = if (!lines.isNullOrEmpty()) lines else repository.getLinesForOrderDirect(invoice.orderId)
                val result = PdfInvoiceEngine.saveInvoicePdfToUri(
                    context = context,
                    uri = uri,
                    company = companyHeader.value,
                    invoice = invoice,
                    lines = itemLines
                )
                when (result) {
                    is PosResult.Success -> {
                        _toastMessage.value = if (language.value == "bn") "পিডিএফ সফলভাবে সংরক্ষণ করা হয়েছে!" else "Invoice PDF saved successfully!"
                    }
                    is PosResult.Error -> {
                        _toastMessage.value = "PDF save error: ${result.error.message}"
                    }
                }
            } catch (e: Exception) {
                _toastMessage.value = "Failed to save PDF: ${e.message}"
            }
        }
    }

    fun generateAndShareCurrentDraftPdf(context: Context) {
        viewModelScope.launch {
            try {
                if (_draftItems.value.isEmpty()) {
                    _toastMessage.value = Localization.get(Localization.Key.EMPTY_INVOICE_PROMPT, _language.value)
                    return@launch
                }
                val orderId = _draftOrderId.value
                val now = System.currentTimeMillis()
                val operator = activeOperator.value
                val settings = appSettings.value
                val header = companyHeader.value
                val branchCode = header?.branchNumber?.ifBlank { "01" } ?: "01"
                val globalId = com.example.pos.core.util.IdGenerator.generateGlobalTransactionId()

                val lines = _draftItems.value.mapIndexed { idx, it ->
                    InvoiceLineEntity(
                        orderId = orderId,
                        itemId = it.itemId,
                        itemName = it.itemName,
                        itemType = it.itemType,
                        unit = it.unit,
                        quantityStr = it.quantityStr,
                        unitPricePaisa = it.unitPricePaisa,
                        lineTotalPaisa = it.lineTotalPaisa,
                        sortOrder = idx + 1
                    )
                }
                val rawTotal = FinancialCalculationEngine.calculateRawTotal(lines.map { it.lineTotalPaisa })
                val roundedTotal = FinancialCalculationEngine.calculateRoundedTotalBdt(rawTotal)
                val invoice = InvoiceEntity(
                    orderId = orderId,
                    globalTransactionId = globalId,
                    branchCode = branchCode,
                    dateTime = now,
                    operatorId = operator?.userId ?: "OP-101",
                    operatorName = operator?.operatorName ?: "Operator",
                    customerInfo = _customerInfo.value.ifBlank { "01700000000" },
                    rawTotalPaisa = rawTotal,
                    roundedTotalBdt = roundedTotal,
                    footerNote = "${settings?.footerNoteLine1 ?: ""}\n${settings?.footerNoteLine2 ?: ""}".trim()
                )

                val result = PdfInvoiceEngine.generateInvoicePdf(
                    context = context,
                    company = header,
                    invoice = invoice,
                    lines = lines
                )
                when (result) {
                    is PosResult.Success -> {
                        val shareIntent = PdfInvoiceEngine.getSharePdfIntent(context, result.data)
                        context.startActivity(Intent.createChooser(shareIntent, "Share Invoice PDF"))
                    }
                    is PosResult.Error -> {
                        _toastMessage.value = "PDF error: ${result.error.message}"
                    }
                }
            } catch (e: Exception) {
                _toastMessage.value = "Failed to generate PDF: ${e.message}"
            }
        }
    }

    // Excel Export & Import
    fun exportSalesToExcel(
        customRange: Pair<Long, Long>? = null,
        onSuccess: (File) -> Unit
    ) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val (startDate, endDate) = customRange ?: _exportDateRange.value ?: calculateDateRange(_dateFilter.value)
                val invoices = repository.getInvoicesForExport(startDate, endDate)
                val branchCode = companyHeader.value?.branchNumber ?: "01"
                val result = ExcelExchangeEngine.exportToXlsx(getApplication(), invoices, branchCode) { orderId ->
                    repository.getLinesForOrderDirect(orderId)
                }
                result.onSuccess { file ->
                    val isBn = language.value == "bn"
                    _toastMessage.value = if (isBn) "এক্সেল ফাইল এক্সপোর্ট সম্পন্ন: ${file.name}" else "Exported to: ${file.name}"
                    refreshDatabaseSize()
                    onSuccess(file)
                }.onFailure { err ->
                    val isBn = language.value == "bn"
                    _toastMessage.value = if (isBn) "এক্সপোর্ট ব্যর্থ হয়েছে: ${err.message}" else "Export failed: ${err.message}"
                }
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun exportSalesToUri(
        uri: Uri,
        customRange: Pair<Long, Long>? = null
    ) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val (startDate, endDate) = customRange ?: _exportDateRange.value ?: calculateDateRange(_dateFilter.value)
                val invoices = repository.getInvoicesForExport(startDate, endDate)
                val branchCode = companyHeader.value?.branchNumber ?: "01"
                val result = ExcelExchangeEngine.exportToUri(getApplication(), uri, invoices, branchCode) { orderId ->
                    repository.getLinesForOrderDirect(orderId)
                }
                result.onSuccess {
                    val isBn = language.value == "bn"
                    _toastMessage.value = if (isBn) "বিক্রয় লেনদেন সফলভাবে এক্সেল ফাইলে এক্সপোর্ট হয়েছে!" else "Sales transactions successfully exported to chosen file!"
                    refreshDatabaseSize()
                }.onFailure { err ->
                    val isBn = language.value == "bn"
                    _toastMessage.value = if (isBn) "এক্সপোর্ট ব্যর্থ হয়েছে: ${err.message}" else "Export failed: ${err.message}"
                }
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun importSalesFromUri(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val result = repository.importSalesFromUri(getApplication(), uri)
                result.onSuccess { summary ->
                    _importSummary.value = summary
                    val isBn = language.value == "bn"
                    _toastMessage.value = if (isBn) {
                        "সিঙ্ক সম্পন্ন: ${summary.importedCount} নতুন যুক্ত, ${summary.duplicateSkippedCount} বাদ, ${summary.conflictCount} কনফ্লিক্ট।"
                    } else {
                        "Sync complete: ${summary.importedCount} imported, ${summary.duplicateSkippedCount} skipped, ${summary.conflictCount} conflicts."
                    }
                    refreshHistory()
                    refreshDatabaseSize()
                }.onFailure { err ->
                    val isBn = language.value == "bn"
                    _toastMessage.value = if (isBn) "ইমপোর্ট ব্যর্থ হয়েছে: ${err.message}" else "Import failed: ${err.message}"
                }
            } finally {
                _isImporting.value = false
            }
        }
    }

    // Product CRUD
    fun saveProduct(
        itemId: String?,
        name: String,
        nameBn: String = "",
        nameEn: String = "",
        type: String,
        unit: String,
        priceBdtText: String,
        sortOrder: Int
    ) {
        viewModelScope.launch {
            val id = itemId ?: ("item-" + UUID.randomUUID().toString().take(6))
            val pricePaisa = FinancialCalculationEngine.parseBdtToPaisa(priceBdtText)
            val trimmedName = name.trim()
            val trimmedBn = nameBn.trim()
            val trimmedEn = nameEn.trim()
            val finalName = when {
                trimmedName.isNotBlank() -> trimmedName
                trimmedEn.isNotBlank() -> trimmedEn
                trimmedBn.isNotBlank() -> trimmedBn
                else -> "Item"
            }
            val entity = ProductEntity(
                itemId = id,
                itemName = finalName,
                nameBn = trimmedBn.ifBlank { if (trimmedName.any { it in '\u0980'..'\u09FF' }) trimmedName else "" },
                nameEn = trimmedEn.ifBlank { if (!trimmedName.any { it in '\u0980'..'\u09FF' }) trimmedName else "" },
                itemType = type,
                unit = unit.trim(),
                defaultUnitPricePaisa = pricePaisa,
                sortOrder = sortOrder,
                isActive = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.saveProduct(entity)
            _toastMessage.value = "Product saved!"
        }
    }

    fun deleteProduct(itemId: String) {
        viewModelScope.launch {
            repository.deleteProduct(itemId)
            _toastMessage.value = "Product removed"
        }
    }

    fun reorderProducts(orderedItemIds: List<String>) {
        viewModelScope.launch {
            repository.updateProductReorder(orderedItemIds)
            _toastMessage.value = "Order updated!"
        }
    }

    fun moveProduct(fromIndex: Int, toIndex: Int, currentProducts: List<ProductEntity>) {
        if (fromIndex in currentProducts.indices && toIndex in currentProducts.indices && fromIndex != toIndex) {
            val mutableList = currentProducts.toMutableList()
            val item = mutableList.removeAt(fromIndex)
            mutableList.add(toIndex, item)
            reorderProducts(mutableList.map { it.itemId })
        }
    }

    // Settings Updates
    fun updateCompanyHeader(
        companyName: String,
        branchName: String,
        branchNumber: String,
        phone: String,
        address: String
    ) {
        viewModelScope.launch {
            val current = companyHeader.value
            val updated = current?.copy(
                companyName = companyName.trim(),
                branchName = branchName.trim(),
                branchNumber = branchNumber.trim(),
                phoneNumber = phone.trim(),
                address = address.trim(),
                updatedAt = System.currentTimeMillis()
            ) ?: CompanyHeaderEntity(
                id = 1,
                companyName = companyName.trim(),
                branchName = branchName.trim(),
                branchNumber = branchNumber.trim(),
                phoneNumber = phone.trim(),
                address = address.trim(),
                logoUri = null,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateCompanyHeader(updated)
            _toastMessage.value = "Company header updated!"
        }
    }

    /**
     * Configures the company logo from a user-selected device Uri (e.g. Photo Picker).
     * Saves a permanent local copy in private storage so it survives restarts offline.
     */
    fun setCompanyLogoUri(sourceUri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val savedPath = CompanyLogoManager.copyLogoFromUri(app, sourceUri)
            if (savedPath != null) {
                val current = companyHeader.value
                val updated = current?.copy(
                    logoUri = savedPath,
                    updatedAt = System.currentTimeMillis()
                ) ?: CompanyHeaderEntity(
                    id = 1,
                    companyName = "মেসার্স সততা ফিলিং স্টেশন",
                    branchName = "প্রধান শাখা",
                    branchNumber = "01",
                    address = "ঢাকা, বাংলাদেশ",
                    phoneNumber = "01700000000",
                    logoUri = savedPath,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateCompanyHeader(updated)
                _toastMessage.value = Localization.get(Localization.Key.LOGO_ADDED_SUCCESS, _language.value)
            } else {
                _toastMessage.value = Localization.get(Localization.Key.LOGO_ERROR_READ, _language.value)
            }
        }
    }

    /**
     * Configures or restores the official 512x512 company logo asset.
     */
    fun useOfficialDefaultLogo() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val savedPath = CompanyLogoManager.copyDefaultOfficialLogo(app)
            if (savedPath != null) {
                val current = companyHeader.value
                val updated = current?.copy(
                    logoUri = savedPath,
                    updatedAt = System.currentTimeMillis()
                ) ?: CompanyHeaderEntity(
                    id = 1,
                    companyName = "মেসার্স সততা ফিলিং স্টেশন",
                    branchName = "প্রধান শাখা",
                    branchNumber = "01",
                    address = "ঢাকা, বাংলাদেশ",
                    phoneNumber = "01700000000",
                    logoUri = savedPath,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateCompanyHeader(updated)
                _toastMessage.value = Localization.get(Localization.Key.LOGO_ADDED_SUCCESS, _language.value)
            } else {
                _toastMessage.value = Localization.get(Localization.Key.LOGO_ERROR_READ, _language.value)
            }
        }
    }

    /**
     * Removes the configured company logo and gracefully resets to default.
     */
    fun removeCompanyLogo() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            CompanyLogoManager.removeLogo(app)
            val current = companyHeader.value
            if (current != null) {
                val updated = current.copy(
                    logoUri = null,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateCompanyHeader(updated)
            }
            _toastMessage.value = Localization.get(Localization.Key.LOGO_REMOVED_SUCCESS, _language.value)
        }
    }

    fun updateOperatorProfile(name: String, phone: String, address: String) {
        viewModelScope.launch {
            repository.updateOperatorProfile(name, phone, address)
            _toastMessage.value = "Operator profile updated!"
        }
    }

    fun updateFooterNotes(line1: String, line2: String) {
        viewModelScope.launch {
            repository.updateFooterNotes(line1, line2)
            _toastMessage.value = "Footer notes saved!"
        }
    }

    fun setLanguage(lang: String) {
        _language.value = lang
        viewModelScope.launch {
            repository.updateLanguage(lang)
        }
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        viewModelScope.launch {
            repository.updateThemeMode(mode)
        }
    }

    fun setPaperSize(size: String) {
        viewModelScope.launch {
            repository.updatePaperSize(size)
        }
    }

    fun setAutoCutter(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutoCutter(enabled)
        }
    }

    fun setBluetoothPrinter(address: String?, name: String?) {
        viewModelScope.launch {
            repository.updateBluetoothPrinter(address, name)
            _toastMessage.value = "Printer set to: ${name ?: address ?: "None"}"
        }
    }

    fun testPrint() {
        viewModelScope.launch {
            val settings = appSettings.value
            val header = companyHeader.value
            val paperSize = settings?.paperSize ?: "58mm"
            val autoCutter = settings?.autoCutterEnabled ?: true
            val isBn = language.value == "bn"

            val address = settings?.bluetoothPrinterAddress
            val companyName = header?.companyName?.ifBlank { "POS Station" } ?: "POS Station"

            val testSlipText = ThermalReceiptFormatter.generateTestSlipText(
                companyName = companyName,
                paperSize = paperSize,
                autoCutter = autoCutter
            )

            if (address != null) {
                val payload = ThermalReceiptFormatter.buildEscPosTestSlip(
                    companyName = companyName,
                    paperSize = paperSize,
                    autoCutter = autoCutter
                )
                val res = BluetoothPrinterManager.print(
                    deviceAddress = address,
                    deviceName = settings.bluetoothPrinterName,
                    payload = payload
                )
                if (res.isSuccess) {
                    _toastMessage.value = if (isBn) "টেস্ট রসিদ প্রিন্ট সম্পন্ন!" else "Test print sent successfully!"
                } else {
                    _toastMessage.value = if (isBn) "প্রিন্টার সমস্যা: ${res.exceptionOrNull()?.message}" else "Printer error: ${res.exceptionOrNull()?.message}"
                }
            } else {
                _toastMessage.value = if (isBn) "কোনো প্রিন্টার যুক্ত নেই। টেস্ট স্লিপ প্রিভিউ দেখানো হচ্ছে।" else "No printer configured. Previewing test slip."
                _receiptPreviewText.value = testSlipText
                _receiptPreviewOpen.value = true
            }
        }
    }

    // Auth Operations
    fun login(phone: String, pass: String, remember: Boolean, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.login(phone, pass, remember)
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    fun register(name: String, phone: String, address: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.registerOperator(name, phone, address, pass)
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Registration failed")
            }
        }
    }

    fun resetPassword(phone: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.resetPassword(phone, pass)
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Reset failed")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
