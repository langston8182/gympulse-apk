package com.gympulse.app.core.designsystem.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gympulse.app.core.designsystem.components.rememberEvoHaptics
import com.gympulse.app.domain.model.SeriesPoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

private val AxisWidth = 52.dp
private val XLabelHeight = 22.dp
private val TooltipSpace = 44.dp

private data class ChartColors(
    val line: Color,
    val grid: Color,
    val label: Color,
    val tooltipBackground: Color,
    val tooltipText: Color,
    val surface: Color,
)

@Composable
private fun chartColors(line: Color) = ChartColors(
    line = line,
    grid = MaterialTheme.colorScheme.outlineVariant,
    label = MaterialTheme.colorScheme.onSurfaceVariant,
    tooltipBackground = MaterialTheme.colorScheme.inverseSurface,
    tooltipText = MaterialTheme.colorScheme.inverseOnSurface,
    surface = MaterialTheme.colorScheme.surfaceContainer,
)

@Composable
private fun ChartPlaceholder(height: Dp, message: String, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Interactive line chart: tap or drag horizontally to inspect a point. X positions are
 * proportional to time so that irregular training dates are represented faithfully.
 */
@Composable
fun LineChart(
    points: List<SeriesPoint>,
    valueFormatter: (Double) -> String,
    dateFormatter: (Long) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    axisFormatter: (Double) -> String = valueFormatter,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 220.dp,
    yStartsAtZero: Boolean = false,
    emptyMessage: String = "Pas encore assez de données",
) {
    if (points.isEmpty()) {
        ChartPlaceholder(height, emptyMessage, modifier)
        return
    }
    val colors = chartColors(color)
    val measurer = rememberTextMeasurer()
    val haptics = rememberEvoHaptics()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.label)
    val tooltipStyle = TextStyle(color = colors.tooltipText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val progress = remember { Animatable(0f) }
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }

    val minX = points.minOf { it.timestamp }
    val maxX = points.maxOf { it.timestamp }
    val rawMin = points.minOf { it.value }
    val rawMax = points.maxOf { it.value }
    val span = (rawMax - rawMin).takeIf { it > 0 } ?: max(abs(rawMax) * 0.2, 1.0)
    val axis = niceAxis(
        dataMin = if (yStartsAtZero) 0.0 else rawMin - span * 0.1,
        dataMax = rawMax + span * 0.1,
        startAtZero = yStartsAtZero,
        integerOnly = points.all { it.value % 1.0 == 0.0 },
    )
    val yMin = axis.min
    val yMax = axis.max

    fun selectAt(x: Float, chartLeft: Float, chartWidth: Float) {
        val relative = ((x - chartLeft) / chartWidth).coerceIn(0f, 1f)
        val target = minX + (maxX - minX) * relative
        val index = points.indices.minByOrNull { abs(points[it].timestamp - target) } ?: return
        if (index != selected) haptics.tick()
        selected = index
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(points) {
                val left = AxisWidth.toPx()
                detectTapGestures { offset -> selectAt(offset.x, left, size.width - left - 8.dp.toPx()) }
            }
            .pointerInput(points) {
                val left = AxisWidth.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = {},
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        selectAt(change.position.x, left, size.width - left - 8.dp.toPx())
                    },
                )
            },
    ) {
        val left = AxisWidth.toPx()
        val right = size.width - 8.dp.toPx()
        val top = TooltipSpace.toPx()
        val bottom = size.height - XLabelHeight.toPx()
        val chartWidth = right - left
        val chartHeight = bottom - top

        fun xOf(timestamp: Long): Float =
            if (maxX == minX) left + chartWidth / 2 else left + chartWidth * ((timestamp - minX).toFloat() / (maxX - minX))

        fun yOf(value: Double): Float = bottom - chartHeight * ((value - yMin) / (yMax - yMin)).toFloat()

        drawGrid(measurer, labelStyle, colors, left, right, top, bottom, axis, axisFormatter)
        drawXLabels(measurer, labelStyle, points.map { it.timestamp }, ::xOf, bottom, dateFormatter)

        val offsets = points.map { Offset(xOf(it.timestamp), yOf(it.value)) }
        val line = linePath(offsets)
        val clipRight = left + chartWidth * progress.value

        if (offsets.size > 1) {
            val area = Path().apply {
                addPath(line)
                lineTo(offsets.last().x, bottom)
                lineTo(offsets.first().x, bottom)
                close()
            }
            clipRect(left = 0f, right = clipRight + 2f) {
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f)), startY = top, endY = bottom))
                drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        offsets.forEachIndexed { index, offset ->
            if (offset.x <= clipRight + 1f) {
                val isLast = index == offsets.lastIndex
                drawCircle(colors.surface, radius = if (isLast) 6.dp.toPx() else 4.dp.toPx(), center = offset)
                drawCircle(color, radius = if (isLast) 4.5.dp.toPx() else 2.5.dp.toPx(), center = offset)
            }
        }

        selected?.let { index ->
            val point = offsets[index]
            drawLine(color.copy(alpha = 0.5f), Offset(point.x, top), Offset(point.x, bottom), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            drawCircle(color.copy(alpha = 0.25f), radius = 12.dp.toPx(), center = point)
            drawCircle(color, radius = 6.dp.toPx(), center = point)
            drawTooltip(measurer, tooltipStyle, colors, "${valueFormatter(points[index].value)} · ${dateFormatter(points[index].timestamp)}", point.x, 4.dp.toPx())
        }
    }
}

