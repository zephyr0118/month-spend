package com.yueji.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yueji.finance.core.model.*
import com.yueji.finance.feature.MainViewModel
import com.yueji.finance.ui.components.CategoryBars
import com.yueji.finance.ui.components.DonutChart
import com.yueji.finance.ui.components.ExpenseLineChart
import java.time.LocalDate

private enum class AnalysisModule(val label: String) { OVERVIEW("概览"), EXPENSE("消费"), INCOME("收入"), SAVINGS("结余"), ASSETS("资产"), ACCOUNTS("账户"), BEHAVIOR("行为"), QUALITY("数据质量") }
private enum class AnalysisDimension(val label: String) { CATEGORY("分类"), ACCOUNT("账户"), MERCHANT("商户"), TAG("标签"), NECESSITY("必要/可选"), VARIABILITY("固定/可变"), FREQUENCY("一次性/经常性") }

@Composable
fun AnalyticsScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle(); val rows by viewModel.transactions.collectAsStateWithLifecycle()
    val snapshots by viewModel.snapshots.collectAsStateWithLifecycle(); val annual by viewModel.annualSummaries.collectAsStateWithLifecycle()
    var module by rememberSaveable { mutableStateOf(AnalysisModule.OVERVIEW) }; var dimension by rememberSaveable { mutableStateOf(AnalysisDimension.CATEGORY) }
    val accountChartValues = remember(state.dashboard.accounts) {
        state.dashboard.accounts.map { it.name to it.amountMinor.coerceAtLeast(0) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(viewModel::previousMonth) { Icon(Icons.Default.ChevronLeft, "上个月") }; Text(Periods.label(state.month), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); IconButton(viewModel::nextMonth) { Icon(Icons.Default.ChevronRight, "下个月") }
        }
        ScrollableTabRow(AnalysisModule.entries.indexOf(module), edgePadding = 8.dp) { AnalysisModule.entries.forEach { Tab(module == it, { module = it }, text = { Text(it.label) }) } }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (module) {
                AnalysisModule.OVERVIEW -> {
                    item { FinanceSummary(state.dashboard, state.settings.amountsHidden) }
                    item { SectionCard("每日趋势", Icons.Default.Timeline) { ExpenseLineChart(state.dashboard.daily, state.settings.amountsHidden) } }
                    item { AnnualHistory(annual, state.settings.amountsHidden) }
                }
                AnalysisModule.EXPENSE -> {
                    item { DimensionSelector(dimension) { dimension = it } }
                    item { DimensionAnalysis(dimension, rows, state.settings.amountsHidden) }
                    item { SectionCard("分类分布", Icons.Default.BarChart) { CategoryBars(state.dashboard.categories, state.settings.amountsHidden) } }
                }
                AnalysisModule.INCOME -> item { IncomeAnalysis(rows, state.dashboard, state.settings.amountsHidden) }
                AnalysisModule.SAVINGS -> item { SavingsAnalysis(state.dashboard, annual, state.settings.amountsHidden) }
                AnalysisModule.ASSETS -> {
                    item { SectionCard("资产趋势（真实快照）", Icons.Default.ShowChart) { SnapshotTrend(snapshots, state.settings.amountsHidden) } }
                    item { SectionCard("当前账户分布", Icons.Default.DonutLarge) { DonutChart(accountChartValues, state.settings.amountsHidden) } }
                }
                AnalysisModule.ACCOUNTS -> item { AccountAnalysis(state.dashboard, state.settings.amountsHidden) }
                AnalysisModule.BEHAVIOR -> item { BehaviorAnalysis(rows, state.month.lengthOfMonth(), state.settings.amountsHidden) }
                AnalysisModule.QUALITY -> item { QualityAnalysis(rows, state.dashboard, snapshots.isNotEmpty(), state.settings.lastBackupEpochMillis) }
            }
        }
    }
}

@Composable private fun FinanceSummary(d: MonthlyDashboard, hidden: Boolean) = SectionCard("核心指标", Icons.Default.AccountBalanceWallet) {
    val metrics = listOf("收入" to Money(d.incomeMinor).format(hidden), "消费" to Money(d.effectiveExpenseMinor).format(hidden), "结余" to Money(d.savingsMinor).format(hidden), "储蓄率" to (d.savingsRate?.let { "${it / 100f}%" } ?: "不可计算"), "总资产" to Money(d.totalAssetsMinor).format(hidden), "无消费日" to "${d.month.lengthOfMonth() - d.daily.count { it.amountMinor > 0 }} 天")
    metrics.chunked(2).forEach { pair -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { pair.forEach { (label, value) -> Column(Modifier.weight(1f)) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) } } } }
}

