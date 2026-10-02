package com.example.pos.modules.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.OperatorEntity
import com.example.pos.ui.components.TopStationHeader

/**
 * Modular foundation for Shared UI Components (Module 16).
 * Exposes reusable, accessibility-compliant Material 3 components.
 */
object SharedUiFeature {

    @Composable
    fun Header(
        company: CompanyHeaderEntity?,
        operator: OperatorEntity?,
        currentLanguage: String,
        currentTheme: String,
        onToggleLanguage: () -> Unit,
        onToggleTheme: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        TopStationHeader(
            company = company,
            operator = operator,
            currentLanguage = currentLanguage,
            currentTheme = currentTheme,
            onToggleLanguage = onToggleLanguage,
            onToggleTheme = onToggleTheme,
            modifier = modifier
        )
    }
}
