package com.example.pos.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontFamily
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.printer.BluetoothDeviceItem
import com.example.pos.printer.BluetoothPrinterManager
import com.example.pos.printer.PrinterConnectionState
import com.example.pos.printer.ThermalReceiptFormatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.pos.data.Localization
import com.example.pos.ui.components.CompanyLogoView
import com.example.pos.ui.components.ImportSummaryDialog
import com.example.pos.ui.components.TopStationHeader
import com.example.pos.ui.viewmodel.PosViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: PosViewModel,
    modifier: Modifier = Modifier
) {
    val language by viewModel.language.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val companyHeader by viewModel.companyHeader.collectAsState()
    val activeOperator by viewModel.activeOperator.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val pairedPrinters by viewModel.pairedPrinters.collectAsState()
    val printerConnectionState by viewModel.printerConnectionState.collectAsState()
    val pairedPrinterDevices by viewModel.pairedPrinterDevices.collectAsState()
    val discoveredPrinterDevices by viewModel.discoveredPrinterDevices.collectAsState()
    val isBluetoothScanning by viewModel.isBluetoothScanning.collectAsState()

    val totalInvoiceCount by viewModel.totalInvoiceCount.collectAsState()
    val totalInvoiceLinesCount by viewModel.totalInvoiceLinesCount.collectAsState()
    val totalProductCount by viewModel.totalProductCount.collectAsState()
    val totalCustomerCount by viewModel.totalCustomerCount.collectAsState()
    val databaseSizeBytes by viewModel.databaseSizeBytes.collectAsState()
    val supportedCapacity = viewModel.supportedCapacity

    val importSummary by viewModel.importSummary.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            viewModel.exportSalesToUri(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importSalesFromUri(uri)
        }
    }

    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val tabTitles = listOf(
        Localization.get(Localization.Key.TAB_COMPANY_HEADER, language),
        Localization.get(Localization.Key.TAB_USER_PROFILE, language),
        Localization.get(Localization.Key.TAB_FOOTER_NOTE, language),
        Localization.get(Localization.Key.TAB_LANGUAGE, language),
        Localization.get(Localization.Key.TAB_THEME, language),
        Localization.get(Localization.Key.TAB_PRINTER, language),
        Localization.get(Localization.Key.TAB_DATABASE_STATUS, language)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        // Top Header
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

        // 6 Tabs as specified in PRD Section 5.7
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal),
                            maxLines = 1
                        )
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            when (selectedTabIndex) {
                0 -> item {
                    CompanyHeaderSettingsTab(
                        company = companyHeader,
                        language = language,
                        onSave = { name, branch, num, phone, address ->
                            viewModel.updateCompanyHeader(name, branch, num, phone, address)
                        },
                        onSelectLogoUri = { uri -> viewModel.setCompanyLogoUri(uri) },
                        onUseOfficialLogo = { viewModel.useOfficialDefaultLogo() },
                        onRemoveLogo = { viewModel.removeCompanyLogo() }
                    )
                }
                1 -> item {
                    UserProfileSettingsTab(
                        operator = activeOperator,
                        language = language,
                        onSave = { name, phone, address ->
                            viewModel.updateOperatorProfile(name, phone, address)
                        },
                        onLogout = { viewModel.logout() }
                    )
                }
                2 -> item {
                    FooterNoteSettingsTab(
                        line1 = appSettings?.footerNoteLine1 ?: "",
                        line2 = appSettings?.footerNoteLine2 ?: "",
                        language = language,
                        onSave = { l1, l2 -> viewModel.updateFooterNotes(l1, l2) }
                    )
                }
                3 -> item {
                    LanguageSettingsTab(
                        currentLang = language,
                        language = language,
                        onSelectLang = { viewModel.setLanguage(it) }
                    )
                }
                4 -> item {
                    ThemeSettingsTab(
                        currentTheme = themeMode,
                        language = language,
                        onSelectTheme = { viewModel.setThemeMode(it) }
                    )
                }
                5 -> item {
                    PrinterSettingsTab(
                        paperSize = appSettings?.paperSize ?: "58mm",
                        autoCutter = appSettings?.autoCutterEnabled ?: true,
                        selectedAddress = appSettings?.bluetoothPrinterAddress,
                        selectedName = appSettings?.bluetoothPrinterName,
                        connectionState = printerConnectionState,
                        pairedDevices = pairedPrinterDevices,
                        discoveredDevices = discoveredPrinterDevices,
                        isScanning = isBluetoothScanning,
                        isBluetoothEnabled = viewModel.isBluetoothEnabled,
                        language = language,
                        onSelectPaperSize = { viewModel.setPaperSize(it) },
                        onToggleAutoCutter = { viewModel.setAutoCutter(it) },
                        onSelectPrinter = { addr, name -> viewModel.setBluetoothPrinter(addr, name) },
                        onConnectPrinter = { addr, name -> viewModel.connectPrinter(addr, name) },
                        onDisconnectPrinter = { viewModel.disconnectPrinter() },
                        onStartScan = { viewModel.startBluetoothScanning() },
                        onStopScan = { viewModel.stopBluetoothScanning() },
                        onRefreshPrinters = { viewModel.refreshPairedPrinters() },
                        onTestPrint = { viewModel.testPrint() },
                        companyHeader = companyHeader
                    )
                }
                6 -> item {
                    DatabaseStatusSettingsTab(
                        totalInvoices = totalInvoiceCount,
                        totalLines = totalInvoiceLinesCount,
                        totalProducts = totalProductCount,
                        totalCustomers = totalCustomerCount,
                        databaseSizeBytes = databaseSizeBytes,
                        supportedCapacity = supportedCapacity,
                        language = language,
                        isExporting = isExporting,
                        isImporting = isImporting,
                        onRefreshSize = { viewModel.refreshDatabaseSize() },
                        onExportExcelFile = { range ->
                            viewModel.setExportDateRange(range)
                            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                            exportLauncher.launch("POS_Sales_Export_$timeStamp.xlsx")
                        },
                        onImportExcel = {
                            importLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        onBackupExcel = { range ->
                            viewModel.exportSalesToExcel(customRange = range) { file ->
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Backup Sales Report"))
                                } catch (e: Exception) {
                                    // Fallback if share chooser encounters any issue
                                }
                            }
                        }
                    )
                }
            }
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
}