/** Interactive bar chart; tap a bar to read its value. */
@Composable
fun BarChart(
    points: List<SeriesPoint>,
    valueFormatter: (Double) -> String,
    dateFormatter: (Long) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    axisFormatter: (Double) -> String = valueFormatter,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 200.dp,
    highlightLast: Boolean = true,
    emptyMessage: String = "Aucune donnée sur la période",
) {
    if (points.isEmpty() || points.all { it.value <= 0.0 }) {
        ChartPlaceholder(height, emptyMessage, modifier)
        return
    }
    val colors = chartColors(color)
    val measurer = rememberTextMeasurer()
    val haptics = rememberEvoHaptics()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.label)
    val tooltipStyle = TextStyle(color = colors.tooltipText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val progress = remember { Animatable(0f) }
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val axis = niceAxis(0.0, points.maxOf { it.value }, startAtZero = true, integerOnly = points.all { it.value % 1.0 == 0.0 })
    val yMax = axis.max

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(points) {
                val left = AxisWidth.toPx()
                detectTapGestures { offset ->
                    val slot = (size.width - left - 8.dp.toPx()) / points.size
                    val index = ((offset.x - left) / slot).toInt().coerceIn(0, points.lastIndex)
                    if (index != selected) haptics.tick()
                    selected = if (selected == index) null else index
                }
            },
    ) {
        val left = AxisWidth.toPx()
        val right = size.width - 8.dp.toPx()
        val top = TooltipSpace.toPx()
        val bottom = size.height - XLabelHeight.toPx()
        val chartHeight = bottom - top
        val slot = (right - left) / points.size
        val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())

        drawGrid(measurer, labelStyle, colors, left, right, top, bottom, axis, axisFormatter)
        drawXLabels(measurer, labelStyle, points.map { it.timestamp }, { timestamp ->
            val index = points.indexOfFirst { it.timestamp == timestamp }
            left + slot * index + slot / 2
        }, bottom, dateFormatter)

        points.forEachIndexed { index, point ->
            val centerX = left + slot * index + slot / 2
            val fullHeight = (chartHeight * (point.value / yMax)).toFloat()
            val barHeight = if (point.value > 0) max(fullHeight * progress.value, 3.dp.toPx()) else 2.dp.toPx()
            val isHighlighted = selected == index || (selected == null && highlightLast && index == points.lastIndex)
            val barColor = when {
                point.value <= 0 -> colors.grid
                isHighlighted -> color
                else -> color.copy(alpha = 0.45f)
            }
            drawRoundRect(
                color = barColor,
                topLeft = Offset(centerX - barWidth / 2, bottom - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 3, barWidth / 3),
            )
        }
        selected?.let { index ->
            val centerX = left + slot * index + slot / 2
            drawTooltip(measurer, tooltipStyle, colors, "${valueFormatter(points[index].value)} · ${dateFormatter(points[index].timestamp)}", centerX, 4.dp.toPx())
        }
    }
}

