package com.example.pos

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.util.DateTimeUtils
import com.example.pos.data.Localization
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.repository.HistoryRepositoryImpl
import com.example.pos.data.repository.HistorySortOption
import com.example.pos.ui.screens.ReceiptDetailsDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoucherDetailsAndExportFilterRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: PosDatabase
    private lateinit var historyRepo: HistoryRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        historyRepo = HistoryRepositoryImpl(db.invoiceDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testReceiptDetailsDialog_verticalButtonArrangement_Bangla() {
        val invoice = InvoiceEntity(
            orderId = "INV-2026-001",
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Manager",
            customerInfo = "01711223344",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L,
            footerNote = "ধন্যবাদ আবার আসবেন"
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = "INV-2026-001",
                itemId = "OCT-1",
                itemName = "অকটেন",
                itemType = "FUEL",
                unit = "লিটার",
                quantityStr = "4.0",
                unitPricePaisa = 12500L,
                lineTotalPaisa = 50000L
            )
        )

        composeTestRule.setContent {
            ReceiptDetailsDialog(
                invoice = invoice,
                lines = lines,
                receiptText = "=== RECEIPT ===",
                language = "bn",
                onClose = {},
                onReprint = {},
                onSharePdf = {},
                onDelete = {}
            )
        }

        // 1. Verify buttons exist and display
        val deleteNode = composeTestRule.onNodeWithTag("dialog_delete_invoice_btn")
        val pdfNode = composeTestRule.onNodeWithTag("dialog_share_pdf_btn")

        deleteNode.assertIsDisplayed()
        pdfNode.assertIsDisplayed()

        // 2. Verify VERTICAL layout: Delete button must appear above PDF button
        val deleteBounds = deleteNode.getBoundsInRoot()
        val pdfBounds = pdfNode.getBoundsInRoot()

        assertTrue(
            "Delete button top (${deleteBounds.top}) must be above PDF button top (${pdfBounds.top})",
            deleteBounds.top < pdfBounds.top
        )
        assertTrue(
            "Delete button bottom (${deleteBounds.bottom}) must be <= PDF button top (${pdfBounds.top})",
            deleteBounds.bottom <= pdfBounds.top
        )

        // 3. Both buttons should use available width properly (near identical width)
        val deleteWidth = (deleteBounds.right - deleteBounds.left).value
        val pdfWidth = (pdfBounds.right - pdfBounds.left).value
        assertEquals(deleteWidth, pdfWidth, 2f)
    }

    @Test
    fun testReceiptDetailsDialog_verticalButtonArrangement_English() {
        val invoice = InvoiceEntity(
            orderId = "INV-2026-002",
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Admin",
            customerInfo = "01811223344",
            rawTotalPaisa = 25000L,
            roundedTotalBdt = 250L,
            footerNote = "Thank you"
        )

        composeTestRule.setContent {
            ReceiptDetailsDialog(
                invoice = invoice,
                lines = emptyList(),
                receiptText = "=== RECEIPT ===",
                language = "en",
                onClose = {},
                onReprint = {},
                onSharePdf = {},
                onDelete = {}
            )
        }

        val deleteNode = composeTestRule.onNodeWithTag("dialog_delete_invoice_btn")
        val pdfNode = composeTestRule.onNodeWithTag("dialog_share_pdf_btn")

        deleteNode.assertIsDisplayed()
        pdfNode.assertIsDisplayed()

        val deleteBounds = deleteNode.getBoundsInRoot()
        val pdfBounds = pdfNode.getBoundsInRoot()

        assertTrue(
            "Delete button should be strictly above PDF button in English layout",
            deleteBounds.bottom <= pdfBounds.top
        )
    }

    @Test
    fun testDateRangeParsingAndValidation() {
        // Valid date parsing
        val validDate = DateTimeUtils.parseDateDdMmYyyy("18-11-2025")
        assertNotNull("18-11-2025 should be parsed successfully", validDate)

        val cal = Calendar.getInstance().apply { time = validDate!! }
        assertEquals(18, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH))
        assertEquals(2025, cal.get(Calendar.YEAR))

        // Invalid date formats
        assertNull(DateTimeUtils.parseDateDdMmYyyy("2025-11-18"))
        assertNull(DateTimeUtils.parseDateDdMmYyyy("18/11/2025"))
        assertNull(DateTimeUtils.parseDateDdMmYyyy("invalid"))
        assertNull(DateTimeUtils.parseDateDdMmYyyy("32-01-2025")) // Day > 31
        assertNull(DateTimeUtils.parseDateDdMmYyyy("15-13-2025")) // Month > 12

        // Date range validation: From cannot be after To
        val fromDate = DateTimeUtils.parseDateDdMmYyyy("20-11-2025")!!
        val toDate = DateTimeUtils.parseDateDdMmYyyy("18-11-2025")!!
        assertTrue("From date after To date must be detected as invalid range", fromDate.time > toDate.time)
    }

    @Test
    fun testDatabaseSideDateRangeExportQuery() = runBlocking {
        val invoiceDao = db.invoiceDao()

        // Create 3 invoices with specific dates
        val nov10 = Calendar.getInstance().apply {
            set(2025, Calendar.NOVEMBER, 10, 12, 0, 0)
        }.timeInMillis

        val nov18 = Calendar.getInstance().apply {
            set(2025, Calendar.NOVEMBER, 18, 14, 30, 0)
        }.timeInMillis

        val nov25 = Calendar.getInstance().apply {
            set(2025, Calendar.NOVEMBER, 25, 9, 15, 0)
        }.timeInMillis

        invoiceDao.insertInvoice(
            InvoiceEntity(
                orderId = "INV-NOV-10",
                dateTime = nov10,
                operatorId = "OP",
                operatorName = "Staff",
                customerInfo = "Cust 1",
                rawTotalPaisa = 10000L,
                roundedTotalBdt = 100L
            )
        )
        invoiceDao.insertInvoice(
            InvoiceEntity(
                orderId = "INV-NOV-18",
                dateTime = nov18,
                operatorId = "OP",
                operatorName = "Staff",
                customerInfo = "Cust 2",
                rawTotalPaisa = 20000L,
                roundedTotalBdt = 200L
            )
        )
        invoiceDao.insertInvoice(
            InvoiceEntity(
                orderId = "INV-NOV-25",
                dateTime = nov25,
                operatorId = "OP",
                operatorName = "Staff",
                customerInfo = "Cust 3",
                rawTotalPaisa = 30000L,
                roundedTotalBdt = 300L
            )
        )

        // 1. Full export (no date range / 0 to Long.MAX_VALUE)
        val allInvoices = invoiceDao.getInvoicesForExport(0L, Long.MAX_VALUE)
        assertEquals(3, allInvoices.size)

        // 2. Filtered date range: Nov 15 to Nov 20 (Should ONLY match INV-NOV-18)
        val startRange = Calendar.getInstance().apply {
            set(2025, Calendar.NOVEMBER, 15, 0, 0, 0)
        }.timeInMillis
        val endRange = Calendar.getInstance().apply {
            set(2025, Calendar.NOVEMBER, 20, 23, 59, 59)
        }.timeInMillis

        val rangeInvoices = invoiceDao.getInvoicesForExport(startRange, endRange)
        assertEquals(1, rangeInvoices.size)
        assertEquals("INV-NOV-18", rangeInvoices.first().orderId)

        // 3. Boundary inclusiveness: Nov 10 to Nov 18
        val boundaryInvoices = invoiceDao.getInvoicesForExport(nov10, nov18)
        assertEquals(2, boundaryInvoices.size)
        assertTrue(boundaryInvoices.any { it.orderId == "INV-NOV-10" })
        assertTrue(boundaryInvoices.any { it.orderId == "INV-NOV-18" })
    }

    @Test
    fun testHistoryFilteringSortingAndSafeUpdate() = runBlocking {
        val invoiceDao = db.invoiceDao()

        // Populate sample invoices
        val now = System.currentTimeMillis()
        invoiceDao.insertInvoice(
            InvoiceEntity(
                orderId = "INV-001",
                dateTime = now - 10000,
                operatorId = "OP-1",
                operatorName = "Rahim",
                customerInfo = "01700112233",
                rawTotalPaisa = 50000L,
                roundedTotalBdt = 500L,
                footerNote = "Initial Note"
            )
        )
        invoiceDao.insertInvoice(
            InvoiceEntity(
                orderId = "INV-002",
                dateTime = now,
                operatorId = "OP-2",
                operatorName = "Karim",
                customerInfo = "01800112233",
                rawTotalPaisa = 120000L,
                roundedTotalBdt = 1200L,
                footerNote = "Second Note"
            )
        )

        // 1. Dynamic Search suggestions
        val suggestions = historyRepo.getMatchingOrderIds("INV")
        assertTrue("Suggestions should contain INV-001 and INV-002", suggestions.contains("INV-001"))
        assertTrue("Suggestions should contain INV-002", suggestions.contains("INV-002"))

        // Exact search
        val filtered = historyRepo.getInvoicesPaged(
            query = "INV-001",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = Long.MAX_VALUE,
            sortOption = HistorySortOption.DATE_DESC,
            limit = 10,
            offset = 0
        ).first()
        assertEquals(1, filtered.size)
        assertEquals("INV-001", filtered.first().orderId)

        // 2. Sorting by Amount DESC and ASC
        val sortDesc = historyRepo.getInvoicesPaged(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = Long.MAX_VALUE,
            sortOption = HistorySortOption.AMOUNT_DESC,
            limit = 10,
            offset = 0
        ).first()
        assertEquals("INV-002", sortDesc.first().orderId)

        val sortAsc = historyRepo.getInvoicesPaged(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = Long.MAX_VALUE,
            sortOption = HistorySortOption.AMOUNT_ASC,
            limit = 10,
            offset = 0
        ).first()
        assertEquals("INV-001", sortAsc.first().orderId)

        // 3. Safe Update of Invoice Details (preserve historical financials)
        invoiceDao.updateInvoiceCustomerAndNote(
            orderId = "INV-001",
            customerInfo = "Updated Customer 01799887766",
            footerNote = "Updated Safe Remark"
        )
        val updated = invoiceDao.getInvoiceById("INV-001")
        assertNotNull(updated)
        assertEquals("Updated Customer 01799887766", updated!!.customerInfo)
        assertEquals("Updated Safe Remark", updated.footerNote)
        // Verify historical financial amounts remained 100% UNTOUCHED
        assertEquals(50000L, updated.rawTotalPaisa)
        assertEquals(500L, updated.roundedTotalBdt)

        // 4. Safe Delete
        invoiceDao.deleteFullOrder("INV-001")
        assertNull("INV-001 should be deleted", invoiceDao.getInvoiceById("INV-001"))
    }

    @Test
    fun testLocalizationCompletenessForNewFeatures() {
        val keysToCheck = listOf(
            Localization.Key.DATE_FILTER_SIX_MONTHS,
            Localization.Key.CUSTOM_DATE_FROM,
            Localization.Key.CUSTOM_DATE_TO,
            Localization.Key.INVALID_DATE_FORMAT,
            Localization.Key.DATE_RANGE_ORDER_ERROR,
            Localization.Key.SELECT_CUSTOM_DATE_RANGE,
            Localization.Key.ENTER_DATE_RANGE_HELP,
            Localization.Key.BTN_APPLY,
            Localization.Key.DATA_EXPORT_SECTION_TITLE,
            Localization.Key.DATA_EXPORT_SECTION_DESC,
            Localization.Key.EXPORT_FULL_DATA_CONFIRM_TITLE,
            Localization.Key.EXPORT_FULL_DATA_CONFIRM_MSG,
            Localization.Key.CONFIRM_EXPORT,
            Localization.Key.BTN_EXPORT_XLSX,
            Localization.Key.EXPORT_DATE_RANGE_ERROR,
            Localization.Key.BTN_UPDATE_INVOICE,
            Localization.Key.EDIT_INVOICE_TITLE,
            Localization.Key.EDIT_INVOICE_CUSTOMER_LABEL,
            Localization.Key.EDIT_INVOICE_NOTE_LABEL,
            Localization.Key.EDIT_INVOICE_READONLY_NOTICE,
            Localization.Key.SAVE_CHANGES,
            Localization.Key.INVOICE_UPDATE_SUCCESS,
            Localization.Key.SEARCH_VOUCHER_SUGGESTIONS,
            Localization.Key.SEARCH_BY_ORDER_OR_VOUCHER
        )

        for (key in keysToCheck) {
            val enText = Localization.get(key, "en")
            val bnText = Localization.get(key, "bn")

            assertTrue("Key $key must have non-blank English text", enText.isNotBlank())
            assertTrue("Key $key must have non-blank Bangla text", bnText.isNotBlank())
            assertFalse("Key $key English and Bangla should be properly distinct", enText == bnText)
        }
    }
}
