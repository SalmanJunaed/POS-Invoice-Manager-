package com.example.pos.modules.report

import com.example.pos.core.util.DateTimeUtils
import com.example.pos.domain.model.SalesKPIs

/**
 * Reports module foundation.
 * Aggregates date-range reporting and performance summaries.
 */
object ReportEngine {

    enum class ReportPeriod {
        TODAY,
        YESTERDAY,
        LAST_7_DAYS,
        THIS_MONTH,
        CUSTOM
    }

    fun computeDateRange(period: ReportPeriod, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        return when (period) {
            ReportPeriod.TODAY -> Pair(DateTimeUtils.getStartOfDay(now), now)
            ReportPeriod.YESTERDAY -> {
                val startToday = DateTimeUtils.getStartOfDay(now)
                val startYesterday = startToday - 24L * 60L * 60L * 1000L
                Pair(startYesterday, startToday - 1)
            }
            ReportPeriod.LAST_7_DAYS -> Pair(DateTimeUtils.getStartOfWeek(now), now)
            ReportPeriod.THIS_MONTH -> Pair(DateTimeUtils.getStartOfMonth(now), now)
            ReportPeriod.CUSTOM -> Pair(0L, now)
        }
    }

    fun calculateAverageOrderValuePaisa(kpis: SalesKPIs): Long {
        if (kpis.totalOrders <= 0) return 0L
        return kpis.totalSalesPaisa / kpis.totalOrders
    }
}
