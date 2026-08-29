package com.yueji.finance.ui

import android.app.Activity
import android.Manifest
import android.os.Build
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yueji.finance.BuildConfig
import com.yueji.finance.core.database.AccountEntity
import com.yueji.finance.core.database.CategoryEntity
import com.yueji.finance.core.model.*
import com.yueji.finance.feature.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle(); val accounts by viewModel.accounts.collectAsStateWithLifecycle(); val categories by viewModel.categories.collectAsStateWithLifecycle()
    val activeAccounts = remember(accounts) { accounts.filter { !it.isArchived } }
    val activeCategories = remember(categories) { categories.filter { !it.isArchived } }
    val context = LocalContext.current; var accountDialog by remember { mutableStateOf<AccountEntity?>(null) }; var newAccount by remember { mutableStateOf(false) }
    var categoriesOpen by remember { mutableStateOf(false) }; var accountOpen by remember { mutableStateOf(false) }; var diagnostics by remember { mutableStateOf(false) }; var privacy by remember { mutableStateOf(false) }
    var backupPasswordDialog by remember { mutableStateOf(false) }; var restorePasswordDialog by remember { mutableStateOf(false) }; var clearDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingBackupPassword by remember { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> viewModel.setReminder(granted) }
    val csvImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { context.contentResolver.openInputStream(it)?.let { input -> viewModel.importCsv(input, it.lastPathSegment ?: "import.csv") } } }
    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { context.contentResolver.openOutputStream(it)?.let(viewModel::exportCsv) } }
    val monthlyReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> uri?.let { context.contentResolver.openOutputStream(it)?.let(viewModel::exportMonthlyReport) } }
    val annualReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> uri?.let { context.contentResolver.openOutputStream(it)?.let(viewModel::exportAnnualReport) } }
    val backupExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.let { out -> viewModel.createBackup(out, pendingBackupPassword?.takeIf(String::isNotEmpty)?.toCharArray()) } }
        pendingBackupPassword = null
    }
    val backupRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { pendingRestoreUri = uri; restorePasswordDialog = true } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item { SettingsHero(activeAccounts.size, activeCategories.size) }
        item { SettingsHeader("账本") }
        item { SettingsItem(Icons.Default.AccountBalance, "账户管理", "${activeAccounts.size} 个启用账户") { accountOpen = !accountOpen } }
        if (accountOpen) {
            items(accounts, key = { it.id }) { account ->
                val index = activeAccounts.indexOfFirst { it.id == account.id }
                AccountManagerRow(account, index > 0, index >= 0 && index < activeAccounts.lastIndex, { accountDialog = account }, { viewModel.moveAccount(account.id, -1) }, { viewModel.moveAccount(account.id, 1) })
            }
            item { TextButton({ newAccount = true; accountDialog = null }, Modifier.padding(horizontal = 16.dp)) { Icon(Icons.Default.Add, null); Text("添加账户") } }
        }
        item { DefaultAccountSetting(state.settings.defaultAccountId, activeAccounts, viewModel::setDefaultAccount) }
        item { SettingsItem(Icons.Default.Category, "分类和标签", "${activeCategories.size} 个分类") { categoriesOpen = !categoriesOpen } }
        if (categoriesOpen) item { CategoryManager(categories, viewModel) }
        item { FiscalSetting(state.settings.fiscalYearStartMonth, viewModel::setFiscalMonth) }
        item { SettingsHeader("外观与隐私") }
        item { ThemeSetting(state.settings.themeMode, viewModel::setTheme) }
        item { SwitchItem(Icons.Default.Palette, "动态取色", "Android 12 及以上跟随系统色彩", state.settings.dynamicColor, viewModel::setDynamicColor) }
        item {
            SettingsItem(
                Icons.Default.Speed,
                "高刷新率",
                "应用已申请最高刷新率；一加 12 请在“每应用刷新率”中选择 120Hz",
            ) { context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
        }
        item { SwitchItem(Icons.Default.VisibilityOff, "默认隐藏金额", "覆盖首页、流水、分析和目标", state.settings.amountsHidden, viewModel::setAmountsHidden) }
        item { SwitchItem(Icons.Default.Security, "隐藏最近任务缩略图", "同时阻止系统截图", state.settings.hideInRecents, viewModel::setHideInRecents) }
        item { SwitchItem(Icons.Default.Fingerprint, "应用锁", "使用设备凭据或生物识别", state.settings.appLockEnabled, viewModel::setAppLock) }
        item { SettingsHeader("提醒") }
        item { SwitchItem(Icons.Default.Notifications, "记账与复盘提醒", "默认 20:00；关闭后取消后台提醒", state.settings.reminderEnabled) { enabled -> if (enabled && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.setReminder(enabled) } }
        item { SettingsHeader("数据导入、导出与恢复") }
        item { SettingsItem(Icons.Default.UploadFile, "导入交易 CSV", "UTF-8/GB18030；自动去重并报告具体行号") { csvImport.launch(arrayOf("text/*", "text/csv", "application/csv")) } }
        item { SettingsItem(Icons.Default.Download, "导出全部交易 CSV", "标准字段，可再次导入") { csvExport.launch("月迹交易_${java.time.LocalDate.now()}.csv") } }
        item { SettingsItem(Icons.Default.PictureAsPdf, "导出本月月报 PDF", "默认隐藏账户详细余额") { monthlyReport.launch("月迹月报_${state.month}.pdf") } }
        item { SettingsItem(Icons.Default.Assessment, "导出年度回顾 PDF", "包含真实年度汇总与历史对比") { annualReport.launch("月迹年度回顾_${java.time.LocalDate.now().year}.pdf") } }
        item { SettingsItem(Icons.Default.Backup, "创建完整备份", "生成 .yueji 文件，可选密码加密") { backupPasswordDialog = true } }
        item { SettingsItem(Icons.Default.Restore, "从完整备份恢复", "恢复前自动保存当前数据库安全副本") { backupRestore.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) } }
        item { SettingsHeader("关于") }
        item { SettingsItem(Icons.Default.PrivacyTip, "隐私说明", "本地优先、默认不联网") { privacy = true } }
        item { SettingsItem(Icons.Default.Info, "关于月迹", "版本 ${BuildConfig.VERSION_NAME}") { diagnostics = true } }
        item { SettingsItem(Icons.Default.DeveloperMode, "开发者诊断信息", "不包含金额、商户或备注") { diagnostics = true } }
        item { SettingsItem(Icons.Default.DeleteForever, "清空全部数据", "需要输入确认词；操作后返回首次启动", color = MaterialTheme.colorScheme.error) { clearDialog = true } }
    }
    if (accountDialog != null || newAccount) AccountDialog(accountDialog, { accountDialog = null; newAccount = false }) { id, name, type, opening, include, negative -> viewModel.saveAccount(id, name, type, opening, include, negative); accountDialog = null; newAccount = false }
    if (privacy) InfoDialog("隐私说明", UiText.privacy) { privacy = false }
    if (diagnostics) InfoDialog("开发者诊断信息", "应用版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n数据库架构版本：4\n账户数：${accounts.size}\n分类数：${categories.size}\n编译类型：${BuildConfig.BUILD_TYPE}\n\n诊断信息不会记录金额、余额、商户或备注。") { diagnostics = false }
    if (backupPasswordDialog) PasswordDialog("创建完整备份", "留空则不加密", { backupPasswordDialog = false }) { password -> pendingBackupPassword = password; backupPasswordDialog = false; backupExport.launch("月迹备份_${java.time.LocalDate.now()}.yueji") }
    if (restorePasswordDialog) PasswordDialog("恢复备份", "若备份未加密请留空", { restorePasswordDialog = false; pendingRestoreUri = null }) { password ->
        val uri = pendingRestoreUri; restorePasswordDialog = false; pendingRestoreUri = null
        uri?.let { context.contentResolver.openInputStream(it)?.let { input -> viewModel.restoreBackup(input, password.takeIf { p -> p.isNotEmpty() }?.toCharArray()) { restartApp(context as Activity) } } }
    }
    if (clearDialog) ClearDataDialog({ clearDialog = false }) { viewModel.clearAllData { restartApp(context as Activity) }; clearDialog = false }
}

