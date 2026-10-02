package com.example.pos.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pos.data.Localization
import com.example.pos.data.db.ProductEntity
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.ui.components.ItemTypeBadge
import com.example.pos.ui.components.TopStationHeader
import com.example.pos.ui.viewmodel.PosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: PosViewModel,
    modifier: Modifier = Modifier
) {
    val language by viewModel.language.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val companyHeader by viewModel.companyHeader.collectAsState()
    val activeOperator by viewModel.activeOperator.collectAsState()
    val products by viewModel.allProducts.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    // Dialogs
    var productToEdit by remember { mutableStateOf<ProductEntity?>(null) }
    var isAddDialogOpen by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }

    val filteredProducts = remember(products, searchQuery, selectedFilter) {
        products.filter { p ->
            val matchesFilter = when (selectedFilter) {
                "LIQUID" -> p.itemType.equals("Liquid", ignoreCase = true)
                "PRODUCT" -> p.itemType.equals("Product", ignoreCase = true)
                "SERVICE" -> p.itemType.equals("Service", ignoreCase = true)
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    p.itemName.contains(searchQuery, ignoreCase = true) ||
                    p.nameBn.contains(searchQuery, ignoreCase = true) ||
                    p.nameEn.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }.sortedBy { it.sortOrder }
    }

    val canReorder = searchQuery.isBlank() && selectedFilter == "ALL"

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("products_screen"),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddDialogOpen = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_new_product_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Product")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header
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
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
            ) {
                // Search and Filters Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
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
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${Localization.get(Localization.Key.PRODUCTS_TITLE, language)} (${filteredProducts.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        if (canReorder && filteredProducts.size > 1) {
                            Text(
                                text = Localization.get(Localization.Key.DRAG_REORDER_HELP, language),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (filteredProducts.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
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
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = Localization.get(Localization.Key.NO_RECORDS_FOUND, language),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = filteredProducts,
                        key = { _, product -> product.itemId }
                    ) { index, product ->
                        ProductMasterCard(
                            product = product,
                            language = language,
                            canReorder = canReorder,
                            isFirst = index == 0,
                            isLast = index == filteredProducts.lastIndex,
                            onMoveUp = {
                                if (index > 0) {
                                    viewModel.moveProduct(index, index - 1, filteredProducts)
                                }
                            },
                            onMoveDown = {
                                if (index < filteredProducts.lastIndex) {
                                    viewModel.moveProduct(index, index + 1, filteredProducts)
                                }
                            },
                            onEdit = { productToEdit = product },
                            onDelete = { productToDelete = product }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Product Dialog
    if (isAddDialogOpen || productToEdit != null) {
        val existing = productToEdit
        ProductEditDialog(
            existing = existing,
            language = language,
            onDismiss = {
                isAddDialogOpen = false
                productToEdit = null
            },
            onSave = { id, name, nameBn, nameEn, type, unit, priceStr, sortOrder ->
                viewModel.saveProduct(
                    itemId = id,
                    name = name,
                    nameBn = nameBn,
                    nameEn = nameEn,
                    type = type,
                    unit = unit,
                    priceBdtText = priceStr,
                    sortOrder = sortOrder
                )
                isAddDialogOpen = false
                productToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (productToDelete != null) {
        val item = productToDelete!!
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text(Localization.get(Localization.Key.CONFIRM_DELETE, language)) },
            text = { Text("Delete item: ${item.itemName} (${item.unit})?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProduct(item.itemId)
                        productToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Localization.get(Localization.Key.BTN_DELETE, language))
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text(Localization.get(Localization.Key.CANCEL, language))
                }
            }
        )
    }
}

@Composable
fun ProductMasterCard(
    product: ProductEntity,
    language: String,
    canReorder: Boolean = false,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val primaryName = product.getDisplayName(language)
    val secondaryName = if (language.equals("bn", ignoreCase = true)) {
        if (product.nameEn.isNotBlank() && product.nameEn != primaryName) product.nameEn else ""
    } else {
        if (product.nameBn.isNotBlank() && product.nameBn != primaryName) product.nameBn else ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("product_master_${product.itemId}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reorder controls & Drag handle
            if (canReorder) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = !isFirst,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("move_up_${product.itemId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Move Up",
                            modifier = Modifier.size(16.dp),
                            tint = if (!isFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Reorder handle",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    IconButton(
                        onClick = onMoveDown,
                        enabled = !isLast,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("move_down_${product.itemId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Move Down",
                            modifier = Modifier.size(16.dp),
                            tint = if (!isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            // Product Details
            Column(modifier = Modifier.weight(1.5f)) {
                Text(
                    text = primaryName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                if (secondaryName.isNotBlank()) {
                    Text(
                        text = secondaryName,
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemTypeBadge(product.itemType)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Unit: ${product.unit} • #${product.sortOrder}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Price & Actions
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${FinancialCalculationEngine.formatPaisaToBdt(product.defaultUnitPricePaisa)} ৳",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = "/ ${product.unit}",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("edit_product_${product.itemId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("delete_product_${product.itemId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditDialog(
    existing: ProductEntity?,
    language: String,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        name: String,
        nameBn: String,
        nameEn: String,
        type: String,
        unit: String,
        priceStr: String,
        sortOrder: Int
    ) -> Unit
) {
    var name by remember { mutableStateOf(existing?.itemName ?: "") }
    var nameBn by remember { mutableStateOf(existing?.nameBn ?: "") }
    var nameEn by remember { mutableStateOf(existing?.nameEn ?: "") }
    var selectedType by remember { mutableStateOf(existing?.itemType ?: "Liquid") }
    var unit by remember { mutableStateOf(existing?.unit ?: "Liter") }
    var priceStr by remember {
        mutableStateOf(
            if (existing != null) FinancialCalculationEngine.formatPaisaToBdt(existing.defaultUnitPricePaisa) else ""
        )
    }
    var sortOrderStr by remember { mutableStateOf((existing?.sortOrder ?: 1).toString()) }

    var typeMenuExpanded by remember { mutableStateOf(false) }
    val types = listOf("Liquid", "Product", "Service")

    var unitMenuExpanded by remember { mutableStateOf(false) }
    val commonUnits = listOf("Liter", "Quantity", "Pcs", "Kg", "L", "Hour")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("product_edit_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
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
                        text = if (existing != null) Localization.get(Localization.Key.EDIT_PRODUCT, language)
                        else Localization.get(Localization.Key.BTN_NEW_PRODUCT, language),
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

                // Scrollable Form Body: Keyboard-safe with clean vertical spacing
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bangla Name
                    OutlinedTextField(
                        value = nameBn,
                        onValueChange = {
                            nameBn = it
                            if (name.isBlank()) name = it
                        },
                        label = { Text(Localization.get(Localization.Key.PRODUCT_NAME_BN, language), fontSize = 12.sp) },
                        placeholder = { Text(if (language == "bn") "যেমন: অকটেন" else "e.g. অকটেন", fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_name_bn_input"),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    // English Name
                    OutlinedTextField(
                        value = nameEn,
                        onValueChange = {
                            nameEn = it
                            if (name.isBlank() || name == nameBn) name = it
                        },
                        label = { Text(Localization.get(Localization.Key.PRODUCT_NAME_EN, language), fontSize = 12.sp) },
                        placeholder = { Text("e.g. Octane", fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_name_en_input"),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    // Type Dropdown
                    ExposedDropdownMenuBox(
                        expanded = typeMenuExpanded,
                        onExpandedChange = { typeMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(Localization.get(Localization.Key.PRODUCT_TYPE, language), fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("product_type_dropdown"),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = typeMenuExpanded,
                            onDismissRequest = { typeMenuExpanded = false }
                        ) {
                            types.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        selectedType = type
                                        if (type == "Liquid" && (unit.isBlank() || unit == "Pcs" || unit == "Quantity")) unit = "Liter"
                                        if (type == "Product" && (unit.isBlank() || unit == "Liter" || unit == "Quantity")) unit = "Pcs"
                                        if (type == "Service" && (unit.isBlank() || unit == "Liter" || unit == "Pcs")) unit = "Quantity"
                                        typeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Unit & Default Price
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = unitMenuExpanded,
                            onExpandedChange = { unitMenuExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = unit,
                                onValueChange = { unit = it },
                                label = { Text(Localization.get(Localization.Key.UNIT_LABEL, language), fontSize = 12.sp) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("product_unit_input"),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                            ExposedDropdownMenu(
                                expanded = unitMenuExpanded,
                                onDismissRequest = { unitMenuExpanded = false }
                            ) {
                                commonUnits.forEach { u ->
                                    DropdownMenuItem(
                                        text = { Text(u) },
                                        onClick = {
                                            unit = u
                                            unitMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = priceStr,
                            onValueChange = { priceStr = it },
                            label = { Text(Localization.get(Localization.Key.DEFAULT_PRICE, language), fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("product_price_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }

                    // Sort Order
                    OutlinedTextField(
                        value = sortOrderStr,
                        onValueChange = { sortOrderStr = it },
                        label = { Text(Localization.get(Localization.Key.SORT_ORDER_LABEL, language), fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_sort_order_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Footer Actions: Clearly separated from form fields
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
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(Localization.get(Localization.Key.CANCEL, language))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val effectiveName = when {
                                nameEn.isNotBlank() -> nameEn
                                nameBn.isNotBlank() -> nameBn
                                name.isNotBlank() -> name
                                else -> "Item"
                            }
                            if (priceStr.isNotBlank()) {
                                onSave(
                                    existing?.itemId,
                                    effectiveName,
                                    nameBn,
                                    nameEn,
                                    selectedType,
                                    unit,
                                    priceStr,
                                    sortOrderStr.toIntOrNull() ?: 1
                                )
                            }
                        },
                        enabled = (nameBn.isNotBlank() || nameEn.isNotBlank() || name.isNotBlank()) && priceStr.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("save_product_btn")
                    ) {
                        Text(Localization.get(Localization.Key.BTN_SAVE, language))
                    }
                }
            }
        }
    }
}
