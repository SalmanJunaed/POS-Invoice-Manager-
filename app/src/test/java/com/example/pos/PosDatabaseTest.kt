package com.example.pos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.CustomerEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.OperatorEntity
import com.example.pos.data.db.PaymentRecordEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PosDatabaseTest {

    private lateinit var db: PosDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndRetrieveInvoiceWithGlobalIdAndBranch() = runBlocking {
        val invoiceDao = db.invoiceDao()
        val globalId = UUID.randomUUID().toString()

        val invoice = InvoiceEntity(
            orderId = "INV-20001",
            globalTransactionId = globalId,
            branchCode = "02",
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-101",
            operatorName = "Rahim Ahmed",
            customerInfo = "01811223344",
            rawTotalPaisa = 136500L,
            roundedTotalBdt = 1365L,
            footerNote = "Thank you",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val lines = listOf(
            InvoiceLineEntity(
                orderId = "INV-20001",
                itemId = "item-1",
                itemName = "Octane Premium",
                itemType = "Liquid",
                unit = "L",
                quantityStr = "10.5",
                unitPricePaisa = 13000L,
                lineTotalPaisa = 136500L,
                sortOrder = 1
            )
        )

        invoiceDao.saveFullOrder(invoice, lines)

        // Retrieve by Order ID
        val retrieved = invoiceDao.getInvoiceById("INV-20001")
        assertNotNull(retrieved)
        assertEquals("INV-20001", retrieved?.orderId)
        assertEquals(globalId, retrieved?.globalTransactionId)
        assertEquals("02", retrieved?.branchCode)
        assertEquals(136500L, retrieved?.rawTotalPaisa)

        // Retrieve by Global ID
        val retrievedByGlobal = invoiceDao.getInvoiceByGlobalId(globalId)
        assertNotNull(retrievedByGlobal)
        assertEquals("INV-20001", retrievedByGlobal?.orderId)

        // Verify Lines
        val retrievedLines = invoiceDao.getLinesForOrder("INV-20001")
        assertEquals(1, retrievedLines.size)
        assertEquals("Octane Premium", retrievedLines[0].itemName)
        assertEquals("10.5", retrievedLines[0].quantityStr)
        assertEquals(136500L, retrievedLines[0].lineTotalPaisa)
    }

    @Test
    fun testHistoricalInvoiceSnapshotIntegrityWhenCatalogChanges() = runBlocking {
        val productDao = db.productDao()
        val invoiceDao = db.invoiceDao()

        // 1. Add initial product in catalog
        val product = ProductEntity(
            itemId = "prod-99",
            itemName = "Diesel Standard",
            itemType = "Liquid",
            unit = "L",
            defaultUnitPricePaisa = 10000L, // 100 BDT
            sortOrder = 1,
            isActive = true
        )
        productDao.insertProduct(product)

        // 2. Create invoice snapshotting this product
        val invoice = InvoiceEntity(
            orderId = "INV-20002",
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-101",
            operatorName = "Rahim",
            customerInfo = "01999999999",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L
        )
        val line = InvoiceLineEntity(
            orderId = "INV-20002",
            itemId = "prod-99",
            itemName = "Diesel Standard",
            itemType = "Liquid",
            unit = "L",
            quantityStr = "5.0",
            unitPricePaisa = 10000L,
            lineTotalPaisa = 50000L,
            sortOrder = 1
        )
        invoiceDao.saveFullOrder(invoice, listOf(line))

        // 3. Modify product price & name in catalog or delete it completely
        productDao.insertProduct(
            product.copy(
                itemName = "Diesel Standard - Price Hike",
                defaultUnitPricePaisa = 15000L
            )
        )
        productDao.deleteProductById("prod-99")

        // 4. Verify historical invoice item is completely unaffected!
        val preservedLines = invoiceDao.getLinesForOrder("INV-20002")
        assertEquals(1, preservedLines.size)
        assertEquals("Diesel Standard", preservedLines[0].itemName)
        assertEquals(10000L, preservedLines[0].unitPricePaisa)
        assertEquals(50000L, preservedLines[0].lineTotalPaisa)
        assertEquals("5.0", preservedLines[0].quantityStr)
    }

    @Test
    fun testPaginationForHighVolumeTransactions() = runBlocking {
        val invoiceDao = db.invoiceDao()

        // Insert 25 simulated invoices
        val now = System.currentTimeMillis()
        for (i in 1..25) {
            val inv = InvoiceEntity(
                orderId = "INV-${10000 + i}",
                dateTime = now - (i * 1000L),
                operatorId = "OP-1",
                operatorName = "Operator",
                customerInfo = "Customer $i",
                rawTotalPaisa = i * 10000L,
                roundedTotalBdt = (i * 100).toLong()
            )
            invoiceDao.insertInvoice(inv)
        }

        // Fetch page 1 (limit 10, offset 0)
        val page1 = invoiceDao.getInvoicesPagedFlow("", 0L, now + 100000L, limit = 10, offset = 0).first()
        assertEquals(10, page1.size)
        assertEquals("INV-10001", page1[0].orderId)

        // Fetch page 2 (limit 10, offset 10)
        val page2 = invoiceDao.getInvoicesPagedFlow("", 0L, now + 100000L, limit = 10, offset = 10).first()
        assertEquals(10, page2.size)
        assertEquals("INV-10011", page2[0].orderId)

        // Aggregation query test
        val agg = invoiceDao.getAggregatedSalesFlow("", 0L, now + 100000L).first()
        assertEquals(25, agg.totalOrders)
        assertTrue(agg.totalRawPaisa > 0)
    }

    @Test
    fun testMigration1To2Execution() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("migration_test.db")
        if (dbFile.exists()) dbFile.delete()

        // Create a V1 database directly using SQLite
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
            .create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name("migration_test.db")
                    .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS company_header (
                                    id INTEGER PRIMARY KEY NOT NULL,
                                    company_name TEXT NOT NULL,
                                    branch_name TEXT NOT NULL,
                                    branch_number TEXT NOT NULL,
                                    address TEXT NOT NULL,
                                    phone_number TEXT NOT NULL,
                                    logo_uri TEXT,
                                    updated_at INTEGER NOT NULL
                                )
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS operators (
                                    user_id TEXT PRIMARY KEY NOT NULL,
                                    operator_name TEXT NOT NULL,
                                    operator_phone TEXT NOT NULL,
                                    operator_address TEXT NOT NULL,
                                    password_hash TEXT NOT NULL,
                                    is_active INTEGER NOT NULL,
                                    remember_me INTEGER NOT NULL,
                                    created_at INTEGER NOT NULL,
                                    updated_at INTEGER NOT NULL
                                )
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS products (
                                    item_id TEXT PRIMARY KEY NOT NULL,
                                    item_name TEXT NOT NULL,
                                    item_type TEXT NOT NULL,
                                    unit TEXT NOT NULL,
                                    default_unit_price_paisa INTEGER NOT NULL,
                                    sort_order INTEGER NOT NULL,
                                    is_active INTEGER NOT NULL,
                                    created_at INTEGER NOT NULL,
                                    updated_at INTEGER NOT NULL
                                )
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS invoices (
                                    order_id TEXT PRIMARY KEY NOT NULL,
                                    date_time INTEGER NOT NULL,
                                    operator_id TEXT NOT NULL,
                                    operator_name TEXT NOT NULL,
                                    customer_info TEXT NOT NULL,
                                    raw_total_paisa INTEGER NOT NULL,
                                    rounded_total_bdt INTEGER NOT NULL,
                                    footer_note TEXT NOT NULL,
                                    created_at INTEGER NOT NULL,
                                    updated_at INTEGER NOT NULL
                                )
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS invoice_lines (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                    order_id TEXT NOT NULL,
                                    item_id TEXT NOT NULL,
                                    item_name TEXT NOT NULL,
                                    item_type TEXT NOT NULL,
                                    unit TEXT NOT NULL,
                                    quantity_str TEXT NOT NULL,
                                    unit_price_paisa INTEGER NOT NULL,
                                    line_total_paisa INTEGER NOT NULL,
                                    sort_order INTEGER NOT NULL,
                                    FOREIGN KEY(order_id) REFERENCES invoices(order_id) ON UPDATE NO ACTION ON DELETE CASCADE
                                )
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS app_settings (
                                    id INTEGER PRIMARY KEY NOT NULL,
                                    language TEXT NOT NULL,
                                    theme_mode TEXT NOT NULL,
                                    paper_size TEXT NOT NULL,
                                    auto_cutter_enabled INTEGER NOT NULL,
                                    bluetooth_printer_address TEXT,
                                    bluetooth_printer_name TEXT,
                                    footer_note_line1 TEXT NOT NULL,
                                    footer_note_line2 TEXT NOT NULL,
                                    next_order_sequence INTEGER NOT NULL
                                )
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS payment_records (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                    order_id TEXT NOT NULL,
                                    payment_method TEXT NOT NULL,
                                    amount_paid_paisa INTEGER NOT NULL,
                                    transaction_ref TEXT,
                                    timestamp INTEGER NOT NULL,
                                    FOREIGN KEY(order_id) REFERENCES invoices(order_id) ON UPDATE NO ACTION ON DELETE CASCADE
                                )
                                """.trimIndent()
                            )
                            // Insert a dummy company header and a v1 product
                            db.execSQL(
                                "INSERT INTO company_header VALUES (1, 'Test Corp', 'Main', '01', 'Dhaka', '017', NULL, 12345678)"
                            )
                            db.execSQL(
                                "INSERT INTO products VALUES ('prod-v1', 'Octane Old', 'Liquid', 'L', 13000, 1, 1, 1000, 1000)"
                            )
                        }

                        override fun onUpgrade(
                            db: androidx.sqlite.db.SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) {}
                    })
                    .build()
            )
        val v1Db = helper.writableDatabase
        v1Db.close()

        // Now open with Room and apply MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, and MIGRATION_5_6
        val migratedDb = Room.databaseBuilder(context, PosDatabase::class.java, "migration_test.db")
            .addMigrations(
                PosDatabase.MIGRATION_1_2,
                PosDatabase.MIGRATION_2_3,
                PosDatabase.MIGRATION_3_4,
                PosDatabase.MIGRATION_4_5,
                PosDatabase.MIGRATION_5_6
            )
            .allowMainThreadQueries()
            .build()

        val header = runBlocking { migratedDb.companyHeaderDao().getCompanyHeader() }
        assertNotNull(header)
        assertEquals("Test Corp", header?.companyName)
        assertEquals(12345678L, header?.createdAt)

        // Verify customers table created in migration 3->4
        val customerDao = migratedDb.customerDao()
        assertNotNull(customerDao)
        val customer = CustomerEntity(
            customerInfo = "ABC-123",
            customerName = "Toyota Sedan",
            phoneNumber = "01811223344",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        runBlocking {
            customerDao.insertOrUpdateCustomer(customer)
            val fetched = customerDao.getCustomerByInfo("ABC-123")
            assertNotNull(fetched)
            assertEquals("ABC-123", fetched?.customerInfo)
            assertEquals("Toyota Sedan", fetched?.customerName)
            assertEquals(1000L, fetched?.createdAt)
            assertEquals(2000L, fetched?.updatedAt)
        }

        // Verify products table migrated in migration 4->5 with bilingual columns
        val productDao = migratedDb.productDao()
        assertNotNull(productDao)
        runBlocking {
            val v1Product = productDao.getProductById("prod-v1")
            assertNotNull(v1Product)
            assertEquals("Octane Old", v1Product?.itemName)
            assertEquals("", v1Product?.nameBn)
            assertEquals("Octane Old", v1Product?.nameEn)

            // Insert new product with bilingual names into migrated DB
            val newProduct = ProductEntity(
                itemId = "prod-v5",
                itemName = "Diesel",
                nameBn = "ডিজেল",
                nameEn = "Diesel",
                itemType = "Liquid",
                unit = "Liter",
                defaultUnitPricePaisa = 10500,
                sortOrder = 2
            )
            productDao.insertProduct(newProduct)
            val fetchedNew = productDao.getProductById("prod-v5")
            assertNotNull(fetchedNew)
            assertEquals("ডিজেল", fetchedNew?.nameBn)
            assertEquals("Diesel", fetchedNew?.nameEn)
            assertEquals("ডিজেল", fetchedNew?.getDisplayName("bn"))
            assertEquals("Diesel", fetchedNew?.getDisplayName("en"))
        }

        migratedDb.close()
        dbFile.delete()
    }
}
