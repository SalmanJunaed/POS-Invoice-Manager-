package com.example.pos

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.util.CompanyLogoManager
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.PosDatabase
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
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompanyLogoManagerTest {

    private lateinit var context: Context
    private lateinit var db: PosDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        CompanyLogoManager.removeLogo(context)
    }

    @Test
    fun testSaveAndRetrieveLogo() {
        // Create a dummy bitmap and save it to a temporary file
        val testBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val tempFile = File(context.cacheDir, "temp_test_logo.png")
        FileOutputStream(tempFile).use { out ->
            testBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val fileUri = Uri.fromFile(tempFile)
        val savedPath = CompanyLogoManager.copyLogoFromUri(context, fileUri)
        assertNotNull("Saved path should not be null", savedPath)
        assertTrue("Logo file must exist in internal storage", File(savedPath!!).exists())

        val logoFile = CompanyLogoManager.getLogoFile(context)
        assertNotNull(logoFile)
        assertTrue(logoFile.exists())

        // Scaled bitmap decoding
        val decodedBitmap = CompanyLogoManager.loadScaledBitmap(context, savedPath, 64, 64)
        assertNotNull(decodedBitmap)

        // Remove logo
        val removed = CompanyLogoManager.removeLogo(context)
        assertTrue(removed)
        assertFalse(File(savedPath).exists())
    }

    @Test
    fun testLogoChangeDoesNotAffectHistoricalInvoices() = runBlocking {
        val invoiceDao = db.invoiceDao()
        val companyHeaderDao = db.companyHeaderDao()

        // 1. Set initial company header with logo
        companyHeaderDao.insertOrUpdate(
            CompanyHeaderEntity(
                id = 1,
                companyName = "Initial Store",
                branchName = "Main",
                branchNumber = "01",
                phoneNumber = "01700000000",
                address = "Dhaka",
                logoUri = "/data/user/0/com.example/files/company_logo.png"
            )
        )

        // 2. Insert invoice (simulating historical record)
        val invoiceId = "INV-2026-9999"
        val originalInvoice = InvoiceEntity(
            orderId = invoiceId,
            globalTransactionId = UUID.randomUUID().toString(),
            branchCode = "01",
            dateTime = 1726000000000L,
            operatorId = "OP-1",
            operatorName = "Manager",
            customerInfo = "01999999999",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L,
            footerNote = "Thank you",
            createdAt = 1726000000000L,
            updatedAt = 1726000000000L
        )

        val lines = listOf(
            InvoiceLineEntity(
                orderId = invoiceId,
                itemId = "P-1",
                itemName = "Premium Rice",
                itemType = "Solid",
                unit = "kg",
                quantityStr = "1",
                unitPricePaisa = 50000L,
                lineTotalPaisa = 50000L,
                sortOrder = 1
            )
        )
        invoiceDao.saveFullOrder(originalInvoice, lines)

        // 3. Change logo and company header
        companyHeaderDao.insertOrUpdate(
            CompanyHeaderEntity(
                id = 1,
                companyName = "Updated Store",
                branchName = "North",
                branchNumber = "02",
                phoneNumber = "01800000000",
                address = "Chittagong",
                logoUri = "/data/user/0/com.example/files/new_company_logo.png"
            )
        )

        // 4. Verify historical invoice data is completely unchanged
        val fetchedInvoice = invoiceDao.getInvoiceById(invoiceId)
        assertNotNull(fetchedInvoice)
        assertEquals(originalInvoice.orderId, fetchedInvoice?.orderId)
        assertEquals(originalInvoice.rawTotalPaisa, fetchedInvoice?.rawTotalPaisa)
        assertEquals(originalInvoice.roundedTotalBdt, fetchedInvoice?.roundedTotalBdt)
        assertEquals(originalInvoice.customerInfo, fetchedInvoice?.customerInfo)

        val fetchedLines = invoiceDao.getLinesForOrder(invoiceId)
        assertEquals(1, fetchedLines.size)
        assertEquals(50000L, fetchedLines[0].unitPricePaisa)
        assertEquals(50000L, fetchedLines[0].lineTotalPaisa)
    }
}
