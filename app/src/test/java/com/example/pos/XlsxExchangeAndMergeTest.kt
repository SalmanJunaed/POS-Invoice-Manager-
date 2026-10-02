package com.example.pos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.repository.PosRepository
import com.example.pos.excel.ExcelExchangeEngine
import com.example.pos.excel.ParseXlsxResult
import com.example.pos.excel.ParsedTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class XlsxExchangeAndMergeTest {

    private lateinit var db: PosDatabase
    private lateinit var repository: PosRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PosRepository(context, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testExportAndParseXlsxCycle() = runBlocking {
        val invoice1 = InvoiceEntity(
            orderId = "DH-2026-0001",
            globalTransactionId = "GLB-TX-10001",
            branchCode = "01",
            dateTime = 1773000000000L,
            operatorId = "OP-10",
            operatorName = "Rahim Mia",
            customerInfo = "01711000001",
            rawTotalPaisa = 125050L,
            roundedTotalBdt = 1251L
        )

        val line1 = InvoiceLineEntity(
            orderId = "DH-2026-0001",
            itemId = "prod-1",
            itemName = "LPG Cylinder 12kg",
            itemType = "Product",
            unit = "cylinder",
            quantityStr = "1",
            unitPricePaisa = 125050L,
            lineTotalPaisa = 125050L,
            sortOrder = 0,
            createdAt = 1773000000000L
        )

        val xlsxBytes = ExcelExchangeEngine.exportToByteArray(
            invoices = listOf(invoice1),
            branchCode = "01",
            lineItemsFetcher = { listOf(line1) }
        )

        assertTrue("Exported XLSX should not be empty", xlsxBytes.isNotEmpty())

        // Parse generated XLSX
        val parseResult = ExcelExchangeEngine.parseXlsx(ByteArrayInputStream(xlsxBytes))
        assertTrue("Parsing valid XLSX should succeed", parseResult.isSuccess)

        val parsed = parseResult.getOrThrow()
        assertEquals("Should parse 1 transaction", 1, parsed.transactions.size)
        assertEquals("0 invalid rows", 0, parsed.invalidRows)

        val tx = parsed.transactions[0]
        assertEquals("DH-2026-0001", tx.invoice.orderId)
        assertEquals("GLB-TX-10001", tx.invoice.globalTransactionId)
        assertEquals("01", tx.invoice.branchCode)
        assertEquals(125050L, tx.invoice.rawTotalPaisa)
        assertEquals(1251L, tx.invoice.roundedTotalBdt)
        assertEquals("01711000001", tx.invoice.customerInfo)
        assertEquals("Rahim Mia", tx.invoice.operatorName)
        assertEquals(1, tx.lines.size)
        assertEquals("LPG Cylinder 12kg", tx.lines[0].itemName)
        assertEquals(125050L, tx.lines[0].unitPricePaisa)
    }

    @Test
    fun testIdempotentImportAndDuplicatePrevention() = runBlocking {
        val invoice = InvoiceEntity(
            orderId = "CTG-2026-0042",
            globalTransactionId = "GLB-TX-CTG-0042",
            branchCode = "02",
            dateTime = 1773100000000L,
            operatorId = "OP-20",
            operatorName = "Karim Uddin",
            customerInfo = "01999888777",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L
        )
        val line = InvoiceLineEntity(
            orderId = "CTG-2026-0042",
            itemId = "prod-2",
            itemName = "Gas Pipe 2m",
            itemType = "Product",
            unit = "pcs",
            quantityStr = "1",
            unitPricePaisa = 50000L,
            lineTotalPaisa = 50000L,
            sortOrder = 0,
            createdAt = 1773100000000L
        )

        val parsedResult = ParseXlsxResult(
            transactions = listOf(ParsedTransaction(invoice = invoice, lines = listOf(line))),
            totalRowsParsed = 1,
            invalidRows = 0,
            validationErrors = emptyList()
        )

        // 1st Import
        val firstSummary = repository.importAndMergeTransactions(parsedResult)
        assertEquals("First import should insert 1 transaction", 1, firstSummary.importedCount)
        assertEquals(0, firstSummary.duplicateSkippedCount)
        assertEquals(0, firstSummary.conflictCount)
        assertEquals(1, db.invoiceDao().getTotalInvoiceCountFlow().first())

        // 2nd Import (Identical data) - Must be idempotent!
        val secondSummary = repository.importAndMergeTransactions(parsedResult)
        assertEquals("Second import must NOT insert duplicate", 0, secondSummary.importedCount)
        assertEquals("Second import must detect duplicate and skip", 1, secondSummary.duplicateSkippedCount)
        assertEquals(0, secondSummary.conflictCount)
        assertEquals("Invoice count must remain strictly 1", 1, db.invoiceDao().getTotalInvoiceCountFlow().first())
    }

    @Test
    fun testConflictDetectionPreservesLocalRecord() = runBlocking {
        val originalInvoice = InvoiceEntity(
            orderId = "SYL-2026-0005",
            globalTransactionId = "GLB-TX-SYL-0005",
            branchCode = "03",
            dateTime = 1773200000000L,
            operatorId = "OP-30",
            operatorName = "Local Operator",
            customerInfo = "01700000000",
            rawTotalPaisa = 100000L,
            roundedTotalBdt = 1000L
        )
        val localLine = InvoiceLineEntity(
            orderId = "SYL-2026-0005",
            itemId = "prod-loc",
            itemName = "Local Item",
            itemType = "Product",
            unit = "pcs",
            quantityStr = "1",
            unitPricePaisa = 100000L,
            lineTotalPaisa = 100000L,
            sortOrder = 0
        )
        repository.importAndMergeTransactions(
            ParseXlsxResult(
                transactions = listOf(ParsedTransaction(originalInvoice, listOf(localLine))),
                totalRowsParsed = 1,
                invalidRows = 0,
                validationErrors = emptyList()
            )
        )

        // Now import conflicting transaction with SAME globalTransactionId but DIFFERENT total
        val conflictingInvoice = originalInvoice.copy(
            rawTotalPaisa = 999900L,
            roundedTotalBdt = 9999L,
            customerInfo = "01799999999"
        )
        val conflictingLine = InvoiceLineEntity(
            orderId = "SYL-2026-0005",
            itemId = "prod-diff",
            itemName = "Different Item",
            itemType = "Product",
            unit = "pcs",
            quantityStr = "1",
            unitPricePaisa = 999900L,
            lineTotalPaisa = 999900L,
            sortOrder = 0
        )

        val conflictSummary = repository.importAndMergeTransactions(
            ParseXlsxResult(
                transactions = listOf(ParsedTransaction(conflictingInvoice, listOf(conflictingLine))),
                totalRowsParsed = 1,
                invalidRows = 0,
                validationErrors = emptyList()
            )
        )

        assertEquals("Conflict should not be imported", 0, conflictSummary.importedCount)
        assertEquals("Conflict must be registered", 1, conflictSummary.conflictCount)
        assertEquals(1, conflictSummary.conflicts.size)
        assertEquals("SYL-2026-0005", conflictSummary.conflicts[0].orderId)

        // Verify local record was preserved unchanged
        val storedInvoice = db.invoiceDao().getInvoiceByGlobalId("GLB-TX-SYL-0005")
        assertNotNull(storedInvoice)
        assertEquals("Local record raw total must be preserved", 100000L, storedInvoice!!.rawTotalPaisa)
        assertEquals("Local record customer must be preserved", "01700000000", storedInvoice.customerInfo)
    }

    @Test
    fun testCorruptedOrInvalidXlsxRejected() {
        val corruptedBytes = "This is not an excel zip file at all".toByteArray()
        val result = ExcelExchangeEngine.parseXlsx(ByteArrayInputStream(corruptedBytes))
        assertFalse("Corrupted input must fail parsing", result.isSuccess)
    }
}
