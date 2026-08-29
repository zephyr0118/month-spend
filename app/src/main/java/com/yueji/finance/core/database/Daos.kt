package com.yueji.finance.core.database

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.yueji.finance.core.model.*
import kotlinx.coroutines.flow.Flow

data class PeriodSummaryRow(
    val incomeMinor: Long,
    val expenseMinor: Long,
    val refundMinor: Long,
    val transactionCount: Int,
    val aggregateCount: Int,
    val expenseCount: Int,
    val incomeCount: Int,
    val expenseAggregateCount: Int,
    val incomeAggregateCount: Int,
)

data class CategoryTotalRow(val id: String?, val name: String?, val amountMinor: Long)
data class DailyTotalRow(val epochDay: Long, val amountMinor: Long)
data class AccountBalanceRow(val id: String, val name: String, val accountType: AccountType, val includeInAssets: Boolean, val amountMinor: Long)
data class TransactionListRow(
    val id: String,
    val type: TransactionType,
    val amountMinor: Long,
    val occurredAtEpochMillis: Long,
    val localDateEpochDay: Long,
    val accountId: String,
    val accountName: String,
    val destinationAccountId: String?,
    val destinationAccountName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val categoryIconKey: String?,
    val merchantName: String?,
    val note: String?,
    val necessity: Necessity?,
    val variability: Variability?,
    val isOneOff: Boolean,
    val isReimbursable: Boolean,
    val excludeFromBudget: Boolean,
    val status: TransactionStatus,
    val source: TransactionSource,
    val tagsCsv: String?,
    val recordGranularity: RecordGranularity,
    val periodStartEpochDay: Long?,
    val periodEndEpochDay: Long?,
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY isArchived, sortOrder, name") fun observeAll(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY sortOrder, name") suspend fun active(): List<AccountEntity>
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun byId(id: String): AccountEntity?
    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1") suspend fun byName(name: String): AccountEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: AccountEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(entities: List<AccountEntity>)
    @Query("UPDATE accounts SET sortOrder = :sortOrder, updatedAtEpochMillis = :updatedAt WHERE id = :id") suspend fun updateSortOrder(id: String, sortOrder: Int, updatedAt: Long)
    @Delete suspend fun delete(entity: AccountEntity)

    @Query(
        """SELECT a.id, a.name, a.accountType, a.includeInAssets,
        a.openingBalanceMinor + COALESCE(SUM(CASE
          WHEN t.status != 'CONFIRMED' THEN 0
          WHEN t.type = 'EXPENSE' AND t.accountId = a.id THEN -t.amountMinor
          WHEN t.type = 'INCOME' AND t.accountId = a.id THEN t.amountMinor
          WHEN t.type = 'REFUND' AND t.accountId = a.id THEN t.amountMinor
          WHEN t.type = 'BALANCE_ADJUSTMENT' AND t.accountId = a.id THEN t.amountMinor * t.balanceDirection
          WHEN t.type = 'TRANSFER' AND t.accountId = a.id THEN -t.amountMinor
          WHEN t.type = 'TRANSFER' AND t.destinationAccountId = a.id THEN t.amountMinor
          ELSE 0 END), 0) AS amountMinor
        FROM accounts a LEFT JOIN transactions t
          ON t.accountId = a.id OR t.destinationAccountId = a.id
        WHERE a.isArchived = 0
        GROUP BY a.id ORDER BY a.sortOrder, a.name"""
    ) fun observeBalances(): Flow<List<AccountBalanceRow>>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY transactionDirection, sortOrder, name") fun observeActive(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories WHERE isArchived = 0 AND transactionDirection = :direction ORDER BY sortOrder, name") suspend fun active(direction: TransactionDirection): List<CategoryEntity>
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun byId(id: String): CategoryEntity?
    @Query("SELECT * FROM categories WHERE name = :name AND transactionDirection = :direction LIMIT 1") suspend fun byName(name: String, direction: TransactionDirection): CategoryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: CategoryEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(entities: List<CategoryEntity>)
}

@Dao
interface MerchantDao {
    @Query(
        """SELECT * FROM merchants
        WHERE :normalizedQuery = ''
           OR normalizedName LIKE '%' || :normalizedQuery || '%'
           OR displayName LIKE '%' || :displayQuery || '%'
        ORDER BY CASE
            WHEN normalizedName = :normalizedQuery THEN 0
            WHEN normalizedName LIKE :normalizedQuery || '%' THEN 1
            ELSE 2 END,
            useCount DESC,
            COALESCE(lastUsedAtEpochMillis, 0) DESC
        LIMIT :limit"""
    )
    fun observeSuggestions(normalizedQuery: String, displayQuery: String, limit: Int = 8): Flow<List<MerchantEntity>>

