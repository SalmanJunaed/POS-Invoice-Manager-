package com.example.pos.modules.company

import com.example.pos.core.error.PosResult
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.repository.CompanyRepository
import kotlinx.coroutines.flow.Flow

/**
 * Modular foundation for Company Profile (Module 2).
 */
interface CompanyProfileFacade {
    val companyFlow: Flow<CompanyHeaderEntity?>
    suspend fun getCompany(): CompanyHeaderEntity?
    suspend fun updateCompany(
        name: String,
        branchName: String,
        branchNumber: String,
        phone: String,
        address: String
    ): PosResult<Unit>
}

class CompanyProfileFacadeImpl(private val companyRepository: CompanyRepository) : CompanyProfileFacade {
    override val companyFlow = companyRepository.companyHeader
    override suspend fun getCompany() = companyRepository.getCompanyHeader()
    override suspend fun updateCompany(
        name: String,
        branchName: String,
        branchNumber: String,
        phone: String,
        address: String
    ) = companyRepository.updateCompanyHeader(name, branchName, branchNumber, phone, address)
}
