package com.yueji.finance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yueji.finance.core.database.CategoryEntity
import com.yueji.finance.core.database.TransactionListRow
import com.yueji.finance.core.model.*
import com.yueji.finance.feature.MainViewModel
import com.yueji.finance.ui.components.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onQuickAdd: (String?) -> Unit = {},
    onEditBudget: () -> Unit = {},
    onEditTransaction: (TransactionListRow) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val recentTransactions by viewModel.transactions.collectAsStateWithLifecycle()
    val dashboard = state.dashboard
    val hidden = state.settings.amountsHidden
    val quickCategories = remember(categories) { categories.filter { !it.isArchived && it.transactionDirection == TransactionDirection.EXPENSE } }
    val recentRows = remember(recentTransactions) { recentTransactions.take(3) }
    val accountChartValues = remember(dashboard.accounts) { dashboard.accounts.map { it.name to it.amountMinor.coerceAtLeast(0) } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 108.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MonthToolbar(state.month, hidden, viewModel::previousMonth, viewModel::nextMonth) { viewModel.setAmountsHidden(!hidden) } }
        item { HeroOverviewCard(dashboard, hidden, onEditBudget) }
        item { QuickEntryCard(quickCategories, onQuickAdd) }
        if (recentRows.isNotEmpty()) item { RecentTransactionsCard(recentRows, hidden, onEditTransaction) }
        if (!dashboard.hasTransactions) item { EmptyCard(if (dashboard.hasLegacySummaryOnly) UiText.legacyOnly else UiText.noTransactions) }
        item { ForecastCard(dashboard, state.forecast, hidden) }
        item { SectionCard("近 7 天消费趋势", Icons.Default.Timeline) { ExpenseLineChart(dashboard.daily.takeLast(7), hidden) } }
        item {
            SectionCard("分类消费", Icons.Default.DonutLarge) {
                if (dashboard.categories.isEmpty()) Text("记下第一笔支出后，这里会出现分类占比。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else CategoryBars(dashboard.categories.take(5), hidden)
            }
        }
        item { GoalsCard(dashboard.goals, hidden) }
        item { AssetCard(dashboard, hidden) }
        item { SectionCard("账户余额分布", Icons.Default.AccountBalance) { DonutChart(accountChartValues, hidden) } }
        item { InsightCard(state.insights) }
    }
}

