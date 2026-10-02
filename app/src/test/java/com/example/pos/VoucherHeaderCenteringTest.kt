package com.example.pos

import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.printer.EscPosPrinterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoucherHeaderCenteringTest {

    private fun extractHeaderLines(receipt: String, width: Int): List<String> {
        val sep = "=".repeat(width)
        val lines = receipt.lines()
        val firstSepIdx = lines.indexOfFirst { it.trim() == sep }
        assertTrue("First separator must exist", firstSepIdx >= 0)
        val secondSepIdx = lines.drop(firstSepIdx + 1).indexOfFirst { it.trim() == sep }
        assertTrue("Second separator must exist", secondSepIdx >= 0)
        val actualSecondSepIdx = firstSepIdx + 1 + secondSepIdx
        return lines.subList(firstSepIdx + 1, actualSecondSepIdx).filter { it.isNotBlank() }
    }

    private fun assertLineCentered(line: String, width: Int, tolerance: Int = 2) {
        val trimmed = line.trim()
        val visualWidth = EscPosPrinterEngine.getVisualWidth(trimmed)
        assertTrue("Line visual width ($visualWidth) cannot exceed paper width ($width)", visualWidth <= width)
        
        // Count leading spaces
        val leadingSpaces = line.takeWhile { it == ' ' }.length
        val expectedPadding = (width - visualWidth) / 2
        
        // Ensure difference between actual leading spaces and expected padding is within 1-2 chars (for rounding)
        val diff = abs(leadingSpaces - expectedPadding)
        assertTrue(
            "Line '$line' (vWidth=$visualWidth) should have approx $expectedPadding leading spaces on width $width, but had $leadingSpaces (diff=$diff)",
            diff <= tolerance
        )
    }

    @Test
    fun testVoucherHeaderCentering_58mm() {
        val width = 32
        val header = CompanyHeaderEntity(
            companyName = "মেসার্স রাকিব ফিলিং স্টেশন এন্ড এল পি জি অটোগ্যাস ফিলিং স্টেশন",
            branchName = "প্রধান শাখা",
            branchNumber = "01",
            address = "মহাদেবপুর, নওগাঁ",
            phoneNumber = "01700000000"
        )
        val invoice = InvoiceEntity(
            orderId = "DH-2026-TEST",
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Admin",
            customerInfo = "01711000000",
            rawTotalPaisa = 10000L,
            roundedTotalBdt = 100L,
            footerNote = "Thank you\nCome Again"
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = "DH-2026-TEST",
                itemId = "OCT",
                itemName = "Octane",
                itemType = "fuel",
                unit = "L",
                quantityStr = "1.00",
                unitPricePaisa = 10000L,
                lineTotalPaisa = 10000L,
                sortOrder = 1
            )
        )

        val receiptText = EscPosPrinterEngine.generateReceiptText(
            header = header,
            invoice = invoice,
            lines = lines,
            paperSize = "58mm",
            footerLine1 = "Thank you",
            footerLine2 = "Come Again"
        )

        val headerLines = extractHeaderLines(receiptText, width)
        assertTrue("Header should contain multiple lines", headerLines.size >= 4)

        // Verify [Logo] literal does NOT appear anywhere in the receipt
        assertTrue("Literal [Logo] must not appear anywhere in receipt", !receiptText.contains("[Logo]", ignoreCase = true))
        assertTrue("Literal [ LOGO ] must not appear anywhere in receipt", !receiptText.contains("[ LOGO ]"))

        // Verify every line in header is centered
        for (line in headerLines) {
            assertLineCentered(line, width)
        }
    }

    @Test
    fun testVoucherHeaderCentering_80mm() {
        val width = 48
        val header = CompanyHeaderEntity(
            companyName = "মেসার্স রাকিব ফিলিং স্টেশন এন্ড এল পি জি অটোগ্যাস ফিলিং স্টেশন",
            branchName = "প্রধান শাখা",
            branchNumber = "01",
            address = "মহাদেবপুর, নওগাঁ",
            phoneNumber = "01700000000"
        )
        val invoice = InvoiceEntity(
            orderId = "DH-2026-TEST80",
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Admin",
            customerInfo = "01711000000",
            rawTotalPaisa = 10000L,
            roundedTotalBdt = 100L,
            footerNote = "Thank you\nCome Again"
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = "DH-2026-TEST80",
                itemId = "OCT",
                itemName = "Octane",
                itemType = "fuel",
                unit = "L",
                quantityStr = "1.00",
                unitPricePaisa = 10000L,
                lineTotalPaisa = 10000L,
                sortOrder = 1
            )
        )

        val receiptText = EscPosPrinterEngine.generateReceiptText(
            header = header,
            invoice = invoice,
            lines = lines,
            paperSize = "80mm",
            footerLine1 = "Thank you",
            footerLine2 = "Come Again"
        )

        val headerLines = extractHeaderLines(receiptText, width)
        assertTrue("Header should contain multiple lines", headerLines.size >= 4)

        // Verify [Logo] literal does NOT appear anywhere in the receipt
        assertTrue("Literal [Logo] must not appear anywhere in receipt", !receiptText.contains("[Logo]", ignoreCase = true))
        assertTrue("Literal [ LOGO ] must not appear anywhere in receipt", !receiptText.contains("[ LOGO ]"))

        // Verify every line in header is centered
        for (line in headerLines) {
            assertLineCentered(line, width)
        }
    }

    @Test
    fun testLogoAreaWhenNoLogoConfigured() {
        val width = 32
        val header = CompanyHeaderEntity(
            companyName = "POS & INVOICE",
            branchName = "প্রধান শাখা",
            branchNumber = "01",
            address = "ঢাকা",
            phoneNumber = "01700000000",
            logoUri = null
        )
        val invoice = InvoiceEntity(
            orderId = "DH-TEST-NOLOGO",
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Admin",
            customerInfo = "N/A",
            rawTotalPaisa = 0L,
            roundedTotalBdt = 0L,
            footerNote = ""
        )
        val receiptText = EscPosPrinterEngine.generateReceiptText(
            header = header,
            invoice = invoice,
            lines = emptyList(),
            paperSize = "58mm",
            footerLine1 = "",
            footerLine2 = ""
        )
        val headerLines = extractHeaderLines(receiptText, width)
        val hasLogoText = headerLines.any { it.contains("LOGO", ignoreCase = true) }
        assertTrue("Logo literal text must not be present when no logo exists", !hasLogoText)
        val compLine = headerLines.first { it.contains("POS & INVOICE") }
        assertLineCentered(compLine, width)
    }

    @Test
    fun testMultiLineCompanyNameCenteringAndFullPaid() {
        val width = 32
        val multiLineName = "মেসার্স রাকিব ফিলিং স্টেশন\nএন্ড\nএল পি জি অটোগ্যাস ফিলিং স্টেশন"
        val header = CompanyHeaderEntity(
            companyName = multiLineName,
            branchName = "প্রধান শাখা",
            branchNumber = "01",
            address = "মহাদেবপুর, নওগাঁ",
            phoneNumber = "01700000000"
        )
        val invoice = InvoiceEntity(
            orderId = "DH-MULTI",
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Admin",
            customerInfo = "01711000000",
            rawTotalPaisa = 10000L,
            roundedTotalBdt = 100L,
            footerNote = "Thank you\nCome Again"
        )
        val receiptText = EscPosPrinterEngine.generateReceiptText(
            header = header,
            invoice = invoice,
            lines = emptyList(),
            paperSize = "58mm",
            footerLine1 = "Thank you",
            footerLine2 = "Come Again",
            isFullPaid = true
        )

        // Verify [Logo] does not appear
        assertTrue(!receiptText.contains("[Logo]", ignoreCase = true))
        // Verify each line of company name is present and centered
        val lines = receiptText.lines()
        val line1 = lines.first { it.contains("মেসার্স রাকিব ফিলিং স্টেশন") }
        val line2 = lines.first { it.trim() == "এন্ড" }
        val line3 = lines.first { it.contains("এল পি জি অটোগ্যাস ফিলিং স্টেশন") }
        assertLineCentered(line1, width)
        assertLineCentered(line2, width)
        assertLineCentered(line3, width)

        // Verify Full Paid appears before footer
        val posFullPaid = receiptText.indexOf("FULL PAID")
        val posFooter = receiptText.indexOf("Thank you")
        assertTrue("FULL PAID must be present", posFullPaid >= 0)
        assertTrue("FULL PAID must be placed before footer note", posFullPaid < posFooter)
    }
}
