package com.flightwatch.app.ui.simple

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flightwatch.app.data.FlightPricePoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────
// DATA PROCESSING LAYER
// Validates, normalizes, and sorts price records.
// The visualization layer NEVER generates its own data.
// ─────────────────────────────────────────────────────────────

data class ProcessedPricePoint(
    val timestamp: Long,
    val price: Double,
    val dateLabel: String,
)

data class ProcessedPriceData(
    val points: List<ProcessedPricePoint>,
    val lowPrice: Double,
    val highPrice: Double,
    val currency: String,
    val isValid: Boolean,
)

object PriceHistoryProcessor {
    
    private val dateFormat = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

    /**
     * Process raw price records into validated, sorted, normalized data.
     * Rejects invalid records. Returns empty if no valid data.
     */
    fun process(raw: List<FlightPricePoint>): ProcessedPriceData {
        // Step 1: Filter out invalid records
        val valid = raw.filter { point ->
            point.price > 0 && 
            point.price.isFinite() && 
            point.timestamp > 0 &&
            point.currency.isNotBlank()
        }

        if (valid.isEmpty()) {
            return ProcessedPriceData(
                points = emptyList(),
                lowPrice = 0.0,
                highPrice = 0.0,
                currency = "SAR",
                isValid = false,
            )
        }

        // Step 2: Sort chronologically
        val sorted = valid.sortedBy { it.timestamp }

        // Step 3: Normalize to ProcessedPricePoint
        val points = sorted.map { point ->
            ProcessedPricePoint(
                timestamp = point.timestamp,
                price = point.price,
                dateLabel = dateFormat.format(Date(point.timestamp)),
            )
        }

        // Step 4: Calculate stats
        val prices = points.map { it.price }
        return ProcessedPriceData(
            points = points,
            lowPrice = prices.min(),
            highPrice = prices.max(),
            currency = sorted.first().currency,
            isValid = true,
        )
    }

    fun shortDate(timestamp: Long): String = shortDateFormat.format(Date(timestamp))
}

// ─────────────────────────────────────────────────────────────
// VISUALIZATION LAYER
// Receives processed data and renders the chart.
// NEVER generates its own price data.
// ─────────────────────────────────────────────────────────────

@Composable
fun PriceHistoryChart(
    data: ProcessedPriceData,
    modifier: Modifier = Modifier,
) {
    if (!data.isValid || data.points.isEmpty()) {
        // Empty state — NO fabricated graph
        Column(modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No price history available yet.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Price will be recorded after each check.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val points = data.points
    val priceRange = data.highPrice - data.lowPrice

    Column(modifier.fillMaxWidth()) {
        // Chart
        Canvas(Modifier.fillMaxWidth().height(80.dp)) {
            val w = size.width
            val h = size.height
            val padY = h * 0.15f
            val usableH = h - 2 * padY

            if (points.size == 1) {
                // Single record — show dot, no fabricated trend
                val y = h / 2f
                drawCircle(color = Color(0xFFE8A33D), radius = 6.dp.toPx(), center = Offset(w / 2f, y))
                return@Canvas
            }

            // Calculate X/Y positions from REAL data
            val coords = points.mapIndexed { i, p ->
                val x = if (points.size > 1) i * (w / (points.size - 1)) else w / 2f
                val y = if (priceRange <= 0) {
                    h / 2f
                } else {
                    val ratio = ((p.price - data.lowPrice) / priceRange).toFloat()
                    h - padY - ratio * usableH
                }
                Offset(x, y)
            }

            // Area fill (using real coordinates)
            if (coords.size > 1) {
                val area = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    coords.drop(1).forEach { lineTo(it.x, it.y) }
                    lineTo(coords.last().x, h)
                    lineTo(coords.first().x, h)
                    close()
                }
                drawPath(area, color = Color(0xFFE8A33D).copy(alpha = 0.12f))
            }

            // Line connecting real data points
            if (coords.size > 1) {
                val line = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    coords.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    line,
                    color = Color(0xFFE8A33D),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            // Dots at each real data point
            coords.forEach { drawCircle(color = Color(0xFFE8A33D), radius = 3.dp.toPx(), center = it) }
        }

        Spacer(Modifier.height(6.dp))

        // Stats from real data
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Low ${formatPrice(data.lowPrice, data.currency)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF22C55E))
            Spacer(Modifier.width(12.dp))
            Text("High ${formatPrice(data.highPrice, data.currency)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC62828))
            Spacer(Modifier.width(12.dp))
            Text("${points.size} price${if (points.size == 1) "" else "s"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(4.dp))
        Text("Last: ${points.last().dateLabel}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatPrice(price: Double, currency: String): String {
    return "${price.toLong()} $currency"
}
