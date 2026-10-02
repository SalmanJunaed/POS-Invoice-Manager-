package com.example.pos.modules.localization

import com.example.pos.data.Localization

/**
 * Modular foundation for Localization (Module 14).
 * Provides access to localized strings for English ("en") and Bengali ("bn").
 */
object LocalizationFeature {
    fun get(key: Localization.Key, lang: String): String {
        return Localization.get(key, lang)
    }

    fun getByName(keyName: String, lang: String): String {
        return try {
            val enumKey = Localization.Key.valueOf(keyName)
            Localization.get(enumKey, lang)
        } catch (e: Exception) {
            keyName
        }
    }

    fun isBangla(lang: String): Boolean = lang.equals("bn", ignoreCase = true)
}
