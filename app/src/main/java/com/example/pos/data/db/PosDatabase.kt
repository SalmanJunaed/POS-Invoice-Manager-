package com.example.pos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest

@Database(
    entities = [
        CompanyHeaderEntity::class,
        OperatorEntity::class,
        ProductEntity::class,
        InvoiceEntity::class,
        InvoiceLineEntity::class,
        AppSettingsEntity::class,
        PaymentRecordEntity::class,
        CustomerEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class PosDatabase : RoomDatabase() {
    abstract fun companyHeaderDao(): CompanyHeaderDao
    abstract fun operatorDao(): OperatorDao
    abstract fun productDao(): ProductDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun paymentDao(): PaymentDao
    abstract fun customerDao(): CustomerDao

    companion object {
        @Volatile
        private var INSTANCE: PosDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // company_header
                db.execSQL("ALTER TABLE company_header ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE company_header SET created_at = updated_at WHERE created_at = 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_company_header_branch_number ON company_header(branch_number)")

                // invoices
                db.execSQL("ALTER TABLE invoices ADD COLUMN global_transaction_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoices ADD COLUMN branch_code TEXT NOT NULL DEFAULT '01'")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_invoices_global_transaction_id ON invoices(global_transaction_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_operator_id ON invoices(operator_id)")
                db.execSQL("UPDATE invoices SET global_transaction_id = lower(hex(randomblob(16))) WHERE global_transaction_id = ''")

                // invoice_lines
                db.execSQL("ALTER TABLE invoice_lines ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")

                // app_settings
                db.execSQL("ALTER TABLE app_settings ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")

                // payment_records
                db.execSQL("ALTER TABLE payment_records ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")

                // products indices
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_item_name ON products(item_name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_item_type ON products(item_type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_sort_order ON products(sort_order)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_is_active ON products(is_active)")

                // invoices indices
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_date_time ON invoices(date_time)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_customer_info ON invoices(customer_info)")

                // invoice_lines indices
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_lines_order_id ON invoice_lines(order_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_lines_item_id ON invoice_lines(item_id)")

                // payment_records indices
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_records_order_id ON payment_records(order_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_records_payment_method ON payment_records(payment_method)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_records_timestamp ON payment_records(timestamp)")

                // operators
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_operators_operator_phone ON operators(operator_phone)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_operators_is_active ON operators(is_active)")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 3 removes duplicate index on primary key order_id if present
                db.execSQL("DROP INDEX IF EXISTS index_invoices_order_id")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create customers table supporting alphanumeric customer_info, with createdAt and updatedAt
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS customers (
                        customer_info TEXT PRIMARY KEY NOT NULL,
                        customer_name TEXT NOT NULL,
                        phone_number TEXT NOT NULL,
                        address TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        total_orders INTEGER NOT NULL,
                        total_spent_paisa INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_customers_customer_info ON customers(customer_info)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_customer_name ON customers(customer_name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_phone_number ON customers(phone_number)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_updated_at ON customers(updated_at)")

                // Populate initial customers from existing invoices if any
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO customers (customer_info, customer_name, phone_number, address, notes, total_orders, total_spent_paisa, created_at, updated_at)
                    SELECT customer_info, '', customer_info, '', '', COUNT(*), SUM(raw_total_paisa), MIN(created_at), MAX(updated_at)
                    FROM invoices
                    WHERE customer_info IS NOT NULL AND customer_info != ''
                    GROUP BY customer_info
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add name_bn and name_en columns to products table
                db.execSQL("ALTER TABLE products ADD COLUMN name_bn TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN name_en TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_name_bn ON products(name_bn)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_name_en ON products(name_en)")

                // Backfill name_en and name_bn from existing item_name
                db.execSQL("UPDATE products SET name_en = item_name WHERE name_en = ''")
                // Populate common Bangla names for default items
                db.execSQL("UPDATE products SET name_bn = 'অকটেন' WHERE item_id = 'item-1'")
                db.execSQL("UPDATE products SET name_bn = 'ইঞ্জিন অয়েল' WHERE item_id = 'item-2'")
                db.execSQL("UPDATE products SET name_bn = 'এলপিজি গ্যাস' WHERE item_id = 'item-3'")
                db.execSQL("UPDATE products SET name_bn = 'ডিজেল' WHERE item_id = 'item-4'")
                db.execSQL("UPDATE products SET name_bn = 'পেট্রোল' WHERE item_id = 'item-5'")
                db.execSQL("UPDATE products SET name_bn = 'মবিল ১০W-৪০' WHERE item_id = 'item-6'")
                db.execSQL("UPDATE products SET name_bn = 'কার ওয়াশ সার্ভিস' WHERE item_id = 'item-7'")
                db.execSQL("UPDATE products SET name_bn = 'গ্রীসিং সার্ভিস' WHERE item_id = 'item-8'")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_rounded_total_bdt ON invoices(rounded_total_bdt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_raw_total_paisa ON invoices(raw_total_paisa)")
            }
        }

        fun hashPassword(password: String): String {
            return com.example.pos.core.security.PasswordSecurity.hashPassword(password)
        }

        fun getInstance(context: Context): PosDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PosDatabase::class.java,
                    "pos_invoice_master.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Populate default business records in background
                            CoroutineScope(Dispatchers.IO).launch {
                                prepopulateDatabase(getInstance(context))
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun prepopulateDatabase(database: PosDatabase) {
            val companyDao = database.companyHeaderDao()
            val operatorDao = database.operatorDao()
            val productDao = database.productDao()
            val invoiceDao = database.invoiceDao()
            val settingsDao = database.appSettingsDao()
            val customerDao = database.customerDao()

            // 1. Company Header Model
            companyDao.insertOrUpdate(
                CompanyHeaderEntity(
                    id = 1,
                    companyName = "মেসার্স রাকিব ফিলিং স্টেশন এন্ড এল পি জি অটোগ্যাস ফিলিং স্টেশন",
                    branchName = "মহাদেবপুর শাখা",
                    branchNumber = "01",
                    address = "আখেড়া, মহাদেবপুর, নওগাঁ",
                    phoneNumber = "০১৭১১-৮৭৫১৪৪",
                    logoUri = null,
                    updatedAt = System.currentTimeMillis()
                )
            )

            // 2. Operator Profile Model
            operatorDao.insertOperator(
                OperatorEntity(
                    userId = "OP-101",
                    operatorName = "Salman Junaed",
                    operatorPhone = "01700000000",
                    operatorAddress = "মহাদেবপুর, নওগাঁ",
                    passwordHash = hashPassword("1234"),
                    isActive = true,
                    rememberMe = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )

            // 3. Product / Service Catalog (with Bangla and English names from PRD)
            val initialProducts = listOf(
                ProductEntity(
                    itemId = "item-1",
                    itemName = "Octane",
                    nameBn = "অকটেন",
                    nameEn = "Octane",
                    itemType = "Liquid",
                    unit = "Liter",
                    defaultUnitPricePaisa = 13000L,
                    sortOrder = 1,
                    isActive = true
                ),
                ProductEntity(
                    itemId = "item-2",
                    itemName = "Engine Oil",
                    nameBn = "ইঞ্জিন অয়েল",
                    nameEn = "Engine Oil",
                    itemType = "Product",
                    unit = "Pcs",
                    defaultUnitPricePaisa = 50000L,
                    sortOrder = 2,
                    isActive = true
                ),
                ProductEntity(
                    itemId = "item-3",
                    itemName = "LPG Gas",
                    nameBn = "এলপিজি গ্যাস",
                    nameEn = "LPG Gas",
                    itemType = "Liquid",
                    unit = "Liter",
                    defaultUnitPricePaisa = 9500L,
                    sortOrder = 3,
                    isActive = true
                ),
                ProductEntity(
                    itemId = "item-4",
                    itemName = "Diesel",
                    nameBn = "ডিজেল",
                    nameEn = "Diesel",
                    itemType = "Liquid",
                    unit = "Liter",
                    defaultUnitPricePaisa = 10500L,
                    sortOrder = 4,
                    isActive = true
                ),
                ProductEntity(
                    itemId = "item-5",
                    itemName = "Petrol",
                    nameBn = "পেট্রোল",
                    nameEn = "Petrol",
                    itemType = "Liquid",
                    unit = "Liter",
                    defaultUnitPricePaisa = 12500L,
                    sortOrder = 5,
                    isActive = true
                ),
                ProductEntity(
                    itemId = "item-6",
                    itemName = "Mobil 10W-40",
                    nameBn = "মবিল ১০W-৪০",
                    nameEn = "Mobil 10W-40",
                    itemType = "Product",
                    unit = "Liter",
                    defaultUnitPricePaisa = 68000L,
                    sortOrder = 6,
                    isActive = true
                ),
                ProductEntity(
                    itemId = "item-7",
                    itemName = "Car Wash Service",
                    nameBn = "কার ওয়াশ সার্ভিস",
                    nameEn = "Car Wash Service",
                    itemType = "Service",
                    unit = "Quantity",
                    defaultUnitPricePaisa = 35000L,
                    sortOrder = 7,
                    isActive = true
                ),
                ProductEntity(
                    itemId = "item-8",
                    itemName = "Greasing Service",
                    nameBn = "গ্রীসিং সার্ভিস",
                    nameEn = "Greasing Service",
                    itemType = "Service",
                    unit = "Quantity",
                    defaultUnitPricePaisa = 15000L,
                    sortOrder = 8,
                    isActive = true
                )
            )
            productDao.insertProducts(initialProducts)

            // 4. Initial Sample Invoice (from PRD Page 3)
            val sampleInvoice = InvoiceEntity(
                orderId = "INV-10293",
                dateTime = System.currentTimeMillis() - 3600000L,
                operatorId = "OP-101",
                operatorName = "Salman Junaed",
                customerInfo = "01700000000 / ABC-123",
                rawTotalPaisa = 431125L, // 4311.25 BDT
                roundedTotalBdt = 4311L, // 4311.00 BDT
                footerNote = "*** নিখুঁত মানের নিশ্চয়তা ***\nধন্যবাদ আবার আসবেন",
                createdAt = System.currentTimeMillis() - 3600000L,
                updatedAt = System.currentTimeMillis() - 3600000L
            )
            val sampleLines = listOf(
                InvoiceLineEntity(
                    orderId = "INV-10293",
                    itemId = "item-1",
                    itemName = "Octane",
                    itemType = "Liquid",
                    unit = "L",
                    quantityStr = "10.5",
                    unitPricePaisa = 13000L,
                    lineTotalPaisa = 136500L,
                    sortOrder = 1
                ),
                InvoiceLineEntity(
                    orderId = "INV-10293",
                    itemId = "item-2",
                    itemName = "Engine Oil",
                    itemType = "Product",
                    unit = "Pcs",
                    quantityStr = "1.0",
                    unitPricePaisa = 50000L,
                    lineTotalPaisa = 50000L,
                    sortOrder = 2
                ),
                InvoiceLineEntity(
                    orderId = "INV-10293",
                    itemId = "item-3",
                    itemName = "LPG Gas",
                    itemType = "Liquid",
                    unit = "L",
                    quantityStr = "25.75",
                    unitPricePaisa = 9500L,
                    lineTotalPaisa = 244625L,
                    sortOrder = 3
                )
            )
            invoiceDao.saveFullOrder(sampleInvoice, sampleLines)

            // 5. Initial Customers (Supporting alphanumeric examples from PRD)
            val now = System.currentTimeMillis()
            customerDao.insertOrUpdateCustomer(
                CustomerEntity(
                    customerInfo = "01700000000",
                    customerName = "সাধারণ ক্রেতা",
                    phoneNumber = "01700000000",
                    address = "নওগাঁ",
                    notes = "নিয়মিত গ্রাহক",
                    totalOrders = 1,
                    totalSpentPaisa = 431125L,
                    createdAt = now,
                    updatedAt = now
                )
            )
            customerDao.insertOrUpdateCustomer(
                CustomerEntity(
                    customerInfo = "ABC-123",
                    customerName = "Toyota Sedan",
                    phoneNumber = "01811223344",
                    address = "মহাদেবপুর",
                    notes = "Vehicle Plate ABC-123",
                    totalOrders = 0,
                    totalSpentPaisa = 0L,
                    createdAt = now,
                    updatedAt = now
                )
            )
            customerDao.insertOrUpdateCustomer(
                CustomerEntity(
                    customerInfo = "CUSTOMER-001",
                    customerName = "Prime Logistics",
                    phoneNumber = "01999887766",
                    address = "বগুড়া",
                    notes = "Commercial Account",
                    totalOrders = 0,
                    totalSpentPaisa = 0L,
                    createdAt = now,
                    updatedAt = now
                )
            )

            // 6. App Settings
            settingsDao.insertOrUpdate(
                AppSettingsEntity(
                    id = 1,
                    language = "bn",
                    themeMode = "LIGHT",
                    paperSize = "58mm",
                    autoCutterEnabled = true,
                    bluetoothPrinterAddress = null,
                    bluetoothPrinterName = null,
                    footerNoteLine1 = "*** নিখুঁত মানের নিশ্চয়তা ***",
                    footerNoteLine2 = "ধন্যবাদ আবার আসবেন",
                    nextOrderSequence = 10294L
                )
            )
        }
    }
}
