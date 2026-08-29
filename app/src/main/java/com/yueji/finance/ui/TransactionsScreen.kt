package com.yueji.finance.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.yueji.finance.core.database.TransactionListRow
import com.yueji.finance.core.model.*
import com.yueji.finance.feature.MainViewModel
import com.yueji.finance.feature.AdvancedTransactionFilter
import com.yueji.finance.ui.components.CategoryIconBadge
import com.yueji.finance.ui.components.transactionAmountColor
import com.yueji.finance.ui.components.transactionIconKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(viewModel: MainViewModel, onEdit: (TransactionListRow) -> Unit) {
    val rows by viewModel.transactions.collectAsStateWithLifecycle(); val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle(); val categories by viewModel.categories.collectAsStateWithLifecycle(); val advanced by viewModel.advancedFilter.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }; var selectedType by rememberSaveable { mutableStateOf<TransactionType?>(null) }
    var calendar by rememberSaveable { mutableStateOf(false) }; var selection by remember { mutableStateOf(setOf<String>()) }
    var filterDialog by remember { mutableStateOf(false) }
    val groupedRows = remember(rows) { rows.groupBy { LocalDate.ofEpochDay(it.localDateEpochDay) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("收支流水", style = MaterialTheme.typography.headlineSmall); Text("每一笔都有迹可循", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            BadgedBox(badge = { if (advanced.activeCount > 0) Badge { Text(advanced.activeCount.toString()) } }) { IconButton({ filterDialog = true }) { Icon(Icons.Default.FilterAlt, "更多筛选") } }
            IconButton({ calendar = !calendar }) { Icon(if (calendar) Icons.Default.ViewList else Icons.Default.CalendarMonth, if (calendar) "列表视图" else "日历视图") }
        }
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), elevation = CardDefaults.cardElevation(0.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(viewModel::previousMonth) { Icon(Icons.Default.ChevronLeft, "上个月", tint = MaterialTheme.colorScheme.onPrimary) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Periods.label(state.month), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("支出 ${Money(state.dashboard.effectiveExpenseMinor).format(state.settings.amountsHidden)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                        Text("收入 ${Money(state.dashboard.incomeMinor).format(state.settings.amountsHidden)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                IconButton(viewModel::nextMonth) { Icon(Icons.Default.ChevronRight, "下个月", tint = MaterialTheme.colorScheme.onPrimary) }
            }
        }
        OutlinedTextField(
            query, { query = it; viewModel.search(it) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("搜索商户、备注、账户或分类") }, singleLine = true,
            shape = CircleShape, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant, focusedBorderColor = MaterialTheme.colorScheme.primary),
        )
        ScrollableTabRow(selectedTabIndex = TransactionType.entries.indexOf(selectedType).plus(1), edgePadding = 12.dp, divider = {}) {
            FilterChip(selectedType == null, { selectedType = null; viewModel.filterType(null) }, { Text("全部") }, Modifier.padding(6.dp))
            TransactionType.entries.forEach { type -> FilterChip(selectedType == type, { selectedType = type; viewModel.filterType(type) }, { Text(type.label()) }, Modifier.padding(6.dp)) }
        }
        if (selection.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("已选择 ${selection.size} 项", Modifier.weight(1f)); TextButton({ selection.forEach(viewModel::deleteTransaction); selection = emptySet() }) { Icon(Icons.Default.Delete, null); Text("批量删除") }
            }
        }
        if (rows.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(UiText.noTransactions, Modifier.padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else if (calendar) TransactionCalendar(rows, state.month, state.settings.amountsHidden, onEdit)
        else LazyColumn(contentPadding = PaddingValues(bottom = 104.dp)) {
            groupedRows.forEach { (date, dayRows) ->
                stickyHeader { Surface(color = MaterialTheme.colorScheme.background) { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(date.format(DateTimeFormatter.ofPattern("M 月 d 日 EEEE")), fontWeight = FontWeight.SemiBold)
                    Text(Money(dayRows.sumOf { if (it.type == TransactionType.EXPENSE) it.amountMinor else if (it.type == TransactionType.REFUND) -it.amountMinor else 0 }).format(state.settings.amountsHidden), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } } }
                items(dayRows, key = { it.id }) { row -> TransactionRow(row, state.settings.amountsHidden, row.id in selection,
                    onClick = { if (selection.isEmpty()) onEdit(row) else selection = if (row.id in selection) selection - row.id else selection + row.id },
                    onLongClick = { selection = selection + row.id }) }
            }
        }
    }
    if (filterDialog) AdvancedFilterDialog(advanced, accounts, categories, { filterDialog = false }) { viewModel.applyAdvancedFilter(it); filterDialog = false }
}

@Composable private fun AdvancedFilterDialog(initial: AdvancedTransactionFilter, accounts: List<com.yueji.finance.core.database.AccountEntity>, categories: List<com.yueji.finance.core.database.CategoryEntity>, onDismiss: () -> Unit, onApply: (AdvancedTransactionFilter) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }; var accountMenu by remember { mutableStateOf(false) }; var categoryMenu by remember { mutableStateOf(false) }; var sourceMenu by remember { mutableStateOf(false) }; var tag by remember { mutableStateOf(initial.tag.orEmpty()) }; var min by remember { mutableStateOf(initial.minimumMinor?.let { java.math.BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()) }; var max by remember { mutableStateOf(initial.maximumMinor?.let { java.math.BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("更多筛选") }, text = { Column(Modifier.heightIn(max = 520.dp)) {
        Box { OutlinedButton({ accountMenu = true }, Modifier.fillMaxWidth()) { Text("账户：${accounts.firstOrNull { it.id == value.accountId }?.name ?: "全部"}") }; DropdownMenu(accountMenu, { accountMenu = false }) { DropdownMenuItem({ Text("全部账户") }, { value = value.copy(accountId = null); accountMenu = false }); accounts.filter { !it.isArchived }.forEach { item -> DropdownMenuItem({ Text(item.name) }, { value = value.copy(accountId = item.id); accountMenu = false }) } } }
        Box { OutlinedButton({ categoryMenu = true }, Modifier.fillMaxWidth()) { Text("分类：${categories.firstOrNull { it.id == value.categoryId }?.name ?: "全部"}") }; DropdownMenu(categoryMenu, { categoryMenu = false }) { DropdownMenuItem({ Text("全部分类") }, { value = value.copy(categoryId = null); categoryMenu = false }); categories.filter { !it.isArchived }.forEach { item -> DropdownMenuItem({ Text(item.name) }, { value = value.copy(categoryId = item.id); categoryMenu = false }) } } }
        OutlinedTextField(tag, { tag = it }, Modifier.fillMaxWidth(), label = { Text("标签（精确匹配）") }, singleLine = true)
        Text("必要性"); Row { FilterChip(value.necessity == null, { value = value.copy(necessity = null) }, { Text("全部") }); Spacer(Modifier.width(6.dp)); FilterChip(value.necessity == Necessity.NECESSARY, { value = value.copy(necessity = Necessity.NECESSARY) }, { Text("必要") }); Spacer(Modifier.width(6.dp)); FilterChip(value.necessity == Necessity.OPTIONAL, { value = value.copy(necessity = Necessity.OPTIONAL) }, { Text("可选") }) }
        Text("支出变化"); Row { FilterChip(value.variability == null, { value = value.copy(variability = null) }, { Text("全部") }); Spacer(Modifier.width(6.dp)); FilterChip(value.variability == Variability.FIXED, { value = value.copy(variability = Variability.FIXED) }, { Text("固定") }); Spacer(Modifier.width(6.dp)); FilterChip(value.variability == Variability.VARIABLE, { value = value.copy(variability = Variability.VARIABLE) }, { Text("可变") }) }
        Row { FilterChip(value.oneOff == true, { value = value.copy(oneOff = if (value.oneOff == true) null else true) }, { Text("一次性") }); Spacer(Modifier.width(6.dp)); FilterChip(value.reimbursable == true, { value = value.copy(reimbursable = if (value.reimbursable == true) null else true) }, { Text("可报销") }) }
        Box { OutlinedButton({ sourceMenu = true }) { Text("来源：${value.source?.name ?: "全部"}") }; DropdownMenu(sourceMenu, { sourceMenu = false }) { DropdownMenuItem({ Text("全部") }, { value = value.copy(source = null); sourceMenu = false }); TransactionSource.entries.forEach { item -> DropdownMenuItem({ Text(item.name) }, { value = value.copy(source = item); sourceMenu = false }) } } }
        Row { OutlinedTextField(min, { min = it }, Modifier.weight(1f), label = { Text("最低金额") }, singleLine = true); Spacer(Modifier.width(8.dp)); OutlinedTextField(max, { max = it }, Modifier.weight(1f), label = { Text("最高金额") }, singleLine = true) }
    } }, confirmButton = { TextButton({ onApply(value.copy(tag = tag.ifBlank { null }, minimumMinor = runCatching { Money.parse(min).minor }.getOrNull(), maximumMinor = runCatching { Money.parse(max).minor }.getOrNull())) }) { Text("应用") } }, dismissButton = { Row { TextButton({ onApply(AdvancedTransactionFilter()) }) { Text("清除") }; TextButton(onDismiss) { Text("取消") } } })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun TransactionRow(row: TransactionListRow, hidden: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryIconBadge(transactionIconKey(row.type, row.categoryIconKey), row.categoryName ?: row.type.label(), size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(transactionPrimaryLabel(row.merchantName, row.categoryName, row.type), fontWeight = FontWeight.SemiBold)
                Text(buildString {
                    val anchor = LocalDate.ofEpochDay(row.localDateEpochDay)
                    append(if (row.recordGranularity == RecordGranularity.DAY) anchor.format(DateTimeFormatter.ofPattern("M月d日")) else "${RecordPeriods.label(row.recordGranularity, anchor)}汇总")
                    if (!row.merchantName.isNullOrBlank()) row.categoryName?.let { append(" · $it") }
                    append(" · ${row.accountName}")
                    row.destinationAccountName?.let { append(" → $it") }
                    row.note?.let { append(" · $it") }
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                (if (row.type == TransactionType.EXPENSE) "−" else if (row.type == TransactionType.INCOME || row.type == TransactionType.REFUND) "+" else "") + Money(row.amountMinor).format(hidden),
                fontWeight = FontWeight.Bold,
                color = transactionAmountColor(row.type == TransactionType.INCOME || row.type == TransactionType.REFUND),
            )
            if (selected) Checkbox(true, { onLongClick() })
        }
    }
}

@Composable private fun TransactionCalendar(rows: List<TransactionListRow>, month: java.time.YearMonth, hidden: Boolean, onEdit: (TransactionListRow) -> Unit) {
    val byDay = remember(rows) { rows.groupBy { LocalDate.ofEpochDay(it.localDateEpochDay).dayOfMonth } }
    val leading = month.atDay(1).dayOfWeek.value - 1
    LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp)) {
        item { Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.weight(1f).padding(8.dp), style = MaterialTheme.typography.labelMedium) } } }
        items(((leading + month.lengthOfMonth() + 6) / 7)) { week ->
            Row(Modifier.fillMaxWidth()) { repeat(7) { weekday ->
                val day = week * 7 + weekday - leading + 1; val amount = byDay[day].orEmpty().sumOf { if (it.type == TransactionType.EXPENSE) it.amountMinor else 0 }
                Card(Modifier.weight(1f).padding(2.dp).height(72.dp).clickable(enabled = byDay[day].orEmpty().isNotEmpty()) { byDay[day]?.firstOrNull()?.let(onEdit) }) {
                    if (day in 1..month.lengthOfMonth()) Column(Modifier.padding(6.dp)) { Text(day.toString()); if (amount > 0) Text(Money(amount).format(hidden), style = MaterialTheme.typography.labelSmall, maxLines = 2) }
                }
            } }
        }
    }
}

internal fun TransactionType.label() = when (this) { TransactionType.EXPENSE -> "支出"; TransactionType.INCOME -> "收入"; TransactionType.TRANSFER -> "转账"; TransactionType.REFUND -> "退款"; TransactionType.BALANCE_ADJUSTMENT -> "调整" }
internal fun transactionPrimaryLabel(merchantName: String?, categoryName: String?, type: TransactionType): String =
    merchantName?.trim()?.takeIf(String::isNotEmpty) ?: categoryName?.trim()?.takeIf(String::isNotEmpty) ?: type.label()
private fun TransactionType.icon() = when (this) { TransactionType.EXPENSE -> Icons.Default.ArrowOutward; TransactionType.INCOME -> Icons.Default.SouthWest; TransactionType.TRANSFER -> Icons.Default.SwapHoriz; TransactionType.REFUND -> Icons.Default.Replay; TransactionType.BALANCE_ADJUSTMENT -> Icons.Default.Tune }
@Composable private fun TransactionType.color() = when (this) { TransactionType.EXPENSE -> MaterialTheme.colorScheme.error; TransactionType.INCOME, TransactionType.REFUND -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.primary }
