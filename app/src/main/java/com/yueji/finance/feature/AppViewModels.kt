package com.yueji.finance.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yueji.finance.core.database.*
import com.yueji.finance.core.model.*
import com.yueji.finance.data.*
import com.yueji.finance.domain.ForecastEngine
import com.yueji.finance.domain.InsightEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import android.net.Uri
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

data class OnboardingUiState(
    val step: Int = 0,
    val importHistory: Boolean = true,
    val fiscalYearStartMonth: Int = 9,
    val saveRecommendedGoals: Boolean = true,
    val working: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val finance: FinanceRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state = _state.asStateFlow()
    fun choose(importHistory: Boolean) { _state.update { it.copy(importHistory = importHistory, step = 1) } }
    fun fiscalMonth(month: Int) { _state.update { it.copy(fiscalYearStartMonth = month.coerceIn(1, 12)) } }
    fun goals(enabled: Boolean) { _state.update { it.copy(saveRecommendedGoals = enabled) } }
    fun next() { _state.update { it.copy(step = (it.step + 1).coerceAtMost(3)) } }
    fun back() { _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) } }
    fun finish() = viewModelScope.launch {
        _state.update { it.copy(working = true, error = null) }
        runCatching {
            val current = _state.value
            if (current.importHistory) finance.importLegacyData(current.saveRecommendedGoals) else finance.initializeBlankBook()
            settings.setFiscalYearStartMonth(current.fiscalYearStartMonth)
            settings.completeOnboarding()
        }.onFailure { error -> _state.update { it.copy(working = false, error = error.message) } }
    }
}

data class MainUiState(
    val month: YearMonth = YearMonth.now(),
    val dashboard: MonthlyDashboard = MonthlyDashboard(YearMonth.now()),
    val forecast: Forecast = Forecast(0, 0, "较低", "暂无数据"),
    val insights: List<Insight> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = true,
)

data class AdvancedTransactionFilter(
    val categoryId: String? = null,
    val accountId: String? = null,
    val tag: String? = null,
    val necessity: Necessity? = null,
    val variability: Variability? = null,
    val oneOff: Boolean? = null,
    val reimbursable: Boolean? = null,
    val source: TransactionSource? = null,
    val minimumMinor: Long? = null,
    val maximumMinor: Long? = null,
) {
    val activeCount get() = listOf(categoryId, accountId, tag, necessity, variability, oneOff, reimbursable, source, minimumMinor, maximumMinor).count { it != null && it != "" }
}

