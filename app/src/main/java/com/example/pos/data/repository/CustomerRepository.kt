package com.example.pos.data.repository

import com.example.pos.core.error.PosResult
import com.example.pos.data.db.CustomerDao
import com.example.pos.data.db.CustomerEntity
import kotlinx.coroutines.flow.Flow

interface CustomerRepository {
    val allCustomers: Flow<List<CustomerEntity>>
    fun searchCustomersFlow(query: String, limit: Int = 20): Flow<List<CustomerEntity>>
    suspend fun searchCustomers(query: String, limit: Int = 20): List<CustomerEntity>
    suspend fun getCustomerByInfo(customerInfo: String): CustomerEntity?
    suspend fun saveCustomer(customer: CustomerEntity): PosResult<Unit>
    suspend fun recordCustomerOrder(customerInfo: String, totalPaisa: Long): PosResult<Unit>
    suspend fun deleteCustomer(customerInfo: String): PosResult<Unit>
}

class CustomerRepositoryImpl(private val customerDao: CustomerDao) : CustomerRepository {

    override val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomersFlow()

    override fun searchCustomersFlow(query: String, limit: Int): Flow<List<CustomerEntity>> {
        return customerDao.searchCustomersFlow(query.trim(), limit)
    }

    override suspend fun searchCustomers(query: String, limit: Int): List<CustomerEntity> {
        return customerDao.searchCustomers(query.trim(), limit)
    }

    override suspend fun getCustomerByInfo(customerInfo: String): CustomerEntity? {
        return customerDao.getCustomerByInfo(customerInfo.trim())
    }

    override suspend fun saveCustomer(customer: CustomerEntity): PosResult<Unit> {
        return try {
            val existing = customerDao.getCustomerByInfo(customer.customerInfo.trim())
            val now = System.currentTimeMillis()
            val toSave = customer.copy(
                customerInfo = customer.customerInfo.trim(),
                createdAt = existing?.createdAt ?: customer.createdAt,
                updatedAt = now
            )
            customerDao.insertOrUpdateCustomer(toSave)
            PosResult.Success(Unit)
        } catch (e: Exception) {
            PosResult.Error(com.example.pos.core.error.PosError.DatabaseError(e.message ?: "Failed to save customer", e))
        }
    }

    override suspend fun recordCustomerOrder(customerInfo: String, totalPaisa: Long): PosResult<Unit> {
        return try {
            val trimmed = customerInfo.trim()
            if (trimmed.isNotBlank()) {
                val existing = customerDao.getCustomerByInfo(trimmed)
                val now = System.currentTimeMillis()
                if (existing != null) {
                    customerDao.recordCustomerOrder(trimmed, totalPaisa, now)
                } else {
                    customerDao.insertOrUpdateCustomer(
                        CustomerEntity(
                            customerInfo = trimmed,
                            phoneNumber = if (trimmed.all { it.isDigit() || it in "+- " }) trimmed else "",
                            totalOrders = 1,
                            totalSpentPaisa = totalPaisa,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }
            PosResult.Success(Unit)
        } catch (e: Exception) {
            PosResult.Error(com.example.pos.core.error.PosError.DatabaseError(e.message ?: "Failed to record customer order", e))
        }
    }

    override suspend fun deleteCustomer(customerInfo: String): PosResult<Unit> {
        return try {
            customerDao.deleteCustomer(customerInfo.trim())
            PosResult.Success(Unit)
        } catch (e: Exception) {
            PosResult.Error(com.example.pos.core.error.PosError.DatabaseError(e.message ?: "Failed to delete customer", e))
        }
    }
}