@Composable private fun DimensionSelector(selected: AnalysisDimension, onSelect: (AnalysisDimension) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton({ open = true }) { Icon(Icons.Default.FilterAlt, null); Spacer(Modifier.width(8.dp)); Text("分析维度：${selected.label}") }
        DropdownMenu(open, { open = false }) { AnalysisDimension.entries.forEach { DropdownMenuItem({ Text(it.label) }, { onSelect(it); open = false }) } }
    }
}

@Composable private fun DimensionAnalysis(dimension: AnalysisDimension, rows: List<com.yueji.finance.core.database.TransactionListRow>, hidden: Boolean) {
    val groups: List<CategoryTotal> = remember(rows, dimension) {
        val expense = rows.filter { it.type == TransactionType.EXPENSE }
        when (dimension) {
            AnalysisDimension.CATEGORY -> expense.groupBy { it.categoryName ?: "未分类" }
            AnalysisDimension.ACCOUNT -> expense.groupBy { it.accountName }
            AnalysisDimension.MERCHANT -> expense.groupBy { it.merchantName ?: "未填写商户" }
            AnalysisDimension.TAG -> expense.flatMap { row -> row.tagsCsv.orEmpty().split(',').filter(String::isNotBlank).map { it to row } }.groupBy({ it.first }, { it.second })
            AnalysisDimension.NECESSITY -> expense.groupBy { if (it.necessity == Necessity.NECESSARY) "必要" else "可选" }
            AnalysisDimension.VARIABILITY -> expense.groupBy { if (it.variability == Variability.FIXED) "固定" else "可变" }
            AnalysisDimension.FREQUENCY -> expense.groupBy { if (it.isOneOff) "一次性" else "经常性" }
        }.map { CategoryTotal(null, it.key, it.value.sumOf { row -> row.amountMinor }) }.sortedByDescending { it.amountMinor }
    }
    SectionCard("按${dimension.label}分析", Icons.Default.PieChart) {
        if (groups.isEmpty()) Text(if (dimension == AnalysisDimension.TAG) "当前月份没有标签数据。可在 CSV 导入或交易编辑中添加标签。" else "暂无可分析的支出明细", color = MaterialTheme.colorScheme.onSurfaceVariant) else CategoryBars(groups, hidden)
    }
}

@Composable private fun IncomeAnalysis(rows: List<com.yueji.finance.core.database.TransactionListRow>, d: MonthlyDashboard, hidden: Boolean) = SectionCard("收入分析", Icons.Default.TrendingUp) {
    Text(Money(d.incomeMinor).format(hidden), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    if (d.incomeMinor <= 0) Text(UiText.noIncome, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val groups = remember(rows) {
        rows.filter { it.type == TransactionType.INCOME }
            .groupBy { it.categoryName ?: "其他" }
            .map { CategoryTotal(null, it.key, it.value.sumOf { row -> row.amountMinor }) }
            .sortedByDescending { it.amountMinor }
    }
    if (groups.isNotEmpty()) { Spacer(Modifier.height(12.dp)); CategoryBars(groups, hidden) }
}

@Composable private fun SavingsAnalysis(d: MonthlyDashboard, annual: List<com.yueji.finance.core.database.LegacyAnnualSummaryEntity>, hidden: Boolean) = SectionCard("结余与储蓄率", Icons.Default.Savings) {
    Text("本月结余 ${Money(d.savingsMinor).format(hidden)}", style = MaterialTheme.typography.headlineSmall)
    Text(d.savingsRate?.let { "储蓄率 ${it / 100f}%" } ?: UiText.noIncome)
    HorizontalDivider(Modifier.padding(vertical = 12.dp)); annual.forEach { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(it.label); Text(Money(it.savingsMinor).format(hidden), fontWeight = FontWeight.SemiBold) } }
}

@Composable private fun AnnualHistory(items: List<com.yueji.finance.core.database.LegacyAnnualSummaryEntity>, hidden: Boolean) = SectionCard("历史年度汇总（真实数据）", Icons.Default.CalendarToday) {
    items.forEach { item -> Column(Modifier.padding(vertical = 6.dp)) { Text(item.label, fontWeight = FontWeight.SemiBold); Text("收入 ${Money(item.incomeMinor).format(hidden)} · 消费 ${Money(item.expenseMinor).format(hidden)} · 结余 ${Money(item.savingsMinor).format(hidden)}", style = MaterialTheme.typography.bodySmall) } }
    if (items.isEmpty()) Text("暂无历史年度汇总")
}

@Composable private fun SnapshotTrend(items: List<com.yueji.finance.core.database.BalanceSnapshotEntity>, hidden: Boolean) {
    val totals = remember(items) { items.groupBy { it.snapshotYearMonth }.mapValues { it.value.sumOf { row -> row.amountMinor } }.toSortedMap() }
    totals.forEach { (month, total) -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("${month / 100}-${(month % 100).toString().padStart(2, '0')}"); Text(Money(total).format(hidden), fontWeight = FontWeight.SemiBold) } }
    if (totals.isEmpty()) Text("暂无余额快照")
}

