package com.example.pos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.repository.HistoryRepositoryImpl
import com.example.pos.data.repository.HistorySortOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SalesHistoryPerformanceAndReportsTest {

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
    fun testLargeDatasetPaginationAndDatabaseAggregations() = runBlocking {
        val invoiceDao = db.invoiceDao()
        val totalCount = 3000
        val baseTime = 1700000000000L

        // Generate batch of invoices and lines
        val invoices = ArrayList<InvoiceEntity>(totalCount)
        val lines = ArrayList<InvoiceLineEntity>(totalCount * 2)

        var expectedRawPaisaSum = 0L
        var expectedRoundedBdtSum = 0L

        for (i in 1..totalCount) {
            val orderId = String.format("INV-%06d", i)
            val rawPaisa = (i * 1000L) // e.g. 10.00 BDT to 30000.00 BDT
            val roundedBdt = (rawPaisa / 100L)
            val customerPhone = if (i % 3 == 0) "01711000111" else "01822000222"
            val timestamp = baseTime + (i * 60000L) // Each 1 minute apart

            expectedRawPaisaSum += rawPaisa
            expectedRoundedBdtSum += roundedBdt

            invoices.add(
                InvoiceEntity(
                    orderId = orderId,
                    globalTransactionId = UUID.randomUUID().toString(),
                    branchCode = "01",
                    dateTime = timestamp,
                    operatorId = "OP-1",
                    operatorName = "Operator A",
                    customerInfo = customerPhone,
                    rawTotalPaisa = rawPaisa,
                    roundedTotalBdt = roundedBdt,
                    footerNote = ""
                )
            )

            // 2 items per invoice
            lines.add(
                InvoiceLineEntity(
                    orderId = orderId,
                    itemId = if (i % 2 == 0) "item-octane" else "item-petrol",
                    itemName = if (i % 2 == 0) "অকটেন" else "পেট্রোল",
                    itemType = "LIQUID",
                    unit = "L",
                    quantityStr = "1.00",
                    unitPricePaisa = rawPaisa / 2,
                    lineTotalPaisa = rawPaisa / 2,
                    sortOrder = 1
                )
            )
            lines.add(
                InvoiceLineEntity(
                    orderId = orderId,
                    itemId = "item-mobil",
                    itemName = "মবিল সুপার",
                    itemType = "PRODUCT",
                    unit = "L",
                    quantityStr = "1.00",
                    unitPricePaisa = rawPaisa / 2,
                    lineTotalPaisa = rawPaisa / 2,
                    sortOrder = 2
                )
            )
        }

        // Batch insert into in-memory SQLite database
        invoiceDao.insertInvoices(invoices)
        invoiceDao.insertInvoiceLines(lines)

        // 1. Verify Database Count without loading dataset
        val count = historyRepo.getInvoiceCount("", "", "", 0L, Long.MAX_VALUE).first()
        assertEquals(totalCount, count)

        // 2. Test Pagination (Page 0, Page 1)
        val pageSize = 25
        val page0 = historyRepo.getInvoicesPaged(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = Long.MAX_VALUE,
            sortOption = HistorySortOption.DATE_DESC,
            limit = pageSize,
            offset = 0
        ).first()

        assertEquals(pageSize, page0.size)
        // Newest should be last inserted
        assertEquals(String.format("INV-%06d", totalCount), page0.first().orderId)

        val page1 = historyRepo.getInvoicesPaged(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = Long.MAX_VALUE,
            sortOption = HistorySortOption.DATE_DESC,
            limit = pageSize,
            offset = 25
        ).first()

        assertEquals(pageSize, page1.size)
        assertEquals(String.format("INV-%06d", totalCount - 25), page1.first().orderId)

        // 3. Test Sorting Options
        // Date ASC (Oldest first)
        val dateAsc = historyRepo.getInvoicesPaged(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = Long.MAX_VALUE,
            sortOption = HistorySortOption.DATE_ASC,
            limit = 10,
            offset = 0
        ).first()
        assertEquals("INV-000001", dateAsc.first().orderId)

        // Amount DESC (Highest total first)
        val amountDesc = historyRepo.getInvoicesPaged(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = Long.MAX_VALUE,
            sortOption = HistorySortOption.AMOUNT_DESC,
            limit = 10,
            offset = 0
        ).first()
        assertEquals(String.format("INV-%06d", totalCount), amountDesc.first().orderId)
        assertTrue(amountDesc.first().roundedTotalBdt >= amountDesc.last().roundedTotalBdt)

        // Amount ASC (Lowest total first)
        val amountAsc = historyRepo.getInvoicesPaged(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = Long.MAX_VALUE,
            sortOption = HistorySortOption.AMOUNT_ASC,
            limit = 10,
            offset = 0
        ).first()
        assertEquals("INV-000001", amountAsc.first().orderId)
        assertTrue(amountAsc.first().roundedTotalBdt <= amountAsc.last().roundedTotalBdt)

        // 4. Test Search Filters
        // Order ID Filter
        val singleOrder = historyRepo.getInvoicesPaged(
            query = "",
            orderIdFilter = "INV-000500",
            customerFilter = "",
            startDate = 0L,
            endDate = Long.MAX_VALUE,
            sortOption = HistorySortOption.DATE_DESC,
            limit = 10,
            offset = 0
        ).first()
        assertEquals(1, singleOrder.size)
        assertEquals("INV-000500", singleOrder.first().orderId)

        // Customer Phone Filter
        val customerInvoices = historyRepo.getInvoicesPaged(
            query = "",
            orderIdFilter = "",
            customerFilter = "01711000111",
            startDate = 0L,
            endDate = Long.MAX_VALUE,
            sortOption = HistorySortOption.DATE_DESC,
            limit = 50,
            offset = 0
        ).first()
        assertEquals(50, customerInvoices.size)
        customerInvoices.forEach {
            assertEquals("01711000111", it.customerInfo)
        }

        // 5. Test Database-Side Aggregations
        val agg = historyRepo.getAggregatedSales("", "", "", 0L, Long.MAX_VALUE).first()
        assertEquals(totalCount, agg.totalOrders)
        assertEquals(expectedRawPaisaSum, agg.totalRawPaisa)
        assertEquals(expectedRoundedBdtSum, agg.totalRoundedBdt)

        // 6. Test Top Selling Items Database Query
        val topItems = historyRepo.getTopSellingItems("", "", "", 0L, Long.MAX_VALUE, 5).first()
        assertTrue(topItems.isNotEmpty())
        assertEquals("মবিল সুপার", topItems.first().itemName) // present in all invoices
        assertEquals(totalCount, topItems.first().orderCount)

        // 7. Verify Line Items Fetching
        val sampleLines = historyRepo.getLinesForOrders(listOf("INV-000100", "INV-000200"))
        assertEquals(4, sampleLines.size) // 2 lines per invoice
    }
}
