package com.example.pos

import com.example.pos.data.Localization
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.printer.EscPosPrinterEngine
import com.example.pos.printer.ThermalReceiptFormatter
import com.example.pos.ui.screens.parseReceiptSectionsForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoucherLocalizationAndSummaryColumnsTest {

    private fun sampleCompany(): CompanyHeaderEntity {
        return CompanyHeaderEntity(
            companyName = "Green Grocery",
            branchName = "Dhanmondi Branch",
            branchNumber = "04",
            phoneNumber = "01711223344",
            address = "Road 27, Dhanmondi, Dhaka"
        )
    }

    private fun sampleInvoice(): InvoiceEntity {
        return InvoiceEntity(
            orderId = "ORD-9910",
            dateTime = 1711188000000L,
            operatorId = "OP-1",
            operatorName = "Kamal Hossain",
            customerInfo = "01912345678",
            rawTotalPaisa = 45000L,
            roundedTotalBdt = 450L,
            footerNote = "Thank you!\nVisit again."
        )
    }

    private fun sampleLines(): List<InvoiceLineEntity> {
        return listOf(
            InvoiceLineEntity(
                orderId = "ORD-9910",
                itemId = "P1",
                itemName = "Mustard Oil",
                unitPricePaisa = 25000L,
                quantityStr = "1",
                unit = "Ltr",
                lineTotalPaisa = 25000L
            ),
            InvoiceLineEntity(
                orderId = "ORD-9910",
                itemId = "P2",
                itemName = "Sugar",
                unitPricePaisa = 10000L,
                quantityStr = "2",
                unit = "Kg",
                lineTotalPaisa = 20000L
            )
        )
    }

    @Test
    fun testLocalizationKeysCompleteness() {
        // Audit and verify all required voucher labels exist in en and bn
        val keys = listOf(
            Localization.Key.VOUCHER_ITEM_NAME to Pair("Item Name", "পণ্যের নাম"),
            Localization.Key.VOUCHER_DESCRIPTION to Pair("Item Description", "বিবরণ"),
            Localization.Key.VOUCHER_QTY to Pair("Qty", "পরিমাণ"),
            Localization.Key.VOUCHER_UNIT to Pair("Unit", "একক"),
            Localization.Key.VOUCHER_RATE to Pair("Rate", "দর"),
            Localization.Key.VOUCHER_TOTAL to Pair("Total", "মোট"),
            Localization.Key.VOUCHER_SUBTOTAL to Pair("Sub Total", "উপমোট"),
            Localization.Key.VOUCHER_TOTAL_SUMMARY to Pair("TOTAL", "মোট"),
            Localization.Key.VOUCHER_DATE to Pair("Date", "তারিখ"),
            Localization.Key.VOUCHER_ORDER_ID to Pair("Order ID", "অর্ডার আইডি"),
            Localization.Key.VOUCHER_OPERATOR to Pair("Operator", "অপারেটর"),
            Localization.Key.VOUCHER_CUSTOMER to Pair("Customer", "ক্রেতা"),
            Localization.Key.VOUCHER_FULL_PAID to Pair("FULL PAID ✓", "সম্পূর্ণ পরিশোধিত ✓"),
            Localization.Key.VOUCHER_FULL_PAID_BADGE to Pair("Full Paid", "সম্পূর্ণ পরিশোধিত")
        )

        for ((key, expected) in keys) {
            val enVal = Localization.get(key, "en")
            val bnVal = Localization.get(key, "bn")
            assertEquals("English mismatch for key $key", expected.first, enVal)
            assertEquals("Bangla mismatch for key $key", expected.second, bnVal)
        }
    }

    @Test
    fun testEnglish58mmVoucherSummaryRowsAndLabels() {
        val text = ThermalReceiptFormatter.generateReceiptText(
            header = sampleCompany(),
            invoice = sampleInvoice(),
            lines = sampleLines(),
            paperSize = "58mm",
            footerLine1 = "Thank you!",
            footerLine2 = "",
            isFullPaid = true,
            lang = "en"
        )

        assertTrue("Should contain English Item Name header", text.contains("Item Name"))
        assertTrue("Should contain English Qty header", text.contains("Qty"))
        assertTrue("Should contain English Rate header", text.contains("Rate"))
        assertTrue("Should contain English Total header", text.contains("Total"))
        assertTrue("Should contain English Date label", text.contains("Date:"))
        assertTrue("Should contain English Order ID label", text.contains("Order ID:"))
        assertTrue("Should contain English Operator label", text.contains("Operator:"))
        assertTrue("Should contain English Customer label", text.contains("Customer:"))
        assertTrue("Should contain English FULL PAID", text.contains("FULL PAID ✓"))

        // Check Subtotal line layout: Sub Total on left, amount on right
        val lines = text.lines()
        val subtotalLine = lines.firstOrNull { it.contains("Sub Total") }
        assertNotNull("Sub Total line must be present", subtotalLine)
        assertTrue("Sub Total must start at column 0", subtotalLine!!.startsWith("Sub Total"))
        assertTrue("Sub Total amount must end at column width 32", subtotalLine.trimEnd().endsWith("450.00"))

        // Check TOTAL line layout: TOTAL on left, amount on right
        val totalLine = lines.firstOrNull { it.contains("TOTAL") }
        assertNotNull("TOTAL line must be present", totalLine)
        assertTrue("TOTAL must start at column 0", totalLine!!.startsWith("TOTAL"))
        assertTrue("TOTAL amount must end at column width 32", totalLine.trimEnd().endsWith("450"))
    }

    @Test
    fun testBangla58mmVoucherSummaryRowsAndLabels() {
        val text = ThermalReceiptFormatter.generateReceiptText(
            header = sampleCompany(),
            invoice = sampleInvoice(),
            lines = sampleLines(),
            paperSize = "58mm",
            footerLine1 = "ধন্যবাদ!",
            footerLine2 = "",
            isFullPaid = true,
            lang = "bn"
        )

        assertTrue("Should contain Bangla Item Name header", text.contains("পণ্যের নাম"))
        assertTrue("Should contain Bangla Qty header", text.contains("পরিমাণ"))
        assertTrue("Should contain Bangla Rate header", text.contains("দর"))
        assertTrue("Should contain Bangla Total header", text.contains("মোট"))
        assertTrue("Should contain Bangla Date label", text.contains("তারিখ:"))
        assertTrue("Should contain Bangla Order ID label", text.contains("অর্ডার আইডি:"))
        assertTrue("Should contain Bangla Operator label", text.contains("অপারেটর:"))
        assertTrue("Should contain Bangla Customer label", text.contains("ক্রেতা:"))
        assertTrue("Should contain Bangla FULL PAID", text.contains("সম্পূর্ণ পরিশোধিত ✓"))

        // Check Subtotal line layout: উপমোট on left, amount on right
        val lines = text.lines()
        val subtotalLine = lines.firstOrNull { it.contains("উপমোট") }
        assertNotNull("উপমোট line must be present", subtotalLine)
        assertTrue("উপমোট must start at column 0", subtotalLine!!.startsWith("উপমোট"))
        assertTrue("উপমোট amount must end at right", subtotalLine.trimEnd().endsWith("450.00"))

        // Check TOTAL line layout: মোট on left, amount on right
        val totalLine = lines.firstOrNull { it.startsWith("মোট") }
        assertNotNull("মোট line must be present", totalLine)
        assertTrue("মোট must start at column 0", totalLine!!.startsWith("মোট"))
        assertTrue("মোট amount must end at right", totalLine.trimEnd().endsWith("450"))
    }

    @Test
    fun testEnglish80mmVoucherSummaryRowsAndLabels() {
        val text = ThermalReceiptFormatter.generateReceiptText(
            header = sampleCompany(),
            invoice = sampleInvoice(),
            lines = sampleLines(),
            paperSize = "80mm",
            footerLine1 = "Thank you!",
            footerLine2 = "",
            isFullPaid = true,
            lang = "en"
        )

        assertTrue("Should contain English Description header", text.contains("Item Description"))
        assertTrue("Should contain English Unit header", text.contains("Unit"))

        val lines = text.lines()
        val subtotalLine = lines.firstOrNull { it.contains("Sub Total") }
        assertNotNull("Sub Total line must be present", subtotalLine)
        assertTrue("Sub Total must start at column 0", subtotalLine!!.startsWith("Sub Total"))
        assertTrue("Sub Total amount must end at column width 48", subtotalLine.trimEnd().endsWith("450.00"))

        val totalLine = lines.firstOrNull { it.contains("TOTAL") }
        assertNotNull("TOTAL line must be present", totalLine)
        assertTrue("TOTAL must start at column 0", totalLine!!.startsWith("TOTAL"))
        assertTrue("TOTAL amount must end at column width 48", totalLine.trimEnd().endsWith("450"))
    }

    @Test
    fun testBangla80mmVoucherSummaryRowsAndLabels() {
        val text = ThermalReceiptFormatter.generateReceiptText(
            header = sampleCompany(),
            invoice = sampleInvoice(),
            lines = sampleLines(),
            paperSize = "80mm",
            footerLine1 = "ধন্যবাদ!",
            footerLine2 = "",
            isFullPaid = true,
            lang = "bn"
        )

        assertTrue("Should contain Bangla Description header", text.contains("বিবরণ"))
        assertTrue("Should contain Bangla Unit header", text.contains("একক"))

        val lines = text.lines()
        val subtotalLine = lines.firstOrNull { it.contains("উপমোট") }
        assertNotNull("উপমোট line must be present", subtotalLine)
        assertTrue("উপমোট must start at column 0", subtotalLine!!.startsWith("উপমোট"))
        assertTrue("উপমোট amount must end at right", subtotalLine.trimEnd().endsWith("450.00"))

        val totalLine = lines.firstOrNull { it.startsWith("মোট") }
        assertNotNull("মোট line must be present", totalLine)
        assertTrue("মোট must start at column 0", totalLine!!.startsWith("মোট"))
        assertTrue("মোট amount must end at right", totalLine.trimEnd().endsWith("450"))
    }

    @Test
    fun testParseReceiptSectionsForPreviewWithSummaryRows() {
        // Test parsing English 58mm receipt
        val enText = ThermalReceiptFormatter.generateReceiptText(
            header = sampleCompany(),
            invoice = sampleInvoice(),
            lines = sampleLines(),
            paperSize = "58mm",
            footerLine1 = "Footer 1",
            footerLine2 = "",
            isFullPaid = true,
            lang = "en"
        )
        val enSections = parseReceiptSectionsForPreview(enText, emptyList(), "en")
        assertNotNull("English preview sections must parse", enSections)
        assertEquals(2, enSections!!.table.rows.size)
        assertEquals(2, enSections.table.summaryRows.size)
        assertEquals("Sub Total", enSections.table.summaryRows[0].label)
        assertEquals("450.00", enSections.table.summaryRows[0].amount)
        assertFalse(enSections.table.summaryRows[0].isBold)
        assertEquals("TOTAL", enSections.table.summaryRows[1].label)
        assertEquals("450", enSections.table.summaryRows[1].amount)
        assertTrue(enSections.table.summaryRows[1].isBold)
        // postTableText should NOT duplicate Subtotal or Total
        assertFalse(enSections.postTableText.contains("Sub Total"))
        assertFalse(enSections.postTableText.contains("TOTAL"))

        // Test parsing Bangla 58mm receipt
        val bnText = ThermalReceiptFormatter.generateReceiptText(
            header = sampleCompany(),
            invoice = sampleInvoice(),
            lines = sampleLines(),
            paperSize = "58mm",
            footerLine1 = "ধন্যবাদ",
            footerLine2 = "",
            isFullPaid = true,
            lang = "bn"
        )
        val bnSections = parseReceiptSectionsForPreview(bnText, emptyList(), "bn")
        assertNotNull("Bangla preview sections must parse", bnSections)
        assertEquals(2, bnSections!!.table.rows.size)
        assertEquals(2, bnSections.table.summaryRows.size)
        assertEquals("উপমোট", bnSections.table.summaryRows[0].label)
        assertEquals("450.00", bnSections.table.summaryRows[0].amount)
        assertFalse(bnSections.table.summaryRows[0].isBold)
        assertEquals("মোট", bnSections.table.summaryRows[1].label)
        assertEquals("450", bnSections.table.summaryRows[1].amount)
        assertTrue(bnSections.table.summaryRows[1].isBold)
        // postTableText should NOT duplicate Subtotal or Total
        assertFalse(bnSections.postTableText.contains("উপমোট"))
        assertFalse(bnSections.postTableText.contains("মোট"))
    }

    @Test
    fun testEscPosBinaryReceiptWithBanglaSummaryAndFullPaid() {
        val payload = ThermalReceiptFormatter.buildEscPosReceipt(
            header = sampleCompany(),
            invoice = sampleInvoice(),
            lines = sampleLines(),
            paperSize = "58mm",
            autoCutter = true,
            footerLine1 = "ধন্যবাদ!",
            footerLine2 = "",
            logoBitmap = null,
            isFullPaid = true,
            lang = "bn"
        )
        assertNotNull(payload)
        assertTrue("ESC/POS payload must not be empty", payload.isNotEmpty())
    }
}