@Composable private fun SettingsHero(accountCount: Int, categoryCount: Int) {
    Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)) { Icon(Icons.Default.Person, null, Modifier.padding(12.dp).size(28.dp), tint = MaterialTheme.colorScheme.onPrimary) }
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text("我的月迹", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary); Text("$accountCount 个账户 · $categoryCount 个分类 · 数据仅存本机", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)) }
        }
    }
}

@Composable private fun SettingsHeader(text: String) { Text(text, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
@Composable private fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) { val actual = color ?: LocalContentColor.current; ListItem(modifier = Modifier.clickable(onClick = onClick), leadingContent = { Icon(icon, null, tint = actual) }, headlineContent = { Text(title, color = actual) }, supportingContent = { Text(subtitle) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }) }
@Composable private fun SwitchItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) { ListItem(leadingContent = { Icon(icon, null) }, headlineContent = { Text(title) }, supportingContent = { Text(subtitle) }, trailingContent = { Switch(value, onChange) }) }

@Composable private fun AccountManagerRow(account: AccountEntity, canMoveUp: Boolean, canMoveDown: Boolean, onEdit: () -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onEdit),
        headlineContent = { Text(account.name) },
        supportingContent = { Text("${account.accountType.name} · ${if (account.includeInAssets) "计入总资产" else "不计入总资产"}${if (account.isArchived) " · 已归档" else ""}") },
        trailingContent = { Row { IconButton(onMoveUp, enabled = canMoveUp) { Icon(Icons.Default.KeyboardArrowUp, "上移") }; IconButton(onMoveDown, enabled = canMoveDown) { Icon(Icons.Default.KeyboardArrowDown, "下移") }; IconButton(onEdit) { Icon(Icons.Default.Edit, "编辑账户") } } },
    )
}

