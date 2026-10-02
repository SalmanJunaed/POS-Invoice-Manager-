package com.example.pos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.PosDatabase
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnalyticsAggregatesTest {

    private lateinit var db: PosDatabase

    private val utcDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun parseUtc(str: String): Long = utcDateFormat.parse(str)!!.time

    @Before
    fun setup() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSalesAggregationByDayMonthYearWithKnownValues() = runBlocking {
        val dao = db.invoiceDao()

        val t1 = parseUtc("2024-01-15 10:00:00")
        val t2 = parseUtc("2024-01-15 14:30:00")
        val t3 = parseUtc("2024-01-15 18:00:00")
        val t4 = parseUtc("2024-01-16 09:15:00")
        val t5 = parseUtc("2024-01-16 16:45:00")
        val t6 = parseUtc("2024-02-10 11:20:00")
        val t7 = parseUtc("2025-03-01 12:00:00")

        val invoices = listOf(
            InvoiceEntity(orderId = "INV-001", customerInfo = "01711111111", rawTotalPaisa = 1050L, roundedTotalBdt = 11L, dateTime = t1, operatorId = "OP-1", operatorName = "Operator A"),
            InvoiceEntity(orderId = "INV-002", customerInfo = "01722222222", rawTotalPaisa = 2025L, roundedTotalBdt = 20L, dateTime = t2, operatorId = "OP-1", operatorName = "Operator A"),
            InvoiceEntity(orderId = "INV-003", customerInfo = "01733333333", rawTotalPaisa = 5000L, roundedTotalBdt = 50L, dateTime = t3, operatorId = "OP-1", operatorName = "Operator A"),
            InvoiceEntity(orderId = "INV-004", customerInfo = "01744444444", rawTotalPaisa = 3000L, roundedTotalBdt = 30L, dateTime = t4, operatorId = "OP-1", operatorName = "Operator A"),
            InvoiceEntity(orderId = "INV-005", customerInfo = "01755555555", rawTotalPaisa = 7000L, roundedTotalBdt = 70L, dateTime = t5, operatorId = "OP-1", operatorName = "Operator A"),
            InvoiceEntity(orderId = "INV-006", customerInfo = "01766666666", rawTotalPaisa = 15000L, roundedTotalBdt = 150L, dateTime = t6, operatorId = "OP-1", operatorName = "Operator A"),
            InvoiceEntity(orderId = "INV-007", customerInfo = "01777777777", rawTotalPaisa = 40000L, roundedTotalBdt = 400L, dateTime = t7, operatorId = "OP-1", operatorName = "Operator A")
        )

        dao.insertInvoices(invoices)

        // 1. Test Sales by Day
        val dayAggregates = dao.getSalesByDayFlow(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = parseUtc("2024-01-17 00:00:00")
        ).first()
        assertEquals(2, dayAggregates.size)

        val day1 = dayAggregates.first { it.periodKey == "2024-01-15" }
        assertEquals(3, day1.transactionCount)
        assertEquals(1050L + 2025L + 5000L, day1.totalRawPaisa) // 8075L
        assertEquals(11L + 20L + 50L, day1.totalRoundedBdt) // 81L

        val day2 = dayAggregates.first { it.periodKey == "2024-01-16" }
        assertEquals(2, day2.transactionCount)
        assertEquals(3000L + 7000L, day2.totalRawPaisa) // 10000L
        assertEquals(30L + 70L, day2.totalRoundedBdt) // 100L

        // 2. Test Sales by Month
        val monthAggregates = dao.getSalesByMonthFlow(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = parseUtc("2024-12-31 23:59:59")
        ).first()
        assertEquals(2, monthAggregates.size)

        val jan = monthAggregates.first { it.periodKey == "2024-01" }
        assertEquals(5, jan.transactionCount)
        assertEquals(8075L + 10000L, jan.totalRawPaisa) // 18075L
        assertEquals(81L + 100L, jan.totalRoundedBdt) // 181L

        val feb = monthAggregates.first { it.periodKey == "2024-02" }
        assertEquals(1, feb.transactionCount)
        assertEquals(15000L, feb.totalRawPaisa)
        assertEquals(150L, feb.totalRoundedBdt)

        // 3. Test Sales by Year
        val yearAggregates = dao.getSalesByYearFlow(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = Long.MAX_VALUE
        ).first()
        assertEquals(2, yearAggregates.size)

        val y2024 = yearAggregates.first { it.periodKey == "2024" }
        assertEquals(6, y2024.transactionCount)
        assertEquals(18075L + 15000L, y2024.totalRawPaisa) // 33075L
        assertEquals(181L + 150L, y2024.totalRoundedBdt) // 331L

        val y2025 = yearAggregates.first { it.periodKey == "2025" }
        assertEquals(1, y2025.transactionCount)
        assertEquals(40000L, y2025.totalRawPaisa)
        assertEquals(400L, y2025.totalRoundedBdt)

        // 4. Test Empty Result for Non-existent Range
        val emptyAggregates = dao.getSalesByDayFlow(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = parseUtc("2023-01-01 00:00:00"),
            endDate = parseUtc("2023-01-31 23:59:59")
        ).first()
        assertTrue(emptyAggregates.isEmpty())
    }
}