    @Query("SELECT * FROM merchants WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun byNormalizedName(normalizedName: String): MerchantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MerchantEntity)
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(entity: TransactionEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnoringDuplicates(entities: List<TransactionEntity>): List<Long>
    @Update suspend fun update(entity: TransactionEntity)
    @Query("UPDATE transactions SET status = 'DELETED', updatedAtEpochMillis = :updatedAt WHERE id = :id") suspend fun softDelete(id: String, updatedAt: Long)
    @Query("UPDATE transactions SET status = 'CONFIRMED', updatedAtEpochMillis = :updatedAt WHERE id = :id") suspend fun restore(id: String, updatedAt: Long)
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun byId(id: String): TransactionEntity?
    @Query("SELECT * FROM transactions WHERE status != 'DELETED' ORDER BY occurredAtEpochMillis DESC LIMIT 1") suspend fun last(): TransactionEntity?
    @Query("SELECT COUNT(*) FROM transactions WHERE status != 'DELETED'") suspend fun count(): Int
    @Query("SELECT * FROM transactions WHERE status != 'DELETED' ORDER BY occurredAtEpochMillis") suspend fun allForExport(): List<TransactionEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE fingerprint = :fingerprint)") suspend fun fingerprintExists(fingerprint: String): Boolean

    @Query(
        """SELECT
        COALESCE(SUM(CASE WHEN type = 'INCOME' AND status = 'CONFIRMED' THEN amountMinor ELSE 0 END), 0) AS incomeMinor,
        COALESCE(SUM(CASE WHEN type = 'EXPENSE' AND status = 'CONFIRMED' AND excludeFromBudget = 0 THEN amountMinor ELSE 0 END), 0) AS expenseMinor,
        COALESCE(SUM(CASE WHEN type = 'REFUND' AND status = 'CONFIRMED' AND excludeFromBudget = 0 THEN amountMinor ELSE 0 END), 0) AS refundMinor,
        COALESCE(SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END), 0) AS transactionCount,
        COALESCE(SUM(CASE WHEN status = 'CONFIRMED' AND recordGranularity != 'DAY' THEN 1 ELSE 0 END), 0) AS aggregateCount,
        COALESCE(SUM(CASE WHEN status = 'CONFIRMED' AND type IN ('EXPENSE', 'REFUND') THEN 1 ELSE 0 END), 0) AS expenseCount,
        COALESCE(SUM(CASE WHEN status = 'CONFIRMED' AND type = 'INCOME' THEN 1 ELSE 0 END), 0) AS incomeCount,
        COALESCE(SUM(CASE WHEN status = 'CONFIRMED' AND type IN ('EXPENSE', 'REFUND') AND recordGranularity != 'DAY' THEN 1 ELSE 0 END), 0) AS expenseAggregateCount,
        COALESCE(SUM(CASE WHEN status = 'CONFIRMED' AND type = 'INCOME' AND recordGranularity != 'DAY' THEN 1 ELSE 0 END), 0) AS incomeAggregateCount
        FROM transactions WHERE localDateEpochDay BETWEEN :start AND :end"""
    ) fun observeSummary(start: Long, end: Long): Flow<PeriodSummaryRow>

    @Query(
        """SELECT c.id AS id, COALESCE(c.name, '未分类') AS name,
        COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amountMinor WHEN t.type = 'REFUND' THEN -t.amountMinor ELSE 0 END), 0) AS amountMinor
        FROM transactions t LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.localDateEpochDay BETWEEN :start AND :end AND t.status = 'CONFIRMED'
          AND t.excludeFromBudget = 0 AND t.type IN ('EXPENSE', 'REFUND')
        GROUP BY c.id ORDER BY amountMinor DESC"""
    ) fun observeExpenseByCategory(start: Long, end: Long): Flow<List<CategoryTotalRow>>

    @Query(
        """SELECT localDateEpochDay AS epochDay,
        COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountMinor WHEN type = 'REFUND' THEN -amountMinor ELSE 0 END), 0) AS amountMinor
        FROM transactions WHERE localDateEpochDay BETWEEN :start AND :end AND status = 'CONFIRMED'
          AND excludeFromBudget = 0 AND type IN ('EXPENSE', 'REFUND') AND recordGranularity = 'DAY'
        GROUP BY localDateEpochDay ORDER BY localDateEpochDay"""
    ) fun observeDailyExpense(start: Long, end: Long): Flow<List<DailyTotalRow>>

    @Query(
        """SELECT t.id, t.type, t.amountMinor, t.occurredAtEpochMillis, t.localDateEpochDay,
        t.accountId, a.name AS accountName, t.destinationAccountId, da.name AS destinationAccountName,
        t.categoryId, c.name AS categoryName, c.iconKey AS categoryIconKey, t.merchantName, t.note, t.necessity, t.variability,
        t.isOneOff, t.isReimbursable, t.excludeFromBudget, t.status, t.source,
        t.recordGranularity, t.periodStartEpochDay, t.periodEndEpochDay,
        (SELECT GROUP_CONCAT(tg.name, ',') FROM transaction_tags tt JOIN tags tg ON tg.id = tt.tagId WHERE tt.transactionId = t.id) AS tagsCsv
        FROM transactions t
        JOIN accounts a ON a.id = t.accountId
        LEFT JOIN accounts da ON da.id = t.destinationAccountId
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.status != 'DELETED' AND t.localDateEpochDay BETWEEN :start AND :end
        ORDER BY t.occurredAtEpochMillis DESC"""
    ) fun observeList(start: Long, end: Long): Flow<List<TransactionListRow>>

    @RawQuery(observedEntities = [TransactionEntity::class, AccountEntity::class, CategoryEntity::class, MerchantEntity::class])
    fun observeFiltered(query: SupportSQLiteQuery): Flow<List<TransactionListRow>>
}

@Dao
interface PlanningDao {
    @Query("SELECT * FROM budgets WHERE isEnabled = 1 ORDER BY endEpochDay DESC") fun observeBudgets(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets WHERE isEnabled = 1 AND ((startEpochDay <= :end AND endEpochDay >= :start) OR isRecurring = 1) ORDER BY startEpochDay DESC, categoryId") fun observeBudgetsFor(start: Long, end: Long): Flow<List<BudgetEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBudget(entity: BudgetEntity)
    @Query("DELETE FROM budgets WHERE id != 'monthly_budget' AND id GLOB 'monthly_budget_*'") suspend fun deleteLegacyMonthlyBudgets()
    @Query("DELETE FROM budgets WHERE id = :id") suspend fun deleteBudget(id: String)
    @Query("SELECT * FROM goals WHERE status = 'ACTIVE' ORDER BY targetEpochDay") fun observeGoals(): Flow<List<GoalEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertGoal(entity: GoalEntity)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun deleteGoal(id: String)
    @Query("SELECT * FROM recurring_rules WHERE isEnabled = 1 ORDER BY nextOccurrenceEpochDay") fun observeRecurringRules(): Flow<List<RecurringRuleEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRecurringRule(entity: RecurringRuleEntity)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM balance_snapshots ORDER BY snapshotYearMonth, accountId") fun observeSnapshots(): Flow<List<BalanceSnapshotEntity>>
    @Query("SELECT * FROM legacy_annual_summaries ORDER BY periodStartYearMonth") fun observeAnnualSummaries(): Flow<List<LegacyAnnualSummaryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSnapshots(items: List<BalanceSnapshotEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSummaries(items: List<LegacyAnnualSummaryEntity>)
    @Query("SELECT COUNT(*) FROM balance_snapshots") suspend fun snapshotCount(): Int
    @Query("SELECT COUNT(*) FROM legacy_annual_summaries") suspend fun summaryCount(): Int
}

@Dao
interface ImportDao {
    @Query("SELECT * FROM import_batches ORDER BY importedAtEpochMillis DESC") fun observeBatches(): Flow<List<ImportBatchEntity>>
    @Query("SELECT * FROM import_batches WHERE fileHash = :hash") suspend fun byHash(hash: String): ImportBatchEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBatch(batch: ImportBatchEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTags(tags: List<TagEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTransactionTags(items: List<TransactionTagCrossRef>)
    @Query("DELETE FROM transaction_tags WHERE transactionId = :transactionId") suspend fun clearTransactionTags(transactionId: String)
    @Query("SELECT tags.* FROM tags JOIN transaction_tags ON tags.id = transaction_tags.tagId WHERE transaction_tags.transactionId = :transactionId") suspend fun tagsFor(transactionId: String): List<TagEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAttachment(entity: AttachmentEntity)
    @Query("SELECT * FROM attachments WHERE transactionId = :transactionId") suspend fun attachmentsFor(transactionId: String): List<AttachmentEntity>
}
