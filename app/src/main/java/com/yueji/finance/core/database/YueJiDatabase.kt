package com.yueji.finance.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, MerchantEntity::class,
        TransactionEntity::class, TagEntity::class, TransactionTagCrossRef::class,
        BudgetEntity::class, GoalEntity::class, BalanceSnapshotEntity::class,
        LegacyAnnualSummaryEntity::class, RecurringRuleEntity::class,
        ImportBatchEntity::class, AttachmentEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class YueJiDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun transactionDao(): TransactionDao
    abstract fun planningDao(): PlanningDao
    abstract fun historyDao(): HistoryDao
    abstract fun importDao(): ImportDao
}

object DatabaseMigrations {
    val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE import_batches ADD COLUMN appVersion TEXT NOT NULL DEFAULT '1.0'")
        }
    }
    val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN recordGranularity TEXT NOT NULL DEFAULT 'DAY'")
            db.execSQL("ALTER TABLE transactions ADD COLUMN periodStartEpochDay INTEGER")
            db.execSQL("ALTER TABLE transactions ADD COLUMN periodEndEpochDay INTEGER")
        }
    }
    val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            // 转账和余额调整有自己的业务类型，不应继承历史支出分类。
            db.execSQL("UPDATE transactions SET categoryId = NULL WHERE type IN ('TRANSFER', 'BALANCE_ADJUSTMENT')")
        }
    }
    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
