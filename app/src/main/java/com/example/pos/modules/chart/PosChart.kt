package com.example.pos.modules.chart

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.domain.model.ChartDataPoint

/**
 * Lightweight, high-performance Canvas-based Chart engine for sales trend visualization.
 */
@Composable
fun PosSimpleBarChart(
    dataPoints: List<ChartDataPoint>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    if (dataPoints.isEmpty()) return

    val maxValue = dataPoints.maxOfOrNull { it.value } ?: 1L
    val safeMax = if (maxValue <= 0L) 1L else maxValue

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            val count = dataPoints.size
            val availableWidth = size.width
            val barSpacing = 16f
            val totalSpacing = barSpacing * (count + 1)
            val barWidth = ((availableWidth - totalSpacing) / count).coerceAtLeast(12f)
            val canvasHeight = size.height

            dataPoints.forEachIndexed { index, point ->
                val fraction = (point.value.toFloat() / safeMax.toFloat()).coerceIn(0.05f, 1f)
                val barHeight = canvasHeight * fraction
                val topLeftX = barSpacing + index * (barWidth + barSpacing)
                val topLeftY = canvasHeight - barHeight

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(topLeftX, topLeftY),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
        }

        // Labels underneath bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dataPoints.forEach { point ->
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Production-ready, touch-interactive Canvas Bar Chart for Offline POS Analytics.
 * Handles single day, dense months (30+ days), and multi-year data with horizontal scrolling,
 * Y-axis reference levels, tap-to-inspect detail pill, and smooth rendering.
 */
@Composable
fun PosAnalyticsBarChart(
    dataPoints: List<ChartDataPoint>,
    modifier: Modifier = Modifier,
    metricTitle: String = "Sales",
    isCurrency: Boolean = true,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.tertiary,
    emptyMessage: String = "No transaction data available for the selected range."
) {
    if (dataPoints.isEmpty()) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("chart_empty_state"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val maxValue = dataPoints.maxOfOrNull { it.value } ?: 1L
    val safeMax = if (maxValue <= 0L) 1L else maxValue

    val isDense = dataPoints.size > 14
    val scrollState = rememberScrollState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pos_analytics_chart"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with selected tooltip info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = metricTitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isCurrency) "Max Peak: ${FinancialCalculationEngine.formatWholeBdtToDisplay(safeMax)} ৳" else "Max Peak: $safeMax orders",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Interactive Inspector Pill
                val activePoint = selectedIndex?.let { if (it in dataPoints.indices) dataPoints[it] else null }
                if (activePoint != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = primaryColor.copy(alpha = 0.15f),
                        modifier = Modifier.testTag("chart_active_tooltip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${activePoint.label}: ",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = primaryColor
                            )
                            Text(
                                text = if (isCurrency) "${FinancialCalculationEngine.formatWholeBdtToDisplay(activePoint.value)} ৳" else "${activePoint.value}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = primaryColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chart Canvas area with Y-axis scale lines
            val chartHeightDp = 160.dp
            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

            Box(modifier = Modifier.fillMaxWidth()) {
                // Background Y-Axis Reference Guidelines (0%, 33%, 66%, 100%)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeightDp)
                ) {
                    val steps = 3
                    val stepHeight = size.height / steps
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                    for (i in 0..steps) {
                        val y = i * stepHeight
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f,
                            pathEffect = pathEffect
                        )
                    }
                }

                // Foreground Scrollable or Centered Bars
                val rowModifier = if (isDense) {
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                } else {
                    Modifier.fillMaxWidth()
                }

                Row(
                    modifier = rowModifier
                        .height(chartHeightDp + 26.dp)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = if (isDense) Arrangement.spacedBy(8.dp) else Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val barWidthDp = if (isDense) 28.dp else (320.dp / (dataPoints.size + 1)).coerceIn(24.dp, 48.dp)

                    dataPoints.forEachIndexed { index, point ->
                        val isSelected = selectedIndex == index
                        val fraction = (point.value.toFloat() / safeMax.toFloat()).coerceIn(0.04f, 1f)

                        Column(
                            modifier = Modifier
                                .width(barWidthDp)
                                .clickable {
                                    selectedIndex = if (selectedIndex == index) null else index
                                }
                                .testTag("chart_bar_$index"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Top value indicator when selected or high peak
                            if (isSelected) {
                                Text(
                                    text = if (isCurrency) "${point.value}" else "${point.value}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = primaryColor,
                                    maxLines = 1
                                )
                            }

                            // The Bar
                            Box(
                                modifier = Modifier
                                    .width(barWidthDp - 4.dp)
                                    .height((chartHeightDp.value * fraction).dp)
                                    .background(
                                        brush = if (isSelected) {
                                            Brush.verticalGradient(
                                                listOf(secondaryColor, primaryColor)
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                listOf(primaryColor, primaryColor.copy(alpha = 0.75f))
                                            )
                                        },
                                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                    )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // X-Axis Period Label
                            Text(
                                text = point.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (isDense) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "← Scroll horizontally to inspect all periods →",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
