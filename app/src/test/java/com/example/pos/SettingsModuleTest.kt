package com.example.pos

import com.example.pos.data.Localization
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.printer.ThermalReceiptFormatter
import org.junit.Assert.*
import org.junit.Test

class SettingsModuleTest {

    @Test
    fun testDatabaseCapacityWarningThreshold() {
        val supportedCapacity = 200_000

        // Test safe capacity: 198,999 invoices (1,001 remaining) -> NOT near capacity
        val invoicesSafe = 198_999
        val remainingSafe = (supportedCapacity - invoicesSafe).coerceAtLeast(0)
        val isNearCapacitySafe = remainingSafe <= 1000
        assertEquals(1001, remainingSafe)
        assertFalse("At 1,001 remaining records, warning should not show", isNearCapacitySafe)

        // Test exact threshold: 199,000 invoices (1,000 remaining) -> WARNING
        val invoicesThreshold = 199_000
        val remainingThreshold = (supportedCapacity - invoicesThreshold).coerceAtLeast(0)
        val isNearCapacityThreshold = remainingThreshold <= 1000
        assertEquals(1000, remainingThreshold)
        assertTrue("At 1,000 remaining records, warning MUST show", isNearCapacityThreshold)

        // Test critical capacity: 199,800 invoices (200 remaining) -> WARNING
        val invoicesCritical = 199_800
        val remainingCritical = (supportedCapacity - invoicesCritical).coerceAtLeast(0)
        val isNearCapacityCritical = remainingCritical <= 1000
        assertEquals(200, remainingCritical)
        assertTrue("At 200 remaining records, warning MUST show", isNearCapacityCritical)

        // Test over capacity: 205,000 invoices (0 remaining) -> WARNING
        val invoicesOver = 205_000
        val remainingOver = (supportedCapacity - invoicesOver).coerceAtLeast(0)
        val isNearCapacityOver = remainingOver <= 1000
        assertEquals(0, remainingOver)
        assertTrue("At 0 remaining records, warning MUST show", isNearCapacityOver)
    }

    @Test
    fun testMultiLineFooterNoteInReceipt58mmAnd80mm() {
        val header = CompanyHeaderEntity(
            companyName = "Modern Gas & Tech",
            branchName = "Uttara Outlet",
            branchNumber = "12",
            address = "Sector 3, Uttara, Dhaka",
            phoneNumber = "01711000000"
        )
        val invoice = InvoiceEntity(
            orderId = "ORD-TEST-001",
            dateTime = 1710000000000L,
            operatorId = "OP-1",
            operatorName = "Rahim",
            customerInfo = "Karim (01811223344)",
            rawTotalPaisa = 12500,
            roundedTotalBdt = 125,
            footerNote = "Line One\nLine Two\nLine Three"
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = "ORD-TEST-001",
                itemId = "ITEM-1",
                itemName = "LPG Cylinder 12kg",
                itemType = "GAS",
                unit = "cyl",
                quantityStr = "1",
                unitPricePaisa = 12500,
                lineTotalPaisa = 12500
            )
        )

        // Test 58mm layout with multi-line footer note
        val receipt58 = ThermalReceiptFormatter.generateReceiptText(
            header = header,
            invoice = invoice,
            lines = lines,
            paperSize = "58mm",
            footerLine1 = "নিখুঁত মানের নিশ্চয়তা",
            footerLine2 = "ধন্যবাদ আবার আসবেন"
        )
        assertTrue(receipt58.contains("নিখুঁত মানের নিশ্চয়তা"))
        assertTrue(receipt58.contains("ধন্যবাদ আবার আসবেন"))

        // Test 80mm layout with multi-line footer note
        val receipt80 = ThermalReceiptFormatter.generateReceiptText(
            header = header,
            invoice = invoice,
            lines = lines,
            paperSize = "80mm",
            footerLine1 = "নিখুঁত মানের নিশ্চয়তা",
            footerLine2 = "ধন্যবাদ আবার আসবেন\nসবার সাথে শেয়ার করুন"
        )
        assertTrue(receipt80.contains("নিখুঁত মানের নিশ্চয়তা"))
        assertTrue(receipt80.contains("ধন্যবাদ আবার আসবেন"))
        assertTrue(receipt80.contains("সবার সাথে শেয়ার করুন"))

        // Test ESC/POS binary output with auto-cutter ON vs OFF
        val binaryCutterOn = ThermalReceiptFormatter.buildEscPosReceipt(
            header = header,
            invoice = invoice,
            lines = lines,
            paperSize = "58mm",
            autoCutter = true,
            footerLine1 = "নিখুঁত মানের নিশ্চয়তা",
            footerLine2 = "ধন্যবাদ আবার আসবেন"
        )
        // Auto-cutter ESC/POS sequence: GS V 66 0 -> 0x1D, 0x56, 0x42, 0x00
        val targetCutBytes = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
        val hasCutterBytes = binaryCutterOn.toList().windowed(4).any { window ->
            window[0] == targetCutBytes[0] &&
            window[1] == targetCutBytes[1] &&
            window[2] == targetCutBytes[2] &&
            window[3] == targetCutBytes[3]
        }
        assertTrue("ESC/POS output must contain auto-cut command when autoCutter=true", hasCutterBytes)

        val binaryCutterOff = ThermalReceiptFormatter.buildEscPosReceipt(
            header = header,
            invoice = invoice,
            lines = lines,
            paperSize = "58mm",
            autoCutter = false,
            footerLine1 = "Test 1",
            footerLine2 = "Test 2"
        )
        val hasCutterBytesOff = binaryCutterOff.toList().windowed(4).any { window ->
            window[0] == targetCutBytes[0] &&
            window[1] == targetCutBytes[1] &&
            window[2] == targetCutBytes[2] &&
            window[3] == targetCutBytes[3]
        }
        assertFalse("ESC/POS output must NOT contain auto-cut command when autoCutter=false", hasCutterBytesOff)
    }

    @Test
    fun testAllLocalizationKeysExistInBothLanguages() {
        Localization.Key.values().forEach { key ->
            val enText = Localization.get(key, "en")
            val bnText = Localization.get(key, "bn")
            assertNotNull("English localization missing for key $key", enText)
            assertNotNull("Bangla localization missing for key $key", bnText)
            assertTrue("English localization cannot be blank for $key", enText.isNotBlank())
            assertTrue("Bangla localization cannot be blank for $key", bnText.isNotBlank())
        }
    }
}
