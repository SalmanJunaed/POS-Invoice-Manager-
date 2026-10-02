package com.example.pos.data.repository

import com.example.pos.core.error.PosResult
import com.example.pos.data.db.CompanyHeaderDao
import com.example.pos.data.db.CompanyHeaderEntity
import kotlinx.coroutines.flow.Flow

interface CompanyRepository {
    val companyHeader: Flow<CompanyHeaderEntity?>
    suspend fun getCompanyHeader(): CompanyHeaderEntity?
    suspend fun updateCompanyHeader(
        companyName: String,
        branchName: String,
        branchNumber: String,
        phoneNumber: String,
        address: String
    ): PosResult<Unit>
}

class CompanyRepositoryImpl(private val companyHeaderDao: CompanyHeaderDao) : CompanyRepository {

    override val companyHeader: Flow<CompanyHeaderEntity?> = companyHeaderDao.getCompanyHeaderFlow()

    override suspend fun getCompanyHeader(): CompanyHeaderEntity? = companyHeaderDao.getCompanyHeader()

    override suspend fun updateCompanyHeader(
        companyName: String,
        branchName: String,
        branchNumber: String,
        phoneNumber: String,
        address: String
    ): PosResult<Unit> {
        val current = companyHeaderDao.getCompanyHeader()
        val updated = current?.copy(
            companyName = companyName.trim(),
            branchName = branchName.trim(),
            branchNumber = branchNumber.trim(),
            phoneNumber = phoneNumber.trim(),
            address = address.trim(),
            updatedAt = System.currentTimeMillis()
        ) ?: CompanyHeaderEntity(
            companyName = companyName.trim(),
            branchName = branchName.trim(),
            branchNumber = branchNumber.trim(),
            phoneNumber = phoneNumber.trim(),
            address = address.trim()
        )
        companyHeaderDao.insertOrUpdate(updated)
        return PosResult.Success(Unit)
    }
}
