package com.example.pos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pos.data.Localization
import com.example.pos.data.db.ProductEntity
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.ui.components.ItemTypeBadge
import com.example.pos.ui.components.TopStationHeader
import com.example.pos.ui.viewmodel.DraftInvoiceItem
import com.example.pos.ui.viewmodel.PosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScreen(
    viewModel: PosViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val companyHeader by viewModel.companyHeader.collectAsState()
    val activeOperator by viewModel.activeOperator.collectAsState()
    val allActiveProducts by viewModel.allActiveProducts.collectAsState()

    val draftOrderId by viewModel.draftOrderId.collectAsState()
    val customerInfo by viewModel.customerInfo.collectAsState()
    val customerInfoError by viewModel.customerInfoError.collectAsState()
    val customerSuggestions by viewModel.customerSuggestions.collectAsState()
    val draftItems by viewModel.draftItems.collectAsState()
    val rawSubtotalPaisa by viewModel.rawSubtotalPaisa.collectAsState()
    val roundedTotalBdt by viewModel.roundedTotalBdt.collectAsState()
    val roundingDiffPaisa by viewModel.roundingDifferencePaisa.collectAsState()
    val isProcessingOrder by viewModel.isProcessingOrder.collectAsState()
    val isDraftFullPaid by viewModel.isDraftFullPaid.collectAsState()
    val effectiveDraftPaidBdt by viewModel.effectiveDraftPaidBdt.collectAsState()
    val draftDueBdt by viewModel.draftDueBdt.collectAsState()

    // Modals
    var showProductPickerModal by remember { mutableStateOf(false) }
    var showCustomItemDialog by remember { mutableStateOf(false) }
    var showClearDraftConfirmDialog by remember { mutableStateOf(false) }
    var showCheckoutConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("invoice_screen")
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp)
        ) {
            // Customer Info & Order ID Row
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "#$draftOrderId",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }

                            if (draftItems.isNotEmpty()) {
                                TextButton(
                                    onClick = { showClearDraftConfirmDialog = true },
                                    modifier = Modifier.testTag("clear_draft_btn")
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = Localization.get(Localization.Key.CANCEL, language),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = customerInfo,
                            onValueChange = { viewModel.setCustomerInfo(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customer_info_input"),
                            label = { Text(Localization.get(Localization.Key.CUSTOMER_INFO_LABEL, language), fontSize = 12.sp) },
                            placeholder = { Text(Localization.get(Localization.Key.CUSTOMER_INFO_PLACEHOLDER, language), fontSize = 12.sp) },
                            singleLine = true,
                            isError = customerInfoError != null,
                            supportingText = {
                                if (customerInfoError != null) {
                                    Text(
                                        text = customerInfoError ?: "",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp
                                    )
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                if (customerInfo.isNotBlank()) {
                                    IconButton(onClick = { viewModel.setCustomerInfo("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        )

                        // Customer Auto-complete Suggestions (PRD: Efficient Search & Autocomplete)
                        if (customerSuggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (language == "bn") "পরামর্শ (ট্যাপ করুন):" else "Matches (tap to select):",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                customerSuggestions.take(3).forEach { cust ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .clickable { viewModel.selectCustomerSuggestion(cust) }
                                            .testTag("suggestion_${cust.customerInfo}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = cust.customerInfo,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                            if (cust.customerName.isNotBlank()) {
                                                Text(
                                                    text = " (${cust.customerName})",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Add Action Buttons Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showProductPickerModal = true },
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("add_product_btn"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = Localization.get(Localization.Key.ADD_PRODUCT_OR_SERVICE, language),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    OutlinedButton(
                        onClick = { showCustomItemDialog = true },
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("add_custom_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = Localization.get(Localization.Key.ADD_CUSTOM_ITEM, language),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Invoice Table Header
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(28.dp)) // space for reorder arrows
                        Text(
                            text = Localization.get(Localization.Key.TABLE_ITEM, language),
                            modifier = Modifier.weight(1.3f),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Localization.get(Localization.Key.TABLE_QTY_UNIT, language),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Localization.get(Localization.Key.TABLE_RATE, language),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Localization.get(Localization.Key.TABLE_TOTAL, language),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(36.dp)) // space for delete action
                    }
                }
            }

            // Empty State
            if (draftItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = Localization.get(Localization.Key.EMPTY_INVOICE_PROMPT, language),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Table Rows with In-Line Editing and Drag/Order Controls
                itemsIndexed(draftItems, key = { _, item -> item.tempId }) { index, item ->
                    InvoiceItemRow(
                        item = item,
                        index = index,
                        totalItems = draftItems.size,
                        onQuantityChanged = { newQty -> viewModel.updateItemQuantity(item.tempId, newQty) },
                        onPriceChanged = { newPrice -> viewModel.updateItemUnitPrice(item.tempId, newPrice) },
                        onMoveUp = { if (index > 0) viewModel.moveItem(index, index - 1) },
                        onMoveDown = { if (index < draftItems.size - 1) viewModel.moveItem(index, index + 1) },
                        onRemove = { viewModel.removeItem(item.tempId) }
                    )
                }
            }

            // Calculations & Auto-Rounding Box
            if (draftItems.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("invoice_summary_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Subtotal Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Localization.get(Localization.Key.SUBTOTAL, language),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${FinancialCalculationEngine.formatPaisaToBdt(rawSubtotalPaisa)} ৳",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Auto-Rounding Rule Explanation Badge
                            val roundingSign = if (roundingDiffPaisa > 0) "+" else ""
                            val roundingText = "${roundingSign}${FinancialCalculationEngine.formatPaisaToBdt(roundingDiffPaisa)} ৳"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = Localization.get(Localization.Key.ROUNDING_ADJUSTMENT, language),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (roundingDiffPaisa == 0L) Color(0xFFEEEEEE)
                                        else if (roundingDiffPaisa < 0) Color(0xFFFFEBEE)
                                        else Color(0xFFE8F5E9)
                                    ) {
                                        Text(
                                            text = if (roundingDiffPaisa < 0) "(.01-.50 ↓)" else if (roundingDiffPaisa > 0) "(.51-.99 ↑)" else "(.00 =)",
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = if (roundingDiffPaisa < 0) Color(0xFFC62828) else if (roundingDiffPaisa > 0) Color(0xFF2E7D32) else Color.Gray
                                        )
                                    }
                                }

                                Text(
                                    text = roundingText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = if (roundingDiffPaisa < 0) Color(0xFFD32F2F) else if (roundingDiffPaisa > 0) Color(0xFF388E3C) else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            // Final Rounded Payable Total (BDT)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = Localization.get(Localization.Key.ROUNDED_TOTAL, language),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Final Payable Amount in BDT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${FinancialCalculationEngine.formatWholeBdtToDisplay(roundedTotalBdt)} ৳",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }

                // Action Checkout / Print / PDF Buttons
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showCheckoutConfirmDialog = true },
                            enabled = !isProcessingOrder && draftItems.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("checkout_print_btn"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isProcessingOrder) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (language == "bn") "সংরক্ষণ হচ্ছে..." else "Saving...",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            } else {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Localization.get(Localization.Key.BTN_CHECKOUT_PRINT, language),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.openReceiptPreview() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("preview_receipt_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = Localization.get(Localization.Key.BTN_PREVIEW_RECEIPT, language),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.generateAndShareCurrentDraftPdf(context) },
                            enabled = draftItems.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("draft_share_pdf_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = "Share PDF",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = Localization.get(Localization.Key.BTN_DRAFT_PDF, language),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet: Product Selection from Master Catalog
    if (showProductPickerModal) {
        ProductPickerBottomSheet(
            products = allActiveProducts,
            language = language,
            onDismiss = { showProductPickerModal = false },
            onSelect = { product ->
                viewModel.addItemFromCatalog(product)
                showProductPickerModal = false
            }
        )
    }

    // Dialog: Custom Non-Catalog Item
    if (showCustomItemDialog) {
        CustomItemDialog(
            language = language,
            onDismiss = { showCustomItemDialog = false },
            onAdd = { name, price, qty, unit, itemType ->
                viewModel.addCustomItem(name, price, qty, unit, itemType)
                showCustomItemDialog = false
            }
        )
    }

    // Confirmation Dialog: Clear Active Draft
    if (showClearDraftConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearDraftConfirmDialog = false },
            title = {
                Text(
                    text = if (language == "bn") "ইনভয়েস বাতিল করবেন?" else "Discard Current Draft?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = if (language == "bn") "বর্তমান ড্রাফটের সকল পণ্য ও ইনপুট মুছে ফেলা হবে।"
                    else "All items and inputs in this invoice draft will be cleared.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearDraft()
                        showClearDraftConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (language == "bn") "হ্যাঁ, বাতিল করুন" else "Yes, Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDraftConfirmDialog = false }) {
                    Text(Localization.get(Localization.Key.CANCEL, language))
                }
            }
        )
    }

    // Confirmation Modal: "Full Paid" Confirmation & Invoice Print Feature (PHASE 4 & PHASE 6)
    if (showCheckoutConfirmDialog) {
        Dialog(onDismissRequest = { showCheckoutConfirmDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .testTag("checkout_confirm_dialog"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isDraftFullPaid) {
                        // Green Check / Tick Icon
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Full Paid",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(50.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Full Paid",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = if (language == "bn") "✓ সম্পূর্ণ পরিশোধিত ইনভয়েস" else "✓ Fully Paid Invoice",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFF2E7D32)
                        )
                    } else {
                        // When NOT fully paid
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Payment Status",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(46.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (language == "bn") "বাকি / অপরিশোধিত" else "Due / Partial Payment",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Summary Details Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (language == "bn") "ইনভয়েস নং:" else "Invoice ID:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "#$draftOrderId",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = Localization.get(Localization.Key.ROUNDED_TOTAL, language) + ":",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${FinancialCalculationEngine.formatWholeBdtToDisplay(roundedTotalBdt)} ৳",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (language == "bn") "পরিশোধিত টাকা:" else "Paid Amount:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${FinancialCalculationEngine.formatWholeBdtToDisplay(effectiveDraftPaidBdt)} ৳",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDraftFullPaid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }

                            if (!isDraftFullPaid && draftDueBdt > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (language == "bn") "বাকি টাকা:" else "Due Amount:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "${FinancialCalculationEngine.formatWholeBdtToDisplay(draftDueBdt)} ৳",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons (Vertical Layout)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showCheckoutConfirmDialog = false
                                viewModel.checkoutAndSave(printDirectly = true)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("confirm_print_btn"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (language == "bn") "প্রিন্ট ও নিশ্চিতকরণ" else "Print & Confirm",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        OutlinedButton(
                            onClick = { showCheckoutConfirmDialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("cancel_confirm_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = Localization.get(Localization.Key.CANCEL, language),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosInlineEditableCell(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    testTag: String = ""
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    var isFocused by remember { mutableStateOf(false) }
    var shouldReplaceOnInput by remember { mutableStateOf(false) }

    // Keep state in sync with external updates (e.g. catalog adds or item reorder)
    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { incoming ->
            val oldText = value
            val resolvedText = if (shouldReplaceOnInput && oldText.isNotEmpty()) {
                when {
                    // Typed before existing text (e.g. cursor at 0, operator typed "120" before "130" -> "120130")
                    incoming.text.endsWith(oldText) && incoming.text.length > oldText.length -> {
                        incoming.text.removeSuffix(oldText)
                    }
                    // Typed after existing text (e.g. operator typed "120" after "130" -> "130120")
                    incoming.text.startsWith(oldText) && incoming.text.length > oldText.length -> {
                        incoming.text.removePrefix(oldText)
                    }
                    else -> incoming.text
                }
            } else {
                incoming.text
            }

            shouldReplaceOnInput = false
            textFieldValue = TextFieldValue(text = resolvedText, selection = TextRange(resolvedText.length))
            onValueChange(resolvedText)
        },
        modifier = modifier
            .testTag(testTag)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    shouldReplaceOnInput = true
                    textFieldValue = textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length))
                } else {
                    shouldReplaceOnInput = false
                }
                isFocused = focusState.isFocused
            },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            textAlign = textAlign,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(
                        color = if (isFocused) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .border(
                        width = if (isFocused) 1.5.dp else 1.dp,
                        color = if (isFocused) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp),
                contentAlignment = when (textAlign) {
                    TextAlign.End -> Alignment.CenterEnd
                    TextAlign.Start -> Alignment.CenterStart
                    else -> Alignment.Center
                }
            ) {
                innerTextField()
            }
        }
    )
}

@Composable
fun InvoiceItemRow(
    item: DraftInvoiceItem,
    index: Int,
    totalItems: Int,
    onQuantityChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reorder Arrows (24dp + 4dp spacer = 28dp matching header)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(24.dp)
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowDropUp,
                        contentDescription = "Up",
                        tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.LightGray
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < totalItems - 1,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Down",
                        tint = if (index < totalItems - 1) MaterialTheme.colorScheme.primary else Color.LightGray
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Item Name & Unit Badge
            Column(modifier = Modifier.weight(1.3f)) {
                Text(
                    text = item.itemName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemTypeBadge(item.itemType)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Editable Quantity
            PosInlineEditableCell(
                value = item.quantityStr,
                onValueChange = onQuantityChanged,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp),
                textAlign = TextAlign.Center,
                testTag = "qty_input_${item.itemId}"
            )

            // Editable Invoice-specific Unit Price / Rate
            PosInlineEditableCell(
                value = item.unitPriceBdtStr,
                onValueChange = onPriceChanged,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp),
                textAlign = TextAlign.End,
                testTag = "price_input_${item.itemId}"
            )

            // Dynamic Total
            Text(
                text = FinancialCalculationEngine.formatPaisaToBdt(item.lineTotalPaisa),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            // Delete Button (32dp + 4dp space = 36dp matching header)
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("delete_item_${item.itemId}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductPickerBottomSheet(
    products: List<ProductEntity>,
    language: String,
    onDismiss: () -> Unit,
    onSelect: (ProductEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredProducts = remember(products, searchQuery, selectedFilter) {
        products.filter { p ->
            val matchesFilter = when (selectedFilter) {
                "LIQUID" -> p.itemType.equals("Liquid", ignoreCase = true)
                "PRODUCT" -> p.itemType.equals("Product", ignoreCase = true)
                "SERVICE" -> p.itemType.equals("Service", ignoreCase = true)
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    p.itemName.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .testTag("product_picker_modal")
        ) {
            Text(
                text = Localization.get(Localization.Key.MODAL_SELECT_PRODUCT, language),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(Localization.get(Localization.Key.SEARCH_PRODUCTS, language), fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text(Localization.get(Localization.Key.FILTER_ALL, language), fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "LIQUID",
                    onClick = { selectedFilter = "LIQUID" },
                    label = { Text(Localization.get(Localization.Key.FILTER_LIQUID, language), fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "PRODUCT",
                    onClick = { selectedFilter = "PRODUCT" },
                    label = { Text(Localization.get(Localization.Key.FILTER_PRODUCT, language), fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "SERVICE",
                    onClick = { selectedFilter = "SERVICE" },
                    label = { Text(Localization.get(Localization.Key.FILTER_SERVICE, language), fontSize = 11.sp) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredProducts.size) { idx ->
                    val product = filteredProducts[idx]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(product) }
                            .testTag("catalog_item_${product.itemId}"),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = product.itemName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ItemTypeBadge(product.itemType)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${product.unit} • Serial #${product.sortOrder}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = "${FinancialCalculationEngine.formatPaisaToBdt(product.defaultUnitPricePaisa)} ৳",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomItemDialog(
    language: String,
    onDismiss: () -> Unit,
    onAdd: (name: String, price: String, qty: String, unit: String, itemType: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("Unit") }
    var itemType by remember { mutableStateOf("Product") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .widthIn(max = 480.dp)
                .imePadding()
                .testTag("custom_item_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            // Single keyboard-safe scrollable container so all fields and action buttons remain reachable
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Bar: Title clearly separated with close icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Localization.get(Localization.Key.ADD_CUSTOM_ITEM, language),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Form Body with clear label-to-input spacing and responsive field layout
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    val useTwoColumnPriceQty = maxWidth >= 280.dp

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Item / Service Name Field
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = Localization.get(Localization.Key.CUSTOM_ITEM_NAME, language),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                placeholder = {
                                    Text(
                                        text = Localization.get(Localization.Key.CUSTOM_ITEM_NAME, language),
                                        fontSize = 13.sp
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_item_name_input"),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }

                        // 2. Item Type Selection
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = Localization.get(Localization.Key.CUSTOM_ITEM_TYPE, language),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val types = listOf("Product", "Service", "Liquid")
                                types.forEach { typeOption ->
                                    val selected = itemType.equals(typeOption, ignoreCase = true)
                                    val labelText = when (typeOption) {
                                        "Product" -> Localization.get(Localization.Key.FILTER_PRODUCT, language)
                                        "Service" -> Localization.get(Localization.Key.FILTER_SERVICE, language)
                                        "Liquid" -> Localization.get(Localization.Key.FILTER_LIQUID, language)
                                        else -> typeOption
                                    }
                                    FilterChip(
                                        selected = selected,
                                        onClick = { itemType = typeOption },
                                        label = { Text(labelText, fontSize = 12.sp) },
                                        modifier = Modifier.testTag("custom_type_chip_${typeOption.lowercase()}")
                                    )
                                }
                            }
                        }

                        // 3. Price & Quantity Fields (Responsive 2-column or stacked on very narrow screens)
                        if (useTwoColumnPriceQty) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Localization.get(Localization.Key.CUSTOM_ITEM_PRICE, language),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = price,
                                        onValueChange = { price = it },
                                        placeholder = { Text("0.00", fontSize = 13.sp) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("custom_item_price_input"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Localization.get(Localization.Key.CUSTOM_ITEM_QTY, language),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = qty,
                                        onValueChange = { qty = it },
                                        placeholder = { Text("1", fontSize = 13.sp) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("custom_item_qty_input"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = Localization.get(Localization.Key.CUSTOM_ITEM_PRICE, language),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = price,
                                    onValueChange = { price = it },
                                    placeholder = { Text("0.00", fontSize = 13.sp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_item_price_input"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                                )
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = Localization.get(Localization.Key.CUSTOM_ITEM_QTY, language),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = qty,
                                    onValueChange = { qty = it },
                                    placeholder = { Text("1", fontSize = 13.sp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_item_qty_input"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                                )
                            }
                        }

                        // 4. Unit Field (Full width so Bangla/English unit label and text are never clipped)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = Localization.get(Localization.Key.CUSTOM_ITEM_UNIT, language),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = unit,
                                onValueChange = { unit = it },
                                placeholder = {
                                    Text(
                                        text = Localization.get(Localization.Key.CUSTOM_ITEM_UNIT, language),
                                        fontSize = 13.sp
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_item_unit_input"),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Footer Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("custom_item_cancel_btn")
                    ) {
                        Text(Localization.get(Localization.Key.CANCEL, language))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && price.isNotBlank()) {
                                onAdd(name, price, qty, unit, itemType)
                            }
                        },
                        enabled = name.isNotBlank() && price.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("custom_item_add_btn")
                    ) {
                        Text(Localization.get(Localization.Key.BTN_ADD, language))
                    }
                }
            }
        }
    }
}
