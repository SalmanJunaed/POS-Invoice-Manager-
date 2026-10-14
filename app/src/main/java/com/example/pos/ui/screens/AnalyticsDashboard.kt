package com.example.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pos.core.util.DateTimeUtils
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.data.Localization
import com.example.pos.data.db.ItemSalesSummary
import com.example.pos.data.db.SalesPeriodAggregate
import com.example.pos.domain.model.ChartDataPoint
import com.example.pos.modules.chart.PosAnalyticsBarChart
import com.example.pos.ui.viewmodel.AnalyticsGrouping
import com.example.pos.ui.viewmodel.AnalyticsMetric
import com.example.pos.ui.viewmodel.DateFilterMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsDashboardView(
    language: String,
    dateFilter: DateFilterMode,
    analyticsGrouping: AnalyticsGrouping,
    analyticsMetric: AnalyticsMetric,
    analyticsDataPoints: List<SalesPeriodAggregate>,
    chartDataPoints: List<ChartDataPoint>,
    aggregatedOrders: Int,
    aggregatedRawPaisa: Long,
    aggregatedRoundedBdt: Long,
    topSellingItems: List<ItemSalesSummary>,
    onSelectDateFilter: (DateFilterMode) -> Unit,
    onOpenCustomDateDialog: () -> Unit,
    onSelectGrouping: (AnalyticsGrouping) -> Unit,
    onSelectMetric: (AnalyticsMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("analytics_dashboard_view")
    ) {
        // Filter Controls Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Date Range Selection Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (language == "bn") "তারিখ অনুযায়ী ফিল্টার" else "Date Range Filter",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = dateFilter == DateFilterMode.ALL,
                        onClick = { onSelectDateFilter(DateFilterMode.ALL) },
                        label = {
                            Text(
                                text = Localization.get(Localization.Key.DATE_FILTER_ALL, language),
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        modifier = Modifier.testTag("analytics_filter_all")
                    )
                    FilterChip(
                        selected = dateFilter == DateFilterMode.TODAY,
                        onClick = { onSelectDateFilter(DateFilterMode.TODAY) },
                        label = {
                            Text(
                                text = Localization.get(Localization.Key.DATE_FILTER_TODAY, language),
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        modifier = Modifier.testTag("analytics_filter_today")
                    )
                    FilterChip(
                        selected = dateFilter == DateFilterMode.YESTERDAY,
                        onClick = { onSelectDateFilter(DateFilterMode.YESTERDAY) },
                        label = {
                            Text(
                                text = Localization.get(Localization.Key.DATE_FILTER_YESTERDAY, language),
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                    FilterChip(
                        selected = dateFilter == DateFilterMode.WEEK,
                        onClick = { onSelectDateFilter(DateFilterMode.WEEK) },
                        label = {
                            Text(
                                text = Localization.get(Localization.Key.DATE_FILTER_WEEK, language),
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                    FilterChip(
                        selected = dateFilter == DateFilterMode.MONTH,
                        onClick = { onSelectDateFilter(DateFilterMode.MONTH) },
                        label = {
                            Text(
                                text = Localization.get(Localization.Key.DATE_FILTER_MONTH, language),
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        modifier = Modifier.testTag("analytics_filter_month")
                    )
                    FilterChip(
                        selected = dateFilter == DateFilterMode.YEAR,
                        onClick = { onSelectDateFilter(DateFilterMode.YEAR) },
                        label = {
                            Text(
                                text = Localization.get(Localization.Key.DATE_FILTER_YEAR, language),
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        modifier = Modifier.testTag("analytics_filter_year")
                    )
                    FilterChip(
                        selected = dateFilter == DateFilterMode.CUSTOM,
                        onClick = onOpenCustomDateDialog,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = {
                            Text(
                                text = Localization.get(Localization.Key.DATE_FILTER_CUSTOM, language),
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Responsive Period Grouping & Chart Metric Selectors
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isWide = maxWidth >= 600.dp
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                PeriodGroupingSection(
                                    language = language,
                                    analyticsGrouping = analyticsGrouping,
                                    onSelectGrouping = onSelectGrouping
                                )
                            }
                            Column(modifier = Modifier.weight(1.2f)) {
                                ChartMetricSection(
                                    language = language,
                                    analyticsMetric = analyticsMetric,
                                    onSelectMetric = onSelectMetric
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            PeriodGroupingSection(
                                language = language,
                                analyticsGrouping = analyticsGrouping,
                                onSelectGrouping = onSelectGrouping
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            ChartMetricSection(
                                language = language,
                                analyticsMetric = analyticsMetric,
                                onSelectMetric = onSelectMetric
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live Database Aggregate KPI Summary Cards
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isNarrowScreen = maxWidth < 380.dp
            val avgTicket = if (aggregatedOrders > 0) aggregatedRoundedBdt / aggregatedOrders else 0L
            val roundingDiffPaisa = (aggregatedRoundedBdt * 100L) - aggregatedRawPaisa

            if (isNarrowScreen) {
                // 2 columns for narrow screens so cards and numbers don't get squished
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiSummaryCard(
                            title = Localization.get(Localization.Key.KPI_TOTAL_ORDERS, language),
                            value = "$aggregatedOrders",
                            modifier = Modifier.weight(1f),
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                        KpiSummaryCard(
                            title = Localization.get(Localization.Key.AVG_ORDER_VALUE, language),
                            value = "${FinancialCalculationEngine.formatWholeBdtToDisplay(avgTicket)} ৳",
                            modifier = Modifier.weight(1f),
                            accentColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiSummaryCard(
                            title = Localization.get(Localization.Key.KPI_TOTAL_SALES, language),
                            value = "${FinancialCalculationEngine.formatPaisaToBdt(aggregatedRawPaisa)} ৳",
                            modifier = Modifier.weight(1f),
                            accentColor = MaterialTheme.colorScheme.secondary
                        )
                        KpiSummaryCard(
                            title = Localization.get(Localization.Key.KPI_TOTAL_ROUNDED, language),
                            value = "${FinancialCalculationEngine.formatWholeBdtToDisplay(aggregatedRoundedBdt)} ৳",
                            modifier = Modifier.weight(1f),
                            accentColor = Color(0xFF2E7D32)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiSummaryCard(
                            title = Localization.get(Localization.Key.KPI_ROUNDING_DIFF, language),
                            value = "${if (roundingDiffPaisa >= 0) "+" else ""}${FinancialCalculationEngine.formatPaisaToBdt(roundingDiffPaisa)} ৳",
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = if (roundingDiffPaisa >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
            } else {
                // Standard responsive 3 + 2 layout
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiSummaryCard(
                            title = Localization.get(Localization.Key.KPI_TOTAL_ORDERS, language),
                            value = "$aggregatedOrders",
                            modifier = Modifier.weight(1f),
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                        KpiSummaryCard(
                            title = Localization.get(Localization.Key.KPI_TOTAL_SALES, language),
                            value = "${FinancialCalculationEngine.formatPaisaToBdt(aggregatedRawPaisa)} ৳",
                            modifier = Modifier.weight(1.2f),
                            accentColor = MaterialTheme.colorScheme.secondary
                        )
                        KpiSummaryCard(
                            title = Localization.get(Localization.Key.KPI_TOTAL_ROUNDED, language),
                            value = "${FinancialCalculationEngine.formatWholeBdtToDisplay(aggregatedRoundedBdt)} ৳",
                            modifier = Modifier.weight(1.2f),
                            accentColor = Color(0xFF2E7D32)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiSummaryCard(
                            title = Localization.get(Localization.Key.AVG_ORDER_VALUE, language),
                            value = "${FinancialCalculationEngine.formatWholeBdtToDisplay(avgTicket)} ৳",
                            modifier = Modifier.weight(1f),
                            accentColor = MaterialTheme.colorScheme.tertiary
                        )
                        KpiSummaryCard(
                            title = Localization.get(Localization.Key.KPI_ROUNDING_DIFF, language),
                            value = "${if (roundingDiffPaisa >= 0) "+" else ""}${FinancialCalculationEngine.formatPaisaToBdt(roundingDiffPaisa)} ৳",
                            modifier = Modifier.weight(1f),
                            accentColor = if (roundingDiffPaisa >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Visual Canvas Bar Chart
        val metricTitle = when (analyticsMetric) {
            AnalyticsMetric.ROUNDED_SALES -> Localization.get(Localization.Key.METRIC_ROUNDED_SALES, language)
            AnalyticsMetric.RAW_SALES -> Localization.get(Localization.Key.METRIC_RAW_SALES, language)
            AnalyticsMetric.TRANSACTION_COUNT -> Localization.get(Localization.Key.METRIC_TRANSACTIONS, language)
        }

        PosAnalyticsBarChart(
            dataPoints = chartDataPoints,
            metricTitle = metricTitle,
            isCurrency = analyticsMetric != AnalyticsMetric.TRANSACTION_COUNT,
            primaryColor = when (analyticsMetric) {
                AnalyticsMetric.ROUNDED_SALES -> Color(0xFF2E7D32)
                AnalyticsMetric.RAW_SALES -> MaterialTheme.colorScheme.primary
                AnalyticsMetric.TRANSACTION_COUNT -> MaterialTheme.colorScheme.tertiary
            },
            emptyMessage = Localization.get(Localization.Key.NO_ANALYTICS_DATA, language)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Top Selling Products Breakdown
        if (topSellingItems.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("top_selling_items_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Localization.get(Localization.Key.TOP_SELLING_ITEMS, language),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    topSellingItems.forEachIndexed { index, summary ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. ${summary.itemName}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${summary.orderCount} ${summary.unit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Text(
                                text = "${FinancialCalculationEngine.formatPaisaToBdt(summary.totalRevenuePaisa)} ৳",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                        if (index < topSellingItems.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Detailed Period Breakdown Table
        if (analyticsDataPoints.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("period_breakdown_table"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Localization.get(Localization.Key.ANALYTICS_BREAKDOWN_TITLE, language),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Table Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Localization.get(Localization.Key.COLUMN_PERIOD, language),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            modifier = Modifier.weight(1.2f)
                        )
                        Text(
                            text = Localization.get(Localization.Key.COLUMN_ORDERS, language),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.weight(0.8f)
                        )
                        Text(
                            text = Localization.get(Localization.Key.COLUMN_RAW_TOTAL, language),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier.weight(1.2f)
                        )
                        Text(
                            text = Localization.get(Localization.Key.COLUMN_ROUNDED_TOTAL, language),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Table Data Rows
                    analyticsDataPoints.forEachIndexed { idx, point ->
                        val formattedLabel = DateTimeUtils.formatPeriodKeyForDisplay(point.periodKey, language)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formattedLabel,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1.2f)
                            )
                            Text(
                                text = "${point.transactionCount}",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(0.8f)
                            )
                            Text(
                                text = "${FinancialCalculationEngine.formatPaisaToBdt(point.totalRawPaisa)} ৳",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1.2f)
                            )
                            Text(
                                text = "${FinancialCalculationEngine.formatWholeBdtToDisplay(point.totalRoundedBdt)} ৳",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF2E7D32),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1.2f)
                            )
                        }

                        if (idx < analyticsDataPoints.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodGroupingSection(
    language: String,
    analyticsGrouping: AnalyticsGrouping,
    onSelectGrouping: (AnalyticsGrouping) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Equalizer,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (language == "bn") "গ্রুপিং মোড" else "Period Grouping",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = analyticsGrouping == AnalyticsGrouping.DAY,
                onClick = { onSelectGrouping(AnalyticsGrouping.DAY) },
                label = {
                    Text(
                        text = if (language == "bn") "দিন" else "Day",
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                modifier = Modifier.testTag("grouping_day")
            )
            FilterChip(
                selected = analyticsGrouping == AnalyticsGrouping.MONTH,
                onClick = { onSelectGrouping(AnalyticsGrouping.MONTH) },
                label = {
                    Text(
                        text = if (language == "bn") "মাস" else "Month",
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                modifier = Modifier.testTag("grouping_month")
            )
            FilterChip(
                selected = analyticsGrouping == AnalyticsGrouping.YEAR,
                onClick = { onSelectGrouping(AnalyticsGrouping.YEAR) },
                label = {
                    Text(
                        text = if (language == "bn") "বছর" else "Year",
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                modifier = Modifier.testTag("grouping_year")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartMetricSection(
    language: String,
    analyticsMetric: AnalyticsMetric,
    onSelectMetric: (AnalyticsMetric) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PieChart,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (language == "bn") "গ্রাফের মেট্রিক" else "Chart Metric",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.secondary
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = analyticsMetric == AnalyticsMetric.ROUNDED_SALES,
                onClick = { onSelectMetric(AnalyticsMetric.ROUNDED_SALES) },
                label = {
                    Text(
                        text = if (language == "bn") "রাউন্ডেড (৳)" else "Rounded (৳)",
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                modifier = Modifier.testTag("metric_rounded")
            )
            FilterChip(
                selected = analyticsMetric == AnalyticsMetric.RAW_SALES,
                onClick = { onSelectMetric(AnalyticsMetric.RAW_SALES) },
                label = {
                    Text(
                        text = if (language == "bn") "আসল (৳)" else "Raw (৳)",
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                modifier = Modifier.testTag("metric_raw")
            )
            FilterChip(
                selected = analyticsMetric == AnalyticsMetric.TRANSACTION_COUNT,
                onClick = { onSelectMetric(AnalyticsMetric.TRANSACTION_COUNT) },
                label = {
                    Text(
                        text = if (language == "bn") "অর্ডার সংখ্যা" else "Orders",
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                modifier = Modifier.testTag("metric_count")
            )
        }
    }
}

