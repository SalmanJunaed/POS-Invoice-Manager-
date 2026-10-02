package com.example.pos.excel

import android.content.Context
import android.net.Uri
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.domain.FinancialCalculationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Details of a transaction data conflict detected between imported XLSX and local Room record.
 */
data class ImportConflict(
    val globalTransactionId: String,
    val orderId: String,
    val localSummary: String,
    val importedSummary: String,
    val reason: String
)

/**
 * Comprehensive result of an XLSX multi-branch sync import operation.
 */
data class ImportSummary(
    val totalRows: Int,
    val importedCount: Int,
    val duplicateSkippedCount: Int,
    val invalidCount: Int,
    val conflictCount: Int,
    val failedCount: Int = 0,
    val conflicts: List<ImportConflict> = emptyList(),
    val validationErrors: List<String> = emptyList()
)

/**
 * Encapsulates an individual transaction payload parsed from an XLSX file.
 */
data class ParsedTransaction(
    val invoice: InvoiceEntity,
    val lines: List<InvoiceLineEntity>
)

/**
 * Result of raw XLSX structure and row parsing.
 */
data class ParseXlsxResult(
    val transactions: List<ParsedTransaction>,
    val totalRowsParsed: Int,
    val invalidRows: Int,
    val validationErrors: List<String>
)

object ExcelExchangeEngine {

    // Sheet 1: Transactions Headers
    val TRANSACTION_HEADERS = listOf(
        "Global Transaction ID",
        "Branch Number",
        "Order ID",
        "Date Time",
        "Date Time Epoch",
        "Operator ID",
        "Operator Name",
        "Customer Info",
        "Raw Total (BDT)",
        "Raw Total Paisa",
        "Rounded Total (BDT)",
        "Footer Note",
        "Created At",
        "Updated At",
        "Items Summary"
    )

    // Sheet 2: Invoice Items Headers
    val ITEM_HEADERS = listOf(
        "Global Transaction ID",
        "Order ID",
        "Item ID",
        "Item Name",
        "Item Type",
        "Unit",
        "Quantity",
        "Unit Price (BDT)",
        "Unit Price Paisa",
        "Line Total (BDT)",
        "Line Total Paisa",
        "Sort Order",
        "Created At"
    )

