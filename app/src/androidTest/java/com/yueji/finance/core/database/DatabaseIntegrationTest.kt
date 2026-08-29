package com.yueji.finance.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yueji.finance.core.model.*
import com.yueji.finance.data.OfflineFinanceRepository
import com.yueji.finance.data.TransactionDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DatabaseIntegrationTest {
    private lateinit var db: YueJiDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), YueJiDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()

    @Test fun aggregatesExcludeTransferAndAdjustmentButRefundOffsetsExpense() = runTest {
        val now = System.currentTimeMillis(); val day = LocalDate.of(2026, 8, 4).toEpochDay()
        db.accountDao().upsertAll(listOf(account("a", now, 100_000), account("b", now, 0)))
        db.transactionDao().insert(tx("expense", TransactionType.EXPENSE, 50_000, "a", day))
        db.transactionDao().insert(tx("income", TransactionType.INCOME, 120_000, "a", day))
        db.transactionDao().insert(tx("transfer", TransactionType.TRANSFER, 30_000, "a", day, "b"))
        db.transactionDao().insert(tx("refund", TransactionType.REFUND, 10_000, "a", day))
        db.transactionDao().insert(tx("adjust", TransactionType.BALANCE_ADJUSTMENT, 5_000, "a", day, direction = -1))
        val summary = db.transactionDao().observeSummary(day, day).first()
        assertEquals(120_000L, summary.incomeMinor); assertEquals(50_000L, summary.expenseMinor); assertEquals(10_000L, summary.refundMinor)
        val balances = db.accountDao().observeBalances().first().associateBy { it.id }
        assertEquals(145_000L, balances.getValue("a").amountMinor) // 1000 - 500 + 1200 - 300 + 100 - 50 yuan
        assertEquals(30_000L, balances.getValue("b").amountMinor)
    }

    @Test fun snapshotUniqueIndexRejectsDuplicateMonthAccount() = runTest {
        db.accountDao().upsert(account("a", 0, 0)); val item = BalanceSnapshotEntity("one", "a", 202608, 1, createdAtEpochMillis = 0)
        db.historyDao().upsertSnapshots(listOf(item)); db.historyDao().upsertSnapshots(listOf(item.copy(id = "two", amountMinor = 2)))
        val rows = db.historyDao().observeSnapshots().first(); assertEquals(1, rows.size); assertEquals(2L, rows.single().amountMinor)
    }

    @Test fun tenThousandTransactionsAggregateCorrectly() = runTest {
        db.accountDao().upsert(account("a", 0, 0)); val dao = db.transactionDao(); val day = LocalDate.of(2026, 8, 1).toEpochDay()
        val rows = (0 until 10_000).map { tx("t$it", TransactionType.EXPENSE, 1, "a", day + it % 31) }
        assertEquals(10_000, dao.insertIgnoringDuplicates(rows).count { it != -1L })
        val summary = dao.observeSummary(day, day + 30).first(); assertEquals(10_000L, summary.expenseMinor); assertEquals(10_000, summary.transactionCount)
    }

    @Test fun monthlyBudgetUpsertReplacesOldValueAndLegacyDuplicatesCanBeRemoved() = runTest {
        val dao = db.planningDao()
        val start = LocalDate.of(2026, 8, 1).toEpochDay()
        val end = LocalDate.of(2026, 8, 31).toEpochDay()
        dao.upsertBudget(BudgetEntity("monthly_budget", "月消费目标", PeriodType.MONTH, start, end, 500_000L))
        dao.upsertBudget(BudgetEntity("monthly_budget_2026-08", "旧版月预算", PeriodType.MONTH, start, end, 600_000L))
        dao.deleteLegacyMonthlyBudgets()
        dao.upsertBudget(BudgetEntity("monthly_budget", "月消费目标", PeriodType.MONTH, start, end, 750_000L))

        val rows = dao.observeBudgetsFor(start, end).first()
        assertEquals(1, rows.size)
        assertEquals("monthly_budget", rows.single().id)
        assertEquals(750_000L, rows.single().targetAmountMinor)
    }

    @Test fun goalUpsertWithSameIdEditsInsteadOfCreatingDuplicate() = runTest {
        val dao = db.planningDao()
        val today = LocalDate.of(2026, 8, 5).toEpochDay()
        val original = GoalEntity("goal-edit", GoalType.ASSET_BALANCE, "原目标", 1_000_000L, startEpochDay = today, targetEpochDay = today + 365, periodType = PeriodType.CUSTOM, createdAtEpochMillis = 100, updatedAtEpochMillis = 100)
        dao.upsertGoal(original)
        dao.upsertGoal(original.copy(name = "新目标", targetAmountMinor = 2_000_000L, targetEpochDay = today + 730, updatedAtEpochMillis = 200))

        val rows = dao.observeGoals().first()
        assertEquals(1, rows.size)
        assertEquals("goal-edit", rows.single().id)
        assertEquals("新目标", rows.single().name)
        assertEquals(2_000_000L, rows.single().targetAmountMinor)
        assertEquals(100L, rows.single().createdAtEpochMillis)
    }

    @Test fun transactionListIncludesSelectedCategoryIcon() = runTest {
        val day = LocalDate.of(2026, 8, 5).toEpochDay()
        db.accountDao().upsert(account("a", 0, 0))
        db.categoryDao().upsert(CategoryEntity(id = "food", name = "餐饮", transactionDirection = TransactionDirection.EXPENSE, iconKey = "restaurant", defaultNecessity = Necessity.NECESSARY, defaultVariability = Variability.VARIABLE))
        db.transactionDao().insert(tx("with-icon", TransactionType.EXPENSE, 2_500, "a", day).copy(categoryId = "food"))

        val row = db.transactionDao().observeList(day, day).first().single()
        assertEquals("餐饮", row.categoryName)
        assertEquals("restaurant", row.categoryIconKey)
    }

    @Test fun accountSortOrderCanBeChanged() = runTest {
        db.accountDao().upsert(account("first", 0, 0).copy(sortOrder = 0))
        db.accountDao().upsert(account("second", 0, 0).copy(sortOrder = 1))
        db.accountDao().updateSortOrder("second", -1, 10)

        assertEquals(listOf("second", "first"), db.accountDao().observeAll().first().map { it.id })
    }

    @Test fun aggregateExpenseCountsInSummaryButNotDailyTrend() = runTest {
        val day = LocalDate.of(2026, 8, 5).toEpochDay()
        db.accountDao().upsert(account("a", 0, 0))
        db.transactionDao().insert(tx("monthly-total", TransactionType.EXPENSE, 300_000, "a", day).copy(
            recordGranularity = RecordGranularity.MONTH,
            periodStartEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
            periodEndEpochDay = LocalDate.of(2026, 8, 31).toEpochDay(),
        ))

        val summary = db.transactionDao().observeSummary(day - 4, day + 26).first()
        assertEquals(300_000L, summary.expenseMinor)
        assertEquals(1, summary.aggregateCount)
        assertEquals(1, summary.expenseAggregateCount)
        assertEquals(0, summary.incomeAggregateCount)
        assertEquals(emptyList<DailyTotalRow>(), db.transactionDao().observeDailyExpense(day - 4, day + 26).first())
    }

    @Test fun merchantSuggestionsSupportContainsMatchingAndUsageRanking() = runTest {
        db.merchantDao().upsert(MerchantEntity("m1", "盒马鲜生", "盒马鲜生", useCount = 3, lastUsedAtEpochMillis = 10))
        db.merchantDao().upsert(MerchantEntity("m2", "山姆会员商店", "山姆会员商店", useCount = 8, lastUsedAtEpochMillis = 20))
        db.merchantDao().upsert(MerchantEntity("m3", "盒马mini", "盒马 Mini", useCount = 1, lastUsedAtEpochMillis = 30))

        val suggestions = db.merchantDao().observeSuggestions("盒马", "盒马").first()
        assertEquals(listOf("盒马鲜生", "盒马 Mini"), suggestions.map { it.displayName })
        assertEquals(emptyList<MerchantEntity>(), db.merchantDao().observeSuggestions("滴滴", "滴滴").first())
    }

    @Test fun dynamicDashboardBudgetUsesPreviousMonthActualExpense() = runTest {
        val repository = repository()
        val now = System.currentTimeMillis()
        db.accountDao().upsert(account("a", now, 0))
        val julyDay = LocalDate.of(2026, 7, 10).toEpochDay()
        db.transactionDao().insert(tx("july-expense", TransactionType.EXPENSE, 400_000, "a", julyDay))
        val august = Periods.month(java.time.YearMonth.of(2026, 8))
        db.planningDao().upsertBudget(BudgetEntity("monthly_budget", "月消费目标", PeriodType.MONTH, august.startEpochDay, august.endEpochDay, 500_000, rolloverMode = RolloverMode.NET))

        val dashboard = repository.observeDashboard(java.time.YearMonth.of(2026, 8)).first()
        assertEquals(500_000L, dashboard.baseBudgetMinor)
        assertEquals(100_000L, dashboard.budgetAdjustmentMinor)
        assertEquals(600_000L, dashboard.budgetMinor)
        assertEquals(400_000L, dashboard.previousMonthExpenseMinor)
    }

    @Test fun repositoryRemembersMerchantAndNeverPersistsExpenseCategoryOnTransfer() = runTest {
        val repository = repository()
        val now = System.currentTimeMillis()
        db.accountDao().upsertAll(listOf(account("a", now, 0), account("b", now, 0)))
        db.categoryDao().upsert(CategoryEntity("food", name = "餐饮", transactionDirection = TransactionDirection.EXPENSE, iconKey = "restaurant", defaultNecessity = Necessity.NECESSARY, defaultVariability = Variability.VARIABLE))
        val date = LocalDate.of(2026, 8, 9)
        repository.addTransaction(TransactionDraft(type = TransactionType.EXPENSE, amountMinor = 2_000, date = date, accountId = "a", categoryId = "food", merchantName = "盒马鲜生"))
        val transferId = repository.addTransaction(TransactionDraft(type = TransactionType.TRANSFER, amountMinor = 5_000, date = date, accountId = "a", destinationAccountId = "b", categoryId = "food"))

        assertEquals("盒马鲜生", repository.observeMerchantSuggestions("鲜").first().single().displayName)
        assertEquals("food", repository.observeMerchantSuggestions("盒马").first().single().defaultCategoryId)
        assertNull(db.transactionDao().byId(transferId)?.categoryId)
    }

    private fun repository() = OfflineFinanceRepository(
        db, db.accountDao(), db.categoryDao(), db.merchantDao(), db.transactionDao(), db.planningDao(), db.historyDao(), db.importDao()
    )

    private fun account(id: String, now: Long, opening: Long) = AccountEntity(id, id, accountType = AccountType.BANK, openingBalanceMinor = opening, createdAtEpochMillis = now, updatedAtEpochMillis = now)
    private fun tx(id: String, type: TransactionType, amount: Long, account: String, day: Long, destination: String? = null, direction: Int = 1) = TransactionEntity(id, type, amount, occurredAtEpochMillis = 0, localDateEpochDay = day, accountId = account, destinationAccountId = destination, balanceDirection = direction, fingerprint = "fp_$id", createdAtEpochMillis = 0, updatedAtEpochMillis = 0)
}
