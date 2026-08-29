package com.yueji.finance.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.yueji.finance.core.model.*

@Entity(tableName = "accounts", indices = [Index("parentAccountId")])
data class AccountEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val institutionName: String? = null,
    val parentAccountId: String? = null,
    val accountType: AccountType,
    val currencyCode: String = "CNY",
    val openingBalanceMinor: Long = 0,
    val includeInAssets: Boolean = true,
    val allowNegativeBalance: Boolean = false,
    val isArchived: Boolean = false,
    val iconKey: String = "account_balance",
    val colorArgb: Long? = null,
    val sortOrder: Int = 0,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "categories", indices = [Index("parentId")])
data class CategoryEntity(
    @androidx.room.PrimaryKey val id: String,
    val parentId: String? = null,
    val name: String,
    val transactionDirection: TransactionDirection,
    val iconKey: String,
    val defaultNecessity: Necessity,
    val defaultVariability: Variability,
    val includeInLivingCost: Boolean = true,
    val includeInEmergencyFund: Boolean = true,
    val isSystem: Boolean = true,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(tableName = "merchants", indices = [Index(value = ["normalizedName"], unique = true)])
data class MerchantEntity(
    @androidx.room.PrimaryKey val id: String,
    val normalizedName: String,
    val displayName: String,
    val defaultCategoryId: String? = null,
    val lastUsedAtEpochMillis: Long? = null,
    val useCount: Int = 0,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("localDateEpochDay"), Index(value = ["accountId", "localDateEpochDay"]),
        Index(value = ["categoryId", "localDateEpochDay"]), Index(value = ["merchantId", "localDateEpochDay"]),
        Index(value = ["type", "localDateEpochDay"]), Index("destinationAccountId"), Index("linkedTransactionId"),
        Index(value = ["fingerprint"], unique = true),
    ],
)
data class TransactionEntity(
    @androidx.room.PrimaryKey val id: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currencyCode: String = "CNY",
    val occurredAtEpochMillis: Long,
    val localDateEpochDay: Long,
    val accountId: String,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val subcategoryId: String? = null,
    val merchantId: String? = null,
    val merchantName: String? = null,
    val note: String? = null,
    val necessity: Necessity? = null,
    val variability: Variability? = null,
    val isOneOff: Boolean = false,
    val isReimbursable: Boolean = false,
    val excludeFromBudget: Boolean = false,
    val linkedTransactionId: String? = null,
    val recurringRuleId: String? = null,
    val importBatchId: String? = null,
    val source: TransactionSource = TransactionSource.MANUAL,
    val status: TransactionStatus = TransactionStatus.CONFIRMED,
    val balanceDirection: Int = 1,
    val fingerprint: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val recordGranularity: RecordGranularity = RecordGranularity.DAY,
    val periodStartEpochDay: Long? = null,
    val periodEndEpochDay: Long? = null,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(@androidx.room.PrimaryKey val id: String, val name: String, val colorArgb: Long? = null)

@Entity(tableName = "transaction_tags", primaryKeys = ["transactionId", "tagId"], indices = [Index("tagId")])
data class TransactionTagCrossRef(val transactionId: String, val tagId: String)

@Entity(tableName = "budgets", indices = [Index("categoryId"), Index("accountId")])
data class BudgetEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val periodType: PeriodType,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val targetAmountMinor: Long,
    val categoryId: String? = null,
    val accountId: String? = null,
    val rolloverMode: RolloverMode = RolloverMode.NONE,
    val warningThresholdPercent: Int = 90,
    val isRecurring: Boolean = true,
    val isEnabled: Boolean = true,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @androidx.room.PrimaryKey val id: String,
    val goalType: GoalType,
    val name: String,
    val targetAmountMinor: Long = 0,
    val targetRatioBasisPoints: Int? = null,
    val startEpochDay: Long,
    val targetEpochDay: Long,
    val periodType: PeriodType,
    val fiscalYearStartMonth: Int = 9,
    val isRecurring: Boolean = false,
    val status: String = "ACTIVE",
    val reminderEnabled: Boolean = true,
    val note: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "balance_snapshots", indices = [Index(value = ["accountId", "snapshotYearMonth"], unique = true)])
data class BalanceSnapshotEntity(
    @androidx.room.PrimaryKey val id: String,
    val accountId: String,
    val snapshotYearMonth: Int,
    val amountMinor: Long,
    val datePrecision: String = "MONTH_ONLY",
    val source: TransactionSource = TransactionSource.LEGACY,
    val note: String? = null,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "legacy_annual_summaries", indices = [Index(value = ["periodStartYearMonth", "periodEndYearMonth"], unique = true)])
data class LegacyAnnualSummaryEntity(
    @androidx.room.PrimaryKey val id: String,
    val label: String,
    val periodStartYearMonth: Int,
    val periodEndYearMonth: Int,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val savingsMinor: Long,
    val isEstimated: Boolean = false,
    val note: String? = null,
)

@Entity(tableName = "recurring_rules", indices = [Index("templateTransactionId")])
data class RecurringRuleEntity(
    @androidx.room.PrimaryKey val id: String,
    val templateTransactionId: String,
    val frequency: String,
    val interval: Int = 1,
    val dayOfMonth: Int? = null,
    val dayOfWeek: Int? = null,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val nextOccurrenceEpochDay: Long,
    val autoCreate: Boolean = false,
    val reminderOnly: Boolean = true,
    val isEnabled: Boolean = true,
)

@Entity(tableName = "import_batches", indices = [Index(value = ["fileHash"], unique = true)])
data class ImportBatchEntity(
    @androidx.room.PrimaryKey val id: String,
    val fileName: String,
    val fileHash: String,
    val importedAtEpochMillis: Long,
    val successCount: Int,
    val skippedCount: Int,
    val errorCount: Int,
    val source: String,
    val errorReportPath: String? = null,
    val appVersion: String = "1.0",
)

@Entity(tableName = "attachments", indices = [Index("transactionId")])
data class AttachmentEntity(
    @androidx.room.PrimaryKey val id: String,
    val transactionId: String,
    val localRelativePath: String,
    val mimeType: String,
    val fileSize: Long,
    val createdAtEpochMillis: Long,
)
