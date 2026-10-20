package com.example.pos.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pos.core.util.DateTimeUtils
import com.example.pos.data.Localization
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.ItemSalesSummary
import com.example.pos.data.repository.HistorySortOption
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.ui.components.CompanyLogoView
import com.example.pos.ui.components.ImportSummaryDialog
import com.example.pos.ui.components.TopStationHeader
import com.example.pos.ui.viewmodel.DateFilterMode
import com.example.pos.ui.viewmodel.PosViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: PosViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val companyHeader by viewModel.companyHeader.collectAsState()
    val activeOperator by viewModel.activeOperator.collectAsState()

    val historyQuery by viewModel.historyQuery.collectAsState()
    val historyOrderIdFilter by viewModel.historyOrderIdFilter.collectAsState()
    val historyCustomerFilter by viewModel.historyCustomerFilter.collectAsState()
    val dateFilter by viewModel.dateFilter.collectAsState()
    val sortOption by viewModel.historySortOption.collectAsState()

    val invoices by viewModel.historyInvoices.collectAsState()
    val totalCount by viewModel.totalFilteredCount.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val pageSize by viewModel.pageSize.collectAsState()
    val isLoading by viewModel.isLoadingHistory.collectAsState()

    val expandedOrderId by viewModel.expandedOrderId.collectAsState()
    val expandedLines by viewModel.expandedLines.collectAsState()
    val orderLinesCache by viewModel.orderLinesCache.collectAsState()

    val aggregatedOrders by viewModel.aggregatedOrders.collectAsState()
    val aggregatedRawPaisa by viewModel.aggregatedRawPaisa.collectAsState()
    val aggregatedRoundedBdt by viewModel.aggregatedRoundedBdt.collectAsState()
    val topSellingItems by viewModel.topSellingItems.collectAsState()

    val isReceiptPopupOpen by viewModel.isReceiptPopupOpen.collectAsState()
    val selectedInvoiceForReceipt by viewModel.selectedInvoiceForReceipt.collectAsState()
    val receiptPopupLines by viewModel.receiptPopupLines.collectAsState()
    val receiptPopupText by viewModel.receiptPreviewText.collectAsState()

    val analyticsGrouping by viewModel.analyticsGrouping.collectAsState()
    val analyticsMetric by viewModel.analyticsMetric.collectAsState()
    val analyticsDataPoints by viewModel.analyticsDataPoints.collectAsState()
    val chartDataPoints by viewModel.chartDataPoints.collectAsState()

    var activeHistoryTab by remember { mutableStateOf(0) }

    // UI state for filter dialogs
    var showAdvancedFilters by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    val isDeletingInvoice by viewModel.isDeletingInvoice.collectAsState()
    var invoiceToDelete by remember { mutableStateOf<InvoiceEntity?>(null) }
    var invoiceToEdit by remember { mutableStateOf<InvoiceEntity?>(null) }
    val orderIdSuggestions by viewModel.orderIdSuggestions.collectAsState()

    val totalInvoiceCount by viewModel.totalInvoiceCount.collectAsState()
    val remainingCapacity = (viewModel.supportedCapacity - totalInvoiceCount).coerceAtLeast(0)

    val importSummary by viewModel.importSummary.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()

    val exportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            viewModel.exportSalesToUri(uri)
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importSalesFromUri(uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("history_screen")
    ) {
        // Top Station Header
        TopStationHeader(
            company = companyHeader,
            operator = activeOperator,
            currentLanguage = language,
            currentTheme = themeMode,
            onToggleLanguage = {
                viewModel.setLanguage(if (language == "bn") "en" else "bn")
            },
            onToggleTheme = {
                viewModel.setThemeMode(if (themeMode == "DARK") "LIGHT" else "DARK")
            }
        )

        // Primary Navigation Tabs (Transactions vs Graphs & Analytics)
        PrimaryTabRow(
            selectedTabIndex = activeHistoryTab,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_primary_tabs")
        ) {
            Tab(
                selected = activeHistoryTab == 0,
                onClick = { activeHistoryTab = 0 },
                text = {
                    Text(
                        text = Localization.get(Localization.Key.TRANSACTIONS_TAB_TITLE, language),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("tab_transactions")
            )
            Tab(
                selected = activeHistoryTab == 1,
                onClick = { activeHistoryTab = 1 },
                text = {
                    Text(
                        text = Localization.get(Localization.Key.ANALYTICS_TAB_TITLE, language),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("tab_analytics")
            )
        }

        if (activeHistoryTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
            // Search and Filtering Controls
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Main Search Field
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = historyQuery,
                                onValueChange = { viewModel.setHistoryQuery(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("history_search_input"),
                                placeholder = {
                                    Text(
                                        text = Localization.get(Localization.Key.SEARCH_ORDERS_PLACEHOLDER, language),
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (historyQuery.isNotBlank()) {
                                        IconButton(onClick = { viewModel.setHistoryQuery("") }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Sort Dropdown Button
                            Box {
                                OutlinedButton(
                                    onClick = { showSortMenu = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("sort_menu_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when (sortOption) {
                                            HistorySortOption.DATE_DESC -> if (language == "bn") "তারিখ ↓" else "Date ↓"
                                            HistorySortOption.DATE_ASC -> if (language == "bn") "তারিখ ↑" else "Date ↑"
                                            HistorySortOption.AMOUNT_DESC -> if (language == "bn") "টাকা ↓" else "BDT ↓"
                                            HistorySortOption.AMOUNT_ASC -> if (language == "bn") "টাকা ↑" else "BDT ↑"
                                            HistorySortOption.ORDER_ID_DESC -> if (language == "bn") "আইডি Z-A" else "ID Z-A"
                                            HistorySortOption.ORDER_ID_ASC -> if (language == "bn") "আইডি A-Z" else "ID A-Z"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.SORT_DATE_DESC, language)) },
                                        onClick = {
                                            viewModel.setHistorySortOption(HistorySortOption.DATE_DESC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.SORT_DATE_ASC, language)) },
                                        onClick = {
                                            viewModel.setHistorySortOption(HistorySortOption.DATE_ASC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.SORT_AMOUNT_DESC, language)) },
                                        onClick = {
                                            viewModel.setHistorySortOption(HistorySortOption.AMOUNT_DESC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.SORT_AMOUNT_ASC, language)) },
                                        onClick = {
                                            viewModel.setHistorySortOption(HistorySortOption.AMOUNT_ASC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.SORT_ORDER_DESC, language)) },
                                        onClick = {
                                            viewModel.setHistorySortOption(HistorySortOption.ORDER_ID_DESC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.SORT_ORDER_ASC, language)) },
                                        onClick = {
                                            viewModel.setHistorySortOption(HistorySortOption.ORDER_ID_ASC)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Dynamic Autocomplete Suggestions for Voucher / Order ID
                        if ((historyQuery.isNotBlank() || historyOrderIdFilter.isNotBlank()) && orderIdSuggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = Localization.get(Localization.Key.SEARCH_VOUCHER_SUGGESTIONS, language),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_suggestions_container"),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                orderIdSuggestions.forEach { suggestionId ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier
                                            .clickable {
                                                viewModel.setHistoryQuery(suggestionId)
                                                viewModel.setOrderIdFilter(suggestionId)
                                                viewModel.toggleOrderExpansion(suggestionId)
                                            }
                                            .testTag("suggestion_$suggestionId")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Receipt,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = suggestionId,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Date Filter Chips
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = dateFilter == DateFilterMode.ALL,
                                onClick = { viewModel.setDateFilter(DateFilterMode.ALL) },
                                label = { Text(Localization.get(Localization.Key.DATE_FILTER_ALL, language), fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = dateFilter == DateFilterMode.TODAY,
                                onClick = { viewModel.setDateFilter(DateFilterMode.TODAY) },
                                label = { Text(Localization.get(Localization.Key.DATE_FILTER_TODAY, language), fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = dateFilter == DateFilterMode.YESTERDAY,
                                onClick = { viewModel.setDateFilter(DateFilterMode.YESTERDAY) },
                                label = { Text(Localization.get(Localization.Key.DATE_FILTER_YESTERDAY, language), fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = dateFilter == DateFilterMode.WEEK,
                                onClick = { viewModel.setDateFilter(DateFilterMode.WEEK) },
                                label = { Text(Localization.get(Localization.Key.DATE_FILTER_WEEK, language), fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = dateFilter == DateFilterMode.MONTH,
                                onClick = { viewModel.setDateFilter(DateFilterMode.MONTH) },
                                label = { Text(Localization.get(Localization.Key.DATE_FILTER_MONTH, language), fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = dateFilter == DateFilterMode.SIX_MONTHS,
                                onClick = { viewModel.setDateFilter(DateFilterMode.SIX_MONTHS) },
                                label = { Text(Localization.get(Localization.Key.DATE_FILTER_SIX_MONTHS, language), fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = dateFilter == DateFilterMode.YEAR,
                                onClick = { viewModel.setDateFilter(DateFilterMode.YEAR) },
                                label = { Text(Localization.get(Localization.Key.DATE_FILTER_YEAR, language), fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = dateFilter == DateFilterMode.CUSTOM,
                                onClick = { showCustomDateDialog = true },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = { Text(Localization.get(Localization.Key.DATE_FILTER_CUSTOM, language), fontSize = 11.sp) }
                            )
                        }

                        // Specific Order ID & Customer Filter Row (Collapsible)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showAdvancedFilters = !showAdvancedFilters },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showAdvancedFilters) {
                                        if (language == "bn") "নির্দিষ্ট ফিল্টার লুকান ▲" else "Hide Detailed Filters ▲"
                                    } else {
                                        if (language == "bn") "অর্ডার বা কাস্টমার নির্দিষ্ট ফিল্টার ▼" else "Order ID / Customer Filters ▼"
                                    },
                                    fontSize = 11.sp
                                )
                            }

                            if (historyQuery.isNotBlank() || historyOrderIdFilter.isNotBlank() || historyCustomerFilter.isNotBlank() || dateFilter != DateFilterMode.ALL) {
                                TextButton(
                                    onClick = { viewModel.clearAllFilters() },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = Localization.get(Localization.Key.FILTER_CLEAR_ALL, language),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = showAdvancedFilters) {
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = historyOrderIdFilter,
                                        onValueChange = { viewModel.setOrderIdFilter(it) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("filter_order_id_input"),
                                        placeholder = { Text("Filter Order ID (#INV)", fontSize = 11.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    OutlinedTextField(
                                        value = historyCustomerFilter,
                                        onValueChange = { viewModel.setCustomerFilter(it) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("filter_customer_input"),
                                        placeholder = { Text("Customer / Phone", fontSize = 11.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // XLSX Branch Synchronization (Offline Multi-Branch Exchange)
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("xlsx_branch_sync_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Localization.string(Localization.Key.XLSX_SYNC_TITLE, language),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isImporting || isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = Localization.string(Localization.Key.XLSX_SYNC_DESC, language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                                    exportFileLauncher.launch("POS_Sales_Export_$timeStamp.xlsx")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_export_xlsx"),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isExporting && !isImporting
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = Localization.string(Localization.Key.BTN_EXPORT_EXCEL, language),
                                    fontSize = 12.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    importFileLauncher.launch(
                                        arrayOf(
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/octet-stream",
                                            "*/*"
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_import_xlsx"),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isExporting && !isImporting
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = Localization.string(Localization.Key.BTN_IMPORT_EXCEL, language),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Low Database Capacity Warning (Phase 6)
            if (remainingCapacity <= 1000) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("history_capacity_warning_card"),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = Localization.get(Localization.Key.DB_CAPACITY_WARNING_TITLE, language),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = Localization.get(Localization.Key.DB_CAPACITY_WARNING_MSG, language),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Database Aggregation KPI Summary Cards
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KpiSummaryCard(
                        title = Localization.get(Localization.Key.KPI_TOTAL_ORDERS, language),
                        value = "$aggregatedOrders",
                        modifier = Modifier.weight(1f),
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    KpiSummaryCard(
                        title = Localization.get(Localization.Key.KPI_TOTAL_SALES, language),
                        value = "${FinancialCalculationEngine.formatPaisaToBdt(aggregatedRawPaisa)} ৳",
                        modifier = Modifier.weight(1.3f),
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                    KpiSummaryCard(
                        title = Localization.get(Localization.Key.KPI_TOTAL_ROUNDED, language),
                        value = "${FinancialCalculationEngine.formatWholeBdtToDisplay(aggregatedRoundedBdt)} ৳",
                        modifier = Modifier.weight(1.3f),
                        accentColor = Color(0xFF2E7D32)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Average Ticket & Rounding Net Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val avgTicket = if (aggregatedOrders > 0) aggregatedRoundedBdt / aggregatedOrders else 0L
                    val roundingDiffPaisa = (aggregatedRoundedBdt * 100L) - aggregatedRawPaisa
                    KpiSummaryCard(
                        title = Localization.get(Localization.Key.AVG_ORDER_VALUE, language),
                        value = "${FinancialCalculationEngine.formatWholeBdtToDisplay(avgTicket)} ৳",
                        modifier = Modifier.weight(1f),
                        accentColor = MaterialTheme.colorScheme.tertiary
                    )
                    KpiSummaryCard(
                        title = Localization.get(Localization.Key.KPI_ROUNDING_DIFF, language),
                        value = "${if (roundingDiffPaisa >= 0) "+" else ""}${FinancialCalculationEngine.formatPaisaToBdt(roundingDiffPaisa)} ৳",
                        modifier = Modifier.weight(1f),
                        accentColor = if (roundingDiffPaisa >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }

            // Top Selling Products database-aggregated report
            if (topSellingItems.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Localization.get(Localization.Key.TOP_SELLING_ITEMS, language),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            topSellingItems.forEachIndexed { index, summary ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1.5f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${index + 1}.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = summary.itemName,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${summary.orderCount} orders",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${FinancialCalculationEngine.formatPaisaToBdt(summary.totalRevenuePaisa)} ৳",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (index < topSellingItems.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Header for transactions list with pagination info
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val startRecord = if (totalCount == 0) 0 else (currentPage * pageSize + 1)
                    val endRecord = min(totalCount, (currentPage + 1) * pageSize)
                    Column {
                        Text(
                            text = "${Localization.get(Localization.Key.HISTORY_TITLE, language)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (totalCount > 0) {
                                "Showing $startRecord-$endRecord of $totalCount"
                            } else {
                                "0 records"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Page Size Selector
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Size: ", style = MaterialTheme.typography.labelSmall)
                        listOf(25, 50, 100).forEach { size ->
                            FilterChip(
                                selected = pageSize == size,
                                onClick = { viewModel.setPageSize(size) },
                                label = { Text("$size", fontSize = 11.sp) },
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .height(32.dp)
                            )
                        }
                    }
                }
            }

            // Invoices Paged List
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }
            } else if (invoices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = Localization.get(Localization.Key.NO_RECORDS_FOUND, language),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (historyQuery.isNotBlank() || historyOrderIdFilter.isNotBlank() || historyCustomerFilter.isNotBlank() || dateFilter != DateFilterMode.ALL) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(onClick = { viewModel.clearAllFilters() }) {
                                    Text(Localization.get(Localization.Key.FILTER_CLEAR_ALL, language))
                                }
                            }
                        }
                    }
                }
            } else {
                items(invoices, key = { it.orderId }) { invoice ->
                    val isExpanded = expandedOrderId == invoice.orderId
                    val cachedLines = orderLinesCache[invoice.orderId] ?: if (isExpanded) expandedLines else emptyList()

                    HistoryInvoiceCard(
                        invoice = invoice,
                        isExpanded = isExpanded,
                        lines = cachedLines,
                        language = language,
                        onToggleExpand = { viewModel.toggleOrderExpansion(invoice.orderId) },
                        onViewReceipt = { viewModel.openReceiptPopup(invoice) },
                        onSharePdf = { viewModel.generateAndShareInvoicePdf(context, invoice, cachedLines) },
                        onDelete = { invoiceToDelete = invoice },
                        onEdit = { invoiceToEdit = invoice }
                    )
                }

                // Pagination Navigation Controls
                item {
                    val totalPages = if (totalCount == 0) 1 else ((totalCount - 1) / pageSize) + 1
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.prevPage() },
                                enabled = currentPage > 0,
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("pagination_prev_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Page",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Localization.get(Localization.Key.BTN_PREV_PAGE, language), fontSize = 11.sp)
                            }

                            Text(
                                text = "${Localization.get(Localization.Key.PAGE_LABEL, language)} ${currentPage + 1} / $totalPages",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            OutlinedButton(
                                onClick = { viewModel.nextPage() },
                                enabled = (currentPage + 1) * pageSize < totalCount,
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("pagination_next_btn")
                            ) {
                                Text(Localization.get(Localization.Key.BTN_NEXT_PAGE, language), fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Page",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                item {
                    AnalyticsDashboardView(
                        language = language,
                        dateFilter = dateFilter,
                        analyticsGrouping = analyticsGrouping,
                        analyticsMetric = analyticsMetric,
                        analyticsDataPoints = analyticsDataPoints,
                        chartDataPoints = chartDataPoints,
                        aggregatedOrders = aggregatedOrders,
                        aggregatedRawPaisa = aggregatedRawPaisa,
                        aggregatedRoundedBdt = aggregatedRoundedBdt,
                        topSellingItems = topSellingItems,
                        onSelectDateFilter = { viewModel.setDateFilter(it) },
                        onOpenCustomDateDialog = { showCustomDateDialog = true },
                        onSelectGrouping = { viewModel.setAnalyticsGrouping(it) },
                        onSelectMetric = { viewModel.setAnalyticsMetric(it) }
                    )
                }
            }
        }
    }

    // Popup Dialog for Instant Receipt View & Print
    if (isReceiptPopupOpen && selectedInvoiceForReceipt != null) {
        ReceiptDetailsDialog(
            invoice = selectedInvoiceForReceipt!!,
            lines = receiptPopupLines,
            receiptText = receiptPopupText,
            language = language,
            logoUri = companyHeader?.logoUri,
            onClose = { viewModel.closeReceiptPopup() },
            onReprint = { viewModel.reprintInvoice(selectedInvoiceForReceipt!!) },
            onSharePdf = {
                viewModel.generateAndShareInvoicePdf(
                    context = context,
                    invoice = selectedInvoiceForReceipt!!,
                    lines = receiptPopupLines
                )
            },
            onDelete = {
                val toDel = selectedInvoiceForReceipt!!
                viewModel.closeReceiptPopup()
                invoiceToDelete = toDel
            }
        )
    }

    // Safe Edit Dialog for Invoice Details
    if (invoiceToEdit != null) {
        val target = invoiceToEdit!!
        EditInvoiceDialog(
            invoice = target,
            language = language,
            onDismiss = { invoiceToEdit = null },
            onSave = { updatedCustomer, updatedNote ->
                viewModel.updateInvoiceDetails(target.orderId, updatedCustomer, updatedNote)
                invoiceToEdit = null
            }
        )
    }

    // Safe Delete Confirmation Dialog
    if (invoiceToDelete != null) {
        val target = invoiceToDelete!!
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingInvoice) {
                    invoiceToDelete = null
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = Localization.get(Localization.Key.CONFIRM_DELETE_TRANSACTION_TITLE, language),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                val isBn = language == "bn"
                Text(
                    text = if (isBn) {
                        "আপনি কি নিশ্চিত যে ভাউচার #${target.orderId} মুছে ফেলতে চান?\nএই পরিবর্তনটি আর পূর্বাবস্থায় ফিরিয়ে আনা যাবে না।"
                    } else {
                        "Are you sure you want to delete voucher #${target.orderId}?\nThis action cannot be undone."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInvoice(target.orderId) {
                            invoiceToDelete = null
                        }
                    },
                    enabled = !isDeletingInvoice,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_btn")
                ) {
                    Text(Localization.get(Localization.Key.BTN_DELETE_TRANSACTION, language))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { invoiceToDelete = null },
                    enabled = !isDeletingInvoice
                ) {
                    Text(Localization.get(Localization.Key.CANCEL, language))
                }
            }
        )
    }

    // Custom Date Range Dialog
    if (showCustomDateDialog) {
        CustomDateRangeDialog(
            language = language,
            onDismiss = { showCustomDateDialog = false },
            onApply = { startMs, endMs ->
                viewModel.setCustomDateRange(startMs, endMs)
                showCustomDateDialog = false
            }
        )
    }

    // XLSX Sync & Import Summary Dialog
    importSummary?.let { summary ->
        ImportSummaryDialog(
            summary = summary,
            language = language,
            onDismiss = { viewModel.clearImportSummary() }
        )
    }
}

@Composable
fun KpiSummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color
) {
    Card(
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HistoryInvoiceCard(
    invoice: InvoiceEntity,
    isExpanded: Boolean,
    lines: List<InvoiceLineEntity>,
    language: String,
    onToggleExpand: () -> Unit,
    onViewReceipt: () -> Unit,
    onSharePdf: (() -> Unit)? = null,
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }
    val formattedDate = remember(invoice.dateTime) { dateFormat.format(Date(invoice.dateTime)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onToggleExpand() }
            .testTag("invoice_card_${invoice.orderId}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Order ID, Branch, Date & Time, Rounded Total Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "#${invoice.orderId}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Branch: ${invoice.branchCode}",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        )
                    }
                }

                // Rounded Total display prominently
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${FinancialCalculationEngine.formatWholeBdtToDisplay(invoice.roundedTotalBdt)} ৳",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = "Raw: ${FinancialCalculationEngine.formatPaisaToBdt(invoice.rawTotalPaisa)} ৳",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Sub-row: Customer info & Date Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1.5f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (invoice.customerInfo.isNotBlank()) invoice.customerInfo else "(Walk-in Customer)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action row: Items count, Operator & Receipt Popup Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Op: ${invoice.operatorName}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (lines.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${lines.size} ${Localization.get(Localization.Key.ITEMS_COUNT, language)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                // Actions: Details, PDF & Delete Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Popup Details Button
                    OutlinedButton(
                        onClick = { onViewReceipt() },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("view_receipt_${invoice.orderId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(Localization.get(Localization.Key.VIEW_RECEIPT_POPUP, language), fontSize = 11.sp)
                    }

                    // Direct PDF Share Button
                    if (onSharePdf != null) {
                        OutlinedButton(
                            onClick = { onSharePdf() },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("card_share_pdf_${invoice.orderId}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "PDF",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("PDF", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Update / Safe Edit Button
                    OutlinedButton(
                        onClick = { onEdit() },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("edit_invoice_${invoice.orderId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Update",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(Localization.get(Localization.Key.BTN_UPDATE_INVOICE, language), fontSize = 11.sp)
                    }

                    // Delete Button
                    OutlinedButton(
                        onClick = { onDelete() },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("delete_invoice_${invoice.orderId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(Localization.get(Localization.Key.BTN_DELETE_TRANSACTION, language), fontSize = 11.sp)
                    }
                }
            }

            // Expanded Inline Details
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = Localization.get(Localization.Key.INLINE_ITEMS_DETAILS, language),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (lines.isEmpty()) {
                        Text(
                            text = "Loading items...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    } else {
                        lines.forEach { line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text(
                                        text = line.itemName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "${line.quantityStr} ${line.unit} @ ${FinancialCalculationEngine.formatPaisaToBdt(line.unitPricePaisa)} ৳",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${FinancialCalculationEngine.formatPaisaToBdt(line.lineTotalPaisa)} ৳",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Raw Subtotal: ${FinancialCalculationEngine.formatPaisaToBdt(invoice.rawTotalPaisa)} ৳",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Rounded Payable: ${FinancialCalculationEngine.formatWholeBdtToDisplay(invoice.roundedTotalBdt)} ৳",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val roundingDiff = (invoice.roundedTotalBdt * 100L) - invoice.rawTotalPaisa
                    Text(
                        text = "Rounding Adjustment: ${if (roundingDiff >= 0) "+" else ""}${FinancialCalculationEngine.formatPaisaToBdt(roundingDiff)} ৳",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Global Transaction ID: ${invoice.globalTransactionId}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun ReceiptDetailsDialog(
    invoice: InvoiceEntity,
    lines: List<InvoiceLineEntity>,
    receiptText: String,
    language: String,
    logoUri: String? = null,
    onClose: () -> Unit,
    onReprint: () -> Unit,
    onSharePdf: (() -> Unit)? = null,
    onDelete: () -> Unit = {}
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val invoiceTitle = if (language == "bn") "ইনভয়েস #${invoice.orderId}" else "Invoice #${invoice.orderId}"
                        val walkInLabel = if (language == "bn") Localization.get(Localization.Key.VOUCHER_WALK_IN, "bn") else Localization.get(Localization.Key.VOUCHER_WALK_IN, "en")
                        val customerLabel = if (language == "bn") Localization.get(Localization.Key.VOUCHER_CUSTOMER, "bn") else Localization.get(Localization.Key.VOUCHER_CUSTOMER, "en")
                        val customerVal = if (invoice.customerInfo.isNotBlank()) invoice.customerInfo else walkInLabel
                        Text(
                            text = invoiceTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$customerLabel: $customerVal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Scrollable Slip Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val density = LocalDensity.current
                            val logoSizeDp = with(density) { 100.toDp() }
                            // Official canonical logo at top of voucher
                            Box(
                                modifier = Modifier
                                    .size(logoSizeDp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CompanyLogoView(
                                    logoUri = logoUri,
                                    modifier = Modifier.fillMaxSize(),
                                    fallback = {
                                        Icon(
                                            imageVector = Icons.Default.Business,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val is80mm = (receiptText.lines().firstOrNull()?.length ?: 32) >= 40
                            val receiptFontSize = if (is80mm) 9.5.sp else 11.sp
                            val receiptLineHeight = if (is80mm) 13.5.sp else 15.sp

                            if (receiptText.isBlank()) {
                                Text(
                                    text = if (language == "bn") "ভাউচার লোড হচ্ছে..." else "Loading receipt text...",
                                    modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = receiptFontSize,
                                    lineHeight = receiptLineHeight,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                ReceiptFormattedBody(
                                    bodyText = receiptText,
                                    is80mm = is80mm,
                                    receiptFontSize = receiptFontSize,
                                    receiptLineHeight = receiptLineHeight,
                                    textColor = MaterialTheme.colorScheme.onSurface,
                                    separatorColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    explicitLines = lines,
                                    language = language
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions: Arranged VERTICALLY (Delete Voucher & PDF)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("dialog_delete_invoice_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Localization.get(Localization.Key.BTN_DELETE_TRANSACTION, language))
                    }

                    if (onSharePdf != null) {
                        OutlinedButton(
                            onClick = onSharePdf,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("dialog_share_pdf_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "PDF",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (language == "bn") "পিডিএফ" else "PDF")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomDateRangeDialog(
    language: String,
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit
) {
    var fromDateText by remember { mutableStateOf("") }
    var toDateText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = Localization.get(Localization.Key.SELECT_CUSTOM_DATE_RANGE, language),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                Text(
                    text = Localization.get(Localization.Key.ENTER_DATE_RANGE_HELP, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = fromDateText,
                    onValueChange = {
                        fromDateText = it
                        errorMessage = null
                    },
                    label = { Text(Localization.get(Localization.Key.CUSTOM_DATE_FROM, language), fontSize = 11.sp) },
                    placeholder = { Text("dd-MM-yyyy (e.g. 18-11-2025)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_date_from_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = toDateText,
                    onValueChange = {
                        toDateText = it
                        errorMessage = null
                    },
                    label = { Text(Localization.get(Localization.Key.CUSTOM_DATE_TO, language), fontSize = 11.sp) },
                    placeholder = { Text("dd-MM-yyyy (e.g. 18-11-2025)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_date_to_input")
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("custom_date_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedFrom = DateTimeUtils.parseDateDdMmYyyy(fromDateText)
                    val parsedTo = DateTimeUtils.parseDateDdMmYyyy(toDateText)
                    if (parsedFrom == null || parsedTo == null) {
                        errorMessage = Localization.get(Localization.Key.INVALID_DATE_FORMAT, language)
                        return@Button
                    }
                    val calFrom = Calendar.getInstance().apply {
                        time = parsedFrom
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val calTo = Calendar.getInstance().apply {
                        time = parsedTo
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    if (calFrom.timeInMillis > calTo.timeInMillis) {
                        errorMessage = Localization.get(Localization.Key.DATE_RANGE_ORDER_ERROR, language)
                        return@Button
                    }
                    onApply(calFrom.timeInMillis, calTo.timeInMillis)
                },
                modifier = Modifier.testTag("apply_custom_date_btn")
            ) {
                Text(Localization.get(Localization.Key.BTN_APPLY, language))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Localization.get(Localization.Key.CANCEL, language))
            }
        }
    )
}

@Composable
fun EditInvoiceDialog(
    invoice: InvoiceEntity,
    language: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var customerText by remember { mutableStateOf(invoice.customerInfo) }
    var noteText by remember { mutableStateOf(invoice.footerNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${Localization.get(Localization.Key.EDIT_INVOICE_TITLE, language)} #${invoice.orderId}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = Localization.get(Localization.Key.EDIT_INVOICE_READONLY_NOTICE, language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Total: ৳${invoice.roundedTotalBdt}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = customerText,
                    onValueChange = { customerText = it },
                    label = { Text(Localization.get(Localization.Key.EDIT_INVOICE_CUSTOMER_LABEL, language), fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_invoice_customer_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(Localization.get(Localization.Key.EDIT_INVOICE_NOTE_LABEL, language), fontSize = 11.sp) },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_invoice_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(customerText, noteText) },
                modifier = Modifier.testTag("save_invoice_edit_btn")
            ) {
                Text(Localization.get(Localization.Key.SAVE_CHANGES, language))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Localization.get(Localization.Key.CANCEL, language))
            }
        }
    )
}
