package com.example.pos

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.ui.viewmodel.PosViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BillingRateFieldRegressionTest {

    private lateinit var app: Application
    private lateinit var db: PosDatabase
    private lateinit var viewModel: PosViewModel

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext<Application>()
        db = PosDatabase.getInstance(app)
        viewModel = PosViewModel(app)
    }

    @After
    fun tearDown() {
        // Clear draft for subsequent tests
        viewModel.clearDraft()
    }

    @Test
    fun testBillingRateFieldRegressionSuite() = runBlocking {
        // Seed catalog product: Octane / অকটেন with default price 130 BDT (13000 Paisa)
        val octane = ProductEntity(
            itemId = "prod-octane-reg",
            itemName = "Octane",
            nameBn = "অকটেন",
            nameEn = "Octane",
            itemType = "Fuel",
            unit = "Liter",
            defaultUnitPricePaisa = 13000L,
            sortOrder = 1
        )
        db.productDao().insertProduct(octane)

        // -------------------------------------------------------------
        // TEST A: Add অকটেন, catalog price 130
        // -> Rate/দর shows 130
        // -> Quantity shows 1
        // -> Total shows 130
        // -------------------------------------------------------------
        viewModel.addItemFromCatalog(octane)
        val itemsA = viewModel.draftItems.first()
        assertEquals(1, itemsA.size)
        val itemA = itemsA[0]
        assertEquals("1", itemA.quantityStr)
        assertEquals("130", itemA.unitPriceBdtStr)
        assertEquals(13000L, itemA.unitPricePaisa)
        assertEquals(13000L, itemA.lineTotalPaisa)
        assertEquals("130.00", FinancialCalculationEngine.formatPaisaToBdt(itemA.lineTotalPaisa))

        // -------------------------------------------------------------
        // TEST B: Change Rate/দর 130 -> 120
        // -> Rate shows 120
        // -> Total becomes 120
        // -> No "120130"
        // -------------------------------------------------------------
        viewModel.updateItemUnitPrice(itemA.tempId, "120")
        val itemsB = viewModel.draftItems.first()
        val itemB = itemsB[0]
        assertEquals("120", itemB.unitPriceBdtStr)
        assertNotEquals("120130", itemB.unitPriceBdtStr)
        assertEquals(12000L, itemB.unitPricePaisa)
        assertEquals(12000L, itemB.lineTotalPaisa)
        assertEquals("120.00", FinancialCalculationEngine.formatPaisaToBdt(itemB.lineTotalPaisa))

        // -------------------------------------------------------------
        // TEST C: Quantity 2.5, Rate 120
        // -> Total becomes 300
        // -------------------------------------------------------------
        viewModel.updateItemQuantity(itemB.tempId, "2.5")
        val itemsC = viewModel.draftItems.first()
        val itemC = itemsC[0]
        assertEquals("2.5", itemC.quantityStr)
        assertEquals("120", itemC.unitPriceBdtStr)
        assertEquals(12000L, itemC.unitPricePaisa)
        // 2.5 * 120 = 300.00 BDT -> 30000 Paisa
        assertEquals(30000L, itemC.lineTotalPaisa)
        assertEquals("300.00", FinancialCalculationEngine.formatPaisaToBdt(itemC.lineTotalPaisa))

        // -------------------------------------------------------------
        // TEST D: Change Rate 120 -> 130
        // -> Total becomes 325 for quantity 2.5
        // -------------------------------------------------------------
        viewModel.updateItemUnitPrice(itemC.tempId, "130")
        val itemsD = viewModel.draftItems.first()
        val itemD = itemsD[0]
        assertEquals("2.5", itemD.quantityStr)
        assertEquals("130", itemD.unitPriceBdtStr)
        assertEquals(13000L, itemD.unitPricePaisa)
        // 2.5 * 130 = 325.00 BDT -> 32500 Paisa
        assertEquals(32500L, itemD.lineTotalPaisa)
        assertEquals("325.00", FinancialCalculationEngine.formatPaisaToBdt(itemD.lineTotalPaisa))

        // -------------------------------------------------------------
        // TEST E: Verify Product Management still shows catalog price 130
        // -------------------------------------------------------------
        val catalogProduct = db.productDao().getProductById("prod-octane-reg")
        assertNotNull(catalogProduct)
        assertEquals(13000L, catalogProduct?.defaultUnitPricePaisa)
        assertEquals("130", FinancialCalculationEngine.formatPaisaToBdtInput(catalogProduct!!.defaultUnitPricePaisa))

        // -------------------------------------------------------------
        // TEST F: Checkout and verify saved invoice uses the actual invoice-specific rate
        // -------------------------------------------------------------
        val currentOrderId = viewModel.draftOrderId.value
        viewModel.checkoutAndSave(printDirectly = false)

        // Allow coroutine to persist
        kotlinx.coroutines.delay(200)

        val savedInvoice = db.invoiceDao().getInvoiceById(currentOrderId)
        assertNotNull(savedInvoice)
        assertEquals(32500L, savedInvoice?.rawTotalPaisa)
        assertEquals(325L, savedInvoice?.roundedTotalBdt)

        val savedLines = db.invoiceDao().getLinesForOrder(currentOrderId)
        assertEquals(1, savedLines.size)
        val savedLine = savedLines[0]
        assertEquals("prod-octane-reg", savedLine.itemId)
        assertEquals(13000L, savedLine.unitPricePaisa)
        assertEquals(32500L, savedLine.lineTotalPaisa)
        assertEquals("2.5", savedLine.quantityStr)
    }

    @Test
    fun testFormatPaisaToBdtInputCleanFormatting() {
        assertEquals("130", FinancialCalculationEngine.formatPaisaToBdtInput(13000L))
        assertEquals("120", FinancialCalculationEngine.formatPaisaToBdtInput(12000L))
        assertEquals("105", FinancialCalculationEngine.formatPaisaToBdtInput(10500L))
        assertEquals("130.5", FinancialCalculationEngine.formatPaisaToBdtInput(13050L))
        assertEquals("130.75", FinancialCalculationEngine.formatPaisaToBdtInput(13075L))
        assertEquals("0", FinancialCalculationEngine.formatPaisaToBdtInput(0L))
    }
}
