package com.example.pos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.pos.data.db.ItemSalesSummary
import com.example.pos.data.db.SalesPeriodAggregate
import com.example.pos.domain.model.ChartDataPoint
import com.example.pos.ui.screens.AnalyticsDashboardView
import com.example.pos.ui.viewmodel.AnalyticsGrouping
import com.example.pos.ui.viewmodel.AnalyticsMetric
import com.example.pos.ui.viewmodel.DateFilterMode
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class AnalyticsDashboardLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testAnalyticsDashboardResponsiveControlsAndChartRender() {
        var selectedDateFilter = DateFilterMode.ALL
        var selectedGrouping = AnalyticsGrouping.DAY
        var selectedMetric = AnalyticsMetric.ROUNDED_SALES

        val sampleAggregates = listOf(
            SalesPeriodAggregate(
                periodKey = "2024-01-15",
                transactionCount = 5,
                totalRawPaisa = 150000L,
                totalRoundedBdt = 1500L
            ),
            SalesPeriodAggregate(
                periodKey = "2024-01-16",
                transactionCount = 8,
                totalRawPaisa = 240000L,
                totalRoundedBdt = 2400L
            )
        )

        val sampleChartPoints = listOf(
            ChartDataPoint(label = "15 Jan", value = 1500L),
            ChartDataPoint(label = "16 Jan", value = 2400L)
        )

        val sampleTopSelling = listOf(
            ItemSalesSummary(
                itemName = "Octane Super",
                unit = "L",
                orderCount = 10,
                totalRevenuePaisa = 650000L
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                AnalyticsDashboardView(
                    language = "en",
                    dateFilter = selectedDateFilter,
                    analyticsGrouping = selectedGrouping,
                    analyticsMetric = selectedMetric,
                    analyticsDataPoints = sampleAggregates,
                    chartDataPoints = sampleChartPoints,
                    aggregatedOrders = 13,
                    aggregatedRawPaisa = 390000L,
                    aggregatedRoundedBdt = 3900L,
                    topSellingItems = sampleTopSelling,
                    onSelectDateFilter = { selectedDateFilter = it },
                    onOpenCustomDateDialog = {},
                    onSelectGrouping = { selectedGrouping = it },
                    onSelectMetric = { selectedMetric = it }
                )
            }
        }

        // Verify root dashboard view
        composeTestRule.onNodeWithTag("analytics_dashboard_view").assertIsDisplayed()

        // Verify Date Filter chips including Year
        composeTestRule.onNodeWithTag("analytics_filter_all").assertIsDisplayed()
        composeTestRule.onNodeWithTag("analytics_filter_today").assertIsDisplayed()
        composeTestRule.onNodeWithTag("analytics_filter_month").assertIsDisplayed()
        composeTestRule.onNodeWithTag("analytics_filter_year").assertIsDisplayed()

        // Test clicking Date Filter Year
        composeTestRule.onNodeWithTag("analytics_filter_year").performClick()
        assertEquals(DateFilterMode.YEAR, selectedDateFilter)

        // Verify Period Grouping chips (Day, Month, Year) are all present and displayed
        composeTestRule.onNodeWithTag("grouping_day").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grouping_month").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grouping_year").assertIsDisplayed()

        // Test clicking Period Grouping Year
        composeTestRule.onNodeWithTag("grouping_year").performClick()
        assertEquals(AnalyticsGrouping.YEAR, selectedGrouping)

        // Verify Chart Metric chips are present and clickable
        composeTestRule.onNodeWithTag("metric_rounded").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metric_raw").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metric_count").assertIsDisplayed()

        composeTestRule.onNodeWithTag("metric_raw").performClick()
        assertEquals(AnalyticsMetric.RAW_SALES, selectedMetric)

        // Verify Chart container and individual bars
        composeTestRule.onNodeWithTag("pos_analytics_chart").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chart_bar_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chart_bar_1").assertIsDisplayed()

        // Verify Top Selling and Breakdown tables
        composeTestRule.onNodeWithTag("top_selling_items_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_breakdown_table").assertIsDisplayed()
    }

    @Test
    fun testAnalyticsDashboardBengaliLanguageSupport() {
        var selectedGrouping = AnalyticsGrouping.MONTH

        composeTestRule.setContent {
            MyApplicationTheme {
                AnalyticsDashboardView(
                    language = "bn",
                    dateFilter = DateFilterMode.TODAY,
                    analyticsGrouping = selectedGrouping,
                    analyticsMetric = AnalyticsMetric.TRANSACTION_COUNT,
                    analyticsDataPoints = emptyList(),
                    chartDataPoints = emptyList(),
                    aggregatedOrders = 0,
                    aggregatedRawPaisa = 0L,
                    aggregatedRoundedBdt = 0L,
                    topSellingItems = emptyList(),
                    onSelectDateFilter = {},
                    onOpenCustomDateDialog = {},
                    onSelectGrouping = { selectedGrouping = it },
                    onSelectMetric = {}
                )
            }
        }

        // Verify chips render without crash in Bengali
        composeTestRule.onNodeWithTag("analytics_filter_year").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grouping_year").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chart_empty_state").assertIsDisplayed()
    }
}
