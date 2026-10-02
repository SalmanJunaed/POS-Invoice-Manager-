package com.example.pos.modules.user

import com.example.pos.core.error.PosResult
import com.example.pos.data.db.OperatorEntity
import com.example.pos.data.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

/**
 * Modular foundation for User Profile (Module 3).
 */
interface UserProfileFacade {
    val activeOperatorFlow: Flow<OperatorEntity?>
    suspend fun getActiveOperator(): OperatorEntity?
    suspend fun updateProfile(name: String, phone: String, address: String): PosResult<Unit>
}

class UserProfileFacadeImpl(private val authRepository: AuthRepository) : UserProfileFacade {
    override val activeOperatorFlow = authRepository.activeOperator
    override suspend fun getActiveOperator() = authRepository.getActiveOperator()
    override suspend fun updateProfile(name: String, phone: String, address: String) =
        authRepository.updateProfile(name, phone, address)
}