/** Tiny trend line without axes, for list items and cards. */
@Composable
fun Sparkline(
    points: List<SeriesPoint>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (points.size < 2) return
    Canvas(modifier) {
        val minX = points.minOf { it.timestamp }
        val maxX = points.maxOf { it.timestamp }
        val minY = points.minOf { it.value }
        val maxY = points.maxOf { it.value }
        val spanY = (maxY - minY).takeIf { it > 0 } ?: 1.0
        val padding = 3.dp.toPx()
        val offsets = points.map {
            Offset(
                x = if (maxX == minX) size.width / 2 else padding + (size.width - 2 * padding) * ((it.timestamp - minX).toFloat() / (maxX - minX)),
                y = size.height - padding - (size.height - 2 * padding) * ((it.value - minY) / spanY).toFloat(),
            )
        }
        val path = linePath(offsets)
        val area = Path().apply {
            addPath(path)
            lineTo(offsets.last().x, size.height)
            lineTo(offsets.first().x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, radius = 3.dp.toPx(), center = offsets.last())
    }
}

/** Straight segments: honest with irregular data, no overshoot between points. */
private fun linePath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
}

/** Y axis with "round" ticks (1, 2, 2.5, 5 × 10^n), integer steps for integer data. */
internal data class ChartAxis(val min: Double, val max: Double, val ticks: List<Double>)

internal fun niceAxis(dataMin: Double, dataMax: Double, startAtZero: Boolean, integerOnly: Boolean, targetTicks: Int = 4): ChartAxis {
    var low = if (startAtZero) minOf(0.0, dataMin) else dataMin
    var high = dataMax
    if (high - low <= 0.0) {
        val pad = max(abs(high) * 0.1, 1.0)
        high += pad
        if (!startAtZero) low -= pad
    }
    val rough = (high - low) / targetTicks
    val magnitude = 10.0.pow(floor(log10(rough)))
    val residual = rough / magnitude
    var step = magnitude * when {
        residual <= 1.0 -> 1.0
        residual <= 2.0 -> 2.0
        residual <= 2.5 -> 2.5
        residual <= 5.0 -> 5.0
        else -> 10.0
    }
    if (integerOnly) step = max(1.0, ceil(step))
    val min = if (startAtZero && low >= 0) 0.0 else floor(low / step) * step
    var top = ceil(high / step) * step
    if (top <= min) top = min + step
    val ticks = generateSequence(min) { it + step }.takeWhile { it <= top + step / 1000 }.toList()
    return ChartAxis(min, top, ticks)
}

private fun DrawScope.drawGrid(
    measurer: TextMeasurer,
    style: TextStyle,
    colors: ChartColors,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    axis: ChartAxis,
    formatter: (Double) -> String,
) {
    axis.ticks.forEachIndexed { index, value ->
        val y = bottom - (bottom - top) * ((value - axis.min) / (axis.max - axis.min)).toFloat()
        drawLine(
            colors.grid,
            Offset(left, y),
            Offset(right, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = if (index == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 10f)),
        )
        val label = if (value == 0.0) "0" else formatter(value)
        val layout = measurer.measure(label, style, maxLines = 1)
        drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f).coerceInVertical(0f, size.height - layout.size.height))
    }
}

private fun Offset.coerceInVertical(min: Float, max: Float) = Offset(x, y.coerceIn(min, max))

private fun DrawScope.drawXLabels(
    measurer: TextMeasurer,
    style: TextStyle,
    timestamps: List<Long>,
    xOf: (Long) -> Float,
    bottom: Float,
    formatter: (Long) -> String,
) {
    if (timestamps.isEmpty()) return
    val indices = when {
        timestamps.size <= 7 -> timestamps.indices.toList()
        else -> listOf(0, timestamps.size / 3, 2 * timestamps.size / 3, timestamps.lastIndex)
    }.distinct()
    indices.forEach { index ->
        val layout = measurer.measure(formatter(timestamps[index]), style, maxLines = 1)
        val x = (xOf(timestamps[index]) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(x, bottom + 6.dp.toPx()))
    }
}

private fun DrawScope.drawTooltip(
    measurer: TextMeasurer,
    style: TextStyle,
    colors: ChartColors,
    text: String,
    anchorX: Float,
    top: Float,
) {
    val layout = measurer.measure(text, style, maxLines = 1)
    val paddingH = 10.dp.toPx()
    val paddingV = 6.dp.toPx()
    val width = layout.size.width + paddingH * 2
    val height = layout.size.height + paddingV * 2
    val x = (anchorX - width / 2).coerceIn(0f, size.width - width)
    drawRoundRect(colors.tooltipBackground, topLeft = Offset(x, top), size = Size(width, height), cornerRadius = CornerRadius(height / 2, height / 2))
    drawText(layout, topLeft = Offset(x + paddingH, top + paddingV))
}