@Composable private fun AccountAnalysis(d: MonthlyDashboard, hidden: Boolean) = SectionCard("账户分析", Icons.Default.AccountBalance) {
    val sortedAccounts = remember(d.accounts) { d.accounts.sortedByDescending { it.amountMinor } }
    sortedAccounts.forEach { account -> ListItem(headlineContent = { Text(account.name) }, supportingContent = { Text(account.type.name) }, trailingContent = { Text(Money(account.amountMinor).format(hidden), color = if (account.amountMinor < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }) }
}

@Composable private fun BehaviorAnalysis(rows: List<com.yueji.finance.core.database.TransactionListRow>, monthDays: Int, hidden: Boolean) = SectionCard("记账与消费行为", Icons.Default.Psychology) {
    val stats = remember(rows) {
        val expenses = rows.filter { it.type == TransactionType.EXPENSE }
        val total = expenses.sumOf { it.amountMinor }
        val weekend = expenses.filter { LocalDate.ofEpochDay(it.localDateEpochDay).dayOfWeek.value >= 6 }.sumOf { it.amountMinor }
        BehaviorStats(
            days = expenses.map { it.localDateEpochDay }.distinct().size,
            weekdayMinor = total - weekend,
            weekendMinor = weekend,
            oneOffPercent = if (total > 0) expenses.filter { it.isOneOff }.sumOf { it.amountMinor } * 100 / total else 0,
        )
    }
    Text("有消费日期：${stats.days} 天 · 无消费日期：${monthDays - stats.days} 天")
    Text("工作日消费 ${Money(stats.weekdayMinor).format(hidden)} · 周末消费 ${Money(stats.weekendMinor).format(hidden)}")
    Text("一次性支出占比：${stats.oneOffPercent}%")
}

private data class BehaviorStats(val days: Int, val weekdayMinor: Long, val weekendMinor: Long, val oneOffPercent: Long)

@Composable private fun QualityAnalysis(rows: List<com.yueji.finance.core.database.TransactionListRow>, d: MonthlyDashboard, hasSnapshots: Boolean, backup: Long?) = SectionCard("数据质量", Icons.Default.FactCheck) {
    QualityLine("未分类交易", rows.count { it.categoryId == null && it.type in setOf(TransactionType.EXPENSE, TransactionType.INCOME) }, rows.none { it.categoryId == null && it.type in setOf(TransactionType.EXPENSE, TransactionType.INCOME) })
    QualityLine("异常负余额账户", d.accounts.count { it.amountMinor < 0 && it.type != AccountType.LIABILITY }, d.accounts.none { it.amountMinor < 0 && it.type != AccountType.LIABILITY })
    QualityLine("余额快照", if (hasSnapshots) 0 else 1, hasSnapshots)
    QualityLine("30 天内备份", if (backup != null && System.currentTimeMillis() - backup < 30L * 86_400_000) 0 else 1, backup != null && System.currentTimeMillis() - backup < 30L * 86_400_000)
    if (d.hasLegacySummaryOnly) Text("该月只有年度汇总，没有逐月明细；系统没有将其显示为零消费。", color = MaterialTheme.colorScheme.primary)
}
@Composable private fun QualityLine(label: String, count: Int, good: Boolean) { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (good) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error); Spacer(Modifier.width(10.dp)); Text(label, Modifier.weight(1f)); Text(if (good) "正常" else "$count 项") } }
