package com.example.pos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.security.PasswordSecurity
import com.example.pos.core.security.SecureSessionManager
import com.example.pos.data.db.OperatorEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.repository.PosRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalAuthAndSessionSecurityTest {

    private lateinit var db: PosDatabase
    private lateinit var context: Context
    private lateinit var repository: PosRepository
    private lateinit var sessionManager: SecureSessionManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PosRepository(context, db)
        sessionManager = SecureSessionManager.getInstance(context)
        sessionManager.clearSession(clearRememberedPhone = true)
    }

    @After
    fun teardown() {
        db.close()
        sessionManager.clearSession(clearRememberedPhone = true)
    }

    @Test
    fun testPasswordHashingIsIrreversibleAndSecure() {
        val rawPassword = "SecurePassword@123"
        val hash = PasswordSecurity.hashPassword(rawPassword)

        assertFalse("Password should never be stored as plain text", hash == rawPassword)
        assertTrue("Hash should be non-blank", hash.isNotBlank())
        assertTrue("Password verification should succeed with correct credentials",
            PasswordSecurity.verifyPassword(rawPassword, hash))
        assertFalse("Password verification should fail with wrong credentials",
            PasswordSecurity.verifyPassword("WrongPassword", hash))
    }

    @Test
    fun testRegistrationWithCompleteFieldsAndSecureHash() = runBlocking {
        val name = "Ataur Rahman"
        val phone = "01711223344"
        val address = "মহাদেবপুর, নওগাঁ"
        val password = "SuperSecretPin99"

        val regResult = repository.registerOperator(name, phone, address, password)
        assertTrue("Registration must succeed", regResult.isSuccess)

        val operator = regResult.getOrNull()
        assertNotNull(operator)
        assertEquals(name, operator!!.operatorName)
        assertEquals(phone, operator.operatorPhone)
        assertEquals(address, operator.operatorAddress)
        assertFalse("Stored password must not be plain text", operator.passwordHash == password)
        assertTrue(operator.isActive)

        // Verify active operator in DB
        val active = repository.getActiveOperator()
        assertNotNull(active)
        assertEquals(operator.userId, active!!.userId)
    }

    @Test
    fun testOnlyOneOperatorCanBeActiveAtATime() = runBlocking {
        // Register operator 1
        val op1Result = repository.registerOperator("Op One", "01711111111", "Branch A", "pass1")
        assertTrue(op1Result.isSuccess)
        val op1 = op1Result.getOrNull()!!

        // Register operator 2
        val op2Result = repository.registerOperator("Op Two", "01722222222", "Branch B", "pass2")
        assertTrue(op2Result.isSuccess)
        val op2 = op2Result.getOrNull()!!

        // Only Operator 2 should be active after registering operator 2
        val activeAfterReg2 = repository.getActiveOperator()
        assertNotNull(activeAfterReg2)
        assertEquals("Only the latest registered operator should be active", op2.userId, activeAfterReg2!!.userId)

        // Login Operator 1
        val login1Result = repository.login("01711111111", "pass1", rememberMe = true)
        assertTrue(login1Result.isSuccess)

        val activeAfterLogin1 = repository.getActiveOperator()
        assertEquals("Operator 1 must now be the sole active operator", op1.userId, activeAfterLogin1!!.userId)

        // Verify Operator 2 is inactive in DB
        val op2InDb = db.operatorDao().getOperatorById(op2.userId)
        assertNotNull(op2InDb)
        assertFalse("Operator 2 must be deactivated", op2InDb!!.isActive)
    }

    @Test
    fun testResetPasswordUpdatesCredentialsOffline() = runBlocking {
        val phone = "01799887766"
        val originalPass = "oldPass123"
        val newPass = "newPass456"

        val regResult = repository.registerOperator("Test User", phone, "Naogaon", originalPass)
        assertTrue(regResult.isSuccess)

        // Reset password
        val resetResult = repository.resetPassword(phone, newPass)
        assertTrue("Password reset should succeed offline", resetResult.isSuccess)

        // Old password must fail
        val oldLoginResult = repository.login(phone, originalPass, false)
        assertFalse("Login with old password must fail", oldLoginResult.isSuccess)

        // New password must succeed
        val newLoginResult = repository.login(phone, newPass, false)
        assertTrue("Login with new password must succeed", newLoginResult.isSuccess)
    }

    @Test
    fun testSessionRestorationWithRememberMe() = runBlocking {
        val phone = "01755554444"
        val pass = "myPin123"

        repository.registerOperator("Remember User", phone, "Bogra", pass)

        // Test logout
        repository.logout()
        assertNull("After logout, active operator must be null", repository.getActiveOperator())

        // Login with rememberMe = true
        val loginResult = repository.login(phone, pass, rememberMe = true)
        assertTrue(loginResult.isSuccess)

        // Check session restoration simulates app restart
        val restored = repository.checkAndRestoreSession()
        assertNotNull("Session must be restored when remember-me is enabled", restored)
        assertEquals(phone, restored!!.operatorPhone)
        assertEquals(phone, repository.getRememberedPhone())
        assertTrue(repository.isRememberMeSaved())
    }

    @Test
    fun testActiveOperatorRecordedOnTransaction() = runBlocking {
        val phone = "01733332222"
        val opName = "Station Master Kabir"
        val pass = "adminPin"

        val regResult = repository.registerOperator(opName, phone, "Dhaka", pass)
        val operator = regResult.getOrNull()!!

        // Save order and verify invoice has this operator's ID and name
        val orderId = "INV-99001"
        val now = System.currentTimeMillis()
        val invoice = com.example.pos.data.db.InvoiceEntity(
            orderId = orderId,
            globalTransactionId = com.example.pos.core.util.IdGenerator.generateGlobalTransactionId(),
            branchCode = "01",
            dateTime = now,
            operatorId = operator.userId,
            operatorName = operator.operatorName,
            customerInfo = "01700000000",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L,
            footerNote = "Test Note"
        )
        repository.saveOrder(invoice, emptyList())

        val savedInvoice = db.invoiceDao().getInvoiceById(orderId)
        assertNotNull(savedInvoice)
        assertEquals("Invoice must record correct operator ID", operator.userId, savedInvoice!!.operatorId)
        assertEquals("Invoice must record correct operator name", opName, savedInvoice.operatorName)
    }
}