@Composable private fun DefaultAccountSetting(selectedId: String?, accounts: List<AccountEntity>, onChange: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListItem(
            modifier = Modifier.clickable { open = true },
            leadingContent = { Icon(Icons.Default.Payments, null) },
            headlineContent = { Text("默认付款账户") },
            supportingContent = { Text(accounts.firstOrNull { it.id == selectedId }?.name ?: "跟随最近使用") },
            trailingContent = { Icon(Icons.Default.UnfoldMore, null) },
        )
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text("跟随最近使用") }, { onChange(null); open = false }, leadingIcon = { if (selectedId == null) Icon(Icons.Default.Check, null) })
            accounts.forEach { account -> DropdownMenuItem({ Text(account.name) }, { onChange(account.id); open = false }, leadingIcon = { if (selectedId == account.id) Icon(Icons.Default.Check, null) }) }
        }
    }
}

@Composable private fun FiscalSetting(value: Int, onChange: (Int) -> Unit) { var open by remember { mutableStateOf(false) }; ListItem(modifier = Modifier.clickable { open = true }, leadingContent = { Icon(Icons.Default.DateRange, null) }, headlineContent = { Text("统计周期") }, supportingContent = { Text(if (value == 1) "自然年（1—12 月）" else "$value 月至次年 ${value - 1} 月") }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }); if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("财务年起始月") }, text = { Column { Text("当前：$value 月"); Slider(value.toFloat(), { onChange(it.toInt()) }, valueRange = 1f..12f, steps = 10) } }, confirmButton = { TextButton({ open = false }) { Text("完成") } }) }
@Composable private fun ThemeSetting(value: ThemeMode, onChange: (ThemeMode) -> Unit) { var open by remember { mutableStateOf(false) }; ListItem(modifier = Modifier.clickable { open = true }, leadingContent = { Icon(Icons.Default.DarkMode, null) }, headlineContent = { Text("主题") }, supportingContent = { Text(when (value) { ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色" }) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }); DropdownMenu(open, { open = false }) { ThemeMode.entries.forEach { mode -> DropdownMenuItem({ Text(when (mode) { ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色" }) }, { onChange(mode); open = false }) } } }