@Composable
fun CompanyHeaderSettingsTab(
    company: com.example.pos.data.db.CompanyHeaderEntity?,
    language: String,
    onSave: (name: String, branch: String, num: String, phone: String, address: String) -> Unit,
    onSelectLogoUri: (Uri) -> Unit,
    onUseOfficialLogo: () -> Unit,
    onRemoveLogo: () -> Unit
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onSelectLogoUri(uri)
        }
    }

    var name by remember(company) { mutableStateOf(company?.companyName ?: "") }
    var branch by remember(company) { mutableStateOf(company?.branchName ?: "") }
    var num by remember(company) { mutableStateOf(company?.branchNumber ?: "") }
    var phone by remember(company) { mutableStateOf(company?.phoneNumber ?: "") }
    var address by remember(company) { mutableStateOf(company?.address ?: "") }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section 1: Company Logo & Branding Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("company_logo_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Landscape,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Localization.get(Localization.Key.COMPANY_LOGO, language),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    if (!company?.logoUri.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = Localization.get(Localization.Key.ACTIVE_BADGE, language),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Logo Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF9F9FB))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CompanyLogoView(
                            logoUri = company?.logoUri,
                            modifier = Modifier
                                .size(100.dp)
                                .testTag("logo_preview_image"),
                            fallback = {
                                Text(
                                    text = Localization.get(Localization.Key.NO_LOGO_CONFIGURED, language),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons: Add / Change / Remove / Default
                if (!company?.logoUri.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_change_logo"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Localization.get(Localization.Key.CHANGE_LOGO, language), fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onRemoveLogo,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_remove_logo"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Localization.get(Localization.Key.REMOVE_LOGO, language), fontSize = 12.sp)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("btn_add_logo"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Localization.get(Localization.Key.ADD_LOGO, language), fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onUseOfficialLogo,
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("btn_use_official_logo"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Localization.get(Localization.Key.USE_OFFICIAL_LOGO, language), fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 2: Company Header Information Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Localization.get(Localization.Key.TAB_COMPANY_HEADER, language),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(Localization.get(Localization.Key.COMPANY_NAME, language), fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = branch,
                        onValueChange = { branch = it },
                        label = { Text(Localization.get(Localization.Key.BRANCH_NAME, language), fontSize = 12.sp) },
                        modifier = Modifier.weight(1.5f)
                    )
                    OutlinedTextField(
                        value = num,
                        onValueChange = { num = it },
                        label = { Text(Localization.get(Localization.Key.BRANCH_NUMBER, language), fontSize = 12.sp) },
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(Localization.get(Localization.Key.COMPANY_PHONE, language), fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(Localization.get(Localization.Key.COMPANY_ADDRESS, language), fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onSave(name, branch, num, phone, address) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(Localization.get(Localization.Key.BTN_SAVE, language))
                }
            }
        }
    }
}

@Composable
fun UserProfileSettingsTab(
    operator: com.example.pos.data.db.OperatorEntity?,
    language: String,
    onSave: (name: String, phone: String, address: String) -> Unit,
    onLogout: () -> Unit
) {
    var name by remember(operator) { mutableStateOf(operator?.operatorName ?: "") }
    var phone by remember(operator) { mutableStateOf(operator?.operatorPhone ?: "") }
    var address by remember(operator) { mutableStateOf(operator?.operatorAddress ?: "") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Localization.get(Localization.Key.TAB_USER_PROFILE, language),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                if (operator != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = operator.userId,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(Localization.get(Localization.Key.FULL_NAME, language), fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(Localization.get(Localization.Key.PHONE_NUMBER, language), fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(Localization.get(Localization.Key.ADDRESS, language), fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onSave(name, phone, address) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(Localization.get(Localization.Key.BTN_SAVE, language))
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(Localization.get(Localization.Key.LOGOUT, language))
            }
        }
    }
}

@Composable
fun FooterNoteSettingsTab(
    line1: String,
    line2: String,
    language: String,
    onSave: (l1: String, l2: String) -> Unit
) {
    val initialCombined = remember(line1, line2) {
        listOf(line1, line2).filter { it.isNotBlank() }.joinToString("\n")
    }
    var multiLineText by remember(line1, line2) { mutableStateOf(initialCombined) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Localization.get(Localization.Key.TAB_FOOTER_NOTE, language),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = Localization.get(Localization.Key.FOOTER_NOTE_HELP, language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Multi-line editor
            OutlinedTextField(
                value = multiLineText,
                onValueChange = { multiLineText = it },
                label = { Text(Localization.get(Localization.Key.TAB_FOOTER_NOTE, language), fontSize = 12.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("footer_note_multiline_input"),
                minLines = 3,
                maxLines = 8
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Example Button
            OutlinedButton(
                onClick = {
                    multiLineText = if (language == "bn") {
                        "নিখুঁত মানের নিশ্চয়তা\nধন্যবাদ আবার আসবেন"
                    } else {
                        "Guaranteed Quality Assurance\nThank you, please visit again!"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_load_example_footer"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = Localization.get(Localization.Key.BTN_LOAD_EXAMPLE_FOOTER, language),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Real-time Thermal Receipt Preview
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = Localization.get(Localization.Key.FOOTER_NOTE_PREVIEW_TITLE, language),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "--------------------------------",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.outline
                    )
                    val lines = multiLineText.lines().filter { it.isNotBlank() }
                    if (lines.isEmpty()) {
                        Text(
                            text = "(No footer note)",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        lines.forEach { line ->
                            Text(
                                text = line.trim(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Text(
                        text = "--------------------------------",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val rawLines = multiLineText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    val l1 = rawLines.firstOrNull() ?: ""
                    val l2 = rawLines.drop(1).joinToString("\n")
                    onSave(l1, l2)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_footer_notes_btn"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(Localization.get(Localization.Key.BTN_SAVE, language))
            }
        }
    }
}

@Composable
fun LanguageSettingsTab(
    currentLang: String,
    language: String,
    onSelectLang: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Localization.get(Localization.Key.TAB_LANGUAGE, language),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectLang("bn") }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentLang == "bn",
                    onClick = { onSelectLang("bn") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = Localization.get(Localization.Key.LANGUAGE_BANGLA_TITLE, language),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = Localization.get(Localization.Key.LANGUAGE_BANGLA_DESC, language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectLang("en") }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentLang == "en",
                    onClick = { onSelectLang("en") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = Localization.get(Localization.Key.LANGUAGE_ENGLISH_TITLE, language),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = Localization.get(Localization.Key.LANGUAGE_ENGLISH_DESC, language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeSettingsTab(
    currentTheme: String,
    language: String,
    onSelectTheme: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Localization.get(Localization.Key.TAB_THEME, language),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectTheme("LIGHT") }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = currentTheme == "LIGHT", onClick = { onSelectTheme("LIGHT") })
                Spacer(modifier = Modifier.width(8.dp))
                Text(Localization.get(Localization.Key.THEME_LIGHT, language), style = MaterialTheme.typography.bodyLarge)
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectTheme("DARK") }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = currentTheme == "DARK", onClick = { onSelectTheme("DARK") })
                Spacer(modifier = Modifier.width(8.dp))
                Text(Localization.get(Localization.Key.THEME_DARK, language), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun PrinterSettingsTab(
    paperSize: String,
    autoCutter: Boolean,
    selectedAddress: String?,
    selectedName: String?,
    connectionState: PrinterConnectionState,
    pairedDevices: List<BluetoothDeviceItem>,
    discoveredDevices: List<BluetoothDeviceItem>,
    isScanning: Boolean,
    isBluetoothEnabled: Boolean,
    language: String,
    onSelectPaperSize: (String) -> Unit,
    onToggleAutoCutter: (Boolean) -> Unit,
    onSelectPrinter: (address: String?, name: String?) -> Unit,
    onConnectPrinter: (address: String, name: String?) -> Unit,
    onDisconnectPrinter: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onRefreshPrinters: () -> Unit,
    onTestPrint: () -> Unit,
    companyHeader: CompanyHeaderEntity? = null
) {
    val context = LocalContext.current
    val isBn = language == "bn"
    var showPreviewDialog by remember { mutableStateOf(false) }

    // Modern Android Bluetooth Permission Handling
    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    var hasPermissions by remember {
        mutableStateOf(BluetoothPrinterManager.hasRequiredPermissions(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
        if (hasPermissions) {
            onRefreshPrinters()
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshPrinters()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. BLUETOOTH DISABLED WARNING BANNER ---
        if (!isBluetoothEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.BluetoothDisabled,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Localization.get(Localization.Key.BLUETOOTH_OFF_TITLE, language),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = Localization.get(Localization.Key.BLUETOOTH_OFF_DESC, language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                enableBluetoothLauncher.launch(intent)
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(Localization.get(Localization.Key.ENABLE_BLUETOOTH, language))
                    }
                }
            }
        }

        // --- 2. PERMISSION BANNER (IF NOT GRANTED) ---
        if (!hasPermissions) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Localization.get(Localization.Key.PERMISSION_REQUIRED_TITLE, language),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = Localization.get(Localization.Key.PERMISSION_REQUIRED_DESC, language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { permissionLauncher.launch(permissionsToRequest) }
                    ) {
                        Text(Localization.get(Localization.Key.GRANT_PERMISSION, language))
                    }
                }
            }
        }

        // --- 3. HARDWARE CONNECTION STATUS CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (connectionState) {
                    is PrinterConnectionState.Connected -> MaterialTheme.colorScheme.primaryContainer
                    is PrinterConnectionState.Connecting -> MaterialTheme.colorScheme.secondaryContainer
                    is PrinterConnectionState.Printing -> MaterialTheme.colorScheme.surfaceVariant
                    is PrinterConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
                    is PrinterConnectionState.Disconnected -> MaterialTheme.colorScheme.surface
                }
            ),
            border = if (connectionState is PrinterConnectionState.Disconnected) {
                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            } else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (connectionState) {
                    is PrinterConnectionState.Connected -> {
                        Icon(
                            Icons.Default.BluetoothConnected,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = Localization.get(Localization.Key.PRINTER_CONNECTED_STATUS, language),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "ESC/POS",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = connectionState.deviceName.ifBlank { connectionState.address },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = connectionState.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onDisconnectPrinter,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(Localization.get(Localization.Key.BTN_DISCONNECT, language))
                        }
                    }
                    is PrinterConnectionState.Connecting -> {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Localization.get(Localization.Key.PRINTER_CONNECTING, language),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = connectionState.deviceName.ifBlank { connectionState.address },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        OutlinedButton(onClick = onDisconnectPrinter) {
                            Text(Localization.get(Localization.Key.CANCEL, language))
                        }
                    }
                    is PrinterConnectionState.Printing -> {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Localization.get(Localization.Key.PRINTER_PRINTING, language),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = connectionState.step,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    is PrinterConnectionState.Error -> {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Localization.get(Localization.Key.PRINTER_ERROR, language),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = connectionState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        if (selectedAddress != null) {
                            Button(
                                onClick = { onConnectPrinter(selectedAddress, selectedName) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(if (isBn) "পুনরায় চেষ্টা" else "Retry")
                            }
                        }
                    }
                    is PrinterConnectionState.Disconnected -> {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Localization.get(Localization.Key.PRINTER_DISCONNECTED, language),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            if (selectedAddress != null) {
                                Text(
                                    text = "${selectedName ?: "Printer"} ($selectedAddress)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = Localization.get(Localization.Key.PRINTER_NOT_CONNECTED, language),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (selectedAddress != null) {
                            Button(
                                onClick = { onConnectPrinter(selectedAddress, selectedName) }
                            ) {
                                Text(Localization.get(Localization.Key.BTN_CONNECT, language))
                            }
                        }
                    }
                }
            }
        }

        // --- 4. PRINTER CONFIGURATION CARD (PAPER SIZE & CUTTER) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = Localization.get(Localization.Key.PAPER_SIZE_LABEL, language),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Paper Size Radio Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 58mm Option
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectPaperSize("58mm") },
                        shape = RoundedCornerShape(8.dp),
                        color = if (paperSize == "58mm") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (paperSize == "58mm") androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = paperSize == "58mm", onClick = { onSelectPaperSize("58mm") })
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Localization.get(Localization.Key.PAPER_58MM_TITLE, language), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            }
                            Text(
                                text = Localization.get(Localization.Key.PAPER_58MM_DESC, language),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = Localization.get(Localization.Key.PAPER_58MM_SUB, language),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    // 80mm Option
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectPaperSize("80mm") },
                        shape = RoundedCornerShape(8.dp),
                        color = if (paperSize == "80mm") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (paperSize == "80mm") androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = paperSize == "80mm", onClick = { onSelectPaperSize("80mm") })
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Localization.get(Localization.Key.PAPER_80MM_TITLE, language), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            }
                            Text(
                                text = Localization.get(Localization.Key.PAPER_80MM_DESC, language),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = Localization.get(Localization.Key.PAPER_80MM_SUB, language),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Auto-Cutter Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Localization.get(Localization.Key.AUTO_CUTTER_LABEL, language),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = Localization.get(Localization.Key.AUTO_CUTTER_DESC, language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoCutter, onCheckedChange = onToggleAutoCutter)
                }
            }
        }

        // --- 5. SEARCH & DEVICE DISCOVERY CONTROLS ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Localization.get(Localization.Key.BLUETOOTH_DEVICES, language),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onRefreshPrinters) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh paired")
                        }
                        if (isScanning) {
                            Button(
                                onClick = onStopScan,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Localization.get(Localization.Key.STOP_SCAN, language))
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (hasPermissions) {
                                        onStartScan()
                                    } else {
                                        permissionLauncher.launch(permissionsToRequest)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Localization.get(Localization.Key.SCAN_PRINTERS, language))
                            }
                        }
                    }
                }

                if (isScanning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Localization.get(Localization.Key.SCANNING_DEVICES, language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- PAIRED DEVICES LIST ---
                Text(
                    text = Localization.get(Localization.Key.PAIRED_PRINTERS_HEADER, language),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (pairedDevices.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = Localization.get(Localization.Key.PRINTER_NOT_CONNECTED, language),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    pairedDevices.forEach { device ->
                        val isSelected = selectedAddress == device.address
                        val isCurrentConnected = connectionState is PrinterConnectionState.Connected &&
                                connectionState.address == device.address

                        DeviceListItem(
                            name = device.name,
                            address = device.address,
                            isPrinter = device.isPrinter,
                            isBonded = true,
                            isSelected = isSelected,
                            isConnected = isCurrentConnected,
                            language = language,
                            onSelect = {
                                if (isSelected) onSelectPrinter(null, null)
                                else onSelectPrinter(device.address, device.name)
                            },
                            onConnect = { onConnectPrinter(device.address, device.name) },
                            onDisconnect = onDisconnectPrinter
                        )
                    }
                }

                // --- DISCOVERED NEARBY DEVICES LIST ---
                if (discoveredDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = Localization.get(Localization.Key.AVAILABLE_PRINTERS_HEADER, language),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    discoveredDevices.forEach { device ->
                        val isSelected = selectedAddress == device.address
                        val isCurrentConnected = connectionState is PrinterConnectionState.Connected &&
                                connectionState.address == device.address

                        DeviceListItem(
                            name = device.name,
                            address = device.address,
                            isPrinter = device.isPrinter,
                            isBonded = false,
                            isSelected = isSelected,
                            isConnected = isCurrentConnected,
                            language = language,
                            onSelect = {
                                onSelectPrinter(device.address, device.name)
                            },
                            onConnect = {
                                onSelectPrinter(device.address, device.name)
                                onConnectPrinter(device.address, device.name)
                            },
                            onDisconnect = onDisconnectPrinter
                        )
                    }
                }
            }
        }

        // --- 6. ACTION BUTTONS: TEST PRINT & PREVIEW RECEIPT ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { showPreviewDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("preview_receipt_btn")
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(Localization.get(Localization.Key.PREVIEW_RECEIPT, language))
            }

            Button(
                onClick = onTestPrint,
                modifier = Modifier
                    .weight(1f)
                    .testTag("test_print_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(Localization.get(Localization.Key.TEST_PRINT, language))
            }
        }
    }

    // --- RECEIPT PREVIEW DIALOG ---
    if (showPreviewDialog) {
        val sampleText = remember(paperSize, autoCutter, companyHeader) {
            val sampleInvoice = InvoiceEntity(
                orderId = "PREV-901",
                dateTime = System.currentTimeMillis(),
                operatorId = "OP-101",
                operatorName = "Manager",
                customerInfo = "01711223344",
                rawTotalPaisa = 14550,
                roundedTotalBdt = 146,
                footerNote = "Thank you for shopping!\nPlease visit again."
            )
            val sampleLines = listOf(
                InvoiceLineEntity(
                    orderId = "PREV-901",
                    itemId = "ITM-01",
                    itemName = if (isBn) "পোলাও চাল প্রিমিয়াম" else "Basmati Rice 1kg",
                    itemType = "KILO",
                    unit = "kg",
                    quantityStr = "2.0",
                    unitPricePaisa = 5000,
                    lineTotalPaisa = 10000
                ),
                InvoiceLineEntity(
                    orderId = "PREV-901",
                    itemId = "ITM-02",
                    itemName = if (isBn) "সরিষার তেল ৫০০ মিলি" else "Mustard Oil 500ml",
                    itemType = "PIECE",
                    unit = "pc",
                    quantityStr = "1.0",
                    unitPricePaisa = 4550,
                    lineTotalPaisa = 4550
                )
            )
            ThermalReceiptFormatter.generateReceiptText(
                header = companyHeader,
                invoice = sampleInvoice,
                lines = sampleLines,
                paperSize = paperSize,
                footerLine1 = "Thank you for shopping!",
                footerLine2 = "Please visit again."
            )
        }

        AlertDialog(
            onDismissRequest = { showPreviewDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${Localization.get(Localization.Key.PREVIEW_RECEIPT, language)} ($paperSize)")
                }
            },
            text = {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyColumn(modifier = Modifier.padding(12.dp)) {
                        item {
                            Text(
                                text = sampleText,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showPreviewDialog = false }) {
                    Text(Localization.get(Localization.Key.BTN_CLOSE, language))
                }
            }
        )
    }
}

@Composable
private fun DeviceListItem(
    name: String,
    address: String,
    isPrinter: Boolean,
    isBonded: Boolean,
    isSelected: Boolean,
    isConnected: Boolean,
    language: String,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isSelected, onClick = onSelect)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                if (isPrinter) Icons.Default.Print else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = if (isPrinter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    if (isPrinter) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "PRINTER",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (isConnected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = Localization.get(Localization.Key.PRINTER_CONNECTED, language),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Localization.get(Localization.Key.BTN_DISCONNECT, language))
                }
            } else {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(Localization.get(Localization.Key.BTN_CONNECT, language))
                }
            }
        }
    }
}

