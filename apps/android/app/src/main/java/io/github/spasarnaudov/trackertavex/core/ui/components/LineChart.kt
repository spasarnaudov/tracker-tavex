package io.github.spasarnaudov.trackertavex.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.spasarnaudov.trackertavex.R
import io.github.spasarnaudov.trackertavex.core.ui.format.formatMoney
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ChartPoint(val timestamp: LocalDateTime, val value: BigDecimal)

internal object LineChartDefaults {

    val BottomPadding = 24.dp
    val ChartHeight = 220.dp
    val ChartWidthPadding = 32.dp
    val GridLineStrokeWidth = 1.dp
    val HorizontalPadding = 8.dp
    val LabelBottomMargin = 4.dp
    val LabelTextSize = 11.sp
    val LineStrokeWidth = 2.5.dp
    val PointRadius = 3.dp
    val PointSpacing = 48.dp
    val TopPadding = 8.dp
}

/**
 * A small dependency-free Canvas line chart: handles empty/one/many points.
 * When [scrollable] is true (the default), each point gets a fixed pixel spacing and the
 * chart scrolls horizontally for large point counts. When false, all points are always
 * squeezed into the available width — no scrolling, but points can get crowded.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    showTimeInLabels: Boolean = false,
    scrollable: Boolean = true,
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(LineChartDefaults.ChartHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.common_line_chart_no_data), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val scrollState = rememberScrollState()

    val minValue = points.minOf { it.value }
    val maxValue = points.maxOf { it.value }
    val valueRange = (maxValue - minValue).let { if (it.signum() == 0) BigDecimal.ONE else it }

    val chartWidth = remember(points.size) {
        LineChartDefaults.PointSpacing * (points.size - 1).coerceAtLeast(1) + LineChartDefaults.ChartWidthPadding
    }

    Column {
        Box(modifier = if (scrollable) modifier.horizontalScroll(scrollState) else modifier) {
            Canvas(
                modifier = if (scrollable) {
                    Modifier
                        .width(if (points.size > 1) chartWidth else LineChartDefaults.ChartHeight)
                        .height(LineChartDefaults.ChartHeight)
                } else {
                    Modifier.fillMaxWidth().height(LineChartDefaults.ChartHeight)
                },
            ) {
                val leftPad = LineChartDefaults.HorizontalPadding.toPx()
                val bottomPad = LineChartDefaults.BottomPadding.toPx()
                val topPad = LineChartDefaults.TopPadding.toPx()
                val usableHeight = size.height - bottomPad - topPad
                val usableWidth = size.width - leftPad * 2

                drawLine(
                    color = gridColor,
                    start = Offset(leftPad, size.height - bottomPad),
                    end = Offset(size.width - leftPad, size.height - bottomPad),
                    strokeWidth = LineChartDefaults.GridLineStrokeWidth.toPx(),
                )

                fun xFor(index: Int): Float =
                    if (points.size == 1) size.width / 2f else leftPad + usableWidth * index / (points.size - 1)

                fun yFor(value: BigDecimal): Float {
                    val fraction = (value - minValue).toFloat() / valueRange.toFloat()
                    return topPad + usableHeight * (1f - fraction)
                }

                if (points.size > 1) {
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val x = xFor(index)
                        val y = yFor(point.value)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path = path, color = lineColor, style = Stroke(width = LineChartDefaults.LineStrokeWidth.toPx()))
                }

                points.forEachIndexed { index, point ->
                    drawCircle(
                        color = lineColor,
                        radius = LineChartDefaults.PointRadius.toPx(),
                        center = Offset(xFor(index), yFor(point.value)),
                    )
                }

                val firstLabel = formatAxisLabel(points.first().timestamp, showTimeInLabels)
                val lastLabel = formatAxisLabel(points.last().timestamp, showTimeInLabels)
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = labelColor.toArgb()
                        textSize = LineChartDefaults.LabelTextSize.toPx()
                    }
                    val labelBottomMargin = LineChartDefaults.LabelBottomMargin.toPx()
                    drawText(firstLabel, leftPad, size.height - labelBottomMargin, paint)
                    if (points.size > 1) {
                        drawText(
                            lastLabel,
                            size.width - leftPad - paint.measureText(lastLabel),
                            size.height - labelBottomMargin,
                            paint,
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = stringResource(R.string.common_line_chart_low_value, minValue.formatMoney()), style = MaterialTheme.typography.labelSmall)
            Text(text = stringResource(R.string.common_line_chart_high_value, maxValue.formatMoney()), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatAxisLabel(timestamp: LocalDateTime, showTime: Boolean): String {
    val pattern = if (showTime) "MMM d HH:mm" else "MMM d"
    return timestamp.format(DateTimeFormatter.ofPattern(pattern))
}
