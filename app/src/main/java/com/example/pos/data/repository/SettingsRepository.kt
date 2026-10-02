package com.example.pos.data.repository

import com.example.pos.core.error.PosResult
import com.example.pos.data.db.AppSettingsDao
import com.example.pos.data.db.AppSettingsEntity
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settingsFlow: Flow<AppSettingsEntity?>
    suspend fun getSettings(): AppSettingsEntity?
    suspend fun updateLanguage(lang: String): PosResult<Unit>
    suspend fun updateThemeMode(theme: String): PosResult<Unit>
    suspend fun updatePaperSize(paperSize: String): PosResult<Unit>
    suspend fun updateAutoCutter(enabled: Boolean): PosResult<Unit>
    suspend fun updateBluetoothPrinter(address: String?, name: String?): PosResult<Unit>
    suspend fun updateFooterNotes(l1: String, l2: String): PosResult<Unit>
}

class SettingsRepositoryImpl(private val appSettingsDao: AppSettingsDao) : SettingsRepository {

    override val settingsFlow: Flow<AppSettingsEntity?> = appSettingsDao.getSettingsFlow()

    override suspend fun getSettings(): AppSettingsEntity? = appSettingsDao.getSettings()

    override suspend fun updateLanguage(lang: String): PosResult<Unit> {
        appSettingsDao.updateLanguage(lang)
        return PosResult.Success(Unit)
    }

    override suspend fun updateThemeMode(theme: String): PosResult<Unit> {
        appSettingsDao.updateThemeMode(theme)
        return PosResult.Success(Unit)
    }

    override suspend fun updatePaperSize(paperSize: String): PosResult<Unit> {
        appSettingsDao.updatePaperSize(paperSize)
        return PosResult.Success(Unit)
    }

    override suspend fun updateAutoCutter(enabled: Boolean): PosResult<Unit> {
        appSettingsDao.updateAutoCutter(enabled)
        return PosResult.Success(Unit)
    }

    override suspend fun updateBluetoothPrinter(address: String?, name: String?): PosResult<Unit> {
        appSettingsDao.updateBluetoothPrinter(address, name)
        return PosResult.Success(Unit)
    }

    override suspend fun updateFooterNotes(l1: String, l2: String): PosResult<Unit> {
        appSettingsDao.updateFooterNotes(l1, l2)
        return PosResult.Success(Unit)
    }
}