@Composable
private fun RecentTransactionsCard(rows: List<TransactionListRow>, hidden: Boolean, onEdit: (TransactionListRow) -> Unit) = SectionCard("最近账单", Icons.Default.ReceiptLong) {
    rows.forEachIndexed { index, row ->
        Row(
            Modifier.fillMaxWidth().clickable { onEdit(row) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIconBadge(transactionIconKey(row.type, row.categoryIconKey), row.categoryName ?: row.type.label(), size = 40.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(transactionPrimaryLabel(row.merchantName, row.categoryName, row.type), fontWeight = FontWeight.SemiBold)
                Text(buildString {
                    val anchor = LocalDate.ofEpochDay(row.localDateEpochDay)
                    append(if (row.recordGranularity == RecordGranularity.DAY) anchor.toString() else "${RecordPeriods.label(row.recordGranularity, anchor)}汇总")
                    if (!row.merchantName.isNullOrBlank()) row.categoryName?.let { append(" · $it") }
                    append(" · ${row.accountName}")
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(
                (if (row.type == TransactionType.EXPENSE) "−" else if (row.type == TransactionType.INCOME || row.type == TransactionType.REFUND) "+" else "") + Money(row.amountMinor).format(hidden),
                fontWeight = FontWeight.Bold,
                color = transactionAmountColor(row.type == TransactionType.INCOME || row.type == TransactionType.REFUND),
            )
        }
        if (index != rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun MonthToolbar(month: YearMonth, hidden: Boolean, previous: () -> Unit, next: () -> Unit, toggleHidden: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("月迹", style = MaterialTheme.typography.headlineSmall)
            Text("让每一笔都清清楚楚", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(previous, Modifier.size(40.dp)) { Icon(Icons.Default.ChevronLeft, "上个月") }
                Text(Periods.label(month), style = MaterialTheme.typography.titleMedium)
                IconButton(next, Modifier.size(40.dp)) { Icon(Icons.Default.ChevronRight, "下个月") }
            }
        }
        IconButton(toggleHidden) { Icon(if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (hidden) "显示金额" else "隐藏金额") }
    }
}

@Composable
private fun HeroOverviewCard(d: MonthlyDashboard, hidden: Boolean, onEditBudget: () -> Unit) {
    val progress = d.budgetMinor?.let { FinancialMath.progressBasisPoints(d.effectiveExpenseMinor, it) }?.coerceAtLeast(0)
    val ratio = ((progress ?: 0) / 10_000f).coerceIn(0f, 1f)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("本月支出", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f), style = MaterialTheme.typography.titleMedium)
                Surface(onClick = onEditBudget, shape = CircleShape, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(5.dp)); Text("改预算", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            Text(Money(d.effectiveExpenseMinor).format(hidden), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HeroMetric("本月收入", Money(d.incomeMinor).format(hidden), Modifier.weight(1f))
                HeroMetric("本月结余", Money(d.savingsMinor).format(hidden), Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (d.budgetMinor == null) "尚未设置月预算"
                    else if (d.budgetRolloverMode == RolloverMode.NONE) "固定预算 ${Money(d.budgetMinor).format(hidden)}"
                    else "动态预算 ${Money(d.budgetMinor).format(hidden)} · 结转${if (d.budgetAdjustmentMinor >= 0) "+" else "−"}${Money(kotlin.math.abs(d.budgetAdjustmentMinor)).format(hidden)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    when {
                        progress == null -> "点右上角设置"
                        progress <= 10_000 -> "还剩 ${formatBudgetPercent(10_000 - progress)} · ${Money(d.budgetRemainingMinor ?: 0).format(hidden)}"
                        else -> "已超预算 ${formatBudgetPercent(progress - 10_000)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(7.dp),
                color = if ((progress ?: 0) > 10_000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@Composable private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier) = Column(modifier) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f))
    Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
}

@Composable
private fun QuickEntryCard(categories: List<CategoryEntity>, onQuickAdd: (String?) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("快速记一笔", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton({ onQuickAdd(null) }) { Text("更多分类"); Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(6.dp))
            categories.take(8).chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(4) { index ->
                        val category = row.getOrNull(index)
                        if (category == null) Spacer(Modifier.weight(1f))
                        else Column(
                            Modifier.weight(1f).clickable { onQuickAdd(category.id) }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CategoryIconBadge(category.iconKey, category.name, size = 46.dp)
                            Spacer(Modifier.height(7.dp)); Text(category.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastCard(d: MonthlyDashboard, forecast: Forecast, hidden: Boolean) = SectionCard("月末预测", Icons.Default.AutoGraph) {
    val today = LocalDate.now()
    val elapsed = if (YearMonth.from(today) == d.month) today.dayOfMonth else d.month.lengthOfMonth()
    val daily = d.effectiveExpenseMinor / elapsed.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Metric("当前日均", if (d.hasExpenseAggregateRecords) "不适用" else Money(daily).format(hidden), Modifier.weight(1f))
        Metric("预计支出", Money(forecast.expenseMinor).format(hidden), Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Metric("预计收入", Money(forecast.incomeMinor).format(hidden), Modifier.weight(1f))
        Metric("预计结余", Money(forecast.savingsMinor).format(hidden), Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    val difference = d.budgetMinor?.let { forecast.expenseMinor - it }
    Text(
        when {
            difference == null -> "设置月预算后即可看到超支风险。"
            difference > 0 -> "按当前速度，月末可能超出预算 ${Money(difference).format(hidden)}。"
            else -> "按当前速度，月末预计比预算少支出 ${Money(-difference).format(hidden)}。"
        }
    )
    Text("置信度：${forecast.confidence} · ${forecast.basis} · 仅供参考", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun GoalsCard(goals: List<GoalProgress>, hidden: Boolean) = SectionCard("年度与资产目标", Icons.Default.Flag) {
    if (goals.isEmpty()) Text("尚未设置目标", color = MaterialTheme.colorScheme.onSurfaceVariant) else goals.take(5).forEach { goal ->
        val progress = FinancialMath.progressBasisPoints(goal.currentMinor, goal.targetMinor)?.coerceIn(0, 15_000) ?: 0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(goal.name); Text("${progress / 100}%", fontWeight = FontWeight.SemiBold) }
        LinearProgressIndicator(progress = { (progress / 10_000f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp))
        if (!hidden && goal.targetMinor > 0) Text("目标 ${Money(goal.targetMinor).format(false)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun AssetCard(d: MonthlyDashboard, hidden: Boolean) = SectionCard("我的资产", Icons.Default.Savings) {
    Text(Money(d.totalAssetsMinor).format(hidden), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    val largest = d.accounts.maxByOrNull { it.amountMinor }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Metric("账户数量", "${d.accounts.size}", Modifier.weight(1f))
        Metric("最大账户占比", largest?.takeIf { d.totalAssetsMinor > 0 }?.let { "${it.amountMinor * 100 / d.totalAssetsMinor}%" } ?: "—", Modifier.weight(1f))
    }
}

@Composable private fun InsightCard(items: List<Insight>) = SectionCard("智能提示", Icons.Default.Lightbulb) {
    if (items.isEmpty()) Text("暂无重要提示", color = MaterialTheme.colorScheme.onSurfaceVariant) else items.forEach { insight ->
        ListItem(
            headlineContent = { Text(insight.title, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(insight.message) },
            leadingContent = {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Icon(
                        when (insight.level) { InsightLevel.CRITICAL -> Icons.Default.Error; InsightLevel.WARNING -> Icons.Default.Warning; InsightLevel.POSITIVE -> Icons.Default.EmojiEvents; else -> Icons.Default.Info },
                        null, Modifier.padding(9.dp), tint = when (insight.level) { InsightLevel.CRITICAL -> MaterialTheme.colorScheme.error; InsightLevel.WARNING -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.tertiary },
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        )
    }
}

@Composable
fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)) {
                    Icon(icon, null, Modifier.padding(8.dp).size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(10.dp)); Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(16.dp)); content()
        }
    }
}

@Composable private fun Metric(label: String, value: String, modifier: Modifier = Modifier) = Column(modifier) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

@Composable fun EmptyCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null); Spacer(Modifier.width(12.dp)); Text(text)
        }
    }
}

private fun formatBudgetPercent(basisPoints: Int): String {
    val tenths = (basisPoints.coerceAtLeast(0) + 5) / 10
    return if (tenths % 10 == 0) "${tenths / 10}%" else "${tenths / 10}.${tenths % 10}%"
}
