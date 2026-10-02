package com.example.pos.data.repository

import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.data.db.OperatorDao
import com.example.pos.data.db.OperatorEntity
import com.example.pos.data.db.PosDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface AuthRepository {
    val activeOperator: Flow<OperatorEntity?>
    suspend fun getActiveOperator(): OperatorEntity?
    suspend fun login(phone: String, passwordRaw: String, rememberMe: Boolean): PosResult<OperatorEntity>
    suspend fun register(name: String, phone: String, address: String, passwordRaw: String): PosResult<OperatorEntity>
    suspend fun resetPassword(phone: String, newPasswordRaw: String): PosResult<Unit>
    suspend fun updateProfile(name: String, phone: String, address: String): PosResult<Unit>
    suspend fun logout()
}

class AuthRepositoryImpl(private val operatorDao: OperatorDao) : AuthRepository {

    override val activeOperator: Flow<OperatorEntity?> = operatorDao.getActiveOperatorFlow()

    override suspend fun getActiveOperator(): OperatorEntity? = operatorDao.getActiveOperator()

    override suspend fun login(phone: String, passwordRaw: String, rememberMe: Boolean): PosResult<OperatorEntity> {
        val cleanPhone = phone.trim()
        val op = operatorDao.getOperatorByPhone(cleanPhone)
            ?: return PosResult.Error(PosError.InvalidCredentials())

        val isValid = com.example.pos.core.security.PasswordSecurity.verifyPassword(passwordRaw, op.passwordHash)
        if (!isValid) {
            return PosResult.Error(PosError.InvalidCredentials())
        }

        operatorDao.clearAllActiveOperators()
        operatorDao.setActiveOperator(op.userId, rememberMe)
        return PosResult.Success(op.copy(isActive = true, rememberMe = rememberMe))
    }

    override suspend fun register(name: String, phone: String, address: String, passwordRaw: String): PosResult<OperatorEntity> {
        val cleanPhone = phone.trim()
        val existing = operatorDao.getOperatorByPhone(cleanPhone)
        if (existing != null) {
            return PosResult.Error(PosError.UserAlreadyExists())
        }

        val secureHash = com.example.pos.core.security.PasswordSecurity.hashPassword(passwordRaw)
        val newOp = OperatorEntity(
            userId = "OP-" + UUID.randomUUID().toString().take(8).uppercase(),
            operatorName = name.trim(),
            operatorPhone = cleanPhone,
            operatorAddress = address.trim(),
            passwordHash = secureHash,
            isActive = true,
            rememberMe = true
        )
        operatorDao.clearAllActiveOperators()
        operatorDao.insertOperator(newOp)
        return PosResult.Success(newOp)
    }

    override suspend fun resetPassword(phone: String, newPasswordRaw: String): PosResult<Unit> {
        val cleanPhone = phone.trim()
        val existing = operatorDao.getOperatorByPhone(cleanPhone)
            ?: return PosResult.Error(PosError.RecordNotFound("Operator", cleanPhone))

        val secureHash = com.example.pos.core.security.PasswordSecurity.hashPassword(newPasswordRaw)
        val updated = existing.copy(
            passwordHash = secureHash,
            updatedAt = System.currentTimeMillis()
        )
        operatorDao.updateOperator(updated)
        return PosResult.Success(Unit)
    }

    override suspend fun updateProfile(name: String, phone: String, address: String): PosResult<Unit> {
        val active = operatorDao.getActiveOperator()
            ?: return PosResult.Error(PosError.Unauthenticated())

        val updated = active.copy(
            operatorName = name.trim(),
            operatorPhone = phone.trim(),
            operatorAddress = address.trim(),
            updatedAt = System.currentTimeMillis()
        )
        operatorDao.updateOperator(updated)
        return PosResult.Success(Unit)
    }

    override suspend fun logout() {
        operatorDao.clearAllActiveOperators()
    }
}
