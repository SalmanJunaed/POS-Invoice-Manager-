package com.example.pos.core.state

import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.OperatorEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared in-memory state coordination across POS screens.
 * Holds active operator session, company profile, station headers, and UI preferences.
 */
class AppSharedState {

    private val _activeOperator = MutableStateFlow<OperatorEntity?>(null)
    val activeOperator: StateFlow<OperatorEntity?> = _activeOperator.asStateFlow()

    private val _companyHeader = MutableStateFlow<CompanyHeaderEntity?>(null)
    val companyHeader: StateFlow<CompanyHeaderEntity?> = _companyHeader.asStateFlow()

    private val _currentLanguage = MutableStateFlow("bn")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private val _currentTheme = MutableStateFlow("LIGHT")
    val currentTheme: StateFlow<String> = _currentTheme.asStateFlow()

    fun updateOperator(operator: OperatorEntity?) {
        _activeOperator.value = operator
    }

    fun updateCompany(company: CompanyHeaderEntity?) {
        _companyHeader.value = company
    }

    fun setLanguage(lang: String) {
        _currentLanguage.value = lang
    }

    fun setTheme(theme: String) {
        _currentTheme.value = theme
    }

    companion object {
        val instance by lazy { AppSharedState() }
    }
}
