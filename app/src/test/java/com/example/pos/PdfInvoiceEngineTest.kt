package com.example.pos

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.error.PosResult
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.modules.pdf.PdfInvoiceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.ByteArrayOutputStream
import java.io.File

@Implements(PdfDocument::class)
class ShadowRobolectricPdfDocument {
    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        val pageConstructor = PdfDocument.Page::class.java.getDeclaredConstructor(Canvas::class.java, PdfDocument.PageInfo::class.java)
        pageConstructor.isAccessible = true
        return pageConstructor.newInstance(Canvas(), pageInfo)
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {
        // No-op in JVM shadow
    }

    @Implementation
    fun writeTo(out: java.io.OutputStream) {
        out.write("%PDF-1.4\n%mock-pdf-content-for-tests\n%%EOF\n".toByteArray(Charsets.UTF_8))
    }

    @Implementation
    fun close() {
        // No-op in JVM shadow
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowRobolectricPdfDocument::class])
class PdfInvoiceEngineTest {

    private lateinit var context: Context
    private lateinit var sampleHeader: CompanyHeaderEntity
    private lateinit var sampleInvoice: InvoiceEntity

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        sampleHeader = CompanyHeaderEntity(
            companyName = "মেসার্স রাকিব ফিলিং স্টেশন এন্ড এল পি জি অটোগ্যাস ফিলিং স্টেশন",
            branchName = "প্রধান শাখা",
            branchNumber = "01",
            address = "মহাদেবপুর, নওগাঁ",
            phoneNumber = "01700000000"
        )
        sampleInvoice = InvoiceEntity(
            orderId = "DH-2026-PDF01",
            globalTransactionId = "TXN-TEST-001",
            branchCode = "01",
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-101",
            operatorName = "Rakib Operator",
            customerInfo = "01711223344",
            rawTotalPaisa = 136500L,
            roundedTotalBdt = 1365L,
            footerNote = "Thank you for your business!"
        )
    }

    private fun createDummyLines(count: Int): List<InvoiceLineEntity> {
        return (1..count).map { i ->
            InvoiceLineEntity(
                orderId = sampleInvoice.orderId,
                itemId = "ITEM-$i",
                itemName = "Test Product Item #$i",
                itemType = "GOODS",
                unit = "Pcs",
                quantityStr = "1.0",
                unitPricePaisa = 10000L,
                lineTotalPaisa = 10000L,
                sortOrder = i,
                createdAt = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun testPaginationCalculation_SinglePageSmallList() {
        // 0 lines -> empty invoice, fits on 1 page
        assertEquals(1, PdfInvoiceEngine.calculatePageCount(0))

        // 1 line -> fits easily on 1 page
        assertEquals(1, PdfInvoiceEngine.calculatePageCount(1))

        // 10 lines -> 210 + 10*22 = 430. 430 + 110 (summary) = 540 <= 750 -> 1 page
        assertEquals(1, PdfInvoiceEngine.calculatePageCount(10))

        // 19 lines -> 210 + 19*22 = 628. 628 + 110 = 738 <= 750 -> 1 page
        assertEquals(1, PdfInvoiceEngine.calculatePageCount(19))
    }

    @Test
    fun testPaginationCalculation_LinePageBudgetingSummaryOverflow() {
        // Page 1 start Y = 210, MAX_TABLE_Y = 750, ROW_HEIGHT = 22, SUMMARY_HEIGHT = 110
        // Available space for items on page 1: (750 - 210) = 540
        // Max items that can fit on page 1 alone: 540 / 22 = 24 items (210 + 24*22 = 738 <= 750)
        // With 20 items:
        // currentY after items = 210 + 20*22 = 650.
        // Summary check: 650 + 110 = 760 > 750 (overflows page 1!).
        // Thus, summary overflows to Page 2 -> 2 pages total.
        assertEquals(2, PdfInvoiceEngine.calculatePageCount(20))
        assertEquals(2, PdfInvoiceEngine.calculatePageCount(24))
    }

    @Test
    fun testPaginationCalculation_MultiPageHandling() {
        // 25 items:
        // 24 items fit on page 1 (Y = 738).
        // 25th item overflows (738 + 22 = 760 > 750) -> page 2 starts.
        // Page 2 start Y = 90. Item 25 at Y = 90 -> currentY = 112.
        // 112 + 110 = 222 <= 750 -> fits on page 2.
        // Total = 2 pages.
        assertEquals(2, PdfInvoiceEngine.calculatePageCount(25))

        // Page 2 capacity: (750 - 90) = 660. 660 / 22 = 30 items.
        // Total items that can fit on Page 1 (24 items) + Page 2 (30 items) = 54 items.
        // If 54 items: 24 on page 1, 30 on page 2. Page 2 currentY = 90 + 30*22 = 750.
        // Summary: 750 + 110 = 860 > 750 -> summary pushes to Page 3.
        assertEquals(3, PdfInvoiceEngine.calculatePageCount(54))

        // 60 items:
        // Page 1 has 24 items.
        // Page 2 has 30 items (total 54 items).
        // Page 3 has 6 items (total 60 items) + summary.
        // Total = 3 pages.
        assertEquals(3, PdfInvoiceEngine.calculatePageCount(60))

        // 100 items:
        // Page 1: 24 items (remaining 76)
        // Page 2: 30 items (remaining 46)
        // Page 3: 30 items (remaining 16)
        // Page 4: 16 items (Y = 90 + 16*22 = 442) + summary (442 + 110 = 552 <= 750)
        // Total = 4 pages.
        assertEquals(4, PdfInvoiceEngine.calculatePageCount(100))
    }

    @Test
    fun testGenerateInvoicePdf_SinglePageDocument() {
        val lines = createDummyLines(3)
        val result = PdfInvoiceEngine.generateInvoicePdf(
            context = context,
            company = sampleHeader,
            invoice = sampleInvoice,
            lines = lines
        )

        if (result is PosResult.Error) {
            System.err.println("generateInvoicePdf ERROR: " + result.error.message + " cause: " + (result.error as? com.example.pos.core.error.PosError.UnexpectedError)?.cause)
            (result.error as? com.example.pos.core.error.PosError.UnexpectedError)?.cause?.printStackTrace()
        }
        assertTrue("generateInvoicePdf must succeed: ${(result as? PosResult.Error)?.error?.message}", result is PosResult.Success)
        val pdfFile = (result as PosResult.Success).data
        assertNotNull(pdfFile)
        assertTrue("PDF file must exist", pdfFile.exists())
        assertTrue("PDF file must not be empty", pdfFile.length() > 0)
        assertEquals("Invoice_${sampleInvoice.orderId}.pdf", pdfFile.name)
    }

    @Test
    fun testGenerateInvoicePdf_MultiPageDocumentStream() {
        // Generate with 60 lines (multi-page PDF)
        val lines = createDummyLines(60)
        val outputStream = ByteArrayOutputStream()

        val result = PdfInvoiceEngine.writeInvoicePdfToStream(
            context = context,
            company = sampleHeader,
            invoice = sampleInvoice,
            lines = lines,
            outputStream = outputStream
        )

        assertTrue("writeInvoicePdfToStream must succeed", result is PosResult.Success)
        val bytes = outputStream.toByteArray()
        assertTrue("Output bytes must not be empty", bytes.isNotEmpty())

        // Standard PDF magic header check "%PDF-"
        val header = String(bytes.take(5).toByteArray())
        assertEquals("%PDF-", header)
    }

    @Test
    fun testSharePdfIntent() {
        val dummyFile = File(context.cacheDir, "exports/Invoice_TEST-1.pdf").apply {
            parentFile?.mkdirs()
            writeText("dummy-pdf-content")
        }

        val intent = PdfInvoiceEngine.getSharePdfIntent(context, dummyFile)
        assertNotNull(intent)
        assertEquals("application/pdf", intent.type)
        assertEquals(android.content.Intent.ACTION_SEND, intent.action)
        assertTrue(intent.hasExtra(android.content.Intent.EXTRA_STREAM))
    }
}