enum class UndoMode { DELETE, RESTORE }
sealed interface UserMessage {
    data class Info(val text: String, val undoTransactionId: String? = null, val undoMode: UndoMode? = null) : UserMessage
    data class Error(val text: String) : UserMessage
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val finance: FinanceRepository,
    private val settingsRepository: SettingsRepository,
    private val importExport: ImportExportService,
    private val backupService: BackupService,
    private val reminderScheduler: ReminderScheduler,
    private val reportService: ReportService,
    private val attachmentService: AttachmentService,
) : ViewModel() {
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private val search = MutableStateFlow("")
    private val typeFilter = MutableStateFlow<Set<TransactionType>>(emptySet())
    private val merchantSearch = MutableStateFlow("")
    private val advanced = MutableStateFlow(AdvancedTransactionFilter())
    val advancedFilter = advanced.asStateFlow()
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val accounts = finance.observeAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = finance.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val budgets = finance.observeBudgets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val goals = finance.observeGoals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val snapshots = finance.observeSnapshots().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val annualSummaries = finance.observeAnnualSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val merchantSuggestions = merchantSearch
        .debounce(120)
        .distinctUntilChanged()
        .flatMapLatest { finance.observeMerchantSuggestions(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val dashboard = combine(selectedMonth, settings) { month, prefs -> month to prefs.fiscalYearStartMonth }
        .flatMapLatest { (month, fiscal) -> finance.observeDashboard(month, fiscal) }

    val uiState = combine(selectedMonth, dashboard, settings) { month, data, prefs ->
        MainUiState(month, data, ForecastEngine.forDashboard(data), InsightEngine.generate(data, lastBackupEpochMillis = prefs.lastBackupEpochMillis), prefs, false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    val transactions = combine(selectedMonth, search, typeFilter, advanced) { month, query, types, more ->
        TransactionFilter(Periods.month(month), query, types, more.categoryId, more.accountId, tag = more.tag,
            necessity = more.necessity, variability = more.variability, oneOff = more.oneOff, reimbursable = more.reimbursable,
            source = more.source, minimumMinor = more.minimumMinor, maximumMinor = more.maximumMinor)
    }.flatMapLatest(finance::observeTransactions)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun previousMonth() { selectedMonth.update { it.minusMonths(1) } }
    fun nextMonth() { selectedMonth.update { it.plusMonths(1) } }
    fun selectMonth(month: YearMonth) { selectedMonth.value = month }
    fun search(value: String) { search.value = value }
    fun searchMerchants(value: String) { merchantSearch.value = value }
    fun filterType(type: TransactionType?) { typeFilter.value = type?.let(::setOf) ?: emptySet() }
    fun applyAdvancedFilter(value: AdvancedTransactionFilter) { advanced.value = value }

    fun saveTransaction(draft: TransactionDraft, attachmentUri: Uri? = null) = viewModelScope.launch {
        runCatching {
            if (draft.id == null) {
                val id = finance.addTransaction(draft)
                attachmentUri?.let { attachmentService.add(id, it) }
                _messages.emit(UserMessage.Info("已保存，2 秒内可撤销", id, UndoMode.DELETE))
            } else {
                finance.updateTransaction(draft); _messages.emit(UserMessage.Info("交易已更新"))
            }
        }.onFailure { _messages.emit(UserMessage.Error(it.message ?: "保存失败")) }
    }

    fun deleteTransaction(id: String) = viewModelScope.launch {
        runCatching { finance.deleteTransaction(id); _messages.emit(UserMessage.Info("交易已删除", id, UndoMode.RESTORE)) }
            .onFailure { _messages.emit(UserMessage.Error(it.message ?: "删除失败")) }
    }
    fun undoDelete(id: String) = viewModelScope.launch { finance.restoreTransaction(id) }

    fun saveMonthlyBudget(amountMinor: Long, rolloverMode: RolloverMode = RolloverMode.NONE) = viewModelScope.launch {
        runCatching { finance.setMonthlyBudget(selectedMonth.value, amountMinor, rolloverMode) }
            .onSuccess { _messages.emit(UserMessage.Info(if (rolloverMode == RolloverMode.NONE) "固定月预算已更新" else "动态月预算已更新")) }
            .onFailure { _messages.emit(UserMessage.Error(it.message ?: "月预算保存失败")) }
    }

    fun saveGoal(existing: GoalEntity?, type: GoalType, name: String, targetMinor: Long, targetRatioBasisPoints: Int? = null, date: LocalDate = LocalDate.now().plusYears(1)) = viewModelScope.launch {
        val now = System.currentTimeMillis(); val today = LocalDate.now()
        runCatching {
            require(name.isNotBlank()) { "目标名称不能为空" }
            require(date >= today || existing != null) { "截止日期不能早于今天" }
            if (type == GoalType.SAVINGS_RATE) require(targetRatioBasisPoints != null && targetRatioBasisPoints in 1..10_000) { "储蓄率应在 0% 到 100% 之间" }
            else require(targetMinor > 0) { "目标金额必须大于 0" }
            val period = when {
                type.name.startsWith("MONTHLY") -> PeriodType.MONTH
                type.name.startsWith("ANNUAL") -> PeriodType.NATURAL_YEAR
                else -> PeriodType.CUSTOM
            }
            finance.upsertGoal(
                GoalEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(), goalType = type, name = name.trim(), targetAmountMinor = targetMinor,
                    targetRatioBasisPoints = targetRatioBasisPoints, startEpochDay = existing?.startEpochDay ?: today.toEpochDay(), targetEpochDay = date.toEpochDay(),
                    periodType = period, fiscalYearStartMonth = existing?.fiscalYearStartMonth ?: settings.value.fiscalYearStartMonth,
                    isRecurring = existing?.isRecurring ?: false, status = existing?.status ?: "ACTIVE", reminderEnabled = existing?.reminderEnabled ?: true,
                    note = existing?.note, createdAtEpochMillis = existing?.createdAtEpochMillis ?: now, updatedAtEpochMillis = now,
                )
            )
        }.onSuccess { _messages.emit(UserMessage.Info(if (existing == null) "目标已创建" else "目标已更新")) }
            .onFailure { _messages.emit(UserMessage.Error(it.message ?: "目标保存失败")) }
    }
    fun deleteGoal(id: String) = viewModelScope.launch { finance.deleteGoal(id) }

    fun saveAccount(id: String?, name: String, type: AccountType, openingMinor: Long, includeAssets: Boolean, allowNegative: Boolean) = viewModelScope.launch {
        val now = System.currentTimeMillis(); val existing = id?.let { target -> accounts.value.firstOrNull { it.id == target } }
        val account = existing?.copy(name = name, accountType = type, openingBalanceMinor = openingMinor, includeInAssets = includeAssets,
            allowNegativeBalance = allowNegative, updatedAtEpochMillis = now)
            ?: AccountEntity(id ?: UUID.randomUUID().toString(), name = name, accountType = type,
                openingBalanceMinor = openingMinor, includeInAssets = includeAssets, allowNegativeBalance = allowNegative,
                sortOrder = (accounts.value.maxOfOrNull { it.sortOrder } ?: -1) + 1, createdAtEpochMillis = now, updatedAtEpochMillis = now)
        finance.upsertAccount(account)
        _messages.emit(UserMessage.Info("账户已保存"))
    }
    fun moveAccount(id: String, direction: Int) = viewModelScope.launch {
        val ordered = accounts.value.filter { !it.isArchived }.sortedBy { it.sortOrder }.map { it.id }.toMutableList()
        val from = ordered.indexOf(id); val to = (from + direction).coerceIn(0, ordered.lastIndex)
        if (from >= 0 && from != to) { val moved = ordered.removeAt(from); ordered.add(to, moved); finance.reorderAccounts(ordered) }
    }
    fun saveCategory(id: String?, name: String, direction: TransactionDirection) = viewModelScope.launch {
        finance.upsertCategory(CategoryEntity(id ?: UUID.randomUUID().toString(), name = name, transactionDirection = direction,
            iconKey = "category", defaultNecessity = Necessity.NECESSARY, defaultVariability = Variability.VARIABLE, isSystem = false))
        _messages.emit(UserMessage.Info("分类已保存"))
    }

    fun setAmountsHidden(value: Boolean) = viewModelScope.launch { settingsRepository.setAmountsHidden(value) }
    fun setTheme(value: ThemeMode) = viewModelScope.launch { settingsRepository.setTheme(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(value) }
    fun setDefaultAccount(id: String?) = viewModelScope.launch { settingsRepository.setDefaultAccount(id) }
    fun setFiscalMonth(value: Int) = viewModelScope.launch { settingsRepository.setFiscalYearStartMonth(value) }
    fun setHideInRecents(value: Boolean) = viewModelScope.launch { settingsRepository.setHideInRecents(value) }
    fun setAppLock(value: Boolean) = viewModelScope.launch { settingsRepository.setAppLock(value) }
    fun setReminder(value: Boolean, hour: Int = settings.value.reminderHour) = viewModelScope.launch { settingsRepository.setReminder(value, hour); reminderScheduler.schedule(value, hour) }

    fun importCsv(input: InputStream, name: String) = viewModelScope.launch {
        runCatching { importExport.importCsv(input, name) }.onSuccess { result ->
            val text = if (result.duplicateBatch) "该文件已经导入过" else "导入 ${result.added} 条，跳过 ${result.skipped} 条，错误 ${result.errors.size} 条" +
                result.errors.take(3).joinToString(prefix = if (result.errors.isEmpty()) "" else "\n") { "第 ${it.line} 行：${it.reason}" }
            _messages.emit(UserMessage.Info(text))
        }.onFailure { _messages.emit(UserMessage.Error(it.message ?: "导入失败")) }
    }
    fun exportCsv(output: OutputStream) = viewModelScope.launch {
        runCatching { importExport.exportCsv(output) }.onSuccess { _messages.emit(UserMessage.Info("CSV 已导出，请妥善保管")) }
            .onFailure { _messages.emit(UserMessage.Error(it.message ?: "导出失败")) }
    }
    fun createBackup(output: OutputStream, password: CharArray? = null) = viewModelScope.launch {
        runCatching { backupService.create(output, password) }.onSuccess { _messages.emit(UserMessage.Info("完整备份已创建，请妥善保管")) }
            .onFailure { _messages.emit(UserMessage.Error(it.message ?: "备份失败")) }
    }
    fun exportMonthlyReport(output: OutputStream) = viewModelScope.launch {
        runCatching { reportService.monthly(output, uiState.value.dashboard) }.onSuccess { _messages.emit(UserMessage.Info("月报 PDF 已导出")) }
            .onFailure { _messages.emit(UserMessage.Error(it.message ?: "月报导出失败")) }
    }
    fun exportAnnualReport(output: OutputStream) = viewModelScope.launch {
        runCatching { reportService.annual(output, annualSummaries.value) }.onSuccess { _messages.emit(UserMessage.Info("年报 PDF 已导出")) }
            .onFailure { _messages.emit(UserMessage.Error(it.message ?: "年报导出失败")) }
    }
    fun restoreBackup(input: InputStream, password: CharArray? = null, onRestored: () -> Unit) = viewModelScope.launch {
        runCatching { backupService.restore(input, password) }.onSuccess { _messages.emit(UserMessage.Info("恢复完成，即将重启应用")); onRestored() }
            .onFailure { _messages.emit(UserMessage.Error(it.message ?: "恢复失败")) }
    }
    fun clearAllData(onCleared: () -> Unit) = viewModelScope.launch {
        runCatching { finance.clearAllData(); settingsRepository.reset() }.onSuccess { onCleared() }
            .onFailure { _messages.emit(UserMessage.Error(it.message ?: "清空失败")) }
    }
}
