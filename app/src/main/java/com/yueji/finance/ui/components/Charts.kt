package com.yueji.finance.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yueji.finance.core.model.CategoryTotal
import com.yueji.finance.core.model.DailyTotal
import com.yueji.finance.core.model.Money
import kotlin.math.max

@Composable
fun BudgetRing(progressBasisPoints: Int?, label: String, modifier: Modifier = Modifier) {
    val target = ((progressBasisPoints ?: 0).coerceIn(0, 10_000) / 10_000f)
    val progress by animateFloatAsState(target, label = "budget progress")
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val statusColor = when {
        progressBasisPoints == null -> MaterialTheme.colorScheme.outline
        progressBasisPoints > 10_000 -> MaterialTheme.colorScheme.error
        progressBasisPoints >= 9_000 -> Color(0xFFD97706)
        else -> primary
    }
    Box(modifier.size(112.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(track, -90f, 360f, false, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
            drawArc(statusColor, -90f, 360f * progress, false, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
        }
        Text(if (progressBasisPoints == null) "未设置" else "${progressBasisPoints / 100}%", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CategoryBars(items: List<CategoryTotal>, hidden: Boolean, modifier: Modifier = Modifier) {
    val maxAmount = remember(items) { max(1L, items.maxOfOrNull { it.amountMinor } ?: 1L) }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val barColor = MaterialTheme.colorScheme.primary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.take(6).forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.name, style = MaterialTheme.typography.bodyMedium)
                    Text(Money(item.amountMinor).format(hidden), style = MaterialTheme.typography.labelLarge)
                }
                val fraction by animateFloatAsState((item.amountMinor.toFloat() / maxAmount).coerceIn(0f, 1f), label = item.name)
                Canvas(Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = "${item.name}，${Money(item.amountMinor).format(hidden)}" }) {
                    drawRoundRect(trackColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                    drawRoundRect(barColor, size = Size(size.width * fraction, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                }
            }
        }
    }
}

@Composable
fun ExpenseLineChart(items: List<DailyTotal>, hidden: Boolean, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maxAmount = remember(items) { max(1L, items.maxOfOrNull { it.amountMinor } ?: 1L) }
    Canvas(modifier.height(150.dp).fillMaxWidth().semantics {
        contentDescription = if (items.isEmpty()) "暂无每日消费趋势" else "每日消费趋势，共 ${items.size} 个有消费日期，最高 ${Money(maxAmount).format(hidden)}"
    }) {
        repeat(4) { i -> val y = size.height * i / 3; drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
        if (items.size == 1) {
            drawCircle(lineColor, 5.dp.toPx(), Offset(size.width / 2, size.height * (1 - items[0].amountMinor.toFloat() / maxAmount)))
        } else if (items.size > 1) {
            val path = Path()
            items.forEachIndexed { index, item ->
                val x = size.width * index / (items.size - 1)
                val y = size.height * (1f - item.amountMinor.toFloat() / maxAmount)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
fun DonutChart(values: List<Pair<String, Long>>, hidden: Boolean, modifier: Modifier = Modifier) {
    val colors = remember { listOf(Color(0xFF006874), Color(0xFF4B6267), Color(0xFF6A5ACD), Color(0xFF548235), Color(0xFFC27C0E), Color(0xFF9C4F74)) }
    val positive = remember(values) { values.filter { it.second > 0 } }
    val total = remember(positive) { positive.sumOf { it.second }.coerceAtLeast(1) }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = "账户余额分布，总计 ${Money(total).format(hidden)}" }) {
            var start = -90f
            positive.take(6).forEachIndexed { i, (_, value) ->
                val sweep = value.toFloat() / total * 360f
                drawArc(colors[i % colors.size], start, sweep, false, topLeft = Offset((size.width - size.height) / 2, 0f), size = Size(size.height, size.height), style = Stroke(24.dp.toPx()))
                start += sweep
            }
        }
        positive.take(6).forEachIndexed { i, (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(10.dp)) { drawCircle(colors[i % colors.size]) }
                Spacer(Modifier.width(8.dp)); Text("$label  ${Money(value).format(hidden)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
