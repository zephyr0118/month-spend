package com.yueji.finance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yueji.finance.core.database.GoalEntity
import com.yueji.finance.core.model.*
import com.yueji.finance.feature.MainViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun GoalsScreen(viewModel: MainViewModel) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showGoalDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<GoalEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<GoalEntity?>(null) }
    var budgetDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("目标与预算", style = MaterialTheme.typography.headlineSmall)
                Text("目标可以随时修改，进度会自动重算", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton({ budgetDialog = true }) { Icon(Icons.Default.AccountBalanceWallet, null); Spacer(Modifier.width(4.dp)); Text("预算") }
            Spacer(Modifier.width(8.dp))
            Button({ editingGoal = null; showGoalDialog = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("目标") }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 104.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { CurrentBudgetCard(state.dashboard, state.settings.amountsHidden) { budgetDialog = true } }
            if (goals.isEmpty()) item { EmptyCard("尚未设置目标。可以创建收入、结余、资产、储蓄率或应急金目标。") }
            items(goals.size, key = { goals[it].id }) { index ->
                val goal = goals[index]
                GoalCard(goal, state.dashboard, state.settings.amountsHidden, onEdit = { editingGoal = goal; showGoalDialog = true }, onDelete = { pendingDelete = goal })
            }
        }
    }

    if (showGoalDialog) GoalDialog(editingGoal, { showGoalDialog = false; editingGoal = null }) { existing, type, name, target, ratio, date ->
        viewModel.saveGoal(existing, type, name, target, ratio, date)
        showGoalDialog = false; editingGoal = null
    }
    if (budgetDialog) BudgetDialog(state.dashboard, { budgetDialog = false }) { amount, mode ->
        viewModel.saveMonthlyBudget(amount, mode); budgetDialog = false
    }
    pendingDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Default.DeleteOutline, null) },
            title = { Text("删除“${goal.name}”？") },
            text = { Text("只会删除这个目标，不会删除任何账单、账户或历史数据。") },
            confirmButton = { Button({ viewModel.deleteGoal(goal.id); pendingDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("删除") } },
            dismissButton = { TextButton({ pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun CurrentBudgetCard(d: MonthlyDashboard, hidden: Boolean, onEdit: () -> Unit) = Card(
    Modifier.fillMaxWidth().clickable(onClick = onEdit),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    elevation = CardDefaults.cardElevation(0.dp),
) {
    Column(Modifier.padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${d.month.monthValue} 月消费预算", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Default.Edit, "修改预算", Modifier.size(19.dp))
        }
        if (d.budgetMinor == null) Text("未设置，点此添加") else {
            val progress = FinancialMath.progressBasisPoints(d.effectiveExpenseMinor, d.budgetMinor) ?: 0
            Text("${Money(d.effectiveExpenseMinor).format(hidden)} / ${Money(d.budgetMinor).format(hidden)}", style = MaterialTheme.typography.headlineSmall)
            LinearProgressIndicator({ (progress / 10_000f).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text("剩余 ${Money(d.budgetRemainingMinor ?: 0).format(hidden)} · 已使用 ${progress / 100}%")
            if (d.budgetRolloverMode != RolloverMode.NONE) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "动态预算：基础 ${Money(d.baseBudgetMinor ?: 0).format(hidden)} ${if (d.budgetAdjustmentMinor >= 0) "+" else "−"} 结转 ${Money(kotlin.math.abs(d.budgetAdjustmentMinor)).format(hidden)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Text("固定预算", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun GoalCard(goal: GoalEntity, d: MonthlyDashboard, hidden: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val current = when (goal.goalType) {
        GoalType.ASSET_BALANCE, GoalType.EMERGENCY_FUND -> d.totalAssetsMinor
        GoalType.MONTHLY_INCOME, GoalType.ANNUAL_INCOME -> d.incomeMinor
        GoalType.MONTHLY_EXPENSE, GoalType.ANNUAL_EXPENSE, GoalType.CATEGORY_BUDGET -> d.effectiveExpenseMinor
        else -> d.savingsMinor
    }
    val progress = if (goal.goalType == GoalType.SAVINGS_RATE) {
        val target = goal.targetRatioBasisPoints ?: 0
        if (target > 0) ((d.savingsRate ?: 0).toLong() * 10_000L / target).toInt() else 0
    } else FinancialMath.progressBasisPoints(current, goal.targetAmountMinor) ?: 0
    Surface(Modifier.fillMaxWidth().clickable(onClick = onEdit), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(goal.goalType.icon(), null, Modifier.padding(9.dp).size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${goal.goalType.label()} · 截止 ${LocalDate.ofEpochDay(goal.targetEpochDay).format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onEdit) { Icon(Icons.Default.Edit, "修改目标") }
                IconButton(onDelete) { Icon(Icons.Default.DeleteOutline, "删除目标") }
            }
            LinearProgressIndicator({ (progress / 10_000f).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(vertical = 10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (goal.goalType == GoalType.SAVINGS_RATE) "当前 ${(d.savingsRate ?: 0) / 100f}% / 目标 ${(goal.targetRatioBasisPoints ?: 0) / 100f}%"
                    else "当前 ${Money(current).format(hidden)} / 目标 ${Money(goal.targetAmountMinor).format(hidden)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("${(progress / 100f).coerceAtLeast(0f)}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDialog(initial: GoalEntity?, onDismiss: () -> Unit, onSave: (GoalEntity?, GoalType, String, Long, Int?, LocalDate) -> Unit) {
    var type by remember(initial?.id) { mutableStateOf(initial?.goalType ?: GoalType.ASSET_BALANCE) }
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "资产目标") }
    var value by remember(initial?.id) {
        mutableStateOf(
            if (initial?.goalType == GoalType.SAVINGS_RATE) BigDecimal.valueOf((initial.targetRatioBasisPoints ?: 0).toLong(), 2).stripTrailingZeros().toPlainString()
            else initial?.targetAmountMinor?.let { BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString() }.orEmpty()
        )
    }
    var dateText by remember(initial?.id) { mutableStateOf(LocalDate.ofEpochDay(initial?.targetEpochDay ?: LocalDate.now().plusYears(1).toEpochDay()).toString()) }
    var menu by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (initial == null) Icons.Default.AddTask else Icons.Default.Edit, null) },
        title = { Text(if (initial == null) "创建目标" else "修改目标") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton({ menu = true }, Modifier.fillMaxWidth()) { Text(type.label(), Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null) }
                    DropdownMenu(menu, { menu = false }) {
                        GoalType.entries.forEach { item -> DropdownMenuItem({ Text(item.label()) }, {
                            if (name.isBlank() || name == type.label()) name = item.label()
                            type = item; value = ""; error = null; menu = false
                        }) }
                    }
                }
                OutlinedTextField(name, { name = it; error = null }, label = { Text("目标名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    value, { value = it; error = null },
                    label = { Text(if (type == GoalType.SAVINGS_RATE) "目标储蓄率" else "目标金额") },
                    prefix = { Text(if (type == GoalType.SAVINGS_RATE) "" else "¥ ") }, suffix = { if (type == GoalType.SAVINGS_RATE) Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    dateText, {}, readOnly = true, modifier = Modifier.fillMaxWidth().clickable { datePicker = true },
                    label = { Text("截止日期") }, leadingIcon = { Icon(Icons.Default.Event, null) }, trailingIcon = { IconButton({ datePicker = true }) { Icon(Icons.Default.CalendarMonth, "选择日期") } },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (initial != null) Text("修改后会保留原目标 ID 和创建时间，历史账单不会受到影响。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button({
                val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
                when {
                    name.isBlank() -> error = "请填写目标名称"
                    date == null -> error = "请选择有效的截止日期"
                    type == GoalType.SAVINGS_RATE -> {
                        val ratio = value.toBigDecimalOrNull()?.multiply(BigDecimal(100))?.toInt()
                        if (ratio == null || ratio !in 1..10_000) error = "储蓄率应大于 0% 且不超过 100%"
                        else onSave(initial, type, name.trim(), 0, ratio, date)
                    }
                    else -> {
                        val amount = runCatching { Money.parse(value).minor }.getOrNull()
                        if (amount == null || amount <= 0) error = "请输入大于 0 的目标金额"
                        else onSave(initial, type, name.trim(), amount, null, date)
                    }
                }
            }) { Text(if (initial == null) "创建" else "保存修改") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )

    if (datePicker) GoalDatePicker(runCatching { LocalDate.parse(dateText) }.getOrDefault(LocalDate.now().plusYears(1)), { datePicker = false }) {
        dateText = it.toString(); error = null; datePicker = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDatePicker(initial: LocalDate, onDismiss: () -> Unit, onSelect: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton({ state.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } }) { Text("确定") }
    }, dismissButton = { TextButton(onDismiss) { Text("取消") } }) { DatePicker(state) }
}

@Composable
fun BudgetDialog(dashboard: MonthlyDashboard, onDismiss: () -> Unit, onSave: (Long, RolloverMode) -> Unit) {
    var text by remember { mutableStateOf((dashboard.baseBudgetMinor ?: dashboard.budgetMinor)?.let { BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString() }.orEmpty()) }
    var mode by remember { mutableStateOf(if (dashboard.budgetRolloverMode == RolloverMode.NONE) RolloverMode.NONE else RolloverMode.NET) }
    var error by remember { mutableStateOf(false) }
    val entered = runCatching { Money.parse(text).minor }.getOrNull()
    val preview = entered?.takeIf { it > 0 }?.let { BudgetMath.calculate(it, dashboard.previousMonthExpenseMinor, mode) }
    val previewAdjustment = preview?.adjustmentMinor ?: 0L
    val previewBudget = preview?.effectiveMinor
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置每月消费预算") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(RolloverMode.NONE to "固定预算", RolloverMode.NET to "动态预算").forEachIndexed { index, item ->
                        SegmentedButton(mode == item.first, { mode = item.first }, SegmentedButtonDefaults.itemShape(index, 2)) { Text(item.second) }
                    }
                }
                Text(
                    if (mode == RolloverMode.NONE) "每个月始终使用相同预算，不受上月消费影响。"
                    else "本月预算 = 基础预算 +（基础预算 − 上月实际支出）。上月没有支出记录时不结转。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(text, { text = it; error = false }, label = { Text("基础预算（元）") }, prefix = { Text("¥ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = error, singleLine = true)
                if (mode == RolloverMode.NET && entered != null) {
                    val previous = dashboard.previousMonthExpenseMinor
                    if (previous == null) {
                        Text("上月没有支出数据，本月暂按基础预算执行。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    } else {
                        Text("上月实际支出 ${Money(previous).format()} · 本月动态预算 ${Money(previewBudget ?: entered).format()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text("结转调整 ${if (previewAdjustment >= 0) "+" else "−"}${Money(kotlin.math.abs(previewAdjustment)).format()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { Button({ val amount = runCatching { Money.parse(text).minor }.getOrNull(); if (amount == null || amount <= 0) error = true else onSave(amount, mode) }) { Text("更新预算") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

private fun GoalType.label() = when (this) {
    GoalType.MONTHLY_EXPENSE -> "月消费上限"; GoalType.ANNUAL_EXPENSE -> "年消费上限"; GoalType.CATEGORY_BUDGET -> "分类预算"
    GoalType.MONTHLY_INCOME -> "月收入目标"; GoalType.ANNUAL_INCOME -> "年收入目标"; GoalType.MONTHLY_SAVINGS -> "月结余目标"
    GoalType.ANNUAL_SAVINGS -> "年结余目标"; GoalType.SAVINGS_RATE -> "储蓄率目标"; GoalType.ASSET_BALANCE -> "资产余额目标"
    GoalType.EMERGENCY_FUND -> "应急金覆盖月数"; GoalType.CUSTOM -> "自定义金额目标"
}

private fun GoalType.icon() = when (this) {
    GoalType.ASSET_BALANCE -> Icons.Default.Savings; GoalType.SAVINGS_RATE -> Icons.Default.Percent; GoalType.EMERGENCY_FUND -> Icons.Default.HealthAndSafety
    GoalType.MONTHLY_INCOME, GoalType.ANNUAL_INCOME -> Icons.Default.TrendingUp
    GoalType.MONTHLY_EXPENSE, GoalType.ANNUAL_EXPENSE, GoalType.CATEGORY_BUDGET -> Icons.Default.AccountBalanceWallet
    else -> Icons.Default.Flag
}