@Composable private fun CategoryManager(items: List<CategoryEntity>, viewModel: MainViewModel) { var add by remember { mutableStateOf(false) }; Column(Modifier.padding(horizontal = 16.dp)) { items.filter { !it.isArchived }.take(8).forEach { Text("${if (it.transactionDirection == TransactionDirection.EXPENSE) "支" else "收"} · ${it.name}", Modifier.padding(8.dp)) }; if (items.size > 8) Text("另有 ${items.size - 8} 个分类", color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton({ add = true }) { Icon(Icons.Default.Add, null); Text("添加分类") } }; if (add) { var name by remember { mutableStateOf("") }; var direction by remember { mutableStateOf(TransactionDirection.EXPENSE) }; AlertDialog(onDismissRequest = { add = false }, title = { Text("添加分类") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("名称") }); Row { FilterChip(direction == TransactionDirection.EXPENSE, { direction = TransactionDirection.EXPENSE }, { Text("支出") }); Spacer(Modifier.width(8.dp)); FilterChip(direction == TransactionDirection.INCOME, { direction = TransactionDirection.INCOME }, { Text("收入") }) } } }, confirmButton = { TextButton({ if (name.isNotBlank()) { viewModel.saveCategory(null, name, direction); add = false } }) { Text("保存") } }, dismissButton = { TextButton({ add = false }) { Text("取消") } }) } }

@Composable private fun AccountDialog(account: AccountEntity?, onDismiss: () -> Unit, onSave: (String?, String, AccountType, Long, Boolean, Boolean) -> Unit) { var name by remember { mutableStateOf(account?.name.orEmpty()) }; var opening by remember { mutableStateOf(account?.openingBalanceMinor?.let { java.math.BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()) }; var type by remember { mutableStateOf(account?.accountType ?: AccountType.BANK) }; var include by remember { mutableStateOf(account?.includeInAssets ?: true) }; var negative by remember { mutableStateOf(account?.allowNegativeBalance ?: false) }; var menu by remember { mutableStateOf(false) }; AlertDialog(onDismissRequest = onDismiss, title = { Text(if (account == null) "添加账户" else "编辑账户") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("名称") }); Spacer(Modifier.height(8.dp)); OutlinedTextField(opening, { opening = it }, label = { Text("期初余额（元，可为负）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); Box { TextButton({ menu = true }) { Text("类型：${type.name}") }; DropdownMenu(menu, { menu = false }) { AccountType.entries.forEach { item -> DropdownMenuItem({ Text(item.name) }, { type = item; menu = false }) } } }; SwitchLine("计入总资产", include) { include = it }; SwitchLine("允许负余额", negative) { negative = it } } }, confirmButton = { TextButton({ val amount = runCatching { Money.parse(opening.ifBlank { "0" }).minor }.getOrNull(); if (name.isNotBlank() && amount != null) onSave(account?.id, name, type, amount, include, negative) }) { Text("保存") } }, dismissButton = { TextButton(onDismiss) { Text("取消") } }) }
@Composable private fun SwitchLine(text: String, value: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(text, Modifier.weight(1f)); Switch(value, onChange) } }
@Composable private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) }, confirmButton = { TextButton(onDismiss) { Text("知道了") } }) }
@Composable private fun PasswordDialog(title: String, subtitle: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) { var value by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column { Text(subtitle); OutlinedTextField(value, { value = it }, label = { Text("密码") }) } }, confirmButton = { TextButton({ onConfirm(value) }) { Text("继续") } }, dismissButton = { TextButton(onDismiss) { Text("取消") } }) }
@Composable private fun ClearDataDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) { var text by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("清空全部数据") }, text = { Column { Text("此操作不可撤销。请输入“清空月迹”以确认。"); OutlinedTextField(text, { text = it }, label = { Text("确认词") }) } }, confirmButton = { TextButton(onConfirm, enabled = text == "清空月迹") { Text("永久清空", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onDismiss) { Text("取消") } }) }

private fun restartApp(activity: Activity) { val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK); activity.startActivity(intent); activity.finishAffinity(); Runtime.getRuntime().exit(0) }