    /**
     * Exports invoices and granular line items to an offline standard Microsoft Excel (.xlsx) file in cache.
     */
    suspend fun exportToXlsx(
        context: Context,
        invoices: List<InvoiceEntity>,
        branchCode: String = "01",
        lineItemsFetcher: suspend (String) -> List<InvoiceLineEntity>
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val xlsxFile = File(exportDir, "POS_Sales_Export_$timestamp.xlsx")

            FileOutputStream(xlsxFile).use { fos ->
                writeXlsxToStream(fos, invoices, branchCode, lineItemsFetcher)
            }

            Result.success(xlsxFile)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to export .xlsx: ${e.message}"))
        }
    }

    /**
     * Exports invoices directly into a user-selected Storage Access Framework (SAF) URI.
     */
    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        invoices: List<InvoiceEntity>,
        branchCode: String = "01",
        lineItemsFetcher: suspend (String) -> List<InvoiceLineEntity>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open destination file stream for writing."))

            outputStream.use { os ->
                writeXlsxToStream(os, invoices, branchCode, lineItemsFetcher)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to export to selected location: ${e.message}"))
        }
    }

    /**
     * Exports invoices into in-memory ByteArray for tests or direct transmission.
     */
    suspend fun exportToByteArray(
        invoices: List<InvoiceEntity>,
        branchCode: String = "01",
        lineItemsFetcher: suspend (String) -> List<InvoiceLineEntity>
    ): ByteArray = withContext(Dispatchers.IO) {
        val baos = ByteArrayOutputStream()
        writeXlsxToStream(baos, invoices, branchCode, lineItemsFetcher)
        baos.toByteArray()
    }

    /**
     * Writes real OpenXML (.xlsx) archive into given OutputStream.
     */
    suspend fun writeXlsxToStream(
        outputStream: OutputStream,
        invoices: List<InvoiceEntity>,
        branchCode: String = "01",
        lineItemsFetcher: suspend (String) -> List<InvoiceLineEntity>
    ) {
        // Collect line items mapping
        val linesMap = mutableMapOf<String, List<InvoiceLineEntity>>()
        for (inv in invoices) {
            linesMap[inv.orderId] = lineItemsFetcher(inv.orderId)
        }

        ZipOutputStream(outputStream).use { zos ->
            // 1. [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(CONTENT_TYPES_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(RELS_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. xl/_rels/workbook.xml.rels
            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zos.write(WORKBOOK_RELS_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 4. xl/workbook.xml
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            zos.write(WORKBOOK_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 5. xl/worksheets/sheet1.xml (Transactions)
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            writeTransactionsSheet(zos, invoices, branchCode, linesMap)
            zos.closeEntry()

            // 6. xl/worksheets/sheet2.xml (Invoice Items)
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet2.xml"))
            writeInvoiceItemsSheet(zos, invoices, linesMap)
            zos.closeEntry()

            zos.finish()
        }
    }

    private fun writeTransactionsSheet(
        zos: ZipOutputStream,
        invoices: List<InvoiceEntity>,
        defaultBranchCode: String,
        linesMap: Map<String, List<InvoiceLineEntity>>
    ) {
        val writer = zos.bufferedWriter(Charsets.UTF_8)
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        writer.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n")
        writer.write("<sheetData>\n")

        // Header Row (Row 1)
        writer.write("<row r=\"1\">\n")
        TRANSACTION_HEADERS.forEachIndexed { colIdx, header ->
            val colLetter = colIndexToLetter(colIdx)
            writer.write("<c r=\"${colLetter}1\" t=\"inlineStr\"><is><t>")
            writer.write(escapeXml(header))
            writer.write("</t></is></c>\n")
        }
        writer.write("</row>\n")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        // Data Rows
        var rowNum = 2
        for (inv in invoices) {
            val lines = linesMap[inv.orderId] ?: emptyList()
            val itemsSummary = lines.joinToString("; ") {
                "${it.itemName} (${it.quantityStr} ${it.unit} @ ${FinancialCalculationEngine.formatPaisaToBdt(it.unitPricePaisa)})"
            }

            val bCode = if (inv.branchCode.isNotBlank()) inv.branchCode else defaultBranchCode
            val gId = if (inv.globalTransactionId.isNotBlank()) inv.globalTransactionId else UUID.randomUUID().toString()

            val rowValues = listOf(
                gId,
                bCode,
                inv.orderId,
                dateFormat.format(Date(inv.dateTime)),
                inv.dateTime.toString(),
                inv.operatorId,
                inv.operatorName,
                inv.customerInfo,
                FinancialCalculationEngine.formatPaisaToBdt(inv.rawTotalPaisa),
                inv.rawTotalPaisa.toString(),
                inv.roundedTotalBdt.toString(),
                inv.footerNote,
                inv.createdAt.toString(),
                inv.updatedAt.toString(),
                itemsSummary
            )

            writer.write("<row r=\"$rowNum\">\n")
            rowValues.forEachIndexed { colIdx, value ->
                val colLetter = colIndexToLetter(colIdx)
                writer.write("<c r=\"$colLetter$rowNum\" t=\"inlineStr\"><is><t>")
                writer.write(escapeXml(value))
                writer.write("</t></is></c>\n")
            }
            writer.write("</row>\n")
            rowNum++
        }

        writer.write("</sheetData>\n")
        writer.write("</worksheet>")
        writer.flush()
    }

    private fun writeInvoiceItemsSheet(
        zos: ZipOutputStream,
        invoices: List<InvoiceEntity>,
        linesMap: Map<String, List<InvoiceLineEntity>>
    ) {
        val writer = zos.bufferedWriter(Charsets.UTF_8)
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        writer.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n")
        writer.write("<sheetData>\n")

        // Header Row (Row 1)
        writer.write("<row r=\"1\">\n")
        ITEM_HEADERS.forEachIndexed { colIdx, header ->
            val colLetter = colIndexToLetter(colIdx)
            writer.write("<c r=\"${colLetter}1\" t=\"inlineStr\"><is><t>")
            writer.write(escapeXml(header))
            writer.write("</t></is></c>\n")
        }
        writer.write("</row>\n")

        // Item Rows
        var rowNum = 2
        for (inv in invoices) {
            val lines = linesMap[inv.orderId] ?: emptyList()
            for (line in lines) {
                val rowValues = listOf(
                    inv.globalTransactionId,
                    inv.orderId,
                    line.itemId,
                    line.itemName,
                    line.itemType,
                    line.unit,
                    line.quantityStr,
                    FinancialCalculationEngine.formatPaisaToBdt(line.unitPricePaisa),
                    line.unitPricePaisa.toString(),
                    FinancialCalculationEngine.formatPaisaToBdt(line.lineTotalPaisa),
                    line.lineTotalPaisa.toString(),
                    line.sortOrder.toString(),
                    line.createdAt.toString()
                )

                writer.write("<row r=\"$rowNum\">\n")
                rowValues.forEachIndexed { colIdx, value ->
                    val colLetter = colIndexToLetter(colIdx)
                    writer.write("<c r=\"$colLetter$rowNum\" t=\"inlineStr\"><is><t>")
                    writer.write(escapeXml(value))
                    writer.write("</t></is></c>\n")
                }
                writer.write("</row>\n")
                rowNum++
            }
        }

        writer.write("</sheetData>\n")
        writer.write("</worksheet>")
        writer.flush()
    }

    /**
     * Parses an .xlsx file from Uri, validating structure, headers, data types, and required fields.
     */
    suspend fun parseXlsxFromUri(
        context: Context,
        uri: Uri
    ): Result<ParseXlsxResult> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open file stream from selected URI."))
            inputStream.use { parseXlsx(it) }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to read file: ${e.message}"))
        }
    }

    /**
     * Parses an .xlsx InputStream with complete validation.
     */
    fun parseXlsx(inputStream: InputStream): Result<ParseXlsxResult> {
        return try {
            val entries = mutableMapOf<String, ByteArray>()
            try {
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            entries[entry.name] = zis.readBytes()
                        }
                        entry = zis.nextEntry
                    }
                }
            } catch (e: Exception) {
                return Result.failure(Exception("Invalid file: The selected file is not a valid Excel (.xlsx) archive: ${e.message}"))
            }

            // 1. Validate file structure
            if (entries.isEmpty()) {
                return Result.failure(Exception("Empty file: The selected file contains 0 bytes."))
            }

            val sheet1Bytes = entries["xl/worksheets/sheet1.xml"]
                ?: return Result.failure(Exception("Invalid file: Not a valid Excel (.xlsx) file (missing worksheet data)."))

            // Parse shared strings if present
            val sharedStrings = if (entries.containsKey("xl/sharedStrings.xml")) {
                parseSharedStrings(String(entries["xl/sharedStrings.xml"]!!, Charsets.UTF_8))
            } else {
                emptyList()
            }

            // Parse Sheet 1 rows
            val sheet1Xml = String(sheet1Bytes, Charsets.UTF_8)
            val sheet1Rows = parseWorksheetRows(sheet1Xml, sharedStrings)

            if (sheet1Rows.isEmpty()) {
                return Result.failure(Exception("Invalid file: Sheet contains no data."))
            }

            // 2. Validate Headers
            val headerRow = sheet1Rows.first()
            val headerIndexMap = mutableMapOf<String, Int>()
            for ((colIdx, headerText) in headerRow) {
                val normalized = normalizeHeader(headerText)
                headerIndexMap[normalized] = colIdx
            }

            // Validate required columns
            val hasIdCol = headerIndexMap.keys.any {
                it.contains("globaltransactionid") || it.contains("orderid") ||
                        it.contains("invoiceid") || it.contains("invoicenumber") || it.contains("transactionid")
            }
            val hasDateCol = headerIndexMap.keys.any {
                it.contains("datetime") || it.contains("date") || it.contains("time") || it.contains("timestamp")
            }
            val hasAmountCol = headerIndexMap.keys.any {
                it.contains("rawtotal") || it.contains("roundedtotal") ||
                        it.contains("total") || it.contains("amount") || it.contains("subtotal")
            }

            if (!hasIdCol || !hasDateCol || !hasAmountCol) {
                val missing = mutableListOf<String>()
                if (!hasIdCol) missing.add("Order ID / Global Transaction ID")
                if (!hasDateCol) missing.add("Date / Time")
                if (!hasAmountCol) missing.add("Total Amount")
                return Result.failure(Exception("Missing required column(s): ${missing.joinToString(", ")}"))
            }

            // Map column indices
            val globalIdCol = findColumnIndex(headerIndexMap, "globaltransactionid", "globalid", "transactionid")
            val branchCol = findColumnIndex(headerIndexMap, "branchnumber", "branchcode", "branch")
            val orderIdCol = findColumnIndex(headerIndexMap, "orderid", "invoicenumber", "invoiceid")
            val dateTimeStrCol = findColumnIndex(headerIndexMap, "datetime", "date", "time")
            val dateTimeEpochCol = findColumnIndex(headerIndexMap, "datetimeepoch", "epoch", "timestamp")
            val operatorIdCol = findColumnIndex(headerIndexMap, "operatorid")
            val operatorNameCol = findColumnIndex(headerIndexMap, "operatorname", "operator")
            val customerCol = findColumnIndex(headerIndexMap, "customerinfo", "customer")
            val rawBdtCol = findColumnIndex(headerIndexMap, "rawtotal(bdt)", "rawtotalbdt", "rawtotal", "subtotal")
            val rawPaisaCol = findColumnIndex(headerIndexMap, "rawtotalpaisa", "rawpaisa")
            val roundedBdtCol = findColumnIndex(headerIndexMap, "roundedtotal(bdt)", "roundedtotalbdt", "roundedtotal", "total(bdt)", "total")
            val footerNoteCol = findColumnIndex(headerIndexMap, "footernote", "note")
            val createdAtCol = findColumnIndex(headerIndexMap, "createdat")
            val updatedAtCol = findColumnIndex(headerIndexMap, "updatedat")
            val itemsSummaryCol = findColumnIndex(headerIndexMap, "itemssummary", "items", "itemspurchased")

            val parsedTransactions = mutableListOf<ParsedTransaction>()
            val validationErrors = mutableListOf<String>()
            var invalidRowCount = 0
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

            // Parse data rows (Row 2+)
            for (rowIdx in 1 until sheet1Rows.size) {
                val rowMap = sheet1Rows[rowIdx]
                if (rowMap.isEmpty() || rowMap.values.all { it.isBlank() }) {
                    continue // skip empty rows
                }

                val rowNumDisplay = rowIdx + 1
                val rawOrderId = (orderIdCol?.let { rowMap[it] } ?: "").trim()
                val rawGlobalId = (globalIdCol?.let { rowMap[it] } ?: "").trim()

                // Validate required ID
                if (rawOrderId.isBlank() && rawGlobalId.isBlank()) {
                    invalidRowCount++
                    validationErrors.add("Row $rowNumDisplay: Missing Order ID and Global Transaction ID.")
                    continue
                }

                val globalId = if (rawGlobalId.isNotBlank()) rawGlobalId else UUID.randomUUID().toString()
                val orderId = if (rawOrderId.isNotBlank()) rawOrderId else "INV-${globalId.take(8)}"
                val branchCode = (branchCol?.let { rowMap[it] } ?: "").trim().ifBlank { "01" }

                // Parse & Validate Amounts
                var rawPaisa = 0L
                val rawPaisaStr = rawPaisaCol?.let { rowMap[it] }?.trim()
                val rawBdtStr = rawBdtCol?.let { rowMap[it] }?.trim()
                val roundedBdtStr = roundedBdtCol?.let { rowMap[it] }?.trim()

                if (!rawPaisaStr.isNullOrBlank() && rawPaisaStr.toLongOrNull() != null) {
                    rawPaisa = rawPaisaStr.toLong()
                } else if (!rawBdtStr.isNullOrBlank()) {
                    rawPaisa = try {
                        FinancialCalculationEngine.parseBdtToPaisa(rawBdtStr)
                    } catch (e: Exception) {
                        -1L
                    }
                } else if (!roundedBdtStr.isNullOrBlank()) {
                    val rBdt = roundedBdtStr.toLongOrNull() ?: -1L
                    rawPaisa = if (rBdt >= 0) rBdt * 100L else -1L
                }

                // Check for invalid or negative amount
                if (rawPaisa < 0L) {
                    invalidRowCount++
                    validationErrors.add("Row $rowNumDisplay: Invalid or negative total amount.")
                    continue
                }

                var roundedBdt = 0L
                if (!roundedBdtStr.isNullOrBlank() && roundedBdtStr.toLongOrNull() != null) {
                    roundedBdt = roundedBdtStr.toLong()
                } else {
                    roundedBdt = FinancialCalculationEngine.roundPaisaToWholeBdt(rawPaisa)
                }

                // Parse Date / Time
                var dateTime = 0L
                val epochStr = dateTimeEpochCol?.let { rowMap[it] }?.trim()
                if (!epochStr.isNullOrBlank() && epochStr.toLongOrNull() != null) {
                    dateTime = epochStr.toLong()
                } else {
                    val dateStr = dateTimeStrCol?.let { rowMap[it] }?.trim() ?: ""
                    dateTime = try {
                        dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                }

                val operatorId = (operatorIdCol?.let { rowMap[it] } ?: "").trim().ifBlank { "IMPORT" }
                val operatorName = (operatorNameCol?.let { rowMap[it] } ?: "").trim().ifBlank { "Branch Import" }
                val customerInfo = (customerCol?.let { rowMap[it] } ?: "").trim().ifBlank { "External Customer" }
                val footerNote = (footerNoteCol?.let { rowMap[it] } ?: "").trim().ifBlank { "Multi-Branch Sync" }
                val createdAt = createdAtCol?.let { rowMap[it] }?.trim()?.toLongOrNull() ?: dateTime
                val updatedAt = updatedAtCol?.let { rowMap[it] }?.trim()?.toLongOrNull() ?: dateTime

                val invoice = InvoiceEntity(
                    orderId = orderId,
                    globalTransactionId = globalId,
                    branchCode = branchCode,
                    dateTime = dateTime,
                    operatorId = operatorId,
                    operatorName = operatorName,
                    customerInfo = customerInfo,
                    rawTotalPaisa = rawPaisa,
                    roundedTotalBdt = roundedBdt,
                    footerNote = footerNote,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )

                // Build initial line items from items summary if available
                val lines = mutableListOf<InvoiceLineEntity>()
                val itemsSummary = (itemsSummaryCol?.let { rowMap[it] } ?: "").trim()
                if (itemsSummary.isNotBlank()) {
                    val parts = itemsSummary.split(";")
                    parts.forEachIndexed { idx, part ->
                        val trimmed = part.trim()
                        if (trimmed.isNotBlank()) {
                            lines.add(
                                InvoiceLineEntity(
                                    orderId = orderId,
                                    itemId = "item-$idx",
                                    itemName = trimmed,
                                    itemType = "Product",
                                    unit = "Unit",
                                    quantityStr = "1",
                                    unitPricePaisa = rawPaisa,
                                    lineTotalPaisa = rawPaisa,
                                    sortOrder = idx,
                                    createdAt = createdAt
                                )
                            )
                        }
                    }
                }

                parsedTransactions.add(ParsedTransaction(invoice, lines))
            }

            // 3. Parse Sheet 2 (Invoice Items) if present
            val sheet2Bytes = entries["xl/worksheets/sheet2.xml"]
            if (sheet2Bytes != null) {
                val sheet2Xml = String(sheet2Bytes, Charsets.UTF_8)
                val sheet2Rows = parseWorksheetRows(sheet2Xml, sharedStrings)
                if (sheet2Rows.size > 1) {
                    val itemHeaderMap = mutableMapOf<String, Int>()
                    for ((colIdx, headerText) in sheet2Rows.first()) {
                        itemHeaderMap[normalizeHeader(headerText)] = colIdx
                    }

                    val itemGlobalIdCol = findColumnIndex(itemHeaderMap, "globaltransactionid", "globalid", "transactionid")
                    val itemOrderIdCol = findColumnIndex(itemHeaderMap, "orderid", "invoicenumber", "invoiceid")
                    val itemIdCol = findColumnIndex(itemHeaderMap, "itemid")
                    val itemNameCol = findColumnIndex(itemHeaderMap, "itemname", "name", "item")
                    val itemTypeCol = findColumnIndex(itemHeaderMap, "itemtype", "type")
                    val itemUnitCol = findColumnIndex(itemHeaderMap, "unit")
                    val itemQtyCol = findColumnIndex(itemHeaderMap, "quantity", "qty")
                    val itemUnitPricePaisaCol = findColumnIndex(itemHeaderMap, "unitpricepaisa", "unitprice")
                    val itemLineTotalPaisaCol = findColumnIndex(itemHeaderMap, "linetotalpaisa", "linetotal")
                    val itemSortCol = findColumnIndex(itemHeaderMap, "sortorder")
                    val itemCreatedAtCol = findColumnIndex(itemHeaderMap, "createdat")

                    val itemsByOrderId = mutableMapOf<String, MutableList<InvoiceLineEntity>>()
                    val itemsByGlobalId = mutableMapOf<String, MutableList<InvoiceLineEntity>>()

                    for (rowIdx in 1 until sheet2Rows.size) {
                        val rowMap = sheet2Rows[rowIdx]
                        if (rowMap.isEmpty() || rowMap.values.all { it.isBlank() }) continue

                        val gId = itemGlobalIdCol?.let { rowMap[it] }?.trim() ?: ""
                        val oId = itemOrderIdCol?.let { rowMap[it] }?.trim() ?: ""
                        val name = itemNameCol?.let { rowMap[it] }?.trim() ?: "Item"
                        val type = itemTypeCol?.let { rowMap[it] }?.trim() ?: "Product"
                        val unit = itemUnitCol?.let { rowMap[it] }?.trim() ?: "Unit"
                        val qty = itemQtyCol?.let { rowMap[it] }?.trim() ?: "1"

                        var unitPricePaisa = 0L
                        itemUnitPricePaisaCol?.let {
                            val v = rowMap[it]?.trim() ?: ""
                            unitPricePaisa = v.toLongOrNull() ?: FinancialCalculationEngine.parseBdtToPaisa(v)
                        }

                        var lineTotalPaisa = 0L
                        itemLineTotalPaisaCol?.let {
                            val v = rowMap[it]?.trim() ?: ""
                            lineTotalPaisa = v.toLongOrNull() ?: FinancialCalculationEngine.parseBdtToPaisa(v)
                        }

                        val sortOrder = itemSortCol?.let { rowMap[it] }?.trim()?.toIntOrNull() ?: 0
                        val createdAt = itemCreatedAtCol?.let { rowMap[it] }?.trim()?.toLongOrNull() ?: System.currentTimeMillis()
                        val itemId = itemIdCol?.let { rowMap[it] }?.trim()?.ifBlank { "item-$rowIdx" } ?: "item-$rowIdx"

                        val lineEntity = InvoiceLineEntity(
                            orderId = oId,
                            itemId = itemId,
                            itemName = name,
                            itemType = type,
                            unit = unit,
                            quantityStr = qty,
                            unitPricePaisa = unitPricePaisa,
                            lineTotalPaisa = lineTotalPaisa,
                            sortOrder = sortOrder,
                            createdAt = createdAt
                        )

                        if (oId.isNotBlank()) {
                            itemsByOrderId.getOrPut(oId) { mutableListOf() }.add(lineEntity)
                        }
                        if (gId.isNotBlank()) {
                            itemsByGlobalId.getOrPut(gId) { mutableListOf() }.add(lineEntity)
                        }
                    }

                    // Attach granular items to matching transactions
                    val updatedTransactions = parsedTransactions.map { pt ->
                        val granular = itemsByGlobalId[pt.invoice.globalTransactionId]
                            ?: itemsByOrderId[pt.invoice.orderId]
                        if (!granular.isNullOrEmpty()) {
                            pt.copy(lines = granular.map { it.copy(orderId = pt.invoice.orderId) })
                        } else {
                            pt
                        }
                    }
                    return Result.success(
                        ParseXlsxResult(
                            transactions = updatedTransactions,
                            totalRowsParsed = sheet1Rows.size - 1,
                            invalidRows = invalidRowCount,
                            validationErrors = validationErrors
                        )
                    )
                }
            }

            Result.success(
                ParseXlsxResult(
                    transactions = parsedTransactions,
                    totalRowsParsed = sheet1Rows.size - 1,
                    invalidRows = invalidRowCount,
                    validationErrors = validationErrors
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("XLSX parsing failed: ${e.message}"))
        }
    }

    /**
     * Legacy CSV fallback method maintained for compatibility.
     */
    suspend fun importFromUri(
        context: Context,
        uri: Uri
    ): Result<Pair<List<InvoiceEntity>, List<InvoiceLineEntity>>> = withContext(Dispatchers.IO) {
        val parseResult = parseXlsxFromUri(context, uri)
        if (parseResult.isSuccess) {
            val res = parseResult.getOrThrow()
            val invoices = res.transactions.map { it.invoice }
            val lines = res.transactions.flatMap { it.lines }
            Result.success(Pair(invoices, lines))
        } else {
            Result.failure(parseResult.exceptionOrNull() ?: Exception("Import failed."))
        }
    }

    // --- XML Parsing Helpers ---

    private fun normalizeHeader(text: String): String {
        return text.lowercase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
    }

    private fun findColumnIndex(headerMap: Map<String, Int>, vararg candidates: String): Int? {
        for (candidate in candidates) {
            val direct = headerMap[candidate]
            if (direct != null) return direct
        }
        for (candidate in candidates) {
            for ((key, colIdx) in headerMap) {
                if (key.contains(candidate)) return colIdx
            }
        }
        return null
    }

    fun colLetterToIndex(colRef: String): Int {
        var result = 0
        val upper = colRef.uppercase()
        for (i in upper.indices) {
            val ch = upper[i]
            if (ch in 'A'..'Z') {
                result = result * 26 + (ch - 'A' + 1)
            }
        }
        return (result - 1).coerceAtLeast(0)
    }

    fun colIndexToLetter(index: Int): String {
        var temp = index
        val sb = java.lang.StringBuilder()
        while (temp >= 0) {
            sb.insert(0, ('A'.code + (temp % 26)).toChar())
            temp = (temp / 26) - 1
        }
        return sb.toString()
    }

    private fun parseSharedStrings(xmlContent: String): List<String> {
        val list = mutableListOf<String>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlContent))

        var eventType = parser.eventType
        var inSi = false
        var inT = false
        val sb = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "si" -> {
                            inSi = true
                            sb.setLength(0)
                        }
                        "t" -> inT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inSi && inT) {
                        sb.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        "t" -> inT = false
                        "si" -> {
                            inSi = false
                            list.add(sb.toString())
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return list
    }

    private fun parseWorksheetRows(xmlContent: String, sharedStrings: List<String>): List<Map<Int, String>> {
        val rows = mutableListOf<Map<Int, String>>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlContent))

        var eventType = parser.eventType
        var currentRowMap: MutableMap<Int, String>? = null
        var currentCellCol = 0
        var currentCellType = ""
        var inCell = false
        var inValue = false
        var inText = false
        val currentTextBuilder = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.lowercase()
                    when (tag) {
                        "row" -> {
                            currentRowMap = mutableMapOf()
                            currentCellCol = 0
                        }
                        "c" -> {
                            inCell = true
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            val cellRef = parser.getAttributeValue(null, "r")
                            if (cellRef != null) {
                                val letters = cellRef.takeWhile { it.isLetter() }
                                currentCellCol = colLetterToIndex(letters)
                            }
                            currentTextBuilder.setLength(0)
                        }
                        "v" -> {
                            inValue = true
                            currentTextBuilder.setLength(0)
                        }
                        "t" -> {
                            inText = true
                            currentTextBuilder.setLength(0)
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText || inValue) {
                        currentTextBuilder.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.lowercase()
                    when (tag) {
                        "v" -> {
                            inValue = false
                            if (inCell && currentRowMap != null) {
                                val rawVal = currentTextBuilder.toString().trim()
                                val cellValue = if (currentCellType == "s") {
                                    val sstIndex = rawVal.toIntOrNull()
                                    if (sstIndex != null && sstIndex in sharedStrings.indices) {
                                        sharedStrings[sstIndex]
                                    } else {
                                        rawVal
                                    }
                                } else {
                                    rawVal
                                }
                                currentRowMap[currentCellCol] = cellValue
                            }
                        }
                        "t" -> {
                            inText = false
                            if (inCell && currentRowMap != null) {
                                currentRowMap[currentCellCol] = currentTextBuilder.toString()
                            }
                        }
                        "c" -> {
                            inCell = false
                            currentCellCol++
                        }
                        "row" -> {
                            if (currentRowMap != null) {
                                rows.add(currentRowMap)
                                currentRowMap = null
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return rows
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private const val CONTENT_TYPES_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private const val RELS_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_RELS_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
</Relationships>"""

    private const val WORKBOOK_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Transactions" sheetId="1" r:id="rId1"/>
    <sheet name="Invoice_Items" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>"""
}