@Composable
fun DatabaseStatusSettingsTab(
    totalInvoices: Int,
    totalLines: Int,
    totalProducts: Int,
    totalCustomers: Int,
    databaseSizeBytes: Long,
    supportedCapacity: Int,
    language: String,
    isExporting: Boolean = false,
    isImporting: Boolean = false,
    onRefreshSize: () -> Unit,
    onExportExcelFile: (Pair<Long, Long>?) -> Unit,
    onImportExcel: () -> Unit,
    onBackupExcel: (Pair<Long, Long>?) -> Unit
) {
    val remainingCapacity = (supportedCapacity - totalInvoices).coerceAtLeast(0)
    val isNearCapacity = remainingCapacity <= 1000
    val usageRatio = (totalInvoices.toFloat() / supportedCapacity).coerceIn(0f, 1f)

    var exportFromDateText by remember { mutableStateOf("") }
    var exportToDateText by remember { mutableStateOf("") }
    var exportValidationError by remember { mutableStateOf<String?>(null) }
    var showFullExportConfirmAlert by remember { mutableStateOf(false) }

    val dbSizeFormatted = remember(databaseSizeBytes) {
        if (databaseSizeBytes <= 0) {
            "0 KB"
        } else if (databaseSizeBytes < 1024 * 1024) {
            String.format(java.util.Locale.US, "%.1f KB", databaseSizeBytes / 1024.0)
        } else {
            String.format(java.util.Locale.US, "%.2f MB", databaseSizeBytes / (1024.0 * 1024.0))
        }
    }

    fun validateAndExport(isBackupShare: Boolean = false) {
        val fromBlank = exportFromDateText.isBlank()
        val toBlank = exportToDateText.isBlank()

        if (fromBlank && toBlank) {
            // Full export requested -> ask confirmation
            exportValidationError = null
            showFullExportConfirmAlert = true
            return
        }

        if (fromBlank || toBlank) {
            exportValidationError = Localization.get(Localization.Key.EXPORT_DATE_RANGE_ERROR, language)
            return
        }

        val parsedFrom = parseDateDdMmYyyy(exportFromDateText)
        val parsedTo = parseDateDdMmYyyy(exportToDateText)

        if (parsedFrom == null || parsedTo == null) {
            exportValidationError = Localization.get(Localization.Key.INVALID_DATE_FORMAT, language)
            return
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
            exportValidationError = Localization.get(Localization.Key.DATE_RANGE_ORDER_ERROR, language)
            return
        }

        exportValidationError = null
        val range = Pair(calFrom.timeInMillis, calTo.timeInMillis)
        if (isBackupShare) {
            onBackupExcel(range)
        } else {
            onExportExcelFile(range)
        }
    }

    if (showFullExportConfirmAlert) {
        AlertDialog(
            onDismissRequest = { showFullExportConfirmAlert = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = Localization.get(Localization.Key.EXPORT_FULL_DATA_CONFIRM_TITLE, language),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = Localization.get(Localization.Key.EXPORT_FULL_DATA_CONFIRM_MSG, language),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFullExportConfirmAlert = false
                        onExportExcelFile(null)
                    }
                ) {
                    Text(Localization.get(Localization.Key.CONFIRM_EXPORT, language))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullExportConfirmAlert = false }) {
                    Text(Localization.get(Localization.Key.CANCEL, language))
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("database_status_tab"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Localization.get(Localization.Key.DB_STATUS_TITLE, language),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onRefreshSize) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Capacity Warning Banner (When remaining <= 1000)
            if (isNearCapacity) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("db_capacity_warning_card"),
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
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
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
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // Healthy status card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = Localization.get(Localization.Key.DB_CAPACITY_SAFE_MSG, language),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFF1B5E20)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Progress Bar of Capacity
            Text(
                text = "${Localization.get(Localization.Key.DB_SUPPORTED_CAPACITY, language)}: ${String.format(java.util.Locale.US, "%,d", supportedCapacity)}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { usageRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (isNearCapacity) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${Localization.get(Localization.Key.DB_REMAINING_CAPACITY, language)}: ${String.format(java.util.Locale.US, "%,d", remainingCapacity)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isNearCapacity) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isNearCapacity) FontWeight.Bold else FontWeight.Normal
                    )
                )
                Text(
                    text = "${String.format(java.util.Locale.US, "%.1f", usageRatio * 100f)}% used",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Metrics Grid / Rows
            DbStatRow(
                label = Localization.get(Localization.Key.MAX_SUPPORTED_CAPACITY, language),
                value = String.format(java.util.Locale.US, "%,d", supportedCapacity)
            )
            DbStatRow(
                label = Localization.get(Localization.Key.DB_TOTAL_TRANSACTIONS, language),
                value = String.format(java.util.Locale.US, "%,d", totalInvoices)
            )
            DbStatRow(
                label = Localization.get(Localization.Key.DB_TOTAL_ITEMS_SOLD, language),
                value = String.format(java.util.Locale.US, "%,d", totalLines)
            )
            DbStatRow(
                label = Localization.get(Localization.Key.DB_TOTAL_PRODUCTS, language),
                value = String.format(java.util.Locale.US, "%,d", totalProducts)
            )
            DbStatRow(
                label = Localization.get(Localization.Key.DB_TOTAL_CUSTOMERS, language),
                value = String.format(java.util.Locale.US, "%,d", totalCustomers)
            )
            DbStatRow(
                label = Localization.get(Localization.Key.DB_FILE_SIZE, language),
                value = dbSizeFormatted
            )
            DbStatRow(
                label = Localization.get(Localization.Key.DB_STORAGE_STATUS, language),
                value = if (isNearCapacity) {
                    Localization.get(Localization.Key.DB_STATUS_WARNING, language)
                } else {
                    Localization.get(Localization.Key.DB_STATUS_NORMAL, language)
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Multi-Branch XLSX Synchronization
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Localization.get(Localization.Key.XLSX_SYNC_TITLE, language),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = Localization.get(Localization.Key.XLSX_SYNC_DESC, language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Feature Badges
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Localization.get(Localization.Key.FEATURE_DUPLICATE_DETECTION, language),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Localization.get(Localization.Key.FEATURE_CONFLICT_PROTECTION, language),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Localization.get(Localization.Key.FEATURE_PRESERVE_LOCAL, language),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Data Export Section with Date Range
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Localization.get(Localization.Key.DATA_EXPORT_SECTION_TITLE, language),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = Localization.get(Localization.Key.DATA_EXPORT_SECTION_DESC, language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = exportFromDateText,
                    onValueChange = {
                        exportFromDateText = it
                        exportValidationError = null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_from_date_input"),
                    label = { Text(Localization.get(Localization.Key.CUSTOM_DATE_FROM, language), fontSize = 11.sp) },
                    placeholder = { Text("dd-MM-yyyy", fontSize = 11.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = exportToDateText,
                    onValueChange = {
                        exportToDateText = it
                        exportValidationError = null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_to_date_input"),
                    label = { Text(Localization.get(Localization.Key.CUSTOM_DATE_TO, language), fontSize = 11.sp) },
                    placeholder = { Text("dd-MM-yyyy", fontSize = 11.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            if (exportValidationError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = exportValidationError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("export_validation_error_text")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { validateAndExport(false) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("db_export_xlsx_btn"),
                    enabled = !isExporting && !isImporting
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Localization.get(Localization.Key.BTN_EXPORT_XLSX, language), fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onImportExcel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("db_import_xlsx_btn"),
                    enabled = !isExporting && !isImporting
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Localization.get(Localization.Key.BTN_IMPORT_EXCEL, language), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Dedicated Data Backup & Export Section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Localization.get(Localization.Key.DATA_BACKUP_SECTION_TITLE, language),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = Localization.get(Localization.Key.DATA_BACKUP_SECTION_DESC, language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { validateAndExport(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("db_backup_excel_btn"),
                enabled = !isExporting && !isImporting
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(Localization.get(Localization.Key.BTN_FULL_BACKUP_XLSX, language), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DbStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

internal fun parseDateDdMmYyyy(input: String): Date? {
    val trimmed = input.trim()
    val parts = trimmed.split("-")
    if (parts.size != 3) return null
    val day = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val year = parts[2].toIntOrNull() ?: return null
    if (day !in 1..31 || month !in 1..12 || year !in 1970..2100) return null
    return try {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US).apply { isLenient = false }
        sdf.parse(trimmed)
    } catch (e: Exception) {
        null
    }
}
