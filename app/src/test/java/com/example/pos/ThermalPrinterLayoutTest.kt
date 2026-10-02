package com.example.pos

import android.graphics.Bitmap
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.printer.ThermalReceiptFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThermalPrinterLayoutTest {

    private fun createSampleInvoice(): InvoiceEntity {
        return InvoiceEntity(
            orderId = "DH-2026-902",
            dateTime = 1711188000000L, // Fixed timestamp for reproducible test
            operatorId = "OP-10",
            operatorName = "Rakib Hassan",
            customerInfo = "01899112233",
            rawTotalPaisa = 18550L,
            roundedTotalBdt = 186L,
            footerNote = "Thank you for shopping!\nPlease visit again."
        )
    }

    private fun createSampleLines(): List<InvoiceLineEntity> {
        return listOf(
            InvoiceLineEntity(
                orderId = "DH-2026-902",
                itemId = "ITM-01",
                itemName = "Basmati Rice Super Long Grain 1kg",
                itemType = "KILO",
                unit = "kg",
                quantityStr = "2.5",
                unitPricePaisa = 5000L,
                lineTotalPaisa = 12500L,
                sortOrder = 1
            ),
            InvoiceLineEntity(
                orderId = "DH-2026-902",
                itemId = "ITM-02",
                itemName = "Pure Mustard Oil Bottle 500ml",
                itemType = "PIECE",
                unit = "pc",
                quantityStr = "1",
                unitPricePaisa = 6050L,
                lineTotalPaisa = 6050L,
                sortOrder = 2
            )
        )
    }

    private fun createSampleHeader(): CompanyHeaderEntity {
        return CompanyHeaderEntity(
            companyName = "Green Super Market Ltd.",
            branchName = "Uttara Branch",
            branchNumber = "04",
            address = "Sector 3, Uttara, Dhaka-1230",
            phoneNumber = "+8801700112233"
        )
    }

    @Test
    fun testReceiptHierarchy_58mm_32Columns() {
        val header = createSampleHeader()
        val invoice = createSampleInvoice()
        val lines = createSampleLines()

        val text = ThermalReceiptFormatter.generateReceiptText(
            header = header,
            invoice = invoice,
            lines = lines,
            paperSize = "58mm",
            footerLine1 = "System Powered by Smart POS",
            footerLine2 = "No return after 7 days"
        )

        // 1. Line lengths must not exceed 32 visual characters
        text.lines().forEach { line ->
            val vWidth = ThermalReceiptFormatter.getVisualWidth(line)
            assertTrue("Line '$line' width $vWidth exceeds 32 chars on 58mm", vWidth <= 32)
        }

        // 2. Hierarchy Check (Order of sections)
        assertTrue("Literal [Logo] must not appear anywhere in receipt", !text.contains("[Logo]", ignoreCase = true))
        assertTrue("Literal [ LOGO ] must not appear anywhere in receipt", !text.contains("[ LOGO ]"))

        val posCompany = text.indexOf("Green Super Market Ltd.")
        val posAddress = text.indexOf("Sector 3, Uttara, Dhaka-1230")
        val posPhone = text.indexOf("+8801700112233")
        val posOrderId = text.indexOf("DH-2026-902")
        val posOperator = text.indexOf("Rakib Hassan")
        val posCustomer = text.indexOf("01899112233")
        val posItemHeader = text.indexOf("Item")
        val posItem1 = text.indexOf("Basmati Rice")
        val posItem2 = text.indexOf("Pure Mustard Oil")
        val posSubtotal = text.indexOf("185.50")
        val posRoundedTotal = text.indexOf("186")
        val posFullPaid = text.indexOf("FULL PAID")
        val posFooter1 = text.indexOf("System Powered by Smart POS")

        assertTrue("Company name must be present", posCompany >= 0)
        assertTrue("Company name must precede address", posCompany in 0..<posAddress)
        assertTrue("Address must precede phone", posAddress in 0..<posPhone)
        assertTrue("Phone must precede Order ID", posPhone in 0..<posOrderId)
        assertTrue("Order ID must precede Operator", posOrderId in 0..<posOperator)
        assertTrue("Operator must precede Customer", posOperator in 0..<posCustomer)
        assertTrue("Customer must precede Items Header", posCustomer in 0..<posItemHeader)
        assertTrue("Item header must precede items", posItemHeader in 0..<posItem1)
        assertTrue("Item 1 must precede Item 2", posItem1 in 0..<posItem2)
        assertTrue("Items must precede Subtotal", posItem2 in 0..<posSubtotal)
        assertTrue("Subtotal must precede Rounded Total", posSubtotal in 0..<posRoundedTotal)
        assertTrue("Full Paid must precede Footer", posFullPaid in 0..<posFooter1)
        assertTrue("Rounded Total must precede Footer", posRoundedTotal in 0..<posFooter1)
    }

    @Test
    fun testReceiptHierarchy_80mm_48Columns() {
        val header = createSampleHeader()
        val invoice = createSampleInvoice()
        val lines = createSampleLines()

        val text = ThermalReceiptFormatter.generateReceiptText(
            header = header,
            invoice = invoice,
            lines = lines,
            paperSize = "80mm",
            footerLine1 = "System Powered by Smart POS",
            footerLine2 = "No return after 7 days"
        )

        // 1. Line lengths must not exceed 48 visual characters
        text.lines().forEach { line ->
            val vWidth = ThermalReceiptFormatter.getVisualWidth(line)
            assertTrue("Line '$line' width $vWidth exceeds 48 chars on 80mm", vWidth <= 48)
        }

        // 2. 80mm table format check
        assertTrue("80mm must contain standard column header", text.contains("Item") && text.contains("Rate") && text.contains("Total"))
        val sep80 = "=".repeat(48)
        assertTrue("80mm must contain 48-char separator", text.contains(sep80))
    }

    @Test
    fun testEscPosBinaryReceipt_WithAutoCutter() {
        val header = createSampleHeader()
        val invoice = createSampleInvoice()
        val lines = createSampleLines()

        val bytesWithCut = ThermalReceiptFormatter.buildEscPosReceipt(
            header = header,
            invoice = invoice,
            lines = lines,
            paperSize = "58mm",
            autoCutter = true,
            footerLine1 = "Thank you",
            footerLine2 = "Visit Again"
        )

        val bytesWithoutCut = ThermalReceiptFormatter.buildEscPosReceipt(
            header = header,
            invoice = invoice,
            lines = lines,
            paperSize = "58mm",
            autoCutter = false,
            footerLine1 = "Thank you",
            footerLine2 = "Visit Again"
        )

        // Verify initial ESC @ sequence (0x1B, 0x40)
        assertEquals(0x1B.toByte(), bytesWithCut[0])
        assertEquals(0x40.toByte(), bytesWithCut[1])

        // Verify cutter command (GS V 66 0 -> 0x1D, 0x56, 0x42, 0x00)
        val cutCommand = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
        val containsCutInWith = bytesWithCut.takeLast(10).containsSubsequence(cutCommand)
        val containsCutInWithout = bytesWithoutCut.takeLast(10).containsSubsequence(cutCommand)

        assertTrue("Receipt with autoCutter=true must contain cut command at the end", containsCutInWith)
        assertFalse("Receipt with autoCutter=false must NOT contain cut command", containsCutInWithout)
    }

    @Test
    fun testEscPosRasterBitmapConversion() {
        // Create 16x8 bitmap (2 bytes wide x 8 lines high)
        val bitmap = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888)
        // Fill top-left 8x8 with black
        for (x in 0 until 8) {
            for (y in 0 until 8) {
                bitmap.setPixel(x, y, android.graphics.Color.BLACK)
            }
        }

        val rasterBytes = ThermalReceiptFormatter.bitmapToEscPosRaster(bitmap, 384)!!
        // Header is 8 bytes: GS v 0 0 xL xH yL yH
        assertEquals(0x1D.toByte(), rasterBytes[0])
        assertEquals(0x76.toByte(), rasterBytes[1])
        assertEquals(0x30.toByte(), rasterBytes[2])
        assertEquals(0x00.toByte(), rasterBytes[3])
        // Width in bytes = 16 / 8 = 2 bytes
        assertEquals(2.toByte(), rasterBytes[4])
        assertEquals(0.toByte(), rasterBytes[5])
        // Height in dots = 8
        assertEquals(8.toByte(), rasterBytes[6])
        assertEquals(0.toByte(), rasterBytes[7])
        // Total size = 8 + (2 * 8) = 24 bytes
        assertEquals(24, rasterBytes.size)
    }

    private fun List<Byte>.containsSubsequence(sub: ByteArray): Boolean {
        for (i in 0..this.size - sub.size) {
            var match = true
            for (j in sub.indices) {
                if (this[i + j] != sub[j]) {
                    match = false
                    break
                }
            }
            if (match) return true
        }
        return false
    }
}
