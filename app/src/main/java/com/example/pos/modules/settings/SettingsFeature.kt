package com.example.pos.modules.settings

import com.example.pos.core.error.PosResult
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * Modular foundation for Settings (Module 13).
 */
interface SettingsFeatureFacade {
    val settingsFlow: Flow<AppSettingsEntity?>
    suspend fun setLanguage(lang: String): PosResult<Unit>
    suspend fun setThemeMode(mode: String): PosResult<Unit>
    suspend fun setPaperSize(size: String): PosResult<Unit>
    suspend fun setAutoCutter(enabled: Boolean): PosResult<Unit>
    suspend fun setBluetoothPrinter(address: String?, name: String?): PosResult<Unit>
    suspend fun setFooterNotes(line1: String, line2: String): PosResult<Unit>
}

class SettingsFeatureFacadeImpl(private val settingsRepository: SettingsRepository) : SettingsFeatureFacade {
    override val settingsFlow = settingsRepository.settingsFlow
    override suspend fun setLanguage(lang: String) = settingsRepository.updateLanguage(lang)
    override suspend fun setThemeMode(mode: String) = settingsRepository.updateThemeMode(mode)
    override suspend fun setPaperSize(size: String) = settingsRepository.updatePaperSize(size)
    override suspend fun setAutoCutter(enabled: Boolean) = settingsRepository.updateAutoCutter(enabled)
    override suspend fun setBluetoothPrinter(address: String?, name: String?) =
        settingsRepository.updateBluetoothPrinter(address, name)
    override suspend fun setFooterNotes(line1: String, line2: String) =
        settingsRepository.updateFooterNotes(line1, line2)
}
