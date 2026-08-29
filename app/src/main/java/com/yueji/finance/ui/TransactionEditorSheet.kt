package com.yueji.finance.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yueji.finance.core.database.*
import com.yueji.finance.core.model.*
import com.yueji.finance.data.TransactionDraft
import com.yueji.finance.feature.MainViewModel
import com.yueji.finance.ui.components.CategoryIconBadge
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorSheet(
    viewModel: MainViewModel,
    editing: TransactionListRow?,
    initialCategoryId: String? = null,
    onDismiss: () -> Unit,
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val recentTransactions by viewModel.transactions.collectAsStateWithLifecycle()
    val merchantSuggestions by viewModel.merchantSuggestions.collectAsStateWithLifecycle()
    val appSettings by viewModel.settings.collectAsStateWithLifecycle()
    var type by remember(editing?.id) { mutableStateOf(editing?.type ?: TransactionType.EXPENSE) }
    var recordGranularity by remember(editing?.id) { mutableStateOf(editing?.recordGranularity ?: RecordGranularity.DAY) }
    var amount by remember(editing?.id) { mutableStateOf(editing?.let { BigDecimal.valueOf(it.amountMinor, 2).stripTrailingZeros().toPlainString() }.orEmpty()) }
    var accountId by remember(editing?.id, accounts) { mutableStateOf(editing?.accountId ?: accounts.firstOrNull { !it.isArchived }?.id.orEmpty()) }
    var destinationId by remember(editing?.id) { mutableStateOf(editing?.destinationAccountId.orEmpty()) }
    var categoryId by remember(editing?.id, initialCategoryId) { mutableStateOf(editing?.categoryId ?: initialCategoryId.orEmpty()) }
    var merchant by remember(editing?.id) { mutableStateOf(editing?.merchantName.orEmpty()) }
    var note by remember(editing?.id) { mutableStateOf(editing?.note.orEmpty()) }
    var tags by remember(editing?.id) { mutableStateOf(editing?.tagsCsv.orEmpty()) }
    var dateText by remember(editing?.id) { mutableStateOf(LocalDate.ofEpochDay(editing?.localDateEpochDay ?: LocalDate.now().toEpochDay()).toString()) }
    var necessity by remember(editing?.id) { mutableStateOf(editing?.necessity ?: Necessity.NECESSARY) }
    var variability by remember(editing?.id) { mutableStateOf(editing?.variability ?: Variability.VARIABLE) }
    var oneOff by remember(editing?.id) { mutableStateOf(editing?.isOneOff ?: false) }
    var reimbursable by remember(editing?.id) { mutableStateOf(editing?.isReimbursable ?: false) }
    var excludeBudget by remember(editing?.id) { mutableStateOf(editing?.excludeFromBudget ?: false) }
    var adjustmentDirection by remember { mutableIntStateOf(1) }
    var createReminder by remember { mutableStateOf(false) }
    var attachmentUri by remember { mutableStateOf<Uri?>(null) }
    var moreExpanded by remember(editing?.id) { mutableStateOf(editing != null) }
    var datePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var appliedRecentDefaults by remember(editing?.id) { mutableStateOf(false) }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { attachmentUri = it }
    val direction = if (type == TransactionType.INCOME) TransactionDirection.INCOME else TransactionDirection.EXPENSE
    val visibleCategories = remember(categories, direction) { categories.filter { !it.isArchived && it.transactionDirection == direction } }
    val activeAccounts = remember(accounts) { accounts.filter { !it.isArchived } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(type, visibleCategories, initialCategoryId) {
        if (type in setOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.REFUND) && visibleCategories.none { it.id == categoryId }) {
            categoryId = visibleCategories.firstOrNull { it.id == initialCategoryId }?.id ?: visibleCategories.firstOrNull()?.id.orEmpty()
        }
    }
    LaunchedEffect(editing?.id, recentTransactions, accounts, appSettings.defaultAccountId) {
        if (editing == null && !appliedRecentDefaults) {
            val configured = accounts.firstOrNull { it.id == appSettings.defaultAccountId && !it.isArchived }
            val last = recentTransactions.firstOrNull()
            when {
                configured != null -> { accountId = configured.id; appliedRecentDefaults = true }
                last != null -> {
                    if (accounts.any { it.id == last.accountId && !it.isArchived }) accountId = last.accountId
                    if (initialCategoryId == null && type == last.type && visibleCategories.any { it.id == last.categoryId }) categoryId = last.categoryId.orEmpty()
                    appliedRecentDefaults = true
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxHeight()) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight().navigationBarsPadding(),
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (editing == null) "记一笔" else "编辑账单", style = MaterialTheme.typography.headlineSmall)
                    Text("金额与分类优先，其他信息可以稍后补充", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (editing != null) IconButton({ viewModel.deleteTransaction(editing.id); onDismiss() }) { Icon(Icons.Default.DeleteOutline, "删除") }
                else recentTransactions.firstOrNull()?.let { last ->
                    TextButton({
                        type = last.type
                        recordGranularity = last.recordGranularity
                        amount = BigDecimal.valueOf(last.amountMinor, 2).stripTrailingZeros().toPlainString()
                        accountId = last.accountId; destinationId = last.destinationAccountId.orEmpty()
                        categoryId = last.categoryId.takeIf { last.type in setOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.REFUND) }.orEmpty()
                        merchant = last.merchantName.orEmpty(); note = last.note.orEmpty(); tags = last.tagsCsv.orEmpty()
                        necessity = last.necessity ?: Necessity.NECESSARY; variability = last.variability ?: Variability.VARIABLE
                        oneOff = last.isOneOff; reimbursable = last.isReimbursable; excludeBudget = last.excludeFromBudget
                    }) { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("上一笔") }
                }
            }

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionType.entries.forEach { value ->
                    FilterChip(
                        selected = type == value,
                        onClick = {
                            type = value
                            if (value == TransactionType.TRANSFER || value == TransactionType.BALANCE_ADJUSTMENT) categoryId = ""
                            if (editing == null) recordGranularity = if (value == TransactionType.INCOME) RecordGranularity.MONTH else RecordGranularity.DAY
                            if (value !in setOf(TransactionType.EXPENSE, TransactionType.INCOME)) recordGranularity = RecordGranularity.DAY
                            error = null
                        },
                        label = { Text(value.label()) },
                        leadingIcon = if (type == value) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
                    )
                }
            }

            if (type in setOf(TransactionType.EXPENSE, TransactionType.INCOME)) {
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp)) {
                        Text("记录粒度", style = MaterialTheme.typography.titleMedium)
                        Text(if (recordGranularity == RecordGranularity.DAY) "记录某一天的实际金额" else "一次补录整个周期的汇总金额", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            RecordGranularity.entries.forEachIndexed { index, value ->
                                SegmentedButton(
                                    selected = recordGranularity == value,
                                    onClick = { recordGranularity = value; error = null },
                                    shape = SegmentedButtonDefaults.itemShape(index, RecordGranularity.entries.size),
                                ) { Text(when (value) { RecordGranularity.DAY -> "天"; RecordGranularity.WEEK -> "周"; RecordGranularity.MONTH -> "月"; RecordGranularity.QUARTER -> "季"; RecordGranularity.YEAR -> "年" }) }
                            }
                        }
                        if (recordGranularity != RecordGranularity.DAY) {
                            Spacer(Modifier.height(8.dp))
                            Text("汇总补录会作为一笔金额计入统计，请避免与同一周期已经记录的逐日账单重复。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(18.dp)) {
                    Text("金额（元）", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text("¥", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(amount.ifBlank { "0" }, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    NumberPad(amount) { amount = it; error = null }
                }
            }

            if (type in setOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.REFUND)) {
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp)) {
                        Text("选择分类", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        visibleCategories.take(12).chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                repeat(4) { index ->
                                    val category = row.getOrNull(index)
                                    if (category == null) Spacer(Modifier.weight(1f)) else Column(
                                        Modifier.weight(1f).clickable { categoryId = category.id; error = null }.padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        CategoryIconBadge(category.iconKey, category.name, size = 44.dp, selected = category.id == categoryId)
                                        Spacer(Modifier.height(5.dp)); Text(category.name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
                                    }
                                }
                            }
                        }
                        if (visibleCategories.size > 12) {
                            Spacer(Modifier.height(4.dp)); EntityDropdown("全部分类", categoryId, visibleCategories, { it.id }, { it.name }, { categoryId = it })
                        }
                    }
                }
            }

            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EntityDropdown("付款账户*", accountId, activeAccounts, { it.id }, { it.name }, { accountId = it })
                    if (type == TransactionType.TRANSFER) EntityDropdown("转入账户*", destinationId, activeAccounts.filter { it.id != accountId }, { it.id }, { it.name }, { destinationId = it })
                    if (type == TransactionType.BALANCE_ADJUSTMENT) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(1 to "增加余额", -1 to "减少余额").forEachIndexed { i, pair ->
                                SegmentedButton(adjustmentDirection == pair.first, { adjustmentDirection = pair.first }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(pair.second) }
                            }
                        }
                    }
                }
            }

            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("日期与来源", style = MaterialTheme.typography.titleMedium)
                    Text("归属周期：${RecordPeriods.label(recordGranularity, runCatching { LocalDate.parse(dateText) }.getOrDefault(LocalDate.now()))}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(dateText == LocalDate.now().toString(), { dateText = LocalDate.now().toString(); error = null }, { Text("今天") })
                        FilterChip(dateText == LocalDate.now().minusDays(1).toString(), { dateText = LocalDate.now().minusDays(1).toString(); error = null }, { Text("昨天") })
                        Button({ datePicker = true }, Modifier.weight(1f)) { Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(dateText) }
                    }
                    if (type != TransactionType.TRANSFER && type != TransactionType.BALANCE_ADJUSTMENT) {
                        MerchantAutocomplete(
                            value = merchant,
                            income = type == TransactionType.INCOME,
                            suggestions = merchantSuggestions,
                            onQuery = viewModel::searchMerchants,
                            onValueChange = { merchant = it },
                            onSelect = { selected ->
                                merchant = selected.displayName
                                selected.defaultCategoryId?.takeIf { id -> visibleCategories.any { it.id == id } }?.let { categoryId = it }
                            },
                        )
                    }
                }
            }

            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column {
                    ListItem(
                        modifier = Modifier.clickable { moreExpanded = !moreExpanded },
                        headlineContent = { Text("补充信息") },
                        supportingContent = { Text("备注、标签与预算属性") },
                        leadingContent = { Icon(Icons.Default.Tune, null) },
                        trailingContent = { Icon(if (moreExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                    if (moreExpanded) Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text(if (type == TransactionType.BALANCE_ADJUSTMENT) "调整原因*" else "备注") }, minLines = 2)
                        if (type != TransactionType.TRANSFER && type != TransactionType.BALANCE_ADJUSTMENT) OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), label = { Text("标签（逗号分隔）") }, singleLine = true)
                        if (type == TransactionType.EXPENSE) {
                            Text("支出属性", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(necessity == Necessity.NECESSARY, { necessity = Necessity.NECESSARY }, { Text("必要") })
                                FilterChip(necessity == Necessity.OPTIONAL, { necessity = Necessity.OPTIONAL }, { Text("可选") })
                                FilterChip(variability == Variability.FIXED, { variability = Variability.FIXED }, { Text("固定") })
                                FilterChip(variability == Variability.VARIABLE, { variability = Variability.VARIABLE }, { Text("可变") })
                            }
                            FlagRow("一次性支出", oneOff) { oneOff = it }
                            FlagRow("可报销", reimbursable) { reimbursable = it }
                            FlagRow("不计入预算", excludeBudget) { excludeBudget = it }
                            FlagRow("每月重复提醒", createReminder) { createReminder = it }
                        }
                        if (editing == null && type in setOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.REFUND)) {
                            OutlinedButton({ attachmentPicker.launch("image/*") }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.AttachFile, null); Spacer(Modifier.width(6.dp)); Text(if (attachmentUri == null) "添加图片附件" else "已选择附件，点此更换")
                            }
                        }
                    }
                }
            }

                Spacer(Modifier.height(4.dp))
            }
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), textAlign = TextAlign.Center) }
                    Button(
                onClick = {
                    val money = runCatching { Money.parse(amount).minor }.getOrNull()
                    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
                    error = when {
                        money == null || money <= 0 -> "请输入有效金额"
                        date == null -> "日期格式无效"
                        accountId.isBlank() -> "请选择账户"
                        type == TransactionType.TRANSFER && destinationId.isBlank() -> "请选择转入账户"
                        type in setOf(TransactionType.EXPENSE, TransactionType.INCOME) && categoryId.isBlank() -> "请选择分类"
                        type == TransactionType.BALANCE_ADJUSTMENT && note.isBlank() -> "余额调整必须填写原因"
                        else -> null
                    }
                    if (error == null) {
                        viewModel.saveTransaction(
                            TransactionDraft(
                                editing?.id, type, money!!, date!!, LocalTime.now().withSecond(0).withNano(0), accountId,
                                destinationId.takeIf { type == TransactionType.TRANSFER && it.isNotBlank() },
                                categoryId.takeIf { type in setOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.REFUND) && it.isNotBlank() },
                                merchant.ifBlank { null }, note.ifBlank { null },
                                necessity.takeIf { type == TransactionType.EXPENSE }, variability.takeIf { type == TransactionType.EXPENSE }, oneOff, reimbursable, excludeBudget,
                                balanceDirection = adjustmentDirection, tags = tags.split(',', '，').map(String::trim).filter(String::isNotEmpty), createMonthlyReminder = createReminder,
                                recordGranularity = recordGranularity,
                            ), attachmentUri,
                        )
                        onDismiss()
                    }
                },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    ) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(7.dp)); Text(if (editing == null) "保存这笔账" else "保存修改", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
    if (datePicker) TransactionDatePicker(runCatching { LocalDate.parse(dateText) }.getOrDefault(LocalDate.now()), { datePicker = false }) {
        dateText = it.toString(); error = null; datePicker = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MerchantAutocomplete(
    value: String,
    income: Boolean,
    suggestions: List<MerchantEntity>,
    onQuery: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onSelect: (MerchantEntity) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val menuWidth = maxWidth
        ExposedDropdownMenuBox(
            expanded = expanded && suggestions.isNotEmpty(),
            onExpandedChange = {
                expanded = it
                if (it) onQuery(value)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    onQuery(it)
                    expanded = true
                },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                label = { Text(if (income) "收入来源" else "商户 / 支出去向") },
                placeholder = { Text(if (income) "例如：公司工资、奖金、兼职" else "例如：盒马、房东、滴滴") },
                supportingText = { Text(if (income) "输入关键字可选择历史收入来源" else "输入关键字可模糊查找历史商户") },
                leadingIcon = { Icon(if (income) Icons.Default.Work else Icons.Default.Storefront, null) },
                trailingIcon = { if (suggestions.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                singleLine = true,
            )
            if (expanded && suggestions.isNotEmpty()) {
                Popup(
                    popupPositionProvider = UpwardDropdownPositionProvider,
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(
                        focusable = false,
                        dismissOnClickOutside = true,
                        clippingEnabled = true,
                    ),
                ) {
                    Surface(
                        modifier = Modifier.width(menuWidth).heightIn(max = 240.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        shadowElevation = 8.dp,
                    ) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                            suggestions.forEach { merchant ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(merchant.displayName)
                                            Text("历史使用 ${merchant.useCount} 次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        onSelect(merchant)
                                        expanded = false
                                    },
                                    leadingIcon = { Icon(if (income) Icons.Default.Work else Icons.Default.History, null) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private object UpwardDropdownPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val preferredX = when (layoutDirection) {
            LayoutDirection.Ltr -> anchorBounds.left
            LayoutDirection.Rtl -> anchorBounds.right - popupContentSize.width
        }
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = preferredX.coerceIn(0, maxX)
        val y = (anchorBounds.top - popupContentSize.height - 8).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
private fun NumberPad(value: String, onValueChange: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫")).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { key ->
                Surface(
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (key == "⌫") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val next = when (key) {
                            "⌫" -> value.dropLast(1)
                            "." -> if (value.contains('.')) value else if (value.isBlank()) "0." else "$value."
                            else -> when {
                                value == "0" -> key
                                value.substringAfter('.', "").length >= 2 && value.contains('.') -> value
                                value.length >= 12 -> value
                                else -> value + key
                            }
                        }
                        onValueChange(next)
                    },
                ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(key, style = MaterialTheme.typography.titleLarge) } }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDatePicker(initial: LocalDate, onDismiss: () -> Unit, onSelect: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ state.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } }) { Text("确定") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    ) { DatePicker(state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EntityDropdown(label: String, selectedId: String, items: List<T>, id: (T) -> String, name: (T) -> String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = items.firstOrNull { id(it) == selectedId }
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(
            selected?.let(name) ?: items.firstOrNull()?.let(name) ?: "暂无可选项", {}, readOnly = true,
            modifier = Modifier.menuAnchor().fillMaxWidth(), label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            items.forEach { item -> DropdownMenuItem({ Text(name(item)) }, { onSelect(id(item)); expanded = false }) }
        }
    }
}

@Composable
private fun FlagRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label); Switch(value, onChange)
    }
}