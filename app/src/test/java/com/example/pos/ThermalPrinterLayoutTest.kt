package com.example.pos

import android.graphics.Bitmap
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.printer.EscPosPrinterEngine
import com.example.pos.printer.ThermalReceiptFormatter
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

    @Test
    fun testBanglaUnicodeDetection() {
        assertTrue(ThermalReceiptFormatter.containsBangla("মেসার্স জামান স্টোর"))
        assertTrue(ThermalReceiptFormatter.containsBangla("কেজি"))
        assertTrue(ThermalReceiptFormatter.containsBangla("প্যাকেট"))
        assertTrue(ThermalReceiptFormatter.containsBangla("১২.৫০"))
        assertFalse(ThermalReceiptFormatter.containsBangla("Green Super Market"))
        assertFalse(ThermalReceiptFormatter.containsBangla("12.50 BDT"))
        assertFalse(ThermalReceiptFormatter.containsBangla("Item Name"))
    }

    @Test
    fun testBanglaTextRasterRendering_58mmAnd80mm() {
        val banglaHeader = "মেসার্স জামান ট্রেডার্স"
        val raster58 = ThermalReceiptFormatter.renderTextToRaster(
            text = banglaHeader,
            widthDots = ThermalReceiptFormatter.DOTS_58MM,
            textSizePx = 32f,
            isBold = true,
            alignment = android.text.Layout.Alignment.ALIGN_CENTER
        )
        assertNotNull("58mm raster must not be null", raster58)
        assertTrue("58mm raster must start with GS v 0", raster58!!.size > 8)
        assertEquals(0x1D.toByte(), raster58[0])
        assertEquals(0x76.toByte(), raster58[1])
        assertEquals(0x30.toByte(), raster58[2])
        assertEquals(0x00.toByte(), raster58[3])
        // Width for 58mm = 384 dots = 48 bytes
        assertEquals((384 / 8).toByte(), raster58[4])
        assertEquals(0.toByte(), raster58[5])

        val raster80 = ThermalReceiptFormatter.renderTextToRaster(
            text = banglaHeader,
            widthDots = ThermalReceiptFormatter.DOTS_80MM,
            textSizePx = 38f,
            isBold = true,
            alignment = android.text.Layout.Alignment.ALIGN_CENTER
        )
        assertNotNull("80mm raster must not be null", raster80)
        assertTrue("80mm raster must start with GS v 0", raster80!!.size > 8)
        // Width for 80mm = 576 dots = 72 bytes
        assertEquals((576 / 8).toByte(), raster80[4])
        assertEquals(0.toByte(), raster80[5])
    }

    @Test
    fun testBanglaItemRowRasterRendering_58mmAnd80mm() {
        val name = "সয়াবিন তেল ১ লিটার"
        val qty = "২.৫"
        val unit = "লিটার"
        val rate = "190.00"
        val total = "475.00"

        val row58 = ThermalReceiptFormatter.render58mmItemRowToRaster(name, qty, unit, rate, total)
        assertNotNull("58mm item raster must not be null", row58)
        assertTrue("58mm item raster size must be > 8 bytes", row58!!.size > 8)
        assertEquals(0x1D.toByte(), row58[0])
        assertEquals(0x76.toByte(), row58[1])
        assertEquals(0x30.toByte(), row58[2])
        assertEquals(0x00.toByte(), row58[3])
        assertEquals((ThermalReceiptFormatter.DOTS_58MM / 8).toByte(), row58[4])

        val row80 = ThermalReceiptFormatter.render80mmItemRowToRaster(name, qty, unit, rate, total)
        assertNotNull("80mm item raster must not be null", row80)
        assertTrue("80mm item raster size must be > 8 bytes", row80!!.size > 8)
        assertEquals(0x1D.toByte(), row80[0])
        assertEquals(0x76.toByte(), row80[1])
        assertEquals(0x30.toByte(), row80[2])
        assertEquals(0x00.toByte(), row80[3])
        assertEquals((ThermalReceiptFormatter.DOTS_80MM / 8).toByte(), row80[4])
    }

    @Test
    fun testBanglaEscPosReceiptGeneration_58mmAnd80mm() {
        val banglaHeader = CompanyHeaderEntity(
            companyName = "মেসার্স জামান ট্রেডার্স",
            branchName = "উত্তরা শাখা",
            branchNumber = "০৪",
            address = "সেক্টর ৩, উত্তরা, ঢাকা",
            phoneNumber = "০১৭১২৩৪৫৬৭৮"
        )
        val banglaInvoice = InvoiceEntity(
            orderId = "DH-2026-905",
            dateTime = 1711188000000L,
            operatorId = "OP-1",
            operatorName = "কামাল হোসেন",
            customerInfo = "রহিম মিয়া",
            rawTotalPaisa = 47500L,
            roundedTotalBdt = 475L,
            footerNote = "ধন্যবাদ, আবার আসবেন!"
        )
        val banglaLines = listOf(
            InvoiceLineEntity(
                orderId = "DH-2026-905",
                itemId = "ITM-BN-01",
                itemName = "সয়াবিন তেল ১ লিটার",
                itemType = "LIQUID",
                unit = "বোতল",
                quantityStr = "১",
                unitPricePaisa = 19000L,
                lineTotalPaisa = 19000L,
                sortOrder = 1
            ),
            InvoiceLineEntity(
                orderId = "DH-2026-905",
                itemId = "ITM-BN-02",
                itemName = "বাসমতী চাল প্রিমিয়াম",
                itemType = "KILO",
                unit = "কেজি",
                quantityStr = "২",
                unitPricePaisa = 14250L,
                lineTotalPaisa = 28500L,
                sortOrder = 2
            )
        )

        // 58mm test
        val bytes58 = ThermalReceiptFormatter.buildEscPosReceipt(
            header = banglaHeader,
            invoice = banglaInvoice,
            lines = banglaLines,
            paperSize = "58mm",
            autoCutter = true,
            footerLine1 = "ধন্যবাদ, আবার আসবেন!",
            footerLine2 = "পণ্য ফেরত নেওয়ার ক্ষেত্রে রসিদ আবশ্যক"
        )
        assertTrue("58mm Bangla receipt must not be empty", bytes58.isNotEmpty())
        assertEquals("Must start with ESC @", 0x1B.toByte(), bytes58[0])
        assertEquals("Must start with ESC @", 0x40.toByte(), bytes58[1])

        // Verify GS v 0 raster command is present in the stream
        val gsV0 = byteArrayOf(0x1D, 0x76, 0x30, 0x00)
        assertTrue("58mm receipt must contain GS v 0 raster commands for Bangla text", bytes58.toList().containsSubsequence(gsV0))

        // Verify auto cutter command is present at the end
        val cutCommand = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
        assertTrue("58mm receipt must contain auto cutter", bytes58.takeLast(10).containsSubsequence(cutCommand))

        // 80mm test
        val bytes80 = ThermalReceiptFormatter.buildEscPosReceipt(
            header = banglaHeader,
            invoice = banglaInvoice,
            lines = banglaLines,
            paperSize = "80mm",
            autoCutter = false,
            footerLine1 = "ধন্যবাদ, আবার আসবেন!",
            footerLine2 = "পণ্য ফেরত নেওয়ার ক্ষেত্রে রসিদ আবশ্যক"
        )
        assertTrue("80mm Bangla receipt must not be empty", bytes80.isNotEmpty())
        assertEquals(0x1B.toByte(), bytes80[0])
        assertEquals(0x40.toByte(), bytes80[1])
        assertTrue("80mm receipt must contain GS v 0 raster commands for Bangla text", bytes80.toList().containsSubsequence(gsV0))
        assertFalse("80mm receipt with autoCutter=false must not contain cutter command", bytes80.takeLast(10).containsSubsequence(cutCommand))
    }

    @Test
    fun testBuildEscPosBytesWithBanglaText() {
        val receiptText = """
            ================================
                 মেসার্স জামান ট্রেডার্স
                   উত্তরা শাখা (০৪)
            ================================
            Date: 23-Oct-2026 04:30PM
            Order ID: #DH-2026-905
            Operator: কামাল হোসেন
            Customer: রহিম মিয়া
            ================================
            Item Name    Qty   Rate   Total
            --------------------------------
            সয়াবিন তেল ১ লিটার
              ১.০ x ১৯০.০০        ১৯০.০০ BDT
            --------------------------------
            Sub Total:            190.00 BDT
            TOTAL:                190 BDT
            ================================
                      [✓] FULL PAID
            --------------------------------
                ধন্যবাদ, আবার আসবেন!
            ================================
        """.trimIndent()

        val bytes = EscPosPrinterEngine.buildEscPosBytes(receiptText, autoCut = true, paperSize = "58mm")
        assertTrue("buildEscPosBytes must not be empty", bytes.isNotEmpty())
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])

        val gsV0 = byteArrayOf(0x1D, 0x76, 0x30, 0x00)
        assertTrue("buildEscPosBytes must rasterize Bangla lines using GS v 0", bytes.toList().containsSubsequence(gsV0))
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
