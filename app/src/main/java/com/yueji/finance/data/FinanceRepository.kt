package com.yueji.finance.data

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.yueji.finance.core.database.*
import com.yueji.finance.core.model.*
import kotlinx.coroutines.flow.*
import java.security.MessageDigest
import java.text.Normalizer
import java.time.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class TransactionDraft(
    val id: String? = null,
    val type: TransactionType,
    val amountMinor: Long,
    val date: LocalDate,
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val accountId: String,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val merchantName: String? = null,
    val note: String? = null,
    val necessity: Necessity? = null,
    val variability: Variability? = null,
    val isOneOff: Boolean = false,
    val isReimbursable: Boolean = false,
    val excludeFromBudget: Boolean = false,
    val linkedTransactionId: String? = null,
    val balanceDirection: Int = 1,
    val source: TransactionSource = TransactionSource.MANUAL,
    val status: TransactionStatus = TransactionStatus.CONFIRMED,
    val tags: List<String> = emptyList(),
    val createMonthlyReminder: Boolean = false,
    val recordGranularity: RecordGranularity = RecordGranularity.DAY,
)

data class TransactionFilter(
    val range: DateRange,
    val search: String = "",
    val types: Set<TransactionType> = emptySet(),
    val categoryId: String? = null,
    val accountId: String? = null,
    val merchant: String? = null,
    val tag: String? = null,
    val necessity: Necessity? = null,
    val variability: Variability? = null,
    val oneOff: Boolean? = null,
    val reimbursable: Boolean? = null,
    val source: TransactionSource? = null,
    val minimumMinor: Long? = null,
    val maximumMinor: Long? = null,
)

interface FinanceRepository {
    fun observeAccounts(): Flow<List<AccountEntity>>
    fun observeCategories(): Flow<List<CategoryEntity>>
    fun observeMerchantSuggestions(query: String, limit: Int = 8): Flow<List<MerchantEntity>>
    fun observeTransactions(filter: TransactionFilter): Flow<List<TransactionListRow>>
    fun observeDashboard(month: YearMonth, fiscalYearStartMonth: Int = 9): Flow<MonthlyDashboard>
    fun observeBudgets(): Flow<List<BudgetEntity>>
    fun observeGoals(): Flow<List<GoalEntity>>
    fun observeSnapshots(): Flow<List<BalanceSnapshotEntity>>
    fun observeAnnualSummaries(): Flow<List<LegacyAnnualSummaryEntity>>
    suspend fun addTransaction(draft: TransactionDraft): String
    suspend fun updateTransaction(draft: TransactionDraft)
    suspend fun deleteTransaction(id: String)
    suspend fun restoreTransaction(id: String)
    suspend fun upsertAccount(account: AccountEntity)
    suspend fun reorderAccounts(orderedIds: List<String>)
    suspend fun upsertCategory(category: CategoryEntity)
    suspend fun upsertBudget(budget: BudgetEntity)
    suspend fun setMonthlyBudget(month: YearMonth, amountMinor: Long, rolloverMode: RolloverMode = RolloverMode.NONE)
    suspend fun deleteBudget(id: String)
    suspend fun upsertGoal(goal: GoalEntity)
    suspend fun deleteGoal(id: String)
    suspend fun initializeBlankBook()
    suspend fun importLegacyData(withRecommendedGoals: Boolean = true)
    suspend fun transactionCount(): Int
    suspend fun clearAllData()
}

