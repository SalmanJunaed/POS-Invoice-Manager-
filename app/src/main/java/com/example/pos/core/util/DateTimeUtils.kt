package com.example.pos.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Robust date and time utilities for receipt timestamps, reporting periods, and live clock.
 */
object DateTimeUtils {

    private val receiptDateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US)
    private val historyDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val timeOnlyFormat = SimpleDateFormat("hh:mm:ss a", Locale.US)
    private val dateOnlyFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    fun formatReceiptDateTime(timestamp: Long): String {
        return receiptDateFormat.format(Date(timestamp))
    }

    fun formatHistoryDateTime(timestamp: Long): String {
        return historyDateFormat.format(Date(timestamp))
    }

    fun formatLiveTime(timestamp: Long = System.currentTimeMillis()): String {
        return timeOnlyFormat.format(Date(timestamp))
    }

    fun formatDateOnly(timestamp: Long = System.currentTimeMillis()): String {
        return dateOnlyFormat.format(Date(timestamp))
    }

    fun getStartOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getStartOfWeek(timestamp: Long = System.currentTimeMillis()): Long {
        return getStartOfDay(timestamp) - (7L * 24L * 60L * 60L * 1000L)
    }

    fun getStartOfMonth(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getStartOfYear(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(java.util.Calendar.DAY_OF_YEAR, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun formatPeriodKeyForDisplay(periodKey: String, language: String = "en"): String {
        return try {
            when {
                // Day format: YYYY-MM-DD
                periodKey.matches(Regex("""\d{4}-\d{2}-\d{2}""")) -> {
                    val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val date = sdfIn.parse(periodKey) ?: return periodKey
                    val sdfOut = SimpleDateFormat("dd MMM", if (language == "bn") Locale("bn") else Locale.US)
                    sdfOut.format(date)
                }
                // Month format: YYYY-MM
                periodKey.matches(Regex("""\d{4}-\d{2}""")) -> {
                    val sdfIn = SimpleDateFormat("yyyy-MM", Locale.US)
                    val date = sdfIn.parse(periodKey) ?: return periodKey
                    val sdfOut = SimpleDateFormat("MMM yyyy", if (language == "bn") Locale("bn") else Locale.US)
                    sdfOut.format(date)
                }
                // Year format: YYYY
                periodKey.matches(Regex("""\d{4}""")) -> periodKey
                else -> periodKey
            }
        } catch (_: Exception) {
            periodKey
        }
    }

    fun parseDateDdMmYyyy(input: String): Date? {
        val trimmed = input.trim()
        val parts = trimmed.split("-")
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val year = parts[2].toIntOrNull() ?: return null
        if (day !in 1..31 || month !in 1..12 || year !in 1970..2100) return null
        return try {
            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US).apply { isLenient = false }
            sdf.parse(trimmed)
        } catch (_: Exception) {
            null
        }
    }
}