@Singleton
class OfflineFinanceRepository @Inject constructor(
    private val db: YueJiDatabase,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantDao,
    private val transactionDao: TransactionDao,
    private val planningDao: PlanningDao,
    private val historyDao: HistoryDao,
    private val importDao: ImportDao,
) : FinanceRepository {
    override fun observeAccounts() = accountDao.observeAll()
    override fun observeCategories() = categoryDao.observeActive()
    override fun observeMerchantSuggestions(query: String, limit: Int): Flow<List<MerchantEntity>> {
        val display = query.trim()
        return merchantDao.observeSuggestions(normalizeMerchantName(display), display, limit.coerceIn(1, 20))
    }
    override fun observeBudgets() = planningDao.observeBudgets()
    override fun observeGoals() = planningDao.observeGoals()
    override fun observeSnapshots() = historyDao.observeSnapshots()
    override fun observeAnnualSummaries() = historyDao.observeAnnualSummaries()

    override fun observeTransactions(filter: TransactionFilter): Flow<List<TransactionListRow>> {
        val conditions = mutableListOf("t.status != 'DELETED'", "t.localDateEpochDay BETWEEN ? AND ?")
        val args = mutableListOf<Any>(filter.range.startEpochDay, filter.range.endEpochDay)
        if (filter.search.isNotBlank()) {
            conditions += "(COALESCE(t.merchantName,'') LIKE ? OR COALESCE(t.note,'') LIKE ? OR a.name LIKE ? OR COALESCE(c.name,'') LIKE ?)"
            repeat(4) { args += "%${filter.search.trim()}%" }
        }
        if (filter.types.isNotEmpty()) {
            conditions += "t.type IN (${filter.types.joinToString { "?" }})"
            args.addAll(filter.types.map { it.name })
        }
        fun add(column: String, value: Any?) { if (value != null) { conditions += "$column = ?"; args += value } }
        add("t.categoryId", filter.categoryId); add("t.merchantName", filter.merchant)
        if (!filter.tag.isNullOrBlank()) { conditions += "EXISTS(SELECT 1 FROM transaction_tags tt JOIN tags tg ON tg.id = tt.tagId WHERE tt.transactionId = t.id AND tg.name = ?)"; args += filter.tag }
        if (filter.accountId != null) { conditions += "(t.accountId = ? OR t.destinationAccountId = ?)"; args += filter.accountId; args += filter.accountId }
        add("t.necessity", filter.necessity?.name); add("t.variability", filter.variability?.name)
        add("t.isOneOff", filter.oneOff?.let { if (it) 1 else 0 }); add("t.isReimbursable", filter.reimbursable?.let { if (it) 1 else 0 })
        add("t.source", filter.source?.name); filter.minimumMinor?.let { conditions += "t.amountMinor >= ?"; args += it }
        filter.maximumMinor?.let { conditions += "t.amountMinor <= ?"; args += it }
        val sql = """SELECT t.id, t.type, t.amountMinor, t.occurredAtEpochMillis, t.localDateEpochDay,
            t.accountId, a.name AS accountName, t.destinationAccountId, da.name AS destinationAccountName,
            t.categoryId, c.name AS categoryName, c.iconKey AS categoryIconKey, t.merchantName, t.note, t.necessity, t.variability,
            t.isOneOff, t.isReimbursable, t.excludeFromBudget, t.status, t.source,
            t.recordGranularity, t.periodStartEpochDay, t.periodEndEpochDay,
            (SELECT GROUP_CONCAT(tg.name, ',') FROM transaction_tags tt JOIN tags tg ON tg.id = tt.tagId WHERE tt.transactionId = t.id) AS tagsCsv
            FROM transactions t JOIN accounts a ON a.id=t.accountId
            LEFT JOIN accounts da ON da.id=t.destinationAccountId LEFT JOIN categories c ON c.id=t.categoryId
            WHERE ${conditions.joinToString(" AND ")} ORDER BY t.occurredAtEpochMillis DESC"""
        return transactionDao.observeFiltered(SimpleSQLiteQuery(sql, args.toTypedArray()))
    }

    override fun observeDashboard(month: YearMonth, fiscalYearStartMonth: Int): Flow<MonthlyDashboard> {
        val range = Periods.month(month)
        val previousRange = Periods.month(month.minusMonths(1))
        val fiscalRange = Periods.financialYear(month.atDay(1), fiscalYearStartMonth).range
        val budgetFlow = planningDao.observeBudgetsFor(range.startEpochDay, range.endEpochDay)
        return combine(
            transactionDao.observeSummary(range.startEpochDay, range.endEpochDay),
            transactionDao.observeExpenseByCategory(range.startEpochDay, range.endEpochDay),
            transactionDao.observeDailyExpense(range.startEpochDay, range.endEpochDay),
            accountDao.observeBalances(), budgetFlow, planningDao.observeGoals(), historyDao.observeAnnualSummaries(),
            transactionDao.observeSummary(fiscalRange.startEpochDay, fiscalRange.endEpochDay),
            transactionDao.observeSummary(previousRange.startEpochDay, previousRange.endEpochDay),
        ) { values ->
            val summary = values[0] as PeriodSummaryRow
            @Suppress("UNCHECKED_CAST") val categories = values[1] as List<CategoryTotalRow>
            @Suppress("UNCHECKED_CAST") val daily = values[2] as List<DailyTotalRow>
            @Suppress("UNCHECKED_CAST") val balances = values[3] as List<AccountBalanceRow>
            @Suppress("UNCHECKED_CAST") val budgets = values[4] as List<BudgetEntity>
            @Suppress("UNCHECKED_CAST") val goals = values[5] as List<GoalEntity>
            @Suppress("UNCHECKED_CAST") val legacy = values[6] as List<LegacyAnnualSummaryEntity>
            val annualSummary = values[7] as PeriodSummaryRow
            val previousSummary = values[8] as PeriodSummaryRow
            val overallBudget = budgets.firstOrNull { it.id == "monthly_budget" }
                ?: budgets.firstOrNull { it.categoryId == null && it.periodType == PeriodType.MONTH }
            val previousExpense = (previousSummary.expenseMinor - previousSummary.refundMinor).coerceAtLeast(0)
            val hasPreviousExpenseData = previousSummary.expenseCount > 0
            val baseBudget = overallBudget?.targetAmountMinor
            val budgetCalculation = baseBudget?.let {
                BudgetMath.calculate(it, previousExpense.takeIf { hasPreviousExpenseData }, overallBudget?.rolloverMode ?: RolloverMode.NONE)
            }
            val budgetAdjustment = budgetCalculation?.adjustmentMinor ?: 0L
            val effectiveBudget = budgetCalculation?.effectiveMinor
            MonthlyDashboard(
                month = month, expenseMinor = summary.expenseMinor, incomeMinor = summary.incomeMinor,
                refundMinor = summary.refundMinor, budgetMinor = effectiveBudget,
                categories = categories.map { CategoryTotal(it.id, it.name ?: "未分类", it.amountMinor) }.filter { it.amountMinor > 0 },
                daily = daily.map { DailyTotal(it.epochDay, it.amountMinor) },
                accounts = balances.map { AccountBalance(it.id, it.name, it.amountMinor, it.accountType, it.includeInAssets) },
                goals = goals.map { goal -> GoalProgress(goal.id, goal.name, goal.goalType, goalCurrent(goal, summary, annualSummary, balances), goal.targetAmountMinor, goal.targetRatioBasisPoints) },
                hasTransactions = summary.transactionCount > 0,
                hasLegacySummaryOnly = summary.transactionCount == 0 && legacy.any { monthCode(month) in it.periodStartYearMonth..it.periodEndYearMonth },
                hasAggregateRecords = summary.aggregateCount > 0,
                hasExpenseAggregateRecords = summary.expenseAggregateCount > 0,
                hasIncomeAggregateRecords = summary.incomeAggregateCount > 0,
                baseBudgetMinor = baseBudget,
                budgetAdjustmentMinor = budgetAdjustment,
                budgetRolloverMode = overallBudget?.rolloverMode ?: RolloverMode.NONE,
                previousMonthExpenseMinor = previousExpense.takeIf { hasPreviousExpenseData },
            )
        }
    }

    private fun goalCurrent(goal: GoalEntity, summary: PeriodSummaryRow, annual: PeriodSummaryRow, balances: List<AccountBalanceRow>): Long = when (goal.goalType) {
        GoalType.MONTHLY_EXPENSE, GoalType.CATEGORY_BUDGET -> summary.expenseMinor - summary.refundMinor
        GoalType.ANNUAL_EXPENSE -> annual.expenseMinor - annual.refundMinor
        GoalType.MONTHLY_INCOME -> summary.incomeMinor
        GoalType.ANNUAL_INCOME -> annual.incomeMinor
        GoalType.MONTHLY_SAVINGS -> summary.incomeMinor - summary.expenseMinor + summary.refundMinor
        GoalType.ANNUAL_SAVINGS -> annual.incomeMinor - annual.expenseMinor + annual.refundMinor
        GoalType.ASSET_BALANCE, GoalType.EMERGENCY_FUND -> balances.filter { it.includeInAssets }.sumOf { it.amountMinor }
        else -> summary.incomeMinor - summary.expenseMinor + summary.refundMinor
    }

    override suspend fun addTransaction(draft: TransactionDraft): String {
        validateDraft(draft)
        val id = draft.id ?: UUID.randomUUID().toString()
        val ruleId = if (draft.createMonthlyReminder) UUID.randomUUID().toString() else null
        db.withTransaction {
            val merchant = rememberMerchant(draft.merchantName.takeIf { draft.type !in setOf(TransactionType.TRANSFER, TransactionType.BALANCE_ADJUSTMENT) }, draft.categoryId)
            val entity = draft.toEntity(id).copy(recurringRuleId = ruleId, merchantId = merchant?.id)
            transactionDao.insert(entity); saveTags(id, draft.tags)
            if (ruleId != null) planningDao.upsertRecurringRule(RecurringRuleEntity(ruleId, id, "MONTHLY", dayOfMonth = draft.date.dayOfMonth,
                startEpochDay = draft.date.toEpochDay(), nextOccurrenceEpochDay = draft.date.plusMonths(1).toEpochDay()))
        }
        return id
    }

    override suspend fun updateTransaction(draft: TransactionDraft) {
        requireNotNull(draft.id); validateDraft(draft)
        val previous = requireNotNull(transactionDao.byId(draft.id))
        db.withTransaction {
            val merchant = rememberMerchant(draft.merchantName.takeIf { draft.type !in setOf(TransactionType.TRANSFER, TransactionType.BALANCE_ADJUSTMENT) }, draft.categoryId)
            transactionDao.update(draft.toEntity(draft.id, previous.createdAtEpochMillis).copy(merchantId = merchant?.id))
            importDao.clearTransactionTags(draft.id)
            saveTags(draft.id, draft.tags)
        }
    }

    override suspend fun deleteTransaction(id: String) = transactionDao.softDelete(id, System.currentTimeMillis())
    override suspend fun restoreTransaction(id: String) = transactionDao.restore(id, System.currentTimeMillis())
    override suspend fun upsertAccount(account: AccountEntity) = accountDao.upsert(account)
    override suspend fun reorderAccounts(orderedIds: List<String>) = db.withTransaction {
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id -> accountDao.updateSortOrder(id, index, now) }
    }
    override suspend fun upsertCategory(category: CategoryEntity) = categoryDao.upsert(category)
    override suspend fun upsertBudget(budget: BudgetEntity) = planningDao.upsertBudget(budget)
    override suspend fun setMonthlyBudget(month: YearMonth, amountMinor: Long, rolloverMode: RolloverMode) {
        require(amountMinor > 0) { "月预算必须大于 0" }
        val range = Periods.month(month)
        db.withTransaction {
            planningDao.deleteLegacyMonthlyBudgets()
            planningDao.upsertBudget(
                BudgetEntity(
                    id = "monthly_budget",
                    name = "月消费目标",
                    periodType = PeriodType.MONTH,
                    startEpochDay = range.startEpochDay,
                    endEpochDay = range.endEpochDay,
                    targetAmountMinor = amountMinor,
                    rolloverMode = rolloverMode,
                    isRecurring = true,
                )
            )
        }
    }
    override suspend fun deleteBudget(id: String) = planningDao.deleteBudget(id)
    override suspend fun upsertGoal(goal: GoalEntity) = planningDao.upsertGoal(goal)
    override suspend fun deleteGoal(id: String) = planningDao.deleteGoal(id)
    override suspend fun transactionCount() = transactionDao.count()
    override suspend fun clearAllData() = db.clearAllTables()

    override suspend fun initializeBlankBook() = db.withTransaction {
        if (categoryDao.active(TransactionDirection.EXPENSE).isEmpty()) categoryDao.upsertAll(SeedData.categories())
        if (accountDao.active().isEmpty()) {
            val now = System.currentTimeMillis()
            accountDao.upsert(AccountEntity("cash", "现金", accountType = AccountType.CASH, createdAtEpochMillis = now, updatedAtEpochMillis = now))
        }
    }

    override suspend fun importLegacyData(withRecommendedGoals: Boolean) = db.withTransaction {
        SeedData.validateHistoricalData()
        val now = System.currentTimeMillis()
        categoryDao.upsertAll(SeedData.categories())
        accountDao.upsertAll(SeedData.accounts(now))
        historyDao.upsertSnapshots(SeedData.snapshots(now))
        historyDao.upsertSummaries(SeedData.annualSummaries())
        if (withRecommendedGoals) {
            val today = LocalDate.now(); val month = YearMonth.from(today); val monthRange = Periods.month(month)
            val fiscal = Periods.financialYear(today, 9)
            planningDao.upsertBudget(BudgetEntity("monthly_budget", "月消费目标", PeriodType.MONTH, monthRange.startEpochDay, monthRange.endEpochDay, 500_000L))
            listOf(
                GoalEntity("annual_expense", GoalType.ANNUAL_EXPENSE, "年消费目标", 6_000_000L, startEpochDay = fiscal.range.startEpochDay, targetEpochDay = fiscal.range.endEpochDay, periodType = PeriodType.FISCAL_YEAR, isRecurring = true, createdAtEpochMillis = now, updatedAtEpochMillis = now),
                GoalEntity("monthly_savings", GoalType.MONTHLY_SAVINGS, "月结余目标", 600_000L, startEpochDay = monthRange.startEpochDay, targetEpochDay = monthRange.endEpochDay, periodType = PeriodType.MONTH, isRecurring = true, createdAtEpochMillis = now, updatedAtEpochMillis = now),
                GoalEntity("annual_savings", GoalType.ANNUAL_SAVINGS, "年结余目标", 7_200_000L, startEpochDay = fiscal.range.startEpochDay, targetEpochDay = fiscal.range.endEpochDay, periodType = PeriodType.FISCAL_YEAR, isRecurring = true, createdAtEpochMillis = now, updatedAtEpochMillis = now),
                GoalEntity("savings_rate", GoalType.SAVINGS_RATE, "年储蓄率目标", targetRatioBasisPoints = 6000, startEpochDay = fiscal.range.startEpochDay, targetEpochDay = fiscal.range.endEpochDay, periodType = PeriodType.FISCAL_YEAR, isRecurring = true, createdAtEpochMillis = now, updatedAtEpochMillis = now),
                GoalEntity("asset_300k", GoalType.ASSET_BALANCE, "30 万资产目标", 30_000_000L, startEpochDay = today.toEpochDay(), targetEpochDay = today.plusYears(1).toEpochDay(), periodType = PeriodType.CUSTOM, createdAtEpochMillis = now, updatedAtEpochMillis = now),
            ).forEach { planningDao.upsertGoal(it) }
        }
        // 历史导入只写快照和年度汇总，绝不生成月度交易。
        check(transactionDao.count() == 0) { "历史导入不得生成虚假月度流水" }
    }

    private fun validateDraft(draft: TransactionDraft) {
        require(draft.amountMinor > 0) { "金额必须大于 0" }
        require(draft.balanceDirection == 1 || draft.balanceDirection == -1) { "调整方向无效" }
        if (draft.type == TransactionType.TRANSFER) {
            require(!draft.destinationAccountId.isNullOrBlank()) { "转账必须选择转入账户" }
            require(draft.accountId != draft.destinationAccountId) { "转入和转出账户不能相同" }
        }
        if (draft.type in setOf(TransactionType.EXPENSE, TransactionType.INCOME)) require(!draft.categoryId.isNullOrBlank()) { "请选择分类" }
        if (draft.type !in setOf(TransactionType.EXPENSE, TransactionType.INCOME)) require(draft.recordGranularity == RecordGranularity.DAY) { "只有支出和收入支持周期汇总补录" }
    }

    private suspend fun saveTags(transactionId: String, names: List<String>) {
        val normalized = names.map(String::trim).filter(String::isNotEmpty).distinct().take(12)
        val tags = normalized.map { name -> TagEntity("tag_${sha256Text(name.lowercase()).take(20)}", name) }
        importDao.upsertTags(tags); importDao.insertTransactionTags(tags.map { TransactionTagCrossRef(transactionId, it.id) })
    }

    private suspend fun rememberMerchant(rawName: String?, categoryId: String?): MerchantEntity? {
        val displayName = rawName?.trim()?.replace(Regex("\\s+"), " ")?.takeIf(String::isNotEmpty) ?: return null
        val normalizedName = normalizeMerchantName(displayName)
        val existing = merchantDao.byNormalizedName(normalizedName)
        val entity = MerchantEntity(
            id = existing?.id ?: "merchant_${sha256Text(normalizedName).take(20)}",
            normalizedName = normalizedName,
            displayName = displayName,
            defaultCategoryId = categoryId ?: existing?.defaultCategoryId,
            lastUsedAtEpochMillis = System.currentTimeMillis(),
            useCount = (existing?.useCount ?: 0) + 1,
        )
        merchantDao.upsert(entity)
        return entity
    }

    private fun normalizeMerchantName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().lowercase().replace(Regex("\\s+"), " ")

    private fun sha256Text(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun TransactionDraft.toEntity(id: String, createdAt: Long = System.currentTimeMillis()): TransactionEntity {
        val zone = ZoneId.systemDefault(); val instant = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
        val period = RecordPeriods.range(recordGranularity, date)
        val canonical = listOf(date, time, amountMinor, type, accountId, merchantName.orEmpty(), note.orEmpty(), recordGranularity, period.start, period.endInclusive, id).joinToString("|")
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
        return TransactionEntity(id, type, amountMinor, occurredAtEpochMillis = instant, localDateEpochDay = date.toEpochDay(),
            accountId = accountId,
            destinationAccountId = destinationAccountId.takeIf { type == TransactionType.TRANSFER },
            categoryId = categoryId.takeIf { type in setOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.REFUND) },
            merchantName = merchantName?.trim()?.takeIf { type !in setOf(TransactionType.TRANSFER, TransactionType.BALANCE_ADJUSTMENT) && it.isNotEmpty() },
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            necessity = necessity, variability = variability, isOneOff = isOneOff, isReimbursable = isReimbursable,
            excludeFromBudget = excludeFromBudget, linkedTransactionId = linkedTransactionId, source = source, status = status,
            balanceDirection = balanceDirection, fingerprint = fingerprint, createdAtEpochMillis = createdAt, updatedAtEpochMillis = System.currentTimeMillis(),
            recordGranularity = recordGranularity, periodStartEpochDay = period.startEpochDay, periodEndEpochDay = period.endEpochDay)
    }

    private fun monthCode(month: YearMonth) = month.year * 100 + month.monthValue
}
